package org.simpleweather.app;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.os.Build;
import dalvik.system.DexClassLoader;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.*;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;
import java.util.zip.*;

/** Signed, host-version-bound executable packages. Never loads code from shared storage. */
public final class AppModules {
    public static final String API="daidai-toolbox/pre-3.0.0-3";
    public static final String CATALOG="https://raw.githubusercontent.com/AdybirdZ/daidai-toolbox/main/modules/store/pre-3.0.0-3/catalog.json";
    public static final String[] IDS={"weather","schedule","status","weather-map","astronomy","earthquake","random","todo"};
    public static final String[] CLASSES={"WeatherScreen","ScheduleScreen","GpsScreen","MapScreen","AstronomyScreen","EarthquakeScreen","RandomChoiceScreen","TodoScreen"};
    public static final String[] EMBEDDED={"music","messaging","colorvision"};
    public static final int PICK=91;
    public static final long MAX=32L*1024*1024;
    private final MainActivity a;
    private final File directory;
    public volatile boolean busy;
    public AppModules(MainActivity a){this.a=a;directory=new File(a.getFilesDir(),"apps");directory.mkdirs();}
    public static boolean known(String id){return Arrays.asList(IDS).contains(id)||Arrays.asList(EMBEDDED).contains(id);}
    public static boolean present(Context c,String id){if(!known(id))return false;try(ZipFile zip=new ZipFile(ModuleRuntime.packageFile(c,id))){ZipEntry e=zip.getEntry("assets/module.json");return e!=null&&API.equals(new JSONObject(new String(read(zip.getInputStream(e),65536),"UTF-8")).optString("hostApi"));}catch(Exception e){return false;}}
    public String title(String id){int n=Arrays.asList(IDS).indexOf(id);if(n>=0)return a.tabName(n);return a.getString(id.equals("music")?R.string.module_music:id.equals("messaging")?R.string.module_messaging:id.equals("colorvision")?R.string.module_colorvision:id.equals("settings")?R.string.action_settings:R.string.launcher_store);}
    private static int flags(){return Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES;}
    private static byte[] certificate(PackageInfo info)throws Exception{if(info==null)throw new IOException("Invalid package");android.content.pm.Signature[] sig=Build.VERSION.SDK_INT>=28?info.signingInfo.getApkContentsSigners():info.signatures;if(sig==null||sig.length!=1)throw new IOException("Invalid signer");return sig[0].toByteArray();}
    public boolean installed(String id){return present(a,id);}
    public boolean reconcile(){return false;}
    public void initialize(){SharedPreferences p=a.getSharedPreferences("apps_host",0);boolean first=!p.getBoolean("initialized",false);if(!first&&API.equals(p.getString("api","")))return;try{for(String id:new String[]{"weather","status"})if(first||ModuleRuntime.packageFile(a,id).exists()){try(InputStream in=a.getAssets().open("default-apps/"+id+".ddapp")){install(in,id,null);}}if(!p.edit().putBoolean("initialized",true).putString("api",API).remove("pending_external").commit())throw new IOException("Cannot commit defaults");}catch(Exception e){a.toast(a.getString(R.string.module_install_failed));}}
    public static JSONObject inspect(Context c,File file,String id)throws Exception{
        if(!known(id)||file.length()>MAX)throw new IOException("Unsupported app");
        PackageInfo info=c.getPackageManager().getPackageArchiveInfo(file.getAbsolutePath(),flags());
        byte[] own=certificate(c.getPackageManager().getPackageInfo(c.getPackageName(),flags()));
        if(info==null||!info.packageName.equals("org.simpleweather.module."+id.replace('-','_'))||!MessageDigest.isEqual(own,certificate(info)))throw new IOException("Signature mismatch");
        try(ZipFile zip=new ZipFile(file)){Set<String> paths=new HashSet<>();Enumeration<? extends ZipEntry> entries=zip.entries();while(entries.hasMoreElements()){ZipEntry e=entries.nextElement();String n=e.getName();if(!paths.add(n)||n.contains("..")||n.startsWith("/")||n.contains("\\")||e.getSize()>MAX)throw new IOException("Invalid package path");}
            if(!paths.contains("classes.dex"))throw new IOException("Missing executable");ZipEntry descriptor=zip.getEntry("assets/module.json");if(descriptor==null)throw new IOException("Missing descriptor");JSONObject m=new JSONObject(new String(read(zip.getInputStream(descriptor),65536),"UTF-8"));
            if(!API.equals(m.optString("hostApi"))||!id.equals(m.optString("id"))||!"signed-dex-app".equals(m.optString("format"))||m.optInt("versionCode")<=0||m.optInt("versionCode")!=info.versionCode||!entryName(id).equals(m.optString("entry")))throw new IOException("Incompatible app");return m;
        }
    }
    private static String entryName(String id){int n=Arrays.asList(IDS).indexOf(id);return n>=0?CLASSES[n]:"org.daidai.modules."+id+"."+(id.equals("music")?"MusicActivity":id.equals("messaging")?"MessagingActivity":"ColorVisionActivity");}
    public synchronized void install(InputStream input,String id,String expectedHash)throws Exception{
        if(!known(id))throw new IOException("Unknown application");boolean changing=false;File stage=new File(directory,id+".pending");if(stage.exists())stage.delete();
        try{try(FileOutputStream out=new FileOutputStream(stage);InputStream in=input){if(!stage.setReadOnly())throw new IOException("Cannot protect executable");copy(in,out,MAX);out.getFD().sync();}
            if(expectedHash!=null&&!expectedHash.equals(sha(stage)))throw new IOException("Checksum mismatch");JSONObject next=inspect(a,stage,id);if(present(a,id)&&inspect(a,ModuleRuntime.packageFile(a,id),id).getInt("versionCode")>next.getInt("versionCode"))throw new IOException("Downgrade rejected");if(android.os.Looper.myLooper()==android.os.Looper.getMainLooper())a.releaseModule(id);else{java.util.concurrent.CountDownLatch released=new java.util.concurrent.CountDownLatch(1);a.runOnUiThread(()->{try{a.releaseModule(id);}finally{released.countDown();}});if(!released.await(10,java.util.concurrent.TimeUnit.SECONDS))throw new IOException("Page did not stop");}ModuleRuntime.beginChange(a,id);changing=true;
            Files.move(stage.toPath(),new File(directory,id+".ddapp").toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
            if(id.equals("todo"))TodoScheduler.afterImport(a);
        }finally{stage.delete();if(changing)ModuleRuntime.endChange(id);}
    }
    public ScreenHandle create(int index,MainActivity activity,City city,WeatherData data,Instant at,Consumer<Instant> change)throws Exception{
        String id=IDS[index];File file=new File(directory,id+".ddapp");inspect(a,file,id);
        ClassLoader loader=ModuleRuntime.loader(a,id);
        Class<?> entry=Class.forName("org.simpleweather.app."+CLASSES[index],true,loader);
        if(entry.getClassLoader()!=loader)throw new IOException("App code unexpectedly embedded in host");
        Object screen=index==4?entry.getConstructor(MainActivity.class,City.class,WeatherData.class,Instant.class,Consumer.class).newInstance(activity,city,data,at,change):entry.getConstructor(MainActivity.class).newInstance(activity);
        return new ScreenHandle(screen);
    }
    public void uninstall(String id){if(!known(id)||busy)return;busy=true;a.releaseModule(id);new Thread(()->{try{ModuleRuntime.beginChange(a,id);File file=ModuleRuntime.packageFile(a,id);if(file.exists()&&!file.delete())throw new IOException("Cannot remove application");if(id.equals("status")){RouteRecorder.stop();LocationService.stop(a);}if(id.equals("todo"))TodoScheduler.suspend(a);if(id.equals("schedule"))ScheduleNotifier.refresh(a,false);a.runOnUiThread(a::launcher);}catch(Exception e){a.runOnUiThread(()->a.toast(a.getString(R.string.module_install_failed)));}finally{ModuleRuntime.endChange(id);busy=false;}},"app-uninstall").start();}
    public void openInternal(String id){if(installed(id))a.startActivity(ModuleRuntime.pageIntent(a,id,"main"));}
    public JSONArray bundledCatalog()throws Exception{try(InputStream in=a.getAssets().open("app-catalog.json")){return new JSONObject(new String(read(in,1024*1024),"UTF-8")).getJSONArray("apps");}}
    public JSONArray remoteCatalog()throws Exception{try(InputStream in=network(CATALOG)){JSONObject object=new JSONObject(new String(read(in,1024*1024),"UTF-8"));if(!API.equals(object.optString("hostApi")))throw new IOException("Catalog ABI mismatch");JSONArray items=object.getJSONArray("apps");if(items.length()>32)throw new IOException("Catalog too large");return items;}}
    public boolean updateAvailable(JSONObject item){try{return installed(item.getString("id"))&&item.getInt("versionCode")>inspect(a,ModuleRuntime.packageFile(a,item.getString("id")),item.getString("id")).getInt("versionCode");}catch(Exception e){return false;}}
    public void download(JSONObject item)throws Exception{String id=item.getString("id"),hash=item.getString("sha256");if(!known(id)||!hash.matches("[0-9a-f]{64}")||!"signed-dex-app".equals(item.getString("type")))throw new IOException("Invalid application");try(InputStream in=network(item.getString("url"))){install(in,id,hash);}}
    public boolean result(int request,int result,Intent data){
        if(request!=PICK)return false;
        if(result!=Activity.RESULT_OK||data==null||data.getData()==null||busy)return true;
        busy=true;Uri uri=data.getData();
        new Thread(()->{
            File temp=new File(a.getCacheDir(),"selected-app.ddapp");
            try{
                try(InputStream in=a.getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(temp)){copy(in,out,MAX);}
                String id;
                try(ZipFile zip=new ZipFile(temp)){
                    ZipEntry e=zip.getEntry("assets/module.json");if(e==null)throw new IOException("Not an internal app");JSONObject m=new JSONObject(new String(read(zip.getInputStream(e),65536),"UTF-8"));id=m.getString("id");inspect(a,temp,id);
                }
                final String selected=id;
                a.runOnUiThread(()->{
                    if(a.isDestroyed()){busy=false;temp.delete();return;}
                    new ToolDialog(a).setTitle(title(selected)).setMessage(a.getString(R.string.module_trust_note))
                        .setPositiveButton(a.getString(R.string.module_install),(d,w)->{
                            new Thread(()->{
                                try{try(InputStream in=new FileInputStream(temp)){install(in,selected,null);}a.runOnUiThread(()->{if(!a.isDestroyed()){a.releaseModule(selected);a.installedAppsChanged();}});}
                                catch(Exception e){a.runOnUiThread(()->a.toast(a.getString(R.string.module_install_failed)));}
                                finally{temp.delete();busy=false;}
                            }).start();
                        }).setNegativeButton(a.getString(R.string.action_cancel),(d,w)->{busy=false;temp.delete();})
                        .setOnCancelListener(d->{busy=false;temp.delete();}).show();
                });
            }catch(Exception e){busy=false;temp.delete();a.runOnUiThread(()->a.toast(a.getString(R.string.module_install_failed)));}
        }).start();return true;
    }
    public void pick(){try{a.startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE),PICK);}catch(Exception e){a.toast(a.getString(R.string.config_picker_failed));}}
    public static InputStream network(String address)throws Exception{
        URL u=new URL(address);for(int redirects=0;redirects<5;redirects++){String host=u.getHost();if(!u.getProtocol().equals("https")||!(host.equals("raw.githubusercontent.com")||host.equals("github.com")||host.equals("release-assets.githubusercontent.com")||host.equals("objects.githubusercontent.com")))throw new IOException("Unsupported download origin");HttpURLConnection c=(HttpURLConnection)u.openConnection();c.setConnectTimeout(20000);c.setReadTimeout(30000);c.setInstanceFollowRedirects(false);int code=c.getResponseCode();if(code>=300&&code<400){String next=c.getHeaderField("Location");c.disconnect();u=new URL(u,next);continue;}if(code!=200){c.disconnect();throw new IOException("HTTP "+code);}return new FilterInputStream(c.getInputStream()){@Override public void close()throws IOException{try{super.close();}finally{c.disconnect();}}};}throw new IOException("Too many redirects");
    }
    public static void copy(InputStream in,OutputStream out,long max)throws IOException{if(in==null)throw new IOException("No file");byte[] b=new byte[32768];long total=0;int n;while((n=in.read(b))!=-1){if((total+=n)>max)throw new IOException("File too large");out.write(b,0,n);}}
    public static byte[] read(InputStream in,int max)throws IOException{try(InputStream source=in;ByteArrayOutputStream out=new ByteArrayOutputStream()){copy(source,out,max);return out.toByteArray();}}
    public static String sha(File f)throws Exception{MessageDigest d=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(f)){byte[] b=new byte[32768];int n;while((n=in.read(b))!=-1)d.update(b,0,n);}return hex(d.digest());}
    private static String hex(byte[] bytes){StringBuilder s=new StringBuilder();for(byte b:bytes)s.append(String.format(Locale.ROOT,"%02x",b&255));return s.toString();}
}
