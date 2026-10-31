package org.simpleweather.app;

/** Compact labels do not replace the original source processing information. */
final class EarthquakeDisplay {
    static boolean reviewed(EarthquakeData.Report r){return r.review.contains(UiText.raw("identity_earthquake_display_001"))||r.review.contains(UiText.raw("identity_earthquake_data_002"));}
    static String review(EarthquakeData.Report report){
        String raw=report.review;
        if(raw.contains(UiText.raw("identity_earthquake_display_001"))||raw.contains(UiText.raw("identity_earthquake_data_002")))return UiText.get("earthquake_data_004");
        if(raw.contains(UiText.raw("identity_earthquake_display_002")))return UiText.get("earthquake_display_001");
        return UiText.get("earthquake_display_002");
    }
    static String source(String code){return code.equals("Wolfx")?UiText.get("earthquake_display_003"):EarthquakeData.sourceName(code);}
    static String source(EarthquakeData.Report report){
        if(!report.source.equals("EMSC"))return source(report.source);
        String name=EarthquakeAgencies.name(report.agency);
        // An aggregation route is not necessarily the agency that calculated the origin.
        if(name.equals(UiText.get("earthquake_agencies_067"))||name.equals(UiText.get("earthquake_agencies_069")))
            return UiText.get("earthquake_display_004")+name+UiText.get("earthquake_api_004");
        return name;
    }
    static String sources(EarthquakeData.Event event){
        java.util.Set<String> names=new java.util.LinkedHashSet<>();
        names.add(source(event.main()));
        for(EarthquakeData.Report r:event.reports)names.add(source(r));
        return String.join(UiText.get("earthquake_data_003"),names);
    }
}
