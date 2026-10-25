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
    private static double[] solarEcliptic(double d){double w=282.9404+0.0000470935*d,e=.016709-0.000000001151*d,M=356.0470+.9856002585*d,E=eccentric(M,e);double x=cos(E)-e,y=Math.sqrt(1-e*e)*sin(E),lon=atan(y,x)+w,r=Math.hypot(x,y);return new double[]{r*cos(lon),r*sin(lon),0,r};}
    public static double[] sun(Instant time){double d=days(time),v[]=solarEcliptic(d);return equatorial(v[0],v[1],v[2],d);}
    public static double[] moon(Instant time){double d=days(time),N=125.1228-.0529538083*d,i=5.1454,w=318.0634+.1643573223*d,e=.0549,M=115.3654+13.0649929509*d,E=eccentric(M,e),x=60.2666*(cos(E)-e),y=60.2666*Math.sqrt(1-e*e)*sin(E),v=atan(y,x),r=Math.hypot(x,y);return equatorial(r*(cos(N)*cos(v+w)-sin(N)*sin(v+w)*cos(i)),r*(sin(N)*cos(v+w)+cos(N)*sin(v+w)*cos(i)),r*sin(v+w)*sin(i),d);}
    public static double[] horizontal(double ra,double dec,Instant time,double lat,double lon){double h=sidereal(time,lon)-ra;double alt=Math.toDegrees(Math.asin(sin(lat)*sin(dec)+cos(lat)*cos(dec)*cos(h)));double az=norm(atan(sin(h),cos(h)*sin(lat)-Math.tan(Math.toRadians(dec))*cos(lat))+180);return new double[]{alt,az};}
    public static double[] sunPosition(Instant time,double lat,double lon){double[] eq=sun(time);return horizontal(eq[0],eq[1],time,lat,lon);}
    public static double[] moonPosition(Instant time,double lat,double lon){double[] eq=moon(time);double lst=sidereal(time,lon),r=eq[2];double x=r*cos(eq[1])*cos(eq[0])-cos(lat)*cos(lst),y=r*cos(eq[1])*sin(eq[0])-cos(lat)*sin(lst),z=r*sin(eq[1])-sin(lat);return horizontal(norm(atan(y,x)),atan(z,Math.hypot(x,y)),time,lat,lon);}

    /** Approximate geocentric positions for the visible planets. Elements are the
     * compact low precision set from the independently implemented public-domain
     * orbital recipe used by this app. They are for observing guidance, not navigation. */
    public static final class Planet {
        public final String name; public final double ra,dec, distance, magnitude;
        Planet(String n,double r,double d,double dist,double mag){name=n;ra=r;dec=d;distance=dist;magnitude=mag;}
    }
    /** Approximate geocentric positions for the visible planets.
     * This is the same JPL "Approximate Positions of the Major Planets" model
     * used by the reference web implementation (valid approximately 1800-2050).
     */
    private static final class JplElements {
        final String name,en; final double[] el,rt;
        JplElements(String n,String e,double[] a,double[] r){name=n;en=e;el=a;rt=r;}
    }
    private static final JplElements EARTH = new JplElements("地球","Earth",
        new double[]{1.00000261,.01671123,-.00001531,100.46457166,102.93768193,0},
        new double[]{.00000562,-.00004392,-.01294668,35999.37244981,.32327364,0});
    private static final JplElements[] PLANET_ELEMENTS={
        new JplElements("水星","Mercury",new double[]{.38709927,.20563593,7.00497902,252.25032350,77.45779628,48.33076593},new double[]{.00000037,.00001906,-.00594749,149472.67411175,.16047689,-.12534081}),
        new JplElements("金星","Venus",new double[]{.72333566,.00677672,3.39467605,181.97909950,131.60246718,76.67984255},new double[]{.00000390,-.00004107,-.00078890,58517.81538729,.00268329,-.27769418}),
        new JplElements("火星","Mars",new double[]{1.52371034,.09339410,1.84969142,-4.55343205,-23.94362959,49.55953891},new double[]{.00001847,.00007882,-.00813131,19140.30268499,.44441088,-.29257343}),
        new JplElements("木星","Jupiter",new double[]{5.20288700,.04838624,1.30439695,34.39644051,14.72847983,100.47390909},new double[]{-.00011607,-.00013253,-.00183714,3034.74612775,.21252668,.20469106}),
        new JplElements("土星","Saturn",new double[]{9.53667594,.05386179,2.48599187,49.95424423,92.59887831,113.66242448},new double[]{-.00125060,-.00050991,.00193609,1222.49362201,-.41897216,-.28867794}),
        new JplElements("天王星","Uranus",new double[]{19.18916464,.04725744,.77263783,313.23810451,170.95427630,74.01692503},new double[]{.00000198,-.00004397,-.00242939,428.48202785,.40805281,.04240589}),
        new JplElements("海王星","Neptune",new double[]{30.06992276,.00859048,1.77004347,-55.12002969,44.96476227,131.78422574},new double[]{.00026291,.00005105,.00035372,218.45945325,-.32241464,-.00508664})
    };
    private static double julianDate(Instant time){return time.toEpochMilli()/86400000.0+2440587.5;}
    /** JPL heliocentric ecliptic coordinates in AU, using Julian centuries from J2000. */
    private static double[] jplHeliocentric(JplElements q,double T){
        double a=q.el[0]+q.rt[0]*T,e=q.el[1]+q.rt[1]*T,I=q.el[2]+q.rt[2]*T;
        double L=q.el[3]+q.rt[3]*T,w=q.el[4]+q.rt[4]*T,O=q.el[5]+q.rt[5]*T;
        double M=norm(L-w);if(M>180)M-=360;
        double E=eccentric(M,e),xp=a*(cos(E)-e),yp=a*Math.sqrt(1-e*e)*sin(E);
        double wp=w-O,om=O,inc=I;
        double x=(cos(wp)*cos(om)-sin(wp)*sin(om)*cos(inc))*xp-(sin(wp)*cos(om)+cos(wp)*sin(om)*cos(inc))*yp;
        double y=(cos(wp)*sin(om)+sin(wp)*cos(om)*cos(inc))*xp-(sin(wp)*sin(om)-cos(wp)*cos(om)*cos(inc))*yp;
        double z=sin(wp)*sin(inc)*xp+cos(wp)*sin(inc)*yp;
        return new double[]{x,y,z,Math.sqrt(x*x+y*y+z*z)};
    }
    private static double planetMagnitude(String name,double r,double delta,double phase){
        double base=5*Math.log10(r*delta),v;
        if(name.equals("水星"))v=-.42+base+.0380*phase-.000273*phase*phase+.000002*phase*phase*phase;
        else if(name.equals("金星"))v=-4.40+base+.0009*phase+.000239*phase*phase-.00000065*phase*phase*phase;
        else if(name.equals("火星"))v=-1.52+base+.016*phase;
        else if(name.equals("木星"))v=-9.40+base+.005*phase;
        else if(name.equals("土星"))v=-8.88+base+.044*phase;
        else if(name.equals("天王星"))v=-7.19+base+.0028*phase;
        else if(name.equals("海王星"))v=-6.87+base+.0018*phase;
        else v=-2+base;
        return Double.isFinite(v)?v:Double.NaN;
    }
    public static Planet[] planets(Instant time){
        double jd=julianDate(time),T=(jd-2451545.0)/36525.0;
        double[] earth=jplHeliocentric(EARTH,T);
        Planet[] out=new Planet[PLANET_ELEMENTS.length];
        for(int n=0;n<PLANET_ELEMENTS.length;n++){
            double[] p=jplHeliocentric(PLANET_ELEMENTS[n],T);
            // Geocentric vector is target heliocentric position minus Earth's.
            double x=p[0]-earth[0],y=p[1]-earth[1],z=p[2]-earth[2];
            double delta=Math.sqrt(x*x+y*y+z*z);
            double eps=23.43928-.0000004*(jd-2451545.0);
            double ye=y*cos(eps)-z*sin(eps),ze=y*sin(eps)+z*cos(eps);
            double ra=norm(atan(ye,x)),dec=atan(ze,Math.hypot(x,ye));
            double r=p[3],cosPhase=(r*r+delta*delta-earth[3]*earth[3])/(2*r*delta);
            double phase=Math.toDegrees(Math.acos(Math.max(-1,Math.min(1,cosPhase))));
            out[n]=new Planet(PLANET_ELEMENTS[n].name,ra,dec,delta,planetMagnitude(PLANET_ELEMENTS[n].name,r,delta,phase));
        }
        return out;
    }
    public static double[] planetPosition(Planet p,Instant time,double lat,double lon){return horizontal(p.ra,p.dec,time,lat,lon);}
    public static double phase(Instant time){double[] a=sun(time),b=moon(time);double e=23.4393-.0000003563*days(time);double as=atan(sin(a[0])*cos(a[1])*cos(e)+sin(a[1])*sin(e),cos(a[0])*cos(a[1]));double bs=atan(sin(b[0])*cos(b[1])*cos(e)+sin(b[1])*sin(e),cos(b[0])*cos(b[1]));return norm(bs-as)/360.0;}
    public static double illumination(Instant time){return (1-cos(phase(time)*360))/2;}
    /** V-band geometric brightness estimate; Schlyter section 15, independently implemented.
     * Earth-radius lunar distance; excludes extinction, earthshine and eclipses. */
    public static double moonMagnitude(Instant time){
        double[] solar=sun(time),lunar=moon(time);
        double cosine=sin(solar[1])*sin(lunar[1])+cos(solar[1])*cos(lunar[1])*cos(solar[0]-lunar[0]);
        double angle=180-Math.toDegrees(Math.acos(Math.max(-1,Math.min(1,cosine))));
        return moonMagnitudeEstimate(angle,solar[2],lunar[2]);
    }
    public static double moonMagnitudeEstimate(double phaseAngle,double solarDistanceAu,double lunarDistanceEarthRadii){
        // The empirical curve is unreliable near new moon; do not extrapolate there.
        if(!Double.isFinite(phaseAngle)||phaseAngle<0||phaseAngle>=170||!Double.isFinite(solarDistanceAu)||solarDistanceAu<=0||!Double.isFinite(lunarDistanceEarthRadii)||lunarDistanceEarthRadii<=0)return Double.NaN;
        return -21.62+5*Math.log10(solarDistanceAu*lunarDistanceEarthRadii)+.026*phaseAngle+4e-9*Math.pow(phaseAngle,4);
    }
    /** Illuminated left/right x boundaries of a unit lunar disc, north-up convention.
     * Waxing right side, waning left side. Does not model local parallactic rotation. */
    public static double[] moonDiscSpan(double phase,double y){
        double edge=Math.sqrt(Math.max(0,1-y*y)),cycle=(phase%1+1)%1,k=cos(cycle*360);
        return cycle<=.5?new double[]{k*edge,edge}:new double[]{-edge,-k*edge};
    }
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
    public static boolean isDipper(String n){return n.equals("天枢")||n.equals("天璇")||n.equals("天玑")||n.equals("天权")||n.equals("玉衡")||n.equals("开阳")||n.equals("摇光");}
    /** Annual reference peaks and fixed approximate radiant coordinates (degrees).
     * A radiant is a direction, not a physical star or a predicted meteor. */
    public static final class Shower {
        public final String name;public final MonthDay peak;public final double ra,dec;
        Shower(String n,int month,int day,double r,double d){name=n;peak=MonthDay.of(month,day);ra=r;dec=d;}
        public boolean activeOn(LocalDate date){
            for(int year=date.getYear()-1;year<=date.getYear()+1;year++)
                if(Math.abs(java.time.temporal.ChronoUnit.DAYS.between(peak.atYear(year),date))<=3)return true;
            return false;
        }
        public LocalDate nextPeak(LocalDate date){LocalDate p=peak.atYear(date.getYear());return p.isBefore(date)?peak.atYear(date.getYear()+1):p;}
    }
    public static final Shower[] SHOWERS={
        new Shower("象限仪座",1,4,230,49),new Shower("天琴座",4,22,278,34),new Shower("宝瓶座η",5,6,338,-1),new Shower("英仙座",8,12,48,58),
        new Shower("猎户座",10,21,95,16),new Shower("狮子座",11,17,152,22),new Shower("双子座",12,14,112,33),new Shower("小熊座",12,22,217,76)
    };
    /** Approximate J2000 centers, Wikidata CC0 structured data: Q13423 and Q2469.
     * Extended objects have no point-source magnitude here. See docs/ASTRONOMY-1.0.9.md. */
    public static final Star[] DEEP_SKY={new Star("昴星团",56.7500,24.1167,Double.NaN),new Star("仙女座星系",10.6847083,41.2687500,Double.NaN)};
    public static final Star[] STARS={
        new Star("天狼星",101.287,-16.716,-1.46),new Star("老人星",95.988,-52.696,-.74),new Star("南门二",219.902,-60.834,-.27),new Star("大角星",213.915,19.182,-.05),new Star("织女一",279.235,38.784,.03),new Star("五车二",79.172,45.998,.08),new Star("参宿七",78.634,-8.202,.13),new Star("南河三",114.826,5.225,.34),new Star("参宿四",88.793,7.407,.5),new Star("水委一",24.429,-57.237,.46),new Star("河鼓二",297.696,8.868,.77),new Star("十字架二",186.65,-63.099,.76),new Star("毕宿五",68.98,16.509,.85),new Star("角宿一",201.298,-11.161,.98),new Star("心宿二",247.352,-26.432,1.06),new Star("北落师门",344.413,-29.622,1.16),new Star("天津四",310.358,45.28,1.25),new Star("轩辕十四",152.093,11.967,1.35),new Star("勾陈一",37.955,89.264,1.98),new Star("天枢",165.932,61.751,1.79),new Star("天璇",165.46,56.383,2.37),new Star("天玑",178.457,53.695,2.44),new Star("天权",183.857,57.033,3.31),new Star("玉衡",193.507,55.96,1.76),new Star("开阳",200.981,54.925,2.23),new Star("摇光",206.885,49.313,1.85)};
}
