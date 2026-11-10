import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.daidai.modules.music.MusicLyrics;

public final class MusicLyricsChecks {
    private static int checks;
    private static void check(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
    public static void main(String[] args)throws Exception{
        MusicLyrics lyrics=MusicLyrics.parse("[ar:Example]\n[00:01.2] First\n[00:02.34]Second\n[00:03.456]Third\n[00:04]Fourth\n[00:05]Fifth\n");
        check(lyrics.size()==5,"metadata is not a lyric cue");
        check(lyrics.cues().get(0).millis==1200&&lyrics.cues().get(1).millis==2340&&lyrics.cues().get(2).millis==3456,"timestamp fractional precision");
        MusicLyrics.Window before=lyrics.at(0);check(before.currentIndex==-1&&before.previous.isEmpty()&&before.current.isEmpty()&&before.next.equals("First")&&before.nextAfter.equals("Second"),"before first cue shows only the next two cues");
        MusicLyrics.Window first=lyrics.at(1200);check(first.currentIndex==0&&first.previous.isEmpty()&&first.current.equals("First")&&first.next.equals("Second")&&first.nextAfter.equals("Third"),"inclusive first timestamp");
        MusicLyrics.Window middle=lyrics.at(3999);check(middle.previous.equals("Second")&&middle.current.equals("Third")&&middle.next.equals("Fourth")&&middle.nextAfter.equals("Fifth"),"previous/current/two upcoming cues");
        check(lyrics.at(2340).current.equals("Second")&&lyrics.at(2339).current.equals("First"),"seek boundary does not activate future lyrics");
        MusicLyrics.Window end=lyrics.at(Long.MAX_VALUE);check(end.previous.equals("Fourth")&&end.current.equals("Fifth")&&end.next.isEmpty()&&end.nextAfter.isEmpty(),"last cue stays visible");
        check(lyrics.at(1500).current.equals("First"),"backward seek locates correct cue");
        MusicLyrics blanks=MusicLyrics.parse("[00:01]First\n[00:02]  \n\n[00:03]Third");check(blanks.size()==2&&blanks.at(2500).current.equals("First")&&blanks.at(3000).previous.equals("First"),"blank separators preserve previous lyrics");
        MusicLyrics duplicates=MusicLyrics.parse("[00:02]Original\n[00:02]Translation\n[00:02]Original\n[00:01][00:03.1]Repeated");check(duplicates.size()==3&&duplicates.at(2000).current.equals("Original\nTranslation"),"same-time languages merge without duplicate text");check(duplicates.at(1000).current.equals("Repeated")&&duplicates.at(3100).current.equals("Repeated"),"multiple time tags create individual cues");
        check(MusicLyrics.parse("[offset:250]\n[00:01]Shift").at(1249).current.isEmpty()&&MusicLyrics.parse("[offset:250]\n[00:01]Shift").at(1250).current.equals("Shift"),"positive LRC offset delays a cue");
        check(MusicLyrics.parse("[00:01]Shift\n[offset:-2000]").at(0).current.equals("Shift"),"global negative offset clamps to start");
        check(MusicLyrics.parse("[offset:50]\n[offset:100]\n[00:01]Last offset").cues().get(0).millis==1100,"last offset applies regardless of metadata order");
        check(MusicLyrics.parse("[00:60]invalid\n[00:99]invalid\nuntimed line\n[al:album]").size()==0,"invalid timestamps and untimed metadata ignored");
        check(MusicLyrics.parse("\ufeff[00:01]BOM\r[00:02]CR\r\n[00:03]CRLF").size()==3,"BOM and common line endings");
        check(MusicLyrics.parse(null)==MusicLyrics.EMPTY&&MusicLyrics.parse("")==MusicLyrics.EMPTY,"empty input");
        String chinese="[00:01]中文歌词";
        check(MusicLyrics.read(new ByteArrayInputStream(chinese.getBytes(StandardCharsets.UTF_8))).at(1000).current.equals("中文歌词"),"UTF-8 LRC");
        check(MusicLyrics.read(new ByteArrayInputStream(chinese.getBytes(Charset.forName("GB18030")))).at(1000).current.equals("中文歌词"),"legacy Chinese encoding fallback");
        byte[] utf16=chinese.getBytes(StandardCharsets.UTF_16LE),bom=new byte[utf16.length+2];bom[0]=(byte)255;bom[1]=(byte)254;System.arraycopy(utf16,0,bom,2,utf16.length);check(MusicLyrics.read(new ByteArrayInputStream(bom)).at(1000).current.equals("中文歌词"),"UTF-16LE BOM");
        boolean immutable=false;try{lyrics.cues().clear();}catch(UnsupportedOperationException expected){immutable=true;}check(immutable,"timeline immutable");
        boolean large=false;try{MusicLyrics.read(new ByteArrayInputStream(new byte[MusicLyrics.MAX_BYTES+1]));}catch(IOException expected){large=true;}check(large,"oversized file rejected before parsing");
        StringBuilder many=new StringBuilder();for(int i=0;i<10000;i++)many.append('[').append(i/60).append(':').append(String.format(java.util.Locale.ROOT,"%02d",i%60)).append("]Cue ").append(i).append('\n');MusicLyrics timeline=MusicLyrics.parse(many.toString());for(int i=0;i<10000;i+=37)check(timeline.at(i*1000L).current.equals("Cue "+i),"binary seek "+i);
        System.out.println("PASS: "+checks+" lyric timeline/encoding checks");
    }
}
