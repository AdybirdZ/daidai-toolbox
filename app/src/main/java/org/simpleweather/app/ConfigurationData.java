package org.simpleweather.app;

import org.json.*;
import java.util.*;

/** Validated, versioned configuration interchange. No Android or persistence side effects. */
final class ConfigurationData {
    static final String FORMAT="daidai-toolbox-configuration";
    static final int MAX_BYTES=40*1024*1024, MAX_BACKGROUND=8*1024*1024;
    static final String[] GROUPS={"toolbox","weather","status_places","status_routes","timetable"};
    static final class Document {
        final Map<String,Map<String,Object>> groups=new LinkedHashMap<>();
        boolean backgroundSpecified;
        byte[] background;
        int places,routes,courses,regions;
    }
    private static final Set<String> BOOLEAN=new HashSet<>(Arrays.asList(
        "astro_align_sky","outline","quake_manual_location","quake_field_depth","quake_field_distance","quake_field_intensity","quake_field_source","quake_field_review",
        "location_continuous","status_true_north","status_battery_reverse_current","course_name_prefix","course_teacher_prefix","status_satellite_rotate","status_receive_pause","status_camera_background","quake_source_Wolfx"));
    private static final Set<String> INTEGER=new HashSet<>(Arrays.asList(
        "status_camera_iso","status_camera_exposure_ms","ui_button_color","ui_link_color","ui_body_color","ui_title_color","ui_choice_color","ui_note_color","theme_color","global_time_mode","astro_coordinates","astro_visibility_mode","outline_color","quake_days","quake_radius","quake_area","quake_sort","route_interval","radar_region","map_product","background_brightness","course_room_prefix",
        "text_primary_color","text_secondary_color","text_accent_color","button_primary_color","button_primary_text_color","button_secondary_color","button_secondary_text_color"));
    private static final Set<String> STRING=new HashSet<>(Arrays.asList(
        "astro_order","astro_selected","astro_orientation_reference","status_light_camera","location_provider","quake_order","status_order","status_sensor_order","status_light_source","status_battery_order","status_device_order","tab_order","weather_order","status_sat_company_order","status_sat_sort"));
    private static final Set<String> FLOAT=new HashSet<>(Arrays.asList("astro_magnitude_limit","quake_global_min","quake_regional_min","quake_ref_lat","quake_ref_lon"));
    static String type(String group,String key) {
        if(group.equals("toolbox")){
            if(BOOLEAN.contains(key)||key.matches("(?:astro_(?:star|planet|deep)|(?:astro|quake|status|tab|weather)_show|status_sensor_show|status_battery_show|status_device_show|status_constellation)_.{1,80}")||key.equals("quake_source_GEOFON")||key.equals("quake_source_EMSC")||key.equals("quake_source_USGS"))return "boolean";
            if(INTEGER.contains(key))return "int";
            if(STRING.contains(key))return "string";
            if(FLOAT.contains(key))return "float";
            if(key.equals("location_interval")||key.equals("quake_interval"))return "long";
        }else if(group.equals("weather")&&(key.equals("selected")||key.equals("saved")))return "string";
        else if(group.equals("status_places")&&(key.equals("places")||key.equals("selected")))return "string";
        else if(group.equals("status_routes")&&key.equals("routes"))return "string";
        else if(group.equals("timetable")&&key.equals("data"))return "string";
        return null; // Runtime state, permissions, acknowledgements and quotas are never restored.
    }
    static JSONObject encode(Map<String,Map<String,?>> source,byte[] background)throws Exception {
        JSONObject root=new JSONObject().put("format",FORMAT).put("version",2).put("coordinateSystem","WGS84");
        for(String group:GROUPS){JSONObject values=new JSONObject();Map<String,?> data=source.get(group);
            if(data!=null)for(Map.Entry<String,?> e:data.entrySet()){String kind=type(group,e.getKey());if(kind!=null)values.put(e.getKey(),new JSONObject().put("type",kind).put("value",e.getValue()));}
            root.put(group,values);
        }
        if(background!=null&&background.length>MAX_BACKGROUND)throw invalid();
        root.put("background",background==null?JSONObject.NULL:Base64.getEncoder().encodeToString(background));
        parse(root.toString());return root;
    }
    static Document parse(String text)throws Exception {
        if(text==null||text.length()>MAX_BYTES)throw invalid();
        checkNesting(text);
        JSONTokener tokener=new JSONTokener(text.startsWith("\uFEFF")?text.substring(1):text);Object value=tokener.nextValue();
        if(!(value instanceof JSONObject)||tokener.nextClean()!=0)throw invalid();JSONObject root=(JSONObject)value;
        if(!FORMAT.equals(root.optString("format")))throw invalid();long version=whole(root.get("version"));if(version!=1&&version!=2)throw invalid();
        if(version==2&&!"WGS84".equals(root.optString("coordinateSystem")))throw invalid();
        Document doc=new Document();
        for(String group:GROUPS){JSONObject values=root.getJSONObject(group);if(values.length()>1000)throw invalid();Map<String,Object> out=new LinkedHashMap<>();
            Iterator<String> keys=values.keys();while(keys.hasNext()){String key=keys.next();String kind=type(group,key);if(kind==null)continue;
                Object v=values.get(key);if(version==2){if(!(v instanceof JSONObject)||!kind.equals(((JSONObject)v).optString("type")))throw invalid();v=((JSONObject)v).get("value");}
                out.put(key,checked(group,key,kind,v));
            }
            doc.groups.put(group,out);
        }
        validateModels(doc);
        // Import never resumes background work or carries the Wolfx reading acknowledgement.
        Map<String,Object> toolbox=doc.groups.get("toolbox");toolbox.put("location_continuous",false);toolbox.put("status_camera_background",false);toolbox.put("quake_source_Wolfx",false);
        if(version==2){if(!root.has("background"))throw invalid();doc.backgroundSpecified=true;
            if(!root.isNull("background")){Object b=root.get("background");if(!(b instanceof String)||((String)b).length()>((MAX_BACKGROUND+2)/3)*4)throw invalid();try{doc.background=Base64.getDecoder().decode((String)b);}catch(IllegalArgumentException e){throw invalid();}if(doc.background.length==0||doc.background.length>MAX_BACKGROUND)throw invalid();}
        }
        return doc;
    }
    private static void checkNesting(String text)throws JSONException {
        boolean quoted=false,escaped=false;int depth=0;
        for(int i=0;i<text.length();i++){char c=text.charAt(i);if(quoted){if(escaped)escaped=false;else if(c=='\\')escaped=true;else if(c=='"')quoted=false;}else if(c=='"')quoted=true;else if(c=='{'||c=='['){if(++depth>16)throw invalid();}else if(c=='}'||c==']'){if(--depth<0)throw invalid();}}
        if(depth!=0||quoted)throw invalid();
    }
    private static Object checked(String group,String key,String kind,Object v)throws Exception {
        if(kind.equals("boolean")){if(!(v instanceof Boolean))throw invalid();return v;}
        if(kind.equals("string")){
            if(!(v instanceof String))throw invalid();String s=(String)v;if(s.length()>(group.equals("toolbox")?4096:StatusTransferData.MAX_BYTES))throw invalid();
            if(!group.equals("toolbox")&&!s.isEmpty()&&(!group.equals("status_places")||key.equals("places")))checkNesting(s);
            if(group.equals("toolbox")){
                if(key.endsWith("_order")&&!s.matches("[0-9,]*"))throw invalid();
                if(key.equals("location_provider")&&!Arrays.asList("gps","network").contains(s))throw invalid();
                if(key.equals("status_light_source")&&!Arrays.asList("sensor","camera").contains(s))throw invalid();
                if(key.equals("status_light_camera")&&!Arrays.asList("front","back").contains(s))throw invalid();
                if(key.equals("astro_orientation_reference")&&!Arrays.asList("camera","top").contains(s))throw invalid();
                if(key.equals("status_sat_sort")&&!Arrays.asList("company","cn0").contains(s))throw invalid();
            }
            return s;
        }
        if(!(v instanceof Number)||!Double.isFinite(((Number)v).doubleValue()))throw invalid();double n=((Number)v).doubleValue();
        if(kind.equals("float")){
            if(key.equals("quake_ref_lat"))range(n,-90,90);else if(key.equals("quake_ref_lon"))range(n,-180,180);else if(key.equals("astro_magnitude_limit"))range(n,-2,8);else range(n,0,7);
            return (float)n;
        }
        long x=whole(v);
        if(kind.equals("long")){if(key.equals("location_interval")){if(!Arrays.asList(1000L,5000L,10000L,30000L,60000L,180000L,300000L,600000L).contains(x))throw invalid();}else if(!Arrays.asList(0L,300000L,900000L,1800000L,3600000L).contains(x))throw invalid();return x;}
        range(n,Integer.MIN_VALUE,Integer.MAX_VALUE);
        if(key.endsWith("_color")) {if((x&0xff000000L)!=0xff000000L)throw invalid();}
        else if(key.equals("status_camera_iso"))range(n,1,204800);
        else if(key.equals("status_camera_exposure_ms"))range(n,1,1000);
        else if(key.equals("background_brightness"))range(n,0,100);
        else if(key.equals("route_interval"))range(n,2,60);
        else if(key.equals("quake_radius"))range(n,50,5000);
        else if(key.equals("quake_days")){if(!Arrays.asList(1L,3L,7L,30L).contains(x))throw invalid();}
        else if(key.equals("radar_region"))range(n,0,7);
        else if(key.equals("map_product"))range(n,0,5);
        else if(key.equals("astro_visibility_mode"))range(n,0,1);
        else range(n,0,2);
        return (int)x;
    }
    private static void validateModels(Document doc)throws Exception {
        Map<String,Object> weather=doc.groups.get("weather");
        if(weather.containsKey("selected")&&!((String)weather.get("selected")).isEmpty())new City(new JSONObject((String)weather.get("selected")));
        if(weather.containsKey("saved")){JSONArray a=array((String)weather.get("saved"));if(a.length()>20)throw invalid();for(int i=0;i<a.length();i++)new City(a.getJSONObject(i));doc.regions=a.length();}
        Map<String,Object> places=doc.groups.get("status_places");
        if(places.containsKey("places")){StatusTransferData.Document d=StatusTransferData.parse(StatusTransferData.encode("places",array((String)places.get("places"))),"places");places.put("places",d.rows.toString());doc.places=d.rows.length();}
        Map<String,Object> routes=doc.groups.get("status_routes");
        if(routes.containsKey("routes")){StatusTransferData.Document d=StatusTransferData.parse(StatusTransferData.encode("routes",array((String)routes.get("routes"))),"routes");routes.put("routes",d.rows.toString());doc.routes=d.rows.length();}
        Map<String,Object> timetable=doc.groups.get("timetable");
        if(timetable.containsKey("data")&&!((String)timetable.get("data")).isEmpty()){ScheduleModel model=ScheduleModel.parse((String)timetable.get("data"));timetable.put("data",model.json().toString());doc.courses=model.courses.size();}
    }
    private static JSONArray array(String s)throws JSONException{JSONTokener t=new JSONTokener(s);Object a=t.nextValue();if(!(a instanceof JSONArray)||t.nextClean()!=0)throw invalid();return (JSONArray)a;}
    private static long whole(Object v)throws JSONException{if(!(v instanceof Number))throw invalid();double n=((Number)v).doubleValue();if(!Double.isFinite(n)||n!=Math.rint(n)||n<Long.MIN_VALUE||n>=0x1.0p63)throw invalid();return ((Number)v).longValue();}
    private static void range(double n,double min,double max)throws JSONException{if(n<min||n>max)throw invalid();}
    private static JSONException invalid(){return new JSONException("Invalid configuration document");}
}
