package org.simpleweather.app;

import android.app.*;
import android.content.*;
import android.content.res.*;
import android.os.*;
import java.io.File;

/** Host-owned window, using the signed app's resources and page implementation. */
public final class InternalAppActivity extends Activity {
    private String appId,activityLanguage;private Resources moduleResources;private Resources.Theme moduleTheme;private ModulePage page;private SettingsNavigation settingsPages;
    @Override public void finish(){super.finish();overridePendingTransition(0,0);}
    @Override protected void attachBaseContext(Context base){super.attachBaseContext(AppLanguage.wrap(base));}
    @Override public Resources getResources(){return moduleResources==null?super.getResources():moduleResources;}
    @Override public AssetManager getAssets(){return moduleResources==null?super.getAssets():moduleResources.getAssets();}
    @Override public Context createConfigurationContext(Configuration config){Context base=super.createConfigurationContext(config);return moduleResources==null?base:new ModuleRuntime.ModuleContext(base,appId,ModuleRuntime.configuredResources(moduleResources,config));}
    @Override public Resources.Theme getTheme(){return moduleTheme==null?super.getTheme():moduleTheme;}
    @Override public File getFilesDir(){return appId==null?super.getFilesDir():ModuleRuntime.dataDirectory(this,appId);}
    @Override public File getCacheDir(){if(appId==null)return super.getCacheDir();File f=new File(super.getCacheDir(),"internal-"+appId);f.mkdirs();return f;}
    @Override public Context getApplicationContext(){return appId==null?super.getApplicationContext():new ModuleRuntime.ModuleContext(super.getApplicationContext(),appId,getResources());}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);activityLanguage=AppLanguage.choice(this);String id=getIntent().getStringExtra("internal_app");
        if(!java.util.Arrays.asList(AppModules.EMBEDDED).contains(id)){finish();return;}appId=id;
        try{ModuleLaunch.begin(this,id);moduleResources=ModuleRuntime.resources(getBaseContext(),id);moduleTheme=moduleResources.newTheme();int theme=moduleResources.getIdentifier("PrototypeTheme","style","org.simpleweather.module."+id);moduleTheme.applyStyle(theme==0?android.R.style.Theme_Material_Light_NoActionBar:theme,true);
            if(getIntent().getBooleanExtra("settings_mode",false)){settingsPages=new SettingsNavigation(this);setResult(RESULT_OK);}
            page=ModuleRuntime.page(this,id,getIntent().getStringExtra("internal_page")==null?"main":getIntent().getStringExtra("internal_page"));page.dispatch("create",state);if(settingsPages!=null)settingsPages.restoreState(state,appId);
            if(getSharedPreferences("toolbox",0).getBoolean("keep_screen_awake",false))getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            ModuleLaunch.observe(this,id,getWindow().getDecorView(),()->page!=null);
        }catch(Throwable e){android.util.Log.e("InternalApp","Cannot open "+id,e);boolean recovered=false;try{recovered=AppModules.recoverPrevious(this,id);}catch(Exception recovery){android.util.Log.e("InternalApp","Cannot recover "+id,recovery);}android.widget.Toast.makeText(getBaseContext(),getBaseContext().getString(recovered?R.string.module_recovered:R.string.module_open_failed),1).show();finish();}
    }
    @Override protected void onStart(){super.onStart();if(page!=null)page.dispatch("start",null);}
    @Override protected void onResume(){super.onResume();if(page!=null)page.dispatch("resume",null);ModuleLaunch.resume(this);}
    @Override protected void onPause(){if(page!=null)page.dispatch("pause",null);ModuleLaunch.pause(this);super.onPause();}
    @Override protected void onStop(){if(page!=null)page.dispatch("stop",null);super.onStop();}
    @Override protected void onDestroy(){if(settingsPages!=null)settingsPages.close();releasePage();ModuleRuntime.pageClosed(appId,this);ModuleLaunch.destroy(this);super.onDestroy();}
    @Override public void onBackPressed(){if(settingsPages==null||!settingsPages.back())super.onBackPressed();}
    private void releasePage(){if(page!=null){page.dispatch("destroy",null);page=null;}}
    void closeModule(){if(page!=null){page.dispatch("pause",null);page.dispatch("stop",null);releasePage();}if(!isFinishing())finish();}
    @Override protected void onSaveInstanceState(Bundle state){if(page!=null)page.dispatch("save",state);if(settingsPages!=null)settingsPages.captureState(state);super.onSaveInstanceState(state);}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==SettingsActivity.REQUEST&&!java.util.Objects.equals(activityLanguage,AppLanguage.choice(this))){recreate();return;}if(page!=null)page.result(request,result,data);}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){super.onRequestPermissionsResult(request,permissions,grants);if(page!=null)page.onRequestPermissionsResult(request,permissions,grants);}
    @Override public void onConfigurationChanged(Configuration config){super.onConfigurationChanged(config);try{moduleResources=ModuleRuntime.resources(getBaseContext(),appId);}catch(Exception ignored){}if(page!=null)page.onConfigurationChanged(config);}
}
