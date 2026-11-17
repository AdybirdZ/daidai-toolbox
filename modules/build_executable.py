"""Build signed executable app payloads; called after shared Java compilation.

The pre-3 host ABI shares models, Android component bridges, text resources and styling.
Screen classes and their anonymous/inner classes are excluded from the host DEX.
"""
from pathlib import Path
import json,zipfile,hashlib,shutil,subprocess,os

IDS=['weather','schedule','status','weather-map','astronomy','earthquake','random','todo','music','messaging','colorvision']
SCREENS=['WeatherScreen','ScheduleScreen','GpsScreen','MapScreen','AstronomyScreen','EarthquakeScreen','RandomChoiceScreen','TodoScreen','org.daidai.modules.music.MusicActivity','org.daidai.modules.messaging.MessagingActivity','org.daidai.modules.colorvision.ColorVisionActivity']
API='daidai-toolbox/pre-3.0.0-2'
VERSION='pre-3.0.0-2'
VERSION_CODE=2
EMBEDDED={'music','messaging','colorvision'}
GROUPS={'weather':['weather','toolbox'],'status':['toolbox','status_places','status_routes'],'schedule':['toolbox','timetable'],'todo':['toolbox','todos'],'random':['toolbox','random_choice']}
PERMISSIONS={
    'weather':['INTERNET'], 'weather-map':['INTERNET'], 'astronomy':[], 'random':[],
    'status':['ACCESS_COARSE_LOCATION','ACCESS_FINE_LOCATION','CAMERA','FOREGROUND_SERVICE','FOREGROUND_SERVICE_LOCATION','POST_NOTIFICATIONS'],
    'earthquake':['INTERNET','ACCESS_COARSE_LOCATION','ACCESS_FINE_LOCATION'],
    'schedule':['POST_NOTIFICATIONS','SCHEDULE_EXACT_ALARM','RECEIVE_BOOT_COMPLETED'],
    'todo':['POST_NOTIFICATIONS','SCHEDULE_EXACT_ALARM','RECEIVE_BOOT_COMPLETED'],
    'music':['FOREGROUND_SERVICE','FOREGROUND_SERVICE_MEDIA_PLAYBACK','WAKE_LOCK','POST_NOTIFICATIONS'],
    'messaging':['INTERNET','ACCESS_NETWORK_STATE','BLUETOOTH_CONNECT','FOREGROUND_SERVICE','FOREGROUND_SERVICE_REMOTE_MESSAGING','WAKE_LOCK','POST_NOTIFICATIONS'],
    'colorvision':['CAMERA']}
KEY_RULES={'weather':['weather_*'],'weather-map':['radar_region','map_product'],'status':['status_*','location_*','route_interval'],'astronomy':['astro_*'],'earthquake':['quake_*'],'schedule':['course_*'],'random':['random_*'],'todo':['todo_*']}
BRIDGES={'status':['LocationService'],'todo':['TodoReceiver','TodoScheduler'],'schedule':['TodoReceiver','ScheduleNotifier'],'weather-map':['OfficialPageActivity']}

