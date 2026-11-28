"""Run core behavior tests without Android. JSON-java is a test-only dependency."""
import argparse
import os
from pathlib import Path
import shutil
import subprocess
import sys

root = Path(__file__).resolve().parent.parent
subprocess.run([sys.executable, str(root / 'tests/release_checks.py')], check=True)
p = argparse.ArgumentParser()
p.add_argument('--json-jar', required=True, help='JSON-java 20240303 jar; not included in the APK')
opts = p.parse_args()
subprocess.run([sys.executable, str(root / 'tests/music_import_checks.py')], check=True)
subprocess.run([sys.executable, str(root / 'modules/prototypes/ball-race/tests/run.py')], check=True)
subprocess.run([sys.executable, str(root / 'tests/launcher_layout_checks.py'), '--json-jar', opts.json_jar], check=True)
jar = Path(opts.json_jar).resolve()
output = root / 'build/tests'
output.mkdir(parents=True, exist_ok=True)
package = root / 'app/src/main/java/org/daidai/toolbox'
sources = [next(iter(root.glob('modules/apps/*/java/org/daidai/toolbox/'+name+'.java')), package / (name + '.java')) for name in ['WeatherClient', 'LauncherLayout', 'TransientText', 'TodoModel', 'RandomChoiceModel', 'UiText', 'LanguagePolicy', 'BatteryReadings', 'CameraExposure', 'CameraLightSamples', 'City', 'WeatherData', 'WeatherApi', 'ScheduleModel', 'Astronomy', 'SkyGeometry', 'SkyLabels', 'SkyVisibility', 'DisplayOrder', 'ForecastCache', 'EarthquakeData', 'EarthquakeApi', 'EarthquakeCache', 'EarthquakeRestore', 'EarthquakeIntensity', 'EarthquakeReferenceIntensity', 'EarthquakeOptions', 'EarthquakeDisplay', 'EarthquakeAgencies', 'CoordinateFormat', 'StatusMath', 'StatusPlace', 'RoutePlayback', 'RouteViewport', 'StatusTransferData', 'ConfigurationData', 'ThemeColors', 'SkyPointing']]
subprocess.run([shutil.which('javac'), '-encoding', 'UTF-8', '--release', '8', '-cp', str(jar), '-d', str(output),
                *map(str, sources), str(root / 'modules/apps/status/java/org/daidai/toolbox/StatusRefreshPolicy.java'), str(root / 'modules/apps/status/java/org/daidai/toolbox/StatusSatelliteStore.java'), str(root / 'tests/TransientTextTests.java'), str(root / 'tests/TextTestLauncher.java'), str(root / 'tests/EnglishModelTests.java'), str(root / 'tests/LanguageTests.java'), str(root / 'tests/CoreTests.java'), str(root / 'tests/ToolboxTests.java'), str(root / 'tests/EarthquakeTests.java'), str(root / 'tests/StatusTests.java'), str(root / 'tests/RoutePlaybackTests.java'), str(root / 'tests/StatusUpdateTests.java'), str(root / 'tests/StatusTransferTests.java'), str(root / 'tests/StatusRefreshPolicyTests.java'), str(root / 'tests/ConfigurationTests.java'), str(root / 'tests/Release1211Tests.java'), str(root / 'tests/CameraLightTests.java'), str(root / 'tests/RandomChoiceTests.java'), str(root / 'tests/TodoTests.java')], check=True)
subprocess.run([shutil.which('java'), '-cp', str(output) + os.pathsep + str(jar), 'org.daidai.toolbox.TextTestLauncher', str(root / 'app/src/main/res/values'), 'org.daidai.toolbox.CoreTests',
                str(root / 'tests/fixtures/beijing.json'), str(root / 'app/src/main/assets/cities.json')], check=True)
subprocess.run([shutil.which('java'), '-cp', str(output) + os.pathsep + str(jar), 'org.daidai.toolbox.TextTestLauncher', str(root / 'app/src/main/res/values'), 'org.daidai.toolbox.ToolboxTests'], check=True)

subprocess.run([shutil.which('java'), '-cp', str(output) + os.pathsep + str(jar), 'org.daidai.toolbox.TextTestLauncher', str(root / 'app/src/main/res/values'), 'org.daidai.toolbox.EarthquakeTests', str(root / 'tests/fixtures')], check=True)

subprocess.run([shutil.which('java'), '-cp', str(output) + os.pathsep + str(jar), 'org.daidai.toolbox.TextTestLauncher', str(root / 'app/src/main/res/values'), 'org.daidai.toolbox.StatusTests'], check=True)
subprocess.run([shutil.which('java'), '-cp', str(output) + os.pathsep + str(jar), 'org.daidai.toolbox.TextTestLauncher', str(root / 'app/src/main/res/values'), 'org.daidai.toolbox.RoutePlaybackTests'], check=True)

subprocess.run([shutil.which('java'), '-cp', str(output) + os.pathsep + str(jar), 'org.daidai.toolbox.TextTestLauncher', str(root / 'app/src/main/res/values'), 'org.daidai.toolbox.StatusUpdateTests'], check=True)

subprocess.run([shutil.which('java'), '-cp', str(output) + os.pathsep + str(jar), 'org.daidai.toolbox.TextTestLauncher', str(root / 'app/src/main/res/values'), 'org.daidai.toolbox.StatusTransferTests'], check=True)

subprocess.run([shutil.which('java'), '-cp', str(output) + os.pathsep + str(jar), 'org.daidai.toolbox.StatusRefreshPolicyTests'], check=True)

subprocess.run([shutil.which('java'), '-cp', str(output) + os.pathsep + str(jar), 'org.daidai.toolbox.TextTestLauncher', str(root / 'app/src/main/res/values'), 'org.daidai.toolbox.ConfigurationTests'], check=True)

subprocess.run([shutil.which('java'), '-cp', str(output) + os.pathsep + str(jar), 'org.daidai.toolbox.TextTestLauncher', str(root / 'app/src/main/res/values'), 'org.daidai.toolbox.Release1211Tests'], check=True)

subprocess.run([shutil.which('java'), '-cp', str(output) + os.pathsep + str(jar), 'org.daidai.toolbox.CameraLightTests'], check=True)

subprocess.run([shutil.which('java'), '-cp', str(output) + os.pathsep + str(jar), 'org.daidai.toolbox.RandomChoiceTests'], check=True)

subprocess.run([shutil.which("java"), "-cp", str(output) + os.pathsep + str(jar), "org.daidai.toolbox.TextTestLauncher", str(root / "app/src/main/res/values"), "org.daidai.toolbox.LanguageTests"], check=True)

subprocess.run([shutil.which("java"), "-cp", str(output) + os.pathsep + str(jar), "org.daidai.toolbox.TextTestLauncher", str(root / "app/src/main/res/values") + os.pathsep + str(root / "app/src/main/res/values-en"), "org.daidai.toolbox.EnglishModelTests"], check=True)

subprocess.run([shutil.which("java"), "-cp", str(output)+os.pathsep+str(jar), "org.daidai.toolbox.TodoTests"], check=True)
subprocess.run([shutil.which("java"), "-cp", str(output)+os.pathsep+str(jar), "org.daidai.toolbox.TransientTextTests"], check=True)
