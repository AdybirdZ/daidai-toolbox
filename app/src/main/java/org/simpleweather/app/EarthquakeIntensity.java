package org.simpleweather.app;

import java.util.*;

/** Independently implemented mathematical IPE; no third-party source code is incorporated.
 * Allen, Wald & Worden (2012), DOI 10.1007/s10950-012-9278-7, hypocentral formulation.
 * Application deliberately restricts the shallow-crust / Mw / distance / intensity domain.
 */
final class EarthquakeIntensity {
    static double estimate(double mw,double depth,double epicentralDistance){
        if(!Double.isFinite(mw)||mw<5||mw>7.9||!Double.isFinite(depth)||depth<=0||depth>30||!Double.isFinite(epicentralDistance)||epicentralDistance<0)return Double.NaN;
        double r=Math.hypot(depth,epicentralDistance);if(r>=300)return Double.NaN;
        double saturation=-.209+2.042*Math.exp(mw-5);
        double value=2.085+1.428*mw-1.402*Math.log(Math.hypot(r,saturation))+(r>50?.078*Math.log(r/50):0);
        return value>=2&&value<=12?value:Double.NaN;
    }
    static String roman(double n){String[] values={"I","II","III","IV","V","VI","VII","VIII","IX","X","XI","XII"};return Double.isFinite(n)&&n>=1&&n<=12?values[Math.max(0,Math.min(11,(int)Math.round(n)-1))]:"—";}
    static String summary(EarthquakeData.Event event){
        String detail=label(event);
        if(detail.contains(UiText.get("earthquake_intensity_001")))return UiText.get("earthquake_intensity_002");
        int note=detail.indexOf(UiText.get("earthquake_info_005"));if(note>=0)detail=detail.substring(0,note);
        return detail.replace(" MMI "," ").replace(UiText.get("intensity_scale_prefix"),UiText.get("earthquake_intensity_003"));
    }
    static String label(EarthquakeData.Event event){
        EarthquakeData.Report reported=null,model=null;
        for(EarthquakeData.Report r:event.reports){if(Double.isFinite(r.reportedMmi)&&(reported==null||r.updated>reported.updated))reported=r;if(Double.isFinite(r.modelMmi)&&(model==null||r.updated>model.updated))model=r;}
        if(reported!=null)return UiText.get("earthquake_intensity_004")+roman(reported.reportedMmi)+String.format(Locale.ROOT,UiText.get("earthquake_intensity_005"),reported.reportedMmi);
        if(model!=null)return UiText.get("earthquake_intensity_006")+roman(model.modelMmi)+String.format(Locale.ROOT,UiText.get("earthquake_intensity_007"),model.modelMmi);
        for(EarthquakeData.Report r:event.reports){if(!r.magnitudeType.toLowerCase(Locale.ROOT).startsWith("mw"))continue;if(!r.depthNote.isEmpty()||Math.abs(r.depth-10)<.001)continue;double v=estimate(r.magnitude,r.depth,0);if(Double.isFinite(v))return UiText.get("earthquake_intensity_008")+roman(v)+String.format(Locale.ROOT,UiText.get("earthquake_intensity_009"),v);}
        EarthquakeData.Report reference=event.main();
        if(!Double.isFinite(reference.depth))for(EarthquakeData.Report r:event.reports)if(Double.isFinite(r.depth)){reference=r;break;}
        int level=EarthquakeReferenceIntensity.level(EarthquakeReferenceIntensity.acceleration(reference.magnitude,reference.depth,0));
        if(level>=0){
            String assumptions=(!reference.magnitudeType.toLowerCase(Locale.ROOT).startsWith("mw")?UiText.get("earthquake_intensity_010"):"")+(!reference.depthNote.isEmpty()||Math.abs(reference.depth-10)<.001?UiText.get("earthquake_intensity_011"):"");
            if(level==0)return UiText.get("earthquake_intensity_012")+assumptions+UiText.get("earthquake_api_004");
            return UiText.get("earthquake_intensity_008")+roman(level)+UiText.get("earthquake_intensity_013")+assumptions+UiText.get("earthquake_api_004");
        }
        return UiText.get("earthquake_intensity_014");
    }
}
