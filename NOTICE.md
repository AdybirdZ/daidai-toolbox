# 数据署名

Weather data: Open-Meteo, https://open-meteo.com/ — CC BY 4.0.

Location data: GeoNames, https://www.geonames.org/ , supplied through the Open-Meteo Geocoding API — CC BY 4.0.

License: https://creativecommons.org/licenses/by/4.0/

The app translates weather descriptions, rounds numerical values for display, rearranges forecasts, normalizes selected location display labels, and uses Beijing time for Chinese locations. This does not imply endorsement by Open-Meteo, GeoNames or any national weather service.

`app/src/main/assets/cities.json` contains location data retrieved on 2026-10-24. Selected ambiguous presets were resolved using the official `/v1/get?id=...` endpoint and GeoNames identifiers. `tests/fixtures/beijing.json` is a weather API response retained solely as an attributed regression fixture, not bundled as live weather in the APK.

Free hosted API access is subject to separate non-commercial service terms: https://open-meteo.com/en/terms . The MIT license for the newly written app code does not replace these data or service terms.

All weather icons in this app were drawn in its source code. No upstream Weather Fighter code or visual assets are redistributed.


## Additional references in Daidai Toolbox 1.0.2

Weather maps are complete original publisher webpages from nmc.cn and weather.com.cn, not copied image assets or authorized image APIs. All relevant copyrights remain with their publishers. Aurora content has been removed in version 1.0.1.

Astronomical mathematics reference: https://stjarnhimlen.se/comp/ppcomp.html . Algorithms were independently implemented; the approximate results are not for navigation or measurement.

Timetable feature reference: https://github.com/XingHeYuZhuan/shiguangschedule , Apache License 2.0, commit feabb68f75c4058319b449287980b5f645eb8d61. No source code or assets from that repository are incorporated. The new native timetable implementation is covered by this project's MIT license.

Weather Fighter reference commit: e75bbe601237d663bf611577e618c3354685f68f. No source code or visual assets incorporated.

