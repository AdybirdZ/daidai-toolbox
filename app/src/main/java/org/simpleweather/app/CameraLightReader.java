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

/** Alternating Camera2 Y-plane samples, never a calibrated lux meter. No images are saved. */
final class CameraLightReader {
    private final MainActivity activity;
    private HandlerThread thread;private Handler worker;
    private CameraDevice device;private CameraCaptureSession session;private ImageReader reader;
    private volatile boolean active;private volatile int generation;
    private volatile int exposureMode; // 0 waiting, 1 manual, 2 AE locked, 3 auto, 4 unavailable
    private volatile boolean manual;
    private boolean lockSupported,lockRequested;private int frames;
    private final long[] requestedExposure=new long[2],requestedDuration=new long[2];
    private final int[] requestedIso=new int[2];
    final CameraLightSamples samples=new CameraLightSamples();
    CameraLightReader(MainActivity a){activity=a;}
    boolean start(){
        stop();exposureMode=0;
        if(activity.checkSelfPermission(android.Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){activity.requestPermissions(new String[]{android.Manifest.permission.CAMERA},48);return false;}
        try{
            CameraManager manager=(CameraManager)activity.getSystemService(android.content.Context.CAMERA_SERVICE);
            int facing="front".equals(activity.preferences.getString("status_light_camera","back"))?CameraCharacteristics.LENS_FACING_FRONT:CameraCharacteristics.LENS_FACING_BACK;
            String id=null;
            for(String item:manager.getCameraIdList())if(Objects.equals(manager.getCameraCharacteristics(item).get(CameraCharacteristics.LENS_FACING),facing)){id=item;break;}
            if(id==null){exposureMode=4;return false;}
            CameraCharacteristics info=manager.getCameraCharacteristics(id);
            int[] caps=info.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
            Range<Long> times=info.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE);
            Range<Integer> iso=info.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE);
            Long maxDuration=info.get(CameraCharacteristics.SENSOR_INFO_MAX_FRAME_DURATION);
            manual=caps!=null&&Arrays.stream(caps).anyMatch(c->c==CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR)&&times!=null&&iso!=null&&maxDuration!=null;
            lockSupported=Boolean.TRUE.equals(info.get(CameraCharacteristics.CONTROL_AE_LOCK_AVAILABLE));lockRequested=false;frames=0;
            android.hardware.camera2.params.StreamConfigurationMap map=info.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Size[] sizes=map==null?null:map.getOutputSizes(ImageFormat.YUV_420_888);
            if(sizes==null||sizes.length==0)throw new IllegalStateException();
            Size size=Collections.min(Arrays.asList(sizes),Comparator.comparingLong(s->(long)s.getWidth()*s.getHeight()));
            if(manual)for(int p=0;p<2;p++){
                requestedExposure[p]=Math.min(maxDuration,times.clamp(CameraExposure.milliseconds(activity.preferences.getInt(CameraExposure.msKey(p),CameraExposure.defaultMs(p)),p)*1_000_000L));
                requestedIso[p]=iso.clamp(CameraExposure.iso(activity.preferences.getInt(CameraExposure.isoKey(p),CameraExposure.defaultIso(p)),p));
                requestedDuration[p]=Math.min(maxDuration,Math.max(Math.max(requestedExposure[p],33_333_333L),map.getOutputMinFrameDuration(ImageFormat.YUV_420_888,size)));
            }
            active=true;final int token=++generation;
            thread=new HandlerThread("camera-light");thread.start();worker=new Handler(thread.getLooper());
            reader=ImageReader.newInstance(size.getWidth(),size.getHeight(),ImageFormat.YUV_420_888,3);
            reader.setOnImageAvailableListener(source->{try(Image image=source.acquireNextImage()){
                if(image==null||!valid(token))return;
                Image.Plane plane=image.getPlanes()[0];ByteBuffer bytes=plane.getBuffer();
                int row=plane.getRowStride(),pixel=plane.getPixelStride(),step=Math.max(1,(int)Math.sqrt((double)image.getWidth()*image.getHeight()/4096));
                long sum=0;int count=0;
                for(int y=0;y<image.getHeight();y+=step)for(int x=0;x<image.getWidth();x+=step){int offset=y*row+x*pixel;if(offset<bytes.limit()){sum+=bytes.get(offset)&255;count++;}}
                if(count>0&&valid(token))samples.image(image.getTimestamp(),sum/(float)count,SystemClock.elapsedRealtime());
            }catch(Exception ignored){}},worker);
            manager.openCamera(id,new CameraDevice.StateCallback(){
                public void onOpened(CameraDevice c){
                    if(!valid(token)){c.close();return;}device=c;
                    try{c.createCaptureSession(Collections.singletonList(reader.getSurface()),new CameraCaptureSession.StateCallback(){
                        public void onConfigured(CameraCaptureSession s){if(!valid(token)){s.close();return;}session=s;repeat(token,false);}
                        public void onConfigureFailed(CameraCaptureSession s){s.close();fail(token);}
                    },worker);}catch(Exception e){fail(token);}
                }
                public void onDisconnected(CameraDevice c){c.close();fail(token);}
                public void onError(CameraDevice c,int error){c.close();fail(token);}
            },worker);
            return true;
        }catch(Exception e){stop();exposureMode=4;return false;}
    }
    private boolean valid(int token){return active&&generation==token;}
    private void repeat(int token,boolean lock){
        if(!valid(token)||device==null||session==null||reader==null)return;
        try{
            List<CaptureRequest> requests=new ArrayList<>();
            for(int p=0;p<(manual?2:1);p++){
                CaptureRequest.Builder request=device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                request.addTarget(reader.getSurface());request.setTag(manual?p:2);
                request.set(CaptureRequest.CONTROL_MODE,CaptureRequest.CONTROL_MODE_AUTO);
                request.set(CaptureRequest.FLASH_MODE,CaptureRequest.FLASH_MODE_OFF);
                if(manual){
                    request.set(CaptureRequest.CONTROL_AE_MODE,CaptureRequest.CONTROL_AE_MODE_OFF);
                    request.set(CaptureRequest.SENSOR_EXPOSURE_TIME,requestedExposure[p]);
                    request.set(CaptureRequest.SENSOR_SENSITIVITY,requestedIso[p]);
                    request.set(CaptureRequest.SENSOR_FRAME_DURATION,requestedDuration[p]);
                }else{
                    request.set(CaptureRequest.CONTROL_AE_MODE,CaptureRequest.CONTROL_AE_MODE_ON);
                    if(lockSupported)request.set(CaptureRequest.CONTROL_AE_LOCK,lock);
                }
                requests.add(request.build());
            }
            session.setRepeatingBurst(requests,new CameraCaptureSession.CaptureCallback(){
                @Override public void onCaptureCompleted(CameraCaptureSession s,CaptureRequest req,TotalCaptureResult result){
                    if(!valid(token))return;
                    int profile=(Integer)req.getTag();
                    if((profile<2)!=manual)return;
                    Long stamp=result.get(CaptureResult.SENSOR_TIMESTAMP),time=result.get(CaptureResult.SENSOR_EXPOSURE_TIME);
                    Integer gain=result.get(CaptureResult.SENSOR_SENSITIVITY),mode=result.get(CaptureResult.CONTROL_AE_MODE),state=result.get(CaptureResult.CONTROL_AE_STATE);
                    Boolean locked=result.get(CaptureResult.CONTROL_AE_LOCK);
                    boolean fixed=manual&&Objects.equals(mode,CaptureResult.CONTROL_AE_MODE_OFF);
                    int measuredMode=fixed?1:Boolean.TRUE.equals(locked)||Objects.equals(state,CaptureResult.CONTROL_AE_STATE_LOCKED)?2:3;
                    exposureMode=measuredMode;
                    if(stamp!=null)samples.metadata(stamp,profile,time==null?0:time,gain==null?0:gain,measuredMode);
                    if(fixed){frames=0;return;}
                    frames++;
                    if(manual&&frames>=5){fallback(token);return;}
                    if(!manual&&lockSupported&&!lockRequested&&(Objects.equals(state,CaptureResult.CONTROL_AE_STATE_CONVERGED)||Objects.equals(state,CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED)||frames>=30)){lockRequested=true;repeat(token,true);}
                }
                @Override public void onCaptureFailed(CameraCaptureSession s,CaptureRequest req,CaptureFailure failure){if(valid(token)&&((Integer)req.getTag()<2)==manual){if(manual)fallback(token);else fail(token);}}
            },worker);
        }catch(Exception e){if(manual)fallback(token);else if(lock){lockSupported=false;lockRequested=false;repeat(token,false);}else fail(token);}
    }
    private void fallback(int token){manual=false;frames=0;samples.clear();repeat(token,false);}
    private void fail(int token){if(!valid(token))return;new Handler(Looper.getMainLooper()).post(()->{if(valid(token)){stop();exposureMode=4;}});}
    private String reading(CameraLightSamples.Reading r){
        if(r==null)return activity.getString(R.string.camera_waiting);
        String text=activity.getString(R.string.camera_brightness_value,r.brightness);
        if(r.exposureNanos>0&&r.iso>0)text+=activity.getString(R.string.camera_exposure_values,r.exposureNanos/1_000_000.,r.iso);
        long age=Math.max(0,(SystemClock.elapsedRealtime()-r.receivedAt)/1000);
        if(age>=3)text+=activity.getString(R.string.camera_sample_age,age);
        return text;
    }
    String text(){
        if(exposureMode==4)return activity.getString(R.string.camera_unavailable);
        String heading=activity.getString(R.string.camera_samples_heading);
        if(manual)return heading+"\n"+activity.getString(R.string.camera_indoor_value,reading(samples.get(0)))+"\n"+activity.getString(R.string.camera_outdoor_value,reading(samples.get(1)));
        if(exposureMode==0)return heading+"\n"+activity.getString(R.string.camera_waiting);
        int label=exposureMode==2?R.string.camera_locked:R.string.camera_auto;
        return heading+"\n"+activity.getString(R.string.camera_dual_unavailable)+"\n"+activity.getString(label)+"\n"+reading(samples.get(2));
    }
    void stop(){
        active=false;generation++;
        if(session!=null){try{session.close();}catch(Exception ignored){}session=null;}
        if(device!=null){try{device.close();}catch(Exception ignored){}device=null;}
        if(reader!=null){try{reader.close();}catch(Exception ignored){}reader=null;}
        if(thread!=null){thread.quitSafely();thread=null;worker=null;}
        samples.clear();
    }
}
