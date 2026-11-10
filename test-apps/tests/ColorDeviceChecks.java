package org.daidai.prototype.qa;
import android.app.*;import android.os.*;import android.content.*;import android.graphics.*;import android.net.Uri;import android.view.*;import android.widget.*;import android.hardware.camera2.*;import java.io.*;import java.lang.reflect.*;import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;
import org.daidai.modules.colorvision.*;

public class ColorDeviceChecks extends Instrumentation {
 @Override public void onCreate(Bundle args){super.onCreate(args);start();}
 private Activity activity;private int checks;
 private void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
 private Field member(Object obj,String name)throws Exception{Class<?> type=obj.getClass();while(type!=null){try{Field f=type.getDeclaredField(name);f.setAccessible(true);return f;}catch(NoSuchFieldException e){type=type.getSuperclass();}}throw new NoSuchFieldException(name);}
 private Object field(Object obj,String name)throws Exception{return member(obj,name).get(obj);}
 private void invoke(String name,Class<?>[] types,Object... args)throws Exception{Method m=ColorVisionActivity.class.getDeclaredMethod(name,types);m.setAccessible(true);final Throwable[] fail={null};runOnMainSync(()->{try{m.invoke(activity,args);}catch(Throwable e){fail[0]=e;}});if(fail[0]!=null)throw new RuntimeException(fail[0]);}
 private void waitFor(Callable<Boolean> predicate)throws Exception{long until=System.currentTimeMillis()+16000;while(System.currentTimeMillis()<until){if(predicate.call())return;Thread.sleep(80);}throw new AssertionError("Color wait timed out");}
 private void checksIn(View view,List<CheckBox> result){if(view instanceof CheckBox)result.add((CheckBox)view);if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)checksIn(((ViewGroup)view).getChildAt(i),result);}
 private void select(int mask){runOnMainSync(()->{List<CheckBox> boxes=new ArrayList<>();checksIn(activity.getWindow().getDecorView(),boxes);for(int i=0;i<4;i++)if((mask&(1<<i))!=0&&!boxes.get(i).isChecked())boxes.get(i).performClick();for(int i=0;i<4;i++)if((mask&(1<<i))==0&&boxes.get(i).isChecked())boxes.get(i).performClick();});}
 private Bitmap snapshot(ComparisonView view){Bitmap[] result={null};runOnMainSync(()->result[0]=view.snapshot());return result[0];}
 private Bitmap capture(CameraPreview camera)throws Exception{Bitmap b=Bitmap.createBitmap(camera.getWidth(),camera.getHeight(),Bitmap.Config.ARGB_8888);CountDownLatch done=new CountDownLatch(1);int[] status={-1};runOnMainSync(()->PixelCopy.request(camera,b,result->{status[0]=result;done.countDown();},new Handler(Looper.getMainLooper())));check(done.await(5,TimeUnit.SECONDS)&&status[0]==PixelCopy.SUCCESS,"capture GL output");return b;}
 private void photoPanels(ComparisonView view,int mask)throws Exception{
  Bitmap bitmap=snapshot(view);float scale=bitmap.getWidth()/(float)view.getWidth();
  for(ComparisonLayout.Panel panel:ComparisonLayout.panels(mask,view.getWidth(),view.getHeight(),view.gap())){
   float fit=Math.min(panel.width/32f,(panel.height-view.labelHeight())/24f);float x=panel.x+(panel.width-32*fit)/2+3*fit,y=panel.y+view.labelHeight()+(panel.height-view.labelHeight())/2f;
   int color=bitmap.getPixel(Math.round(x*scale),Math.round(y*scale));check(color==ColorVision.pixel(0xffff0000,panel.mode),"photo grid panel "+panel.mode+" / mask "+mask+" / "+Integer.toHexString(color));
  }bitmap.recycle();
 }
 @Override public void onStart(){Bundle result=new Bundle();try{
  Context context=getTargetContext();context.getSharedPreferences("colorvision_module",0).edit().putInt("views",15).commit();
  activity=startActivitySync(new Intent().setClassName(context.getPackageName(),context.getPackageName()+".MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
  File input=new File(context.getFilesDir(),"color-fixture.png");Bitmap fixture=Bitmap.createBitmap(32,24,Bitmap.Config.ARGB_8888);int[] colors={0xffff0000,0xff00ff00,0xff0000ff,0xffffffff};for(int y=0;y<24;y++)for(int x=0;x<32;x++)fixture.setPixel(x,y,colors[x/8]);try(OutputStream out=new FileOutputStream(input)){fixture.compress(Bitmap.CompressFormat.PNG,100,out);}
  invoke("onActivityResult",new Class[]{int.class,int.class,Intent.class},10,Activity.RESULT_OK,new Intent().setData(Uri.fromFile(input)));waitFor(()->(Boolean)field(activity,"ready"));Bitmap[] pictures=(Bitmap[])field(activity,"pictures");ComparisonView view=(ComparisonView)field(activity,"comparison");
  check(pictures.length==4&&pictures[0].getWidth()==32&&pictures[0].getHeight()==24,"four image variants");
  for(int mode=0;mode<4;mode++)check(pictures[mode].getPixel(2,2)==ColorVision.pixel(colors[0],mode),"image transform "+mode);
  for(int mask:new int[]{15,3,7,5,8,1}){select(mask);check((Integer)field(activity,"selectedMask")==mask,"checkbox combination "+mask);photoPanels(view,mask);}
  runOnMainSync(()->{List<CheckBox> boxes=new ArrayList<>();checksIn(activity.getWindow().getDecorView(),boxes);boxes.get(0).performClick();});check((Integer)field(activity,"selectedMask")==1,"last panel cannot be hidden");select(15);
  Bitmap comparison=snapshot(view);File output=new File(context.getFilesDir(),"comparison-export.png");member(activity,"exportSnapshot").set(activity,comparison);invoke("onActivityResult",new Class[]{int.class,int.class,Intent.class},11,Activity.RESULT_OK,new Intent().setData(Uri.fromFile(output)));waitFor(()->!(Boolean)field(activity,"busy"));Bitmap exported=BitmapFactory.decodeFile(output.toString());check(exported!=null&&exported.getWidth()==view.getWidth()&&exported.getHeight()==view.getHeight(),"grid PNG export dimensions");exported.recycle();
  getUiAutomation().grantRuntimePermission(context.getPackageName(),android.Manifest.permission.CAMERA);invoke("activateCamera",new Class[]{});CameraPreview camera=(CameraPreview)field(activity,"camera");waitFor(()->{SurfaceTexture texture=(SurfaceTexture)field(camera,"texture");return texture!=null&&texture.getTimestamp()>0;});
  Thread.sleep(800);Bitmap frame=capture(camera);ComparisonLayout.Panel[] panels=ComparisonLayout.panels(15,frame.getWidth(),frame.getHeight(),view.gap());
  for(int mode=1;mode<4;mode++){long error=0,difference=0;int n=0;for(float fy=.08f;fy<.94f;fy+=.035f)for(float fx=.08f;fx<.94f;fx+=.035f){ComparisonLayout.Panel a=panels[0],b=panels[mode];int original=frame.getPixel(a.x+(int)(a.width*fx),a.y+view.labelHeight()+(int)((a.height-view.labelHeight())*fy)),actual=frame.getPixel(b.x+(int)(b.width*fx),b.y+view.labelHeight()+(int)((b.height-view.labelHeight())*fy)),expected=ColorVision.pixel(original,mode);for(int shift=0;shift<=16;shift+=8){error+=Math.abs(((expected>>shift)&255)-((actual>>shift)&255));difference+=Math.abs(((original>>shift)&255)-((actual>>shift)&255));n++;}}check(error/(double)n<8,"same-frame CPU/GPU parity "+mode+": "+error/(double)n);check(difference/(double)n>1,"live simulation differs "+mode+": "+difference/(double)n);}
  CameraDevice originalDevice=(CameraDevice)field(camera,"camera");for(int mask:new int[]{1,3,7,15}){select(mask);check(field(camera,"camera")==originalDevice,"one camera reused for mask "+mask);}
  select(1);Reference reference=orientationReference(camera,view);check(reference.matchesOrientation(),"portrait orientation matches TextureView: "+reference.describe());
  runOnMainSync(()->activity.startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)));waitFor(()->field(camera,"camera")==null&&!((Boolean)field(camera,"active")));check(true,"camera closed in background");reference.close();
  runOnMainSync(()->activity.startActivity(new Intent(activity,activity.getClass()).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)));waitFor(()->field(camera,"camera")!=null&&((Boolean)field(camera,"active")));check(true,"camera resumes");String firstId=((CameraDevice)field(camera,"camera")).getId();member(activity,"front").set(activity,true);invoke("activateCamera",new Class[]{});CameraPreview front=(CameraPreview)field(activity,"camera");waitFor(()->field(front,"camera")!=null);check(!firstId.equals(((CameraDevice)field(front,"camera")).getId()),"front camera selected");waitFor(()->{SurfaceTexture texture=(SurfaceTexture)field(front,"texture");return texture!=null&&texture.getTimestamp()>0;});Reference frontReference=orientationReference(front,view);check(frontReference.matchesOrientation(),"front orientation and mirroring match TextureView: "+frontReference.describe());
  invoke("closeCamera",new Class[]{});frontReference.close();
  for(boolean useFront:new boolean[]{false,true}){
   runOnMainSync(()->activity.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));waitFor(()->activity.getWindowManager().getDefaultDisplay().getRotation()%2==1);
   member(activity,"front").set(activity,useFront);invoke("activateCamera",new Class[]{});CameraPreview landscape=(CameraPreview)field(activity,"camera");waitFor(()->{SurfaceTexture texture=(SurfaceTexture)field(landscape,"texture");return texture!=null&&texture.getTimestamp()>0&&landscape.getWidth()>landscape.getHeight();});Reference landscapeReference=orientationReference(landscape,view);check(landscapeReference.matchesOrientation(),"landscape "+(useFront?"front":"back")+" orientation: "+landscapeReference.describe());invoke("closeCamera",new Class[]{});landscapeReference.close();
  }
  runOnMainSync(()->activity.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));waitFor(()->activity.getWindowManager().getDefaultDisplay().getRotation()==0);select(15);runOnMainSync(()->activity.finish());result.putString("stream","PASS: "+checks+" color 0.2.0 device checks (1-4 panels, PNG, shared frame, CPU/GPU, portrait/landscape TextureView orientation reference, lifecycle)\n");finish(-1,result);
 }catch(Throwable e){result.putString("stream","FAIL: "+android.util.Log.getStackTraceString(e));finish(0,result);}}

 private final class Reference {
  TextureView view;Surface surface;Matrix displayMatrix;
  double error,alternativeError;String alternative;long glTimestamp,nativeTimestamp,sessionTimestamp;
  boolean matchesOrientation(){return error<=.35&&alternativeError-error>=.15;}
  String describe(){return "normalized error="+error+", best other="+alternative+" / "+alternativeError+", timestamps GL="+glTimestamp+", native="+nativeTimestamp+", session="+sessionTimestamp;}
  void close(){runOnMainSync(()->{((android.view.ViewGroup)view.getParent()).removeView(view);surface.release();});}
 }
 private Reference orientationReference(CameraPreview camera,ComparisonView overlay)throws Exception{
  waitFor(()->camera.getWidth()>0&&camera.getHeight()>overlay.labelHeight());
  float[] transform=((float[])field(camera,"transform")).clone();android.util.Size size=(android.util.Size)field(camera,"size");int rotation=activity.getWindowManager().getDefaultDisplay().getRotation()*90;
  float ratio=PreviewGeometry.aspect(size.getWidth(),size.getHeight(),transform,rotation);int available=camera.getHeight()-overlay.labelHeight();int width=Math.min(camera.getWidth(),Math.round(available*ratio)),height=Math.min(available,Math.round(camera.getWidth()/ratio));int x=(camera.getWidth()-width)/2,y=overlay.labelHeight()+(available-height)/2;
  Reference reference=new Reference();runOnMainSync(()->{reference.view=new TextureView(activity);FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(width,height);lp.leftMargin=x;lp.topMargin=y;((FrameLayout)camera.getParent()).addView(reference.view,lp);
   Matrix matrix=new Matrix();float cx=width/2f,cy=height/2f;if(rotation==90||rotation==270){RectF screen=new RectF(0,0,width,height),buffer=new RectF(0,0,size.getHeight(),size.getWidth());buffer.offset(cx-buffer.centerX(),cy-buffer.centerY());matrix.setRectToRect(screen,buffer,Matrix.ScaleToFit.FILL);float scale=Math.max(height/(float)size.getHeight(),width/(float)size.getWidth());matrix.postScale(scale,scale,cx,cy);matrix.postRotate(rotation==90?-90:90,cx,cy);}else if(rotation==180)matrix.postRotate(180,cx,cy);reference.displayMatrix=matrix;reference.view.setTransform(matrix);
  });waitFor(()->reference.view.isAvailable());SurfaceTexture input=reference.view.getSurfaceTexture();input.setDefaultBufferSize(size.getWidth(),size.getHeight());reference.surface=new Surface(input);
  SurfaceTexture glInput=(SurfaceTexture)field(camera,"texture");long previousGl=glInput.getTimestamp(),previousNative=input.getTimestamp();
  CameraDevice device=(CameraDevice)field(camera,"camera");Surface original=(Surface)field(camera,"surface");Handler handler=(Handler)field(camera,"cameraHandler");CountDownLatch configured=new CountDownLatch(1);Throwable[] failure={null};
  AtomicLong firstCapture=new AtomicLong(),latestCapture=new AtomicLong(),exposure=new AtomicLong(),sensitivity=new AtomicLong();AtomicInteger captures=new AtomicInteger();
  handler.post(()->{try{device.createCaptureSession(Arrays.asList(original,reference.surface),new CameraCaptureSession.StateCallback(){public void onConfigured(CameraCaptureSession session){try{CaptureRequest.Builder request=device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);request.addTarget(original);request.addTarget(reference.surface);request.set(CaptureRequest.CONTROL_AF_MODE,CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);session.setRepeatingRequest(request.build(),new CameraCaptureSession.CaptureCallback(){
   @Override public void onCaptureCompleted(CameraCaptureSession current,CaptureRequest request,TotalCaptureResult result){Long stamp=result.get(CaptureResult.SENSOR_TIMESTAMP);if(stamp!=null){firstCapture.compareAndSet(0,stamp);latestCapture.set(stamp);captures.incrementAndGet();}Long time=result.get(CaptureResult.SENSOR_EXPOSURE_TIME);Integer iso=result.get(CaptureResult.SENSOR_SENSITIVITY);if(time!=null)exposure.set(time);if(iso!=null)sensitivity.set(iso);}
  },handler);member(camera,"session").set(camera,session);}catch(Throwable e){failure[0]=e;}configured.countDown();}public void onConfigureFailed(CameraCaptureSession s){failure[0]=new AssertionError("Reference camera session failed");configured.countDown();}},handler);}catch(Throwable e){failure[0]=e;configured.countDown();}});
  check(configured.await(10,TimeUnit.SECONDS)&&failure[0]==null,"native TextureView reference session");
  // Both consumers must receive frames from this new dual-output session. A camera-open
  // callback, a pre-existing timestamp or a fixed sleep alone does not establish that.
  waitFor(()->captures.get()>=3&&firstCapture.get()>0&&glInput.getTimestamp()>=firstCapture.get()&&input.getTimestamp()>=firstCapture.get()&&glInput.getTimestamp()>previousGl&&input.getTimestamp()>previousNative);
  long advancingGl=glInput.getTimestamp(),advancingNative=input.getTimestamp();
  waitFor(()->glInput.getTimestamp()>advancingGl&&input.getTimestamp()>advancingNative);
  // Freeze only this instrumentation-created session at one shared capture. A slow
  // software GPU can take longer than a frame period to perform PixelCopy; sampling
  // moving previews before/after that operation cannot establish a matching frame.
  Bitmap gl=null;Bitmap[] nativeImage={null};boolean matched=false;String matchFailure="No final frame";
  for(int attempt=0;attempt<3&&!matched;attempt++){
   AtomicLong finalTimestamp=new AtomicLong();AtomicReference<Throwable> captureFailure=new AtomicReference<>();CountDownLatch finalCapture=new CountDownLatch(1);
   handler.post(()->{try{CameraCaptureSession current=(CameraCaptureSession)field(camera,"session");current.stopRepeating();CaptureRequest.Builder request=device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);request.addTarget(original);request.addTarget(reference.surface);request.set(CaptureRequest.CONTROL_AF_MODE,CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);current.capture(request.build(),new CameraCaptureSession.CaptureCallback(){
    @Override public void onCaptureCompleted(CameraCaptureSession session,CaptureRequest request,TotalCaptureResult result){Long stamp=result.get(CaptureResult.SENSOR_TIMESTAMP);if(stamp!=null)finalTimestamp.set(stamp);Long time=result.get(CaptureResult.SENSOR_EXPOSURE_TIME);Integer iso=result.get(CaptureResult.SENSOR_SENSITIVITY);if(time!=null)exposure.set(time);if(iso!=null)sensitivity.set(iso);finalCapture.countDown();}
    @Override public void onCaptureFailed(CameraCaptureSession session,CaptureRequest request,CaptureFailure failure){captureFailure.set(new AssertionError("Final capture failed: "+failure.getReason()));finalCapture.countDown();}
   },handler);}catch(Throwable error){captureFailure.set(error);finalCapture.countDown();}});
   if(!finalCapture.await(8,TimeUnit.SECONDS)||captureFailure.get()!=null||finalTimestamp.get()<=0){matchFailure="Final capture did not complete: "+captureFailure.get();continue;}
   long stamp=finalTimestamp.get();reference.sessionTimestamp=stamp;
   if(!awaitCondition(()->glInput.getTimestamp()==stamp&&input.getTimestamp()==stamp,6000)){reference.glTimestamp=glInput.getTimestamp();reference.nativeTimestamp=input.getTimestamp();matchFailure="Both consumers did not reach the same final capture";continue;}
   // getTimestamp changes inside onDrawFrame, before the buffer is swapped. A queued
   // GL barrier runs after that draw/swap, so PixelCopy sees the final rendered frame.
   CountDownLatch rendered=new CountDownLatch(1);camera.queueEvent(()->{android.opengl.GLES20.glFinish();rendered.countDown();});
   if(!rendered.await(5,TimeUnit.SECONDS)){matchFailure="Final GL render did not complete";continue;}
   gl=capture(camera);runOnMainSync(()->{nativeImage[0]=reference.view.getBitmap();reference.nativeTimestamp=input.getTimestamp();reference.glTimestamp=glInput.getTimestamp();});
   matched=nativeImage[0]!=null&&reference.glTimestamp==stamp&&reference.nativeTimestamp==stamp;
   if(!matched){matchFailure="Final capture changed during readback";gl.recycle();gl=null;if(nativeImage[0]!=null){nativeImage[0].recycle();nativeImage[0]=null;}}
  }
  check(matched,"native reference frames match final capture: "+matchFailure+" / "+reference.describe());
  // TextureView.getBitmap copies the texture layer before its view matrix. Apply that
  // matrix explicitly so the comparison reflects the displayed preview geometry.
  Bitmap displayed=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);Canvas referenceCanvas=new Canvas(displayed);referenceCanvas.drawColor(Color.BLACK);referenceCanvas.concat(reference.displayMatrix);referenceCanvas.drawBitmap(nativeImage[0],0,0,new Paint(3));nativeImage[0].recycle();nativeImage[0]=displayed;
  String[] names={"correct","horizontal flip","vertical flip","180 degrees","90 degrees","270 degrees","diagonal transpose","opposite diagonal transpose"};
  reference.error=normalizedGeometryError(gl,x,y,nativeImage[0],0);reference.alternativeError=Double.POSITIVE_INFINITY;
  for(int candidate=1;candidate<names.length;candidate++){double error=normalizedGeometryError(gl,x,y,nativeImage[0],candidate);if(error<reference.alternativeError){reference.alternativeError=error;reference.alternative=names[candidate];}}
  try(OutputStream out=new FileOutputStream(new File(activity.getExternalFilesDir(null),"reference-gl-"+rotation+".png"))){gl.compress(Bitmap.CompressFormat.PNG,100,out);}try(OutputStream out=new FileOutputStream(new File(activity.getExternalFilesDir(null),"reference-native-"+rotation+".png"))){nativeImage[0].compress(Bitmap.CompressFormat.PNG,100,out);}android.util.Log.i("ColorOrientation","rotation="+rotation+" sensor="+field(camera,"sensorOrientation")+" buffer="+size+" GL="+camera.getWidth()+"x"+camera.getHeight()+" ref="+width+"x"+height+" at="+x+","+y+" matrix="+Arrays.toString((float[])field(camera,"transform"))+" "+reference.describe()+" exposure="+exposure.get()+" ISO="+sensitivity.get());
  gl.recycle();nativeImage[0].recycle();return reference;
 }
 private boolean awaitCondition(Callable<Boolean> condition,long timeoutMillis)throws Exception{
  long until=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
  while(System.nanoTime()<until){if(condition.call())return true;Thread.sleep(25);}return condition.call();
 }
 private static double luminance(int color){return .2126*((color>>16)&255)+.7152*((color>>8)&255)+.0722*(color&255);}
 private static double normalizedGeometryError(Bitmap gl,int left,int top,Bitmap nativeImage,int transform){
  int width=nativeImage.getWidth(),height=nativeImage.getHeight(),count=0;double sumA=0,sumB=0,squareA=0,squareB=0,product=0;
  for(int yy=Math.max(2,Math.round(height*.08f));yy<height*.92f;yy+=7)for(int xx=Math.max(2,Math.round(width*.08f));xx<width*.92f;xx+=7){
   double u=xx/(double)(width-1),v=yy/(double)(height-1),a=u,b=v;
   switch(transform){case 1:a=1-u;break;case 2:b=1-v;break;case 3:a=1-u;b=1-v;break;case 4:a=v;b=1-u;break;case 5:a=1-v;b=u;break;case 6:a=v;b=u;break;case 7:a=1-v;b=1-u;break;}
   double first=luminance(gl.getPixel(left+xx,top+yy)),second=luminance(nativeImage.getPixel((int)Math.round(a*(width-1)),(int)Math.round(b*(height-1))));
   count++;sumA+=first;sumB+=second;squareA+=first*first;squareB+=second*second;product+=first*second;
  }
  double varianceA=squareA-sumA*sumA/count,varianceB=squareB-sumB*sumB/count;
  // A nearly uniform or symmetric image cannot prove orientation. Never let a lack of
  // visual evidence pass merely because its raw pixels happen to match after a flip.
  if(count<100||varianceA/count<64||varianceB/count<64)return Double.POSITIVE_INFINITY;
  double correlation=(product-sumA*sumB/count)/Math.sqrt(varianceA*varianceB);
  return 1-Math.max(-1,Math.min(1,correlation));
 }
}
