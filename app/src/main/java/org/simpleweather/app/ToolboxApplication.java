package org.simpleweather.app;

import android.app.Application;
import android.content.res.Resources;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Initialize text before Activities, services and their static model catalogs load. */
public final class ToolboxApplication extends Application {
    @Override public void onCreate(){super.onCreate();final Resources res=getResources();final String pkg=getPackageName();
        UiText.install(new UiText.Provider(){
            final Map<String,Integer> ids=new ConcurrentHashMap<>();
            final Map<String,String> labels=new HashMap<>();
            {String[] raw=res.getStringArray(R.array.identity_keys),display=res.getStringArray(R.array.identity_label_keys);for(int i=0;i<raw.length;i++)labels.put(get(raw[i]),display[i]);}
            public String get(String key){Integer id=ids.get(key);if(id==null){id=res.getIdentifier(key,"string",pkg);if(id==0)throw new IllegalArgumentException("Missing text resource: "+key);ids.put(key,id);}return res.getString(id);}
            public String label(String identity){String key=labels.get(identity);return key==null?identity:get(key);}
        });
    }
}
