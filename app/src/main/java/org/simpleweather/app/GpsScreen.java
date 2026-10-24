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

/** Foreground-only Android location; the app never uploads coordinates. */
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
        LinearLayout c=a.card();c.addView(a.text("当前位置",18,a.INK,true));a.gap(c,12);status=a.text("尚未开始定位",14,a.MUTED,false);c.addView(status);a.gap(c,22);
        latitude=value(c,"纬度 · WGS84");longitude=value(c,"经度 · WGS84");accuracy=value(c,"水平定位精度 / 估计误差半径");altitude=value(c,"海拔 · 相对 WGS84 椭球");age=a.text("等待定位结果",12,a.MUTED,false);c.addView(age);a.gap(c,16);
        c.addView(a.button("开始 / 重新定位",true,()->{wanted=true;foreground=true;start();}));a.gap(c,10);c.addView(a.button("停止定位",false,()->{wanted=false;pause();set(status,"定位已停止"+(fix==null?"":" · 下方为上次结果"));update();}));a.gap(a.body,16);
        LinearLayout info=a.card();info.addView(a.text("定位说明",17,a.INK,true));a.gap(info,12);info.addView(a.text("仅在定位页前台启动后获取位置。切换页面、锁屏或离开应用即停止更新；应用不上传经纬度。\n\n精度为 Android 提供的水平误差半径估计（约 68% 置信水平），不是实际偏差保证。\n\nGPS 硬件模式：直接读取卫星定位，应用无需联网，室内或遮挡处可能无法获得信号。\n\n网络模式：需系统支持网络位置服务且保持网络连接，精度通常较低。系统可能把 IP、基站或 Wi-Fi 信息发给其定位服务商，适用系统与服务商规则。没有网络位置提供程序的设备无法使用此模式。\n\n经纬度为 WGS84，不做地图坐标偏移。可在设置→定位中选择模式。",13,a.MUTED,false));a.gap(info,12);info.addView(a.button("打开手机定位设置",false,()->a.startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))));a.gap(info,8);info.addView(a.button("打开应用权限设置",false,()->a.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:"+a.getPackageName())))));
        c.addView(a.text("当前方式："+mode()+" · 设置中可切换",12,a.MUTED,false));
        update();
    }
    private TextView value(LinearLayout c,String title){c.addView(a.text(title,12,a.MUTED,false));a.gap(c,8);TextView t=a.text("—",25,a.INK,true);t.setTextIsSelectable(true);c.addView(t);a.gap(c,20);return t;}
    private String provider(){return a.preferences.getString("location_provider","gps").equals("network")?LocationManager.NETWORK_PROVIDER:LocationManager.GPS_PROVIDER;}
    private boolean network(){return provider().equals(LocationManager.NETWORK_PROVIDER);}
    private String mode(){return network()?"网络定位":"GPS 硬件定位";}
    void modeChanged(){wanted=false;pause();fix=null;}
    private boolean allowed(){return a.checkSelfPermission(network()?Manifest.permission.ACCESS_COARSE_LOCATION:Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;}
    void permissionResult(){if(allowed())start();else {wanted=false;if(status!=null)set(status,"未授予所需定位权限；可再次申请或到应用权限设置开启。");}}
    private void start(){
        if(!wanted||!foreground)return;
        pause();foreground=true;
        if(!allowed()){pause();a.requestPermissions(network()?new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}:new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},41);return;}
        try{
            if(!manager.getAllProviders().contains(provider())){set(status,"此设备没有"+mode()+"提供程序，请到设置选择另一模式");return;}
            if(!manager.isProviderEnabled(provider())){set(status,mode()+"未开启，请打开手机定位设置");return;}
            manager.removeUpdates(this);manager.requestLocationUpdates(provider(),1000,0,this,Looper.getMainLooper());active=true;set(status,"正在等待"+mode()+"结果…");handler.removeCallbacks(tick);handler.post(tick);
            if(fix==null){Location cached=manager.getLastKnownLocation(provider());if(cached!=null){fix=new Location(cached);set(status,"等待新信号 · 下方为系统上次定位");update();}}
        }catch(SecurityException e){active=false;set(status,"定位权限已变化，请重新授权");}catch(Exception e){active=false;set(status,mode()+"暂不可用，请检查设置");}
    }
    void resume(){foreground=true;if(wanted)start();}
    void pause(){foreground=false;active=false;handler.removeCallbacks(tick);try{manager.removeUpdates(this);}catch(Exception ignored){}}
    void stopForNavigation(){wanted=false;pause();}
    private void set(TextView view,String value){if(!value.contentEquals(view.getText()))view.setText(value);}
    private void update(){
        if(latitude==null||fix==null)return;
        set(latitude,String.format(Locale.ROOT,"%.6f° %s",Math.abs(fix.getLatitude()),fix.getLatitude()<0?"S":"N"));set(longitude,String.format(Locale.ROOT,"%.6f° %s",Math.abs(fix.getLongitude()),fix.getLongitude()<0?"W":"E"));
        set(accuracy,fix.hasAccuracy()?String.format(Locale.ROOT,"± %.1f 米",fix.getAccuracy()):"未提供精度");set(altitude,fix.hasAltitude()?String.format(Locale.ROOT,"%.1f 米",fix.getAltitude()):"未提供海拔");
        long seconds=Math.max(0,(SystemClock.elapsedRealtimeNanos()-fix.getElapsedRealtimeNanos())/1_000_000_000L);set(age,mode()+" · 距上次定位 "+(seconds<5?"5 秒内":seconds+" 秒")+(seconds>30?" · 结果较旧":""));
        if(active&&seconds<=10)set(status,"已获取"+mode());else if(active&&seconds>30)set(status,"等待新信号 · 下方为上次定位");
    }
    @Override public void onLocationChanged(Location location){if(!active)return;fix=new Location(location);update();}
    @Override public void onProviderDisabled(String provider){active=false;handler.removeCallbacks(tick);if(status!=null)set(status,mode()+"已关闭，正在等待开启");}
    @Override public void onProviderEnabled(String provider){if(wanted&&foreground)start();}
    @Override public void onStatusChanged(String p,int s,Bundle b){}
}
