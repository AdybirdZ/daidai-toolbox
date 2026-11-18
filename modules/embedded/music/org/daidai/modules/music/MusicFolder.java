package org.daidai.modules.music;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import java.io.IOException;
import java.util.*;

/** Reads only the granted SAF tree, including subfolders. Call from a worker thread. */
public final class MusicFolder {
    private MusicFolder() {}
    /** Per-screen cache: unchanged files missing optional tags are not reparsed on every visit. */
    static final class ScanCache {final Map<String,String> stamps=new HashMap<>();}
    /** Provider identities are used, never display titles or coordinate-like path guesses. */
    public static boolean contains(Context context,String treeUri,String songUri){
        if(treeUri==null||treeUri.isEmpty()||songUri==null||songUri.isEmpty())return false;
        try{
            Uri tree=Uri.parse(treeUri),song=Uri.parse(songUri);if(!Objects.equals(tree.getAuthority(),song.getAuthority()))return false;String root=DocumentsContract.getTreeDocumentId(tree),wanted=DocumentsContract.getDocumentId(song);if(root.equals(wanted))return false;
            Uri parent=DocumentsContract.buildDocumentUriUsingTree(tree,root),document=DocumentsContract.buildDocumentUriUsingTree(tree,wanted);try{if(DocumentsContract.isChildDocument(context.getContentResolver(),parent,document))return true;}catch(Exception ignored){}
            ArrayDeque<String> folders=new ArrayDeque<>();HashSet<String> visited=new HashSet<>();folders.add(root);while(!folders.isEmpty()){
                if(Thread.currentThread().isInterrupted())return false;String folder=folders.removeFirst();if(!visited.add(folder))continue;Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,folder);try(Cursor rows=context.getContentResolver().query(children,new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_MIME_TYPE},null,null,null)){if(rows==null)return false;while(rows.moveToNext()){String id=rows.getString(0);if(wanted.equals(id))return !DocumentsContract.Document.MIME_TYPE_DIR.equals(rows.getString(1));if(DocumentsContract.Document.MIME_TYPE_DIR.equals(rows.getString(1)))folders.add(id);}}
            }
        }catch(Exception ignored){}return false;
    }
    /** Runs on a worker after explicit user confirmation. Failure leaves catalogue references intact. */
    public static void delete(Context context,String treeUri,String songUri)throws IOException{
        if(!contains(context,treeUri,songUri))throw new IOException("Song is not in the selected music folder");try{Uri tree=Uri.parse(treeUri),song=Uri.parse(songUri);Uri document=DocumentsContract.buildDocumentUriUsingTree(tree,DocumentsContract.getDocumentId(song));if(!DocumentsContract.deleteDocument(context.getContentResolver(),document))throw new IOException("The file provider did not delete the song");MusicLibrary.removeEverywhere(context,songUri);}catch(IOException e){throw e;}catch(Exception e){throw new IOException(e);}
    }
    public static ArrayList<MusicLibrary.Track> load(Context context,String treeUri){try{return loadChecked(context,treeUri);}catch(Exception ignored){return new ArrayList<>();}}
    public static ArrayList<MusicLibrary.Track> loadChecked(Context context,String treeUri)throws IOException,InterruptedException{
        return loadChecked(context,treeUri,null,new ScanCache());
    }
    static ArrayList<MusicLibrary.Track> loadChecked(Context context,String treeUri,MusicImportProgress progress,ScanCache cache)throws IOException,InterruptedException{
        ArrayList<MusicLibrary.Track> result=new ArrayList<>();if(treeUri==null||treeUri.trim().isEmpty())return result;
        try{
            Uri tree=Uri.parse(treeUri);String root=DocumentsContract.getTreeDocumentId(tree);ArrayDeque<String> pending=new ArrayDeque<>();pending.add(root);
            HashMap<String,String> folderNames=new HashMap<>();folderNames.put(root,MusicLibrary.title(context,DocumentsContract.buildDocumentUriUsingTree(tree,root)));
            HashSet<String> seen=new HashSet<>(),files=new HashSet<>();HashMap<String,MusicLibrary.Track> known=new HashMap<>();for(MusicLibrary.Track t:MusicLibrary.catalogue(context))known.put(MusicLibrary.identity(t.uri),t);
            while(!pending.isEmpty()){
                if(progress!=null)progress.check();if(Thread.currentThread().isInterrupted())throw new InterruptedException();String parent=pending.removeFirst();if(!seen.add(parent))continue;
                String folderName=folderNames.get(parent);if(progress!=null){progress.file(folderName,result.size(),0);progress.phase(MusicImportReport.Phase.SCAN);}
                Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,parent);String[] columns={DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_MIME_TYPE,DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_SIZE,DocumentsContract.Document.COLUMN_LAST_MODIFIED};
                try(Cursor cursor=context.getContentResolver().query(children,columns,null,null,null)){
                    if(cursor==null)throw new IOException();int id=cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID),mime=cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE),name=cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME);
                    while(cursor.moveToNext()){
                        if(progress!=null)progress.check();if(Thread.currentThread().isInterrupted())throw new InterruptedException();String doc=id<0?"":cursor.getString(id),type=mime<0?"":cursor.getString(mime),file=name<0?"":cursor.getString(name);if(doc==null||doc.isEmpty())continue;
                        if(DocumentsContract.Document.MIME_TYPE_DIR.equals(type)){pending.add(doc);folderNames.put(doc,file);continue;}
                        Uri uri=DocumentsContract.buildDocumentUriUsingTree(tree,doc);String key=MusicLibrary.identity(uri.toString());if(!files.add(key))continue;
                        if(!MusicAudioFiles.accepts(file,type)){if(progress!=null)progress.skipped(uri.toString(),file);continue;}
                        if(progress!=null)progress.file(file==null?"":file,result.size()+1,0);
                        try{
                            int size=cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE),modified=cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED);
                            String stamp=(size<0||cursor.isNull(size)?"?":cursor.getString(size))+":"+(modified<0||cursor.isNull(modified)?"?":cursor.getString(modified));
                            MusicLibrary.Track t=known.get(key);boolean cached=stamp.equals(cache.stamps.get(key));
                            if(t==null||!cached&&(MusicLibrary.needsMetadataRefresh(context,t)||cache.stamps.containsKey(key)))t=MusicLibrary.readMetadataChecked(context,uri,progress);
                            if(progress!=null)progress.check();result.add(t);cache.stamps.put(key,stamp);if(progress!=null)progress.success(uri.toString(),uri.toString(),file);
                        }catch(java.io.InterruptedIOException e){throw e;}catch(Exception e){if(progress==null)throw e;progress.failed(uri.toString(),uri.toString(),file,e);}
                    }
                }catch(java.io.InterruptedIOException e){throw e;}catch(InterruptedException e){throw e;}catch(Exception e){if(progress==null)throw e;progress.phase(MusicImportReport.Phase.SCAN);progress.failed(treeUri,treeUri,folderName,e);}
            }
            java.text.Collator collator=java.text.Collator.getInstance(Locale.getDefault());result.sort((x,y)->collator.compare(x.title,y.title));return result;
        }catch(InterruptedException e){throw e;}catch(IOException e){throw e;}catch(Exception e){throw new IOException(e);}
    }
    /** Retry exact failed audio files; directory failures require a fresh tree scan. */
    static ArrayList<MusicLibrary.Track> retryChecked(Context context,String treeUri,List<MusicImportReport.Entry> failures,MusicImportProgress progress,ScanCache cache)throws IOException,InterruptedException{
        for(MusicImportReport.Entry entry:failures)if(entry.phase==MusicImportReport.Phase.SCAN)return loadChecked(context,treeUri,progress,cache);
        ArrayList<MusicLibrary.Track> rows=new ArrayList<>();int index=0;Uri tree=Uri.parse(treeUri);
        for(MusicImportReport.Entry entry:failures){
            progress.file(entry.name,++index,failures.size());
            try{Uri source=Uri.parse(entry.retryUri);if(!Objects.equals(tree.getAuthority(),source.getAuthority())||!contains(context,treeUri,entry.retryUri))throw new SecurityException();Uri uri=DocumentsContract.buildDocumentUriUsingTree(tree,DocumentsContract.getDocumentId(source));MusicLibrary.Track song=MusicLibrary.readMetadataChecked(context,uri,progress);progress.check();rows.add(song);progress.success(entry.source,uri.toString(),entry.name);}
            catch(java.io.InterruptedIOException e){throw e;}catch(Exception e){progress.failed(entry.source,entry.retryUri,entry.name,e);}
        }
        return rows;
    }
}
