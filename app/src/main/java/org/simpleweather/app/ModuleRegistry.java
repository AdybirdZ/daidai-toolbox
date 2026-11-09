package org.simpleweather.app;

import java.util.*;

/** Stable module identities used by the 3.0.0 package boundary. */
final class ModuleRegistry {
    static final class Module { final String id, title; final String[] groups; Module(String id,String title,String...groups){this.id=id;this.title=title;this.groups=groups;} }
    static final List<Module> ALL=Collections.unmodifiableList(Arrays.asList(
        new Module("weather","tab_weather","weather"),
        new Module("status","tab_status","status_places","status_routes"),
        new Module("todo","tab_todo","todos"),
        new Module("schedule","tab_schedule","timetable"),
        new Module("astronomy","tab_astronomy","toolbox"),
        new Module("earthquake","tab_earthquake","toolbox"),
        new Module("random","tab_random","random_choice"),
        new Module("global","settings_014","toolbox")));
    static Module find(String id){for(Module m:ALL)if(m.id.equals(id))return m;return null;}
    static boolean allowed(Module m,String group,String key){
        if(!Arrays.asList(m.groups).contains(group))return false;
        if(!group.equals("toolbox"))return true;
        if(m.id.equals("global"))return true;
        if(m.id.equals("astronomy"))return key.startsWith("astro_");
        if(m.id.equals("earthquake"))return key.startsWith("quake_");
        return false;
    }
}
