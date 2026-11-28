"""Prevent mismatched install metadata and stale current-version labels before building."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parent.parent
namespace = '{http://schemas.android.com/apk/res/android}'
manifest = ET.parse(root / 'app/src/main/AndroidManifest.xml').getroot()
gradle = (root / 'app/build.gradle').read_text(encoding='utf-8')
assert manifest.attrib.get('package') == 'org.simpleweather.app', 'Android install identity must remain stable'
assert re.search(r"namespace\s+['\"]org\.daidai\.toolbox['\"]", gradle), 'Source namespace must use org.daidai.toolbox'
assert re.search(r"applicationId\s+['\"]org\.simpleweather\.app['\"]", gradle), 'applicationId must remain upgrade-compatible'
name = manifest.attrib[namespace + 'versionName']
code = manifest.attrib[namespace + 'versionCode']
assert re.search(r"versionName\s+['\"]" + re.escape(name) + r"['\"]", gradle), 'Manifest/Gradle versionName mismatch'
assert re.search(r'versionCode\s+' + code + r'\b', gradle), 'Manifest/Gradle versionCode mismatch'
main = (root / 'app/src/main/java/org/daidai/toolbox/MainActivity.java').read_text(encoding='utf-8')
settings = (root / 'app/src/main/java/org/daidai/toolbox/SettingsScreen.java').read_text(encoding='utf-8')
assert not re.search(r'呆呆工具箱\s*\d+\.\d+\.\d+', main + settings), 'Current app/version labels must use package metadata'
assert not re.search(r'about_title\)\s*\+\s*"[^"\n]*\d+\.\d+', main), 'Weather footer version must use package metadata'
for resources in (root / 'app/src/main/res').glob('values*/strings.xml'):
    for string in ET.parse(resources).getroot():
        if string.attrib.get('name') == 'about_version':
            assert '%1$s' in ''.join(string.itertext()), 'About version must be a resource format placeholder'
assert 'versionName()' in settings and 'versionName()' in main, 'Version must be read at runtime'
assert (root / 'app/src/main/java/org/daidai/toolbox/MainActivity.java').is_file(), 'Host sources must live under the new namespace path'
assert not (root / 'app/src/main/java/org/simpleweather/app').exists(), 'Old host source path must be removed'
aliases = [node for node in manifest.find('application').findall('activity-alias')
           if node.attrib.get(namespace + 'name') == 'org.simpleweather.app.MainActivity']
assert len(aliases) == 1 and aliases[0].attrib.get(namespace + 'targetActivity') == 'org.daidai.toolbox.MainActivity', 'Old pinned shortcuts must resolve through the activity alias'
receivers = [node for node in manifest.find('application').findall('receiver')
             if node.attrib.get(namespace + 'name') == 'org.simpleweather.app.TodoReceiver']
assert len(receivers) == 1 and receivers[0].attrib.get(namespace + 'exported') == 'false', 'Old notification and alarm PendingIntents must resolve to a private compatibility receiver'
legacy_receiver = (root / 'app/src/main/java/legacy/TodoReceiver.java').read_text(encoding='utf-8')
assert 'package org.simpleweather.app;' in legacy_receiver and 'new org.daidai.toolbox.TodoReceiver().onReceive(context, intent)' in legacy_receiver, 'Legacy receiver must forward the original action and extras to the new implementation'
module_builder = (root / 'modules/build_executable.py').read_text(encoding='utf-8')
assert "shutil.copyfile(package,migrations/package.name)" in module_builder, 'Every built app must be available as a one-time API migration asset'
modules = (root / 'app/src/main/java/org/daidai/toolbox/AppModules.java').read_text(encoding='utf-8')
assert 'for(String id:allIds())' in modules and 'existing.isFile()&&(repair310||!present(a,id))' in modules, 'API migration must repair invalid packages without reinstalling uninstalled apps'
schedule_notifier = (root / 'modules/apps/schedule/java/org/daidai/toolbox/ScheduleNotifierImpl.java').read_text(encoding='utf-8')
assert 'text(c,"\\u8bfe\\u7a0b\\u63d0\\u793a"' in schedule_notifier and 'text(c,"\\u5c06\\u4e8e %1$s \\u5f00\\u59cb"' in schedule_notifier, 'Schedule notifications must avoid stale resource IDs across the API namespace migration'
print(f'Release metadata: {name} / {code}; current version labels use package metadata')

import subprocess,sys
subprocess.run([sys.executable,str(root / "tests/resource_checks.py")],check=True)
subprocess.run([sys.executable,str(root / "tests/internal_resource_checks.py")],check=True)

for client in ("WeatherApi.java","EarthquakeApi.java"):
    text=next(root.glob('modules/apps/*/java/org/daidai/toolbox/'+client)).read_text(encoding="utf8")
    assert "DaidaiToolbox/"+name+" " in text, "HTTP client version mismatch: "+client
host_updates=(root/'app/src/main/java/org/daidai/toolbox/HostUpdateManager.java').read_text(encoding='utf8')
assert 'DaidaiToolbox/'+name+' Android' in host_updates, 'Host update HTTP client version mismatch'
assert 'parseLatestRelease' in host_updates and 'expectedUrl.equals(url)' in host_updates, 'Host updates must validate the official versioned release asset URL'
assert 'parseReleaseManifest' in host_updates and 'RELEASE_MANIFEST' in host_updates, 'Host updates must support the API-independent public manifest'
assert 'raw.githubusercontent.com' in host_updates, 'Host update manifest must be fetched from the official raw repository origin'
