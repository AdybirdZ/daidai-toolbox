package org.simpleweather.app;

import java.util.*;

/** Stable IDs let display preferences survive renaming and future additions. */
public final class DisplayOrder {
    public static List<Integer> parse(String saved,int count){
        List<Integer> order=new ArrayList<>();
        for(String token:saved.split(","))try{int id=Integer.parseInt(token.trim());if(id>=0&&id<count&&!order.contains(id))order.add(id);}catch(Exception ignored){}
        for(int id=0;id<count;id++)if(!order.contains(id))order.add(id);
        return order;
    }
    public static String encode(List<Integer> order){StringJoiner result=new StringJoiner(",");for(int id:order)result.add(String.valueOf(id));return result.toString();}
    public static List<Integer> visible(List<Integer> order,Set<Integer> hidden){List<Integer> result=new ArrayList<>();for(int id:order)if(!hidden.contains(id))result.add(id);return result;}
    public static List<Integer> window(List<Integer> visible,int offset){if(visible.size()<5)return new ArrayList<>(visible);int start=Math.max(0,Math.min(offset,visible.size()-3));return new ArrayList<>(visible.subList(start,start+3));}
}
