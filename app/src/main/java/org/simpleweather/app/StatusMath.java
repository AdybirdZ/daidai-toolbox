package org.simpleweather.app;

/** Offline directions, spherical distances and display geometry; no mapping service. */
final class StatusMath {
    static String[] directions(){return new String[]{UiText.get("astronomy_026"),UiText.get("status_math_001"),UiText.get("astronomy_027"),UiText.get("status_math_002"),UiText.get("astronomy_028"),UiText.get("status_math_003"),UiText.get("astronomy_029"),UiText.get("status_math_004"),UiText.get("astronomy_030"),UiText.get("status_math_005"),UiText.get("astronomy_031"),UiText.get("status_math_006"),UiText.get("astronomy_032"),UiText.get("status_math_007"),UiText.get("astronomy_033"),UiText.get("status_math_008")};}
    static double norm(double n){return Double.isFinite(n)?(n%360+360)%360:Double.NaN;}
    static String direction(double n){return Double.isFinite(n)?directions()[(int)Math.floor((norm(n)+11.25)/22.5)%16]:UiText.get("status_math_009");}
    static double delta(double target,double heading){return norm(target-heading+180)-180;}
    static double smooth(double previous,double current,double factor){return !Double.isFinite(previous)?norm(current):norm(previous+delta(current,previous)*factor);}
    static boolean coordinate(double lon,double lat){return Double.isFinite(lon)&&Double.isFinite(lat)&&Math.abs(lon)<=180&&Math.abs(lat)<=90;}
    static double distance(double lon1,double lat1,double lon2,double lat2){
        if(!coordinate(lon1,lat1)||!coordinate(lon2,lat2))return Double.NaN;
        double dlat=Math.toRadians(lat2-lat1),dlon=Math.toRadians(lon2-lon1),q=Math.sin(dlat/2)*Math.sin(dlat/2)+Math.cos(Math.toRadians(lat1))*Math.cos(Math.toRadians(lat2))*Math.sin(dlon/2)*Math.sin(dlon/2);
        return 6371008.8*2*Math.asin(Math.sqrt(Math.min(1,Math.max(0,q))));
    }
    static double bearing(double lon1,double lat1,double lon2,double lat2){
        if(!coordinate(lon1,lat1)||!coordinate(lon2,lat2)||distance(lon1,lat1,lon2,lat2)<.01)return Double.NaN;
        double d=Math.toRadians(lon2-lon1),a=Math.toRadians(lat1),b=Math.toRadians(lat2);
        return norm(Math.toDegrees(Math.atan2(Math.sin(d)*Math.cos(b),Math.cos(a)*Math.sin(b)-Math.sin(a)*Math.cos(b)*Math.cos(d))));
    }
    static boolean satelliteAngles(double elevation,double azimuth){return Double.isFinite(elevation)&&elevation>=-90&&elevation<=90&&Double.isFinite(azimuth)&&azimuth>=0&&azimuth<=360;}
    static double[] sky(double elevation,double azimuth){double r=(90-elevation)/90,t=Math.toRadians(azimuth);return new double[]{r*Math.sin(t),-r*Math.cos(t)};}
    static String distanceText(double meters){return !Double.isFinite(meters)?UiText.get("status_math_010"):meters>=1000?String.format(java.util.Locale.ROOT,"%.2f km",meters/1000):String.format(java.util.Locale.ROOT,UiText.get("gps_141"),meters);}
    static double placeRange(double targetMeters,double farthestMeters){double meters=Double.isFinite(targetMeters)?targetMeters:farthestMeters;return meters<=100?100:meters<=1000?1000:Math.pow(10,Math.ceil(Math.log10(meters)));}
    /** Chinese display names; constellation abbreviations are deliberately not shown in the UI. */
    static String constellation(int c){switch(c){case 1:return UiText.get("status_math_011");case 2:return UiText.get("status_math_012");case 3:return UiText.get("status_math_013");case 4:return UiText.get("status_math_014");case 5:return UiText.get("status_math_015");case 6:return UiText.get("status_math_016");case 7:return UiText.get("status_math_017");default:return UiText.get("status_math_018");}}
    static String satelliteId(int c,int id){return UiText.get("status_math_019")+id;}
    static int constellationRank(int c,int[] order){for(int n=0;n<order.length;n++)if(order[n]==c)return n;return order.length+10;}
}
