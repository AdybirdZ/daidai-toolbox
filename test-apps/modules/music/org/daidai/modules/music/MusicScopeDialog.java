package org.daidai.modules.music;

import android.app.*;
import android.os.*;
import android.net.Uri;
import android.widget.*;
import org.daidai.modules.shared.Texts;
import org.daidai.modules.shared.Ui;
import java.io.*;
import java.util.concurrent.*;

/** Local PCM windows follow playback without recording microphone or output. */
public final class MusicScopeDialog {
    private MusicScopeDialog(){}
    public static void open(Activity activity,PlaybackService service){new Controller(activity,service).open();}
    private static final class Controller implements Application.ActivityLifecycleCallbacks {
        final Activity a;final PlaybackService service;final Handler main=new Handler(Looper.getMainLooper());final ExecutorService worker=Executors.newSingleThreadExecutor();
        OscilloscopeView scope;TextView status;AlertDialog dialog;Future<?> job;StereoPcmReader pcm;File pcmFile;String uri="\u0000";boolean closed,working,waiting;long generation,windowStart,windowEnd;
        Controller(Activity a,PlaybackService s){this.a=a;service=s;}
        String text(String key,Object... args){return Texts.get(a,key,args);}
        void open(){LinearLayout box=Ui.column(a);box.setPadding(Ui.dp(a,22),Ui.dp(a,10),Ui.dp(a,22),Ui.dp(a,12));RadioGroup modes=new RadioGroup(a);modes.setOrientation(RadioGroup.HORIZONTAL);RadioButton normal=new RadioButton(a),xy=new RadioButton(a);normal.setId(1);xy.setId(2);normal.setText(text("music_scope_normal"));xy.setText(text("music_scope_xy"));modes.addView(normal,new RadioGroup.LayoutParams(0,-2,1));modes.addView(xy,new RadioGroup.LayoutParams(0,-2,1));modes.check(1);box.addView(modes);scope=new OscilloscopeView(a);box.addView(scope,new LinearLayout.LayoutParams(-1,Ui.dp(a,270)));modes.setOnCheckedChangeListener((g,id)->scope.setMode(id==2?OscilloscopeView.XY:OscilloscopeView.WAVEFORM));status=Ui.text(a,text("music_scope_loading"),14);box.addView(status);box.addView(Ui.text(a,text("music_scope_note"),13));ScrollView scroll=new ScrollView(a);scroll.addView(box);dialog=new AlertDialog.Builder(a).setTitle(text("music_scope_title")).setView(scroll).setNegativeButton(text("music_close"),null).create();dialog.setOnDismissListener(d->close());a.getApplication().registerActivityLifecycleCallbacks(this);dialog.show();main.post(tick);}
        final Runnable tick=new Runnable(){public void run(){if(closed)return;MusicLibrary.Track t=service==null?null:service.selected();String current=t==null?"":t.uri;int at=service==null?0:service.position();if(!current.equals(uri)||waiting&&service.duration()>0||pcm!=null&&!working&&(at<windowStart||at+40>=windowEnd&&windowEnd<service.duration())){reload(t,at);}else if(pcm!=null&&!working){working=true;StereoPcmReader ready=pcm;long token=generation,relative=at-windowStart;job=worker.submit(()->{try{float[][] points=ready.read(relative,1024);main.post(()->{if(!closed&&token==generation){scope.samples(points);working=false;}});}catch(Exception e){main.post(()->{if(!closed&&token==generation)working=false;});}});}main.postDelayed(this,50);}};
        void reload(MusicLibrary.Track t,int at){generation++;waiting=false;if(job!=null)job.cancel(true);clearPcm();uri=t==null?"":t.uri;scope.samples(null);if(t==null||service.duration()<=0){waiting=t!=null;working=false;status.setText(text("music_scope_select"));return;}status.setText(text("music_scope_loading"));working=true;long token=generation,start=Math.max(0,at-500),end=Math.min(service.duration(),start+8000);windowStart=start;windowEnd=end;job=worker.submit(()->{File file=null;StereoPcmReader reader=null;try{file=File.createTempFile("scope-",".wav",a.getCacheDir());AudioTrimmer.export(a,Uri.parse(t.uri),start*1000,end*1000,file,p->{});if(Thread.currentThread().isInterrupted())throw new InterruptedException();reader=new StereoPcmReader(file);final StereoPcmReader ready=reader;final File readyFile=file;reader=null;file=null;main.post(()->{if(closed||token!=generation){dispose(ready,readyFile);return;}pcm=ready;pcmFile=readyFile;working=false;status.setText(text("music_scope_track",t.title));});}catch(Exception e){dispose(reader,file);main.post(()->{if(!closed&&token==generation){working=false;status.setText(text("music_scope_failed"));}});}});}
        void clearPcm(){dispose(pcm,pcmFile);pcm=null;pcmFile=null;}
        static void dispose(StereoPcmReader p,File f){if(p!=null)try{p.close();}catch(Exception ignored){}if(f!=null)f.delete();}
        void close(){if(closed)return;closed=true;generation++;main.removeCallbacks(tick);a.getApplication().unregisterActivityLifecycleCallbacks(this);if(job!=null)job.cancel(true);worker.shutdownNow();clearPcm();}
        public void onActivityStopped(Activity value){if(value==a){if(dialog!=null&&dialog.isShowing())dialog.dismiss();else close();}}public void onActivityDestroyed(Activity value){if(value==a)close();}public void onActivityCreated(Activity value,Bundle b){}public void onActivityStarted(Activity value){}public void onActivityResumed(Activity value){}public void onActivityPaused(Activity value){}public void onActivitySaveInstanceState(Activity value,Bundle b){}
    }
}
