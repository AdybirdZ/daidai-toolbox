package org.simpleweather.app;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.*;
import java.util.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public final class WeatherStore implements WeatherApi.RequestPolicy {
    private final Context context;
    private final SharedPreferences prefs;
    public WeatherStore(Context context) { this.context = context; prefs = context.getSharedPreferences("weather", 0); }
    @Override public synchronized void reserve() throws IOException {
        long now=System.currentTimeMillis();
        if(now<prefs.getLong("network_blocked_until",0))throw new IOException("数据服务暂时限流，请稍后重试");
        long[] windows={60_000,3_600_000,86_400_000};int[] limits={20,1000,2000};
        SharedPreferences.Editor edit=prefs.edit();
        for(int i=0;i<windows.length;i++) {
            long bucket=now/windows[i],previous=prefs.getLong("quota_window_"+i,-1);
            int count=bucket==previous?prefs.getInt("quota_count_"+i,0):0;
            if(bucket<previous||count>=limits[i])throw new IOException(i==2?"今日查询较多，请明天再试":"查询较频繁，请稍后再试");
            edit.putLong("quota_window_"+i,bucket).putInt("quota_count_"+i,count+1);
        }
        edit.apply();
    }
    @Override public void blockUntil(long timestamp) {prefs.edit().putLong("network_blocked_until",timestamp).apply();}
    public City selected(City fallback) {
        try { return new City(new JSONObject(prefs.getString("selected", ""))); } catch (Exception e) { return fallback; }
    }
    public void select(City city) { prefs.edit().putString("selected", city.json().toString()).apply(); }
    public List<City> saved() {
        List<City> out = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(prefs.getString("saved", "[]"));
            for (int i=0;i<array.length();i++) try { out.add(new City(array.getJSONObject(i))); } catch (Exception ignored) { }
        } catch (Exception ignored) { }
        return out;
    }
    public boolean isSaved(City city) { for(City c:saved()) if(c.key().equals(city.key())) return true; return false; }
    public boolean toggle(City city) {
        List<City> cities=saved(); boolean removed=false;
        for(int i=cities.size()-1;i>=0;i--) if(cities.get(i).key().equals(city.key())) {cities.remove(i);removed=true;}
        if(!removed) { if(cities.size()>=20) cities.remove(0); cities.add(city); }
        JSONArray array=new JSONArray();for(City c:cities)array.put(c.json());
        prefs.edit().putString("saved",array.toString()).apply();return !removed;
    }
    private File file(City city) { return new File(context.getFilesDir(), "forecast_"+city.key()+".json"); }
    public WeatherData cached(City city) {
        try {
            JSONObject entry=new JSONObject(new String(Files.readAllBytes(file(city).toPath()),StandardCharsets.UTF_8));
            return new WeatherData(entry.getJSONObject("data"),entry.getLong("fetchedAt"));
        } catch(Exception e) { return null; }
    }
    public synchronized void save(City city, WeatherData data) {
        try {
            JSONObject entry=new JSONObject();entry.put("data",data.raw);entry.put("fetchedAt",data.fetchedAt);
            File target=file(city), temp=new File(target.getPath()+".tmp");
            try(FileOutputStream out=new FileOutputStream(temp)) {out.write(entry.toString().getBytes(StandardCharsets.UTF_8));}
            Files.move(temp.toPath(),target.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            File[] files=context.getFilesDir().listFiles((dir,name)->name.startsWith("forecast_")&&name.endsWith(".json"));
            if(files!=null && files.length>20) {
                Arrays.sort(files, Comparator.comparingLong(File::lastModified));
                for(int i=0;i<files.length-20;i++) files[i].delete();
            }
        } catch(Exception ignored) { }
    }
    public synchronized boolean clearCache(){return ForecastCache.clear(context.getFilesDir());}
    public List<City> catalog() {
        List<City> cities=new ArrayList<>();
        try(InputStream in=context.getAssets().open("cities.json")) {
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int n;
            while((n=in.read(buffer))!=-1)bytes.write(buffer,0,n);
            JSONArray array=new JSONArray(new String(bytes.toByteArray(),StandardCharsets.UTF_8));
            for(int i=0;i<array.length();i++) cities.add(new City(array.getJSONObject(i)));
        } catch(Exception ignored) { }
        return cities;
    }
}
