package org.simpleweather.app;

import android.content.*;
import android.graphics.*;
import android.location.Location;
import android.view.*;
import org.json.*;
import java.util.*;

public final class RoutePoint {
    public final double lon,lat; public final long time; public final float accuracy;
    public RoutePoint(double lon,double lat,long time,float accuracy){this.lon=lon;this.lat=lat;this.time=time;this.accuracy=accuracy;}
}

/** Small on-device route recorder. It stores only completed routes in a JSON preference. */
