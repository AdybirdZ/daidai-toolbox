"""Build and verify an executable, locally delivered Android music extension package."""
import argparse, hashlib, json, os, re, shutil, subprocess, tempfile, zipfile
import xml.etree.ElementTree as ET
from pathlib import Path, PurePosixPath

ROOT=Path(__file__).resolve().parent
WORKSPACE=ROOT.parents[1]
EXPECTED_SIGNER='0d4d9d87dfb81fa0ea922fc764ff4326d16284ca69ca8715bca247e22973e1f4'
ALLOWED={'module.json','configuration-contract.json','README.md','payload/music.apk'}
MAX_APK=128*1024*1024
HOST={'applicationId':'org.simpleweather.app','api':'daidai-toolbox/3.0.0','implementationStatus':'not-yet-implemented'}
CONFIG={'format':'daidai-toolbox-module-configuration-contract','schemaVersion':1,'module':'music','hostAdapterApi':'daidai-android-app/1','ownerPackage':'org.daidai.prototype.music','storage':'android-private-application-data','migration':'same-package-same-signer-update','hostCanReadPrivateData':False,'userDataIncluded':False,'crossPackageUriGrantsTransferable':False,'exportImportBridgeImplemented':False}

def music_android_contract():
    """The current launcher manifest is the sole local version baseline."""
    manifest=ET.parse(ROOT.parent/'launchers/music/AndroidManifest.xml').getroot()
    android='{http://schemas.android.com/apk/res/android}'
    version=manifest.get(android+'versionName','');code=manifest.get(android+'versionCode','')
    uses=manifest.find('uses-sdk')
    if manifest.get('package')!='org.daidai.prototype.music' or not re.fullmatch(r'[0-9]+\.[0-9]+\.[0-9]+',version) or not code.isdecimal() or int(code)<=0 or uses is None or uses.get(android+'minSdkVersion')!='26':
        raise ValueError('Unexpected music launcher identity/version')
    return {'packageName':'org.daidai.prototype.music','launchActivity':'org.daidai.prototype.music.MainActivity','versionName':version,'versionCode':int(code),'minSdk':26}

def current_apk_path():
    version=music_android_contract()['versionName']
    local=WORKSPACE/f'work/prototypes/{version}/DaidaiMusic-{version}.apk'
    public=WORKSPACE/f'test-apk/DaidaiMusic-{version}.apk'
    return local if local.is_file() or not public.is_file() else public

def current_package_path(for_pack=False):
    version=music_android_contract()['versionName']
    local=WORKSPACE/f'work/prototypes/music-{version}/music-{version}.ddapp'
    public=WORKSPACE/f'modules/packages/external-android-apps/music/{version}/music-{version}.ddapp'
    return local if for_pack or local.is_file() or not public.is_file() else public

def digest(value):return hashlib.sha256(value).hexdigest()

def inspect_apk(apk):
    bundled=WORKSPACE/'tools/android-sdk'
    sdk=bundled if bundled.is_dir() else Path(os.environ.get('ANDROID_SDK_ROOT') or os.environ.get('ANDROID_HOME') or '')
    tools=sdk/'build-tools/35.0.0'
    if not (tools/'aapt2.exe').is_file() or not (tools/'lib/apksigner.jar').is_file():raise ValueError('Android SDK Build Tools 35.0.0 are required; set ANDROID_SDK_ROOT when using a source snapshot')
    result=subprocess.check_output([shutil.which('java'),'-jar',str(tools/'lib/apksigner.jar'),'verify','--print-certs',str(apk)],text=True,encoding='utf8')
    found=re.search(r'Signer #1 certificate SHA-256 digest: (\w+)',result)
    if not found or found.group(1)!=EXPECTED_SIGNER:raise ValueError('Untrusted signing certificate')
    badging=subprocess.check_output([str(tools/'aapt2.exe'),'dump','badging',str(apk)],text=True,encoding='utf8')
    match=re.search(r"package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'",badging)
    identity=music_android_contract()
    if not match or match.groups()!=(identity['packageName'],str(identity['versionCode']),identity['versionName']):raise ValueError('Wrong application identity/version')
    if "launchable-activity: name='org.daidai.prototype.music.MainActivity'" not in badging:raise ValueError('Wrong launch activity')
    permissions=re.findall(r"uses-permission: name='([^']+)'",badging)
    if set(permissions)!={'android.permission.WAKE_LOCK','android.permission.FOREGROUND_SERVICE','android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK','android.permission.POST_NOTIFICATIONS'}:raise ValueError('Unexpected permissions')
    return permissions

