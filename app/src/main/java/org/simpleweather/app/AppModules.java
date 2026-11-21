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

/** Signed executable packages bound to a stable host interface, never shared storage. */
public final class AppModules {
    public static final String API="daidai-toolbox/api-1";
    public static final String CATALOG="https://raw.githubusercontent.com/AdybirdZ/daidai-toolbox/main/modules/store/catalog.json";
    public static final String[] IDS={"weather","schedule","status","weather-map","astronomy","earthquake","random","todo"};
    public static final String[] CLASSES={"WeatherScreen","ScheduleScreen","GpsScreen","MapScreen","AstronomyScreen","EarthquakeScreen","RandomChoiceScreen","TodoScreen"};
    public static final String[] EMBEDDED={"music","messaging","colorvision"};
    public static final int PICK=91;
    public static final long MAX=32L*1024*1024;
    private final MainActivity a;
    private final File directory;
    private static final String PROCESS=UUID.randomUUID().toString();
    public static volatile boolean busy;
    public AppModules(MainActivity a){this.a=a;directory=new File(a.getFilesDir(),"apps");directory.mkdirs();}
    public static boolean known(String id){return Arrays.asList(IDS).contains(id)||Arrays.asList(EMBEDDED).contains(id);}
    public static boolean present(Context c,String id){if(!known(id))return false;try(ZipFile zip=new ZipFile(ModuleRuntime.packageFile(c,id))){ZipEntry e=zip.getEntry("assets/module.json");return e!=null&&API.equals(new JSONObject(new String(read(zip.getInputStream(e),65536),"UTF-8")).optString("hostApi"));}catch(Exception e){return false;}}
    public String title(String id){int n=Arrays.asList(IDS).indexOf(id);if(n>=0)return a.tabName(n);return a.getString(id.equals("music")?R.string.module_music:id.equals("messaging")?R.string.module_messaging:id.equals("colorvision")?R.string.module_colorvision:id.equals("settings")?R.string.action_settings:R.string.launcher_store);}
    private static int flags(){return Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES;}
    private static byte[] certificate(PackageInfo info)throws Exception{if(info==null)throw new IOException("Invalid package");android.content.pm.Signature[] sig=Build.VERSION.SDK_INT>=28?info.signingInfo.getApkContentsSigners():info.signatures;if(sig==null||sig.length!=1)throw new IOException("Invalid signer");return sig[0].toByteArray();}
    public boolean installed(String id){return present(a,id);}
    public boolean reconcile(){boolean changed=false;SharedPreferences p=a.getSharedPreferences("apps_host",0);for(String id:allIds())if(p.getBoolean("launching_"+id,false)&&!PROCESS.equals(p.getString("launch_process_"+id,""))){try{changed|=recoverPrevious(a,id);}catch(Exception ignored){}clearLaunch(p.edit(),id).commit();}return changed;}
    public static List<String> allIds(){List<String> ids=new ArrayList<>(Arrays.asList(IDS));ids.addAll(Arrays.asList(EMBEDDED));return ids;}
    public static int hostVersion(Context c){try{return c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionCode;}catch(Exception e){return 0;}}
    public boolean compatible(JSONObject item){return ModuleCompatibility.accepts(item.optString("hostApi",API),API,item.optInt("minHostVersionCode",0),hostVersion(a),item.optInt("minSdk",26),Build.VERSION.SDK_INT);}
    public static void beginLaunch(Context c,String id){beginTrackedLaunch(c,id);}
    static synchronized String beginTrackedLaunch(Context c,String id){if(!known(id))return null;SharedPreferences p=c.getSharedPreferences("apps_host",0);if(!p.getBoolean("pending_"+id,false))return null;String token=UUID.randomUUID().toString();p.edit().putBoolean("launching_"+id,true).putString("launch_token_"+id,token).putString("launch_process_"+id,PROCESS).commit();return token;}
    private static SharedPreferences.Editor clearLaunch(SharedPreferences.Editor edit,String id){return edit.remove("launching_"+id).remove("launch_token_"+id).remove("launch_process_"+id);}
    static synchronized void cancelLaunch(Context c,String id,String token){SharedPreferences p=c.getSharedPreferences("apps_host",0);if(token!=null&&token.equals(p.getString("launch_token_"+id,"")))clearLaunch(p.edit(),id).commit();}
    static synchronized void markLaunchSucceeded(Context c,String id,String token){SharedPreferences p=c.getSharedPreferences("apps_host",0);if(token!=null&&token.equals(p.getString("launch_token_"+id,"")))markLaunchSucceeded(c,id);}
    public static synchronized void markLaunchSucceeded(Context c,String id){if(!known(id))return;clearLaunch(c.getSharedPreferences("apps_host",0).edit(),id).remove("pending_"+id).commit();File previous=new File(ModuleRuntime.packageFile(c,id).getParentFile(),id+".previous.ddapp");previous.delete();}
    public void markLaunchSucceeded(String id){markLaunchSucceeded(a,id);}
    public static boolean recoverPrevious(Context c,String id)throws Exception{
        if(!known(id))return false;File previous=new File(ModuleRuntime.packageFile(c,id).getParentFile(),id+".previous.ddapp");if(!previous.isFile())return false;inspect(c,previous,id);boolean changing=false;
        try{ModuleRuntime.beginChange(c,id);changing=true;Files.move(previous.toPath(),ModuleRuntime.packageFile(c,id).toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);clearLaunch(c.getSharedPreferences("apps_host",0).edit(),id).remove("pending_"+id).commit();return true;}finally{if(changing)ModuleRuntime.endChange(id);}
    }
    public boolean recoverPrevious(String id)throws Exception{return recoverPrevious(a,id);}
    private void retainPrevious(String id)throws Exception{
        File current=ModuleRuntime.packageFile(a,id),previous=new File(directory,id+".previous.ddapp");
        if(!current.isFile())return;try{inspect(a,current,id);}catch(Exception incompatible){return;}
        if(previous.isFile()&&a.getSharedPreferences("apps_host",0).contains("pending_"+id))return;
        File stage=new File(directory,id+".previous.pending");stage.delete();try{Files.copy(current.toPath(),stage.toPath(),StandardCopyOption.REPLACE_EXISTING);if(!stage.setReadOnly())throw new IOException("Cannot protect executable");Files.move(stage.toPath(),previous.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}finally{stage.delete();}
    }
    public void initialize(){SharedPreferences p=a.getSharedPreferences("apps_host",0);boolean first=!p.getBoolean("initialized",false);if(!first&&API.equals(p.getString("api","")))return;try{for(String id:new String[]{"weather","status"})if(first||ModuleRuntime.packageFile(a,id).exists()){try(InputStream in=a.getAssets().open("default-apps/"+id+".ddapp")){install(in,id,null);}}if(!p.edit().putBoolean("initialized",true).putString("api",API).remove("pending_external").commit())throw new IOException("Cannot commit defaults");}catch(Exception e){a.toast(a.getString(R.string.module_install_failed));}}
    public static JSONObject inspect(Context c,File file,String id)throws Exception{
        if(!known(id)||file.length()>MAX)throw new IOException("Unsupported app");
        PackageInfo info=c.getPackageManager().getPackageArchiveInfo(file.getAbsolutePath(),flags());
        byte[] own=certificate(c.getPackageManager().getPackageInfo(c.getPackageName(),flags()));
        if(info==null||!info.packageName.equals("org.simpleweather.module."+id.replace('-','_'))||!MessageDigest.isEqual(own,certificate(info)))throw new IOException("Signature mismatch");
        try(ZipFile zip=new ZipFile(file)){Set<String> paths=new HashSet<>();Enumeration<? extends ZipEntry> entries=zip.entries();while(entries.hasMoreElements()){ZipEntry e=entries.nextElement();String n=e.getName();if(!paths.add(n)||n.contains("..")||n.startsWith("/")||n.contains("\\")||e.getSize()>MAX)throw new IOException("Invalid package path");}
            if(!paths.contains("classes.dex"))throw new IOException("Missing executable");ZipEntry descriptor=zip.getEntry("assets/module.json");if(descriptor==null)throw new IOException("Missing descriptor");JSONObject m=new JSONObject(new String(read(zip.getInputStream(descriptor),65536),"UTF-8"));
            if(!ModuleCompatibility.accepts(m.optString("hostApi"),API,m.optInt("minHostVersionCode",0),hostVersion(c),m.optInt("minSdk",26),Build.VERSION.SDK_INT)||!id.equals(m.optString("id"))||!"signed-dex-app".equals(m.optString("format"))||m.optInt("versionCode")<=0||m.optInt("versionCode")!=info.versionCode||!entryName(id).equals(m.optString("entry")))throw new IOException("Incompatible app");return m;
        }
    }
    private static String entryName(String id){int n=Arrays.asList(IDS).indexOf(id);return n>=0?CLASSES[n]:"org.daidai.modules."+id+"."+(id.equals("music")?"MusicActivity":id.equals("messaging")?"MessagingActivity":"ColorVisionActivity");}
    public synchronized void install(InputStream input,String id,String expectedHash)throws Exception{install(input,id,expectedHash,null);}
    private synchronized void install(InputStream input,String id,String expectedHash,DownloadTask task)throws Exception{
        if(!known(id))throw new IOException("Unknown application");boolean changing=false;File stage=new File(directory,id+".pending");if(stage.exists())stage.delete();
        try{try(FileOutputStream out=new FileOutputStream(stage);InputStream in=input){if(!stage.setReadOnly())throw new IOException("Cannot protect executable");copy(in,out,MAX);out.getFD().sync();}
            if(task!=null){task.check();task.verifying=true;}
            if(expectedHash!=null&&!expectedHash.equals(sha(stage)))throw new IOException("Checksum mismatch");JSONObject next=inspect(a,stage,id);if(present(a,id)&&inspect(a,ModuleRuntime.packageFile(a,id),id).getInt("versionCode")>next.getInt("versionCode"))throw new IOException("Downgrade rejected");if(android.os.Looper.myLooper()==android.os.Looper.getMainLooper())a.releaseModule(id);else{java.util.concurrent.CountDownLatch released=new java.util.concurrent.CountDownLatch(1);a.runOnUiThread(()->{try{a.releaseModule(id);}finally{released.countDown();}});if(!released.await(10,java.util.concurrent.TimeUnit.SECONDS))throw new IOException("Page did not stop");}if(task!=null)task.check();ModuleRuntime.beginChange(a,id);changing=true;
            retainPrevious(id);if(task!=null)task.check();
            if(task==null)Files.move(stage.toPath(),new File(directory,id+".ddapp").toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);else synchronized(task){task.check();Files.move(stage.toPath(),new File(directory,id+".ddapp").toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);task.completed=true;}
            a.getSharedPreferences("apps_host",0).edit().putBoolean("pending_"+id,true).commit();
        }finally{stage.delete();if(changing)ModuleRuntime.endChange(id);}
        try{if(id.equals("todo"))TodoScheduler.afterImport(a);else if(id.equals("schedule"))ScheduleNotifier.refresh(a,false);}catch(Exception failure){android.util.Log.w("AppModules","App installed; reminders could not refresh",failure);}
    }
    public ScreenHandle create(int index,MainActivity activity,City city,WeatherData data,Instant at,Consumer<Instant> change)throws Exception{
        String id=IDS[index];File file=new File(directory,id+".ddapp");inspect(a,file,id);
        ClassLoader loader=ModuleRuntime.loader(a,id);
        Class<?> entry=Class.forName("org.simpleweather.app."+CLASSES[index],true,loader);
        if(entry.getClassLoader()!=loader)throw new IOException("App code unexpectedly embedded in host");
        Object screen=index==4?entry.getConstructor(MainActivity.class,City.class,WeatherData.class,Instant.class,Consumer.class).newInstance(activity,city,data,at,change):entry.getConstructor(MainActivity.class).newInstance(activity);
        return new ScreenHandle(screen);
    }
    public void uninstall(String id){if(!known(id)||busy)return;busy=true;a.releaseModule(id);new Thread(()->{boolean removed=false;try{ModuleRuntime.beginChange(a,id);File file=ModuleRuntime.packageFile(a,id);if(file.exists()&&!file.delete())throw new IOException("Cannot remove application");new File(directory,id+".previous.ddapp").delete();a.getSharedPreferences("apps_host",0).edit().remove("launching_"+id).remove("pending_"+id).commit();if(id.equals("status")){RouteRecorder.stop();LocationService.stop(a);}if(id.equals("todo"))TodoScheduler.suspend(a);if(id.equals("schedule"))ScheduleNotifier.refresh(a,false);removed=true;}catch(Exception e){a.runOnUiThread(()->a.toast(a.getString(R.string.module_install_failed)));}finally{ModuleRuntime.endChange(id);busy=false;}if(removed)a.runOnUiThread(()->{if(!a.isDestroyed())a.installedAppsChanged();});},"app-uninstall").start();}
    public void openInternal(String id){if(installed(id))a.startActivity(ModuleRuntime.pageIntent(a,id,"main"));}
    public JSONArray bundledCatalog()throws Exception{try(InputStream in=a.getAssets().open("app-catalog.json")){return new JSONObject(new String(read(in,1024*1024),"UTF-8")).getJSONArray("apps");}}
    public JSONArray remoteCatalog()throws Exception{try(InputStream in=network(CATALOG)){JSONObject object=new JSONObject(new String(read(in,1024*1024),"UTF-8"));if(!"daidai-app-store".equals(object.optString("format"))||object.optInt("schemaVersion")!=1)throw new IOException("Invalid catalog");JSONArray items=object.getJSONArray("apps");if(items.length()>32)throw new IOException("Catalog too large");for(int n=0;n<items.length();n++){JSONObject item=items.getJSONObject(n);if(!item.has("hostApi"))item.put("hostApi",object.optString("hostApi"));}return items;}}
    /** A retained legacy/unreadable executable also needs replacement, exactly as its row offers. */
    public boolean updateAvailable(JSONObject item){String id=item.optString("id");if(!known(id)||!compatible(item)||item.optInt("versionCode")<=0)return false;File file=ModuleRuntime.packageFile(a,id);if(!file.isFile())return false;try{return item.getInt("versionCode")>inspect(a,file,id).getInt("versionCode");}catch(Exception legacyOrDamaged){return true;}}
    public void download(JSONObject item)throws Exception{download(item,new DownloadTask());}
    public void download(JSONObject item,DownloadTask task)throws Exception{
        String id=item.getString("id"),hash=item.getString("sha256");long length=item.optLong("bytes",-1);
        if(!known(id)||!hash.matches("[0-9a-f]{64}")||!"signed-dex-app".equals(item.getString("type"))||length<=0||length>MAX)throw new IOException("Invalid application");
        if(!compatible(item))throw new IOException("Incompatible app");task.total=length;task.check();
        try(InputStream raw=network(item.getString("url"),task);InputStream in=trackedStream(raw,length,task)){install(in,id,hash,task);}catch(Exception failure){if(task.cancelled)throw new InterruptedIOException("Cancelled");throw failure;}finally{task.connection=null;}
    }
    static InputStream trackedStream(InputStream raw,long length,DownloadTask task){
        task.total=length;return new FilterInputStream(raw){
            public int read()throws IOException{byte[] one=new byte[1];int n=read(one,0,1);return n<0?-1:one[0]&255;}
            public int read(byte[] b,int off,int count)throws IOException{task.check();int n=super.read(b,off,count);if(n<0){if(!ModuleCompatibility.validLength(length,task.received,MAX))throw new IOException("Length mismatch");}else{task.received+=n;if(task.received>length)throw new IOException("Length mismatch");}task.check();return n;}
        };
    }
    public static final class DownloadTask {
        public volatile long received,total;public volatile boolean verifying,completed,cancelled;
        private volatile HttpURLConnection connection;
        public synchronized void cancel(){if(completed)return;cancelled=true;HttpURLConnection c=connection;if(c!=null)c.disconnect();}
        public void check()throws InterruptedIOException{if(cancelled||Thread.currentThread().isInterrupted())throw new InterruptedIOException("Cancelled");}
    }
    public int failureMessage(Throwable failure){
        Throwable e=failure;while(e.getCause()!=null&&e.getCause()!=e)e=e.getCause();String text=String.valueOf(failure.getMessage());
        if(e instanceof InterruptedIOException&&!(e instanceof SocketTimeoutException))return R.string.store_failure_cancelled;
        if(e instanceof UnknownHostException||e instanceof ConnectException||e instanceof SocketTimeoutException||e instanceof javax.net.ssl.SSLException)return R.string.store_failure_network;
        if(text.contains("Signature"))return R.string.store_failure_signature;
        if(text.contains("Checksum")||text.contains("Length"))return R.string.store_failure_integrity;
        if(text.contains("Incompatible")||text.contains("Downgrade"))return R.string.store_failure_compatibility;
        if(text.startsWith("HTTP "))return R.string.store_failure_server;
        if(text.contains("ENOSPC")||text.contains("space"))return R.string.store_failure_storage;
        return R.string.store_failure_package;
    }
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
    public static InputStream network(String address)throws Exception{return network(address,null);}
    private static InputStream network(String address,DownloadTask task)throws Exception{
        try{return connect(address,task);}catch(IOException primary){
            if(task!=null)task.check();if(Thread.currentThread().isInterrupted()||primary instanceof InterruptedIOException&&!(primary instanceof SocketTimeoutException))throw primary;
            String fallback=contentsFallback(address);if(fallback==null)throw primary;
            try{return connect(fallback,task);}catch(Exception failure){failure.addSuppressed(primary);throw failure;}
        }
    }
    private static final String RAW_STORE="/AdybirdZ/daidai-toolbox/main/modules/store/",API_STORE="/repos/AdybirdZ/daidai-toolbox/contents/modules/store/";
    /** One anonymous official fallback, limited to this repository's published main store. */
    static String contentsFallback(String address){try{URL u=new URL(address);String originalPath=new URI(address).getRawPath();if(!plainHttps(u)||!u.getHost().equals("raw.githubusercontent.com")||u.getQuery()!=null||originalPath==null||!originalPath.startsWith(RAW_STORE))return null;String path=originalPath.substring(RAW_STORE.length());return storePath(path)?"https://api.github.com"+API_STORE+path+"?ref=main":null;}catch(MalformedURLException|URISyntaxException invalid){return null;}}
    private static boolean plainHttps(URL u){return u.getProtocol().equals("https")&&u.getUserInfo()==null&&u.getPort()==-1&&u.getRef()==null;}
    private static boolean storePath(String path){if(path.isEmpty()||path.length()>1024)return false;for(String segment:path.split("/",-1))if(!segment.matches("[A-Za-z0-9._-]+")||segment.equals(".")||segment.equals(".."))return false;return true;}
    private static boolean contentsApi(URL u){return plainHttps(u)&&u.getHost().equals("api.github.com")&&"ref=main".equals(u.getQuery())&&u.getPath().startsWith(API_STORE)&&storePath(u.getPath().substring(API_STORE.length()));}
    private static InputStream connect(String address,DownloadTask task)throws Exception{
        URL u=new URL(address);for(int redirects=0;redirects<5;redirects++){
            String host=u.getHost();boolean api=contentsApi(u);if(!plainHttps(u)||!(api||host.equals("raw.githubusercontent.com")||host.equals("github.com")||host.equals("release-assets.githubusercontent.com")||host.equals("objects.githubusercontent.com")))throw new IOException("Unsupported download origin");
            if(task!=null)task.check();HttpURLConnection c=(HttpURLConnection)u.openConnection();c.setConnectTimeout(20000);c.setReadTimeout(30000);c.setInstanceFollowRedirects(false);if(api){c.setRequestProperty("Accept","application/vnd.github.raw+json");c.setRequestProperty("X-GitHub-Api-Version","2022-11-28");c.setRequestProperty("User-Agent","DaidaiToolbox");}
            if(task!=null){task.check();task.connection=c;}int code;try{code=c.getResponseCode();}catch(Exception e){c.disconnect();throw e;}
            if(code>=300&&code<400){String next=c.getHeaderField("Location");c.disconnect();if(next==null)throw new IOException("Missing redirect");u=new URL(u,next);continue;}
            if(code!=200){c.disconnect();throw new IOException("HTTP "+code);}InputStream input;try{input=c.getInputStream();}catch(Exception failure){c.disconnect();throw failure;}
            return new FilterInputStream(input){@Override public void close()throws IOException{try{super.close();}finally{c.disconnect();}}};
        }throw new IOException("Too many redirects");
    }
    public static void copy(InputStream in,OutputStream out,long max)throws IOException{if(in==null)throw new IOException("No file");byte[] b=new byte[32768];long total=0;int n;while((n=in.read(b))!=-1){if((total+=n)>max)throw new IOException("File too large");out.write(b,0,n);}}
    public static byte[] read(InputStream in,int max)throws IOException{try(InputStream source=in;ByteArrayOutputStream out=new ByteArrayOutputStream()){copy(source,out,max);return out.toByteArray();}}
    public static String sha(File f)throws Exception{MessageDigest d=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(f)){byte[] b=new byte[32768];int n;while((n=in.read(b))!=-1)d.update(b,0,n);}return hex(d.digest());}
    private static String hex(byte[] bytes){StringBuilder s=new StringBuilder();for(byte b:bytes)s.append(String.format(Locale.ROOT,"%02x",b&255));return s.toString();}
}
