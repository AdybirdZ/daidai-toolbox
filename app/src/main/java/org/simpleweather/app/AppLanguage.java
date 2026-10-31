package org.simpleweather.app;

import android.app.LocaleManager;
import android.content.*;
import android.content.res.*;
import android.os.*;
import java.util.*;

/** Per-app language on Android 8+, without changing the device language. */
final class AppLanguage {
    private static String resourceChoice;
    private static int resourceConfig,systemConfig;
    private static Resources localized;
    static String choice(Context context){return LanguagePolicy.normalize(context.getSharedPreferences("toolbox",0).getString(LanguagePolicy.KEY,"system"));}
    static Context wrap(Context base){
        LocaleList system=Build.VERSION.SDK_INT>=33?base.getSystemService(LocaleManager.class).getSystemLocales():Resources.getSystem().getConfiguration().getLocales();
        List<Locale> locales=new ArrayList<>();for(int i=0;i<system.size();i++)locales.add(system.get(i));
        Locale locale=LanguagePolicy.resolve(choice(base),locales);
        Configuration config=new Configuration(base.getResources().getConfiguration());
        config.setLocales(new LocaleList(locale));config.setLayoutDirection(locale);
        return base.createConfigurationContext(config);
    }
    static synchronized Resources resources(Context context){
        String choice=choice(context);int config=context.getResources().getConfiguration().hashCode(),system=Resources.getSystem().getConfiguration().hashCode();
        if(localized==null||!choice.equals(resourceChoice)||config!=resourceConfig||system!=systemConfig){
            localized=wrap(context).getResources();resourceChoice=choice;resourceConfig=config;systemConfig=system;
        }
        return localized;
    }
}
