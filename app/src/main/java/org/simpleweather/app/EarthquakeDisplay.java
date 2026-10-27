package org.simpleweather.app;

/** Compact labels do not replace the original source processing information. */
final class EarthquakeDisplay {
    static String review(EarthquakeData.Report report){
        String raw=report.review;
        if(raw.contains("人工")||raw.contains("已复核"))return "已复核";
        if(raw.contains("自动"))return "自动结果";
        return "状态未知";
    }
    static String source(String code){return code.equals("Wolfx")?"中国地震台网":EarthquakeData.sourceName(code);}
    static String source(EarthquakeData.Report report){
        if(!report.source.equals("EMSC"))return source(report.source);
        String name=EarthquakeAgencies.name(report.agency);
        // An aggregation route is not necessarily the agency that calculated the origin.
        if(name.equals("未提供贡献机构")||name.equals("机构中文名称待核对"))
            return "欧洲—地中海地震中心（"+name+"）";
        return name;
    }
    static String sources(EarthquakeData.Event event){
        java.util.Set<String> names=new java.util.LinkedHashSet<>();
        names.add(source(event.main()));
        for(EarthquakeData.Report r:event.reports)names.add(source(r));
        return String.join("、",names);
    }
}
