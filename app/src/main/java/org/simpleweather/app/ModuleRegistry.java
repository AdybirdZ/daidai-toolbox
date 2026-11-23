package org.simpleweather.app;

import java.util.*;

/** Stable module identities used by the 3.0.0 package boundary. */
final class ModuleRegistry {
    static final class Module { final String id, title; final String[] groups; Module(String id,String title,String...groups){this.id=id;this.title=title;this.groups=groups;} }
    static final List<Module> ALL=createAll();
    private static List<Module> createAll(){
        List<Module> modules=new ArrayList<>(Arrays.asList(
        new Module("weather","tab_weather","weather","toolbox"),
        new Module("schedule","tab_schedule","timetable","toolbox"),
        new Module("status","tab_status","status_places","status_routes","toolbox"),
        new Module("weather-map","tab_weather_map","toolbox"),
        new Module("astronomy","tab_astronomy","toolbox"),
        new Module("earthquake","tab_earthquake","toolbox"),
        new Module("random","tab_random","random_choice","toolbox"),
        new Module("todo","tab_todo","todos","toolbox"),
        new Module("music","module_music","music_module"),
        new Module("messaging","module_messaging","messaging_module"),
        new Module("colorvision","module_colorvision","colorvision_module"),
        new Module("global","settings_014","toolbox")));
        if(HostBuildProfile.BALL_RACE_TEST_HOST){modules.clear();modules.add(new Module("ballrace","module_ballrace","ballrace_module"));}
        return Collections.unmodifiableList(modules);
    }
    static boolean embedded(String id){return Arrays.asList(AppModules.EMBEDDED).contains(id);}
    static Module find(String id){for(Module m:ALL)if(m.id.equals(id))return m;return null;}
    static boolean allowed(Module m,String group,String key){
        if(!Arrays.asList(m.groups).contains(group))return false;
        if(!group.equals("toolbox"))return true;
        if(m.id.equals("global")){if(ConfigurationData.type(group,key)==null)return false;for(Module other:ALL)if(!other.id.equals("global")&&allowed(other,group,key))return false;return true;}
        if(m.id.equals("astronomy"))return key.startsWith("astro_");
        if(m.id.equals("earthquake"))return key.startsWith("quake_");
        if(m.id.equals("weather"))return key.startsWith("weather_");
        if(m.id.equals("weather-map"))return key.equals("radar_region")||key.equals("map_product");
        if(m.id.equals("status"))return key.startsWith("status_")||key.startsWith("location_")||key.equals("route_interval");
        if(m.id.equals("todo"))return key.startsWith("todo_");
        if(m.id.equals("schedule"))return key.startsWith("course_");
        if(m.id.equals("random"))return key.startsWith("random_");
        return false;
    }
}
