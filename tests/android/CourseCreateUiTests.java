package org.simpleweather.qa;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/** Exercises the actual toolbar action and repeated course creation on Android. */
public final class CourseCreateUiTests extends Instrumentation {
    private Activity activity;
    private ClassLoader loader;
    private SharedPreferences toolbox,timetable;
    private Map<String,?> oldToolbox,oldTimetable;
    private int checks;

    public void onCreate(Bundle state){super.onCreate(state);start();}
    private Class<?> type(String name)throws Exception{return Class.forName("org.daidai.toolbox."+name,true,loader);}
    private Object field(Object target,String name)throws Exception{Class<?> start=target instanceof Class?(Class<?>)target:target.getClass();Object owner=target instanceof Class?null:target;for(Class<?> c=start;c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(name);}
    private Object call(Object target,String name,Class<?>[] args,Object... values)throws Exception{for(Class<?> c=target.getClass();c!=null;c=c.getSuperclass())try{Method m=c.getDeclaredMethod(name,args);m.setAccessible(true);return m.invoke(target,values);}catch(NoSuchMethodException ignored){}throw new NoSuchMethodException(name);}
    private void ui(Runnable task){AtomicReference<Throwable> failure=new AtomicReference<>();runOnMainSync(()->{try{task.run();}catch(Throwable e){failure.set(e);}});if(failure.get()!=null)throw new RuntimeException(failure.get());waitForIdleSync();}
    private void check(boolean ok,String message){if(!ok)throw new AssertionError(message);checks++;}
    private String text(String key){int id=activity.getResources().getIdentifier(key,"string",activity.getPackageName());return activity.getString(id);}
    private View find(View root,String value,boolean clickable){if(root instanceof TextView&&value.contentEquals(((TextView)root).getText())&&(!clickable||root.isClickable()))return root;if(root instanceof ViewGroup){ViewGroup g=(ViewGroup)root;for(int i=0;i<g.getChildCount();i++){View v=find(g.getChildAt(i),value,clickable);if(v!=null)return v;}}return null;}
    private EditText input(AlertDialog dialog,String label){for(View v:all(dialog.getWindow().getDecorView()))if(v instanceof EditText&&label.contentEquals(v.getContentDescription()))return (EditText)v;return null;}
    private List<View> all(View root){List<View> result=new ArrayList<>();result.add(root);if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++)result.addAll(all(((ViewGroup)root).getChildAt(i)));return result;}
    private AlertDialog dialog(){try{for(Object item:new ArrayList<>(((Map<?,?>)field(type("ToolDialog"),"OPEN")).keySet()))if(item instanceof AlertDialog&&((AlertDialog)item).isShowing()&&input((AlertDialog)item,text("schedule_065"))!=null)return (AlertDialog)item;}catch(Exception e){throw new RuntimeException(e);}throw new AssertionError("Course editor did not open");}
    private void installSchedule()throws Exception{Object apps=field(activity,"apps");try(java.io.InputStream in=new java.io.FileInputStream("/data/local/tmp/internal-apps/schedule.ddapp")){call(apps,"install",new Class[]{java.io.InputStream.class,String.class,String.class},in,"schedule",null);}}
    private void addAndSave(String name,int dayIndex){AtomicReference<AlertDialog> opened=new AtomicReference<>();ui(()->{View add=find(activity.getWindow().getDecorView(),text("action_add_course"),true);check(add!=null,"Add course toolbar action is visible");add.performClick();opened.set(dialog());});waitForIdleSync();ui(()->{AlertDialog editor=opened.get();EditText title=input(editor,text("schedule_065"));check(title!=null,"new course name input is present");title.setText(name);for(View view:all(editor.getWindow().getDecorView()))if(view instanceof Spinner){((Spinner)view).setSelection(dayIndex);break;}Button save=editor.getButton(AlertDialog.BUTTON_POSITIVE);check(save!=null&&save.isEnabled(),"new course save action is available");check(save.performClick(),"new course save action accepted click");});SystemClock.sleep(150);waitForIdleSync();}
    public void onStart(){Bundle result=new Bundle();try{
        Context c=getTargetContext();toolbox=c.getSharedPreferences("toolbox",0);timetable=c.getSharedPreferences("timetable",0);oldToolbox=new HashMap<>(toolbox.getAll());oldTimetable=new HashMap<>(timetable.getAll());toolbox.edit().putString("app_language","zh-CN").putInt("seen_version",Integer.MAX_VALUE).putInt("last_tab",-1).commit();timetable.edit().clear().commit();loader=c.getClassLoader();
        activity=startActivitySync(new Intent().setClassName(c,"org.daidai.toolbox.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();installSchedule();ui(()->{try{call(activity,"switchTab",new Class[]{int.class},1);}catch(Exception e){throw new RuntimeException(e);}});
        addAndSave("课程新增回归 A",0);String firstRaw=timetable.getString("data","");check(!firstRaw.isEmpty(),"first course data is persisted after toolbar action; raw="+firstRaw);
        JSONObject first=new JSONObject(firstRaw);check(first.getJSONArray("courses").toString().contains("课程新增回归 A"),"first course is persisted after toolbar action; raw="+firstRaw);
        addAndSave("课程新增回归 B",1);String secondRaw=timetable.getString("data","");check(!secondRaw.isEmpty(),"second course data is persisted");JSONObject second=new JSONObject(secondRaw);check(second.getJSONArray("courses").toString().contains("课程新增回归 A")&&second.getJSONArray("courses").toString().contains("课程新增回归 B"),"consecutive course creation preserves both records");
        AtomicReference<AlertDialog> cancelled=new AtomicReference<>();ui(()->{View add=find(activity.getWindow().getDecorView(),text("action_add_course"),true);add.performClick();cancelled.set(dialog());cancelled.get().getButton(AlertDialog.BUTTON_NEGATIVE).performClick();});waitForIdleSync();check(!cancelled.get().isShowing(),"course editor cancels without crashing");
        result.putString("result","PASS: "+checks+" actual course creation UI checks");
    }catch(Throwable failure){result.putString("result","FAIL: "+android.util.Log.getStackTraceString(failure));}
    finally{if(activity!=null)ui(activity::finish);if(timetable!=null)restore(timetable,oldTimetable);if(toolbox!=null)restore(toolbox,oldToolbox);finish(Activity.RESULT_OK,result);}}
    private void restore(SharedPreferences preferences,Map<String,?> values){SharedPreferences.Editor editor=preferences.edit().clear();for(Map.Entry<String,?> row:values.entrySet()){Object v=row.getValue();if(v instanceof String)editor.putString(row.getKey(),(String)v);else if(v instanceof Boolean)editor.putBoolean(row.getKey(),(Boolean)v);else if(v instanceof Integer)editor.putInt(row.getKey(),(Integer)v);else if(v instanceof Long)editor.putLong(row.getKey(),(Long)v);else if(v instanceof Float)editor.putFloat(row.getKey(),(Float)v);else if(v instanceof Set)editor.putStringSet(row.getKey(),(Set<String>)v);}editor.commit();}
}
