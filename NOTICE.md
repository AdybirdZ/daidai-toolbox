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
