package org.daidai.modules.music;

/** One-shot playback deadline. All times use the caller's monotonic clock. */
public final class SleepTimer {
    public static final int MIN_MINUTES=1,MAX_MINUTES=120;
    public enum Decision { NONE, WAIT_FOR_TRACK, STOP }
    private long deadline=-1;
    private boolean finishTrack,waiting;

    public void start(int minutes,boolean finishCurrentTrack,long now){
        if(minutes<MIN_MINUTES||minutes>MAX_MINUTES||now<0)throw new IllegalArgumentException("Invalid sleep timer");
        long duration=minutes*60000L;
        if(now>Long.MAX_VALUE-duration)throw new IllegalArgumentException("Clock overflow");
        deadline=now+duration;finishTrack=finishCurrentTrack;waiting=false;
    }
    public void cancel(){deadline=-1;waiting=false;}
    public boolean active(){return deadline>=0;}
    public boolean waitingForTrack(){return active()&&waiting;}
    public boolean finishesCurrentTrack(){return active()&&finishTrack;}
    public long deadline(){return deadline;}
    public long remaining(long now){return active()?Math.max(0,deadline-now):0;}

    /** A paused, failed, or still loading source must never prolong an expired timer. */
    public Decision due(long now,boolean playing){
        if(!active()||now<deadline)return Decision.NONE;
        if(finishTrack&&playing){waiting=true;return Decision.WAIT_FOR_TRACK;}
        cancel();return Decision.STOP;
    }
    /** Natural completion and manual track changes both end the final permitted song. */
    public boolean beforeTrackChange(long now){
        if(!active()||now<deadline)return false;
        cancel();return true;
    }
    public boolean beforePause(long now){return beforeTrackChange(now);}
}
