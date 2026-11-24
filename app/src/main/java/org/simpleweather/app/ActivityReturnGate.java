package org.simpleweather.app;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewTreeObserver;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Finishes a settings window only after its underlying application has drawn. */
final class ActivityReturnGate {
    static final String EXTRA="settings_return_token";
    private static final long FALLBACK_MS=80;
    private static final Map<String,WeakReference<Activity>> ORIGINS=new HashMap<>();

    private ActivityReturnGate(){}

    static String register(Activity origin){String token=UUID.randomUUID().toString();synchronized(ORIGINS){ORIGINS.put(token,new WeakReference<>(origin));}return token;}

    static boolean finishAfterDraw(Activity child,Runnable finish){
        String token=child.getIntent().getStringExtra(EXTRA);if(token==null)return false;
        Activity origin;synchronized(ORIGINS){WeakReference<Activity> ref=ORIGINS.remove(token);origin=ref==null?null:ref.get();}
        if(origin==null||origin.isFinishing()||origin.isDestroyed())return false;
        View decor=origin.getWindow().getDecorView();
        if(!decor.isAttachedToWindow()||decor.getWidth()==0||decor.getHeight()==0)return false;
        child.getWindow().getDecorView().setAlpha(0f);
        final Gate gate=new Gate(decor,finish);ViewTreeObserver observer=decor.getViewTreeObserver();
        if(!observer.isAlive())return false;
        observer.addOnDrawListener(gate.listener);
        decor.requestLayout();decor.invalidate();
        gate.timeout.postDelayed(gate.completion,FALLBACK_MS);
        return true;
    }

    private static final class Gate {
        final View decor;final Runnable finish;final Handler timeout=new Handler(Looper.getMainLooper());final ViewTreeObserver.OnDrawListener listener;final Runnable completion=this::complete;
        boolean completed;
        Gate(View decor,Runnable finish){this.decor=decor;this.finish=finish;listener=()->{if(!completed)decor.postOnAnimation(completion);};}
        void complete(){if(completed)return;completed=true;timeout.removeCallbacks(completion);ViewTreeObserver observer=decor.getViewTreeObserver();if(observer.isAlive())observer.removeOnDrawListener(listener);finish.run();}
    }
}
