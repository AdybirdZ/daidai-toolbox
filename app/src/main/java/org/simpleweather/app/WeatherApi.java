package org.simpleweather.app;

import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.net.ssl.HttpsURLConnection;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/** Only documented HTTPS endpoints. No referrer spoofing, proxy or quota-host switching. */
public final class WeatherApi {
    public interface RequestPolicy {
        void reserve() throws IOException;
        void blockUntil(long timestamp);
    }
    private final RequestPolicy policy;
    public WeatherApi() { this(null); }
    public WeatherApi(RequestPolicy policy) { this.policy = policy; }
    public static final String FORECAST_HOST = "https://api.open-meteo.com/v1/forecast";
    public static final String SEARCH_HOST = "https://geocoding-api.open-meteo.com/v1/search";
    private long blockedUntil;
    private final Deque<Long> calls = new ArrayDeque<>();
    public static String forecastUrl(City city) {
        return FORECAST_HOST + "?latitude=" + city.latitude + "&longitude=" + city.longitude
            + "&current=temperature_2m,relative_humidity_2m,apparent_temperature,is_day,precipitation,weather_code,cloud_cover,wind_speed_10m,wind_direction_10m"
            + "&hourly=temperature_2m,precipitation_probability,weather_code,is_day,cloud_cover,relative_humidity_2m,visibility,wind_speed_10m"
            + "&daily=weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,precipitation_probability_max,uv_index_max"
            + "&timezone=" + (UiText.raw("identity_weather_api_001").equals(city.country) ? "Asia%2FShanghai" : "auto")
            + "&forecast_days=7&temperature_unit=celsius&wind_speed_unit=kmh&precipitation_unit=mm";
    }
    public JSONObject forecast(City city) throws Exception { return get(forecastUrl(city)); }
    public static long retryUntil(String header, long now) {
        long until = now + 60_000;
        if (header == null) return until;
        try {
            long seconds = Math.max(0, Long.parseLong(header.trim()));
            long parsed = seconds > (Long.MAX_VALUE - now) / 1000 ? Long.MAX_VALUE : now + seconds * 1000;
            return Math.max(until, parsed);
        } catch (Exception ignored) { }
        try { return Math.max(until, ZonedDateTime.parse(header, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()); }
        catch (Exception ignored) { return until; }
    }
    public List<City> search(String query) throws Exception {
        String q = query.trim();
        if (q.length() < 2 || q.length() > 80) throw new IOException(UiText.get("weather_api_001"));
        JSONObject data = get(SEARCH_HOST + "?name=" + URLEncoder.encode(q, "UTF-8") + "&count=30&language=zh&format=json");
        List<City> list = new ArrayList<>(); JSONArray a = data.optJSONArray("results");
        if (a != null) for (int i = 0; i < a.length(); i++) try { list.add(new City(a.getJSONObject(i))); } catch (JSONException ignored) { }
        return list;
    }
    private synchronized void reserve() throws IOException {
        long now = System.currentTimeMillis();
        if (now < blockedUntil) throw new IOException(UiText.get("weather_api_002"));
        while (!calls.isEmpty() && calls.peekFirst() < now - 60_000) calls.removeFirst();
        if (calls.size() >= 20) throw new IOException(UiText.get("main_035"));
        if (policy != null) policy.reserve();
        calls.addLast(now);
    }
    private JSONObject get(String url) throws Exception {
        reserve();
        HttpsURLConnection conn = (HttpsURLConnection)new URL(url).openConnection();
        conn.setConnectTimeout(12000); conn.setReadTimeout(18000); conn.setInstanceFollowRedirects(false);
        conn.setRequestProperty("User-Agent", "DaidaiToolbox/2.0.0 (personal non-commercial Android client)");
        conn.setRequestProperty("Accept", "application/json");
        try {
            int status = conn.getResponseCode();
            if (status == 429) {
                synchronized (this) {
                    blockedUntil = retryUntil(conn.getHeaderField("Retry-After"), System.currentTimeMillis());
                    if (policy != null) policy.blockUntil(blockedUntil);
                }
                throw new IOException(UiText.get("weather_api_003"));
            }
            if (status != 200) throw new IOException(UiText.get("weather_api_004") + status + UiText.get("earthquake_api_004"));
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (InputStream in = conn.getInputStream()) {
                byte[] buffer = new byte[8192]; int length;
                while ((length = in.read(buffer)) != -1) {
                    if (bytes.size() + length > 2_000_000) throw new IOException(UiText.get("weather_api_005"));
                    bytes.write(buffer, 0, length);
                }
            }
            JSONObject json = new JSONObject(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
            if (json.optBoolean("error")) throw new IOException(UiText.get("weather_api_006"));
            return json;
        } finally { conn.disconnect(); }
    }
}
