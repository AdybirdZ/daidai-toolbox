package org.daidai.modules.colorvision;
/** Machado, Oliveira & Fernandes (2009), severity 1. Linear-sRGB matrices. */
public final class ColorVision {
 public static final float[][] MATRIX={
 {1,0,0,0,1,0,0,0,1},
 {.152286f,1.052583f,-.204868f,.114503f,.786281f,.099216f,-.003882f,-.048116f,1.051998f},
 {.367322f,.860646f,-.227968f,.280085f,.672501f,.047413f,-.011820f,.042940f,.968881f},
 {1.255528f,-.076749f,-.178779f,-.078411f,.930809f,.147602f,.004733f,.691367f,.303900f}};
 private static final float[] LINEAR=new float[256];
 static {for(int i=0;i<256;i++){float c=i/255f;LINEAR[i]=c<=.04045f?c/12.92f:(float)Math.pow((c+.055f)/1.055f,2.4);}}
 public static int encode(float c){c=Math.max(0,Math.min(1,c));return Math.round(255*(c<=.0031308f?12.92f*c:1.055f*(float)Math.pow(c,1/2.4)-.055f));}
 public static int pixel(int argb,int mode){if(mode==0)return argb;if(mode<0||mode>3)throw new IllegalArgumentException("mode");float[] m=MATRIX[mode];float r=LINEAR[(argb>>16)&255],g=LINEAR[(argb>>8)&255],b=LINEAR[argb&255];return (argb&0xff000000)|(encode(m[0]*r+m[1]*g+m[2]*b)<<16)|(encode(m[3]*r+m[4]*g+m[5]*b)<<8)|encode(m[6]*r+m[7]*g+m[8]*b);}
 public static void apply(int[] pixels,int mode){for(int i=0;i<pixels.length;i++)pixels[i]=pixel(pixels[i],mode);}
 private ColorVision(){}
}
