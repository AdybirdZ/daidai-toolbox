# 地震页面与扩展烈度参考 · 1.1.3

2026-10-26；versionCode 18。目录来源、Wolfx 默认关闭和确认机制及数据权利边界沿用 [1.1.2](EARTHQUAKE-1.1.2.md)。无新增地震在线来源或预警功能。

## 参考算法与许可

GlobalQuake 原作者官方仓库：`xspanger3770/GlobalQuake`。本版核查固定历史提交 **00d63afae10fcb910f7609873bf85c8d80566eaa**（旧 main）的 [MIT LICENSE](https://github.com/xspanger3770/GlobalQuake/blob/00d63afae10fcb910f7609873bf85c8d80566eaa/LICENSE)，版权 (c) 2023 xspanger3770。只依据该旧 MIT 快照适配，不将 [现行 announcement](https://github.com/xspanger3770/GlobalQuake/tree/announcement) 中说明的 1.0.0 后私有开发内容当作开放代码。

适配的具体原文件：

- [GeoUtils.java](https://github.com/xspanger3770/GlobalQuake/blob/00d63afae10fcb910f7609873bf85c8d80566eaa/GlobalQuakeCore/src/main/java/globalquake/utils/GeoUtils.java)：pgaFunction / pgaFunctionGen2。
- [EarthquakeAnalysis.java](https://github.com/xspanger3770/GlobalQuake/blob/00d63afae10fcb910f7609873bf85c8d80566eaa/GlobalQuakeCore/src/main/java/globalquake/core/earthquake/EarthquakeAnalysis.java)：getDepthCorrection。
- [MMIIntensityScale.java](https://github.com/xspanger3770/GlobalQuake/blob/00d63afae10fcb910f7609873bf85c8d80566eaa/GlobalQuakeCore/src/main/java/globalquake/core/intensity/MMIIntensityScale.java) 和 [IntensityScale.java](https://github.com/xspanger3770/GlobalQuake/blob/00d63afae10fcb910f7609873bf85c8d80566eaa/GlobalQuakeCore/src/main/java/globalquake/core/intensity/IntensityScale.java)：阈值及按下界选档逻辑。
- [ShakeMap.java](https://github.com/xspanger3770/GlobalQuake/blob/00d63afae10fcb910f7609873bf85c8d80566eaa/GlobalQuakeClient/src/main/java/globalquake/intensity/ShakeMap.java)：核对震中参考处使用深度作距离；不移植地图、网格或任何依赖。

新增 EarthquakeReferenceIntensity 只包含必要计算、输入校验及阈值选档；完整 MIT 通知在源码和 APK 的 `assets/GlobalQuake-MIT.txt`（源码路径 `app/src/main/assets/GlobalQuake-MIT.txt`）。没有引入整个 GlobalQuake 引擎、其站网/波形数据或第三方代码依赖。代码许可不替代 CENC 等目录数据许可。

## 如何扩大范围

显示优先级仍为：来源公众震感 cdi → 来源 ShakeMap mmi → 原论文模型（符合保守条件）→ GlobalQuake 扩展参考算法。两类模型都始终标“估算”，不是正式最大烈度或参考位置烈度。中国地震烈度不由 MMI 直接转换。

原 Allen/Wald/Worden（2012）公式及 Mw、深度等限制保持 [1.1.2 所述](EARTHQUAKE-1.1.2.md#本地经验模型)，不把它硬外推成适合所有地震。

扩展参考在**震中处**令深度 h、来源震级 M：

`c = log10(h+160) − log10(160)`

`m = M + 0.4c`

`r = h / (1+0.75c)`

`P = 10^(0.575m) / (0.36r^(1.25+m/22) + 10)`

参考原项目 MMI 档 I～XII 的 P 下界依次为 0.5、1、2.1、5、11、26、60、140、321.8、740、1702、3000。仅显示档位，不把这一离散参考包装成来源给出的连续精确烈度。P<0.5 显示“未达模型的 MMI I 显示阈值”，不凭空定义 MMI 0 或零震动，也不把无法分档说成参数缺失。

本应用接受目录原有的有效震级 −2～10.5、深度 0～800 km，扩展模型可计算这些范围；这只是**数值输入边界，不是科学验证覆盖范围**。原项目自称 experimental，旧模型未在本次核查中证明经过全球各震级、深度及构造类型的系统标定。因此能显示估算不等于估算可靠，不能宣称与 GlobalQuake 当前闭源版本相同。

非 Mw 记录按来源震级近似，标签提示；10 km 或已标记默认／固定深度也提示。深度缺失不采用假定 10 km。多个来源仍保留各自震级类型，不平均、不悄悄做 ML/mb/Ms→Mw 转换。原论文模型与扩展模型不是统一标定的数值序列，不能据其边界附近结果作精密比较。场地效应、破裂尺度、构造环境未逐事件确认，结果仅供参考，不用于防灾判断。

## 页面、默认值及术语

刷新区只留刷新目录和定位位置模式下的刷新定位；原时间范围和筛选设置按钮删除，查询条件仍显示为普通摘要。所有筛选入口统一在设置 → 地震。时间选项称“最近 1 天”；地震设置内重复的数据来源与隐私入口删除，关于入口保留。

隐私正文（含原天气文字、地震文字和 Wolfx 的多段提示）统一拆段并使用一个正文行高作为段间空白，字号缩放时随字号调整，不再混用内嵌空行和不同 dp 间距。

MMI 默认项在前；本次升级统一切至 MMI 一次，之后用户切回中国标度也会持久保留。恢复默认采用 MMI，不会在下次启动又覆盖其后的手动选择。

术语说明精确对应当前解析代码，不将以下标签当作质量等级排序：

| 标签 | 字段含义 |
| --- | --- |
| 自动解算 | 来源 automatic，表示自动计算，不能据此认为经过人工检查。 |
| 人工处理 | 德国来源 QuakeML evaluationMode=manual，描述处理方式；不能仅凭 manual 推出 evaluationStatus=reviewed/confirmed。 |
| 已复核 | 德国来源 evaluationStatus 为 reviewed 或 confirmed 时的合并显示名；若同时有 manual，当前界面先显示人工处理。 |
| 来源标记已复核 | 美国来源 status 或 Wolfx type=reviewed 的转述，并非应用独立复核；各机构流程可能不同。 |

全部结果都可能修订，复核不会使模型估算成为官方实测。术语页删除主震、前震、余震段落，其余定义保留。
