package org.simpleweather.app;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Recent catalogues only. Wolfx upstream public-display applicability remains unconfirmed. */
final class EarthquakeApi {
    interface Limits {long until(String source);void until(String source,long time);}
    static final class Result {final String raw;final List<EarthquakeData.Report> reports;Result(String s,List<EarthquakeData.Report> r){raw=s;reports=r;}}
    static String url(String source,long since,double minimum){
        requireSource(source);
        if(source.equals("Wolfx"))return "https://api.wolfx.jp/cenc_eqlist.json";
        if(source.equals("USGS"))return "https://earthquake.usgs.gov/fdsnws/event/1/query?format=geojson&contributor=us&starttime="+DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss").withZone(ZoneOffset.UTC).format(Instant.ofEpochMilli(since))+"&minmagnitude="+String.format(Locale.ROOT,"%.1f",minimum)+"&orderby=time&limit="+EarthquakeData.LIMIT;
        String start=DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss").withZone(ZoneOffset.UTC).format(Instant.ofEpochMilli(since));
        String base=source.equals("GEOFON")?"https://geofon.gfz.de/fdsnws/event/1/query?format=xml":"https://www.seismicportal.eu/fdsnws/event/1/query?format=json";
        return base+"&starttime="+start+"&minmagnitude="+String.format(Locale.ROOT,"%.1f",minimum)+"&orderby=time&limit="+EarthquakeData.LIMIT;
    }
    static Result fetch(String source,long since,double minimum,Limits limits)throws Exception{
        if(limits.until(source)>System.currentTimeMillis())throw new IOException("请求冷却中，请稍后重试");
        limits.until(source,System.currentTimeMillis()+60_000);
        HttpURLConnection c=(HttpURLConnection)new URL(url(source,since,minimum)).openConnection();
        c.setConnectTimeout(18_000);c.setReadTimeout(22_000);c.setInstanceFollowRedirects(false);c.setRequestProperty("Accept",source.equals("GEOFON")?"application/xml":"application/json");c.setRequestProperty("User-Agent","DaidaiToolbox/1.1.4 (recent earthquake catalogue)");
        try{
            int code=c.getResponseCode();
            if(code==429||code==503){limits.until(source,retryAfter(c.getHeaderField("Retry-After"),System.currentTimeMillis()));throw new IOException("来源限流或维护中，已暂停请求");}
            if(code==204){return new Result("",new ArrayList<>());}
            if(code!=200)throw new IOException("来源暂不可用（HTTP "+code+"）");
            ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];
            try(InputStream in=c.getInputStream()){int n;while((n=in.read(buffer))!=-1){if(out.size()+n>12*1024*1024)throw new IOException("返回数据过大，请缩小时间范围或提高最低震级");out.write(buffer,0,n);}}
            String raw=new String(out.toByteArray(),StandardCharsets.UTF_8);return new Result(raw,parse(source,raw));
        }finally{c.disconnect();}
    }
    private static void requireSource(String source){if(!Arrays.asList(EarthquakeData.SOURCES).contains(source))throw new IllegalArgumentException("未知地震数据源");}
    static List<EarthquakeData.Report> parse(String source,String raw)throws Exception{requireSource(source);return raw.isEmpty()?new ArrayList<>():source.equals("GEOFON")?EarthquakeData.geofon(raw):source.equals("Wolfx")?EarthquakeData.wolfx(raw):source.equals("USGS")?EarthquakeData.usgs(raw):EarthquakeData.emsc(raw);}
    static long retryAfter(String value,long now){
        long delay=60_000;try{delay=Math.max(delay,Math.multiplyExact(Long.parseLong(value.trim()),1000));}catch(Exception ignored){try{delay=Math.max(delay,ZonedDateTime.parse(value,DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()-now);}catch(Exception ignored2){}}
        return now+Math.min(delay,7L*86400_000);
    }
}
