package org.daidai.modules.music;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.MotionEvent;
import android.view.View;
import org.daidai.modules.shared.Texts;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Original spectrum and time-frequency history; all levels refer to decoded digital samples. */
public final class SpectrumView extends View {
    private static final int HISTORY=80,COLUMNS=192;
    private static final int[] COLORS=palette();
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path trace=new Path();
    private final ArrayDeque<MusicSpectrum.Frame> history=new ArrayDeque<>();
    private final Bitmap image=Bitmap.createBitmap(COLUMNS,HISTORY,Bitmap.Config.ARGB_8888);
    private final int[] pixels=new int[COLUMNS*HISTORY];
    private MusicSpectrum.Frame frame;
    private boolean logarithmic=true;private int peakCount=3;
    private Runnable rangeChanged;
    private double low=20,high=24000;private float lastX,spanX;private boolean pinching;
    public SpectrumView(Context c){super(c);setFocusable(false);}
    private float dp(float n){return n*getResources().getDisplayMetrics().density;}
    public void setLogarithmic(boolean value){logarithmic=value;resetFrequencyRange();}
    public boolean isLogarithmic(){return logarithmic;}
    public void setPeakCount(int value){peakCount=Math.max(0,Math.min(10,value));invalidate();}
    public int peakCount(){return peakCount;}
    public void setOnFrequencyRangeChanged(Runnable listener){rangeChanged=listener;}
    /** Text fallback keeps every requested frequency available when on-chart labels overlap. */
    public String peakSummary(){if(frame==null||peakCount==0)return "";StringBuilder result=new StringBuilder();for(MusicSpectrum.Peak peak:MusicSpectrum.peaks(frame,peakCount,low,high)){if(result.length()>0)result.append(" · ");result.append(String.format(Locale.getDefault(),"%.0f Hz (%.1f dBFS)",peak.frequency,peak.db));}return result.toString();}
    public void clear(){frame=null;history.clear();rebuildHistory();invalidate();}
    public void samples(MusicSpectrum.Frame value,boolean append){
        if(value==null)return;boolean changed=frame==null||frame.sampleRate!=value.sampleRate;frame=value;
        if(changed){history.clear();low=logarithmic?20:0;high=value.sampleRate/2.;}
        if(append){history.addFirst(value);while(history.size()>HISTORY)history.removeLast();rebuildHistory();}invalidate();
    }
    public void resetFrequencyRange(){low=logarithmic?20:0;high=frame==null?24000:frame.sampleRate/2.;rebuildHistory();invalidate();}
    private double scale(double hz){return logarithmic?Math.log(Math.max(1,hz)):hz;}
    private double unscale(double v){return logarithmic?Math.exp(v):v;}
    private double frequency(double fraction){return unscale(scale(low)+fraction*(scale(high)-scale(low)));}
    private float x(double hz,float left,float right){return left+(float)((scale(hz)-scale(low))/(scale(high)-scale(low)))*(right-left);}
    private float level(MusicSpectrum.Frame value,double hz){double index=hz*value.fftSize/value.sampleRate;int at=(int)Math.max(0,Math.min(value.db.length-1,index));int next=Math.min(at+1,value.db.length-1);return (float)(value.db[at]+(value.db[next]-value.db[at])*(index-at));}
    private void rebuildHistory(){
        java.util.Arrays.fill(pixels,0xff091724);int row=0;double[] frequencies=new double[COLUMNS];for(int i=0;i<COLUMNS;i++)frequencies[i]=frequency(i/(double)(COLUMNS-1));
        for(MusicSpectrum.Frame value:history){if(row>=HISTORY)break;for(int col=0;col<COLUMNS;col++)pixels[row*COLUMNS+col]=COLORS[Math.max(0,Math.min(96,Math.round(level(value,frequencies[col])+96)))];row++;}
        image.setPixels(pixels,0,COLUMNS,0,0,COLUMNS,HISTORY);
    }
    private static int[] palette(){int[] colors=new int[97];for(int i=0;i<colors.length;i++){float t=i/96f;colors[i]=Color.HSVToColor(new float[]{230-230*t,.88f,.12f+.88f*t});}return colors;}
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);float left=dp(44),right=getWidth()-dp(12),top=dp(24),bottom=getHeight()-dp(29),height=bottom-top;if(right<=left||height<=0)return;
        float curveBottom=top+height*.57f,historyTop=curveBottom+dp(27);paint.setTextSize(dp(10));paint.setStyle(Paint.Style.FILL);paint.setStrokeWidth(dp(1));paint.setColor(0xff536170);
        c.drawText(Texts.get(getContext(),"music050_spectrum_axes"),left,dp(13),paint);
        for(int i=0;i<=4;i++){float y=top+(curveBottom-top)*i/4;paint.setColor(0xffd3dbe5);c.drawLine(left,y,right,y,paint);paint.setColor(0xff536170);c.drawText(Integer.toString(-24*i),dp(4),y+dp(3),paint);}
        if(frame!=null){
            c.save();c.clipRect(left,top,right,curveBottom);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(1.4f));paint.setColor(0xff215eb0);trace.reset();
            // At least one maximum per screen column keeps narrow peaks visible after zooming out.
            int columns=Math.max(2,(int)(right-left));for(int col=0;col<columns;col++){double f0=frequency(col/(double)(columns-1)),f1=frequency((col+1.)/(columns-1));int a=(int)(f0*frame.fftSize/frame.sampleRate),b=(int)Math.ceil(f1*frame.fftSize/frame.sampleRate);float db=level(frame,f0);for(int bin=Math.max(0,a);bin<=Math.min(frame.db.length-1,b);bin++)db=Math.max(db,frame.db[bin]);float xx=left+col*(right-left)/(columns-1),y=top+(curveBottom-top)*Math.max(0,Math.min(1,-db/96));if(col==0)trace.moveTo(xx,y);else trace.lineTo(xx,y);}c.drawPath(trace,paint);c.restore();paint.setStyle(Paint.Style.FILL);
            List<float[]> occupied=new ArrayList<>();
            for(MusicSpectrum.Peak peak:MusicSpectrum.peaks(frame,peakCount,low,high)){
                float xx=x(peak.frequency,left,right),yy=top+(curveBottom-top)*Math.max(0,Math.min(1,-peak.db/96));paint.setColor(0xffb34b00);c.drawCircle(xx,yy,dp(2.3f),paint);
                String label=String.format(Locale.getDefault(),"%.0f Hz",peak.frequency);float width=paint.measureText(label),labelX=Math.max(left,Math.min(right-width,xx-width/2)),labelY=top+dp(11);boolean placed=false;
                for(int row=0;row<5;row++){labelY=top+dp(11+row*13);boolean clash=false;for(float[] box:occupied)if(labelY>box[1]-dp(12)&&labelY<box[1]+dp(12)&&labelX<box[0]+box[2]+dp(4)&&labelX+width>box[0]-dp(4)){clash=true;break;}if(!clash){placed=true;break;}}
                if(placed){paint.setColor(0xe6ffffff);c.drawRect(labelX-dp(2),labelY-dp(10),labelX+width+dp(2),labelY+dp(2),paint);paint.setColor(0xff8d3e00);c.drawText(label,labelX,labelY,paint);occupied.add(new float[]{labelX,labelY,width});}
            }
        }
        paint.setColor(0xff536170);for(int i=0;i<=4;i++){float xx=left+(right-left)*i/4;String label=frequencyLabel(frequency(i/4.));float w=paint.measureText(label);c.drawText(label,Math.max(left,Math.min(right-w,xx-w/2)),curveBottom+dp(15),paint);}
        if(historyTop<bottom){c.drawBitmap(image,null,new android.graphics.RectF(left,historyTop,right,bottom),paint);paint.setColor(0xff536170);c.drawText(Texts.get(getContext(),"music050_spectrum_newest"),left,bottom+dp(15),paint);}
    }
    private static String frequencyLabel(double hz){return hz>=1000?String.format(Locale.getDefault(),"%.1f kHz",hz/1000):String.format(Locale.getDefault(),"%.0f Hz",hz);}
    @Override public boolean onTouchEvent(MotionEvent e){
        float width=getWidth()-dp(56);if(width<=1)return false;
        if(e.getActionMasked()==MotionEvent.ACTION_DOWN){lastX=e.getX();return true;}
        if(e.getActionMasked()==MotionEvent.ACTION_POINTER_DOWN&&e.getPointerCount()>=2){spanX=Math.abs(e.getX(0)-e.getX(1));pinching=true;if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(true);return true;}
        if(e.getActionMasked()==MotionEvent.ACTION_MOVE&&e.getPointerCount()>=2){float span=Math.abs(e.getX(0)-e.getX(1));if(spanX>dp(8)&&span>dp(8)){double focus=Math.max(0,Math.min(1,((e.getX(0)+e.getX(1))/2-dp(44))/width));zoom(span/spanX,focus);spanX=span;}return true;}
        if(e.getActionMasked()==MotionEvent.ACTION_MOVE&&!pinching){float dx=e.getX()-lastX;lastX=e.getX();if(Math.abs(dx)>dp(2)){if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(true);shift(-dx/width);}return true;}
        if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL){pinching=false;if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(false);performClick();return true;}
        if(e.getActionMasked()==MotionEvent.ACTION_POINTER_UP){pinching=true;return true;}return true;
    }
    @Override public boolean performClick(){super.performClick();return true;}
    private void zoom(double factor,double focus){double a=scale(low),b=scale(high),size=b-a,center=a+focus*size;double minimum=logarithmic?Math.log(1.1):50;size=Math.max(minimum,size/Math.max(.5,Math.min(2,factor)));setRange(center-focus*size,center+(1-focus)*size);}
    private void shift(double fraction){double a=scale(low),b=scale(high),offset=(b-a)*fraction;setRange(a+offset,b+offset);}
    private void setRange(double a,double b){double minimum=scale(logarithmic?20:0),maximum=scale(frame==null?24000:frame.sampleRate/2.);if(b-a>=maximum-minimum){a=minimum;b=maximum;}else{if(a<minimum){b+=minimum-a;a=minimum;}if(b>maximum){a-=b-maximum;b=maximum;}}low=unscale(a);high=unscale(b);rebuildHistory();invalidate();if(rangeChanged!=null)rangeChanged.run();}
}
