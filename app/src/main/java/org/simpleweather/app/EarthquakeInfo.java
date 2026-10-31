package org.simpleweather.app;

import android.widget.*;

final class EarthquakeInfo {
    static final String WOLFX_NOTICE="Wolfx 为非官方数据转发服务。本应用仅接入其中国地震台网近期目录，接口最多提供约 50 条记录，不提供全球目录或地震预警。\n\n中国地震台网数据通过该服务向不特定用户公开展示的授权依据尚未完全确认，本应用未取得中国地震台网的再发布授权。跨域访问支持（CORS）、接口可访问、非商业用途、署名及免责声明均不能替代授权。\n\n启用后，应用会向 Wolfx 请求目录，服务方可接收 IP 地址；请求不包含参考位置坐标。接口不保证完整覆盖所选时段、收录全部地震或在固定时间内更新。\n\n如官方机构或相关权利人要求停用，维护者将立即停止接入，下架其维护的相关安装包，并配合处理。可随时在地震数据源设置中关闭此服务。";
    static void paragraph(MainActivity a,LinearLayout c,String text){
        TextParagraphs.append(a,c,text,14,a.INK);
    }
    static void append(MainActivity a,LinearLayout c){
        c.addView(a.text("地震数据与说明",17,a.TITLE,true));a.gap(c,12);
        paragraph(a,c,"本功能提供近期地震目录查询，不提供地震预警、地震波到达倒计时或后台地震通知。目录可能遗漏地震，发布时间不固定，事件参数也可能修订；空列表不表示没有发生地震。最低震级默认为 M≥1.0，可设为 M≥0.0 至 M≥7.0，该设置仅用于筛选，不代表数据源的完整收录范围。“中国及周边”为经度 70° E～140° E、纬度 15° N～55° N 的矩形范围，不对应行政边界。");
        int option=a.preferences.getInt("global_time_mode",a.preferences.getInt("quake_timezone",0));java.time.ZoneId zone=EarthquakeOptions.zone(option);
        paragraph(a,c,"时间显示："+EarthquakeOptions.offset(zone,System.currentTimeMillis())+"（"+(option==0?"设备时区":option==1?"北京时间":"协调世界时")+"）。日期均带年份；选择采用夏令时的时区时，各事件按发生日期的实际偏移显示。中国地震台网转发目录按 UTC+08:00 解析后转换。距离为参考位置到震中的地表大圆距离，不代表到震源的距离，计算在本机完成。参考位置改变后自动更新列表；地震页的“刷新定位”按状态设置中的定位方式获取一次位置，无需先进入状态页，也不会自动开启持续定位。");
        paragraph(a,c,"来源：德国地学研究中心 GEOFON（GFZ / GEVN）和欧洲—地中海地震中心（EMSC / SeismicPortal），按 CC BY 4.0 署名。\n\nEvent locations were obtained from the GEOFON program of the GFZ Helmholtz Centre for Geosciences using data from the GEVN partner networks.\n\n美国地质调查局（USGS）的全球补充目录仅采用其国家地震信息中心提供的参数（net=us），不复制第三方图像，也不将整套聚合目录视为公有领域数据。\n\n本应用进行事件匹配、筛选、时间格式转换和部分地名翻译。合并记录保留各来源参数，不对震级取平均；各来源也不一定完全独立。列表优先显示具体贡献机构，聚合平台及转发信息在详情中注明。同次地震优先采用中国地震台网的已复核结果，其次为其他机构的已复核结果，最后为自动结果或状态未知的记录；同级采用较新结果，不因来源为中国地震台网而直接认定已复核。");
        paragraph(a,c,"烈度优先采用美国地质调查局的公众震感汇总（cdi），标为“来源报告烈度”；该值不等同于官方现场调查结果。ShakeMap 的 mmi 字段为模型结果，标为“估算烈度”。\n\n本地估算为震中参考烈度，不代表最大烈度或参考位置处的烈度。来源更新后，应用自动采用当前有效值；模型结果即使经过复核，也不会改标为实测结果。本应用仅采用修订麦加利烈度（MMI），列表省略标度和模型括注，详情保留完整说明。");
        paragraph(a,c,"数据源未提供可用烈度时，满足 Mw 5.0～7.9、深度大于 0 且不超过 30 km 等条件的记录，采用 Allen、Wald 和 Worden（2012）经验模型；其他具有有效震级和深度的记录，采用 GlobalQuake 旧开源版本的峰值地面加速度（PGA）及 MMI 阈值参考算法。\n\n后者接受震级 −2～10.5、深度 0～800 km 的输入，但这是算法接受的数值范围，并非全球适用性或精度保证。小震、深震及不同地质构造下的结果可能存在较大误差；两种模型结果不宜视为相同标定尺度下的精确比较。");
        paragraph(a,c,"GlobalQuake 参考算法按旧版 MIT 许可适配，未使用其现行闭源版本。本应用保留来源震级类型，非 Mw 时注明“按来源震级近似”；采用 10 km 或来源标记为默认、固定深度时，在详情中提示。不估填缺失深度；未达到模型首档显示阈值时，不显示为 MMI 0 度。场地效应、断层破裂形态及构造适用性未逐事件核验，估算仅供参考，不用于防灾决策。");
        paragraph(a,c,"部分目录的 10 km 可能为默认浅源深度，固定深度不代表可靠实测。Wolfx 的 intensity 字段标度和模型仍未核实，不采用该字段，也不将它认作正式最大烈度。");
        paragraph(a,c,"中国地震台网目录由 Wolfx 非官方转发，默认关闭，启用前须确认使用提示，无强制阅读倒计时。德国、欧洲和美国来源补充全球记录。Wolfx 文档中的日本气象厅区域目录未接入本应用。");
        paragraph(a,c,WOLFX_NOTICE+"\n\n德国和欧洲来源的 CC BY 4.0 许可不适用于中国地震台网或 Wolfx 数据。具体速报产品的现行数据分级、公开展示及转发条件仍待确认；本应用尚未提交申请或取得相关新授权。");
        paragraph(a,c,"地震查询会向所选数据源发送 IP 地址、客户端标识、时间范围和最低震级，不发送参考位置坐标、课程表、背景或收藏信息。地震页处于前台时按设置自动刷新，默认间隔为 15 分钟；关闭自动刷新后，即使无缓存也不自动请求。手动刷新至少间隔 1 分钟，并遵守来源的限流要求。\n\n目录及烈度字段缓存在本机，请求失败时保留上次结果。清除缓存不删除设置或限流记录；关闭某数据源后，不再请求该来源，也不显示其缓存记录。");
    }
    static void links(MainActivity a,LinearLayout c){
        a.link(c,"德国地学研究中心 · 来源与许可","https://geofon.gfz.de/eqinfo/faq/");a.link(c,"欧洲—地中海地震中心 · 许可与条款","https://www.seismicportal.eu/terms.html");a.link(c,"美国地质调查局 · 全球目录接口文档","https://earthquake.usgs.gov/fdsnws/event/1/");a.link(c,"美国地质调查局 · 数据字段说明","https://earthquake.usgs.gov/data/comcat/");a.link(c,"美国地质调查局 · 版权与署名政策","https://www.usgs.gov/information-policies-and-instructions/copyrights-and-credits");a.link(c,"MMI 经验模型 · 原始论文","https://doi.org/10.1007/s10950-012-9278-7");a.link(c,"GlobalQuake · 参考算法与 MIT 许可旧版本","https://github.com/xspanger3770/GlobalQuake/tree/00d63afae10fcb910f7609873bf85c8d80566eaa");a.link(c,"Wolfx · 开放 API 文档","https://wolfx.jp/zh/docs/open-api/");a.link(c,"Wolfx · 使用条款","https://wolfx.jp/zh/legal/terms/");a.link(c,"中国地震台网中心 · 官方网站","https://www.cenc.ac.cn/");
    }
    static void terms(MainActivity a){
        LinearLayout c=a.column();c.setPadding(a.dp(18),a.dp(8),a.dp(18),a.dp(16));
        paragraph(a,c,"震级：衡量地震大小的量，可依据地震波振幅或地震矩等计算。Mw 为矩震级，ML 为地方震级，mb 为体波震级，Ms 为面波震级；不同类型不宜直接比较。仅标为 M 时，表示来源未注明震级类型。震级可以为负值，本应用的最低筛选条件为 M≥0.0。");
        paragraph(a,c,"烈度：某地点受到的震动影响程度，同一次地震在不同地点可有不同烈度。本应用采用修订麦加利烈度（MMI），用 I～XII 表示震感与影响程度，它不是震级。列表中的烈度数值均为 MMI，来源、模型和计算假设可在详情查看。");
        paragraph(a,c,"最大烈度与震中烈度：最大烈度为调查或模型范围内的最高烈度，震中烈度指震中附近的烈度，二者不一定相同。本应用的本地经验估算为震中 MMI，不代表参考位置处的烈度或官方最大烈度。公众震感汇总来自问卷，模型烈度来自计算，两者均可能修订。");
        paragraph(a,c,"震中、震源与深度：震源是地下破裂开始的位置，震中是其在地表的对应位置；深度是震源距地表的距离。本应用的距离为参考位置到震中的地表距离，不是到地下震源的直线距离。");
        paragraph(a,c,"地震类型：天然地震可与断层构造运动、火山活动或塌陷等有关；还可有诱发地震及爆破、核试验等非天然事件。本应用仅展示来源标为地震或对应地震类型的记录，不根据位置自行推断成因，不将采石爆破纳入地震记录。");
        paragraph(a,c,"自动结果：来源标记为自动解算，由来源机构的计算机系统分析观测数据得到。该标记不包含明确的人工处理或复核信息，但不据此判断是否曾有人查看。");
        paragraph(a,c,"已复核：本应用为简化显示，将来源标记为“人工处理”“已复核”或“已确认”的记录合并归入此类。“人工处理”仅表示有人参与，不保证完成来源机构的正式复核流程；具体标记可在详情中查看。");
        paragraph(a,c,"状态未知：来源未提供可识别的处理或复核状态，不能据此归为自动结果。");
        paragraph(a,c,"复核状态仅转述数据源标记，不表示本应用独立核验，也不构成可靠性评级。所有状态的参数均可能随后修订；复核状态不改变模型估算烈度的性质。");
        a.link(c,"美国地质调查局 · 地震术语","https://earthquake.usgs.gov/learn/glossary/");ScrollView s=new ScrollView(a);s.addView(c);new ToolDialog(a).setTitle("地震术语介绍").setView(s).setNegativeButton("返回",null).show();
    }
}
