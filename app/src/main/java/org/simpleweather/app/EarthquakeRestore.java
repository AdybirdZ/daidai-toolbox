package org.simpleweather.app;

import java.util.*;
import org.json.*;

/** Small task-state metadata; catalogue bytes remain in the existing independent cache. */
final class EarthquakeRestore {
    static String snapshot(Map<String,EarthquakeCache.Entry> data,Set<String> cached,Map<String,String> failures){
        JSONObject root=new JSONObject();
        for(String source:EarthquakeData.SOURCES)try{
            EarthquakeCache.Entry entry=data.get(source);
            JSONObject row=new JSONObject().put("fetched",entry==null?0:entry.fetched).put("cached",cached.contains(source));
            if(failures.containsKey(source))row.put("failure",failures.get(source));
            root.put(source,row);
        }catch(JSONException ignored){}
        return root.toString();
    }
    static void apply(String saved,Map<String,EarthquakeCache.Entry> loaded,Set<String> cached,Map<String,String> failures){
        if(saved==null||saved.length()>16384)return;
        try{
            JSONObject root=new JSONObject(saved);
            for(String source:EarthquakeData.SOURCES){
                JSONObject row=root.optJSONObject(source);if(row==null)continue;
                EarthquakeCache.Entry entry=loaded.get(source);long stamp=row.optLong("fetched",-1);
                // Never attach old labels to replaced, cleared or missing catalogue data.
                if(stamp!=(entry==null?0:entry.fetched))continue;
                if(entry!=null&&!row.optBoolean("cached",true))cached.remove(source);
                String failure=row.optString("failure","");if(!failure.isEmpty())failures.put(source,failure);
            }
        }catch(JSONException ignored){}
    }
}
