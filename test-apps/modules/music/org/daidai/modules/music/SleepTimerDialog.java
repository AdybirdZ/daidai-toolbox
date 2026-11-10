package org.daidai.modules.music;

import android.app.*;
import android.content.res.ColorStateList;
import android.os.*;
import android.view.Gravity;
import android.widget.*;
import java.util.Locale;
import org.daidai.modules.shared.Texts;
import org.daidai.modules.shared.Ui;

/** Timer controls belong to the music module; its service owns the actual deadline. */
public final class SleepTimerDialog {
    private SleepTimerDialog(){}
    private static String text(Activity a,String key,Object... args){return Texts.get(a,key,args);}
    public static String summary(Activity a,PlaybackService service){
        if(service==null||!service.sleepTimerActive())return text(a,"music070_timer_inactive");
        if(service.sleepTimerWaiting())return text(a,"music070_timer_waiting");
        long seconds=(service.sleepTimerRemainingMs()+999)/1000;
        return text(a,"music070_timer_remaining",String.format(Locale.ROOT,"%02d:%02d",seconds/60,seconds%60));
    }
    public static String buttonLabel(Activity a,PlaybackService service){
        return service!=null&&service.sleepTimerActive()?summary(a,service):text(a,"music070_timer_title");
    }
    public static void open(Activity a,PlaybackService service){
        if(service==null){Ui.note(a,text(a,"music070_timer_title"),text(a,"music070_timer_select_track"));return;}
        LinearLayout box=Ui.column(a);box.setPadding(Ui.dp(a,22),Ui.dp(a,12),Ui.dp(a,22),Ui.dp(a,12));
        TextView status=Ui.text(a,summary(a,service),16);status.setGravity(Gravity.CENTER);box.addView(status);
        int minutes=Math.max(SleepTimer.MIN_MINUTES,Math.min(SleepTimer.MAX_MINUTES,a.getSharedPreferences("music_module",0).getInt("sleep_timer_minutes",30)));
        TextView duration=Ui.text(a,text(a,"music070_timer_duration",minutes),15);box.addView(duration);
        SeekBar slider=new SeekBar(a);slider.setMax(SleepTimer.MAX_MINUTES-SleepTimer.MIN_MINUTES);slider.setProgress(minutes-SleepTimer.MIN_MINUTES);box.addView(slider);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar v){}public void onStopTrackingTouch(SeekBar v){}public void onProgressChanged(SeekBar v,int value,boolean user){duration.setText(text(a,"music070_timer_duration",value+SleepTimer.MIN_MINUTES));}});
        CheckBox finish=new CheckBox(a);finish.setText(text(a,"music070_timer_finish_track"));finish.setTextColor(Ui.INK);finish.setButtonTintList(ColorStateList.valueOf(Ui.BLUE));finish.setChecked(a.getSharedPreferences("music_module",0).getBoolean("sleep_timer_finish_track",true));box.addView(finish);
        Ui.button(box,text(a,"music070_timer_apply"),()->{if(!service.setSleepTimer(slider.getProgress()+SleepTimer.MIN_MINUTES,finish.isChecked()))Ui.note(a,text(a,"music070_timer_title"),text(a,"music070_timer_select_track"));status.setText(summary(a,service));});
        Button cancel=Ui.button(box,text(a,"music070_timer_cancel"),()->{service.cancelSleepTimer();status.setText(summary(a,service));});
        box.addView(Ui.text(a,text(a,"music070_timer_note"),13));
        ScrollView scroll=new ScrollView(a);scroll.addView(box);
        AlertDialog dialog=new AlertDialog.Builder(a).setTitle(text(a,"music070_timer_title")).setView(scroll).setPositiveButton(text(a,"music_close"),null).create();
        Handler ui=new Handler(Looper.getMainLooper());Runnable tick=new Runnable(){public void run(){if(!dialog.isShowing()||a.isFinishing()||a.isDestroyed())return;status.setText(summary(a,service));cancel.setEnabled(service.sleepTimerActive());ui.postDelayed(this,1000);}};
        dialog.setOnShowListener(d->ui.post(tick));dialog.setOnDismissListener(d->ui.removeCallbacks(tick));dialog.show();
    }
}
