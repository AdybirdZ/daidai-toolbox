# 呆呆工具箱 1.0.0

核查日期：2026-10-24。用途：你已确认的个人、非商业使用。

## 数据授权结论

**Open-Meteo 的公开条款明确允许本次无广告、无订阅的个人非商业应用调用其免费 API。** 天气数据和地区数据分别按其要求署名，并链接 CC BY 4.0 许可。这个结论有用途和额度条件；不能把它扩大为免费接口可用于任何商业运营。

| 内容 | 实际采用的来源 | 授权与本版处理 |
| --- | --- | --- |
| 当前天气、24 小时和 7 日预报 | Open-Meteo 官方 Forecast API | 数据采用 CC BY 4.0；免费服务仅限非商业用途。天气卡片旁有可点击的来源链接，详情页有许可及使用条款链接。 |
| 地区检索、内置地区的名称和坐标 | Open-Meteo Geocoding API / GeoNames | 按 CC BY 4.0 署名 GeoNames；地区页与详情页提供来源链接。内置 43 个常用地区，部分同名城市已按唯一 GeoNames 编号校正。 |
| 界面、图标和客户端代码 | 本次独立实现 | 未复制原项目的前端、城市目录、代理实现、第三方图标或字体。客户端源码按随附 MIT 许可提供；数据许可另行保留。 |

### 查证依据

官网条款页面在本次命令行访问中返回 403，因此同时核查了 Open-Meteo **官方组织维护的官网源码**及其官方服务端说明，并保留了对应提交链接。接口能返回数据本身不被当作授权证据。

