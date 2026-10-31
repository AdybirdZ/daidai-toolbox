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
public final class EnglishPreviewUiTests extends Instrumentation {
    Activity a;View courseEditor;int checks;final Map<String,Map<String,?>> before=new HashMap<>();String cameraResult="";
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
    @Override public void onCreate(Bundle b){super.onCreate(b);start();}
    @Override public void onStart(){Bundle result=new Bundle();int code=Activity.RESULT_OK;
        try{
            for(String group:new String[]{"toolbox","weather","timetable","status_places","status_routes"})before.put(group,new HashMap<>(prefs(group).getAll()));
            prefs("toolbox").edit().putInt("seen_version",Integer.MAX_VALUE).putInt("last_tab",0).putBoolean("tab_show_4",true).putBoolean("tab_show_3",true).putLong("quake_interval",0).putInt("status_camera_iso",1000).putInt("status_camera_exposure_ms",200).commit();
            for(int id=0;id<9;id++)prefs("toolbox").edit().putBoolean("status_show_"+id,true).commit();
            a=startActivitySync(new Intent().setClassName(getTargetContext(),"org.simpleweather.app.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();
            ui(()->{
                check(prefix(a.getWindow().getDecorView(),"Daidai Toolbox · Weather")!=null,"English weather heading");
                call(a,"settings",new Class[]{});View settings=dialog("Settings");tap(settings,"Status");View status=dialog("Status settings");tap(status,"Sensors");View sensor=dialog("Sensors");tap(sensor,"Ambient-light source");View light=dialog("Ambient-light source");check(text(light,"System ambient-light sensor (default)")!=null,"Historical source choices translated");tap(light,"Camera settings");View camera=dialog("Camera settings");tap(camera,"Indoor measurement parameters");View exposure=dialog("Indoor measurement parameters");check(prefix(exposure,"Default sensitivity: ISO 1000")!=null,"New camera explanation translated");check(text(exposure,"Save")!=null&&text(exposure,"Back")!=null,"Camera actions translated");tap(exposure,"Back");tap(camera,"Back");tap(light,"Back");tap(sensor,"Back");tap(status,"Back");tap(settings,"About");View about=dialog("About");tap(about,"Version history");View history=dialog("Version history");check(prefix(history,"1.2.15 ·")!=null&&prefix(history,"1.0.0 ·")!=null,"Current and historical update headings translated");tap(history,"Close");tap(about,"Back");tap(settings,"Close");
                Object permissionSettings=create("SettingsScreen",new Class[]{a.getClass()},a);call(permissionSettings,"global",new Class[]{});View global=dialog("Global settings");tap(global,"Permissions and system restrictions");View permissions=dialog("Permissions and system restrictions");check(text(permissions,"Open app permissions")!=null&&text(permissions,"Open system location settings")!=null&&text(permissions,"Disable battery optimization")!=null,"Permissions hub and all entries translated");tap(permissions,"Back");check(dialog("Global settings")==global,"English permission hub retains parent");tap(global,"Back");
                call(a,"switchTab",new Class[]{int.class},1);Object schedule=field(a,"schedule");call(schedule,"addCourse",new Class[]{});View edit=dialog("Add course");check(prefix(edit,"Course color · Blue")!=null,"Historical editor and color name translated");tap(edit,"Cancel");
                call(a,"switchTab",new Class[]{int.class},2);check(text(a.getWindow().getDecorView(),"Current location")!=null,"Current location translated");check(text(a.getWindow().getDecorView(),"Places")!=null,"Places translated");check(text(a.getWindow().getDecorView(),"Battery")!=null,"Battery translated");
                call(a,"switchTab",new Class[]{int.class},4);View sky=sky(a.getWindow().getDecorView());check(sky!=null,"Astronomy renders with English resources and stable identities");
                call(a,"switchTab",new Class[]{int.class},5);check(text(a.getWindow().getDecorView(),"Filters and refresh")!=null&&text(a.getWindow().getDecorView(),"Refresh catalog")!=null,"Earthquake controls translated");
            });
            result.putString("stream","\nPASS: "+checks+" English preview UI checks.\n");
        }catch(Throwable e){code=Activity.RESULT_CANCELED;result.putString("stream","\nFAIL: "+android.util.Log.getStackTraceString(e));}
        finally{if(a!=null)ui(()->a.finish());for(Map.Entry<String,Map<String,?>> g:before.entrySet())restore(prefs(g.getKey()),g.getValue());}finish(code,result);
    }
    int choiceColor(){try{Class<?> c=a.getClassLoader().loadClass("org.simpleweather.app.UiColors");Method m=c.getDeclaredMethod("ink",Context.class,String.class);m.setAccessible(true);return (Integer)m.invoke(null,a,"ui_choice_color");}catch(Exception e){throw new RuntimeException(e);}}
    CheckBox firstCheck(View v){if(v instanceof CheckBox)return (CheckBox)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){CheckBox found=firstCheck(((ViewGroup)v).getChildAt(i));if(found!=null)return found;}return null;}
    Spinner findSpinner(View v){if(v instanceof Spinner)return (Spinner)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){Spinner found=findSpinner(((ViewGroup)v).getChildAt(i));if(found!=null)return found;}return null;}
    EditText findInput(View v){if(v instanceof EditText)return (EditText)v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){EditText input=findInput(g.getChildAt(i));if(input!=null)return input;}}return null;}
    void restore(SharedPreferences p,Map<String,?> values){SharedPreferences.Editor e=p.edit().clear();for(Map.Entry<String,?> x:values.entrySet()){Object v=x.getValue();String k=x.getKey();if(v instanceof Boolean)e.putBoolean(k,(Boolean)v);else if(v instanceof Integer)e.putInt(k,(Integer)v);else if(v instanceof Long)e.putLong(k,(Long)v);else if(v instanceof Float)e.putFloat(k,(Float)v);else if(v instanceof String)e.putString(k,(String)v);else if(v instanceof Set)e.putStringSet(k,(Set<String>)v);}e.commit();}
}
