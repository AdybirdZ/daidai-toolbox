package org.simpleweather.app;

/** Requested camera values; Camera2 further clamps these to hardware capabilities. */
final class CameraExposure {
    static final int DEFAULT_ISO=1000, DEFAULT_MS=200;
    static int iso(int n){return n>=1&&n<=204800?n:DEFAULT_ISO;}
    static int milliseconds(int n){return n>=1&&n<=1000?n:DEFAULT_MS;}
    static boolean valid(int iso,int milliseconds){return iso>=1&&iso<=204800&&milliseconds>=1&&milliseconds<=1000;}
}
