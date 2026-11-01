package org.simpleweather.app;

import org.json.*;
import java.time.*;
import java.util.*;

/** Local civil-date tasks. Notification delivery state is deliberately separate. */
final class TodoModel {
    static final int MAX_ITEMS=2000;
    final List<Item> items=new ArrayList<>();
    static final class Slot {int from,to;Slot(int from,int to){this.from=from;this.to=to;}}
    static final class Part {String text;boolean done;Part(String text,boolean done){this.text=text;this.done=done;}}
    static final class Item {
        String id=UUID.randomUUID().toString(),title="",note="",category="",completedThrough="";
        LocalDate date=LocalDate.now(),until=date;
        boolean allDay,done,pin,remindEnd;
        int priority=1,color=0xff246bce,repeat,weekMask,advance=0;
        long created=System.currentTimeMillis(),updated=created;
        final List<Slot> slots=new ArrayList<>();final List<Part> parts=new ArrayList<>();
    }
    static final class Occurrence {
        final Item item;final LocalDate day;final long start,end;
        Occurrence(Item item,LocalDate day,ZoneId zone){this.item=item;this.day=day;start=at(day,item.allDay?0:item.slots.get(0).from,zone);end=item.allDay?at(item.repeat==0?item.until.plusDays(1):day.plusDays(1),0,zone):at(day,item.slots.get(item.slots.size()-1).to,zone);}
    }
    static final class Event {
        final Item item;final LocalDate day;final long when;final boolean end;final String key;
        Event(Item item,LocalDate day,int slot,long when,boolean end){this.item=item;this.day=day;this.when=when;this.end=end;key=item.id+":"+item.updated+":"+day+":"+slot+":"+end;}
    }
    Item find(String id){for(Item item:items)if(item.id.equals(id))return item;return null;}
    static long at(LocalDate day,int minute,ZoneId zone){return day.atStartOfDay().plusMinutes(minute).atZone(zone).toInstant().toEpochMilli();}
    static boolean occurs(Item i,LocalDate day){
        if(day.isBefore(i.date)||i.done)return false;
        if(!i.completedThrough.isEmpty()&&!day.isAfter(LocalDate.parse(i.completedThrough)))return false;
        if(i.repeat==0)return day.equals(i.date);
        if(i.repeat==1)return true;
        if(i.repeat==2)return day.getDayOfWeek()==i.date.getDayOfWeek();
        if(i.repeat==3)return day.getDayOfWeek().getValue()<=5;
        return (i.weekMask&(1<<(day.getDayOfWeek().getValue()-1)))!=0;
    }
    static Occurrence next(Item item,long now,ZoneId zone){
        if(item.done)return null;
        if(item.repeat==0)return new Occurrence(item,item.date,zone);
        LocalDate day=Instant.ofEpochMilli(now).atZone(zone).toLocalDate();if(day.isBefore(item.date))day=item.date;
        if(!item.completedThrough.isEmpty()&&!day.isAfter(LocalDate.parse(item.completedThrough)))day=LocalDate.parse(item.completedThrough).plusDays(1);
        for(int n=0;n<8;n++,day=day.plusDays(1))if(occurs(item,day))return new Occurrence(item,day,zone);
        return null;
    }
    static void complete(Item item,LocalDate day){if(item.repeat==0)item.done=true;else if(occurs(item,day)){item.completedThrough=day.toString();for(Part p:item.parts)p.done=false;}}
    static List<Event> events(Item i,long now,ZoneId zone,int allDayMinute,long notBefore){
        List<Event> result=new ArrayList<>();if(i.done||i.advance<0)return result;
        LocalDate first=Instant.ofEpochMilli(now).atZone(zone).toLocalDate().minusDays(1);
        if(i.repeat==0)first=i.date;
        long cutoff=Math.max(i.updated,notBefore);
        for(int n=0;n<(i.repeat==0?1:16);n++){
            LocalDate day=first.plusDays(n);if(!occurs(i,day))continue;
            int size=i.allDay?1:i.slots.size();
            for(int s=0;s<size;s++){
                long start=at(day,i.allDay?allDayMinute:i.slots.get(s).from,zone);
                long remind=start-i.advance*60000L;if(remind<cutoff&&start>=cutoff)remind=start;
                if(remind>=cutoff&&remind>=now-86400000L)result.add(new Event(i,day,s,remind,false));
                if(i.remindEnd){long end=i.allDay?at(i.repeat==0?i.until.plusDays(1):day.plusDays(1),0,zone):at(day,i.slots.get(s).to,zone);if(end>=cutoff&&end>=now-86400000L)result.add(new Event(i,day,s,end,true));}
            }
        }
        return result;
    }
    static int minute(String text){if(text==null||!text.trim().matches("[0-9]{1,2}:[0-9]{2}"))throw new IllegalArgumentException();String[] p=text.trim().split(":");int h=Integer.parseInt(p[0]),m=Integer.parseInt(p[1]);if(h>23||m>59)throw new IllegalArgumentException();return h*60+m;}
    static String clock(int minute){return String.format(Locale.ROOT,"%02d:%02d",minute/60,minute%60);}
    static void validate(Item i){
        if(i.id.isEmpty()||i.id.length()>80||i.title.trim().isEmpty()||i.title.length()>160||i.note.length()>4000||i.category.length()>40||i.priority<0||i.priority>2||(i.color>>>24)!=255||i.repeat<0||i.repeat>4||i.weekMask<0||i.weekMask>127||i.created<0||i.updated<i.created)throw new IllegalArgumentException();
        if(i.date.getYear()<1970||i.date.getYear()>2200||i.until.getYear()>2200||i.until.isBefore(i.date)||i.until.toEpochDay()-i.date.toEpochDay()>3660)throw new IllegalArgumentException();
        if(i.repeat!=0&&i.allDay&&!i.until.equals(i.date))throw new IllegalArgumentException();
        if(i.repeat==4&&i.weekMask==0)throw new IllegalArgumentException();
        if(!Arrays.asList(-1,0,5,10,15,30,60,1440).contains(i.advance))throw new IllegalArgumentException();
        if(!i.completedThrough.isEmpty()){LocalDate d=LocalDate.parse(i.completedThrough);if(d.isBefore(i.date)||d.getYear()>2200)throw new IllegalArgumentException();}
        if(!i.allDay){if(i.slots.isEmpty()||i.slots.size()>12)throw new IllegalArgumentException();i.slots.sort(Comparator.comparingInt(s->s.from));int previous=-1;for(Slot s:i.slots){if(s.from<0||s.to>1440||s.to<=s.from||s.from<previous)throw new IllegalArgumentException();previous=s.to;}}
        if(i.parts.size()>50)throw new IllegalArgumentException();for(Part p:i.parts)if(p.text.trim().isEmpty()||p.text.length()>200)throw new IllegalArgumentException();
    }
    JSONObject json()throws JSONException{JSONArray rows=new JSONArray();for(Item i:items)rows.put(encode(i));return new JSONObject().put("version",1).put("items",rows);}
    static JSONObject encode(Item i)throws JSONException{
        JSONArray slots=new JSONArray(),parts=new JSONArray();for(Slot s:i.slots)slots.put(new JSONArray().put(s.from).put(s.to));for(Part p:i.parts)parts.put(new JSONObject().put("text",p.text).put("done",p.done));
        return new JSONObject().put("id",i.id).put("title",i.title).put("note",i.note).put("category",i.category).put("date",i.date.toString()).put("until",i.until.toString()).put("allDay",i.allDay).put("done",i.done).put("pin",i.pin).put("remindEnd",i.remindEnd).put("priority",i.priority).put("color",i.color).put("repeat",i.repeat).put("weekMask",i.weekMask).put("advance",i.advance).put("created",i.created).put("updated",i.updated).put("completedThrough",i.completedThrough).put("slots",slots).put("parts",parts);
    }
    static TodoModel parse(String raw)throws JSONException{
        try{if(raw==null||raw.length()>16*1024*1024)throw new IllegalArgumentException();
            boolean quote=false,escape=false;int depth=0;for(int n=0;n<raw.length();n++){char c=raw.charAt(n);if(quote){if(escape)escape=false;else if(c=='\\')escape=true;else if(c=='"')quote=false;}else if(c=='"')quote=true;else if(c=='{'||c=='['){if(++depth>8)throw new IllegalArgumentException();}else if(c=='}'||c==']'){if(--depth<0)throw new IllegalArgumentException();}}if(depth!=0||quote)throw new IllegalArgumentException();
            JSONTokener tokener=new JSONTokener(raw);Object value=tokener.nextValue();if(!(value instanceof JSONObject)||tokener.nextClean()!=0)throw new IllegalArgumentException();JSONObject root=(JSONObject)value;
            if(number(root,"version")!=1)throw new IllegalArgumentException();JSONArray rows=root.getJSONArray("items");if(rows.length()>MAX_ITEMS)throw new IllegalArgumentException();TodoModel model=new TodoModel();Set<String> ids=new HashSet<>();
            for(int n=0;n<rows.length();n++){JSONObject j=rows.getJSONObject(n);Item i=new Item();i.id=string(j,"id");i.title=string(j,"title");i.note=string(j,"note");i.category=string(j,"category");i.date=LocalDate.parse(string(j,"date"));i.until=LocalDate.parse(string(j,"until"));i.completedThrough=string(j,"completedThrough");
                i.allDay=bool(j,"allDay");i.done=bool(j,"done");i.pin=bool(j,"pin");i.remindEnd=bool(j,"remindEnd");i.priority=integer(j,"priority");i.color=integer(j,"color");i.repeat=integer(j,"repeat");i.weekMask=integer(j,"weekMask");i.advance=integer(j,"advance");i.created=number(j,"created");i.updated=number(j,"updated");
                JSONArray slots=j.getJSONArray("slots"),parts=j.getJSONArray("parts");if(slots.length()>12||parts.length()>50)throw new IllegalArgumentException();for(int s=0;s<slots.length();s++){JSONArray slot=slots.getJSONArray(s);if(slot.length()!=2)throw new IllegalArgumentException();i.slots.add(new Slot(integer(slot.get(0)),integer(slot.get(1))));}for(int p=0;p<parts.length();p++){JSONObject part=parts.getJSONObject(p);i.parts.add(new Part(string(part,"text"),bool(part,"done")));}
                validate(i);if(!ids.add(i.id))throw new IllegalArgumentException();model.items.add(i);
            }return model;
        }catch(Exception e){throw new JSONException("Invalid todo document");}
    }
    private static String string(JSONObject j,String key)throws JSONException{Object v=j.get(key);if(!(v instanceof String))throw new JSONException("string");return (String)v;}
    private static boolean bool(JSONObject j,String key)throws JSONException{Object v=j.get(key);if(!(v instanceof Boolean))throw new JSONException("boolean");return (Boolean)v;}
    private static long number(JSONObject j,String key)throws JSONException{Object v=j.get(key);if(!(v instanceof Number))throw new JSONException("number");double d=((Number)v).doubleValue();if(!Double.isFinite(d)||d!=Math.rint(d)||d<Long.MIN_VALUE||d>=0x1.0p63)throw new JSONException("integer");return ((Number)v).longValue();}
    private static int integer(JSONObject j,String key)throws JSONException{return integer(j.get(key));}
    private static int integer(Object value)throws JSONException{if(!(value instanceof Number))throw new JSONException("integer");double d=((Number)value).doubleValue();if(!Double.isFinite(d)||d!=Math.rint(d)||d<Integer.MIN_VALUE||d>Integer.MAX_VALUE)throw new JSONException("integer");return ((Number)value).intValue();}
}
