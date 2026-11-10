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
        ArrayList<MusicLibrary.Track> result=new ArrayList<>();if(treeUri==null||treeUri.trim().isEmpty())return result;
        try{
            Uri tree=Uri.parse(treeUri);ArrayDeque<String> pending=new ArrayDeque<>();pending.add(DocumentsContract.getTreeDocumentId(tree));HashSet<String> seen=new HashSet<>();HashMap<String,MusicLibrary.Track> known=new HashMap<>();for(MusicLibrary.Track t:MusicLibrary.catalogue(context))known.put(t.uri,t);
            while(!pending.isEmpty()){
                if(Thread.currentThread().isInterrupted())throw new InterruptedException();String parent=pending.removeFirst();if(!seen.add(parent))continue;
                Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,parent);String[] columns={DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_MIME_TYPE,DocumentsContract.Document.COLUMN_DISPLAY_NAME};
                try(Cursor cursor=context.getContentResolver().query(children,columns,null,null,null)){
                    if(cursor==null)throw new IOException("Folder provider returned no result");int id=cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID),mime=cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE),name=cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME);
                    while(cursor.moveToNext()){
                        if(Thread.currentThread().isInterrupted())throw new InterruptedException();String doc=id<0?"":cursor.getString(id),type=mime<0?"":cursor.getString(mime),file=name<0?"":cursor.getString(name);if(doc==null||doc.isEmpty())continue;
                        if(DocumentsContract.Document.MIME_TYPE_DIR.equals(type)){pending.add(doc);continue;}
                        String lower=file==null?"":file.toLowerCase(Locale.ROOT);if((type==null||!type.startsWith("audio/"))&&!lower.matches(".*\\.(mp3|m4a|aac|wav|flac|ogg|opus|amr|aiff|aif|wma|mid|midi)$"))continue;
                        Uri uri=DocumentsContract.buildDocumentUriUsingTree(tree,doc);MusicLibrary.Track t=known.get(uri.toString());result.add(t==null||MusicLibrary.needsMetadataRefresh(context,t)?MusicLibrary.readMetadata(context,uri):t);
                    }
                }
            }
            result.sort((x,y)->java.text.Collator.getInstance(Locale.getDefault()).compare(x.title,y.title));return result;
        }catch(InterruptedException e){throw e;}catch(Exception e){throw new IOException(e);}
    }
}
