package org.daidai.toolbox;

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
    public final MainActivity a;private final SensorManager sensors;private final LocationManager locations;
    /* Keep sensor and GNSS callbacks off the UI looper. */
    private final HandlerThread hardwareThread;private final Handler hardwareHandler;
    public final Map<Integer,float[]> values=new HashMap<>();public final Map<Integer,Integer> accuracy=new HashMap<>();
    private final Map<Integer,Sensor> sensorCache=new HashMap<>();
    public final Map<Integer,Long> sampleAt=new HashMap<>();
    /* Some devices deliver UI sensors much faster than the page can display them.
       Keep the latest accepted sample at a bounded rate so callbacks do not spend
       the UI budget cloning arrays and recalculating orientation on every event. */
    private static final long MIN_SAMPLE_INTERVAL_MS=100;
    public volatile boolean active,gnssRunning,gnssRequested,gnssPaused;public volatile String gnssMessage=UiText.get("status_hardware_001");
    public volatile double magneticHeading=Double.NaN,pitch=Double.NaN,roll=Double.NaN;public volatile boolean rotationVector;
    public volatile int compassAccuracy=SensorManager.SENSOR_STATUS_UNRELIABLE;public volatile long headingAt;public volatile long gnssAt;
    private final StatusSatelliteStore<Satellite> satelliteStore=new StatusSatelliteStore<>();
    private long shownSatelliteRevision=Long.MIN_VALUE;
    private String shownSatelliteSettings="";
    private List<Satellite> shownSatelliteCache=Collections.emptyList();
    private float[] gravity,magnetic;private long orientationAt;private int lastDisplay=-1;private boolean matrixReady;private final float[] rotation=new float[9],remapped=new float[9],angles=new float[3];
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
    private volatile int displayRotation;
    public StatusHardware(MainActivity a){this.a=a;sensors=(SensorManager)a.getSystemService(Context.SENSOR_SERVICE);locations=(LocationManager)a.getSystemService(Context.LOCATION_SERVICE);hardwareThread=new HandlerThread("status-hardware");hardwareThread.start();hardwareHandler=new Handler(hardwareThread.getLooper());displayRotation=a.getWindowManager().getDefaultDisplay().getRotation();resetCameraEntry();}
    public synchronized Sensor sensor(int type){if(sensors==null)return null;if(sensorCache.containsKey(type))return sensorCache.get(type);Sensor value=sensors.getDefaultSensor(type);sensorCache.put(type,value);return value;}
    private boolean compassWanted,allSensorsWanted,cameraWanted;private String cameraFacing="";private int cameraIso,cameraExposure,cameraOutdoorIso,cameraOutdoorExposure;private volatile boolean cameraPaused;private volatile CameraLightReader cameraReader;private volatile long hardwareGeneration;
    public void resume(boolean compass,boolean allSensors){
        if(entering){resetCameraEntry();entering=false;}
        int iso=a.preferences.getInt("status_camera_iso",CameraExposure.DEFAULT_ISO),exposure=a.preferences.getInt("status_camera_exposure_ms",CameraExposure.DEFAULT_MS);int outdoorIso=a.preferences.getInt("status_camera_outdoor_iso",CameraExposure.OUTDOOR_ISO),outdoorExposure=a.preferences.getInt("status_camera_outdoor_exposure_ms",CameraExposure.OUTDOOR_MS);String facing=a.preferences.getString("status_light_camera","back");boolean requestedCamera=allSensors&&sensorEnabled(Sensor.TYPE_LIGHT)&&"camera".equals(a.preferences.getString("status_light_source","sensor"));
        if(active&&compass==compassWanted&&allSensors==allSensorsWanted&&requestedCamera==cameraWanted&&facing.equals(cameraFacing)&&iso==cameraIso&&exposure==cameraExposure&&outdoorIso==cameraOutdoorIso&&outdoorExposure==cameraOutdoorExposure)return;
        final long generation=++hardwareGeneration;active=true;compassWanted=compass;allSensorsWanted=allSensors;cameraWanted=requestedCamera;cameraFacing=facing;cameraIso=iso;cameraExposure=exposure;cameraOutdoorIso=outdoorIso;cameraOutdoorExposure=outdoorExposure;magneticHeading=Double.NaN;headingAt=0;orientationAt=0;gravity=null;magnetic=null;matrixReady=false;lastDisplay=-1;
        hardwareHandler.post(()->{if(generation!=hardwareGeneration)return;if(sensors!=null)sensors.unregisterListener(this);stopCameraOnHardware();if(!active||generation!=hardwareGeneration)return;boolean cameraLight=cameraWanted;if(cameraLight&&!cameraPaused)startCamera();if(sensors!=null){int delay=sensorDelay();Sensor vector=compassWanted?sensor(Sensor.TYPE_ROTATION_VECTOR):null;rotationVector=vector!=null;if(vector!=null)sensors.registerListener(this,vector,delay,hardwareHandler);for(int type:TYPES){boolean fallback=compassWanted&&!rotationVector&&(type==Sensor.TYPE_ACCELEROMETER||type==Sensor.TYPE_MAGNETIC_FIELD);if(type==Sensor.TYPE_LIGHT&&cameraLight)continue;if(!allSensorsWanted&&!fallback)continue;if(allSensorsWanted&&!sensorEnabled(type)&&!fallback)continue;Sensor sensor=sensor(type);if(sensor!=null)sensors.registerListener(this,sensor,delay,hardwareHandler);}}if(gnssPaused){gnssPaused=false;startGnss();}});
    }
    private void stopCameraOnHardware(){CameraLightReader reader=cameraReader;if(reader!=null){cameraReader=null;reader.stop();}}
    private void startCamera(){if(a.checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){new Handler(Looper.getMainLooper()).post(()->a.requestPermissions(new String[]{Manifest.permission.CAMERA},48));return;}CameraLightReader reader=new CameraLightReader(a);cameraReader=reader;if(!reader.start()&&cameraReader==reader)cameraReader=null;}
    public boolean cameraPaused(){return cameraPaused;}
    public void toggleCamera(){
        cameraPaused=!cameraPaused;
        final long generation=++hardwareGeneration;hardwareHandler.post(()->{if(generation!=hardwareGeneration)return;stopCameraOnHardware();if(!cameraPaused&&active&&cameraWanted)startCamera();});
    }
    private boolean entering;
    public void resetCameraEntry(){entering=true;cameraPaused=!a.preferences.getBoolean("status_camera_auto_start",false);}

    /** The status page is a display, not a raw sensor logger. */
    public int sensorDelay(){return SensorManager.SENSOR_DELAY_UI;}
    public boolean sensorEnabled(int type){return a.preferences.getBoolean("status_sensor_show_"+type,true);}
    public void shutdown(){pause(false);hardwareThread.quitSafely();}
    public void pause(){pause(a.preferences.getBoolean("status_receive_pause",true));}
    public void pause(boolean preserveReceive){active=false;final long generation=++hardwareGeneration;boolean keepCamera=a.preferences.getBoolean("status_camera_background",false);hardwareHandler.post(()->{if(generation!=hardwareGeneration)return;if(sensors!=null)sensors.unregisterListener(this);if(!keepCamera)stopCameraOnHardware();});if(preserveReceive&&(gnssRunning||gnssRequested)){gnssRunning=false;gnssPaused=true;gnssMessage=UiText.get("status_hardware_002");postGnss(() -> unregisterGnss());return;}stopGnss(UiText.get("status_hardware_003"));}
    @Override public synchronized void onAccuracyChanged(Sensor s,int level){accuracy.put(s.getType(),level);if(s.getType()==(rotationVector?Sensor.TYPE_ROTATION_VECTOR:Sensor.TYPE_MAGNETIC_FIELD))compassAccuracy=level;}
    @Override public synchronized void onSensorChanged(SensorEvent e){if(!active)return;long now=SystemClock.elapsedRealtime();int type=e.sensor.getType();Long previous=sampleAt.get(type);if(previous!=null&&now-previous<MIN_SAMPLE_INTERVAL_MS)return;
        // Reuse sample arrays. Cloning every callback creates a large amount of
        // short-lived garbage when all sensors are enabled, causing UI GC pauses.
        float[] stored=values.get(type);if(stored==null||stored.length!=e.values.length){stored=new float[e.values.length];values.put(type,stored);}System.arraycopy(e.values,0,stored,0,e.values.length);sampleAt.put(type,now);accuracy.put(type,e.accuracy);
        if(type==Sensor.TYPE_ACCELEROMETER){if(gravity==null||gravity.length!=e.values.length)gravity=new float[e.values.length];System.arraycopy(e.values,0,gravity,0,e.values.length);}if(type==Sensor.TYPE_MAGNETIC_FIELD){if(magnetic==null||magnetic.length!=e.values.length)magnetic=new float[e.values.length];System.arraycopy(e.values,0,magnetic,0,e.values.length);}
        boolean ready=false;if(rotationVector&&e.sensor.getType()==Sensor.TYPE_ROTATION_VECTOR){SensorManager.getRotationMatrixFromVector(rotation,e.values);compassAccuracy=e.accuracy;ready=true;}
        else if(!rotationVector&&gravity!=null&&magnetic!=null&&(e.sensor.getType()==Sensor.TYPE_ACCELEROMETER||e.sensor.getType()==Sensor.TYPE_MAGNETIC_FIELD)){ready=SensorManager.getRotationMatrix(rotation,null,gravity,magnetic);compassAccuracy=accuracy.containsKey(Sensor.TYPE_MAGNETIC_FIELD)?accuracy.get(Sensor.TYPE_MAGNETIC_FIELD):0;}
        if(!ready||now-orientationAt<80)return;
        matrixReady=true;orientationAt=now;orient();headingAt=now;
    }
    public void configurationChanged(){displayRotation=a.getWindowManager().getDefaultDisplay().getRotation();if(active&&matrixReady)orient();}
    private void orient(){
        int display=displayRotation;int x=SensorManager.AXIS_X,y=SensorManager.AXIS_Y;
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
    private final LocationListener fixes=new LocationListener(){public void onLocationChanged(Location l){if(active&&gnssRunning){LocationService.record(a,l);}}public void onStatusChanged(String p,int s,Bundle b){}public void onProviderEnabled(String p){gnssMessage=UiText.get("status_hardware_007");}public void onProviderDisabled(String p){clearSatelliteStatus(UiText.get("status_hardware_008"));}};
    private final GnssStatus.Callback callback=new GnssStatus.Callback(){
        @Override public void onStarted(){if(gnssRunning)gnssMessage=UiText.get("status_hardware_009");}
        @Override public void onStopped(){gnssStopped();}
        @Override public void onFirstFix(int ms){if(gnssRunning)gnssMessage=UiText.get("status_hardware_011");}
        @Override public void onSatelliteStatusChanged(GnssStatus status){if(!active||!gnssRunning)return;Map<String,Satellite> unique=new LinkedHashMap<>();for(int i=0;i<status.getSatelliteCount();i++){Satellite s=new Satellite(status.getConstellationType(i),status.getSvid(i),status.getElevationDegrees(i),status.getAzimuthDegrees(i),status.getCn0DbHz(i),status.usedInFix(i));Satellite old=unique.get(s.key());if(old!=null)s=new Satellite(s.constellation,s.svid,s.elevation,s.azimuth,Math.max(s.cn0,old.cn0),s.used||old.used);unique.put(s.key(),s);}ArrayList<Satellite> next=new ArrayList<>(unique.values());next.sort(Comparator.comparingInt((Satellite s)->s.constellation).thenComparingInt(s->s.svid));publishSatelliteStatus(next);}
    };
    private synchronized void publishSatelliteStatus(List<Satellite> next){if(!active||!gnssRunning)return;long captured=SystemClock.elapsedRealtime();satelliteStore.publish(next,captured);gnssAt=captured;gnssMessage=next.isEmpty()?UiText.get("status_hardware_012"):UiText.get("status_hardware_013");}
    private synchronized void clearSatelliteStatus(String message){satelliteStore.publish(Collections.emptyList(),0);gnssAt=0;gnssMessage=message;}
    private synchronized void gnssStopped(){if(gnssRunning)clearSatelliteStatus(UiText.get("status_hardware_010"));}
    private void postGnss(Runnable action){if(Looper.myLooper()==hardwareThread.getLooper())action.run();else hardwareHandler.post(action);}
    private void unregisterGnss(){try{if(locations!=null){locations.unregisterGnssStatusCallback(callback);locations.removeUpdates(fixes);}}catch(Exception ignored){}}
    private void startGnssOnHardware(){
        if(!active||!gnssRequested)return;
        stopGnssOnHardware(UiText.get("status_hardware_014"));if(!active||!gnssRequested)return;
        try{if(locations==null||!locations.getAllProviders().contains(LocationManager.GPS_PROVIDER)){gnssRequested=false;gnssMessage=UiText.get("status_hardware_016");return;}if(!locations.isProviderEnabled(LocationManager.GPS_PROVIDER)){gnssRequested=false;gnssMessage=UiText.get("status_hardware_017");return;}
            gnssRunning=true;if(!locations.registerGnssStatusCallback(callback,hardwareHandler))throw new IllegalStateException();locations.requestLocationUpdates(LocationManager.GPS_PROVIDER,1000,0,fixes,hardwareHandler.getLooper());gnssMessage=UiText.get("status_hardware_018");
        }catch(SecurityException e){stopGnssOnHardware(UiText.get("status_hardware_019"));gnssRequested=false;}catch(Exception e){stopGnssOnHardware(UiText.get("status_hardware_020"));gnssRequested=false;}
    }
    public boolean startGnss(){
        gnssRequested=true;if(a.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){a.requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},46);return false;}
        if(!active){gnssMessage=UiText.get("status_hardware_015");return false;}postGnss(this::startGnssOnHardware);return true;
    }
    public void permissionResult(){if(gnssRequested&&active){if(a.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED)startGnss();else stopGnss(UiText.get("gps_047"));}else stopGnss(UiText.get("status_hardware_021"));}
    private void stopGnssOnHardware(String message){gnssRunning=false;unregisterGnss();clearSatelliteStatus(message);}
    public void stopGnss(String message){gnssRunning=false;gnssRequested=false;gnssPaused=false;postGnss(() -> stopGnssOnHardware(message));}
    private int[] companyOrder(){String raw=a.preferences.getString("status_sat_company_order","5,1,6,3,4,7,2,0");ArrayList<Integer> out=new ArrayList<>();for(String token:raw.split(","))try{int n=Integer.parseInt(token.trim());if(!out.contains(n))out.add(n);}catch(Exception ignored){}for(int n:new int[]{5,1,6,3,4,7,2,0})if(!out.contains(n))out.add(n);int[] result=new int[out.size()];for(int n=0;n<out.size();n++)result[n]=out.get(n);return result;}
    public List<Satellite> shown(){
        String sort=a.preferences.getString("status_sat_sort","company"),companyOrder=a.preferences.getString("status_sat_company_order","5,1,6,3,4,7,2,0");
        StringBuilder settings=new StringBuilder(sort).append('|').append(companyOrder).append('|');
        for(int constellation=0;constellation<8;constellation++)settings.append(a.preferences.getBoolean("status_constellation_"+constellation,true)?'1':'0');
        String key=settings.toString();StatusSatelliteStore.Snapshot<Satellite> snapshot=satelliteStore.snapshot();long revision=snapshot.revision;if(shownSatelliteRevision==revision&&key.equals(shownSatelliteSettings))return shownSatelliteCache;List<Satellite> rows=new ArrayList<>();for(Satellite s:snapshot.values)if(a.preferences.getBoolean("status_constellation_"+s.constellation,true))rows.add(s);
        final int[] order=companyOrder();Comparator<Satellite> comparator=Comparator.comparingInt((Satellite s)->StatusMath.constellationRank(s.constellation,order)).thenComparingInt(s->s.svid);
        if("cn0".equals(sort))comparator=Comparator.comparingDouble((Satellite s)->Float.isFinite(s.cn0)?-s.cn0:Double.POSITIVE_INFINITY).thenComparingInt(s->StatusMath.constellationRank(s.constellation,order)).thenComparingInt(s->s.svid);
        rows.sort(comparator);shownSatelliteCache=Collections.unmodifiableList(rows);shownSatelliteRevision=revision;shownSatelliteSettings=key;return shownSatelliteCache;
    }
    public long satelliteVersion(){return satelliteStore.snapshot().revision;}
    public List<Satellite> snapshotSatellites(){return new ArrayList<>(satelliteStore.snapshot().values);}
    public synchronized String sensorText(int type,String unit){if(type==Sensor.TYPE_LIGHT&&"camera".equals(a.preferences.getString("status_light_source","sensor"))){return cameraPaused?a.getString(R.string.camera_paused):cameraReader==null?a.getString(R.string.camera_waiting):cameraReader.text();}Sensor s=sensor(type);if(s==null)return UiText.get("status_hardware_022");float[] v=values.get(type);if(v==null||!sampleAt.containsKey(type))return UiText.get("status_hardware_023");
        double maximum=s.getMaximumRange();for(float sample:v)if(!Float.isFinite(sample)||(Double.isFinite(maximum)&&maximum>0&&Math.abs(sample)>maximum*1.05+.01))return UiText.get("status_hardware_024");
        if(v.length>0){float sample=v[0];if(type==Sensor.TYPE_AMBIENT_TEMPERATURE&&sample< -273.15f||type==Sensor.TYPE_PRESSURE&&sample<=0||type==Sensor.TYPE_LIGHT&&sample<0||type==Sensor.TYPE_PROXIMITY&&sample<0||type==Sensor.TYPE_RELATIVE_HUMIDITY&&(sample<0||sample>100))return UiText.get("status_hardware_024");}
        long age=(SystemClock.elapsedRealtime()-sampleAt.get(type))/1000;String old=age>5?" · "+age+UiText.get("earthquake_025"):"";
        if(v.length>=3)return String.format(Locale.ROOT,"X %.3f · Y %.3f · Z %.3f %s%s",v[0],v[1],v[2],unit,old);
        return String.format(Locale.ROOT,"%.2f %s%s",v[0],unit,old);
    }
}
