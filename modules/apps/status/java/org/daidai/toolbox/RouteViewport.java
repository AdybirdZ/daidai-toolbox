package org.daidai.toolbox;

/** Pixel offsets and zoom share the viewport center as their origin. */
public final class RouteViewport {
    public static final double METERS_PER_DEGREE=Math.PI*6371008.8/180;
    public static final int SLIDER_MAX=1000;
    public float zoom=1,panX,panY;
    public double sideMeters(){return 1000.0/zoom;}
    public static double metersAt(int progress){return 10000*Math.pow(30.0/10000,Math.max(0,Math.min(SLIDER_MAX,progress))/(double)SLIDER_MAX);}
    public static int progressAt(double meters){return (int)Math.round(SLIDER_MAX*Math.log(Math.max(30,Math.min(10000,meters))/10000)/Math.log(30.0/10000));}
    public void sideMeters(double meters){if(Double.isFinite(meters)&&meters>0)zoomTo((float)(1000/Math.max(30,Math.min(10000,meters))));}
    public void center(){panX=panY=0;}
    public void reset(){center();zoom=1;}
    public void pan(float dx,float dy){panX+=dx;panY+=dy;}
    public void zoomTo(float value){
        if(!Float.isFinite(value))return;
        float next=Math.max(.1f,Math.min(1000f/30,value)),ratio=next/zoom;
        panX*=ratio;panY*=ratio;zoom=next;
    }
}
