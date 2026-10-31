package org.simpleweather.app;

import java.util.LinkedHashMap;
import java.util.Map;

/** Match Y-plane images to their own capture metadata, regardless of callback order. */
final class CameraLightSamples {
    static final class Reading {
        final long timestamp,exposureNanos,receivedAt;
        final int profile,iso,mode;
        final float brightness;
        Reading(long timestamp,int profile,long exposure,int iso,int mode,float brightness,long receivedAt){
            this.timestamp=timestamp;this.profile=profile;this.exposureNanos=exposure;
            this.iso=iso;this.mode=mode;this.brightness=brightness;this.receivedAt=receivedAt;
        }
    }
    private static final class Pending {
        Float brightness;int profile,iso,mode;long exposure,receivedAt;boolean metadata;
    }
    private final Map<Long,Pending> pending=new LinkedHashMap<>();
    private final Reading[] latest=new Reading[3]; // indoor, outdoor, automatic fallback
    private Pending pending(long stamp){
        Pending p=pending.get(stamp);
        if(p==null){p=new Pending();pending.put(stamp,p);}
        while(pending.size()>24)pending.remove(pending.keySet().iterator().next());
        return p;
    }
    synchronized void image(long stamp,float brightness,long receivedAt){
        if(stamp<=0||!Float.isFinite(brightness)||brightness<0||brightness>255)return;
        Pending p=pending(stamp);p.brightness=brightness;p.receivedAt=receivedAt;publish(stamp,p);
    }
    synchronized void metadata(long stamp,int profile,long exposure,int iso,int mode){
        if(stamp<=0||profile<0||profile>2||mode<1||mode>3)return;
        // A named parameter profile is only valid when the device confirms manual exposure.
        if(profile<2&&(mode!=1||exposure<=0||iso<=0))return;
        Pending p=pending(stamp);p.profile=profile;p.exposure=exposure;p.iso=iso;p.mode=mode;p.metadata=true;publish(stamp,p);
    }
    private void publish(long stamp,Pending p){
        if(p.brightness==null||!p.metadata)return;
        Reading old=latest[p.profile];
        if(old==null||stamp>old.timestamp)latest[p.profile]=new Reading(stamp,p.profile,p.exposure,p.iso,p.mode,p.brightness,p.receivedAt);
        pending.remove(stamp);
    }
    synchronized Reading get(int profile){return latest[profile];}
    synchronized void clear(){pending.clear();java.util.Arrays.fill(latest,null);}
}
