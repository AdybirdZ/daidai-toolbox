package org.simpleweather.app;

import org.json.*;
import java.util.*;

/** Versioned local interchange. Validation completes before any stored record changes. */
final class StatusTransferData {
    static final String FORMAT="daidai-toolbox-status";
    static final int MAX_BYTES=20*1024*1024;
    static final class Document {
        final String kind;final JSONArray rows;
        Document(String kind,JSONArray rows){this.kind=kind;this.rows=rows;}
    }
    static final class Merge {
        final JSONArray rows;final int added,skipped;
        Merge(JSONArray rows,int added,int skipped){this.rows=rows;this.added=added;this.skipped=skipped;}
    }
    static String encode(String kind,JSONArray rows)throws JSONException {
        JSONObject root=new JSONObject().put("format",FORMAT).put("version",1).put("kind",kind).put("coordinateSystem","WGS84").put("records",rows);
        String text=root.toString();parse(text,kind);return text;
    }
    static Document parse(String text,String expected)throws JSONException {
        if(text==null||text.length()>MAX_BYTES)throw invalid();
        // Reject excessive nesting before invoking the platform JSON parser.
        boolean quoted=false,escaped=false;int depth=0;
        for(int i=0;i<text.length();i++){char c=text.charAt(i);if(quoted){if(escaped)escaped=false;else if(c=='\\')escaped=true;else if(c=='"')quoted=false;}else if(c=='"')quoted=true;else if(c=='{'||c=='['){if(++depth>16)throw invalid();}else if(c=='}'||c==']')depth--;}
        JSONTokener tokener=new JSONTokener(text.startsWith("\uFEFF")?text.substring(1):text);
        Object value=tokener.nextValue();if(!(value instanceof JSONObject)||tokener.nextClean()!=0)throw invalid();JSONObject root=(JSONObject)value;
        if(!FORMAT.equals(root.optString("format"))||integer(root,"version")!=1||!expected.equals(root.optString("kind"))||!(expected.equals("routes")||expected.equals("places"))||!"WGS84".equals(root.optString("coordinateSystem")))throw invalid();
        JSONArray source=root.getJSONArray("records"),rows=new JSONArray();if(source.length()>100000)throw invalid();int pointCount=0;
        Set<String> ids=new HashSet<>();
        for(int i=0;i<source.length();i++){
            JSONObject row=source.getJSONObject(i);String id=string(row,"id",120),name=string(row,"name",expected.equals("places")?60:1000);if(!ids.add(id))throw invalid();
            if(expected.equals("places")){
                double lon=number(row,"lon"),lat=number(row,"lat");if(!StatusMath.coordinate(lon,lat))throw invalid();
                long created=integer(row,"created");if(created<0)throw invalid();
                int color=StatusPlace.DEFAULT_COLOR;if(row.has("color")){long n=integer(row,"color");if(n<Integer.MIN_VALUE||n>Integer.MAX_VALUE)throw invalid();color=(int)n;}
                boolean visible=true;if(row.has("visible")){if(!(row.get("visible") instanceof Boolean))throw invalid();visible=row.getBoolean("visible");}
                rows.put(new JSONArray(StatusPlace.encode(Collections.singletonList(new StatusPlace(id,name,lon,lat,created,color,visible)))).getJSONObject(0));
            }else{
                JSONArray points=row.getJSONArray("points"),normalized=new JSONArray();pointCount+=points.length();if(points.length()==0||pointCount>200000)throw invalid();long previous=-1;
                for(int n=0;n<points.length();n++){
                    JSONObject point=points.getJSONObject(n);double lon=number(point,"lon"),lat=number(point,"lat");long time=integer(point,"time");if(!StatusMath.coordinate(lon,lat)||time<=0||time<previous)throw invalid();previous=time;
                    JSONObject q=new JSONObject().put("lon",lon).put("lat",lat).put("time",time);
                    if(point.has("accuracy")&&!point.isNull("accuracy")){double accuracy=number(point,"accuracy");if(accuracy<0||accuracy>Float.MAX_VALUE)throw invalid();q.put("accuracy",(double)(float)accuracy);}
                    normalized.put(q);
                }
                rows.put(new JSONObject().put("id",id).put("name",name).put("points",normalized));
            }
        }
        return new Document(expected,rows);
    }
    static Merge merge(JSONArray existing,Document incoming)throws JSONException {
        JSONArray rows=new JSONArray(existing.toString());Set<String> ids=new HashSet<>(),contents=new HashSet<>();
        for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);ids.add(row.getString("id"));contents.add(content(row,incoming.kind));}
        int added=0,skipped=0;
        for(int i=0;i<incoming.rows.length();i++){
            JSONObject row=new JSONObject(incoming.rows.getJSONObject(i).toString());String key=content(row,incoming.kind);
            if(!contents.add(key)){skipped++;continue;}
            String id=row.getString("id");if(ids.contains(id)){do{id=UUID.randomUUID().toString();}while(ids.contains(id));row.put("id",id);}ids.add(id);rows.put(row);added++;
        }
        return new Merge(rows,added,skipped);
    }
    private static String content(JSONObject row,String kind)throws JSONException {
        // Build in a fixed order so object key order in an imported file is irrelevant.
        JSONArray key=new JSONArray().put(row.getString("name"));
        if(kind.equals("places"))key.put(row.getDouble("lon")).put(row.getDouble("lat")).put(row.optLong("created",0)).put(row.optInt("color",StatusPlace.DEFAULT_COLOR)|0xFF000000).put(row.optBoolean("visible",true));
        else{JSONArray points=new JSONArray(),source=row.getJSONArray("points");for(int i=0;i<source.length();i++){JSONObject q=source.getJSONObject(i);points.put(new JSONArray().put(q.getDouble("lon")).put(q.getDouble("lat")).put(q.getLong("time")).put(q.has("accuracy")&&!q.isNull("accuracy")?q.getDouble("accuracy"):JSONObject.NULL));}key.put(points);}
        return key.toString();
    }
    private static String string(JSONObject row,String key,int max)throws JSONException {Object v=row.get(key);if(!(v instanceof String)||((String)v).trim().isEmpty()||((String)v).length()>max)throw invalid();return ((String)v).trim();}
    private static double number(JSONObject row,String key)throws JSONException {Object v=row.get(key);if(!(v instanceof Number))throw invalid();double n=((Number)v).doubleValue();if(!Double.isFinite(n))throw invalid();return n;}
    private static long integer(JSONObject row,String key)throws JSONException {double n=number(row,key);if(n!=Math.rint(n)||n<Long.MIN_VALUE||n>=0x1.0p63)throw invalid();return ((Number)row.get(key)).longValue();}
    private static JSONException invalid(){return new JSONException(UiText.get("status_transfer_data_001"));}
}
