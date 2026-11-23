package org.simpleweather.app;
import java.time.*;
import java.util.*;
public final class EnglishModelTests {
    private static int checks;
    public static void main(String[] args){
        check(WeatherData.condition(95).equals("Slight or moderate thunderstorm"),"Weather model labels use English XML");
        check(UiText.label(Astronomy.STARS[4].name).equals("Vega"),"Stable star identity has an English display label");
        check(Astronomy.STARS[4].name.equals("织女一"),"Saved Chinese star identity remains stable");
        Set<String> newStarLabels=new HashSet<>();for(Astronomy.Star star:Astronomy.STARS)if(star.name.equals("土司空")||star.name.equals("五车三")||star.name.equals("阁道三")||star.name.equals("天船三")||star.name.equals("天船二")||star.name.equals("天船五")||star.name.equals("大陵五")||star.name.equals("奎宿五")||star.name.equals("天大将军九")||star.name.equals("天钩五")||star.name.equals("奎宿九")||star.name.equals("娄宿二")||star.name.equals("娄宿一")||star.name.equals("离宫四")||star.name.equals("天囷一")||star.name.equals("天囷二")||star.name.equals("天津五")||star.name.equals("天津二")||star.name.equals("天棓三")||star.name.equals("上卫增一"))newStarLabels.add(UiText.label(star.name));
        check(newStarLabels.size()==20,"Every added Chinese star has its own English display label");
        check(Astronomy.planets(Instant.parse("2026-10-31T12:00:00Z"))[0].name.equals("水星"),"Planet calculation identities remain stable");
        int dipper=0;for(Astronomy.Star star:Astronomy.STARS)if(Astronomy.isDipper(star.name))dipper++;
        check(dipper==7,"Big Dipper grouping uses stable identities, not localized names");
        EarthquakeData.Report other=new EarthquakeData.Report();other.source="GEOFON";other.review=UiText.raw("identity_earthquake_data_008");
        EarthquakeData.Report cn=new EarthquakeData.Report();cn.source="Wolfx";cn.review=UiText.raw("identity_earthquake_data_005");
        EarthquakeData.Event e=new EarthquakeData.Event();e.reports.add(other);e.reports.add(cn);
        check(e.main()==cn,"Localization cannot change reviewed Chinese-source priority");
        check(EarthquakeDisplay.review(cn).equals("Reviewed"),"Stable source status translates for display");
        check(EarthquakeDisplay.region("XIZANG").equals("Xizang")&&EarthquakeDisplay.region("SICHUAN, CHINA").equals("Sichuan"),"English region labels omit source suffixes");
        check(EarthquakeDisplay.region("TAIWAN REGION").equals("Taiwan area")&&EarthquakeDisplay.region("TAIWAN").equals("Taiwan area"),"Taiwan source variants share one English area label");
        check(EarthquakeDisplay.region("NEAR EAST COAST OF HONSHU, JAPAN").equals("East coast of Honshu"),"English Honshu label omits country wording");
        check(EarthquakeDisplay.region("XIZANG").equals("Xizang")&&EarthquakeDisplay.region("SICHUAN, CHINA").equals("Sichuan"),"English earthquake region labels omit source-name suffixes");
        check(EarthquakeDisplay.region("TAIWAN REGION").equals("Taiwan area")&&EarthquakeDisplay.region("TAIWAN").equals("Taiwan area"),"English Taiwan source variants share a geographic label");
        check(EarthquakeDisplay.region("NEAR EAST COAST OF HONSHU, JAPAN").equals("East coast of Honshu"),"English Honshu label omits country wording");
        check(EarthquakeAgencies.name(UiText.raw("identity_earthquake_data_004")).contains("National Earthquake Information Center"),"Stored agency text maps to English name");
        check(CameraExposure.DEFAULT_ISO==1000&&CameraExposure.DEFAULT_MS==200,"Camera defaults");
        check(!CameraExposure.valid(0,50)&&!CameraExposure.valid(300,1001)&&CameraExposure.valid(640,25),"Camera input boundaries");
        System.out.println("English model tests: "+checks+" checks passed");
    }
    static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
}
