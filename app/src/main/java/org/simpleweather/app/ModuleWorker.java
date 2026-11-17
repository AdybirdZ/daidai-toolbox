package org.simpleweather.app;

import android.app.*;
import android.content.*;
import android.os.IBinder;

/** Lifecycle adapter for host-declared Android services; implementation lives in the app. */
public abstract class ModuleWorker extends ContextWrapper {
    protected final Service service;
    public static final int START_NOT_STICKY=Service.START_NOT_STICKY;
    public ModuleWorker(Service service,Context context){super(context);this.service=service;}
    public void onCreate(){}
    public void onDestroy(){}
    public IBinder onBind(Intent intent){return null;}
    public int onStartCommand(Intent intent,int flags,int id){return START_NOT_STICKY;}
    public final void startForeground(int id,Notification note){service.startForeground(id,note);}
    public final void stopForeground(boolean remove){service.stopForeground(remove);}
    public final void stopSelf(){service.stopSelf();}
}
