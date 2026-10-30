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
    /** Stable place key used to collapse duplicate geocoder rows.  Geocoders may
     * return the same city once from the bundled directory and once remotely,
     * with slightly different precision or an omitted 市 suffix. */
    public String placeKey() {
        String value=(label()+" "+admin).toLowerCase(Locale.ROOT).replaceAll("[\\s,，、·.。\\-]", "");
        value=value.replaceAll("(市|区|县|镇|乡|旗)$", "");
        return value;
    }
    public boolean samePlace(City other) {
        if(other==null)return false;
        double dlat=latitude-other.latitude,dlon=(longitude-other.longitude)*Math.cos(Math.toRadians((latitude+other.latitude)/2));
        return placeKey().equals(other.placeKey()) || dlat*dlat+dlon*dlon < 0.12*0.12;
    }
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
