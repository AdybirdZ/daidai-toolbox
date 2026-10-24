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
    run([tools / ('aapt2' + ext), 'link', '-o', unsigned, '-I', platform,
         '--manifest', source / 'AndroidManifest.xml', '--java', build / 'generated',
         '-A', source / 'assets', '--min-sdk-version', '26', '--target-sdk-version', '35',
         build / 'resources.zip'])
    sources = list((source / 'java').rglob('*.java')) + list((build / 'generated').rglob('*.java'))
    run([javac, '-encoding', 'UTF-8', '--release', '8', '-classpath', platform, '-d', build / 'classes', *sources])
    run([java, '-cp', tools / 'lib/d8.jar', 'com.android.tools.r8.D8', '--lib', platform,
         '--min-api', '26', '--output', build / 'dex', *list((build / 'classes').rglob('*.class'))])
    with zipfile.ZipFile(unsigned, 'a', zipfile.ZIP_DEFLATED) as apk:
        for dex in (build / 'dex').glob('*.dex'):
            apk.write(dex, dex.name)
    aligned = build / 'aligned.apk'
    run([tools / ('zipalign' + ext), '-f', '-P', '16', '4', unsigned, aligned])
    signing = ROOT / '.signing'
    signing.mkdir(exist_ok=True)
    keystore = signing / 'development.jks'
    # Public development password; this key is for private sideloading, not a store release.
    if not keystore.exists():
        run([java_bin / ('keytool' + ext), '-genkeypair', '-keystore', keystore, '-alias', 'simpleweather',
             '-storepass', 'android', '-keypass', 'android', '-keyalg', 'RSA', '-keysize', '3072',
             '-validity', '10000', '-dname', 'CN=SimpleWeather Local Development', '-noprompt'])
    output = Path(opts.out).resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    signer = [java, '-jar', tools / 'lib/apksigner.jar']
    run([*signer, 'sign', '--ks', keystore, '--ks-key-alias', 'simpleweather',
         '--ks-pass', 'pass:android', '--key-pass', 'pass:android', '--out', output, aligned])
    run([*signer, 'verify', '--verbose', '--print-certs', output])
    print('APK:', output)


if __name__ == '__main__':
    main()
