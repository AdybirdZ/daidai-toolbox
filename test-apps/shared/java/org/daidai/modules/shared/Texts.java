package org.daidai.modules.shared;
import android.content.Context;
/** Module resources resolved in the host package; no dependency on a generated host R. */
public final class Texts {
    public static String get(Context context,String key,Object... args){
        int id=context.getResources().getIdentifier(key,"string",context.getPackageName());
        if(id==0)throw new IllegalArgumentException("Missing module text: "+key);
        return context.getString(id,args);
    }
    private Texts(){}
}
