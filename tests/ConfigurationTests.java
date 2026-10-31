package org.simpleweather.app;

import org.json.*;
import java.util.*;

public final class ConfigurationTests {
    private static int checks;
    private static void check(boolean v,String why){checks++;if(!v)throw new AssertionError(why);}
    private static JSONObject fixture()throws Exception{
        JSONObject j=new JSONObject().put("format",ConfigurationData.FORMAT).put("version",1);
        for(String g:ConfigurationData.GROUPS)j.put(g,new JSONObject());return j;
    }
    private static void bad(JSONObject j,String why)throws Exception{boolean rejected=false;try{ConfigurationData.parse(j.toString());}catch(Exception e){rejected=true;}check(rejected,why);}
    public static void main(String[] args)throws Exception{
        Map<String,Map<String,?>> source=new HashMap<>();Map<String,Object> toolbox=new HashMap<>();
        for(String role:new String[]{"ui_button_color","ui_link_color","ui_body_color","ui_title_color","ui_choice_color","ui_note_color"})toolbox.put(role,0xff7852a8);toolbox.put("theme_color",0xffB83232);toolbox.put("outline",true);toolbox.put("outline_color",0xff246bce);toolbox.put("location_interval",600000L);toolbox.put("quake_interval",0L);toolbox.put("quake_ref_lat",-33.5f);toolbox.put("quake_ref_lon",151.2f);toolbox.put("weather_order","1,2,0,3,4,5");
        toolbox.put("status_camera_outdoor_iso",50);toolbox.put("status_camera_outdoor_exposure_ms",5);toolbox.put("status_camera_paused",true);toolbox.put("status_camera_iso",300);toolbox.put("status_camera_exposure_ms",50);toolbox.put("quake_source_USGS",false);toolbox.put("quake_wolfx_ack_112",true);toolbox.put("quake_source_Wolfx",true);toolbox.put("location_continuous",true);toolbox.put("status_camera_background",true);source.put("toolbox",toolbox);
        source.put("timetable",Collections.singletonMap("data",new ScheduleModel().json().toString()));
        source.put("status_places",Collections.singletonMap("places","[{\"id\":\"point1\",\"name\":\"地点\",\"lon\":116.4,\"lat\":39.9,\"created\":1,\"color\":-16776961,\"visible\":false}]"));
        source.put("status_routes",Collections.singletonMap("routes","[{\"id\":\"route1\",\"name\":\"路线\",\"points\":[{\"lon\":116.4,\"lat\":39.9,\"time\":1},{\"lon\":116.5,\"lat\":39.8,\"time\":2}]}]"));
        source.put("weather",Collections.singletonMap("saved","[{\"name\":\"苏州市\",\"country\":\"中国\",\"admin1\":\"江苏省\",\"longitude\":120.6,\"latitude\":31.3}]"));
        byte[] background={1,2,3,4};JSONObject encoded=ConfigurationData.encode(source,background);ConfigurationData.Document d=ConfigurationData.parse(encoded.toString());Map<String,Object> p=d.groups.get("toolbox");
        check(p.get("ui_button_color").equals(0xff7852a8)&&p.get("ui_link_color").equals(0xff7852a8)&&p.get("ui_body_color").equals(0xff7852a8)&&p.get("ui_title_color").equals(0xff7852a8)&&p.get("ui_choice_color").equals(0xff7852a8)&&p.get("ui_note_color").equals(0xff7852a8),"Six independent color roles survive configuration transfer");
        check(p.get("status_camera_iso").equals(300)&&p.get("status_camera_exposure_ms").equals(50),"Camera parameters survive configuration round trip");
        check(p.get("theme_color").equals(0xffB83232),"Unified theme color survives configuration transfer");
        check(p.get("outline_color") instanceof Integer,"Color keeps SharedPreferences int type");check(p.get("location_interval") instanceof Long,"Small interval remains long after JSON round trip");check(p.get("quake_ref_lat") instanceof Float,"Coordinates keep float preference type");
        check(p.get("outline").equals(true)&&p.get("quake_interval").equals(0L),"Flags and disabled auto refresh survive");
        check(p.get("status_camera_outdoor_iso").equals(50)&&p.get("status_camera_outdoor_exposure_ms").equals(5),"Both outdoor parameters round trip");
        check(!p.containsKey("status_camera_paused"),"Sampling pause is runtime-only");
        check(!p.containsKey("quake_wolfx_ack_112")&&Boolean.FALSE.equals(p.get("quake_source_Wolfx")),"Acknowledgement cannot be transferred");check(Boolean.FALSE.equals(p.get("location_continuous"))&&Boolean.FALSE.equals(p.get("status_camera_background")),"No automatic background work on import");
        check(Boolean.FALSE.equals(p.get("quake_source_USGS")),"All current earthquake sources transfer");check(Arrays.equals(background,d.background)&&d.backgroundSpecified,"Background bytes round trip");
        check(d.places==1&&d.routes==1&&d.regions==1&&d.courses==0,"Summary reflects actual stored models");
        check(!new JSONArray((String)d.groups.get("status_places").get("places")).getJSONObject(0).getBoolean("visible"),"Place color and visibility preserved");
        check(ConfigurationData.parse(fixture().toString()).backgroundSpecified==false,"Legacy exports preserve existing background");
        check(ConfigurationData.parse(ConfigurationData.encode(source,null).toString()).backgroundSpecified,"Explicit empty background restores default");
        JSONObject j=fixture();j.getJSONObject("toolbox").put("seen_version",999).put("location_notice_asked",true).put("status_camera_lux_sample",23);j.getJSONObject("weather").put("quota_count_0",0).put("network_blocked_until",0);
        d=ConfigurationData.parse(j.toString());check(!d.groups.get("toolbox").containsKey("seen_version")&&d.groups.get("weather").isEmpty(),"Runtime state and network limits excluded");
        bad(fixture().put("version",3),"Reject future version");bad(fixture().put("version",4294967297L),"Do not truncate overflowing version");bad(fixture().put("version",1.1),"Reject fractional version");bad(fixture().put("format","other"),"Reject wrong format");bad(fixture().put("weather",new JSONArray()),"Require expected preference groups");
        Object[][] invalid={{"status_camera_outdoor_iso",0},{"status_camera_outdoor_iso",204801},{"status_camera_outdoor_iso",1.5},{"status_camera_outdoor_exposure_ms",0},{"status_camera_outdoor_exposure_ms",1001},{"status_camera_iso",0},{"status_camera_iso",204801},{"status_camera_iso",300.5},{"status_camera_exposure_ms",0},{"status_camera_exposure_ms",1001},{"outline","true"},{"location_interval",5001},{"quake_ref_lon",181},{"quake_ref_lat",-91},{"route_interval",1},{"astro_magnitude_limit",9},{"quake_global_min",8},{"outline_color",123},{"course_room_prefix",4},{"quake_days",2},{"weather_order","1,2,x"},{"location_provider","fused"}};
        for(Object[] row:invalid){j=fixture();j.getJSONObject("toolbox").put((String)row[0],row[1]);bad(j,"Reject invalid setting "+row[0]);}
        j=fixture();j.getJSONObject("weather").put("selected","{\"name\":\"bad\",\"latitude\":91,\"longitude\":0}");bad(j,"Reject invalid selected coordinates");
        j=fixture();j.getJSONObject("status_routes").put("routes","[{\"id\":\"r\",\"name\":\"r\",\"points\":[{\"lon\":0,\"lat\":0,\"time\":2},{\"lon\":0,\"lat\":0,\"time\":1}]}]");bad(j,"Validate route time order before write");
        j=fixture();j.getJSONObject("status_places").put("places","[{\"id\":\"p\",\"name\":\"p\",\"lon\":0,\"lat\":91,\"created\":1}]");bad(j,"Validate all place records before write");
        j=fixture();j.getJSONObject("timetable").put("data","{\"weeks\":0}");bad(j,"Validate timetable before write");
        j=ConfigurationData.encode(source,null);j.getJSONObject("toolbox").getJSONObject("outline").put("type","string");bad(j,"Reject mismatched version 2 type");
        j=ConfigurationData.encode(source,null).put("background","not base64!!!");bad(j,"Reject invalid background encoding");
        String deep="0";for(int i=0;i<100;i++)deep="["+deep+"]";j=fixture();j.getJSONObject("status_places").put("places",deep);bad(j,"Reject excessive nesting even inside embedded JSON strings");
        boolean rejected=false;try{ConfigurationData.parse(fixture().toString()+"{}");}catch(Exception e){rejected=true;}check(rejected,"Reject trailing JSON content");
        System.out.println("Configuration tests: "+checks+" checks passed");
    }
}
