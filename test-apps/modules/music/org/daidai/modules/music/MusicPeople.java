package org.daidai.modules.music;

import java.util.*;

/** Person names are edited individually; delimiters are accepted only when reading legacy fields. */
public final class MusicPeople {
    private static final String PREFIX="\u001eDDMP1:";
    private MusicPeople(){}
    public static ArrayList<String> split(String raw){
        if(raw!=null&&raw.startsWith(PREFIX)){ArrayList<String> decoded=decode(raw);if(decoded!=null)return decoded;}
        LinkedHashSet<String> names=new LinkedHashSet<>();if(raw!=null)for(String value:raw.split("[;；,，、\\r\\n]+")){String name=value.trim();if(!name.isEmpty())names.add(name);}return new ArrayList<>(names);
    }
    private static ArrayList<String> decode(String raw){
        LinkedHashSet<String> names=new LinkedHashSet<>();int at=PREFIX.length();
        while(at<raw.length()){int colon=raw.indexOf(':',at);if(colon<=at||colon-at>9)return null;for(int i=at;i<colon;i++)if(raw.charAt(i)<'0'||raw.charAt(i)>'9')return null;int size;try{size=Integer.parseInt(raw.substring(at,colon));}catch(NumberFormatException invalid){return null;}at=colon+1;if(size<=0||size>raw.length()-at)return null;String name=raw.substring(at,at+size).trim();if(!name.isEmpty())names.add(name);at+=size;}
        return new ArrayList<>(names);
    }
    /** Length-prefixed fields preserve punctuation inside each newly edited name. */
    public static String join(Collection<String> names){StringBuilder value=new StringBuilder();LinkedHashSet<String> seen=new LinkedHashSet<>();for(String raw:names){String name=raw==null?"":raw.trim();if(!name.isEmpty()&&seen.add(name)){if(value.length()==0)value.append(PREFIX);value.append(name.length()).append(':').append(name);}}return value.toString();}
    public static String display(String raw,String separator){StringBuilder value=new StringBuilder();for(String name:split(raw)){if(value.length()>0)value.append(separator);value.append(name);}return value.toString();}
}
