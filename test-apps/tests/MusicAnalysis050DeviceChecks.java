package org.daidai.prototype.qa;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.net.Uri;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.CountDownLatch;
import org.daidai.modules.music.*;

/** Exercises the embedded panel against real local PCM and the actual playback service. */
public final class MusicAnalysis050DeviceChecks {
    private static Instrumentation runner;private static Activity activity;private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static Object field(Object owner,String name){try{Class<?> type=owner.getClass();while(type!=null){try{Field f=type.getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(NoSuchFieldException ignored){type=type.getSuperclass();}}throw new NoSuchFieldException(name);}catch(Exception e){throw new RuntimeException(e);}}
    private static void set(Object owner,String name,Object value){try{Class<?> type=owner.getClass();while(type!=null){try{Field f=type.getDeclaredField(name);f.setAccessible(true);f.set(owner,value);return;}catch(NoSuchFieldException ignored){type=type.getSuperclass();}}throw new NoSuchFieldException(name);}catch(Exception e){throw new RuntimeException(e);}}
    private static void call(Object owner,String name){try{Class<?> type=owner.getClass();while(type!=null){try{Method m=type.getDeclaredMethod(name);m.setAccessible(true);m.invoke(owner);return;}catch(NoSuchMethodException ignored){type=type.getSuperclass();}}throw new NoSuchMethodException(name);}catch(Exception e){throw new RuntimeException(e);}}
    private static void main(Runnable task){final Throwable[] error={null};runner.runOnMainSync(()->{try{task.run();}catch(Throwable t){error[0]=t;}});runner.waitForIdleSync();if(error[0]!=null)throw new AssertionError("Analysis UI action failed",error[0]);}
    private static void waitFor(Callable<Boolean> predicate,String name)throws Exception{long end=SystemClock.uptimeMillis()+15000;while(SystemClock.uptimeMillis()<end){boolean[] ready={false};main(()->{try{ready[0]=predicate.call();}catch(Exception e){throw new RuntimeException(e);}});if(ready[0])return;Thread.sleep(35);}throw new AssertionError("Timed out: "+name);}
    private static Set<String> cacheFiles(){Set<String> files=new HashSet<>();File[] all=activity.getCacheDir().listFiles((d,n)->n.startsWith("music-analysis-")&&n.endsWith(".wav"));if(all!=null)for(File f:all)files.add(f.getAbsolutePath());return files;}
    private static void screenshot(String name)throws Exception{runner.waitForIdleSync();Thread.sleep(600);Bitmap image=runner.getUiAutomation().takeScreenshot();File dir=new File(activity.getFilesDir(),"ui-checks");dir.mkdirs();try(java.io.OutputStream output=new java.io.FileOutputStream(new File(dir,name+".png"))){image.compress(Bitmap.CompressFormat.PNG,100,output);}finally{image.recycle();}}
    private static void restore(SharedPreferences prefs,Map<String,?> snapshot){SharedPreferences.Editor e=prefs.edit().clear();for(Map.Entry<String,?> row:snapshot.entrySet()){Object v=row.getValue();if(v instanceof String)e.putString(row.getKey(),(String)v);else if(v instanceof Boolean)e.putBoolean(row.getKey(),(Boolean)v);else if(v instanceof Integer)e.putInt(row.getKey(),(Integer)v);else if(v instanceof Long)e.putLong(row.getKey(),(Long)v);else if(v instanceof Float)e.putFloat(row.getKey(),(Float)v);else if(v instanceof Set)e.putStringSet(row.getKey(),new HashSet<>((Set<String>)v));}e.commit();}
    public static int run(Instrumentation instrumentation,Activity screen,PlaybackService service)throws Exception{
        runner=instrumentation;activity=screen;checks=0;SharedPreferences prefs=activity.getSharedPreferences("music_module",0);Map<String,?> before=prefs.getAll();
        ArrayList<MusicLibrary.Track> original=MusicLibrary.load(activity);final int originalIndex=service.index(),originalPosition=service.position(),originalVolume=service.volume(),originalTab=(Integer)field(activity,"tab");final float originalSpeed=service.speed();final boolean originalPlaying=service.isPlaying();
        Set<String> baseline=cacheFiles();File fixture=new File(activity.getCacheDir(),"analysis050-fixture.wav");MusicAnalysisPanel[] panel={null};
        try{
            try(WaveWriter wave=new WaveWriter(fixture,48000,2)){for(int i=0;i<48000*20;i++){wave.sample((short)(14000*Math.sin(2*Math.PI*440*i/48000)));wave.sample((short)(8000*Math.sin(2*Math.PI*880*i/48000)));}}
            ArrayList<MusicLibrary.Track> queue=new ArrayList<>();queue.add(new MusicLibrary.Track(Uri.fromFile(fixture).toString(),"Analysis fixture"));
            // Replacing the queue can call stopSelf(); restart through the same
            // Android service contract as the app instead of only invoking its binder.
            main(()->{MusicLibrary.save(activity,queue);service.setVolume(0);service.reload();service.setSpeed(2);activity.startForegroundService(new Intent(activity,PlaybackService.class).setAction(PlaybackService.PLAY).putExtra("index",0));prefs.edit().putBoolean("analysis_show",true).putInt("analysis_display_mode",MusicAnalysisPanel.WAVEFORM).commit();set(activity,"tab",0);call(activity,"render");panel[0]=(MusicAnalysisPanel)field(activity,"analysis");});
            waitFor(()->service.duration()>=19900&&service.isPlaying(),"20 second PCM prepared");check(true,"real PCM fixture playing silently at 2x");
            main(()->{check(panel[0]!=null,"player creates inline analysis");View cover=(View)field(activity,"cover"),slot=(View)field(activity,"analysisSlot"),title=(View)field(activity,"title");ViewGroup parent=(ViewGroup)cover.getParent();check(slot.getParent()==parent&&title.getParent()==parent&&parent.indexOfChild(cover)<parent.indexOfChild(slot)&&parent.indexOfChild(slot)<parent.indexOfChild(title),"analysis lies below artwork and above title");});
            waitFor(()->field(panel[0],"current")!=null&&field(panel[0],"next")!=null,"current and prefetched PCM windows");
            waitFor(()->((float[])field(field(panel[0],"scope"),"left")).length>0,"decoded waveform samples");
            main(()->{float[] left=(float[])field(field(panel[0],"scope"),"left"),right=(float[])field(field(panel[0],"scope"),"right");double energy=0,difference=0;for(int i=0;i<left.length;i++){energy+=Math.abs(left[i]);difference+=Math.abs(left[i]-right[i]);}check(energy>10&&difference>10,"real left and right PCM reach waveform");Button mode=(Button)field(panel[0],"switchMode");mode.performClick();check(panel[0].displayMode()==MusicAnalysisPanel.XY,"inline mode button switches to X-Y");mode.performClick();check(panel[0].displayMode()==MusicAnalysisPanel.SPECTRUM,"inline mode button switches to spectrum");});
            waitFor(()->field(field(panel[0],"spectrum"),"frame")!=null,"spectrum FFT");
            waitFor(()->((MusicSpectrumHistory)field(field(panel[0],"spectrum"),"history")).size()>=12,"dense source-timestamped spectrum history");
            main(()->{List<MusicSpectrumHistory.Entry> entries=((MusicSpectrumHistory)field(field(panel[0],"spectrum"),"history")).entries();check(entries.get(0).positionMillis-entries.get(11).positionMillis<=150,"spectrum samples multiple real audio positions between UI ticks");});
            main(()->{MusicSpectrum.Frame frame=(MusicSpectrum.Frame)field(field(panel[0],"spectrum"),"frame");List<MusicSpectrum.Peak> peaks=MusicSpectrum.peaks(frame,3,20,20000);check(!peaks.isEmpty()&&Math.abs(peaks.get(0).frequency-440)<3,"spectrum finds actual 440 Hz source tone");panel[0].setSpectrumLogarithmic(false);check(!(Boolean)field(field(panel[0],"spectrum"),"logarithmic"),"linear frequency setting reaches view");panel[0].setSpectrumLogarithmic(true);panel[0].setPeakCount(0);check((Integer)field(field(panel[0],"spectrum"),"peakCount")==0,"zero peak labels accepted");panel[0].setPeakCount(10);check((Integer)field(field(panel[0],"spectrum"),"peakCount")==10,"ten peak labels accepted");Bitmap bitmap=Bitmap.createBitmap(600,700,Bitmap.Config.ARGB_8888);View v=(View)field(panel[0],"spectrum");v.layout(0,0,600,700);v.draw(new Canvas(bitmap));bitmap.recycle();check(true,"spectrum and history render on a canvas");((Button)field(panel[0],"switchMode")).performClick();check(panel[0].displayMode()==MusicAnalysisPanel.WAVEFORM,"third mode click returns to waveform");});
            main(()->{panel[0].setDisplayMode(MusicAnalysisPanel.SPECTRUM);panel[0].view().requestLayout();});screenshot("spectrum");main(()->panel[0].setDisplayMode(MusicAnalysisPanel.WAVEFORM));screenshot("waveform");
            final long[] firstWindow={-1};main(()->firstWindow[0]=(Long)field(field(panel[0],"current"),"start"));boolean[] crossed={false};long until=SystemClock.uptimeMillis()+9000;
            while(SystemClock.uptimeMillis()<until&&!crossed[0]){main(()->{check(((float[])field(field(panel[0],"scope"),"left")).length>0,"waveform retains samples while playback crosses decode boundary");Object window=field(panel[0],"current");crossed[0]=window!=null&&(Long)field(window,"start")>firstWindow[0]&&service.position()>8500;});Thread.sleep(35);}
            check(crossed[0],"playback advances across the 8 second PCM cache without clearing waveform");
            final long[] pausedAt={0};main(()->{panel[0].pause();pausedAt[0]=(Long)field(panel[0],"lastAnalysisPosition");});Thread.sleep(180);main(()->{check((Long)field(panel[0],"lastAnalysisPosition")==pausedAt[0],"paused lifecycle stops analysis polling");panel[0].resume();service.seek(14000);});
            waitFor(()->{Object current=field(panel[0],"current");return current!=null&&(Long)field(current,"start")>=13500;} ,"seek discards stale PCM generation");check(true,"seek replaces stale source window");
            main(()->call(activity,"closeAnalysis"));waitFor(()->((ExecutorService)field(panel[0],"decoder")).isTerminated()&&((ExecutorService)field(panel[0],"analyzer")).isTerminated(),"analysis workers shut down");
            waitFor(()->{Set<String> now=cacheFiles();now.removeAll(baseline);return now.isEmpty();},"analysis temporary WAV cleanup");check(true,"close cancels workers and removes PCM cache files");
            checkSmallSeekAndFailureBackoff(fixture);
            return checks;
        }finally{
            main(()->{call(activity,"closeAnalysis");service.pause();restore(prefs,before);service.reload();service.setSpeed(originalSpeed);service.setVolume(originalVolume);if(!original.isEmpty())activity.startForegroundService(new Intent(activity,PlaybackService.class).setAction(PlaybackService.PLAY).putExtra("index",Math.max(0,Math.min(original.size()-1,originalIndex))));set(activity,"tab",originalTab);call(activity,"render");});
            if(!original.isEmpty()){waitFor(()->!service.loading()&&service.duration()>0,"original playback queue prepared after analysis test");main(()->{service.seek(originalPosition);if(originalPlaying)service.resume();else service.pause();});}
            fixture.delete();
        }
    }
    private static void checkSmallSeekAndFailureBackoff(File fixture)throws Exception{
        final long[] position={0};final MusicAnalysisPanel[] probe={null};final Uri uri=Uri.fromFile(fixture);CountDownLatch unblock=new CountDownLatch(1);
        try{
            main(()->{probe[0]=new MusicAnalysisPanel(activity,new MusicAnalysisPanel.PlaybackCursor(){public Uri source(){return uri;}public String title(){return "Small-seek fixture";}public long positionMillis(){return position[0];}public long durationMillis(){return 20000;}public boolean playing(){return false;}});probe[0].resume();});
            waitFor(()->field(probe[0],"current")!=null&&field(probe[0],"next")!=null,"small-seek probe prefetch");
            // Block only the next decode: promote the prefetched window, then drag backward
            // by less than 1.5 s, outside both the current and pending future windows.
            ((ExecutorService)field(probe[0],"decoder")).submit(()->{try{unblock.await();}catch(InterruptedException ignored){}});
            main(()->{position[0]=(Long)field(field(probe[0],"current"),"end")-70;set(probe[0],"lastPosition",position[0]);});
            waitFor(()->(Boolean)field(probe[0],"decoding")&&(Long)field(probe[0],"plannedStart")>10000,"future window queued");
            final long[] oldGeneration={0};main(()->{oldGeneration[0]=(Long)field(probe[0],"generation");long start=(Long)field(field(probe[0],"current"),"start");position[0]=start-80;set(probe[0],"lastPosition",start+20);});
            waitFor(()->(Long)field(probe[0],"generation")>oldGeneration[0]&&(Long)field(probe[0],"plannedStart")<position[0],"small backward seek cancels unrelated prefetch");check(true,"short backward seek does not wait for unrelated future decode");unblock.countDown();
            waitFor(()->{Object current=field(probe[0],"current");return current!=null&&(Long)field(current,"start")<=position[0]&&(Long)field(current,"end")>position[0];},"new seek generation owns PCM");check(true,"only replacement generation supplies seek samples");
        }finally{unblock.countDown();if(probe[0]!=null){main(()->probe[0].close());waitFor(()->((ExecutorService)field(probe[0],"decoder")).isTerminated()&&((ExecutorService)field(probe[0],"analyzer")).isTerminated(),"seek probe cleanup");}}
        final MusicAnalysisPanel[] broken={null};
        try{
            main(()->{broken[0]=new MusicAnalysisPanel(activity,new MusicAnalysisPanel.PlaybackCursor(){public Uri source(){return Uri.fromFile(new File(activity.getCacheDir(),"missing-analysis-fixture.wav"));}public String title(){return "Missing source";}public long positionMillis(){return 0;}public long durationMillis(){return 20000;}public boolean playing(){return false;}});broken[0].resume();});
            waitFor(()->(Integer)field(broken[0],"decodeFailures")==1,"expected decode failure");Thread.sleep(220);main(()->check((Integer)field(broken[0],"decodeFailures")==1&&!(Boolean)field(broken[0],"decoding"),"failure backs off instead of retrying every 50 ms"));
        }finally{if(broken[0]!=null){main(()->broken[0].close());waitFor(()->((ExecutorService)field(broken[0],"decoder")).isTerminated()&&((ExecutorService)field(broken[0],"analyzer")).isTerminated(),"failed probe cleanup");}}
    }
}
