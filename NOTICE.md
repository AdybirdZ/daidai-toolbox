# 数据署名

Weather data: Open-Meteo, https://open-meteo.com/ — CC BY 4.0.

Location data: GeoNames, https://www.geonames.org/ , supplied through the Open-Meteo Geocoding API — CC BY 4.0.

License: https://creativecommons.org/licenses/by/4.0/

The app translates weather descriptions, rounds numerical values for display, rearranges forecasts, normalizes selected location display labels, and uses Beijing time for Chinese locations. This does not imply endorsement by Open-Meteo, GeoNames or any national weather service.

`app/src/main/assets/cities.json` contains location data retrieved on 2026-10-24. Selected ambiguous presets were resolved using the official `/v1/get?id=...` endpoint and GeoNames identifiers. `tests/fixtures/beijing.json` is a weather API response retained solely as an attributed regression fixture, not bundled as live weather in the APK.

Free hosted API access is subject to separate non-commercial service terms: https://open-meteo.com/en/terms . The MIT license for the newly written app code does not replace these data or service terms.

All weather icons in this app were drawn in its source code. No upstream Weather Fighter code or visual assets are redistributed.


## Additional references in Daidai Toolbox 1.0.0

Weather maps are complete original publisher webpages from nmc.cn and weather.com.cn, not copied image assets or authorized image APIs. All relevant copyrights remain with their publishers. NOAA aurora content is likewise viewed on its original page.

Astronomical mathematics reference: https://stjarnhimlen.se/comp/ppcomp.html . Algorithms were independently implemented; the approximate results are not for navigation or measurement.

Timetable feature reference: https://github.com/XingHeYuZhuan/shiguangschedule , Apache License 2.0, commit feabb68f75c4058319b449287980b5f645eb8d61. No source code or assets from that repository are incorporated. The new native timetable implementation is covered by this project's MIT license.

Weather Fighter reference commit: e75bbe601237d663bf611577e618c3354685f68f. No source code or visual assets incorporated.
