"""Run core behavior tests without Android. JSON-java is a test-only dependency."""
import argparse
import os
from pathlib import Path
import shutil
import subprocess

root = Path(__file__).resolve().parent.parent
p = argparse.ArgumentParser()
p.add_argument('--json-jar', required=True, help='JSON-java 20240303 jar; not included in the APK')
opts = p.parse_args()
jar = Path(opts.json_jar).resolve()
output = root / 'build/tests'
output.mkdir(parents=True, exist_ok=True)
package = root / 'app/src/main/java/org/simpleweather/app'
sources = [package / (name + '.java') for name in ['City', 'WeatherData', 'WeatherApi', 'ScheduleModel', 'Astronomy', 'SkyGeometry', 'SkyLabels', 'DisplayOrder', 'ForecastCache', 'EarthquakeData', 'EarthquakeApi', 'EarthquakeCache', 'EarthquakeIntensity', 'EarthquakeOptions']]
subprocess.run([shutil.which('javac'), '-encoding', 'UTF-8', '--release', '8', '-cp', str(jar), '-d', str(output),
                *map(str, sources), str(root / 'tests/CoreTests.java'), str(root / 'tests/ToolboxTests.java'), str(root / 'tests/EarthquakeTests.java')], check=True)
subprocess.run([shutil.which('java'), '-cp', str(output) + os.pathsep + str(jar), 'org.simpleweather.app.CoreTests',
                str(root / 'tests/fixtures/beijing.json'), str(root / 'app/src/main/assets/cities.json')], check=True)
subprocess.run([shutil.which('java'), '-cp', str(output) + os.pathsep + str(jar), 'org.simpleweather.app.ToolboxTests'], check=True)

subprocess.run([shutil.which('java'), '-cp', str(output) + os.pathsep + str(jar), 'org.simpleweather.app.EarthquakeTests', str(root / 'tests/fixtures')], check=True)
