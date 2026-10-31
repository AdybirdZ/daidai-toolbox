package org.simpleweather.app;

import java.util.*;
import org.json.*;

public final class LanguageTests {
    private static int checks;
    private static void check(boolean okay,String reason){checks++;if(!okay)throw new AssertionError(reason);}
    public static void main(String[] args)throws Exception{
        check(LanguagePolicy.resolve("en",Arrays.asList(Locale.CHINESE)).equals(Locale.ENGLISH),"Explicit English wins");
        check(LanguagePolicy.resolve("zh-CN",Arrays.asList(Locale.ENGLISH)).equals(Locale.SIMPLIFIED_CHINESE),"Explicit Chinese wins");
        check(LanguagePolicy.resolve("system",Arrays.asList(Locale.UK)).equals(Locale.ENGLISH),"English system variant");
        check(LanguagePolicy.resolve("system",Arrays.asList(Locale.TRADITIONAL_CHINESE)).equals(Locale.SIMPLIFIED_CHINESE),"Chinese system variant");
        check(LanguagePolicy.resolve("system",Arrays.asList(Locale.FRENCH)).equals(Locale.SIMPLIFIED_CHINESE),"Unsupported locale fallback");
        check(LanguagePolicy.resolve("system",Arrays.asList(Locale.FRENCH,Locale.ENGLISH)).equals(Locale.ENGLISH),"Supported secondary system language");
        check(LanguagePolicy.resolve("system",Collections.emptyList()).equals(Locale.SIMPLIFIED_CHINESE),"Empty system list");
        for(String choice:LanguagePolicy.CHOICES){
            check(LanguagePolicy.valid(choice),"Supported preference "+choice);
            Map<String,Map<String,?>> values=new HashMap<>();Map<String,Object> prefs=new HashMap<>();prefs.put(LanguagePolicy.KEY,choice);prefs.put("astro_selected",Astronomy.STARS[4].name);values.put("toolbox",prefs);
            ConfigurationData.Document doc=ConfigurationData.parse(ConfigurationData.encode(values,null).toString());
            check(doc.groups.get("toolbox").get(LanguagePolicy.KEY).equals(choice),"Language configuration round trip "+choice);
            check(doc.groups.get("toolbox").get("astro_selected").equals(Astronomy.STARS[4].name),"Stable star identity round trip");
        }
        for(String choice:new String[]{"","fr","EN","中文","en-US",null}){
            check(!LanguagePolicy.valid(choice),"Invalid stored choice");
            check(LanguagePolicy.normalize(choice).equals("system"),"Corrupt preference safely defaults");
        }
        Map<String,Map<String,?>> values=new HashMap<>();Map<String,Object> prefs=new HashMap<>();prefs.put(LanguagePolicy.KEY,"fr");values.put("toolbox",prefs);
        boolean rejected=false;try{ConfigurationData.encode(values,null);}catch(Exception e){rejected=true;}check(rejected,"Invalid imported language rejected before writes");
        ConfigurationData.Document legacy=ConfigurationData.parse(ConfigurationData.encode(Collections.emptyMap(),null).toString());
        check(!legacy.groups.get("toolbox").containsKey(LanguagePolicy.KEY),"Old configuration retains default language semantics");
        // Load and then replace the provider in the same process: no stale translated catalogs.
        check(EarthquakeAgencies.name("GFZ").equals(UiText.get("earthquake_agencies_011")),"Agency before language change");
        StatusMath.direction(0);
        UiText.install(new UiText.Provider(){public String get(String key){return "test:"+key;}public String label(String id){return id;}});
        check(EarthquakeAgencies.name("GFZ").equals("test:earthquake_agencies_011"),"Agency follows changed language");
        check(StatusMath.direction(0).equals("test:astronomy_026"),"Direction follows changed language");
        System.out.println("Language tests: "+checks+" checks passed");
    }
}
