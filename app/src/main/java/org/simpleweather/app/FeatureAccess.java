package org.simpleweather.app;

import android.content.Context;
import android.widget.LinearLayout;
import java.lang.reflect.*;

/** Optional feature implementations are resolved only through their installed signed package. */
public final class FeatureAccess {
    private FeatureAccess(){}
    public static Object call(Context c,String id,String type,String method,Class<?>[] signature,Object...args){
        if(!AppModules.present(c,id))return null;
        try{return Class.forName("org.simpleweather.app."+type,true,ModuleRuntime.loader(c,id)).getMethod(method,signature).invoke(null,args);}
        catch(InvocationTargetException e){Throwable cause=e.getCause();if(cause instanceof RuntimeException)throw (RuntimeException)cause;throw new IllegalStateException(cause);}
        catch(ModuleRuntime.Updating changing){return null;}
        catch(Exception e){throw new IllegalStateException("Cannot invoke installed application "+id,e);}
    }
    public static void settings(MainActivity a,String id){
        if(!a.apps.installed(id))return;
        if(!(a instanceof SettingsActivity)){SettingsActivity.open(a,id);return;}
        String type=id.equals("weather")?"WeatherSettings":id.equals("astronomy")?"AstronomySettings":id.equals("status")?"StatusSettings":"MapSettings";
        try{Object target=Class.forName("org.simpleweather.app."+type,true,ModuleRuntime.loader(a,id)).getConstructor(MainActivity.class).newInstance(a);target.getClass().getMethod("show").invoke(target);}
        catch(Exception e){android.util.Log.e("FeatureAccess","Cannot open settings",e);a.toast(a.getString(R.string.module_open_failed));}
    }
    public static void earthquakeInfo(MainActivity a,LinearLayout c,boolean links){call(a,"earthquake","EarthquakeInfo",links?"links":"append",new Class<?>[]{MainActivity.class,LinearLayout.class},a,c);}
}
