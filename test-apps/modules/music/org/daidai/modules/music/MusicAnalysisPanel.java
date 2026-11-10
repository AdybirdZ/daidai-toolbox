package org.daidai.modules.music;

import android.app.Activity;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import org.daidai.modules.shared.Texts;
import org.daidai.modules.shared.Ui;
import java.io.File;
import java.util.Locale;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Reusable embedded local-audio analysis. The host supplies playback state and owns lifecycle. */
public final class MusicAnalysisPanel implements AutoCloseable {
    public static final int WAVEFORM=0,XY=1,SPECTRUM=2;
    /** No service, launcher, notification, library or persistence dependency is required by a host. */
    public interface PlaybackCursor {
        Uri source();String title();long positionMillis();long durationMillis();boolean playing();
    }
    private static final int FFT_SIZE=4096,WAVE_SIZE=1024;
    private static final long WINDOW_MS=8000,OVERLAP_MS=200;
    private final Activity activity;private final PlaybackCursor cursor;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService decoder=Executors.newSingleThreadExecutor(),analyzer=Executors.newSingleThreadExecutor();
    private final LinearLayout root,amplitudeControls;private final OscilloscopeView scope;private final SpectrumView spectrum;
    private final TextView status,amplitude,peaks;private final Button switchMode;
    private Future<?> decodeJob;private Window current,next;private long generation,lastPosition=-1,lastAnalysisPosition=-1,lastSpectrumPosition=-1,plannedStart=-1,plannedEnd=-1,retryAt;
    private int decodeFailures;
    private String source="";private boolean running,closed,decoding,analyzing;private int mode=WAVEFORM;
    private static final class Window {
        final StereoPcmReader reader;final File file;final long start,end;
        Window(StereoPcmReader p,File f,long from){reader=p;file=f;start=from;end=from+(long)Math.ceil(p.frames()*1000./p.sampleRate());}
        boolean contains(long at){return at>=start&&at<end;}
        void dispose(){try{reader.close();}catch(Exception ignored){}file.delete();}
    }
    public MusicAnalysisPanel(Activity a,PlaybackService service){this(a,new PlaybackCursor(){
        public Uri source(){MusicLibrary.Track t=service==null?null:service.selected();return t==null?null:Uri.parse(t.uri);}
        public String title(){MusicLibrary.Track t=service==null?null:service.selected();return t==null?"":t.title;}
        public long positionMillis(){return service==null?0:service.position();}public long durationMillis(){return service==null?0:service.duration();}public boolean playing(){return service!=null&&service.isPlaying();}
    });}
    public MusicAnalysisPanel(Activity a,PlaybackCursor playback){
        if(playback==null)throw new IllegalArgumentException("Playback cursor required");activity=a;cursor=playback;
        root=Ui.column(a);root.setPadding(Ui.dp(a,8),Ui.dp(a,8),Ui.dp(a,8),Ui.dp(a,8));
        switchMode=Ui.button(root,"",()->setDisplayMode((mode+1)%3));
        scope=new OscilloscopeView(a);root.addView(scope,new LinearLayout.LayoutParams(-1,Ui.dp(a,260)));
        spectrum=new SpectrumView(a);root.addView(spectrum,new LinearLayout.LayoutParams(-1,Ui.dp(a,500)));
        peaks=Ui.text(a,"",12);root.addView(peaks);spectrum.setOnFrequencyRangeChanged(this::updatePeaks);
        amplitudeControls=Ui.column(a);amplitude=Ui.text(a,"",13);amplitudeControls.addView(amplitude);SeekBar gain=new SeekBar(a);gain.setMax(100);gain.setProgress(100);amplitudeControls.addView(gain,new LinearLayout.LayoutParams(-1,-2));gain.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar bar,int value,boolean user){scope.setAmplitudeRange((float)(.02*Math.pow(50,value/100.)));updateAmplitude();}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}});root.addView(amplitudeControls);
        status=Ui.text(a,text("music050_analysis_select"),13);root.addView(status);root.addView(Ui.text(a,text("music060_analysis_note"),12));setDisplayMode(a.getSharedPreferences("music_module",0).getInt("analysis_display_mode",WAVEFORM));updateAmplitude();
    }
    private String text(String key,Object... args){return Texts.get(activity,key,args);}
    public View view(){return root;}
    public int displayMode(){return mode;}
    public void setDisplayMode(int value){mode=value==XY?XY:value==SPECTRUM?SPECTRUM:WAVEFORM;activity.getSharedPreferences("music_module",0).edit().putInt("analysis_display_mode",mode).apply();scope.setMode(mode);scope.setVisibility(mode==SPECTRUM?View.GONE:View.VISIBLE);spectrum.setVisibility(mode==SPECTRUM?View.VISIBLE:View.GONE);amplitudeControls.setVisibility(mode==WAVEFORM?View.VISIBLE:View.GONE);updatePeaks();switchMode.setText(text("music050_analysis_switch",text(mode==XY?"music050_analysis_xy":mode==SPECTRUM?"music050_analysis_spectrum":"music050_analysis_waveform")));lastAnalysisPosition=lastSpectrumPosition=-1;}
    public void setSpectrumLogarithmic(boolean value){spectrum.setLogarithmic(value);updatePeaks();}
    public void setPeakCount(int value){spectrum.setPeakCount(value);updatePeaks();}
    public void setHistoryDurationMillis(int value){spectrum.setHistoryDurationMillis(value);}
    public void resetFrequencyRange(){spectrum.resetFrequencyRange();updatePeaks();}
    private void updateAmplitude(){amplitude.setText(text("music050_scope_range",String.format(Locale.getDefault(),"%.3f",scope.amplitudeRange()/2)));}
    private void updatePeaks(){String summary=spectrum.peakSummary();peaks.setVisibility(mode==SPECTRUM&&!summary.isEmpty()?View.VISIBLE:View.GONE);if(!summary.isEmpty())peaks.setText(text("music050_spectrum_peak_list",summary));}
    public void resume(){if(closed||running)return;running=true;lastPosition=-1;main.post(tick);}
    public void pause(){running=false;main.removeCallbacks(tick);}
    private final Runnable tick=new Runnable(){public void run(){
        if(!running||closed)return;
        Uri uri=cursor.source();String now=uri==null?"":uri.toString();long at=Math.max(0,cursor.positionMillis()),duration=Math.max(0,cursor.durationMillis());if(duration>0)at=Math.min(at,duration-1);
        boolean seek=lastPosition>=0&&(Math.abs(at-lastPosition)>1500||at<lastPosition-150);
        boolean pending=decoding&&at>=plannedStart&&at<plannedEnd;
        boolean buffered=current!=null&&current.contains(at)||next!=null&&next.contains(at);
        if(!now.equals(source)||seek){restart(now,at,duration,true);}
        // A short backward seek can leave both resident windows while the decoder is
        // preparing an unrelated future window. Cancel that generation immediately.
        else if(!buffered&&!pending&&(current!=null||decoding)&&SystemClock.uptimeMillis()>=retryAt){boolean changedPosition=decoding||current!=null&&at<current.start;restart(now,at,duration,changedPosition);}
        else if(!now.isEmpty()&&duration>0&&current==null&&!decoding){decode(Math.max(0,at-300),duration,true);}
        if(next!=null&&next.contains(at)&&(current==null||at>=current.end-FFT_SIZE*1000L/current.reader.sampleRate())){Window old=current;current=next;next=null;if(old!=null)disposeLater(old);lastAnalysisPosition=-1;}
        if(current!=null){
            if(current.contains(at)){
                if(!decoding&&next==null&&current.end<duration-30)decode(prefetchStart(current),duration,false);
                if(!analyzing&&(at!=lastAnalysisPosition||lastAnalysisPosition<0))analyze(current,at);
            }
        }
        lastPosition=at;main.postDelayed(this,50);
    }};
    private static long prefetchStart(Window window){return Math.max(window.start+1,window.end-Math.max(OVERLAP_MS,FFT_SIZE*1000L/window.reader.sampleRate()+100));}
    private void restart(String now,long at,long duration,boolean resetFailureBackoff){
        generation++;if(decodeJob!=null)decodeJob.cancel(true);decoding=false;analyzing=false;plannedStart=plannedEnd=-1;source=now;lastAnalysisPosition=lastSpectrumPosition=-1;if(resetFailureBackoff){decodeFailures=0;retryAt=0;}
        if(current!=null)disposeLater(current);if(next!=null)disposeLater(next);current=null;next=null;spectrum.clear();scope.samples(null);
        if(now.isEmpty()||duration<=0){status.setText(text("music050_analysis_select"));return;}status.setText(text("music050_analysis_loading"));decode(Math.max(0,at-300),duration,true);
    }
    private void decode(long start,long duration,boolean initial){
        if(closed||duration<=start||SystemClock.uptimeMillis()<retryAt)return;final long token=generation,end=Math.min(duration,start+WINDOW_MS);final String uri=source;decoding=true;plannedStart=start;plannedEnd=end;
        decodeJob=decoder.submit(()->{File file=null;StereoPcmReader reader=null;try{
            file=File.createTempFile("music-analysis-",".wav",activity.getCacheDir());AudioTrimmer.export(activity,Uri.parse(uri),start*1000,end*1000,file,p->{});
            if(Thread.currentThread().isInterrupted())throw new InterruptedException();reader=new StereoPcmReader(file);Window ready=new Window(reader,file,start);reader=null;file=null;
            main.post(()->{if(closed||token!=generation){ready.dispose();return;}decoding=false;plannedStart=plannedEnd=-1;decodeFailures=0;retryAt=0;if(initial){if(current!=null)disposeLater(current);current=ready;}else{if(next!=null)disposeLater(next);next=ready;}status.setText(text("music050_analysis_ready"));if(current!=null&&next==null&&current.end<cursor.durationMillis()-30)decode(prefetchStart(current),cursor.durationMillis(),false);});
        }catch(Exception failure){if(reader!=null)try{reader.close();}catch(Exception ignored){}if(file!=null)file.delete();main.post(()->{if(!closed&&token==generation){decoding=false;plannedStart=plannedEnd=-1;decodeFailures=Math.min(31,decodeFailures+1);retryAt=SystemClock.uptimeMillis()+Math.min(30000L,1000L<<Math.min(5,decodeFailures-1));status.setText(text("music050_analysis_failed"));}});}});
    }
    private void analyze(Window window,long at){
        analyzing=true;final long token=generation;lastAnalysisPosition=at;final int selectedMode=mode;final long previous=lastSpectrumPosition;
        analyzer.submit(()->{try{float[][] points=null;ArrayList<MusicSpectrum.Frame> frames=new ArrayList<>();ArrayList<Long> stamps=new ArrayList<>();
            if(selectedMode==SPECTRUM){long first=previous>=0&&at>=previous?Math.max(window.start,Math.max(previous+MusicSpectrumHistory.STEP_MS,at-500)):at;
                for(long position=first;position<=at;position+=MusicSpectrumHistory.STEP_MS){frames.add(MusicSpectrum.analyze(window.reader.read(position-window.start,FFT_SIZE),window.reader.sampleRate(),FFT_SIZE));stamps.add(position);}
                if(stamps.isEmpty()&&previous<0){frames.add(MusicSpectrum.analyze(window.reader.read(at-window.start,FFT_SIZE),window.reader.sampleRate(),FFT_SIZE));stamps.add(at);}
            }else points=window.reader.read(at-window.start,WAVE_SIZE);
            final float[][] waveform=points;final MusicSpectrum.Frame[] transformed=frames.toArray(new MusicSpectrum.Frame[0]);final long[] positions=new long[stamps.size()];for(int i=0;i<positions.length;i++)positions[i]=stamps.get(i);
            main.post(()->{if(closed||token!=generation)return;analyzing=false;if(mode!=selectedMode)return;scope.setSampleRate(window.reader.sampleRate());if(selectedMode==SPECTRUM){spectrum.samples(transformed,positions);if(positions.length>0)lastSpectrumPosition=positions[positions.length-1];updatePeaks();}else scope.samples(waveform);});
        }catch(Exception failure){main.post(()->{if(!closed&&token==generation)analyzing=false;});}});
    }
    private void disposeLater(Window window){if(!analyzer.isShutdown())analyzer.submit(window::dispose);else window.dispose();}
    @Override public void close(){if(closed)return;closed=true;pause();generation++;if(decodeJob!=null)decodeJob.cancel(true);decoder.shutdownNow();if(current!=null)disposeLater(current);if(next!=null)disposeLater(next);current=null;next=null;analyzer.shutdown();}
}
