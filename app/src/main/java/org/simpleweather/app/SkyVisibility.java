package org.simpleweather.app;

/** Magnitudes are approximate visual values; lower numbers mean brighter objects. */
final class SkyVisibility {
    static float limit(float value){return Float.isFinite(value)?Math.round(Math.max(-2,Math.min(8,value))*10)/10f:6f;}
    static boolean visible(boolean byMagnitude,boolean manual,double magnitude,float threshold,boolean sunOrMoon){
        if(sunOrMoon)return true;
        return byMagnitude?Double.isFinite(magnitude)&&magnitude<=Math.round(limit(threshold)*10)/10.0+1e-9:manual;
    }
    static String magnitude(double value){return Double.isFinite(value)?String.format(java.util.Locale.ROOT,"约 %.1f 等",value):"未提供";}
}
