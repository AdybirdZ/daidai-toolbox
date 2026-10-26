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
    static String sources(EarthquakeData.Event event){java.util.StringJoiner names=new java.util.StringJoiner("、");for(EarthquakeData.Report r:event.reports)names.add(source(r.source));return names.toString();}
}
