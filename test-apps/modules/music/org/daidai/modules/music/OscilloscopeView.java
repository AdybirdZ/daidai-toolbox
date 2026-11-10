package org.daidai.modules.music;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

/** A true left/right PCM oscilloscope. Samples are decoded from the local track. */
public final class OscilloscopeView extends View {
    public static final int WAVEFORM=0,XY=1;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private float[] left=new float[0],right=new float[0];
    private int mode;
    public OscilloscopeView(Context c){super(c);paint.setStyle(Paint.Style.STROKE);setFocusable(false);}
    public void setMode(int value){mode=value==XY?XY:WAVEFORM;invalidate();}
    public void samples(float[][] data){left=data==null?new float[0]:data[0];right=data==null?new float[0]:data[1];invalidate();}
    @Override protected void onDraw(Canvas c){super.onDraw(c);int w=getWidth(),h=getHeight();if(w<2||h<2)return;paint.setStrokeWidth(getResources().getDisplayMetrics().density);paint.setColor(0xffd4deeb);for(int i=1;i<4;i++){c.drawLine(w*i/4f,0,w*i/4f,h,paint);c.drawLine(0,h*i/4f,w,h*i/4f,paint);}paint.setStrokeWidth(2*getResources().getDisplayMetrics().density);if(mode==XY){paint.setColor(0xff215eb0);int n=Math.min(left.length,right.length);for(int i=1;i<n;i++)c.drawLine(w/2f+left[i-1]*w*.45f,h/2f-right[i-1]*h*.45f,w/2f+left[i]*w*.45f,h/2f-right[i]*h*.45f,paint);}else{wave(c,left,w,h,0xff215eb0);wave(c,right,w,h,0xffd47517);}}
    private void wave(Canvas c,float[] data,int w,int h,int color){if(data.length<2)return;paint.setColor(color);for(int i=1;i<data.length;i++)c.drawLine((i-1)*w/(float)(data.length-1),h/2f-data[i-1]*h*.45f,i*w/(float)(data.length-1),h/2f-data[i]*h*.45f,paint);}
}
