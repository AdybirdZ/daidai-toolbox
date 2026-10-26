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
    static String label(EarthquakeData.Event event,boolean chinese){
        if(chinese)return "中国地震烈度：暂无经核实结果（不由 MMI 换算）";
        EarthquakeData.Report reported=null,model=null;
        for(EarthquakeData.Report r:event.reports){if(Double.isFinite(r.reportedMmi)&&(reported==null||r.updated>reported.updated))reported=r;if(Double.isFinite(r.modelMmi)&&(model==null||r.updated>model.updated))model=r;}
        if(reported!=null)return "来源报告烈度 MMI "+roman(reported.reportedMmi)+String.format(Locale.ROOT,"（%.1f，公众震感汇总）",reported.reportedMmi);
        if(model!=null)return "估算烈度 MMI "+roman(model.modelMmi)+String.format(Locale.ROOT,"（%.1f，来源 ShakeMap 模型）",model.modelMmi);
        for(EarthquakeData.Report r:event.reports){if(!r.magnitudeType.toLowerCase(Locale.ROOT).startsWith("mw"))continue;if(!r.depthNote.equals("来源未说明深度是否固定")||Math.abs(r.depth-10)<.001)continue;double v=estimate(r.magnitude,r.depth,0);if(Double.isFinite(v))return "估算震中烈度 MMI "+roman(v)+String.format(Locale.ROOT,"（%.1f，本地经验模型）",v);}
        return "估算烈度 MMI：参数不足或超出模型适用范围";
    }
}
