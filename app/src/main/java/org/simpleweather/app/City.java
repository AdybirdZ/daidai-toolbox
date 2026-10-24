package org.simpleweather.app;

import org.json.JSONObject;
import org.json.JSONException;
import java.util.Locale;

public final class City {
    public final String name, country, admin, timezone, alias;
    public final double latitude, longitude;

    public City(JSONObject json) throws JSONException {
        name = json.getString("name");
        latitude = json.getDouble("latitude"); longitude = json.getDouble("longitude");
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude) || latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180)
            throw new JSONException("Invalid coordinates");
        country = json.optString("country", ""); admin = json.optString("admin1", "");
        timezone = json.optString("timezone", "UTC"); alias = json.optString("alias", "");
    }
    public String label() { return alias.isEmpty() ? name : alias; }
    public String detail() {
        if (admin.isEmpty() || admin.equals(name)) return country;
        return admin + (country.isEmpty() ? "" : " · " + country);
    }
    public String key() { return String.format(Locale.ROOT, "%.5f_%.5f", latitude, longitude); }
    public boolean matches(String query) {
        String q = query.trim().toLowerCase(Locale.ROOT);
        return (name + " " + alias + " " + admin + " " + country).toLowerCase(Locale.ROOT).contains(q);
    }
    public JSONObject json() {
        JSONObject j = new JSONObject();
        try {
            j.put("name", name); j.put("latitude", latitude); j.put("longitude", longitude);
            j.put("country", country); j.put("admin1", admin); j.put("timezone", timezone); j.put("alias", alias);
        } catch (JSONException ignored) { }
        return j;
    }
}
