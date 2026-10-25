# 1.0.8 跟进

本版已采用下文提出的 57、67 强度范围，分别为“中或大冻毛毛雨”“中或大冻雨”；Open-Meteo 官方计算源码也采用包含中等强度的名称。81、86 等留待后续决定。以下是 1.0.7 时的核查报告，表中的“当前中文”指当时版本。

---

# Open-Meteo 与 WMO 英文电码核对

核查日期：2026-10-25；本次版本：1.0.7。仅修改用户指定的95、96、97、99命名和99图标，其他代码的差异供下个版本决策。

## 官方依据

- [Open-Meteo Forecast API 文档](https://open-meteo.com/en/docs#weather_variable_documentation)：接口提供数值天气代码，表格中的英文是服务商的天气解释。
- [WMO Codes Registry · Present weather](https://codes.wmo.int/bufr4/codeflag/0-20-003?_view=all)：WMO官方当前天气电码；本次比较其0–99区段。该注册表还包含其他区段，不能将其全部解释成Open-Meteo支持的代码。
- [WMO 99的完整英文原文](https://codes.wmo.int/bufr4/codeflag/0-20-003/99?_view=all)。原文为“Thunderstorm, heavy, with hail* at time of observation”，heavy修饰Thunderstorm。
- [WMO官方组织维护的BUFR电码CSV](https://github.com/wmo-im/BUFR4/blob/master/BUFRCREX_CodeFlag_en_20.csv)：筛选FXY=020003与相应CodeFigure，和注册表交叉核对。
- [Open-Meteo官方WeatherCode实现（固定提交）](https://github.com/open-meteo/open-meteo/blob/3a64572c7797738300a5d1d87081a7cbd8f35b3c/Sources/App/Helper/WeatherCode.swift)：包含根据模型云量、降水、降雪、能见度、不稳定度等计算代码的逻辑。此处仅用于核查，未复制其源码进入应用。

## 本版雷暴组的改法

| 代码 | Open-Meteo简写 | WMO英文的核心含义 | 1.0.7短标签 |
| --- | --- | --- | --- |
| 95 | Thunderstorm | slight or moderate，without hail，with rain and/or snow | 小或中雷暴 |
| 96 | Thunderstorm with slight hail | slight or moderate修饰Thunderstorm，with hail | 小或中雷暴伴冰雹 |
| 97 | Heavy thunderstorm | heavy修饰Thunderstorm，without hail，with rain and/or snow | 大雷暴 |
| 99 | Thunderstorm with heavy hail | heavy修饰Thunderstorm，with hail | 大雷暴伴冰雹 |

以上是界面短标签，没有逐字包含WMO的“观测时”“伴雨和/或雪”等全部观测条件。95与97仍有雨线；96保留两枚冰雹圆点，99在原两点基础上新增两点。圆点数量只是图标区分，不代表冰雹颗数预报或冰雹直径。

## 适合下个版本讨论的差异

| 代码 | 当前Open-Meteo英文 | WMO英文多出的/不同的含义 | 当前中文及后续选项 |
| --- | --- | --- | --- |
| 57 | Dense freezing drizzle | moderate or heavy (dense)，含中等强度 | 大冻毛毛雨；可讨论改为“中或大冻毛毛雨” |
| 67 | Heavy freezing rain | moderate or heavy，含中等强度 | 大冻雨；可讨论改为“中或大冻雨” |
| 81 | Moderate rain showers | moderate or heavy，含大强度 | 中阵雨；可讨论改为“中或大阵雨” |
| 86 | Heavy snow showers | moderate or heavy，含中等强度 | 大阵雪；可讨论改为“中或大阵雪” |
| 45 | Fog | fog or ice fog，sky invisible，前一小时无显著变化 | 雾；服务商省略冰雾、天空可见性和变化条件 |
| 48 | Depositing rime fog | sky visible | 沉积雾凇的雾；服务商省略天空可见性条件 |
| 51/53/55、61/63/65、71/73/75 | 各强度毛毛雨、雨、雪 | continuous；毛毛雨和雨还明确not freezing | 当前中文省略“持续性/非冻结”；模型预报不一定能还原这些观测条件 |
| 77 | Snow grains | with or without fog | 米雪；服务商省略“有雾或无雾”，不改变米雪核心含义 |

**0–3是语义重用，不能当成普通漏译。** Open-Meteo分别表示晴、少云、多云、阴；WMO原始观测表分别描述云发展未观测、云消散/减弱、天空状态大致不变、云形成/发展，并关注过去一小时的变化。官方模型代码实现根据当前云量区间选取0–3。若直接改成WMO原文中文，反而会错误描述接口结果，因此本版保留晴/少云/多云/阴。

82在两份英文中均为violent rain showers，不属于“Open-Meteo与WMO英文不一致”的情形；现用“大阵雨”属于此前指定的中文命名，可另行讨论，不能将其列为英文表差异。

## 为什么没有写“Open-Meteo不做二次处理”

“接口返回代码而非中文”不等于“服务端没有处理天气数据”。官方实现确实包含模型变量到天气代码的计算。因此图例说明写明返回的是数值代码、中文由本应用解释、本版雷暴组参考WMO命名，同时提醒它是可能经模型计算的预报代码；不将它冒充未经处理的原始地面观测电码。

## 逐码英文对照（29种）

下列内容摘录WMO官方英文条目及相关强度子项，并列服务商当前英文简写；没有将这些差异自动改入其他天气名称。

| 代码 | Open-Meteo官方英文 | WMO官方英文条目及子项 |
| --- | --- | --- |
| 0 | Clear sky | Cloud development not observed or not observable; Characteristic change of the state of sky during the past hour |
| 1 | Mainly clear | Clouds generally dissolving or becoming less developed; Characteristic change of the state of sky during the past hour |
| 2 | Partly cloudy | State of sky on the whole unchanged; Characteristic change of the state of sky during the past hour |
| 3 | Overcast | Clouds generally forming or developing; Characteristic change of the state of sky during the past hour |
| 45 | Fog | Fog or ice fog, sky invisible; no appreciable change during the preceding hour |
| 48 | Depositing rime fog | Fog, depositing rime, sky visible |
| 51 | Light drizzle | Drizzle, not freezing, continuous; slight at time of observation |
| 53 | Moderate drizzle | Drizzle, not freezing, continuous; moderate at time of observation |
| 55 | Dense drizzle | Drizzle, not freezing, continuous; heavy (dense) at time of observation |
| 56 | Light freezing drizzle | Drizzle, freezing, slight |
| 57 | Dense freezing drizzle | Drizzle, freezing, moderate or heavy (dense) |
| 61 | Slight rain | Rain, not freezing, continuous; slight at time of observation |
| 63 | Moderate rain | Rain, not freezing, continuous; moderate at time of observation |
| 65 | Heavy rain | Rain, not freezing, continuous; heavy at time of observation |
| 66 | Light freezing rain | Rain, freezing, slight |
| 67 | Heavy freezing rain | Rain, freezing, moderate or heavy |
| 71 | Slight snowfall | Continuous fall of snowflakes; slight at time of observation |
| 73 | Moderate snowfall | Continuous fall of snowflakes; moderate at time of observation |
| 75 | Heavy snowfall | Continuous fall of snowflakes; heavy at time of observation |
| 77 | Snow grains | Snow grains (with or without fog) |
| 80 | Slight rain showers | Rain shower(s), slight |
| 81 | Moderate rain showers | Rain shower(s), moderate or heavy |
| 82 | Violent rain showers | Rain shower(s), violent |
| 85 | Slight snow showers | Snow shower(s), slight |
| 86 | Heavy snow showers | Snow shower(s), moderate or heavy |
| 95 | Thunderstorm | Thunderstorm, slight or moderate, without hail, but with rain and/or snow at time of observation; Thunderstorm at time of observation |
| 96 | Thunderstorm with slight hail * | Thunderstorm, slight or moderate, with hail at time of observation; Thunderstorm at time of observation |
| 97 | Heavy thunderstorm | Thunderstorm, heavy, without hail, but with rain and/or snow at time of observation; Thunderstorm at time of observation |
| 99 | Thunderstorm with heavy hail * | Thunderstorm, heavy, with hail at time of observation; Thunderstorm at time of observation |

WMO注册表页脚注明，除另有说明外，内容按[Open Government Licence v3.0](https://www.nationalarchives.gov.uk/doc/open-government-licence/version/3/)提供；本表含对WMO英文条目的摘录与中文解释，不表示WMO对本应用背书。模型代码仅链接核查，应用无新增运行时依赖或API。
