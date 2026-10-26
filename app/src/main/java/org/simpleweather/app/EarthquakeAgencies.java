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
            {"AFAD","土耳其灾害与应急管理局地震部门"},
            {"AK","阿拉斯加地震中心"},
            {"ARSO","斯洛文尼亚环境署地震办公室"},
            {"AUST","澳大利亚地球科学局"},
            {"BMKG","印度尼西亚气象、气候与地球物理局"},
            {"CSN","智利大学地球物理系国家地震中心"},
            {"CWA","台湾中央气象署"},{"CWB","台湾中央气象部门"},
            {"EMSC","欧洲—地中海地震中心"},
            {"ETHZ","瑞士地震服务中心"},{"ZUR","瑞士地震服务中心"},
            {"GFZ","德国地学研究中心"},
            {"GNS","新西兰地质与核科学研究所地震监测网"},
            {"GSD","塞浦路斯地质调查局"},
            {"GSRAS","俄罗斯科学院地球物理调查局"},
            {"HV","夏威夷火山观测站地震台网"},
            {"IGC","巴拿马大学地球科学研究所"},
            {"IGEO","阿尔巴尼亚地拉那理工大学地球科学研究所"},
            {"IGN","西班牙国家地理研究所"},
            {"INET","尼加拉瓜国土研究所"},
            {"INGV","意大利国家地球物理与火山研究所"},
            {"IOGA","马达加斯加塔那那利佛地球物理研究与观测所"},
            {"JMA","日本气象厅"},
            {"LED","德国巴登—符腾堡州地震服务机构"},
            {"LIM","秘鲁地球物理研究所"},
            {"MB","美国蒙大拿矿业与地质局区域地震台网"},
            {"MCSM","乌克兰特殊监测主中心"},
            {"MLT","马耳他大学地震监测与研究组"},
            {"NC","美国地质调查局北加利福尼亚区域地震台网"},
            {"NDI","印度国家地震中心"},
            {"NEIC","美国地质调查局国家地震信息中心"},
            {"USGS","美国地质调查局"},
            {"NM","美国新马德里联合地震台网"},
            {"NOA","希腊雅典国家天文台地球动力学研究所"},
            {"NSC","尼泊尔矿业与地质局国家地震中心"},
            {"NSNA","阿根廷国家地震预防研究所"},
            {"OGS","意大利国家海洋学与实验地球物理研究所"},
            {"OSC","玻利维亚圣卡利克斯托观测站"},
            {"PDA","葡萄牙海洋与大气研究所"},
            {"PGI-NRI","波兰地质研究所国家研究院"},
            {"PIVS","菲律宾火山与地震研究所"},
            {"PR","波多黎各大学马亚圭斯分校地震台网与强震动项目"},
            {"QUI","厄瓜多尔国立理工大学"},
            {"RENASS","法国国家地震监测网"},
            {"SASN","南非地震台网"},
            {"SCSN","南加利福尼亚地震台网"},
            {"THE","希腊塞萨洛尼基亚里士多德大学地球物理系"},
            {"TSB","泰国地震监测局"},
            {"TX","美国得克萨斯大学奥斯汀分校经济地质局"},
            {"UNA","哥斯达黎加国立大学火山与地震观测研究所"},
            {"UNM","墨西哥国立自治大学地球物理研究所国家地震服务机构"},
            {"UU","美国犹他大学地震台站区域台网"},
            {"UW","美国华盛顿大学太平洋西北区域地震台网"},
            {"YSVOC","也门地震与火山观测中心"},
            {"CENC","中国地震台网中心"},
            {"KOERI","土耳其坎迪利天文台与地震研究所"},
            {"GFU","捷克科学院地球物理研究所"},
            {"KNMI","荷兰皇家气象研究所"},
            {"LDG","法国探测与地球物理实验室"},
            {"IPMA","葡萄牙海洋与大气研究所"},
            {"NORSAR","挪威地震研究机构"},
            {"KNDC","哈萨克斯坦国家数据中心"},
            {"NSSC","叙利亚国家地震中心"},
            {"NSSP","亚美尼亚国家地震防护机构"},
            {"SNSN","瑞典国家地震台网"},
            {"GSA","奥地利地球科学局"},
            {"BGS","英国地质调查局"},
            {"BGR","德国联邦地球科学与自然资源研究所"}
        };
        for(String[] entry:entries)NAMES.put(entry[0],entry[1]);
    }
    static String name(String code){
        if(code==null||code.trim().isEmpty())return "未提供贡献机构";
        String value=code.trim();String known=NAMES.get(value.toUpperCase(Locale.ROOT));
        if(known!=null)return known;
        if(value.equals("美国地质调查局国家地震信息中心（USGS / NEIC）"))return NAMES.get("NEIC");
        if(value.startsWith("中国地震台网中心"))return NAMES.get("CENC");
        if(value.matches(".*[\\u4e00-\\u9fff].*")&&!value.matches(".*[A-Za-z].*"))return value;
        return "机构中文名称待核对";
    }
}
