package org.simpleweather.app;

/** Requested camera values; Camera2 further clamps these to hardware capabilities. */
final class CameraExposure {
    static final int DEFAULT_ISO=1000, DEFAULT_MS=200;
    static final int OUTDOOR_ISO=50, OUTDOOR_MS=5;
    static int defaultIso(int profile){return profile==1?OUTDOOR_ISO:DEFAULT_ISO;}
    static int defaultMs(int profile){return profile==1?OUTDOOR_MS:DEFAULT_MS;}
    static String isoKey(int profile){return profile==1?"status_camera_outdoor_iso":"status_camera_iso";}
    static String msKey(int profile){return profile==1?"status_camera_outdoor_exposure_ms":"status_camera_exposure_ms";}
    static int iso(int n,int profile){return valid(n,1)?n:defaultIso(profile);}
    static int milliseconds(int n,int profile){return valid(1,n)?n:defaultMs(profile);}
    static int iso(int n){return n>=1&&n<=204800?n:DEFAULT_ISO;}
    static int milliseconds(int n){return n>=1&&n<=1000?n:DEFAULT_MS;}
    static boolean valid(int iso,int milliseconds){return iso>=1&&iso<=204800&&milliseconds>=1&&milliseconds<=1000;}
}
