"""Build a dependency-free Android APK using Google's SDK and a JDK (no Gradle download).

Usage: python build.py --sdk /path/to/android-sdk --out /path/to/SimpleWeather-1.0.0.apk
Requires SDK platform android-35, build-tools 35.0.0, JDK 17+.
Creates a LOCAL DEVELOPMENT signing key in .signing/, which must be kept for updates.
"""
import argparse
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import sys
import zipfile
import json
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent
BALL_RACE_MODULE_VERSION_CODE = 11


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
    parser.add_argument('--build-dir', help='Directory for generated build intermediates.')
    parser.add_argument('--ball-race-test-host', action='store_true',
                        help='Build the isolated local ball-race test host and its signed ddapp.')
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
    build = Path(opts.build_dir).resolve() if opts.build_dir else ROOT / 'build'
    build.mkdir(exist_ok=True)
    # All cleanup stays inside this project's generated build/ directory.
    for name in ['classes', 'dex', 'generated']:
        directory = build / name
        if directory.exists():
            shutil.rmtree(directory)
        directory.mkdir()
    source = ROOT / 'app/src/main'
    overlay_zip = None
    if opts.ball_race_test_host:
        overlay_zip = build / 'test-host-resources.zip'
        run([tools / ('aapt2' + ext), 'compile', '--dir',
             ROOT / 'modules/prototypes/ball-race/test-host/res', '-o', overlay_zip])
        compiled_manifest = build / 'test-host-manifest.xml'
        ET.register_namespace('android', 'http://schemas.android.com/apk/res/android')
        manifest_root = ET.parse(source / 'AndroidManifest.xml').getroot()
        manifest_root.set('package', 'org.simpleweather.testhost')
        queries = ET.Element('queries')
        ET.SubElement(queries, 'package', {
            '{http://schemas.android.com/apk/res/android}name': 'org.simpleweather.app'
        })
        application = next(element for element in manifest_root if element.tag == 'application')
        manifest_root.insert(list(manifest_root).index(application), queries)
        for element in manifest_root.iter():
            name = element.get('{http://schemas.android.com/apk/res/android}name')
            if name and name.startswith('.'):
                element.set('{http://schemas.android.com/apk/res/android}name', 'org.daidai.toolbox' + name)
        ET.ElementTree(manifest_root).write(compiled_manifest, encoding='utf-8', xml_declaration=True)
        resource_manifest = compiled_manifest
        resource_package = 'org.daidai.toolbox'
    else:
        resource_manifest = source / 'AndroidManifest.xml'
        resource_package = 'org.daidai.toolbox'
    run([tools / ('aapt2' + ext), 'compile', '--dir', source / 'res', '-o', build / 'resources.zip'])
    unsigned = build / 'unsigned.apk'
    stable_ids=ROOT/'modules/stable-resource-ids.txt'
    link_stable_ids=stable_ids
    if opts.ball_race_test_host and stable_ids.exists():
        link_stable_ids=build/'test-host-stable-resource-ids.txt'
        value=stable_ids.read_text(encoding='utf-8').replace(
            'org.daidai.toolbox:', 'org.simpleweather.testhost:')
        link_stable_ids.write_text(value, encoding='utf-8')
    full_ids=build/'full-resource-ids.txt'
    stable_args=['--stable-ids',link_stable_ids] if link_stable_ids.exists() else []
    java_package_args = ['--custom-package', resource_package] if resource_package else []
    resource_package_args = []
    overlay_args = ['--auto-add-overlay', '-R', overlay_zip] if overlay_zip else []
    run([tools / ('aapt2' + ext), 'link', '-o', unsigned, '-I', platform,
         '--manifest', resource_manifest, '--java', build / 'generated', *java_package_args,
         '-A', source / 'assets', '--min-sdk-version', '26', '--target-sdk-version', '35',
         '--emit-ids',full_ids,*stable_args,*resource_package_args,build / 'resources.zip', *overlay_args])
    if stable_ids.exists() and not opts.ball_race_test_host:
        existing_lines=stable_ids.read_text('utf8').splitlines()
        before=dict(line.split(' = ') for line in existing_lines if ' = ' in line)
        full_lines=full_ids.read_text('utf8').splitlines()
        after=dict(line.split(' = ') for line in full_lines if ' = ' in line)
        assert all(after.get(key)==value for key,value in before.items()), 'Host API 1 resource IDs must not be removed or changed'
        appended=[line for line in full_lines if ' = ' in line and line.split(' = ',1)[0] not in before]
        if appended:
            stable_ids.write_text('\n'.join(existing_lines+appended)+'\n','utf8')
    if opts.ball_race_test_host and link_stable_ids.exists():
        existing=dict(line.split(' = ') for line in link_stable_ids.read_text('utf8').splitlines() if ' = ' in line)
        for line in full_ids.read_text('utf8').splitlines():
            if ' = ' in line:
                key,value=line.split(' = ',1)
                existing.setdefault(key,value)
        link_stable_ids.write_text(''.join(key+' = '+value+'\n' for key,value in existing.items()),'utf8')
    # The complete public resource ID map is append-only within host API 1.
    if not opts.ball_race_test_host and not stable_ids.exists():
        shutil.copyfile(full_ids,stable_ids)
    sys.path.insert(0,str(ROOT/'modules'))
    from resource_partition import partition
    parts=partition(ROOT, build)
    run([tools/('aapt2'+ext),'compile','--dir',parts/'host','-o',build/'host-resources.zip'])
    from build_executable import EMBEDDED, IDS
    app_sources = [path for app_id in IDS if app_id not in EMBEDDED
                   for path in (ROOT / 'modules/apps' / app_id).rglob('*.java')]
    embedded_sources = [path for app_id in (*sorted(EMBEDDED), 'shared')
                        for path in (ROOT / 'modules/embedded' / app_id).rglob('*.java')]
    if opts.ball_race_test_host:
        embedded_sources += list((ROOT / 'modules/prototypes/ball-race/app').rglob('*.java'))
    sources = (list((source / 'java').rglob('*.java')) + app_sources + embedded_sources
               + list((build / 'generated').rglob('*.java')))
    if opts.ball_race_test_host:
        sources = [path for path in sources if path.name != 'HostBuildProfile.java']
        profile_source = build / 'generated-profile/org/daidai/toolbox/HostBuildProfile.java'
        profile_source.parent.mkdir(parents=True, exist_ok=True)
        profile_source.write_text(
            'package org.daidai.toolbox;\n'
            '/** 本机小球竞赛测试宿主的构建配置。 */\n'
            'final class HostBuildProfile {\n'
            '    static final boolean BALL_RACE_TEST_HOST = true;\n'
            '    private HostBuildProfile() {}\n'
            '}\n', encoding='utf-8')
        sources.append(profile_source)
    run([javac, '-encoding', 'UTF-8', '--release', '8', '-classpath', platform, '-d', build / 'classes', *sources])
    contract=ROOT/'modules/host-java-api.json'
    exported={}
    javap=java_bin/('javap'+ext)
    host_names={p.stem for p in (source/'java/org/daidai/toolbox').glob('*.java')}
    for path in sorted((build/'classes/org/daidai/toolbox').glob('*.class')):
        if path.stem.split('$')[0] not in host_names:continue
        if path.stem=='R' or path.stem.startswith('R$'):continue
        name='org.daidai.toolbox.'+path.stem
        output=subprocess.check_output([str(javap),'-public','-classpath',str(build/'classes'),name],text=True,encoding='utf8')
        # Synchronization changes method implementation, not its JVM linkage contract.
        lines=[line.strip().replace(' synchronized ',' ') for line in output.splitlines() if line.strip().startswith(('public ','protected '))]
        if lines and any(re.match(r'public .*\b(?:class|interface|enum)\b', line) for line in lines):exported[name]=lines
    previous=json.loads(contract.read_text('utf8')) if contract.exists() else {}
    previous={name:[line.replace(' synchronized ',' ') for line in lines] for name,lines in previous.items()}
    for name,lines in previous.items():
        current_name=name.replace('org.simpleweather.app.','org.daidai.toolbox.')
        current_lines=[line.replace('org.simpleweather.app.','org.daidai.toolbox.') for line in lines]
        assert set(current_lines)<=set(exported.get(current_name,[])), 'Host API signature changed: '+name
    if not opts.ball_race_test_host:
        contract.write_text(json.dumps(exported,indent=2)+'\n','utf8')
    signing = ROOT / '.signing'
    keystore = signing / 'development.jks'
    if opts.ball_race_test_host and not keystore.is_file():
        parser.error('The local development signing key must already exist in .signing/development.jks.')
    if not opts.ball_race_test_host:
        signing.mkdir(exist_ok=True)
    # Public development password; this key is for private sideloading, not a store release.
    if not keystore.exists():
        run([java_bin / ('keytool' + ext), '-genkeypair', '-keystore', keystore, '-alias', 'simpleweather',
             '-storepass', 'android', '-keypass', 'android', '-keyalg', 'RSA', '-keysize', '3072',
             '-validity', '10000', '-dname', 'CN=SimpleWeather Local Development', '-noprompt'])
    # Build executable app packages first; their classes must not also enter host DEX.
    sys.path.insert(0, str(ROOT / 'modules'))
    from build_executable import build as build_apps
    if opts.ball_race_test_host:
        app_classes = build_ball_race_package(ROOT, sdk, java, tools, platform, keystore, build, run)
    else:
        app_classes = build_apps(ROOT, sdk, java, run, build)
    # Refresh packaged assets after the default signed apps and catalog are generated.
    host_assets=build/'host-assets'
    shutil.copytree(source/'assets',host_assets,dirs_exist_ok=True)
    (host_assets/'cities.json').unlink(missing_ok=True)
    host_assets = build / 'host-assets'
    shutil.copytree(source / 'assets', host_assets, dirs_exist_ok=True)
    (host_assets / 'cities.json').unlink(missing_ok=True)
    if opts.ball_race_test_host:
        for package in (host_assets / 'default-apps').glob('*.ddapp'):
            package.unlink()
        shutil.copyfile(build / 'ballrace-module/ballrace.ddapp',
                        host_assets / 'default-apps/ballrace.ddapp')
        catalog = {
            'format': 'daidai-app-store', 'schemaVersion': 1, 'hostApi': 'daidai-toolbox/api-2',
            'defaultInstalled': ['ballrace'],
            'apps': [{'id': 'ballrace', 'type': 'signed-dex-app', 'version': '3.0.5',
                      'versionCode': BALL_RACE_MODULE_VERSION_CODE, 'hostApi': 'daidai-toolbox/api-2', 'minHostVersionCode': 67,
                      'minSdk': 26, 'permissions': [],
                      'description': {'zh': '小球竞速本地测试。', 'en': 'Local ball-race testing.'},
                      'releaseNotes': {'zh': '本地测试包。', 'en': 'Local test package.'},
                      'bytes': (build / 'ballrace-module/ballrace.ddapp').stat().st_size,
                      'sha256': hashlib.sha256((build / 'ballrace-module/ballrace.ddapp').read_bytes()).hexdigest(),
                      'url': ''}]
        }
        (host_assets / 'app-catalog.json').write_text(json.dumps(catalog, ensure_ascii=False), 'utf8')
    run([tools / ('aapt2' + ext), 'link', '-o', unsigned, '-I', platform,
         '--manifest', resource_manifest, '--java', build / 'generated', *java_package_args,
         '-A', host_assets, '--min-sdk-version', '26', '--target-sdk-version', '35',
         *(['--stable-ids', link_stable_ids] if link_stable_ids.exists() else []), *resource_package_args,
         build / 'host-resources.zip',
         *overlay_args])
    # Pass one filtered archive to D8. Passing every class path separately
    # exceeds Windows' command-line limit once all module packages are built.
    host_classes = build / 'host-classes.jar'
    with zipfile.ZipFile(host_classes, 'w', zipfile.ZIP_DEFLATED) as archive:
        for path in (build / 'classes').rglob('*.class'):
            if path not in app_classes:
                archive.write(path, path.relative_to(build / 'classes').as_posix())
    run([java, '-cp', tools / 'lib/d8.jar', 'com.android.tools.r8.D8', '--lib', platform,
         '--min-api', '26', '--output', build / 'dex', host_classes])
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
    if opts.ball_race_test_host:
        package_output = output.parent / 'BallRace.ddapp'
        package_output.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(build / 'ballrace-module/ballrace.ddapp', package_output)
        print('Ball race app:', package_output)
    print('APK:', output)


