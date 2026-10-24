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
        double[] moon=Astronomy.moonPosition(full,39.9,116.4);check(Double.isFinite(moon[0])&&moon[0]>=-90&&moon[0]<=90&&moon[1]>=0&&moon[1]<360,"Moon altitude and azimuth remain valid");
        System.out.println("PASS: "+checks+" timetable and astronomy checks");
    }
}
