package org.simpleweather.qa;

import android.app.*;
import android.content.*;
import android.content.res.*;
import android.location.Location;
import android.os.*;
import android.os.strictmode.DiskReadViolation;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.*;

/** Regressions for live UI resource I/O and redundant position-driven catalogue work. */
public final class Performance301UiTests extends Instrumentation {
    private Context context;private Activity activity;private ClassLoader host;private Object apps;private Bundle arguments;
    private int checks;private long resourceNanos;private Map<String,?> originalPreferences;private Object originalFix;
    private final Map<String,Boolean> installedBefore=new LinkedHashMap<>();
    public void onCreate(Bundle args){super.onCreate(args);arguments=args;start();}
    private Class<?> type(String name)throws Exception{return Class.forName("org.daidai.toolbox."+name,true,host);}
    private Object field(Object target,String name)throws Exception{Field f=(target instanceof Class?(Class<?>)target:target.getClass()).getDeclaredField(name);f.setAccessible(true);return f.get(target instanceof Class?null:target);}
    private void set(Object target,String name,Object value)throws Exception{Field f=(target instanceof Class?(Class<?>)target:target.getClass()).getDeclaredField(name);f.setAccessible(true);f.set(target instanceof Class?null:target,value);}
    private Object call(Object target,String name,Class<?>[] signature,Object...args)throws Exception{Method m=(target instanceof Class?(Class<?>)target:target.getClass()).getDeclaredMethod(name,signature);m.setAccessible(true);return m.invoke(target instanceof Class?null:target,args);}
    private void ui(Runnable action){AtomicReference<Throwable> error=new AtomicReference<>();runOnMainSync(()->{try{action.run();}catch(Throwable e){error.set(e);}});if(error.get()!=null)throw new RuntimeException(error.get());waitForIdleSync();}
    private void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
    private void tab(int index){ui(()->{try{call(activity,"switchTab",new Class[]{int.class},index);}catch(Exception e){throw new RuntimeException(e);}});}
    private Object screen(int index)throws Exception{return field(((Map<?,?>)field(activity,"screens")).get(index),"target");}
    private void install(String id)throws Exception{try(InputStream in=new FileInputStream("/data/local/tmp/internal-apps/"+id+".ddapp")){call(apps,"install",new Class[]{InputStream.class,String.class,String.class},in,id,null);}}
    private boolean installed(String id)throws Exception{return (Boolean)call(apps,"installed",new Class[]{String.class},id);}
    private void resourceHotPath(){ui(()->{try{
        Resources warm=activity.getResources();Method dp=activity.getClass().getMethod("dp",float.class);dp.invoke(activity,12f);
        int title=type("R$string").getField("tab_status").getInt(null);warm.getString(title);
        AtomicInteger diskReads=new AtomicInteger();StrictMode.ThreadPolicy before=StrictMode.getThreadPolicy();
        StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder().detectDiskReads().penaltyListener(Runnable::run,v->{if(v instanceof DiskReadViolation)diskReads.incrementAndGet();}).build());
        try{long start=SystemClock.elapsedRealtimeNanos();for(int n=0;n<1000;n++){check(activity.getResources()==warm,"mounted Resources reused "+n);dp.invoke(activity,12f);activity.getString(title);}resourceNanos=SystemClock.elapsedRealtimeNanos()-start;}
        finally{StrictMode.setThreadPolicy(before);}
        check(diskReads.get()==0,"warm resource/dp/string lookups perform no package filesystem reads: "+diskReads.get());
        call(type("ModuleAssets"),"invalidate",new Class[]{});Resources remounted=activity.getResources();check(remounted!=warm,"managed generation invalidation remounts Resources");check(remounted.getString(title).equals(warm.getString(title)),"resource invalidation retains correct translated content");
        Configuration config=new Configuration(context.getResources().getConfiguration());config.setLocales(new LocaleList(Locale.ENGLISH));Context english=context.createConfigurationContext(config);
        Resources localized=(Resources)call(type("ModuleAssets"),"resources",new Class[]{Context.class,Resources.class},english,english.getResources());
        int back=type("R$string").getField("action_back").getInt(null);check(localized.getString(back).equals("Back"),"configuration-specific resource cache keeps English separate");
    }catch(Exception e){throw new RuntimeException(e);}});}
    private void declinationCache()throws Exception{
        tab(2);Object gps=screen(2),hardware=field(gps,"hardware");Location fix=new Location("gps");fix.setLatitude(31.3);fix.setLongitude(120.6);fix.setAltitude(20);
        ui(()->{try{double first=(Double)call(hardware,"declination",new Class[]{Location.class},fix);long at=(Long)field(hardware,"declinationAt");for(int n=0;n<100;n++)check((Double)call(hardware,"declination",new Class[]{Location.class},new Location(fix))==first,"unchanged position reuses geomagnetic model "+n);check((Long)field(hardware,"declinationAt")==at,"model is not recalculated for every displayed card");Location moved=new Location(fix);moved.setLongitude(140);double changed=(Double)call(hardware,"declination",new Class[]{Location.class},moved);check(Math.abs(first-changed)>.01,"position changes invalidate geomagnetic result");check(Double.isNaN((Double)call(hardware,"declination",new Class[]{Location.class},new Object[]{null})),"missing position never reuses a valid declination");}catch(Exception e){throw new RuntimeException(e);}});
    }
    private void resourceLocaleIsolation(){ui(()->{try{
        Resources original=(Resources)call(type("ModuleRuntime"),"resources",new Class[]{Context.class,String.class},context,"music");int label=original.getIdentifier("music_tab_settings","string","org.simpleweather.module.music");check(label!=0,"music locale fixture has a settings label");
        String before=original.getString(label);boolean english=arguments.getString("language","zh-CN").equals("en");check(before.equals(english?"Settings":"设置"),"module begins in selected host language");
        Class<?> wrapper=type("ModuleRuntime$ModuleContext");Constructor<?> constructor=wrapper.getDeclaredConstructor(Context.class,String.class,Resources.class);constructor.setAccessible(true);Context module=(Context)constructor.newInstance(context,"music",original);
        Configuration alternate=new Configuration();alternate.setLocales(new LocaleList(english?Locale.SIMPLIFIED_CHINESE:Locale.ENGLISH));Context other=module.createConfigurationContext(alternate);
        check(other.getResources().getAssets()!=original.getAssets(),"configuration contexts isolate AssetManager locale state");check(other.getString(label).equals(english?"设置":"Settings"),"explicit alternate locale resolves translated text");
        Configuration fontOnly=new Configuration();fontOnly.fontScale=1.25f;Context font=module.createConfigurationContext(fontOnly);check(font.getString(label).equals(before),"partial widget override retains module language");check(font.getResources().getAssets()!=original.getAssets(),"partial overrides also own separate AssetManager");
        for(int n=0;n<50;n++){check(original.getString(label).equals(before),"parent language stable after alternate/font context "+n);check(other.getString(label).equals(english?"设置":"Settings"),"alternate language stable during parent reads "+n);}
    }catch(Exception e){throw new RuntimeException(e);}});}
    private void referenceWork()throws Exception{
        tab(5);Object quake=screen(5);ClassLoader module=quake.getClass().getClassLoader();Map<String,Object> live=(Map<String,Object>)field(quake,"data");Map<String,Object> saved=new HashMap<>(live);
        try{ui(()->{try{
            call(quake,"pause",new Class[]{});set(quake,"visible",true);live.clear();Class<?> entryType=Class.forName("org.daidai.toolbox.EarthquakeCache$Entry",true,module),reportType=Class.forName("org.daidai.toolbox.EarthquakeData$Report",true,module);
            Object entry=entryType.getConstructor().newInstance();List<Object> reports=new ArrayList<>();set(entry,"reports",reports);long now=System.currentTimeMillis();
            for(int n=0;n<200;n++){Object report=reportType.getConstructor().newInstance();set(report,"source","GEOFON");set(report,"id","performance-"+n);set(report,"time",now-n*60000);set(report,"updated",now);set(report,"latitude",31.0+n*.001);set(report,"longitude",120.0);set(report,"magnitude",3.0);set(report,"depth",10.0);set(report,"region","Fixture");reports.add(report);}live.put("GEOFON",entry);
            Location fix=new Location("gps");fix.setLatitude(31.3);fix.setLongitude(120.6);fix.setTime(now);set(type("LocationService"),"latest",fix);call(quake,"updateList",new Class[]{});
            LinearLayout list=(LinearLayout)field(quake,"listBox");check(list.getChildCount()>3,"fixture builds visible earthquake catalogue");Object rows=field(quake,"mergedEvents");View first=list.getChildAt(0);Map<?,?> distanceRows=(Map<?,?>)field(quake,"distanceRows");check(!distanceRows.isEmpty(),"visible rows expose independently refreshed distance labels");TextView distance=(TextView)distanceRows.values().iterator().next();String beforeDistance=distance.getText().toString();
            for(int n=1;n<=10;n++){Location same=new Location(fix);same.setTime(now+n*1000);set(type("LocationService"),"latest",same);call(quake,"updateReferenceList",new Class[]{});check(list.getChildAt(0)==first,"same coordinates/new timestamp does not rebuild rows "+n);check(field(quake,"mergedEvents")==rows,"same coordinates do not merge catalogue again "+n);}
            Location changed=new Location(fix);changed.setLongitude(121.2);set(type("LocationService"),"latest",changed);call(quake,"updateReferenceList",new Class[]{});check(list.getChildAt(0)==first,"changed reference preserves time-sorted row hierarchy");check(!distance.getText().toString().equals(beforeDistance),"changed reference refreshes displayed distances in place");check(field(quake,"mergedEvents")==rows,"new reference reuses unchanged merged catalogue");
            call(quake,"updateReferenceList",new Class[]{});View after=list.getChildAt(0);call(quake,"updateReferenceList",new Class[]{});check(list.getChildAt(0)==after,"completed one-shot fix is not rebuilt again next tick");
            context.getSharedPreferences("toolbox",0).edit().putInt("quake_sort",2).apply();call(quake,"updateList",new Class[]{});View sorted=list.getChildAt(0);changed.setLongitude(122);set(type("LocationService"),"latest",changed);call(quake,"updateReferenceList",new Class[]{});check(list.getChildAt(0)!=sorted,"distance-sorted catalogue recomputes order for changed coordinates");check(field(quake,"mergedEvents")==rows,"distance sorting also reuses merged reports");
        }catch(Exception e){throw new RuntimeException(e);}});}finally{ui(()->{try{live.clear();live.putAll(saved);set(type("LocationService"),"latest",originalFix);call(quake,"updateList",new Class[]{});call(quake,"resume",new Class[]{});}catch(Exception e){throw new RuntimeException(e);}});}
    }
    public void onStart(){Bundle result=new Bundle();try{
        context=getTargetContext();host=context.getClassLoader();SharedPreferences prefs=context.getSharedPreferences("toolbox",0);originalPreferences=new HashMap<>(prefs.getAll());originalFix=field(type("LocationService"),"latest");prefs.edit().putString("app_language",arguments.getString("language","zh-CN")).putInt("seen_version",Integer.MAX_VALUE).putLong("quake_interval",0).putBoolean("quake_manual_location",false).putBoolean("quake_show_2",true).putBoolean("quake_field_distance",true).putBoolean("quake_source_GEOFON",true).putInt("quake_sort",0).putInt("quake_area",0).putFloat("quake_global_min",1).putFloat("quake_regional_min",1).commit();
        activity=startActivitySync(new Intent().setClassName(context,"org.daidai.toolbox.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();apps=field(activity,"apps");
        // The host update intentionally preserves installed application versions.
        // Always update these fixtures so this regression exercises the new code,
        // rather than silently running a retained 3.0.0 signed application package.
        for(String id:new String[]{"status","earthquake","astronomy","music"}){installedBefore.put(id,installed(id));install(id);}
        tab(2);resourceHotPath();resourceLocaleIsolation();declinationCache();referenceWork();result.putString("result","PASS: "+checks+" live UI performance regressions; 1000 resource/dp/text rounds "+resourceNanos/1_000_000+" ms ("+arguments.getString("language","zh-CN")+")");
    }catch(Throwable failure){StringWriter log=new StringWriter();failure.printStackTrace(new PrintWriter(log));result.putString("result","FAIL: "+log);}finally{
        try{if(activity!=null){tab(-1);for(Map.Entry<String,Boolean> e:installedBefore.entrySet())if(!e.getValue()&&installed(e.getKey())){ui(()->{try{call(apps,"uninstall",new Class[]{String.class},e.getKey());}catch(Exception failure){throw new RuntimeException(failure);}});long until=SystemClock.elapsedRealtime()+10000;while((Boolean)field(apps,"busy")&&SystemClock.elapsedRealtime()<until)Thread.sleep(20);}}if(originalPreferences!=null)call(type("ConfigurationTransfer"),"write",new Class[]{SharedPreferences.class,Map.class},context.getSharedPreferences("toolbox",0),originalPreferences);if(host!=null)set(type("LocationService"),"latest",originalFix);if(activity!=null)ui(()->activity.finish());}catch(Throwable failure){result.putString("result",result.getString("result")+"\nRestore failed: "+failure);}
        finish(Activity.RESULT_OK,result);
    }}
}
