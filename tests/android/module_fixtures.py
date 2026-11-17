"""Prepare signed and invalid executable fixtures on an isolated emulator."""
from pathlib import Path
import shutil,subprocess,zipfile,json
root=Path(__file__).resolve().parents[2];ws=root.parent;sdk=ws/'tools/android-sdk';tools=sdk/'build-tools/35.0.0';out=ws/'work/qa/module-host-fixtures';out.mkdir(parents=True,exist_ok=True)
java=Path(shutil.which('java'));keytool=java.parent/'keytool.exe';signer=[java,'-jar',tools/'lib/apksigner.jar'];adb=sdk/'platform-tools/adb.exe'
def run(args):subprocess.run([str(x) for x in args],check=True)
base=root/'modules/store/pre-3.0.0-2/weather.ddapp'
for p in (root/'modules/store/pre-3.0.0-2').glob('*.ddapp'):shutil.copyfile(p,out/p.name)
shutil.copyfile(root/'modules/packages/external-android-apps/music/0.7.0/music-0.7.0.ddapp',out/'music-0.7.0.ddapp')
content=bytearray(base.read_bytes());content[len(content)//2]^=15;(out/'corrupt.ddapp').write_bytes(content)
key=out/'test-only.jks'
if not key.exists():run([keytool,'-genkeypair','-keystore',key,'-alias','test','-storepass','android','-keypass','android','-keyalg','RSA','-keysize','2048','-validity','30','-dname','CN=Untrusted module fixture','-noprompt'])
for name in ['wrong-signer','wrong-api','zip-slip','metadata-only']:
 raw=out/(name+'.unsigned.apk')
 with zipfile.ZipFile(base) as src,zipfile.ZipFile(raw,'w',zipfile.ZIP_DEFLATED) as z:
  for info in src.infolist():
   if info.filename.startswith('META-INF/'):continue
   if name=='metadata-only' and info.filename=='classes.dex':continue
   b=src.read(info.filename)
   if name=='wrong-api' and info.filename=='assets/module.json':
    m=json.loads(b);m['hostApi']='not-this-host';b=json.dumps(m).encode()
   z.writestr(info.filename,b)
  if name=='zip-slip':z.writestr('../outside.dex',b'not executable')
 aligned=out/(name+'.aligned.apk');run([tools/'zipalign.exe','-f','4',raw,aligned]);chosen=key if name=='wrong-signer' else root/'.signing/development.jks'
 run([*signer,'sign','--ks',chosen,'--ks-key-alias','test' if name=='wrong-signer' else 'simpleweather','--ks-pass','pass:android','--key-pass','pass:android','--out',out/(name+'.ddapp'),aligned])
run([adb,'-s','emulator-5580','shell','mkdir','-p','/data/local/tmp/module-host-test'])
for p in out.glob('*.ddapp'):run([adb,'-s','emulator-5580','push',p,'/data/local/tmp/module-host-test/'+p.name])
run([adb,'-s','emulator-5580','shell','chmod','-R','a+rX','/data/local/tmp/module-host-test'])
