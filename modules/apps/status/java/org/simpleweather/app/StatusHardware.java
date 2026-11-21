package org.simpleweather.app;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.hardware.*;
import android.location.*;
import android.os.*;
import android.view.Surface;
import java.util.*;

/** Foreground-only sensors and user-started GNSS status; never uploads or downloads orbit data. */
public final class StatusHardware implements SensorEventListener {
    public final MainActivity a;private final SensorManager sensors;private final LocationManager locations;private CameraLightReader cameraReader;
    public final Map<Integer,float[]> values=new HashMap<>();public final Map<Integer,Integer> accuracy=new HashMap<>();
    public final Map<Integer,Long> sampleAt=new HashMap<>();
    public boolean active,gnssRunning,gnssRequested,gnssPaused;public String gnssMessage=UiText.get("status_hardware_001");
    public double magneticHeading=Double.NaN,pitch=Double.NaN,roll=Double.NaN;public boolean rotationVector;
    public int compassAccuracy=SensorManager.SENSOR_STATUS_UNRELIABLE;public long headingAt,gnssAt;
    public final List<Satellite> satellites=new ArrayList<>();
    private float[] gravity,magnetic;private int lastDisplay=-1;private boolean matrixReady;private final float[] rotation=new float[9],remapped=new float[9],angles=new float[3];
    private double declinationLatitude=Double.NaN,declinationLongitude=Double.NaN,declinationAltitude=Double.NaN,cachedDeclination=Double.NaN;private long declinationAt;
    public static final int[] TYPES={Sensor.TYPE_GYROSCOPE,Sensor.TYPE_ACCELEROMETER,Sensor.TYPE_LINEAR_ACCELERATION,Sensor.TYPE_GRAVITY,Sensor.TYPE_MAGNETIC_FIELD,Sensor.TYPE_PRESSURE,Sensor.TYPE_LIGHT,Sensor.TYPE_PROXIMITY,Sensor.TYPE_AMBIENT_TEMPERATURE,Sensor.TYPE_RELATIVE_HUMIDITY};
    public static final class Satellite {
        public final int constellation,svid;public final float elevation,azimuth,cn0;public final boolean used;
        public Satellite(int c,int id,float e,float az,float cn,boolean u){constellation=c;svid=id;elevation=e;azimuth=az;cn0=cn;used=u;}
        public String key(){return constellation+":"+svid;}
        public String label(){return StatusMath.constellation(constellation)+" · "+StatusMath.satelliteId(constellation,svid);}
        public boolean validAngles(){return StatusMath.satelliteAngles(elevation,azimuth);}
        public boolean plotted(){return validAngles()&&elevation>=0;}
    }
    public StatusHardware(MainActivity a){this.a=a;sensors=(SensorManager)a.getSystemService(Context.SENSOR_SERVICE);locations=(LocationManager)a.getSystemService(Context.LOCATION_SERVICE);resetCameraEntry();}
    public Sensor sensor(int type){return sensors==null?null:sensors.getDefaultSensor(type);}
    private boolean compassWanted,allSensorsWanted,cameraWanted;private String cameraFacing="";private int cameraIso,cameraExposure,cameraOutdoorIso,cameraOutdoorExposure;private boolean cameraPaused;
    public void resume(boolean compass,boolean allSensors){
        if(entering){resetCameraEntry();entering=false;}
        int iso=a.preferences.getInt("status_camera_iso",CameraExposure.DEFAULT_ISO),exposure=a.preferences.getInt("status_camera_exposure_ms",CameraExposure.DEFAULT_MS);int outdoorIso=a.preferences.getInt("status_camera_outdoor_iso",CameraExposure.OUTDOOR_ISO),outdoorExposure=a.preferences.getInt("status_camera_outdoor_exposure_ms",CameraExposure.OUTDOOR_MS);String facing=a.preferences.getString("status_light_camera","back");boolean requestedCamera=allSensors&&sensorEnabled(Sensor.TYPE_LIGHT)&&"camera".equals(a.preferences.getString("status_light_source","sensor"));
        if(active&&compass==compassWanted&&allSensors==allSensorsWanted&&requestedCamera==cameraWanted&&facing.equals(cameraFacing)&&iso==cameraIso&&exposure==cameraExposure&&outdoorIso==cameraOutdoorIso&&outdoorExposure==cameraOutdoorExposure)return;
        if(sensors!=null)sensors.unregisterListener(this);if(cameraReader!=null){cameraReader.stop();cameraReader=null;}active=true;compassWanted=compass;allSensorsWanted=allSensors;cameraWanted=requestedCamera;cameraFacing=facing;cameraIso=iso;cameraExposure=exposure;cameraOutdoorIso=outdoorIso;cameraOutdoorExposure=outdoorExposure;magneticHeading=Double.NaN;headingAt=0;gravity=null;magnetic=null;matrixReady=false;lastDisplay=-1;
        boolean cameraLight=requestedCamera;if(cameraLight&&!cameraPaused)startCamera();
        if(sensors!=null){int delay=sensorDelay();Sensor vector=compass?sensor(Sensor.TYPE_ROTATION_VECTOR):null;rotationVector=vector!=null;if(vector!=null)sensors.registerListener(this,vector,delay);for(int type:TYPES){boolean fallback=compass&&!rotationVector&&(type==Sensor.TYPE_ACCELEROMETER||type==Sensor.TYPE_MAGNETIC_FIELD);if(type==Sensor.TYPE_LIGHT&&cameraLight)continue;if(!allSensors&&!fallback)continue;if(allSensors&&!sensorEnabled(type)&&!fallback)continue;Sensor sensor=sensor(type);if(sensor!=null)sensors.registerListener(this,sensor,delay);}}
        if(gnssPaused){gnssPaused=false;startGnss();}
    }
    private void startCamera(){cameraReader=new CameraLightReader(a);cameraReader.start();}
    public boolean cameraPaused(){return cameraPaused;}
    public void toggleCamera(){
        cameraPaused=!cameraPaused;
        if(cameraReader!=null){cameraReader.stop();cameraReader=null;}
        if(!cameraPaused&&active&&cameraWanted)startCamera();
    }
    private boolean entering;
    public void resetCameraEntry(){entering=true;cameraPaused=!a.preferences.getBoolean("status_camera_auto_start",false);}

