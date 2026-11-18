package org.simpleweather.qa;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.json.*;

/** About three minutes on an isolated emulator; requires the eight signed fixture packages. */
public final class ModuleEnduranceUiTests extends Instrumentation {
    private static final String MAIN="org.simpleweather.app.MainActivity";
    private static final String[] IDS={"weather","schedule","status","weather-map","astronomy","earthquake","random","todo"};
    private static final String[] GROUPS={"toolbox","weather","status_places","status_routes","timetable","random_choice","todos","todo_runtime"};
    private Activity activity;private Context context;private ClassLoader host;private Object apps;private Bundle args;private int checks;
    private final Map<String,Map<String,?>> original=new LinkedHashMap<>();
    private final Map<String,Map<String,Object>> expected=new LinkedHashMap<>();
    private final Map<String,Boolean> installedBefore=new LinkedHashMap<>();
    private final Map<String,String> packageHashes=new LinkedHashMap<>();
    private final Map<File,byte[]> sentinels=new LinkedHashMap<>();

    public void onCreate(Bundle value){super.onCreate(value);args=value;start();}
    private Class<?> type(String name)throws Exception{return Class.forName("org.simpleweather.app."+name,true,host);}
    private Object field(Object value,String name)throws Exception{Field f=value.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(value);}
    private void set(Object value,String name,Object data)throws Exception{Field f=value.getClass().getField(name);f.set(value,data);}
    private Object call(Object value,String name,Class<?>[] signature,Object...arguments)throws Exception{
        Class<?> owner=value instanceof Class?(Class<?>)value:value.getClass();Method m=owner.getDeclaredMethod(name,signature);m.setAccessible(true);return m.invoke(value instanceof Class?null:value,arguments);
    }
    private void check(boolean value,String label){if(!value)throw new AssertionError(label);checks++;}
    private void ui(Runnable action){AtomicReference<Throwable> error=new AtomicReference<>();runOnMainSync(()->{try{action.run();}catch(Throwable e){error.set(e);}});if(error.get()!=null)throw new RuntimeException(error.get());waitForIdleSync();}
    private SharedPreferences prefs(String group){return context.getSharedPreferences(group,0);}
    private void tab(int index){ui(()->{try{call(activity,"switchTab",new Class[]{int.class},index);}catch(Exception e){throw new RuntimeException(e);}});}
    private boolean installed(String id)throws Exception{return (Boolean)call(apps,"installed",new Class[]{String.class},id);}
    private void install(String id)throws Exception{try(InputStream in=new FileInputStream("/data/local/tmp/internal-apps/"+id+".ddapp")){call(apps,"install",new Class[]{InputStream.class,String.class,String.class},in,id,null);}}
    private String hash(File file)throws Exception{return (String)call(type("AppModules"),"sha",new Class[]{File.class},file);}
    private File packageFile(String id)throws Exception{return (File)call(type("ModuleRuntime"),"packageFile",new Class[]{Context.class,String.class},context,id);}
    private int textCount(View root){int count=root instanceof TextView&&!((TextView)root).getText().toString().trim().isEmpty()?1:0;if(root instanceof ViewGroup)for(int n=0;n<((ViewGroup)root).getChildCount();n++)count+=textCount(((ViewGroup)root).getChildAt(n));return count;}
    private void screen(int index)throws Exception{
        check(!activity.isFinishing()&&!activity.isDestroyed(),"Activity remains alive for "+IDS[index]);
        check((Integer)field(activity,"tab")==index,"selected application retained "+IDS[index]);
        Object handle=((Map<?,?>)field(activity,"screens")).get(index);check(handle!=null,"executable screen exists "+IDS[index]);
        check(field(handle,"target").getClass().getClassLoader()!=host,"installed application loader used "+IDS[index]);
        check(textCount(activity.getWindow().getDecorView())>6,"resource-backed content remains usable "+IDS[index]);
        check(((LinearLayout)field(activity,"navigation")).getChildCount()==1,"only desktop remains in application navigation "+IDS[index]);
    }
    private Map<String,Object> business(String group)throws Exception{
        Map<String,Object> values=new LinkedHashMap<>();for(Map.Entry<String,?> e:prefs(group).getAll().entrySet())if(call(type("ConfigurationData"),"type",new Class[]{String.class,String.class},group,e.getKey())!=null)values.put(e.getKey(),e.getValue());return values;
    }
    private void unchanged(String stage)throws Exception{
        for(Map.Entry<String,Map<String,Object>> entry:expected.entrySet())check(entry.getValue().equals(business(entry.getKey())),"business preferences retained at "+stage+": "+entry.getKey());
        for(Map.Entry<File,byte[]> entry:sentinels.entrySet())check(entry.getKey().isFile()&&Arrays.equals(entry.getValue(),Files.readAllBytes(entry.getKey().toPath())),"private data retained at "+stage+": "+entry.getKey().getParentFile().getName());
    }
    @SuppressWarnings("unchecked") private void seed()throws Exception{
        Object tasks=type("TodoModel").getConstructor().newInstance(),task=type("TodoModel$Item").getConstructor().newInstance();
        set(task,"id","qa-endurance-task");set(task,"title","Endurance task · 待办");set(task,"someday",true);set(task,"advance",-1);set(task,"deleteAfterDays",0);
        call(type("TodoModel"),"validate",new Class[]{type("TodoModel$Item")},task);((List<Object>)field(tasks,"items")).add(task);prefs("todos").edit().putString("data",call(tasks,"json",new Class[]{}).toString()).commit();
        Object timetable=type("ScheduleModel").getConstructor().newInstance(),course=type("ScheduleModel$Course").getConstructor().newInstance();
        set(course,"id","qa-endurance-course");set(course,"name","Endurance course · 课程");set(course,"room","Room 101");set(course,"teacher","Teacher");call(timetable,"put",new Class[]{type("ScheduleModel$Course")},course);prefs("timetable").edit().putString("data",call(timetable,"json",new Class[]{}).toString()).commit();
        Object random=type("RandomChoiceModel").getConstructor().newInstance();set(random,"draft","甲\n乙\n丙");set(random,"quantity","2");call(random,"reset",new Class[]{});call(random,"save",new Class[]{String.class},"Endurance list");call(random,"draw",new Class[]{int.class,boolean.class},1,true);prefs("random_choice").edit().putString("data",call(random,"json",new Class[]{}).toString()).commit();
        long now=System.currentTimeMillis();Object place=type("StatusPlace").getConstructor(String.class,String.class,double.class,double.class,long.class).newInstance("qa-endurance-place","Endurance place",120.62,31.31,now);
        String places=(String)call(type("StatusPlace"),"encode",new Class[]{List.class},Collections.singletonList(place));prefs("status_places").edit().putString("places",places).putString("selected","qa-endurance-place").commit();
        JSONArray points=new JSONArray().put(new JSONObject().put("lon",120.62).put("lat",31.31).put("time",now).put("accuracy",5.0)).put(new JSONObject().put("lon",120.6201).put("lat",31.3101).put("time",now+5000).put("accuracy",5.0));
        String routes=new JSONArray().put(new JSONObject().put("id","qa-endurance-route").put("name","Endurance route").put("points",points)).toString();prefs("status_routes").edit().putString("routes",routes).commit();
        for(String id:IDS){File directory=(File)call(type("ModuleRuntime"),"dataDirectory",new Class[]{Context.class,String.class},context,id);File sentinel=new File(directory,"qa-endurance-"+UUID.randomUUID()+".bin");byte[] bytes=("Retained application data: "+id+" · 用户资料").getBytes(StandardCharsets.UTF_8);Files.write(sentinel.toPath(),bytes);sentinels.put(sentinel,bytes);}
    }
    private void recreate()throws Exception{
        int selected=(Integer)field(activity,"tab");Activity previous=activity;ActivityMonitor monitor=addMonitor(MAIN,null,false);Activity next;
        try{ui(previous::recreate);next=monitor.waitForActivityWithTimeout(12000);}finally{removeMonitor(monitor);}
        check(next!=null&&next!=previous,"new Activity created after recreation");activity=next;waitForIdleSync();apps=field(activity,"apps");
        check((Integer)field(activity,"tab")==selected,"selected application survives Activity recreation");if(selected>=0)screen(selected);unchanged("recreation");
    }
    @SuppressWarnings("unchecked") private void satelliteDrawing()throws Exception{
        tab(2);Object target=field(((Map<?,?>)field(activity,"screens")).get(2),"target"),hardware=field(target,"hardware");ClassLoader loader=hardware.getClass().getClassLoader();
        Class<?> satellite=Class.forName("org.simpleweather.app.StatusHardware$Satellite",true,loader),sky=Class.forName("org.simpleweather.app.StatusViews$Sky",true,loader);
        List<Object> samples=(List<Object>)field(hardware,"satellites"),prior=new ArrayList<>(samples);long previous=(Long)field(hardware,"gnssAt");
        check(type("SkyLabels").getClassLoader()==host,"label placement is shared host geometry");
        ui(()->{try{
            for(int round=0;round<3;round++){
                samples.clear();int[] systems={5,1,6,3,4,7};for(int n=0;n<systems.length;n++)samples.add(satellite.getConstructor(int.class,int.class,float.class,float.class,float.class,boolean.class).newInstance(systems[n],n+1,25f+n*8,10f+n*50+round*3,35f+n,n%2==0));
                set(hardware,"gnssAt",SystemClock.elapsedRealtime());View view=(View)sky.getConstructor(type("MainActivity"),hardware.getClass(),Runnable.class).newInstance(activity,hardware,(Runnable)()->{});set(view,"selected","5:1");
                int measure=View.MeasureSpec.makeMeasureSpec(480,View.MeasureSpec.EXACTLY);view.measure(measure,measure);view.layout(0,0,480,480);Bitmap bitmap=Bitmap.createBitmap(480,480,Bitmap.Config.ARGB_8888);
                try{view.draw(new Canvas(bitmap));check(((Map<?,?>)field(view,"hits")).size()==systems.length,"satellite drawing resolves shared labels with six real samples, frame "+(round+1));}finally{bitmap.recycle();}
            }
        }catch(Exception e){throw new RuntimeException(e);}finally{samples.clear();samples.addAll(prior);try{set(hardware,"gnssAt",previous);}catch(Exception e){throw new RuntimeException(e);}}});
        screen(2);tab(-1);
    }
    private void background(long duration)throws Exception{
        int selected=(Integer)field(activity,"tab");AtomicReference<Boolean> moved=new AtomicReference<>();ui(()->moved.set(activity.moveTaskToBack(true)));check(Boolean.TRUE.equals(moved.get()),"task moves to background");
        SystemClock.sleep(duration);check(!activity.getWindow().getDecorView().hasWindowFocus(),"background task releases window focus");
        context.startActivity(new Intent().setClassName(context,MAIN).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT|Intent.FLAG_ACTIVITY_SINGLE_TOP));
        long until=SystemClock.elapsedRealtime()+10000;while(!activity.hasWindowFocus()&&SystemClock.elapsedRealtime()<until)SystemClock.sleep(50);waitForIdleSync();
        check(activity.hasWindowFocus()&&!activity.isDestroyed(),"existing task returns to foreground");check((Integer)field(activity,"tab")==selected,"background return retains selected application");if(selected>=0)screen(selected);
    }
    private void ticks()throws Exception{
        Object receiver=type("TodoReceiver").getConstructor().newInstance();ui(()->{try{for(String action:new String[]{"todo.tick","course.tick"})call(receiver,"onReceive",new Class[]{Context.class,Intent.class},activity,new Intent(action));}catch(Exception e){throw new RuntimeException(e);}});
        checks+=2; // Both production broadcast dispatchers returned without an exception.
    }
    private void changing(String id,int index)throws Exception{
        tab(-1);ui(()->{try{call(activity,"releaseModule",new Class[]{String.class},id);}catch(Exception e){throw new RuntimeException(e);}});
        call(type("ModuleRuntime"),"beginChange",new Class[]{Context.class,String.class},context,id);
        try{
            boolean blocked=false;try{call(type("ModuleRuntime"),"loader",new Class[]{Context.class,String.class},context,id);}catch(InvocationTargetException e){blocked=e.getCause().getClass().getName().endsWith("ModuleRuntime$Updating");}
            check(blocked,"real loader is gated during package change "+id);ticks();background(3500);ticks();
            // onResume normally refreshes both reminders; exercise repeated calls while still gated.
            ui(()->{try{call(activity,"onResume",new Class[]{});}catch(Exception e){throw new RuntimeException(e);}});checks++;
            SystemClock.sleep(3500);unchanged("changing "+id);
        }finally{call(type("ModuleRuntime"),"endChange",new Class[]{String.class},id);}
        ticks();tab(index);SystemClock.sleep(1600);screen(index);check(hash(packageFile(id)).equals(packageHashes.get(id)),"package retained after changing gate "+id);unchanged("change complete "+id);
    }
    private void exercise()throws Exception{
        for(int round=0;round<10;round++){
            for(int index=0;index<IDS.length;index++){tab(index);SystemClock.sleep(1600);screen(index);}
            if(round==2||round==5||round==8){background(8000);recreate();}
            tab(-1);SystemClock.sleep(300);check((Integer)field(activity,"tab")==-1,"desktop remains reachable after round "+(round+1));unchanged("round "+(round+1));
        }
        changing("todo",7);changing("schedule",1);
        for(int index=0;index<IDS.length;index++){tab(index);SystemClock.sleep(1100);screen(index);check(hash(packageFile(IDS[index])).equals(packageHashes.get(IDS[index])),"installed executable remains unchanged "+IDS[index]);}
        satelliteDrawing();tab(-1);unchanged("final");
    }
    private void restore()throws Exception{
        List<Throwable> failures=new ArrayList<>();
        try{if(activity!=null&&!activity.isDestroyed()){ui(()->{try{call(type("ToolDialog"),"closeAll",new Class[]{});}catch(Exception e){throw new RuntimeException(e);}});tab(-1);}}catch(Throwable e){failures.add(e);}
        if(apps!=null)for(Map.Entry<String,Boolean> entry:installedBefore.entrySet())try{if(!entry.getValue()&&installed(entry.getKey())){
            ui(()->{try{call(apps,"uninstall",new Class[]{String.class},entry.getKey());}catch(Exception e){throw new RuntimeException(e);}});
            long until=SystemClock.elapsedRealtime()+12000;while((Boolean)field(apps,"busy")&&SystemClock.elapsedRealtime()<until)SystemClock.sleep(20);check(!(Boolean)field(apps,"busy"),"fixture app uninstall completes "+entry.getKey());
        }}catch(Throwable e){failures.add(e);}
        try{if(activity!=null&&!activity.isDestroyed()){ui(()->activity.finish());waitForIdleSync();}activity=null;}catch(Throwable e){failures.add(e);}
        for(File file:sentinels.keySet())try{Files.deleteIfExists(file.toPath());}catch(Throwable e){failures.add(e);}
        // Restore after Activity destruction, so stale screen flushes cannot overwrite the originals.
        for(Map.Entry<String,Map<String,?>> entry:original.entrySet())try{call(type("ConfigurationTransfer"),"write",new Class[]{SharedPreferences.class,Map.class},prefs(entry.getKey()),entry.getValue());}catch(Throwable e){failures.add(e);}
        if(!failures.isEmpty()){Exception failure=new Exception("Cannot restore all endurance fixtures");for(Throwable e:failures)failure.addSuppressed(e);throw failure;}
    }
    public void onStart(){Bundle result=new Bundle();long started=SystemClock.elapsedRealtime();try{
        context=getTargetContext();host=context.getClassLoader();String hardware=Build.HARDWARE==null?"":Build.HARDWARE;
        check(hardware.contains("ranchu")||hardware.contains("goldfish")||Build.FINGERPRINT.contains("emulator"),"isolated emulator required");
        for(String group:GROUPS)original.put(group,new HashMap<>(prefs(group).getAll()));
        prefs("toolbox").edit().putString("app_language",args.getString("language","zh-CN")).putInt("seen_version",context.getPackageManager().getPackageInfo(context.getPackageName(),0).versionCode).putBoolean("keep_screen_awake",true).putBoolean("todo_notify",false).putBoolean("todo_summary",false).putBoolean("course_notify",false).putBoolean("location_continuous",false).putBoolean("astro_align_sky",false).putString("status_light_source","sensor").putLong("quake_interval",0).commit();seed();
        SharedPreferences.Editor constellations=prefs("toolbox").edit();for(int system:new int[]{5,1,6,3,4,7})constellations.putBoolean("status_constellation_"+system,true);constellations.commit();
        activity=startActivitySync(new Intent().setClassName(context,MAIN).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();apps=field(activity,"apps");
        for(String id:IDS){boolean present=installed(id);installedBefore.put(id,present);if(!present)install(id);check(installed(id),"required application installed "+id);packageHashes.put(id,hash(packageFile(id)));}
        for(int index=0;index<IDS.length;index++){tab(index);screen(index);}tab(-1);
        for(String group:GROUPS)if(!group.equals("todo_runtime"))expected.put(group,business(group));unchanged("baseline");exercise();
        result.putString("result","PASS: "+checks+" module endurance checks, 10 rounds / 3 recreations / 2 changing gates, "+((SystemClock.elapsedRealtime()-started)/1000)+" s ("+args.getString("language","zh-CN")+")");
    }catch(Throwable failure){StringWriter log=new StringWriter();failure.printStackTrace(new PrintWriter(log));result.putString("result","FAIL: "+log);}finally{
        try{restore();}catch(Throwable failure){StringWriter log=new StringWriter();failure.printStackTrace(new PrintWriter(log));result.putString("result","FAIL: fixture restore\n"+log+"\n"+result.getString("result"));}finish(Activity.RESULT_OK,result);
    }}
}
