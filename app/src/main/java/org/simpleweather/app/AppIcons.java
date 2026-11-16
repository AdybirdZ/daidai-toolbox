package org.simpleweather.app;
/** Original artwork, also supplied as SVG sources in modules/icons. */
public final class AppIcons { public static int resource(String id){switch(id){
case "weather":return R.drawable.app_weather;
case "status":return R.drawable.app_status;
case "schedule":return R.drawable.app_schedule;
case "weather-map":return R.drawable.app_weather_map;
case "astronomy":return R.drawable.app_astronomy;
case "earthquake":return R.drawable.app_earthquake;
case "random":return R.drawable.app_random;
case "todo":return R.drawable.app_todo;
case "music":return R.drawable.app_music;
case "messaging":return R.drawable.app_messaging;
case "colorvision":return R.drawable.app_colorvision;
case "settings":return R.drawable.app_settings;
case "store":return R.drawable.app_store;
default:return R.drawable.app_store;}}}