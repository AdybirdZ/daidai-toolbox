package org.daidai.modules.music;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import org.json.*;
import java.io.*;
import java.security.*;
import java.util.*;
import java.util.zip.*;
import org.daidai.contracts.MusicDataContract;

/** Same-device transfer: no audio is copied and no private path is read by another app. */
public final class MusicMigration {
    public static final long MAX_ARCHIVE=256L*1024*1024,MAX_ASSET=16L*1024*1024;
    private static final int MAX_ITEMS=20000;
    private MusicMigration(){}
    public static final class Summary {public final int songs,playlists,lyrics,artwork;Summary(int s,int p,int l,int a){songs=s;playlists=p;lyrics=l;artwork=a;}}
    public static final class Prepared implements Closeable {
        public final Summary summary;private final Map<String,Object> preferences;private File assets;
        Prepared(Summary summary,Map<String,Object> preferences,File assets){this.summary=summary;this.preferences=preferences;this.assets=assets;}
        public synchronized void commit(Context c)throws Exception{if(assets==null||Thread.currentThread().isInterrupted())throw new InterruptedIOException();SharedPreferences prefs=c.getSharedPreferences("music_module",0);Map<String,?> before=new HashMap<>(prefs.getAll());synchronized(MusicLibrary.class){if(!write(prefs,preferences)){write(prefs,before);throw new IOException("Unable to save music data");}}assets=null;}
        @Override public synchronized void close(){if(assets!=null)remove(assets);assets=null;}
    }
    public static File exportData(Context c)throws Exception{
        return exportData(c, Collections.emptyMap(), Collections.emptySet());
    }

    static final class Unlinked extends IOException {
        final MusicLibrary.Track track;
        Unlinked(MusicLibrary.Track track, String reason) {
            super(reason);
            this.track = track;
        }
    }

