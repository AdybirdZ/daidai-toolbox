package org.simpleweather.qa;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.view.inspector.WindowInspector;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/** Isolated emulator: real prefs, image bytes, ContentResolver, dialogs and page reload. */
public final class ConfigurationUiTests extends Instrumentation {
    private Activity activity;private Object transfer;private int checks;
    private final Map<String,Map<String,?>> original=new HashMap<>();private byte[] background;
    private static final String[] GROUPS={"toolbox","weather","status_places","status_routes","timetable"};
    private Object field(Object o,String key){try{Field f=o.getClass().getDeclaredField(key);f.setAccessible(true);return f.get(o);}catch(Exception e){throw new RuntimeException(e);}}
    private void field(Object o,String key,Object v){try{Field f=o.getClass().getDeclaredField(key);f.setAccessible(true);f.set(o,v);}catch(Exception e){throw new RuntimeException(e);}}
    private Object call(Object o,String name,Class<?>[] types,Object... values){try{Method m=o.getClass().getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(o,values);}catch(Exception e){throw new RuntimeException(e);}}
    private void ui(Runnable r){AtomicReference<Throwable> error=new AtomicReference<>();runOnMainSync(()->{try{r.run();}catch(Throwable e){error.set(e);}});if(error.get()!=null)throw new RuntimeException(error.get());}
    private void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    private SharedPreferences prefs(String name){return getTargetContext().getSharedPreferences(name,0);}
    private TextView text(View root,String value){if(root instanceof TextView&&value.contentEquals(((TextView)root).getText()))return (TextView)root;if(root instanceof ViewGroup){ViewGroup g=(ViewGroup)root;for(int i=0;i<g.getChildCount();i++){TextView t=text(g.getChildAt(i),value);if(t!=null)return t;}}return null;}
    private View dialog(String title){for(View root:WindowInspector.getGlobalWindowViews())if(root.isShown()&&text(root,title)!=null)return root;return null;}
    private View await(String title){for(int i=0;i<160;i++){AtomicReference<View> v=new AtomicReference<>();ui(()->v.set(dialog(title)));if(v.get()!=null)return v.get();SystemClock.sleep(50);}throw new AssertionError("Missing dialog "+title);}
    private void idleTransfer(){for(int i=0;i<200;i++){AtomicReference<Boolean> busy=new AtomicReference<>();ui(()->busy.set((Boolean)field(transfer,"busy")));if(!busy.get())return;SystemClock.sleep(50);}throw new AssertionError("Transfer timeout");}
    @Override public void onCreate(Bundle b){super.onCreate(b);start();}
    @Override public void onStart(){Bundle result=new Bundle();int code=Activity.RESULT_OK;File image=new File(getTargetContext().getFilesDir(),"background.jpg");
        try{
            for(String group:GROUPS)original.put(group,new HashMap<>(prefs(group).getAll()));if(image.exists())background=Files.readAllBytes(image.toPath());
            prefs("toolbox").edit().putInt("seen_version",Integer.MAX_VALUE).putInt("last_tab",0).putLong("quake_interval",0).putBoolean("outline",true).putInt("background_brightness",41).putLong("location_interval",10000).putFloat("quake_ref_lon",120.75f).commit();
            prefs("weather").edit().putInt("quota_count_0",7).commit();
            prefs("status_places").edit().putString("places","[{\"id\":\"config-place\",\"name\":\"配置地点\",\"lon\":120.6,\"lat\":31.3,\"created\":1000,\"color\":-16711936,\"visible\":false}]").putString("selected","config-place").commit();
            prefs("status_routes").edit().putString("routes","[{\"id\":\"config-route\",\"name\":\"配置路线\",\"points\":[{\"lon\":120.6,\"lat\":31.3,\"time\":1000},{\"lon\":120.7,\"lat\":31.4,\"time\":2000}]}]").commit();
            try(FileOutputStream out=new FileOutputStream(image)){Bitmap bitmap=Bitmap.createBitmap(8,12,Bitmap.Config.ARGB_8888);bitmap.eraseColor(Color.BLUE);bitmap.compress(Bitmap.CompressFormat.PNG,100,out);bitmap.recycle();}
            activity=startActivitySync(new Intent().setClassName(getTargetContext(),"org.simpleweather.app.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();transfer=field(activity,"configuration");
            check(getTargetContext().getPackageManager().getPackageInfo(getTargetContext().getPackageName(),0).versionName.equals(call(activity,"versionName",new Class[]{})),"About version comes from actual installed package");
            searchChecks();
            File exported=new File(getTargetContext().getCacheDir(),"configuration-ui.json");AtomicReference<Intent> picker=new AtomicReference<>();
            ActivityMonitor exportMonitor=new ActivityMonitor(){@Override public ActivityResult onStartActivity(Intent i){if(Intent.ACTION_CREATE_DOCUMENT.equals(i.getAction())){picker.set(i);return new ActivityResult(Activity.RESULT_OK,new Intent().setData(Uri.fromFile(exported)));}return null;}};addMonitor(exportMonitor);
            ui(()->call(transfer,"exportFile",new Class[]{}));idleTransfer();removeMonitor(exportMonitor);
            check(picker.get()!=null&&"application/json".equals(picker.get().getType())&&picker.get().hasCategory(Intent.CATEGORY_OPENABLE),"Export launches real system document contract");
            JSONObject document=new JSONObject(new String(Files.readAllBytes(exported.toPath()),StandardCharsets.UTF_8));check(document.getInt("version")==2,"Real export is version 2");
            check(Arrays.equals(Base64.getDecoder().decode(document.getString("background")),Files.readAllBytes(image.toPath())),"Export includes exact cropped image bytes");
            check(!document.getJSONObject("weather").has("quota_count_0"),"Export excludes quota counters");
            deliver(exported);View confirm=await("导入全部配置");check(text(confirm,"确认覆盖并导入")!=null,"Import validates and requests explicit overwrite");ui(()->text(confirm,"取消").performClick());idleTransfer();check(prefs("toolbox").getBoolean("outline",false),"Cancel changes no preference");
            File bad=new File(getTargetContext().getCacheDir(),"bad-configuration.json");Files.write(bad.toPath(),"{bad".getBytes(StandardCharsets.UTF_8));deliver(bad);idleTransfer();check(prefs("toolbox").getInt("background_brightness",0)==41,"Malformed file leaves store untouched");
            JSONObject wrong=new JSONObject(document.toString()).put("background",Base64.getEncoder().encodeToString(new byte[]{1,2,3}));Files.write(bad.toPath(),wrong.toString().getBytes(StandardCharsets.UTF_8));deliver(bad);idleTransfer();check(prefs("toolbox").getInt("background_brightness",0)==41,"Invalid decoded image rejected before confirmation or writes");
            prefs("toolbox").edit().putBoolean("outline",false).putInt("background_brightness",12).putLong("location_interval",5000).putBoolean("quake_source_Wolfx",true).putBoolean("quake_wolfx_ack_112",true).commit();
            prefs("weather").edit().putInt("quota_count_0",13).commit();prefs("status_places").edit().putString("places","[]").commit();prefs("status_routes").edit().putString("routes","[]").commit();Files.delete(image.toPath());
            ActivityMonitor reload=new ActivityMonitor("org.simpleweather.app.MainActivity",null,false);addMonitor(reload);
            deliver(exported);View okay=await("导入全部配置");ui(()->text(okay,"确认覆盖并导入").performClick());idleTransfer();Activity next=waitForMonitorWithTimeout(reload,8000);removeMonitor(reload);check(next!=null,"Successful import opens a fresh Activity/model graph");if(next!=null)activity=next;
            check(prefs("toolbox").getBoolean("outline",false)&&prefs("toolbox").getInt("background_brightness",0)==41,"Actual imported settings replace changed settings");
            check(prefs("toolbox").getLong("location_interval",0)==10000&&prefs("toolbox").getFloat("quake_ref_lon",0)==120.75f,"JSON restores native long and float preference types");
            check(prefs("weather").getInt("quota_count_0",0)>=13,"Import does not reset running quota counters");
            check(!prefs("toolbox").getBoolean("location_continuous",true)&&!prefs("toolbox").getBoolean("status_camera_background",true)&&!prefs("toolbox").getBoolean("quake_source_Wolfx",true)&&!prefs("toolbox").getBoolean("quake_wolfx_ack_112",true),"Unsafe runtime and acknowledged source do not resume");
            check(new JSONArray(prefs("status_places").getString("places","[]")).length()==1&&new JSONArray(prefs("status_routes").getString("routes","[]")).length()==1,"Actual saved place and route stores restored");
            check(Arrays.equals(Files.readAllBytes(image.toPath()),Base64.getDecoder().decode(document.getString("background"))),"Background restored before fresh Activity decodes it");
            Object gps=field(activity,"gps"),places=field(gps,"places"),store=field(gps,"routeStore");
            ui(()->{check(((List<?>)call(places,"all",new Class[]{})).size()==1,"Fresh places model reads imported store");check(((List<?>)call(store,"all",new Class[]{})).size()==1,"Fresh route model reads imported store");});
            String exportedSchedule=document.getJSONObject("timetable").getJSONObject("data").getString("value");Class<?> modelType=activity.getClassLoader().loadClass("org.simpleweather.app.ScheduleModel");Object migrated=modelType.getMethod("parse",String.class).invoke(null,exportedSchedule);check(prefs("timetable").getString("data","").equals(modelType.getMethod("json").invoke(migrated).toString()),"Timetable contents survive color-format migration");
            result.putString("stream","\nPASS: "+checks+" configuration/search Android checks.\n");
        }catch(Throwable e){code=Activity.RESULT_CANCELED;result.putString("stream","\nFAIL: "+android.util.Log.getStackTraceString(e));}
        finally{
            if(activity!=null)ui(()->activity.finish());
            for(String group:GROUPS)if(original.containsKey(group))restore(prefs(group),original.get(group));
            try{if(background==null)Files.deleteIfExists(image.toPath());else Files.write(image.toPath(),background);}catch(Exception ignored){}
        }
        finish(code,result);
    }
    private void searchChecks(){ui(()->{call(activity,"showPicker",new Class[]{});EditText input=(EditText)field(activity,"search");for(String name:new String[]{"通化市","宿迁市","康定市","草湖市","龙海市","头份市","员林市"}){input.setText(name);LinearLayout results=(LinearLayout)field(activity,"results");int rows=0;for(int i=0;i<results.getChildCount();i++)if(results.getChildAt(i) instanceof LinearLayout)rows++;check(rows==1,"One local search result for "+name);}input.setText("甘孜藏族自治州");check(text((View)field(activity,"results"),"甘孜藏族自治州")==null,"Removed autonomous prefecture not offered as city");call(activity,"switchTab",new Class[]{int.class},0);});}
    private void deliver(File f){ui(()->{field(transfer,"busy",true);call(activity,"onActivityResult",new Class[]{int.class,int.class,Intent.class},71,Activity.RESULT_OK,new Intent().setData(Uri.fromFile(f)));});}
    private static void restore(SharedPreferences p,Map<String,?> values){SharedPreferences.Editor e=p.edit().clear();for(Map.Entry<String,?> x:values.entrySet()){Object v=x.getValue();String k=x.getKey();if(v instanceof Boolean)e.putBoolean(k,(Boolean)v);else if(v instanceof Integer)e.putInt(k,(Integer)v);else if(v instanceof Long)e.putLong(k,(Long)v);else if(v instanceof Float)e.putFloat(k,(Float)v);else if(v instanceof String)e.putString(k,(String)v);else if(v instanceof Set)e.putStringSet(k,(Set<String>)v);}e.commit();}
}
