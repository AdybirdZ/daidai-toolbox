package org.daidai.contracts;

import org.json.*;
import java.io.IOException;
import java.util.*;
import java.math.BigDecimal;

/** Data validation shared by the host backup reader and the final migration helper. */
public final class MusicDataContract {
    private MusicDataContract(){}
    public static void nesting(String s)throws IOException{boolean quoted=false,escape=false;int depth=0;for(int i=0;i<s.length();i++){char c=s.charAt(i);if(quoted){if(escape)escape=false;else if(c=='\\')escape=true;else if(c=='"')quoted=false;}else if(c=='"')quoted=true;else if(c=='['||c=='{'){if(++depth>40)throw new IOException("Data is nested too deeply");}else if(c==']'||c=='}'){if(--depth<0)throw new IOException("Invalid JSON");}}if(quoted||depth!=0)throw new IOException("Invalid JSON");}
    public static String expected(String key){
        if(Arrays.asList("catalogue","queue","collections","storage_tree_uri","mode","credit_roles_order").contains(key))return "string";
        if(Arrays.asList("metadata_manually_edited","credit_roles_visible").contains(key))return "set";
        if(Arrays.asList("cover_alpha","speed").contains(key))return "float";
        if(Arrays.asList("cover_show","bpm_keep_decimal","spectrum_log","pitch_follow_speed","analysis_show","sleep_timer_finish_track").contains(key))return "boolean";
        if(Arrays.asList("max_volume_percent","volume_percent","spectrum_peaks","spectrum_history_ms","analysis_display_mode","lyrics_font_sp","sleep_timer_minutes").contains(key))return "int";return null;
    }
    public static void validate(Map<String,Object> values)throws Exception{
        for(Map.Entry<String,Object> e:values.entrySet()){String key=e.getKey(),type=expected(key);Object v=e.getValue();if(type==null)continue;boolean valid=type.equals("string")?v instanceof String:type.equals("int")?v instanceof Integer:type.equals("float")?v instanceof Float:type.equals("boolean")?v instanceof Boolean:v instanceof Set;if(!valid)throw new IOException("Wrong setting type: "+key);}
        range(values,"max_volume_percent",0,100);range(values,"volume_percent",0,100);range(values,"spectrum_peaks",0,10);range(values,"spectrum_history_ms",500,5000);range(values,"analysis_display_mode",0,2);range(values,"lyrics_font_sp",14,36);range(values,"sleep_timer_minutes",1,120);range(values,"speed",.5,2);range(values,"cover_alpha",0,1);
        if(values.containsKey("mode")&&!Arrays.asList("ALL","ONE","SHUFFLE").contains(values.get("mode")))throw new IOException("Invalid playback mode");
        if(values.containsKey("credit_roles_order")){String order=(String)values.get("credit_roles_order");Set<String> seen=new HashSet<>();for(String role:order.split(","))if(!Arrays.asList("composer","arranger","vocalist").contains(role)||!seen.add(role))throw new IOException("Invalid credit order");}
        if(values.containsKey("credit_roles_visible"))for(Object role:(Set<?>)values.get("credit_roles_visible"))if(!Arrays.asList("composer","arranger","vocalist").contains(role))throw new IOException("Invalid visible credits");
        JSONArray catalogue=array(values,"catalogue"),queue=array(values,"queue");Set<String> ids=new HashSet<>();for(int i=0;i<catalogue.length();i++){JSONObject t=catalogue.getJSONObject(i);track(t);if(!ids.add(t.getString("uri")))throw new IOException("Duplicate song");}
        for(int i=0;i<queue.length();i++){Object item=queue.get(i);if(item instanceof JSONObject)track((JSONObject)item);else if(!(item instanceof String)||((String)item).length()>16384)throw new IOException("Invalid playback queue");}
        String collections=(String)values.getOrDefault("collections","{}");nesting(collections);JSONObject lists=new JSONObject(collections);if(lists.length()>20000)throw new IOException("Too many playlists");Iterator<String> names=lists.keys();while(names.hasNext()){String name=names.next();if(name.length()>10000)throw new IOException();JSONArray a=lists.getJSONArray(name);if(a.length()>20000)throw new IOException();for(int i=0;i<a.length();i++)if(!(a.get(i) instanceof String)||a.getString(i).length()>16384)throw new IOException("Invalid playlist");}
    }
    private static JSONArray array(Map<String,Object> values,String key)throws Exception{String s=(String)values.getOrDefault(key,"[]");nesting(s);JSONArray a=new JSONArray(s);if(a.length()>20000)throw new IOException("Too many songs");return a;}
    private static void range(Map<String,Object> values,String key,double min,double max)throws IOException{if(!values.containsKey(key))return;double n=((Number)values.get(key)).doubleValue();if(!Double.isFinite(n)||n<min||n>max)throw new IOException("Setting out of range: "+key);}
    private static void track(JSONObject t)throws Exception{
        Object uri=t.get("uri");if(!(uri instanceof String)||((String)uri).isEmpty()||((String)uri).length()>16384)throw new IOException("Invalid song URI");
        for(String k:new String[]{"title","vocalist","artist","composer","arranger","notes","tags","lrcPath","coverPath"})if(t.has(k)&&(!(t.get(k) instanceof String)||t.getString(k).length()>1000000))throw new IOException("Invalid song text");
        if(t.has("rating")){Object raw=t.get("rating");if(!(raw instanceof Number)||((Number)raw).doubleValue()!=((Number)raw).intValue()||((Number)raw).intValue() < -1||((Number)raw).intValue()>10)throw new IOException("Invalid rating");}
        for(String k:new String[]{"instrumental","ratingSet"})if(t.has(k)&&!(t.get(k) instanceof Boolean))throw new IOException("Invalid song flag");
        if(t.has("duration")&&(!(t.get("duration") instanceof Number)||t.getDouble("duration")<0||t.getDouble("duration")>Long.MAX_VALUE))throw new IOException("Invalid duration");
        String bpm=t.optString("bpm","");if(bpm.length()>1000)throw new IOException("Invalid BPM");if(!bpm.isEmpty()){if(!bpm.matches("[0-9]+(?:\\.[0-9]+)?")||new BigDecimal(bpm).signum()<=0)throw new IOException("Invalid BPM");}
    }
}
