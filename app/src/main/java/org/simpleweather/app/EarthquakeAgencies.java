package org.simpleweather.app;

import java.util.*;

/** Names translated from the EMSC contributor directory, checked 2026-10-26.
 * https://www.emsc-csem.org/Earthquake_data/contributors.php
 * Unknown codes are not guessed or presented as institution names.
 */
final class EarthquakeAgencies {
    private static final Map<String,String> NAMES=new HashMap<>();
    static {
        String[][] entries={
            {"AFAD","earthquake_agencies_001"},
            {"AK","earthquake_agencies_002"},
            {"ARSO","earthquake_agencies_003"},
            {"AUST","earthquake_agencies_004"},
            {"BMKG","earthquake_agencies_005"},
            {"CSN","earthquake_agencies_006"},
            {"CWA","earthquake_agencies_007"},{"CWB","earthquake_agencies_008"},
            {"EMSC","earthquake_agencies_009"},
            {"ETHZ","earthquake_agencies_010"},{"ZUR","earthquake_agencies_010"},
            {"GFZ","earthquake_agencies_011"},
            {"GNS","earthquake_agencies_012"},
            {"GSD","earthquake_agencies_013"},
            {"GSRAS","earthquake_agencies_014"},
            {"HV","earthquake_agencies_015"},
            {"IGC","earthquake_agencies_016"},
            {"IGEO","earthquake_agencies_017"},
            {"IGN","earthquake_agencies_018"},
            {"INET","earthquake_agencies_019"},
            {"INGV","earthquake_agencies_020"},
            {"IOGA","earthquake_agencies_021"},
            {"JMA","earthquake_agencies_022"},
            {"LED","earthquake_agencies_023"},
            {"LIM","earthquake_agencies_024"},
            {"MB","earthquake_agencies_025"},
            {"MCSM","earthquake_agencies_026"},
            {"MLT","earthquake_agencies_027"},
            {"NC","earthquake_agencies_028"},
            {"NDI","earthquake_agencies_029"},
            {"NEIC","earthquake_agencies_030"},
            {"USGS","earthquake_agencies_031"},
            {"NM","earthquake_agencies_032"},
            {"NOA","earthquake_agencies_033"},
            {"NSC","earthquake_agencies_034"},
            {"NSNA","earthquake_agencies_035"},
            {"OGS","earthquake_agencies_036"},
            {"OSC","earthquake_agencies_037"},
            {"PDA","earthquake_agencies_038"},
            {"PGI-NRI","earthquake_agencies_039"},
            {"PIVS","earthquake_agencies_040"},
            {"PR","earthquake_agencies_041"},
            {"QUI","earthquake_agencies_042"},
            {"RENASS","earthquake_agencies_043"},
            {"SASN","earthquake_agencies_044"},
            {"SCSN","earthquake_agencies_045"},
            {"THE","earthquake_agencies_046"},
            {"TSB","earthquake_agencies_047"},
            {"TX","earthquake_agencies_048"},
            {"UNA","earthquake_agencies_049"},
            {"UNM","earthquake_agencies_050"},
            {"UU","earthquake_agencies_051"},
            {"UW","earthquake_agencies_052"},
            {"YSVOC","earthquake_agencies_053"},
            {"CENC","earthquake_agencies_054"},
            {"KOERI","earthquake_agencies_055"},
            {"GFU","earthquake_agencies_056"},
            {"KNMI","earthquake_agencies_057"},
            {"LDG","earthquake_agencies_058"},
            {"IPMA","earthquake_agencies_038"},
            {"NORSAR","earthquake_agencies_059"},
            {"KNDC","earthquake_agencies_060"},
            {"NSSC","earthquake_agencies_061"},
            {"NSSP","earthquake_agencies_062"},
            {"SNSN","earthquake_agencies_063"},
            {"GSA","earthquake_agencies_064"},
            {"BGS","earthquake_agencies_065"},
            {"BGR","earthquake_agencies_066"}
        };
        for(String[] entry:entries)NAMES.put(entry[0],entry[1]);
    }
    static String name(String code){
        if(code==null||code.trim().isEmpty())return UiText.get("earthquake_agencies_067");
        String value=code.trim();String known=NAMES.get(value.toUpperCase(Locale.ROOT));
        if(known!=null)return UiText.get(known);
        if(value.equals(UiText.raw("identity_earthquake_data_004")))return UiText.get(NAMES.get("NEIC"));
        if(value.startsWith(UiText.raw("identity_earthquake_data_003")))return UiText.get(NAMES.get("CENC"));
        if(value.matches(".*[\\u4e00-\\u9fff].*")&&!value.matches(".*[A-Za-z].*"))return value;
        return UiText.get("earthquake_agencies_069");
    }
}
