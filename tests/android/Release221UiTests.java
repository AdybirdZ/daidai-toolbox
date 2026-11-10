package org.simpleweather.qa;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.inspector.WindowInspector;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/** Covers legacy shortcut re-entry, settings ancestry, editor drafts, and lifecycle safety. */
public final class Release221UiTests extends Instrumentation {
    private Activity activity;
    private int checks;
    private String language="zh-CN";
    private final Map<String,Map<String,?>> before=new HashMap<>();
    private Object field(Object target,String key){try{Field f=target.getClass().getDeclaredField(key);f.setAccessible(true);return f.get(target);}catch(Exception e){throw new RuntimeException(e);}}
    private Object call(Object target,String key,Class<?>[] types,Object...args){try{Method m=target.getClass().getDeclaredMethod(key,types);m.setAccessible(true);return m.invoke(target,args);}catch(Exception e){throw new RuntimeException(e);}}
    private void ui(Runnable work){AtomicReference<Throwable> failure=new AtomicReference<>();runOnMainSync(()->{try{work.run();}catch(Throwable e){failure.set(e);}});if(failure.get()!=null)throw new RuntimeException(failure.get());}
    private void check(boolean condition,String why){checks++;if(!condition)throw new AssertionError(why);}
    private String label(String key){return activity.getString(activity.getResources().getIdentifier(key,"string",activity.getPackageName()));}
    private TextView text(View root,String value){if(root instanceof TextView&&value.contentEquals(((TextView)root).getText()))return (TextView)root;if(root instanceof ViewGroup){ViewGroup g=(ViewGroup)root;for(int n=0;n<g.getChildCount();n++){TextView found=text(g.getChildAt(n),value);if(found!=null)return found;}}return null;}
    private View dialog(String key){int title=getTargetContext().getResources().getIdentifier("alertTitle","id","android");for(View root:WindowInspector.getGlobalWindowViews()){View heading=root.findViewById(title);if(root.isShown()&&heading instanceof TextView&&label(key).contentEquals(((TextView)heading).getText()))return root;}throw new AssertionError("Missing dialog "+key);}
    private void tap(View root,String key){TextView target=text(root,label(key));check(target!=null,"Control exists: "+key);target.performClick();}
    private void inputs(View root,ArrayList<EditText> out){if(root instanceof EditText)out.add((EditText)root);if(root instanceof ViewGroup){ViewGroup g=(ViewGroup)root;for(int n=0;n<g.getChildCount();n++)inputs(g.getChildAt(n),out);}}
    private ScrollView scroll(View root){if(root instanceof ScrollView)return (ScrollView)root;if(root instanceof ViewGroup){ViewGroup g=(ViewGroup)root;for(int n=0;n<g.getChildCount();n++){ScrollView found=scroll(g.getChildAt(n));if(found!=null)return found;}}return null;}
    private SharedPreferences prefs(String group){return getTargetContext().getSharedPreferences(group,0);}
    private void homeAndReturn(boolean legacyShortcut){
        ui(()->activity.startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
        SystemClock.sleep(250);waitForIdleSync();
        ui(()->{
            check(!activity.hasWindowFocus(),"Home actually leaves the app window");
            Object gps=field(activity,"gps"),hardware=field(gps,"hardware");
            check(!(Boolean)field(hardware,"active"),"Foreground sensors are paused while backgrounded");
            Intent enter=new Intent(Intent.ACTION_MAIN).setClassName(getTargetContext(),"org.simpleweather.app.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            // Existing pinned shortcuts lack SINGLE_TOP. Manifest compatibility must retain them too.
            if(legacyShortcut)enter.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);else enter.addCategory(Intent.CATEGORY_LAUNCHER);
            getTargetContext().startActivity(enter);
        });
        SystemClock.sleep(300);waitForIdleSync();
        ui(()->check(!activity.isDestroyed()&&!activity.isFinishing(),"Re-entry retains the same live Activity"));
    }
    @Override public void onCreate(Bundle args){super.onCreate(args);if(args!=null)language=args.getString("language","zh-CN");start();}
    @Override public void onStart(){Bundle result=new Bundle();int status=Activity.RESULT_OK;
        try{
            for(String group:new String[]{"toolbox","random_choice"})before.put(group,new HashMap<>(prefs(group).getAll()));
            prefs("toolbox").edit().putString("app_language",language).putInt("seen_version",Integer.MAX_VALUE).putInt("last_tab",0).putBoolean("location_continuous",false).putBoolean("status_camera_background",false).commit();
            activity=startActivitySync(new Intent().setClassName(getTargetContext(),"org.simpleweather.app.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();
            final View[] pages=new View[4];
            ui(()->{
                call(activity,"settings",new Class[]{});pages[0]=dialog("action_settings");tap(pages[0],"settings_014");pages[1]=dialog("settings_014");
                check(text(pages[1],label("keep_screen_awake_title"))==null,"Awake setting is grouped under permissions");
                tap(pages[1],"settings_permissions");pages[2]=dialog("settings_permissions");
                check(text(pages[2],label("keep_screen_awake_title"))!=null,"Awake setting is available in permission hub");
                tap(pages[2],"permissions_shortcut_settings");pages[3]=dialog("permissions_shortcut_settings");
                check(text(pages[3],label("permissions_shortcut_note"))!=null,"Shortcut limitations are explained without a false permission switch");
            });
            homeAndReturn(true);
            ui(()->{
                check(dialog("permissions_shortcut_settings")==pages[3],"Legacy shortcut retains leaf settings window");tap(pages[3],"action_back");
                check(dialog("settings_permissions")==pages[2],"Back retains permissions parent");tap(pages[2],"action_back");
                check(dialog("settings_014")==pages[1],"Back retains global settings parent");tap(pages[1],"action_back");tap(pages[0],"action_close");
                call(activity,"switchTab",new Class[]{int.class},7);Object todo=field(activity,"todo");Class<?> item;try{item=activity.getClassLoader().loadClass("org.simpleweather.app.TodoModel$Item");}catch(Exception e){throw new RuntimeException(e);}call(todo,"edit",new Class[]{item},new Object[]{null});
            });
            final View[] editor=new View[1];final EditText[] title=new EditText[1];final int[] offset=new int[1];
            ui(()->{editor[0]=dialog("todo_add");ArrayList<EditText> fields=new ArrayList<>();inputs(editor[0],fields);title[0]=fields.get(0);title[0].setText("unsaved lifecycle draft");title[0].setSelection(7);ScrollView s=scroll(editor[0]);s.scrollTo(0,300);offset[0]=s.getScrollY();});
            homeAndReturn(false);
            ui(()->{
                check(dialog("todo_add")==editor[0],"Launcher return retains unsaved editor window");
                check(title[0].getText().toString().equals("unsaved lifecycle draft")&&title[0].getSelectionStart()==7,"Draft text and caret survive Home");
                check(scroll(editor[0]).getScrollY()==offset[0],"Editor scroll position survives Home");tap(editor[0],"action_cancel");
                call(activity,"switchTab",new Class[]{int.class},2);
            });
            homeAndReturn(true);
            ui(()->{check((Integer)field(activity,"tab")==2,"Status tab survives shortcut return");check((Boolean)field(field(field(activity,"gps"),"hardware"),"active"),"Foreground sensors resume only on active status page");});
            result.putString("stream","\nPASS: "+checks+" navigation and permissions checks ("+language+"). Process-death form restoration is outside this fix.\n");
        }catch(Throwable e){status=Activity.RESULT_CANCELED;result.putString("stream","\nFAIL: "+android.util.Log.getStackTraceString(e));}
        finally{
            if(activity!=null&&!activity.isFinishing())ui(()->activity.finish());waitForIdleSync();
            for(Map.Entry<String,Map<String,?>> group:before.entrySet()){SharedPreferences.Editor e=prefs(group.getKey()).edit().clear();for(Map.Entry<String,?> v:group.getValue().entrySet()){Object x=v.getValue();String k=v.getKey();if(x instanceof Boolean)e.putBoolean(k,(Boolean)x);else if(x instanceof Integer)e.putInt(k,(Integer)x);else if(x instanceof Long)e.putLong(k,(Long)x);else if(x instanceof Float)e.putFloat(k,(Float)x);else if(x instanceof String)e.putString(k,(String)x);else if(x instanceof Set)e.putStringSet(k,(Set<String>)x);}e.commit();}
        }
        finish(status,result);
    }
}
