"""Run signed Android UI regressions on an already running, isolated emulator."""
import argparse,os,shutil,subprocess,zipfile
from pathlib import Path
root=Path(__file__).resolve().parents[2]
workspace=root.parent
parser=argparse.ArgumentParser();parser.add_argument('--serial',default='emulator-5580');parser.add_argument('--suite',choices=['StatusUiTests','ConfigurationUiTests','Release1212UiTests','Release1213UiTests','EnglishPreviewUiTests'],default='StatusUiTests');opts=parser.parse_args()
if not opts.serial.startswith('emulator-'):raise SystemExit('Use an isolated Android emulator, not a personal device.')
sdk=workspace/'tools/android-sdk';tools=sdk/'build-tools/35.0.0';platform=sdk/'platforms/android-35/android.jar'
java_dir=Path(shutil.which('javac')).parent
out=workspace/('work/qa/android-'+opts.suite);out.mkdir(parents=True,exist_ok=True)
(out/'classes').mkdir(exist_ok=True);(out/'dex').mkdir(exist_ok=True)
def run(args,**kwargs):
 command=[os.path.relpath(x,workspace) if i>0 and isinstance(x,Path) else str(x) for i,x in enumerate(args)]
 return subprocess.run(command,cwd=workspace,check=True,**kwargs)
manifest=out/'AndroidManifest.xml'
manifest.write_text('''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="org.simpleweather.qa" android:versionCode="1"><uses-sdk android:minSdkVersion="26" android:targetSdkVersion="35"/><application android:label="Status regression tests"/><instrumentation android:name="org.simpleweather.qa.StatusUiTests" android:targetPackage="org.simpleweather.app"/></manifest>'''.replace('StatusUiTests',opts.suite),encoding='utf-8')
unsigned=out/'unsigned.apk';aligned=out/'aligned.apk';apk=out/'tests.apk'
run([tools/'aapt2.exe','link','-o',unsigned,'-I',platform,'--manifest',manifest])
run([java_dir/'javac.exe','-encoding','UTF-8','--release','8','-cp',platform,'-d',out/'classes',root/('tests/android/'+opts.suite+'.java')])
run([java_dir/'java.exe','-cp',tools/'lib/d8.jar','com.android.tools.r8.D8','--lib',platform,'--min-api','26','--output',out/'dex',*list((out/'classes').rglob('*.class'))])
with zipfile.ZipFile(unsigned,'a',zipfile.ZIP_DEFLATED) as z:
 for p in (out/'dex').glob('*.dex'):z.write(p,p.name)
run([tools/'zipalign.exe','-f','4',unsigned,aligned])
run([java_dir/'java.exe','-jar',tools/'lib/apksigner.jar','sign','--ks',root/'.signing/development.jks','--ks-key-alias','simpleweather','--ks-pass','pass:android','--key-pass','pass:android','--out',apk,aligned])
adb=sdk/'platform-tools/adb.exe'
run([adb,'-s',opts.serial,'install','-r',apk])
result=run([adb,'-s',opts.serial,'shell','am','instrument','-w','org.simpleweather.qa/org.simpleweather.qa.'+opts.suite],capture_output=True,text=True,encoding='utf-8')
print(result.stdout)
if 'PASS:' not in result.stdout or 'FAIL:' in result.stdout:raise SystemExit('Native regression checks failed')
