"""Run production SAF folder/copy logic against an in-memory document provider, without building an APK."""
from pathlib import Path
import shutil
import subprocess

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / 'build/tests/music-import'
SOURCES = {
'android/os/SystemClock.java': r'''package android.os;public final class SystemClock {private static long now;public static long uptimeMillis(){return now++;}}''',
'android/widget/LinearLayout.java': r'''package android.widget;public final class LinearLayout {public boolean attached=true;public final java.util.ArrayDeque<Runnable> tasks=new java.util.ArrayDeque<>();public boolean isAttachedToWindow(){return attached;}public void postOnAnimation(Runnable action){tasks.add(action);}}''',
'android/net/Uri.java': r'''package android.net;
public final class Uri {private final String raw;private Uri(String s){raw=s;}public static Uri parse(String s){return new Uri(s);}public String toString(){return raw;}public String getAuthority(){return java.net.URI.create(raw).getAuthority();}public String getScheme(){return java.net.URI.create(raw).getScheme();}public String getPath(){return java.net.URI.create(raw).getPath();}}''',
'android/database/Cursor.java': r'''package android.database;
public interface Cursor extends AutoCloseable {boolean moveToNext();boolean moveToFirst();String getString(int i);int getColumnIndex(String s);boolean isNull(int i);void close();}''',
'android/content/SharedPreferences.java': r'''package android.content;
public final class SharedPreferences {public String tree="";public String getString(String key,String fallback){return tree;}}''',
'android/content/ContentResolver.java': r'''package android.content;
import android.net.Uri;import android.database.Cursor;import java.io.*;
public abstract class ContentResolver {public abstract Cursor query(Uri u,String[] columns,String s,String[] args,String order);public abstract String getType(Uri u);public abstract InputStream openInputStream(Uri u)throws IOException;public abstract OutputStream openOutputStream(Uri u,String mode)throws IOException;public abstract Uri create(Uri parent,String mime,String name)throws IOException;public abstract boolean delete(Uri u);}''',
'android/content/Context.java': r'''package android.content;
public final class Context {public final SharedPreferences prefs=new SharedPreferences();private final ContentResolver resolver;public Context(ContentResolver r){resolver=r;}public ContentResolver getContentResolver(){return resolver;}public SharedPreferences getSharedPreferences(String s,int mode){return prefs;}}''',
'android/provider/DocumentsContract.java': r'''package android.provider;
import android.net.Uri;import android.content.ContentResolver;import java.io.*;
public final class DocumentsContract {
 public static final class Document {public static final String COLUMN_DOCUMENT_ID="id",COLUMN_MIME_TYPE="mime",COLUMN_DISPLAY_NAME="name",COLUMN_SIZE="size",COLUMN_LAST_MODIFIED="modified",MIME_TYPE_DIR="dir";}
 public static String getDocumentId(Uri u){String s=u.getPath();int n=s.indexOf("/document/");if(n<0)throw new IllegalArgumentException();return s.substring(n+10);}
 public static String getTreeDocumentId(Uri u){String s=u.getPath();int n=s.indexOf("/tree/");if(n<0)throw new IllegalArgumentException();s=s.substring(n+6);int end=s.indexOf("/document/");return end<0?s:s.substring(0,end);}
 public static Uri buildDocumentUriUsingTree(Uri tree,String id){return Uri.parse("content://"+tree.getAuthority()+"/document/"+id);}
 public static Uri buildChildDocumentsUriUsingTree(Uri tree,String id){return Uri.parse("content://"+tree.getAuthority()+"/children/"+id);}
 public static boolean isChildDocument(ContentResolver r,Uri parent,Uri song){return getDocumentId(song).startsWith(getDocumentId(parent)+"/");}
 public static Uri createDocument(ContentResolver r,Uri p,String m,String n)throws IOException{return r.create(p,m,n);}
 public static boolean deleteDocument(ContentResolver r,Uri u){return r.delete(u);}
}''',
'org/daidai/modules/shared/Texts.java': r'''package org.daidai.modules.shared;public final class Texts {public static String get(android.content.Context c,String key){return key;}}''',
'org/daidai/modules/music/MusicImportProgress.java': r'''package org.daidai.modules.music;
import java.io.*;import java.util.*;
final class MusicImportProgress {
 final List<String> names=new ArrayList<>();final MusicImportReport report=new MusicImportReport(MusicImportReport.Mode.FOLDER);MusicImportReport.Phase current=MusicImportReport.Phase.SCAN;boolean cancel;int checks,cancelAfter=Integer.MAX_VALUE;
 void check()throws InterruptedIOException{if(cancel||++checks>=cancelAfter||Thread.currentThread().isInterrupted())throw new InterruptedIOException();}
 void file(String name,int done,int total)throws InterruptedIOException{check();names.add(name);}
 void phase(MusicImportReport.Phase phase)throws InterruptedIOException{check();current=phase;}
 void success(String source,String stored,String name){report.add(new MusicImportReport.Entry(name,source,stored,current,MusicImportReport.Result.SUCCESS,""));}
 void skipped(String source,String name){report.add(new MusicImportReport.Entry(name,source,source,current,MusicImportReport.Result.SKIPPED,"music_import_reason_non_audio"));}
 void failed(String source,String stored,String name,Throwable error){report.add(new MusicImportReport.Entry(name,source,stored,current,MusicImportReport.Result.FAILED,MusicImportReport.reason(error)));}
}''',
# The traversal/copy unit has a catalogue boundary; Android metadata decoding is not emulated.
'org/daidai/modules/music/MusicLibrary.java': r'''package org.daidai.modules.music;
import android.content.Context;import android.net.Uri;import android.provider.DocumentsContract;import java.util.*;
public final class MusicLibrary {
 static int metadataReads;static final Set<String> denied=new HashSet<>();static final ArrayList<Track> saved=new ArrayList<>();
 public static final class Track {public final String uri,title;Track(String u,String n){uri=u;title=n;}}
 static String identity(String raw){try{return Uri.parse(raw).getAuthority()+":"+DocumentsContract.getDocumentId(Uri.parse(raw));}catch(Exception e){return raw;}}
 public static ArrayList<Track> catalogue(Context c){return new ArrayList<>(saved);}public static boolean needsMetadataRefresh(Context c,Track t){return true;}
 public static Track readMetadata(Context c,Uri u){metadataReads++;return new Track(u.toString(),title(c,u));}
 public static Track readMetadataChecked(Context c,Uri u,MusicImportProgress progress)throws java.io.IOException{if(progress!=null)progress.phase(MusicImportReport.Phase.METADATA);if(denied.contains(DocumentsContract.getDocumentId(u)))throw new java.io.FileNotFoundException();return readMetadata(c,u);}
 public static String title(Context c,Uri u){try(android.database.Cursor row=c.getContentResolver().query(u,new String[]{"name"},null,null,null)){return row.moveToFirst()?row.getString(0):"audio";}}
 public static void removeEverywhere(Context c,String uri){}
}''',
'org/daidai/modules/music/BulkImportChecks.java': r'''package org.daidai.modules.music;
import android.content.*;import android.database.Cursor;import android.net.Uri;import android.provider.DocumentsContract;import java.io.*;import java.util.*;
public final class BulkImportChecks {
 static int checks;static void ok(boolean on,String message){if(!on)throw new AssertionError(message);checks++;}
 static final class Doc {String id,parent,name,mime;byte[] data=new byte[]{1,2,3};long modified=1;Doc(String id,String parent,String name,String mime){this.id=id;this.parent=parent;this.name=name;this.mime=mime;}}
 static final class Provider extends ContentResolver {
  final Map<String,Doc> docs=new LinkedHashMap<>();int directories,reads,writes,created;boolean collide;
  Doc add(String id,String parent,String name,String mime){Doc d=new Doc(id,parent,name,mime);docs.put(id,d);return d;}
  public Cursor query(Uri uri,String[] columns,String s,String[] a,String order){List<Doc> rows=new ArrayList<>();String path=uri.getPath();if(path.startsWith("/children/")){directories++;String parent=path.substring(10);for(Doc d:docs.values())if(parent.equals(d.parent))rows.add(d);}else{Doc d=docs.get(DocumentsContract.getDocumentId(uri));if(d!=null)rows.add(d);}return new Cursor(){int at=-1;public boolean moveToNext(){return ++at<rows.size();}public boolean moveToFirst(){at=0;return !rows.isEmpty();}public int getColumnIndex(String key){return Arrays.asList(columns).indexOf(key);}public boolean isNull(int i){return getString(i)==null;}public String getString(int i){Doc d=rows.get(at);switch(columns[i]){case "id":return d.id;case "name":return d.name;case "mime":return d.mime;case "size":return ""+d.data.length;case "modified":return ""+d.modified;default:return null;}}public void close(){}};}
  public String getType(Uri u){return docs.get(DocumentsContract.getDocumentId(u)).mime;}
  public InputStream openInputStream(Uri u){reads++;return new ByteArrayInputStream(docs.get(DocumentsContract.getDocumentId(u)).data);}
  public OutputStream openOutputStream(Uri u,String mode){writes++;Doc d=docs.get(DocumentsContract.getDocumentId(u));return new ByteArrayOutputStream(){public void close(){d.data=toByteArray();}};}
  public Uri create(Uri p,String mime,String name){if(collide)return Uri.parse("content://files/document/root/song0");String id="root/import"+(created++);add(id,"root",name,mime);return Uri.parse("content://files/document/"+id);}
  public boolean delete(Uri u){return docs.remove(DocumentsContract.getDocumentId(u))!=null;}
 }
 public static void main(String[] args)throws Exception {
  for(String extension:new String[]{"MP3","m4a","flac","ogg","wav","opus","aac","midi"})ok(MusicAudioFiles.accepts("song."+extension,"application/octet-stream"),"audio extension: "+extension);
  for(String extension:new String[]{"txt","lrc","png","jpg","json","pdf","zip","ddapp"})ok(!MusicAudioFiles.accepts("file."+extension,"audio/mpeg"),"bad MIME sidecar: "+extension);
  ok(!MusicAudioFiles.accepts("unknown.bin","application/octet-stream"),"unknown binary skipped");ok(!MusicAudioFiles.accepts("image.mp3","image/png"),"explicit image MIME skipped");ok(MusicAudioFiles.accepts("track","audio/flac; charset=binary"),"audio MIME with no extension");
  Provider p=new Provider();Context c=new Context(p);String tree="content://files/tree/root";c.prefs.tree=tree;
  p.add("root","","Music","dir");p.add("root/sub","root","sub","dir");p.add("root/cycle","root/sub","cycle","dir");
  for(int i=0;i<600;i++)p.add("root/song"+i,i%2==0?"root":"root/sub","song"+i+".mp3","audio/mpeg");
  p.add("root/art","root","art.jpg","audio/mpeg");p.add("root/lyrics","root","song.lrc","text/plain");p.add("root/readme","root/sub","notes.txt","text/plain");
  MusicFolder.ScanCache cache=new MusicFolder.ScanCache();MusicImportProgress progress=new MusicImportProgress();
  ArrayList<MusicLibrary.Track> songs=MusicFolder.loadChecked(c,tree,progress,cache);ok(songs.size()==600,"mixed folder has only 600 audio files");ok(progress.names.size()==603&&progress.names.contains("song599.mp3"),"filenames reported for every song");ok(MusicLibrary.metadataReads==600,"read each new song once");
  ok(progress.report.count(MusicImportReport.Result.SUCCESS)==600&&progress.report.count(MusicImportReport.Result.SKIPPED)==3,"report includes every accepted and skipped file");
  ok(progress.report.failures().isEmpty(),"successful scan has no retry candidates");
  MusicLibrary.saved.addAll(songs);MusicFolder.loadChecked(c,tree,new MusicImportProgress(),cache);ok(MusicLibrary.metadataReads==600,"unchanged missing optional tags are cached");
  p.docs.get("root/song0").modified++;MusicFolder.loadChecked(c,tree,new MusicImportProgress(),cache);ok(MusicLibrary.metadataReads==601,"changed file refreshed alone");
  MusicImportProgress cancel=new MusicImportProgress();cancel.cancel=true;try{MusicFolder.loadChecked(c,tree,cancel,cache);throw new AssertionError("scan cancellation");}catch(InterruptedIOException expected){checks++;}
  MusicLibrary.denied.add("root/song9");p.docs.get("root/song9").modified++;MusicImportProgress failed=new MusicImportProgress();
  ArrayList<MusicLibrary.Track> partialRows=MusicFolder.loadChecked(c,tree,failed,cache);
  ok(partialRows.size()==599&&failed.report.failures().size()==1,"single inaccessible audio does not abort remaining songs");
  MusicImportReport.Entry failedFile=failed.report.failures().get(0);ok(failedFile.name.equals("song9.mp3")&&failedFile.reason.equals("music_import_reason_missing"),"failed filename and normalized reason are retained");
  MusicLibrary.denied.clear();int metadataBefore=MusicLibrary.metadataReads;MusicImportProgress retried=new MusicImportProgress();
  ArrayList<MusicLibrary.Track> retriedRows=MusicFolder.retryChecked(c,tree,failed.report.failures(),retried,cache);
  ok(retriedRows.size()==1&&MusicLibrary.metadataReads==metadataBefore+1,"retry reparses only the failed song");
  ok(retried.report.count(MusicImportReport.Result.SUCCESS)==1&&retried.report.failures().isEmpty(),"retry success replaces failure state");
  ok(MusicImportReport.reason(new IOException(new SecurityException("private provider path"))).equals("music_import_reason_permission"),"permission diagnostics do not leak raw exception text");
  ok(MusicImportReport.reason(new IOException()).equals("music_import_reason_io"),"I/O failures have a distinct cause");
  MusicImportReport duplicateFailures=new MusicImportReport(MusicImportReport.Mode.COPY);
  duplicateFailures.add(new MusicImportReport.Entry("original","content://source/document/a","content://files/document/copy",MusicImportReport.Phase.METADATA,MusicImportReport.Result.FAILED,"music_import_reason_read"));
  duplicateFailures.add(new MusicImportReport.Entry("same","content://source/document/a","content://files/document/copy",MusicImportReport.Phase.ARTWORK,MusicImportReport.Result.FAILED,"music_import_reason_read"));
  ok(duplicateFailures.failures().size()==1&&duplicateFailures.failures().get(0).retryUri.endsWith("/copy"),"retry uses a completed target copy once");
  int before=p.directories;MusicImporter.Batch batch=new MusicImporter.Batch(c,new MusicImportProgress());int indexed=p.directories;
  ok(indexed-before==3,"destination directories enumerated once per batch");
  for(int i=0;i<120;i++){p.add("incoming"+i,"incoming","same.mp3","audio/mpeg");batch.copy(Uri.parse("content://files/document/incoming"+i));}
  ok(p.directories==indexed,"120 copies do not rescan destination");ok(p.created==120&&p.writes==120,"all selected songs copied");
  Set<String> names=new HashSet<>();for(Doc d:p.docs.values())if(d.id.startsWith("root/import"))names.add(d.name);ok(names.size()==120&&names.contains("same (119).mp3"),"same names never overwrite");
  int writes=p.writes;Uri alias=Uri.parse("content://files/tree/root/document/root/song0");batch.copy(alias);ok(p.writes==writes,"already in selected folder uses existing audio");
  MusicImportProgress stopped=new MusicImportProgress();MusicImporter.Batch partial=new MusicImporter.Batch(c,stopped);int docs=p.docs.size();stopped.cancelAfter=stopped.checks+4;
  try{partial.copy(Uri.parse("content://files/document/incoming0"));throw new AssertionError("cancel copy");}catch(InterruptedIOException expected){checks++;}ok(p.docs.size()==docs,"cancelled copy leaves no partial file");
  MusicImporter.Batch protectedBatch=new MusicImporter.Batch(c,null);p.collide=true;writes=p.writes;try{protectedBatch.copy(Uri.parse("content://files/document/incoming0"));throw new AssertionError("provider collision");}catch(IOException expected){checks++;}ok(p.writes==writes&&p.docs.containsKey("root/song0"),"provider collision never opens or deletes existing audio");
  android.widget.LinearLayout rows=new android.widget.LinearLayout();int[] shown={0};boolean[] valid={true};MusicRows.append(rows,100,()->valid[0],i->{ok(i==shown[0],"incremental row order");shown[0]++;});
  rows.tasks.removeFirst().run();ok(shown[0]>0&&shown[0]<=12&&shown[0]<100,"large list yields before all rows are created");
  while(!rows.tasks.isEmpty())rows.tasks.removeFirst().run();ok(shown[0]==100,"all rows eventually displayed");
  MusicRows.append(rows,50,()->valid[0],i->shown[0]++);valid[0]=false;rows.tasks.removeFirst().run();ok(shown[0]==100,"obsolete render never adds rows");
  rows.attached=false;MusicRows.append(rows,50,()->true,i->shown[0]++);rows.tasks.removeFirst().run();ok(shown[0]==100,"detached screen stops row creation");
  System.out.println("Music bulk import: "+checks+" checks passed (600-song mixed folder; 120-file copy batch)");
 }
}''',
}

def run():
    OUT.mkdir(parents=True, exist_ok=True)
    generated = OUT / 'stubs'
    sources = []
    for relative, contents in SOURCES.items():
        path = generated / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(contents, encoding='utf8')
        sources.append(path)
    module = ROOT / 'modules/embedded/music/org/daidai/modules/music'
    sources += [module / name for name in ('MusicFolder.java', 'MusicImporter.java', 'MusicAudioFiles.java', 'MusicRows.java', 'MusicImportReport.java')]
    arguments = ['-encoding','UTF-8','--release','8','-d',str(OUT),*map(str,sources)]
    argfile = OUT / 'sources.args'
    argfile.write_text('\n'.join('"'+s.replace('\\','/')+'"' for s in arguments), encoding='utf8')
    subprocess.run([shutil.which('javac'),'@'+str(argfile)],check=True)
    subprocess.run([shutil.which('java'),'-cp',str(OUT),'org.daidai.modules.music.BulkImportChecks'],check=True,timeout=45)

if __name__ == '__main__':
    run()
