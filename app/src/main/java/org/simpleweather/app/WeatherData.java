package org.simpleweather.app;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Local times are interpreted in the selected city's zone, never the phone's zone. */
public final class WeatherData {
    private static final int[] SUPPORTED_CODES={0,1,2,3,45,48,51,53,55,56,57,61,63,65,66,67,71,73,75,77,80,81,82,85,86,95,96,97,99};
    public static int[] supportedCodes() { return SUPPORTED_CODES.clone(); }
    public static boolean isSupportedCode(int code) {
        for (int supported : SUPPORTED_CODES) if (code == supported) return true;
        return false;
    }
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
            case 0: return UiText.get("weather_data_001"); case 1: return UiText.get("weather_data_002"); case 2: return UiText.get("weather_data_003"); case 3: return UiText.get("weather_data_004");
            case 45: return UiText.get("weather_data_005"); case 48: return UiText.get("weather_data_006");
            case 51: return UiText.get("weather_data_007"); case 53: return UiText.get("weather_data_008"); case 55: return UiText.get("weather_data_009");
            case 56: return UiText.get("weather_data_010"); case 57: return UiText.get("weather_data_011");
            case 61: return UiText.get("weather_data_012"); case 63: return UiText.get("weather_data_013"); case 65: return UiText.get("weather_data_014");
            case 66: return UiText.get("weather_data_015"); case 67: return UiText.get("weather_data_016");
            case 71: return UiText.get("weather_data_017"); case 73: return UiText.get("weather_data_018"); case 75: return UiText.get("weather_data_019"); case 77: return UiText.get("weather_data_020");
            case 80: return UiText.get("weather_data_021"); case 81: return UiText.get("weather_data_022"); case 82: return UiText.get("weather_data_023");
            case 85: return UiText.get("weather_data_024"); case 86: return UiText.get("weather_data_025");
            case 95: return UiText.get("weather_data_026"); case 96: return UiText.get("weather_data_027"); case 97: return UiText.get("weather_data_028"); case 99: return UiText.get("weather_data_029");
            default: return UiText.get("unknown_weather");
        }
    }
    public static int code(JSONObject object, String field, int index) {
        double n = index < 0 ? value(object, field) : at(object, field, index);
        return Double.isFinite(n) && n >= 0 && n <= Integer.MAX_VALUE && n == Math.rint(n) ? (int)n : -1;
    }
    public static String wind(double direction) {
        if (!Double.isFinite(direction)) return "";
        return new String[]{UiText.get("weather_data_030"), UiText.get("weather_data_031"), UiText.get("weather_data_032"), UiText.get("weather_data_033"), UiText.get("weather_data_034"), UiText.get("weather_data_035"), UiText.get("weather_data_036"), UiText.get("weather_data_037")}[(int)Math.floor(((direction % 360 + 360) % 360 + 22.5) / 45) % 8];
    }
    public static String clock(String iso) { return iso.length() >= 16 ? iso.substring(11, 16) : "—"; }
    public static String day(String iso, LocalDate today) {
        try {
            LocalDate date = LocalDate.parse(iso);
            if (date.equals(today)) return UiText.get("weather_data_038");
            if (date.equals(today.plusDays(1))) return UiText.get("weather_data_039");
            return new String[]{UiText.get("schedule_001"), UiText.get("schedule_002"), UiText.get("schedule_003"), UiText.get("schedule_004"), UiText.get("schedule_005"), UiText.get("schedule_006"), UiText.get("schedule_007")}[date.getDayOfWeek().getValue() - 1];
        } catch (Exception e) { return "—"; }
    }
}
