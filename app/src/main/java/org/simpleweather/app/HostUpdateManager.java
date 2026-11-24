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
    private static final long MAX_APK=80L*1024*1024;
    private static final int REQUEST_INSTALL_SOURCE=8460;
    private HostUpdateManager(){}

    static final class Release {
        public final String version,downloadUrl,sha256,notes;
        public final long bytes;
        private Release(String version,String downloadUrl,String sha256,long bytes,String notes){this.version=version;this.downloadUrl=downloadUrl;this.sha256=sha256;this.bytes=bytes;this.notes=notes;}
    }
    static final class DownloadTask {
        public volatile long received,total;
        private volatile HttpURLConnection connection;
        private volatile boolean cancelled;
        public synchronized void cancel(){cancelled=true;if(connection!=null)connection.disconnect();}
        public boolean isCancelled(){return cancelled;}
        private void check()throws InterruptedIOException{if(cancelled||Thread.currentThread().isInterrupted())throw new InterruptedIOException("Cancelled");}
    }
    public static Release latest(Context context)throws Exception{
        HttpURLConnection connection=open(RELEASES);
        try{
            connection.setRequestProperty("Accept","application/vnd.github+json");
            connection.setRequestProperty("X-GitHub-Api-Version","2022-11-28");
            checkResponse(connection);
            byte[] bytes=read(connection,1024*1024);
            return parseLatestRelease(new String(bytes,"UTF-8"),installedVersion(context));
        }finally{connection.disconnect();}
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
            if(!newer(version,installed))return null;
            return new Release(version,url,digest.substring(7),size,release.optString("body"));
    }
    private static String installedVersion(Context context){try{return context.getPackageManager().getPackageInfo(context.getPackageName(),0).versionName;}catch(Exception e){return "0.0.0";}}
    private static boolean newer(String remote,String local){
        try{String[] a=remote.split("\\."),b=local.split("\\.");for(int i=0;i<3;i++){int x=Integer.parseInt(a[i]),y=i<b.length?Integer.parseInt(b[i]):0;if(x!=y)return x>y;}return false;}catch(Exception e){return false;}
    }
    public static File download(Context context,Release release,DownloadTask task)throws Exception{
        if(release==null||!release.sha256.matches("[0-9a-f]{64}")||release.bytes<=0||release.bytes>MAX_APK)throw new IOException("Invalid host update");
        File directory=new File(context.getFilesDir(),"host-update");if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("Cannot create update directory");
        File temporary=new File(directory,"host.apk.pending"),target=new File(directory,"host.apk");if(temporary.exists())temporary.delete();task.check();
        HttpURLConnection connection=open(release.downloadUrl);task.connection=connection;task.total=release.bytes;
        try{
            task.check();
            checkResponse(connection);
            try(InputStream input=connection.getInputStream();FileOutputStream output=new FileOutputStream(temporary)){
                MessageDigest digest=MessageDigest.getInstance("SHA-256");byte[] buffer=new byte[32768];int count;
                while((count=input.read(buffer))!=-1){task.check();task.received+=count;if(task.received>release.bytes||task.received>MAX_APK)throw new IOException("Host APK size mismatch");digest.update(buffer,0,count);output.write(buffer,0,count);}
                output.getFD().sync();
                if(task.received!=release.bytes||!hex(digest.digest()).equals(release.sha256))throw new IOException("Host APK checksum mismatch");
            }
        }catch(Exception failure){temporary.delete();if(task.cancelled)throw new InterruptedIOException("Cancelled");throw failure;}finally{task.connection=null;connection.disconnect();}
        verify(context,temporary,release.version);
        Files.move(temporary.toPath(),target.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        return target;
    }
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
        HttpURLConnection connection=(HttpURLConnection)url.openConnection();connection.setConnectTimeout(12000);connection.setReadTimeout(20000);connection.setInstanceFollowRedirects(true);connection.setRequestProperty("User-Agent","DaidaiToolbox/3.0.8 Android");return connection;
    }
    private static void checkResponse(HttpURLConnection connection)throws IOException{
        int code=connection.getResponseCode();if(code<200||code>=300){connection.disconnect();throw new IOException("HTTP "+code);}
        String host=connection.getURL().getHost().toLowerCase(Locale.ROOT);if(!host.equals("api.github.com")&&!host.equals("github.com")&&!host.equals("release-assets.githubusercontent.com")&&!host.equals("objects.githubusercontent.com")){connection.disconnect();throw new IOException("Unsupported host update origin");}
    }
    private static byte[] read(HttpURLConnection connection,int maximum)throws IOException{
        try(InputStream in=connection.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[8192];int count;while((count=in.read(buffer))!=-1){if(out.size()+count>maximum)throw new IOException("Update metadata is too large");out.write(buffer,0,count);}return out.toByteArray();}
    }
    private static String hex(byte[] bytes){StringBuilder value=new StringBuilder(bytes.length*2);for(byte b:bytes)value.append(String.format(Locale.ROOT,"%02x",b&255));return value.toString();}
}
