import org.daidai.modules.music.AudioFileTags;
import java.io.*;
import java.util.*;

/** In-memory metadata fixtures; no third-party music or parser library is used. */
public final class AudioFileTagsChecks {
    static int checks;
    static final byte[] IMAGE={-119,80,78,71,13,10,26,10,1,2,3,4};
    static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    static byte[] concat(byte[]... arrays)throws Exception{ByteArrayOutputStream out=new ByteArrayOutputStream();for(byte[] a:arrays)out.write(a);return out.toByteArray();}
    static byte[] text(String s)throws Exception{return s.getBytes("UTF-8");}
    static byte[] latin(String s)throws Exception{return s.getBytes("ISO-8859-1");}
    static byte[] be(int v){return new byte[]{(byte)(v>>24),(byte)(v>>16),(byte)(v>>8),(byte)v};}
    static byte[] le(int v){return new byte[]{(byte)v,(byte)(v>>8),(byte)(v>>16),(byte)(v>>24)};}
    static byte[] sync(int v){return new byte[]{(byte)(v>>21&127),(byte)(v>>14&127),(byte)(v>>7&127),(byte)(v&127)};}
    static byte[] frame(String id,byte[] payload,int version)throws Exception{return version==2?concat(latin(id),new byte[]{(byte)(payload.length>>16),(byte)(payload.length>>8),(byte)payload.length},payload):concat(latin(id),version==4?sync(payload.length):be(payload.length),new byte[2],payload);}
    public static byte[] id3(int version)throws Exception{byte[] frames=concat(frame(version==2?"TT2":"TIT2",concat(new byte[]{3},text("甜蜜出逃")),version),frame(version==2?"TP1":"TPE1",concat(new byte[]{3},text("演唱甲; Artist B")),version),frame(version==2?"TBP":"TBPM",concat(new byte[]{3},text("129.6")),version),frame(version==2?"TCM":"TCOM",concat(new byte[]{3},text("Composer")),version),frame(version==2?"PIC":"APIC",version==2?concat(new byte[]{0},latin("PNG"),new byte[]{3,0},IMAGE):concat(new byte[]{0},latin("image/png"),new byte[]{0,3,0},IMAGE),version));return concat(latin("ID3"),new byte[]{(byte)version,0,0},sync(frames.length),frames);}
    static byte[] flacPicture()throws Exception{return concat(be(3),be(9),latin("image/png"),be(0),be(1),be(1),be(24),be(0),be(IMAGE.length),IMAGE);}
    static byte[] comments(String... values)throws Exception{ByteArrayOutputStream out=new ByteArrayOutputStream();out.write(le(4));out.write(text("test"));out.write(le(values.length));for(String v:values){byte[] b=text(v);out.write(le(b.length));out.write(b);}return out.toByteArray();}
    public static byte[] flac()throws Exception{byte[] tags=comments("TITLE=FLAC title","ARTIST=Artist 1","ARTIST=Artist 2","BPM=145","COMPOSER=Composer","ARRANGER=Arranger"),pic=flacPicture();return concat(latin("fLaC"),new byte[]{4,(byte)(tags.length>>16),(byte)(tags.length>>8),(byte)tags.length},tags,new byte[]{(byte)134,(byte)(pic.length>>16),(byte)(pic.length>>8),(byte)pic.length},pic);}
    public static byte[] opus()throws Exception{byte[] data=concat(latin("OpusTags"),comments("TITLE=Opus title","ARTIST=歌手","BPM=100","METADATA_BLOCK_PICTURE="+Base64.getEncoder().encodeToString(flacPicture())));ByteArrayOutputStream sizes=new ByteArrayOutputStream();for(int n=data.length;n>=255;n-=255)sizes.write(255);sizes.write(data.length%255);byte[] h=new byte[27];System.arraycopy(latin("OggS"),0,h,0,4);h[26]=(byte)sizes.size();return concat(h,sizes.toByteArray(),data);}
    static byte[] atom(String type,byte[] value)throws Exception{return concat(be(value.length+8),latin(type),value);}
    static byte[] mp4Value(byte[] raw)throws Exception{return atom("data",concat(be(1),be(0),raw));}
    public static byte[] mp4()throws Exception{return concat(atom("ftyp",latin("M4A ")),atom("mdat",new byte[19]),atom("moov",atom("udta",atom("meta",concat(new byte[4],atom("ilst",concat(atom("\u00a9nam",mp4Value(text("M4A title"))),atom("\u00a9ART",mp4Value(text("Singer"))),atom("\u00a9wrt",mp4Value(text("Composer"))),atom("tmpo",mp4Value(new byte[]{0,(byte)150})),atom("covr",mp4Value(IMAGE)))))))));}
    public static byte[] wave()throws Exception{byte[] tags=id3(3);return concat(latin("RIFF"),le(4+8+tags.length+(tags.length&1)),latin("WAVE"),latin("id3 "),le(tags.length),tags,new byte[tags.length&1]);}
    static AudioFileTags.Tags parse(byte[] b)throws Exception{return AudioFileTags.read(new ByteArrayInputStream(b));}
    static byte[] contributingId3()throws Exception{
        byte[] custom=concat(new byte[]{3},text("参与创作的艺术家"),new byte[]{0},text("电脑艺术家甲; 电脑艺术家乙"));
        byte[] involved=concat(new byte[]{3},text("performer"),new byte[]{0},text("TIPL 演唱者"));
        byte[] frames=concat(frame("TXXX",custom,3),frame("TIPL",involved,3));
        return concat(latin("ID3"),new byte[]{3,0,0},sync(frames.length),frames);
    }
    public static void main(String[] args)throws Exception{
        for(int v=2;v<=4;v++){AudioFileTags.Tags t=parse(id3(v));check(t.title.equals("甜蜜出逃"),"ID3 title v"+v);check(t.artist.equals("演唱甲; Artist B"),"contributing artists v"+v);check(t.roundedBpm()==130,"BPM v"+v);check(t.composer.equals("Composer"),"composer v"+v);check(Arrays.equals(t.picture,IMAGE),"embedded image v"+v);}
        AudioFileTags.Tags f=parse(flac());check(f.title.equals("FLAC title"),"FLAC title");check(f.artist.equals("Artist 1; Artist 2"),"FLAC multiple artists");check(f.roundedBpm()==145,"FLAC BPM");check(f.arranger.equals("Arranger"),"FLAC arranger");check(Arrays.equals(f.picture,IMAGE),"FLAC picture");
        AudioFileTags.Tags o=parse(opus());check(o.artist.equals("歌手")&&o.roundedBpm()==100,"Opus comments");check(Arrays.equals(o.picture,IMAGE),"Opus base64 picture");
        AudioFileTags.Tags m=parse(mp4());check(m.title.equals("M4A title")&&m.artist.equals("Singer"),"MP4 title artist");check(m.roundedBpm()==150,"MP4 integer tempo");check(Arrays.equals(m.picture,IMAGE),"MP4 embedded cover");
        AudioFileTags.Tags c=parse(contributingId3());check(c.artist.contains("电脑艺术家甲")&&c.artist.contains("电脑艺术家乙")&&c.artist.contains("TIPL 演唱者"),"contributing-artist fields map to vocalist");
        check(parse(wave()).roundedBpm()==130,"WAV ID3 metadata");
        AudioFileTags.Tags blank=parse(new byte[12]);check(blank.roundedBpm()==0&&blank.picture==null,"unavailable metadata remains blank");
        AudioFileTags.Tags invalid=new AudioFileTags.Tags();for(String raw:new String[]{"0","-1","NaN","Infinity","1001","not a number"}){invalid.bpm=raw;check(invalid.roundedBpm()==0,"invalid BPM "+raw);}
        byte[] malformed=concat(latin("ID3"),new byte[]{4,0,0},sync(0x0fffffff));check(parse(malformed).bpm.isEmpty(),"huge metadata length rejected");
        for(byte[] source:new byte[][]{id3(4),flac(),mp4(),wave(),opus()})for(int n=0;n<source.length;n+=Math.max(1,source.length/9)){parse(Arrays.copyOf(source,n));checks++;}
        byte[] h=concat(latin("ID3"),new byte[]{3,0,(byte)128},sync(7),new byte[]{(byte)255,0,(byte)224,1,2,3,4});parse(h);checks++;
        System.out.println("PASS: "+checks+" embedded audio tag checks (ID3, FLAC, Opus, MP4, WAV, malformed input)");
    }
}
