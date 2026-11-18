package org.simpleweather.app;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Each child dialog retains its parent; selections apply without closing the page. */
public final class MapSettings {
    private final MainActivity a;
    private final android.content.SharedPreferences prefs;
    private static final long[] INTERVALS={1000,5000,10000,30000,60000,180000,300000,600000};
    public MapSettings(MainActivity a){this.a=a;prefs=a.preferences;}
    public void show(){maps();}
    public void maps(){String[] names={UiText.get("settings_093"),UiText.get("map_002"),UiText.get("astronomy_027"),UiText.get("map_003"),UiText.get("map_004"),UiText.get("map_005"),UiText.get("astronomy_031"),UiText.get("astronomy_033")};int selected=Math.max(0,Math.min(7,prefs.getInt("radar_region",0)));new ToolDialog(a).setTitle(UiText.get("settings_094")).setSingleChoiceItems(names,selected,(d,n)->{prefs.edit().putInt("radar_region",n).apply();a.refreshScreen();}).setNegativeButton(UiText.get("action_back"),null).show();}
}
