package org.daidai.modules.music;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import org.daidai.modules.shared.Texts;
import java.io.*;
import java.util.*;

/** Copy-only import, isolated from the launcher and UI. Never opens an existing destination for writing. */
public final class MusicImporter {
    private MusicImporter(){}
    public static Uri copyToFolder(Context context,Uri source)throws IOException {
        String tree=context.getSharedPreferences("music_module",0).getString("storage_tree_uri","");
        if(tree.isEmpty())throw new IOException(Texts.get(context,"music_folder_required"));
        if(MusicFolder.contains(context,tree,source.toString()))return DocumentsContract.buildDocumentUriUsingTree(Uri.parse(tree),DocumentsContract.getDocumentId(source));
        Uri treeUri=Uri.parse(tree),parent=DocumentsContract.buildDocumentUriUsingTree(treeUri,DocumentsContract.getTreeDocumentId(treeUri));
        Set<String> names=new HashSet<>(),identities=new HashSet<>();
        Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(treeUri,DocumentsContract.getTreeDocumentId(treeUri));
        try(Cursor rows=context.getContentResolver().query(children,new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME},null,null,null)){
            if(rows==null)throw new IOException("Directory unavailable");while(rows.moveToNext()){identities.add(rows.getString(0));String name=rows.getString(1);if(name!=null)names.add(name.toLowerCase(Locale.ROOT));}
        }catch(Exception failure){throw new IOException(Texts.get(context,"music_copy_failed"),failure);}
        String name="audio";try(Cursor row=context.getContentResolver().query(source,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(row!=null&&row.moveToFirst()&&row.getString(0)!=null)name=row.getString(0);}catch(Exception ignored){}
        name=name.replaceAll("[\\\\/:*?\"<>|]","_");if(name.isEmpty())name="audio";
        String candidate=name;int extension=name.lastIndexOf('.');String base=extension>0?name.substring(0,extension):name,suffix=extension>0?name.substring(extension):"";
        for(int index=1;names.contains(candidate.toLowerCase(Locale.ROOT));index++){if(index>100000)throw new IOException(Texts.get(context,"music_copy_failed"));candidate=base+" ("+index+")"+suffix;}
        String mime=context.getContentResolver().getType(source);if(mime==null||!mime.startsWith("audio/"))mime="audio/mpeg";
        Uri destination=null;boolean created=false;
        try {
            destination=DocumentsContract.createDocument(context.getContentResolver(),parent,mime,candidate);
            if(destination==null||identities.contains(DocumentsContract.getDocumentId(destination)))throw new IOException(Texts.get(context,"music_copy_failed"));
            created=true;
            try(InputStream input=context.getContentResolver().openInputStream(source);OutputStream output=context.getContentResolver().openOutputStream(destination,"wt")){
                if(input==null||output==null)throw new IOException();byte[] buffer=new byte[65536];int size;
                while((size=input.read(buffer))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();output.write(buffer,0,size);}
            }
            return destination;
        }catch(Exception failure){if(created)try{DocumentsContract.deleteDocument(context.getContentResolver(),destination);}catch(Exception ignored){}throw new IOException(Texts.get(context,"music_copy_failed"),failure);}
    }
}
