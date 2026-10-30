package org.simpleweather.app;

/** Pixel offsets and zoom share the viewport center as their origin. */
final class RouteViewport {
    static final double METERS_PER_DEGREE=Math.PI*6371008.8/180;
    static final int SLIDER_MAX=1000;
    float zoom=1,panX,panY;
    double sideMeters(){return 1000.0/zoom;}
    static double metersAt(int progress){return 30*Math.pow(10000.0/30,Math.max(0,Math.min(SLIDER_MAX,progress))/(double)SLIDER_MAX);}
    static int progressAt(double meters){return (int)Math.round(SLIDER_MAX*Math.log(Math.max(30,Math.min(10000,meters))/30)/Math.log(10000.0/30));}
    void sideMeters(double meters){if(Double.isFinite(meters)&&meters>0)zoomTo((float)(1000/Math.max(30,Math.min(10000,meters))));}
    void center(){panX=panY=0;}
    void reset(){center();zoom=1;}
    void pan(float dx,float dy){panX+=dx;panY+=dy;}
    void zoomTo(float value){
        if(!Float.isFinite(value))return;
        float next=Math.max(.1f,Math.min(1000f/30,value)),ratio=next/zoom;
        panX*=ratio;panY*=ratio;zoom=next;
    }
}
