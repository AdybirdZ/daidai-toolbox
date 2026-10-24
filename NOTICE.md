# 数据署名

Weather data: Open-Meteo, https://open-meteo.com/ — CC BY 4.0.

Location data: GeoNames, https://www.geonames.org/ , supplied through the Open-Meteo Geocoding API — CC BY 4.0.

License: https://creativecommons.org/licenses/by/4.0/

The app translates weather descriptions, rounds numerical values for display, rearranges forecasts, normalizes selected location display labels, and uses Beijing time for Chinese locations. This does not imply endorsement by Open-Meteo, GeoNames or any national weather service.

`app/src/main/assets/cities.json` contains location data retrieved on 2026-10-24. Selected ambiguous presets were resolved using the official `/v1/get?id=...` endpoint and GeoNames identifiers. `tests/fixtures/beijing.json` is a weather API response retained solely as an attributed regression fixture, not bundled as live weather in the APK.

Free hosted API access is subject to separate non-commercial service terms: https://open-meteo.com/en/terms . The MIT license for the newly written app code does not replace these data or service terms.

All weather icons in this app were drawn in its source code. No upstream Weather Fighter code or visual assets are redistributed.
