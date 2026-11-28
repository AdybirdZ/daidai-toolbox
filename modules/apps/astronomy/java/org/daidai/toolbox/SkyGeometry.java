package org.daidai.toolbox;

import java.time.Instant;

/** Normalized full-sky chart projections; no Android or remote ephemeris dependency. */
public final class SkyGeometry {
    public static final int ZOOM_STEPS=1000;
    public static final float MIN_ZOOM=.65f,MAX_ZOOM=2.6f;
    private SkyGeometry(){}
    public static float zoomAt(int progress){return MIN_ZOOM+(MAX_ZOOM-MIN_ZOOM)*Math.max(0,Math.min(ZOOM_STEPS,progress))/(float)ZOOM_STEPS;}
    public static int zoomProgress(float zoom){if(!Float.isFinite(zoom))zoom=MIN_ZOOM;return Math.round(ZOOM_STEPS*(Math.max(MIN_ZOOM,Math.min(MAX_ZOOM,zoom))-MIN_ZOOM)/(MAX_ZOOM-MIN_ZOOM));}
    public static float labelMagnitudeLimit(float zoom){return 1f+5f*(Math.max(0,Math.min(1,(zoom-MIN_ZOOM)/(MAX_ZOOM-MIN_ZOOM))));}
    public static boolean insideViewport(float x,float y,float centerX,float centerY,float radius){return Float.isFinite(x)&&Float.isFinite(y)&&Math.hypot(x-centerX,y-centerY)<=radius;}
    /** Zenith center, horizon radius 1, north up and east left. */
    public static double[] horizontal(double altitude,double azimuth){double r=(90-altitude)/90,a=Math.toRadians(azimuth);return new double[]{-r*Math.sin(a),-r*Math.cos(a)};}
    /** North celestial pole center, south pole boundary; RA 0h up, 6h left. */
    public static double[] northPolar(double ra,double dec){double r=(90-dec)/180,a=Math.toRadians(ra);return new double[]{-r*Math.sin(a),-r*Math.cos(a)};}
    /** Inverse spherical transformation for drawing the local horizon on an equatorial map. */
    public static double[] equatorial(double altitude,double azimuth,Instant at,double lat,double lon){
        double a=Math.toRadians(altitude),z=Math.toRadians(azimuth),p=Math.toRadians(lat);
        double dec=Math.asin(Math.max(-1,Math.min(1,Math.sin(p)*Math.sin(a)+Math.cos(p)*Math.cos(a)*Math.cos(z))));
        double hour=Math.atan2(-Math.cos(a)*Math.sin(z),Math.cos(p)*Math.sin(a)-Math.sin(p)*Math.cos(a)*Math.cos(z));
        return new double[]{Astronomy.norm(Astronomy.sidereal(at,lon)-Math.toDegrees(hour)),Math.toDegrees(dec)};
    }
    /** Stable speed, independent of the selected astronomical date. One clockwise turn per 30 seconds. */
    public static float selectionAngle(long uptimeMillis){return (uptimeMillis%30000)*360f/30000;}
}
