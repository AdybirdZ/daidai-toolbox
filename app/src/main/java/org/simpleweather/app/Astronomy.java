package org.simpleweather.app;

import java.time.*;
import java.util.*;

/** Low-precision orbital elements and spherical astronomy, independently implemented.
 * Angles in degrees, WGS84 coordinates. Intended for approximate observing guidance.
 * Moon perturbations, refraction variations, proper motion and terrain are not modeled.
 */
public final class Astronomy {
    public static String skyDescription(Instant at,double lat,double lon){
        double sun=sunPosition(at,lat,lon)[0];boolean rising=sunPosition(at.plusSeconds(300),lat,lon)[0]>=sunPosition(at.minusSeconds(300),lat,lon)[0];String twilight=rising?"曙光":"暮光";
        if(sun>=0)return "太阳在地平线上，天空较亮";
        if(sun>=-6)return "民用"+twilight+"中，天空较亮";
        if(sun>=-12)return "航海"+twilight+"中，可寻找亮星";
        if(sun>=-18)return "天文"+twilight+"中，天色较暗";
        return "当前为天文夜";
    }

    private static double sin(double x){return Math.sin(Math.toRadians(x));}
    private static double cos(double x){return Math.cos(Math.toRadians(x));}
    private static double atan(double y,double x){return Math.toDegrees(Math.atan2(y,x));}
    public static double norm(double x){return (x%360+360)%360;}
    private static double days(Instant time){return time.toEpochMilli()/86400000.0+2440587.5-2451543.5;}
    public static double sidereal(Instant time,double longitude){double jd=time.toEpochMilli()/86400000.0+2440587.5;return norm(280.46061837+360.98564736629*(jd-2451545.0)+longitude);}
    private static double eccentric(double m,double e){double E=Math.toRadians(norm(m)),M=E;for(int i=0;i<10;i++)E-=(E-e*Math.sin(E)-M)/(1-e*Math.cos(E));return Math.toDegrees(E);}
    private static double[] equatorial(double x,double y,double z,double d){double e=23.4393-0.0000003563*d;double yy=y*cos(e)-z*sin(e),zz=y*sin(e)+z*cos(e);return new double[]{norm(atan(yy,x)),atan(zz,Math.hypot(x,yy)),Math.sqrt(x*x+yy*yy+zz*zz)};}
    public static double[] sun(Instant time){double d=days(time),w=282.9404+0.0000470935*d,e=.016709-0.000000001151*d,M=356.0470+.9856002585*d,E=eccentric(M,e);double x=cos(E)-e,y=Math.sqrt(1-e*e)*sin(E),lon=atan(y,x)+w,r=Math.hypot(x,y);return equatorial(r*cos(lon),r*sin(lon),0,d);}
    public static double[] moon(Instant time){double d=days(time),N=125.1228-.0529538083*d,i=5.1454,w=318.0634+.1643573223*d,e=.0549,M=115.3654+13.0649929509*d,E=eccentric(M,e),x=60.2666*(cos(E)-e),y=60.2666*Math.sqrt(1-e*e)*sin(E),v=atan(y,x),r=Math.hypot(x,y);return equatorial(r*(cos(N)*cos(v+w)-sin(N)*sin(v+w)*cos(i)),r*(sin(N)*cos(v+w)+cos(N)*sin(v+w)*cos(i)),r*sin(v+w)*sin(i),d);}
    public static double[] horizontal(double ra,double dec,Instant time,double lat,double lon){double h=sidereal(time,lon)-ra;double alt=Math.toDegrees(Math.asin(sin(lat)*sin(dec)+cos(lat)*cos(dec)*cos(h)));double az=norm(atan(sin(h),cos(h)*sin(lat)-Math.tan(Math.toRadians(dec))*cos(lat))+180);return new double[]{alt,az};}
    public static double[] sunPosition(Instant time,double lat,double lon){double[] eq=sun(time);return horizontal(eq[0],eq[1],time,lat,lon);}
    public static double[] moonPosition(Instant time,double lat,double lon){double[] eq=moon(time);double lst=sidereal(time,lon),r=eq[2];double x=r*cos(eq[1])*cos(eq[0])-cos(lat)*cos(lst),y=r*cos(eq[1])*sin(eq[0])-cos(lat)*sin(lst),z=r*sin(eq[1])-sin(lat);return horizontal(norm(atan(y,x)),atan(z,Math.hypot(x,y)),time,lat,lon);}
    public static double phase(Instant time){double[] a=sun(time),b=moon(time);double e=23.4393-.0000003563*days(time);double as=atan(sin(a[0])*cos(a[1])*cos(e)+sin(a[1])*sin(e),cos(a[0])*cos(a[1]));double bs=atan(sin(b[0])*cos(b[1])*cos(e)+sin(b[1])*sin(e),cos(b[0])*cos(b[1]));return norm(bs-as)/360.0;}
    public static double illumination(Instant time){return (1-cos(phase(time)*360))/2;}
    public static String phaseName(double phase){return new String[]{"新月","娥眉月","上弦月","盈凸月","满月","亏凸月","下弦月","残月"}[(int)Math.floor(phase*8+.5)%8];}
    public static String compass(double az){return new String[]{"北","东北","东","东南","南","西南","西","西北"}[(int)Math.floor((norm(az)+22.5)/45)%8];}
    public static final class Event {public final Instant at;public final boolean rising;Event(Instant t,boolean r){at=t;rising=r;}}
    public static List<Event> crossings(LocalDate date,ZoneId zone,double lat,double lon,boolean lunar,double altitude){
        List<Event> out=new ArrayList<>();Instant start=date.atStartOfDay(zone).toInstant(),end=date.plusDays(1).atStartOfDay(zone).toInstant();Instant prev=start;double last=(lunar?moonPosition(prev,lat,lon):sunPosition(prev,lat,lon))[0]-altitude;
        for(Instant t=start.plusSeconds(600);;t=t.plusSeconds(600)){
            if(t.isAfter(end))t=end;double value=(lunar?moonPosition(t,lat,lon):sunPosition(t,lat,lon))[0]-altitude;
            if((last<0&&value>=0)||(last>=0&&value<0)){long lo=prev.toEpochMilli(),hi=t.toEpochMilli();boolean rising=value>last;for(int j=0;j<20;j++){long mid=lo+(hi-lo)/2;double a=(lunar?moonPosition(Instant.ofEpochMilli(mid),lat,lon):sunPosition(Instant.ofEpochMilli(mid),lat,lon))[0]-altitude;if((a<0)==rising)lo=mid;else hi=mid;}out.add(new Event(Instant.ofEpochMilli(lo+(hi-lo)/2),rising));}
            if(!t.isBefore(end))break;prev=t;last=value;
        }return out;
    }
    public static final class Star {
        public final String name;public final double ra,dec,mag;
        Star(String n,double r,double d,double m){name=n;ra=r;dec=d;mag=m;}
    }
    public static final Star[] STARS={
        new Star("天狼星",101.287,-16.716,-1.46),new Star("老人星",95.988,-52.696,-.74),new Star("南门二",219.902,-60.834,-.27),new Star("大角星",213.915,19.182,-.05),new Star("织女星",279.235,38.784,.03),new Star("五车二",79.172,45.998,.08),new Star("参宿七",78.634,-8.202,.13),new Star("南河三",114.826,5.225,.34),new Star("参宿四",88.793,7.407,.5),new Star("水委一",24.429,-57.237,.46),new Star("牛郎星",297.696,8.868,.77),new Star("十字架二",186.65,-63.099,.76),new Star("毕宿五",68.98,16.509,.85),new Star("角宿一",201.298,-11.161,.98),new Star("心宿二",247.352,-26.432,1.06),new Star("北落师门",344.413,-29.622,1.16),new Star("天津四",310.358,45.28,1.25),new Star("轩辕十四",152.093,11.967,1.35),new Star("北极星",37.955,89.264,1.98),new Star("天枢",165.932,61.751,1.79),new Star("天璇",165.46,56.383,2.37),new Star("天玑",178.457,53.695,2.44),new Star("天权",183.857,57.033,3.31),new Star("玉衡",193.507,55.96,1.76),new Star("开阳",200.981,54.925,2.23),new Star("摇光",206.885,49.313,1.85)};
}
