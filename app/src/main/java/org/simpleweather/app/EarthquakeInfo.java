package org.simpleweather.app;

import android.widget.*;

final class EarthquakeInfo {
    static String wolfxNotice(){return UiText.get("earthquake_info_001");}
    static void paragraph(MainActivity a,LinearLayout c,String text){
        TextParagraphs.append(a,c,text,14,a.INK);
    }
    static void append(MainActivity a,LinearLayout c){
        c.addView(a.text(UiText.get("earthquake_info_002"),17,a.TITLE,true));a.gap(c,12);
        paragraph(a,c,UiText.get("earthquake_info_003"));
        int option=a.preferences.getInt("global_time_mode",a.preferences.getInt("quake_timezone",0));java.time.ZoneId zone=EarthquakeOptions.zone(option);
        paragraph(a,c,UiText.get("earthquake_info_004")+EarthquakeOptions.offset(zone,System.currentTimeMillis())+UiText.get("earthquake_info_005")+(option==0?UiText.get("earthquake_info_006"):option==1?UiText.get("earthquake_info_007"):UiText.get("earthquake_info_008"))+UiText.get("earthquake_info_009"));
        paragraph(a,c,UiText.get("earthquake_info_010"));
        paragraph(a,c,UiText.get("earthquake_info_011"));
        paragraph(a,c,UiText.get("earthquake_info_012"));
        paragraph(a,c,UiText.get("earthquake_info_013"));
        paragraph(a,c,UiText.get("earthquake_info_014"));
        paragraph(a,c,UiText.get("earthquake_info_015"));
        paragraph(a,c,wolfxNotice()+UiText.get("earthquake_info_016"));
        paragraph(a,c,UiText.get("earthquake_info_017"));
    }
    static void links(MainActivity a,LinearLayout c){
        a.link(c,UiText.get("earthquake_info_018"),"https://geofon.gfz.de/eqinfo/faq/");a.link(c,UiText.get("earthquake_info_019"),"https://www.seismicportal.eu/terms.html");a.link(c,UiText.get("earthquake_info_020"),"https://earthquake.usgs.gov/fdsnws/event/1/");a.link(c,UiText.get("earthquake_info_021"),"https://earthquake.usgs.gov/data/comcat/");a.link(c,UiText.get("earthquake_info_022"),"https://www.usgs.gov/information-policies-and-instructions/copyrights-and-credits");a.link(c,UiText.get("earthquake_info_023"),"https://doi.org/10.1007/s10950-012-9278-7");a.link(c,UiText.get("earthquake_info_024"),"https://github.com/xspanger3770/GlobalQuake/tree/00d63afae10fcb910f7609873bf85c8d80566eaa");a.link(c,UiText.get("earthquake_info_025"),"https://wolfx.jp/zh/docs/open-api/");a.link(c,UiText.get("earthquake_info_026"),"https://wolfx.jp/zh/legal/terms/");a.link(c,UiText.get("earthquake_info_027"),"https://www.cenc.ac.cn/");
    }
    static void terms(MainActivity a){
        LinearLayout c=a.column();c.setPadding(a.dp(18),a.dp(8),a.dp(18),a.dp(16));
        paragraph(a,c,UiText.get("earthquake_info_028"));
        paragraph(a,c,UiText.get("earthquake_info_029"));
        paragraph(a,c,UiText.get("earthquake_info_030"));
        paragraph(a,c,UiText.get("earthquake_info_031"));
        paragraph(a,c,UiText.get("earthquake_info_032"));
        paragraph(a,c,UiText.get("earthquake_info_033"));
        paragraph(a,c,UiText.get("earthquake_info_034"));
        paragraph(a,c,UiText.get("earthquake_info_035"));
        paragraph(a,c,UiText.get("earthquake_info_036"));
        a.link(c,UiText.get("earthquake_info_037"),"https://earthquake.usgs.gov/learn/glossary/");ScrollView s=new ScrollView(a);s.addView(c);new ToolDialog(a).setTitle(UiText.get("earthquake_info_038")).setView(s).setNegativeButton(UiText.get("action_back"),null).show();
    }
}
