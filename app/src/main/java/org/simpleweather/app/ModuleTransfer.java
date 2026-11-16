package org.simpleweather.app;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

/** Per-module JSON transfer. It intentionally does not install or download modules. */
final class ModuleTransfer {
    static final int EXPORT=80, IMPORT=81;
    private final MainActivity a; private final Handler main=new Handler(Looper.getMainLooper());
    private String module; private byte[] pending; private boolean busy;
    ModuleTransfer(MainActivity a){this.a=a;}
    void exportModule(String id){ModuleRegistry.Module m=ModuleRegistry.find(id);if(m==null||busy)return;busy=true;module=id;new Thread(()->{try{Map<String,Map<String,?>> values=new LinkedHashMap<>();for(String g:ConfigurationData.GROUPS)values.put(g,Collections.emptyMap());for(String g:m.groups){Map<String,Object> selected=new LinkedHashMap<>();for(Map.Entry<String,?> e:a.getSharedPreferences(g,0).getAll().entrySet())if(ModuleRegistry.allowed(m,g,e.getKey()))selected.put(e.getKey(),e.getValue());values.put(g,selected);}JSONObject root=ConfigurationData.encode(values,null).put("module",id).put("moduleTitle",m.title);pending=root.toString().getBytes(StandardCharsets.UTF_8);main.post(()->{try{a.startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/json").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"DaidaiToolbox-"+id+".json"),EXPORT);}catch(Exception e){busy=false;a.toast(a.getString(R.string.config_picker_failed));}});}catch(Exception e){busy=false;a.toast(a.getString(R.string.config_export_failed));}}).start();}
    void importModule(String id){if(ModuleRegistry.find(id)==null||busy)return;busy=true;module=id;try{a.startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("application/json").addCategory(Intent.CATEGORY_OPENABLE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),IMPORT);}catch(Exception e){busy=false;a.toast(a.getString(R.string.config_picker_failed));}}
    void result(int request,int result,Intent intent){if(!busy||result!=Activity.RESULT_OK||intent==null||intent.getData()==null){busy=false;pending=null;return;}Uri uri=intent.getData();if(request==EXPORT){byte[] bytes=pending;pending=null;new Thread(()->{try(OutputStream out=a.getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new IOException();out.write(bytes);out.flush();main.post(()->{busy=false;a.toast(a.getString(R.string.module_exported));});}catch(Exception e){main.post(()->{busy=false;a.toast(a.getString(R.string.config_export_failed));});}}).start();return;}new Thread(()->{try{byte[] bytes=read(uri);JSONObject root=new JSONObject(new String(bytes,StandardCharsets.UTF_8));if(!module.equals(root.optString("module")))throw new IOException();ConfigurationData.Document doc=ConfigurationData.parse(root.toString());ModuleRegistry.Module m=ModuleRegistry.find(module);Map<String,Map<String,Object>> values=new LinkedHashMap<>();for(String g:m.groups)values.put(g,doc.groups.get(g));main.post(()->confirm(m,values));}catch(Exception e){main.post(()->{busy=false;a.toast(a.getString(R.string.config_import_invalid));});}}).start();}
    private byte[] read(Uri u)throws IOException{try(InputStream in=a.getContentResolver().openInputStream(u);ByteArrayOutputStream out=new ByteArrayOutputStream()){if(in==null)throw new IOException();byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(out.size()+n>ConfigurationData.MAX_BYTES)throw new IOException();out.write(b,0,n);}return out.toByteArray();}}
    private void confirm(ModuleRegistry.Module m,Map<String,Map<String,Object>> values){
        String title=a.getString(a.getResources().getIdentifier(m.title,"string",a.getPackageName()));
        if(RouteRecorder.active()){busy=false;a.toast(a.getString(R.string.config_finish_route));return;}
        // A module document may only change keys owned by that app, even in a shared group.
        for(String group:m.groups)for(String key:values.get(group).keySet())if(!ModuleRegistry.allowed(m,group,key)){
            // ConfigurationData injects these safe runtime defaults for every document.
            if(!(group.equals("toolbox")&&(key.equals("location_continuous")||key.equals("status_camera_background")||key.equals("quake_source_Wolfx")))){busy=false;a.toast(a.getString(R.string.config_import_invalid));return;}
        }
        new ToolDialog(a).setTitle(a.getString(R.string.module_import_title)).setMessage(a.getString(R.string.module_import_summary,title))
            .setOnCancelListener(d->busy=false).setNegativeButton(a.getString(R.string.action_cancel),(d,w)->busy=false)
            .setPositiveButton(a.getString(R.string.config_confirm),(d,w)->{
                if(RouteRecorder.active()){busy=false;a.toast(a.getString(R.string.config_finish_route));return;}
                Map<String,Map<String,?>> before=new LinkedHashMap<>();boolean okay=false,restored=true;
                for(String group:m.groups)before.put(group,new HashMap<>(a.getSharedPreferences(group,0).getAll()));
                a.prepareConfigurationImport();
                try{
                    for(String group:m.groups){
                        Map<String,Object> next=new HashMap<>(before.get(group));
                        next.keySet().removeIf(k->ModuleRegistry.allowed(m,group,k));
                        for(Map.Entry<String,Object> item:values.get(group).entrySet())if(ModuleRegistry.allowed(m,group,item.getKey()))next.put(item.getKey(),item.getValue());
                        if(!ConfigurationTransfer.write(a.getSharedPreferences(group,0),next))throw new IOException();
                    }
                    okay=true;
                }catch(Exception e){for(String group:m.groups)if(!ConfigurationTransfer.write(a.getSharedPreferences(group,0),before.get(group)))restored=false;}
                TodoScheduler.afterImport(a);busy=false;a.configurationImportFinished(okay,restored);
            }).show();
    }
}
