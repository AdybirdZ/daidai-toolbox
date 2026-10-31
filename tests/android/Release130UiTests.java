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
public final class Release130UiTests extends Instrumentation {
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
    @Override public void onCreate(Bundle b){super.onCreate(b);start();}


    @Override public void onStart(){Bundle result=new Bundle();int code=Activity.RESULT_OK;
        try{
            for(String group:new String[]{"toolbox","weather","timetable","status_places","status_routes","random_choice"})before.put(group,new HashMap<>(prefs(group).getAll()));
            prefs("toolbox").edit().clear().putInt("seen_version",Integer.MAX_VALUE).putInt("last_tab",6).putLong("quake_interval",0).commit();prefs("random_choice").edit().clear().commit();
            a=startActivitySync(new Intent().setClassName(getTargetContext(),"org.simpleweather.app.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();
            ui(()->{
                check(text(root(),label("app_name")+" · "+label("tab_random"))!=null,"Localized header");
                check(((List<?>)call(a,"visibleTabs",new Class[]{})).equals(Arrays.asList(0,1,2,5,6)),"Default tabs append random selection and retain map/astronomy hidden");
                check(inputs(root()).size()==2,"Candidates and draw quantity are editable");
                inputs(root()).get(0).setText("A,B\nC,D,A");inputs(root()).get(1).setText("2");
                call(a,"settings",new Class[]{});View settings=dialogKey("action_settings");tapKey(settings,"tab_random");View choice=dialogKey("tab_random");
                Switch exclusion=(Switch)text(choice,label("random_remove")),animation=(Switch)text(choice,label("random_animation"));
                check(!exclusion.isChecked()&&animation.isChecked(),"Defaults: no exclusion, animation enabled");
                exclusion.performClick();animation.performClick();check(dialogKey("tab_random")==choice,"Changing options retains settings");
                check(exclusion.getThumbTintList()!=null,"Switch receives the fixed selection tint");tapKey(choice,"action_back");check(dialogKey("action_settings")==settings,"Random settings returns to parent settings");tapKey(settings,"action_close");
                tapKey(root(),"random_start");check(state("result").size()==2&&used()==2,"Draw returns two distinct candidates");check(!(Boolean)field(screen(),"animating"),"Animation disabled shows results immediately");
                tapKey(root(),"random_start");check(used()==4&&state("result").size()==2,"Second draw excludes previous items");String previous=json();tapKey(root(),"random_start");check(json().equals(previous),"Exhausted pool leaves progress and results unchanged");
                tapKey(root(),"random_copy");check(((android.content.ClipboardManager)a.getSystemService(Context.CLIPBOARD_SERVICE)).getPrimaryClip().getItemAt(0).getText().toString().equals(String.join("\n",(List<String>)state("result"))),"Copy returns actual final results");
                tapKey(root(),"random_save_list");View name=dialogKey("random_save_list");inputs(name).get(0).setText("Students");tapKey(name,"action_save");check(state("lists").size()==1,"Saved snapshot retained");check(text(root(),label("random_remaining"))!=null&&text(root(),label("random_lists"))!=null,"Remaining and saved-list actions are both visible");tapKey(root(),"random_remaining");View remaining=dialogKey("random_remaining_title");check(text(remaining,label("random_remaining_count"))!=null||text(remaining,label("random_remaining_empty"))!=null,"Remaining-items dialog opens");tapKey(remaining,"action_back");
                tapKey(root(),"random_lists");View saved=dialogKey("random_lists");tap(saved,a.getString(a.getResources().getIdentifier("random_list_entry","string",a.getPackageName()),"Students",4));View actions=dialog("Students");tapKey(actions,"random_rename");View rename=dialogKey("random_rename");inputs(rename).get(0).setText("Group");tapKey(rename,"action_save");check(dialogKey("random_lists")==saved,"Rename returns to saved list dialog");check(text(saved,a.getString(a.getResources().getIdentifier("random_list_entry","string",a.getPackageName()),"Group",4))!=null,"Renamed list updates in place");tapKey(saved,"action_back");
                tapKey(root(),"random_reset");View reset=dialogKey("random_reset");tapKey(reset,"action_cancel");
            });waitForIdleSync();ui(()->{check(used()==4,"Cancel reset preserves progress");tapKey(root(),"random_reset");tapKey(dialogKey("random_reset"),"random_confirm");
            });waitForIdleSync();ui(()->{check(used()==0&&state("result").isEmpty(),"Confirmed reset clears progress and result");
                prefs("toolbox").edit().putBoolean("random_animation",true).commit();tapKey(root(),"random_start");check((Boolean)field(screen(),"animating"),"Animated draw begins");check(state("result").size()==2&&used()==2,"Result committed before animation begins");
            });
            SystemClock.sleep(160);
            ui(()->{String expected=json();tapKey(root(),"random_reveal");check(!(Boolean)field(screen(),"animating")&&json().equals(expected),"Skip only ends presentation");tapKey(root(),"random_start");check((Boolean)field(screen(),"animating"),"Second animation starts");call(a,"switchTab",new Class[]{int.class},1);check(!(Boolean)field(screen(),"animating"),"Leaving tab cancels animation callbacks");call(a,"switchTab",new Class[]{int.class},6);check(used()==4&&state("result").size()==2,"Returning does not repeat the draw");});
            String expected=json();recreate();ui(()->{check((Integer)field(a,"tab")==6&&json().equals(expected),"Activity recreation preserves selected tab, draft, lists, result and pool");
                inputs(root()).get(0).setText("X,Y");tapKey(root(),"random_lists");View saved=dialogKey("random_lists");tap(saved,a.getString(a.getResources().getIdentifier("random_list_entry","string",a.getPackageName()),"Group",4));tapKey(dialog("Group"),"random_load");check(field(model(),"draft").equals("A\nB\nC\nD")&&used()==0,"Loading snapshot restores entries and resets progress");
                prefs("toolbox").edit().putBoolean("random_animation",false).commit();inputs(root()).get(1).setText("2");tapKey(root(),"random_start");
            });
            ui(()->{call(model(),"reset",new Class[]{});prefs("toolbox").edit().putBoolean("random_remove_drawn",false).putBoolean("random_animation",true).commit();tapKey(root(),"random_start");});
            String fixed=json();SystemClock.sleep(4700);ui(()->{check(!(Boolean)field(screen(),"animating")&&json().equals(fixed),"Animation decelerates and completes without redrawing");});
            animationLayout();
            configurationRoundTrip();
            ui(()->{call(a,"switchTab",new Class[]{int.class},6);String kept=json();call(a,"restoreDefaults",new Class[]{});check(json().equals(kept)&&state("lists").size()==1,"Restore defaults retains saved lists and progress");call(a,"switchTab",new Class[]{int.class},6);
                tapKey(root(),"random_lists");View saved=dialogKey("random_lists");tap(saved,a.getString(a.getResources().getIdentifier("random_list_entry","string",a.getPackageName()),"Group",4));tapKey(dialog("Group"),"random_delete");tapKey(dialogKey("random_delete"),"action_cancel");
            });waitForIdleSync();ui(()->{View saved=dialogKey("random_lists");check(state("lists").size()==1,"Cancel delete preserves list");tap(saved,a.getString(a.getResources().getIdentifier("random_list_entry","string",a.getPackageName()),"Group",4));tapKey(dialog("Group"),"random_delete");tapKey(dialogKey("random_delete"),"random_confirm");
            });waitForIdleSync();ui(()->{View saved=dialogKey("random_lists");check(state("lists").isEmpty()&&text(saved,label("random_no_lists"))!=null,"Confirm delete refreshes empty list state");tapKey(saved,"action_back");call(a,"updates",new Class[]{});View updates=dialogKey("settings_100");check(prefix(updates,"1.3.2 ·")!=null&&prefix(updates,"1.0.0 ·")!=null,"Current and historical updates retained");tapKey(updates,"action_close");
            });
            SystemClock.sleep(500);waitForIdleSync();Bitmap screenshot=getUiAutomation().takeScreenshot();if(screenshot!=null){try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(getTargetContext().getExternalFilesDir(null),"random-selection-130.png"))){screenshot.compress(Bitmap.CompressFormat.PNG,100,out);}screenshot.recycle();}
            result.putString("stream","\nPASS: "+checks+" random selection Android checks.\n");
        }catch(Throwable error){code=Activity.RESULT_CANCELED;result.putString("stream","\nFAIL: "+android.util.Log.getStackTraceString(error));}
        finally{if(a!=null)ui(()->a.finish());waitForIdleSync();for(Map.Entry<String,Map<String,?>> g:before.entrySet())restore(prefs(g.getKey()),g.getValue());}finish(code,result);
    }
    void animationLayout(){
        String draft=(String)field(model(),"draft"),quantity=(String)field(model(),"quantity");
        ui(()->{StringBuilder entries=new StringBuilder();for(int i=0;i<8;i++)entries.append(i).append(" ABCDEFGHIJKLMNOPQRSTUVWXYZ ABCDEFGHIJKLMNOPQRSTUVWXYZ ABCDEFGHIJKLMNOPQRSTUVWXYZ\n");inputs(root()).get(0).setText(entries.toString());inputs(root()).get(1).setText("8");tapKey(root(),"random_start");});
        SystemClock.sleep(180);waitForIdleSync();
        ui(()->{TextView animated=(TextView)field(screen(),"result"),reveal=(TextView)field(screen(),"skip");
            check((Boolean)field(screen(),"animating")&&animated.getText().toString().split("\n").length==8,"Eight items animate simultaneously");
            check(animated.getLineCount()>8,"Long candidates wrap during animation");
            check(animated.getHeight()-animated.getCompoundPaddingTop()-animated.getCompoundPaddingBottom()>=animated.getLayout().getHeight(),"All animated lines fit without clipping");
            check(reveal.getVisibility()==View.VISIBLE&&reveal.getTop()>=animated.getBottom(),"Reveal button is laid out below every animated line");
            tapKey(root(),"random_reveal");check(animated.getText().toString().split("\n").length==8,"Revealing retains every drawn item");
            inputs(root()).get(0).setText(draft);inputs(root()).get(1).setText(quantity);call(model(),"reset",new Class[]{});call(screen(),"flush",new Class[]{});
            check(!label("battery_optimization_on").contains("豁免")&&!label("battery_optimization_off").contains("豁免"),"Battery optimization uses enabled and disabled wording");
        });
    }
    Object transfer;
    void idleTransfer(){for(int i=0;i<200;i++){if(!(Boolean)field(transfer,"busy"))return;SystemClock.sleep(50);}throw new AssertionError("Transfer timeout");}
    View await(String key){for(int i=0;i<160;i++){AtomicReference<View> view=new AtomicReference<>();ui(()->{try{view.set(dialogKey(key));}catch(AssertionError ignored){}});if(view.get()!=null)return view.get();SystemClock.sleep(50);}throw new AssertionError("Missing dialog "+key);}
    void deliver(java.io.File file){ui(()->{field(transfer,"busy",true);call(a,"onActivityResult",new Class[]{int.class,int.class,Intent.class},71,Activity.RESULT_OK,new Intent().setData(android.net.Uri.fromFile(file)));});}
    void importFile(java.io.File file){ActivityMonitor monitor=new ActivityMonitor(a.getClass().getName(),null,false);addMonitor(monitor);deliver(file);View confirmation=await("config_import_title");ui(()->tapKey(confirmation,"config_confirm"));idleTransfer();Activity next=waitForMonitorWithTimeout(monitor,8000);removeMonitor(monitor);check(next!=null,"Import recreates models");a=next;waitForIdleSync();transfer=field(a,"configuration");}
    void configurationRoundTrip()throws Exception{
        transfer=field(a,"configuration");java.io.File file=new java.io.File(a.getCacheDir(),"random-config.json");
        ActivityMonitor monitor=new ActivityMonitor(){@Override public ActivityResult onStartActivity(Intent intent){if(Intent.ACTION_CREATE_DOCUMENT.equals(intent.getAction()))return new ActivityResult(Activity.RESULT_OK,new Intent().setData(android.net.Uri.fromFile(file)));return null;}};addMonitor(monitor);
        ui(()->call(a,"exportConfiguration",new Class[]{}));idleTransfer();removeMonitor(monitor);
        JSONObject doc=new JSONObject(new String(java.nio.file.Files.readAllBytes(file.toPath()),java.nio.charset.StandardCharsets.UTF_8));String exported=doc.getJSONObject("random_choice").getJSONObject("data").getString("value");check(new JSONObject(exported).getJSONArray("lists").length()==1,"Actual exported file includes saved lists");
        ui(()->{inputs(root()).get(0).setText("Changed");call(screen(),"flush",new Class[]{});});importFile(file);check(json().equals(exported),"Imported lists, results and progress survive old Activity destruction");
        doc.remove("random_choice");java.nio.file.Files.write(file.toPath(),doc.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));importFile(file);check(json().equals(exported),"Legacy import preserves current random selection data");
        JSONObject invalid=new JSONObject(doc.toString());invalid.put("random_choice",new JSONObject().put("data",new JSONObject().put("type","string").put("value","{}")));java.nio.file.Files.write(file.toPath(),invalid.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));deliver(file);idleTransfer();check(json().equals(exported),"Invalid random data rejected without overwriting current state");
    }
    void restore(SharedPreferences p,Map<String,?> values){SharedPreferences.Editor e=p.edit().clear();for(Map.Entry<String,?> x:values.entrySet()){Object v=x.getValue();String k=x.getKey();if(v instanceof Boolean)e.putBoolean(k,(Boolean)v);else if(v instanceof Integer)e.putInt(k,(Integer)v);else if(v instanceof Long)e.putLong(k,(Long)v);else if(v instanceof Float)e.putFloat(k,(Float)v);else if(v instanceof String)e.putString(k,(String)v);else if(v instanceof Set)e.putStringSet(k,(Set<String>)v);}e.commit();}
}
