package org.simpleweather.app;

/** Offline directions, spherical distances and display geometry; no mapping service. */
final class StatusMath {
    static final String[] DIRECTIONS={"北","东北偏北","东北","东北偏东","东","东南偏东","东南","东南偏南","南","西南偏南","西南","西南偏西","西","西北偏西","西北","西北偏北"};
    static double norm(double n){return Double.isFinite(n)?(n%360+360)%360:Double.NaN;}
    static String direction(double n){return Double.isFinite(n)?DIRECTIONS[(int)Math.floor((norm(n)+11.25)/22.5)%16]:"方向不可用";}
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
    static String distanceText(double meters){return !Double.isFinite(meters)?"距离不可用":meters>=1000?String.format(java.util.Locale.ROOT,"%.2f km",meters/1000):String.format(java.util.Locale.ROOT,"%.1f 米",meters);}
    static String constellation(int c){switch(c){case 1:return "美国 GPS";case 2:return "星基增强 SBAS";case 3:return "俄罗斯 GLONASS";case 4:return "日本 QZSS";case 5:return "中国北斗";case 6:return "欧洲伽利略";case 7:return "印度 NavIC";default:return "未知系统";}}
    static String satelliteId(int c,int id){return (c==1?"G":c==3?"R":c==5?"C":c==6?"E":c==4?"J":c==7?"I":c==2?"S":"?")+id;}
}
