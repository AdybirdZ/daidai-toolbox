package org.simpleweather.app;
import java.time.*;
import java.util.*;
public final class EnglishModelTests {
    private static int checks;
    public static void main(String[] args){
        check(WeatherData.condition(95).equals("Slight or moderate thunderstorm"),"Weather model labels use English XML");
        check(UiText.label(Astronomy.STARS[4].name).equals("Vega"),"Stable star identity has an English display label");
        check(Astronomy.STARS[4].name.equals("织女一"),"Saved Chinese star identity remains stable");
        check(Astronomy.planets(Instant.parse("2026-10-31T12:00:00Z"))[0].name.equals("水星"),"Planet calculation identities remain stable");
        int dipper=0;for(Astronomy.Star star:Astronomy.STARS)if(Astronomy.isDipper(star.name))dipper++;
        check(dipper==7,"Big Dipper grouping uses stable identities, not localized names");
        EarthquakeData.Report other=new EarthquakeData.Report();other.source="GEOFON";other.review=UiText.raw("identity_earthquake_data_008");
        EarthquakeData.Report cn=new EarthquakeData.Report();cn.source="Wolfx";cn.review=UiText.raw("identity_earthquake_data_005");
        EarthquakeData.Event e=new EarthquakeData.Event();e.reports.add(other);e.reports.add(cn);
        check(e.main()==cn,"Localization cannot change reviewed Chinese-source priority");
        check(EarthquakeDisplay.review(cn).equals("Reviewed"),"Stable source status translates for display");
        check(EarthquakeAgencies.name(UiText.raw("identity_earthquake_data_004")).contains("National Earthquake Information Center"),"Stored agency text maps to English name");
        check(CameraExposure.DEFAULT_ISO==1000&&CameraExposure.DEFAULT_MS==200,"Camera defaults");
        check(!CameraExposure.valid(0,50)&&!CameraExposure.valid(300,1001)&&CameraExposure.valid(640,25),"Camera input boundaries");
        System.out.println("English model tests: "+checks+" checks passed");
    }
    static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
}
