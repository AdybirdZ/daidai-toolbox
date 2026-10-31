package org.simpleweather.app;

import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.camera2.*;
import android.media.Image;
import android.media.ImageReader;
import android.os.*;
import android.util.*;
import java.nio.ByteBuffer;
import java.util.*;

/** Camera2 Y-plane samples. Exposure is fixed where possible; never a calibrated lux meter. */
final class CameraLightReader {
    private final MainActivity activity;
    private HandlerThread thread;private Handler worker;private CameraDevice device;private CameraCaptureSession session;private ImageReader reader;
    private volatile boolean active;private int generation;
    private volatile float sample=-1;private volatile int exposureMode; // 0 waiting, 1 manual, 2 AE locked, 3 auto, 4 unavailable
    private volatile long exposureNanos;private volatile int sensitivity;
    private boolean manual,lockSupported,lockRequested;private int frames;
    private long requestedExposure,requestedDuration;private int requestedIso;
    CameraLightReader(MainActivity a){activity=a;}
    boolean start(){
        stop();sample=-1;exposureMode=0;
        if(activity.checkSelfPermission(android.Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){activity.requestPermissions(new String[]{android.Manifest.permission.CAMERA},48);return false;}
        try{
            CameraManager manager=(CameraManager)activity.getSystemService(android.content.Context.CAMERA_SERVICE);
            int facing="front".equals(activity.preferences.getString("status_light_camera","back"))?CameraCharacteristics.LENS_FACING_FRONT:CameraCharacteristics.LENS_FACING_BACK;
            String id=null;for(String item:manager.getCameraIdList())if(Objects.equals(manager.getCameraCharacteristics(item).get(CameraCharacteristics.LENS_FACING),facing)){id=item;break;}
            if(id==null){exposureMode=4;return false;}CameraCharacteristics info=manager.getCameraCharacteristics(id);
            int[] caps=info.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);Range<Long> times=info.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE);Range<Integer> iso=info.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE);Long maxDuration=info.get(CameraCharacteristics.SENSOR_INFO_MAX_FRAME_DURATION);
            manual=caps!=null&&java.util.Arrays.stream(caps).anyMatch(c->c==CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR)&&times!=null&&iso!=null&&maxDuration!=null;
            lockSupported=Boolean.TRUE.equals(info.get(CameraCharacteristics.CONTROL_AE_LOCK_AVAILABLE));lockRequested=false;frames=0;
            android.hardware.camera2.params.StreamConfigurationMap map=info.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);Size[] sizes=map==null?null:map.getOutputSizes(ImageFormat.YUV_420_888);if(sizes==null||sizes.length==0)throw new IllegalStateException();
            Size size=Collections.min(Arrays.asList(sizes),Comparator.comparingLong(s->(long)s.getWidth()*s.getHeight()));
            if(manual){requestedExposure=times.clamp(CameraExposure.milliseconds(activity.preferences.getInt("status_camera_exposure_ms",CameraExposure.DEFAULT_MS))*1_000_000L);requestedIso=iso.clamp(CameraExposure.iso(activity.preferences.getInt("status_camera_iso",CameraExposure.DEFAULT_ISO)));requestedDuration=Math.min(maxDuration,Math.max(Math.max(requestedExposure,33_333_333L),map.getOutputMinFrameDuration(ImageFormat.YUV_420_888,size)));if(requestedDuration<requestedExposure)manual=false;}
            active=true;final int token=++generation;thread=new HandlerThread("camera-light");thread.start();worker=new Handler(thread.getLooper());reader=ImageReader.newInstance(size.getWidth(),size.getHeight(),ImageFormat.YUV_420_888,2);
            reader.setOnImageAvailableListener(source->{try(Image image=source.acquireLatestImage()){if(image==null||!valid(token))return;Image.Plane plane=image.getPlanes()[0];ByteBuffer bytes=plane.getBuffer();int row=plane.getRowStride(),pixel=plane.getPixelStride(),step=Math.max(1,(int)Math.sqrt((double)image.getWidth()*image.getHeight()/4096));long sum=0;int count=0;
                for(int y=0;y<image.getHeight();y+=step)for(int x=0;x<image.getWidth();x+=step){int offset=y*row+x*pixel;if(offset<bytes.limit()){sum+=bytes.get(offset)&255;count++;}}if(count>0)sample=sum/(float)count;
            }catch(Exception ignored){}},worker);
            manager.openCamera(id,new CameraDevice.StateCallback(){public void onOpened(CameraDevice c){if(!valid(token)){c.close();return;}device=c;try{c.createCaptureSession(Collections.singletonList(reader.getSurface()),new CameraCaptureSession.StateCallback(){public void onConfigured(CameraCaptureSession s){if(!valid(token)){s.close();return;}session=s;repeat(token,false);}public void onConfigureFailed(CameraCaptureSession s){fail(token);}},worker);}catch(Exception e){fail(token);}}public void onDisconnected(CameraDevice c){c.close();fail(token);}public void onError(CameraDevice c,int error){c.close();fail(token);}},worker);return true;
        }catch(Exception e){stop();exposureMode=4;return false;}
    }
    private boolean valid(int token){return active&&generation==token;}
    private void repeat(int token,boolean lock){
        if(!valid(token)||device==null||session==null||reader==null)return;
        try{CaptureRequest.Builder request=device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);request.addTarget(reader.getSurface());
            request.set(CaptureRequest.CONTROL_MODE,CaptureRequest.CONTROL_MODE_AUTO);
            if(manual){request.set(CaptureRequest.CONTROL_AE_MODE,CaptureRequest.CONTROL_AE_MODE_OFF);request.set(CaptureRequest.SENSOR_EXPOSURE_TIME,requestedExposure);request.set(CaptureRequest.SENSOR_SENSITIVITY,requestedIso);request.set(CaptureRequest.SENSOR_FRAME_DURATION,requestedDuration);}
            else {request.set(CaptureRequest.CONTROL_AE_MODE,CaptureRequest.CONTROL_AE_MODE_ON);if(lockSupported)request.set(CaptureRequest.CONTROL_AE_LOCK,lock);}
            session.setRepeatingRequest(request.build(),new CameraCaptureSession.CaptureCallback(){@Override public void onCaptureCompleted(CameraCaptureSession s,CaptureRequest req,TotalCaptureResult result){if(!valid(token))return;Long time=result.get(CaptureResult.SENSOR_EXPOSURE_TIME);Integer gain=result.get(CaptureResult.SENSOR_SENSITIVITY);if(time!=null)exposureNanos=time;if(gain!=null)sensitivity=gain;
                Integer mode=result.get(CaptureResult.CONTROL_AE_MODE),state=result.get(CaptureResult.CONTROL_AE_STATE);Boolean locked=result.get(CaptureResult.CONTROL_AE_LOCK);
                if(manual&&Objects.equals(mode,CaptureResult.CONTROL_AE_MODE_OFF)){exposureMode=1;return;}
                if(Boolean.TRUE.equals(locked)||Objects.equals(state,CaptureResult.CONTROL_AE_STATE_LOCKED)){exposureMode=2;return;}
                exposureMode=3;frames++;if(manual&&frames>=5){manual=false;frames=0;repeat(token,false);return;}
                if(!manual&&lockSupported&&!lockRequested&&(Objects.equals(state,CaptureResult.CONTROL_AE_STATE_CONVERGED)||Objects.equals(state,CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED)||frames>=30)){lockRequested=true;repeat(token,true);}
            }},worker);
        }catch(Exception e){if(manual){manual=false;repeat(token,false);}else if(lock){lockSupported=false;lockRequested=false;repeat(token,false);}else fail(token);}
    }
    private void fail(int token){if(!valid(token))return;new Handler(Looper.getMainLooper()).post(()->{if(valid(token)){stop();exposureMode=4;}});}
    String text(){if(exposureMode==4)return activity.getString(R.string.camera_unavailable);if(sample<0)return activity.getString(R.string.camera_waiting);int label=exposureMode==1?R.string.camera_manual:exposureMode==2?R.string.camera_locked:exposureMode==3?R.string.camera_auto:R.string.camera_waiting;String settings=exposureNanos>0&&sensitivity>0?activity.getString(R.string.camera_exposure_values,exposureNanos/1_000_000.,sensitivity):"";return activity.getString(R.string.camera_sample,sample)+"\n"+activity.getString(label)+settings;}
    void stop(){active=false;generation++;if(session!=null){try{session.close();}catch(Exception ignored){}session=null;}if(device!=null){try{device.close();}catch(Exception ignored){}device=null;}if(reader!=null){try{reader.close();}catch(Exception ignored){}reader=null;}if(thread!=null){thread.quitSafely();thread=null;worker=null;}}
}
