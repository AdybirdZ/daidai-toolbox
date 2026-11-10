package org.daidai.prototype.qa;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.net.Uri;
import android.view.View;
import android.widget.LinearLayout;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.daidai.modules.music.*;

/** Behavior checks for remembered modes, square X-Y coordinates and source-timed history. */
public final class MusicAnalysis060DeviceChecks {
    private static int checks;
    private static void check(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
    private static Object field(Object owner,String key){try{Field f=owner.getClass().getDeclaredField(key);f.setAccessible(true);return f.get(owner);}catch(Exception e){throw new RuntimeException(e);}}
    private static Object call(Object owner,String key){try{Method m=owner.getClass().getDeclaredMethod(key);m.setAccessible(true);return m.invoke(owner);}catch(Exception e){throw new RuntimeException(e);}}
    private static void restore(SharedPreferences prefs,Map<String,?> values){SharedPreferences.Editor e=prefs.edit().clear();for(Map.Entry<String,?> row:values.entrySet()){Object v=row.getValue();if(v instanceof String)e.putString(row.getKey(),(String)v);else if(v instanceof Boolean)e.putBoolean(row.getKey(),(Boolean)v);else if(v instanceof Integer)e.putInt(row.getKey(),(Integer)v);else if(v instanceof Long)e.putLong(row.getKey(),(Long)v);else if(v instanceof Float)e.putFloat(row.getKey(),(Float)v);else if(v instanceof Set)e.putStringSet(row.getKey(),new HashSet<>((Set<String>)v));}e.commit();}
    public static int run(Instrumentation runner,Activity activity,PlaybackService service){
        checks=0;final Throwable[] error={null};runner.runOnMainSync(()->{SharedPreferences prefs=activity.getSharedPreferences("music_module",0);Map<String,?> saved=prefs.getAll();MusicAnalysisPanel first=null,second=null;try{
            prefs.edit().remove("analysis_display_mode").commit();MusicAnalysisPanel.PlaybackCursor cursor=new MusicAnalysisPanel.PlaybackCursor(){public Uri source(){return null;}public String title(){return "";}public long positionMillis(){return 0;}public long durationMillis(){return 0;}public boolean playing(){return false;}};
            first=new MusicAnalysisPanel(activity,cursor);check(first.displayMode()==MusicAnalysisPanel.WAVEFORM,"fresh analysis starts in time-domain mode");SpectrumView spectrum=(SpectrumView)field(first,"spectrum");check(spectrum.peakCount()==5,"fresh spectrum defaults to five peak labels");check(spectrum.historyDurationMillis()==1000,"fresh history spans one second");
            first.setDisplayMode(MusicAnalysisPanel.XY);first.close();first=null;second=new MusicAnalysisPanel(activity,cursor);check(second.displayMode()==MusicAnalysisPanel.XY,"new panel restores selected analysis mode");
            OscilloscopeView scope=(OscilloscopeView)field(second,"scope");scope.measure(View.MeasureSpec.makeMeasureSpec(640,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));scope.layout(0,0,scope.getMeasuredWidth(),scope.getMeasuredHeight());RectF plot=(RectF)call(scope,"plotBounds");check(Math.abs(plot.width()-plot.height())<.01f,"X-Y plot has equal-width and equal-height axes");
            second.setDisplayMode(MusicAnalysisPanel.SPECTRUM);spectrum=(SpectrumView)field(second,"spectrum");check(((LinearLayout.LayoutParams)spectrum.getLayoutParams()).height>=Math.round(500*activity.getResources().getDisplayMetrics().density),"spectrum reserves enlarged history height");second.setHistoryDurationMillis(500);check(spectrum.historyDurationMillis()==500,"half-second history setting reaches view");second.setHistoryDurationMillis(5000);check(spectrum.historyDurationMillis()==5000,"five-second history setting reaches view");second.setHistoryDurationMillis(1000);
            float[][] source=new float[2][4096];for(int i=0;i<4096;i++)source[0][i]=source[1][i]=(float)Math.sin(2*Math.PI*440*i/48000);MusicSpectrum.Frame frame=MusicSpectrum.analyze(source,48000,4096);
            spectrum.samples(frame,1000);spectrum.samples(frame,1050);MusicSpectrumHistory timeline=(MusicSpectrumHistory)field(spectrum,"history");check(timeline.positionMillis()==1050&&timeline.frameAtAge(50)==frame,"history positions use audio timestamps");check(timeline.frameAtAge(500)==null,"unobserved source time is blank instead of inventing older history");int size=timeline.size();spectrum.samples(frame,1050);check(timeline.size()==size&&timeline.positionMillis()==1050,"repeat draw at paused timestamp cannot advance history");
            spectrum.measure(View.MeasureSpec.makeMeasureSpec(640,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(800,View.MeasureSpec.EXACTLY));spectrum.layout(0,0,640,800);Bitmap bitmap=Bitmap.createBitmap(640,800,Bitmap.Config.ARGB_8888);spectrum.draw(new Canvas(bitmap));bitmap.recycle();check(true,"spectrum renders source-time scale and history");
        }catch(Throwable failure){error[0]=failure;}finally{if(first!=null)first.close();if(second!=null)second.close();restore(prefs,saved);}});if(error[0]!=null)throw new AssertionError("0.6 analysis behavior failed",error[0]);return checks;
    }
}
