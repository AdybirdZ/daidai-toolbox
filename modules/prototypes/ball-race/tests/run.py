"""在原型目录之外的临时目录编译并运行纯 Java 小球模型测试。"""
import argparse
import os
from pathlib import Path
import shutil
import subprocess
import tempfile

workspace = Path(__file__).resolve().parents[4]
parser = argparse.ArgumentParser()
parser.add_argument('--json-jar', default=str(workspace.parent / 'tools/test-libs/json.jar'),
                    help='工作区测试用 JSON-Java jar')
arguments = parser.parse_args()
json_jar = Path(arguments.json_jar).resolve()
source = Path(__file__).resolve().parents[1] / 'src'
output = Path(tempfile.mkdtemp(prefix='ball-race-tests-'))
try:
    java_sources = sorted(source.rglob('*.java'))
    app_source = Path(__file__).resolve().parents[1] / 'app'
    app_tests = Path(__file__).resolve().parents[1] / 'app-test'
    for name in ('BallPhysics.java', 'RaceCourse.java', 'RaceProgress.java', 'EditableLevel.java', 'BallRoster.java'):
        java_sources.append(app_source / 'org/daidai/modules/ballrace' / name)
    java_sources.extend(sorted(app_tests.rglob('*.java')))
    subprocess.run([
        shutil.which('javac'), '-encoding', 'UTF-8', '--release', '8',
        '-classpath', str(json_jar), '-d', str(output),
        *map(str, java_sources)
    ], check=True)
    classpath = str(output) + os.pathsep + str(json_jar)
    test_classes = (
        ('org.daidai.prototypes.ballrace.BallRaceTests', None),
        ('org.daidai.prototypes.ballrace.CourseDocumentTests',
         Path(__file__).resolve().parents[1] / 'examples/vertical-course.json'),
        ('org.daidai.modules.ballrace.BallRaceTests', None),
        ('org.daidai.modules.ballrace.EditableLevelTests', None),
    )
    for test_class, argument in test_classes:
        command = [
            shutil.which('java'), '-classpath', classpath,
            test_class
        ]
        if argument is not None:
            command.append(str(argument))
        subprocess.run(command, check=True)
finally:
    shutil.rmtree(output, ignore_errors=True)
