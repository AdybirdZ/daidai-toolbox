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
import org.json.*;

/** Regression coverage for legacy-package batches, stable viewports and uninstall navigation. */
public final class Store301UiTests extends Instrumentation {
    private Activity activity;private ClassLoader loader;private int checks;private Bundle args;
    public void onCreate(Bundle b){super.onCreate(b);args=b;start();}
    private Class<?> type(String n)throws Exception{return Class.forName("org.simpleweather.app."+n,true,loader);}
    private Object field(Object o,String n)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
    private Object call(Object o,String n,Class<?>[] types,Object...values)throws Exception{Class<?> c=o instanceof Class?(Class<?>)o:o.getClass();Method m=c.getDeclaredMethod(n,types);m.setAccessible(true);return m.invoke(o instanceof Class?null:o,values);}
    private void ui(Runnable r){AtomicReference<Throwable> error=new AtomicReference<>();runOnMainSync(()->{try{r.run();}catch(Throwable x){error.set(x);}});if(error.get()!=null)throw new RuntimeException(error.get());waitForIdleSync();}
    private void check(boolean value,String name){if(!value)throw new AssertionError(name);checks++;}
    private void install(Object apps,String id)throws Exception{try(InputStream in=new FileInputStream("/data/local/tmp/internal-apps/"+id+".ddapp")){call(apps,"install",new Class[]{InputStream.class,String.class,String.class},in,id,null);}}
    private byte[] read(File file)throws Exception{try(InputStream in=new FileInputStream(file);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))>=0)out.write(b,0,n);return out.toByteArray();}}
    private void write(File file,byte[] content)throws Exception{file.setWritable(true,true);try(OutputStream out=new FileOutputStream(file)){out.write(content);}file.setReadOnly();}
    private int top(View v){int[] p=new int[2];v.getLocationInWindow(p);return p[1];}
    private boolean update(Object apps,JSONObject item)throws Exception{return (Boolean)call(apps,"updateAvailable",new Class[]{JSONObject.class},item);}
    private JSONObject cloneItem(JSONObject item)throws Exception{return new JSONObject(item.toString());}
    private Set<String> batch(Object store)throws Exception{Set<String> ids=new LinkedHashSet<>();for(Object item:(List<?>)call(store,"availableUpdates",new Class[]{}))ids.add(((JSONObject)item).getString("id"));return ids;}
    private void layout(){ui(()->activity.getWindow().getDecorView().requestLayout());try{Thread.sleep(80);}catch(InterruptedException e){Thread.currentThread().interrupt();}waitForIdleSync();}
    public void onStart(){Bundle result=new Bundle();File legacyFile=null;byte[] original=null;try{
        Context c=getTargetContext();String language=args.getString("language","zh-CN");c.getSharedPreferences("toolbox",0).edit().putString("app_language",language).putInt("seen_version",Integer.MAX_VALUE).commit();loader=c.getClassLoader();
        activity=startActivitySync(new Intent().setClassName(c,"org.simpleweather.app.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();Object apps=field(activity,"apps");Class<?> modules=type("AppModules");
        install(apps,"weather");install(apps,"random");
        JSONObject weather=(JSONObject)call(modules,"inspect",new Class[]{Context.class,File.class,String.class},c,new File(c.getFilesDir(),"apps/weather.ddapp"),"weather");
        JSONObject random=(JSONObject)call(modules,"inspect",new Class[]{Context.class,File.class,String.class},c,new File(c.getFilesDir(),"apps/random.ddapp"),"random");
        JSONArray catalog=(JSONArray)call(apps,"bundledCatalog",new Class[]{});Map<String,JSONObject> byId=new HashMap<>();for(int n=0;n<catalog.length();n++)byId.put(catalog.getJSONObject(n).getString("id"),catalog.getJSONObject(n));
        JSONObject current=cloneItem(byId.get("weather")).put("versionCode",weather.getInt("versionCode"));
        JSONObject newer=cloneItem(current).put("versionCode",weather.getInt("versionCode")+1),older=cloneItem(current).put("versionCode",Math.max(0,weather.getInt("versionCode")-1));
        JSONObject incompatible=cloneItem(newer).put("minHostVersionCode",Integer.MAX_VALUE);
        check(!update(apps,current),"matching executable not offered an update");check(update(apps,newer),"new version included");check(!update(apps,older),"downgrade not offered");check(!update(apps,incompatible),"incompatible candidate excluded");check(!update(apps,cloneItem(newer).put("id","unknown")),"unknown identity excluded");
        legacyFile=new File(c.getFilesDir(),"apps/random.ddapp");original=read(legacyFile);write(legacyFile,"legacy package that cannot run on api-1".getBytes("UTF-8"));
        JSONObject repair=cloneItem(byId.get("random")).put("versionCode",random.getInt("versionCode"));check(!(Boolean)call(apps,"installed",new Class[]{String.class},"random"),"legacy executable is not runnable");check(update(apps,repair),"retained legacy executable included for replacement");
        final Object[] storeRef=new Object[1];ui(()->{try{Field busy=modules.getField("busy");busy.setBoolean(null,true);call(activity,"switchTab",new Class[]{int.class},-2);busy.setBoolean(null,false);storeRef[0]=field(activity,"appStoreScreen");}catch(Exception e){throw new RuntimeException(e);}});Object store=storeRef[0];
        JSONArray candidates=new JSONArray().put(newer).put(repair).put(repair).put(incompatible).put(cloneItem(newer).put("id","unknown"));
        ui(()->{try{call(store,"draw",new Class[]{JSONArray.class},candidates);}catch(Exception e){throw new RuntimeException(e);}});
        check(batch(store).equals(new LinkedHashSet<>(Arrays.asList("weather","random"))),"batch matches all offered compatible updates including legacy replacement and deduplicates");
        write(legacyFile,original);original=null;
        // List content remains fixed on screen while the progress and retry regions change height.
        ui(()->{try{call(store,"draw",new Class[]{JSONArray.class},catalog);}catch(Exception e){throw new RuntimeException(e);}});layout();ScrollView scroll=(ScrollView)field(activity,"pageScroll");LinearLayout list=(LinearLayout)field(store,"list");
        View anchor=list.findViewWithTag("earthquake");ui(()->scroll.scrollTo(0,anchor.getTop()+list.getTop()-24));layout();final int anchorTop=top(list.findViewWithTag("earthquake"));final List<Integer> frames=new ArrayList<>();ViewTreeObserver.OnDrawListener draw=()->{View card=list.findViewWithTag("earthquake");if(card!=null)frames.add(top(card));};ui(()->list.getViewTreeObserver().addOnDrawListener(draw));
        LinearLayout progress=(LinearLayout)field(store,"progressPanel");TextView progressText=(TextView)field(store,"progressText"),retry=(TextView)field(store,"retryButton");
        ui(()->{try{call(store,"preserveViewport",new Class[]{Runnable.class},(Runnable)()->{progress.setVisibility(View.VISIBLE);progressText.setText(String.join(" ",Collections.nCopies(50,"downloading")));});}catch(Exception e){throw new RuntimeException(e);}});layout();check(Math.abs(top(list.findViewWithTag("earthquake"))-anchorTop)<=1,"showing download progress retains visible card coordinate");
        ui(()->{try{call(store,"preserveViewport",new Class[]{Runnable.class},(Runnable)()->progressText.setText("verifying"));}catch(Exception e){throw new RuntimeException(e);}});layout();check(Math.abs(top(list.findViewWithTag("earthquake"))-anchorTop)<=1,"changing progress text height does not move visible card");
        ui(()->{try{call(store,"preserveViewport",new Class[]{Runnable.class},(Runnable)()->{progress.setVisibility(View.GONE);retry.setVisibility(View.VISIBLE);try{call(store,"draw",new Class[]{JSONArray.class},catalog);}catch(Exception e){throw new RuntimeException(e);}});}catch(Exception e){throw new RuntimeException(e);}});layout();check(Math.abs(top(list.findViewWithTag("earthquake"))-anchorTop)<=1,"completion redraw and retry preserve card coordinate");
        ui(()->list.getViewTreeObserver().removeOnDrawListener(draw));check(!frames.isEmpty(),"viewport frames observed");for(Integer y:frames)check(Math.abs(y-anchorTop)<=1,"no intermediate scroll jump before final position");
        Object owner=field(store,"owner");ui(()->{try{call(apps,"uninstall",new Class[]{String.class},"random");}catch(Exception e){throw new RuntimeException(e);}});long deadline=SystemClock.uptimeMillis()+10000;while((Boolean)modules.getField("busy").get(null)&&SystemClock.uptimeMillis()<deadline)Thread.sleep(20);layout();
        check(!(Boolean)modules.getField("busy").get(null),"uninstall completed");check((Integer)field(activity,"tab")==-2,"uninstall from store remains in store");check(owner==field(activity,"body"),"uninstall keeps mounted store hierarchy");check(!(Boolean)call(apps,"installed",new Class[]{String.class},"random"),"uninstalled executable removed");install(apps,"random");
        result.putString("result","PASS: "+checks+" store 3.0.1 regression checks ("+language+")");
    }catch(Throwable e){StringWriter trace=new StringWriter();e.printStackTrace(new PrintWriter(trace));result.putString("result","FAIL: "+trace);}finally{try{if(original!=null&&legacyFile!=null)write(legacyFile,original);}catch(Exception ignored){}try{type("AppModules").getField("busy").setBoolean(null,false);}catch(Exception ignored){}if(activity!=null)ui(()->activity.finish());finish(Activity.RESULT_OK,result);}}
}
