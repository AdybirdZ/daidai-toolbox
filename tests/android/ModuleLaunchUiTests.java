package org.simpleweather.qa;

import android.app.*;
import android.content.*;
import android.os.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/** Real first-launch lifecycle and recovery, using the signed random app fixture. */
public final class ModuleLaunchUiTests extends Instrumentation {
    private static final String ID="random",MAIN="org.simpleweather.app.MainActivity";
    private Activity activity;private Context context;private ClassLoader host;private Object apps;private Bundle args;private int checks;
    private final Map<String,Map<String,?>> preferences=new LinkedHashMap<>();private final Map<File,byte[]> files=new LinkedHashMap<>();
    public void onCreate(Bundle value){super.onCreate(value);args=value;start();}
    private Class<?> type(String name)throws Exception{return Class.forName("org.simpleweather.app."+name,true,host);}
    private Object call(Object owner,String name,Class<?>[] signature,Object...values)throws Exception{Class<?> c=owner instanceof Class?(Class<?>)owner:owner.getClass();Method m=c.getDeclaredMethod(name,signature);m.setAccessible(true);return m.invoke(owner instanceof Class?null:owner,values);}
    private Object field(Object owner,String name)throws Exception{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
    private void ui(Runnable action){AtomicReference<Throwable> error=new AtomicReference<>();runOnMainSync(()->{try{action.run();}catch(Throwable e){error.set(e);}});if(error.get()!=null)throw new RuntimeException(error.get());waitForIdleSync();}
    private void check(boolean pass,String label){if(!pass)throw new AssertionError(label);checks++;}
    private SharedPreferences state(){return context.getSharedPreferences("apps_host",0);}
    private boolean launching(){return state().getBoolean("launching_"+ID,false);}
    private boolean pending(){return state().getBoolean("pending_"+ID,false);}
    private File current(){return new File(context.getFilesDir(),"apps/"+ID+".ddapp");}
    private File previous(){return new File(context.getFilesDir(),"apps/"+ID+".previous.ddapp");}
    private void tab(int index){ui(()->{try{call(activity,"switchTab",new Class[]{int.class},index);}catch(Exception e){throw new RuntimeException(e);}});}
    private void install()throws Exception{try(InputStream in=new FileInputStream("/data/local/tmp/internal-apps/"+ID+".ddapp")){call(apps,"install",new Class[]{InputStream.class,String.class,String.class},in,ID,null);}}
    private void update()throws Exception{tab(-1);call(type("AppModules"),"markLaunchSucceeded",new Class[]{Context.class,String.class},context,ID);install();check(pending()&&previous().isFile(),"update retains pending version and previous executable");}
    private void preserved(String stage)throws Exception{check(!launching(),stage+": incomplete attempt is no longer a crash marker");check(pending()&&previous().isFile(),stage+": unconfirmed update and previous executable retained");check(!(Boolean)call(apps,"reconcile",new Class[]{}),stage+": normal navigation never triggers recovery");check(previous().isFile(),stage+": recovery did not consume previous executable");}
    private void recreate()throws Exception{Activity old=activity;ActivityMonitor monitor=addMonitor(MAIN,null,false);Activity next;try{ui(old::recreate);next=monitor.waitForActivityWithTimeout(10000);}finally{removeMonitor(monitor);}check(next!=null&&next!=old,"Activity recreates");activity=next;waitForIdleSync();apps=field(activity,"apps");}
    private void foreground()throws Exception{context.startActivity(new Intent().setClassName(context,MAIN).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT|Intent.FLAG_ACTIVITY_SINGLE_TOP));long until=SystemClock.elapsedRealtime()+10000;while(!activity.hasWindowFocus()&&SystemClock.elapsedRealtime()<until)SystemClock.sleep(30);waitForIdleSync();check(activity.hasWindowFocus(),"existing task returns to foreground");}
    private void behavior()throws Exception{
        install();update();tab(6);check(launching(),"first render starts tracked attempt");tab(-1);preserved("return to desktop within one second");
        tab(6);check(launching(),"reopening retries confirmation");check(!(Boolean)call(apps,"reconcile",new Class[]{}),"same-process Activity creation does not treat live attempt as crash");check(previous().isFile(),"same-process reconcile retains backup");recreate();check(previous().isFile()&&pending(),"normal recreation keeps updated executable");tab(-1);preserved("normal recreation");
        tab(6);check(launching(),"foreground retry starts attempt");ui(()->check(activity.moveTaskToBack(true),"task moves to background"));SystemClock.sleep(1300);preserved("normal Home exit before confirmation");foreground();check(launching(),"resume starts a fresh attempt");SystemClock.sleep(1500);waitForIdleSync();check(!launching()&&!pending()&&!previous().exists(),"resumed first draw plus stable second confirms update");
        update();String first=(String)call(type("AppModules"),"beginTrackedLaunch",new Class[]{Context.class,String.class},context,ID),second=(String)call(type("AppModules"),"beginTrackedLaunch",new Class[]{Context.class,String.class},context,ID);
        check(first!=null&&second!=null&&!first.equals(second),"each attempt owns a distinct token");call(type("AppModules"),"cancelLaunch",new Class[]{Context.class,String.class,String.class},context,ID,first);check(launching(),"stale cancellation cannot clear a new attempt");call(type("AppModules"),"markLaunchSucceeded",new Class[]{Context.class,String.class,String.class},context,ID,first);check(pending()&&previous().isFile(),"stale success cannot confirm a later attempt");call(type("AppModules"),"cancelLaunch",new Class[]{Context.class,String.class,String.class},context,ID,second);preserved("token cancellation");
        // Simulate the durable marker a terminated process leaves; execute real reconciliation.
        call(type("AppModules"),"beginLaunch",new Class[]{Context.class,String.class},context,ID);state().edit().putString("launch_process_"+ID,"qa-terminated-process").commit();check(launching(),"unconfirmed terminated launch remains durable");String expected=(String)call(type("AppModules"),"sha",new Class[]{File.class},previous());check((Boolean)call(apps,"reconcile",new Class[]{}),"next-process marker restores previous signed executable");check(expected.equals(call(type("AppModules"),"sha",new Class[]{File.class},current())),"recovery installs the retained executable");check(!launching()&&!pending()&&!previous().exists(),"recovery clears launch/update state atomically");
        update();tab(6);SystemClock.sleep(1500);waitForIdleSync();check(!pending()&&!previous().exists(),"ordinary foreground first launch confirms normally");
    }
    private void restore()throws Exception{
        if(activity!=null){tab(-1);ui(()->{try{call(activity,"releaseModule",new Class[]{String.class},ID);}catch(Exception e){throw new RuntimeException(e);}activity.finish();});waitForIdleSync();}
        boolean changing=false;try{call(type("ModuleRuntime"),"beginChange",new Class[]{Context.class,String.class},context,ID);changing=true;for(Map.Entry<File,byte[]> entry:files.entrySet()){Files.deleteIfExists(entry.getKey().toPath());if(entry.getValue()!=null){Files.write(entry.getKey().toPath(),entry.getValue());if(!entry.getKey().setReadOnly())throw new IOException("Cannot protect restored package");}}}finally{if(changing)call(type("ModuleRuntime"),"endChange",new Class[]{String.class},ID);}
        for(Map.Entry<String,Map<String,?>> entry:preferences.entrySet())call(type("ConfigurationTransfer"),"write",new Class[]{SharedPreferences.class,Map.class},context.getSharedPreferences(entry.getKey(),0),entry.getValue());
    }
    public void onStart(){Bundle result=new Bundle();try{
        context=getTargetContext();host=context.getClassLoader();check(Build.HARDWARE.contains("ranchu")||Build.HARDWARE.contains("goldfish"),"isolated emulator required");for(String group:new String[]{"toolbox","apps_host","random_choice"})preferences.put(group,new HashMap<>(context.getSharedPreferences(group,0).getAll()));for(File file:new File[]{current(),previous()})files.put(file,file.isFile()?Files.readAllBytes(file.toPath()):null);
        context.getSharedPreferences("toolbox",0).edit().putString("app_language",args.getString("language","zh-CN")).putInt("seen_version",Integer.MAX_VALUE).putBoolean("keep_screen_awake",true).commit();activity=startActivitySync(new Intent().setClassName(context,MAIN).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();apps=field(activity,"apps");behavior();result.putString("result","PASS: "+checks+" launch lifecycle checks ("+args.getString("language","zh-CN")+")");
    }catch(Throwable failure){StringWriter log=new StringWriter();failure.printStackTrace(new PrintWriter(log));result.putString("result","FAIL: "+log);}finally{try{restore();}catch(Throwable failure){result.putString("result","FAIL: fixture restore "+failure+"\n"+result.getString("result"));}finish(Activity.RESULT_OK,result);}}
}
