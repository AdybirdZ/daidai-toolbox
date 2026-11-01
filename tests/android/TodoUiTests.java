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
public final class TodoUiTests extends Instrumentation {
    String language="zh-CN";Activity a;int checks;final Map<String,Map<String,?>> before=new HashMap<>();
    Object field(Object o,String name){try{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}catch(Exception e){throw new RuntimeException(e);}}
    void field(Object o,String name,Object value){try{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.set(o,value);}catch(Exception e){throw new RuntimeException(e);}}
    Object call(Object o,String name,Class<?>[] types,Object... args){try{Method m=o.getClass().getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(o,args);}catch(Exception e){throw new RuntimeException(e);}}
    void ui(Runnable task){AtomicReference<Throwable> error=new AtomicReference<>();runOnMainSync(()->{try{task.run();}catch(Throwable e){error.set(e);}});if(error.get()!=null)throw new RuntimeException(error.get());waitForIdleSync();}
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

    void tapKey(View v,String key){tap(v,label(key));}
    View dialogKey(String key){return dialog(label(key));}
    EditText edit(View root,String key){String desc=label(key);if(root instanceof EditText&&desc.contentEquals(root.getContentDescription()))return (EditText)root;if(root instanceof ViewGroup)for(int n=0;n<((ViewGroup)root).getChildCount();n++){EditText e=edit(((ViewGroup)root).getChildAt(n),key);if(e!=null)return e;}return null;}
    Object staticCall(String cls,String name,Class<?>[] types,Object...args){try{Class<?> c=a.getClassLoader().loadClass("org.simpleweather.app."+cls);Method m=c.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(null,args);}catch(Exception e){throw new RuntimeException(e);}}
    JSONObject data(){try{return new JSONObject(prefs("todos").getString("data","{\"version\":1,\"items\":[]}"));}catch(Exception e){throw new RuntimeException(e);}}
    void data(JSONObject doc){prefs("todos").edit().putString("data",doc.toString()).commit();}
    void close(){try{staticCall("ToolDialog","closeAll",new Class[]{});}catch(Exception ignored){}}
    Object todo(){return field(a,"todo");}
    void scheduler(boolean dispatch){staticCall("TodoScheduler","refresh",new Class[]{Context.class,boolean.class},a,dispatch);}
    android.service.notification.StatusBarNotification notification(String tag){for(android.service.notification.StatusBarNotification n:a.getSystemService(NotificationManager.class).getActiveNotifications())if(tag.equals(n.getTag()))return n;return null;}
    JSONObject task(String id,boolean due)throws Exception{java.time.LocalDate today=java.time.LocalDate.now();long now=System.currentTimeMillis();return new JSONObject().put("id",id).put("title","Notify Alpha").put("note","Reminder body").put("category","Work").put("date",today.toString()).put("until",today.toString()).put("allDay",true).put("done",false).put("pin",true).put("remindEnd",false).put("priority",1).put("color",0xff246bce).put("repeat",0).put("weekMask",0).put("advance",due?0:-1).put("created",now-3600000).put("updated",now-3600000).put("completedThrough","").put("slots",new JSONArray()).put("parts",new JSONArray());}
    void receive(Intent intent){staticCall("TodoScheduler","receive",new Class[]{Context.class,Intent.class},a,intent);}
    void waitActions(){SystemClock.sleep(250);waitForIdleSync();}
    void edits(View v,java.util.List<EditText> fields){if(v instanceof EditText)fields.add((EditText)v);else if(v instanceof ViewGroup)for(int n=0;n<((ViewGroup)v).getChildCount();n++)edits(((ViewGroup)v).getChildAt(n),fields);}
    void setRange(View v,int h,int m,int h2,int m2){java.util.List<EditText> fields=new ArrayList<>();edits(v,fields);check(fields.size()==4,"Range editor has four direct numeric fields");int[] values={h,m,h2,m2};for(int n=0;n<4;n++)fields.get(n).setText(String.valueOf(values[n]));}
    void spinner(View v,String key,int value){String desc=label(key);if(v instanceof Spinner&&desc.contentEquals(v.getContentDescription())){((Spinner)v).setSelection(value);return;}if(v instanceof ViewGroup)for(int n=0;n<((ViewGroup)v).getChildCount();n++)spinner(((ViewGroup)v).getChildAt(n),key,value);}
    JSONObject v2(String id)throws Exception{return task(id,false).put("completedAt",0).put("completedDay","").put("previousThrough","").put("deleteAfterDays",0);}
    void completionManagement()throws Exception{
        ui(()->{
            close();call(a,"switchTab",new Class[]{int.class},7);tapKey(root(),"todo_add");View editor=dialogKey("todo_add");edit(editor,"todo_title").setText("Retention test");
            Switch auto=(Switch)text(editor,label("todo_auto_delete"));check(!auto.isChecked(),"Per-item deletion defaults off");auto.performClick();check(edit(editor,"todo_delete_days").isShown(),"Enabling retention exposes day input");edit(editor,"todo_delete_days").setText("2");
            tap(editor,"09:00–10:00");View range=dialogKey("todo_edit_slot");setRange(range,23,0,24,0);range.findViewById(android.R.id.button1).performClick();
            tapKey(editor,"todo_add_slot");range=dialogKey("todo_add_slot");setRange(range,23,30,24,0);range.findViewById(android.R.id.button1).performClick();check(dialogKey("todo_add_slot")!=null,"Overlapping range rejected in place");range.findViewById(android.R.id.button2).performClick();
            editor.findViewById(android.R.id.button1).performClick();check(data().optJSONArray("items").optJSONObject(2).optInt("deleteAfterDays")==2,"Per-item retention stored");
            edit(root(),"todo_search").setText("Retention test");tapKey(root(),"todo_complete");long at=data().optJSONArray("items").optJSONObject(2).optLong("completedAt");check(at>0,"UI completion records actual instant");
            spinner(root(),"todo_filter",1);field(todo(),"filter",1);call(todo(),"refreshList",new Class[]{});check(text(root(),"Retention test")!=null,"Today keeps completed task visible");check(prefix(root(),java.time.LocalDate.now().toString()+" ")!=null,"Schedule shows full numeric UTC timestamps");check(prefix(root(),label("todo_last_completed").split("%1")[0])!=null,"Completion instant visible");
            tapKey(root(),"todo_edit");editor=dialogKey("todo_edit");edit(editor,"todo_note").setText("Edited after completion");editor.findViewById(android.R.id.button1).performClick();check(data().optJSONArray("items").optJSONObject(2).optLong("completedAt")==at,"Editing leaves completion clock unchanged");tapKey(root(),"todo_undo");check(data().optJSONArray("items").optJSONObject(2).optLong("completedAt")==0,"UI undo clears clock");tapKey(root(),"todo_complete");check(data().optJSONArray("items").optJSONObject(2).optLong("completedAt")>=at,"New completion restarts clock");
        });
        ui(()->{
            tapKey(root(),"todo_edit");View editor=dialogKey("todo_edit");edit(editor,"todo_note").setText("Stale draft");JSONObject doc=data();JSONObject row=doc.optJSONArray("items").optJSONObject(2);row.optString("id");try{row.put("updated",row.optLong("updated")+1000);}catch(Exception e){throw new RuntimeException(e);}data(doc);editor.findViewById(android.R.id.button1).performClick();check(dialogKey("todo_edit")!=null,"Stale editor cannot overwrite a newer completion or edit");check(!data().optJSONArray("items").optJSONObject(2).optString("note").equals("Stale draft"),"Newer data retained");editor.findViewById(android.R.id.button2).performClick();
        });
        ui(()->{
            JSONObject doc=data();JSONObject row=doc.optJSONArray("items").optJSONObject(2);try{row.put("deleteAfterDays",0).put("completedAt",System.currentTimeMillis()-4*86400000L);}catch(Exception e){throw new RuntimeException(e);}data(doc);call(todo(),"refreshList",new Class[]{});tapKey(root(),"todo_edit");View editor=dialogKey("todo_edit");check(!((Switch)text(editor,label("todo_auto_delete"))).isChecked(),"Disabled retention loaded in editor");((Switch)text(editor,label("todo_auto_delete"))).performClick();edit(editor,"todo_delete_days").setText("1");editor.findViewById(android.R.id.button1).performClick();View confirm=dialogKey("todo_auto_delete");check(data().optJSONArray("items").length()==3,"Newly expired retention requires confirmation");confirm.findViewById(android.R.id.button2).performClick();
        });
        ui(()->{View editor=dialogKey("todo_edit");editor.findViewById(android.R.id.button1).performClick();dialogKey("todo_auto_delete").findViewById(android.R.id.button1).performClick();});
        ui(()->check(data().optJSONArray("items").length()==2,"Confirmed shortened retention removes expired task"));
        final JSONObject[] boundary=new JSONObject[1];long now=System.currentTimeMillis();JSONObject once=v2("once").put("title","Done today").put("done",true).put("completedDay",java.time.LocalDate.now().toString()).put("completedAt",now-3*86400000L+1500).put("created",now-5*86400000L).put("updated",now-3*86400000L+1500);
        JSONObject recurring=v2("repeat").put("title","Repeated today").put("repeat",1).put("completedThrough",java.time.LocalDate.now().toString()).put("completedDay",java.time.LocalDate.now().toString()).put("completedAt",now).put("created",now-5*86400000L);
        JSONObject old=v2("old").put("title","Not scheduled today").put("date",java.time.LocalDate.now().minusDays(1).toString()).put("until",java.time.LocalDate.now().minusDays(1).toString()).put("done",true);
        boundary[0]=new JSONObject().put("version",2).put("items",new JSONArray().put(once).put(recurring).put(old));
        ui(()->{edit(root(),"todo_search").setText("");spinner(root(),"todo_filter",1);field(todo(),"filter",1);data(boundary[0]);call(todo(),"refreshList",new Class[]{});check(text(root(),"Done today")!=null&&text(root(),"Repeated today")!=null,"Today contains completed once and repeating items");check(text(root(),"Not scheduled today")==null,"Today excludes yesterday-only task");check(text(((LinearLayout)field(todo(),"list")).getChildAt(0),"Done today")!=null,"Today sorts completed and pending occurrences by their scheduled start");edit(root(),"todo_search").setText("Done today");check(text(root(),label("todo_undo"))!=null,"Undo initially present near deadline");});
        SystemClock.sleep(1800);waitForIdleSync();ui(()->check(text(root(),label("todo_undo"))==null,"Undo disappears at 72 hours without reopening page"));
        JSONObject deleting=v2("delete").put("title","Expired repeat").put("repeat",1).put("completedThrough",java.time.LocalDate.now().toString()).put("completedDay",java.time.LocalDate.now().toString()).put("completedAt",System.currentTimeMillis()-86400000L+1300).put("created",now-5*86400000L).put("deleteAfterDays",1);
        boundary[0]=new JSONObject().put("version",2).put("items",new JSONArray().put(deleting).put(old));
        ui(()->{data(boundary[0]);scheduler(false);});SystemClock.sleep(1600);waitForIdleSync();check(data().getJSONArray("items").length()==1&&data().getJSONArray("items").getJSONObject(0).getString("id").equals("old"),"Automatic deletion runs with notifications disabled and keeps other task");
        JSONObject expired=v2("expired").put("repeat",1).put("completedDay",java.time.LocalDate.now().minusDays(2).toString()).put("completedThrough",java.time.LocalDate.now().minusDays(2).toString()).put("date",java.time.LocalDate.now().minusDays(3).toString()).put("until",java.time.LocalDate.now().minusDays(3).toString()).put("completedAt",now-2*86400000L).put("created",now-5*86400000L).put("deleteAfterDays",1);
        ui(()->{try{data(new JSONObject().put("version",2).put("items",new JSONArray().put(expired)));receive(new Intent("todo.complete").putExtra("id","expired").putExtra("updated",expired.optLong("updated")).putExtra("day",java.time.LocalDate.now().toString()));check(data().optJSONArray("items").length()==0,"Expired notification cannot postpone deletion by completing next repeat");}catch(Exception e){throw new RuntimeException(e);}});
    }
    void createTasks()throws Exception{
        ui(()->{
            call(a,"switchTab",new Class[]{int.class},7);check(text(root(),label("todo_add"))!=null,"To-do tab renders");
            tapKey(root(),"todo_add");View d=dialogKey("todo_add");edit(d,"todo_title").setText("Task Alpha");edit(d,"todo_note").setText("Notes");edit(d,"todo_category").setText("Work");tapKey(d,"todo_add_slot");View range=dialogKey("todo_add_slot");setRange(range,15,0,16,0);range.findViewById(android.R.id.button1).performClick();edit(d,"todo_parts").setText("First\nSecond");tapKey(d,"action_save");
            check(data().optJSONArray("items").length()==1,"New task persisted");JSONObject i=data().optJSONArray("items").optJSONObject(0);check(i.optJSONArray("slots").length()==2,"Separate ranges persisted");check(i.optJSONArray("parts").length()==2,"Checklist persisted");check(text(root(),"Task Alpha")!=null,"New task visible without page navigation");check(((Spinner)field(todo(),"categoryChoice")).getAdapter().getCount()==2,"New category immediately available in filter");
            CheckBox first=(CheckBox)text(root(),"First");check(first!=null,"Checklist checkbox");check(first.getButtonTintList().getDefaultColor()==0xff246bce,"Checklist checkmark fixed blue");first.performClick();check(data().optJSONArray("items").optJSONObject(0).optJSONArray("parts").optJSONObject(0).optBoolean("done"),"Checklist completion persisted");
            tapKey(root(),"todo_edit");View editor=dialogKey("todo_edit");check(text(editor,label("todo_delete"))!=null,"Edit has delete action");edit(editor,"todo_title").setText("Changed");tapKey(editor,"action_cancel");
        });
        ui(()->{
            check(data().optJSONArray("items").optJSONObject(0).optString("title").equals("Task Alpha"),"Cancel preserves data");tapKey(root(),"todo_edit");View editor=dialogKey("todo_edit");edit(editor,"todo_title").setText("");tapKey(editor,"action_save");check(dialogKey("todo_edit")!=null,"Invalid task remains in editor");check(data().optJSONArray("items").length()==1,"Invalid save not destructive");tapKey(editor,"action_cancel");
        });
        ui(()->{
            tapKey(root(),"todo_add");View all=dialogKey("todo_add");edit(all,"todo_title").setText("Multi day");((Switch)text(all,label("todo_all_day"))).performClick();edit(all,"todo_until").setText(java.time.LocalDate.now().plusDays(2).toString());tapKey(all,"action_save");check(data().optJSONArray("items").length()==2,"Multi-day saved");check(data().optJSONArray("items").optJSONObject(1).optBoolean("allDay"),"All-day type persisted");
            tapKey(root(),"todo_add");View temp=dialogKey("todo_add");edit(temp,"todo_title").setText("Temporary");tapKey(temp,"action_save");edit(root(),"todo_search").setText("Temporary");tapKey(root(),"todo_edit");tapKey(dialogKey("todo_edit"),"todo_delete");tapKey(dialogKey("todo_delete"),"action_cancel");
        });
        ui(()->{check(data().optJSONArray("items").length()==3,"Delete cancellation preserves task");tapKey(dialogKey("todo_edit"),"todo_delete");dialogKey("todo_delete").findViewById(android.R.id.button1).performClick();});
        ui(()->{
            check(data().optJSONArray("items").length()==2,"Confirmed delete removes only selected task");edit(root(),"todo_search").setText("Alpha");check(text(root(),"Task Alpha")!=null&&text(root(),"Multi day")==null,"Search filters list");tapKey(root(),"todo_complete");check(data().optJSONArray("items").optJSONObject(0).optBoolean("done"),"Complete persisted");check(text(root(),"Task Alpha")==null,"Completed hidden in pending filter");edit(root(),"todo_search").setText("");
            call(a,"settings",new Class[]{});tapKey(dialogKey("action_settings"),"tab_todo");tapKey(dialogKey("tab_todo"),"todo_display_settings");View settings=dialogKey("todo_display_settings");((Switch)text(settings,label("todo_show_notes"))).performClick();check(dialogKey("todo_display_settings")!=null,"Selection leaves child open");tapKey(settings,"action_back");
        });
        ui(()->{check(dialogKey("tab_todo")!=null,"Back retains parent task settings");tapKey(dialogKey("tab_todo"),"action_back");});
        ui(()->{check(dialogKey("action_settings")!=null,"Back returns to general settings");tapKey(dialogKey("action_settings"),"action_close");});
    }
    void timeZones()throws Exception{
        final JSONObject weather=new JSONObject().put("timezone","Asia/Shanghai").put("current",new JSONObject().put("time","2026-11-01T08:30")).put("daily",new JSONObject().put("time",new JSONArray())).put("hourly",new JSONObject().put("time",new JSONArray()));
        ui(()->{Object saved=field(a,"data");int mode=prefs("toolbox").getInt("global_time_mode",0);field(a,"data",create("WeatherData",new Class[]{JSONObject.class,long.class},weather,System.currentTimeMillis()));
            for(int choice:new int[]{0,2,1}){prefs("toolbox").edit().putInt("global_time_mode",choice).commit();((LinearLayout)field(a,"body")).removeAllViews();call(a,"currentWeather",new Class[]{java.time.Instant.class},java.time.Instant.parse("2026-11-01T00:30:00Z"));TextView footer=prefix(root(),label("main_016"));check(footer!=null&&footer.getText().toString().contains(choice==2?"00:30 · UTC+0":"08:30 · UTC+8"),"Weather clock and UTC offset describe same instant in mode "+choice);check(!footer.getText().toString().contains("Asia/"),"Weather omits internal zone identifier");}
            field(a,"data",saved);prefs("toolbox").edit().putInt("global_time_mode",mode).commit();call(a,"switchTab",new Class[]{int.class},7);
        });
    }
    void rangeSnapshot(){ui(()->{tapKey(root(),"todo_add");tapKey(dialogKey("todo_add"),"todo_add_slot");});snapshot("range");ui(()->dialogKey("todo_add_slot").findViewById(android.R.id.button2).performClick());ui(()->dialogKey("todo_add").findViewById(android.R.id.button2).performClick());}
    void snapshot(String name){try{java.io.File file=new java.io.File(a.getExternalFilesDir(null),"todo-"+language+"-"+name+".png");try(java.io.FileOutputStream out=new java.io.FileOutputStream(file)){getUiAutomation().takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}}catch(Exception e){throw new RuntimeException(e);}}
    Object transfer;
    void idleTransfer(){for(int i=0;i<200;i++){if(!(Boolean)field(transfer,"busy"))return;SystemClock.sleep(50);}throw new AssertionError("Transfer timeout");}
    View await(String key){for(int i=0;i<160;i++){AtomicReference<View> view=new AtomicReference<>();ui(()->{try{view.set(dialogKey(key));}catch(AssertionError ignored){}});if(view.get()!=null)return view.get();SystemClock.sleep(50);}throw new AssertionError("Missing dialog "+key);}
    void deliver(java.io.File file){ui(()->{field(transfer,"busy",true);call(a,"onActivityResult",new Class[]{int.class,int.class,Intent.class},71,Activity.RESULT_OK,new Intent().setData(android.net.Uri.fromFile(file)));});}
    void importFile(java.io.File file){ActivityMonitor monitor=new ActivityMonitor(a.getClass().getName(),null,false);addMonitor(monitor);deliver(file);View confirmation=await("config_import_title");ui(()->tapKey(confirmation,"config_confirm"));idleTransfer();Activity next=waitForMonitorWithTimeout(monitor,8000);removeMonitor(monitor);check(next!=null,"Import recreates models");a=next;waitForIdleSync();transfer=field(a,"configuration");}
    void configurationRoundTrip()throws Exception{
        transfer=field(a,"configuration");java.io.File file=new java.io.File(a.getCacheDir(),"todo-config.json");
        ActivityMonitor monitor=new ActivityMonitor(){@Override public ActivityResult onStartActivity(Intent intent){if(Intent.ACTION_CREATE_DOCUMENT.equals(intent.getAction()))return new ActivityResult(Activity.RESULT_OK,new Intent().setData(android.net.Uri.fromFile(file)));return null;}};addMonitor(monitor);
        ui(()->call(a,"exportConfiguration",new Class[]{}));idleTransfer();removeMonitor(monitor);
        JSONObject doc=new JSONObject(new String(java.nio.file.Files.readAllBytes(file.toPath()),java.nio.charset.StandardCharsets.UTF_8));String exported=doc.getJSONObject("todos").getJSONObject("data").getString("value");check(new JSONObject(exported).getJSONArray("items").length()==2,"Actual export includes tasks and checklist");
        ui(()->prefs("todos").edit().clear().commit());importFile(file);check(data().toString().equals(exported),"Actual import restores task data");
        doc.remove("todos");java.nio.file.Files.write(file.toPath(),doc.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));importFile(file);check(data().toString().equals(exported),"Legacy import preserves current tasks");
        JSONObject invalid=new JSONObject(doc.toString());invalid.put("todos",new JSONObject().put("data",new JSONObject().put("type","string").put("value","{}")));java.nio.file.Files.write(file.toPath(),invalid.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));deliver(file);idleTransfer();check(data().toString().equals(exported),"Invalid tasks rejected without overwriting");
        ActivityMonitor reset=new ActivityMonitor(a.getClass().getName(),null,false);addMonitor(reset);
        ui(()->{call(a,"switchTab",new Class[]{int.class},7);call(a,"restoreDefaults",new Class[]{});check(data().optJSONArray("items").length()==2,"Reset defaults retains personal tasks");});
        Activity next=waitForMonitorWithTimeout(reset,8000);removeMonitor(reset);check(next!=null,"Default restoration recreates localized activity");a=next;waitForIdleSync();
        ActivityMonitor relanguage=new ActivityMonitor(a.getClass().getName(),null,false);addMonitor(relanguage);ui(()->{prefs("toolbox").edit().putString("app_language",language).commit();a.recreate();});next=waitForMonitorWithTimeout(relanguage,8000);removeMonitor(relanguage);check(next!=null,"Test language restored after resetting defaults");a=next;waitForIdleSync();
    }
    void actualAlarm()throws Exception{
        boolean exact=(Boolean)staticCall("TodoScheduler","exact",new Class[]{Context.class},a);
        if(!exact){ui(()->scheduler(false));check(notification("todo")!=null,"Inexact fallback retains summary without exact access");return;}
        ui(()->{try{JSONObject i=data().getJSONArray("items").getJSONObject(0);prefs("todo_runtime").edit().putString("snooze:"+i.getString("id"),java.time.LocalDate.now()+"|"+i.getLong("updated")+"|"+(System.currentTimeMillis()+6500)).commit();scheduler(false);}catch(Exception e){throw new RuntimeException(e);}});
        SystemClock.sleep(8500);waitForIdleSync();check(notification("todo:notify-alpha")!=null,"Actual AlarmManager delivery reaches manifest receiver");check(!prefs("todo_runtime").contains("snooze:notify-alpha"),"Actual dispatch consumes snooze");
    }
    void notifications()throws Exception{
        JSONObject doc=new JSONObject().put("version",1).put("items",new JSONArray().put(task("notify-alpha",true)));
        ui(()->{close();prefs("todo_runtime").edit().clear().commit();prefs("toolbox").edit().putBoolean("todo_notify",true).putBoolean("todo_summary",true).putBoolean("todo_private",false).putInt("todo_day_minute",java.time.LocalTime.now().getHour()*60+java.time.LocalTime.now().getMinute()).commit();data(doc);scheduler(true);
            NotificationManager nm=a.getSystemService(NotificationManager.class);check(nm.getNotificationChannel("todo_reminders_v1").getImportance()==NotificationManager.IMPORTANCE_HIGH,"Reminder channel enables heads-up");check(nm.getNotificationChannel("todo_summary_v1").getImportance()==NotificationManager.IMPORTANCE_LOW,"Summary is silent low importance");check(notification("todo")!=null,"Ongoing summary posted");check((notification("todo").getNotification().flags&Notification.FLAG_ONGOING_EVENT)!=0,"Summary marked ongoing");check(notification("todo:notify-alpha")!=null,"Due reminder posted");check(notification("todo:notify-alpha").getNotification().actions.length==2,"Complete and snooze actions");
            int count=prefs("todo_runtime").getAll().size();scheduler(true);check(prefs("todo_runtime").getAll().size()==count,"Repeated tick deduplicates event");
        });
        Notification n=notification("todo:notify-alpha").getNotification();n.actions[1].actionIntent.send();waitActions();check(prefs("todo_runtime").contains("snooze:notify-alpha"),"Snooze schedules durable ten-minute reminder");check(notification("todo:notify-alpha")==null,"Snoozed notification removed");
        n.actions[0].actionIntent.send();waitActions();check(data().getJSONArray("items").getJSONObject(0).getBoolean("done"),"Notification completes task");check(!prefs("todo_runtime").contains("snooze:notify-alpha"),"Complete clears snooze");check(notification("todo")==null,"Completed tasks remove summary");
        ui(()->{try{JSONObject changed=task("notify-alpha",true);changed.put("updated",System.currentTimeMillis());data(new JSONObject().put("version",1).put("items",new JSONArray().put(changed)));}catch(Exception e){throw new RuntimeException(e);}});n.actions[0].actionIntent.send();waitActions();check(!data().getJSONArray("items").getJSONObject(0).getBoolean("done"),"Stale action cannot complete edited task");
        ui(()->{prefs("toolbox").edit().putBoolean("todo_private",true).commit();scheduler(false);});waitActions();ui(()->{Notification summary=notification("todo").getNotification();check(summary.visibility==Notification.VISIBILITY_SECRET,"Private notification hidden on lock screen");check(!summary.extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains("Notify Alpha"),"Private summary excludes titles");
            receive(new Intent(Intent.ACTION_BOOT_COMPLETED));check(notification("todo")!=null,"Boot dispatch rearms and updates summary");receive(new Intent(Intent.ACTION_TIMEZONE_CHANGED));check(notification("todo")!=null,"Timezone change reschedules tasks");
            staticCall("TodoScheduler","suspend",new Class[]{Context.class},a);check(notification("todo")==null,"Import suspension clears summary");staticCall("TodoScheduler","afterImport",new Class[]{Context.class},a);check(prefs("todo_runtime").getLong("not_before",0)>0,"Import uses new reminder cutoff");check(notification("todo:notify-alpha")==null,"Import does not replay old alerts");
        });
    }
    @Override public void onCreate(Bundle b){super.onCreate(b);if(b!=null)language=b.getString("language","zh-CN");start();}
    @Override public void onStart(){Bundle result=new Bundle();int code=Activity.RESULT_OK;try{
        for(String group:new String[]{"toolbox","todos","todo_runtime"})before.put(group,new HashMap<>(prefs(group).getAll()));
        prefs("toolbox").edit().putString("app_language",language).putInt("seen_version",Integer.MAX_VALUE).putInt("last_tab",7).putBoolean("tab_show_7",true).putBoolean("todo_notify",false).putBoolean("todo_summary",false).commit();prefs("todos").edit().clear().commit();prefs("todo_runtime").edit().clear().commit();
        a=startActivitySync(new Intent().setClassName(getTargetContext(),"org.simpleweather.app.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();createTasks();waitForIdleSync();SystemClock.sleep(300);snapshot("list");rangeSnapshot();configurationRoundTrip();completionManagement();timeZones();notifications();actualAlarm();
        result.putString("stream","PASS: "+checks+" To-do UI and notification checks ("+language+")\n");
    }catch(Throwable e){code=Activity.RESULT_CANCELED;result.putString("stream","FAIL: "+android.util.Log.getStackTraceString(e));}finally{
        if(a!=null)ui(()->{close();a.finish();});for(Map.Entry<String,Map<String,?>> entry:before.entrySet())restore(prefs(entry.getKey()),entry.getValue());if(a!=null)staticCall("TodoScheduler","changed",new Class[]{Context.class},getTargetContext());
    }finish(code,result);}
    void restore(SharedPreferences p,Map<String,?> values){SharedPreferences.Editor e=p.edit().clear();for(Map.Entry<String,?> x:values.entrySet()){Object v=x.getValue();String k=x.getKey();if(v instanceof Boolean)e.putBoolean(k,(Boolean)v);else if(v instanceof Integer)e.putInt(k,(Integer)v);else if(v instanceof Long)e.putLong(k,(Long)v);else if(v instanceof Float)e.putFloat(k,(Float)v);else if(v instanceof String)e.putString(k,(String)v);else if(v instanceof Set)e.putStringSet(k,(Set<String>)v);}e.commit();}
}
