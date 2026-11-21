package org.simpleweather.qa;

import android.app.*;
import android.content.*;
import android.os.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.file.Files;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.json.*;

/** Shared model invalidation across the real settings window and its paused owner. */
public final class SettingsData301UiTests extends Instrumentation {
    private Activity main,settings;private Context context;private ClassLoader host;private Bundle args;private Object apps;private int checks;
    private final Map<String,Map<String,?>> before=new LinkedHashMap<>();
    private final Map<String,Boolean> installedBefore=new LinkedHashMap<>();
    private final Map<File,byte[]> localArtwork=new LinkedHashMap<>();
    private static final String[] GROUPS={"toolbox","weather","timetable","todos","todo_runtime","random_choice","status_places","status_routes","location_state"};
    public void onCreate(Bundle value){super.onCreate(value);args=value;start();}
    private Class<?> type(String name)throws Exception{return Class.forName("org.simpleweather.app."+name,true,host);}
    private Object field(Object target,String name)throws Exception{for(Class<?> c=target instanceof Class?(Class<?>)target:target.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(name);f.setAccessible(true);return f.get(target instanceof Class?null:target);}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(name);}
    private Object call(Object target,String name,Class<?>[] signature,Object...values)throws Exception{for(Class<?> c=target instanceof Class?(Class<?>)target:target.getClass();c!=null;c=c.getSuperclass())try{Method m=c.getDeclaredMethod(name,signature);m.setAccessible(true);return m.invoke(target instanceof Class?null:target,values);}catch(NoSuchMethodException ignored){}throw new NoSuchMethodException(name);}
    private void ui(Runnable action){AtomicReference<Throwable> error=new AtomicReference<>();runOnMainSync(()->{try{action.run();}catch(Throwable failure){error.set(failure);}});if(error.get()!=null)throw new RuntimeException(error.get());settle();}
    private void settle(){waitForIdleSync();SystemClock.sleep(80);waitForIdleSync();}
    private void check(boolean condition,String name){if(!condition)throw new AssertionError(name);checks++;}
    private void tab(int index){ui(()->{try{call(main,"switchTab",new Class[]{int.class},index);}catch(Exception failure){throw new RuntimeException(failure);}});}
    private Object target(int index)throws Exception{return field(((Map<?,?>)field(main,"screens")).get(index),"target");}
    private boolean installed(String id)throws Exception{return (Boolean)call(apps,"installed",new Class[]{String.class},id);}
    private void install(String id)throws Exception{try(InputStream in=new FileInputStream("/data/local/tmp/internal-apps/"+id+".ddapp")){call(apps,"install",new Class[]{InputStream.class,String.class,String.class},in,id,null);}}
    private void openSettings()throws Exception{
        ActivityMonitor monitor=addMonitor("org.simpleweather.app.SettingsActivity",null,false);
        try{ui(()->{try{call(type("SettingsActivity"),"open",new Class[]{Activity.class,String.class},main,"");}catch(Exception failure){throw new RuntimeException(failure);}});settings=monitor.waitForActivityWithTimeout(10000);check(settings!=null,"separate settings application opened for result");settle();}
        finally{removeMonitor(monitor);}
        Set<?> active=(Set<?>)field(type("MainActivity"),"ACTIVE");check(active.contains(main)&&active.contains(settings),"active registry includes original and settings owners");
    }
    private void closeSettings(){if(settings==null)return;Activity old=settings;ui(old::finish);settings=null;settle();}
    private void perApplicationInvalidation()throws Exception{
        tab(2);Object gps=target(2);tab(1);Object timetable=field(main,"schedule");tab(5);Object quake=target(5);openSettings();
        Object originalStore=field(main,"store");
        ui(()->{try{call(settings,"prepareApplicationDataChange",new Class[]{String.class},"earthquake");check(!((Map<?,?>)field(main,"screens")).containsKey(5),"other owner drops affected catalogue model before cache deletion");check(field(main,"earthquakes")==null,"affected direct screen handle cleared");check((Boolean)field(quake,"dead"),"released catalogue cannot repopulate after queued network callback");check(field(main,"gps")!=null&&field(field(main,"gps"),"target")==gps,"unrelated status model preserved");check(field(main,"schedule")==timetable,"unrelated course model preserved");check((Boolean)field(main,"reloadSharedData"),"original owner records shared data reload requirement");}catch(Exception failure){throw new RuntimeException(failure);}});
        closeSettings();check(!(Boolean)field(main,"reloadSharedData"),"settings result consumes shared data invalidation");check(field(main,"store")!=originalStore,"return refreshes original owner weather selection/cache reader");check((Integer)field(main,"tab")==5,"cache preparation preserves original application selection");check(target(5)!=quake,"original application reconstructs a fresh catalogue model");
    }
    private void importPreparation()throws Exception{
        tab(2);Object gps=target(2);tab(1);tab(5);Object quake=target(5);openSettings();
        int search=(Integer)field(main,"searchVersion"),cache=(Integer)field(main,"cacheGeneration");
        JSONObject imported=new JSONObject().put("name","Imported fixture reference").put("latitude",1.234).put("longitude",-35.678).put("timezone","UTC").put("country_code","ZZ").put("placeId","fixture:settings-data-301");
        ui(()->{try{call(settings,"prepareConfigurationImport",new Class[]{});check(((Map<?,?>)field(main,"screens")).isEmpty(),"full import releases every cached screen in paused owner");check((Boolean)field(gps,"destroyed"),"full import destroys old status sampler");check((Boolean)field(quake,"dead"),"full import destroys old catalogue worker generation");check(field(main,"gps")==null&&field(main,"earthquakes")==null&&field(main,"schedule")==null,"full import removes stale direct screen handles");check((Integer)field(main,"searchVersion")>search&&(Integer)field(main,"cacheGeneration")>cache,"full import invalidates queued searches and weather cache callbacks");check((Boolean)field(main,"reloadSharedData"),"full import marks original shared models stale");context.getSharedPreferences("weather",0).edit().putString("selected",imported.toString()).commit();}catch(Exception failure){throw new RuntimeException(failure);}});
        closeSettings();Object city=field(main,"city");check(city.getClass().getField("name").get(city).equals("Imported fixture reference"),"return uses newly imported reference rather than cached selection");check(Math.abs(city.getClass().getField("longitude").getDouble(city)+35.678)<.000001,"imported longitude retained accurately");check(!(Boolean)field(main,"reloadSharedData"),"full-import reload flag cleared after owner returns");check(target(5)!=quake,"old destroyed catalogue instance is never revived");
    }
    private void courseFixture()throws Exception{
        Object model=type("ScheduleModel").getConstructor().newInstance();Field weeks=type("ScheduleModel").getField("weeks");weeks.setInt(model,30);type("ScheduleModel").getField("semester").set(model,LocalDate.now().with(DayOfWeek.MONDAY).minusWeeks(8));
        List<Object> slots=(List<Object>)field(model,"slots");slots.set(0,type("ScheduleModel$Slot").getConstructor(String.class,String.class).newInstance("06:00","06:35"));
        Object course=type("ScheduleModel$Course").getConstructor().newInstance();course.getClass().getField("id").set(course,"settings-data301-course");course.getClass().getField("name").set(course,"Retained course fixture");course.getClass().getField("room").set(course,"Fixture room");course.getClass().getField("teacher").set(course,"Fixture teacher");course.getClass().getField("weekEnd").setInt(course,12);((List<Object>)field(model,"courses")).add(course);
        context.getSharedPreferences("timetable",0).edit().putString("data",call(model,"json",new Class[]{}).toString()).commit();
    }
    private void resetFromSeparateSettings()throws Exception{
        // No filesystem fixtures are created. Preserve the two optional artwork files
        // that resetDefaults owns, in addition to all preference groups.
        for(String name:new String[]{"background.jpg","launcher-icon.png"}){File file=new File(context.getFilesDir(),name);localArtwork.put(file,file.isFile()?Files.readAllBytes(file.toPath()):null);}
        courseFixture();ui(()->{try{call(main,"releaseModule",new Class[]{String.class},"schedule");}catch(Exception failure){throw new RuntimeException(failure);}});tab(1);Object originalSchedule=field(main,"schedule");
        Map<String,Map<String,?>> longTerm=new LinkedHashMap<>();for(String group:new String[]{"todos","status_places","status_routes","random_choice"})longTerm.put(group,new HashMap<>(context.getSharedPreferences(group,0).getAll()));
        openSettings();check(field(settings,"schedule")==null,"settings owner has never constructed the timetable screen before reset");
        ActivityMonitor recreated=addMonitor("org.simpleweather.app.SettingsActivity",null,false);Activity oldSettings=settings;
        try{ui(()->{try{call(oldSettings,"restoreDefaults",new Class[]{});check(field(main,"schedule")==null,"default reset releases original cached timetable");}catch(Exception failure){throw new RuntimeException(failure);}});Activity next=recreated.waitForActivityWithTimeout(10000);if(next!=null)settings=next;settle();}
        finally{removeMonitor(recreated);}
        JSONObject saved=new JSONObject(context.getSharedPreferences("timetable",0).getString("data",""));check(saved.getInt("weeks")==17,"separate settings reset applies timetable default weeks");check(saved.getString("semester").equals(LocalDate.now().with(DayOfWeek.MONDAY).toString()),"separate settings reset applies current default semester");check(saved.getJSONArray("slots").length()==12,"default periods restored without opening timetable settings");check(saved.getJSONArray("slots").getJSONObject(0).getString("start").equals("08:00"),"custom period start replaced with default");JSONArray courses=saved.getJSONArray("courses");check(courses.length()==1,"reset retains saved courses");JSONObject course=courses.getJSONObject(0);check(course.getString("id").equals("settings-data301-course")&&course.getString("name").equals("Retained course fixture"),"reset preserves course identity and name");check(course.getString("room").equals("Fixture room")&&course.getString("teacher").equals("Fixture teacher"),"reset preserves course details");
        for(String group:longTerm.keySet())check(context.getSharedPreferences(group,0).getAll().equals(longTerm.get(group)),"default reset preserves long-term "+group+" data");
        ActivityMonitor recreatedMain=addMonitor("org.simpleweather.app.MainActivity",null,false);try{closeSettings();Activity next=recreatedMain.waitForActivityWithTimeout(5000);if(next!=null){main=next;apps=field(main,"apps");settle();}}finally{removeMonitor(recreatedMain);}check(field(main,"schedule")!=originalSchedule,"return never revives pre-reset timetable model");
    }
    public void onStart(){Bundle result=new Bundle();try{
        context=getTargetContext();host=context.getClassLoader();for(String group:GROUPS)before.put(group,new HashMap<>(context.getSharedPreferences(group,0).getAll()));context.getSharedPreferences("toolbox",0).edit().putInt("seen_version",Integer.MAX_VALUE).putString("app_language",args.getString("language","zh-CN")).putBoolean("course_notify",false).putLong("quake_interval",0).putBoolean("location_continuous",false).commit();
        main=startActivitySync(new Intent().setClassName(context,"org.simpleweather.app.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));settle();apps=field(main,"apps");for(String id:new String[]{"weather","status","earthquake","schedule"}){installedBefore.put(id,installed(id));install(id);}
        perApplicationInvalidation();importPreparation();resetFromSeparateSettings();result.putString("result","PASS: "+checks+" cross-activity shared-data/default-reset checks ("+args.getString("language","zh-CN")+")");
    }catch(Throwable failure){StringWriter log=new StringWriter();failure.printStackTrace(new PrintWriter(log));result.putString("result","FAIL: "+log);}finally{
        try{closeSettings();if(main!=null&&!main.isDestroyed()&&!main.isFinishing()){tab(-1);for(Map.Entry<String,Boolean> entry:installedBefore.entrySet())if(!entry.getValue()&&installed(entry.getKey())){ui(()->{try{call(apps,"uninstall",new Class[]{String.class},entry.getKey());}catch(Exception failure){throw new RuntimeException(failure);}});long until=SystemClock.elapsedRealtime()+10000;while((Boolean)field(apps,"busy")&&SystemClock.elapsedRealtime()<until)SystemClock.sleep(20);}ui(main::finish);}for(Map.Entry<File,byte[]> entry:localArtwork.entrySet())if(entry.getValue()!=null)Files.write(entry.getKey().toPath(),entry.getValue());else Files.deleteIfExists(entry.getKey().toPath());for(Map.Entry<String,Map<String,?>> entry:before.entrySet())call(type("ConfigurationTransfer"),"write",new Class[]{SharedPreferences.class,Map.class},context.getSharedPreferences(entry.getKey(),0),entry.getValue());}
        catch(Throwable failure){result.putString("result","FAIL: Fixture restore failed: "+failure+"\nPrior result: "+result.getString("result"));}
        finish(Activity.RESULT_OK,result);
    }}
}
