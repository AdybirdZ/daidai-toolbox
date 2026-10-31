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
    private final Runnable timeout=new Runnable(){public void run(){cancel();status=UiText.get("earthquake_location_001");changed.run();}};
    EarthquakeLocation(MainActivity a,Runnable changed){this.a=a;this.changed=changed;}
    private boolean network(){return a.preferences.getString("location_provider","gps").equals("network");}
    void start(){
        String permission=network()?Manifest.permission.ACCESS_COARSE_LOCATION:Manifest.permission.ACCESS_FINE_LOCATION;
        if(a.checkSelfPermission(permission)!=PackageManager.PERMISSION_GRANTED){pending=true;a.requestPermissions(network()?new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}:new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},45);return;}
        pending=false;cancel();String provider=network()?LocationManager.NETWORK_PROVIDER:LocationManager.GPS_PROVIDER;
        try{
            manager=(LocationManager)a.getSystemService(android.content.Context.LOCATION_SERVICE);
            if(!manager.getAllProviders().contains(provider)){status=UiText.get("earthquake_location_002");changed.run();return;}
            if(!manager.isProviderEnabled(provider)){status=UiText.get("earthquake_location_003");changed.run();return;}
            waiting=true;status=UiText.get("earthquake_location_004")+(network()?UiText.get("earthquake_location_005"):UiText.get("earthquake_location_006"));
            manager.requestLocationUpdates(provider,0,0,this,Looper.getMainLooper());handler.postDelayed(timeout,30_000);changed.run();
        }catch(SecurityException ex){cancel();status=UiText.get("earthquake_location_007");changed.run();}catch(Exception ex){cancel();status=UiText.get("earthquake_location_008");changed.run();}
    }
    void permissionResult(boolean visible){if(!pending)return;if(!visible){permissionReady=true;return;}pending=false;permissionReady=false;String permission=network()?Manifest.permission.ACCESS_COARSE_LOCATION:Manifest.permission.ACCESS_FINE_LOCATION;if(a.checkSelfPermission(permission)==PackageManager.PERMISSION_GRANTED)start();else{status=UiText.get("earthquake_location_009");changed.run();}}
    void resume(){if(permissionReady)permissionResult(true);}
    void cancel(){waiting=false;handler.removeCallbacks(timeout);if(manager!=null)try{manager.removeUpdates(this);}catch(Exception ignored){}}
    void pause(){boolean wasWaiting=waiting;cancel();if(wasWaiting)status=UiText.get("earthquake_location_010");}
    void destroy(){pending=false;permissionReady=false;cancel();}
    @Override public void onLocationChanged(Location fix){if(!waiting)return;LocationService.record(a,fix);cancel();status=UiText.get("earthquake_location_011");changed.run();}
    @Override public void onProviderDisabled(String provider){cancel();status=UiText.get("earthquake_location_012");changed.run();}
    @Override public void onProviderEnabled(String provider){}
    @Override public void onStatusChanged(String provider,int state,Bundle extras){}
}
