import org.daidai.modules.music.SleepTimer;

/** Portable timer state checks: no Android clock, service, or playback dependency. */
public final class MusicSleepTimerChecks {
    private static int checks;
    private static void check(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
    private static void rejects(Runnable call,String why){boolean failed=false;try{call.run();}catch(IllegalArgumentException expected){failed=true;}check(failed,why);}
    public static void main(String[] args){
        SleepTimer timer=new SleepTimer();
        check(!timer.active()&&timer.remaining(0)==0,"No timer before the user starts one");
        rejects(()->timer.start(0,true,0),"Minimum is one minute");
        rejects(()->timer.start(121,true,0),"Maximum is 120 minutes");
        rejects(()->timer.start(1,true,-1),"Clock must be monotonic and nonnegative");
        rejects(()->timer.start(1,true,Long.MAX_VALUE),"Deadline cannot overflow");
        timer.start(120,true,123);
        check(timer.deadline()==7200123&&timer.remaining(123)==7200000,"120-minute duration has no integer overflow");
        timer.start(1,true,1000);
        check(timer.due(60999,true)==SleepTimer.Decision.NONE&&timer.remaining(60999)==1,"Timer does not expire before its deadline");
        check(timer.due(61000,true)==SleepTimer.Decision.WAIT_FOR_TRACK&&timer.waitingForTrack(),"Playing song may finish at exact deadline");
        check(timer.due(62000,true)==SleepTimer.Decision.WAIT_FOR_TRACK,"Later deadline callbacks do not start a new song");
        check(timer.beforeTrackChange(62001)&&!timer.active(),"Natural completion or manual skip consumes expired timer");
        timer.start(1,false,0);
        check(timer.due(60000,true)==SleepTimer.Decision.STOP&&!timer.active(),"Finish-song off stops immediately");
        timer.start(1,true,0);
        check(timer.due(60000,false)==SleepTimer.Decision.STOP&&!timer.active(),"Paused or loading source stops at deadline");
        timer.start(1,true,0);
        check(!timer.beforePause(50000)&&timer.remaining(55000)==5000,"Pausing does not suspend wall-duration countdown");
        check(timer.beforePause(60000)&&!timer.active(),"Pause at deadline cannot leave an unlimited waiting timer");
        timer.start(1,true,0);timer.due(60000,true);
        check(timer.due(60001,false)==SleepTimer.Decision.STOP,"Resuming after an expired paused state cannot bypass stop");
        timer.start(1,true,0);
        check(!timer.beforeTrackChange(59999)&&timer.active(),"Earlier manual changes preserve remaining countdown");
        timer.start(2,false,60000);
        check(timer.remaining(60000)==120000&&!timer.waitingForTrack()&&!timer.finishesCurrentTrack(),"Reset replaces deadline and finish-song preference");
        timer.cancel();check(!timer.active()&&!timer.waitingForTrack(),"Cancel clears both countdown and finish-song wait");
        check(timer.due(9999999,true)==SleepTimer.Decision.NONE&&!timer.beforeTrackChange(9999999),"Cancelled timer cannot stop later playback");
        timer.start(1,true,0);
        check(timer.due(3600000,true)==SleepTimer.Decision.WAIT_FOR_TRACK,"Delayed callback compares actual elapsed time");
        timer.start(1,false,0);
        check(timer.due(3600000,true)==SleepTimer.Decision.STOP,"Delayed immediate-stop callback is never treated as remaining time");
        System.out.println("PASS: "+checks+" music sleep timer checks");
    }
}
