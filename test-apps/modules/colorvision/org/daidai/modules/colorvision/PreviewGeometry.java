package org.daidai.modules.colorvision;

/** SurfaceTexture already includes the camera sensor rotation and front-camera transform. */
public final class PreviewGeometry {
 public static boolean swapsAxes(float[] texture){return Math.abs(texture[4])>Math.abs(texture[0]);}
 public static float aspect(int width,int height,float[] texture,int displayDegrees){
  boolean swap=swapsAxes(texture)^((displayDegrees%180)!=0);return swap?height/(float)width:width/(float)height;
 }
 public static float[] uv(int displayDegrees){
  float[] result={0,0,1,0,0,1,1,1};double angle=Math.toRadians(-displayDegrees);
  for(int i=0;i<4;i++){float u=result[i*2]-.5f,v=result[i*2+1]-.5f;result[i*2]=.5f+(float)(u*Math.cos(angle)-v*Math.sin(angle));result[i*2+1]=.5f+(float)(u*Math.sin(angle)+v*Math.cos(angle));}
  return result;
 }
 private PreviewGeometry(){}
}
