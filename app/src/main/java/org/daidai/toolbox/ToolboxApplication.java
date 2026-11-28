package org.daidai.toolbox;

import android.app.Application;
import android.content.res.Resources;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Initialize text before Activities, services and their static model catalogs load. */
public final class ToolboxApplication extends Application {
    @Override public void onCreate(){super.onCreate();ModuleAssets.beginProcess(this);final Resources res=AppLanguage.resources(this);final String pkg=getPackageName();
        UiText.install(new UiText.Provider(){
            final Map<String,Integer> ids=new ConcurrentHashMap<>();
            final Map<String,String> labels=new HashMap<>();
            {String[] raw=res.getStringArray(R.array.identity_keys),display=res.getStringArray(R.array.identity_label_keys);for(int i=0;i<raw.length;i++)labels.put(get(raw[i]),display[i]);}
            public String get(String key){Resources current=AppLanguage.resources(ToolboxApplication.this);Integer id=ids.get(key);if(id!=null)try{return current.getString(id);}catch(Exception stale){ids.remove(key);id=null;}if(id==null){
                // Feature packages use the stable host namespace when their
                // resources are mounted into the host process. Older host
                // lookups used the applicationId and therefore missed these
                // strings after the api-2 migration.
                id=current.getIdentifier(key,"string",pkg);
                if(id==0)id=current.getIdentifier(key,"string","org.simpleweather.app");
                if(id==0)id=current.getIdentifier(key,"string","org.daidai.toolbox");
                if(id==0)id=res.getIdentifier(key,"string",pkg);
                if(id==0)id=res.getIdentifier(key,"string","org.simpleweather.app");
                if(id!=0)try{ids.put(key,id);return current.getString(id);}catch(Exception ignored){}
                // Modules may be mounted after the application provider was
                // initialized. Resolve their feature strings directly so a
                // stale host resource table cannot break an app on launch.
                for(String module:AppModules.allIds())try{
                    if(!AppModules.present(ToolboxApplication.this,module))continue;
                    Resources feature=ModuleRuntime.resources(ToolboxApplication.this,module);
                    int featureId=feature.getIdentifier(key,"string","org.daidai.toolbox");
                    if(featureId!=0)return feature.getString(featureId);
                }catch(Exception ignored){}
                try{
                    Resources host=ToolboxApplication.this.getBaseContext().getResources();
                    int hostId=host.getIdentifier(key,"string",pkg);
                    if(hostId==0)hostId=host.getIdentifier(key,"string","org.simpleweather.app");
                    if(hostId==0)hostId=host.getIdentifier(key,"string","org.daidai.toolbox");
                    if(hostId!=0)return host.getString(hostId);
                }catch(Exception ignored){}
                // Some Android resource loaders discard the original package
                // name while merging stable-ID tables. Resolve the host's
                // generated ID directly as a final compatibility path.
                try{
                    int stable=(Integer)R.string.class.getField(key).get(null);
                    try{return ToolboxApplication.this.getBaseContext().getResources().getString(stable);}catch(Exception ignored){}
                    try{return current.getString(stable);}catch(Exception ignored){return res.getString(stable);}
                }catch(Exception ignored){}
                }
                throw new IllegalArgumentException("Missing text resource: "+key);}
            public String label(String identity){String key=labels.get(identity);return key==null?identity:get(key);}
        });
    }
}
