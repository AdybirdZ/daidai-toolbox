package org.simpleweather.app;

import org.json.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Independent manual timetable model. All validation runs before replacing persisted data. */
public final class ScheduleModel {
    public static final class Slot {
        public String start,end;
        public Slot(String start,String end){this.start=start;this.end=end;}
    }
    public static final class Course {
        public String id=UUID.randomUUID().toString(),name="",room="",teacher="";
        public int day=1,first=1,last=1,weekStart=1,weekEnd=20,parity=0,color=0;
        public final SortedSet<Integer> customWeeks=new TreeSet<>();
        public boolean runs(int week){if(parity==3)return customWeeks.contains(week);return week>=weekStart&&week<=weekEnd&&(parity==0||week%2==parity%2);}
        public Course copy(){Course c=new Course();c.id=id;c.name=name;c.room=room;c.teacher=teacher;c.day=day;c.first=first;c.last=last;c.weekStart=weekStart;c.weekEnd=weekEnd;c.parity=parity;c.color=color;c.customWeeks.addAll(customWeeks);return c;}
    }
    public LocalDate semester=LocalDate.now().with(java.time.DayOfWeek.MONDAY);
    public int weeks=20;
    public final List<Slot> slots=new ArrayList<>();
    public final List<Course> courses=new ArrayList<>();
    public ScheduleModel(){
        String[][] defaults={{"08:00","08:45"},{"08:55","09:40"},{"10:00","10:45"},{"10:55","11:40"},{"14:00","14:45"},{"14:55","15:40"},{"16:00","16:45"},{"16:55","17:40"},{"19:00","19:45"},{"19:55","20:40"}};
        for(String[] t:defaults)slots.add(new Slot(t[0],t[1]));
    }
    /** Reset configuration without deleting courses or creating invalid period/week references. */
    public void resetSettings(){ScheduleModel defaults=new ScheduleModel();int neededPeriods=0,neededWeeks=20;for(Course c:courses){neededPeriods=Math.max(neededPeriods,c.last);neededWeeks=Math.max(neededWeeks,c.parity==3?c.customWeeks.last():c.weekEnd);}semester=defaults.semester;weeks=neededWeeks;if(neededPeriods<=defaults.slots.size()){slots.clear();slots.addAll(defaults.slots);}else{boolean compatible=minute(slots.get(defaults.slots.size()).start)>=minute(defaults.slots.get(defaults.slots.size()-1).end);if(compatible){for(int i=0;i<defaults.slots.size();i++)slots.set(i,defaults.slots.get(i));}while(slots.size()>neededPeriods)slots.remove(slots.size()-1);}}
    public int week(LocalDate date){return (int)Math.floorDiv(ChronoUnit.DAYS.between(semester,date),7)+1;}
    public static int minute(String value){LocalTime t=LocalTime.parse(value);return t.getHour()*60+t.getMinute();}
    public String validateSlot(int index,String start,String end){
        try{
            int a=minute(start),b=minute(end);
            if(a>=b)return "下课时间必须晚于上课时间";
            if(index>0&&a<minute(slots.get(index-1).end))return "不能与上一节课的时间重叠";
            if(index+1<slots.size()&&b>minute(slots.get(index+1).start))return "不能与下一节课的时间重叠";
            return null;
        }catch(Exception e){return "请输入有效的时间";}
    }
    public String validateCourse(Course c){
        if(c.name.trim().isEmpty()||c.name.length()>60)return "课程名称必填，最多 60 个字";
        if(c.room.length()>80||c.teacher.length()>80)return "地点和老师各最多 80 个字";
        if(c.day<1||c.day>7)return "请选择星期";
        if(c.first<1||c.last<c.first||c.last>slots.size())return "结束节次不能早于开始节次";
        if(c.parity!=3&&(c.weekStart<1||c.weekEnd<c.weekStart||c.weekEnd>weeks))return "授课周次必须在学期范围内，结束周不能早于开始周";
        if(c.parity<0||c.parity>3)return "请选择有效的授课周规则";
        if(c.parity==3&&(c.customWeeks.isEmpty()||c.customWeeks.first()<1||c.customWeeks.last()>weeks))return "请选择学期范围内的自定义授课周";
        boolean any=false;for(int w=1;w<=weeks;w++)if(c.runs(w))any=true;
        if(!any)return "所选授课范围内没有符合单双周规则的周次";
        for(Course other:courses){
            if(other.id.equals(c.id)||other.day!=c.day||other.last<c.first||other.first>c.last)continue;
            for(int w=1;w<=weeks;w++)
                if(c.runs(w)&&other.runs(w))return "与“"+other.name+"”在第 "+w+" 周的节次冲突";
        }
        return null;
    }
    public void put(Course c){courses.removeIf(x->x.id.equals(c.id));courses.add(c);}
    public String courseTime(Course c){return slots.get(c.first-1).start+"–"+slots.get(c.last-1).end;}
    public String weekLabel(Course c){if(c.parity==3)return "自定义 · 第 "+customLabel(c.customWeeks)+" 周";return "第 "+c.weekStart+"–"+c.weekEnd+" 周"+(c.parity==1?" · 单周":c.parity==2?" · 双周":"");}
    public static String customLabel(Collection<Integer> weeks){StringJoiner out=new StringJoiner("、");for(int week:weeks)out.add(String.valueOf(week));return out.toString();}
    public JSONObject json() throws JSONException {
        JSONObject out=new JSONObject();out.put("semester",semester.toString());out.put("weeks",weeks);
        JSONArray times=new JSONArray();for(Slot s:slots){JSONObject t=new JSONObject();t.put("start",s.start);t.put("end",s.end);times.put(t);}out.put("slots",times);
        JSONArray list=new JSONArray();for(Course c:courses){JSONObject j=new JSONObject();j.put("id",c.id);j.put("name",c.name);j.put("room",c.room);j.put("teacher",c.teacher);j.put("day",c.day);j.put("first",c.first);j.put("last",c.last);j.put("weekStart",c.weekStart);j.put("weekEnd",c.weekEnd);j.put("parity",c.parity);j.put("color",c.color);j.put("customWeeks",new JSONArray(c.customWeeks));list.put(j);}out.put("courses",list);return out;
    }
    public static ScheduleModel parse(String value) throws Exception {
        ScheduleModel m=new ScheduleModel();JSONObject j=new JSONObject(value);m.semester=LocalDate.parse(j.getString("semester"));m.weeks=j.getInt("weeks");
        if(m.semester.getDayOfWeek()!=DayOfWeek.MONDAY||m.weeks<1||m.weeks>60)throw new JSONException("Invalid semester");
        JSONArray slots=j.getJSONArray("slots");if(slots.length()<1||slots.length()>20)throw new JSONException("Invalid periods");
        m.slots.clear();for(int i=0;i<slots.length();i++){JSONObject s=slots.getJSONObject(i);String a=s.getString("start"),b=s.getString("end");if(m.validateSlot(i,a,b)!=null)throw new JSONException("Overlapping times");m.slots.add(new Slot(a,b));}
        JSONArray list=j.getJSONArray("courses");if(list.length()>300)throw new JSONException("Too many courses");
        for(int i=0;i<list.length();i++){JSONObject c=list.getJSONObject(i);Course x=new Course();x.id=c.getString("id");x.name=c.getString("name");x.room=c.optString("room");x.teacher=c.optString("teacher");x.day=c.getInt("day");x.first=c.getInt("first");x.last=c.getInt("last");x.weekStart=c.getInt("weekStart");x.weekEnd=c.getInt("weekEnd");x.parity=c.optInt("parity");JSONArray custom=c.optJSONArray("customWeeks");if(custom!=null)for(int w=0;w<custom.length();w++)x.customWeeks.add(custom.getInt(w));x.color=c.optInt("color");if(m.validateCourse(x)!=null)throw new JSONException("Invalid course");m.put(x);}
        return m;
    }
}
