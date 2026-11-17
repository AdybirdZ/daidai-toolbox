package org.simpleweather.app;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.content.res.*;
import android.os.*;
import dalvik.system.DexClassLoader;
import java.io.*;
import java.util.*;

/** One loader per installed code generation, shared by its page and service. */
public final class ModuleRuntime {
    private static final Map<String,ClassLoader> LOADERS=new HashMap<>();
    private static final Map<String,Set<InternalAppActivity>> PAGES=new HashMap<>();
    private static final Map<String,ModuleServiceBridge> SERVICES=new HashMap<>();
    private static final Set<String> CHANGING=new HashSet<>();
    private ModuleRuntime(){}
    public static File dataDirectory(Context c,String id){File f=new File(c.getApplicationInfo().dataDir,"files/internal-app-data/"+id);f.mkdirs();return f;}
    public static File packageFile(Context c,String id){return new File(c.getApplicationInfo().dataDir,"files/apps/"+id+".ddapp");}
    public static synchronized ClassLoader loader(Context c,String id)throws Exception{
        if(CHANGING.contains(id))throw new IOException("Application is being updated");
        ClassLoader result=LOADERS.get(id);if(result!=null)return result;
        File file=packageFile(c,id);AppModules.inspect(c,file,id);
        result=new DexClassLoader(file.getAbsolutePath(),c.getCodeCacheDir().getAbsolutePath(),null,ModuleRuntime.class.getClassLoader());LOADERS.put(id,result);return result;
    }
    static Resources resources(Context c,String id)throws Exception{
        File file=packageFile(c,id);AppModules.inspect(c,file,id);
        PackageInfo info=c.getPackageManager().getPackageArchiveInfo(file.getAbsolutePath(),0);
        if(info==null)throw new IOException("Missing resources");
        info.applicationInfo.sourceDir=file.getAbsolutePath();info.applicationInfo.publicSourceDir=file.getAbsolutePath();
        Resources source=c.getPackageManager().getResourcesForApplication(info.applicationInfo);
        Configuration config=new Configuration(AppLanguage.wrap(c).getResources().getConfiguration());
        return new Resources(source.getAssets(),c.getResources().getDisplayMetrics(),config);
    }
    public static Intent pageIntent(Context c,String id,String page){return new Intent(c,InternalAppActivity.class).putExtra("internal_app",id).putExtra("internal_page",page);}
    public static String appVersion(Context c,String id){try{return AppModules.inspect(c,packageFile(c,id),id).getString("version");}catch(Exception e){return "";}}
    public static Intent serviceIntent(Context c,String id){if(id.equals("music"))return new Intent(c,MusicServiceBridge.class);if(id.equals("messaging"))return new Intent(c,MessagingServiceBridge.class);throw new IllegalArgumentException("No service bridge");}
    static ModulePage page(InternalAppActivity a,String id,String page)throws Exception{
        String entry=AppModules.inspect(a,packageFile(a,id),id).getJSONObject("pages").getString(page);
        ClassLoader loader=loader(a,id);Class<?> type=Class.forName(entry,true,loader);if(type.getClassLoader()!=loader)throw new IOException("Embedded application code in host");
        ModulePage result=(ModulePage)type.getConstructor(Activity.class).newInstance(a);
        synchronized(ModuleRuntime.class){PAGES.computeIfAbsent(id,k->new HashSet<>()).add(a);}return result;
    }
    static ModuleWorker worker(Service service,String id)throws Exception{
        Context context=new ModuleContext(service,id,resources(service,id));
        String entry=AppModules.inspect(service,packageFile(service,id),id).getString("worker");ClassLoader loader=loader(service,id);Class<?> type=Class.forName(entry,true,loader);if(type.getClassLoader()!=loader)throw new IOException("Worker code in host");
        return (ModuleWorker)type.getConstructor(Service.class,Context.class).newInstance(service,context);
    }
    static synchronized void pageClosed(String id,InternalAppActivity a){Set<InternalAppActivity> pages=PAGES.get(id);if(pages!=null){pages.remove(a);if(pages.isEmpty())PAGES.remove(id);}}
    static synchronized void serviceOpened(String id,ModuleServiceBridge s){SERVICES.put(id,s);}
    static synchronized void serviceClosed(String id,ModuleServiceBridge s){if(SERVICES.get(id)==s)SERVICES.remove(id);}
    /** Called off the UI thread before replacing/deleting code. */
    static void beginChange(Context context,String id)throws Exception{
        java.util.concurrent.CountDownLatch done=new java.util.concurrent.CountDownLatch(1);
        Runnable stop=()->{try{synchronized(ModuleRuntime.class){CHANGING.add(id);Set<InternalAppActivity> pages=PAGES.get(id);if(pages!=null)for(InternalAppActivity a:new ArrayList<>(pages))a.closeModule();ModuleServiceBridge service=SERVICES.get(id);if(service!=null)service.closeModule();LOADERS.remove(id);}}finally{done.countDown();}};
        if(Looper.myLooper()==Looper.getMainLooper())stop.run();else new Handler(Looper.getMainLooper()).post(stop);
        if(!done.await(10,java.util.concurrent.TimeUnit.SECONDS))throw new IOException("Application could not be stopped");
    }
    static synchronized void endChange(String id){CHANGING.remove(id);}
    public static void stopService(Context c,String id){c.stopService(serviceIntent(c,id));}
    static final class ModuleContext extends ContextWrapper {
        private final String id;private final Resources resources;
        ModuleContext(Context base,String id,Resources resources){super(base);this.id=id;this.resources=resources;}
        @Override public Resources getResources(){return resources;}
        @Override public AssetManager getAssets(){return resources.getAssets();}
        @Override public Context createConfigurationContext(Configuration config){return new ModuleContext(getBaseContext().createConfigurationContext(config),id,new Resources(resources.getAssets(),resources.getDisplayMetrics(),config));}
        @Override public Context getApplicationContext(){return this;}
        @Override public File getFilesDir(){return dataDirectory(this,id);}
        @Override public File getCacheDir(){File f=new File(super.getCacheDir(),"internal-"+id);f.mkdirs();return f;}
    }
}
