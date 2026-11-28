package org.daidai.toolbox;

/** Update cadence for the status display, independent from sensor sampling. */
public final class StatusRefreshPolicy {
    /** Static cards need no display polling; age labels refresh once per second. */
    public static final long IDLE_TICK_MS=1000;
    public static final long ACTIVE_TICK_MS=250;
    public static final long ROUTE_TICK_MS=100;
    public static final long SENSOR_TEXT_MS=250;
    public static final long TREASURE_TEXT_MS=500;

    private StatusRefreshPolicy(){}

    public static long tickMillis(boolean recordingRoute,boolean playingRoute,boolean liveReading){
        if(recordingRoute||playingRoute)return ROUTE_TICK_MS;
        return liveReading?ACTIVE_TICK_MS:IDLE_TICK_MS;
    }

    public static boolean due(long now,long last,long interval){
        return now-last>=interval;
    }
}
