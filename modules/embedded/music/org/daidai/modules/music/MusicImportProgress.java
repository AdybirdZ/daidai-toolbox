package org.daidai.modules.music;

import android.app.Activity;
import android.app.ProgressDialog;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import org.daidai.modules.shared.Texts;
import java.io.InterruptedIOException;

/** Coalesces filename changes so a fast folder provider cannot flood the UI queue. */
final class MusicImportProgress implements AutoCloseable {
    private final Activity activity;
    private final ProgressDialog dialog;
    private final Handler main=new Handler(Looper.getMainLooper());
    private volatile boolean cancelled;
    private volatile MusicImportReport.Phase phase=MusicImportReport.Phase.SCAN;
    private volatile long phaseStarted=SystemClock.elapsedRealtime();
    private volatile String name="";
    private volatile int completed,total;
    private volatile boolean metadataWarning;
    final MusicImportReport report;
    private boolean closed;
    private final Runnable refresh=new Runnable(){public void run(){
        if(closed||activity.isDestroyed())return;
        long seconds=Math.max(0,(SystemClock.elapsedRealtime()-phaseStarted)/1000);
        String text=cancelled?Texts.get(activity,"music_import_cancelling"):Texts.get(activity,"music_import_phase_"+phase.name().toLowerCase(java.util.Locale.ROOT));
        if(!name.isEmpty())text+="\n"+name;
        if(total>0)text+="\n"+Texts.get(activity,"music_import_position",completed,total);
        if(seconds>=10)text+="\n"+Texts.get(activity,"music_import_slow_operation",seconds);
        dialog.setMessage(text);main.postDelayed(this,200);
    }};
    MusicImportProgress(Activity activity){this(activity,MusicImportReport.Mode.FOLDER);}
    MusicImportProgress(Activity activity,MusicImportReport.Mode mode){
        this.activity=activity;report=new MusicImportReport(mode);
        dialog=new ProgressDialog(activity);dialog.setMessage(Texts.get(activity,"music_import_reading_folder"));dialog.setCancelable(false);
        dialog.setButton(ProgressDialog.BUTTON_NEGATIVE,Texts.get(activity,"music_cancel"),(d,w)->{});
        dialog.setOnShowListener(d->dialog.getButton(ProgressDialog.BUTTON_NEGATIVE).setOnClickListener(v->{cancelled=true;report.cancelled=true;dialog.getButton(ProgressDialog.BUTTON_NEGATIVE).setEnabled(false);dialog.setMessage(Texts.get(activity,"music_import_cancelling"));}));
        dialog.show();main.post(refresh);
    }
    void file(String name,int completed,int total)throws InterruptedIOException {
        check();this.name=name==null?"":name;this.completed=completed;this.total=total;metadataWarning=false;phaseStarted=SystemClock.elapsedRealtime();
    }
    void phase(MusicImportReport.Phase phase)throws InterruptedIOException{check();this.phase=phase;phaseStarted=SystemClock.elapsedRealtime();}
    void warning(){metadataWarning=true;}
    void success(String source,String stored,String name){report.add(new MusicImportReport.Entry(name,source,stored,phase,MusicImportReport.Result.SUCCESS,metadataWarning?"music_import_reason_optional":""));}
    void skipped(String source,String name){report.add(new MusicImportReport.Entry(name,source,source,MusicImportReport.Phase.SCAN,MusicImportReport.Result.SKIPPED,"music_import_reason_non_audio"));}
    void failed(String source,String stored,String name,Throwable error){report.add(new MusicImportReport.Entry(name,source,stored,phase,MusicImportReport.Result.FAILED,MusicImportReport.reason(error)));}
    void saving()throws InterruptedIOException {phase(MusicImportReport.Phase.SAVE);}
    void check()throws InterruptedIOException {if(cancelled||Thread.currentThread().isInterrupted())throw new InterruptedIOException();}
    boolean cancelled(){return cancelled;}
    public void close(){closed=true;main.removeCallbacks(refresh);if(dialog.isShowing())dialog.dismiss();}
    void destroy(){cancelled=true;report.cancelled=true;close();}
}
