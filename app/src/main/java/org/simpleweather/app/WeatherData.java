package org.simpleweather.app;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Local times are interpreted in the selected city's zone, never the phone's zone. */
public final class WeatherData {
    public final JSONObject raw, current, daily, hourly;
    public final ZoneId zone;
    public final LocalDateTime currentTime;
    public final long fetchedAt;
    public WeatherData(JSONObject raw, long fetchedAt) throws JSONException {
        this.raw = raw; this.fetchedAt = fetchedAt;
        if (raw.optBoolean("error")) throw new JSONException("API returned an error");
        current = raw.getJSONObject("current"); daily = raw.getJSONObject("daily"); hourly = raw.getJSONObject("hourly");
        try { zone = ZoneId.of(raw.getString("timezone")); currentTime = LocalDateTime.parse(current.getString("time")); }
        catch (Exception e) { throw new JSONException("Invalid local time or timezone"); }
        if (daily.optJSONArray("time") == null || hourly.optJSONArray("time") == null) throw new JSONException("Missing forecast times");
    }
    public static double value(JSONObject obj, String field) { return obj.optDouble(field, Double.NaN); }
    public static double at(JSONObject obj, String field, int index) {
        JSONArray a = obj.optJSONArray(field); return a == null ? Double.NaN : a.optDouble(index, Double.NaN);
    }
    public static String textAt(JSONObject obj, String field, int index) {
        JSONArray a = obj.optJSONArray(field); return a == null || a.isNull(index) ? "" : a.optString(index, "");
    }
    public int todayIndex(Instant now) {
        String today = now.atZone(zone).toLocalDate().toString();
        JSONArray dates = daily.optJSONArray("time");
        for (int i = 0; i < dates.length(); i++) if (today.equals(dates.optString(i))) return i;
        return -1;
    }
    public List<Integer> nextHours(Instant now, int count) {
        LocalDateTime floor = now.atZone(zone).toLocalDateTime().withMinute(0).withSecond(0).withNano(0);
        List<Integer> out = new ArrayList<>(); JSONArray times = hourly.optJSONArray("time");
        for (int i = 0; i < times.length() && out.size() < count; i++) {
            try { if (!LocalDateTime.parse(times.getString(i)).isBefore(floor)) out.add(i); }
            catch (Exception ignored) { }
        }
        return out;
    }
    public boolean currentIsOld(Instant now) {
        return Math.abs(Duration.between(currentTime.atZone(zone).toInstant(), now).toMinutes()) > 90;
    }
    public boolean fresh(long now) { return now >= fetchedAt && now - fetchedAt < 15 * 60_000; }
    public static String number(double n) { return Double.isFinite(n) ? String.format(Locale.ROOT, "%.0f", n) : "—"; }
    public static String degrees(double n) { return number(n) + "℃"; }
    public static String condition(int code) {
        switch (code) {
            case 0: return "晴"; case 1: return "少云"; case 2: return "多云"; case 3: return "阴";
            case 45: return "雾"; case 48: return "沉积雾凇的雾";
            case 51: return "小毛毛雨"; case 53: return "中毛毛雨"; case 55: return "大毛毛雨";
            case 56: return "小冻毛毛雨"; case 57: return "大冻毛毛雨";
            case 61: return "小雨"; case 63: return "中雨"; case 65: return "大雨";
            case 66: return "小冻雨"; case 67: return "大冻雨";
            case 71: return "小雪"; case 73: return "中雪"; case 75: return "大雪"; case 77: return "米雪";
            case 80: return "小阵雨"; case 81: return "中阵雨"; case 82: return "大阵雨";
            case 85: return "小阵雪"; case 86: return "大阵雪";
            case 95: return "小雷雨 / 中雷雨"; case 96: return "雷雨伴小冰雹"; case 99: return "雷雨伴大冰雹";
            default: return "天气未知";
        }
    }
    public static int code(JSONObject object, String field, int index) {
        double n = index < 0 ? value(object, field) : at(object, field, index);
        return Double.isFinite(n) ? (int)n : -1;
    }
    public static String wind(double direction) {
        if (!Double.isFinite(direction)) return "";
        return new String[]{"北风", "东北风", "东风", "东南风", "南风", "西南风", "西风", "西北风"}[(int)Math.floor(((direction % 360 + 360) % 360 + 22.5) / 45) % 8];
    }
    public static String clock(String iso) { return iso.length() >= 16 ? iso.substring(11, 16) : "—"; }
    public static String day(String iso, LocalDate today) {
        try {
            LocalDate date = LocalDate.parse(iso);
            if (date.equals(today)) return "今天";
            if (date.equals(today.plusDays(1))) return "明天";
            return new String[]{"周一", "周二", "周三", "周四", "周五", "周六", "周日"}[date.getDayOfWeek().getValue() - 1];
        } catch (Exception e) { return "—"; }
    }
}
