package org.simpleweather.app;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.location.*;
import android.os.*;
import android.provider.Settings;
import android.widget.*;
import java.util.Locale;

/** Location service control and display. The Activity never owns background callbacks. */
final class GpsScreen {
    private final MainActivity a;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private TextView status,latitude,longitude,accuracy,altitude,age,speed,bearing,motion;
    private boolean visible,pendingPermission;
    private final Runnable tick=new Runnable(){public void run(){if(visible){update();handler.postDelayed(this,frameDelay());}}};
    private long frameDelay(){int d=a.preferences.getInt("status_sensor_delay",android.hardware.SensorManager.SENSOR_DELAY_FASTEST);return d==android.hardware.SensorManager.SENSOR_DELAY_FASTEST?16:d==android.hardware.SensorManager.SENSOR_DELAY_GAME?16:d==android.hardware.SensorManager.SENSOR_DELAY_UI?33:50;}
    GpsScreen(MainActivity activity){a=activity;hardware=new StatusHardware(a);places=new StatusPlaces(a,()->{lastSecond=-1;update();});}
    static final String[] SECTION_NAMES={"指南针","当前位置","定位卫星","地点","传感器","电池","设备与环境","使用说明"};
    private final StatusHardware hardware;
    private final StatusPlaces places;
    private StatusViews.Compass compass;private StatusViews.Treasure treasure;private StatusViews.Sky sky;
    private TextView compassInfo,satelliteStatus,satelliteInfo,satelliteDetails,treasureInfo,batteryInfo,deviceInfo;
    private LinearLayout satelliteDetailBox;private boolean details,pendingSatellite,batteryRegistered;
    private Intent battery;
    private long lastSecond=-1;
    private String selectedSatellite="";
    private final java.util.Map<Integer,TextView> sensorViews=new java.util.HashMap<>();
    private final BroadcastReceiver batteryReceiver=new BroadcastReceiver(){public void onReceive(Context c,Intent i){battery=i;}};
    void render(){
        status=latitude=longitude=accuracy=altitude=age=speed=bearing=motion=compassInfo=satelliteStatus=satelliteInfo=satelliteDetails=treasureInfo=batteryInfo=deviceInfo=null;
        compass=null;treasure=null;sky=null;satelliteDetailBox=null;sensorViews.clear();lastSecond=-1;
        for(int id:DisplayOrder.parse(a.preferences.getString("status_order",""),SECTION_NAMES.length)){
            if(!a.preferences.getBoolean("status_show_"+id,true))continue;
            switch(id){case 0:compassCard();break;case 1:locationCard();break;case 2:satellitesCard();break;case 3:placesCard();break;case 4:sensorsCard();break;case 5:batteryCard();break;case 6:deviceCard();break;case 7:infoCard();break;}
            a.gap(a.body,16);
        }
        if(sky==null){pendingSatellite=false;hardware.stopGnss("卫星区域已隐藏，接收已停止");}
        resume();
    }
    private LinearLayout card(String title){LinearLayout c=a.card();c.addView(a.text(title,18,a.INK,true));a.gap(c,12);return c;}
    private void compassCard(){LinearLayout c=card("指南针");compass=new StatusViews.Compass(a,hardware);c.addView(compass,a.lp(-1,-2));compassInfo=a.text("等待方向传感器",14,a.MUTED,false);c.addView(compassInfo);a.gap(c,12);c.addView(a.text("屏幕上沿为朝向，请平放设备。远离磁铁、金属和电器，必要时缓慢做 8 字校准；真北需最近定位估算磁偏角。",13,a.MUTED,false));}
    private void locationCard(){
        LinearLayout c=a.card();c.addView(a.text("当前位置",18,a.INK,true));a.gap(c,12);status=a.text(LocationService.status,14,a.MUTED,false);c.addView(status);a.gap(c,22);
        longitude=value(c,"经度 · WGS84");latitude=value(c,"纬度 · WGS84");accuracy=value(c,"水平定位误差半径");altitude=value(c,"海拔 · 相对 WGS84 椭球");speed=value(c,"地面速度");bearing=value(c,"移动方位");age=a.text("等待定位结果",12,a.MUTED,false);c.addView(age);a.gap(c,10);motion=a.text("移动方位由定位结果提供，不是设备朝向。",13,a.MUTED,false);c.addView(motion);a.gap(c,16);
        c.addView(a.button("开始 / 重新定位",true,this::start));a.gap(c,10);c.addView(a.button("停止定位",false,()->{pendingPermission=false;LocationService.stop(a);hardware.stopGnss("卫星接收已停止");update();}));a.gap(c,12);c.addView(a.text("当前方式："+mode()+" · 设置中可切换",12,a.MUTED,false));a.gap(a.body,16);
    }
    private void satellitesCard(){LinearLayout c=card("定位卫星 · 设备接收状态");satelliteStatus=a.text(hardware.gnssMessage,14,a.MUTED,false);c.addView(satelliteStatus);a.gap(c,12);
        LinearLayout actions=a.row();actions.addView(a.button("开始卫星接收",true,this::startSatellites),new LinearLayout.LayoutParams(0,-2,1));a.gapHorizontal(actions,8);actions.addView(a.button("停止卫星接收",false,()->{pendingSatellite=false;hardware.stopGnss("卫星接收已停止");lastSecond=-1;update();}),new LinearLayout.LayoutParams(0,-2,1));c.addView(actions);a.gap(c,12);
        sky=new StatusViews.Sky(a,hardware,this::satelliteSelection);sky.selected=selectedSatellite;c.addView(sky,a.lp(-1,-2));
        c.addView(a.text("北在上、南在下、东在右、西在左；圆周为高度角 0°，中心为 90°；方位角从真北起顺时针。颜色按卫星系统区分，实心表示参与本次定位，空心表示未参与。点卫星查看参数。",13,a.MUTED,false));a.gap(c,12);
        satelliteInfo=a.text("尚未选中卫星",14,a.INK,false);c.addView(satelliteInfo);a.gap(c,12);
        c.addView(a.button("展开 / 收起卫星明细",false,()->{details=!details;if(satelliteDetailBox!=null)satelliteDetailBox.setVisibility(details?android.view.View.VISIBLE:android.view.View.GONE);lastSecond=-1;update();}));a.gap(c,10);satelliteDetailBox=a.column();satelliteDetails=a.text("等待设备数据",13,a.INK,false);satelliteDetails.setTextIsSelectable(true);satelliteDetailBox.addView(satelliteDetails);satelliteDetailBox.setVisibility(details?android.view.View.VISIBLE:android.view.View.GONE);c.addView(satelliteDetailBox);
        a.gap(c,12);c.addView(a.text("仅显示芯片与 Android 报告的卫星，不推算完整星座。请授予精确定位；网络定位本身没有卫星状态，按钮会单独开启前台卫星接收。切页、锁屏或后台停止此接收，不受持续定位设置影响。系统辅助定位可能使用网络；应用不下载星历。",13,a.MUTED,false));
    }
    private void startSatellites(){if(a.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){pendingSatellite=true;a.requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},46);return;}pendingSatellite=false;hardware.startGnss();lastSecond=-1;update();}
    void satellitePermissionResult(){if(!pendingSatellite)return;if(a.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){pendingSatellite=false;hardware.gnssMessage="未授予精确定位，不能读取卫星状态";a.toast(hardware.gnssMessage);}else if(visible)startSatellites();}
    private void satelliteSelection(){if(satelliteInfo==null||sky==null)return;selectedSatellite=sky.selected;for(StatusHardware.Satellite s:hardware.shown())if(s.key().equals(sky.selected)){set(satelliteInfo,satelliteLine(s));return;}set(satelliteInfo,sky.selected.isEmpty()?"尚未选中卫星":"所选卫星当前未报告或已被隐藏");}
    private String satelliteLine(StatusHardware.Satellite s){return s.label()+"\n高度角 "+(s.validAngles()?String.format(Locale.ROOT,"%.1f° · 方位角 %.1f° %s",s.elevation,s.azimuth,StatusMath.direction(s.azimuth))+(s.elevation<0?" · 地平线以下":""):"未提供有效角度")+"\n载噪密度比 "+(Float.isFinite(s.cn0)&&s.cn0>0?String.format(Locale.ROOT,"%.1f dB-Hz",s.cn0):"未提供")+" · "+(s.used?"参与本次定位":"未参与本次定位");}
    private void placesCard(){LinearLayout c=card("地点");treasureInfo=a.text("请选择保存的地点",14,a.INK,false);c.addView(treasureInfo);a.gap(c,12);treasure=new StatusViews.Treasure(a,hardware,places);c.addView(treasure,a.lp(-1,-2));
        c.addView(a.button("保存当前位置",true,places::addCurrent));a.gap(c,10);c.addView(a.button("手动添加地点",false,()->places.edit(null,null)));a.gap(c,10);c.addView(a.button("选择 / 管理地点",false,places::manage));a.gap(c,12);
        c.addView(a.text("蓝点是所选地点，中心是你；外圈自动缩放。方向传感器与最近位置有效时，顶部跟随设备上沿朝向；否则真北向上。只计算直线地表距离和初始方位，不提供道路、障碍物或路线。定位和磁场误差会影响方向，不能用于安全关键导航。地点仅在本机保存，清缓存或恢复默认设置不删除；卸载应用会丢失。",13,a.MUTED,false));
    }
    static final String[] SENSOR_NAMES={"陀螺仪 · 角速度","加速度计 · 含重力","线性加速度 · 不含重力","重力传感器","磁场","气压","环境光","距离传感器","环境温度","相对湿度"};
    static final String[] UNITS={"rad/s","m/s²","m/s²","m/s²","μT","hPa","lx","cm","℃","%"};
    private void sensorsCard(){LinearLayout c=card("传感器");java.util.List<Integer> order=DisplayOrder.parse(a.preferences.getString("status_sensor_order",""),StatusHardware.TYPES.length);for(int index:order){int type=StatusHardware.TYPES[index];if(!a.preferences.getBoolean("status_sensor_show_"+type,true))continue;c.addView(a.text(SENSOR_NAMES[index],15,a.INK,true));a.gap(c,8);TextView data=a.text("等待结果",14,a.MUTED,false);c.addView(data);sensorViews.put(type,data);android.hardware.Sensor s=hardware.sensor(type);if(s!=null){a.gap(c,5);c.addView(a.text(s.getName(),11,a.MUTED,false));}a.gap(c,16);}if(sensorViews.isEmpty())c.addView(a.text("已在设置中隐藏所有传感器。",14,a.MUTED,false));c.addView(a.text("传感器帧率可在设置中调整，默认不限帧率，实际仍受硬件和系统调度影响。轴固定于设备自然方向，不随屏幕旋转：X 向右、Y 向上、Z 垂直屏幕向外；陀螺仪记录绕各轴的角速度，不是角度。加速度计含重力，适合检测设备整体受力和倾斜；重力传感器只给系统估算的重力方向，已滤除线性运动，适合指南针和姿态参考；线性加速度则尽量去掉重力，用于观察运动。环境温度不等于电池温度。未安装或系统未开放的传感器显示“设备未提供”。",13,a.MUTED,false));}
    private void batteryCard(){LinearLayout c=card("电池");batteryInfo=a.text("等待系统电池状态",15,a.INK,false);c.addView(batteryInfo);a.gap(c,12);c.addView(a.text("健康状态依据 Android BatteryManager.EXTRA_HEALTH：系统返回良好、过热、过冷、失效、电压过高或系统报告故障；这只是电池管理芯片的状态，不等于电池实际容量或安全保证。瞬时电流按 Android 当前值反号显示：正值表示应用层观察到放电，负值表示充电；不同厂商可能不提供或定义不同。系统未提供的字段不显示为零。",13,a.MUTED,false));}
    private void deviceCard(){LinearLayout c=card("设备与环境");deviceInfo=a.text("读取系统信息",14,a.INK,false);deviceInfo.setTextIsSelectable(true);c.addView(deviceInfo);}
    private void infoCard(){
        LinearLayout info=a.card();info.addView(a.text("状态使用说明",17,a.INK,true));a.gap(info,12);info.addView(a.text("默认只在状态页前台工作，切换栏目、锁屏或离开应用会暂停状态页接收；设置中可改为停止，回到状态页后按选择恢复或等待再次开始。设置→状态可开启“切换栏目和后台继续定位”；开启后仍需点击开始，运行时提供持续定位通知，可在通知或本页停止；未允许通知时，系统可能仅在活动应用管理中显示服务。系统省电、权限变化或强制停止仍可能中断定位；关闭此选项后回到默认行为。应用不上传经纬度。\n\n水平定位误差半径为 Android 提供的估计（约 68% 置信水平），不是实际偏差保证。\n\n卫星硬件模式：接收设备支持的卫星信号，应用无需联网，室内或遮挡处可能无法获得信号；天空图只显示设备实际报告的卫星。\n\n网络模式：需系统支持网络位置服务且保持网络连接。系统可能把 IP、基站或 Wi-Fi 信息发给其定位服务商，适用系统与服务商规则。没有网络位置提供程序的设备无法使用此模式。\n\n当系统定位结果不含海拔字段时，显示“未提供海拔”；常见于网络定位仅提供平面位置、设备或定位服务未返回高度时。这不表示海拔为零。海拔存在时为 WGS84 椭球高，不等同于海拔基准面上的正高。\n\n经纬度为 WGS84，不做地图坐标偏移。显示顺序为经度、纬度；E 为东经、W 为西经、N 为北纬、S 为南纬。地震距离在本机利用最近定位计算，未定位也能查询全球目录。自动定位间隔是请求系统更新的最短间隔，系统可能延迟或返回缓存；距上次定位时间按实际结果计算。",13,a.MUTED,false));a.gap(info,12);info.addView(a.button("打开手机定位设置",false,()->a.startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))));a.gap(info,8);info.addView(a.button("打开应用权限设置",false,()->a.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:"+a.getPackageName())))));
        a.gap(info,12);info.addView(a.text("状态页的指南针、传感器和电池读取只在本页前台工作；不读取设备序列号、IMEI、MAC、已安装应用或通信内容。卫星图只可视化设备接收信息，不提供测绘底图、测量成果、基站改正数或无线电发射。权限与数据范围详见设置 → 关于 → 数据来源与隐私。",13,a.MUTED,false));
    }
    private TextView value(LinearLayout c,String title){c.addView(a.text(title,12,a.MUTED,false));a.gap(c,8);TextView t=a.text("—",25,a.INK,true);t.setTextIsSelectable(true);c.addView(t);a.gap(c,20);return t;}
    private boolean network(){return a.preferences.getString("location_provider","gps").equals("network");}
    private String mode(){return network()?"网络定位":"卫星硬件定位";}
    void configurationChanged(){hardware.configurationChanged();lastSecond=-1;update();}
    void modeChanged(){pendingPermission=false;pendingSatellite=false;LocationService.stop(a);hardware.stopGnss("定位方式已修改，卫星接收已停止");}
    void intervalChanged(){if(LocationService.running)startService();}
    void continuousChanged(){if(LocationService.running)startService();}
    private boolean allowed(){return a.checkSelfPermission(network()?Manifest.permission.ACCESS_COARSE_LOCATION:Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;}
    void permissionResult(){if(!pendingPermission)return;if(allowed()){if(visible){pendingPermission=false;start();}}else{pendingPermission=false;LocationService.status="未授予所需定位权限；可到应用权限设置开启。";lastSecond=-1;update();}}
    void notificationPermissionResult(){if(LocationService.running&&LocationService.continuous(a))startService();if(Build.VERSION.SDK_INT>=33&&a.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)a.toast("通知未开启，可在状态页或系统活动应用管理中停止定位");}
    private void start(){
        if(!allowed()){pendingPermission=true;a.requestPermissions(network()?new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}:new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},41);return;}
        if(LocationService.continuous(a)&&Build.VERSION.SDK_INT>=33&&a.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED&&!a.preferences.getBoolean("location_notice_asked",false)){
            a.preferences.edit().putBoolean("location_notice_asked",true).apply();a.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},44);
        }
        startService();
    }
    private void startService(){try{Intent i=new Intent(a,LocationService.class);if(LocationService.continuous(a))a.startForegroundService(i);else a.startService(i);}catch(Exception e){LocationService.status="定位启动失败，请返回应用前台重新开始";a.toast(LocationService.status);}handler.removeCallbacks(tick);if(visible)handler.post(tick);}
    void resume(){visible=true;lastSecond=-1;hardware.resume(compass!=null||treasure!=null,!sensorViews.isEmpty());if(pendingPermission&&allowed()){pendingPermission=false;start();}if(batteryInfo==null&&batteryRegistered){try{a.unregisterReceiver(batteryReceiver);}catch(Exception ignored){}batteryRegistered=false;}if(batteryInfo!=null&&!batteryRegistered){try{battery=a.registerReceiver(batteryReceiver,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));batteryRegistered=true;}catch(Exception ignored){}}if(pendingSatellite&&a.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED)startSatellites();handler.removeCallbacks(tick);handler.post(tick);}
    void pause(){visible=false;hardware.pause();if(batteryRegistered){try{a.unregisterReceiver(batteryReceiver);}catch(Exception ignored){}batteryRegistered=false;}handler.removeCallbacks(tick);if(!LocationService.continuous(a)&&!pendingPermission)LocationService.stop(a);}
    void stopForNavigation(){pendingPermission=false;pendingSatellite=false;pause();}
    private void set(TextView view,String value){if(view!=null&&!value.contentEquals(view.getText()))view.setText(value);}
    private void update(){
        if(!visible)return;
        if(compass!=null)compass.invalidate();if(treasure!=null)treasure.invalidate();
        long second=SystemClock.elapsedRealtime()/1000;if(second==lastSecond)return;lastSecond=second;
        updateCompass();updateSatellites();updateTreasure();updateSensors();updateBattery();updateDevice();
        if(status==null)return;set(status,hardware.gnssRunning&&!LocationService.running?"卫星接收中 · 位置由卫星接收更新":LocationService.status);Location fix=LocationService.last(a);if(fix==null){set(longitude,"—");set(latitude,"—");set(accuracy,"—");set(altitude,"—");set(speed,"—");set(bearing,"—");set(age,"等待定位结果");set(motion,"移动方位由定位结果提供，不是设备朝向。");return;}
        set(longitude,CoordinateFormat.longitude(fix.getLongitude(),6));set(latitude,CoordinateFormat.latitude(fix.getLatitude(),6));
        set(accuracy,fix.hasAccuracy()?String.format(Locale.ROOT,"%.1f 米",fix.getAccuracy()):"未提供精度");set(altitude,fix.hasAltitude()?String.format(Locale.ROOT,"%.1f 米",fix.getAltitude()):"未提供海拔");
        set(speed,fix.hasSpeed()?String.format(Locale.ROOT,"%.2f m/s · %.1f km/h",fix.getSpeed(),fix.getSpeed()*3.6):"未提供");
        set(bearing,fix.hasBearing()?String.format(Locale.ROOT,"%.1f° · %s（真北）",fix.getBearing(),StatusMath.direction(fix.getBearing())):"未提供");
        set(motion,(fix.hasVerticalAccuracy()?String.format(Locale.ROOT,"垂直定位误差半径：%.1f 米 · ",fix.getVerticalAccuracyMeters()):"")+"移动方位由定位结果提供，不是设备朝向。");
        long seconds=LocationService.ageSeconds(fix);String actual="network".equals(fix.getProvider())?"网络定位":"卫星硬件定位";set(age,actual+" · 距上次定位"+seconds+"秒"+(seconds>30?" · 结果较旧":""));
        if(LocationService.running&&seconds>30)set(status,"等待新信号 · 下方为上次定位");
    }
    private void updateCompass(){
        if(compassInfo==null)return;Location fix=LocationService.last(a);boolean trueNorth=a.preferences.getBoolean("status_true_north",false);double heading=trueNorth?hardware.trueHeading(fix):hardware.magneticHeading;
        String quality=hardware.compassAccuracy==3?"精度较高":hardware.compassAccuracy==2?"精度中等":hardware.compassAccuracy==1?"精度较低，请校准":"方向精度不可靠，请校准";
        String info=hardware.compassSource()+" · "+quality;
        if(hardware.headingAvailable()&&Double.isFinite(heading))info+="\n朝向 "+StatusMath.direction(heading)+String.format(Locale.ROOT," · %.1f°",StatusMath.norm(heading));
        else info+="\n"+(trueNorth&&fix==null?"真北需要最近定位，请开始定位":"请平放设备并等待有效方向");
        if(Double.isFinite(hardware.pitch))info+=String.format(Locale.ROOT,"\n俯仰 %.1f°（屏幕上方向天上为正） · 横滚 %.1f°（向左负，向右正）",hardware.pitch,hardware.roll);
        if(fix!=null&&Double.isFinite(hardware.declination(fix)))info+=String.format(Locale.ROOT,"\n当前位置估算磁偏角 %.1f° · 参考定位 %d 秒前",hardware.declination(fix),LocationService.ageSeconds(fix));set(compassInfo,info);
    }
    private void updateSatellites(){if(satelliteStatus==null)return;int total=hardware.satellites.size(),used=0;for(StatusHardware.Satellite s:hardware.satellites)if(s.used)used++;
        long age=hardware.gnssAt==0?0:(SystemClock.elapsedRealtime()-hardware.gnssAt)/1000;String stamp=hardware.gnssAt==0?"":"\n最近卫星状态 "+age+" 秒前"+(age>15?" · 结果较旧，图中暂不绘制":"");
        set(satelliteStatus,hardware.gnssMessage+"\n设备报告 "+total+" 颗 · 参与定位 "+used+" 颗 · 当前显示 "+hardware.shown().size()+" 颗"+stamp);
        if(sky!=null)sky.invalidate();satelliteSelection();if(satelliteDetails!=null&&details){StringBuilder lines=new StringBuilder();for(StatusHardware.Satellite s:hardware.shown()){if(lines.length()>0)lines.append("\n\n");lines.append(satelliteLine(s));}set(satelliteDetails,lines.length()==0?"尚无设备报告的卫星":lines.toString());}
    }
    private void updateTreasure(){if(treasureInfo==null)return;StatusPlace target=places.selected();Location fix=LocationService.last(a);if(target==null){set(treasureInfo,"已保存 "+places.all().size()+" 个地点 · 请选择目标");return;}
        String info="目标："+target.name+"\n"+CoordinateFormat.pair(target.longitude,target.latitude,6);if(fix==null){set(treasureInfo,info+"\n请先获取当前位置");return;}
        double distance=StatusMath.distance(fix.getLongitude(),fix.getLatitude(),target.longitude,target.latitude),bearing=StatusMath.bearing(fix.getLongitude(),fix.getLatitude(),target.longitude,target.latitude);
        info+="\n直线距离 "+StatusMath.distanceText(distance)+(Double.isFinite(bearing)?String.format(Locale.ROOT,"\n真北方位 %.1f° · %s",bearing,StatusMath.direction(bearing)):"\n与参考位置重合，方位未定义");
        double heading=hardware.trueHeading(fix);if(Double.isFinite(heading)&&Double.isFinite(bearing)){double delta=StatusMath.delta(bearing,heading);info+=Math.abs(delta)<5?"\n目标大致在正前方":String.format(Locale.ROOT,"\n目标在%s %.0f°",delta>=0?"右侧":"左侧",Math.abs(delta));}
        info+="\n参考定位 "+LocationService.ageSeconds(fix)+" 秒前"+(LocationService.ageSeconds(fix)>30?" · 位置较旧，请刷新":"");if(fix.hasAccuracy())info+=String.format(Locale.ROOT," · 误差半径 %.1f 米",fix.getAccuracy());set(treasureInfo,info);
    }
    private void updateSensors(){for(int n=0;n<StatusHardware.TYPES.length;n++){int type=StatusHardware.TYPES[n];set(sensorViews.get(type),hardware.sensorText(type,UNITS[n]));}}
    private static String chargeStatus(int status){switch(status){case BatteryManager.BATTERY_STATUS_CHARGING:return "充电中";case BatteryManager.BATTERY_STATUS_DISCHARGING:return "放电中";case BatteryManager.BATTERY_STATUS_FULL:return "已充满";case BatteryManager.BATTERY_STATUS_NOT_CHARGING:return "未充电";default:return "状态未知";}}
    private void updateBattery(){if(batteryInfo==null)return;if(battery==null){set(batteryInfo,"系统未提供电池状态");return;}if(battery.hasExtra(BatteryManager.EXTRA_PRESENT)&&!battery.getBooleanExtra(BatteryManager.EXTRA_PRESENT,true)){set(batteryInfo,"系统报告没有电池");return;}
        int level=battery.getIntExtra(BatteryManager.EXTRA_LEVEL,-1),scale=battery.getIntExtra(BatteryManager.EXTRA_SCALE,-1),voltage=battery.getIntExtra(BatteryManager.EXTRA_VOLTAGE,-1),temperature=battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE,Integer.MIN_VALUE),plugged=battery.getIntExtra(BatteryManager.EXTRA_PLUGGED,-1),health=battery.getIntExtra(BatteryManager.EXTRA_HEALTH,0);
        String healthText=health==BatteryManager.BATTERY_HEALTH_GOOD?"良好":health==BatteryManager.BATTERY_HEALTH_OVERHEAT?"过热":health==BatteryManager.BATTERY_HEALTH_COLD?"过冷":health==BatteryManager.BATTERY_HEALTH_DEAD?"失效":health==BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE?"电压过高":health==BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE?"系统报告故障":"未提供";
        String info="剩余电量："+(level>=0&&scale>0?Math.round(level*100f/scale)+"%":"未提供")+"\n状态："+chargeStatus(battery.getIntExtra(BatteryManager.EXTRA_STATUS,0))+"\n电池温度："+(temperature!=Integer.MIN_VALUE?String.format(Locale.ROOT,"%.1f ℃",temperature/10.0):"未提供")+"\n电压："+(voltage>0?String.format(Locale.ROOT,"%.3f V",voltage/1000.0):"未提供")+"\n健康状态："+healthText;
        String source=plugged<0?"未提供":plugged==0?"未接电源":(plugged&BatteryManager.BATTERY_PLUGGED_AC)!=0?"交流电源":(plugged&BatteryManager.BATTERY_PLUGGED_USB)!=0?"USB":(plugged&BatteryManager.BATTERY_PLUGGED_WIRELESS)!=0?"无线充电":"其他电源";info+="\n供电方式："+source;
        String technology=battery.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY);if(technology!=null&&!technology.isEmpty())info+="\n电池技术："+technology;
        BatteryManager bm=(BatteryManager)a.getSystemService(Context.BATTERY_SERVICE);if(bm!=null){int current=bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW),charge=bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);if(current!=Integer.MIN_VALUE)info+=String.format(Locale.ROOT,"\n瞬时电流：%.1f mA",-current/1000.0);if(charge!=Integer.MIN_VALUE&&charge>=0)info+=String.format(Locale.ROOT,"\n剩余电荷：%.0f mAh（非设计容量）",charge/1000.0);}
        set(batteryInfo,info);
    }
    private void updateDevice(){if(deviceInfo==null)return;String info="设备："+Build.MANUFACTURER+" "+Build.MODEL+"\nAndroid "+Build.VERSION.RELEASE+" · API "+Build.VERSION.SDK_INT+"\n安全更新："+Build.VERSION.SECURITY_PATCH;
        try{ActivityManager am=(ActivityManager)a.getSystemService(Context.ACTIVITY_SERVICE);ActivityManager.MemoryInfo m=new ActivityManager.MemoryInfo();am.getMemoryInfo(m);info+="\n内存可用 / 总量："+bytes(m.availMem)+" / "+bytes(m.totalMem);StatFs fs=new StatFs(a.getFilesDir().getPath());info+="\n应用所在存储可用 / 总量："+bytes(fs.getAvailableBytes())+" / "+bytes(fs.getTotalBytes());}catch(Exception ignored){}
        long seconds=SystemClock.elapsedRealtime()/1000;info+="\n开机时长（含休眠）："+(seconds/86400)+" 天 "+(seconds/3600%24)+" 时 "+(seconds/60%60)+" 分";
        android.util.DisplayMetrics dm=a.getResources().getDisplayMetrics();info+="\n当前窗口："+dm.widthPixels+" × "+dm.heightPixels+" px · "+dm.densityDpi+" dpi";
        set(deviceInfo,info);
    }
    private static String bytes(long bytes){return String.format(Locale.ROOT,"%.2f GiB",bytes/1073741824.0);}
}

