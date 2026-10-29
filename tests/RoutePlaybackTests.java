package org.simpleweather.app;

public final class RoutePlaybackTests {
    private static int checks;
    private static void check(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
    public static void main(String[] args){
        RoutePlayback r=new RoutePlayback();check(!r.loaded(),"initially no route");r.play(0);check(!r.playing(),"empty cannot play");
        r.load(5);check(r.index()==4&&!r.playing(),"loading previews the complete route, paused");
        r.play(1000);check(r.index()==0&&r.playing(),"play starts preview again at first point");
        check(!r.advance(1999)&&r.index()==0,"does not invent a point before interval");
        check(r.advance(2000)&&r.index()==1,"one point per second");
        r.seek(4);check(r.index()==1,"seeking disabled during playback");r.play(2100);check(r.index()==1,"repeated play does not restart");
        r.pause();r.advance(9000);check(r.index()==1,"pause freezes progress");
        r.seek(0);check(r.index()==0,"jump to start");r.seek(4);check(r.index()==4,"jump to last");r.seek(500);check(r.index()==4,"upper boundary");r.seek(-1);check(r.index()==0,"lower boundary");
        r.seek(2);r.play(10000);r.advance(11000);check(r.index()==3,"resume from selected point");
        r.advance(12000);check(r.index()==4&&!r.playing(),"end automatically pauses without overflow");
        r.play(13000);check(r.index()==0,"play at end restarts");r.pause();r.load(100);r.speed(20,14000);r.play(14000);
        check(!r.advance(14049),"20 per second waits 50 ms");r.advance(14050);check(r.index()==1,"20 per second advances after 50 ms");
        r.advance(14500);check(r.index()==10,"catches up by elapsed monotonic time");
        r.speed(1,14525);r.advance(15025);check(r.index()==11,"speed change accounts for old-rate fractional progress");
        r.pause();r.seek(4);r.play(20000);r.advance(20999);check(r.index()==4,"pause discards fractional credit");
        r.advance(21000);check(r.index()==5,"resume timing starts fresh");
        r.pause();r.speed(0,22000);check(r.speed()==1,"speed minimum");r.speed(90,22000);check(r.speed()==20,"speed maximum");
        r.load(1);r.play(23000);check(r.index()==0&&!r.playing(),"single-point route remains visible, paused");
        r.load(0);check(!r.loaded()&&r.index()==-1&&!r.playing(),"recording clears replay state");
        r.load(3);r.play(24000);r.advance(30000);check(r.index()==2&&!r.playing(),"delayed frame stops at final point");
        System.out.println("RoutePlaybackTests: "+checks+" checks passed");
    }
}
