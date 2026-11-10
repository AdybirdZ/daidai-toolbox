package org.daidai.prototype.qa;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.widget.ImageView;
import org.daidai.modules.music.*;
import org.json.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;

/** Device checks for files transferred to local storage with embedded tags. */
public final class MusicMetadataDeviceChecks {
    private static int checks;
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError("Embedded music metadata: "+why);}
    private static File copyAsset(Context target,Context test,String ext)throws Exception{
        File file=new File(target.getFilesDir(),"transferred-tagged."+ext);
        try(InputStream in=test.getAssets().open("music-tags/tagged."+ext);OutputStream out=new FileOutputStream(file)){byte[] b=new byte[8192];int n;while((n=in.read(b))>=0)out.write(b,0,n);}
        return file;
    }
    public static int run(Instrumentation runner,Activity activity)throws Exception{
        checks=0;Context c=runner.getTargetContext(),test=runner.getContext();SharedPreferences prefs=c.getSharedPreferences("music_module",0);Map<String,?> before=new HashMap<>(prefs.getAll());
        try{
            prefs.edit().clear().commit();String[] extensions={"mp3","flac","m4a"};ArrayList<File> files=new ArrayList<>();
            for(String ext:extensions)files.add(copyAsset(c,test,ext));
            for(int i=0;i<files.size();i++){
                File file=files.get(i);String ext=extensions[i];MusicLibrary.Track t=MusicLibrary.readMetadata(c,Uri.fromFile(file));MusicLibrary.discover(c,Collections.singleton(t));
                check(t.title.equals("Imported "+ext.toUpperCase(Locale.ROOT)+" title"),ext+" title decoded");check(t.bpmText.equals("flac".equals(ext)?"123.4":"123"),ext+" embedded BPM imported without rounding");check(t.vocalist.contains("Windows Artist A")&&t.vocalist.contains("Windows Artist B"),ext+" contributing artists import into vocalist");check(!t.vocalist.contains("Album artist is different"),ext+" album artist does not override contributing artists");check(t.composer.equals("Fixture composer"),ext+" composer imported");
                File cover=new File(t.coverPath);check(cover.isFile()&&cover.getCanonicalPath().startsWith(c.getFilesDir().getCanonicalPath()+File.separator),ext+" cover durable app-private file");Bitmap bitmap=BitmapFactory.decodeFile(t.coverPath);check(bitmap!=null&&bitmap.getWidth()==16&&bitmap.getHeight()==16,ext+" embedded cover decodes");if(bitmap!=null){check(bitmap.getPixel(4,4)==Color.rgb(23,145,210),ext+" extracted cover matches original");bitmap.recycle();}
            }
            MusicLibrary.Track original=MusicLibrary.find(c,Uri.fromFile(files.get(0)).toString());check(original!=null,"original record available");
            JSONObject legacy=new JSONObject().put("uri",original.uri).put("title","Old saved title").put("vocalist","Existing artist").put("bpm",0).put("rating",-1).put("coverPath","");
            prefs.edit().putString("catalogue",new JSONArray().put(legacy).toString()).putString("queue",new JSONArray().put(original.uri).toString()).commit();
            MusicLibrary.discover(c,Collections.singleton(MusicLibrary.readMetadata(c,Uri.fromFile(files.get(0)))));MusicLibrary.Track enriched=MusicLibrary.find(c,original.uri);
            check(enriched.bpmText.equals("123")&&!enriched.coverPath.isEmpty(),"old imported record gains missing BPM and artwork during rescan");check(enriched.title.equals("Old saved title")&&enriched.vocalist.equals("Existing artist"),"old nonempty metadata preserved while filling missing fields");
            MusicLibrary.Track edited=enriched.withMeta("Manual title","Manual vocalist","Manual composer","Manual arranger","Manual note","Manual tag",150,0,false,enriched.lrcPath,enriched.coverPath);MusicLibrary.update(c,edited);MusicLibrary.markMetadataEdited(c,edited.uri);MusicLibrary.discover(c,Collections.singleton(MusicLibrary.readMetadata(c,Uri.fromFile(files.get(0)))));MusicLibrary.Track kept=MusicLibrary.find(c,original.uri);
            check(kept.title.equals("Manual title")&&kept.vocalist.equals("Manual vocalist")&&kept.bpmText.equals("150")&&kept.rating==0,"rescan preserves manual edits including zero rating");
            // The player UI uses the durable coverPath above; UI playback remains covered by the existing suite.
            File copied=new File(c.getFilesDir(),"copied-tagged.mp3");try(InputStream in=new FileInputStream(files.get(0));OutputStream out=new FileOutputStream(copied)){byte[] b=new byte[8192];int n;while((n=in.read(b))>=0)out.write(b,0,n);}MusicLibrary.Track copy=MusicLibrary.readMetadata(c,Uri.fromFile(copied));check(copy.bpmText.equals("123")&&copy.vocalist.contains("Windows Artist A")&&!copy.coverPath.isEmpty(),"byte-for-byte storage copy retains artwork BPM and contributing artists");
            return checks;
        }finally{
            // Do not alter the activity or playback service here; the enclosing suite owns their lifecycle.
            SharedPreferences.Editor e=prefs.edit().clear();for(Map.Entry<String,?> row:before.entrySet()){Object v=row.getValue();if(v instanceof String)e.putString(row.getKey(),(String)v);else if(v instanceof Boolean)e.putBoolean(row.getKey(),(Boolean)v);else if(v instanceof Integer)e.putInt(row.getKey(),(Integer)v);else if(v instanceof Long)e.putLong(row.getKey(),(Long)v);else if(v instanceof Float)e.putFloat(row.getKey(),(Float)v);else if(v instanceof Set)e.putStringSet(row.getKey(),new HashSet<>((Set<String>)v));}e.commit();
        }
    }
}
