package org.simpleweather.app;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.os.Bundle;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.Gravity;
import android.widget.*;
import java.util.List;

/** Explicitly opened camera reader for relative ambient-light comparison. */
public final class CameraLightActivity extends Activity implements SurfaceHolder.Callback, Camera.PreviewCallback {
    private Camera camera; private TextView reading; private SurfaceView preview;
    @Override public void onCreate(Bundle state){super.onCreate(state);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(24,24,24,24);reading=new TextView(this);reading.setText("正在打开相机…");reading.setTextSize(16);root.addView(reading,new LinearLayout.LayoutParams(-1,70));preview=new SurfaceView(this);root.addView(preview,new LinearLayout.LayoutParams(-1,0,1));TextView close=new TextView(this);close.setText("关闭");close.setGravity(Gravity.CENTER);close.setTextSize(16);close.setPadding(12,20,12,20);close.setOnClickListener(v->finish());root.addView(close,new LinearLayout.LayoutParams(-1,70));setContentView(root);preview.getHolder().addCallback(this);}
    private void open(){if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.CAMERA},8);return;}try{String want=getSharedPreferences("toolbox",0).getString("status_light_camera","back");int facing="front".equals(want)?Camera.CameraInfo.CAMERA_FACING_FRONT:Camera.CameraInfo.CAMERA_FACING_BACK;int id=-1;for(int i=0;i<Camera.getNumberOfCameras();i++){Camera.CameraInfo info=new Camera.CameraInfo();Camera.getCameraInfo(i,info);if(info.facing==facing){id=i;break;}}if(id<0&&Camera.getNumberOfCameras()>0)id=0;if(id<0)throw new IllegalStateException();camera=Camera.open(id);camera.setPreviewDisplay(preview.getHolder());camera.setPreviewCallback(this);Camera.Parameters params=camera.getParameters();List<Camera.Size> sizes=params.getSupportedPreviewSizes();if(sizes!=null&&!sizes.isEmpty())params.setPreviewSize(sizes.get(0).width,sizes.get(0).height);camera.setParameters(params);camera.startPreview();}catch(Exception e){reading.setText("无法打开相机，请检查权限或设备设置");}}
    @Override public void surfaceCreated(SurfaceHolder h){open();}
    @Override public void surfaceChanged(SurfaceHolder h,int f,int w,int t){}
    @Override public void surfaceDestroyed(SurfaceHolder h){closeCamera();}
    @Override public void onPreviewFrame(byte[] data,Camera c){if(data==null||data.length==0)return;long sum=0;int count=Math.min(data.length*2/3,20000);for(int i=0;i<count;i++)sum+=(data[i]&255);final int avg=(int)(sum/Math.max(1,count));runOnUiThread(()->reading.setText("相机环境光读数 · 亮度样本 "+avg+" / 255\n仅作相对参考，不是照度计标定值"));}
    private void closeCamera(){if(camera!=null){camera.setPreviewCallback(null);camera.stopPreview();camera.release();camera=null;}}
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);if(r==8&&g.length>0&&g[0]==PackageManager.PERMISSION_GRANTED)open();else reading.setText("未授予相机权限，无法测量环境光");}
    @Override protected void onPause(){closeCamera();super.onPause();}
    @Override protected void onDestroy(){closeCamera();super.onDestroy();}
}
