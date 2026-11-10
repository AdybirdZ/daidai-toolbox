package org.daidai.modules.music;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.os.Bundle;
import android.widget.ScrollView;
import org.daidai.modules.shared.Texts;

/** Compatibility entry point; playback pages should embed MusicAnalysisPanel directly. */
public final class MusicScopeDialog {
    private MusicScopeDialog(){}
    public static void open(Activity activity,PlaybackService service){
        MusicAnalysisPanel panel=new MusicAnalysisPanel(activity,service);ScrollView scroll=new ScrollView(activity);scroll.addView(panel.view());
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle(Texts.get(activity,"music_scope_title")).setView(scroll).setNegativeButton(Texts.get(activity,"music_close"),null).create();
        Application.ActivityLifecycleCallbacks lifecycle=new Application.ActivityLifecycleCallbacks(){
            public void onActivityStopped(Activity value){if(value==activity)dialog.dismiss();}
            public void onActivityDestroyed(Activity value){if(value==activity)dialog.dismiss();}
            public void onActivityCreated(Activity value,Bundle state){}public void onActivityStarted(Activity value){}public void onActivityResumed(Activity value){}public void onActivityPaused(Activity value){}public void onActivitySaveInstanceState(Activity value,Bundle state){}
        };
        activity.getApplication().registerActivityLifecycleCallbacks(lifecycle);dialog.setOnDismissListener(value->{activity.getApplication().unregisterActivityLifecycleCallbacks(lifecycle);panel.close();});dialog.show();panel.resume();
    }
}
