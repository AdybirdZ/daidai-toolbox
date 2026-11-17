package org.simpleweather.app;

import android.app.*;
import android.content.*;
import android.content.res.*;
import android.os.*;
import java.io.File;

/** Host-owned window, using the signed app's resources and page implementation. */
public final class InternalAppActivity extends Activity {
    private String appId;private Resources moduleResources;private Resources.Theme moduleTheme;private ModulePage page;
    @Override protected void attachBaseContext(Context base){super.attachBaseContext(AppLanguage.wrap(base));}
    @Override public Resources getResources(){return moduleResources==null?super.getResources():moduleResources;}
    @Override public AssetManager getAssets(){return moduleResources==null?super.getAssets():moduleResources.getAssets();}
    @Override public Context createConfigurationContext(Configuration config){Context base=super.createConfigurationContext(config);return moduleResources==null?base:new ModuleRuntime.ModuleContext(base,appId,new Resources(moduleResources.getAssets(),moduleResources.getDisplayMetrics(),config));}
    @Override public Resources.Theme getTheme(){return moduleTheme==null?super.getTheme():moduleTheme;}
    @Override public File getFilesDir(){return appId==null?super.getFilesDir():ModuleRuntime.dataDirectory(this,appId);}
    @Override public File getCacheDir(){if(appId==null)return super.getCacheDir();File f=new File(super.getCacheDir(),"internal-"+appId);f.mkdirs();return f;}
    @Override public Context getApplicationContext(){return appId==null?super.getApplicationContext():new ModuleRuntime.ModuleContext(super.getApplicationContext(),appId,getResources());}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);String id=getIntent().getStringExtra("internal_app");
        if(!java.util.Arrays.asList(AppModules.EMBEDDED).contains(id)){finish();return;}appId=id;
        try{moduleResources=ModuleRuntime.resources(getBaseContext(),id);moduleTheme=moduleResources.newTheme();int theme=moduleResources.getIdentifier("PrototypeTheme","style","org.simpleweather.module."+id);moduleTheme.applyStyle(theme==0?android.R.style.Theme_Material_Light_NoActionBar:theme,true);
            page=ModuleRuntime.page(this,id,getIntent().getStringExtra("internal_page")==null?"main":getIntent().getStringExtra("internal_page"));page.dispatch("create",state);
            if(getSharedPreferences("toolbox",0).getBoolean("keep_screen_awake",false))getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }catch(Exception e){android.util.Log.e("InternalApp","Cannot open "+id,e);android.widget.Toast.makeText(getBaseContext(),getBaseContext().getString(R.string.module_open_failed),1).show();finish();}
    }
    @Override protected void onStart(){super.onStart();if(page!=null)page.dispatch("start",null);}
    @Override protected void onResume(){super.onResume();if(page!=null)page.dispatch("resume",null);}
    @Override protected void onPause(){if(page!=null)page.dispatch("pause",null);super.onPause();}
    @Override protected void onStop(){if(page!=null)page.dispatch("stop",null);super.onStop();}
    @Override protected void onDestroy(){closeModule();ModuleRuntime.pageClosed(appId,this);super.onDestroy();}
    void closeModule(){if(page!=null){page.dispatch("pause",null);page.dispatch("stop",null);page.dispatch("destroy",null);page=null;}if(!isFinishing())finish();}
    @Override protected void onSaveInstanceState(Bundle state){if(page!=null)page.dispatch("save",state);super.onSaveInstanceState(state);}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(page!=null)page.result(request,result,data);}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){super.onRequestPermissionsResult(request,permissions,grants);if(page!=null)page.onRequestPermissionsResult(request,permissions,grants);}
    @Override public void onConfigurationChanged(Configuration config){super.onConfigurationChanged(config);try{moduleResources=ModuleRuntime.resources(getBaseContext(),appId);}catch(Exception ignored){}if(page!=null)page.onConfigurationChanged(config);}
}
