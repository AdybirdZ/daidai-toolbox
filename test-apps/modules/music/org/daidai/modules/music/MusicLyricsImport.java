package org.daidai.modules.music;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import org.daidai.modules.shared.Texts;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.*;

/** Copies lyrics beside the songs without ever opening an existing document for writing. */
public final class MusicLyricsImport {
    private MusicLyricsImport(){}
    public static final class Result {
        public final Uri uri;
        private final boolean copied;
        private Result(Uri uri,boolean copied){this.uri=uri;this.copied=copied;}
        /** Roll back only a document created by this import, never a reused source file. */
        public void discard(Context context){if(copied)try{DocumentsContract.deleteDocument(context.getContentResolver(),uri);}catch(Exception ignored){}}
    }
    public static Result copyToFolder(Context context,Uri source)throws IOException{
        String tree=context.getSharedPreferences("music_module",0).getString("storage_tree_uri","");
        if(tree.isEmpty())throw new IOException(Texts.get(context,"music_folder_required"));
        byte[] sourceBytes=readSource(context,source);
        if(MusicFolder.contains(context,tree,source.toString()))return new Result(DocumentsContract.buildDocumentUriUsingTree(Uri.parse(tree),DocumentsContract.getDocumentId(source)),false);
        Uri treeUri=Uri.parse(tree),parent=DocumentsContract.buildDocumentUriUsingTree(treeUri,DocumentsContract.getTreeDocumentId(treeUri));
        HashSet<String> names=new HashSet<>(),identities=new HashSet<>();
        Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(treeUri,DocumentsContract.getTreeDocumentId(treeUri));
        try(Cursor rows=context.getContentResolver().query(children,new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME},null,null,null)){
            if(rows==null)throw new IOException();while(rows.moveToNext()){identities.add(rows.getString(0));String name=rows.getString(1);if(name!=null)names.add(name.toLowerCase(Locale.ROOT));}
        }catch(Exception error){throw new IOException(Texts.get(context,"music070_lyrics_copy_failed"),error);}
        String name="lyrics.lrc";try(Cursor row=context.getContentResolver().query(source,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(row!=null&&row.moveToFirst()&&row.getString(0)!=null)name=row.getString(0);}catch(Exception ignored){}
        name=name.replaceAll("[\\\\/:*?\"<>|]","_");if(name.trim().isEmpty())name="lyrics.lrc";if(!name.toLowerCase(Locale.ROOT).endsWith(".lrc"))name+=".lrc";
        int extension=name.lastIndexOf('.');String base=name.substring(0,extension),suffix=name.substring(extension),candidate=name;
        for(int index=1;names.contains(candidate.toLowerCase(Locale.ROOT));index++){if(index>100000)throw new IOException(Texts.get(context,"music070_lyrics_copy_failed"));candidate=base+" ("+index+")"+suffix;}
        Uri destination=null;boolean created=false;
        try{
            if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();
            destination=DocumentsContract.createDocument(context.getContentResolver(),parent,"text/plain",candidate);
            if(destination==null||identities.contains(DocumentsContract.getDocumentId(destination)))throw new IOException();created=true;
            try(OutputStream output=context.getContentResolver().openOutputStream(destination,"wt")){
                if(output==null)throw new IOException();for(int at=0;at<sourceBytes.length;at+=65536){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();output.write(sourceBytes,at,Math.min(65536,sourceBytes.length-at));}if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();
            }
            return new Result(destination,true);
        }catch(Exception error){if(created)try{DocumentsContract.deleteDocument(context.getContentResolver(),destination);}catch(Exception ignored){}throw new IOException(Texts.get(context,"music070_lyrics_copy_failed"),error);}
    }
    /** Query-reported sizes are advisory: enforce the limit on bytes actually read, including reuse. */
    private static byte[] readSource(Context context,Uri source)throws IOException{
        try(InputStream input=context.getContentResolver().openInputStream(source);ByteArrayOutputStream bytes=new ByteArrayOutputStream()){
            if(input==null)throw new IOException();byte[] buffer=new byte[8192];int count;while((count=input.read(buffer))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();if(count>MusicLyrics.MAX_BYTES-bytes.size())throw new IOException();bytes.write(buffer,0,count);}
            byte[] raw=bytes.toByteArray();int offset=0;Charset charset=StandardCharsets.UTF_8;if(raw.length>=2&&(raw[0]&255)==255&&(raw[1]&255)==254){charset=StandardCharsets.UTF_16LE;offset=2;}else if(raw.length>=2&&(raw[0]&255)==254&&(raw[1]&255)==255){charset=StandardCharsets.UTF_16BE;offset=2;}
            String text;try{text=decode(raw,offset,charset);}catch(CharacterCodingException failure){if(charset!=StandardCharsets.UTF_8)throw failure;text=decode(raw,0,Charset.forName("GB18030"));}
            for(int i=0;i<text.length();i++){char value=text.charAt(i);if(Character.isISOControl(value)&&value!='\r'&&value!='\n'&&value!='\t')throw new IOException();}
            MusicLyrics.parse(text);return raw;
        }catch(Exception failure){throw new IOException(Texts.get(context,"music070_lyrics_copy_failed"),failure);}
    }
    private static String decode(byte[] raw,int offset,Charset charset)throws CharacterCodingException{return charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(raw,offset,raw.length-offset)).toString();}
}
