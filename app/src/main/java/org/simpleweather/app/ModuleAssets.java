package org.simpleweather.app;

import android.content.*;
import android.content.res.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Mount optional resources with immutable IDs, without copying feature text or assets into the host. */
public final class ModuleAssets {
    private static final Map<String,Resources> CACHE=new HashMap<>();
    private static long generation;
    private static final Map<String,File> SNAPSHOTS=new HashMap<>();
    private static final Map<String,Boolean> PRESENT=new HashMap<>();
    private ModuleAssets(){}
    static void beginProcess(Context c){File directory=new File(c.getCodeCacheDir(),"app-resources");File[] files=directory.listFiles();if(files!=null)for(File file:files)if(file.isFile())file.delete();}
    public static synchronized void invalidate(){CACHE.clear();PRESENT.clear();generation++;}
    static synchronized void changed(String id){invalidate();Iterator<Map.Entry<String,File>> entries=SNAPSHOTS.entrySet().iterator();while(entries.hasNext()){Map.Entry<String,File> entry=entries.next();if(entry.getKey().startsWith(id+":")){entry.getValue().delete();entries.remove();}}}
    public static synchronized long generation(){return generation;}
    public static synchronized Resources resources(Context c,Resources base){
        StringBuilder key=new StringBuilder(c.getApplicationInfo().sourceDir).append(':').append(base.getConfiguration().hashCode());
        ArrayList<File> files=new ArrayList<>();
        for(String id:AppModules.IDS){File f=ModuleRuntime.packageFile(c,id);String stamp=stamp(f);Boolean present=PRESENT.get(stamp);if(present==null){present=f.isFile()&&AppModules.present(c,id);PRESENT.put(stamp,present);}if(present){try{File snapshot=resourceFile(c,id);files.add(snapshot);key.append(':').append(snapshot.getName());}catch(Exception e){android.util.Log.e("ModuleAssets","Cannot retain resource generation",e);}}}
        Resources found=CACHE.get(key.toString());if(found!=null)return found;
        try{
            AssetManager assets=AssetManager.class.getDeclaredConstructor().newInstance();
            java.lang.reflect.Method add=AssetManager.class.getMethod("addAssetPath",String.class);
            if((Integer)add.invoke(assets,c.getApplicationInfo().sourceDir)==0)throw new IllegalStateException("Host resources unavailable");
            for(File f:files)add.invoke(assets,f.getAbsolutePath());
            Resources result=new Resources(assets,base.getDisplayMetrics(),base.getConfiguration());
            if(CACHE.size()>8)CACHE.clear();CACHE.put(key.toString(),result);return result;
        }catch(Exception e){android.util.Log.e("ModuleAssets","Cannot mount optional resources",e);return base;}
    }
    private static String stamp(File f){try{return f.getAbsolutePath()+':'+f.length()+':'+f.lastModified()+':'+android.system.Os.stat(f.getPath()).st_ino;}catch(Exception e){return f.getAbsolutePath()+":missing";}}
    /** Android caches ApkAssets by path. A unique immutable path prevents stale tables after an update. */
    static synchronized File resourceFile(Context c,String id)throws Exception{
        File source=ModuleRuntime.packageFile(c,id);String stamp=id+":"+stamp(source);File found=SNAPSHOTS.get(stamp);if(found!=null&&found.isFile())return found;
        File directory=new File(c.getCodeCacheDir(),"app-resources");if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("Cannot create resource cache");
        File target=new File(directory,id+'-'+AppModules.sha(source)+".ddapp");if(!target.isFile()){
            try{Files.createLink(target.toPath(),source.toPath());}catch(Exception unavailable){Files.copy(source.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING);}
            if(!target.setReadOnly())throw new IOException("Cannot protect resource generation");
        }
        SNAPSHOTS.put(stamp,target);return target;
    }
    public static Context wrap(Context base){return new ContextWrapper(base){
        @Override public Resources getResources(){return resources(getBaseContext(),getBaseContext().getResources());}
        @Override public AssetManager getAssets(){return getResources().getAssets();}
        @Override public Context createConfigurationContext(Configuration config){return wrap(getBaseContext().createConfigurationContext(config));}
    };}
}
