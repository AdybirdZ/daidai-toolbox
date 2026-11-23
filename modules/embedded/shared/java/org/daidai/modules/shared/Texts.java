package org.daidai.modules.shared;
import android.content.Context;
/** Module resources resolved in the host package; no dependency on a generated host R. */
public final class Texts {
    public static String get(Context context,String key,Object... args){
        int id=id(context,key,"string");
        if(id==0)throw new IllegalArgumentException("Missing module text: "+key);
        return context.getString(id,args);
    }
    public static int id(Context context,String key,String type){
        int id=context.getResources().getIdentifier(key,type,context.getPackageName());
        for(String app:new String[]{"music","messaging","colorvision"})if(id==0)id=context.getResources().getIdentifier(key,type,"org.simpleweather.module."+app);
        if(id==0&&"org.simpleweather.testhost".equals(context.getPackageName()))
            id=context.getResources().getIdentifier(key,type,"org.simpleweather.module.ballrace");
        return id;
    }
    private Texts(){}
}
