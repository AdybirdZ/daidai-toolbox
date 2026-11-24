package org.simpleweather.app;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

/** Dedicated settings application window, shared by all installed internal applications. */
public final class SettingsActivity extends MainActivity {
    public static final int REQUEST=7301;
    private SettingsNavigation pages;
    private boolean finishingAfterReturn;
    public static void addBackground(Activity activity,FrameLayout container){
        // InternalAppActivity uses an app-specific files directory for module data;
        // the selected toolbox background remains in the host files directory.
        Bitmap image=BitmapFactory.decodeFile(new java.io.File(activity.getApplicationInfo().dataDir,"files/background.jpg").getPath());
        if(image==null){container.setBackgroundColor(MainActivity.BG);return;}
        ImageView background=new ImageView(activity);background.setScaleType(ImageView.ScaleType.CENTER_CROP);background.setImageBitmap(image);
        container.addView(background,new FrameLayout.LayoutParams(-1,-1));
        int brightness=Math.max(0,Math.min(100,activity.getSharedPreferences("toolbox",0).getInt("background_brightness",20)));
        View shade=new View(activity);shade.setBackgroundColor(Color.argb((100-brightness)*255/100,245,247,251));
        container.addView(shade,new FrameLayout.LayoutParams(-1,-1));
        container.setBackgroundColor(MainActivity.BG);
    }
    public static Activity owner(Context context){for(int i=0;i<10&&context!=null;i++){if(context instanceof Activity)return (Activity)context;if(!(context instanceof ContextWrapper))break;Context next=((ContextWrapper)context).getBaseContext();if(next==context)break;context=next;}return null;}
    public static String hostText(Context context,int id){Activity a=owner(context);return (a==null?context:a.getBaseContext()).getString(id);}
    public static void open(Activity origin,String app){
        if(origin instanceof SettingsActivity&&!java.util.Arrays.asList(AppModules.EMBEDDED).contains(app)){((SettingsActivity)origin).openTarget(app);return;}
        Intent intent;
        if(java.util.Arrays.asList(AppModules.EMBEDDED).contains(app)){
            try{if(!AppModules.inspect(origin,ModuleRuntime.packageFile(origin,app),app).getJSONObject("pages").has("settings")){android.widget.Toast.makeText(origin,hostText(origin,R.string.settings_update_required),0).show();return;}}catch(Exception e){android.widget.Toast.makeText(origin,hostText(origin,R.string.module_open_failed),0).show();return;}
            intent=new Intent(origin,InternalAppActivity.class).putExtra("internal_app",app).putExtra("internal_page","settings").putExtra("settings_mode",true);
        }
        else intent=new Intent(origin,SettingsActivity.class)
                .putExtra("settings_target",app==null?"":app)
                .putExtra("settings_direct_target",app!=null&&!app.isEmpty());
        intent.putExtra(ActivityReturnGate.EXTRA,ActivityReturnGate.register(origin));
        if(origin instanceof MainActivity)((MainActivity)origin).markChildActivity();
        if(origin instanceof InternalAppActivity)((InternalAppActivity)origin).markChildActivity();
        origin.startActivityForResult(intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION),REQUEST);origin.overridePendingTransition(0,0);
    }
    @Override public void finish(){if(!finishingAfterReturn&&ActivityReturnGate.finishAfterDraw(this,()->{finishingAfterReturn=true;finish();}))return;super.finish();overridePendingTransition(0,0);}
    @Override protected void onShellReady(Bundle state){
        pages=new SettingsNavigation(this);
        boolean direct=getIntent().getBooleanExtra("settings_direct_target",false)
                &&getIntent().getStringExtra("settings_target")!=null
                &&!getIntent().getStringExtra("settings_target").isEmpty();
        setResult(RESULT_OK,new Intent().putExtra("settings_direct_target",direct));
        if(state==null||!state.containsKey("toolbox_ui_session")&&state.getInt("toolbox_language_settings",0)==0){
            if(direct)openTarget(getIntent().getStringExtra("settings_target"));
            else new SettingsScreen(this).show();
        }
        getWindow().getDecorView().post(()->getWindow().getDecorView().post(()->{
            if(pages.empty()&&!isFinishing()){if(direct)finish();else new SettingsScreen(this).show();}
        }));
    }
    private void openTarget(String id){if(id==null||id.isEmpty())return;if(!apps.installed(id))return;
        if(java.util.Arrays.asList(AppModules.EMBEDDED).contains(id)){open(this,id);return;}
        switch(id){case "weather":case "weather-map":case "status":case "astronomy":FeatureAccess.settings(this,id);break;case "earthquake":earthquakeSettings();break;case "schedule":scheduleSettings();break;case "random":randomChoiceSettings();break;case "todo":todoSettings();break;}
    }
    @Override public void refreshScreen(){if(pages==null)super.refreshScreen();else{ToolDialog.refresh(this);pages.refresh();}}
    @Override public void changeLanguage(String choice){if(pages!=null&&!AppLanguage.choice(this).equals(choice))pages.preparingRecreation();super.changeLanguage(choice);}
    @Override public boolean restoreDefaults(){if(pages!=null&&!AppLanguage.choice(this).equals("system"))pages.preparingRecreation();return super.restoreDefaults();}
    /** Settings must not briefly render the host desktop before its own page stack exists. */
    @Override public void switchTab(int next){if(pages==null)return;if(next==-1)SettingsNavigation.desktop(this);}
    @Override public void onBackPressed(){if(pages==null||!pages.back())finish();}
    @Override protected void onDestroy(){if(pages!=null)pages.close();super.onDestroy();}
}
