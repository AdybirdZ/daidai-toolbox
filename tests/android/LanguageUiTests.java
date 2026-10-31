package org.simpleweather.qa;
import android.app.*;
import android.content.*;
import android.graphics.*;
import android.hardware.*;
import android.os.*;
import android.view.*;
import android.view.inspector.WindowInspector;
import android.widget.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.json.*;

/** Exercise actual dialog navigation, color selection, fixed chart projection and Camera2. */
public final class LanguageUiTests extends Instrumentation {
    String language="zh-CN";Activity a;View courseEditor;int checks;final Map<String,Map<String,?>> before=new HashMap<>();String cameraResult="";
    Object field(Object o,String name){try{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}catch(Exception e){throw new RuntimeException(e);}}
    void field(Object o,String name,Object value){try{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.set(o,value);}catch(Exception e){throw new RuntimeException(e);}}
    Object call(Object o,String name,Class<?>[] types,Object... args){try{Method m=o.getClass().getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(o,args);}catch(Exception e){throw new RuntimeException(e);}}
    void ui(Runnable task){AtomicReference<Throwable> error=new AtomicReference<>();runOnMainSync(()->{try{task.run();}catch(Throwable e){error.set(e);}});if(error.get()!=null)throw new RuntimeException(error.get());}
    void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    SharedPreferences prefs(String name){return getTargetContext().getSharedPreferences(name,0);}
    TextView text(View root,String label){if(root instanceof TextView&&label.contentEquals(((TextView)root).getText()))return (TextView)root;if(root instanceof ViewGroup){ViewGroup g=(ViewGroup)root;for(int i=0;i<g.getChildCount();i++){TextView t=text(g.getChildAt(i),label);if(t!=null)return t;}}return null;}
    TextView prefix(View root,String label){if(root instanceof TextView&&((TextView)root).getText().toString().startsWith(label))return (TextView)root;if(root instanceof ViewGroup){ViewGroup g=(ViewGroup)root;for(int i=0;i<g.getChildCount();i++){TextView t=prefix(g.getChildAt(i),label);if(t!=null)return t;}}return null;}
    View dialog(String title){int id=getTargetContext().getResources().getIdentifier("alertTitle","id","android");for(View root:WindowInspector.getGlobalWindowViews()){View heading=root.findViewById(id);if(root.isShown()&&heading instanceof TextView&&title.contentEquals(((TextView)heading).getText()))return root;}throw new AssertionError("Missing dialog: "+title);}
    View sky(View root){if(root.getClass().getName().endsWith("AstronomyScreen$Sky"))return root;if(root instanceof ViewGroup){ViewGroup g=(ViewGroup)root;for(int i=0;i<g.getChildCount();i++){View v=sky(g.getChildAt(i));if(v!=null)return v;}}return null;}
    void tap(View root,String label){TextView t=text(root,label);if(t!=null){check(true,"Button exists: "+label);t.performClick();return;}ListView list=list(root);if(list!=null)for(int i=0;i<list.getAdapter().getCount();i++)if(label.equals(String.valueOf(list.getAdapter().getItem(i)))){check(true,"List option exists: "+label);list.performItemClick(list.getAdapter().getView(i,null,list),i,list.getAdapter().getItemId(i));return;}throw new AssertionError("Missing button or option: "+label);}
    ListView list(View root){if(root instanceof ListView)return (ListView)root;if(root instanceof ViewGroup){ViewGroup g=(ViewGroup)root;for(int i=0;i<g.getChildCount();i++){ListView v=list(g.getChildAt(i));if(v!=null)return v;}}return null;}
    int value(String method){return (Integer)call(a,method,new Class[]{});}
    Object create(String name,Class<?>[] types,Object... args){try{Class<?> c=a.getClassLoader().loadClass("org.simpleweather.app."+name);Constructor<?> ctor=c.getDeclaredConstructor(types);ctor.setAccessible(true);return ctor.newInstance(args);}catch(Exception e){throw new RuntimeException(e);}}
    void closeDialogs(){for(View root:WindowInspector.getGlobalWindowViews())if(root!=a.getWindow().getDecorView()&&root.isShown()){root.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BACK));root.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BACK));}}
    String label(String key){int id=a.getResources().getIdentifier(key,"string",a.getPackageName());return a.getString(id);}
    View root(){return a.getWindow().getDecorView();}
    Object screen(){return field(a,"randomChoice");}
    Object model(){return field(screen(),"model");}
    List<?> state(String key){return (List<?>)field(model(),key);}
    String json(){return call(model(),"json",new Class[]{}).toString();}
    void tapKey(View v,String key){tap(v,label(key));}
    View dialogKey(String key){return dialog(label(key));}
    ArrayList<EditText> inputs(View v){ArrayList<EditText> out=new ArrayList<>();collect(v,out);return out;}
    void collect(View v,ArrayList<EditText> out){if(v instanceof EditText)out.add((EditText)v);if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)collect(((ViewGroup)v).getChildAt(i),out);}
    int used(){return ((Set<?>)field(model(),"used")).size();}
    void recreate(){ActivityMonitor m=new ActivityMonitor(a.getClass().getName(),null,false);addMonitor(m);ui(()->a.recreate());Activity next=waitForMonitorWithTimeout(m,8000);removeMonitor(m);check(next!=null,"Activity recreation completed");a=next;waitForIdleSync();}

    Object staticCall(String cls,String method,Class<?>[] types,Object...args){try{Class<?> c=a.getClassLoader().loadClass("org.simpleweather.app."+cls);Method m=c.getDeclaredMethod(method,types);m.setAccessible(true);return m.invoke(null,args);}catch(Exception e){throw new RuntimeException(e);}}
    void change(String selection,String expected){
        ActivityMonitor monitor=new ActivityMonitor(a.getClass().getName(),null,false);addMonitor(monitor);
        ui(()->tap(dialogKey("language_title"),selection));Activity next=waitForMonitorWithTimeout(monitor,8000);removeMonitor(monitor);
        check(next!=null,"Language switch recreates activity");a=next;waitForIdleSync();
        ui(()->{
            check(a.getResources().getConfiguration().getLocales().get(0).getLanguage().equals(expected),"Activity resource locale");
            check((Integer)field(a,"tab")==5,"Current tab retained");
            check(field(a,"astronomyTime").toString().equals("2026-10-31T12:34:00Z"),"Astronomy time retained");
            check(text(dialogKey("language_title"),"English")!=null&&text(dialogKey("language_title"),"简体中文")!=null,"Language selector remains open with language autonyms");
            check(prefix(root(),expected.equals("en")?"Daidai Toolbox · Earthquakes":"呆呆工具箱 · 地震")!=null,"Header changes language");
            check(((String[])staticCall("GpsScreen","sectionNames",new Class[]{}))[0].equals(label("settings_compass")),"Status sections not stale");
            check(((String[])staticCall("AstronomyScreen","sectionNames",new Class[]{}))[0].equals(label("astronomy_106")),"Astronomy sections not stale");
            check(((String[])staticCall("EarthquakeScreen","dayNames",new Class[]{}))[0].equals(label("earthquake_004")),"Earthquake time choices not stale");
            check(staticCall("EarthquakeAgencies","name",new Class[]{String.class},"GFZ").equals(label("earthquake_agencies_011")),"Agency names not stale");
            check(staticCall("StatusMath","direction",new Class[]{double.class},0d).equals(label("astronomy_026")),"Compass directions not stale");
            check(staticCall("EarthquakeInfo","wolfxNotice",new Class[]{}).equals(label("earthquake_info_001")),"Source notice not stale");
            check(prefs("status_places").getString("places","").contains("测试地点 Alpha"),"Saved place name unchanged");
            check(prefs("toolbox").getString("astro_selected","").equals("织女一"),"Saved celestial identity unchanged");
            check((Integer)field(field(a,"earthquakes"),"shown")==90,"Expanded earthquake count retained");
            tapKey(dialogKey("language_title"),"action_back");check(dialogKey("settings_014")!=null,"Back to Global settings");
            tapKey(dialogKey("settings_014"),"action_back");check(dialogKey("action_settings")!=null,"Back to main settings");
            tapKey(dialogKey("action_settings"),"action_close");
        });
    }
    @Override public void onCreate(Bundle b){super.onCreate(b);start();}
    @Override public void onStart(){Bundle result=new Bundle();int code=Activity.RESULT_OK;
        try{
            for(String group:new String[]{"toolbox","weather","timetable","status_places","status_routes","random_choice"})before.put(group,new HashMap<>(prefs(group).getAll()));
            prefs("toolbox").edit().putString("app_language","zh-CN").putInt("seen_version",Integer.MAX_VALUE).putInt("last_tab",5).putBoolean("tab_show_5",true).putBoolean("tab_show_4",true).putBoolean("tab_show_3",true).putString("astro_selected","织女一").putLong("quake_interval",0).commit();
            for(int id=0;id<9;id++)prefs("toolbox").edit().putBoolean("status_show_"+id,true).commit();
            prefs("status_places").edit().putString("places","[{\"id\":\"language-test\",\"name\":\"测试地点 Alpha\",\"lon\":120.6,\"lat\":31.3,\"created\":1}]").commit();
            a=startActivitySync(new Intent().setClassName(getTargetContext(),"org.simpleweather.app.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();
            ui(()->{
                field(a,"astronomyTime",java.time.Instant.parse("2026-10-31T12:34:00Z"));field(field(a,"earthquakes"),"shown",90);
                call(a,"settings",new Class[]{});tapKey(dialogKey("action_settings"),"settings_014");tapKey(dialogKey("settings_014"),"language_title");
            });
            change("English","en");
            ui(()->{
                call(a,"switchTab",new Class[]{int.class},2);check(text(root(),"Current location")!=null&&text(root(),"Places")!=null,"English status headings");
                call(a,"switchTab",new Class[]{int.class},1);Object schedule=field(a,"schedule");call(schedule,"addCourse",new Class[]{});check(prefix(dialog("Add course"),"Course color · Blue")!=null,"English course dialog");tap(dialog("Add course"),"Cancel");
                call(a,"switchTab",new Class[]{int.class},4);check(text(root(),label("astronomy_106"))!=null,"English astronomy headings");
                call(a,"switchTab",new Class[]{int.class},3);check(prefix(root(),"Daidai Toolbox · "+label("tab_weather_map"))!=null,"English map heading");
                call(a,"switchTab",new Class[]{int.class},6);check(prefix(root(),"Daidai Toolbox · "+label("tab_random"))!=null,"English draw heading");
                call(a,"switchTab",new Class[]{int.class},5);call(a,"settings",new Class[]{});tapKey(dialogKey("action_settings"),"settings_014");tapKey(dialogKey("settings_014"),"language_title");
            });
            change("简体中文","zh");
            ui(()->{call(a,"settings",new Class[]{});tapKey(dialogKey("action_settings"),"settings_014");tapKey(dialogKey("settings_014"),"language_title");});
            String sys=android.content.res.Resources.getSystem().getConfiguration().getLocales().get(0).getLanguage();
            change("跟随系统（默认）",sys.equals("en")?"en":"zh");
            // Persisted selection survives a separate activity launch in the same process.
            ui(()->a.finish());waitForIdleSync();
            a=startActivitySync(new Intent().setClassName(getTargetContext(),"org.simpleweather.app.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();
            ui(()->{
                check(prefs("toolbox").getString("app_language","").equals("system"),"Language preference persisted across relaunch");
                check(text(root(),label("app_name")+" · "+label("tab_earthquake"))!=null,"Relaunched heading localized");
                call(a,"updates",new Class[]{});check(prefix(dialogKey("updates_title"),"2.0.0 ·")!=null,"New version entry present");tapKey(dialogKey("updates_title"),"action_close");
            });

            // Import the complete configuration with an English preference and real saved data.
            Map<String,Map<String,?>> groups=new HashMap<>();for(String group:before.keySet())groups.put(group,new HashMap<>(prefs(group).getAll()));
            JSONObject exported=(JSONObject)staticCall("ConfigurationData","encode",new Class[]{Map.class,byte[].class},groups,null);
            exported.getJSONObject("toolbox").put("app_language",new JSONObject().put("type","string").put("value","en"));
            Object document=staticCall("ConfigurationData","parse",new Class[]{String.class},exported.toString());
            ActivityMonitor importMonitor=new ActivityMonitor(a.getClass().getName(),null,false);addMonitor(importMonitor);
            ui(()->call(field(a,"configuration"),"apply",new Class[]{document.getClass()},document));
            Activity imported=waitForMonitorWithTimeout(importMonitor,8000);removeMonitor(importMonitor);check(imported!=null,"Configuration import relaunches activity");a=imported;waitForIdleSync();
            ui(()->{
                check(a.getResources().getConfiguration().getLocales().get(0).getLanguage().equals("en"),"Imported language takes effect in activity");
                check(prefix(root(),"Daidai Toolbox · Weather")!=null,"Imported language reaches page header");
                check(staticCall("StatusMath","direction",new Class[]{double.class},0d).equals(label("astronomy_026")),"Imported language reaches model text");
                check(prefs("status_places").getString("places","").contains("测试地点 Alpha"),"Import retains user's original place name");
                check(!prefs("toolbox").getBoolean("location_continuous",true)&&!prefs("toolbox").getBoolean("status_camera_background",true),"Import does not resume hardware sampling");
            });
            result.putString("stream","\nPASS: "+checks+" bilingual language switching UI checks.\n");
        }catch(Throwable e){code=Activity.RESULT_CANCELED;result.putString("stream","\nFAIL: "+android.util.Log.getStackTraceString(e));}
        finally{if(a!=null)ui(()->a.finish());waitForIdleSync();for(Map.Entry<String,Map<String,?>> g:before.entrySet())restore(prefs(g.getKey()),g.getValue());}finish(code,result);
    }
    void restore(SharedPreferences p,Map<String,?> values){SharedPreferences.Editor e=p.edit().clear();for(Map.Entry<String,?> x:values.entrySet()){Object v=x.getValue();String k=x.getKey();if(v instanceof Boolean)e.putBoolean(k,(Boolean)v);else if(v instanceof Integer)e.putInt(k,(Integer)v);else if(v instanceof Long)e.putLong(k,(Long)v);else if(v instanceof Float)e.putFloat(k,(Float)v);else if(v instanceof String)e.putString(k,(String)v);else if(v instanceof Set)e.putStringSet(k,(Set<String>)v);}e.commit();}
}
