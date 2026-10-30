package org.simpleweather.app;

import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.Handler;
import android.os.Looper;

/** Reads a relative luminance sample without opening a preview window. */
final class CameraLightReader implements Camera.PreviewCallback {
    private final MainActivity activity; private Camera camera; private SurfaceTexture texture;
    private final Handler main=new Handler(Looper.getMainLooper());
    CameraLightReader(MainActivity a){activity=a;}
    boolean start(){
        if(activity.checkSelfPermission(android.Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){activity.requestPermissions(new String[]{android.Manifest.permission.CAMERA},48);return false;}
        try {int want="front".equals(activity.preferences.getString("status_light_camera","back"))?Camera.CameraInfo.CAMERA_FACING_FRONT:Camera.CameraInfo.CAMERA_FACING_BACK;int id=-1;for(int i=0;i<Camera.getNumberOfCameras();i++){Camera.CameraInfo info=new Camera.CameraInfo();Camera.getCameraInfo(i,info);if(info.facing==want){id=i;break;}}if(id<0&&Camera.getNumberOfCameras()>0)id=0;if(id<0)return false;camera=Camera.open(id);texture=new SurfaceTexture(17);camera.setPreviewTexture(texture);camera.setPreviewCallback(this);camera.startPreview();return true;}catch(Exception e){stop();return false;}
    }
    void stop(){if(camera!=null){try{camera.setPreviewCallback(null);camera.stopPreview();camera.release();}catch(Exception ignored){}camera=null;}if(texture!=null){try{texture.release();}catch(Exception ignored){}texture=null;}}
    @Override public void onPreviewFrame(byte[] data,Camera c){if(data==null||data.length==0)return;long sum=0;int count=Math.min(data.length*2/3,20000);for(int i=0;i<count;i++)sum+=(data[i]&255);final float value=sum/(float)Math.max(1,count);activity.preferences.edit().putFloat("status_camera_lux_sample",value).apply();}
}
