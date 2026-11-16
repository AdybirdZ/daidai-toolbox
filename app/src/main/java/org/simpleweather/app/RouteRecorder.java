package org.simpleweather.app;

import android.content.*;
import android.graphics.*;
import android.location.Location;
import android.view.*;
import org.json.*;
import java.util.*;

public final class RouteRecorder {
    private static final Object LOCK=new Object();
    private static boolean active; private static long last=-1; private static ArrayList<RoutePoint> points=new ArrayList<>();
    public static void start(long interval){synchronized(LOCK){active=true;last=-1;points.clear();}}
    public static boolean active(){synchronized(LOCK){return active;}}
    public static int size(){synchronized(LOCK){return points.size();}}
    public static void stop(){synchronized(LOCK){active=false;last=-1;}}
    public static void onLocation(Location l){if(l==null)return;synchronized(LOCK){if(!active)return;long t=l.getTime()>0?l.getTime():System.currentTimeMillis();long interval=RouteStore.intervalMs();if(last>=0&&t-last<interval)return;last=t;points.add(new RoutePoint(l.getLongitude(),l.getLatitude(),t,l.hasAccuracy()?l.getAccuracy():Float.NaN));}}
    public static ArrayList<RoutePoint> points(){synchronized(LOCK){return new ArrayList<>(points);}}
}

