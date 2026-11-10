import java.util.*;
import org.daidai.modules.music.MusicTags;

/** Portable checks for old tag text and lossless individually edited genre tags. */
public final class MusicTagsChecks {
    private static int checks;
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    public static void main(String[] args){
        check(MusicTags.split(null).isEmpty(),"Null legacy tags are empty");
        check(MusicTags.split(" 电子；摇滚、电子, Jazz\nAmbient ").equals(Arrays.asList("电子","摇滚","Jazz","Ambient")),"Legacy delimiters preserve order and remove blanks and duplicates");
        List<String> tags=Arrays.asList("Jazz, fusion","R&B / Soul","甲；乙、丙","Name:123","\ud83c\udfb5");
        check(MusicTags.split(MusicTags.join(tags)).equals(tags),"Explicit tags preserve punctuation and Unicode through storage");
        check(MusicTags.split(MusicTags.join(Arrays.asList(" Ambient ","Ambient",null,"","Pop"))).equals(Arrays.asList("Ambient","Pop")),"Individual editing normalizes only whole tags");
        check(MusicTags.join(Collections.emptyList()).isEmpty(),"An empty tag editor clears all tags");
        check(MusicTags.display(MusicTags.join(Arrays.asList("Jazz, fusion","Ambient"))," | ").equals("Jazz, fusion | Ambient"),"Display and search receive tag text without encoded lengths");
        check(MusicTags.split("\u001eDDMT1:999999999:short").equals(Collections.singletonList("DDMT1:999999999:short")),"Malformed legacy prefix falls back without large allocation");
        check(MusicTags.split(MusicTags.join(Collections.singletonList("a:0:b"))).equals(Collections.singletonList("a:0:b")),"Length-like tag content is not reinterpreted");
        System.out.println("PASS: "+checks+" music tag checks");
    }
}
