package org.simpleweather.qa;

import android.app.*;
import android.content.*;
import android.location.Location;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/** Runs the optional code/resources boundary and missing cross-app dependency paths. */
public final class ArchitectureUiTests extends Instrumentation {
    private Activity activity;private Context context;private ClassLoader host;private Object apps;private int checks;private Bundle args;
    private final String[] ids={"weather","schedule","status","weather-map","astronomy","earthquake","random","todo"};
    private final Map<String,Boolean> originallyInstalled=new LinkedHashMap<>();
    public void onCreate(Bundle args){super.onCreate(args);this.args=args;start();}
    private Class<?> type(String name)throws Exception{return Class.forName("org.daidai.toolbox."+name,true,host);}
    private Object field(Object object,String name)throws Exception{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    private Object call(Object object,String name,Class<?>[] types,Object...values)throws Exception{Class<?> c=object instanceof Class?(Class<?>)object:object.getClass();Method method=c.getDeclaredMethod(name,types);method.setAccessible(true);return method.invoke(object instanceof Class?null:object,values);}
    private void ui(Runnable task){AtomicReference<Throwable> error=new AtomicReference<>();runOnMainSync(()->{try{task.run();}catch(Throwable failure){error.set(failure);}});if(error.get()!=null)throw new RuntimeException(error.get());waitForIdleSync();}
    private void check(boolean condition,String label){if(!condition)throw new AssertionError(label);checks++;}
    private void tab(int index){ui(()->{try{call(activity,"switchTab",new Class[]{int.class},index);}catch(Exception failure){throw new RuntimeException(failure);}});}
    private boolean installed(String id)throws Exception{return (Boolean)call(apps,"installed",new Class[]{String.class},id);}
    private void install(String id)throws Exception{try(InputStream input=new FileInputStream("/data/local/tmp/internal-apps/"+id+".ddapp")){call(apps,"install",new Class[]{InputStream.class,String.class,String.class},input,id,null);}}
    private void uninstall(String id)throws Exception{ui(()->{try{call(apps,"uninstall",new Class[]{String.class},id);}catch(Exception failure){throw new RuntimeException(failure);}});long until=SystemClock.elapsedRealtime()+12000;while((Boolean)field(apps,"busy")&&SystemClock.elapsedRealtime()<until)Thread.sleep(20);check(!(Boolean)field(apps,"busy"),"uninstall completes "+id);waitForIdleSync();check(!installed(id),"uninstalled "+id);}
    private List<TextView> views(View root){List<TextView> output=new ArrayList<>();collect(root,output);return output;}
    private void collect(View root,List<TextView> output){if(root instanceof TextView)output.add((TextView)root);if(root instanceof ViewGroup)for(int n=0;n<((ViewGroup)root).getChildCount();n++)collect(((ViewGroup)root).getChildAt(n),output);}
    private boolean visible(String text){for(TextView v:views(activity.getWindow().getDecorView()))if(v.getText().toString().equals(text))return true;return false;}
    private String text(String resource)throws Exception{int id=type("R$string").getField(resource).getInt(null);return activity.getString(id);}
    private List<AlertDialog> dialogs()throws Exception{Field open=type("ToolDialog").getDeclaredField("OPEN");open.setAccessible(true);List<AlertDialog> result=new ArrayList<>();for(Object value:new ArrayList<>(((Map<?,?>)open.get(null)).keySet()))if(value instanceof AlertDialog&&((AlertDialog)value).isShowing())result.add((AlertDialog)value);return result;}
    private void closeDialogs(){ui(()->{try{call(type("ToolDialog"),"closeAll",new Class[]{});}catch(Exception failure){throw new RuntimeException(failure);}});}
    private void settings(int index)throws Exception{
        closeDialogs();ActivityMonitor monitor=addMonitor("org.daidai.toolbox.SettingsActivity",null,false);Activity page;
        try{ui(()->{try{String id=ids[index];if(index==0||index==2||index==3||index==4)call(type("FeatureAccess"),"settings",new Class[]{type("MainActivity"),String.class},activity,id);else call(activity,index==1?"scheduleSettings":index==5?"earthquakeSettings":index==6?"randomChoiceSettings":"todoSettings",new Class[]{});}catch(Exception failure){throw new RuntimeException(failure);}});page=monitor.waitForActivityWithTimeout(10000);}finally{removeMonitor(monitor);}
        check(page!=null,"settings opens from installed executable "+ids[index]);waitForIdleSync();check(views(page.getWindow().getDecorView()).size()>3,"settings resolves mounted resources "+ids[index]);final Activity opened=page;ui(opened::finish);waitForIdleSync();
    }
    private void recreate()throws Exception{
        ActivityMonitor monitor=addMonitor("org.daidai.toolbox.MainActivity",null,false);ui(()->activity.recreate());Activity next=monitor.waitForActivityWithTimeout(12000);removeMonitor(monitor);check(next!=null&&next!=activity,"host Activity recreated");activity=next;waitForIdleSync();apps=field(activity,"apps");
    }
    private void codeBoundary()throws Exception{
        for(String name:new String[]{"WeatherScreen","WeatherApi","WeatherIcon","WeatherSettings","GpsScreen","StatusHardware","StatusViews","StatusPlaces","StatusTransfer","StatusSettings","CameraLightReader","CameraExposure","BatteryReadings","RouteStore","RouteCanvas","RouteDrawing","RoutePlayback","RouteViewport","Astronomy","AstronomyScreen","AstronomySettings","SkyGeometry","SkyVisibility","SkyPointing","EarthquakeScreen","EarthquakeApi","EarthquakeCache","EarthquakeData","EarthquakeInfo","EarthquakeIntensity","EarthquakeAgencies","EarthquakeLocation","RandomChoiceScreen","TodoScreen","TodoSchedulerImpl","ScheduleScreen","ScheduleNotifierImpl","MapScreen","MapSettings"}){
            try{type(name);throw new AssertionError("Optional code remains in host: "+name);}catch(ClassNotFoundException expected){checks++;}
        }
        for(int index=0;index<ids.length;index++){install(ids[index]);check(installed(ids[index]),"signed application installed "+ids[index]);tab(index);Map<?,?> screens=(Map<?,?>)field(activity,"screens");Object handle=screens.get(index);check(handle!=null,"screen rendered "+ids[index]);Object target=field(handle,"target");check(target.getClass().getClassLoader()!=host,"screen uses installed loader "+ids[index]);check(views(activity.getWindow().getDecorView()).size()>6,"resource-backed screen "+ids[index]);LinearLayout navigation=(LinearLayout)field(activity,"navigation");check(navigation.getChildCount()==1,"in-app bottom bar has only Home "+ids[index]);check(((TextView)navigation.getChildAt(0)).getText().toString().equals(text("launcher_home")),"Home label is localized "+ids[index]);settings(index);tab(-1);}
        recreate();for(int index=0;index<ids.length;index++){tab(index);check(((Map<?,?>)field(activity,"screens")).get(index)!=null,"screen renders after Activity recreation "+ids[index]);}tab(-1);
    }
    private void dependencies()throws Exception{
        uninstall("weather");uninstall("status");tab(4);check(((Map<?,?>)field(activity,"screens")).get(4)!=null,"astronomy remains available without weather");
        ui(()->{try{call(activity,"showPicker",new Class[]{});}catch(Exception failure){throw new RuntimeException(failure);}});List<AlertDialog> showing=dialogs();check(showing.size()==1,"manual reference entry opens without weather");AlertDialog reference=showing.get(0);List<EditText> inputs=new ArrayList<>();for(TextView v:views(reference.getWindow().getDecorView()))if(v instanceof EditText)inputs.add((EditText)v);check(inputs.size()==2,"manual longitude and latitude fields available");ui(()->{inputs.get(0).setText("120.12345");inputs.get(1).setText("31.23456");reference.getButton(AlertDialog.BUTTON_POSITIVE).performClick();});Object city=field(activity,"city");check(Math.abs(city.getClass().getField("longitude").getDouble(city)-120.12345)<.000001,"manual longitude applied");check(Math.abs(city.getClass().getField("latitude").getDouble(city)-31.23456)<.000001,"manual latitude applied");check(((Map<?,?>)field(activity,"screens")).get(4)!=null,"star chart renders after manual reference change");ui(()->{try{Field data=activity.getClass().getField("data");data.set(activity,null);}catch(Exception failure){throw new RuntimeException(failure);}});tab(4);check(visible(text("cross_weather_unavailable")),"unavailable weather is explained without disabling astronomy");
        tab(5);Object quake=field(((Map<?,?>)field(activity,"screens")).get(5),"target");Object location=field(quake,"location");check(location.getClass().getClassLoader()!=host,"earthquake location entry lives in earthquake app");check(!installed("status"),"earthquake does not reinstall status");Method start=location.getClass().getMethod("start"),cancel=location.getClass().getMethod("cancel");check(start.getReturnType()==Void.TYPE&&cancel.getReturnType()==Void.TYPE,"earthquake offers independent fix and cancellation entry points");ui(()->{try{cancel.invoke(location);}catch(Exception failure){throw new RuntimeException(failure);}});
        tab(-1);closeDialogs();Object settings=type("SettingsScreen").getConstructor(type("MainActivity")).newInstance(activity);ui(()->{try{call(settings,"show",new Class[]{});}catch(Exception failure){throw new RuntimeException(failure);}});showing=dialogs();check(showing.size()==1,"settings opens with weather/status absent");List<String> labels=new ArrayList<>();for(TextView v:views(showing.get(0).getWindow().getDecorView()))labels.add(v.getText().toString());check(!labels.contains(text("tab_weather")),"uninstalled weather settings omitted");check(!labels.contains(text("tab_status")),"uninstalled status settings omitted");check(labels.contains(text("tab_astronomy")),"installed astronomy settings retained");closeDialogs();
        recreate();tab(4);check(((Map<?,?>)field(activity,"screens")).get(4)!=null,"astronomy still renders after missing dependency recreation");settings(4);tab(-1);
        uninstall("astronomy");recreate();check(!installed("astronomy"),"optional app remains uninstalled after recreation");install("astronomy");tab(4);check(((Map<?,?>)field(activity,"screens")).get(4)!=null,"resources remount after reinstall");settings(4);tab(-1);
        install("weather");install("status");tab(0);check(((Map<?,?>)field(activity,"screens")).get(0)!=null,"weather resources remount");tab(2);check(((Map<?,?>)field(activity,"screens")).get(2)!=null,"status resources remount");tab(-1);
    }
    private void resourceGenerations()throws Exception{
        Class<?> assets=type("ModuleAssets");android.content.res.Resources mounted=(android.content.res.Resources)call(assets,"resources",new Class[]{Context.class,android.content.res.Resources.class},context,context.getResources());
        File weather=(File)call(assets,"resourceFile",new Class[]{Context.class,String.class},context,"weather"),map=(File)call(assets,"resourceFile",new Class[]{Context.class,String.class},context,"weather-map");
        call(assets,"changed",new Class[]{String.class},"weather");check(!weather.exists(),"updated app resource snapshot is removed");check(map.isFile(),"weather update preserves weather-map resource snapshot");
        try(InputStream input=mounted.getAssets().open("cities.json")){check(input.read()!=-1,"already mounted assets remain readable after snapshot removal");}
        check(((File)call(assets,"resourceFile",new Class[]{Context.class,String.class},context,"weather")).isFile(),"current resource generation can be mounted again");
    }
    public void onStart(){Bundle result=new Bundle();Map<String,?> originalToolbox=null,originalWeather=null;try{
        context=getTargetContext();host=context.getClassLoader();originalToolbox=new HashMap<>(context.getSharedPreferences("toolbox",0).getAll());originalWeather=new HashMap<>(context.getSharedPreferences("weather",0).getAll());context.getSharedPreferences("toolbox",0).edit().putString("app_language",args.getString("language","zh-CN")).putInt("seen_version",Integer.MAX_VALUE).putLong("quake_interval",0).commit();activity=startActivitySync(new Intent().setClassName(context,"org.daidai.toolbox.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();apps=field(activity,"apps");for(String id:ids)originallyInstalled.put(id,installed(id));codeBoundary();resourceGenerations();dependencies();result.putString("result","PASS: "+checks+" modular architecture checks ("+args.getString("language","zh-CN")+")");
    }catch(Throwable failure){StringWriter log=new StringWriter();failure.printStackTrace(new PrintWriter(log));result.putString("result","FAIL: "+log);}finally{try{closeDialogs();if(activity!=null)tab(-1);for(Map.Entry<String,Boolean> e:originallyInstalled.entrySet())if(e.getValue()&&!installed(e.getKey()))install(e.getKey());else if(!e.getValue()&&installed(e.getKey()))uninstall(e.getKey());if(originalToolbox!=null)call(type("ConfigurationTransfer"),"write",new Class[]{SharedPreferences.class,Map.class},context.getSharedPreferences("toolbox",0),originalToolbox);if(originalWeather!=null)call(type("ConfigurationTransfer"),"write",new Class[]{SharedPreferences.class,Map.class},context.getSharedPreferences("weather",0),originalWeather);}catch(Throwable failure){result.putString("result",result.getString("result")+"\nFixture restore failed: "+failure);}if(activity!=null)ui(()->activity.finish());finish(Activity.RESULT_OK,result);}}
}
