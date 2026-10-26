package org.simpleweather.app;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.*;
import android.os.*;

/** A bounded foreground-only fix, independent of the user's ongoing location service. */
final class EarthquakeLocation implements LocationListener {
    private final MainActivity a;
    private final Runnable changed;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private LocationManager manager;
    private boolean waiting,pending,permissionReady;
    String status="";
    private final Runnable timeout=new Runnable(){public void run(){cancel();status="本次定位超时，保留上次位置；请检查定位方式及系统设置";changed.run();}};
    EarthquakeLocation(MainActivity a,Runnable changed){this.a=a;this.changed=changed;}
    private boolean network(){return a.preferences.getString("location_provider","gps").equals("network");}
    void start(){
        String permission=network()?Manifest.permission.ACCESS_COARSE_LOCATION:Manifest.permission.ACCESS_FINE_LOCATION;
        if(a.checkSelfPermission(permission)!=PackageManager.PERMISSION_GRANTED){pending=true;a.requestPermissions(network()?new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}:new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},45);return;}
        pending=false;cancel();String provider=network()?LocationManager.NETWORK_PROVIDER:LocationManager.GPS_PROVIDER;
        try{
            manager=(LocationManager)a.getSystemService(android.content.Context.LOCATION_SERVICE);
            if(!manager.getAllProviders().contains(provider)){status="此设备不支持所选定位方式，请在定位设置中更改";changed.run();return;}
            if(!manager.isProviderEnabled(provider)){status="所选定位方式未开启，请打开手机系统定位";changed.run();return;}
            waiting=true;status="正在刷新定位 · "+(network()?"网络定位":"GPS 硬件定位");
            manager.requestLocationUpdates(provider,0,0,this,Looper.getMainLooper());handler.postDelayed(timeout,30_000);changed.run();
        }catch(SecurityException ex){cancel();status="未获得所选方式所需定位权限";changed.run();}catch(Exception ex){cancel();status="定位暂不可用，保留上次位置";changed.run();}
    }
    void permissionResult(boolean visible){if(!pending)return;if(!visible){permissionReady=true;return;}pending=false;permissionReady=false;String permission=network()?Manifest.permission.ACCESS_COARSE_LOCATION:Manifest.permission.ACCESS_FINE_LOCATION;if(a.checkSelfPermission(permission)==PackageManager.PERMISSION_GRANTED)start();else{status="未授予所需定位权限，可在系统应用权限中开启";changed.run();}}
    void resume(){if(permissionReady)permissionResult(true);}
    void cancel(){waiting=false;handler.removeCallbacks(timeout);if(manager!=null)try{manager.removeUpdates(this);}catch(Exception ignored){}}
    void pause(){boolean wasWaiting=waiting;cancel();if(wasWaiting)status="本次定位已停止，保留上次位置";}
    void destroy(){pending=false;permissionReady=false;cancel();}
    @Override public void onLocationChanged(Location fix){if(!waiting)return;LocationService.record(a,fix);cancel();status="定位已刷新";changed.run();}
    @Override public void onProviderDisabled(String provider){cancel();status="所选定位方式已关闭，保留上次位置";changed.run();}
    @Override public void onProviderEnabled(String provider){}
    @Override public void onStatusChanged(String provider,int state,Bundle extras){}
}
