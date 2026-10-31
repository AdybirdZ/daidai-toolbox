"""Prevent mismatched install metadata and stale current-version labels before building."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parent.parent
namespace = '{http://schemas.android.com/apk/res/android}'
manifest = ET.parse(root / 'app/src/main/AndroidManifest.xml').getroot()
gradle = (root / 'app/build.gradle').read_text(encoding='utf-8')
name = manifest.attrib[namespace + 'versionName']
code = manifest.attrib[namespace + 'versionCode']
assert re.search(r"versionName\s+['\"]" + re.escape(name) + r"['\"]", gradle), 'Manifest/Gradle versionName mismatch'
assert re.search(r'versionCode\s+' + code + r'\b', gradle), 'Manifest/Gradle versionCode mismatch'
main = (root / 'app/src/main/java/org/simpleweather/app/MainActivity.java').read_text(encoding='utf-8')
settings = (root / 'app/src/main/java/org/simpleweather/app/SettingsScreen.java').read_text(encoding='utf-8')
assert not re.search(r'呆呆工具箱\s*\d+\.\d+\.\d+', main + settings), 'Current app/version labels must use package metadata'
assert not re.search(r'about_title\)\s*\+\s*"[^"\n]*\d+\.\d+', main), 'Weather footer version must use package metadata'
for resources in (root / 'app/src/main/res').glob('values*/strings.xml'):
    for string in ET.parse(resources).getroot():
        if string.attrib.get('name') == 'about_version':
            assert '%1$s' in ''.join(string.itertext()), 'About version must be a resource format placeholder'
assert 'versionName()' in settings and 'versionName()' in main, 'Version must be read at runtime'
print(f'Release metadata: {name} / {code}; current version labels use package metadata')

import subprocess,sys
subprocess.run([sys.executable,str(root / "tests/resource_checks.py")],check=True)
