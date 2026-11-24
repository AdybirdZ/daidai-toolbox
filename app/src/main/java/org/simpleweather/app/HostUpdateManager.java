package org.simpleweather.app;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.content.pm.PackageInstaller;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Locale;

/** Checks official stable GitHub releases and stages a user-confirmed host update. */
public final class HostUpdateManager {
    private static final String RELEASES="https://api.github.com/repos/AdybirdZ/daidai-toolbox/releases/latest";
    private static final String RELEASE_MANIFEST="https://raw.githubusercontent.com/AdybirdZ/daidai-toolbox/main/modules/store/host-release.json";
    private static final long MAX_APK=80L*1024*1024;
    private static final int REQUEST_INSTALL_SOURCE=8460;
    private static final long CHECK_INTERVAL=5*60*1000L;
    private static final long CACHE_MAX_AGE=7*24*60*60*1000L;
    private static final String CERT_SHA256="b08f1aa17fc9c7a91f755290e1d3edcc68c88661cb93935f34ef811828bc0e95";
    public enum CheckState { NETWORK, CACHE, THROTTLED, UP_TO_DATE, STALE }
    private static volatile CheckState lastState=CheckState.NETWORK;
    private HostUpdateManager(){}

    static final class Release {
        public final String version,downloadUrl,sha256,notes,certificateSha256;
        public final long bytes;
        public final boolean available,cached;
        private Release(String version,String downloadUrl,String sha256,long bytes,String notes,String certificateSha256,boolean available,boolean cached){this.version=version;this.downloadUrl=downloadUrl;this.sha256=sha256;this.bytes=bytes;this.notes=notes;this.certificateSha256=certificateSha256;this.available=available;this.cached=cached;}
    }
    static final class DownloadTask {
        public volatile long received,total;
        public volatile boolean resumed;
        private volatile HttpURLConnection connection;
        private volatile boolean cancelled;
        public synchronized void cancel(){cancelled=true;if(connection!=null)connection.disconnect();}
        public boolean isCancelled(){return cancelled;}
        private void check()throws InterruptedIOException{if(cancelled||Thread.currentThread().isInterrupted())throw new InterruptedIOException("Cancelled");}
    }
    public static CheckState lastState(){return lastState;}
    public static Release latest(Context context)throws Exception{return latest(context,false);}
    public static Release latest(Context context,boolean force)throws Exception{
        File cache=cacheFile(context);long now=System.currentTimeMillis();android.content.SharedPreferences preferences=context.getSharedPreferences("host_update",0);long lastAttempt=preferences.getLong("last_attempt",0);
        if(!force&&now-lastAttempt>=0&&now-lastAttempt<CHECK_INTERVAL){Release saved=cache.isFile()&&now-cache.lastModified()<CACHE_MAX_AGE?readCache(cache,installedVersion(context)):null;lastState=saved!=null&&!saved.available&&compareVersions(saved.version,installedVersion(context))<0?CheckState.STALE:CheckState.THROTTLED;if(saved!=null)return new Release(saved.version,saved.downloadUrl,saved.sha256,saved.bytes,saved.notes,saved.certificateSha256,saved.available,true);throw new IOException("Host update check is throttled; no cached result is available");}
        preferences.edit().putLong("last_attempt",now).apply();
        Exception manifestFailure=null;
        try{Release result=fetchManifestRelease(context);writeCache(cache,result);lastState=result.available?CheckState.NETWORK:compareVersions(result.version,installedVersion(context))==0?CheckState.UP_TO_DATE:CheckState.STALE;return result;}catch(Exception failure){manifestFailure=failure;}
        try{Release result=fetchApiRelease(context);writeCache(cache,result);lastState=result.available?CheckState.NETWORK:compareVersions(result.version,installedVersion(context))==0?CheckState.UP_TO_DATE:CheckState.STALE;return result;}catch(Exception failure){if(manifestFailure!=null)failure.addSuppressed(manifestFailure);Release saved=cache.isFile()&&now-cache.lastModified()<CACHE_MAX_AGE?readCache(cache,installedVersion(context)):null;if(saved!=null){lastState=!saved.available&&compareVersions(saved.version,installedVersion(context))<0?CheckState.STALE:CheckState.CACHE;return new Release(saved.version,saved.downloadUrl,saved.sha256,saved.bytes,saved.notes,saved.certificateSha256,saved.available,true);}throw failure;}
    }
    private static Release fetchApiRelease(Context context)throws Exception{
        HttpURLConnection connection=open(RELEASES);
        try{connection.setRequestProperty("Accept","application/vnd.github+json");connection.setRequestProperty("X-GitHub-Api-Version","2022-11-28");checkResponse(connection);return parseLatestRelease(new String(read(connection,1024*1024),"UTF-8"),installedVersion(context));}
        finally{connection.disconnect();}
    }
    private static Release fetchManifestRelease(Context context)throws Exception{
        HttpURLConnection connection=open(RELEASE_MANIFEST);
        try{checkResponse(connection);return parseReleaseManifest(new String(read(connection,64*1024),"UTF-8"),installedVersion(context));}
        finally{connection.disconnect();}
    }
    static Release parseLatestRelease(String body,String installed)throws Exception{
            JSONObject release=new JSONObject(body);
            if(release.optBoolean("draft")||release.optBoolean("prerelease"))throw new IOException("No stable release");
            String tag=release.optString("tag_name");if(!tag.matches("v[0-9]+\\.[0-9]+\\.[0-9]+"))throw new IOException("Invalid release tag");
            String version=tag.substring(1);JSONArray assets=release.optJSONArray("assets");if(assets==null)throw new IOException("Release assets are missing");
            JSONObject apk=null;for(int n=0;n<assets.length();n++){JSONObject asset=assets.optJSONObject(n);if(asset!=null&&("DaidaiToolbox-"+version+".apk").equals(asset.optString("name"))){apk=asset;break;}}
            if(apk==null)throw new IOException("Host APK is missing");
            String url=apk.optString("browser_download_url"),digest=apk.optString("digest");long size=apk.optLong("size",-1);
            String expectedUrl="https://github.com/AdybirdZ/daidai-toolbox/releases/download/v"+version+"/DaidaiToolbox-"+version+".apk";
            if(!expectedUrl.equals(url)||!digest.matches("sha256:[0-9a-f]{64}")||size<=0||size>MAX_APK)throw new IOException("Invalid host APK metadata");
            return new Release(version,url,digest.substring(7),size,release.optString("body"),CERT_SHA256,newer(version,installed),false);
    }
    static Release parseReleaseManifest(String body,String installed)throws Exception{
        JSONObject manifest=new JSONObject(body);String format=manifest.optString("format"),version=manifest.optString("version"),url=manifest.optString("url"),digest=manifest.optString("sha256"),certificate=manifest.optString("certificateSha256");long size=manifest.optLong("bytes",-1);
        if(!"daidai-toolbox-host-update".equals(format)||!version.matches("[0-9]+\\.[0-9]+\\.[0-9]+"))throw new IOException("Invalid host update manifest");
        String expectedUrl="https://github.com/AdybirdZ/daidai-toolbox/releases/download/v"+version+"/DaidaiToolbox-"+version+".apk";
        if(!expectedUrl.equals(url)||!digest.matches("[0-9a-f]{64}")||!certificate.matches("[0-9a-f]{64}")||!CERT_SHA256.equals(certificate)||size<=0||size>MAX_APK)throw new IOException("Invalid host update metadata");
        return new Release(version,url,digest,size,manifest.optString("notes"),certificate,newer(version,installed),false);
    }
    private static String installedVersion(Context context){try{return context.getPackageManager().getPackageInfo(context.getPackageName(),0).versionName;}catch(Exception e){return "0.0.0";}}
    private static boolean newer(String remote,String local){
        return compareVersions(remote,local)>0;
    }
    static int compareVersions(String first,String second){try{String[] a=first.split("\\."),b=second.split("\\.");for(int i=0;i<3;i++){int x=Integer.parseInt(a[i]),y=i<b.length?Integer.parseInt(b[i]):0;if(x!=y)return Integer.compare(x,y);}return 0;}catch(Exception invalid){return 0;}}
    static boolean shouldRestartRange(int responseCode){return responseCode==200||responseCode==416;}
    private static File cacheFile(Context context){return new File(new File(context.getFilesDir(),"host-update"),"check.json");}
    private static void writeCache(File file,Release release)throws Exception{if(!file.getParentFile().isDirectory()&&!file.getParentFile().mkdirs())throw new IOException("Cannot create update directory");JSONObject value=new JSONObject().put("checkedAt",System.currentTimeMillis()).put("version",release.version).put("url",release.downloadUrl).put("sha256",release.sha256).put("bytes",release.bytes).put("notes",release.notes).put("certificateSha256",release.certificateSha256);File pending=new File(file.getParentFile(),file.getName()+".pending");try(FileOutputStream out=new FileOutputStream(pending)){out.write(value.toString().getBytes("UTF-8"));out.getFD().sync();}Files.move(pending.toPath(),file.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);}
    private static Release readCache(File file,String installed)throws Exception{JSONObject value=new JSONObject(new String(java.nio.file.Files.readAllBytes(file.toPath()),"UTF-8"));String version=value.optString("version");if(!version.matches("[0-9]+\\.[0-9]+\\.[0-9]+"))throw new IOException("Invalid cached host update");String url=value.optString("url"),digest=value.optString("sha256"),certificate=value.optString("certificateSha256");long bytes=value.optLong("bytes",-1);if(!url.equals("https://github.com/AdybirdZ/daidai-toolbox/releases/download/v"+version+"/DaidaiToolbox-"+version+".apk")||!digest.matches("[0-9a-f]{64}")||!CERT_SHA256.equals(certificate)||bytes<=0||bytes>MAX_APK)throw new IOException("Invalid cached host update");return new Release(version,url,digest,bytes,value.optString("notes"),certificate,newer(version,installed),true);}
    public static File download(Context context,Release release,DownloadTask task)throws Exception{
        if(release==null||!release.sha256.matches("[0-9a-f]{64}")||!CERT_SHA256.equals(release.certificateSha256)||release.bytes<=0||release.bytes>MAX_APK)throw new IOException("Invalid host update");
        File directory=new File(context.getFilesDir(),"host-update");if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("Cannot create update directory");
        File temporary=new File(directory,"host.apk.pending"),metadata=new File(directory,"host.apk.pending.json"),target=new File(directory,"host.apk");task.check();if(!matchesPartial(metadata,release)){temporary.delete();metadata.delete();}long offset=temporary.isFile()?temporary.length():0;if(offset>=release.bytes){temporary.delete();metadata.delete();offset=0;}if(context.getFilesDir().getUsableSpace()<release.bytes-offset+1024*1024)throw new IOException("Insufficient update storage");task.received=offset;task.total=release.bytes;task.resumed=offset>0;
        HttpURLConnection connection=open(release.downloadUrl);if(offset>0)connection.setRequestProperty("Range","bytes="+offset+"-");task.connection=connection;
        try{
            task.check();
            int response=connection.getResponseCode();
            if(offset>0&&response!=206&&shouldRestartRange(response)){connection.disconnect();offset=0;task.received=0;task.resumed=false;temporary.delete();metadata.delete();connection=open(release.downloadUrl);task.connection=connection;response=connection.getResponseCode();}
            checkResponse(connection);
            if(offset>0){String range=connection.getHeaderField("Content-Range");if(range==null||!range.startsWith("bytes "+offset+"-")){temporary.delete();metadata.delete();throw new IOException("Invalid partial update response");}}
            writePartialMetadata(metadata,release);
            try(InputStream input=connection.getInputStream();FileOutputStream output=new FileOutputStream(temporary,offset>0)){
                MessageDigest digest=MessageDigest.getInstance("SHA-256");byte[] buffer=new byte[32768];int count;if(offset>0)try(InputStream existing=new FileInputStream(temporary)){while((count=existing.read(buffer))!=-1)digest.update(buffer,0,count);}
                while((count=input.read(buffer))!=-1){task.check();task.received+=count;if(task.received>release.bytes||task.received>MAX_APK)throw new IOException("Host APK size mismatch");digest.update(buffer,0,count);output.write(buffer,0,count);}
                output.getFD().sync();
                if(task.received!=release.bytes)throw new IOException("Host APK size mismatch");if(!hex(digest.digest()).equals(release.sha256))throw new IOException("Host APK checksum mismatch");
            }
        }catch(Exception failure){if(task.cancelled)throw new InterruptedIOException("Cancelled");if(!isNetworkFailure(failure)){temporary.delete();metadata.delete();}throw failure;}finally{task.connection=null;connection.disconnect();}
        try{verify(context,temporary,release.version);verifyCertificate(context,temporary,release.certificateSha256);}catch(Exception invalid){temporary.delete();metadata.delete();throw invalid;}
        Files.move(temporary.toPath(),target.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        metadata.delete();
        return target;
    }
    public static boolean canResume(Context context,Release release){File directory=new File(context.getFilesDir(),"host-update");File partial=new File(directory,"host.apk.pending"),metadata=new File(directory,"host.apk.pending.json");return partial.isFile()&&partial.length()>0&&matchesPartial(metadata,release);}
    private static boolean matchesPartial(File metadata,Release release){try{JSONObject saved=new JSONObject(new String(java.nio.file.Files.readAllBytes(metadata.toPath()),"UTF-8"));return release.version.equals(saved.optString("version"))&&release.downloadUrl.equals(saved.optString("url"))&&release.sha256.equals(saved.optString("sha256"))&&release.bytes==saved.optLong("bytes",-1);}catch(Exception ignored){return false;}}
    private static void writePartialMetadata(File file,Release release)throws Exception{JSONObject value=new JSONObject().put("version",release.version).put("url",release.downloadUrl).put("sha256",release.sha256).put("bytes",release.bytes);try(FileOutputStream out=new FileOutputStream(file)){out.write(value.toString().getBytes("UTF-8"));out.getFD().sync();}}
    private static boolean isNetworkFailure(Throwable failure){for(Throwable current=failure;current!=null;current=current.getCause())if(current instanceof UnknownHostException||current instanceof SocketTimeoutException||current instanceof ConnectException||current instanceof NoRouteToHostException||current instanceof SocketException||current instanceof EOFException||current instanceof javax.net.ssl.SSLException)return true;return false;}
    static int errorMessage(Throwable failure){if(failure instanceof SecurityException)return R.string.host_update_error_permission;String message=String.valueOf(failure.getMessage()).toLowerCase(Locale.ROOT);if(message.contains("throttled"))return R.string.host_update_error_throttled;if(message.contains("storage"))return R.string.host_update_error_storage;if(message.contains("checksum")||message.contains("size mismatch")||message.contains("signature")||message.contains("certificate")||message.contains("metadata")||message.contains("invalid host")||message.contains("partial update"))return R.string.host_update_error_integrity;if(isNetworkFailure(failure)||message.contains("http "))return R.string.host_update_error_network;return R.string.host_update_error_unknown;}
    private static void verifyCertificate(Context context,File apk,String expected)throws Exception{int flags=Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES;PackageInfo candidate=context.getPackageManager().getPackageArchiveInfo(apk.getAbsolutePath(),flags);if(candidate==null)throw new IOException("Host APK is invalid");android.content.pm.Signature[] signatures=Build.VERSION.SDK_INT>=28?candidate.signingInfo.getApkContentsSigners():candidate.signatures;if(signatures==null||signatures.length!=1||!hex(MessageDigest.getInstance("SHA-256").digest(signatures[0].toByteArray())).equals(expected))throw new IOException("Host APK certificate mismatch");}
    public static void verify(Context context,File apk,String version)throws Exception{
        int flags=Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES;
        PackageInfo candidate=context.getPackageManager().getPackageArchiveInfo(apk.getAbsolutePath(),flags);
        PackageInfo installed=context.getPackageManager().getPackageInfo(context.getPackageName(),flags);
        if(candidate==null||!context.getPackageName().equals(candidate.packageName)||!version.equals(candidate.versionName)||candidate.versionCode<=installed.versionCode)throw new IOException("Host APK version mismatch");
        android.content.pm.Signature[] own=Build.VERSION.SDK_INT>=28?installed.signingInfo.getApkContentsSigners():installed.signatures;
        android.content.pm.Signature[] next=Build.VERSION.SDK_INT>=28?candidate.signingInfo.getApkContentsSigners():candidate.signatures;
        if(own==null||next==null||own.length!=1||next.length!=1||!MessageDigest.isEqual(own[0].toByteArray(),next[0].toByteArray()))throw new IOException("Host APK signature mismatch");
    }
    public static boolean hasInstallPermission(Context context){return Build.VERSION.SDK_INT<26||context.getPackageManager().canRequestPackageInstalls();}
    public static void requestInstallPermission(Activity activity){
        if(Build.VERSION.SDK_INT<26)return;
        Intent intent=new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+activity.getPackageName()));
        try{activity.startActivityForResult(intent,REQUEST_INSTALL_SOURCE);}catch(Exception e){Toast.makeText(activity,activity.getString(R.string.host_update_permission_unavailable),Toast.LENGTH_LONG).show();}
    }
    public static int installPermissionRequestCode(){return REQUEST_INSTALL_SOURCE;}
    public static File pendingFile(Context context){return new File(new File(context.getFilesDir(),"host-update"),"host.apk");}
    public static void installPending(Activity activity)throws Exception{
        if(!hasInstallPermission(activity))throw new SecurityException("Install permission is not enabled");
        install(activity,pendingFile(activity));
    }
    public static void install(Activity activity,File apk)throws Exception{
        if(!hasInstallPermission(activity))throw new SecurityException("Install permission is not enabled");
        verifyPending(activity,apk);PackageInstaller installer=activity.getPackageManager().getPackageInstaller();PackageInstaller.SessionParams params=new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);params.setAppPackageName(activity.getPackageName());
        int sessionId=installer.createSession(params);
        try(PackageInstaller.Session session=installer.openSession(sessionId);InputStream input=new FileInputStream(apk);OutputStream output=session.openWrite("base.apk",0,apk.length())){
            byte[] buffer=new byte[32768];int count;while((count=input.read(buffer))!=-1)output.write(buffer,0,count);session.fsync(output);
            Intent callback=new Intent(activity,HostUpdateReceiver.class).setAction(HostUpdateReceiver.ACTION).putExtra(PackageInstaller.EXTRA_SESSION_ID,sessionId);
            int flags=PendingIntent.FLAG_UPDATE_CURRENT|(Build.VERSION.SDK_INT>=31?PendingIntent.FLAG_MUTABLE:0);
            PendingIntent pending=PendingIntent.getBroadcast(activity,sessionId,callback,flags);session.commit(pending.getIntentSender());
        }catch(Exception failure){installer.abandonSession(sessionId);throw failure;}
    }
    private static void verifyPending(Activity activity,File apk)throws Exception{
        if(!apk.isFile()||apk.length()<=0||apk.length()>MAX_APK)throw new IOException("Pending host APK is unavailable");
        int flags=Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES;
        PackageInfo candidate=activity.getPackageManager().getPackageArchiveInfo(apk.getAbsolutePath(),flags);if(candidate==null)throw new IOException("Pending host APK is invalid");verify(activity,apk,candidate.versionName);
    }
    private static HttpURLConnection open(String address)throws IOException{
        URL url=new URL(address);if(!"https".equalsIgnoreCase(url.getProtocol()))throw new IOException("HTTPS required");
        HttpURLConnection connection=(HttpURLConnection)url.openConnection();connection.setConnectTimeout(12000);connection.setReadTimeout(20000);connection.setInstanceFollowRedirects(true);connection.setRequestProperty("User-Agent","DaidaiToolbox/3.0.9 Android");return connection;
    }
    private static void checkResponse(HttpURLConnection connection)throws IOException{
        int code=connection.getResponseCode();if(code<200||code>=300){connection.disconnect();throw new IOException("HTTP "+code);}
        String host=connection.getURL().getHost().toLowerCase(Locale.ROOT);if(!host.equals("api.github.com")&&!host.equals("github.com")&&!host.equals("raw.githubusercontent.com")&&!host.equals("release-assets.githubusercontent.com")&&!host.equals("objects.githubusercontent.com")){connection.disconnect();throw new IOException("Unsupported host update origin");}
    }
    private static byte[] read(HttpURLConnection connection,int maximum)throws IOException{
        try(InputStream in=connection.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[8192];int count;while((count=in.read(buffer))!=-1){if(out.size()+count>maximum)throw new IOException("Update metadata is too large");out.write(buffer,0,count);}return out.toByteArray();}
    }
    private static String hex(byte[] bytes){StringBuilder value=new StringBuilder(bytes.length*2);for(byte b:bytes)value.append(String.format(Locale.ROOT,"%02x",b&255));return value.toString();}
}
