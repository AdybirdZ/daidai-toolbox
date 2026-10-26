package org.simpleweather.app;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.location.*;
import android.os.*;
import java.util.*;

/** Started by a visible Activity, never by a boot receiver or earthquake polling. */
public final class LocationService extends Service implements LocationListener {
    static volatile boolean running;
    static volatile String status="尚未开始定位";
    private static volatile Location latest;
    static final String STOP="org.simpleweather.app.STOP_LOCATION";
    private static final String CHANNEL="location",STATE="location_state";
    private static final int NOTICE=110;
    private LocationManager manager;
    private String provider;
    private boolean foreground;
    static boolean continuous(Context c){return c.getSharedPreferences("toolbox",0).getBoolean("location_continuous",false);}
    static Location last(Context c){
        Location l=latest;if(l!=null)return new Location(l);
        SharedPreferences p=c.getSharedPreferences(STATE,0);if(!p.contains("lat"))return null;
        try{l=new Location(p.getString("provider","gps"));l.setLatitude(Double.longBitsToDouble(p.getLong("lat",0)));l.setLongitude(Double.longBitsToDouble(p.getLong("lon",0)));l.setTime(p.getLong("time",0));if(p.contains("accuracy"))l.setAccuracy(p.getFloat("accuracy",0));if(p.contains("altitude"))l.setAltitude(Double.longBitsToDouble(p.getLong("altitude",0)));return l;}catch(Exception e){return null;}
    }
    static long ageSeconds(Location l){if(l==null)return 0;long elapsed=l.getElapsedRealtimeNanos();return Math.max(0,elapsed>0&&elapsed<=SystemClock.elapsedRealtimeNanos()?(SystemClock.elapsedRealtimeNanos()-elapsed)/1_000_000_000L:(System.currentTimeMillis()-l.getTime())/1000);}
    static void stop(Context c){c.stopService(new Intent(c,LocationService.class));running=false;status="定位已停止";}
    @Override public void onCreate(){super.onCreate();manager=(LocationManager)getSystemService(LOCATION_SERVICE);}
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent!=null&&STOP.equals(intent.getAction())){status="定位已停止";stopSelf();return START_NOT_STICKY;}
        SharedPreferences p=getSharedPreferences("toolbox",0);provider=p.getString("location_provider","gps").equals("network")?LocationManager.NETWORK_PROVIDER:LocationManager.GPS_PROVIDER;
        try{
            if(continuous(this))promote();else if(foreground){stopForeground(true);foreground=false;}
            manager.removeUpdates(this);
            String permission=provider.equals("network")?Manifest.permission.ACCESS_COARSE_LOCATION:Manifest.permission.ACCESS_FINE_LOCATION;
            if(checkSelfPermission(permission)!=PackageManager.PERMISSION_GRANTED)throw new SecurityException();
            if(!manager.getAllProviders().contains(provider)){status="此设备没有所选定位提供程序，请切换定位方式";stopSelf();return START_NOT_STICKY;}
            if(!manager.isProviderEnabled(provider)){status="所选定位方式未开启，请打开手机定位设置";stopSelf();return START_NOT_STICKY;}
            long interval=p.getLong("location_interval",5000);if(!Arrays.asList(1000L,5000L,10000L,30000L,60000L,180000L,300000L,600000L).contains(interval))interval=5000;
            manager.requestLocationUpdates(provider,interval,0,this,Looper.getMainLooper());running=true;status="正在等待 "+mode()+"结果…";
            if(latest==null){Location cached=manager.getLastKnownLocation(provider);if(cached!=null){save(cached);status="等待新信号 · 下方为系统上次定位";}}
        }catch(SecurityException e){status="定位权限不足，请重新授权";stopSelf();}catch(Exception e){status="定位暂不可用，请检查系统设置后重新开始";stopSelf();}
        return START_NOT_STICKY;
    }
    private String mode(){return "network".equals(provider)?"网络定位":"GPS 硬件定位";}
    private void promote(){
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);NotificationChannel channel=new NotificationChannel(CHANNEL,"持续定位",NotificationManager.IMPORTANCE_LOW);channel.setDescription("主动开始持续定位后显示，点击停止可结束定位");nm.createNotificationChannel(channel);
        Intent open=new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP).putExtra("open_location",true);
        PendingIntent content=PendingIntent.getActivity(this,110,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop=PendingIntent.getService(this,111,new Intent(this,LocationService.class).setAction(STOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder=new Notification.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_location_notification).setContentTitle("呆呆工具箱 · 持续定位中").setContentText(mode()+" · 切换栏目和后台继续工作").setContentIntent(content).setOngoing(true).setOnlyAlertOnce(true).addAction(new Notification.Action.Builder(null,"停止定位",stop).build()).setCategory(Notification.CATEGORY_SERVICE);
        if(Build.VERSION.SDK_INT>=31)builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);Notification n=builder.build();
        if(Build.VERSION.SDK_INT>=29)startForeground(NOTICE,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);else startForeground(NOTICE,n);foreground=true;
    }
    private void save(Location l){record(this,l);}
    static void record(Context context,Location l){
        latest=new Location(l);SharedPreferences.Editor e=context.getSharedPreferences(STATE,0).edit().putLong("lat",Double.doubleToLongBits(l.getLatitude())).putLong("lon",Double.doubleToLongBits(l.getLongitude())).putLong("time",l.getTime()).putString("provider",l.getProvider());
        if(l.hasAccuracy())e.putFloat("accuracy",l.getAccuracy());else e.remove("accuracy");if(l.hasAltitude())e.putLong("altitude",Double.doubleToLongBits(l.getAltitude()));else e.remove("altitude");e.apply();
    }
    @Override public void onLocationChanged(Location l){if(!running)return;save(l);status="已获取 "+mode();}
    @Override public void onProviderDisabled(String p){status=mode()+"已关闭，正在等待开启";}
    @Override public void onProviderEnabled(String p){status="正在等待 "+mode()+"结果…";}
    @Override public void onStatusChanged(String p,int s,Bundle b){}
    @Override public void onDestroy(){running=false;try{manager.removeUpdates(this);}catch(Exception ignored){}if(foreground)stopForeground(true);super.onDestroy();}
}
