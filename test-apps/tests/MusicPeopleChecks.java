import java.util.Arrays;
import java.util.Collections;
import org.daidai.modules.music.MusicPeople;

/** Portable person-field parsing checks, independent of Android catalogue storage. */
public final class MusicPeopleChecks {
    private static int checks;
    private static void check(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
    public static void main(String[] args){
        check(MusicPeople.split(null).isEmpty(),"Null legacy field is empty");
        check(MusicPeople.split(" 甲；乙、甲, 丙\n丁 ").equals(Arrays.asList("甲","乙","丙","丁")),"Legacy separators trim and deduplicate in order");
        check(MusicPeople.split("Simon & Garfunkel; AC/DC").equals(Arrays.asList("Simon & Garfunkel","AC/DC")),"Ampersands and slashes remain inside names");
        check(MusicPeople.split(MusicPeople.join(Arrays.asList(" 甲 ","乙","甲","",null))).equals(Arrays.asList("甲","乙")),"Edited fields normalize blanks and duplicates");
        check(MusicPeople.join(Collections.emptyList()).isEmpty(),"Empty people collection stays empty");
        check(MusicPeople.display("甲;乙;甲","、").equals("甲、乙"),"Localized display has no duplicate person");
        check(MusicPeople.split(MusicPeople.join(Arrays.asList("Alice","小明"))).equals(Arrays.asList("Alice","小明")),"Mixed-language people round trip");
        java.util.List<String> punctuation=Arrays.asList("Earth, Wind & Fire","Last, First","甲；乙、丙","AC/DC","名字:编号1","\ud83c\udfb5 Artist");
        check(MusicPeople.split(MusicPeople.join(punctuation)).equals(punctuation),"Individual names preserve punctuation and UTF-16 pairs without re-splitting");
        check(MusicPeople.display(MusicPeople.join(Arrays.asList("Earth, Wind & Fire","Last, First")),"、").equals("Earth, Wind & Fire、Last, First"),"Encoded names display without storage prefix");
        check(MusicPeople.split(MusicPeople.join(Arrays.asList("\u001eDDMP1:3:ABC","Alice"))).equals(Arrays.asList("DDMP1:3:ABC","Alice")),"Reserved-looking name content is preserved after documented trim");
        check(MusicPeople.split("\u001eDDMP1:999999999:short").equals(Collections.singletonList("DDMP1:999999999:short")),"Malformed length field falls back safely without allocation");
        System.out.println("PASS: "+checks+" music people checks");
    }
}
