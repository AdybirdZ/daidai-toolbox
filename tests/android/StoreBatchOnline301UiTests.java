package org.simpleweather.qa;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import org.json.*;

/** Real update-all queue from signed 3.0.0 packages to the public 3.0.1 catalog. */
public final class StoreBatchOnline301UiTests extends Instrumentation {
    private static final String[] IDS={"weather","schedule","status","weather-map","astronomy","earthquake","random","todo","music","messaging","colorvision"};
    private Activity activity;private Context context;private ClassLoader loader;private Object apps,store;private Bundle args;private int checks;private long batchStarted;
    public void onCreate(Bundle value){super.onCreate(value);args=value;start();}
    private Class<?> type(String name)throws Exception{return Class.forName("org.simpleweather.app."+name,true,loader);}
    private Object field(Object owner,String name)throws Exception{Class<?> cls=owner instanceof Class?(Class<?>)owner:owner.getClass();for(;cls!=null;cls=cls.getSuperclass())try{Field f=cls.getDeclaredField(name);f.setAccessible(true);return f.get(owner instanceof Class?null:owner);}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(name);}
    private Object call(Object owner,String name,Class<?>[] signature,Object... values)throws Exception{Class<?> cls=owner instanceof Class?(Class<?>)owner:owner.getClass();for(;cls!=null;cls=cls.getSuperclass())try{Method m=cls.getDeclaredMethod(name,signature);m.setAccessible(true);return m.invoke(owner instanceof Class?null:owner,values);}catch(NoSuchMethodException ignored){}throw new NoSuchMethodException(name);}
    private void ui(Runnable task){AtomicReference<Throwable> failure=new AtomicReference<>();runOnMainSync(()->{try{task.run();}catch(Throwable error){failure.set(error);}});if(failure.get()!=null)throw new RuntimeException(failure.get());waitForIdleSync();}
    private void check(boolean value,String text){if(!value)throw new AssertionError(text);checks++;}
    private void await(BooleanSupplier condition,long timeout,String label)throws Exception{long until=SystemClock.elapsedRealtime()+timeout;while(SystemClock.elapsedRealtime()<until){if(condition.getAsBoolean()){checks++;return;}SystemClock.sleep(50);}throw new AssertionError(label+"; "+diagnostics());}
    private String diagnostics(){try{return "store="+(store==null?"not mounted":((TextView)field(store,"status")).getText())+", busy="+field(type("AppModules"),"busy")+", completed="+field(type("AppStoreScreen"),"lastSuccess")+", failed="+field(type("AppStoreScreen"),"retryItems");}catch(Exception error){return error.toString();}}
    private String text(String key){int id=activity.getResources().getIdentifier(key,"string",activity.getPackageName());if(id==0)throw new AssertionError("Unknown text "+key);return activity.getString(id);}
    private void collect(View view,List<View> result){result.add(view);if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)collect(((ViewGroup)view).getChildAt(i),result);}
    private TextView button(View root,String label){List<View> views=new ArrayList<>();collect(root,views);for(View value:views)if(value instanceof TextView&&value.isClickable()&&label.contentEquals(((TextView)value).getText()))return (TextView)value;throw new AssertionError("Missing action: "+label);}
    private Set<String> candidateIds(List<?> candidates)throws Exception{Set<String> ids=new LinkedHashSet<>();for(Object value:candidates)check(ids.add(((JSONObject)value).getString("id")),"batch candidate identities are unique");return ids;}
    private Map<String,JSONObject> catalog(JSONArray array)throws Exception{Map<String,JSONObject> entries=new LinkedHashMap<>();for(int i=0;i<array.length();i++){JSONObject entry=array.getJSONObject(i);check(entries.put(entry.getString("id"),entry)==null,"public catalog identity occurs only once");}return entries;}
    private boolean idle(){try{return !(Boolean)field(type("AppModules"),"busy")&&!(Boolean)field(type("AppStoreScreen"),"batchRunning");}catch(Exception failure){throw new RuntimeException(failure);}}
    private List<AlertDialog> dialogs()throws Exception{List<AlertDialog> result=new ArrayList<>();Map<?,?> open=(Map<?,?>)field(type("ToolDialog"),"OPEN");for(Object item:new ArrayList<>(open.keySet()))if(item instanceof AlertDialog&&((AlertDialog)item).isShowing())result.add((AlertDialog)item);return result;}
    private void installLegacy()throws Exception{
        for(String id:IDS){
            check(new File("/data/local/tmp/legacy-apps/"+id+".ddapp").isFile(),"signed legacy fixture exists: "+id);
            ui(()->{try{call(apps,"uninstall",new Class[]{String.class},id);}catch(Exception failure){throw new RuntimeException(failure);}});
            await(()->{try{return idle()&&!(Boolean)call(apps,"installed",new Class[]{String.class},id);}catch(Exception failure){throw new RuntimeException(failure);}},12000,"current app removed before installing legacy fixture: "+id);
            try(InputStream in=new FileInputStream("/data/local/tmp/legacy-apps/"+id+".ddapp")){call(apps,"install",new Class[]{InputStream.class,String.class,String.class},in,id,null);}
            JSONObject metadata=(JSONObject)call(type("AppModules"),"inspect",new Class[]{Context.class,File.class,String.class},context,new File(context.getFilesDir(),"apps/"+id+".ddapp"),id);
            check(metadata.getInt("versionCode")==5&&"3.0.0".equals(metadata.getString("version")),"actual signed 3.0.0 fixture installed: "+id);
            check("daidai-toolbox/api-1".equals(metadata.getString("hostApi")),"legacy package keeps the stable api-1 contract: "+id);
        }
    }
    private void updateFromPublicStore()throws Exception{
        JSONArray remote=(JSONArray)call(apps,"remoteCatalog",new Class[]{});Map<String,JSONObject> expected=catalog(remote);Set<String> identities=new LinkedHashSet<>(Arrays.asList(IDS));
        check(expected.keySet().equals(identities),"real public catalog contains exactly the eleven applications");
        for(String id:IDS){JSONObject item=expected.get(id);check(item.getInt("versionCode")==6&&"3.0.1".equals(item.getString("version")),"public version is 3.0.1: "+id);check(item.getString("url").contains("/modules/store/3.0.1/"),"update uses the published production package path: "+id);check((Boolean)call(apps,"updateAvailable",new Class[]{JSONObject.class},item),"legacy installed app is updateable: "+id);}
        ui(()->{try{call(activity,"switchTab",new Class[]{int.class},-2);}catch(Exception failure){throw new RuntimeException(failure);}});store=field(activity,"appStoreScreen");Object owner=field(activity,"body");
        await(()->{try{return text("module_catalog_online").contentEquals(((TextView)field(store,"status")).getText());}catch(Exception failure){throw new RuntimeException(failure);}},90000,"store itself finishes the real public catalog refresh");
        JSONArray displayed=(JSONArray)field(store,"catalog");check(catalog(displayed).keySet().equals(identities),"mounted store displays all eleven public entries");
        check(candidateIds((List<?>)call(store,"availableUpdates",new Class[]{})).equals(identities),"update-all contains every offered update before the batch");
        TextView update=button(activity.getWindow().getDecorView(),text("store_update_all"));ui(update::performClick);
        List<AlertDialog> confirmations=dialogs();check(confirmations.size()==1,"update-all opens its real confirmation rather than reporting no updates");AlertDialog confirm=confirmations.get(0);Button accept=confirm.getButton(AlertDialog.BUTTON_POSITIVE);check(accept!=null&&text("module_update").contentEquals(accept.getText()),"confirmation offers the update action");
        batchStarted=SystemClock.elapsedRealtime();ui(accept::performClick);check((Integer)field(type("AppStoreScreen"),"queueSize")==11,"real queue has exactly eleven entries");
        await(this::idle,600000,"public batch update completes within the bounded network allowance");waitForIdleSync();
        check((Integer)field(type("AppStoreScreen"),"lastSuccess")==11,"all eleven actual downloads and installs succeeded");check(((List<?>)field(type("AppStoreScreen"),"retryItems")).isEmpty(),"batch leaves no failed or skipped applications");check(!(Boolean)field(type("AppStoreScreen"),"lastCancelled"),"batch was not cancelled");
        check((Integer)field(activity,"tab")==-2,"batch completion stays in the application store");check(field(activity,"body")==owner&&field(activity,"appStoreScreen")==store,"batch retains the mounted store hierarchy");
        String complete=activity.getString(activity.getResources().getIdentifier("store_batch_complete","string",activity.getPackageName()),11);check(complete.contentEquals(((TextView)field(store,"status")).getText()),"store reports all eleven completed updates");
        for(String id:IDS){File file=new File(context.getFilesDir(),"apps/"+id+".ddapp");check(file.isFile()&&(Boolean)call(apps,"installed",new Class[]{String.class},id),"updated executable exists and remains installed: "+id);JSONObject metadata=(JSONObject)call(type("AppModules"),"inspect",new Class[]{Context.class,File.class,String.class},context,file,id);check(metadata.getInt("versionCode")==6&&"3.0.1".equals(metadata.getString("version")),"batch installed the actual target version: "+id);check(expected.get(id).getString("sha256").equals(call(type("AppModules"),"sha",new Class[]{File.class},file)),"installed bytes match the real public catalog: "+id);check(!(Boolean)call(apps,"updateAvailable",new Class[]{JSONObject.class},expected.get(id)),"updated app no longer offered an update: "+id);}
        check(((List<?>)call(store,"availableUpdates",new Class[]{})).isEmpty(),"no-update state is reached only after all updates succeed");int success=(Integer)field(type("AppStoreScreen"),"lastSuccess");ui(update::performClick);check(dialogs().isEmpty(),"second update-all has no confirmation when every app is current");check(idle()&&(Integer)field(type("AppStoreScreen"),"lastSuccess")==success,"no-update action does not start a phantom batch");
    }
    public void onStart(){Bundle result=new Bundle();try{
        context=getTargetContext();check(Build.HARDWARE.contains("ranchu")||Build.HARDWARE.contains("goldfish"),"isolated emulator required");String language=args.getString("language","zh-CN");context.getSharedPreferences("toolbox",0).edit().putString("app_language",language).putInt("seen_version",Integer.MAX_VALUE).commit();loader=context.getClassLoader();activity=startActivitySync(new Intent().setClassName(context,"org.simpleweather.app.MainActivity").putExtra("last_tab",-1).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();apps=field(activity,"apps");check((Integer)call(type("AppModules"),"hostVersion",new Class[]{Context.class},context)==57,"real 3.0.1 host installed");check(idle(),"store has no existing download task");installLegacy();updateFromPublicStore();result.putString("result","PASS: "+checks+" real public GitHub update-all checks ("+language+", batch "+((SystemClock.elapsedRealtime()-batchStarted)/1000)+" s)");
    }catch(Throwable failure){StringWriter trace=new StringWriter();failure.printStackTrace(new PrintWriter(trace));result.putString("result","FAIL: "+trace+"\n"+diagnostics());}finally{
        if(batchStarted>0&&store!=null&&!idle())try{TextView cancel=(TextView)field(store,"cancelButton");ui(cancel::performClick);await(this::idle,15000,"failed test cancels its owned download queue");}catch(Throwable cleanup){result.putString("result",result.getString("result")+"\nQueue cleanup: "+cleanup);}
        if(activity!=null)ui(activity::finish);finish(Activity.RESULT_OK,result);}}
}
