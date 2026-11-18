package org.daidai.modules.music;

import android.app.Activity;
import org.json.JSONObject;
import java.io.*;
import java.nio.file.*;
import java.util.UUID;
import java.util.*;
import org.json.JSONArray;

/** A saved-instance token avoids sending a large URI selection through Android's Binder. */
final class MusicUiState {
    private static final int MAX=16*1024*1024;
    static String save(Activity a,String previous,JSONObject state){
        File temp=null;try{
            String token=valid(previous)?previous:UUID.randomUUID().toString();byte[] data=state.toString().getBytes("UTF-8");if(data.length>MAX)return "";
            temp=File.createTempFile("music-ui-",".tmp",a.getCacheDir());try(OutputStream out=new FileOutputStream(temp)){out.write(data);}
            Files.move(temp.toPath(),file(a,token).toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);temp=null;return token;
        }catch(Exception ignored){return "";}finally{if(temp!=null)temp.delete();}
    }
    static JSONObject read(Activity a,String token){
        if(!valid(token))return null;try{File f=file(a,token);if(!f.isFile()||f.length()>MAX)return null;byte[] data=Files.readAllBytes(f.toPath());if(data.length>MAX)return null;JSONObject state=new JSONObject(new String(data,"UTF-8"));if(state.optInt("version")!=1)return null;Map<String,String> known=new HashMap<>();for(MusicLibrary.Track song:MusicLibrary.catalogue(a))known.put(MusicLibrary.identity(song.uri),song.uri);JSONObject library=state.optJSONObject("library");if(library!=null){reconcile(library,"selected",known);reconcile(library,"folder",known);JSONObject editor=library.optJSONObject("editor");if(editor!=null){String uri=known.get(MusicLibrary.identity(editor.optString("uri")));if(uri==null)library.remove("editor");else editor.put("uri",uri);}}reconcile(state,"queue_selection",known);return state;}catch(Exception ignored){return null;}
    }
    private static void reconcile(JSONObject state,String key,Map<String,String> known)throws Exception{JSONArray old=state.optJSONArray(key),valid=new JSONArray();HashSet<String> seen=new HashSet<>();if(old!=null)for(int i=0;i<old.length();i++){String uri=known.get(MusicLibrary.identity(old.optString(i)));if(uri!=null&&seen.add(uri))valid.put(uri);}state.put(key,valid);}
    private static boolean valid(String token){return token!=null&&token.matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}");}
    private static File file(Activity a,String token){return new File(a.getCacheDir(),"music-ui-"+token+".json");}
}
