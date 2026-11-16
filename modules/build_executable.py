"""Build signed executable app payloads; called after shared Java compilation.

The pre-3 host ABI shares models, Android component bridges, text resources and styling.
Screen classes and their anonymous/inner classes are excluded from the host DEX.
"""
from pathlib import Path
import json,zipfile,hashlib,shutil,subprocess,os

IDS=['weather','schedule','status','weather-map','astronomy','earthquake','random','todo']
SCREENS=['WeatherScreen','ScheduleScreen','GpsScreen','MapScreen','AstronomyScreen','EarthquakeScreen','RandomChoiceScreen','TodoScreen']
API='daidai-toolbox/pre-3.0.0-1'

def build(root,sdk,java,run):
    root=Path(root);build=root/'build';tools=sdk/'build-tools/35.0.0';platform=sdk/'platforms/android-35/android.jar';ext='.exe' if os.name=='nt' else ''
    classes=build/'classes';store=root/'modules/store/pre-3.0.0-1';store.mkdir(parents=True,exist_ok=True)
    defaults=root/'app/src/main/assets/default-apps';defaults.mkdir(exist_ok=True)
    jars=build/'all-classes.jar'
    with zipfile.ZipFile(jars,'w') as z:
        for p in classes.rglob('*.class'):z.write(p,p.relative_to(classes).as_posix())
    apps=[];excluded=set()
    for id,screen in zip(IDS,SCREENS):
        files=list((classes/'org/simpleweather/app').glob(screen+'*.class'))
        files=[p for p in files if p.stem==screen or p.stem.startswith(screen+'$')]
        assert files,(id,'Missing executable classes');excluded.update(files)
        stage=build/'apps'/id;stage.mkdir(parents=True,exist_ok=True);dex=stage/'dex';dex.mkdir(exist_ok=True)
        run([java,'-cp',tools/'lib/d8.jar','com.android.tools.r8.D8','--lib',platform,'--classpath',jars,'--min-api','26','--output',dex,*files])
        manifest=stage/'AndroidManifest.xml'
        manifest.write_text(f'<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="org.simpleweather.module.{id.replace("-","_")}" android:versionCode="1" android:versionName="pre-3.0.0-1"><uses-sdk android:minSdkVersion="26" android:targetSdkVersion="35"/><application android:hasCode="true" android:allowBackup="false"/></manifest>','utf8')
        assets=stage/'assets';assets.mkdir(exist_ok=True)
        (assets/'module.json').write_text(json.dumps({'format':'signed-dex-app','schemaVersion':1,'id':id,'entry':screen,'hostApi':API,'versionCode':1,'version':'pre-3.0.0-1','standaloneInstallable':False,'resourceProvider':'host','dataRetention':'retain-on-uninstall','configurationGroups':{'weather':['weather'],'status':['toolbox','status_places','status_routes'],'schedule':['toolbox','timetable'],'todo':['toolbox','todos'],'random':['toolbox','random_choice']}.get(id,['toolbox'])},ensure_ascii=False,indent=2),'utf8')
        raw=stage/'unsigned.apk';run([tools/('aapt2'+ext),'link','-o',raw,'-I',platform,'--manifest',manifest,'-A',assets,'--min-sdk-version','26','--target-sdk-version','35'])
        with zipfile.ZipFile(raw,'a',zipfile.ZIP_DEFLATED) as z:
            for p in dex.glob('*.dex'):z.write(p,p.name)
        aligned=stage/'aligned.apk';run([tools/('zipalign'+ext),'-f','-P','16','4',raw,aligned])
        package=store/(id+'.ddapp')
        run([java,'-jar',tools/'lib/apksigner.jar','sign','--ks',root/'.signing/development.jks','--ks-key-alias','simpleweather','--ks-pass','pass:android','--key-pass','pass:android','--out',package,aligned])
        run([java,'-jar',tools/'lib/apksigner.jar','verify',package])
        if id in ['weather','status']:shutil.copyfile(package,defaults/package.name)
        apps.append({'id':id,'type':'signed-dex-app','version':'pre-3.0.0-1','bytes':package.stat().st_size,'sha256':hashlib.sha256(package.read_bytes()).hexdigest(),'url':'https://raw.githubusercontent.com/AdybirdZ/daidai-toolbox/main/modules/store/pre-3.0.0-1/'+package.name})
    for id,file,version in [('music','DaidaiMusic-0.7.0.apk','0.7.0'),('messaging','DaidaiMessenger-0.2.0.apk','0.2.0'),('colorvision','DaidaiColorVision-0.2.0.apk','0.2.0')]:
        p=root/'test-apk'/file
        public=json.loads((root/'test-apk/index.json').read_text('utf8'))
        record=next(x for x in public['apps'] if x['apk']==file)
        if p.exists():assert hashlib.sha256(p.read_bytes()).hexdigest()==record['sha256']
        apps.append({'id':id,'type':'external-android-app','version':version,'bytes':record['size'],'sha256':record['sha256'],'url':'https://raw.githubusercontent.com/AdybirdZ/daidai-toolbox/main/test-apk/'+file})
    catalog={'format':'daidai-app-store','schemaVersion':1,'hostApi':API,'defaultInstalled':['weather','status'],'apps':apps}
    for p in [store/'catalog.json',root/'app/src/main/assets/app-catalog.json']:p.write_text(json.dumps(catalog,indent=2)+'\n','utf8')
    (store/'SHA256SUMS.txt').write_text(''.join(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+p.name+'\n' for p in sorted(store.iterdir()) if p.suffix in ['.ddapp','.json']),'utf8')
    return excluded
