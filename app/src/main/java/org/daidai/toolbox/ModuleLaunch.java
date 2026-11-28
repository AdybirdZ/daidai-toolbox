package org.daidai.toolbox;

import android.app.Activity;
import android.view.*;
import java.io.File;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Confirms an update only after its first draw and a short stable foreground interval. */
final class ModuleLaunch {
    private static final Map<Activity,Attempt> ACTIVE=new WeakHashMap<>();
    private static final Set<Activity> RESUMED=Collections.newSetFromMap(new WeakHashMap<Activity,Boolean>());
    private ModuleLaunch(){}
    static void begin(Activity activity,String id){
        leave(activity);ACTIVE.put(activity,new Attempt(activity,id));
    }
    static void observe(Activity activity,String id,View root,BooleanSupplier current){
        Attempt attempt=ACTIVE.get(activity);if(attempt==null||!attempt.id.equals(id)){begin(activity,id);attempt=ACTIVE.get(activity);}
        attempt.detach();attempt.root=root;attempt.current=current;attempt.watch();
    }
    static void resume(Activity activity){RESUMED.add(activity);Attempt attempt=ACTIVE.get(activity);if(attempt!=null){if(attempt.token==null)attempt.token=AppModules.beginTrackedLaunch(activity,attempt.id);attempt.watch();}}
    static void pause(Activity activity){RESUMED.remove(activity);Attempt attempt=ACTIVE.get(activity);if(attempt!=null)attempt.cancel();}
    static void cancel(Activity activity,String id){Attempt attempt=ACTIVE.get(activity);if(attempt!=null&&attempt.id.equals(id))leave(activity);}
    static void leave(Activity activity){Attempt attempt=ACTIVE.remove(activity);if(attempt!=null)attempt.cancel();}
    static void destroy(Activity activity){pause(activity);leave(activity);}
    private static final class Attempt {
        final Activity activity;final String id;final File file;final long stamp,length;String token;View root;BooleanSupplier current;
        ViewTreeObserver.OnDrawListener listener;Runnable confirm;boolean scheduled;int revision;
        Attempt(Activity activity,String id){this.activity=activity;this.id=id;file=ModuleRuntime.packageFile(activity,id);stamp=file.lastModified();length=file.length();token=AppModules.beginTrackedLaunch(activity,id);}
        boolean eligible(){return ACTIVE.get(activity)==this&&RESUMED.contains(activity)&&!activity.isFinishing()&&!activity.isDestroyed()&&root!=null&&root.isAttachedToWindow()&&current!=null&&current.getAsBoolean()&&file.lastModified()==stamp&&file.length()==length;}
        void watch(){
            if(token==null||root==null||listener!=null||scheduled)return;
            final int expectedRevision=revision;listener=()->{if(!eligible()||scheduled||revision!=expectedRevision)return;scheduled=true;final View observed=root;final String expectedToken=token;observed.post(()->{
                if(ACTIVE.get(activity)!=this||root!=observed||revision!=expectedRevision)return;removeListener();
                confirm=()->{if(revision!=expectedRevision)return;confirm=null;scheduled=false;if(eligible()){AppModules.markLaunchSucceeded(activity,id,expectedToken);token=null;ACTIVE.remove(activity);}else cancel();};observed.postDelayed(confirm,1000);
            });};root.getViewTreeObserver().addOnDrawListener(listener);root.invalidate();
        }
        void removeListener(){if(root!=null&&listener!=null&&root.getViewTreeObserver().isAlive())root.getViewTreeObserver().removeOnDrawListener(listener);listener=null;}
        void detach(){revision++;removeListener();if(root!=null&&confirm!=null)root.removeCallbacks(confirm);confirm=null;scheduled=false;}
        void cancel(){detach();if(token!=null)AppModules.cancelLaunch(activity,id,token);token=null;}
    }
}
