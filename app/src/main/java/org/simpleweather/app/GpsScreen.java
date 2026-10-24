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

/** Foreground-only GPS; never shares coordinates with a server or the weather API. */
final class GpsScreen implements LocationListener {
    private final MainActivity a;
    private final LocationManager manager;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private TextView status,latitude,longitude,accuracy,altitude,age;
    private Location fix;
    private boolean wanted,active,foreground;
    private final Runnable tick=new Runnable(){public void run(){if(active){update();handler.postDelayed(this,5000);}}};
    GpsScreen(MainActivity activity){a=activity;manager=(LocationManager)a.getSystemService(Context.LOCATION_SERVICE);}
    void render(){
        LinearLayout heading=a.row();heading.addView(a.text("GPS定位",24,a.INK,true));a.spacer(heading);heading.addView(a.button("设置",false,a::settings));a.body.addView(heading);a.gap(a.body,16);
        LinearLayout c=a.card();c.addView(a.text("当前位置",18,a.INK,true));a.gap(c,12);status=a.text("尚未开始定位",14,a.MUTED,false);c.addView(status);a.gap(c,22);
        latitude=value(c,"纬度 · WGS84");longitude=value(c,"经度 · WGS84");accuracy=value(c,"水平定位精度 / 估计误差半径");altitude=value(c,"海拔 · 相对 WGS84 椭球");age=a.text("等待定位结果",12,a.MUTED,false);c.addView(age);a.gap(c,16);
        c.addView(a.button("开始 / 重新定位",true,()->{wanted=true;foreground=true;start();}));a.gap(c,10);c.addView(a.button("停止定位",false,()->{wanted=false;pause();set(status,"定位已停止"+(fix==null?"":" · 下方为上次结果"));update();}));a.gap(a.body,16);
        LinearLayout info=a.card();info.addView(a.text("定位说明",17,a.INK,true));a.gap(info,12);info.addView(a.text("仅在此页前台运行时获取 GPS。切换页面、锁屏或离开应用即停止更新；经纬度不会上传。\n\n精度为 Android 提供的水平误差半径估计（约 68% 置信水平），不是保证的实际偏差。室内或遮挡环境可能长时间没有信号，请到开阔处等待。\n\n这里只显示 WGS84 原始经纬度，不做地图坐标偏移。GPS 功能无需联网。",13,a.MUTED,false));a.gap(info,12);info.addView(a.button("打开手机定位设置",false,()->a.startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))));a.gap(info,8);info.addView(a.button("打开应用权限设置",false,()->a.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:"+a.getPackageName())))));
        update();
    }
    private TextView value(LinearLayout c,String title){c.addView(a.text(title,12,a.MUTED,false));a.gap(c,8);TextView t=a.text("—",25,a.INK,true);t.setTextIsSelectable(true);c.addView(t);a.gap(c,20);return t;}
    private boolean allowed(){return a.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;}
    void permissionResult(){if(allowed())start();else {wanted=false;if(status!=null)set(status,"未授予精确定位权限，无法读取 GPS；可再次申请或到应用权限设置开启。");}}
    private void start(){
        if(!wanted||!foreground)return;
        if(!allowed()){pause();a.requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},41);return;}
        try{
            if(!manager.getAllProviders().contains(LocationManager.GPS_PROVIDER)){set(status,"此设备未提供 GPS 硬件定位");return;}
            if(!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)){set(status,"手机 GPS 未开启，请打开定位设置");return;}
            manager.removeUpdates(this);manager.requestLocationUpdates(LocationManager.GPS_PROVIDER,1000,0,this,Looper.getMainLooper());active=true;set(status,"正在等待 GPS 信号…");handler.removeCallbacks(tick);handler.post(tick);
            if(fix==null){Location cached=manager.getLastKnownLocation(LocationManager.GPS_PROVIDER);if(cached!=null){fix=new Location(cached);set(status,"等待新信号 · 下方为系统上次定位");update();}}
        }catch(SecurityException e){active=false;set(status,"定位权限已变化，请重新授权");}catch(Exception e){active=false;set(status,"GPS 暂不可用，请检查定位设置");}
    }
    void resume(){foreground=true;if(wanted)start();}
    void pause(){foreground=false;active=false;handler.removeCallbacks(tick);try{manager.removeUpdates(this);}catch(Exception ignored){}}
    void stopForNavigation(){wanted=false;pause();}
    private void set(TextView view,String value){if(!value.contentEquals(view.getText()))view.setText(value);}
    private void update(){
        if(latitude==null||fix==null)return;
        set(latitude,String.format(Locale.ROOT,"%.6f° %s",Math.abs(fix.getLatitude()),fix.getLatitude()<0?"S":"N"));set(longitude,String.format(Locale.ROOT,"%.6f° %s",Math.abs(fix.getLongitude()),fix.getLongitude()<0?"W":"E"));
        set(accuracy,fix.hasAccuracy()?String.format(Locale.ROOT,"± %.1f 米",fix.getAccuracy()):"未提供精度");set(altitude,fix.hasAltitude()?String.format(Locale.ROOT,"%.1f 米",fix.getAltitude()):"未提供海拔");
        long seconds=Math.max(0,(SystemClock.elapsedRealtimeNanos()-fix.getElapsedRealtimeNanos())/1_000_000_000L);set(age,"GPS · 距上次定位 "+(seconds<5?"5 秒内":seconds+" 秒")+(seconds>30?" · 结果较旧":""));
        if(active&&seconds<=10)set(status,"已获取 GPS 定位");else if(active&&seconds>30)set(status,"等待新信号 · 下方为上次定位");
    }
    @Override public void onLocationChanged(Location location){if(!active)return;fix=new Location(location);update();}
    @Override public void onProviderDisabled(String provider){if(status!=null)set(status,"GPS 已关闭，正在等待开启");}
    @Override public void onProviderEnabled(String provider){if(wanted&&foreground)start();}
    @Override public void onStatusChanged(String p,int s,Bundle b){}
}
