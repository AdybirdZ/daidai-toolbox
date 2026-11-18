"""Build signed executable app payloads; called after shared Java compilation.

The pre-3 host ABI shares models, Android component bridges, text resources and styling.
Screen classes and their anonymous/inner classes are excluded from the host DEX.
"""
from pathlib import Path
import json,zipfile,hashlib,shutil,subprocess,os

IDS=['weather','schedule','status','weather-map','astronomy','earthquake','random','todo','music','messaging','colorvision']
SCREENS=['WeatherScreen','ScheduleScreen','GpsScreen','MapScreen','AstronomyScreen','EarthquakeScreen','RandomChoiceScreen','TodoScreen','org.daidai.modules.music.MusicActivity','org.daidai.modules.messaging.MessagingActivity','org.daidai.modules.colorvision.ColorVisionActivity']
API='daidai-toolbox/api-1'
VERSION='3.0.0'
VERSION_CODE=5
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
DESCRIPTIONS={
 'weather':['地区天气、逐时预报与天气详情。','Local weather, hourly forecasts and weather details.'],
 'schedule':['课程安排、学期设置与每日课程提示。','Courses, semester settings and daily class notifications.'],
 'status':['位置、卫星、传感器、地点与路线记录。','Location, satellites, sensors, places and route recording.'],
 'weather-map':['查看官方天气图与雷达网页。','View official weather maps and radar pages.'],
 'astronomy':['当地星空、天体资料与设备指向。','Local sky chart, celestial information and device pointing.'],
 'earthquake':['近期地震目录、距离与烈度参考。','Recent earthquakes, distances and reference intensities.'],
 'random':['名单管理、随机抽取与抽取进度。','Saved lists, random draws and draw progress.'],
 'todo':['事项、重复安排、提醒与完成记录。','Tasks, recurring schedules, reminders and completion history.'],
 'music':['本地歌曲、歌词、歌单与音频分析。','Local music, lyrics, playlists and audio analysis.'],
 'messaging':['通过蓝牙或局域网与附近设备通信。','Communicate with nearby devices over Bluetooth or a local network.'],
 'colorvision':['图片与相机画面的色觉模拟。','Color vision simulation for images and camera views.']}

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
            names={p.stem for p in (root/'modules/apps'/id/'java').rglob('*.java')}
            files=[p for p in (classes/'org/simpleweather/app').glob('*.class') if p.stem.split('$')[0] in names]
        assert files,(id,'Missing executable classes');excluded.update(files)
        stage=build/'apps'/id;stage.mkdir(parents=True,exist_ok=True);dex=stage/'dex';dex.mkdir(exist_ok=True)
        run([java,'-cp',tools/'lib/d8.jar','com.android.tools.r8.D8','--lib',platform,'--classpath',jars,'--min-api','26','--output',dex,*files])
        manifest=stage/'AndroidManifest.xml'
        resource_package='org.simpleweather.module.'+id.replace('-','_') if id in EMBEDDED else 'org.simpleweather.app'
        manifest.write_text(f'<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="{resource_package}" android:versionCode="{VERSION_CODE}" android:versionName="{VERSION}"><uses-sdk android:minSdkVersion="26" android:targetSdkVersion="35"/><application android:hasCode="true" android:allowBackup="false"/></manifest>','utf8')
        assets=stage/'assets';assets.mkdir(exist_ok=True)
        extra_assets=root/'modules/embedded'/id/'assets'
        if id in EMBEDDED and extra_assets.is_dir():shutil.copytree(extra_assets,assets,dirs_exist_ok=True)
        if id=='weather':shutil.copyfile(root/'app/src/main/assets/cities.json',assets/'cities.json')
        descriptor={'format':'signed-dex-app','schemaVersion':1,'id':id,'entry':screen,'hostApi':API,'minHostVersionCode':55,'minSdk':26,'versionCode':VERSION_CODE,'version':VERSION,'standaloneInstallable':False,'resourceProvider':'application' if id in EMBEDDED else 'mounted-stable-ids','dataRetention':'retain-on-uninstall','configurationGroups':[id+'_module'] if id in EMBEDDED else GROUPS.get(id,['toolbox']),'configurationContract':{'format':'daidai-toolbox-module-config','version':1,'toolboxKeyRules':KEY_RULES.get(id,[])},'permissions':['android.permission.'+v for v in PERMISSIONS[id]],'hostComponentBridges':BRIDGES.get(id,[]),'dependencies':['module-page-worker-api'] if id in EMBEDDED else ['host-api-1','shared-data-contracts','stable-resource-ids']}
        if id in EMBEDDED:
            descriptor['pages']={'main':screen}
            if id=='music':descriptor['pages']['artwork']='org.daidai.modules.music.ArtworkPreviewActivity'
            if id in ['music','messaging']:descriptor['worker']='org.daidai.modules.'+id+('.PlaybackService' if id=='music' else '.MessagingService')
        (assets/'module.json').write_text(json.dumps(descriptor,ensure_ascii=False,indent=2),'utf8')
        resources=[]
        if id in EMBEDDED:
            for label,path in [('shared',root/'modules/embedded/shared/res'),('application',root/'modules/embedded'/id/'res')]:
                out=stage/(label+'-res.zip');run([tools/('aapt2'+ext),'compile','--dir',path,'-o',out]);resources.append(out)
        legacy_args=[]
        if id not in EMBEDDED:
            out=stage/'feature-res.zip';run([tools/('aapt2'+ext),'compile','--dir',build/'partitioned-res'/id,'-o',out]);resources.append(out)
            legacy_args=['--stable-ids',root/'modules/stable-resource-ids.txt','--rename-manifest-package','org.simpleweather.module.'+id.replace('-','_')]
        raw=stage/'unsigned.apk';run([tools/('aapt2'+ext),'link','-o',raw,'-I',platform,'--manifest',manifest,'-A',assets,'--min-sdk-version','26','--target-sdk-version','35',*legacy_args,*resources])
        with zipfile.ZipFile(raw,'a',zipfile.ZIP_DEFLATED) as z:
            for p in dex.glob('*.dex'):z.write(p,p.name)
        aligned=stage/'aligned.apk';run([tools/('zipalign'+ext),'-f','-P','16','4',raw,aligned])
        package=store/(id+'.ddapp')
        run([java,'-jar',tools/'lib/apksigner.jar','sign','--v4-signing-enabled','false','--ks',root/'.signing/development.jks','--ks-key-alias','simpleweather','--ks-pass','pass:android','--key-pass','pass:android','--out',package,aligned])
        run([java,'-jar',tools/'lib/apksigner.jar','verify',package])
        if id in ['weather','status']:shutil.copyfile(package,defaults/package.name)
        apps.append({'id':id,'type':'signed-dex-app','version':VERSION,'versionCode':VERSION_CODE,'hostApi':API,'minHostVersionCode':55,'minSdk':26,'permissions':descriptor['permissions'],'description':{'zh':DESCRIPTIONS[id][0],'en':DESCRIPTIONS[id][1]},'releaseNotes':{'zh':'适配稳定宿主接口，独立交付应用代码与资源。','en':'Uses the stable host API with independently packaged app code and resources.'},'bytes':package.stat().st_size,'sha256':hashlib.sha256(package.read_bytes()).hexdigest(),'url':'https://raw.githubusercontent.com/AdybirdZ/daidai-toolbox/main/modules/store/'+VERSION+'/'+package.name})
    catalog={'format':'daidai-app-store','schemaVersion':1,'hostApi':API,'defaultInstalled':['weather','status'],'apps':apps}
    for p in [store/'catalog.json',root/'app/src/main/assets/app-catalog.json']:p.write_text(json.dumps(catalog,indent=2)+'\n','utf8')
    (store/'SHA256SUMS.txt').write_text(''.join(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+p.name+'\n' for p in sorted(store.iterdir()) if p.suffix in ['.ddapp','.json']),'utf8')
    return excluded
