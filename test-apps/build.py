"""Build the three standalone test apps with a local JDK and Android SDK."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parent
APPS = {
    'music': 'DaidaiMusic',
    'messaging': 'DaidaiMessenger',
    'colorvision': 'DaidaiColorVision',
}
ANDROID = '{http://schemas.android.com/apk/res/android}'


def app_version(name):
    return ET.parse(ROOT / 'launchers' / name / 'AndroidManifest.xml').getroot().get(ANDROID + 'versionName')


def run(args, **kwargs):
    # Relative source arguments also work with JDKs affected by non-ASCII paths.
    command = []
    for index, arg in enumerate(args):
        if index and isinstance(arg, Path):
            try:
                arg = os.path.relpath(arg, ROOT)
            except ValueError:
                arg = str(arg)
        command.append(str(arg))
    return subprocess.run(command, cwd=ROOT, check=True, **kwargs)


def resolve_tools(sdk, build_tools, platform):
    compiler = shutil.which('javac')
    if not compiler:
        raise SystemExit('JDK 17 or newer is required; add javac to PATH.')
    java_bin = Path(compiler).resolve().parent
    suffix = '.exe' if os.name == 'nt' else ''
    tools = {
        'java': java_bin / ('java' + suffix),
        'javac': java_bin / ('javac' + suffix),
        'keytool': java_bin / ('keytool' + suffix),
        'aapt2': sdk / 'build-tools' / build_tools / ('aapt2' + suffix),
        'zipalign': sdk / 'build-tools' / build_tools / ('zipalign' + suffix),
        'd8': sdk / 'build-tools' / build_tools / 'lib/d8.jar',
        'apksigner': sdk / 'build-tools' / build_tools / 'lib/apksigner.jar',
        'android': sdk / 'platforms' / platform / 'android.jar',
    }
    for name, file in tools.items():
        if not file.is_file():
            raise SystemExit(f'Missing {name}: {file}')
    return tools


def signing_key(args, tools, output):
    store_password = os.environ.get('DAIDAI_TEST_APPS_STORE_PASSWORD', 'android')
    key_password = os.environ.get('DAIDAI_TEST_APPS_KEY_PASSWORD', store_password)
    if args.keystore:
        keystore = Path(args.keystore).expanduser().resolve()
        if not keystore.is_file():
            raise SystemExit('--keystore must identify an existing signing key.')
        alias = args.ks_alias or 'test-apps'
    else:
        if args.ks_alias:
            raise SystemExit('--ks-alias requires --keystore.')
        keystore = output / '.signing/development.jks'
        alias = 'test-apps'
        if not keystore.exists():
            keystore.parent.mkdir(parents=True, exist_ok=True)
            run([tools['keytool'], '-genkeypair', '-keystore', keystore,
                 '-alias', alias, '-storepass', store_password, '-keypass', key_password,
                 '-keyalg', 'RSA', '-keysize', '3072', '-validity', '10000',
                 '-dname', 'CN=Daidai Test Apps Development', '-noprompt'],
                stdout=subprocess.DEVNULL)
    return keystore, alias, store_password, key_password


def build(name, tools, output, signing):
    version = app_version(name)
    # A fresh generated directory prevents stale classes without deleting user paths.
    intermediate = output / '.work'
    intermediate.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix=name + '-', dir=intermediate) as folder:
        generated = Path(folder)
        for child in ('classes', 'dex', 'r'):
            (generated / child).mkdir()
        resources = []
        for resource_name, resource_dir in (
            ('shared', ROOT / 'shared/res'),
            ('module', ROOT / 'modules' / name / 'res'),
        ):
            if resource_dir.is_dir():
                archive = generated / (resource_name + '-resources.zip')
                run([tools['aapt2'], 'compile', '--dir', resource_dir, '-o', archive])
                resources.append(archive)
        unsigned = generated / 'unsigned.apk'
        launcher = ROOT / 'launchers' / name
        run([tools['aapt2'], 'link', '-o', unsigned, '-I', tools['android'],
             '--manifest', launcher / 'AndroidManifest.xml', '--java', generated / 'r',
             '--min-sdk-version', '26', '--target-sdk-version', '35', *resources])
        sources = []
        for directory in (ROOT / 'modules' / name, launcher / 'java', ROOT / 'shared/java', generated / 'r'):
            sources.extend(sorted(directory.rglob('*.java')))
        run([tools['javac'], '-encoding', 'UTF-8', '--release', '8', '-classpath', tools['android'],
             '-d', generated / 'classes', *sources])
        run([tools['java'], '-cp', tools['d8'], 'com.android.tools.r8.D8', '--lib', tools['android'],
             '--min-api', '26', '--output', generated / 'dex', *sorted((generated / 'classes').rglob('*.class'))])
        with zipfile.ZipFile(unsigned, 'a', zipfile.ZIP_DEFLATED) as archive:
            for file in sorted((generated / 'dex').glob('*.dex')):
                archive.write(file, file.name)
            assets = ROOT / 'modules' / name / 'assets'
            if assets.is_dir():
                for file in sorted(assets.rglob('*')):
                    if file.is_file():
                        archive.write(file, 'assets/' + file.relative_to(assets).as_posix())
        aligned = generated / 'aligned.apk'
        run([tools['zipalign'], '-f', '-P', '16', '4', unsigned, aligned])
        keystore, alias, store_password, key_password = signing
        apk = output / f'{APPS[name]}-{version}.apk'
        environment = os.environ.copy()
        environment['DAIDAI_TEST_APPS_STORE_PASSWORD'] = store_password
        environment['DAIDAI_TEST_APPS_KEY_PASSWORD'] = key_password
        run([tools['java'], '-jar', tools['apksigner'], 'sign', '--ks', keystore,
             '--ks-key-alias', alias, '--ks-pass', 'env:DAIDAI_TEST_APPS_STORE_PASSWORD',
             '--key-pass', 'env:DAIDAI_TEST_APPS_KEY_PASSWORD', '--out', apk, aligned], env=environment)
        run([tools['java'], '-jar', tools['apksigner'], 'verify', apk])
        return {'app': name, 'version': version, 'file': apk.name,
                'bytes': apk.stat().st_size, 'sha256': hashlib.sha256(apk.read_bytes()).hexdigest()}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('app', choices=[*APPS, 'all'], nargs='?', default='all')
    parser.add_argument('--sdk', help='Android SDK directory; defaults to ANDROID_SDK_ROOT or ANDROID_HOME')
    parser.add_argument('--out-dir', type=Path, default=ROOT / 'build', help='Output directory')
    parser.add_argument('--keystore', type=Path, help='Existing signing keystore; otherwise create an output-local development key')
    parser.add_argument('--ks-alias', help='Alias in the supplied keystore; default: test-apps')
    parser.add_argument('--build-tools', default='35.0.0')
    parser.add_argument('--platform', default='android-35')
    args = parser.parse_args()
    sdk = args.sdk or os.environ.get('ANDROID_SDK_ROOT') or os.environ.get('ANDROID_HOME')
    if not sdk:
        parser.error('Provide --sdk or set ANDROID_SDK_ROOT / ANDROID_HOME.')
    output = args.out_dir.expanduser().resolve()
    output.mkdir(parents=True, exist_ok=True)
    tools = resolve_tools(Path(sdk).expanduser().resolve(), args.build_tools, args.platform)
    signing = signing_key(args, tools, output)
    result = [build(name, tools, output, signing) for name in (APPS if args.app == 'all' else [args.app])]
    for record in result:
        (output / (record['app'] + '-build.json')).write_text(json.dumps(record, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(result, indent=2))


if __name__ == '__main__':
    main()