Version 1.0.2 adds the complete NMC FY4B satellite webpage (https://www.nmc.cn/publish/satellite/fy4b-visible.htm) and removes the weather.com.cn radar choice. Weather descriptions follow the user-requested Chinese mapping; composite codes such as 48 and 95 do not imply an exact CMA classification or endorsement.

## Reference links updated in 1.0.8

Weather Fighter Global (天气战士 · 全球版): https://github.com/anyin233/weather-fighter-global .

ShiGuangSchedule (拾光课程表): https://github.com/ShiGuangSchedule/shiguangschedule . The original XingHeYuZhuan URL redirects to this official organization. Both project names were checked against their official README on 2026-10-25. No upstream code or assets are incorporated.

This application's repository: https://github.com/AdybirdZ/daidai-toolbox .

## Deep-sky coordinate facts in 1.0.9

Approximate J2000 centers for the Pleiades and Andromeda Galaxy are from Wikidata structured statements P6257/P6258 in Q13423 and Q2469 (retrieved 2026-10-25), released under CC0: https://www.wikidata.org/wiki/Wikidata:Licensing . Numeric source revisions are recorded in docs/DEEP-SKY-SOURCES.json. No images or encyclopedia prose are incorporated. Coordinates are converted locally with this application's own approximate math; the application does not contact Wikidata at runtime. The source and coordinate system are documented in docs/ASTRONOMY-1.0.9.md.

## Moon estimates and weather-chart entry in 1.0.10

The lunar V-magnitude mathematical formula is referenced from Paul Schlyter, section 15: https://stjarnhimlen.se/comp/ppcomp.html#15 . It is independently implemented in Java using this application's computed geometry and distances; no third-party source code, webpage prose, photographs or textures are bundled. The phase illustration is drawn locally from the geometric illuminated fraction. See docs/ASTRONOMY-1.0.10.md for assumptions and limits.

The new NMC weather-chart button opens the complete publisher page: https://www.nmc.cn/publish/observations/china/dm/weatherchart-h000.htm . Publisher copyright applies; no image redistribution license is claimed.

## Regions and earthquake research in 1.0.11

Moscow (GeoNames 524901) and Paris (GeoNames 2988507) were retrieved through the official Open-Meteo geocoding endpoint on 2026-10-25; the existing GeoNames/Open-Meteo CC BY 4.0 attribution continues to apply. Earthquake sources and terms are documented for future consideration only in docs/EARTHQUAKE-RESEARCH-1.1.0.md. No earthquake client, feed data, dependency or artwork is incorporated.


## Earthquake catalogue parameters in 1.1.0 (historical)

Event locations were obtained from the GEOFON program of the GFZ Helmholtz Centre for Geosciences using data from the GEVN partner networks. GEOFON event locations, depths and magnitudes: CC BY 4.0, https://geofon.gfz.de/eqinfo/faq/ .

Credit: EMSC-CSEM SeismicPortal, https://www.seismicportal.eu . Downloaded datasets/products: CC BY 4.0, https://www.seismicportal.eu/terms.html . Website/internal database rights and service terms remain separate.

The application matches and filters event solutions, formats times/numbers and translates a few region names. Provider magnitudes are not averaged; original solutions are retained in details. Source catalogues are neither complete nor guaranteed to arrive within a fixed time. No endorsement is implied. Real CC BY 4.0 regression samples in tests/fixtures/geofon-earthquakes.xml and emsc-earthquakes.json were retrieved 2026-10-26 and are not bundled as current data.

Wolfx/CENC redistribution applicability is not confirmed; no Wolfx or earthquake early-warning stream is called in this version. Details: docs/EARTHQUAKE-1.1.0.md. Mathematical intensity estimates are not enabled without a validated applicable model.

## Wolfx-relayed CENC recent catalogue in 1.1.1 (historical)

The app now directly requests only https://api.wolfx.jp/cenc_eqlist.json (latest up to approximately 50 records), attributed as CENC via the unofficial Wolfx service. No early-warning stream is requested. Wolfx terms retain upstream rights; the applicability of CENC public-display / redistribution conditions to this product remains unconfirmed. Technical access, CORS, noncommercial use and disclaimers are not authorization. No CENC redistribution authorization is claimed. The source can be disabled independently in the app.

GEOFON / EMSC CC BY 4.0 licensing does NOT extend to Wolfx / CENC. The code's MIT license does not cover third-party data. No live Wolfx catalogue is bundled as a public test fixture. The unverified `intensity` field is not shown as official maximum intensity or mechanically converted to Chinese intensity / MMI. Details and official terms links: [1.1.1 source and limits](docs/EARTHQUAKE-1.1.1.md).

## Earthquake additions and consent in 1.1.2

GEOFON / EMSC attribution above continues. USGS supplementary numerical event parameters are requested from https://earthquake.usgs.gov/fdsnws/event/1/ with contributor=us and retained only when the preferred origin network is us (USGS National Earthquake Information Center). The official feed invites application use; USGS federal-work context is supported by 17 USC 105. This is not a blanket public-domain claim for third-party ComCat contributions, imagery or entire databases. Only numerical factual parameters are displayed; no third-party images/prose are bundled. The USGS current copyright page returned HTTP 403 during this check; its full current text was not verified. See docs/EARTHQUAKE-1.1.2.md for evidence and limits.

Source cdi values are community-reported intensity summaries, not official field-confirmed maximum intensities. ShakeMap mmi remains a model estimate. Neither is converted directly to Chinese seismic intensity. Only net=us summaries are accepted; no live USGS or Wolfx catalogue is distributed as a test fixture.

Independent mathematical implementation: Allen, T. I., Wald, D. J. & Worden, C. B. (2012), “Intensity attenuation for active crustal regions”, Journal of Seismology 16, 409–433, DOI https://doi.org/10.1007/s10950-012-9278-7 . Hypocentral coefficients were verified against the publisher's Table 2. No OpenQuake/other third-party source code or paper figures/text are incorporated; this is not a new third-party software dependency. Model equations are applied conservatively and explicitly labeled estimates; no Chinese-intensity conversion is provided.

Wolfx-relayed CENC access is now off by default and requires explicit in-app acknowledgment, including old enabled installations. The upstream public-display/redistribution applicability remains unconfirmed. Acknowledgment is not a copyright license. The app records the maintainer's commitment to stop the integration and remove maintained affected APKs if officials/rightsholders require it. No new authorization was obtained and no early-warning feed is requested.

## GlobalQuake reference calculation adapted in 1.1.3

The broader fallback adapts small calculation fragments and numeric threshold bins from GlobalQuake, copyright (c) 2023 xspanger3770, under its MIT License at historical commit `00d63afae10fcb910f7609873bf85c8d80566eaa`:
https://github.com/xspanger3770/GlobalQuake/tree/00d63afae10fcb910f7609873bf85c8d80566eaa .

Specifically: GeoUtils.pgaFunction/pgaFunctionGen2; EarthquakeAnalysis.getDepthCorrection; MMIIntensityScale levels and IntensityScale lower-threshold bin selection. Only the necessary computation is adapted into EarthquakeReferenceIntensity, with validation and labels added; no other engine, library, map, artwork, waveform, server API or later proprietary code is incorporated. The complete copyright/permission/warranty notice is distributed in `app/src/main/assets/GlobalQuake-MIT.txt` and the APK asset of the same name. This is an exception to historical descriptions of wholly independent client implementation; both the original client and this permitted adaptation remain MIT-licensed as applicable.

The official announcement says development from 1.0.0 continues privately. This app relies solely on the pinned old MIT snapshot, not the closed-source release or any third-party data authorization. Broad calculation does not establish globally validated accuracy. PGA-like output below the old model's first display bin is labelled below its display threshold, not MMI zero. Existing source rights and Wolfx consent limits are unchanged. Details: docs/EARTHQUAKE-1.1.3.md.

Astronomy 1.1.6: added stellar coordinates and approximate Johnson V magnitudes, including integrated deep-sky magnitudes, from Wikidata structured data under CC0 1.0. Entity and statement attribution: docs/SKY-CATALOG-1.1.6.json and docs/ASTRONOMY-1.1.6.md. No third-party images or encyclopedia text incorporated.

Status 1.2.0: independently implemented device-state features use Android platform APIs; no GPS Status code, images, SDK, satellite service or map data incorporated. User waypoints and sensor/receiver state remain on-device. API references and capability limits: docs/STATUS-1.2.0.md. No new third-party runtime dependencies.

Status 1.2.1: sensor rates, sensor ordering, GNSS display ordering, local declination and battery values remain Android platform data. The app does not incorporate GPS Status code, assets, SDK or service. See docs/STATUS-1.2.1.md.

Status 1.2.2: sensor frame-rate controls were removed; the app requests Android's fastest available sensor delay and reports device-provided values. Satellite labels use country/region plus constellation abbreviation. Battery/device visibility and ordering, current-sign selection, live saved-place age and one-shot form relocation are local UI behavior. Satellite reception pauses on section switch, lock screen, or background according to the setting and resumes when the status page returns. No GPS Status code, assets, SDK, satellite service or map data is incorporated. See docs/STATUS-1.2.2.md.

Status 1.2.3: route drawing uses only Android LocationManager callbacks already requested by the user's continuous location setting. Points are sampled locally, stored as small JSON records after the user names and saves a route, and are never uploaded or matched to a map service. The route view, zoom, panning and point details are independently implemented. Global time formatting is a preference applied to pages that format event times; weather forecasts retain each selected location's data timezone. See docs/STATUS-1.2.3.md.



## 1.2.10 城市目录与配置恢复

城市目录结合 GeoNames（CC BY 4.0）、Wikidata 结构化坐标（CC0）及草湖镇的 OpenStreetMap 坐标（© OpenStreetMap contributors，ODbL 1.0）；合并目录数据库按 ODbL 1.0 提供，GeoNames 等原有署名保留，应用代码的 MIT 许可不替代数据许可。行政区划基础名录的 WTFPL 许可随 APK 保留。逐项来源及核查范围见 [城市目录说明](docs/CITY-CATALOG.md)。不接入 OSM 地图、在线地理服务或新运行时依赖。

全配置导入导出通过系统文件选择器在本机处理，含设置、背景、收藏、地点、路线与课表，可能包含精确坐标及记录时间。导入不会恢复权限、最近定位、缓存、接口限流或 Wolfx 阅读确认，不自动启动持续定位或相机后台采样。旧版文件没有背景时保留当前背景。
