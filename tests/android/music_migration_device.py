"""Historical pre-3.0.0-2 test; use its matching source checkout only."""
raise SystemExit("The standalone music migration feature was removed in 3.0.0. Run this historical test from the pre-3.0.0-2 checkout.")
from pathlib import Path
import shutil,subprocess,zipfile
ws=Path(__file__).resolve().parents[3];src=ws/'source';sdk=ws/'tools/android-sdk';bt=sdk/'build-tools/35.0.0';platform=sdk/'platforms/android-35/android.jar';jdk=Path(shutil.which('javac')).parent;out=ws/'work/qa/music-migration';out.mkdir(parents=True,exist_ok=True)
def run(args,**kwargs):return subprocess.run([str(a) for a in args],cwd=ws,check=True,**kwargs)
provider=(ws/'prototypes/tests/MusicMetadataDocuments.java').read_text('utf8').replace('org.daidai.prototype.qa.music.documents','org.simpleweather.migrationqa.documents')
(out/'MusicMetadataDocuments.java').write_text(provider,'utf8')
manifest=out/'AndroidManifest.xml';manifest.write_text('''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="org.simpleweather.migrationqa"><uses-sdk android:minSdkVersion="26" android:targetSdkVersion="35"/><queries><package android:name="org.simpleweather.app"/></queries><application android:label="Migration test provider"><provider android:name="org.daidai.prototype.qa.MusicMetadataDocuments" android:authorities="org.simpleweather.migrationqa.documents" android:permission="android.permission.MANAGE_DOCUMENTS" android:exported="true" android:grantUriPermissions="true"/><activity android:name="org.simpleweather.qa.MigrationGrantActivity" android:exported="true" android:theme="@android:style/Theme.NoDisplay"/></application><instrumentation android:name="org.simpleweather.qa.MusicLegacyExportTests" android:targetPackage="org.daidai.prototype.music"/></manifest>''','utf8')
for name in ['classes','dex']:(out/name).mkdir(exist_ok=True)
run([bt/'aapt2.exe','link','-o',out/'unsigned.apk','-I',platform,'--manifest',manifest])
run([jdk/'javac.exe','-encoding','UTF-8','--release','8','-cp',platform,'-d',out/'classes',out/'MusicMetadataDocuments.java',src/'tests/android/MigrationGrantActivity.java',src/'tests/android/MusicLegacyExportTests.java'])
run([jdk/'java.exe','-cp',bt/'lib/d8.jar','com.android.tools.r8.D8','--lib',platform,'--min-api','26','--output',out/'dex',*list((out/'classes').rglob('*.class'))])
with zipfile.ZipFile(out/'unsigned.apk','a',zipfile.ZIP_DEFLATED) as z:
 for f in (out/'dex').glob('*.dex'):z.write(f,f.name)
 for f in (ws/'prototypes/tests/fixtures/music-tags').glob('tagged.*'):z.write(f,'assets/music-tags/'+f.name)
run([bt/'zipalign.exe','-f','4',out/'unsigned.apk',out/'aligned.apk'])
run([jdk/'java.exe','-jar',bt/'lib/apksigner.jar','sign','--ks',ws/'private/signing/prototypes.jks','--ks-key-alias','prototypes','--ks-pass','pass:android','--out',out/'tests.apk',out/'aligned.apk'])
adb=sdk/'platform-tools/adb.exe'
run([adb,'-s','emulator-5580','install','-r',out/'tests.apk'])
# Reset only the disposable legacy app on this explicitly isolated emulator, then
# seed the actual 0.7.0 build before upgrading to the original-signature helper.
run([adb,'-s','emulator-5580','uninstall','org.daidai.prototype.music'])
run([adb,'-s','emulator-5580','install',src/'test-apk/DaidaiMusic-0.7.0.apk'])
for mode in ('seed','export'):
 if mode=='export':run([adb,'-s','emulator-5580','install','-r',ws/'work/prototypes/0.7.1-migration/DaidaiMusic-0.7.1-migration.apk'])
 r=run([adb,'-s','emulator-5580','shell','am','instrument','-w','-e','mode',mode,'org.simpleweather.migrationqa/org.simpleweather.qa.MusicLegacyExportTests'],capture_output=True,text=True,encoding='utf8',timeout=180)
 (out/(mode+'.txt')).write_text(r.stdout,'utf8');print(r.stdout)
 if 'PASS:' not in r.stdout or 'FAIL:' in r.stdout:raise SystemExit('Legacy migration failed')
run(['python',src/'tests/android/run.py','--suite','InternalMusicMigrationTests'])
