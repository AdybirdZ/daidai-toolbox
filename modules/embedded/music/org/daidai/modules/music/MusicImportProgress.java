package org.daidai.modules.music;

import android.app.Activity;
import android.app.ProgressDialog;
import android.os.Handler;
import android.os.Looper;
import org.daidai.modules.shared.Texts;
import java.io.InterruptedIOException;

/** Coalesces filename changes so a fast folder provider cannot flood the UI queue. */
final class MusicImportProgress implements AutoCloseable {
    private final Activity activity;
    private final ProgressDialog dialog;
    private final Handler main=new Handler(Looper.getMainLooper());
    private volatile String message;
    private volatile boolean cancelled;
    private boolean closed;
    private final Runnable refresh=new Runnable(){public void run(){
        if(closed||activity.isDestroyed())return;
        dialog.setMessage(message);main.postDelayed(this,100);
    }};
    MusicImportProgress(Activity activity){
        this.activity=activity;message=Texts.get(activity,"music_import_reading_folder");
        dialog=new ProgressDialog(activity);dialog.setMessage(message);dialog.setCancelable(false);
        dialog.setButton(ProgressDialog.BUTTON_NEGATIVE,Texts.get(activity,"music_cancel"),(d,w)->{cancelled=true;close();});
        dialog.show();main.post(refresh);
    }
    void file(String name,int completed,int total)throws InterruptedIOException {
        check();message=total>0?Texts.get(activity,"music_import_file",name,completed,total):Texts.get(activity,"music_import_scanning_file",name,completed);
    }
    void saving()throws InterruptedIOException {check();message=Texts.get(activity,"music_import_saving");}
    void check()throws InterruptedIOException {if(cancelled||Thread.currentThread().isInterrupted())throw new InterruptedIOException();}
    boolean cancelled(){return cancelled;}
    public void close(){closed=true;main.removeCallbacks(refresh);if(dialog.isShowing())dialog.dismiss();}
    void destroy(){cancelled=true;close();}
}
