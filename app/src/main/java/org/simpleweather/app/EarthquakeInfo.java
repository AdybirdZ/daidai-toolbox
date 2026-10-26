package org.simpleweather.app;

import android.widget.*;

final class EarthquakeInfo {
    static final String WOLFX_NOTICE="Wolfx 是非官方转发服务，当前只接入中国地震台网近期最多约 50 条目录；不是全球目录，不是预警。\n\n上游面向不特定用户公开展示的适用依据尚未完全确认，未宣称取得中国地震台网再发布授权。CORS、技术可访问、个人非商业用途、署名和免责声明均不能替代授权。\n\n启用后会向 Wolfx 发起目录请求，对方可接收 IP；不会发送参考经纬度。接口不保证完整覆盖所选天数、完整收录或固定延迟。\n\n如果官方或相关权利人要求停用，我会立刻停止相关接入、下架我维护的相关安装包，并配合处理。可随时在数据源设置中关闭。";
    static void paragraph(MainActivity a,LinearLayout c,String text){
        for(String part:text.replace("\r\n","\n").trim().split("\n[\t ]*\n+")){
            TextView t=a.text(part,14,a.INK,false);t.setLineSpacing(a.dp(4),1);c.addView(t);a.gap(c,Math.max(1,Math.round(t.getLineHeight()/a.getResources().getDisplayMetrics().density)));
        }
    }
    static void append(MainActivity a,LinearLayout c){
        c.addView(a.text("地震数据与说明",17,a.INK,true));a.gap(c,12);
        paragraph(a,c,"近期震后目录查询，不提供预警、地震波倒计时或后台地震通知。查询不保证不漏报或固定发布延迟，空列表不表示没有地震，参数可能修订。默认最低震级为 M≥1.0，滑块可设 M≥0.0～M≥7.0；这只是筛选门槛，不是来源完整收录承诺。中国及周边采用北纬 15°～55°、东经 70°～140° 的宽范围，不是行政边界。");
        int option=a.preferences.getInt("quake_timezone",0);java.time.ZoneId zone=EarthquakeOptions.zone(option);
        paragraph(a,c,"时间显示："+EarthquakeOptions.offset(zone,System.currentTimeMillis())+"（"+(option==0?"跟随手机时区":option==1?"北京时间":"协调世界时")+"）。日期均带年份；选择采用夏令时的时区时，各事件按发生日期的实际偏移显示。中国地震台网转发目录按 UTC+08:00 解析后转换。距离为参考位置到震中的地表大圆距离，不是震源距离，在本机计算。参考位置改变后自动更新列表；地震页“刷新定位”按定位设置取一次位置，不需要先进入定位页，不会自动开启持续定位。");
        paragraph(a,c,"来源：德国地学研究中心 GEOFON（GFZ / GEVN）与欧洲—地中海地震中心（EMSC / SeismicPortal）按 CC BY 4.0 署名。Event locations were obtained from the GEOFON program of the GFZ Helmholtz Centre for Geosciences using data from the GEVN partner networks. 美国地质调查局（USGS）全球补充目录仅保留其国家地震信息中心的参数（net=us）；不复制第三方图像或作品，不将整个聚合目录概称为自创公有领域数据。匹配、筛选、时间格式化和少量地名翻译由本应用处理；合并保留各来源原参数，不平均震级。各来源并非完全独立。");
        paragraph(a,c,"烈度：优先显示美国地质调查局的公众震感汇总（cdi，标明“来源报告烈度”，不是官方实测认定）；其 ShakeMap 模型字段 mmi 仍标“估算烈度”。本地估算指震中参考值，不是最大烈度、不是参考位置处的烈度。来源更新后自动采用当前有效值，模型结果无论是否复核都不冒充实测。中国地震烈度与 MMI 不直接换算，前者暂无经核实结果。");
        paragraph(a,c,"没有来源烈度时，满足 Mw 5.0～7.9、深度大于 0 且不超过 30 km 等原有保守条件，继续采用 Allen、Wald 与 Worden（2012）经验模型；其他具有有效震级、深度的记录，采用 GlobalQuake 旧开源版本的 PGA 与 MMI 阈值参考算法。后者可计算本应用接收的 M −2～10.5、深度 0～800 km，但这只是数值接受范围，不是经论文验证的全球精度范围，不能据此认为小震、深震或不同地质构造下都可靠。两个模型的结果不可视为同一标定尺度下的精确对比。");
        paragraph(a,c,"GlobalQuake 参考算法依其旧版 MIT 许可适配，本应用未使用现行闭源版本。保留来源的震级类型，非 Mw 时注明“按来源震级近似”；使用 10 km 或已标记默认／固定深度时明确提示。不为缺失深度编造数值。未达到模型首档显示阈值时如实说明，不造出 MMI 0 度，也不冒充来源报告。场地效应、破裂几何与构造适用性未逐事件核验，估算仅供参考，不用于防灾判断。");
        paragraph(a,c,"部分目录的 10 km 可能为默认浅源深度，固定深度不代表可靠实测。Wolfx 的 intensity 字段标度和模型仍未核实，不采用该字段，也不将它认作正式最大烈度。");
        paragraph(a,c,"中国地震台网目录（Wolfx 非官方转发）默认关闭，升级时旧开关也不会绕过本版确认；确认按钮无阅读倒计时。全球覆盖由德国、欧洲和美国来源补充。Wolfx 文档另列日本气象厅近期区域目录，本版未新增该接口，不将其称作全球目录。");
        paragraph(a,c,WOLFX_NOTICE+"\n\n德国和欧洲来源的 CC BY 4.0 不扩展到中国地震台网或 Wolfx。仍待确认具体速报产品的现行分级、公开展示与转发适用条件；未提交或取得新授权。");
        paragraph(a,c,"地震请求向所选来源发送 IP、User-Agent，以及全球目录所需的时间范围、最低震级；不发送参考经纬度、课程、背景或收藏。仅地震页前台按设定刷新，默认 15 分钟；关闭自动刷新时空缓存也不请求。手动至少间隔一分钟，遵守来源限流。目录及烈度字段缓存在本机，失败保留上次结果；可清除缓存，设置和限流记录保留。关闭来源后不发新请求，也不在列表采用该来源的缓存。");
    }
    static void links(MainActivity a,LinearLayout c){
        a.link(c,"德国地学研究中心 · 来源与许可","https://geofon.gfz.de/eqinfo/faq/");a.link(c,"欧洲—地中海地震中心 · 许可与条款","https://www.seismicportal.eu/terms.html");a.link(c,"美国地质调查局 · 全球目录接口文档","https://earthquake.usgs.gov/fdsnws/event/1/");a.link(c,"美国地质调查局 · 数据字段说明","https://earthquake.usgs.gov/data/comcat/");a.link(c,"美国地质调查局 · 版权与署名政策","https://www.usgs.gov/information-policies-and-instructions/copyrights-and-credits");a.link(c,"MMI 经验模型 · 原始论文","https://doi.org/10.1007/s10950-012-9278-7");a.link(c,"GlobalQuake · 参考算法与 MIT 许可旧版本","https://github.com/xspanger3770/GlobalQuake/tree/00d63afae10fcb910f7609873bf85c8d80566eaa");a.link(c,"Wolfx · 开放 API 文档","https://wolfx.jp/zh/docs/open-api/");a.link(c,"Wolfx · 使用条款","https://wolfx.jp/zh/legal/terms/");a.link(c,"中国地震台网中心 · 官方网站","https://www.cenc.ac.cn/");
    }
    static void terms(MainActivity a){
        LinearLayout c=a.column();c.setPadding(a.dp(18),a.dp(8),a.dp(18),a.dp(16));
        paragraph(a,c,"震级：描述一次地震释放能量的相对大小。Mw 为矩震级、ML 为地方震级、mb 为体波震级、Ms 为面波震级；不同类型不宜机械比较。仅标 M 表示来源未说明类型。负震级也可存在，本应用筛选滑块最低为 M≥0.0。");
        paragraph(a,c,"烈度：某地点受到的震动影响程度，同一次地震在不同地点可有不同烈度。中国地震烈度与修订麦加利烈度（MMI）均常用 I～XII 表达，但定义、调查方法和判据不同，不是震级，也不能按同一个数直接互换。");
        paragraph(a,c,"最大烈度与震中烈度：前者为调查或模型范围内最大的烈度，后者指震中附近；最大烈度不一定出现在震中。列表里的本地经验估算是震中 MMI，不能当作你所在位置的烈度或官方最大烈度。公众震感汇总来自问卷，模型烈度来自计算，均可能随后修订。");
        paragraph(a,c,"震中、震源与深度：震源是地下破裂开始的位置，震中是其在地表的对应位置；深度是震源距地表的距离。本应用的距离为参考位置到震中的地表距离，不是到地下震源的直线距离。");
        paragraph(a,c,"地震类型：天然地震可与断层构造运动、火山活动或塌陷等有关；还可有诱发地震及爆破、核试验等非天然事件。本应用仅展示来源标为地震或对应地震类型的记录，不根据位置自行推断成因，也不把采石爆破当作地震。");
        paragraph(a,c,"自动解算：来源将结果标记为 automatic 或自动计算，由系统处理观测数据得到，尚不能据此认为经过人工检查；参数可能修订。");
        paragraph(a,c,"人工处理：德国地学研究中心的 QuakeML evaluationMode 标为 manual，表示参数计算或处理有人参与。这是处理方式，单凭 manual 不能推出它已达到 reviewed 或 confirmed 的复核状态。");
        paragraph(a,c,"已复核：德国地学研究中心的 QuakeML evaluationStatus 标为 reviewed 或 confirmed；本应用将两者合并为这个显示名。若同时标有 manual，列表优先显示“人工处理”，不表示复核程度较低。");
        paragraph(a,c,"来源标记已复核：美国地质调查局 status 或 Wolfx 转发的 type 标为 reviewed。这是对该来源标记的转述，不表示本应用另行核实，也不保证与其他机构的复核流程、精度或责任范围相同。没有状态时显示“未提供复核状态”。");
        paragraph(a,c,"上述标签不是从低到高的质量等级。人工处理描述方式，已复核描述状态；任何一种结果都可能修订。已复核不代表所有参数均为实测，也不会使模型估算烈度变成官方现场调查烈度。");
        a.link(c,"美国地质调查局 · 地震术语","https://earthquake.usgs.gov/learn/glossary/");ScrollView s=new ScrollView(a);s.addView(c);new ToolDialog(a).setTitle("地震术语介绍").setView(s).setNegativeButton("返回",null).show();
    }
}
