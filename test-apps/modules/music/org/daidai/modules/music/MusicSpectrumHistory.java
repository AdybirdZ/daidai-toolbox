package org.daidai.modules.music;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Bounded source-audio timeline. Each FFT frame retains its actual sample position. */
public final class MusicSpectrumHistory {
    public static final int MIN_DURATION_MS=500,MAX_DURATION_MS=5000,DEFAULT_DURATION_MS=1000,STEP_MS=10;
    private static final int MAX_FRAMES=MAX_DURATION_MS/STEP_MS+4;
    public static final class Entry {
        public final long positionMillis;public final MusicSpectrum.Frame frame;
        Entry(long at,MusicSpectrum.Frame value){positionMillis=at;frame=value;}
    }
    private final ArrayDeque<Entry> entries=new ArrayDeque<>();
    private long position=-1;
    private int duration=DEFAULT_DURATION_MS;
    public int durationMillis(){return duration;}
    public long positionMillis(){return position;}
    public int size(){return entries.size();}
    public void setDurationMillis(int value){duration=Math.max(MIN_DURATION_MS,Math.min(MAX_DURATION_MS,value));}
    public void clear(){entries.clear();position=-1;}
    /** Backward source movement starts a new timeline; repeated positions replace, never age history. */
    public void append(long at,MusicSpectrum.Frame frame){
        if(frame==null||at<0)return;
        if(position>=0&&at<position)entries.clear();
        if(!entries.isEmpty()&&entries.peekFirst().positionMillis==at)entries.removeFirst();
        entries.addFirst(new Entry(at,frame));position=at;
        while(entries.size()>MAX_FRAMES||!entries.isEmpty()&&at-entries.peekLast().positionMillis>MAX_DURATION_MS+STEP_MS)entries.removeLast();
    }
    public List<Entry> entries(){return new ArrayList<>(entries);}
    /** A row samples a real nearby FFT frame; gaps are empty, never stretched into invented audio. */
    public MusicSpectrum.Frame frameAtAge(long ageMillis){
        if(position<0||ageMillis<0||ageMillis>duration)return null;long target=position-ageMillis;
        Entry nearest=null;long distance=Long.MAX_VALUE;
        for(Entry e:entries){long delta=Math.abs(e.positionMillis-target);if(delta<distance){nearest=e;distance=delta;}if(e.positionMillis<target-STEP_MS)break;}
        return distance<=STEP_MS&&nearest!=null?nearest.frame:null;
    }
    /** Ordered row lookup, O(number of stored frames + rows), for the newest-at-top bitmap. */
    public MusicSpectrum.Frame[] rows(int count){
        if(count<1||count>4096)throw new IllegalArgumentException("Invalid history row count");
        MusicSpectrum.Frame[] out=new MusicSpectrum.Frame[count];if(entries.isEmpty())return out;
        List<Entry> list=entries();int cursor=0;
        for(int row=0;row<count;row++){
            long target=position-Math.round(duration*row/(double)Math.max(1,count-1));
            while(cursor+1<list.size()&&list.get(cursor+1).positionMillis>=target)cursor++;
            Entry nearest=list.get(cursor);long delta=Math.abs(nearest.positionMillis-target);
            if(cursor+1<list.size()){Entry next=list.get(cursor+1);long gap=Math.abs(next.positionMillis-target);if(gap<delta){nearest=next;delta=gap;}}
            if(delta<=STEP_MS)out[row]=nearest.frame;
        }
        return out;
    }
}
