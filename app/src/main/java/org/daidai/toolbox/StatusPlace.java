package org.daidai.toolbox;

import java.util.*;
import org.json.*;

/** User-entered waypoints are separate from settings and cache. */
public final class StatusPlace {
    public static final int DEFAULT_COLOR=0xFF246BCE;
    public final String id,name;public final double longitude,latitude;public final long created;
    public final int color;public final boolean visible;
    public StatusPlace(String id,String name,double lon,double lat,long created){
        this(id,name,lon,lat,created,DEFAULT_COLOR,true);
    }
    public StatusPlace(String id,String name,double lon,double lat,long created,int color,boolean visible){
        if(id==null||id.isEmpty()||name==null||name.trim().isEmpty()||name.trim().length()>60||!StatusMath.coordinate(lon,lat))throw new IllegalArgumentException(UiText.get("status_place_001"));
        this.id=id;this.name=name.trim();longitude=lon;latitude=lat;this.created=created;
        this.color=validColor(color)?(color|0xFF000000):DEFAULT_COLOR;this.visible=visible;
    }
    private static boolean validColor(int c){int v=c|0xFF000000;return v==DEFAULT_COLOR||v==0xFFB83636||v==0xFF287C60||v==0xFFE58323||v==0xFF7852A8||v==0xFF167D9A||v==0xFFB3407D||v==0xFF333333;}
    public StatusPlace withVisibility(boolean shown){return new StatusPlace(id,name,longitude,latitude,created,color,shown);}
    public static List<StatusPlace> decode(String raw){
        List<StatusPlace> result=new ArrayList<>();if(raw==null||raw.length()>20_000_000)return result;
        try{JSONArray rows=new JSONArray(raw);Set<String> ids=new HashSet<>();for(int i=0;i<rows.length();i++)try{JSONObject r=rows.getJSONObject(i);StatusPlace p=new StatusPlace(r.getString("id"),r.getString("name"),r.getDouble("lon"),r.getDouble("lat"),r.optLong("created",0),r.optInt("color",DEFAULT_COLOR),r.optBoolean("visible",true));if(ids.add(p.id))result.add(p);}catch(Exception ignored){}}catch(JSONException ignored){}return result;
    }
    public static String encode(List<StatusPlace> places){JSONArray rows=new JSONArray();for(StatusPlace p:places)try{rows.put(new JSONObject().put("id",p.id).put("name",p.name).put("lon",p.longitude).put("lat",p.latitude).put("created",p.created).put("color",p.color).put("visible",p.visible));}catch(JSONException ex){throw new IllegalArgumentException(ex);}return rows.toString();}
}
