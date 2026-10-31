package org.simpleweather.app;

import java.util.Locale;

/** Presentation only. Stored/API latitude and longitude retain their original semantics. */
final class CoordinateFormat {
    static String longitude(double value,int decimals){return angle(value,180,decimals,"E","W");}
    static String latitude(double value,int decimals){return angle(value,90,decimals,"N","S");}
    static String pair(double longitude,double latitude,int decimals){return UiText.get("coordinate_format_001")+longitude(longitude,decimals)+UiText.get("coordinate_format_002")+latitude(latitude,decimals);}
    private static String angle(double value,int limit,int decimals,String positive,String negative){
        if(!Double.isFinite(value)||Math.abs(value)>limit)return "—";
        return String.format(Locale.ROOT,"%."+Math.max(0,Math.min(8,decimals))+"f° %s",Math.abs(value),value<0?negative:positive);
    }
}
