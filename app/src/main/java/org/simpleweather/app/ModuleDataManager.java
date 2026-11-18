package org.simpleweather.app;

import android.app.*;
import android.content.*;
import android.os.*;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.json.JSONObject;

/** Storage ownership is explicit; external audio and user export destinations are never deleted. */
final class ModuleDataManager {
    private final MainActivity a;
    ModuleDataManager(MainActivity a){this.a=a;}
    private boolean alive(){return !a.isFinishing()&&!a.isDestroyed();}
    void show(){
        LinearLayout list=form();for(String id:AppModules.allIds())button(list,a.apps.title(id),()->details(id));note(list,R.string.app_data_manager_note);dialog(a.getString(R.string.app_data_manager_title),list);
    }
    private LinearLayout form(){LinearLayout c=a.column();c.setPadding(a.dp(18),a.dp(8),a.dp(18),a.dp(16));return c;}
    private void button(LinearLayout c,String label,Runnable click){c.addView(a.button(label,false,click));a.gap(c,10);}
    private void note(LinearLayout c,int text){a.gap(c,4);TextParagraphs.append(a,c,a.getString(text),13,a.MUTED);}
    private AlertDialog dialog(String title,LinearLayout body){ScrollView s=new ScrollView(a);s.addView(body);return new ToolDialog(a).setTitle(title).setView(s).setNegativeButton(a.getString(R.string.action_back),null).show();}
    private String bytes(long n){return n>=1024*1024?String.format(Locale.ROOT,"%.2f MiB",n/(1024.0*1024)):String.format(Locale.ROOT,"%.1f KiB",n/1024.0);}
    private void details(String id){
        AlertDialog waiting=new ToolDialog(a).setTitle(a.apps.title(id)).setMessage(a.getString(R.string.app_data_calculating)).setCancelable(false).show();
        new Thread(()->{long[] sizes;try{sizes=measure(a,id);}catch(Exception e){sizes=null;}final long[] result=sizes;a.runOnUiThread(()->{waiting.dismiss();if(!alive())return;if(result==null){a.toast(a.getString(R.string.app_data_failed));return;}
            LinearLayout c=form();c.addView(a.text(a.getString(a.apps.installed(id)?R.string.store_status_installed:R.string.store_status_uninstalled),14,a.MUTED,false));a.gap(c,12);c.addView(a.text(a.getString(R.string.app_data_program_size,bytes(result[0])),15,a.INK,false));a.gap(c,8);c.addView(a.text(a.getString(R.string.app_data_personal_size,bytes(result[1])),15,a.INK,false));a.gap(c,8);c.addView(a.text(a.getString(R.string.app_data_cache_size,bytes(result[2])),15,a.INK,false));a.gap(c,16);
            button(c,a.getString(R.string.module_export_short),()->a.exportModule(id));button(c,a.getString(R.string.module_import_short),()->a.importModule(id));button(c,a.getString(R.string.app_data_clear_cache),()->confirm(id,false));button(c,a.getString(R.string.internal_data_delete),()->confirm(id,true));note(c,R.string.app_data_manager_note);dialog(a.apps.title(id),c);
        });},"app-storage-size").start();
    }
    private void confirm(String id,boolean erase){
        new ToolDialog(a).setTitle(a.apps.title(id)).setMessage(a.getString(erase?R.string.app_data_erase_confirm:R.string.app_data_cache_confirm,a.apps.title(id))).setNegativeButton(a.getString(R.string.action_cancel),null).setPositiveButton(a.getString(erase?R.string.internal_data_delete:R.string.app_data_clear_cache),(d,w)->{
            if(a.apps.busy){a.toast(a.getString(R.string.module_busy));return;}if(id.equals("status")&&RouteRecorder.active()){a.toast(a.getString(R.string.config_finish_route));return;}a.apps.busy=true;a.prepareApplicationDataChange(id);a.releaseModule(id);AlertDialog waiting=new ToolDialog(a).setTitle(a.apps.title(id)).setMessage(a.getString(R.string.config_working)).setCancelable(false).show();
            new Thread(()->{boolean okay;try{if(erase)erase(a,id);else clearCache(a,id);okay=true;}catch(Exception e){okay=false;}finally{a.apps.busy=false;}final boolean success=okay;a.runOnUiThread(()->{waiting.dismiss();if(alive()){a.applicationDataChanged(id,success&&erase);a.toast(a.getString(success?(erase?R.string.internal_data_deleted:R.string.app_data_cache_cleared):R.string.app_data_failed));}});},"app-data-cleanup").start();
        }).show();
    }
    static long[] measure(Context c,String id)throws Exception{
        ModuleRegistry.Module module=ModuleRegistry.find(id);if(module==null||id.equals("global"))throw new IOException("Unknown application");
        File program=ModuleRuntime.packageFile(c,id);long code=size(program)+size(new File(program.getParentFile(),id+".previous.ddapp")),data=size(ModuleRuntime.dataDirectory(c,id)),cache=cacheBytes(c,id);
        for(String group:module.groups){Map<String,Object> values=owned(c,module,group);if(!values.isEmpty())data+=new JSONObject(values).toString().getBytes(StandardCharsets.UTF_8).length;}
        return new long[]{code,data,cache};
    }
    private static Map<String,Object> owned(Context c,ModuleRegistry.Module module,String group){Map<String,Object> values=new LinkedHashMap<>();for(Map.Entry<String,?> e:c.getSharedPreferences(group,0).getAll().entrySet())if(ModuleRegistry.allowed(module,group,e.getKey())&&(ModuleRegistry.embedded(module.id)||ConfigurationData.type(group,e.getKey())!=null))values.put(e.getKey(),e.getValue());return values;}
    static long cacheBytes(Context c,String id)throws Exception{long count=size(new File(c.getCacheDir(),"internal-"+id));if(id.equals("weather")){File[] files=c.getFilesDir().listFiles((d,n)->n.startsWith("forecast_")&&(n.endsWith(".json")||n.endsWith(".json.tmp")));if(files!=null)for(File f:files)count+=size(f);}if(id.equals("earthquake"))count+=size(new File(c.getFilesDir(),"earthquake_cache"));return count;}
    static void clearCache(Context c,String id)throws Exception{
        if(!AppModules.known(id))throw new IOException("Unknown application");boolean changing=false;try{ModuleRuntime.beginChange(c,id);changing=true;remove(new File(c.getCacheDir(),"internal-"+id));if(id.equals("weather")){File[] files=c.getFilesDir().listFiles((d,n)->n.startsWith("forecast_")&&(n.endsWith(".json")||n.endsWith(".json.tmp")));if(files!=null)for(File f:files)if(f.isFile())remove(f);}if(id.equals("earthquake"))remove(new File(c.getFilesDir(),"earthquake_cache"));}finally{if(changing)ModuleRuntime.endChange(id);}
    }
    static void erase(Context c,String id)throws Exception{
        ModuleRegistry.Module module=ModuleRegistry.find(id);if(module==null||id.equals("global"))throw new IOException("Unknown application");if(ModuleRegistry.embedded(id)){InternalDataBackup.erase(c,id);return;}
        boolean changing=false,todoSuspended=false;Map<String,Map<String,?>> before=new LinkedHashMap<>();try{
            if(id.equals("todo")){TodoScheduler.suspend(c);todoSuspended=true;}
            ModuleRuntime.beginChange(c,id);changing=true;if(id.equals("status"))LocationService.stop(c);
            for(String group:module.groups){SharedPreferences prefs=c.getSharedPreferences(group,0);before.put(group,new HashMap<>(prefs.getAll()));Map<String,Object> next=new HashMap<>(prefs.getAll());next.keySet().removeAll(owned(c,module,group).keySet());if(!ConfigurationTransfer.write(prefs,next))throw new IOException("Cannot clear preferences");}
            remove(ModuleRuntime.dataDirectory(c,id));remove(new File(c.getCacheDir(),"internal-"+id));if(id.equals("weather")){File[] files=c.getFilesDir().listFiles((d,n)->n.startsWith("forecast_")&&(n.endsWith(".json")||n.endsWith(".json.tmp")));if(files!=null)for(File file:files)if(file.isFile())remove(file);}if(id.equals("earthquake"))remove(new File(c.getFilesDir(),"earthquake_cache"));
        }catch(Exception e){for(Map.Entry<String,Map<String,?>> entry:before.entrySet())ConfigurationTransfer.write(c.getSharedPreferences(entry.getKey(),0),entry.getValue());throw e;}finally{
            if(changing)ModuleRuntime.endChange(id);
            try{if(todoSuspended)TodoScheduler.afterImport(c);else if(id.equals("schedule"))ScheduleNotifier.refresh(c,false);}catch(Exception failure){android.util.Log.w("ModuleDataManager","Cannot refresh reminders after data cleanup",failure);}
        }
    }
    private static long size(File file)throws IOException{if(!file.exists()||Files.isSymbolicLink(file.toPath()))return 0;if(file.isFile())return file.length();long total=0;File[] children=file.listFiles();if(children==null)throw new IOException("Cannot inspect app storage");for(File child:children)total+=size(child);return total;}
    private static void remove(File file)throws IOException{if(!file.exists())return;if(!Files.isSymbolicLink(file.toPath())&&file.isDirectory()){File[] children=file.listFiles();if(children==null)throw new IOException("Cannot inspect app storage");for(File child:children)remove(child);}if(!file.delete())throw new IOException("Cannot remove app file");}
}
