package org.simpleweather.app;

import java.util.Arrays;

public final class StatusUpdateTests {
    private static int checks;
    private static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    private static void close(double a,double b,String label){check(Math.abs(a-b)<.001,label+": "+a+" vs "+b);}
    public static void main(String[] args){
        check(DisplayOrder.status("").equals(Arrays.asList(0,1,2,3,8,4,5,6,7)),"route between places and sensors by default");
        check(DisplayOrder.status("  ").equals(DisplayOrder.status("")),"blank saved order uses default");
        check(DisplayOrder.status("8,4,3,2,1,0,7,6,5").equals(Arrays.asList(8,4,3,2,1,0,7,6,5)),"custom ordering retains stable IDs");
        check(DisplayOrder.status("3,3,99,8").equals(Arrays.asList(3,8,0,1,2,4,5,6,7)),"partial ordering recovers valid IDs without duplicates");
        for(float target:new float[]{.1f,.25f,.5f,1,2,16,64,90}){
            RouteViewport v=new RouteViewport();v.pan(71,-35);
            double beforeX=120*v.zoom+v.panX,beforeY=-46*v.zoom+v.panY,oldZoom=v.zoom;
            v.zoomTo(target);double ratio=v.zoom/oldZoom;
            close(120*v.zoom+v.panX,beforeX*ratio,"zoom scales X offset around canvas center after drag");
            close(-46*v.zoom+v.panY,beforeY*ratio,"zoom scales Y offset around canvas center after drag");
            check(v.zoom>=.1f&&v.zoom<=1000f/30,"zoom stays within range");
            v.zoomTo(1);close(v.panX,71,"returning zoom restores horizontal offset");close(v.panY,-35,"returning zoom restores vertical offset");
        }
        RouteViewport v=new RouteViewport();v.pan(-37,53);v.zoomTo(2);v.pan(15,-9);v.zoomTo(4);
        close(v.panX,(-74+15)*2,"second drag followed by zoom remains centered");
        close(v.panY,(106-9)*2,"second drag followed by zoom remains centered vertically");
        v.zoomTo(Float.NaN);close(v.zoom,4,"ignore invalid zoom");v.zoomTo(Float.POSITIVE_INFINITY);close(v.zoom,4,"ignore infinite zoom");
        v.center();close(v.panX,0,"centering clears X pan");close(v.panY,0,"centering clears Y pan");close(v.zoom,4,"centering retains zoom");
        v.reset();close(v.zoom,1,"fitting route resets zoom");close(v.panX,0,"fitting route resets X pan");close(v.panY,0,"fitting route resets Y pan");
        System.out.println("StatusUpdateTests: "+checks+" checks passed");
    }
}
