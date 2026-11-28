package org.daidai.toolbox;

import android.app.*;
import android.content.*;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.*;

/** Public page boundary. Business pages are loaded from a signed internal application. */
public abstract class ModulePage extends ContextWrapper {
    public static final int RESULT_OK=Activity.RESULT_OK;
    protected final Activity activity;
    public ModulePage(Activity activity){super(activity);this.activity=activity;}
    protected void onCreate(Bundle state){}
    protected void onStart(){}
    protected void onResume(){}
    protected void onPause(){}
    protected void onStop(){}
    protected void onDestroy(){}
    protected void onSaveInstanceState(Bundle state){}
    protected void onActivityResult(int request,int result,Intent data){}
    /** Stable no-op hook retained for api-1 compatibility. */
    protected void onActivityReturn(){}
    public void onRequestPermissionsResult(int request,String[] permissions,int[] results){}
    public void onConfigurationChanged(Configuration config){}
    public final void dispatch(String event,Bundle state){switch(event){case "create":onCreate(state);break;case "start":onStart();break;case "resume":onResume();break;case "pause":onPause();break;case "stop":onStop();break;case "destroy":onDestroy();break;case "save":onSaveInstanceState(state);break;}}
    public final void result(int request,int result,Intent data){onActivityResult(request,result,data);}
    public final void returned(){onActivityReturn();}
    public final void runOnUiThread(Runnable action){activity.runOnUiThread(action);}
    public final boolean isDestroyed(){return activity.isDestroyed();}
    public final boolean isFinishing(){return activity.isFinishing();}
    public final void requestPermissions(String[] permissions,int request){activity.requestPermissions(permissions,request);}
    public final boolean shouldShowRequestPermissionRationale(String permission){return activity.shouldShowRequestPermissionRationale(permission);}
    public final void startActivityForResult(Intent intent,int request){activity.startActivityForResult(intent,request);}
    public final Window getWindow(){return activity.getWindow();}
    public final Intent getIntent(){return activity.getIntent();}
    public final void setContentView(View view){activity.setContentView(view);}
    public final void finish(){activity.finish();}
}
