package org.daidai.modules.music;

import java.util.*;

/** Individually edited style tags preserve punctuation; legacy text accepts common separators. */
public final class MusicTags {
    private static final String PREFIX="\u001eDDMT1:";
    private MusicTags(){}
    public static ArrayList<String> split(String raw){
        if(raw!=null&&raw.startsWith(PREFIX)){ArrayList<String> decoded=decode(raw);if(decoded!=null)return decoded;}
        LinkedHashSet<String> tags=new LinkedHashSet<>();if(raw!=null)for(String value:raw.split("[;；,，、\\r\\n]+")){String tag=value.trim();if(!tag.isEmpty())tags.add(tag);}return new ArrayList<>(tags);
    }
    private static ArrayList<String> decode(String raw){
        LinkedHashSet<String> tags=new LinkedHashSet<>();int at=PREFIX.length();
        while(at<raw.length()){int colon=raw.indexOf(':',at);if(colon<=at||colon-at>9)return null;for(int i=at;i<colon;i++)if(raw.charAt(i)<'0'||raw.charAt(i)>'9')return null;int size;try{size=Integer.parseInt(raw.substring(at,colon));}catch(NumberFormatException invalid){return null;}at=colon+1;if(size<=0||size>raw.length()-at)return null;String tag=raw.substring(at,at+size).trim();if(!tag.isEmpty())tags.add(tag);at+=size;}return new ArrayList<>(tags);
    }
    public static String join(Collection<String> tags){StringBuilder value=new StringBuilder();LinkedHashSet<String> seen=new LinkedHashSet<>();for(String raw:tags){String tag=raw==null?"":raw.trim();if(!tag.isEmpty()&&seen.add(tag)){if(value.length()==0)value.append(PREFIX);value.append(tag.length()).append(':').append(tag);}}return value.toString();}
    public static String display(String raw,String separator){StringBuilder value=new StringBuilder();for(String tag:split(raw)){if(value.length()>0)value.append(separator);value.append(tag);}return value.toString();}
}
