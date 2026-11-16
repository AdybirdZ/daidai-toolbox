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
    public static final String API="daidai-toolbox/pre-3.0.0-1";
    public static final String CATALOG="https://raw.githubusercontent.com/AdybirdZ/daidai-toolbox/main/modules/store/pre-3.0.0-1/catalog.json";
    public static final String[] IDS={"weather","schedule","status","weather-map","astronomy","earthquake","random","todo"};
    public static final String[] CLASSES={"WeatherScreen","ScheduleScreen","GpsScreen","MapScreen","AstronomyScreen","EarthquakeScreen","RandomChoiceScreen","TodoScreen"};
    public static final String[] EXTERNAL={"music","messaging","colorvision"};
    private static final String PROTOTYPE_SIGNER="0d4d9d87dfb81fa0ea922fc764ff4326d16284ca69ca8715bca247e22973e1f4";
    public static final int PICK=91,INSTALL=92,UNINSTALL=93;
    public static final long MAX=32L*1024*1024;
    private final MainActivity a;
    private final File directory;
    private String pendingExternal;
    public volatile boolean busy;
    public AppModules(MainActivity a){this.a=a;directory=new File(a.getFilesDir(),"apps");directory.mkdirs();}
    public static boolean present(Context c,String id){return new File(new File(c.getFilesDir(),"apps"),id+".ddapp").isFile();}
    public String title(String id){int n=Arrays.asList(IDS).indexOf(id);if(n>=0)return a.tabName(n);return a.getString(id.equals("music")?R.string.module_music:id.equals("messaging")?R.string.module_messaging:id.equals("colorvision")?R.string.module_colorvision:id.equals("settings")?R.string.action_settings:R.string.launcher_store);}
    public static String packageName(String id){return "org.daidai.prototype."+id;}
    private static int flags(){return Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES;}
    private static byte[] certificate(PackageInfo info)throws Exception{if(info==null)throw new IOException("Invalid package");android.content.pm.Signature[] sig=Build.VERSION.SDK_INT>=28?info.signingInfo.getApkContentsSigners():info.signatures;if(sig==null||sig.length!=1)throw new IOException("Invalid signer");return sig[0].toByteArray();}
    public boolean installed(String id){if(Arrays.asList(IDS).contains(id))return present(a,id);return Arrays.asList(EXTERNAL).contains(id)&&a.getSharedPreferences("apps_host",0).getBoolean("external_"+id,false)&&androidInstalled(id);}
    private boolean androidInstalled(String id){try{PackageInfo p=a.getPackageManager().getPackageInfo(packageName(id),flags());return PROTOTYPE_SIGNER.equals(hex(MessageDigest.getInstance("SHA-256").digest(certificate(p))));}catch(Exception e){return false;}}
    public boolean reconcile(){SharedPreferences prefs=a.getSharedPreferences("apps_host",0);String id=prefs.getString("pending_external","");if(Arrays.asList(EXTERNAL).contains(id)&&androidInstalled(id)){prefs.edit().putBoolean("external_"+id,true).remove("pending_external").commit();return true;}return false;}
    public void initialize(){SharedPreferences p=a.getSharedPreferences("apps_host",0);if(p.getBoolean("initialized",false))return;try{for(String id:new String[]{"weather","status"}){try(InputStream in=a.getAssets().open("default-apps/"+id+".ddapp")){install(in,id,null);}}if(!p.edit().putBoolean("initialized",true).commit())throw new IOException("Cannot commit defaults");}catch(Exception e){a.toast(a.getString(R.string.module_install_failed));}}
    private JSONObject inspect(File file,String id)throws Exception{
        if(!Arrays.asList(IDS).contains(id)||file.length()>MAX)throw new IOException("Unsupported app");
        PackageInfo info=a.getPackageManager().getPackageArchiveInfo(file.getAbsolutePath(),flags());
        byte[] own=certificate(a.getPackageManager().getPackageInfo(a.getPackageName(),flags()));
        if(info==null||!info.packageName.equals("org.simpleweather.module."+id.replace('-','_'))||!MessageDigest.isEqual(own,certificate(info)))throw new IOException("Signature mismatch");
        try(ZipFile zip=new ZipFile(file)){Set<String> paths=new HashSet<>();Enumeration<? extends ZipEntry> entries=zip.entries();while(entries.hasMoreElements()){ZipEntry e=entries.nextElement();String n=e.getName();if(!paths.add(n)||n.contains("..")||n.startsWith("/")||n.contains("\\")||e.getSize()>MAX)throw new IOException("Invalid package path");}
            if(!paths.contains("classes.dex"))throw new IOException("Missing executable");ZipEntry descriptor=zip.getEntry("assets/module.json");if(descriptor==null)throw new IOException("Missing descriptor");JSONObject m=new JSONObject(new String(read(zip.getInputStream(descriptor),65536),"UTF-8"));
            if(!API.equals(m.optString("hostApi"))||!id.equals(m.optString("id"))||!"signed-dex-app".equals(m.optString("format"))||m.optInt("versionCode")!=1||!CLASSES[Arrays.asList(IDS).indexOf(id)].equals(m.optString("entry")))throw new IOException("Incompatible app");return m;
        }
    }
    public synchronized void install(InputStream input,String id,String expectedHash)throws Exception{
        File stage=new File(directory,id+".pending");if(stage.exists())stage.delete();
        try{try(FileOutputStream out=new FileOutputStream(stage);InputStream in=input){if(!stage.setReadOnly())throw new IOException("Cannot protect executable");copy(in,out,MAX);out.getFD().sync();}
            if(expectedHash!=null&&!expectedHash.equals(sha(stage)))throw new IOException("Checksum mismatch");inspect(stage,id);
            Files.move(stage.toPath(),new File(directory,id+".ddapp").toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
            if(id.equals("todo"))TodoScheduler.afterImport(a);
        }finally{stage.delete();}
    }
    public ScreenHandle create(int index,MainActivity activity,City city,WeatherData data,Instant at,Consumer<Instant> change)throws Exception{
        String id=IDS[index];File file=new File(directory,id+".ddapp");inspect(file,id);
        ClassLoader loader=new DexClassLoader(file.getAbsolutePath(),a.getCodeCacheDir().getAbsolutePath(),null,a.getClassLoader());
        Class<?> entry=Class.forName("org.simpleweather.app."+CLASSES[index],true,loader);
        if(entry.getClassLoader()!=loader)throw new IOException("App code unexpectedly embedded in host");
        Object screen=index==4?entry.getConstructor(MainActivity.class,City.class,WeatherData.class,Instant.class,Consumer.class).newInstance(activity,city,data,at,change):entry.getConstructor(MainActivity.class).newInstance(activity);
        return new ScreenHandle(screen);
    }
    public void uninstall(String id){if(Arrays.asList(EXTERNAL).contains(id)){try{a.startActivityForResult(new Intent(Intent.ACTION_UNINSTALL_PACKAGE,Uri.parse("package:"+packageName(id))).putExtra(Intent.EXTRA_RETURN_RESULT,true),UNINSTALL);}catch(Exception e){a.toast(a.getString(R.string.module_install_failed));}return;}
        a.releaseModule(id);File file=new File(directory,id+".ddapp");if(file.exists()&&!file.delete()){a.toast(a.getString(R.string.module_install_failed));return;}if(id.equals("status")){RouteRecorder.stop();LocationService.stop(a);}if(id.equals("todo"))TodoScheduler.suspend(a);if(id.equals("schedule"))ScheduleNotifier.refresh(a,false);a.launcher();
    }
    public void openExternal(String id){if(!installed(id))return;try{Intent intent=a.getPackageManager().getLaunchIntentForPackage(packageName(id));if(intent==null)throw new IOException();a.startActivity(intent);}catch(Exception e){a.toast(a.getString(R.string.module_open_failed));}}
    public JSONArray bundledCatalog()throws Exception{try(InputStream in=a.getAssets().open("app-catalog.json")){return new JSONObject(new String(read(in,1024*1024),"UTF-8")).getJSONArray("apps");}}
    public JSONArray remoteCatalog()throws Exception{try(InputStream in=network(CATALOG)){JSONObject object=new JSONObject(new String(read(in,1024*1024),"UTF-8"));if(!API.equals(object.optString("hostApi")))throw new IOException("Catalog ABI mismatch");JSONArray items=object.getJSONArray("apps");if(items.length()>32)throw new IOException("Catalog too large");return items;}}
    public void download(JSONObject item)throws Exception{
        String id=item.getString("id"),hash=item.getString("sha256");if(!hash.matches("[0-9a-f]{64}"))throw new IOException("Invalid checksum");
        if(Arrays.asList(IDS).contains(id)){try(InputStream in=network(item.getString("url"))){install(in,id,hash);}return;}
        if(!Arrays.asList(EXTERNAL).contains(id))throw new IOException("Unknown app");File dir=new File(a.getFilesDir(),"apk-install");dir.mkdirs();File apk=new File(dir,id+".apk");
        try(InputStream in=network(item.getString("url"));OutputStream out=new FileOutputStream(apk)){copy(in,out,MAX);}
        if(!hash.equals(sha(apk))){apk.delete();throw new IOException("Checksum mismatch");}
        verifyExternal(apk,id);pendingExternal=id;
    }
    private void verifyExternal(File apk,String id)throws Exception{PackageInfo info=a.getPackageManager().getPackageArchiveInfo(apk.getAbsolutePath(),flags());if(info==null||!packageName(id).equals(info.packageName)||!PROTOTYPE_SIGNER.equals(hex(MessageDigest.getInstance("SHA-256").digest(certificate(info)))))throw new IOException("Untrusted application");}
    public void requestExternalInstall(){if(pendingExternal==null)return;try{if(Build.VERSION.SDK_INT>=26&&!a.getPackageManager().canRequestPackageInstalls()){a.startActivityForResult(new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+a.getPackageName())),INSTALL);return;}
        String id=pendingExternal;File apk=new File(new File(a.getFilesDir(),"apk-install"),id+".apk");verifyExternal(apk,id);Uri uri=Uri.parse("content://"+a.getPackageName()+".module-apk/"+id+".apk");a.getSharedPreferences("apps_host",0).edit().putString("pending_external",id).commit();a.startActivityForResult(new Intent(Intent.ACTION_INSTALL_PACKAGE).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).putExtra(Intent.EXTRA_RETURN_RESULT,true),INSTALL);pendingExternal=null;
    }catch(Exception e){a.toast(a.getString(R.string.module_install_failed));}}
    public boolean result(int request,int result,Intent data){
        if(request==INSTALL){
            if(pendingExternal!=null){if(a.getPackageManager().canRequestPackageInstalls())requestExternalInstall();else a.toast(a.getString(R.string.module_install_failed));}
            else{if(result==Activity.RESULT_OK)reconcile();else a.getSharedPreferences("apps_host",0).edit().remove("pending_external").commit();a.refreshScreen();}return true;
        }
        if(request==UNINSTALL){a.refreshScreen();return true;}
        if(request!=PICK)return false;
        if(result!=Activity.RESULT_OK||data==null||data.getData()==null||busy)return true;
        busy=true;Uri uri=data.getData();
        new Thread(()->{
            File temp=new File(a.getCacheDir(),"selected-app.ddapp");
            try{
                try(InputStream in=a.getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(temp)){copy(in,out,MAX);}
                String id;boolean external;
                try(ZipFile zip=new ZipFile(temp)){
                    external=zip.getEntry("assets/module.json")==null;
                    String path=external?"module.json":"assets/module.json";
                    JSONObject m=new JSONObject(new String(read(zip.getInputStream(zip.getEntry(path)),65536),"UTF-8"));
                    id=m.getString(external?"moduleId":"id");
                    if(external)importExternal(zip,m,id);else inspect(temp,id);
                }
                final String selected=id;
                a.runOnUiThread(()->{
                    if(a.isDestroyed()){busy=false;temp.delete();return;}
                    new ToolDialog(a).setTitle(title(selected)).setMessage(a.getString(external?R.string.module_external_note:R.string.module_trust_note))
                        .setPositiveButton(a.getString(R.string.module_install),(d,w)->{
                            if(external){busy=false;temp.delete();pendingExternal=selected;requestExternalInstall();return;}
                            new Thread(()->{
                                try{try(InputStream in=new FileInputStream(temp)){install(in,selected,null);}a.runOnUiThread(()->{if(!a.isDestroyed()){a.releaseModule(selected);a.refreshScreen();}});}
                                catch(Exception e){a.runOnUiThread(()->a.toast(a.getString(R.string.module_install_failed)));}
                                finally{temp.delete();busy=false;}
                            }).start();
                        }).setNegativeButton(a.getString(R.string.action_cancel),(d,w)->{busy=false;temp.delete();})
                        .setOnCancelListener(d->{busy=false;temp.delete();}).show();
                });
            }catch(Exception e){busy=false;temp.delete();a.runOnUiThread(()->a.toast(a.getString(R.string.module_install_failed)));}
        }).start();return true;
    }
    private void importExternal(ZipFile zip,JSONObject m,String id)throws Exception{
        if(!Arrays.asList(EXTERNAL).contains(id)||!"external-android-application".equals(m.optString("packageType"))||!"daidai-android-app/1".equals(m.optString("hostAdapterApi")))throw new IOException("Unsupported external package");
        JSONObject payload=m.getJSONObject("payload"),android=m.getJSONObject("android");String path="payload/"+id+".apk";
        if(!path.equals(payload.getString("file"))||!packageName(id).equals(android.getString("packageName")))throw new IOException("Wrong external identity");
        Set<String> allowed=new HashSet<>(Arrays.asList("module.json","configuration-contract.json","README.md",path));Set<String> actual=new HashSet<>();Enumeration<? extends ZipEntry> entries=zip.entries();
        while(entries.hasMoreElements()){ZipEntry e=entries.nextElement();if(!allowed.contains(e.getName())||!actual.add(e.getName())||e.isDirectory()||e.getSize()<0||e.getSize()>(e.getName().equals(path)?MAX:65536))throw new IOException("Invalid external entries");}
        if(!actual.equals(allowed))throw new IOException("Missing external entries");
        File dir=new File(a.getFilesDir(),"apk-install");dir.mkdirs();File apk=new File(dir,id+".apk");
        try(InputStream in=zip.getInputStream(zip.getEntry(path));OutputStream out=new FileOutputStream(apk)){copy(in,out,MAX);}
        if(!sha(apk).equals(payload.getString("sha256"))||apk.length()!=payload.getLong("bytes"))throw new IOException("External checksum mismatch");verifyExternal(apk,id);
        PackageInfo info=a.getPackageManager().getPackageArchiveInfo(apk.getAbsolutePath(),0);if(info.versionCode!=android.getInt("versionCode")||!info.versionName.equals(android.getString("versionName")))throw new IOException("External version mismatch");
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
