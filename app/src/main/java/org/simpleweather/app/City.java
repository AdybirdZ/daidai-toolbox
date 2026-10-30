package org.simpleweather.app;

import org.json.*;
import java.util.*;

/** A weather point and its administrative identity; coordinates are not an identity. */
public final class City {
    public final String name,country,admin,timezone,alias,placeId,feature,countryCode,searchAliases;
    public final Set<Long> geonamesIds;
    public final double latitude,longitude;
    public City(JSONObject json)throws JSONException {
        name=json.getString("name");if(name.trim().isEmpty()||name.length()>200)throw new JSONException("Invalid place name");
        latitude=json.getDouble("latitude");longitude=json.getDouble("longitude");
        if(!Double.isFinite(latitude)||!Double.isFinite(longitude)||latitude < -90||latitude > 90||longitude < -180||longitude > 180)throw new JSONException("Invalid coordinates");
        country=json.optString("country","");admin=json.optString("admin1","");timezone=json.optString("timezone","UTC");
        try{java.time.ZoneId.of(timezone);}catch(Exception e){throw new JSONException("Invalid timezone");}
        alias=json.optString("alias","");placeId=json.optString("placeId","");feature=json.optString("feature_code","");countryCode=json.optString("country_code","");searchAliases=json.optString("searchAliases","");
        Set<Long> ids=new HashSet<>();if(json.optLong("id",0)>0)ids.add(json.optLong("id"));JSONArray a=json.optJSONArray("geonamesIds");if(a!=null)for(int i=0;i<a.length();i++)if(a.optLong(i)>0)ids.add(a.optLong(i));geonamesIds=Collections.unmodifiableSet(ids);
    }
    public String label(){return alias.isEmpty()?name:alias;}
    public String detail(){if(admin.isEmpty()||admin.equals(name))return country;return admin+(country.isEmpty()?"":" · "+country);}
    public String key(){return String.format(Locale.ROOT,"%.5f_%.5f",latitude,longitude);}
    private static String normalized(String s){return s.toLowerCase(Locale.ROOT).replaceAll("[\\s,，、·.。\\-’']","");}
    static String nameKey(String s){return normalized(s).replaceAll("市$","");}
    private String region(){return normalized(admin).replaceAll("(?:壮族自治区|回族自治区|维吾尔自治区|自治区|特别行政区|省|市)$","");}
    private String nation(){if(isChina())return "CN";if(!countryCode.isEmpty())return countryCode.toUpperCase(Locale.ROOT);String[] names={"美国","日本","英国","法国","澳大利亚","德国","俄罗斯","加拿大","韩国","意大利","新加坡","印度"},codes={"US","JP","GB","FR","AU","DE","RU","CA","KR","IT","SG","IN"};for(int i=0;i<names.length;i++)if(country.equals(names[i]))return codes[i];return normalized(country);}
    boolean isChina(){return Arrays.asList("中国","台湾","香港","澳门").contains(country)||Arrays.asList("CN","TW","HK","MO").contains(countryCode);}
    public String placeKey(){return placeId.isEmpty()?nation()+":"+region()+":"+normalized(label()):placeId;}
    private double distance2(City c){double y=latitude-c.latitude,x=(longitude-c.longitude)*Math.cos(Math.toRadians((latitude+c.latitude)/2));return x*x+y*y;}
    public boolean samePlace(City c){
        if(c==null||!nation().equals(c.nation()))return false;
        if(!placeId.isEmpty()&&!c.placeId.isEmpty())return placeId.equals(c.placeId);
        if(!Collections.disjoint(geonamesIds,c.geonamesIds))return true;
        return region().equals(c.region())&&nameKey(label()).equals(nameKey(c.label()))&&distance2(c)<.05*.05;
    }
    /** Resolve bundled, online and previously stored spellings to the very same record. */
    static City resolve(List<City> catalog,City incoming){
        if(incoming==null)return null;
        for(City c:catalog)if(!c.placeId.isEmpty()&&c.placeId.equals(incoming.placeId))return c;
        for(City c:catalog)if(c.nation().equals(incoming.nation())&&!Collections.disjoint(c.geonamesIds,incoming.geonamesIds))return c;
        for(City c:catalog){
            if(!c.nation().equals(incoming.nation())||!c.region().equals(incoming.region()))continue;
            if((incoming.feature.equals("ADM3")||incoming.feature.equals("PPLA4"))&&!c.placeId.isEmpty()&&!c.label().equals(incoming.label()))continue;
            String n=normalized(incoming.label());boolean exact=nameKey(c.label()).equals(nameKey(n));
            if(!exact)for(String a:c.searchAliases.split("\\|"))if(!a.isEmpty()&&normalized(a).equals(n)){exact=true;break;}
            if(exact&&c.distance2(incoming)<.05*.05)return c;
        }
        return incoming;
    }
    boolean excludedAdministrativeArea(){if(!isChina())return false;String n=label();return placeId.isEmpty()&&(feature.equals("ADM1")||feature.equals("ADM2"))||n.matches(".*(?:自治州|地区|盟)$")||n.equals("甘孜藏族市")||n.equals("甘孜藏族自治区");}
    public boolean matches(String query){String q=normalized(query.trim());return normalized(name+" "+alias+" "+admin+" "+country+" "+searchAliases).contains(q);}
    public JSONObject json(){JSONObject j=new JSONObject();try{
        j.put("name",name).put("latitude",latitude).put("longitude",longitude).put("country",country).put("admin1",admin).put("timezone",timezone).put("alias",alias).put("placeId",placeId).put("feature_code",feature).put("country_code",countryCode).put("searchAliases",searchAliases).put("geonamesIds",new JSONArray(geonamesIds));
    }catch(JSONException ignored){}return j;}
}
