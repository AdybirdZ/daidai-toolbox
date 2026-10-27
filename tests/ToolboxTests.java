package org.simpleweather.app;
import java.time.*;
import java.util.*;

public final class ToolboxTests {
    private static int checks;
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    public static void main(String[] args) throws Exception {
        java.nio.file.Path cache=java.nio.file.Files.createTempDirectory("toolbox-cache-test");
        String[] cacheNames={"forecast_city.json","forecast_city.json.tmp","background.jpg","background.tmp","courses.json","preferences.json"};
        try {
            for(String name:cacheNames)java.nio.file.Files.write(cache.resolve(name),new byte[]{1,2,3});
            check(ForecastCache.clear(cache.toFile()),"Weather cache deletion succeeds");
            check(!java.nio.file.Files.exists(cache.resolve(cacheNames[0]))&&!java.nio.file.Files.exists(cache.resolve(cacheNames[1])),"Both complete and interrupted forecast writes are removed");
            for(int i=2;i<cacheNames.length;i++)check(java.util.Arrays.equals(java.nio.file.Files.readAllBytes(cache.resolve(cacheNames[i])),new byte[]{1,2,3}),"Cache clearing preserves user data: "+cacheNames[i]);
            check(ForecastCache.clear(cache.toFile()),"Clearing twice is harmless");
        } finally {for(String name:cacheNames)java.nio.file.Files.deleteIfExists(cache.resolve(name));java.nio.file.Files.delete(cache);}
        ScheduleModel m=new ScheduleModel();m.semester=LocalDate.of(2026,9,7);
        check(m.weeks==17&&m.slots.size()==12,"Fresh timetable uses 17 weeks and 12 periods");
        String[] expectedTimes={"08:00–08:45","08:50–09:35","09:55–10:40","10:45–11:30","11:35–12:20","14:00–14:45","14:50–15:35","15:55–16:40","16:45–17:30","18:30–19:15","19:25–20:10","20:20–21:05"};
        boolean correctTimes=true;for(int i=0;i<expectedTimes.length;i++){ScheduleModel.Slot slot=m.slots.get(i);correctTimes&=(slot.start+"–"+slot.end).equals(expectedTimes[i])&&m.validateSlot(i,slot.start,slot.end)==null;}
        check(correctTimes,"All requested default class times match and do not overlap");
        ScheduleModel legacy=new ScheduleModel();legacy.weeks=20;legacy.slots.clear();
        String[][] legacyTimes={{"07:55","08:45"},{"08:55","09:40"},{"10:00","10:45"},{"10:55","11:40"},{"14:00","14:45"},{"14:55","15:40"},{"16:00","16:45"},{"16:55","17:40"},{"19:00","19:45"},{"19:55","20:40"}};
        for(String[] time:legacyTimes)legacy.slots.add(new ScheduleModel.Slot(time[0],time[1]));
        ScheduleModel.Course oldCourse=new ScheduleModel.Course();oldCourse.name="Legacy";oldCourse.first=oldCourse.last=10;oldCourse.weekEnd=20;legacy.put(oldCourse);
        String legacyJson=legacy.json().toString();ScheduleModel upgraded=ScheduleModel.parse(legacyJson);
        check(upgraded.weeks==20&&upgraded.slots.size()==10&&upgraded.slots.get(0).start.equals("07:55")&&upgraded.json().toString().equals(legacyJson),"Upgrade preserves old semester, custom times and courses unchanged");
        upgraded.resetSettings();check(upgraded.weeks==20&&upgraded.slots.size()==12&&upgraded.validateCourse(upgraded.courses.get(0))==null,"Reset adopts 12 periods while preserving a course through week 20");
        upgraded.courses.clear();upgraded.resetSettings();check(upgraded.weeks==17&&upgraded.slots.size()==12,"Reset without extended courses returns to 17 weeks");
        check(m.week(LocalDate.of(2026,9,6))==0,"Before semester is not week one");
        check(m.week(LocalDate.of(2026,9,7))==1,"Semester begins on first Monday");
        check(m.week(LocalDate.of(2026,9,13))==1,"Sunday stays in the same week");
        check(m.week(LocalDate.of(2026,9,14))==2,"Next Monday starts week two");
        check(m.validateSlot(1,"08:30","09:30")!=null,"Overlapping time slots are rejected");
        check(m.validateSlot(1,"08:55","08:30")!=null,"End before start is rejected");
        check(m.validateSlot(1,"08:55","09:45")==null,"Valid period edits are accepted");
        check(m.validateSlot(0,"bad","09:00")!=null,"Malformed time input is rejected");
        ScheduleModel.Course odd=new ScheduleModel.Course();odd.name="数学";odd.room="A101";odd.teacher="张老师";odd.first=1;odd.last=2;odd.parity=1;
        check(m.validateCourse(odd)==null,"Valid manual course can be added");m.put(odd);
        ScheduleModel.Course even=odd.copy();even.id="even";even.parity=2;
        check(m.validateCourse(even)==null,"Odd and even weeks can share a time block");m.put(even);
        ScheduleModel.Course conflict=odd.copy();conflict.id="conflict";
        check(m.validateCourse(conflict)!=null,"Overlapping active weeks are rejected");
        conflict.day=2;check(m.validateCourse(conflict)==null,"Same time on a different day is accepted");
        conflict.day=1;conflict.first=3;conflict.last=4;check(m.validateCourse(conflict)==null,"Non-overlapping periods are accepted");
        conflict.first=1;conflict.last=2;conflict.weekStart=21;conflict.weekEnd=21;check(m.validateCourse(conflict)!=null,"Courses cannot exceed semester weeks");
        conflict.weekStart=2;conflict.weekEnd=2;conflict.parity=1;check(m.validateCourse(conflict)!=null,"Empty odd-week range is rejected");
        check(odd.runs(1)&&!odd.runs(2)&&!odd.runs(21),"Course recurrence respects range and parity");
        ScheduleModel copy=ScheduleModel.parse(m.json().toString());
        check(copy.courses.size()==2&&copy.courses.get(0).room.equals("A101")&&copy.courses.get(0).teacher.equals("张老师"),"Course location and teacher persist in serialized timetable");
        check(copy.semester.equals(m.semester)&&copy.slots.size()==m.slots.size(),"Semester and custom periods persist");
        ScheduleModel.Course changed=odd.copy();changed.name="高等数学";m.put(changed);check(m.courses.size()==2,"Editing does not duplicate the course");
        check(Astronomy.norm(-10)==350,"Negative angles wrap correctly");
        Instant equinox=Instant.parse("2024-03-20T03:06:00Z");check(Math.abs(Astronomy.sun(equinox)[1])<.3,"Solar declination is near zero at equinox");
        Instant solstice=Instant.parse("2024-06-20T20:51:00Z");check(Math.abs(Astronomy.sun(solstice)[1]-23.44)<.3,"June solstice declination is near obliquity");
        Instant eclipse=Instant.parse("2024-04-08T18:21:00Z");check(Astronomy.illumination(eclipse)<.02,"2024 eclipse occurs near new moon");
        Instant full=Instant.parse("2024-03-25T07:00:00Z");check(Astronomy.illumination(full)>.98,"Known full moon is nearly fully illuminated");
        double[] h=Astronomy.sunPosition(Instant.parse("2024-03-20T12:00:00Z"),0,0);check(h[0]>87,"Equatorial sun is near zenith around equinox noon");
        check(Astronomy.sunPosition(Instant.parse("2024-03-20T00:00:00Z"),0,0)[0]<-85,"Sun is below horizon at local midnight");
        List<Astronomy.Event> events=Astronomy.crossings(LocalDate.of(2024,3,20),ZoneId.of("UTC"),0,0,false,-.833);
        check(events.size()==2&&events.get(0).rising&&!events.get(1).rising,"Equinox day has ordered sunrise and sunset");
        check(Math.abs(Duration.between(events.get(0).at,events.get(1).at).toMinutes()-720)<15,"Equatorial daylight is about 12 hours");
        check(Astronomy.crossings(LocalDate.of(2024,6,21),ZoneId.of("Europe/Oslo"),69.65,18.96,false,-.833).isEmpty(),"Polar day has no fabricated sunrise or sunset");
        check(Astronomy.crossings(LocalDate.of(2024,12,21),ZoneId.of("Europe/Oslo"),69.65,18.96,false,-.833).isEmpty(),"Polar night has no fabricated sunrise or sunset");
        double polaris=Astronomy.horizontal(37.955,89.264,equinox,39.9,116.4)[0];check(Math.abs(polaris-39.9)<1,"Polaris altitude is near observer latitude");
        double[] moon=Astronomy.moonPosition(full,39.9,116.4);check(Double.isFinite(moon[0])&&moon[0]>=-90&&moon[0]<=90&&moon[1]>=0&&moon[1]<360,"Moon altitude and azimuth remain valid");        Astronomy.Planet[] jpl=Astronomy.planets(Instant.parse("2026-10-25T00:00:00Z"));
        double[] jplRa={227.296,205.422,137.556,145.634,9.748,62.604,2.260};
        double[] jplDec={-20.719,-17.364,17.829,14.454,1.263,20.892,-.565};
        for(int i=0;i<jpl.length;i++)check(Math.abs(jpl[i].ra-jplRa[i])<.02&&Math.abs(jpl[i].dec-jplDec[i])<.02,"JPL planet coordinates match reference for "+jpl[i].name);
        ScheduleModel customModel=new ScheduleModel();ScheduleModel.Course custom=new ScheduleModel.Course();custom.name="自定义课";custom.parity=3;custom.customWeeks.addAll(Arrays.asList(1,3,4,6));
        check(customModel.validateCourse(custom)==null,"Non-consecutive teaching weeks are accepted");customModel.put(custom);
        check(custom.runs(1)&&custom.runs(3)&&custom.runs(4)&&custom.runs(6)&&!custom.runs(2)&&!custom.runs(5),"Custom recurrence uses only selected weeks");
        ScheduleModel.Course customConflict=custom.copy();customConflict.id="new";customConflict.parity=2;customConflict.weekStart=4;customConflict.weekEnd=4;
        check(customModel.validateCourse(customConflict)!=null,"Custom and even-week rules conflict in shared week four");customConflict.weekStart=2;customConflict.weekEnd=2;check(customModel.validateCourse(customConflict)==null,"Disjoint custom weeks may share a period");
        ScheduleModel customCopy=ScheduleModel.parse(customModel.json().toString());check(customCopy.courses.get(0).customWeeks.equals(custom.customWeeks),"Custom week set survives serialization");
        ScheduleModel.Course empty=custom.copy();empty.customWeeks.clear();check(customModel.validateCourse(empty)!=null,"Empty custom weeks are rejected");empty.customWeeks.add(21);check(customModel.validateCourse(empty)!=null,"Custom weeks cannot exceed semester");
        ScheduleModel.Course independent=custom.copy();independent.customWeeks.clear();check(custom.customWeeks.size()==4,"Editing a copied course cannot mutate saved weeks before confirmation");
        check(WeatherData.degrees(19).equals("19℃"),"Temperature unit is Celsius");
        check(DisplayOrder.parse("4,2,4,99,bad,0",5).equals(Arrays.asList(4,2,0,1,3)),"Invalid and duplicate preferences recover without dropping new sections");
        check(DisplayOrder.visible(Arrays.asList(3,0,4,1,2),new HashSet<>(Arrays.asList(3,4))).equals(Arrays.asList(0,1,2)),"Hiding tabs preserves configured order");
        check(DisplayOrder.window(Arrays.asList(0,1),0).size()==2,"Two tabs share one complete row");check(DisplayOrder.window(Arrays.asList(0,1,2,3),0).size()==4,"Four tabs require no arrow paging");
        check(DisplayOrder.window(Arrays.asList(0,1,2,3,4),2).equals(Arrays.asList(2,3,4)),"Arrow paging reveals the last three of five tabs");
        check(Astronomy.skyDescription(Instant.parse("2024-03-20T05:00:00Z"),0,0).contains("曙光"),"Morning twilight is dawn");check(Astronomy.skyDescription(Instant.parse("2024-03-20T19:00:00Z"),0,0).contains("暮光"),"Evening twilight is dusk");
        int[] codes={0,1,2,3,45,48,51,53,55,56,57,61,63,65,66,67,71,73,75,77,80,81,82,85,86,95,96,97,99};
        String[] names={"晴","少云","多云","阴","雾","沉积雾凇的雾","小毛毛雨","中毛毛雨","大毛毛雨","小冻毛毛雨","中或大冻毛毛雨","小雨","中雨","大雨","小冻雨","中或大冻雨","小雪","中雪","大雪","米雪","小阵雨","中阵雨","大阵雨","小阵雪","大阵雪","小或中雷暴","小或中雷暴伴冰雹","大雷暴","大雷暴伴冰雹"};
        for(int i=0;i<codes.length;i++)check(WeatherData.condition(codes[i]).equals(names[i]),"Requested Chinese weather term for code "+codes[i]);
        customModel.semester=LocalDate.of(2020,1,6);customModel.slots.set(0,new ScheduleModel.Slot("07:00","07:45"));customModel.weeks=30;String courseBefore=customModel.json().getJSONArray("courses").toString();customModel.resetSettings();
        check(customModel.slots.get(0).start.equals("08:00")&&customModel.weeks==17&&customModel.semester.equals(LocalDate.now().with(DayOfWeek.MONDAY)),"Reset restores period, semester and week defaults");
        check(customModel.json().getJSONArray("courses").toString().equals(courseBefore),"Reset preserves course details and custom recurrence");
        ScheduleModel extended=new ScheduleModel();extended.weeks=35;extended.slots.add(new ScheduleModel.Slot("21:15","22:00"));ScheduleModel.Course late=new ScheduleModel.Course();late.name="晚课";late.first=late.last=13;late.weekEnd=35;extended.put(late);extended.resetSettings();
        check(extended.weeks==35&&extended.slots.size()==13&&ScheduleModel.parse(extended.json().toString()).courses.size()==1,"Reset keeps necessary extended course ranges valid");
        double[] zenith=SkyGeometry.horizontal(90,123),north=SkyGeometry.horizontal(0,0),east=SkyGeometry.horizontal(0,90);
        check(Math.hypot(zenith[0],zenith[1])<1e-10&&Math.abs(north[1]+1)<1e-10&&Math.abs(east[0]+1)<1e-10,"Horizontal chart has zenith center, north up and east left");
        double[] pole=SkyGeometry.northPolar(0,90),equator=SkyGeometry.northPolar(90,0);
        check(Math.hypot(pole[0],pole[1])<1e-10&&Math.abs(equator[0]+.5)<1e-10,"Equatorial chart is centered on the celestial pole with RA 6h left");
        boolean inverse=true;for(double latitude:new double[]{-70,-20,0,35,80})for(int az=0;az<360;az+=15){double[] eq=SkyGeometry.equatorial(12,az,equinox,latitude,116.4);double[] hz=Astronomy.horizontal(eq[0],eq[1],equinox,latitude,116.4);inverse&=Math.abs(hz[0]-12)<1e-8&&Math.abs(Astronomy.norm(hz[1]-az+180)-180)<1e-8;}
        check(inverse,"Equatorial horizon conversion roundtrips for both hemispheres and all azimuths");
        check(SkyGeometry.selectionAngle(0)==0&&SkyGeometry.selectionAngle(7500)==90&&SkyGeometry.selectionAngle(30000)==0,"Selection cross rotates clockwise once per 30 seconds");
        Set<String> starNames=new HashSet<>();for(Astronomy.Star star:Astronomy.STARS)starNames.add(star.name);
        check(starNames.containsAll(Arrays.asList("织女一","河鼓二","勾陈一"))&&!starNames.contains("织女星")&&!starNames.contains("牛郎星")&&!starNames.contains("北极星"),"Current star labels use the requested traditional catalog names");
        check(Astronomy.DEEP_SKY.length>=12&&Math.abs(Astronomy.DEEP_SKY[0].ra-56.75)<1e-6&&Math.abs(Astronomy.DEEP_SKY[1].dec-41.26875)<1e-6,"Expanded CC0 deep-sky catalogue preserves the two original centers");
        for(Astronomy.Star object:Astronomy.DEEP_SKY){Instant transit=equinox;double longitude=Astronomy.norm(object.ra-Astronomy.sidereal(transit,0)+180)-180;double[] above=Astronomy.horizontal(object.ra,object.dec,transit,35,longitude);double[] below=Astronomy.horizontal(object.ra,object.dec,transit,35,longitude+180);check(above[0]>0&&(object.dec>55?below[0]>0:below[0]<0),"Deep-sky center observes local horizon rather than being always visible: "+object.name);}
        double fullMag=Astronomy.moonMagnitudeEstimate(0,1,60.2666),halfMag=Astronomy.moonMagnitudeEstimate(90,1,60.2666);
        check(Math.abs(fullMag+12.7196)<.01&&halfMag>fullMag+2,"Lunar empirical magnitude reproduces reference full and half brightness");
        check(Math.abs(Astronomy.moonMagnitudeEstimate(0,1,120.5332)-fullMag-5*Math.log10(2))<1e-9,"Moon distance changes magnitude with inverse square illumination");
        check(Double.isNaN(Astronomy.moonMagnitudeEstimate(170,1,60))&&Double.isNaN(Astronomy.moonMagnitudeEstimate(0,0,60))&&Double.isNaN(Astronomy.moonMagnitudeEstimate(Double.NaN,1,60)),"Do not extrapolate lunar photometry near new moon or accept invalid inputs");
        check(Astronomy.moonMagnitude(full)<-12&&Astronomy.moonMagnitude(full)>-13.5,"Known full moon has plausible estimated V magnitude");
        for(double cycle:new double[]{0,.125,.25,.5,.75,.875}){double area=0;for(int i=0;i<10000;i++){double y=-1+(i+.5)/5000;double[] span=Astronomy.moonDiscSpan(cycle,y);area+=(span[1]-span[0])/5000;}check(Math.abs(area/Math.PI-(1-Math.cos(2*Math.PI*cycle))/2)<1e-5,"Lunar disc bright area matches illuminated fraction at phase "+cycle);}
        check(Astronomy.moonDiscSpan(.25,0)[0]>-1e-9&&Astronomy.moonDiscSpan(.75,0)[1]<1e-9,"Waxing illuminates right and waning illuminates left");
        SkyLabels.Candidate high=new SkyLabels.Candidate("high",100,100,40,-10,2),low=new SkyLabels.Candidate("low",100,108,40,-10,2),third=new SkyLabels.Candidate("third",100,110,40,-10,2);
        List<SkyLabels.Placed> placed=SkyLabels.place(Arrays.asList(third,low,high),Collections.emptyList(),300,300,8,2,1);
        check(placed.size()==2&&placed.get(0).candidate.id.equals("high")&&placed.get(0).baseline==92&&placed.get(1).candidate.id.equals("low")&&placed.get(1).baseline==124,"Higher point keeps upper label, lower flips below, third hides if both collide");
        check(Math.abs((high.y-placed.get(0).box.bottom)-(placed.get(1).box.top-low.y))<1e-6,"Upper and lower label gaps are symmetric");
        List<SkyLabels.Placed> blocked=SkyLabels.place(Arrays.asList(high),Arrays.asList(new SkyLabels.Box(75,70,125,100)),300,300,8,2,1);
        check(blocked.size()==1&&blocked.get(0).baseline==116,"Reserved coordinate text makes label use only the fixed lower alternative");
        check(SkyLabels.place(Arrays.asList(new SkyLabels.Candidate("edge",2,100,40,-10,2)),Collections.emptyList(),300,300,8,2,1).isEmpty(),"Out-of-bounds labels hide instead of drifting sideways");
        check(SkyVisibility.visible(true,false,4.6,4.6f,false),"Magnitude cutoff includes an exact tenth despite float preference precision");
        check(!SkyVisibility.visible(true,true,4.61,4.6f,false),"Dimmer than the exact cutoff is hidden without rounding target magnitude");
        check(SkyVisibility.visible(true,false,-1.46,-1.4f,false)&&!SkyVisibility.visible(true,true,-1.46,-1.5f,false),"Negative magnitudes obey lower-is-brighter comparison");
        check(SkyVisibility.visible(false,true,8,0,false)&&!SkyVisibility.visible(false,false,-1,8,false),"Manual mode respects saved toggles independently of magnitude cutoff");
        check(SkyVisibility.visible(true,false,Double.NaN,-2,true),"Sun and Moon are exempt from star magnitude filtering");
        check(!SkyVisibility.visible(true,true,Double.NaN,8,false),"Unknown magnitudes do not pretend to pass a numeric cutoff");
        check(SkyVisibility.limit(Float.NaN)==6&&SkyVisibility.limit(-8)==-2&&SkyVisibility.limit(10)==8&&SkyVisibility.limit(4.64f)==4.6f,"Magnitude setting has finite defaults, bounds and tenth steps");
        Set<String> allNames=new HashSet<>();boolean catalogValid=true;
        for(Astronomy.Star star:Astronomy.STARS)catalogValid&=allNames.add(star.name)&&Double.isFinite(star.ra)&&star.ra>=0&&star.ra<360&&Math.abs(star.dec)<=90&&Double.isFinite(star.mag);
        check(catalogValid&&Astronomy.STARS.length>=40,"Expanded stellar catalogue has unique names and finite physical values");
        for(Astronomy.Star object:Astronomy.DEEP_SKY)check(allNames.add(object.name)&&object.name.matches("M[0-9]+ .+")&&Double.isFinite(object.mag)&&object.mag<=7.0,"Bright deep-sky object has unique name and approximate integrated magnitude: "+object.name);
        check(allNames.containsAll(Arrays.asList("参宿一","参宿二","参宿三","参宿四","参宿五","参宿六","参宿七")),"All seven principal Orion stars are included");
        int dipperCount=0;for(Astronomy.Star star:Astronomy.STARS)if(Astronomy.isDipper(star.name))dipperCount++;
        check(dipperCount==7&&!Astronomy.isDipper("北极星"),"Big Dipper membership identifies precisely seven stars");
        for(Astronomy.Shower shower:Astronomy.SHOWERS){
            LocalDate peak=shower.peak.atYear(2026);
            check(shower.activeOn(peak.minusDays(3))&&shower.activeOn(peak)&&shower.activeOn(peak.plusDays(3)),"Meteor window includes peak and both third days: "+shower.name);
            check(!shower.activeOn(peak.minusDays(4))&&!shower.activeOn(peak.plusDays(4)),"Meteor window excludes both fourth days: "+shower.name);
            check(shower.nextPeak(peak.plusDays(1)).equals(shower.peak.atYear(2027)),"Next meteor peak rolls into next year: "+shower.name);
        }
        Astronomy.Shower newYear=new Astronomy.Shower("Year boundary",1,1,0,0);
        check(newYear.activeOn(LocalDate.of(2025,12,29))&&!newYear.activeOn(LocalDate.of(2025,12,28)),"Meteor activity crosses calendar year correctly");
        LocalDate shanghaiDate=Instant.parse("2026-08-08T16:00:00Z").atZone(ZoneId.of("Asia/Shanghai")).toLocalDate();
        check(Astronomy.SHOWERS[3].activeOn(shanghaiDate),"Meteor visibility uses the selected city's calendar date");
        System.out.println("PASS: "+checks+" timetable and astronomy checks");
    }
}
