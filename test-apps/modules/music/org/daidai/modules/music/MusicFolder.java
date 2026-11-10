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
