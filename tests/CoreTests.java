package org.simpleweather.app;

import org.json.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.lang.reflect.*;

/** Small behavioral regression suite; runs on a JDK with JSON-java on the test classpath. */
public final class CoreTests {
    private static int checks;
    private static void check(boolean condition,String why) {
        checks++;if(!condition)throw new AssertionError(why);
    }
    private static JSONObject read(String path) throws Exception {
        return new JSONObject(new String(Files.readAllBytes(Paths.get(path)),StandardCharsets.UTF_8));
    }
    public static void main(String[] args) throws Exception {
        JSONObject raw=read(args[0]);
        WeatherData w=new WeatherData(raw,Instant.parse("2026-10-24T07:00:00Z").toEpochMilli());
        Instant beijingNow=Instant.parse("2026-10-24T07:00:00Z");
        check(w.todayIndex(beijingNow)==0,"Beijing today maps to the correct forecast date");
        List<Integer> hours=w.nextHours(beijingNow,24);
        check(hours.size()==24,"24 hourly entries are available");
        check(WeatherData.textAt(w.hourly,"time",hours.get(0)).equals("2026-10-24T15:00"),"Past hours are not shown as the next 24h");
        check(!w.currentIsOld(beijingNow),"Recent model time is fresh");
        check(w.currentIsOld(beijingNow.plusSeconds(7200)),"Old cached model values are visibly stale");
        check(w.fresh(beijingNow.toEpochMilli()+899_999),"Cache is reusable for 15 minutes");
        check(!w.fresh(beijingNow.toEpochMilli()+900_000),"Expired cache triggers refresh");
        check(!w.fresh(beijingNow.toEpochMilli()-1000),"Clock rollback cannot mark future cache as fresh");
        JSONObject missing=new JSONObject("{\"temperature_2m\":null,\"weather_code\":null,\"a\":[null,0]}");
        check(WeatherData.number(WeatherData.value(missing,"temperature_2m")).equals("—"),"Null temperature is not silently zero");
        check(WeatherData.number(WeatherData.at(missing,"a",0)).equals("—"),"Null forecast value is not silently zero");
        check(WeatherData.number(WeatherData.at(missing,"a",1)).equals("0"),"Real zero is preserved");
        check(WeatherData.code(missing,"weather_code",-1)==-1,"Missing condition does not become sunny");
        check(WeatherData.condition(-1).equals("天气未知"),"Unknown WMO code stays unknown");
        check(WeatherData.condition(99).equals("雷雨伴冰雹"),"Severe WMO conditions map correctly");
        check(WeatherData.condition(67).equals("冻雨"),"Freezing rain has its own label");
        check(WeatherData.wind(359).equals("北风"),"Wind wraps through zero degrees");
        check(WeatherData.wind(Double.NaN).isEmpty(),"Unknown wind direction is not invented");
        check(WeatherData.clock("").equals("—"),"Polar missing sunrise is supported");
        check(WeatherData.day("bad",LocalDate.now()).equals("—"),"Malformed dates do not crash display");
        JSONObject overseas=new JSONObject(raw.toString());overseas.put("timezone","America/Los_Angeles");
        overseas.getJSONObject("current").put("time","2026-01-01T16:15");
        overseas.getJSONObject("daily").put("time",new JSONArray("[\"2026-01-01\",\"2026-01-02\"]"));
        overseas.getJSONObject("hourly").put("time",new JSONArray("[\"bad\",\"2026-01-01T15:00\",\"2026-01-01T16:00\",\"2026-01-01T17:00\"]"));
        WeatherData la=new WeatherData(overseas,0);Instant utcTomorrow=Instant.parse("2026-01-02T00:30:00Z");
        check(la.todayIndex(utcTomorrow)==0,"Overseas today uses city date, not UTC or phone date");
        check(la.nextHours(utcTomorrow,24).equals(Arrays.asList(2,3)),"Overseas hours filter by city time and skip invalid timestamps");
        check(!la.currentIsOld(utcTomorrow),"Overseas model freshness uses city's timezone");
        check(la.todayIndex(utcTomorrow.plusSeconds(86400*5))==-1,"Expired days are not relabeled as today's forecast");
        boolean rejected=false;try{new WeatherData(new JSONObject("{\"error\":true}"),0);}catch(JSONException e){rejected=true;}
        check(rejected,"API error payload is rejected");
        rejected=false;try{new City(new JSONObject("{\"name\":\"bad\",\"latitude\":91,\"longitude\":0}"));}catch(JSONException e){rejected=true;}
        check(rejected,"Invalid coordinates cannot reach the weather endpoint");
        City cn=new City(new JSONObject("{\"name\":\"乌鲁木齐\",\"latitude\":43.8,\"longitude\":87.6,\"country\":\"中国\"}"));
        check(WeatherApi.forecastUrl(cn).contains("timezone=Asia%2FShanghai"),"Chinese cities consistently use Beijing time");
        City ny=new City(new JSONObject("{\"name\":\"New York\",\"latitude\":40.7,\"longitude\":-74,\"country\":\"美国\"}"));
        check(WeatherApi.forecastUrl(ny).contains("timezone=auto"),"Overseas cities use local automatic timezone");
        check(new City(ny.json()).key().equals(ny.key()),"Saved city identity survives serialization");
        WeatherApi api=new WeatherApi();Method reserve=WeatherApi.class.getDeclaredMethod("reserve");reserve.setAccessible(true);
        for(int i=0;i<20;i++)reserve.invoke(api);
        rejected=false;try{reserve.invoke(api);}catch(InvocationTargetException e){rejected=e.getCause() instanceof java.io.IOException;}
        check(rejected,"Request burst is limited before sending network calls");
        long retryNow=Instant.parse("2026-10-24T07:00:00Z").toEpochMilli();
        check(WeatherApi.retryUntil("120",retryNow)==retryNow+120_000,"Numeric Retry-After is honored");
        check(WeatherApi.retryUntil("Sat, 24 Oct 2026 07:03:00 GMT",retryNow)==retryNow+180_000,"HTTP-date Retry-After is honored");
        check(WeatherApi.retryUntil("172800",retryNow)==retryNow+172_800_000,"Long service cooldown is not bypassed");
        check(WeatherApi.retryUntil("invalid",retryNow)==retryNow+60_000,"Malformed Retry-After gets a safe one-minute pause");
        check(WeatherApi.retryUntil("99999999999999999",retryNow)==Long.MAX_VALUE,"Retry-After cannot overflow and bypass cooldown");
        JSONArray catalog=new JSONArray(new String(Files.readAllBytes(Paths.get(args[1])),StandardCharsets.UTF_8));
        Map<String,City> map=new HashMap<>();for(int i=0;i<catalog.length();i++){City c=new City(catalog.getJSONObject(i));map.put(c.label(),c);}
        check(map.size()>=40,"Bundled city selection works offline");
        check(map.get("长沙").admin.contains("湖南"),"Changsha preset is Hunan, not a namesake town");
        check(map.get("青岛").admin.contains("山东"),"Qingdao preset is Shandong");
        check(map.get("大连").admin.contains("辽宁"),"Dalian preset is Liaoning");
        check(map.get("长春").admin.contains("吉林"),"Changchun preset is Jilin");
        check(map.get("海口").latitude<21,"Haikou preset is Hainan");
        check(map.get("纽约").longitude<-70&&map.get("纽约").longitude>-80,"New York preset is New York City");
        System.out.println("PASS: "+checks+" behavioral checks");
    }
}
