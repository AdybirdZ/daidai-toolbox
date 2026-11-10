package org.daidai.modules.music;

import java.io.ByteArrayOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Immutable timed lyrics and four-cue presentation; independent of Android or a host. */
public final class MusicLyrics {
    public static final int MAX_BYTES=2*1024*1024;
    private static final int MAX_CUES=50000;
    private static final Pattern TIME=Pattern.compile("\\[(\\d{1,9}):([0-5]\\d)(?:\\.(\\d{1,3}))?\\]");
    private static final Pattern OFFSET=Pattern.compile("\\[offset:\\s*([+-]?\\d{1,10})\\s*\\]",Pattern.CASE_INSENSITIVE);
    public static final MusicLyrics EMPTY=new MusicLyrics(Collections.emptyList());
    public static final class Cue {
        public final long millis;public final String text;
        private Cue(long time,String value){millis=time;text=value;}
    }
    public static final class Window {
        public final int currentIndex;
        public final String previous,current,next,nextAfter;
        private Window(int index,String a,String b,String c,String d){currentIndex=index;previous=a;current=b;next=c;nextAfter=d;}
    }
    private final List<Cue> cues;
    private MusicLyrics(List<Cue> values){cues=Collections.unmodifiableList(values);}
    public int size(){return cues.size();}
    public List<Cue> cues(){return cues;}
    public Window at(long positionMillis){
        int low=0,high=cues.size();while(low<high){int mid=(low+high)>>>1;if(cues.get(mid).millis<=positionMillis)low=mid+1;else high=mid;}
        int current=low-1;return new Window(current,text(current-1),text(current),text(current+1),text(current+2));
    }
    private String text(int index){return index>=0&&index<cues.size()?cues.get(index).text:"";}
    /** Empty timed cues are ignored so a separator does not erase the last lyric. */
    public static MusicLyrics parse(String source)throws IOException{
        if(source==null||source.isEmpty())return EMPTY;
        if(source.length()>MAX_BYTES)throw new IOException("Lyrics exceed size limit");
        if(source.charAt(0)=='\ufeff')source=source.substring(1);
        long offset=0;Matcher offsets=OFFSET.matcher(source);while(offsets.find())try{offset=Long.parseLong(offsets.group(1));}catch(NumberFormatException ignored){}
        TreeMap<Long,LinkedHashSet<String>> lines=new TreeMap<>();int expanded=0;BufferedReader reader=new BufferedReader(new StringReader(source));String line;
        while((line=reader.readLine())!=null){
            if(Thread.currentThread().isInterrupted())throw new IOException("Lyrics read interrupted");
            Matcher stamps=TIME.matcher(line);ArrayList<Long> times=new ArrayList<>();int end=0;
            while(stamps.find()){
                long time=(Long.parseLong(stamps.group(1))*60+Long.parseLong(stamps.group(2)))*1000;
                String fraction=stamps.group(3);if(fraction!=null)time+=Integer.parseInt(fraction)*(fraction.length()==1?100:fraction.length()==2?10:1);
                times.add(Math.max(0,time+offset));end=stamps.end();
            }
            if(times.isEmpty())continue;String value=line.substring(end).trim();if(value.isEmpty())continue;
            for(long time:times){LinkedHashSet<String> content=lines.get(time);if(content==null){content=new LinkedHashSet<>();lines.put(time,content);}if(content.add(value)){expanded+=value.length();if(expanded>MAX_BYTES*4)throw new IOException("Expanded lyrics exceed size limit");}}
            if(lines.size()>MAX_CUES)throw new IOException("Too many lyric cues");
        }
        if(lines.isEmpty())return EMPTY;ArrayList<Cue> result=new ArrayList<>();for(Map.Entry<Long,LinkedHashSet<String>> entry:lines.entrySet()){String value;if(entry.getValue().size()==1)value=entry.getValue().iterator().next();else{StringBuilder joined=new StringBuilder();for(String part:entry.getValue()){if(joined.length()>0)joined.append('\n');joined.append(part);}value=joined.toString();}result.add(new Cue(entry.getKey(),value));}return new MusicLyrics(result);
    }
    public static MusicLyrics read(InputStream input)throws IOException{
        if(input==null)throw new IOException("Lyrics source unavailable");ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int count;
        while((count=input.read(buffer))!=-1){if(Thread.currentThread().isInterrupted())throw new IOException("Lyrics read interrupted");if(bytes.size()>MAX_BYTES-count)throw new IOException("Lyrics exceed size limit");bytes.write(buffer,0,count);}
        byte[] raw=bytes.toByteArray();String source;
        if(raw.length>=2&&(raw[0]&255)==255&&(raw[1]&255)==254)source=new String(raw,2,raw.length-2,StandardCharsets.UTF_16LE);
        else if(raw.length>=2&&(raw[0]&255)==254&&(raw[1]&255)==255)source=new String(raw,2,raw.length-2,StandardCharsets.UTF_16BE);
        else try{source=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(raw)).toString();}
        catch(CharacterCodingException e){source=new String(raw,Charset.forName("GB18030"));}
        return parse(source);
    }
}