    public int sensorDelay(){return SensorManager.SENSOR_DELAY_FASTEST;}
    public boolean sensorEnabled(int type){return a.preferences.getBoolean("status_sensor_show_"+type,true);}
    public void shutdown(){pause(false);if(cameraReader!=null){cameraReader.stop();cameraReader=null;}}
    public void pause(){pause(a.preferences.getBoolean("status_receive_pause",true));}
    public void pause(boolean preserveReceive){active=false;if(sensors!=null)sensors.unregisterListener(this);boolean keepCamera=a.preferences.getBoolean("status_camera_background",false);if(!keepCamera&&cameraReader!=null){cameraReader.stop();cameraReader=null;}if(preserveReceive&&(gnssRunning||gnssRequested)){try{if(locations!=null){locations.unregisterGnssStatusCallback(callback);locations.removeUpdates(fixes);}}catch(Exception ignored){}gnssRunning=false;gnssPaused=true;gnssMessage=UiText.get("status_hardware_002");return;}stopGnss(UiText.get("status_hardware_003"));gnssPaused=false;gnssRequested=false;}
    @Override public void onAccuracyChanged(Sensor s,int level){accuracy.put(s.getType(),level);if(s.getType()==(rotationVector?Sensor.TYPE_ROTATION_VECTOR:Sensor.TYPE_MAGNETIC_FIELD))compassAccuracy=level;}
    @Override public void onSensorChanged(SensorEvent e){if(!active)return;values.put(e.sensor.getType(),e.values.clone());sampleAt.put(e.sensor.getType(),SystemClock.elapsedRealtime());accuracy.put(e.sensor.getType(),e.accuracy);
        if(e.sensor.getType()==Sensor.TYPE_ACCELEROMETER)gravity=e.values.clone();if(e.sensor.getType()==Sensor.TYPE_MAGNETIC_FIELD)magnetic=e.values.clone();
        boolean ready=false;if(rotationVector&&e.sensor.getType()==Sensor.TYPE_ROTATION_VECTOR){SensorManager.getRotationMatrixFromVector(rotation,e.values);compassAccuracy=e.accuracy;ready=true;}
        else if(!rotationVector&&gravity!=null&&magnetic!=null&&(e.sensor.getType()==Sensor.TYPE_ACCELEROMETER||e.sensor.getType()==Sensor.TYPE_MAGNETIC_FIELD)){ready=SensorManager.getRotationMatrix(rotation,null,gravity,magnetic);compassAccuracy=accuracy.containsKey(Sensor.TYPE_MAGNETIC_FIELD)?accuracy.get(Sensor.TYPE_MAGNETIC_FIELD):0;}
        if(!ready)return;
        matrixReady=true;orient();headingAt=SystemClock.elapsedRealtime();
    }
    public void configurationChanged(){if(active&&matrixReady)orient();}
    private void orient(){
        int display=a.getWindowManager().getDefaultDisplay().getRotation();int x=SensorManager.AXIS_X,y=SensorManager.AXIS_Y;
        if(display==Surface.ROTATION_90){x=SensorManager.AXIS_Y;y=SensorManager.AXIS_MINUS_X;}else if(display==Surface.ROTATION_180){x=SensorManager.AXIS_MINUS_X;y=SensorManager.AXIS_MINUS_Y;}else if(display==Surface.ROTATION_270){x=SensorManager.AXIS_MINUS_Y;y=SensorManager.AXIS_X;}
        if(!SensorManager.remapCoordinateSystem(rotation,x,y,remapped))return;SensorManager.getOrientation(remapped,angles);
        double current=Math.toDegrees(angles[0]);magneticHeading=display==lastDisplay?StatusMath.smooth(magneticHeading,current,.18):StatusMath.norm(current);lastDisplay=display;
        // Android's positive pitch points toward the display bottom. The UI defines skyward as positive.
        pitch=-Math.toDegrees(angles[1]);
        // Positive roll is right side down; the UI reports left as negative and right as positive.
        roll=Math.toDegrees(angles[2]);
    }
    public boolean headingAvailable(){return active&&Double.isFinite(magneticHeading)&&SystemClock.elapsedRealtime()-headingAt<5000&&Math.abs(pitch)<80&&Math.abs(roll)<80;}
    public String compassSource(){return rotationVector?UiText.get("status_hardware_004"):sensor(Sensor.TYPE_ACCELEROMETER)!=null&&sensor(Sensor.TYPE_MAGNETIC_FIELD)!=null?UiText.get("status_hardware_005"):UiText.get("status_hardware_006");}
    public double declination(Location fix){
        if(fix==null||!StatusMath.coordinate(fix.getLongitude(),fix.getLatitude()))return Double.NaN;
        double latitude=fix.getLatitude(),longitude=fix.getLongitude(),altitude=fix.hasAltitude()?fix.getAltitude():0;long now=System.currentTimeMillis();
        // The model depends on position/date, not device attitude. Multiple live cards
        // request the same value every display tick; reuse it until inputs change.
        if(latitude!=declinationLatitude||longitude!=declinationLongitude||altitude!=declinationAltitude||now-declinationAt>=60_000||now<declinationAt){
            cachedDeclination=new GeomagneticField((float)latitude,(float)longitude,(float)altitude,now).getDeclination();
            declinationLatitude=latitude;declinationLongitude=longitude;declinationAltitude=altitude;declinationAt=now;
        }
        return cachedDeclination;
    }
    public double trueHeading(Location fix){double d=declination(fix);return headingAvailable()&&Double.isFinite(d)?StatusMath.norm(magneticHeading+d):Double.NaN;}
    private final LocationListener fixes=new LocationListener(){public void onLocationChanged(Location l){if(active&&gnssRunning){LocationService.record(a,l);}}public void onStatusChanged(String p,int s,Bundle b){}public void onProviderEnabled(String p){gnssMessage=UiText.get("status_hardware_007");}public void onProviderDisabled(String p){satellites.clear();gnssAt=0;gnssMessage=UiText.get("status_hardware_008");}};
    private final GnssStatus.Callback callback=new GnssStatus.Callback(){
        @Override public void onStarted(){if(gnssRunning)gnssMessage=UiText.get("status_hardware_009");}
        @Override public void onStopped(){satellites.clear();gnssAt=0;if(gnssRunning)gnssMessage=UiText.get("status_hardware_010");}
        @Override public void onFirstFix(int ms){if(gnssRunning)gnssMessage=UiText.get("status_hardware_011");}
        @Override public void onSatelliteStatusChanged(GnssStatus status){if(!active||!gnssRunning)return;Map<String,Satellite> unique=new LinkedHashMap<>();for(int i=0;i<status.getSatelliteCount();i++){Satellite s=new Satellite(status.getConstellationType(i),status.getSvid(i),status.getElevationDegrees(i),status.getAzimuthDegrees(i),status.getCn0DbHz(i),status.usedInFix(i));Satellite old=unique.get(s.key());if(old!=null)s=new Satellite(s.constellation,s.svid,s.elevation,s.azimuth,Math.max(s.cn0,old.cn0),s.used||old.used);unique.put(s.key(),s);}satellites.clear();satellites.addAll(unique.values());satellites.sort(Comparator.comparingInt((Satellite s)->s.constellation).thenComparingInt(s->s.svid));gnssAt=SystemClock.elapsedRealtime();gnssMessage=satellites.isEmpty()?UiText.get("status_hardware_012"):UiText.get("status_hardware_013");}
    };
    public boolean startGnss(){
        gnssRequested=true;if(a.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){a.requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},46);return false;}
        stopGnss(UiText.get("status_hardware_014"));gnssRequested=true;
        try{if(!active){gnssMessage=UiText.get("status_hardware_015");return false;}if(locations==null||!locations.getAllProviders().contains(LocationManager.GPS_PROVIDER)){gnssMessage=UiText.get("status_hardware_016");return false;}if(!locations.isProviderEnabled(LocationManager.GPS_PROVIDER)){gnssMessage=UiText.get("status_hardware_017");return false;}
            gnssRunning=true;if(!locations.registerGnssStatusCallback(callback,new Handler(Looper.getMainLooper())))throw new IllegalStateException();locations.requestLocationUpdates(LocationManager.GPS_PROVIDER,1000,0,fixes,Looper.getMainLooper());gnssMessage=UiText.get("status_hardware_018");return true;
        }catch(SecurityException e){stopGnss(UiText.get("status_hardware_019"));}catch(Exception e){stopGnss(UiText.get("status_hardware_020"));}return false;
    }
    public void permissionResult(){if(gnssRequested&&active){if(a.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED)startGnss();else stopGnss(UiText.get("gps_047"));}else stopGnss(UiText.get("status_hardware_021"));}
    public void stopGnss(String message){gnssRunning=false;gnssRequested=false;gnssPaused=false;try{if(locations!=null){locations.unregisterGnssStatusCallback(callback);locations.removeUpdates(fixes);}}catch(Exception ignored){}satellites.clear();gnssAt=0;gnssMessage=message;}
    private int[] companyOrder(){String raw=a.preferences.getString("status_sat_company_order","5,1,6,3,4,7,2,0");ArrayList<Integer> out=new ArrayList<>();for(String token:raw.split(","))try{int n=Integer.parseInt(token.trim());if(!out.contains(n))out.add(n);}catch(Exception ignored){}for(int n:new int[]{5,1,6,3,4,7,2,0})if(!out.contains(n))out.add(n);int[] result=new int[out.size()];for(int n=0;n<out.size();n++)result[n]=out.get(n);return result;}
    public List<Satellite> shown(){List<Satellite> rows=new ArrayList<>();for(Satellite s:satellites)if(a.preferences.getBoolean("status_constellation_"+s.constellation,true))rows.add(s);final int[] order=companyOrder();String sort=a.preferences.getString("status_sat_sort","company");Comparator<Satellite> comparator=Comparator.comparingInt((Satellite s)->StatusMath.constellationRank(s.constellation,order)).thenComparingInt(s->s.svid);if("cn0".equals(sort))comparator=Comparator.comparingDouble((Satellite s)->Float.isFinite(s.cn0)?-s.cn0:Double.POSITIVE_INFINITY).thenComparingInt(s->StatusMath.constellationRank(s.constellation,order)).thenComparingInt(s->s.svid);rows.sort(comparator);return rows;}
    public String sensorText(int type,String unit){if(type==Sensor.TYPE_LIGHT&&"camera".equals(a.preferences.getString("status_light_source","sensor"))){return cameraPaused?a.getString(R.string.camera_paused):cameraReader==null?a.getString(R.string.camera_waiting):cameraReader.text();}Sensor s=sensor(type);if(s==null)return UiText.get("status_hardware_022");float[] v=values.get(type);if(v==null||!sampleAt.containsKey(type))return UiText.get("status_hardware_023");
        double maximum=s.getMaximumRange();for(float sample:v)if(!Float.isFinite(sample)||(Double.isFinite(maximum)&&maximum>0&&Math.abs(sample)>maximum*1.05+.01))return UiText.get("status_hardware_024");
        if(v.length>0){float sample=v[0];if(type==Sensor.TYPE_AMBIENT_TEMPERATURE&&sample< -273.15f||type==Sensor.TYPE_PRESSURE&&sample<=0||type==Sensor.TYPE_LIGHT&&sample<0||type==Sensor.TYPE_PROXIMITY&&sample<0||type==Sensor.TYPE_RELATIVE_HUMIDITY&&(sample<0||sample>100))return UiText.get("status_hardware_024");}
        long age=(SystemClock.elapsedRealtime()-sampleAt.get(type))/1000;String old=age>5?" · "+age+UiText.get("earthquake_025"):"";
        if(v.length>=3)return String.format(Locale.ROOT,"X %.3f · Y %.3f · Z %.3f %s%s",v[0],v[1],v[2],unit,old);
        return String.format(Locale.ROOT,"%.2f %s%s",v[0],unit,old);
    }
}
