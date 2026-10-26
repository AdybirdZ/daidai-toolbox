package org.simpleweather.app;

/** Adapted from GlobalQuake, commit 00d63afae10fcb910f7609873bf85c8d80566eaa.
 * Copyright (c) 2023 xspanger3770. MIT; full notice in assets/GlobalQuake-MIT.txt.
 * GeoUtils.pgaFunction/Gen2, EarthquakeAnalysis.getDepthCorrection, MMIIntensityScale.
 * These are broad numerical reference estimates, NOT a validated global IPE domain.
 */
final class EarthquakeReferenceIntensity {
    private static final double[] THRESHOLDS={.5,1,2.1,5,11,26,60,140,321.8,740,1702,3000};
    static double acceleration(double magnitude,double depth,double epicentralDistance){
        if(!Double.isFinite(magnitude)||magnitude < -2||magnitude>10.5||!Double.isFinite(depth)||depth<0||depth>800||!Double.isFinite(epicentralDistance)||epicentralDistance<0)return Double.NaN;
        double correction=Math.log10(depth+160)-Math.log10(160);
        double adjustedMagnitude=magnitude+.4*correction;
        double distance=Math.hypot(depth,epicentralDistance)/(1+.75*correction);
        return Math.pow(10,adjustedMagnitude*.575)/(.36*Math.pow(distance,1.25+adjustedMagnitude/22)+10);
    }
    // 0 means below the old model's first display threshold; MMI has no zero degree.
    static int level(double acceleration){
        if(!Double.isFinite(acceleration)||acceleration<0)return -1;
        int level=0;for(double threshold:THRESHOLDS){if(acceleration<threshold)break;level++;}return level;
    }
}