def build(root,sdk,java,run):
    root=Path(root);build=root/'build';tools=sdk/'build-tools/35.0.0';platform=sdk/'platforms/android-35/android.jar';ext='.exe' if os.name=='nt' else ''
    classes=build/'classes';store=root/'modules/store'/VERSION;store.mkdir(parents=True,exist_ok=True)
    defaults=root/'app/src/main/assets/default-apps';defaults.mkdir(exist_ok=True)
    jars=build/'all-classes.jar'
    with zipfile.ZipFile(jars,'w') as z:
        for p in classes.rglob('*.class'):z.write(p,p.relative_to(classes).as_posix())
    apps=[];excluded=set()
    for id,screen in zip(IDS,SCREENS):
        if id in EMBEDDED:
            files=list((classes/'org/daidai/modules'/id).rglob('*.class'))+list((classes/'org/daidai/modules/shared').rglob('*.class'))
        else:
            files=list((classes/'org/simpleweather/app').glob(screen+'*.class'))
            files=[p for p in files if p.stem==screen or p.stem.startswith(screen+'$')]
        assert files,(id,'Missing executable classes');excluded.update(files)
        stage=build/'apps'/id;stage.mkdir(parents=True,exist_ok=True);dex=stage/'dex';dex.mkdir(exist_ok=True)
        run([java,'-cp',tools/'lib/d8.jar','com.android.tools.r8.D8','--lib',platform,'--classpath',jars,'--min-api','26','--output',dex,*files])
        manifest=stage/'AndroidManifest.xml'
        manifest.write_text(f'<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="org.simpleweather.module.{id.replace("-","_")}" android:versionCode="{VERSION_CODE}" android:versionName="{VERSION}"><uses-sdk android:minSdkVersion="26" android:targetSdkVersion="35"/><application android:hasCode="true" android:allowBackup="false"/></manifest>','utf8')
        assets=stage/'assets';assets.mkdir(exist_ok=True)
        extra_assets=root/'modules/embedded'/id/'assets'
        if id in EMBEDDED and extra_assets.is_dir():shutil.copytree(extra_assets,assets,dirs_exist_ok=True)
        descriptor={'format':'signed-dex-app','schemaVersion':1,'id':id,'entry':screen,'hostApi':API,'versionCode':VERSION_CODE,'version':VERSION,'standaloneInstallable':False,'resourceProvider':'application' if id in EMBEDDED else 'host','dataRetention':'retain-on-uninstall','configurationGroups':[id+'_module'] if id in EMBEDDED else GROUPS.get(id,['toolbox']),'configurationContract':{'format':'daidai-toolbox-module-config','version':1,'toolboxKeyRules':KEY_RULES.get(id,[])},'permissions':['android.permission.'+v for v in PERMISSIONS[id]],'hostComponentBridges':BRIDGES.get(id,[]),'dependencies':['module-page-worker-api'] if id in EMBEDDED else ['host-shared-resources','host-shared-models-settings']}
        if id in EMBEDDED:
            descriptor['pages']={'main':screen}
            if id=='music':descriptor['pages']['artwork']='org.daidai.modules.music.ArtworkPreviewActivity'
            if id in ['music','messaging']:descriptor['worker']='org.daidai.modules.'+id+('.PlaybackService' if id=='music' else '.MessagingService')
        (assets/'module.json').write_text(json.dumps(descriptor,ensure_ascii=False,indent=2),'utf8')
        resources=[]
        if id in EMBEDDED:
            for label,path in [('shared',root/'modules/embedded/shared/res'),('application',root/'modules/embedded'/id/'res')]:
                out=stage/(label+'-res.zip');run([tools/('aapt2'+ext),'compile','--dir',path,'-o',out]);resources.append(out)
        raw=stage/'unsigned.apk';run([tools/('aapt2'+ext),'link','-o',raw,'-I',platform,'--manifest',manifest,'-A',assets,'--min-sdk-version','26','--target-sdk-version','35',*resources])
        with zipfile.ZipFile(raw,'a',zipfile.ZIP_DEFLATED) as z:
            for p in dex.glob('*.dex'):z.write(p,p.name)
        aligned=stage/'aligned.apk';run([tools/('zipalign'+ext),'-f','-P','16','4',raw,aligned])
        package=store/(id+'.ddapp')
        run([java,'-jar',tools/'lib/apksigner.jar','sign','--v4-signing-enabled','false','--ks',root/'.signing/development.jks','--ks-key-alias','simpleweather','--ks-pass','pass:android','--key-pass','pass:android','--out',package,aligned])
        run([java,'-jar',tools/'lib/apksigner.jar','verify',package])
        if id in ['weather','status']:shutil.copyfile(package,defaults/package.name)
        apps.append({'id':id,'type':'signed-dex-app','version':VERSION,'versionCode':VERSION_CODE,'bytes':package.stat().st_size,'sha256':hashlib.sha256(package.read_bytes()).hexdigest(),'url':'https://raw.githubusercontent.com/AdybirdZ/daidai-toolbox/main/modules/store/'+VERSION+'/'+package.name})
    catalog={'format':'daidai-app-store','schemaVersion':1,'hostApi':API,'defaultInstalled':['weather','status'],'apps':apps}
    for p in [store/'catalog.json',root/'app/src/main/assets/app-catalog.json']:p.write_text(json.dumps(catalog,indent=2)+'\n','utf8')
    (store/'SHA256SUMS.txt').write_text(''.join(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+p.name+'\n' for p in sorted(store.iterdir()) if p.suffix in ['.ddapp','.json']),'utf8')
    return excluded
