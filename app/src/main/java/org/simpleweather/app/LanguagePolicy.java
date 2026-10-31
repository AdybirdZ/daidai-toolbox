package org.simpleweather.app;

import java.util.*;

/** Stable preference values, independent of translated labels and Android. */
final class LanguagePolicy {
    static final String KEY="app_language";
    static final String[] CHOICES={"system","zh-CN","en"};
    static boolean valid(String choice){return Arrays.asList(CHOICES).contains(choice);}
    static String normalize(String choice){return valid(choice)?choice:"system";}
    static Locale resolve(String choice,List<Locale> system){
        choice=normalize(choice);
        if(choice.equals("en"))return Locale.ENGLISH;
        if(choice.equals("zh-CN"))return Locale.SIMPLIFIED_CHINESE;
        for(Locale locale:system){
            if(locale.getLanguage().equals("zh"))return Locale.SIMPLIFIED_CHINESE;
            if(locale.getLanguage().equals("en"))return Locale.ENGLISH;
        }
        return Locale.SIMPLIFIED_CHINESE;
    }
}
