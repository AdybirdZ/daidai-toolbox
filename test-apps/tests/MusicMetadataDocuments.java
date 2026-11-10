package org.daidai.prototype.qa;

import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract.Document;
import android.provider.DocumentsContract.Root;
import android.provider.DocumentsProvider;
import java.io.*;
import java.util.*;

/** Test-only SAF provider: simulates a phone folder populated by a PC transfer. */
public final class MusicMetadataDocuments extends DocumentsProvider {
    public static final String AUTHORITY="org.daidai.prototype.qa.music.documents";
    private File base;
    public boolean onCreate(){base=new File(getContext().getFilesDir(),"metadata-documents");base.mkdirs();return true;}
    private File file(String id)throws FileNotFoundException{
        try{File f="root".equals(id)?base:new File(base,id);String root=base.getCanonicalPath();String value=f.getCanonicalPath();if(!value.equals(root)&&!value.startsWith(root+File.separator))throw new IOException();return f;}catch(IOException e){throw new FileNotFoundException("Invalid test document");}
    }
    private String id(File f)throws FileNotFoundException{if(f.equals(base))return "root";try{return f.getCanonicalPath().substring(base.getCanonicalPath().length()+1).replace(File.separatorChar,'/');}catch(IOException e){throw new FileNotFoundException();}}
    private String mime(File f){String n=f.getName().toLowerCase(Locale.ROOT);return f.isDirectory()?Document.MIME_TYPE_DIR:n.endsWith(".flac")?"audio/flac":n.endsWith(".m4a")?"audio/mp4":n.endsWith(".mp3")?"audio/mpeg":"application/octet-stream";}
    private void row(MatrixCursor out,File f)throws FileNotFoundException{if(!f.exists())throw new FileNotFoundException();MatrixCursor.RowBuilder b=out.newRow();for(String c:out.getColumnNames()){Object v=null;if(c.equals(Document.COLUMN_DOCUMENT_ID))v=id(f);else if(c.equals(Document.COLUMN_DISPLAY_NAME))v=f.getName();else if(c.equals(Document.COLUMN_MIME_TYPE))v=mime(f);else if(c.equals(Document.COLUMN_SIZE))v=f.length();else if(c.equals(Document.COLUMN_LAST_MODIFIED))v=f.lastModified();else if(c.equals(Document.COLUMN_FLAGS))v=f.isDirectory()?Document.FLAG_DIR_SUPPORTS_CREATE|Document.FLAG_SUPPORTS_DELETE:Document.FLAG_SUPPORTS_WRITE|Document.FLAG_SUPPORTS_DELETE;b.add(v);}}
    public Cursor queryRoots(String[] projection){String[] cols=projection==null?new String[]{Root.COLUMN_ROOT_ID,Root.COLUMN_DOCUMENT_ID,Root.COLUMN_TITLE,Root.COLUMN_FLAGS}:projection;MatrixCursor out=new MatrixCursor(cols);MatrixCursor.RowBuilder b=out.newRow();for(String c:cols)b.add(c.equals(Root.COLUMN_ROOT_ID)||c.equals(Root.COLUMN_DOCUMENT_ID)?"root":c.equals(Root.COLUMN_TITLE)?"Generated music fixtures":c.equals(Root.COLUMN_FLAGS)?Root.FLAG_SUPPORTS_CREATE:null);return out;}
    public Cursor queryDocument(String documentId,String[] projection)throws FileNotFoundException{MatrixCursor out=new MatrixCursor(columns(projection));row(out,file(documentId));return out;}
    public Cursor queryChildDocuments(String parentId,String[] projection,String sortOrder)throws FileNotFoundException{MatrixCursor out=new MatrixCursor(columns(projection));File[] children=file(parentId).listFiles();if(children!=null){Arrays.sort(children,(x,y)->x.getName().compareTo(y.getName()));for(File child:children)row(out,child);}return out;}
    private String[] columns(String[] projection){return projection==null?new String[]{Document.COLUMN_DOCUMENT_ID,Document.COLUMN_DISPLAY_NAME,Document.COLUMN_MIME_TYPE,Document.COLUMN_SIZE,Document.COLUMN_FLAGS,Document.COLUMN_LAST_MODIFIED}:projection;}
    public ParcelFileDescriptor openDocument(String documentId,String mode,CancellationSignal signal)throws FileNotFoundException{return ParcelFileDescriptor.open(file(documentId),ParcelFileDescriptor.parseMode(mode));}
    public String createDocument(String parentId,String mime,String displayName)throws FileNotFoundException{if(displayName.contains("/")||displayName.contains("\\")||displayName.equals(".."))throw new FileNotFoundException();File f=new File(file(parentId),displayName);try{if(Document.MIME_TYPE_DIR.equals(mime)){if(!f.exists()&&!f.mkdir())throw new IOException();}else if(!f.exists()&&!f.createNewFile())throw new IOException();return id(f);}catch(IOException e){throw new FileNotFoundException(e.toString());}}
    public boolean isChildDocument(String parentId,String documentId){try{String parent=file(parentId).getCanonicalPath(),child=file(documentId).getCanonicalPath();return child.startsWith(parent+File.separator);}catch(IOException e){return false;}}
    public void deleteDocument(String documentId)throws FileNotFoundException{if("root".equals(documentId))throw new FileNotFoundException("Cannot delete test root");File target=file(documentId);if(!target.isFile()&&!target.isDirectory()||!target.delete())throw new FileNotFoundException("Unable to delete test document");}
}
