package org.simpleweather.app;
import java.time.*;
import java.util.*;

public final class ToolboxTests {
    private static int checks;
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    public static void main(String[] args) throws Exception {
        ScheduleModel m=new ScheduleModel();m.semester=LocalDate.of(2026,9,7);
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
        String[] names={"晴","少云","多云","阴","雾","沉积雾凇的雾","小毛毛雨","中毛毛雨","大毛毛雨","小冻毛毛雨","大冻毛毛雨","小雨","中雨","大雨","小冻雨","大冻雨","小雪","中雪","大雪","米雪","小阵雨","中阵雨","大阵雨","小阵雪","大阵雪","小或中雷雨","雷雨伴小冰雹","强雷暴","雷雨伴大冰雹"};
        for(int i=0;i<codes.length;i++)check(WeatherData.condition(codes[i]).equals(names[i]),"Requested Chinese weather term for code "+codes[i]);
        customModel.semester=LocalDate.of(2020,1,6);customModel.slots.set(0,new ScheduleModel.Slot("07:00","07:45"));customModel.weeks=30;String courseBefore=customModel.json().getJSONArray("courses").toString();customModel.resetSettings();
        check(customModel.slots.get(0).start.equals("08:00")&&customModel.weeks==20&&customModel.semester.equals(LocalDate.now().with(DayOfWeek.MONDAY)),"Reset restores period, semester and week defaults");
        check(customModel.json().getJSONArray("courses").toString().equals(courseBefore),"Reset preserves course details and custom recurrence");
        ScheduleModel extended=new ScheduleModel();extended.weeks=35;extended.slots.add(new ScheduleModel.Slot("21:00","21:45"));ScheduleModel.Course late=new ScheduleModel.Course();late.name="晚课";late.first=late.last=11;late.weekEnd=35;extended.put(late);extended.resetSettings();
        check(extended.weeks==35&&extended.slots.size()==11&&ScheduleModel.parse(extended.json().toString()).courses.size()==1,"Reset keeps necessary extended course ranges valid");
        int dipperCount=0;for(Astronomy.Star star:Astronomy.STARS)if(Astronomy.isDipper(star.name))dipperCount++;
        check(dipperCount==7&&!Astronomy.isDipper("北极星"),"Only the seven Big Dipper stars are mandatory");
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
