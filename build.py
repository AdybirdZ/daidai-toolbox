"""Build a dependency-free Android APK using Google's SDK and a JDK (no Gradle download).

Usage: python build.py --sdk /path/to/android-sdk --out /path/to/SimpleWeather-1.0.0.apk
Requires SDK platform android-35, build-tools 35.0.0, JDK 17+.
Creates a LOCAL DEVELOPMENT signing key in .signing/, which must be kept for updates.
"""
import argparse
import os
from pathlib import Path
import shutil
import subprocess
import sys
import zipfile
import json
import re

ROOT = Path(__file__).resolve().parent


def run(args):
    # Google's Windows resource tools need relative paths in non-ASCII workspaces.
    command = [os.path.relpath(x, ROOT) if i > 0 and isinstance(x, Path) else str(x)
               for i, x in enumerate(args)]
    subprocess.run(command, check=True, cwd=ROOT)


def main():
    if hasattr(sys.stdout, 'reconfigure'):
        sys.stdout.reconfigure(encoding='utf-8')
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sdk', default=os.getenv('ANDROID_HOME') or os.getenv('ANDROID_SDK_ROOT'))
    parser.add_argument('--out', default=str(ROOT / 'build/DaidaiToolbox-1.0.0.apk'))
    opts = parser.parse_args()
    if not opts.sdk:
        parser.error('Set --sdk or ANDROID_HOME to your Android SDK directory.')
    run([sys.executable, ROOT / 'tests/release_checks.py'])
    sdk = Path(opts.sdk).resolve()
    platform = sdk / 'platforms/android-35/android.jar'
    tools = sdk / 'build-tools/35.0.0'
    ext = '.exe' if os.name == 'nt' else ''
    javac = shutil.which('javac')
    if not javac or not platform.exists():
        parser.error('JDK and SDK platform android-35 are required.')
    java_bin = Path(javac).resolve().parent
    java = java_bin / ('java' + ext)
    build = ROOT / 'build'
    build.mkdir(exist_ok=True)
    # All cleanup stays inside this project's generated build/ directory.
    for name in ['classes', 'dex', 'generated']:
        directory = build / name
        if directory.exists():
            shutil.rmtree(directory)
        directory.mkdir()
    source = ROOT / 'app/src/main'
    run([tools / ('aapt2' + ext), 'compile', '--dir', source / 'res', '-o', build / 'resources.zip'])
    unsigned = build / 'unsigned.apk'
    stable_ids=ROOT/'modules/stable-resource-ids.txt'
    full_ids=build/'full-resource-ids.txt'
    stable_args=['--stable-ids',stable_ids] if stable_ids.exists() else []
    run([tools / ('aapt2' + ext), 'link', '-o', unsigned, '-I', platform,
         '--manifest', source / 'AndroidManifest.xml', '--java', build / 'generated',
         '-A', source / 'assets', '--min-sdk-version', '26', '--target-sdk-version', '35',
         '--emit-ids',full_ids,*stable_args,build / 'resources.zip'])
    if stable_ids.exists():
        before=dict(line.split(' = ') for line in stable_ids.read_text('utf8').splitlines() if ' = ' in line)
        after=dict(line.split(' = ') for line in full_ids.read_text('utf8').splitlines() if ' = ' in line)
        assert all(after.get(key)==value for key,value in before.items()), 'Host API 1 resource IDs must not be removed or changed'
    # The complete public resource ID map is append-only within host API 1.
    shutil.copyfile(full_ids,stable_ids)
    sys.path.insert(0,str(ROOT/'modules'))
    from resource_partition import partition
    parts=partition(ROOT)
    run([tools/('aapt2'+ext),'compile','--dir',parts/'host','-o',build/'host-resources.zip'])
    sources = list((source / 'java').rglob('*.java')) + list((ROOT / 'modules/apps').rglob('*.java')) + list((ROOT / 'modules/embedded').rglob('*.java')) + list((build / 'generated').rglob('*.java'))
    run([javac, '-encoding', 'UTF-8', '--release', '8', '-classpath', platform, '-d', build / 'classes', *sources])
    contract=ROOT/'modules/host-java-api.json'
    exported={}
    javap=java_bin/('javap'+ext)
    host_names={p.stem for p in (source/'java/org/simpleweather/app').glob('*.java')}
    for path in sorted((build/'classes/org/simpleweather/app').glob('*.class')):
        if path.stem.split('$')[0] not in host_names:continue
        if path.stem=='R' or path.stem.startswith('R$'):continue
        name='org.simpleweather.app.'+path.stem
        output=subprocess.check_output([str(javap),'-public','-classpath',str(build/'classes'),name],text=True,encoding='utf8')
        # Synchronization changes method implementation, not its JVM linkage contract.
        lines=[line.strip().replace(' synchronized ',' ') for line in output.splitlines() if line.strip().startswith(('public ','protected '))]
        if lines and any(re.match(r'public .*\b(?:class|interface|enum)\b', line) for line in lines):exported[name]=lines
    previous=json.loads(contract.read_text('utf8')) if contract.exists() else {}
    previous={name:[line.replace(' synchronized ',' ') for line in lines] for name,lines in previous.items()}
    for name,lines in previous.items():assert set(lines)<=set(exported.get(name,[])), 'Host API 1 signature changed: '+name
    contract.write_text(json.dumps(exported,indent=2)+'\n','utf8')
    signing = ROOT / '.signing'
    signing.mkdir(exist_ok=True)
    keystore = signing / 'development.jks'
    # Public development password; this key is for private sideloading, not a store release.
    if not keystore.exists():
        run([java_bin / ('keytool' + ext), '-genkeypair', '-keystore', keystore, '-alias', 'simpleweather',
             '-storepass', 'android', '-keypass', 'android', '-keyalg', 'RSA', '-keysize', '3072',
             '-validity', '10000', '-dname', 'CN=SimpleWeather Local Development', '-noprompt'])
    # Build executable app packages first; their classes must not also enter host DEX.
    sys.path.insert(0, str(ROOT / 'modules'))
    from build_executable import build as build_apps
    app_classes = build_apps(ROOT, sdk, java, run)
    # Refresh packaged assets after the default signed apps and catalog are generated.
    host_assets=build/'host-assets'
    shutil.copytree(source/'assets',host_assets,dirs_exist_ok=True)
    (host_assets/'cities.json').unlink(missing_ok=True)
    run([tools / ('aapt2' + ext), 'link', '-o', unsigned, '-I', platform,
         '--manifest', source / 'AndroidManifest.xml', '--java', build / 'generated',
         '-A', host_assets, '--min-sdk-version', '26', '--target-sdk-version', '35',
         '--stable-ids',stable_ids,build / 'host-resources.zip'])
    run([java, '-cp', tools / 'lib/d8.jar', 'com.android.tools.r8.D8', '--lib', platform,
         '--classpath', build / 'all-classes.jar', '--min-api', '26', '--output', build / 'dex',
         *[p for p in (build / 'classes').rglob('*.class') if p not in app_classes]])
    with zipfile.ZipFile(unsigned, 'a', zipfile.ZIP_DEFLATED) as apk:
        for dex in (build / 'dex').glob('*.dex'):
            apk.write(dex, dex.name)
    aligned = build / 'aligned.apk'
    run([tools / ('zipalign' + ext), '-f', '-P', '16', '4', unsigned, aligned])
    output = Path(opts.out).resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    signer = [java, '-jar', tools / 'lib/apksigner.jar']
    run([*signer, 'sign', '--ks', keystore, '--ks-key-alias', 'simpleweather',
         '--ks-pass', 'pass:android', '--key-pass', 'pass:android', '--out', output, aligned])
    run([*signer, 'verify', '--verbose', '--print-certs', output])
    print('APK:', output)


if __name__ == '__main__':
    main()
