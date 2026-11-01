package org.simpleweather.app;

import java.time.*;
import java.time.format.DateTimeFormatter;

final class EarthquakeOptions {
    static float magnitude(float value){return Float.isFinite(value)?Math.round(Math.max(0,Math.min(7,value))*10)/10f:1f;}
    static int radius(String value){try{if(!value.trim().matches("[0-9]+"))return -1;int n=Integer.parseInt(value.trim());return n>=50&&n<=5000?n:-1;}catch(Exception e){return -1;}}
    static ZoneId zone(int option){return option==1?ZoneId.of("Asia/Shanghai"):option==2?ZoneOffset.UTC:ZoneId.systemDefault();}
    static String offset(ZoneId zone,long at){
        int total=zone.getRules().getOffset(Instant.ofEpochMilli(at)).getTotalSeconds(),absolute=Math.abs(total);
        String value="UTC"+(total<0?"-":"+")+absolute/3600;
        if(absolute%3600!=0)value+=String.format(java.util.Locale.ROOT,":%02d",absolute%3600/60);
        if(absolute%60!=0)value+=String.format(java.util.Locale.ROOT,":%02d",absolute%60);
        return value;
    }
    static String time(long at,ZoneId zone){return at<=0?UiText.get("earthquake_options_001"):DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(zone).format(Instant.ofEpochMilli(at));}
}
