package org.simpleweather.qa;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.json.*;

/** Uses the production HTTPS downloader against the public GitHub store. */
public final class ModuleStoreOnlineTests extends Instrumentation {
    private Activity activity; private ClassLoader loader; private int checks; private Bundle args;
    @Override public void onCreate(Bundle a){super.onCreate(a);args=a;start();}
    private Class<?> type(String n)throws Exception{return Class.forName("org.simpleweather.app."+n,true,loader);}
    private Object field(Object o,String n)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
    private Object call(Object o,String n,Class<?>[] types,Object...v)throws Exception{Method m=o.getClass().getDeclaredMethod(n,types);m.setAccessible(true);return m.invoke(o,v);}
    private void ui(Runnable r){AtomicReference<Throwable> e=new AtomicReference<>();runOnMainSync(()->{try{r.run();}catch(Throwable t){e.set(t);}});if(e.get()!=null)throw new RuntimeException(e.get());waitForIdleSync();}
    private void check(boolean b,String label){if(!b)throw new AssertionError(label);checks++;}
    private void tab(int i){ui(()->{try{call(activity,"switchTab",new Class[]{int.class},i);}catch(Exception e){throw new RuntimeException(e);}});}
    private void collect(View v,List<String> out){if(v instanceof TextView)out.add(((TextView)v).getText().toString());if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)collect(((ViewGroup)v).getChildAt(i),out);}
    private List<String> text(){List<String> out=new ArrayList<>();collect(activity.getWindow().getDecorView(),out);return out;}
    @Override public void onStart(){Bundle result=new Bundle();try{
        String lang=args.getString("language","zh-CN");
        getTargetContext().getSharedPreferences("toolbox",0).edit().putString("app_language",lang).putInt("seen_version",52).commit();
        loader=getTargetContext().getClassLoader();activity=startActivitySync(new Intent().setClassName(getTargetContext(),"org.simpleweather.app.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();Object apps=field(activity,"apps");
        JSONArray catalog=(JSONArray)call(apps,"remoteCatalog",new Class[]{});check(catalog.length()==11,"public catalog includes eleven apps");
        String[] ids={"weather","schedule","status","weather-map","astronomy","earthquake","random","todo"};
        for(int index=0;index<ids.length;index++){
            String id=ids[index];JSONObject item=null;for(int n=0;n<catalog.length();n++)if(id.equals(catalog.getJSONObject(n).getString("id")))item=catalog.getJSONObject(n);check(item!=null,"public listing "+id);
            ui(()->{try{call(apps,"uninstall",new Class[]{String.class},id);}catch(Exception e){throw new RuntimeException(e);}});
            check(!(Boolean)call(apps,"installed",new Class[]{String.class},id),"app absent before download "+id);
            call(apps,"download",new Class[]{JSONObject.class},item);
            check((Boolean)call(apps,"installed",new Class[]{String.class},id),"public app downloaded and installed "+id);
            File file=new File(getTargetContext().getFilesDir(),"apps/"+id+".ddapp");
            check(item.getString("sha256").equals(type("AppModules").getMethod("sha",File.class).invoke(null,file)),"public download checksum "+id);
            check((android.system.Os.stat(file.getPath()).st_mode&0222)==0,"read-only executable "+id);
            tab(index);check(text().size()>6,"downloaded app renders "+id);
            Object handle=((Map<?,?>)field(activity,"screens")).get(index);Object target=field(handle,"target");check(target.getClass().getClassLoader()!=loader,"downloaded code loaded separately "+id);tab(-1);
        }
        for(String id:new String[]{"music","messaging","colorvision"}){
            JSONObject item=null;for(int n=0;n<catalog.length();n++)if(id.equals(catalog.getJSONObject(n).getString("id")))item=catalog.getJSONObject(n);
            call(apps,"download",new Class[]{JSONObject.class},item);File file=new File(getTargetContext().getFilesDir(),"apk-install/"+id+".apk");
            check(file.length()==item.getLong("bytes"),"independent app downloaded "+id);
            check(item.getString("sha256").equals(type("AppModules").getMethod("sha",File.class).invoke(null,file)),"independent APK checksum and trusted signer "+id);
        }
        result.putString("result","PASS: "+checks+" public GitHub store checks ("+lang+")");
    }catch(Throwable e){StringWriter s=new StringWriter();e.printStackTrace(new PrintWriter(s));result.putString("result","FAIL: "+s);}finally{if(activity!=null)ui(()->activity.finish());finish(Activity.RESULT_OK,result);}}
}