def validate_container(path):
    if Path(path).stat().st_size>MAX_APK+1024*1024:raise ValueError('Container too large')
    with zipfile.ZipFile(path) as z:
        infos=z.infolist();names=[i.filename for i in infos]
        if len(names)!=len(set(names)) or set(names)!=ALLOWED:raise ValueError('Unexpected, duplicate or missing entries')
        for i in infos:
            p=PurePosixPath(i.filename)
            if p.is_absolute() or '..' in p.parts or '\\' in i.filename or ':' in i.filename:raise ValueError('Unsafe path')
            if i.flag_bits&1 or i.is_dir() or i.file_size>(MAX_APK if i.filename.endswith('.apk') else 65536):raise ValueError('Unsupported entry or size')
            if (i.external_attr>>16)&0o170000==0o120000:raise ValueError('Symlink entry')
        m=json.loads(z.read('module.json').decode('utf8'))
        if m.get('format')!='daidai-toolbox-module-package' or m.get('schemaVersion')!=1 or m.get('moduleId')!='music' or m.get('packageType')!='external-android-application' or m.get('standaloneInstallable') is not True:raise ValueError('Unsupported module')
        if m.get('hostAdapterApi')!='daidai-android-app/1':raise ValueError('Unsupported host API')
        payload=m.get('payload',{});data=z.read('payload/music.apk')
        if payload.get('file')!='payload/music.apk' or type(payload.get('bytes')) is not int or payload.get('bytes')!=len(data) or payload.get('sha256')!=digest(data):raise ValueError('Payload hash/size mismatch')
        if payload.get('signerCertificateSha256')!=EXPECTED_SIGNER:raise ValueError('Untrusted signer')
        if m.get('android',{})!=music_android_contract():raise ValueError('Unexpected Android contract')
        if m.get('requiresHost')!=HOST:raise ValueError('Unexpected future host contract')
        if m.get('configurationContract')!='configuration-contract.json':raise ValueError('Unexpected configuration contract path')
        if json.loads(z.read('configuration-contract.json').decode('utf8'))!=CONFIG:raise ValueError('Unexpected configuration boundary')
        return m,data

def verify(path):
    manifest,data=validate_container(path)
    with tempfile.TemporaryDirectory(prefix='daidai-module-') as tmp:
        apk=Path(tmp)/'music.apk';apk.write_bytes(data);permissions=inspect_apk(apk)
    if set(manifest.get('permissions',[]))!=set(permissions):raise ValueError('Permission contract mismatch')
    return {'moduleId':'music','version':manifest['android']['versionName'],'versionCode':manifest['android']['versionCode'],'containerSha256':digest(Path(path).read_bytes()),'apkSha256':digest(data),'signatureVerified':True,'executablePayload':True,'hostAdapterApi':'daidai-android-app/1','formalHostAvailable':False}

def pack(apk,output=None):
    apk=Path(apk);permissions=inspect_apk(apk);data=apk.read_bytes()
    if len(data)>MAX_APK:raise ValueError('APK too large')
    manifest={'format':'daidai-toolbox-module-package','schemaVersion':1,'moduleId':'music','title':{'zh-CN':'呆呆音乐','en':'Daidai Music'},'packageType':'external-android-application','standaloneInstallable':True,'hostAdapterApi':'daidai-android-app/1','requiresHost':HOST,'android':music_android_contract(),'payload':{'file':'payload/music.apk','bytes':len(data),'sha256':digest(data),'signerCertificateSha256':EXPECTED_SIGNER},'permissions':permissions,'configurationContract':'configuration-contract.json'}
    config=CONFIG
    output=Path(output) if output else current_package_path(for_pack=True);output.parent.mkdir(parents=True,exist_ok=True)
    entries={'module.json':json.dumps(manifest,ensure_ascii=False,indent=2).encode('utf8'),'configuration-contract.json':json.dumps(config,ensure_ascii=False,indent=2).encode('utf8'),'README.md':(ROOT/'README.md').read_bytes(),'payload/music.apk':data}
    with zipfile.ZipFile(output,'w',zipfile.ZIP_DEFLATED) as z:
        for name,value in sorted(entries.items()):
            info=zipfile.ZipInfo(name,(2026,11,10,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o644<<16;z.writestr(info,value)
    result=verify(output);output.with_suffix('.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf8');return result

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);commands=parser.add_subparsers(dest='command',required=True)
    build=commands.add_parser('pack');build.add_argument('--apk',required=True);build.add_argument('--output')
    check=commands.add_parser('verify');check.add_argument('package');args=parser.parse_args()
    print(json.dumps(pack(args.apk,args.output) if args.command=='pack' else verify(args.package),ensure_ascii=False,indent=2))
