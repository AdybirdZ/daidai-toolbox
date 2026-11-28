package org.daidai.toolbox;

/** Magnitudes are approximate visual values; lower numbers mean brighter objects. */
public final class SkyVisibility {
    public static float limit(float value){return Float.isFinite(value)?Math.round(Math.max(-2,Math.min(8,value))*10)/10f:6f;}
    public static boolean visible(boolean byMagnitude,boolean manual,double magnitude,float threshold,boolean sunOrMoon){
        if(sunOrMoon)return true;
        return byMagnitude?Double.isFinite(magnitude)&&magnitude<=Math.round(limit(threshold)*10)/10.0+1e-9:manual;
    }
    /** Returns 1 for circumpolar, -1 for never rising, and 0 when the object crosses the horizon. */
    public static int horizonClass(double latitude,double declination){
        if(!Double.isFinite(latitude)||!Double.isFinite(declination)||Math.abs(latitude)>90||Math.abs(declination)>90)return 0;
        if(latitude>0){if(declination>90-latitude)return 1;if(declination<latitude-90)return -1;}
        if(latitude<0){if(declination< -90-latitude)return 1;if(declination>90+latitude)return -1;}
        return 0;
    }
    public static String magnitude(double value){return Double.isFinite(value)?String.format(java.util.Locale.ROOT,UiText.get("sky_visibility_001"),value):UiText.get("earthquake_options_001");}
}
