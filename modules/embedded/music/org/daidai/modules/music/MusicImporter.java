package org.daidai.modules.music;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import org.daidai.modules.shared.Texts;
import java.io.*;
import java.util.*;

/** Copy-only import. A batch indexes the destination once and never overwrites existing files. */
public final class MusicImporter {
    private MusicImporter(){}
    public static Uri copyToFolder(Context context,Uri source)throws IOException {return new Batch(context,null).copy(source);}
    static final class Batch {
        private final Context context;
        private final Uri tree,parent;
        private final MusicImportProgress progress;
        private final Set<String> names=new HashSet<>(),members=new HashSet<>();
        Batch(Context context,MusicImportProgress progress)throws IOException {
            this.context=context;this.progress=progress;
            String raw=context.getSharedPreferences("music_module",0).getString("storage_tree_uri","");
            if(raw.isEmpty())throw new IOException(Texts.get(context,"music_folder_required"));
            tree=Uri.parse(raw);String root=DocumentsContract.getTreeDocumentId(tree);
            parent=DocumentsContract.buildDocumentUriUsingTree(tree,root);
            ArrayDeque<String> pending=new ArrayDeque<>();pending.add(root);Set<String> visited=new HashSet<>();
            while(!pending.isEmpty()){
                check();String folder=pending.removeFirst();if(!visited.add(folder))continue;
                try(Cursor rows=context.getContentResolver().query(DocumentsContract.buildChildDocumentsUriUsingTree(tree,folder),new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_MIME_TYPE},null,null,null)){
                    if(rows==null)throw new IOException(Texts.get(context,"music_copy_failed"));
                    while(rows.moveToNext()){
                        check();String id=rows.getString(0),name=rows.getString(1);if(id==null)continue;
                        members.add(MusicLibrary.identity(DocumentsContract.buildDocumentUriUsingTree(tree,id).toString()));
                        if(root.equals(folder)&&name!=null)names.add(name.toLowerCase(Locale.ROOT));
                        if(DocumentsContract.Document.MIME_TYPE_DIR.equals(rows.getString(2)))pending.add(id);
                    }
                }
            }
        }
        void check()throws InterruptedIOException {if(progress!=null)progress.check();else if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();}
        Uri copy(Uri source)throws IOException {
            check();if(members.contains(MusicLibrary.identity(source.toString())))return DocumentsContract.buildDocumentUriUsingTree(tree,DocumentsContract.getDocumentId(source));
            String name=MusicLibrary.title(context,source);
            name=name.replaceAll("[\\\\/:*?\"<>|]","_");if(name.isEmpty())name="audio";
            String candidate=name;int extension=name.lastIndexOf('.');String base=extension>0?name.substring(0,extension):name,suffix=extension>0?name.substring(extension):"";
            for(int index=1;names.contains(candidate.toLowerCase(Locale.ROOT));index++){check();if(index>100000)throw new IOException(Texts.get(context,"music_copy_failed"));candidate=base+" ("+index+")"+suffix;}
            String mime=context.getContentResolver().getType(source);if(mime==null||!mime.startsWith("audio/"))mime="audio/mpeg";
            Uri destination=null;boolean created=false;
            try {
                check();destination=DocumentsContract.createDocument(context.getContentResolver(),parent,mime,candidate);
                if(destination==null||members.contains(MusicLibrary.identity(destination.toString())))throw new IOException(Texts.get(context,"music_copy_failed"));
                created=true;
                try(InputStream input=context.getContentResolver().openInputStream(source);OutputStream output=context.getContentResolver().openOutputStream(destination,"wt")){
                    if(input==null||output==null)throw new IOException();byte[] buffer=new byte[65536];int size;
                    while((size=input.read(buffer))!=-1){check();output.write(buffer,0,size);}
                }
                names.add(candidate.toLowerCase(Locale.ROOT));members.add(MusicLibrary.identity(destination.toString()));return destination;
            }catch(Exception failure){if(created)try{DocumentsContract.deleteDocument(context.getContentResolver(),destination);}catch(Exception ignored){}if(failure instanceof InterruptedIOException)throw (InterruptedIOException)failure;throw new IOException(Texts.get(context,"music_copy_failed"),failure);}
        }
    }
}
