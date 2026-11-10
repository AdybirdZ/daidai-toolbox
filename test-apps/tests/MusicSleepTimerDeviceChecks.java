package org.daidai.prototype.qa;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.inspector.WindowInspector;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.SeekBar;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import org.daidai.modules.music.PlaybackService;
import org.daidai.modules.music.SleepTimer;
import org.daidai.modules.music.SleepTimerDialog;
import org.daidai.modules.shared.Texts;

/** Real-service deadline and MediaPlayer completion checks; no one-minute test waits. */
public final class MusicSleepTimerDeviceChecks {
    private static int count;
    private static void check(boolean yes,String why){count++;if(!yes)throw new AssertionError(why);}
    private static void waitFor(Instrumentation i,Callable<Boolean> predicate)throws Exception{
        long end=SystemClock.elapsedRealtime()+10000;
        while(SystemClock.elapsedRealtime()<end){boolean[] yes={false};i.runOnMainSync(()->{try{yes[0]=predicate.call();}catch(Exception e){throw new RuntimeException(e);}});if(yes[0])return;Thread.sleep(20);}
        throw new AssertionError("Timed out waiting for sleep timer playback state");
    }
    private static void accelerate(PlaybackService service,int milliseconds)throws Exception{
        Field state=PlaybackService.class.getDeclaredField("sleepTimer");state.setAccessible(true);SleepTimer timer=(SleepTimer)state.get(service);
        Field deadline=SleepTimer.class.getDeclaredField("deadline");deadline.setAccessible(true);deadline.setLong(timer,SystemClock.elapsedRealtime()+milliseconds);
        Method schedule=PlaybackService.class.getDeclaredMethod("scheduleSleepDeadline");schedule.setAccessible(true);schedule.invoke(service);
    }
    private static void restart(Instrumentation i,Context c,PlaybackService service,int selected)throws Exception{
        i.runOnMainSync(()->{service.cancelSleepTimer();c.startForegroundService(new Intent(c,PlaybackService.class).setAction(PlaybackService.PLAY).putExtra("index",selected));});
        waitFor(i,service::isPlaying);i.runOnMainSync(()->{service.setSpeed(1);service.seek(0);});
    }
    private static View top(){List<View> roots=WindowInspector.getGlobalWindowViews();if(roots.isEmpty())throw new AssertionError("No timer dialog window");return roots.get(roots.size()-1);}
    private static <T extends View> ArrayList<T> views(View root,Class<T> type){ArrayList<T> all=new ArrayList<>();if(type.isInstance(root))all.add(type.cast(root));if(root instanceof ViewGroup){ViewGroup g=(ViewGroup)root;for(int n=0;n<g.getChildCount();n++)all.addAll(views(g.getChildAt(n),type));}return all;}
    private static Button button(Activity a,View root,String key){String label=Texts.get(a,key);for(Button b:views(root,Button.class))if(label.contentEquals(b.getText()))return b;throw new AssertionError("Missing timer button: "+key);}
    private static void checkDialog(Instrumentation i,Activity a,PlaybackService service){
        i.runOnMainSync(()->{a.getSharedPreferences("music_module",0).edit().remove("sleep_timer_minutes").remove("sleep_timer_finish_track").commit();SleepTimerDialog.open(a,service);View root=top();SeekBar slider=views(root,SeekBar.class).get(0);CheckBox finish=views(root,CheckBox.class).get(0);
            check(finish.isChecked(),"Finish-current option is checked by default");
            check(slider.getMax()==119&&slider.getProgress()==29,"Slider has 1-120-minute bounds and a 30-minute initial value");
            check(finish.getButtonTintList().getDefaultColor()==org.daidai.modules.shared.Ui.BLUE,"Timer checkbox keeps its blue selection color");
            slider.setProgress(0);button(a,root,"music070_timer_apply").performClick();check(service.sleepTimerActive()&&service.sleepTimerRemainingMs()>59000&&service.sleepTimerRemainingMs()<=60000,"Slider minimum applies one minute");
            slider.setProgress(119);finish.setChecked(false);button(a,root,"music070_timer_apply").performClick();check(service.sleepTimerRemainingMs()>7199000&&service.sleepTimerRemainingMs()<=7200000&&!service.sleepTimerFinishesCurrentTrack(),"Slider maximum applies 120 minutes and immediate-stop mode");
            button(a,root,"music070_timer_cancel").performClick();check(!service.sleepTimerActive(),"Dialog cancel-timer button clears service state");
            button(a,root,"music_close").performClick();
            SleepTimerDialog.open(a,service);root=top();check(views(root,SeekBar.class).get(0).getProgress()==119&&!views(root,CheckBox.class).get(0).isChecked(),"Dialog restores duration and finish-current choice");
            button(a,root,"music_close").performClick();
        });
    }
    public static int run(Instrumentation i,Activity activity,PlaybackService service)throws Exception{
        count=0;Context c=i.getTargetContext();int selected=service.index();
        if(selected<0||service.duration()<1500)throw new AssertionError("Timer tests require a prepared audio fixture");
        int oldMinutes=c.getSharedPreferences("music_module",0).getInt("sleep_timer_minutes",30);
        boolean oldFinish=c.getSharedPreferences("music_module",0).getBoolean("sleep_timer_finish_track",true);
        float oldSpeed=service.speed();
        try{
            restart(i,c,service,selected);
            checkDialog(i,activity,service);
            i.runOnMainSync(()->{check(service.setSleepTimer(1,true),"Timer can start on a prepared song");check(service.sleepTimerActive()&&service.sleepTimerRemainingMs()>59000,"Service exposes monotonic remaining time");check(service.sleepTimerFinishesCurrentTrack(),"Finish-current preference is exposed");check(!service.setSleepTimer(0,false)&&service.sleepTimerActive(),"Invalid reset does not erase active timer");check(!service.setSleepTimer(121,false)&&service.sleepTimerActive(),"Out-of-range maximum cannot replace the active timer");service.pause();try{accelerate(service,100);}catch(Exception e){throw new RuntimeException(e);}});
            waitFor(i,()->!service.sleepTimerActive()&&service.duration()==0);
            check(!service.isPlaying(),"Paused song is released when deadline expires");
            i.runOnMainSync(()->check(!service.setSleepTimer(1,true),"Stopped source cannot start a timer without playback"));

            restart(i,c,service,selected);
            i.runOnMainSync(()->{service.setSleepTimer(1,false);try{accelerate(service,100);}catch(Exception e){throw new RuntimeException(e);}});
            waitFor(i,()->!service.sleepTimerActive()&&service.duration()==0);check(!service.isPlaying(),"Immediate mode stops real playback from the service callback");

            restart(i,c,service,selected);
            i.runOnMainSync(()->{service.setSleepTimer(1,true);try{accelerate(service,100);}catch(Exception e){throw new RuntimeException(e);}});
            waitFor(i,service::sleepTimerWaiting);check(service.isPlaying(),"Finish-current keeps the real player running at deadline");
            check(!SleepTimerDialog.summary(activity,service).isEmpty(),"Waiting state has a localized label");
            i.runOnMainSync(()->service.play(selected));waitFor(i,()->service.duration()==0);
            check(!service.sleepTimerActive()&&!service.isPlaying(),"Manual selection after deadline stops instead of starting another song");

            restart(i,c,service,selected);
            i.runOnMainSync(()->{service.setSleepTimer(1,true);try{accelerate(service,100);}catch(Exception e){throw new RuntimeException(e);}});
            waitFor(i,service::sleepTimerWaiting);i.runOnMainSync(service::pause);
            check(service.duration()==0&&!service.sleepTimerActive(),"Pause while finishing consumes the timer");

            restart(i,c,service,selected);
            i.runOnMainSync(()->{service.setSleepTimer(1,true);try{accelerate(service,100);}catch(Exception e){throw new RuntimeException(e);}});
            waitFor(i,service::sleepTimerWaiting);i.runOnMainSync(()->service.seek(service.duration()-250));
            waitFor(i,()->service.duration()==0&&!service.sleepTimerActive());check(!service.isPlaying(),"Natural completion does not repeat or advance after deadline");

            restart(i,c,service,selected);
            i.runOnMainSync(()->{service.setSleepTimer(1,false);try{accelerate(service,100);}catch(Exception e){throw new RuntimeException(e);}service.cancelSleepTimer();});
            Thread.sleep(180);check(service.isPlaying()&&!service.sleepTimerActive(),"Cancellation removes the pending service deadline");
            i.runOnMainSync(()->{service.setSleepTimer(1,false);try{accelerate(service,100);}catch(Exception e){throw new RuntimeException(e);}service.setSleepTimer(2,true);});
            Thread.sleep(180);check(service.isPlaying()&&service.sleepTimerRemainingMs()>119000,"Reset replaces the old deadline without stopping playback");
            i.runOnMainSync(service::stopPlayback);check(!service.sleepTimerActive(),"Manual stop clears timer and final-song wait");
            restart(i,c,service,selected);
            check(!activity.isFinishing()&&!activity.isDestroyed(),"Timer stops playback without closing the activity");
        }finally{
            i.runOnMainSync(()->{service.cancelSleepTimer();service.setSpeed(oldSpeed);c.getSharedPreferences("music_module",0).edit().putInt("sleep_timer_minutes",oldMinutes).putBoolean("sleep_timer_finish_track",oldFinish).commit();});
        }
        return count;
    }
}
