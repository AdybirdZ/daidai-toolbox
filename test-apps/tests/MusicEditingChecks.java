import org.daidai.modules.music.AudioEditPlan;
import java.util.Arrays;

public final class MusicEditingChecks {
    static int count;
    static void check(boolean ok,String reason){count++;if(!ok)throw new AssertionError(reason);}
    static boolean ranges(AudioEditPlan p,long[][] expected){return Arrays.deepEquals(p.ranges(),expected);}
    public static void main(String[] args){
        AudioEditPlan p=new AudioEditPlan(1000);
        p.apply(200,400,false);p.apply(600,800,false);
        check(ranges(p,new long[][]{{0,200},{400,600},{800,1000}}),"separate cuts preserve remaining timeline");
        check(p.durationUs()==600,"joined length");
        p.apply(100,900,true);
        check(ranges(p,new long[][]{{100,200},{400,600},{800,900}}),"keep intersects previous removals");
        long[][] external=p.ranges();external[0][0]=0;
        check(p.ranges()[0][0]==100,"ranges isolated from caller mutation");
        check(p.undo()&&p.operations()==2,"undo only last operation");
        check(ranges(p,new long[][]{{0,200},{400,600},{800,1000}}),"undo restores split segments");
        boolean rejected=false;try{p.apply(0,1000,false);}catch(IllegalArgumentException e){rejected=true;}
        check(rejected&&p.durationUs()==600&&p.operations()==2,"invalid empty result preserves edit history");
        rejected=false;try{p.apply(400,400,true);}catch(IllegalArgumentException e){rejected=true;}
        check(rejected,"zero duration rejected");p.reset();
        check(p.operations()==0&&!p.undo()&&ranges(p,new long[][]{{0,1000}}),"reset clears history");
        p.apply(0,300,false);p.apply(800,1000,false);
        check(ranges(p,new long[][]{{300,800}}),"head and tail removal");
        System.out.println("PASS: "+count+" audio edit plan checks");
    }
}
