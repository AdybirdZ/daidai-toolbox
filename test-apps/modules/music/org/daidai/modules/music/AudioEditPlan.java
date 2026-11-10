package org.daidai.modules.music;

import java.util.*;

/** Non-destructive edits expressed in the original audio timeline, in microseconds. */
public final class AudioEditPlan {
    private final long duration;
    private ArrayList<long[]> ranges=new ArrayList<>();
    private final ArrayList<ArrayList<long[]>> history=new ArrayList<>();
    public AudioEditPlan(long durationUs){if(durationUs<=0)throw new IllegalArgumentException("Invalid duration");duration=durationUs;ranges.add(new long[]{0,duration});}
    public void apply(long from,long to,boolean keep){
        if(from<0||to<=from||to>duration)throw new IllegalArgumentException("Invalid interval");
        ArrayList<long[]> next=new ArrayList<>();
        for(long[] r:ranges){
            if(keep){long a=Math.max(from,r[0]),b=Math.min(to,r[1]);if(b>a)next.add(new long[]{a,b});}
            else {if(r[0]<from)next.add(new long[]{r[0],Math.min(from,r[1])});if(r[1]>to)next.add(new long[]{Math.max(to,r[0]),r[1]});}
        }
        if(next.isEmpty())throw new IllegalArgumentException("No audio remains");
        history.add(copy(ranges));ranges=next;
    }
    public boolean undo(){if(history.isEmpty())return false;ranges=history.remove(history.size()-1);return true;}
    public void reset(){history.clear();ranges.clear();ranges.add(new long[]{0,duration});}
    public int operations(){return history.size();}
    public long durationUs(){long sum=0;for(long[] r:ranges)sum+=r[1]-r[0];return sum;}
    public long[][] ranges(){return copy(ranges).toArray(new long[0][]);}
    private static ArrayList<long[]> copy(List<long[]> values){ArrayList<long[]> result=new ArrayList<>();for(long[] r:values)result.add(r.clone());return result;}
}
