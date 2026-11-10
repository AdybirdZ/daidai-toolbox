package org.daidai.modules.music;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.daidai.modules.shared.Texts;
import org.daidai.modules.shared.Ui;

/** Four wrapped lyric cues. The host supplies position updates and owns close(). */
public final class MusicLyricsView extends LinearLayout implements AutoCloseable {
    public static final String FONT_PREFERENCE="lyrics_font_sp";
    public static final int DEFAULT_FONT_SP=22,MIN_FONT_SP=14,MAX_FONT_SP=36;
    private final TextView[] rows=new TextView[4];
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService loader=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"music-lyrics-view");t.setDaemon(true);return t;});
    private Future<?> task;
    private MusicLyrics timeline=MusicLyrics.EMPTY;
    private String sourceId="",path="",signature;
    private long generation,position,nextProbe;
    private boolean instrumental,selected,loading,probing,failed,closed;
    private int fontSp=-1;
    public MusicLyricsView(Context context){
        super(context);setOrientation(VERTICAL);setPadding(Ui.dp(context,12),Ui.dp(context,8),Ui.dp(context,12),Ui.dp(context,8));
        for(int i=0;i<rows.length;i++){TextView row=new TextView(context);row.setGravity(Gravity.CENTER);row.setTextIsSelectable(false);row.setIncludeFontPadding(false);row.setLineSpacing(Ui.dp(context,2),1.05f);row.setTextColor(i==1?Color.BLACK:0xff59616d);row.setTypeface(Typeface.DEFAULT,i==1?Typeface.BOLD:Typeface.NORMAL);row.setPadding(0,Ui.dp(context,4),0,Ui.dp(context,4));rows[i]=row;addView(row,new LayoutParams(-1,-2));}
        applyFont();render();
    }
    public static int fontSize(Context context){int value=DEFAULT_FONT_SP;try{value=context.getSharedPreferences("music_module",0).getInt(FONT_PREFERENCE,DEFAULT_FONT_SP);}catch(ClassCastException ignored){}return Math.max(MIN_FONT_SP,Math.min(MAX_FONT_SP,value));}
    /** Convenience adapter; hosts can instead use the path-based overload below. */
    public void update(MusicLibrary.Track track,long positionMillis){update(track==null?null:track.uri,track==null?"":track.lrcPath,track!=null&&track.instrumental,positionMillis);}
    /** Accepts an old absolute file path, file URI or system document URI. */
    public void update(String trackId,String lyricsLocation,boolean pureMusic,long positionMillis){
        if(closed)return;boolean hasTrack=trackId!=null;String id=trackId==null?"":trackId,location=lyricsLocation==null?"":lyricsLocation;
        position=Math.max(0,positionMillis);applyFont();
        if(selected!=hasTrack||!sourceId.equals(id)||!path.equals(location)||instrumental!=pureMusic){
            generation++;if(task!=null)task.cancel(true);sourceId=id;path=location;selected=hasTrack;instrumental=pureMusic;signature=null;timeline=MusicLyrics.EMPTY;failed=false;probing=false;loading=selected&&!instrumental&&!path.isEmpty();nextProbe=0;
        }
        if(selected&&!instrumental&&!path.isEmpty()&&SystemClock.uptimeMillis()>=nextProbe&&!probing)probe();render();
    }
    /** Call after replacing lyrics at the same URI when provider metadata is unavailable. */
    public void refresh(){if(closed)return;generation++;if(task!=null)task.cancel(true);signature=null;failed=false;probing=false;nextProbe=0;loading=selected&&!instrumental&&!path.isEmpty();if(loading)probe();render();}
    private void applyFont(){int size=fontSize(getContext());if(size==fontSp)return;fontSp=size;for(int i=0;i<rows.length;i++){float points=i==1?size:Math.max(12,size*.82f);rows[i].setTextSize(points);rows[i].setMinHeight(Math.round(points*getResources().getDisplayMetrics().scaledDensity*1.35f)+Ui.dp(getContext(),8));}}
    private void probe(){
        final long token=generation;final String location=path,known=signature;final boolean retry=failed;probing=true;nextProbe=SystemClock.uptimeMillis()+1000;
        task=loader.submit(()->{MusicLyrics loaded=null;String stamp=null;boolean error=false,changed=false;
            try{stamp=signature(location);changed=retry||!stamp.equals(known);if(changed)try(InputStream input=open(location)){loaded=MusicLyrics.read(input);}}catch(Exception e){error=true;changed=true;if(stamp==null)stamp="unavailable:"+location;}
            final MusicLyrics result=loaded;final String version=stamp;final boolean bad=error,replaced=changed;
            main.post(()->{if(closed||generation!=token)return;probing=false;loading=false;if(replaced){signature=version;failed=bad;timeline=result==null?MusicLyrics.EMPTY:result;}render();});
        });
    }
    private InputStream open(String location)throws IOException{Uri uri=Uri.parse(location);String scheme=uri.getScheme();if(scheme==null||scheme.isEmpty())return new FileInputStream(location);if("file".equals(scheme))return new FileInputStream(new File(uri.getPath()));if(!"content".equals(scheme))throw new IOException("Unsupported lyrics source");InputStream input=getContext().getContentResolver().openInputStream(uri);if(input==null)throw new IOException("Lyrics source unavailable");return input;}
    private String signature(String location)throws IOException{
        Uri uri=Uri.parse(location);String scheme=uri.getScheme();if(scheme==null||scheme.isEmpty()||"file".equals(scheme)){File file=new File(scheme==null||scheme.isEmpty()?location:uri.getPath());if(!file.isFile())throw new IOException("Lyrics file unavailable");if(file.length()>MusicLyrics.MAX_BYTES)throw new IOException("Lyrics exceed size limit");return "file:"+file.lastModified()+":"+file.length();}
        if(!"content".equals(scheme))throw new IOException("Unsupported lyrics source");String size="",modified="";
        try(Cursor cursor=getContext().getContentResolver().query(uri,new String[]{OpenableColumns.SIZE,DocumentsContract.Document.COLUMN_LAST_MODIFIED},null,null,null)){if(cursor!=null&&cursor.moveToFirst()){int at=cursor.getColumnIndex(OpenableColumns.SIZE);if(at>=0&&!cursor.isNull(at)){long bytes=cursor.getLong(at);if(bytes>MusicLyrics.MAX_BYTES)throw new IOException("Lyrics exceed size limit");size=Long.toString(bytes);}at=cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED);if(at>=0&&!cursor.isNull(at))modified=cursor.getString(at);}}
        catch(SecurityException e){throw e;}catch(IOException e){throw e;}catch(Exception ignored){}
        return "content:"+size+":"+modified;
    }
    private void render(){
        String[] text={"","","",""};
        if(selected){if(instrumental)text[1]=Texts.get(getContext(),"library_instrumental_lyrics");else if(path.isEmpty())text[1]=Texts.get(getContext(),"music070_lyrics_none");else if(loading)text[1]=Texts.get(getContext(),"music070_lyrics_loading");else if(failed)text[1]=Texts.get(getContext(),"music070_lyrics_unavailable");else if(timeline.size()==0)text[1]=Texts.get(getContext(),"music070_lyrics_untimed");else{MusicLyrics.Window window=timeline.at(position);text[0]=window.previous;text[1]=window.currentIndex<0?Texts.get(getContext(),"music070_lyrics_waiting"):window.current;text[2]=window.next;text[3]=window.nextAfter;}}
        for(int i=0;i<rows.length;i++){String value=text[i].isEmpty()?" ":text[i];if(!rows[i].getText().toString().equals(value))rows[i].setText(value);}
    }
    @Override public void close(){if(closed)return;closed=true;generation++;if(task!=null)task.cancel(true);loader.shutdownNow();}
}
