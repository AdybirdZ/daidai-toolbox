package org.simpleweather.app;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.location.*;
import android.os.*;
import android.provider.Settings;
import android.widget.*;
import java.util.ArrayList;
import java.util.Locale;

/** Location service control and display. The Activity never owns background callbacks. */
final class GpsScreen {
    private final MainActivity a;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private TextView status,latitude,longitude,accuracy,altitude,verticalAccuracy,age,speed,bearing,motion,locationToggle,satelliteToggle;
    private boolean visible,pendingPermission,locationStarting;
    private final Runnable tick=new Runnable(){public void run(){if(visible){long next=SystemClock.uptimeMillis()+50;update();handler.postAtTime(this,next);}}};
    GpsScreen(MainActivity activity){a=activity;hardware=new StatusHardware(a);places=new StatusPlaces(a,()->{lastSecond=-1;update();});routeStore=new RouteStore(a);}
    static final String[] SECTION_NAMES={"指南针","当前位置","定位卫星","地点","传感器","电池","设备与环境","使用说明","路线绘制"};
    private final StatusHardware hardware;
    private final StatusPlaces places;
    private final RouteStore routeStore;
    private RouteCanvas routeCanvas; private TextView routeInfo,routeToggle,playToggle,previousPoint,nextPoint,firstPoint,lastPoint;
    private LinearLayout replayControls;private SeekBar routeZoom;
    private final RoutePlayback replay=new RoutePlayback();
    private String routeStatus="尚未开始记录路线",replayName="";
    private ArrayList<RoutePoint> displayedRoute=new ArrayList<>();
    private StatusViews.Compass compass;private StatusViews.Treasure treasure;private StatusViews.Sky sky;
    private TextView compassInfo,pitchView,rollView,satelliteStatus,satelliteInfo,satelliteDetails,treasureInfo,batteryInfo,deviceInfo;
    private LinearLayout satelliteDetailBox;private boolean details,pendingSatellite,batteryRegistered;
    private Intent battery;
    private long lastSecond=-1;
    private String selectedSatellite="";
    private final java.util.Map<Integer,TextView> sensorViews=new java.util.HashMap<>();
    private final java.util.Map<Integer,TextView> batteryViews=new java.util.HashMap<>();
    private final java.util.Map<Integer,TextView> deviceViews=new java.util.HashMap<>();
    static final String[] BATTERY_LABELS={"剩余电量","状态","电池温度","电压","健康状态","供电方式","电池类型","瞬时电流","剩余电荷"};
    static final String[] DEVICE_LABELS={"设备","Android","安全更新","内存可用 / 总量","应用所在存储可用 / 总量","开机时长（含休眠）","当前窗口"};
    private final BroadcastReceiver batteryReceiver=new BroadcastReceiver(){public void onReceive(Context c,Intent i){battery=i;}};
    void render(){
        status=latitude=longitude=accuracy=altitude=verticalAccuracy=locationToggle=satelliteToggle=age=speed=bearing=motion=compassInfo=pitchView=rollView=satelliteStatus=satelliteInfo=satelliteDetails=treasureInfo=batteryInfo=deviceInfo=null;
        routeCanvas=null;routeInfo=null;routeToggle=null;replayControls=null;routeZoom=null;compass=null;treasure=null;sky=null;satelliteDetailBox=null;sensorViews.clear();batteryViews.clear();deviceViews.clear();lastSecond=-1;
        for(int id:DisplayOrder.status(a.preferences.getString("status_order",""))){
            if(!a.preferences.getBoolean("status_show_"+id,true))continue;
            switch(id){case 0:compassCard();break;case 1:locationCard();break;case 2:satellitesCard();break;case 3:placesCard();break;case 4:sensorsCard();break;case 5:batteryCard();break;case 6:deviceCard();break;case 7:infoCard();break;case 8:routeCard();break;}
            a.gap(a.body,16);
        }
        if(sky==null){pendingSatellite=false;hardware.stopGnss("卫星区域已隐藏，接收已停止");}
        resume();
    }
    private LinearLayout card(String title){LinearLayout c=a.card();c.addView(a.text(title,18,a.INK,true));a.gap(c,12);return c;}
    private void compassCard(){LinearLayout c=card("指南针");compass=new StatusViews.Compass(a,hardware);c.addView(compass,a.lp(-1,-2));compassInfo=a.text("等待方向传感器",14,a.MUTED,false);c.addView(compassInfo);a.gap(c,10);pitchView=a.text("俯仰 —",25,a.INK,true);rollView=a.text("横滚 —",25,a.INK,true);c.addView(pitchView);a.gap(c,6);c.addView(rollView);a.gap(c,12);c.addView(a.text("以屏幕上沿指向为设备朝向。使用时请平放设备，远离磁铁、金属及电器；必要时缓慢沿“8”字轨迹移动设备以校准。真北方向需根据最近定位估算磁偏角。屏幕上沿抬起时俯仰角为正；向左横滚为负，向右为正。",13,a.MUTED,false));}
    private void locationCard(){
        LinearLayout c=a.card();c.addView(a.text("当前位置",18,a.INK,true));a.gap(c,12);status=a.text(LocationService.status,14,a.MUTED,false);c.addView(status);a.gap(c,22);
        longitude=value(c,"经度 · WGS84");latitude=value(c,"纬度 · WGS84");accuracy=value(c,"水平定位误差半径");altitude=value(c,"海拔 · WGS84 椭球高");verticalAccuracy=value(c,"垂直定位误差半径");speed=value(c,"地面速度");bearing=value(c,"移动方位");age=a.text("等待定位结果",12,a.MUTED,false);c.addView(age);a.gap(c,10);motion=a.text("移动方位由定位结果提供，不是设备朝向。",13,a.MUTED,false);c.addView(motion);a.gap(c,16);
        locationToggle=a.button(LocationService.running?"停止定位":"开始定位",true,()->{if(LocationService.running||locationStarting||pendingPermission)stopLocation();else start();});c.addView(locationToggle);a.gap(c,12);c.addView(a.text("当前方式："+mode()+" · 设置中可切换",12,a.MUTED,false));a.gap(a.body,16);
    }
    private void satellitesCard(){LinearLayout c=card("定位卫星 · 设备接收状态");satelliteStatus=a.text(hardware.gnssMessage,14,a.MUTED,false);c.addView(satelliteStatus);a.gap(c,12);
        satelliteToggle=a.button(hardware.gnssRunning?"停止卫星接收":"开始卫星接收",true,()->{if(hardware.gnssRunning||pendingSatellite){pendingSatellite=false;hardware.stopGnss("卫星接收已停止");lastSecond=-1;update();}else startSatellites();});c.addView(satelliteToggle);a.gap(c,12);
        sky=new StatusViews.Sky(a,hardware,this::satelliteSelection);sky.selected=selectedSatellite;c.addView(sky,a.lp(-1,-2));
        c.addView(a.text("关闭方向跟随后，天空图固定为上北下南左西右东；开启后随设备朝向旋转。圆周为高度角 0°，中心为 90°；方位角以真北为 0°，顺时针递增。颜色区分卫星系统，实心表示参与当前定位解算，空心表示未参与。点击卫星可查看参数。",13,a.MUTED,false));a.gap(c,12);
        satelliteInfo=a.text("尚未选中卫星",14,a.INK,false);c.addView(satelliteInfo);a.gap(c,12);
        c.addView(a.button("展开或收起卫星明细",false,()->{details=!details;if(satelliteDetailBox!=null)satelliteDetailBox.setVisibility(details?android.view.View.VISIBLE:android.view.View.GONE);lastSecond=-1;update();}));a.gap(c,10);satelliteDetailBox=a.column();satelliteDetails=a.text("等待设备数据",13,a.INK,false);satelliteDetailBox.addView(satelliteDetails);satelliteDetailBox.setVisibility(details?android.view.View.VISIBLE:android.view.View.GONE);c.addView(satelliteDetailBox);
        a.gap(c,12);c.addView(a.text("仅显示设备芯片和 Android 系统报告的卫星，不推算完整卫星分布。读取卫星状态须授予精确定位权限；开始卫星接收时会同时启动当前位置更新，采用状态设置中的定位方式。停止卫星接收不会停止已启动的定位；停止定位则同时停止卫星接收。离开状态页后，卫星接收和持续定位分别按各自设置处理。系统辅助定位可能联网，本应用不下载星历。",13,a.MUTED,false));
    }
    private void startSatellites(){if(a.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){pendingSatellite=true;a.requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},46);return;}pendingSatellite=false;if(hardware.startGnss()&&!LocationService.running&&!locationStarting)start();lastSecond=-1;update();}
    void satellitePermissionResult(){if(!pendingSatellite)return;if(a.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){pendingSatellite=false;hardware.gnssMessage="未授予精确定位，不能读取卫星状态";a.toast(hardware.gnssMessage);}else if(visible)startSatellites();}
    void placesPermissionResult(){places.permissionResult();}
    private void satelliteSelection(){if(satelliteInfo==null||sky==null)return;selectedSatellite=sky.selected;for(StatusHardware.Satellite s:hardware.shown())if(s.key().equals(sky.selected)){set(satelliteInfo,satelliteLine(s));return;}set(satelliteInfo,sky.selected.isEmpty()?"尚未选中卫星":"所选卫星当前未报告或已被隐藏");}
    private String satelliteLine(StatusHardware.Satellite s){return s.label()+"\n高度角 "+(s.validAngles()?String.format(Locale.ROOT,"%.1f° · 方位角 %.1f° %s",s.elevation,s.azimuth,StatusMath.direction(s.azimuth))+(s.elevation<0?" · 地平线以下":""):"未提供有效角度")+"\n载噪密度比 "+(Float.isFinite(s.cn0)&&s.cn0>0?String.format(Locale.ROOT,"%.1f dB-Hz",s.cn0):"未提供")+" · "+(s.used?"参与本次定位":"未参与本次定位");}
    private void placesCard(){LinearLayout c=card("地点");treasureInfo=a.text("请选择保存的地点",14,a.INK,false);c.addView(treasureInfo);a.gap(c,12);treasure=new StatusViews.Treasure(a,hardware,places);c.addView(treasure,a.lp(-1,-2));
        c.addView(a.button("保存当前位置",true,places::addCurrent));a.gap(c,10);c.addView(a.button("手动添加地点",false,()->places.edit(null,null)));a.gap(c,10);c.addView(a.button("选择 / 管理地点",false,places::manage));a.gap(c,12);
        c.addView(a.text("各地点按所设颜色显示，圆环标记所选目标，中心表示当前位置，外圈比例按可见地点的最远距离自动调整。可在地点管理中设置颜色及显示状态，点击图中地点可选为目标。方向传感器与最近定位有效时，图形随设备朝向旋转；否则真北向上。距离为地表大圆距离，方位为起点处的初始方位角，不包含道路或障碍物信息。定位与磁场误差会影响结果，不用于安全导航。地点仅保存在本机，清除缓存或恢复默认设置不会删除；卸载应用将删除这些数据。",13,a.MUTED,false));
    }
    private void routeCard(){
        LinearLayout c=card("路线绘制");routeInfo=a.text(routeStatus,14,a.INK,false);c.addView(routeInfo);a.gap(c,10);
        routeCanvas=new RouteCanvas(a);routeCanvas.playback(replay.loaded());if(RouteRecorder.active())displayedRoute=RouteRecorder.points();
        routeCanvas.setRoute(displayedRoute,replay.loaded()?replay.index()+1:displayedRoute.size(),true);c.addView(routeCanvas,a.lp(-1,320));a.gap(c,10);
        LinearLayout actions=a.row();routeToggle=a.button(RouteRecorder.active()?"结束绘制":"开始绘制",true,()->{if(RouteRecorder.active())stopRoute();else startRoute();});
        actions.addView(routeToggle,new LinearLayout.LayoutParams(0,-2,1));a.gapHorizontal(actions,8);actions.addView(a.button("以当前位置为中心",false,()->routeCanvas.centerSelf()),new LinearLayout.LayoutParams(0,-2,1));c.addView(actions);a.gap(c,8);
        LinearLayout zoomRow=a.row();zoomRow.addView(a.text("缩放",14,a.INK,false));routeZoom=new SeekBar(a);routeZoom.setMax(320);routeZoom.setProgress(80);routeZoom.setContentDescription("路线缩放");
        routeZoom.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int n,boolean user){if(routeCanvas!=null)routeCanvas.zoom((float)Math.pow(2,(n-80)/40.0));}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}});
        zoomRow.addView(routeZoom,new LinearLayout.LayoutParams(0,a.dp(48),1));c.addView(zoomRow);a.gap(c,8);
        c.addView(a.button("查看 / 回放已保存路线",false,this::routeList));a.gap(c,8);
        replayControls=a.column();LinearLayout transport=a.row();
        firstPoint=replayButton(transport,"◀◀","跳到开头",()->seekRoute(0));
        previousPoint=replayButton(transport,"◀","上一个记录点",()->seekRoute(replay.index()-1));
        playToggle=replayButton(transport,"播放","播放路线",this::togglePlayback);
        nextPoint=replayButton(transport,"▶","下一个记录点",()->seekRoute(replay.index()+1));
        lastPoint=replayButton(transport,"▶▶","跳到最后",()->seekRoute(replay.size()-1));
        replayControls.addView(transport);a.gap(replayControls,8);
        TextView rate=a.text("回放速度 · "+replay.speed()+" 个记录点/秒",14,a.INK,false);replayControls.addView(rate);
        SeekBar rateSlider=new SeekBar(a);rateSlider.setMax(19);rateSlider.setProgress(replay.speed()-1);rateSlider.setContentDescription("回放速度，1 至 20 个记录点每秒");rateSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int n,boolean user){replay.speed(n+1,SystemClock.elapsedRealtime());rate.setText("回放速度 · "+(n+1)+" 个记录点/秒");updateReplay();}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}});replayControls.addView(rateSlider,a.lp(-1,48));c.addView(replayControls);
        a.gap(c,10);TextParagraphs.append(a,c,"单指拖动可平移路线，拖动缩放滑块可放大或缩小；缩放以路线框中心为基准，不支持双指缩放。点击记录点可查看序号、坐标和记录时刻。\n\n选择已保存路线后，首先显示完整路线；点击播放后从起点逐点显示。回放时点击“以当前位置为中心”将持续跟随橙色回放点，拖动画布可取消跟随。暂停时可逐点切换或跳转至首尾记录点，跳转后以该点为中心。\n\n默认每 5 秒记录一次定位结果，可在状态设置中调整为 2～60 秒；使用网络定位时，有效记录间隔不短于网络定位间隔。开始绘制前须开启持续定位并开始定位。未收到有效定位结果时暂停记录，恢复后继续。路线仅保存在本机。",13,a.MUTED);
        if(replay.loaded())updateReplay();else updateLiveRoute();
    }
    private TextView replayButton(LinearLayout row,String label,String description,Runnable action){if(row.getChildCount()>0)a.gapHorizontal(row,6);TextView b=a.button(label,false,action);b.setTextSize(16);b.setPadding(a.dp(2),a.dp(10),a.dp(2),a.dp(10));b.setContentDescription(description);row.addView(b,new LinearLayout.LayoutParams(0,-2,1));return b;}
    private void startRoute(){
        if(!a.preferences.getBoolean("location_continuous",false)||!LocationService.running){a.toast("请先在状态设置中开启持续定位，并开始定位");return;}
        replay.load(0);if(routeCanvas!=null)routeCanvas.playback(false);displayedRoute.clear();RouteRecorder.start(a.preferences.getInt("route_interval",5));routeStatus="正在记录路线 · 等待定位点";
        if(routeCanvas!=null){routeCanvas.setRoute(displayedRoute,0,true);routeZoom.setProgress(80);}updateLiveRoute();
    }
    private void updateLiveRoute(){
        if(replayControls!=null)replayControls.setVisibility(android.view.View.GONE);
        if(RouteRecorder.active()){
            if(routeCanvas!=null&&routeCanvas.pointCount()!=RouteRecorder.size()){displayedRoute=RouteRecorder.points();routeCanvas.setRoute(displayedRoute,displayedRoute.size(),routeCanvas.pointCount()==0);}
            routeStatus=displayedRoute.isEmpty()?"正在记录路线 · 等待定位点":"正在记录路线 · "+displayedRoute.size()+" 个记录点";
        }
        set(routeInfo,routeStatus);set(routeToggle,RouteRecorder.active()?"结束绘制":"开始绘制");
    }
    private void stopRoute(){
        RouteRecorder.stop();ArrayList<RoutePoint> pts=RouteRecorder.points();displayedRoute=new ArrayList<>(pts);routeStatus="路线绘制已结束 · "+pts.size()+" 个记录点";
        if(routeCanvas!=null)routeCanvas.setRoute(pts,pts.size(),false);updateLiveRoute();
        if(pts.isEmpty()){a.toast("未收到有效定位点");return;}
        LinearLayout form=a.column();form.setPadding(a.dp(24),a.dp(8),a.dp(24),a.dp(8));
        EditText name=new EditText(a);name.setSingleLine(true);name.setHint("例如：晚间跑步");form.addView(name,a.lp(-1,-2));
        AlertDialog saveDialog=new ToolDialog(a).setTitle("保存路线").setMessage("本次路线共有 "+pts.size()+" 个记录点。输入名称后保存，也可以删除本次路线。路线仅保存在本机。")
            .setView(form).setPositiveButton("保存",(d,w)->{String n=name.getText().toString().trim();routeStore.save(n.isEmpty()?"未命名路线":n,pts);routeStatus="路线已保存 · "+pts.size()+" 个记录点";updateLiveRoute();})
            .setNeutralButton("删除",null).setNegativeButton("取消",null).show();
        saveDialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->confirmRouteDeletion(()->{saveDialog.dismiss();displayedRoute.clear();routeStatus="本次路线已删除";if(routeCanvas!=null)routeCanvas.setRoute(displayedRoute,0,true);updateLiveRoute();}));
    }
    private void confirmRouteDeletion(Runnable remove){new ToolDialog(a).setTitle("删除路线").setMessage("确定删除此路线吗？").setPositiveButton("删除",(d,w)->remove.run()).setNegativeButton("取消",null).show();}
    private void routeList(){
        if(RouteRecorder.active()){a.toast("请先结束当前绘制，再查看已保存路线");return;}
        LinearLayout c=a.column();c.setPadding(a.dp(20),a.dp(8),a.dp(20),a.dp(8));ArrayList<RouteStore.Saved> rows=routeStore.all();
        final AlertDialog[] dialog=new AlertDialog[1];if(rows.isEmpty())c.addView(a.text("暂无已保存路线。",14,a.MUTED,false));
        for(RouteStore.Saved row:rows){LinearLayout r=a.row();TextView open=a.button(row.name+" · "+row.points.size()+" 点",false,()->{
            dialog[0].dismiss();displayedRoute=new ArrayList<>(row.points);replayName=row.name;replay.load(row.points.size());
            if(routeCanvas!=null){routeCanvas.setRoute(displayedRoute,displayedRoute.size(),true);routeZoom.setProgress(80);}updateReplay();
        });r.addView(open,new LinearLayout.LayoutParams(0,-2,1));a.gapHorizontal(r,8);r.addView(a.button("删除",false,()->confirmRouteDeletion(()->{routeStore.remove(row.id);dialog[0].dismiss();a.toast("路线已删除");routeList();})),a.lp(72,48));c.addView(r);a.gap(c,8);}
        ScrollView scroll=new ScrollView(a);scroll.addView(c);dialog[0]=new ToolDialog(a).setTitle("已保存路线").setView(scroll).setNegativeButton("返回",null).show();
    }
    private void seekRoute(int index){if(replay.playing())return;replay.seek(index);if(routeCanvas!=null)routeCanvas.centerOn(replay.index());updateReplay();}
    private void togglePlayback(){
        if(replay.playing())replay.pause();else{
            boolean follow=routeCanvas!=null&&routeCanvas.following();boolean restart=replay.index()>=replay.size()-1;replay.play(SystemClock.elapsedRealtime());
            if(restart&&routeCanvas!=null){routeCanvas.setRoute(displayedRoute,replay.index()+1,true);routeZoom.setProgress(80);if(follow)routeCanvas.centerSelf();}
        }
        updateReplay();
    }
    private void updateReplay(){
        if(replayControls==null)return;replayControls.setVisibility(replay.loaded()?android.view.View.VISIBLE:android.view.View.GONE);if(!replay.loaded())return;
        if(routeCanvas!=null){routeCanvas.playback(true);routeCanvas.showThrough(replay.index());}
        set(playToggle,replay.playing()?"暂停":"播放");playToggle.setContentDescription(replay.playing()?"暂停路线":"播放路线");
        boolean paused=!replay.playing();setReplayEnabled(firstPoint,paused&&replay.index()>0);setReplayEnabled(previousPoint,paused&&replay.index()>0);setReplayEnabled(nextPoint,paused&&replay.index()<replay.size()-1);setReplayEnabled(lastPoint,paused&&replay.index()<replay.size()-1);
        routeStatus="回放："+replayName+" · "+(replay.index()+1)+" / "+replay.size()+" 个记录点 · "+(replay.playing()?"播放中":"已暂停");set(routeInfo,routeStatus);
    }
    private void setReplayEnabled(TextView b,boolean enabled){b.setEnabled(enabled);b.setAlpha(enabled?1f:.35f);}
    static final String[] SENSOR_NAMES={"陀螺仪 · 角速度","加速度计 · 含重力","线性加速度 · 不含重力","重力传感器","磁场","气压","环境光","距离传感器","环境温度","相对湿度"};
    static final String[] UNITS={"rad/s","m/s²","m/s²","m/s²","μT","hPa","lx","cm","℃","%"};
    private void sensorsCard(){LinearLayout c=card("传感器");java.util.List<Integer> order=DisplayOrder.parse(a.preferences.getString("status_sensor_order",""),StatusHardware.TYPES.length);for(int index:order){int type=StatusHardware.TYPES[index];if(!a.preferences.getBoolean("status_sensor_show_"+type,true))continue;c.addView(a.text(SENSOR_NAMES[index],15,a.INK,true));a.gap(c,8);TextView data=a.text("等待结果",14,a.MUTED,false);c.addView(data);sensorViews.put(type,data);android.hardware.Sensor s=hardware.sensor(type);if(s!=null){a.gap(c,5);c.addView(a.text(s.getName(),11,a.MUTED,false));}a.gap(c,16);}if(sensorViews.isEmpty())c.addView(a.text("已在设置中隐藏所有传感器。",14,a.MUTED,false));TextParagraphs.append(a,c,"本应用以尽可能高的频率请求传感器数据，界面目标刷新间隔为 50 毫秒；实际频率取决于硬件和系统调度，无新数据时保留最近结果。\n\n传感器坐标轴固定于设备自然方向，不随界面旋转：X 轴向右，Y 轴向上，Z 轴垂直屏幕向外。陀螺仪提供绕各轴的角速度，不直接提供转角。加速度计读数包含重力影响，可用于运动和倾斜检测；重力传感器提供重力加速度的估计值，用于姿态参考；线性加速度传感器尽量去除重力影响，用于观察设备运动。\n\n环境温度与电池温度含义不同。设备未配备或系统未开放的传感器显示“设备未提供”。",13,a.MUTED);}
    private void batteryCard(){LinearLayout c=card("电池");java.util.List<Integer> order=DisplayOrder.parse(a.preferences.getString("status_battery_order",""),BATTERY_LABELS.length);for(int id:order){if(!a.preferences.getBoolean("status_battery_show_"+id,true))continue;c.addView(a.text(BATTERY_LABELS[id],12,a.MUTED,false));a.gap(c,6);TextView value=a.text("等待系统电池状态",15,a.INK,true);batteryViews.put(id,value);c.addView(value);a.gap(c,16);}if(batteryViews.isEmpty())c.addView(a.text("已在设置中隐藏所有电池信息。",14,a.MUTED,false));c.addView(a.text("健康状态由 Android 系统提供，不代表实际剩余容量，也不构成电池安全保证。瞬时电流的正负号可在状态设置中调整，不同厂商定义不同。系统未提供的数据不显示为零。",13,a.MUTED,false));}
    private void deviceCard(){LinearLayout c=card("设备与环境");java.util.List<Integer> order=DisplayOrder.parse(a.preferences.getString("status_device_order",""),DEVICE_LABELS.length);for(int id:order){if(!a.preferences.getBoolean("status_device_show_"+id,true))continue;c.addView(a.text(DEVICE_LABELS[id],12,a.MUTED,false));a.gap(c,6);TextView value=a.text("读取系统信息",14,a.INK,false);deviceViews.put(id,value);c.addView(value);a.gap(c,16);}if(deviceViews.isEmpty())c.addView(a.text("已在设置中隐藏所有设备信息。",14,a.MUTED,false));}
    private void infoCard(){
        LinearLayout info=a.card();info.addView(a.text("状态使用说明",17,a.INK,true));a.gap(info,12);TextParagraphs.append(a,info,"指南针、传感器及电池信息仅在状态页处于前台时读取。已开始的卫星接收默认在切换栏目、锁屏或进入后台时暂停，返回后自动恢复；关闭自动继续选项后，须手动开始接收。\n\n如需离开状态页后持续定位，可在“设置 → 状态”中开启“切换栏目、锁屏和后台持续定位”，然后手动开始定位。运行期间可通过通知或状态页停止定位。未允许通知时，可通过系统活动应用管理查看服务。系统省电策略、权限变更或强制停止可能中断定位。\n\n水平和垂直定位误差由 Android 系统提供，通常对应约 68% 的置信水平，并非实际误差的上限保证。垂直误差表示高度方向的不确定范围；未提供时显示“未提供垂直精度”。海拔字段采用 WGS84 椭球高，不等同于以海平面为基准的正高。系统未返回高度时显示“未提供海拔”，不表示海拔为零。\n\n卫星硬件定位使用设备支持的卫星信号，应用无需联网；室内或遮挡环境可能无法获得有效结果。天空图仅显示设备实际报告的卫星，系统辅助定位可能使用网络。\n\n网络定位依赖系统网络位置服务及网络连接。系统可能向定位服务商发送 IP、基站或 Wi-Fi 信息，适用系统及服务商的规则。设备未提供网络位置服务时，无法使用此方式。网络定位间隔为请求系统更新的最短间隔，实际更新可能延迟或复用已有结果。\n\n坐标采用 WGS84，按经度、纬度的顺序显示：E 为东经，W 为西经，N 为北纬，S 为南纬。应用不上传定位坐标；地震距离和地点方位均在本机计算。距上次定位的时间按定位结果的实际时刻计算。",13,a.MUTED);
        info.addView(a.text("本应用不读取设备序列号、IMEI、MAC 地址、已安装应用列表或通信内容。卫星图仅显示设备接收状态，不提供测绘底图、基站改正数据或无线电发射功能。权限及数据使用范围见“设置 → 关于 → 数据来源与隐私”。",13,a.MUTED,false));
        a.gap(info,12);info.addView(a.button("打开手机定位设置",false,()->a.startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))));a.gap(info,8);info.addView(a.button("打开应用权限设置",false,()->a.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:"+a.getPackageName())))));
    }
    private TextView value(LinearLayout c,String title){c.addView(a.text(title,12,a.MUTED,false));a.gap(c,8);TextView t=a.text("—",25,a.INK,true);c.addView(t);a.gap(c,20);return t;}
    private boolean network(){return a.preferences.getString("location_provider","gps").equals("network");}
    private String mode(){return network()?"网络定位":"卫星硬件定位";}
    void configurationChanged(){hardware.configurationChanged();lastSecond=-1;update();}
    void modeChanged(){locationStarting=false;pendingPermission=false;pendingSatellite=false;LocationService.stop(a);hardware.stopGnss("定位方式已修改，卫星接收已停止");}
    void intervalChanged(){if(LocationService.running)startService();}
    void continuousChanged(){if(LocationService.running)startService();}
    private boolean allowed(){return a.checkSelfPermission(network()?Manifest.permission.ACCESS_COARSE_LOCATION:Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;}
    void permissionResult(){if(!pendingPermission)return;if(allowed()){if(visible){pendingPermission=false;start();}}else{pendingPermission=false;LocationService.status="未授予所需定位权限；可到应用权限设置开启。";lastSecond=-1;update();}}
    void notificationPermissionResult(){if(LocationService.running&&LocationService.continuous(a))startService();if(Build.VERSION.SDK_INT>=33&&a.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)a.toast("通知未开启，可在状态页或系统活动应用管理中停止定位");}
    private void stopLocation(){pendingPermission=false;pendingSatellite=false;locationStarting=false;LocationService.stop(a);hardware.stopGnss("卫星接收已停止");lastSecond=-1;update();}
    private void start(){
        if(!allowed()){pendingPermission=true;a.requestPermissions(network()?new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}:new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},41);return;}
        if(LocationService.continuous(a)&&Build.VERSION.SDK_INT>=33&&a.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED&&!a.preferences.getBoolean("location_notice_asked",false)){
            a.preferences.edit().putBoolean("location_notice_asked",true).apply();a.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},44);
        }
        startService();
    }
    private void startService(){locationStarting=true;LocationService.status="正在启动定位…";try{Intent i=new Intent(a,LocationService.class);if(LocationService.continuous(a))a.startForegroundService(i);else a.startService(i);}catch(Exception e){locationStarting=false;LocationService.status="定位启动失败，请返回应用前台重新开始";a.toast(LocationService.status);}handler.removeCallbacks(tick);if(visible)handler.post(tick);}
    void resume(){visible=true;if(replay.loaded())updateReplay();lastSecond=-1;hardware.resume(compass!=null||treasure!=null,!sensorViews.isEmpty());if(hardware.gnssRunning&&!LocationService.running&&!locationStarting)start();if(pendingPermission&&allowed()){pendingPermission=false;start();}if(batteryViews.isEmpty()&&batteryRegistered){try{a.unregisterReceiver(batteryReceiver);}catch(Exception ignored){}batteryRegistered=false;}if(!batteryViews.isEmpty()&&!batteryRegistered){try{battery=a.registerReceiver(batteryReceiver,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));batteryRegistered=true;}catch(Exception ignored){}}if(pendingSatellite&&a.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED)startSatellites();handler.removeCallbacks(tick);handler.post(tick);}
    void pause(){replay.pause();locationStarting=false;visible=false;hardware.pause();if(batteryRegistered){try{a.unregisterReceiver(batteryReceiver);}catch(Exception ignored){}batteryRegistered=false;}handler.removeCallbacks(tick);if(!LocationService.continuous(a)&&!pendingPermission)LocationService.stop(a);}
    void stopForNavigation(){pendingPermission=false;pendingSatellite=false;pause();}
    private void set(TextView view,String value){if(view!=null&&!value.contentEquals(view.getText()))view.setText(value);}
    private void update(){
        if(!visible)return;
        if(locationStarting&&(LocationService.running||!"正在启动定位…".equals(LocationService.status)))locationStarting=false;
        set(locationToggle,LocationService.running||locationStarting||pendingPermission?"停止定位":"开始定位");
        set(satelliteToggle,hardware.gnssRunning||pendingSatellite?"停止卫星接收":"开始卫星接收");
        if(compass!=null)compass.invalidate();if(treasure!=null)treasure.invalidate();if(routeCanvas!=null){if(RouteRecorder.active())updateLiveRoute();else if(replay.advance(SystemClock.elapsedRealtime()))updateReplay();}
        a.updateReadingValues(()->{updateCompass();updateSatellites();updateTreasure();updateSensors();updateBattery();updateDevice();});
        // Position refresh stays separate from the 50 ms device/sensor display loop.
        long second=SystemClock.elapsedRealtime()/1000;if(second==lastSecond)return;lastSecond=second;
        if(status==null)return;set(status,LocationService.status);Location fix=LocationService.last(a);if(fix==null){set(longitude,"—");set(latitude,"—");set(accuracy,"—");set(altitude,"—");set(verticalAccuracy,"—");set(speed,"—");set(bearing,"—");set(age,"等待定位结果");set(motion,"移动方位由定位结果提供，不是设备朝向。");return;}
        set(longitude,CoordinateFormat.longitude(fix.getLongitude(),6));set(latitude,CoordinateFormat.latitude(fix.getLatitude(),6));
        set(accuracy,fix.hasAccuracy()?String.format(Locale.ROOT,"%.1f 米",fix.getAccuracy()):"未提供精度");set(altitude,fix.hasAltitude()?String.format(Locale.ROOT,"%.1f 米",fix.getAltitude()):"未提供海拔");
        set(verticalAccuracy,fix.hasVerticalAccuracy()?String.format(Locale.ROOT,"%.1f 米",fix.getVerticalAccuracyMeters()):"未提供垂直精度");
        set(speed,fix.hasSpeed()?String.format(Locale.ROOT,"%.2f m/s · %.1f km/h",fix.getSpeed(),fix.getSpeed()*3.6):"未提供");
        set(bearing,fix.hasBearing()?String.format(Locale.ROOT,"%.1f° · %s（真北）",fix.getBearing(),StatusMath.direction(fix.getBearing())):"未提供");
        set(motion,"移动方位由定位结果提供，不是设备朝向。");
        long seconds=LocationService.ageSeconds(fix);String actual="network".equals(fix.getProvider())?"网络定位":"卫星硬件定位";set(age,actual+" · 距上次定位 "+seconds+" 秒"+(seconds>30?" · 结果较旧":""));
        if(LocationService.running&&seconds>30)set(status,"等待新信号 · 下方为上次定位");
    }
    private void updateCompass(){
        if(compassInfo==null)return;Location fix=LocationService.last(a);boolean trueNorth=a.preferences.getBoolean("status_true_north",false);double heading=trueNorth?hardware.trueHeading(fix):hardware.magneticHeading;
        String quality=hardware.compassAccuracy==3?"精度较高":hardware.compassAccuracy==2?"精度中等":hardware.compassAccuracy==1?"精度较低，请校准":"方向精度不可靠，请校准";
        String info=hardware.compassSource()+" · "+quality;
        if(hardware.headingAvailable()&&Double.isFinite(heading))info+="\n朝向 "+StatusMath.direction(heading)+String.format(Locale.ROOT," · %.1f°",StatusMath.norm(heading));
        else info+="\n"+(trueNorth&&fix==null?"显示真北方向需要最近定位，请开始定位":"请平放设备，等待有效方向数据");
        if(pitchView!=null)set(pitchView,Double.isFinite(hardware.pitch)?String.format(Locale.ROOT,"俯仰 %.1f°",hardware.pitch):"俯仰 —");
        if(rollView!=null)set(rollView,Double.isFinite(hardware.roll)?String.format(Locale.ROOT,"横滚 %.1f°",hardware.roll):"横滚 —");
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
        info+="\n地表距离 "+StatusMath.distanceText(distance)+(Double.isFinite(bearing)?String.format(Locale.ROOT,"\n真北方位 %.1f° · %s",bearing,StatusMath.direction(bearing)):"\n与参考位置重合，方位未定义");
        double heading=hardware.trueHeading(fix);if(Double.isFinite(heading)&&Double.isFinite(bearing)){double delta=StatusMath.delta(bearing,heading);info+=Math.abs(delta)<5?"\n目标大致在正前方":String.format(Locale.ROOT,"\n目标相对朝向：向%s偏转 %.0f°",delta>=0?"右":"左",Math.abs(delta));}
        info+="\n参考定位 "+LocationService.ageSeconds(fix)+" 秒前"+(LocationService.ageSeconds(fix)>30?" · 位置较旧，请刷新":"");if(fix.hasAccuracy())info+=String.format(Locale.ROOT," · 误差半径 %.1f 米",fix.getAccuracy());set(treasureInfo,info);
    }
    private void updateSensors(){if(sensorViews.isEmpty())return;for(int n=0;n<StatusHardware.TYPES.length;n++){int type=StatusHardware.TYPES[n];set(sensorViews.get(type),hardware.sensorText(type,UNITS[n]));}}
    private static String chargeStatus(int status){switch(status){case BatteryManager.BATTERY_STATUS_CHARGING:return "充电中";case BatteryManager.BATTERY_STATUS_DISCHARGING:return "放电中";case BatteryManager.BATTERY_STATUS_FULL:return "已充满";case BatteryManager.BATTERY_STATUS_NOT_CHARGING:return "未充电";default:return "状态未知";}}
    private void updateBattery(){if(batteryViews.isEmpty())return;if(battery==null){for(TextView v:batteryViews.values())set(v,"系统未提供电池状态");return;}if(battery.hasExtra(BatteryManager.EXTRA_PRESENT)&&!battery.getBooleanExtra(BatteryManager.EXTRA_PRESENT,true)){for(TextView v:batteryViews.values())set(v,"系统报告没有电池");return;}
        int level=battery.getIntExtra(BatteryManager.EXTRA_LEVEL,-1),scale=battery.getIntExtra(BatteryManager.EXTRA_SCALE,-1),voltage=battery.getIntExtra(BatteryManager.EXTRA_VOLTAGE,-1),temperature=battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE,Integer.MIN_VALUE),plugged=battery.getIntExtra(BatteryManager.EXTRA_PLUGGED,-1),health=battery.getIntExtra(BatteryManager.EXTRA_HEALTH,0);
        String healthText=health==BatteryManager.BATTERY_HEALTH_GOOD?"良好":health==BatteryManager.BATTERY_HEALTH_OVERHEAT?"过热":health==BatteryManager.BATTERY_HEALTH_COLD?"过冷":health==BatteryManager.BATTERY_HEALTH_DEAD?"失效":health==BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE?"电压过高":health==BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE?"系统报告故障":"未提供";
        String source=plugged<0?"未提供":plugged==0?"未接电源":(plugged&BatteryManager.BATTERY_PLUGGED_AC)!=0?"交流电源":(plugged&BatteryManager.BATTERY_PLUGGED_USB)!=0?"USB":(plugged&BatteryManager.BATTERY_PLUGGED_WIRELESS)!=0?"无线充电":"其他电源";
        String technology=battery.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY);if(technology==null||technology.isEmpty())technology="未提供";
        BatteryManager bm=(BatteryManager)a.getSystemService(Context.BATTERY_SERVICE);String currentText="未提供",chargeText="未提供";if(bm!=null){int current=bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW),charge=bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);if(current!=Integer.MIN_VALUE){double amps=current/1000.0;if(a.preferences.getBoolean("status_battery_reverse_current",false))amps=-amps;currentText=String.format(Locale.ROOT,"%.1f mA",amps);}if(charge!=Integer.MIN_VALUE&&charge>=0)chargeText=String.format(Locale.ROOT,"%.0f mAh（非设计容量）",charge/1000.0);}
        String[] values={level>=0&&scale>0?Math.round(level*100f/scale)+"%":"未提供",chargeStatus(battery.getIntExtra(BatteryManager.EXTRA_STATUS,0)),temperature!=Integer.MIN_VALUE?String.format(Locale.ROOT,"%.1f ℃",temperature/10.0):"未提供",voltage>0?String.format(Locale.ROOT,"%.3f V",voltage/1000.0):"未提供",healthText,source,technology,currentText,chargeText};for(int id:batteryViews.keySet()){String value=values[id];set(batteryViews.get(id),value);}
    }
    private void updateDevice(){if(deviceViews.isEmpty())return;String[] values=new String[DEVICE_LABELS.length];values[0]=Build.MANUFACTURER+" "+Build.MODEL;values[1]=Build.VERSION.RELEASE+" · API "+Build.VERSION.SDK_INT;values[2]=Build.VERSION.SECURITY_PATCH;
        try{ActivityManager am=(ActivityManager)a.getSystemService(Context.ACTIVITY_SERVICE);ActivityManager.MemoryInfo m=new ActivityManager.MemoryInfo();am.getMemoryInfo(m);values[3]=bytes(m.availMem)+" / "+bytes(m.totalMem);StatFs fs=new StatFs(a.getFilesDir().getPath());values[4]=bytes(fs.getAvailableBytes())+" / "+bytes(fs.getTotalBytes());}catch(Exception ignored){values[3]=values[4]="未提供";}
        long seconds=SystemClock.elapsedRealtime()/1000;values[5]=(seconds/86400)+" 天 "+(seconds/3600%24)+" 时 "+(seconds/60%60)+" 分";
        android.util.DisplayMetrics dm=a.getResources().getDisplayMetrics();values[6]=dm.widthPixels+" × "+dm.heightPixels+" px · "+dm.densityDpi+" dpi";
        for(int id:deviceViews.keySet())set(deviceViews.get(id),values[id]);
    }
    private static String bytes(long bytes){return String.format(Locale.ROOT,"%.2f GiB",bytes/1073741824.0);}
}


