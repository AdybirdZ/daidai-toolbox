package org.simpleweather.app;

import android.app.*;
import android.content.*;
import android.os.IBinder;

/** Manifest component only. No media or messaging business code is embedded in the host. */
public abstract class ModuleServiceBridge extends Service {
    private ModuleWorker worker;protected abstract String appId();
    @Override public void onCreate(){super.onCreate();try{worker=ModuleRuntime.worker(this,appId());ModuleRuntime.serviceOpened(appId(),this);worker.onCreate();}catch(Exception e){android.util.Log.e("InternalService","Cannot start "+appId(),e);stopSelf();}}
    @Override public IBinder onBind(Intent intent){return worker==null?null:worker.onBind(intent);}
    @Override public int onStartCommand(Intent intent,int flags,int id){if(worker==null){stopSelf();return START_NOT_STICKY;}return worker.onStartCommand(intent,flags,id);}
    void closeModule(){if(worker!=null){worker.onDestroy();worker=null;}stopForeground(true);stopSelf();ModuleRuntime.serviceClosed(appId(),this);}
    @Override public void onDestroy(){closeModule();super.onDestroy();}
}
