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
    private TextView status,latitude,longitude,accuracy,altitude,age;
    private boolean visible,pendingPermission;
    private final Runnable tick=new Runnable(){public void run(){if(visible){update();handler.postDelayed(this,1000);}}};
    GpsScreen(MainActivity activity){a=activity;}
    void render(){
        LinearLayout c=a.card();c.addView(a.text("当前位置",18,a.INK,true));a.gap(c,12);status=a.text(LocationService.status,14,a.MUTED,false);c.addView(status);a.gap(c,22);
        longitude=value(c,"经度 · WGS84");latitude=value(c,"纬度 · WGS84");accuracy=value(c,"水平定位误差半径");altitude=value(c,"海拔 · 相对 WGS84 椭球");age=a.text("等待定位结果",12,a.MUTED,false);c.addView(age);a.gap(c,16);
        c.addView(a.button("开始 / 重新定位",true,this::start));a.gap(c,10);c.addView(a.button("停止定位",false,()->{pendingPermission=false;LocationService.stop(a);update();}));a.gap(c,12);c.addView(a.text("当前方式："+mode()+" · 设置中可切换",12,a.MUTED,false));a.gap(a.body,16);
        LinearLayout info=a.card();info.addView(a.text("定位说明",17,a.INK,true));a.gap(info,12);info.addView(a.text("默认只在定位页前台工作，切换栏目、锁屏或离开应用即停止。设置→定位可开启“切换栏目和后台继续定位”；开启后仍需点击开始，运行时提供持续定位通知，可在通知或本页停止；未允许通知时，系统可能仅在活动应用管理中显示服务。系统省电、权限变化或强制停止仍可能中断定位；关闭此选项后回到默认行为。应用不上传经纬度。\n\n水平定位误差半径为 Android 提供的估计（约 68% 置信水平），不是实际偏差保证。\n\nGPS 硬件模式：直接读取卫星定位，应用无需联网，室内或遮挡处可能无法获得信号。\n\n网络模式：需系统支持网络位置服务且保持网络连接。系统可能把 IP、基站或 Wi-Fi 信息发给其定位服务商，适用系统与服务商规则。没有网络位置提供程序的设备无法使用此模式。\n\n当系统定位结果不含海拔字段时，显示“未提供海拔”；常见于网络定位仅提供平面位置、设备或定位服务未返回高度时。这不表示海拔为零。海拔存在时为 WGS84 椭球高，不等同于海拔基准面上的正高。\n\n经纬度为 WGS84，不做地图坐标偏移。显示顺序为经度、纬度；E 为东经、W 为西经、N 为北纬、S 为南纬。地震距离在本机利用最近定位计算，未定位也能查询全球目录。自动定位间隔是请求系统更新的最短间隔，系统可能延迟或返回缓存；距上次定位时间按实际结果计算。",13,a.MUTED,false));a.gap(info,12);info.addView(a.button("打开手机定位设置",false,()->a.startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))));a.gap(info,8);info.addView(a.button("打开应用权限设置",false,()->a.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:"+a.getPackageName())))));
        resume();
    }
    private TextView value(LinearLayout c,String title){c.addView(a.text(title,12,a.MUTED,false));a.gap(c,8);TextView t=a.text("—",25,a.INK,true);t.setTextIsSelectable(true);c.addView(t);a.gap(c,20);return t;}
    private boolean network(){return a.preferences.getString("location_provider","gps").equals("network");}
    private String mode(){return network()?"网络定位":"GPS 硬件定位";}
    void modeChanged(){pendingPermission=false;LocationService.stop(a);}
    void intervalChanged(){if(LocationService.running)startService();}
    void continuousChanged(){if(LocationService.running)startService();}
    private boolean allowed(){return a.checkSelfPermission(network()?Manifest.permission.ACCESS_COARSE_LOCATION:Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;}
    void permissionResult(){if(pendingPermission&&allowed()){pendingPermission=false;start();}else{pendingPermission=false;LocationService.status="未授予所需定位权限；可到应用权限设置开启。";update();}}
    void notificationPermissionResult(){if(LocationService.running&&LocationService.continuous(a))startService();if(Build.VERSION.SDK_INT>=33&&a.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)a.toast("通知未开启，可在定位页或系统活动应用管理中停止定位");}
    private void start(){
        if(!allowed()){pendingPermission=true;a.requestPermissions(network()?new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}:new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},41);return;}
        if(LocationService.continuous(a)&&Build.VERSION.SDK_INT>=33&&a.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED&&!a.preferences.getBoolean("location_notice_asked",false)){
            a.preferences.edit().putBoolean("location_notice_asked",true).apply();a.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},44);
        }
        startService();
    }
    private void startService(){try{Intent i=new Intent(a,LocationService.class);if(LocationService.continuous(a))a.startForegroundService(i);else a.startService(i);}catch(Exception e){LocationService.status="定位启动失败，请返回应用前台重新开始";a.toast(LocationService.status);}handler.removeCallbacks(tick);if(visible)handler.post(tick);}
    void resume(){visible=true;handler.removeCallbacks(tick);handler.post(tick);}
    void pause(){visible=false;handler.removeCallbacks(tick);if(!LocationService.continuous(a)&&!pendingPermission)LocationService.stop(a);}
    void stopForNavigation(){pendingPermission=false;pause();}
    private void set(TextView view,String value){if(view!=null&&!value.contentEquals(view.getText()))view.setText(value);}
    private void update(){
        if(status==null)return;set(status,LocationService.status);Location fix=LocationService.last(a);if(fix==null)return;
        set(longitude,CoordinateFormat.longitude(fix.getLongitude(),6));set(latitude,CoordinateFormat.latitude(fix.getLatitude(),6));
        set(accuracy,fix.hasAccuracy()?String.format(Locale.ROOT,"%.1f 米",fix.getAccuracy()):"未提供精度");set(altitude,fix.hasAltitude()?String.format(Locale.ROOT,"%.1f 米",fix.getAltitude()):"未提供海拔");
        long seconds=LocationService.ageSeconds(fix);String actual="network".equals(fix.getProvider())?"网络定位":"GPS 硬件定位";set(age,actual+" · 距上次定位"+seconds+"秒"+(seconds>30?" · 结果较旧":""));
        if(LocationService.running&&seconds>30)set(status,"等待新信号 · 下方为上次定位");
    }
}
