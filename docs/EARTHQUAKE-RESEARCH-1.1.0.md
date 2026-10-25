# 地震功能调研（仅研究，未在 1.0.11 接入）

核查日期：2026-10-25。这里区分“源码实际调用了什么”与“数据可否再利用”。非商业用途、接口能访问、参考项目开源，都不能单独证明上游数据获得了再发布许可。

## 天气战士的真实实现

核查本地参考版本 `e75bbe601237d663bf611577e618c3354685f68f` 的 [web/js/api.js](https://github.com/anyin233/weather-fighter-global/blob/e75bbe601237d663bf611577e618c3354685f68f/web/js/api.js#L630)。这是本地所存版本，不声称代表其后所有上游改动。

| 内容 | 实际来源 | 客户端缓存 |
| --- | --- | --- |
| 中国地震台网 CENC 最近地震 | Wolfx `https://api.wolfx.jp/cenc_eqlist.json` | 10 分钟 |
| 日本气象厅 JMA 最近地震 | Wolfx `https://api.wolfx.jp/jma_eqlist.json` | 10 分钟 |
| 全球/历史地震补充 | 直接请求 [USGS FDSN Event API](https://earthquake.usgs.gov/fdsnws/event/1/) | 30 分钟 |
| 多地地震速报/预警汇总 | Wolfx 的 JMA、CENC、四川、福建、重庆、台湾等 JSON 接口 | 1 分钟 |

USGS 查询包含时间范围、最小震级、城市经纬度、半径和最多 400 条限制。默认以所选城市为中心、约 700 km、近 130 天；合并 CENC/JMA/USGS，以时间和位置去重。源码中的 `cwa_eew.json` 注释仅写实测可访问，未列入当时公开文档，不能据此推断许可。部分界面注释写“全部经 Wolfx”，但 USGS 调用代码实际直连，结论以调用代码为准。

## 已确认的 Wolfx 条款

- [官方中文 API 文档](https://wolfx.jp/zh/docs/open-api)，页面文档版本 v20260907。
- [官方中文服务条款](https://wolfx.jp/zh/legal/terms)，页面标注最后更新 2026-07-28。
- 第六节写明：“用户可以依照本条款和专项说明免费使用本服务。专项说明允许的合理缓存、正常自动化访问及公益开发活动不因此被禁止。”同节禁止绕过限流、过量请求，以及未经数据源条件允许的再提供、转售或其他再利用。
- 第七节写明：“来自 JMA、CENC、USGS 或其他第三方的数据、名称、商标和内容，其权利仍归原权利人并受相应条件约束。”并明确“不对第三方权利或许可范围作出保证”。

因此，能确认的是按其条件免费使用 Wolfx 服务；不能据此确认其所有上游数据都可无条件放进公开 APK，再次展示、缓存、分发。它也不是各官方机构的授权代表。该项目的开源代码许可证不能替代数据许可。

## USGS 与下一步

[FDSN 官方接口文档](https://earthquake.usgs.gov/fdsnws/event/1/)、[GeoJSON 文档](https://earthquake.usgs.gov/earthquakes/feed/v1.0/geojson.php)、[ComCat](https://earthquake.usgs.gov/data/comcat/) 和 [贡献机构列表](https://earthquake.usgs.gov/data/comcat/contributor/) 本次均可访问。ComCat 汇集多个贡献机构的数据，不能把整份聚合目录均假定为 USGS 自创内容。

本次访问 USGS 的 [版权与署名政策](https://www.usgs.gov/information-policies-and-instructions/copyrights-and-credits) 和 [网站政策](https://www.usgs.gov/policies-and-notices) 返回 HTTP 403，未取得可逐条核验的当前政策原文，因此不把通常所说的“美国联邦作品公有领域”写成对全部 ComCat 数据的已核实授权结论。

1.1.0 实施前应逐项补齐：USGS 当前数据许可及第三方例外、所用 CENC/JMA 等上游条款、署名与更新要求、服务调用限制。优先考虑有清楚许可依据的目录查询；按数据贡献机构和产品区分来源。若 CENC 转发再利用依据仍不明确，先保留官方原页入口，或向数据权利人取得明确许可。不得把“仅供个人非商业使用”当作免授权证明。

本版本没有新增任何地震请求、依赖或地震页面，也没有主动联系第三方。
