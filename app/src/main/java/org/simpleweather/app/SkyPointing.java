package org.simpleweather.app;

/** Android rotation matrix maps the device axes into magnetic east, north and up. */
final class SkyPointing {
    static boolean supported(int mode){return mode==0||mode==2;}
    static double[] direction(float[] r,boolean top,int displayRotation,double declination){
        if(r==null||r.length!=9)return new double[]{Double.NaN,Double.NaN};
        double x=0,y=0,z=top?0:-1;
        if(top){switch(displayRotation){case 1:x=-1;break;case 2:y=-1;break;case 3:x=1;break;default:y=1;}}
        double east=r[0]*x+r[1]*y+r[2]*z,north=r[3]*x+r[4]*y+r[5]*z,up=r[6]*x+r[7]*y+r[8]*z;
        double length=Math.sqrt(east*east+north*north+up*up);if(!Double.isFinite(length)||length<.1)return new double[]{Double.NaN,Double.NaN};
        double alt=Math.toDegrees(Math.asin(Math.max(-1,Math.min(1,up/length))));
        double az=StatusMath.norm(Math.toDegrees(Math.atan2(east,north))+declination);return new double[]{alt,az};
    }
    static double[] marker(int mode,double altitude,double azimuth){if(!supported(mode)||!Double.isFinite(altitude)||!Double.isFinite(azimuth)||altitude<0||altitude>90)return null;return SkyGeometry.horizontal(altitude,azimuth);}
}
