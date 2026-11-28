package org.daidai.toolbox;

import android.content.*;
import android.net.Uri;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.*;
import org.daidai.contracts.MusicDataContract;

/** Configuration archive with embedded app data and an install-state manifest. */
final class InternalDataBackup {
    static final long MAX=256L*1024*1024,MAX_FILE=16L*1024*1024;
    private static final String[] IDS={"music","messaging","colorvision"};
    static void erase(Context c,String id)throws Exception{if(!AppModules.known(id))throw new IOException();try{ModuleRuntime.beginChange(c,id);if(!c.getSharedPreferences(id+"_module",0).edit().clear().commit())throw new IOException();if(id.equals("messaging"))c.getSharedPreferences("messaging_device_identity",0).edit().clear().commit();removeChecked(ModuleRuntime.dataDirectory(c,id));removeChecked(new File(c.getCacheDir(),"internal-"+id));}finally{ModuleRuntime.endChange(id);}}
    static final class Part {String id,oldRoot;Map<String,Object> preferences;File staged,backup;Map<String,?> before;boolean touched;}
    static final class Prepared implements Closeable {
        final ConfigurationData.Document document;final List<Part> parts;final File stage;final int fileCount;final List<String> installedApps;boolean changing;String moduleId="";
        Prepared(ConfigurationData.Document doc,List<Part> parts,File stage,int count){this(doc,parts,stage,count,Collections.emptyList());}
        Prepared(ConfigurationData.Document doc,List<Part> parts,File stage,int count,List<String> installedApps){document=doc;this.parts=parts;this.stage=stage;fileCount=count;this.installedApps=Collections.unmodifiableList(new ArrayList<>(installedApps));}
        void apply(Context c)throws Exception{changing=true;for(Part p:parts){ModuleRuntime.beginChange(c,p.id);File live=ModuleRuntime.dataDirectory(c,p.id);p.backup=new File(c.getFilesDir(),"config-previous-"+p.id+"-"+UUID.randomUUID());p.before=new HashMap<>(c.getSharedPreferences(p.id+"_module",0).getAll());
                Files.move(live.toPath(),p.backup.toPath());p.touched=true;Files.move(p.staged.toPath(),live.toPath());
                Map<String,Object> values=new LinkedHashMap<>();for(Map.Entry<String,Object> e:p.preferences.entrySet())values.put(e.getKey(),remap(e.getValue(),p.oldRoot,live.getAbsolutePath()));
                if(p.id.equals("music")){String tree=(String)values.getOrDefault("storage_tree_uri","");boolean granted=false;for(android.content.UriPermission grant:c.getContentResolver().getPersistedUriPermissions())if(grant.isReadPermission()&&grant.getUri().toString().equals(tree))granted=true;if(!granted)values.put("storage_tree_uri","");values.remove("library_pick_uri");values.remove("library_pick_request");}
                if(!ConfigurationTransfer.write(c.getSharedPreferences(p.id+"_module",0),values))throw new IOException("Cannot restore internal app preferences");
            }
            // Executable packages stay out of the user data archive, but signed copies
            // ship in migration-apps/. Reinstall the apps recorded by the export.
            if(!installedApps.isEmpty()&&c instanceof MainActivity)((MainActivity)c).apps.restoreBundledApps(installedApps);
        }
        boolean finish(Context c,boolean success){boolean restored=true;for(Part p:parts){if(p.touched){File live=ModuleRuntime.dataDirectory(c,p.id);if(!success){remove(live);try{Files.move(p.backup.toPath(),live.toPath());}catch(Exception e){restored=false;}if(!ConfigurationTransfer.write(c.getSharedPreferences(p.id+"_module",0),p.before))restored=false;}else remove(p.backup);}ModuleRuntime.endChange(p.id);}changing=false;close();return restored;}
        @Override public void close(){if(stage!=null)remove(stage);}
    }
    static File exportData(Context c,byte[] configuration)throws Exception{return exportData(c,configuration,new LinkedHashSet<>(AppModules.allIds()),"");}
    static File exportApp(Context c,String id)throws Exception{
        if(!AppModules.known(id))throw new IOException("Unknown application");Map<String,Map<String,?>> empty=new LinkedHashMap<>();for(String group:ConfigurationData.GROUPS)empty.put(group,Collections.emptyMap());byte[] configuration=ConfigurationData.encode(empty,null).toString().getBytes(StandardCharsets.UTF_8);return exportData(c,configuration,Collections.singleton(id),id);
    }
    private static File exportData(Context c,byte[] configuration,Set<String> selected,String moduleId)throws Exception{
        File out=File.createTempFile("configuration-",".zip",c.getCacheDir());boolean success=false;long[] total={configuration.length};JSONObject root=new JSONObject().put("format","daidai-internal-app-data").put("version",1).put("module",moduleId);JSONArray apps=new JSONArray(),installed=new JSONArray();
        boolean fullConfiguration=moduleId.isEmpty();
        List<String> installState=new ArrayList<>();for(String id:AppModules.allIds())if((fullConfiguration||selected.contains(id))&&AppModules.present(c,id))installState.add(id);
        if(fullConfiguration)try{inferInstalledApps(ConfigurationData.parse(new String(configuration,StandardCharsets.UTF_8)),installState,Collections.emptyList());}catch(Exception ignored){}
        for(String id:installState)if(fullConfiguration||selected.contains(id))installed.put(id);
        try(ZipOutputStream zip=new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(out)))){put(zip,"configuration.json",configuration);for(String id:AppModules.allIds()){if(!selected.contains(id))continue;File directory=ModuleRuntime.dataDirectory(c,id);JSONArray files=new JSONArray();JSONObject app=new JSONObject().put("id",id).put("oldRoot",directory.getAbsolutePath()).put("preferences",encode(c.getSharedPreferences(id+"_module",0).getAll()));
                exportFiles(zip,directory,directory,id,files,total);if(id.equals("music"))externalLyrics(c,zip,app,directory,files,total);apps.put(app.put("files",files));}root.put("apps",apps).put("installedApps",installed);put(zip,"internal.json",root.toString().getBytes(StandardCharsets.UTF_8));success=true;
        }finally{if(!success)out.delete();}if(out.length()>MAX){out.delete();throw new IOException("Backup exceeds 256 MiB");}return out;
    }
    private static void externalLyrics(Context c,ZipOutputStream zip,JSONObject app,File directory,JSONArray files,long[] total)throws Exception{
        JSONObject preferences=app.getJSONObject("preferences");Map<String,String> replacements=new HashMap<>();for(String key:new String[]{"catalogue","queue"}){if(!preferences.has(key))continue;String raw=preferences.getJSONObject(key).getString("value");MusicDataContract.nesting(raw);JSONArray rows=new JSONArray(raw);for(int i=0;i<rows.length();i++){JSONObject row=rows.optJSONObject(i);if(row==null)continue;for(String field:new String[]{"lrcPath","coverPath"}){String source=row.optString(field);if(!source.startsWith("content://")||replacements.containsKey(source))continue;byte[] bytes=AppModules.read(c.getContentResolver().openInputStream(Uri.parse(source)),(int)MAX_FILE);if((total[0]+=bytes.length)>MAX)throw new IOException("Backup exceeds limit");String path="backup-attachments/"+UUID.randomUUID()+(field.equals("lrcPath")?".lrc":".cover");put(zip,"apps/music/"+path,bytes);files.put(new JSONObject().put("path",path).put("bytes",bytes.length).put("sha256",hash(bytes)));replacements.put(source,new File(directory,path).getAbsolutePath());}}}
        for(String key:new String[]{"catalogue","queue"})if(preferences.has(key)){JSONObject setting=preferences.getJSONObject(key);JSONArray rows=new JSONArray(setting.getString("value"));for(int i=0;i<rows.length();i++){JSONObject row=rows.optJSONObject(i);if(row!=null)for(String field:new String[]{"lrcPath","coverPath"})if(replacements.containsKey(row.optString(field)))row.put(field,replacements.get(row.getString(field)));}setting.put("value",rows.toString());}
    }
    private static void exportFiles(ZipOutputStream zip,File root,File directory,String id,JSONArray rows,long[] total)throws Exception{File[] files=directory.listFiles();if(files==null)return;Arrays.sort(files,Comparator.comparing(File::getName));for(File f:files){if(Files.isSymbolicLink(f.toPath())||!f.getCanonicalPath().startsWith(root.getCanonicalPath()+File.separator))throw new IOException("Invalid app data path");if(f.isDirectory()){exportFiles(zip,root,f,id,rows,total);continue;}if(f.length()>MAX_FILE||(total[0]+=f.length())>MAX||rows.length()>20000)throw new IOException("App data exceeds backup limit");String relative=root.toPath().relativize(f.toPath()).toString().replace('\\','/'),entry="apps/"+id+"/"+relative;byte[] bytes=Files.readAllBytes(f.toPath());put(zip,entry,bytes);rows.put(new JSONObject().put("path",relative).put("bytes",bytes.length).put("sha256",hash(bytes)));}}
    static Prepared read(Context c,Uri source)throws Exception{return read(c,source,null);}
    static Prepared readApp(Context c,Uri source,String id)throws Exception{if(!AppModules.known(id))throw new IOException("Unknown application");return read(c,source,id);}
    private static Prepared read(Context c,Uri source,String selected)throws Exception{
        File stage=new File(c.getCacheDir(),"configuration-import-"+UUID.randomUUID());if(!stage.mkdirs())throw new IOException();boolean success=false;File input=new File(stage,"input");
        try{try(InputStream in=c.getContentResolver().openInputStream(source);OutputStream out=new FileOutputStream(input)){AppModules.copy(in,out,MAX);}try(InputStream in=new FileInputStream(input)){if(in.read()!='P'||in.read()!='K'){if(selected!=null||input.length()>ConfigurationData.MAX_BYTES)throw new IOException();String text=new String(Files.readAllBytes(input.toPath()),StandardCharsets.UTF_8);Prepared result=new Prepared(ConfigurationData.parse(text),new ArrayList<>(),stage,0);success=true;return result;}}
            try(ZipFile zip=new ZipFile(input)){Set<String> names=new HashSet<>();Enumeration<? extends ZipEntry> entries=zip.entries();long total=0;while(entries.hasMoreElements()){ZipEntry e=entries.nextElement();String name=e.getName();if(!names.add(name)||name.contains("..")||name.contains("\\")||name.startsWith("/")||e.isDirectory()||e.getSize()<0||e.getSize()>(name.equals("configuration.json")?ConfigurationData.MAX_BYTES:MAX_FILE)||(total+=e.getSize())>MAX)throw new IOException("Invalid backup entry");}
                String config=new String(readEntry(zip,"configuration.json",ConfigurationData.MAX_BYTES),StandardCharsets.UTF_8),internal=new String(readEntry(zip,"internal.json",(int)MAX_FILE),StandardCharsets.UTF_8);ConfigurationData.Document configDocument=ConfigurationData.parse(config);ConfigurationData.checkNesting(internal);JSONObject root=new JSONObject(internal);
                if(!root.getString("format").equals("daidai-internal-app-data")||root.getInt("version")!=1)throw new IOException("Unsupported internal data version");String moduleId=root.optString("module","");if(selected==null?!moduleId.isEmpty():!selected.equals(moduleId))throw new IOException("Wrong application backup");JSONArray apps=root.getJSONArray("apps");if(selected!=null&&(apps.length()!=1||!selected.equals(apps.getJSONObject(0).optString("id"))))throw new IOException("Wrong application backup");if(apps.length()>AppModules.allIds().size())throw new IOException("Too many application data sets");List<String> installedApps=new ArrayList<>();if(root.has("installedApps")){JSONArray listed=root.getJSONArray("installedApps");if(listed.length()>16)throw new IOException();for(int i=0;i<listed.length();i++){String id=listed.getString(i);if(!AppModules.known(id)||installedApps.contains(id))throw new IOException("Invalid installed app manifest");installedApps.add(id);}}else if(selected!=null)installedApps.add(selected);else installedApps.addAll(AppModules.allIds());if(selected!=null&&(!installedApps.contains(selected)||installedApps.size()!=1))throw new IOException("Invalid application manifest");List<Part> parts=new ArrayList<>();Set<String> ids=new HashSet<>(),expected=new HashSet<>(Arrays.asList("configuration.json","internal.json"));int count=0;
                for(int n=0;n<apps.length();n++){JSONObject app=apps.getJSONObject(n);Part p=new Part();p.id=app.getString("id");if(!AppModules.known(p.id)||!ids.add(p.id))throw new IOException("Unknown or duplicate app data");p.oldRoot=app.getString("oldRoot");if(p.oldRoot.isEmpty()||p.oldRoot.length()>4096)throw new IOException();p.preferences=decode(app.getJSONObject("preferences"),p.id);p.staged=new File(stage,"prepared-"+p.id);if(!p.staged.mkdirs())throw new IOException();JSONArray files=app.getJSONArray("files");if(files.length()>20000)throw new IOException();
                    for(int i=0;i<files.length();i++){JSONObject file=files.getJSONObject(i);String path=file.getString("path"),entry="apps/"+p.id+"/"+path;if(path.isEmpty()||path.startsWith("/")||path.contains("..")||path.contains("\\")||!expected.add(entry))throw new IOException("Invalid data path");byte[] bytes=readEntry(zip,entry,(int)MAX_FILE);if(bytes.length!=file.getLong("bytes")||!hash(bytes).equals(file.getString("sha256")))throw new IOException("Backup checksum mismatch");File dest=new File(p.staged,path);if(!dest.getCanonicalPath().startsWith(p.staged.getCanonicalPath()+File.separator))throw new IOException();if(!dest.getParentFile().isDirectory()&&!dest.getParentFile().mkdirs())throw new IOException();Files.write(dest.toPath(),bytes);count++;}parts.add(p);
                }
                if(!names.equals(expected))throw new IOException("Unexpected or missing backup files");
                // Older full backups recorded only the packages that happened to be
                // readable at export time. The desktop configuration is the user's
                // authoritative install list, so recover those entries as well.
                if(selected==null)inferInstalledApps(configDocument,installedApps,new ArrayList<>(ids));
                Prepared result=new Prepared(configDocument,parts,stage,count,installedApps);result.moduleId=moduleId;success=true;return result;
            }
        }finally{if(!success)remove(stage);}
    }
    private static void inferInstalledApps(ConfigurationData.Document document,List<String> installed,List<String> archivedData){
        LinkedHashSet<String> ids=new LinkedHashSet<>(installed);
        ids.addAll(archivedData);
        Map<String,Object> toolbox=document.groups.get("toolbox");
        Object order=toolbox==null?null:toolbox.get("launcher_order");
        if(order instanceof String)for(String id:((String)order).split(","))if(AppModules.known(id))ids.add(id);
        // 3.0.x stores the authoritative desktop placement in separate phone
        // and tablet JSON layouts.  The old comma-separated order may be
        // absent, so recover applications from both roots and folder members.
        if(toolbox!=null){
            addLayoutApps(toolbox.get("launcher_layout_phone"),false,ids);
            addLayoutApps(toolbox.get("launcher_layout_tablet"),true,ids);
        }
        if(toolbox!=null)for(Map.Entry<String,Object> entry:toolbox.entrySet())if(entry.getKey().startsWith("tab_show_")&&Boolean.TRUE.equals(entry.getValue()))try{int n=Integer.parseInt(entry.getKey().substring("tab_show_".length()));if(n>=0&&n<AppModules.IDS.length)ids.add(AppModules.IDS[n]);}catch(NumberFormatException ignored){}
        // A valid full backup always retains the two default applications unless
        // the user explicitly removed them from the desktop; their data is still
        // needed for a usable fresh installation.
        if(toolbox!=null&&(toolbox.containsKey("launcher_order")||!ids.isEmpty())){ids.add("weather");ids.add("status");}
        installed.clear();installed.addAll(ids);
    }
    private static void addLayoutApps(Object raw,boolean tablet,Set<String> ids){
        if(!(raw instanceof String)||((String)raw).isEmpty())return;
        try{
            LauncherLayout layout=LauncherLayout.parse((String)raw,tablet);
            for(String id:layout.order)if(LauncherLayout.application(id))ids.add(id);
            for(LauncherLayout.Folder folder:layout.folders.values())for(String id:folder.apps)if(LauncherLayout.application(id))ids.add(id);
        }catch(Exception ignored){
            // A malformed optional profile must not invalidate the rest of a backup.
        }
    }
    private static byte[] readEntry(ZipFile zip,String entry,int max)throws IOException{ZipEntry e=zip.getEntry(entry);if(e==null)throw new IOException("Missing backup entry");return AppModules.read(zip.getInputStream(e),max);}
    private static void put(ZipOutputStream zip,String entry,byte[] bytes)throws IOException{zip.putNextEntry(new ZipEntry(entry));zip.write(bytes);zip.closeEntry();}
    private static JSONObject encode(Map<String,?> values)throws Exception{JSONObject out=new JSONObject();for(Map.Entry<String,?> e:values.entrySet()){if(e.getKey().startsWith("library_pick_"))continue;Object v=e.getValue();String type=v instanceof Boolean?"boolean":v instanceof Integer?"int":v instanceof Long?"long":v instanceof Float?"float":v instanceof Set?"set":"string";out.put(e.getKey(),new JSONObject().put("type",type).put("value",v instanceof Set?new JSONArray((Set<?>)v):v));}return out;}
    private static Map<String,Object> decode(JSONObject root,String id)throws Exception{if(root.length()>200)throw new IOException();Map<String,Object> out=new LinkedHashMap<>();Iterator<String> keys=root.keys();while(keys.hasNext()){String key=keys.next();if(!key.matches("[a-zA-Z0-9_]{1,100}"))throw new IOException();JSONObject row=root.getJSONObject(key);Object v=row.get("value");String type=row.getString("type");switch(type){case "boolean":if(!(v instanceof Boolean))throw new IOException();break;case "string":if(!(v instanceof String)||((String)v).length()>16*1024*1024)throw new IOException();break;case "int":if(!(v instanceof Number)||((Number)v).doubleValue()!=((Number)v).intValue())throw new IOException();v=((Number)v).intValue();break;case "long":if(!(v instanceof Number))throw new IOException();v=((Number)v).longValue();break;case "float":if(!(v instanceof Number)||!Float.isFinite(((Number)v).floatValue()))throw new IOException();v=((Number)v).floatValue();break;case "set":if(!(v instanceof JSONArray)||((JSONArray)v).length()>20000)throw new IOException();Set<String> set=new HashSet<>();for(int n=0;n<((JSONArray)v).length();n++){Object item=((JSONArray)v).get(n);if(!(item instanceof String))throw new IOException();set.add((String)item);}v=set;break;default:throw new IOException("Invalid setting type");}
            validate(id,key,type,v);out.put(key,v);}if(id.equals("music"))MusicDataContract.validate(out);return out;}
    private static void validate(String app,String key,String type,Object value)throws Exception{
        // Traditional feature packages keep their user settings in the host
        // configuration groups. Their private module preference namespace is
        // intentionally empty; reject injected keys instead of silently
        // accepting malformed archives.
        if(!app.equals("music")&&!app.equals("messaging")&&!app.equals("colorvision"))throw new IOException("Unsupported feature data");
        if(app.equals("colorvision")){if(key.equals("front_camera")){if(!type.equals("boolean"))throw new IOException("Invalid camera settings");}else if(!key.equals("views")||!type.equals("int")||(Integer)value<1||(Integer)value>15)throw new IOException("Invalid view settings");}
        if(app.equals("messaging")){if(key.equals("last_host")){if(!type.equals("string")||((String)value).length()>1000)throw new IOException();}else if(!Arrays.asList("bluetooth","notification_preview").contains(key)||!type.equals("boolean"))throw new IOException("Invalid messaging settings");}
        if(app.equals("music")){
            String expected=null;if(Arrays.asList("catalogue","queue","collections","storage_tree_uri","mode","credit_roles_order").contains(key))expected="string";else if(Arrays.asList("metadata_manually_edited","credit_roles_visible").contains(key))expected="set";else if(Arrays.asList("cover_alpha","speed").contains(key))expected="float";else if(Arrays.asList("cover_show","bpm_keep_decimal","spectrum_log","pitch_follow_speed","analysis_show","sleep_timer_finish_track").contains(key))expected="boolean";else if(Arrays.asList("max_volume_percent","volume_percent","spectrum_peaks","spectrum_history_ms","analysis_display_mode","lyrics_font_sp","sleep_timer_minutes").contains(key))expected="int";
            if(expected!=null&&!expected.equals(type))throw new IOException("Wrong music setting type");if(Arrays.asList("catalogue","queue","collections").contains(key)){ConfigurationData.checkNesting((String)value);if(key.equals("collections"))new JSONObject((String)value);else new JSONArray((String)value);}
            if(key.equals("speed")&&((Float)value<.5f||(Float)value>2)||key.equals("cover_alpha")&&((Float)value<0||(Float)value>1))throw new IOException("Invalid music setting");
            if(Arrays.asList("max_volume_percent","volume_percent").contains(key)&&((Integer)value<0||(Integer)value>100))throw new IOException("Invalid volume");
        }
    }
    private static Object remap(Object value,String oldRoot,String newRoot)throws Exception{if(value instanceof String){String s=(String)value;if(s.startsWith(oldRoot+"/"))return newRoot+s.substring(oldRoot.length());if(s.startsWith("{")||s.startsWith("[")){try{Object parsed=s.startsWith("{")?new JSONObject(s):new JSONArray(s);return rewriteJson(parsed,oldRoot,newRoot).toString();}catch(JSONException ignored){}}}if(value instanceof Set){Set<String> out=new HashSet<>();for(Object s:(Set<?>)value)out.add((String)remap(s,oldRoot,newRoot));return out;}return value;}
    private static Object rewriteJson(Object value,String oldRoot,String newRoot)throws Exception{if(value instanceof JSONObject){JSONObject old=(JSONObject)value,out=new JSONObject();Iterator<String> keys=old.keys();while(keys.hasNext()){String key=keys.next();out.put(key,rewriteJson(old.get(key),oldRoot,newRoot));}return out;}if(value instanceof JSONArray){JSONArray old=(JSONArray)value,out=new JSONArray();for(int n=0;n<old.length();n++)out.put(rewriteJson(old.get(n),oldRoot,newRoot));return out;}return remap(value,oldRoot,newRoot);}
    private static String hash(byte[] bytes)throws Exception{StringBuilder out=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(bytes))out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();}
    private static void removeChecked(File f)throws IOException{if(f==null||!f.exists())return;if(!Files.isSymbolicLink(f.toPath())&&f.isDirectory()){File[] children=f.listFiles();if(children==null)throw new IOException("Cannot inspect app storage");for(File child:children)removeChecked(child);}if(!f.delete())throw new IOException("Cannot delete app data");}
    private static void remove(File f){try{removeChecked(f);}catch(IOException ignored){}}
}
