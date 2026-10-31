package org.simpleweather.app;

import android.app.*;
import android.content.*;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Configuration files use the system document picker and remain on the selected provider. */
final class ConfigurationTransfer {
    static final int EXPORT=70, IMPORT=71;
    private final MainActivity a;
    private final Handler main=new Handler(Looper.getMainLooper());
    private boolean busy,applying;
    private byte[] pending;
    ConfigurationTransfer(MainActivity a){this.a=a;}
    private boolean alive(){return !a.isFinishing()&&!a.isDestroyed();}
    private boolean begin(){if(busy){a.toast(a.getString(R.string.config_busy));return false;}if(RouteRecorder.active()){a.toast(a.getString(R.string.config_finish_route));return false;}busy=true;return true;}
    void exportFile(){
        if(!begin())return;
        new Thread(()->{try{
            Map<String,Map<String,?>> values=new LinkedHashMap<>();for(String group:ConfigurationData.GROUPS)values.put(group,a.getSharedPreferences(group,0).getAll());
            File image=new File(a.getFilesDir(),"background.jpg");byte[] background=null;
            if(image.exists()){if(image.length()>ConfigurationData.MAX_BACKGROUND)throw new IOException();background=Files.readAllBytes(image.toPath());}
            byte[] bytes=ConfigurationData.encode(values,background).toString().getBytes(StandardCharsets.UTF_8);if(bytes.length>ConfigurationData.MAX_BYTES)throw new IOException();
            main.post(()->{if(!alive()){busy=false;return;}pending=bytes;try{a.startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/json").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"DaidaiToolbox-configuration.json"),EXPORT);}catch(Exception e){finish(R.string.config_picker_failed);}});
        }catch(Exception e){main.post(()->finish(R.string.config_export_failed));}},"configuration-export").start();
    }
    void importFile(){if(!begin())return;try{a.startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),IMPORT);}catch(Exception e){finish(R.string.config_picker_failed);}}
    void result(int request,int result,Intent intent){
        if(result!=Activity.RESULT_OK||intent==null||intent.getData()==null){busy=false;pending=null;return;}
        if(!busy){finish(R.string.config_interrupted);return;}Uri uri=intent.getData();
        if(request==EXPORT){byte[] bytes=pending;pending=null;if(bytes==null){finish(R.string.config_interrupted);return;}
            new Thread(()->{try(OutputStream out=a.getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new IOException();out.write(bytes);out.flush();main.post(()->finish(R.string.config_exported));}catch(Exception e){main.post(()->finish(R.string.config_export_failed));}},"configuration-export").start();
        }else new Thread(()->{try{
            byte[] bytes=read(uri);String text=StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString();ConfigurationData.Document doc=ConfigurationData.parse(text);validateImage(doc.background);main.post(()->confirm(doc));
        }catch(Exception|OutOfMemoryError e){main.post(()->finish(R.string.config_import_invalid));}},"configuration-validate").start();
    }
    private byte[] read(Uri uri)throws IOException{try(InputStream in=a.getContentResolver().openInputStream(uri);ByteArrayOutputStream out=new ByteArrayOutputStream()){
        if(in==null)throw new IOException();byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(out.size()+n>ConfigurationData.MAX_BYTES)throw new IOException();out.write(b,0,n);}return out.toByteArray();
    }}
    private static void validateImage(byte[] bytes)throws IOException {
        if(bytes==null)return;BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);
        if(bounds.outWidth<=0||bounds.outHeight<=0||(long)bounds.outWidth*bounds.outHeight>16_000_000||bounds.outWidth>8192||bounds.outHeight>8192)throw new IOException();
        android.graphics.Bitmap bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length);if(bitmap==null)throw new IOException();bitmap.recycle();
    }
    private void confirm(ConfigurationData.Document doc){
        if(!alive()){busy=false;return;}
        new ToolDialog(a).setTitle(a.getString(R.string.config_import_title)).setMessage(a.getString(R.string.config_import_summary,doc.regions,doc.places,doc.routes,doc.courses,doc.lists)+(doc.backgroundSpecified?a.getString(R.string.config_background_replace):a.getString(R.string.config_background_legacy))+a.getString(R.string.config_import_warning))
            .setNegativeButton(a.getString(R.string.action_cancel),null).setPositiveButton(a.getString(R.string.config_confirm),(d,n)->apply(doc)).setOnDismissListener(d->{if(!applying)busy=false;}).show();
    }
    private void apply(ConfigurationData.Document doc){
        if(RouteRecorder.active()){finish(R.string.config_finish_route);return;}
        applying=true;a.prepareConfigurationImport();
        AlertDialog progress=new ToolDialog(a).setTitle(a.getString(R.string.config_import_title)).setMessage(a.getString(R.string.config_working)).setCancelable(false).show();
        new Thread(()->{
            Map<String,Map<String,?>> before=new LinkedHashMap<>();File image=new File(a.getFilesDir(),"background.jpg"),temp=new File(a.getFilesDir(),"configuration-background.tmp");byte[] oldImage=null;boolean success=false,restored=true,changed=false;
            try{
                if(image.exists())oldImage=Files.readAllBytes(image.toPath());
                for(String group:ConfigurationData.GROUPS)before.put(group,new HashMap<>(a.getSharedPreferences(group,0).getAll()));
                if(doc.backgroundSpecified&&doc.background!=null)Files.write(temp.toPath(),doc.background);
                changed=true;
                for(String group:ConfigurationData.GROUPS){if(group.equals("random_choice")&&!doc.randomSpecified)continue;SharedPreferences prefs=a.getSharedPreferences(group,0);Map<String,Object> next=new HashMap<>(before.get(group));
                    next.keySet().removeIf(k->ConfigurationData.type(group,k)!=null);next.putAll(doc.groups.get(group));
                    if(group.equals("toolbox")){next.put("quake_wolfx_ack_112",false);next.put("last_tab",0);}
                    if(!write(prefs,next))throw new IOException();
                }
                if(doc.backgroundSpecified){if(doc.background==null)Files.deleteIfExists(image.toPath());else Files.move(temp.toPath(),image.toPath(),StandardCopyOption.REPLACE_EXISTING);}
                success=true;
            }catch(Exception e){
                if(changed)for(Map.Entry<String,Map<String,?>> entry:before.entrySet())if(!write(a.getSharedPreferences(entry.getKey(),0),entry.getValue()))restored=false;
                try{if(changed){if(oldImage==null)Files.deleteIfExists(image.toPath());else Files.write(image.toPath(),oldImage);}}catch(Exception restore){restored=false;}
            }finally{temp.delete();}
            final boolean okay=success,rollback=restored;
            main.post(()->{busy=applying=false;if(!alive())return;progress.dismiss();a.configurationImportFinished(okay,rollback);});
        },"configuration-apply").start();
    }
    private static boolean write(SharedPreferences p,Map<String,?> values){SharedPreferences.Editor e=p.edit().clear();for(Map.Entry<String,?> item:values.entrySet()){Object v=item.getValue();String k=item.getKey();if(v instanceof Boolean)e.putBoolean(k,(Boolean)v);else if(v instanceof Integer)e.putInt(k,(Integer)v);else if(v instanceof Long)e.putLong(k,(Long)v);else if(v instanceof Float)e.putFloat(k,(Float)v);else if(v instanceof String)e.putString(k,(String)v);else if(v instanceof Set){Set<String> set=new HashSet<>();for(Object s:(Set<?>)v)set.add(String.valueOf(s));e.putStringSet(k,set);}}return e.commit();}
    private void finish(int message){busy=false;pending=null;if(alive())a.toast(a.getString(message));}
}
