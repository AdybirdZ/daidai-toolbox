package org.daidai.modules.colorvision;
import android.app.Activity;
import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.*;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.opengl.*;
import android.os.*;
import android.util.Size;
import android.view.*;
import java.nio.*;
import java.util.*;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/** Camera2 to external OES texture. No frame capture, storage or network. */
public final class CameraPreview extends GLSurfaceView implements GLSurfaceView.Renderer {
 public interface Status {void report(String message);}
 private final Activity activity;private final Status status;private final Handler main=new Handler(Looper.getMainLooper());
 private HandlerThread thread;private Handler cameraHandler;private CameraDevice camera;private CameraCaptureSession session;private Surface surface;
 private SurfaceTexture texture;private int textureId,program,viewWidth,viewHeight;private volatile int sensorOrientation=90;private volatile Size size=new Size(640,480);
 private boolean front;private volatile boolean active;private volatile int generation;private int openingGeneration=-1;private volatile int selectedMask=15;private final int gap,labelHeight;
 private final float[] transform=new float[16];private FloatBuffer vertices;
 private static final String VERTEX="attribute vec2 aPosition;attribute vec2 aUV;uniform mat4 uTransform;varying vec2 vUV;void main(){gl_Position=vec4(aPosition,0.,1.);vUV=(uTransform*vec4(aUV,0.,1.)).xy;}";
 private static final String FRAGMENT="#extension GL_OES_EGL_image_external : require\nprecision highp float;uniform samplerExternalOES uCamera;uniform mat3 uMatrix;varying vec2 vUV;vec3 lin(vec3 c){return mix(c/12.92,pow((c+0.055)/1.055,vec3(2.4)),step(vec3(0.04045),c));}vec3 srgb(vec3 c){c=clamp(c,0.,1.);return mix(c*12.92,1.055*pow(c,vec3(1./2.4))-0.055,step(vec3(0.0031308),c));}void main(){vec4 c=texture2D(uCamera,vUV);gl_FragColor=vec4(srgb(uMatrix*lin(c.rgb)),1.);}";
 public CameraPreview(Activity a,Status callback){super(a);activity=a;status=callback;gap=org.daidai.modules.shared.Ui.dp(a,6);labelHeight=org.daidai.modules.shared.Ui.dp(a,28);setEGLContextClientVersion(2);setRenderer(this);setRenderMode(RENDERMODE_WHEN_DIRTY);}
 public void setMask(int value){if((value&15)==0)throw new IllegalArgumentException("Select a view");selectedMask=value&15;requestRender();}
 public void start(boolean useFront){stop();front=useFront;active=true;generation++;thread=new HandlerThread("color-camera");thread.start();cameraHandler=new Handler(thread.getLooper());super.onResume();requestRender();}
 public void stop(){active=false;generation++;if(session!=null){session.close();session=null;}if(camera!=null){camera.close();camera=null;}if(surface!=null){surface.release();surface=null;}if(thread!=null){thread.quitSafely();thread=null;cameraHandler=null;}super.onPause();}
 private void message(Exception e){main.post(()->status.report(e.getMessage()==null?e.toString():e.getMessage()));}
 private synchronized void open(){if(!active||cameraHandler==null||openingGeneration==generation)return;final int token=generation;openingGeneration=token;Handler handler=cameraHandler;
  handler.post(()->{try{CameraManager manager=(CameraManager)activity.getSystemService(Context.CAMERA_SERVICE);String chosen=null;CameraCharacteristics chars=null;
   for(String id:manager.getCameraIdList()){CameraCharacteristics c=manager.getCameraCharacteristics(id);Integer facing=c.get(CameraCharacteristics.LENS_FACING);if(facing!=null&&facing==(front?CameraCharacteristics.LENS_FACING_FRONT:CameraCharacteristics.LENS_FACING_BACK)){chosen=id;chars=c;break;}}
   if(chosen==null)throw new Exception(org.daidai.modules.shared.Texts.get(activity,"cv_no_camera"));
   Integer orientation=chars.get(CameraCharacteristics.SENSOR_ORIENTATION);sensorOrientation=orientation==null?90:orientation;
   Size[] sizes=chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(SurfaceTexture.class);Size best=null;
   for(Size s:sizes)if(s.getWidth()<=1280&&s.getHeight()<=960&&(best==null||Math.abs(s.getWidth()-960)<Math.abs(best.getWidth()-960)))best=s;
   size=best==null?sizes[sizes.length-1]:best;
   if(!active||token!=generation)return;texture.setDefaultBufferSize(size.getWidth(),size.getHeight());Surface target=new Surface(texture);surface=target;
   manager.openCamera(chosen,new CameraDevice.StateCallback(){public void onOpened(CameraDevice c){if(!active||token!=generation){c.close();return;}camera=c;try{c.createCaptureSession(Arrays.asList(target),new CameraCaptureSession.StateCallback(){public void onConfigured(CameraCaptureSession s){if(!active||token!=generation){s.close();return;}session=s;try{CaptureRequest.Builder r=c.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);r.addTarget(target);r.set(CaptureRequest.CONTROL_AF_MODE,CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);s.setRepeatingRequest(r.build(),null,handler);}catch(Exception e){message(e);}}public void onConfigureFailed(CameraCaptureSession s){message(new Exception(org.daidai.modules.shared.Texts.get(activity,"cv_camera_error")));}},handler);}catch(Exception e){message(e);}}public void onDisconnected(CameraDevice c){c.close();if(token==generation)message(new Exception(org.daidai.modules.shared.Texts.get(activity,"cv_camera_error")));}public void onError(CameraDevice c,int e){c.close();if(token==generation)message(new Exception(org.daidai.modules.shared.Texts.get(activity,"cv_camera_error")+" ("+e+")"));}},handler);
  }catch(Exception e){if(token==generation)message(e);}});
 }
 private int shader(int type,String text){int s=GLES20.glCreateShader(type);GLES20.glShaderSource(s,text);GLES20.glCompileShader(s);int[] ok={0};GLES20.glGetShaderiv(s,GLES20.GL_COMPILE_STATUS,ok,0);if(ok[0]==0)throw new IllegalStateException(GLES20.glGetShaderInfoLog(s));return s;}
 @Override public void onSurfaceCreated(GL10 gl,EGLConfig config){try{int vs=shader(GLES20.GL_VERTEX_SHADER,VERTEX),fs=shader(GLES20.GL_FRAGMENT_SHADER,FRAGMENT);program=GLES20.glCreateProgram();GLES20.glAttachShader(program,vs);GLES20.glAttachShader(program,fs);GLES20.glLinkProgram(program);GLES20.glDeleteShader(vs);GLES20.glDeleteShader(fs);int[] ok={0};GLES20.glGetProgramiv(program,GLES20.GL_LINK_STATUS,ok,0);if(ok[0]==0)throw new IllegalStateException(GLES20.glGetProgramInfoLog(program));int[] ids={0};GLES20.glGenTextures(1,ids,0);textureId=ids[0];GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,textureId);GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE);if(texture!=null)texture.release();texture=new SurfaceTexture(textureId);texture.setOnFrameAvailableListener(t->requestRender());open();}catch(Exception e){message(e);}}
 @Override public void onSurfaceChanged(GL10 gl,int w,int h){viewWidth=w;viewHeight=h;}
 @Override public void onDrawFrame(GL10 gl){GLES20.glViewport(0,0,viewWidth,viewHeight);GLES20.glClearColor(.04f,.04f,.04f,1);GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);if(texture==null||program==0)return;
  // Open only after this GL context has a valid current texture, including resume.
  open();
  try{texture.updateTexImage();texture.getTransformMatrix(transform);}catch(Exception e){return;}
  int rotation=activity.getWindowManager().getDefaultDisplay().getRotation()*90;
  // getTransformMatrix includes sensor rotation/mirroring. Rotate only for the display.
  float aspect=PreviewGeometry.aspect(size.getWidth(),size.getHeight(),transform,rotation);float[] uv=PreviewGeometry.uv(rotation);
  GLES20.glUseProgram(program);GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program,"uTransform"),1,false,transform,0);GLES20.glActiveTexture(GLES20.GL_TEXTURE0);GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,textureId);GLES20.glUniform1i(GLES20.glGetUniformLocation(program,"uCamera"),0);
  int pos=GLES20.glGetAttribLocation(program,"aPosition"),tex=GLES20.glGetAttribLocation(program,"aUV");GLES20.glEnableVertexAttribArray(pos);GLES20.glEnableVertexAttribArray(tex);
  if(vertices==null)vertices=ByteBuffer.allocateDirect(64).order(ByteOrder.nativeOrder()).asFloatBuffer();
  for(ComparisonLayout.Panel panel:ComparisonLayout.panels(selectedMask,viewWidth,viewHeight,gap)){
   int height=Math.max(1,panel.height-labelHeight);GLES20.glViewport(panel.x,viewHeight-panel.y-panel.height,panel.width,height);
   float screen=panel.width/(float)height,x=1,y=1;if(aspect>screen)y=screen/aspect;else x=aspect/screen;
   float[] data={-x,-y,uv[0],uv[1], x,-y,uv[2],uv[3], -x,y,uv[4],uv[5], x,y,uv[6],uv[7]};vertices.position(0);vertices.put(data).position(0);GLES20.glVertexAttribPointer(pos,2,GLES20.GL_FLOAT,false,16,vertices);vertices.position(2);GLES20.glVertexAttribPointer(tex,2,GLES20.GL_FLOAT,false,16,vertices);
   float[] m=ColorVision.MATRIX[panel.mode],column={m[0],m[3],m[6],m[1],m[4],m[7],m[2],m[5],m[8]};GLES20.glUniformMatrix3fv(GLES20.glGetUniformLocation(program,"uMatrix"),1,false,column,0);GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);
  }
 }
}
