package org.simpleweather.app;

/** Pixel offsets and zoom share the viewport center as their origin. */
final class RouteViewport {
    float zoom=1,panX,panY;
    void center(){panX=panY=0;}
    void reset(){center();zoom=1;}
    void pan(float dx,float dy){panX+=dx;panY+=dy;}
    void zoomTo(float value){
        if(!Float.isFinite(value))return;
        float next=Math.max(.25f,Math.min(64,value)),ratio=next/zoom;
        panX*=ratio;panY*=ratio;zoom=next;
    }
}