def build_ball_race_package(root, sdk, java, tools, platform, keystore, build, run):
    import hashlib
    import sys
    import json
    import zipfile

    root = Path(root)
    module = root / 'modules/prototypes/ball-race'
    package_build = build / 'ballrace-module'
    stage = package_build / 'staged'
    if package_build.exists():
        shutil.rmtree(package_build)
    (stage / 'assets').mkdir(parents=True)
    bundled_levels = module / 'app/assets'
    if bundled_levels.exists():
        shutil.copytree(bundled_levels, stage / 'assets', dirs_exist_ok=True)
    classes = build / 'classes'
    jars = build / 'all-classes.jar'
    with zipfile.ZipFile(jars, 'w') as archive:
        for path in classes.rglob('*.class'):
            archive.write(path, path.relative_to(classes).as_posix())
    package_classes = list((classes / 'org/daidai/modules/ballrace').rglob('*.class'))
    package_classes.extend((classes / 'org/daidai/modules/shared').rglob('*.class'))
    dex = stage / 'dex'
    dex.mkdir()
    run([java, '-cp', tools / 'lib/d8.jar', 'com.android.tools.r8.D8', '--lib', platform,
         '--classpath', jars, '--min-api', '26', '--output', dex, *package_classes])
    manifest = stage / 'AndroidManifest.xml'
    manifest.write_text(
        '<manifest xmlns:android="http://schemas.android.com/apk/res/android" '
        f'package="org.simpleweather.module.ballrace" android:versionCode="{BALL_RACE_MODULE_VERSION_CODE}" '
        'android:versionName="3.0.5"><uses-sdk android:minSdkVersion="26" '
        'android:targetSdkVersion="35"/><application android:hasCode="true" '
        'android:allowBackup="false"/></manifest>', encoding='utf-8')
    descriptor = {
        'format': 'signed-dex-app', 'schemaVersion': 1, 'id': 'ballrace',
        'entry': 'org.daidai.modules.ballrace.BallRaceActivity',
        'hostApi': 'daidai-toolbox/api-2', 'minHostVersionCode': 67, 'minSdk': 26,
        'versionCode': BALL_RACE_MODULE_VERSION_CODE, 'version': '3.0.5', 'standaloneInstallable': False,
        'resourceProvider': 'application', 'dataRetention': 'retain-on-uninstall',
        'configurationGroups': ['ballrace_module'],
        'configurationContract': {'format': 'daidai-toolbox-module-config', 'version': 1,
                                  'toolboxKeyRules': []},
        'permissions': [], 'hostComponentBridges': [],
        'dependencies': ['module-page-worker-api'],
        'pages': {'main': 'org.daidai.modules.ballrace.BallRaceActivity',
                  'settings': 'org.daidai.modules.ballrace.BallRaceActivity'}
    }
    (stage / 'assets/module.json').write_text(json.dumps(descriptor, ensure_ascii=False, indent=2), 'utf8')
    resources = []
    for name, directory in [('shared', root / 'modules/embedded/shared/res'),
                             ('application', module / 'app/res')]:
        output = stage / (name + '-resources.zip')
        run([tools / ('aapt2.exe' if os.name == 'nt' else 'aapt2'), 'compile', '--dir', directory, '-o', output])
        resources.append(output)
    unsigned = stage / 'unsigned.apk'
    run([tools / ('aapt2.exe' if os.name == 'nt' else 'aapt2'), 'link', '-o', unsigned,
         '-I', platform, '--manifest', manifest, '--java', build / 'module-r',
         '-A', stage / 'assets', '--min-sdk-version', '26', '--target-sdk-version', '35', *resources])
    with zipfile.ZipFile(unsigned, 'a', zipfile.ZIP_DEFLATED) as archive:
        for path in dex.glob('*.dex'):
            archive.write(path, path.name)
    aligned = stage / 'aligned.apk'
    ext = '.exe' if os.name == 'nt' else ''
    run([tools / ('zipalign' + ext), '-f', '-P', '16', '4', unsigned, aligned])
    output = package_build / 'ballrace.ddapp'
    run([java, '-jar', tools / 'lib/apksigner.jar', 'sign', '--v4-signing-enabled', 'false',
         '--ks', keystore, '--ks-key-alias', 'simpleweather', '--ks-pass', 'pass:android',
         '--key-pass', 'pass:android', '--out', output, aligned])
    run([java, '-jar', tools / 'lib/apksigner.jar', 'verify', output])

    executable_classes = set(package_classes)
    for app_id in ('music', 'messaging', 'colorvision'):
        executable_classes.update((classes / 'org/daidai/modules' / app_id).rglob('*.class'))
        executable_classes.update((classes / 'org/daidai/modules/shared').rglob('*.class'))
    for app_id in ('weather', 'schedule', 'status', 'weather-map', 'astronomy', 'earthquake', 'random', 'todo'):
        java_names = {path.stem for path in (root / 'modules/apps' / app_id / 'java').rglob('*.java')}
        executable_classes.update(path for path in (classes / 'org/daidai/toolbox').glob('*.class')
                                  if path.stem.split('$')[0] in java_names)
    return executable_classes


if __name__ == '__main__':
    main()
