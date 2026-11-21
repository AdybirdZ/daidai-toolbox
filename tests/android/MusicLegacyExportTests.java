package org.simpleweather.qa;
import android.app.*;import android.content.*;import android.net.Uri;import android.os.*;import android.provider.DocumentsContract;import java.io.*;import java.lang.reflect.*;import java.nio.file.*;import java.util.*;import org.json.*;
/** Runs under the old music UID, including a seed run before the helper APK upgrade. */
public final class MusicLegacyExportTests extends Instrumentation {
    public static final String AUTH="org.simpleweather.migrationqa.documents";
    private Bundle args;public void onCreate(Bundle b){super.onCreate(b);args=b;start();}
    private Uri doc(String id){return DocumentsContract.buildDocumentUriUsingTree(DocumentsContract.buildTreeDocumentUri(AUTH,"root"),id);}
    private void write(Uri uri,byte[] data)throws Exception{try(OutputStream out=getTargetContext().getContentResolver().openOutputStream(uri,"wt")){out.write(data);}}
    private byte[] asset(String name)throws Exception{try(InputStream in=getContext().getAssets().open("music-tags/"+name);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toByteArray();}}
    public void onStart(){Bundle out=new Bundle();try{Context c=getTargetContext();c.startActivity(new Intent().setClassName(getContext(),"org.simpleweather.qa.MigrationGrantActivity").putExtra("target",c.getPackageName()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));Thread.sleep(500);waitForIdleSync();Uri tree=DocumentsContract.buildTreeDocumentUri(AUTH,"root");
        if("seed".equals(args.getString("mode"))){JSONArray tracks=new JSONArray();int i=0;for(String name:new String[]{"tagged.mp3","tagged.m4a"}){String display="migration-"+name;try{DocumentsContract.createDocument(c.getContentResolver(),doc("root"),"audio/"+(name.endsWith("mp3")?"mpeg":"mp4"),display);}catch(Exception ignored){}Uri uri=doc(display);write(uri,asset(name));JSONObject t=new JSONObject().put("uri",uri.toString()).put("title","Same title").put("composer","Composer A、Composer B").put("arranger","Arranger").put("vocalist","Vocalist").put("notes","Migration notes").put("tags","Tag A、Tag B").put("bpm","123.45").put("rating",8).put("ratingSet",true).put("instrumental",false).put("duration",1000);if(i++==0){File cover=new File(c.getFilesDir(),"migration-cover.png");byte[] image=android.util.Base64.decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aXioAAAAASUVORK5CYII=",0);Files.write(cover.toPath(),image);DocumentsContract.createDocument(c.getContentResolver(),doc("root"),"text/plain","migration.lrc");write(doc("migration.lrc"),"[00:00.00]Migration lyric".getBytes("UTF-8"));t.put("coverPath",cover.getAbsolutePath()).put("lrcPath",doc("migration.lrc").toString());}tracks.put(t);}
            JSONArray queue=new JSONArray().put(tracks.getJSONObject(1).getString("uri")).put(tracks.getJSONObject(0).getString("uri"));JSONObject collections=new JSONObject().put("favorites",new JSONArray().put(tracks.getJSONObject(0).getString("uri"))).put("playlistOrder",new JSONArray().put("Test playlist")).put("Test playlist",queue);c.getSharedPreferences("music_module",0).edit().clear().putString("catalogue",tracks.toString()).putString("queue",queue.toString()).putString("collections",collections.toString()).putString("storage_tree_uri",tree.toString()).putInt("volume_percent",17).putString("upgrade_sentinel","retained").commit();out.putString("result","PASS: legacy fixture seeded before APK update");
        }else{if(!c.getSharedPreferences("music_module",0).getString("upgrade_sentinel","").equals("retained"))throw new AssertionError("APK update lost existing preferences");Class<?> migration=Class.forName("org.daidai.modules.music.MusicMigration",true,c.getClassLoader());SharedPreferences prefs=c.getSharedPreferences("music_module",0);
            String valid=doc("migration-tagged.mp3").toString(), missing=doc("missing.mp3").toString();
            SharedPreferences.Editor stale=prefs.edit();
            for(String key:new String[]{"catalogue","queue","collections"}) stale.putString(key,prefs.getString(key,"").replace("migration-tagged.mp3","missing.mp3"));
            stale.commit();
            Method export=migration.getDeclaredMethod("exportData",Context.class,Map.class,Set.class);
            export.setAccessible(true);
            try{export.invoke(null,c,Collections.emptyMap(),Collections.emptySet());throw new AssertionError("Missing reference accepted");}
            catch(InvocationTargetException expected){if(!"unlinked".equals(expected.getCause().getMessage()))throw expected;}
            try{export.invoke(null,c,Collections.singletonMap(missing,doc("migration-tagged.m4a").toString()),Collections.emptySet());throw new AssertionError("Duplicate link accepted");}
            catch(InvocationTargetException expected){if(!"occupied".equals(expected.getCause().getMessage()))throw expected;}
            File partial=(File)export.invoke(null,c,Collections.emptyMap(),Collections.singleton(missing));
            try(java.util.zip.ZipFile zip=new java.util.zip.ZipFile(partial);Scanner reader=new Scanner(zip.getInputStream(zip.getEntry("music.json")),"UTF-8")){
                JSONObject root=new JSONObject(reader.useDelimiter("\\A").next()), data=root.getJSONObject("preferences");
                if(root.getJSONArray("songs").length()!=1||new JSONArray(data.getJSONObject("queue").getString("value")).length()!=1||
                    new JSONObject(data.getJSONObject("collections").getString("value")).getJSONArray("favorites").length()!=0)throw new AssertionError("Skipped references remain");
            }finally{partial.delete();}
            File file=(File)export.invoke(null,c,Collections.singletonMap(missing,valid),Collections.emptySet());
            if(!prefs.getString("catalogue","").contains("missing.mp3"))throw new AssertionError("Export changed old library");
            DocumentsContract.createDocument(c.getContentResolver(),doc("root"),"application/zip","migration.zip");write(doc("migration.zip"),Files.readAllBytes(file.toPath()));file.delete();out.putString("result","PASS: old UID export; original-signature APK update retained preferences and attachments");}
    }catch(Throwable e){StringWriter s=new StringWriter();e.printStackTrace(new PrintWriter(s));out.putString("result","FAIL: "+s);}finish(Activity.RESULT_OK,out);}
}