- [Open-Meteo 使用条款](https://open-meteo.com/en/terms)；[已核查的官方条款源码](https://github.com/open-meteo/open-meteo-website/blob/57f3ec93ee3b01b65b9ca5269c72bbd5a3a2253e/src/routes/en/terms/%2Bpage.svelte)。其中明确写明：`You may only use the free API services for non-commercial purposes.` 并将没有广告、订阅的私人应用列为允许情形。
- [Open-Meteo 数据许可](https://open-meteo.com/en/licence)；[已核查的官方许可源码](https://github.com/open-meteo/open-meteo-website/blob/57f3ec93ee3b01b65b9ca5269c72bbd5a3a2253e/src/routes/en/licence/%2Bpage.svelte)：API 数据采用 CC BY 4.0，须署名、链接许可，并说明修改。
- [官方地区检索文档](https://open-meteo.com/en/docs/geocoding-api)；[已核查的文档源码](https://github.com/open-meteo/open-meteo-website/blob/57f3ec93ee3b01b65b9ca5269c72bbd5a3a2253e/src/routes/en/docs/geocoding-api/%2Bpage.svelte)：地区数据来自 GeoNames。
- [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/)；[商业服务说明](https://open-meteo.com/en/pricing)。**数据许可允许商用**与**免费托管 API 仅限非商业**是两个不同层面的条件。

### 原项目的处理

参考仓库：[anyin233/weather-fighter-global](https://github.com/anyin233/weather-fighter-global)，核查提交 `e75bbe601237d663bf611577e618c3354685f68f`。

该提交的根目录没有明确的整体代码 LICENSE，README 说明前端来自另一项目。其国内天气代理还包含为接口补写 Referer 的实现。以上都不足以证明所有源码、数据接口可以自由再分发或继续调用，因此本版只参考“选择地区查看天气”的功能目标，重新实现客户端，改用明确提供使用条款的官方 API。未将相关国内图片接口判定为已获授权；本版采用完整官方网页浏览，详见下文。

### 本版遵守的调用条件

- 仅调用 `https://api.open-meteo.com/v1/forecast` 和 `https://geocoding-api.open-meteo.com/v1/search`。没有私有接口、抓取代理、来源请求头伪装或切换主机规避限流。
- 条款规定免费额度低于每天 10,000、每小时 5,000、每分钟 600 次。服务商对多变量查询可能折算为多次调用。本版每安装限制为每分钟 20、每小时 1,000、每天 2,000 次 HTTP 请求；当前天气查询的变量数在 30 以内，按每次不超过 3 个计费调用保守控制。配额及 429 冷却状态保存在本机，重启应用不清零。服务商按 IP 等方式实际计算的额度仍以其响应为准，同一网络其他使用者也可能影响可用额度。
- 天气缓存 15 分钟，手动刷新同一地区间隔至少一分钟；合并同一地区正在进行的请求。地区查询只在点击“搜索”时联网。没有后台轮询。
- 429 响应遵守数字或 HTTP 日期形式的 `Retry-After`，至少暂停一分钟；没有该字段或解析失败时也暂停一分钟。保留失败提示，不通过备用域名绕过限制。
- 天气数值作中文描述、取整和排版；内置地区个别名称、行政标签和时区经过整理。应用内已说明数据展示处理。中国地区使用北京时间，其他地区使用服务返回的当地时区。
- 当前天气标注为“模型估算”，没有标成中国气象局台站实测，也不冒充官方预警。

商业化、接入广告、订阅或扩大运营规模前，应重新核查条款并取得对应 API 服务授权。此次核查针对所选数据和调用用途，不等于对未来上架、运营、商标、备案等事项作全面法律审查。

## 官方天气图与网页

核查了中央气象台及中国天气网公开页面。中央气象台页底明确写明“本站所刊登的信息、数据和各种专栏材料，未经授权禁止下载使用”；中国天气网雷达页写明“中国天气网版权所有，未经书面授权禁止使用”。能访问图片不等于可以取得图片并嵌入、重发。因此本版没有复制天气战士的图片代理、补写 Referer，也没有直接抓取或缓存官方图像接口。

本版入口使用应用内浏览器打开完整原站页面，保留原站版权、图例、发布时间和链接，并提供系统浏览器按钮。网页由站点自行提供，适用站点的版权及隐私规则。这不是取得了图片 API 或再分发授权。需要专门的原生雷达动画、截图下载或图像再发布时，须先取得对应授权。

- [中央气象台网站声明](https://www.nmc.cn/publish/cms/view/722665831f0a4e98800c41e691444963.html)
- [全国一小时降水](https://www.nmc.cn/publish/observations/hourly-precipitation.html)
- [全国24小时降水](https://www.nmc.cn/publish/observations/24hour-precipitation.html)
- [全国24小时降水预报](https://www.nmc.cn/publish/precipitation/1-day.html)
- [全国逐时气温](https://www.nmc.cn/publish/observations/hourly-temperature.html)
- [全国雷达](https://www.nmc.cn/publish/radar/chinaall.html)：另提供华北、东北、华东、华中、华南、西南、西北原页入口。
- [中国天气网雷达](https://www.weather.com.cn/radar/)

WebView 主页面限 HTTPS 官方域名，不忽略证书错误，无文件访问或原生 JavaScript bridge，禁网页定位及第三方 Cookie。页面可能包含站点自己的脚本和外部资源；访问会向站点及其资源服务商传输 IP、浏览器信息等，不能承诺第三方网页没有统计。网页布局、图片加载和可用性由官方维护。

## 天文与课程表参考

天文位置、月相、日月升落及曙暮光由本地独立实现的低精度公式计算，方法参考 [Paul Schlyter 天体位置计算说明](https://stjarnhimlen.se/comp/ppcomp.html)，未复制其程序源码。忽略部分轨道摄动、地形与实际大气变化，不用于测量或导航，月升落可有数分钟以上误差。星图只显示选定亮星，非完整专业星图。观星条件是基于 Open-Meteo 云量、湿度、降水概率、风速、能见度的经验筛选，未计算光污染；流星雨为常年参考，不等于当年准确预报。

极光入口打开 [NOAA 官方原页](https://www.swpc.noaa.gov/products/aurora-30-minute-forecast)，不提取重发图像，不保证本地可见。没有宣称完整复刻参考项目的所有天文模块。

课程表参考 [时光课表](https://github.com/XingHeYuZhuan/shiguangschedule) 的手动课程与节次功能。核查提交 `feabb68f75c4058319b449287980b5f645eb8d61`，其项目采用 Apache License 2.0。本版原生 Java 课程表从头实现，未复制其源码、图标或其他资源。新代码按本项目 MIT 许可提供。

## 隐私与权限

- 无账号、广告、分析 SDK 或自建服务器；不申请后台定位、广泛存储或通讯录权限。
- 天气和搜索联网：IP、搜索词、所选城市坐标发送给 Open-Meteo。地区收藏和最多20份天气缓存保存本机。GPS 坐标不自动用于天气请求。
- GPS 按需申请前台精确定位权限，只在 GPS 页启动后读取 Android GPS provider。切到其他页、锁屏或后台时停止更新，不上传坐标。显示 WGS84 经纬度、系统水平误差半径（约68%置信估计）和椭球海拔；不是测得的真实偏差保证。可拒绝定位权限而继续使用其他功能。
- 课程表内容保存在应用内部。背景通过系统文件选择器授权读取单张图片，压缩为内部 JPEG，保留方向，移除原图元数据，不上传图片。背景支持遮罩与恢复默认。卸载或清除应用数据会删除这些本地内容。
- 官方天气图和 NOAA 原页由站点自行处理访问数据，适用其规则。Android 系统定位服务本身的隐私规则亦由系统负责。

## 安装与使用

1. 安装 `DaidaiToolbox-1.0.0.apk`，Android 8.0+。首次天气城市默认北京。
2. 包名保持 `org.simpleweather.app`，versionCode 2，沿用旧包签名，可覆盖之前原标1.0.0的测试包并保留天气地区和收藏。旧版统一归档为 `0.1.0-test`；不要安装旧归档包进行降级。
3. 底部切换天气、课程表、GPS。天气页“地区”可搜索和收藏，向下滚动可查看官方天气图和天文入口。
4. 课程表的“设置”调整第一周周一、总周数及逐节上下课时间，“新增”填写课程、地点、老师、节次和周次，点击课程可编辑或删除。周课表左右滑动查看完整一周。
5. 天气或 GPS 页的“设置”、课程表“设置→背景与应用设置”，可选手机图片、调整遮罩、查看更新和来源隐私。GPS 点击开始后授权，在开阔处等待信号。
6. 更新记录含 1.0.0 和 0.1.0-test，首次升级展示，也可从设置反复查看。

接口可用性与国内网络质量以手机实际环境为准。失败时提示错误，有历史天气时明确标记；不会生成假天气。

## 构建与验证

详见源码包 `README.md` 和 `TESTING.md`。APK 使用本机开发密钥签名，供个人侧载；后续覆盖更新必须保留同一签名。源码包不包含 SDK、模拟器、测试依赖 jar 或签名私钥；重新构建生成的新密钥不能覆盖本次 APK。
