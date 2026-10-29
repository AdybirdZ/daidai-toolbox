package org.simpleweather.app;

/** Playback advances by recorded points, independent of location and wall-clock time. */
final class RoutePlayback {
    private int size,index=-1,speed=1;
    private boolean playing;
    private long previous;
    private double credit;
    void load(int count){size=Math.max(0,count);index=size-1;playing=false;credit=0;}
    boolean loaded(){return size>0;}
    int index(){return index;}
    int size(){return size;}
    int speed(){return speed;}
    boolean playing(){return playing;}
    void speed(int value,long now){advance(now);speed=Math.max(1,Math.min(20,value));}
    void play(long now){if(!loaded()||playing)return;if(index>=size-1)index=0;playing=size>1;previous=now;credit=0;}
    void pause(){playing=false;credit=0;}
    void seek(int value){if(playing||!loaded())return;index=Math.max(0,Math.min(size-1,value));credit=0;}
    boolean advance(long now){if(!playing)return false;long elapsed=Math.max(0,now-previous);previous=now;credit+=elapsed*speed/1000.0;int steps=(int)credit;if(steps==0)return false;credit-=steps;index=Math.min(size-1,index+steps);if(index>=size-1)pause();return true;}
}
