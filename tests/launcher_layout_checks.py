"""Independent launcher-model behavior checks; the JSON jar is test-only."""
from pathlib import Path
import argparse, subprocess, shutil, os
root=Path(__file__).resolve().parent.parent
p=argparse.ArgumentParser();p.add_argument('--json-jar',required=True);args=p.parse_args()
jar=Path(args.json_jar).resolve();out=root/'build/launcher-tests';out.mkdir(parents=True,exist_ok=True)
subprocess.run([shutil.which('javac'),'-encoding','UTF-8','--release','8','-cp',str(jar),'-d',str(out),str(root/'app/src/main/java/org/daidai/toolbox/LauncherLayout.java'),str(root/'tests/LauncherLayoutTests.java')],check=True)
subprocess.run([shutil.which('java'),'-cp',str(out)+os.pathsep+str(jar),'org.daidai.toolbox.LauncherLayoutTests'],check=True)
