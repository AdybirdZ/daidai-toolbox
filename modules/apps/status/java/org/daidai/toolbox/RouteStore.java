package org.daidai.toolbox;

import android.content.*;
import android.graphics.*;
import android.location.Location;
import android.view.*;
import org.json.*;
import java.util.*;

public final class RouteStore {
    public static final String PREF="status_routes";
    public static long intervalMs(){return 1000L*Math.max(2,Math.min(60,RouteStoreHolder.context==null?5:RouteStoreHolder.context.getSharedPreferences("toolbox",0).getInt("route_interval",5)));}
    private static final class RouteStoreHolder {public static Context context;}
    private final SharedPreferences p;
    public RouteStore(Context c){p=c.getSharedPreferences(PREF,0);RouteStoreHolder.context=c.getApplicationContext();}
    public static final class Saved {public String id,name;public ArrayList<RoutePoint> points=new ArrayList<>();}
    public ArrayList<Saved> all(){ArrayList<Saved> out=new ArrayList<>();try{JSONArray rows=new JSONArray(p.getString("routes","[]"));for(int i=0;i<rows.length();i++){JSONObject r=rows.optJSONObject(i);if(r==null)continue;Saved s=new Saved();s.id=r.optString("id",UUID.randomUUID().toString());s.name=r.optString("name",UiText.get("gps_090"));JSONArray ps=r.optJSONArray("points");if(ps!=null)for(int n=0;n<ps.length();n++){JSONObject q=ps.optJSONObject(n);if(q!=null) s.points.add(new RoutePoint(q.optDouble("lon"),q.optDouble("lat"),q.optLong("time"),q.has("accuracy")?(float)q.optDouble("accuracy"):Float.NaN));}if(!s.points.isEmpty())out.add(s);}}catch(Exception ignored){}return out;}
    public void save(String name,List<RoutePoint> points){try{JSONArray rows=new JSONArray();for(Saved old:all())rows.put(encode(old.id,old.name,old.points));rows.put(encode(UUID.randomUUID().toString(),name,points));p.edit().putString("routes",rows.toString()).apply();}catch(Exception ignored){}}
    public void rename(String id,String name){try{JSONArray rows=new JSONArray();for(Saved old:all())rows.put(encode(old.id,old.id.equals(id)?name:old.name,old.points));p.edit().putString("routes",rows.toString()).apply();}catch(Exception ignored){}}
    public void move(String id,int delta){try{ArrayList<Saved> rows=all();int at=-1;for(int i=0;i<rows.size();i++)if(rows.get(i).id.equals(id)){at=i;break;}int to=at+delta;if(at<0||to<0||to>=rows.size())return;Saved s=rows.remove(at);rows.add(to,s);JSONArray out=new JSONArray();for(Saved old:rows)out.put(encode(old.id,old.name,old.points));p.edit().putString("routes",out.toString()).apply();}catch(Exception ignored){}}
    public void remove(String id){try{JSONArray rows=new JSONArray();for(Saved old:all())if(!old.id.equals(id))rows.put(encode(old.id,old.name,old.points));p.edit().putString("routes",rows.toString()).apply();}catch(Exception ignored){}}
    public JSONArray exportRows()throws JSONException{JSONArray rows=new JSONArray();for(Saved saved:all())rows.put(encode(saved.id,saved.name,saved.points));return rows;}
    public boolean importRows(JSONArray rows){return p.edit().putString("routes",rows.toString()).commit();}
    private JSONObject encode(String id,String name,List<RoutePoint> points)throws JSONException{JSONObject r=new JSONObject();r.put("id",id);r.put("name",name);JSONArray ps=new JSONArray();for(RoutePoint q:points){JSONObject x=new JSONObject();x.put("lon",q.lon);x.put("lat",q.lat);x.put("time",q.time);if(Float.isFinite(q.accuracy)&&q.accuracy>=0)x.put("accuracy",q.accuracy);ps.put(x);}r.put("points",ps);return r;}
}

