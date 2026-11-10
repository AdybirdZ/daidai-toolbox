package org.daidai.modules.colorvision;

import android.app.*;
import android.os.*;
import android.content.*;
import android.content.res.Configuration;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.graphics.*;
import android.media.ExifInterface;
import android.widget.*;
import org.daidai.modules.shared.*;
import java.io.*;
import java.util.concurrent.*;

public class ColorVisionActivity extends Activity {
 private final ExecutorService worker=Executors.newSingleThreadExecutor();
 private final Handler main=new Handler(Looper.getMainLooper());
 private FrameLayout frame;
 private ComparisonView comparison;
 private TextView status;
 private CameraPreview camera;
 private Bitmap[] pictures;
 private Bitmap exportSnapshot;
 private int selectedMask=15;
 private volatile int generation;
 private boolean sourceCamera,front,resumed,busy,ready;

 private String t(String key,Object... args){return Texts.get(this,"cv_"+key,args);}
 private String version(){try{return getPackageManager().getPackageInfo(getPackageName(),0).versionName;}catch(PackageManager.NameNotFoundException error){throw new IllegalStateException(error);}}
 @Override public void onCreate(Bundle saved){
  super.onCreate(saved);
  selectedMask=getSharedPreferences("colorvision_module",0).getInt("views",15)&15;
  if(selectedMask==0)selectedMask=15;
  LinearLayout page=Ui.page(this,t("title"),t("subtitle",version()));
  LinearLayout body=Ui.scroll(page);
  LinearLayout actions=Ui.row(body);
  Ui.cell(actions,t("camera"),this::activateCamera);
  Ui.cell(actions,t("photo"),this::pickPhoto);
  LinearLayout choices=Ui.card(body,t("views"));
  String[] keys={"reference","protan","deutan","tritan"};
  for(int row=0;row<2;row++){
   LinearLayout line=Ui.row(choices);
   for(int column=0;column<2;column++){
    final int bit=1<<(row*2+column);
    CheckBox check=new CheckBox(this);
    check.setText(t(keys[row*2+column]));
    check.setChecked((selectedMask&bit)!=0);
    line.addView(check,new LinearLayout.LayoutParams(0,-2,1));
    check.setOnCheckedChangeListener((button,checked)->{
     int next=checked?selectedMask|bit:selectedMask&~bit;
     if(next==0){check.setChecked(true);Toast.makeText(this,t("one_view"),Toast.LENGTH_SHORT).show();return;}
     selectedMask=next;
     getSharedPreferences("colorvision_module",0).edit().putInt("views",next).apply();
     comparison.setMask(next);
     if(camera!=null)camera.setMask(next);
    });
   }
  }
  frame=new FrameLayout(this);
  frame.setBackgroundColor(0xff101010);
  body.addView(frame,new LinearLayout.LayoutParams(-1,stageHeight()));
  comparison=new ComparisonView(this);
  comparison.setMask(selectedMask);
  frame.addView(comparison,new FrameLayout.LayoutParams(-1,-1));
  status=Ui.text(this,t("choose"),14);body.addView(status);
  LinearLayout controls=Ui.row(body);
  Ui.cell(controls,t("switch_camera"),()->{front=!front;if(sourceCamera)activateCamera();});
  Ui.cell(controls,t("export"),this::export);
  Ui.button(body,t("guide_title"),()->Ui.note(this,t("guide_title"),t("guide")));
 }
 private int stageHeight(){return Math.max(Ui.dp(this,280),Math.min(Ui.dp(this,520),(int)(getResources().getDisplayMetrics().heightPixels*.58f)));}
 @Override public void onConfigurationChanged(Configuration configuration){super.onConfigurationChanged(configuration);frame.getLayoutParams().height=stageHeight();frame.requestLayout();if(camera!=null)camera.requestRender();}
 private void pickPhoto(){try{startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE),10);}catch(Exception e){Ui.error(this,e);}}
 private void closeCamera(){if(camera!=null){camera.stop();frame.removeView(camera);camera=null;}}
 private void clearPictures(){ready=false;comparison.setImages(null);release(pictures);pictures=null;}
 private static void release(Bitmap[] images){if(images!=null)for(Bitmap image:images)if(image!=null&&!image.isRecycled())image.recycle();}
 private void activateCamera(){
  if(checkSelfPermission(android.Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{android.Manifest.permission.CAMERA},20);return;}
  sourceCamera=true;final int token=++generation;closeCamera();clearPictures();comparison.setCamera(true);
  camera=new CameraPreview(this,message->{if(sourceCamera&&token==generation)status.setText(t("camera_failed",message));});
  frame.addView(camera,0,new FrameLayout.LayoutParams(-1,-1));camera.setMask(selectedMask);status.setText(t("live"));
  if(resumed)camera.start(front);
 }
 private boolean stale(int token){return token!=generation||Thread.currentThread().isInterrupted();}
 private void loadPhoto(Uri uri){
  sourceCamera=false;closeCamera();clearPictures();comparison.setCamera(false);
  final int token=++generation;status.setText(t("processing"));
  worker.execute(()->{
   Bitmap[] output=new Bitmap[4];
   try{
    output[0]=decode(uri);int width=output[0].getWidth(),height=output[0].getHeight();
    int[] original=new int[width*height],mapped=new int[width*height];
    output[0].getPixels(original,0,width,0,0,width,height);
    for(int mode=1;mode<=3;mode++){
     for(int i=0;i<original.length;i++){if((i&65535)==0&&stale(token))throw new CancellationException();mapped[i]=ColorVision.pixel(original[i],mode);}
     output[mode]=Bitmap.createBitmap(mapped,width,height,Bitmap.Config.ARGB_8888);
    }
    main.post(()->{if(token!=generation||isDestroyed()){release(output);return;}pictures=output;ready=true;comparison.setImages(output);status.setText(t("size",width,height));});
   }catch(Exception|OutOfMemoryError error){release(output);main.post(()->{if(token==generation&&!isDestroyed()&&!(error instanceof CancellationException))status.setText(t("image_failed"));});}
  });
 }
 private Bitmap decode(Uri uri)throws Exception{
  BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;
  try(InputStream in=getContentResolver().openInputStream(uri)){BitmapFactory.decodeStream(in,null,options);}
  if(options.outWidth<=0||options.outHeight<=0)throw new IOException(t("image_failed"));
  options.inSampleSize=1;while(options.outWidth/options.inSampleSize>2048||options.outHeight/options.inSampleSize>2048)options.inSampleSize*=2;
  options.inJustDecodeBounds=false;options.inPreferredConfig=Bitmap.Config.ARGB_8888;
  Bitmap image;try(InputStream in=getContentResolver().openInputStream(uri)){image=BitmapFactory.decodeStream(in,null,options);}
  if(image==null)throw new IOException(t("image_failed"));
  int orientation=1;try(InputStream in=getContentResolver().openInputStream(uri)){orientation=new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION,1);}catch(IOException ignored){}
  Matrix matrix=new Matrix();
  switch(orientation){case 2:matrix.setScale(-1,1);break;case 3:matrix.setRotate(180);break;case 4:matrix.setScale(1,-1);break;case 5:matrix.setRotate(90);matrix.postScale(-1,1);break;case 6:matrix.setRotate(90);break;case 7:matrix.setRotate(-90);matrix.postScale(-1,1);break;case 8:matrix.setRotate(-90);break;}
  if(!matrix.isIdentity()){Bitmap oriented=Bitmap.createBitmap(image,0,0,image.getWidth(),image.getHeight(),matrix,true);if(oriented!=image)image.recycle();image=oriented;}
  return image;
 }
 private void export(){
  if(sourceCamera||!ready){Toast.makeText(this,t("export_first"),Toast.LENGTH_LONG).show();return;}
  if(busy)return;
  try{exportSnapshot=comparison.snapshot();startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("image/png").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"Daidai-comparison.png"),11);}
  catch(Exception|OutOfMemoryError error){if(exportSnapshot!=null){exportSnapshot.recycle();exportSnapshot=null;}Ui.error(this,new IOException(t("export_failed"),error));}
 }
 @Override protected void onActivityResult(int request,int result,Intent data){
  super.onActivityResult(request,result,data);
  if(result!=RESULT_OK||data==null||data.getData()==null){if(exportSnapshot!=null){exportSnapshot.recycle();exportSnapshot=null;}return;}
  if(request==10)loadPhoto(data.getData());
  else if(request==11&&exportSnapshot!=null){
   Uri destination=data.getData();Bitmap snapshot=exportSnapshot;exportSnapshot=null;busy=true;
   worker.execute(()->{
    Exception failure=null;
    try(OutputStream out=getContentResolver().openOutputStream(destination,"wt")){if(out==null||!snapshot.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException(t("export_failed"));}
    catch(Exception error){failure=error;}finally{snapshot.recycle();}
    final Exception error=failure;main.post(()->{busy=false;if(!isDestroyed()){if(error!=null)Ui.error(this,error);else Toast.makeText(this,t("saved"),Toast.LENGTH_LONG).show();}});
   });
  }
 }
 @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request==20&&results.length>0){if(results[0]==PackageManager.PERMISSION_GRANTED)activateCamera();else status.setText(t("permission"));}}
 @Override protected void onResume(){super.onResume();resumed=true;if(sourceCamera&&camera!=null)camera.start(front);}
 @Override protected void onPause(){resumed=false;if(camera!=null)camera.stop();super.onPause();}
 @Override protected void onDestroy(){generation++;closeCamera();clearPictures();if(exportSnapshot!=null)exportSnapshot.recycle();worker.shutdownNow();super.onDestroy();}
}
