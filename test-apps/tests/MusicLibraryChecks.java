package org.daidai.prototype.qa;

import android.content.Context;
import android.content.SharedPreferences;
import org.daidai.modules.music.MusicLibrary;
import org.daidai.modules.music.MusicCollections;
import org.daidai.modules.music.MusicSearch;
import org.daidai.modules.music.MusicPeople;
import org.daidai.modules.music.MusicCredits;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;

/** Device-backed regression checks for catalogue migration and queue/collection independence. */
public final class MusicLibraryChecks {
    public static int run(Context c)throws Exception{
        SharedPreferences prefs=c.getSharedPreferences("music_module",0);Map<String,?> before=new HashMap<>(prefs.getAll());int checks=0;
        try{
            prefs.edit().clear().commit();
            check(MusicPeople.split(" 甲；乙、甲, 丙\n丁 ").equals(Arrays.asList("甲","乙","丙","丁")),"legacy personnel split and deduplicate in original order");checks++;
            check(MusicPeople.split("Simon & Garfunkel; AC/DC").equals(Arrays.asList("Simon & Garfunkel","AC/DC")),"person parsing preserves names containing ampersand or slash");checks++;
            check(MusicPeople.split(MusicPeople.join(Arrays.asList(" 甲 ","乙","甲","",null))).equals(Arrays.asList("甲","乙")),"individual name editing trims empty and repeated names");checks++;
            check(MusicCredits.order(c).equals(Arrays.asList("composer","arranger","vocalist"))&&MusicCredits.visible(c).equals(new HashSet<>(Arrays.asList("composer","arranger"))),"default credits order and visibility");checks++;
            MusicLibrary.Track credits=new MusicLibrary.Track("test:credits","Title","丁、小明","甲","乙; 丙","","","",120,-1,false,0,"");
            String rendered=MusicCredits.format(c,credits);check(rendered.contains("甲")&&rendered.contains("乙")&&rendered.contains("丙")&&!rendered.contains("丁")&&rendered.indexOf("甲")<rendered.indexOf("乙"),"default credits omit vocalist and preserve personnel order");checks++;
            MusicCredits.save(c,Arrays.asList("vocalist","composer","arranger"),new HashSet<>(Arrays.asList("vocalist","composer")));rendered=MusicCredits.format(c,credits);check(rendered.contains("丁")&&rendered.contains("小明")&&!rendered.contains("乙")&&rendered.indexOf("丁")<rendered.indexOf("甲"),"saved visibility and order apply to common credits formatter");checks++;
            prefs.edit().putString("credit_roles_order","vocalist,garbage,vocalist").apply();check(MusicCredits.order(c).equals(Arrays.asList("vocalist","composer","arranger")),"invalid credit ordering normalizes without losing roles");checks++;
            prefs.edit().remove("credit_roles_order").remove("credit_roles_visible").apply();
            JSONObject legacy=new JSONObject().put("uri","content://music-test/legacy").put("title","Legacy").put("artist","Singer").put("rating",0).put("bpm",0).put("lrcPath","/retained/lyrics.lrc").put("coverPath","/retained/cover.jpg");
            prefs.edit().putString("queue",new JSONArray().put(legacy).toString()).commit();
            ArrayList<MusicLibrary.Track> queue=MusicLibrary.load(c);check(queue.size()==1,"legacy object queue migration");checks++;
            MusicLibrary.Track t=queue.get(0);check(t.rating==-1,"legacy zero is unset");checks++;check(t.bpm==0,"BPM unset");checks++;check("Singer".equals(t.vocalist),"legacy artist migration");checks++;
            MusicLibrary.save(c,queue);check(MusicLibrary.catalogue(c).size()==1,"catalogue persisted on migration");checks++;
            MusicCollections.toggleFavorite(c,t.uri);check(MusicCollections.createPlaylist(c,"Keep me"),"create playlist");checks++;MusicCollections.add(c,"Keep me",Collections.singleton(t.uri));
            MusicLibrary.save(c,new ArrayList<MusicLibrary.Track>());
            check(MusicLibrary.load(c).isEmpty(),"queue cleared");checks++;check(MusicLibrary.catalogue(c).size()==1,"queue clear keeps catalogue");checks++;
            t=MusicLibrary.find(c,t.uri);check(t!=null&&"/retained/lyrics.lrc".equals(t.lrcPath)&&"/retained/cover.jpg".equals(t.coverPath),"queue clear keeps attached files");checks++;
            check(MusicCollections.isFavorite(c,t.uri),"queue clear keeps like");checks++;check(MusicCollections.playlist(c,"Keep me").contains(t.uri),"queue clear keeps playlist");checks++;
            t=t.withMeta("Edited","Vocalist","Composer","Arranger","Notes","Tag",120,0,false,t.lrcPath,t.coverPath);MusicLibrary.update(c,t);
            check(MusicLibrary.find(c,t.uri).rating==0,"explicit zero rating retained");checks++;
            MusicLibrary.addToQueue(c,Collections.singleton(t));check(MusicLibrary.load(c).get(0).rating==0,"zero rating survives queue persistence");checks++;
            MusicLibrary.discover(c,Collections.singleton(new MusicLibrary.Track(t.uri,"Scanned filename")));
            check("Edited".equals(MusicLibrary.find(c,t.uri).title),"folder rescan keeps edited metadata");checks++;
            MusicLibrary.removeFromQueue(c,t.uri);check(MusicLibrary.load(c).isEmpty()&&MusicLibrary.find(c,t.uri)!=null,"remove queue keeps catalogue");checks++;
            check(MusicCollections.renamePlaylist(c,"Keep me","Renamed"),"playlist rename");checks++;check(MusicCollections.playlist(c,"Renamed").contains(t.uri),"rename preserves members");checks++;
            MusicCollections.deletePlaylist(c,"Renamed");check(MusicLibrary.find(c,t.uri)!=null&&MusicCollections.isFavorite(c,t.uri),"playlist delete keeps catalogue and like");checks++;
            String document="content://com.android.externalstorage.documents/document/primary%3AMusic%2Fsame.mp3",tree="content://com.android.externalstorage.documents/tree/primary%3AMusic/document/primary%3AMusic%2Fsame.mp3";
            MusicLibrary.Track original=new MusicLibrary.Track(document,"Original filename");MusicLibrary.discover(c,Collections.singleton(original));MusicLibrary.Track edited=original.withMeta("Edited document","Singer","Composer","Arranger","","",130,7,false,"/same.lrc","/same.cover");MusicLibrary.update(c,edited);
            MusicLibrary.discover(c,Collections.singleton(new MusicLibrary.Track(tree,"Tree filename")));check(MusicLibrary.catalogue(c).size()==2,"SAF aliases are one catalogue song");checks++;
            check("Edited document".equals(MusicLibrary.find(c,tree).title)&&document.equals(MusicLibrary.find(c,tree).uri),"SAF rescan preserves metadata and stable URI");checks++;
            MusicLibrary.addToQueue(c,Arrays.asList(edited,new MusicLibrary.Track(tree,"Duplicate")));check(MusicLibrary.load(c).size()==1,"SAF aliases are one queue song");checks++;
            MusicLibrary.update(c,new MusicLibrary.Track(tree,"Changed through tree URI"));check("Changed through tree URI".equals(MusicLibrary.find(c,document).title)&&document.equals(MusicLibrary.find(c,tree).uri),"SAF alias updates canonical record");checks++;
            MusicLibrary.removeFromQueue(c,tree);check(MusicLibrary.load(c).isEmpty()&&MusicLibrary.find(c,document)!=null,"SAF alias queue removal retains catalogue");checks++;
            check(!MusicCollections.createPlaylist(c,"@favorites"),"custom playlist cannot shadow built-in favorites identity");checks++;
            check(!MusicCollections.createPlaylist(c,MusicCollections.ALL),"custom playlist cannot shadow built-in all-songs identity");checks++;
            for(String builtIn:Arrays.asList("全部歌曲","我喜欢的音乐","All songs","Liked music","ALL SONGS")){check(!MusicCollections.createPlaylist(c,builtIn),"custom playlist rejects built-in display name: "+builtIn);checks++;}
            MusicCollections.createPlaylist(c,"Rename guard");MusicCollections.add(c,"Rename guard",Collections.singleton(t.uri));check(!MusicCollections.renamePlaylist(c,"Rename guard","全部歌曲")&&!MusicCollections.renamePlaylist(c,"Rename guard","Liked music")&&MusicCollections.playlist(c,"Rename guard").contains(t.uri),"rename cannot shadow built-in display names or lose original membership");checks++;
            MusicCollections.deletePlaylist(c,"favorites");MusicCollections.deletePlaylist(c,MusicCollections.FAVORITES);MusicCollections.deletePlaylist(c,MusicCollections.ALL);check(MusicCollections.isFavorite(c,t.uri),"built-in playlist deletion cannot clear favorites");checks++;
            MusicLibrary.Track precise=new MusicLibrary.Track("content://music-test/precise","Same title","Singer","Composer","Arranger","","","","123.1234567890123456789",-1,false,123000,"");
            MusicLibrary.Track sameName=new MusicLibrary.Track("content://music-test/different-file","Same title");MusicLibrary.addToQueue(c,Arrays.asList(precise,sameName));
            check(MusicLibrary.find(c,precise.uri).bpmText.equals("123.1234567890123456789"),"BPM round trip preserves decimal precision beyond double");checks++;
            check(MusicLibrary.load(c).size()==2&&MusicLibrary.find(c,sameName.uri)!=null,"same display titles keep distinct file identities");checks++;
            MusicLibrary.updateBpm(c,precise.uri,"99.99999999999999999999");check(MusicSearch.key(MusicLibrary.find(c,precise.uri),MusicSearch.Category.BPM).equals("bpm:under100"),"decimal BPM grouping uses exact value rather than rounded compatibility integer");checks++;
            check(MusicSearch.matches(MusicLibrary.find(c,precise.uri),"99.99999999999999999999"),"decimal BPM is searchable without rounding");checks++;
            check(MusicLibrary.measuredBpm("123.5",false).equals("124")&&MusicLibrary.measuredBpm("123.45",true).equals("123.5"),"measured BPM uses half-up integer or one-decimal rounding");checks++;
            check(MusicLibrary.normalizeBpm(" 000.12500 ").equals("0.125")&&MusicLibrary.normalizeBpm("").isEmpty(),"canonical positive decimal and empty BPM");checks++;
            boolean invalidBpm=false;try{MusicLibrary.updateBpm(c,precise.uri,"NaN");}catch(IllegalArgumentException expected){invalidBpm=true;}check(invalidBpm&&MusicLibrary.find(c,precise.uri).bpmText.equals("99.99999999999999999999"),"invalid BPM leaves existing metadata intact");checks++;
            MusicCollections.toggleFavorite(c,precise.uri);MusicCollections.createPlaylist(c,"Deletion references");MusicCollections.add(c,"Deletion references",Arrays.asList(precise.uri,sameName.uri));MusicLibrary.markMetadataEdited(c,precise.uri);
            MusicLibrary.removeEverywhere(c,precise.uri);check(MusicLibrary.find(c,precise.uri)==null&&MusicLibrary.find(c,sameName.uri)!=null&&MusicLibrary.load(c).size()==1,"confirmed file deletion removes only its identity from catalogue and queue");checks++;
            check(!MusicCollections.isFavorite(c,precise.uri)&&!MusicCollections.playlist(c,"Deletion references").contains(precise.uri)&&MusicCollections.playlist(c,"Deletion references").contains(sameName.uri),"confirmed deletion cleans favorite and playlist references while retaining same-title song");checks++;
            MusicLibrary.removeFromQueue(c,Collections.singleton(sameName.uri));check(MusicLibrary.load(c).isEmpty()&&MusicLibrary.find(c,sameName.uri)!=null,"bulk queue removal keeps audio catalogue");checks++;
            return checks;
        }finally{
            SharedPreferences.Editor e=prefs.edit().clear();for(Map.Entry<String,?> row:before.entrySet()){Object v=row.getValue();if(v instanceof String)e.putString(row.getKey(),(String)v);else if(v instanceof Boolean)e.putBoolean(row.getKey(),(Boolean)v);else if(v instanceof Integer)e.putInt(row.getKey(),(Integer)v);else if(v instanceof Long)e.putLong(row.getKey(),(Long)v);else if(v instanceof Float)e.putFloat(row.getKey(),(Float)v);else if(v instanceof Set)e.putStringSet(row.getKey(),new HashSet<>((Set<String>)v));}e.commit();
        }
    }
    private static void check(boolean ok,String what){if(!ok)throw new AssertionError("Music library: "+what);}
}