    static File exportData(Context c, Map<String,String> choices, Set<String> skipped) throws Exception {
        SharedPreferences prefs = c.getSharedPreferences("music_module", 0);
        String tree = prefs.getString("storage_tree_uri", "");
        List<MusicLibrary.Track> songs = MusicLibrary.catalogue(c);
        if (!songs.isEmpty() && tree.isEmpty()) throw new IOException("Choose the original music folder first");
        Map<String,FileRef> files = tree.isEmpty() ? Collections.emptyMap() : scan(c, tree);
        Map<String,FileRef> selected = new LinkedHashMap<>();
        Set<String> reserved = new HashSet<>();
        for (MusicLibrary.Track song : songs) {
            if (files.containsKey(identity(song.uri))) reserved.add(identity(song.uri));
        }
        Map<String,String> links = new HashMap<>();
        JSONArray catalogue = new JSONArray();
        for (MusicLibrary.Track song : songs) {
            if (skipped.contains(song.uri)) continue;
            String target = identity(choices.getOrDefault(song.uri, song.uri));
            FileRef ref = files.get(target);
            if (ref == null) throw new Unlinked(song, choices.containsKey(song.uri) ? "outside" : "unlinked");
            if (!target.equals(identity(song.uri)) && !reserved.add(target)) throw new Unlinked(song, "occupied");
            selected.put(song.uri, ref);
            links.put(identity(song.uri), ref.uri.toString());
            catalogue.put(MusicLibrary.encode(song).put("uri", ref.uri.toString()));
        }
        Map<String,Object> values = new HashMap<>(prefs.getAll());
        values.put("catalogue", catalogue.toString());
        values.put("queue", linkedReferences(new JSONArray(prefs.getString("queue", "[]")), links).toString());
        JSONObject lists = new JSONObject(prefs.getString("collections", "{}"));
        Iterator<String> names = lists.keys();
        while (names.hasNext()) {
            String name = names.next();
            if (!name.equals("playlistOrder")) lists.put(name, linkedReferences(lists.getJSONArray(name), links));
        }
        values.put("collections", lists.toString());
        Set<String> edited = new HashSet<>();
        for (String old : prefs.getStringSet("metadata_manually_edited", Collections.emptySet())) {
            String uri = links.get(identity(old));
            if (uri != null) edited.add(identity(uri));
        }
        values.put("metadata_manually_edited", edited);
        JSONObject root=new JSONObject().put("format","daidai-music-same-device").put("version",1).put("audioIncluded",false).put("preferences",encode(values));
        JSONArray records=new JSONArray(),assets=new JSONArray();File output=File.createTempFile("music-transfer-",".zip",c.getCacheDir());boolean success=false;
        try(ZipOutputStream zip=new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(output)))){
            Map<String,String> stored=new HashMap<>();
            for(MusicLibrary.Track song:songs){
                FileRef ref = selected.get(song.uri);
                if (ref == null) continue;
                Fingerprint fingerprint;
                try { fingerprint = fingerprint(c,ref.uri); }
                catch (FileNotFoundException | SecurityException e) { throw new Unlinked(song, "unreadable"); }
                records.put(new JSONObject().put("uri",ref.uri.toString()).put("identity",identity(ref.uri.toString())).put("relativePath",ref.path).put("bytes",fingerprint.bytes).put("sha256",fingerprint.hash));
                addAsset(c,zip,assets,stored,song.coverPath,"artwork");addAsset(c,zip,assets,stored,song.lrcPath,"lyrics");
            }
            root.put("songs",records).put("assets",assets);byte[] manifest=root.toString().getBytes("UTF-8");if(manifest.length>16*1024*1024)throw new IOException("Too much music metadata");zip.putNextEntry(new ZipEntry("music.json"));zip.write(manifest);zip.closeEntry();success=true;
        }finally{if(!success)output.delete();}if(output.length()>MAX_ARCHIVE){output.delete();throw new IOException("Music transfer exceeds 256 MiB; audio is not included");}return output;
    }
    private static JSONArray linkedReferences(JSONArray rows, Map<String,String> links) throws JSONException {
        JSONArray result = new JSONArray();
        for (int i = 0; i < rows.length(); i++) {
            Object row = rows.get(i);
            String old = row instanceof JSONObject ? ((JSONObject)row).getString("uri") : rows.getString(i);
            String target = links.get(identity(old));
            if (target != null) result.put(target);
        }
        return result;
    }
    private static void addAsset(Context c,ZipOutputStream zip,JSONArray list,Map<String,String> stored,String source,String kind)throws Exception{
        if(source==null||source.isEmpty()||stored.containsKey(source))return;String entry="files/"+list.length()+".bin";byte[] bytes;
        try(InputStream in=open(c,source)){bytes=read(in,(int)MAX_ASSET);}zip.putNextEntry(new ZipEntry(entry));zip.write(bytes);zip.closeEntry();stored.put(source,entry);list.put(new JSONObject().put("source",source).put("entry",entry).put("kind",kind).put("bytes",bytes.length).put("sha256",sha(bytes)));
    }
    private static InputStream open(Context c,String source)throws Exception{Uri uri=Uri.parse(source);if("content".equals(uri.getScheme()))return c.getContentResolver().openInputStream(uri);File file=new File("file".equals(uri.getScheme())?uri.getPath():source);if(!file.getCanonicalPath().startsWith(c.getFilesDir().getCanonicalPath()+File.separator))throw new IOException("Unsupported private attachment");return new FileInputStream(file);}
    public static File copyArchive(Context c,Uri source)throws Exception{File file=File.createTempFile("music-import-",".zip",c.getCacheDir());try(InputStream in=c.getContentResolver().openInputStream(source);OutputStream out=new FileOutputStream(file)){copy(in,out,MAX_ARCHIVE);return file;}catch(Exception e){file.delete();throw e;}}
    public static Summary inspect(File archive)throws Exception{try(ZipFile zip=new ZipFile(archive)){JSONObject root=manifest(zip);JSONArray songs=root.getJSONArray("songs"),assets=root.getJSONArray("assets");int lyrics=0,art=0;for(int n=0;n<assets.length();n++)if(assets.getJSONObject(n).getString("kind").equals("lyrics"))lyrics++;else art++;JSONObject p=root.getJSONObject("preferences");String collections=p.has("collections")?p.getJSONObject("collections").getString("value"):"{}";JSONObject lists=new JSONObject(collections);int playlists=0;Iterator<String> keys=lists.keys();while(keys.hasNext()){String key=keys.next();if(!key.equals("favorites")&&!key.equals("playlistOrder")&&lists.opt(key) instanceof JSONArray)playlists++;}return new Summary(songs.length(),playlists,lyrics,art);}}
    private static JSONObject manifest(ZipFile zip)throws Exception{
        ZipEntry e=zip.getEntry("music.json");if(e==null)throw new IOException("Not a music transfer file");String manifest=new String(read(zip.getInputStream(e),16*1024*1024),"UTF-8");MusicDataContract.nesting(manifest);JSONObject root=new JSONObject(manifest);
        if(!root.getString("format").equals("daidai-music-same-device")||root.getInt("version")!=1||root.getBoolean("audioIncluded"))throw new IOException("Unsupported music transfer version");
        if(root.getJSONArray("songs").length()>MAX_ITEMS||root.getJSONArray("assets").length()>MAX_ITEMS*2)throw new IOException("Too many entries");decode(root.getJSONObject("preferences"));
        Set<String> names=new HashSet<>();Enumeration<? extends ZipEntry> all=zip.entries();long total=0;while(all.hasMoreElements()){ZipEntry item=all.nextElement();String name=item.getName();if(!names.add(name)||!(name.equals("music.json")||name.matches("files/[0-9]+\\.bin"))||item.isDirectory()||item.getSize()<0||item.getSize()>MAX_ASSET||(total+=item.getSize())>MAX_ARCHIVE)throw new IOException("Invalid music transfer archive");}
        Set<String> expected=new HashSet<>(),sources=new HashSet<>(),verified=new HashSet<>();expected.add("music.json");JSONArray songs=root.getJSONArray("songs");for(int n=0;n<songs.length();n++){JSONObject song=songs.getJSONObject(n);String uri=song.getString("uri"),path=song.getString("relativePath");if(!verified.add(uri)||!identity(uri).equals(song.getString("identity"))||path.isEmpty()||path.startsWith("/")||path.contains("..")||path.contains("\\")||song.getLong("bytes")<0||!song.getString("sha256").matches("[0-9a-f]{64}"))throw new IOException("Invalid audio identity");}
        Map<String,Object> data=decode(root.getJSONObject("preferences"));for(String key:new String[]{"catalogue","queue"}){JSONArray rows=new JSONArray((String)data.getOrDefault(key,"[]"));for(int i=0;i<rows.length();i++){Object row=rows.get(i);String uri=row instanceof JSONObject?((JSONObject)row).getString("uri"):(String)row;if(!verified.contains(uri))throw new IOException("Unverified song reference");}}
        JSONArray assets=root.getJSONArray("assets");for(int n=0;n<assets.length();n++){JSONObject asset=assets.getJSONObject(n);String entry=asset.getString("entry");if(!entry.matches("files/[0-9]+\\.bin")||!expected.add(entry)||!sources.add(asset.getString("source"))||!Arrays.asList("lyrics","artwork").contains(asset.getString("kind"))||!asset.getString("sha256").matches("[0-9a-f]{64}"))throw new IOException("Invalid attachment entry");}if(!names.equals(expected))throw new IOException("Unexpected or missing attachment");return root;
    }
    public static Prepared prepare(Context c,File archive,String tree)throws Exception{
        Summary summary=inspect(archive);File target=new File(c.getFilesDir(),"migration-"+UUID.randomUUID());if(!target.mkdirs())throw new IOException("Unable to create music data directory");boolean success=false;
        try(ZipFile zip=new ZipFile(archive)){JSONObject root=manifest(zip);Map<String,FileRef> files=scan(c,tree);Map<String,List<FileRef>> paths=new HashMap<>();for(FileRef ref:files.values())paths.computeIfAbsent(ref.path,k->new ArrayList<>()).add(ref);
            Map<String,String> replacements=new HashMap<>();ArrayList<String> missing=new ArrayList<>();JSONArray songs=root.getJSONArray("songs");Set<String> identities=new HashSet<>();
            for(int n=0;n<songs.length();n++){JSONObject row=songs.getJSONObject(n);String old=row.getString("uri");if(!identities.add(identity(old)))throw new IOException("Duplicate music file identity");FileRef file=files.get(row.getString("identity"));if(file==null){List<FileRef> at=paths.get(row.getString("relativePath"));if(at!=null&&at.size()==1)file=at.get(0);}
                if(file==null){missing.add(row.getString("relativePath"));continue;}Fingerprint f=fingerprint(c,file.uri);if(f.bytes!=row.getLong("bytes")||!f.hash.equals(row.getString("sha256"))){missing.add(row.getString("relativePath"));continue;}
                replacements.put(old,file.uri.toString());replacements.put(row.getString("identity"),identity(file.uri.toString()));
            }
            if(!missing.isEmpty())throw new IOException(org.daidai.modules.shared.Texts.get(c,"music_migration_missing",missing.size(),android.text.TextUtils.join("\n",missing.subList(0,Math.min(missing.size(),30)))));
            JSONArray assets=root.getJSONArray("assets");for(int n=0;n<assets.length();n++){JSONObject item=assets.getJSONObject(n);byte[] bytes=read(zip.getInputStream(zip.getEntry(item.getString("entry"))),(int)MAX_ASSET);if(bytes.length!=item.getLong("bytes")||!sha(bytes).equals(item.getString("sha256")))throw new IOException("Attachment checksum mismatch");String kind=item.getString("kind");if(!kind.equals("lyrics")&&!kind.equals("artwork"))throw new IOException("Unknown attachment type");File asset=new File(target,n+(kind.equals("lyrics")?".lrc":".cover"));try(OutputStream out=new FileOutputStream(asset)){out.write(bytes);}replacements.put(item.getString("source"),asset.getAbsolutePath());}
            JSONObject encoded=(JSONObject)rewrite(root.getJSONObject("preferences"),replacements);Map<String,Object> values=decode(encoded);values.put("storage_tree_uri",tree);values.remove("library_pick_uri");values.remove("library_pick_request");validateTracks(values);Prepared result=new Prepared(summary,values,target);success=true;return result;
        }finally{if(!success)remove(target);}
    }
    private static void validateTracks(Map<String,Object> values)throws Exception{JSONArray rows=new JSONArray((String)values.getOrDefault("catalogue","[]"));if(rows.length()>MAX_ITEMS)throw new IOException("Too many songs");for(int n=0;n<rows.length();n++){JSONObject row=rows.getJSONObject(n);if(!row.get("uri").getClass().equals(String.class)||row.getString("uri").isEmpty()||row.optString("title").length()>10000||!MusicLibrary.normalizeBpm(row.optString("bpm")).equals(row.optString("bpm"))||row.optInt("rating",-1)<-1||row.optInt("rating",-1)>10)throw new IOException("Invalid song metadata");}new JSONArray((String)values.getOrDefault("queue","[]"));new JSONObject((String)values.getOrDefault("collections","{}"));}
    private static Object rewrite(Object value,Map<String,String> replacements)throws Exception{
        if(value instanceof JSONObject){JSONObject object=(JSONObject)value,out=new JSONObject();Iterator<String> keys=object.keys();while(keys.hasNext()){String key=keys.next();out.put(key,rewrite(object.get(key),replacements));}return out;}
        if(value instanceof JSONArray){JSONArray a=(JSONArray)value,out=new JSONArray();for(int n=0;n<a.length();n++)out.put(rewrite(a.get(n),replacements));return out;}
        if(value instanceof String){String text=(String)value;if(replacements.containsKey(text))return replacements.get(text);if(text.startsWith("{")||text.startsWith("[")){try{Object parsed=text.startsWith("{")?new JSONObject(text):new JSONArray(text);return rewrite(parsed,replacements).toString();}catch(JSONException ignored){}}}return value;
    }
    public static JSONObject encode(Map<String,?> values)throws Exception{JSONObject out=new JSONObject();for(Map.Entry<String,?> e:values.entrySet()){String key=e.getKey();if(key.startsWith("library_pick_"))continue;Object v=e.getValue();String type=v instanceof Boolean?"boolean":v instanceof Integer?"int":v instanceof Long?"long":v instanceof Float?"float":v instanceof Set?"set":"string";out.put(key,new JSONObject().put("type",type).put("value",v instanceof Set?new JSONArray((Set<?>)v):v));}return out;}
    public static Map<String,Object> decode(JSONObject object)throws Exception{if(object.length()>200)throw new IOException("Too many settings");Map<String,Object> out=new LinkedHashMap<>();Iterator<String> keys=object.keys();while(keys.hasNext()){String key=keys.next();if(!key.matches("[a-zA-Z0-9_]{1,100}"))throw new IOException("Invalid setting name");JSONObject item=object.getJSONObject(key);Object raw=item.get("value");String type=item.getString("type");Object value;switch(type){case "boolean":if(!(raw instanceof Boolean))throw new IOException();value=raw;break;case "int":if(!(raw instanceof Number)||((Number)raw).doubleValue()!=((Number)raw).intValue())throw new IOException();value=((Number)raw).intValue();break;case "long":if(!(raw instanceof Number))throw new IOException();value=((Number)raw).longValue();break;case "float":if(!(raw instanceof Number)||!Float.isFinite(((Number)raw).floatValue()))throw new IOException();value=((Number)raw).floatValue();break;case "set":if(!(raw instanceof JSONArray)||((JSONArray)raw).length()>MAX_ITEMS)throw new IOException();Set<String> set=new HashSet<>();for(int n=0;n<((JSONArray)raw).length();n++){Object s=((JSONArray)raw).get(n);if(!(s instanceof String))throw new IOException();set.add((String)s);}value=set;break;case "string":if(!(raw instanceof String)||((String)raw).length()>16*1024*1024)throw new IOException();value=raw;break;default:throw new IOException("Unknown setting type");}
            String required=expectedType(key);if(required!=null&&!required.equals(type))throw new IOException("Invalid type for "+key);out.put(key,value);
        }MusicDataContract.validate(out);return out;}
    private static String expectedType(String key){if(Arrays.asList("catalogue","queue","collections","storage_tree_uri","mode","credit_roles_order").contains(key))return "string";if(Arrays.asList("metadata_manually_edited","credit_roles_visible").contains(key))return "set";if(Arrays.asList("cover_alpha","speed").contains(key))return "float";if(Arrays.asList("cover_show","bpm_keep_decimal","spectrum_log","pitch_follow_speed","analysis_show","sleep_timer_finish_track").contains(key))return "boolean";if(Arrays.asList("max_volume_percent","volume_percent","spectrum_peaks","spectrum_history_ms","analysis_display_mode","lyrics_font_sp","sleep_timer_minutes").contains(key))return "int";return null;}
    private static boolean write(SharedPreferences prefs,Map<String,?> values){SharedPreferences.Editor e=prefs.edit().clear();for(Map.Entry<String,?> item:values.entrySet()){Object v=item.getValue();String key=item.getKey();if(v instanceof String)e.putString(key,(String)v);else if(v instanceof Integer)e.putInt(key,(Integer)v);else if(v instanceof Long)e.putLong(key,(Long)v);else if(v instanceof Boolean)e.putBoolean(key,(Boolean)v);else if(v instanceof Float)e.putFloat(key,(Float)v);else if(v instanceof Set){Set<String> s=new HashSet<>();for(Object x:(Set<?>)v)s.add((String)x);e.putStringSet(key,s);}}return e.commit();}
    private static final class FileRef{final Uri uri;final String path;FileRef(Uri u,String p){uri=u;path=p;}}
    private static Map<String,FileRef> scan(Context c,String raw)throws Exception{Uri tree=Uri.parse(raw);String root=DocumentsContract.getTreeDocumentId(tree);ArrayDeque<String[]> pending=new ArrayDeque<>();pending.add(new String[]{root,""});Set<String> seen=new HashSet<>();Map<String,FileRef> files=new LinkedHashMap<>();int count=0;while(!pending.isEmpty()){String[] dir=pending.removeFirst();if(!seen.add(dir[0]))continue;try(Cursor cursor=c.getContentResolver().query(DocumentsContract.buildChildDocumentsUriUsingTree(tree,dir[0]),new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_MIME_TYPE,DocumentsContract.Document.COLUMN_DISPLAY_NAME},null,null,null)){if(cursor==null)throw new IOException("Folder cannot be read");while(cursor.moveToNext()){if(++count>100000)throw new IOException("Too many files");String id=cursor.getString(0),name=cursor.getString(2);String path=dir[1]+name;if(DocumentsContract.Document.MIME_TYPE_DIR.equals(cursor.getString(1)))pending.add(new String[]{id,path+"/"});else{Uri uri=DocumentsContract.buildDocumentUriUsingTree(tree,id);files.put(identity(uri.toString()),new FileRef(uri,path));}}}}return files;}
    private static String identity(String raw){try{Uri u=Uri.parse(raw);return "document:"+u.getAuthority()+":"+DocumentsContract.getDocumentId(u);}catch(Exception e){return raw;}}
    private static final class Fingerprint{final long bytes;final String hash;Fingerprint(long bytes,String hash){this.bytes=bytes;this.hash=hash;}}
    private static Fingerprint fingerprint(Context c,Uri uri)throws Exception{MessageDigest digest=MessageDigest.getInstance("SHA-256");long total=0;try(InputStream in=c.getContentResolver().openInputStream(uri)){if(in==null)throw new IOException("Audio file cannot be read");byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();digest.update(b,0,n);total+=n;}}return new Fingerprint(total,hex(digest.digest()));}
    private static byte[] read(InputStream in,int maximum)throws IOException{try(InputStream source=in;ByteArrayOutputStream out=new ByteArrayOutputStream()){copy(source,out,maximum);return out.toByteArray();}}
    private static void copy(InputStream in,OutputStream out,long maximum)throws IOException{if(in==null)throw new IOException("File cannot be read");byte[] b=new byte[32768];int n;long total=0;while((n=in.read(b))!=-1){if((total+=n)>maximum)throw new IOException("Transfer limit exceeded");out.write(b,0,n);}}
    private static String sha(byte[] b)throws Exception{return hex(MessageDigest.getInstance("SHA-256").digest(b));}
    private static String hex(byte[] b){StringBuilder s=new StringBuilder();for(byte v:b)s.append(String.format(Locale.ROOT,"%02x",v&255));return s.toString();}
    private static void remove(File file){File[] children=file.listFiles();if(children!=null)for(File child:children)remove(child);file.delete();}
}
