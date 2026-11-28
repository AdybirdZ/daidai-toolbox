package org.daidai.toolbox;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

/** App-scoped data transfer: legacy JSON plus data-only archives for embedded apps. */
final class ModuleTransfer {
    static final int EXPORT=80,IMPORT=81;
    private final MainActivity a;private final Handler main=new Handler(Looper.getMainLooper());
    private String module;private byte[] pending;private File pendingArchive;private boolean busy;
    ModuleTransfer(MainActivity a){this.a=a;}
    private boolean alive(){return !a.isFinishing()&&!a.isDestroyed();}
    private void finish(int message){busy=false;pending=null;if(pendingArchive!=null)pendingArchive.delete();pendingArchive=null;if(alive()&&message!=0)a.toast(a.getString(message));}
    void exportModule(String id){
        ModuleRegistry.Module m=ModuleRegistry.find(id);if(m==null||busy)return;busy=true;module=id;
        new Thread(()->{try{
            if(ModuleRegistry.embedded(id))pendingArchive=InternalDataBackup.exportApp(a,id);
            else{Map<String,Map<String,?>> values=new LinkedHashMap<>();for(String g:ConfigurationData.GROUPS)values.put(g,Collections.emptyMap());for(String g:m.groups){Map<String,Object> selected=new LinkedHashMap<>();for(Map.Entry<String,?> e:a.getSharedPreferences(g,0).getAll().entrySet())if(ModuleRegistry.allowed(m,g,e.getKey()))selected.put(e.getKey(),e.getValue());values.put(g,selected);}JSONObject root=ConfigurationData.encode(values,null).put("module",id).put("moduleTitle",m.title);pending=root.toString().getBytes(StandardCharsets.UTF_8);}
            main.post(()->{if(!alive()){finish(0);return;}try{boolean zip=ModuleRegistry.embedded(id);a.startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType(zip?"application/zip":"application/json").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"DaidaiToolbox-"+id+(zip?".zip":".json")),EXPORT);}catch(Exception e){finish(R.string.config_picker_failed);}});
        }catch(Exception e){main.post(()->finish(R.string.config_export_failed));}},"app-data-export").start();
    }
    void importModule(String id){if(ModuleRegistry.find(id)==null||busy)return;busy=true;module=id;try{a.startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),IMPORT);}catch(Exception e){finish(R.string.config_picker_failed);}}
    void result(int request,int result,Intent intent){
        if(!busy||result!=Activity.RESULT_OK||intent==null||intent.getData()==null){finish(0);return;}Uri uri=intent.getData();
        if(request==EXPORT){byte[] bytes=pending;File archive=pendingArchive;pending=null;pendingArchive=null;if(bytes==null&&archive==null){finish(R.string.config_interrupted);return;}
            new Thread(()->{try(OutputStream out=a.getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new IOException();if(archive==null)out.write(bytes);else try(InputStream in=new FileInputStream(archive)){AppModules.copy(in,out,InternalDataBackup.MAX);}out.flush();main.post(()->finish(R.string.module_exported));}catch(Exception e){main.post(()->finish(R.string.config_export_failed));}finally{if(archive!=null)archive.delete();}},"app-data-export").start();return;
        }
        final String selected=module;
        new Thread(()->{try{
            if(ModuleRegistry.embedded(selected)){InternalDataBackup.Prepared prepared=InternalDataBackup.readApp(a,uri,selected);main.post(()->{if(!alive()){prepared.close();finish(0);}else confirmEmbedded(selected,prepared);});return;}
            byte[] bytes=read(uri);JSONObject root=new JSONObject(new String(bytes,StandardCharsets.UTF_8));if(!selected.equals(root.optString("module")))throw new IOException();ConfigurationData.Document doc=ConfigurationData.parse(root.toString());ModuleRegistry.Module m=ModuleRegistry.find(selected);Map<String,Map<String,Object>> values=new LinkedHashMap<>();for(String g:m.groups)values.put(g,doc.groups.get(g));main.post(()->{if(alive())confirm(m,values);else finish(0);});
        }catch(Exception|OutOfMemoryError e){main.post(()->finish(R.string.config_import_invalid));}},"app-data-validate").start();
    }
    private byte[] read(Uri u)throws IOException{try(InputStream in=a.getContentResolver().openInputStream(u);ByteArrayOutputStream out=new ByteArrayOutputStream()){if(in==null)throw new IOException();byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(out.size()+n>ConfigurationData.MAX_BYTES)throw new IOException();out.write(b,0,n);}return out.toByteArray();}}
    private void confirmEmbedded(String id,InternalDataBackup.Prepared prepared){
        new ToolDialog(a).setTitle(a.getString(R.string.module_import_title)).setMessage(a.getString(R.string.module_import_summary,a.apps.title(id))+a.getString(R.string.app_data_import_attachments,prepared.fileCount))
            .setOnCancelListener(d->{prepared.close();finish(0);}).setNegativeButton(a.getString(R.string.action_cancel),(d,w)->{prepared.close();finish(0);})
            .setPositiveButton(a.getString(R.string.config_confirm),(d,w)->{
                if(a.apps.busy){prepared.close();finish(R.string.module_busy);return;}a.apps.busy=true;
                AlertDialog progress=new ToolDialog(a).setTitle(a.getString(R.string.module_import_title)).setMessage(a.getString(R.string.config_working)).setCancelable(false).show();
                new Thread(()->{boolean okay=false,restored;try{prepared.apply(a);okay=true;}catch(Exception failure){}finally{restored=prepared.finish(a,okay);a.apps.busy=false;}final boolean success=okay,rollback=restored;main.post(()->{progress.dismiss();finish(success?R.string.config_imported:rollback?R.string.config_import_failed:R.string.config_restore_failed);});},"app-data-apply").start();
            }).show();
    }
    private void confirm(ModuleRegistry.Module m,Map<String,Map<String,Object>> values){
        String title=a.getString(a.getResources().getIdentifier(m.title,"string",a.getPackageName()));
        if(RouteRecorder.active()){finish(R.string.config_finish_route);return;}
        for(String group:m.groups)for(String key:values.get(group).keySet())if(!ModuleRegistry.allowed(m,group,key)){
            if(!(group.equals("toolbox")&&(key.equals("location_continuous")||key.equals("status_camera_background")||key.equals("quake_source_Wolfx")))){finish(R.string.config_import_invalid);return;}
        }
        new ToolDialog(a).setTitle(a.getString(R.string.module_import_title)).setMessage(a.getString(R.string.module_import_summary,title))
            .setOnCancelListener(d->finish(0)).setNegativeButton(a.getString(R.string.action_cancel),(d,w)->finish(0))
            .setPositiveButton(a.getString(R.string.config_confirm),(d,w)->{
                if(RouteRecorder.active()){finish(R.string.config_finish_route);return;}if(a.apps.busy){finish(R.string.module_busy);return;}a.apps.busy=true;
                Map<String,Map<String,?>> before=new LinkedHashMap<>();boolean okay=false,restored=true;
                try{
                    for(String group:m.groups)before.put(group,new HashMap<>(a.getSharedPreferences(group,0).getAll()));a.prepareConfigurationImport();
                    for(String group:m.groups){Map<String,Object> next=new HashMap<>(before.get(group));next.keySet().removeIf(k->ModuleRegistry.allowed(m,group,k)&&ConfigurationData.type(group,k)!=null);for(Map.Entry<String,Object> item:values.get(group).entrySet())if(ModuleRegistry.allowed(m,group,item.getKey()))next.put(item.getKey(),item.getValue());if(!ConfigurationTransfer.write(a.getSharedPreferences(group,0),next))throw new IOException();}okay=true;
                }catch(Exception e){for(String group:before.keySet())if(!ConfigurationTransfer.write(a.getSharedPreferences(group,0),before.get(group)))restored=false;}
                finally{busy=false;a.apps.busy=false;}
                try{TodoScheduler.afterImport(a);}catch(Exception failure){android.util.Log.w("ModuleTransfer","Data imported; reminders could not refresh",failure);}a.configurationImportFinished(okay,restored);
            }).show();
    }
}
