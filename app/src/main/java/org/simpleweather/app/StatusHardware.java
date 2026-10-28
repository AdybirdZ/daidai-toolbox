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
final class StatusHardware implements SensorEventListener {
    final MainActivity a;private final SensorManager sensors;private final LocationManager locations;
    final Map<Integer,float[]> values=new HashMap<>();final Map<Integer,Integer> accuracy=new HashMap<>();
    final Map<Integer,Long> sampleAt=new HashMap<>();
    boolean active,gnssRunning,gnssRequested,gnssPaused;String gnssMessage="尚未开始卫星接收";
    double magneticHeading=Double.NaN,pitch=Double.NaN,roll=Double.NaN;boolean rotationVector;
    int compassAccuracy=SensorManager.SENSOR_STATUS_UNRELIABLE;long headingAt,gnssAt;
    final List<Satellite> satellites=new ArrayList<>();
    private float[] gravity,magnetic;private int lastDisplay=-1;private boolean matrixReady;private final float[] rotation=new float[9],remapped=new float[9],angles=new float[3];
    static final int[] TYPES={Sensor.TYPE_GYROSCOPE,Sensor.TYPE_ACCELEROMETER,Sensor.TYPE_LINEAR_ACCELERATION,Sensor.TYPE_GRAVITY,Sensor.TYPE_MAGNETIC_FIELD,Sensor.TYPE_PRESSURE,Sensor.TYPE_LIGHT,Sensor.TYPE_PROXIMITY,Sensor.TYPE_AMBIENT_TEMPERATURE,Sensor.TYPE_RELATIVE_HUMIDITY};
    static final class Satellite {
        final int constellation,svid;final float elevation,azimuth,cn0;final boolean used;
        Satellite(int c,int id,float e,float az,float cn,boolean u){constellation=c;svid=id;elevation=e;azimuth=az;cn0=cn;used=u;}
        String key(){return constellation+":"+svid;}
        String label(){return StatusMath.constellation(constellation)+" · "+StatusMath.satelliteId(constellation,svid);}
        boolean validAngles(){return StatusMath.satelliteAngles(elevation,azimuth);}
        boolean plotted(){return validAngles()&&elevation>=0;}
    }
    StatusHardware(MainActivity a){this.a=a;sensors=(SensorManager)a.getSystemService(Context.SENSOR_SERVICE);locations=(LocationManager)a.getSystemService(Context.LOCATION_SERVICE);}
    Sensor sensor(int type){return sensors==null?null:sensors.getDefaultSensor(type);}
    private boolean compassWanted,allSensorsWanted;
    void resume(boolean compass,boolean allSensors){
        if(active&&compass==compassWanted&&allSensors==allSensorsWanted)return;
        if(sensors!=null)sensors.unregisterListener(this);active=true;compassWanted=compass;allSensorsWanted=allSensors;magneticHeading=Double.NaN;headingAt=0;gravity=null;magnetic=null;matrixReady=false;lastDisplay=-1;
        if(sensors!=null){int delay=sensorDelay();Sensor vector=compass?sensor(Sensor.TYPE_ROTATION_VECTOR):null;rotationVector=vector!=null;if(vector!=null)sensors.registerListener(this,vector,delay);for(int type:TYPES){boolean fallback=compass&&!rotationVector&&(type==Sensor.TYPE_ACCELEROMETER||type==Sensor.TYPE_MAGNETIC_FIELD);if(!allSensors&&!fallback)continue;if(allSensors&&!sensorEnabled(type)&&!fallback)continue;Sensor sensor=sensor(type);if(sensor!=null)sensors.registerListener(this,sensor,delay);}}
        if(gnssPaused){gnssPaused=false;startGnss();}
    }
    int sensorDelay(){int d=a.preferences.getInt("status_sensor_delay",SensorManager.SENSOR_DELAY_FASTEST);return d==SensorManager.SENSOR_DELAY_GAME||d==SensorManager.SENSOR_DELAY_UI||d==SensorManager.SENSOR_DELAY_NORMAL?d:SensorManager.SENSOR_DELAY_FASTEST;}
    boolean sensorEnabled(int type){return a.preferences.getBoolean("status_sensor_show_"+type,true);}
    void pause(){pause(a.preferences.getBoolean("status_receive_pause",true));}
    void pause(boolean preserveReceive){active=false;if(sensors!=null)sensors.unregisterListener(this);if(preserveReceive&&(gnssRunning||gnssRequested)){try{if(locations!=null){locations.unregisterGnssStatusCallback(callback);locations.removeUpdates(fixes);}}catch(Exception ignored){}gnssRunning=false;gnssPaused=true;gnssMessage="卫星接收已暂停 · 返回状态页后继续";return;}stopGnss("已停止卫星接收 · 切页或后台即停止");gnssPaused=false;gnssRequested=false;}
    @Override public void onAccuracyChanged(Sensor s,int level){accuracy.put(s.getType(),level);if(s.getType()==(rotationVector?Sensor.TYPE_ROTATION_VECTOR:Sensor.TYPE_MAGNETIC_FIELD))compassAccuracy=level;}
    @Override public void onSensorChanged(SensorEvent e){if(!active)return;values.put(e.sensor.getType(),e.values.clone());sampleAt.put(e.sensor.getType(),SystemClock.elapsedRealtime());accuracy.put(e.sensor.getType(),e.accuracy);
        if(e.sensor.getType()==Sensor.TYPE_ACCELEROMETER)gravity=e.values.clone();if(e.sensor.getType()==Sensor.TYPE_MAGNETIC_FIELD)magnetic=e.values.clone();
        boolean ready=false;if(rotationVector&&e.sensor.getType()==Sensor.TYPE_ROTATION_VECTOR){SensorManager.getRotationMatrixFromVector(rotation,e.values);compassAccuracy=e.accuracy;ready=true;}
        else if(!rotationVector&&gravity!=null&&magnetic!=null&&(e.sensor.getType()==Sensor.TYPE_ACCELEROMETER||e.sensor.getType()==Sensor.TYPE_MAGNETIC_FIELD)){ready=SensorManager.getRotationMatrix(rotation,null,gravity,magnetic);compassAccuracy=accuracy.containsKey(Sensor.TYPE_MAGNETIC_FIELD)?accuracy.get(Sensor.TYPE_MAGNETIC_FIELD):0;}
        if(!ready)return;
        matrixReady=true;orient();headingAt=SystemClock.elapsedRealtime();
    }
    void configurationChanged(){if(active&&matrixReady)orient();}
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
    boolean headingAvailable(){return active&&Double.isFinite(magneticHeading)&&SystemClock.elapsedRealtime()-headingAt<5000&&Math.abs(pitch)<80&&Math.abs(roll)<80;}
    String compassSource(){return rotationVector?"旋转矢量传感器":sensor(Sensor.TYPE_ACCELEROMETER)!=null&&sensor(Sensor.TYPE_MAGNETIC_FIELD)!=null?"加速度计 + 磁力计":"此设备缺少方向传感器";}
    double declination(Location fix){if(fix==null||!StatusMath.coordinate(fix.getLongitude(),fix.getLatitude()))return Double.NaN;return new GeomagneticField((float)fix.getLatitude(),(float)fix.getLongitude(),fix.hasAltitude()?(float)fix.getAltitude():0,System.currentTimeMillis()).getDeclination();}
    double trueHeading(Location fix){double d=declination(fix);return headingAvailable()&&Double.isFinite(d)?StatusMath.norm(magneticHeading+d):Double.NaN;}
    private final LocationListener fixes=new LocationListener(){public void onLocationChanged(Location l){if(active&&gnssRunning){LocationService.record(a,l);}}public void onStatusChanged(String p,int s,Bundle b){}public void onProviderEnabled(String p){gnssMessage="卫星定位已开启，等待接收";}public void onProviderDisabled(String p){satellites.clear();gnssAt=0;gnssMessage="系统卫星定位已关闭";}};
    private final GnssStatus.Callback callback=new GnssStatus.Callback(){
        @Override public void onStarted(){if(gnssRunning)gnssMessage="正在接收 · 请在开阔处等待";}
        @Override public void onStopped(){satellites.clear();gnssAt=0;if(gnssRunning)gnssMessage="卫星引擎已停止，等待系统恢复";}
        @Override public void onFirstFix(int ms){if(gnssRunning)gnssMessage="已获得首次卫星定位";}
        @Override public void onSatelliteStatusChanged(GnssStatus status){if(!active||!gnssRunning)return;Map<String,Satellite> unique=new LinkedHashMap<>();for(int i=0;i<status.getSatelliteCount();i++){Satellite s=new Satellite(status.getConstellationType(i),status.getSvid(i),status.getElevationDegrees(i),status.getAzimuthDegrees(i),status.getCn0DbHz(i),status.usedInFix(i));Satellite old=unique.get(s.key());if(old!=null)s=new Satellite(s.constellation,s.svid,s.elevation,s.azimuth,Math.max(s.cn0,old.cn0),s.used||old.used);unique.put(s.key(),s);}satellites.clear();satellites.addAll(unique.values());satellites.sort(Comparator.comparingInt((Satellite s)->s.constellation).thenComparingInt(s->s.svid));gnssAt=SystemClock.elapsedRealtime();gnssMessage=satellites.isEmpty()?"尚未报告卫星 · 接收不代表天空中没有卫星":"已收到设备卫星状态";}
    };
    boolean startGnss(){
        gnssRequested=true;if(a.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){a.requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},46);return false;}
        stopGnss("正在启动卫星接收");gnssRequested=true;
        try{if(!active){gnssMessage="请回到状态页前台开始";return false;}if(locations==null||!locations.getAllProviders().contains(LocationManager.GPS_PROVIDER)){gnssMessage="此设备没有卫星定位提供程序";return false;}if(!locations.isProviderEnabled(LocationManager.GPS_PROVIDER)){gnssMessage="请先打开系统定位开关";return false;}
            gnssRunning=true;if(!locations.registerGnssStatusCallback(callback,new Handler(Looper.getMainLooper())))throw new IllegalStateException();locations.requestLocationUpdates(LocationManager.GPS_PROVIDER,1000,0,fixes,Looper.getMainLooper());gnssMessage="等待设备报告卫星 · 室内可能无信号";return true;
        }catch(SecurityException e){stopGnss("需要精确定位权限，近似定位不足以读取卫星");}catch(Exception e){stopGnss("此设备或系统未提供卫星状态");}return false;
    }
    void permissionResult(){if(gnssRequested&&active){if(a.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED)startGnss();else stopGnss("未授予精确定位，不能读取卫星状态");}else stopGnss("卫星接收未开始");}
    void stopGnss(String message){gnssRunning=false;gnssRequested=false;gnssPaused=false;try{if(locations!=null){locations.unregisterGnssStatusCallback(callback);locations.removeUpdates(fixes);}}catch(Exception ignored){}satellites.clear();gnssAt=0;gnssMessage=message;}
    private int[] companyOrder(){String raw=a.preferences.getString("status_sat_company_order","5,1,6,3,4,7,2,0");ArrayList<Integer> out=new ArrayList<>();for(String token:raw.split(","))try{int n=Integer.parseInt(token.trim());if(!out.contains(n))out.add(n);}catch(Exception ignored){}for(int n:new int[]{5,1,6,3,4,7,2,0})if(!out.contains(n))out.add(n);int[] result=new int[out.size()];for(int n=0;n<out.size();n++)result[n]=out.get(n);return result;}
    List<Satellite> shown(){List<Satellite> rows=new ArrayList<>();for(Satellite s:satellites)if(a.preferences.getBoolean("status_constellation_"+s.constellation,true))rows.add(s);final int[] order=companyOrder();String sort=a.preferences.getString("status_sat_sort","company");Comparator<Satellite> comparator=Comparator.comparingInt((Satellite s)->StatusMath.constellationRank(s.constellation,order)).thenComparingInt(s->s.svid);if("cn0".equals(sort))comparator=Comparator.comparingDouble((Satellite s)->Float.isFinite(s.cn0)?-s.cn0:Double.POSITIVE_INFINITY).thenComparingInt(s->StatusMath.constellationRank(s.constellation,order)).thenComparingInt(s->s.svid);rows.sort(comparator);return rows;}
    String sensorText(int type,String unit){Sensor s=sensor(type);if(s==null)return "设备未提供";float[] v=values.get(type);if(v==null||!sampleAt.containsKey(type))return "等待传感器结果";
        double maximum=s.getMaximumRange();for(float sample:v)if(!Float.isFinite(sample)||(Double.isFinite(maximum)&&maximum>0&&Math.abs(sample)>maximum*1.05+.01))return "设备返回无效数据";
        if(v.length>0){float sample=v[0];if(type==Sensor.TYPE_AMBIENT_TEMPERATURE&&sample< -273.15f||type==Sensor.TYPE_PRESSURE&&sample<=0||type==Sensor.TYPE_LIGHT&&sample<0||type==Sensor.TYPE_PROXIMITY&&sample<0||type==Sensor.TYPE_RELATIVE_HUMIDITY&&(sample<0||sample>100))return "设备返回无效数据";}
        long age=(SystemClock.elapsedRealtime()-sampleAt.get(type))/1000;String old=age>5?" · "+age+" 秒前":"";
        if(v.length>=3)return String.format(Locale.ROOT,"X %.3f · Y %.3f · Z %.3f %s%s",v[0],v[1],v[2],unit,old);
        return String.format(Locale.ROOT,"%.2f %s%s",v[0],unit,old);
    }
}
