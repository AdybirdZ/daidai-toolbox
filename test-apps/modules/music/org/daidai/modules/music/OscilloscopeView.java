package org.daidai.modules.music;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;
import org.daidai.modules.shared.Texts;
import java.util.Locale;

/** Left/right PCM trace with normalized digital-amplitude axes, never electrical volts. */
public final class OscilloscopeView extends View {
    public static final int WAVEFORM=0,XY=1;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path=new Path();
    private float[] left=new float[0],right=new float[0];
    private int mode,rate=48000;private float range=1f;
    public OscilloscopeView(Context c){super(c);setFocusable(false);}
    public void setMode(int value){mode=value==XY?XY:WAVEFORM;invalidate();}
    /** Visible range is -range to +range; every vertical division is range/2. */
    public void setAmplitudeRange(float value){range=Math.max(.02f,Math.min(2,value));invalidate();}
    public float amplitudeRange(){return range;}
    public void setSampleRate(int value){rate=Math.max(1,value);}
    public void samples(float[][] data){left=data==null?new float[0]:data[0];right=data==null?new float[0]:data[1];invalidate();}
    private float dp(float n){return n*getResources().getDisplayMetrics().density;}
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);float x0=dp(45),y0=dp(23),x1=getWidth()-dp(12),y1=getHeight()-dp(37);if(x1<=x0||y1<=y0)return;
        paint.setTextSize(dp(10));paint.setStrokeWidth(dp(1));paint.setStyle(Paint.Style.FILL);paint.setColor(0xff536170);
        c.drawText(Texts.get(getContext(),mode==XY?"music050_scope_xy_axes":"music050_scope_axes"),x0,dp(13),paint);
        float shownRange=mode==XY?1:range;
        for(int i=0;i<=4;i++){float y=y0+(y1-y0)*i/4;paint.setColor(0xffd3dbe5);c.drawLine(x0,y,x1,y,paint);c.drawLine(x0+(x1-x0)*i/4,y0,x0+(x1-x0)*i/4,y1,paint);paint.setColor(0xff536170);String label=String.format(Locale.getDefault(),"%.2f",shownRange*(1-i/2f));c.drawText(label,dp(2),y+dp(4),paint);}
        for(int i=0;i<=4;i++){float x=x0+(x1-x0)*i/4;String label=mode==XY?String.format(Locale.getDefault(),"%.1f",-1+i*.5):String.format(Locale.getDefault(),"%.1f",1000.*Math.max(0,left.length-1)*i/(4*rate));float tw=paint.measureText(label);c.drawText(label,Math.max(x0,Math.min(x1-tw,x-tw/2)),y1+dp(15),paint);}
        c.drawText(Texts.get(getContext(),mode==XY?"music050_scope_xy_bottom":"music050_scope_time_bottom"),x0,y1+dp(31),paint);
        c.save();c.clipRect(x0,y0,x1,y1);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(1.3f));
        if(mode==XY){paint.setColor(0xff215eb0);path.reset();int n=Math.min(left.length,right.length);for(int i=0;i<n;i++){float x=(x0+x1)/2+left[i]*(x1-x0)/2,y=(y0+y1)/2-right[i]*(y1-y0)/2;if(i==0)path.moveTo(x,y);else path.lineTo(x,y);}c.drawPath(path,paint);}else{wave(c,left,x0,y0,x1,y1,0xff215eb0);wave(c,right,x0,y0,x1,y1,0xffc66a10);}c.restore();paint.setStyle(Paint.Style.FILL);
    }
    private void wave(Canvas c,float[] data,float x0,float y0,float x1,float y1,int color){if(data.length<2)return;paint.setColor(color);path.reset();for(int i=0;i<data.length;i++){float x=x0+i*(x1-x0)/(data.length-1),y=(y0+y1)/2-data[i]*(y1-y0)/(2*range);if(i==0)path.moveTo(x,y);else path.lineTo(x,y);}c.drawPath(path,paint);}
}
