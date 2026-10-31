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
            {"AFAD",UiText.get("earthquake_agencies_001")},
            {"AK",UiText.get("earthquake_agencies_002")},
            {"ARSO",UiText.get("earthquake_agencies_003")},
            {"AUST",UiText.get("earthquake_agencies_004")},
            {"BMKG",UiText.get("earthquake_agencies_005")},
            {"CSN",UiText.get("earthquake_agencies_006")},
            {"CWA",UiText.get("earthquake_agencies_007")},{"CWB",UiText.get("earthquake_agencies_008")},
            {"EMSC",UiText.get("earthquake_agencies_009")},
            {"ETHZ",UiText.get("earthquake_agencies_010")},{"ZUR",UiText.get("earthquake_agencies_010")},
            {"GFZ",UiText.get("earthquake_agencies_011")},
            {"GNS",UiText.get("earthquake_agencies_012")},
            {"GSD",UiText.get("earthquake_agencies_013")},
            {"GSRAS",UiText.get("earthquake_agencies_014")},
            {"HV",UiText.get("earthquake_agencies_015")},
            {"IGC",UiText.get("earthquake_agencies_016")},
            {"IGEO",UiText.get("earthquake_agencies_017")},
            {"IGN",UiText.get("earthquake_agencies_018")},
            {"INET",UiText.get("earthquake_agencies_019")},
            {"INGV",UiText.get("earthquake_agencies_020")},
            {"IOGA",UiText.get("earthquake_agencies_021")},
            {"JMA",UiText.get("earthquake_agencies_022")},
            {"LED",UiText.get("earthquake_agencies_023")},
            {"LIM",UiText.get("earthquake_agencies_024")},
            {"MB",UiText.get("earthquake_agencies_025")},
            {"MCSM",UiText.get("earthquake_agencies_026")},
            {"MLT",UiText.get("earthquake_agencies_027")},
            {"NC",UiText.get("earthquake_agencies_028")},
            {"NDI",UiText.get("earthquake_agencies_029")},
            {"NEIC",UiText.get("earthquake_agencies_030")},
            {"USGS",UiText.get("earthquake_agencies_031")},
            {"NM",UiText.get("earthquake_agencies_032")},
            {"NOA",UiText.get("earthquake_agencies_033")},
            {"NSC",UiText.get("earthquake_agencies_034")},
            {"NSNA",UiText.get("earthquake_agencies_035")},
            {"OGS",UiText.get("earthquake_agencies_036")},
            {"OSC",UiText.get("earthquake_agencies_037")},
            {"PDA",UiText.get("earthquake_agencies_038")},
            {"PGI-NRI",UiText.get("earthquake_agencies_039")},
            {"PIVS",UiText.get("earthquake_agencies_040")},
            {"PR",UiText.get("earthquake_agencies_041")},
            {"QUI",UiText.get("earthquake_agencies_042")},
            {"RENASS",UiText.get("earthquake_agencies_043")},
            {"SASN",UiText.get("earthquake_agencies_044")},
            {"SCSN",UiText.get("earthquake_agencies_045")},
            {"THE",UiText.get("earthquake_agencies_046")},
            {"TSB",UiText.get("earthquake_agencies_047")},
            {"TX",UiText.get("earthquake_agencies_048")},
            {"UNA",UiText.get("earthquake_agencies_049")},
            {"UNM",UiText.get("earthquake_agencies_050")},
            {"UU",UiText.get("earthquake_agencies_051")},
            {"UW",UiText.get("earthquake_agencies_052")},
            {"YSVOC",UiText.get("earthquake_agencies_053")},
            {"CENC",UiText.get("earthquake_agencies_054")},
            {"KOERI",UiText.get("earthquake_agencies_055")},
            {"GFU",UiText.get("earthquake_agencies_056")},
            {"KNMI",UiText.get("earthquake_agencies_057")},
            {"LDG",UiText.get("earthquake_agencies_058")},
            {"IPMA",UiText.get("earthquake_agencies_038")},
            {"NORSAR",UiText.get("earthquake_agencies_059")},
            {"KNDC",UiText.get("earthquake_agencies_060")},
            {"NSSC",UiText.get("earthquake_agencies_061")},
            {"NSSP",UiText.get("earthquake_agencies_062")},
            {"SNSN",UiText.get("earthquake_agencies_063")},
            {"GSA",UiText.get("earthquake_agencies_064")},
            {"BGS",UiText.get("earthquake_agencies_065")},
            {"BGR",UiText.get("earthquake_agencies_066")}
        };
        for(String[] entry:entries)NAMES.put(entry[0],entry[1]);
    }
    static String name(String code){
        if(code==null||code.trim().isEmpty())return UiText.get("earthquake_agencies_067");
        String value=code.trim();String known=NAMES.get(value.toUpperCase(Locale.ROOT));
        if(known!=null)return known;
        if(value.equals(UiText.raw("identity_earthquake_data_004")))return NAMES.get("NEIC");
        if(value.startsWith(UiText.raw("identity_earthquake_data_003")))return NAMES.get("CENC");
        if(value.matches(".*[\\u4e00-\\u9fff].*")&&!value.matches(".*[A-Za-z].*"))return value;
        return UiText.get("earthquake_agencies_069");
    }
}
