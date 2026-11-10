package org.daidai.prototype.qa;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.view.View;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;
import org.daidai.modules.music.*;
import org.daidai.modules.shared.Texts;

/** No audio fixture or service mutation; exercises real asynchronous lyric reads. */
public final class MusicLyrics070DeviceChecks {
    private static int checks;private static Instrumentation runner;
    private static void check(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
    private static void main(Runnable action){Throwable[] failure={null};runner.runOnMainSync(()->{try{action.run();}catch(Throwable e){failure[0]=e;}});if(failure[0]!=null)throw new AssertionError("Lyrics UI action failed",failure[0]);}
    private static Object field(Object owner,String name){try{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(Exception e){throw new RuntimeException(e);}}
    private static void set(Object owner,String name,Object value){try{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);f.set(owner,value);}catch(Exception e){throw new RuntimeException(e);}}
    private static String row(MusicLyricsView view,int index){return ((TextView)view.getChildAt(index)).getText().toString().trim();}
    private static void waitFor(Callable<Boolean> ready,String description)throws Exception{long end=SystemClock.uptimeMillis()+10000;while(SystemClock.uptimeMillis()<end){boolean[] done={false};main(()->{try{done[0]=ready.call();}catch(Exception e){throw new RuntimeException(e);}});if(done[0])return;Thread.sleep(30);}throw new AssertionError("Timed out: "+description);}
    private static void write(File file,String text)throws Exception{try(OutputStream out=new FileOutputStream(file)){out.write(text.getBytes(StandardCharsets.UTF_8));}}
    public static int run(Instrumentation instrumentation,Activity activity)throws Exception{
        runner=instrumentation;checks=0;SharedPreferences prefs=activity.getSharedPreferences("music_module",0);boolean had=prefs.contains(MusicLyricsView.FONT_PREFERENCE);Object old=prefs.getAll().get(MusicLyricsView.FONT_PREFERENCE);
        File first=new File(activity.getCacheDir(),"lyrics070-first.lrc"),second=new File(activity.getCacheDir(),"lyrics070-second.lrc");MusicLyricsView[] view={null};Uri[] document={null};
        try{
            write(first,"[00:01]First\n[00:02]Second\n[00:03]Third\n[00:04]Fourth\n[00:05]Fifth\n");write(second,"[00:00]Replacement\n[00:02]Next\n");
            main(()->{prefs.edit().remove(MusicLyricsView.FONT_PREFERENCE).commit();view[0]=new MusicLyricsView(activity);check(view[0].getChildCount()==4,"lyrics expose four wrapped text blocks");view[0].update("first",first.getAbsolutePath(),false,2500);});
            waitFor(()->row(view[0],1).equals("Second"),"local LRC loaded");main(()->{check(row(view[0],0).equals("First")&&row(view[0],2).equals("Third")&&row(view[0],3).equals("Fourth"),"previous/current/two following lines visible");TextView current=(TextView)view[0].getChildAt(1),other=(TextView)view[0].getChildAt(0);check(current.getCurrentTextColor()==Color.BLACK&&other.getCurrentTextColor()!=Color.BLACK&&current.getTextSize()>other.getTextSize(),"current cue is largest and black; adjacent cues remain legible");check(!current.isTextSelectable(),"lyrics cannot be long-press selected");view[0].update("first",first.getAbsolutePath(),false,0);check(row(view[0],1).equals(Texts.get(activity,"music070_lyrics_waiting"))&&row(view[0],2).equals("First"),"before-first state previews upcoming lyrics");view[0].update("first",first.getAbsolutePath(),false,4500);check(row(view[0],1).equals("Fourth"),"forward position update selects matching cue");view[0].update("first",first.getAbsolutePath(),false,1100);check(row(view[0],1).equals("First"),"backward seek updates immediately without rereading");Object timeline=field(view[0],"timeline");for(int i=0;i<100;i++)view[0].update("first",first.getAbsolutePath(),false,1200+i);check(field(view[0],"timeline")==timeline,"frequent playback ticks reuse the parsed timeline");});
            final Object[] sameMetadata={null};main(()->{sameMetadata[0]=field(view[0],"signature");set(view[0],"failed",true);set(view[0],"timeline",MusicLyrics.EMPTY);set(view[0],"nextProbe",0L);view[0].update("first",first.getAbsolutePath(),false,2500);});waitFor(()->row(view[0],1).equals("Second")&&!(Boolean)field(view[0],"failed"),"transient read failure recovers without metadata change");main(()->check(sameMetadata[0].equals(field(view[0],"signature")),"recovery retries contents even with the same source signature"));
            main(()->{prefs.edit().putInt(MusicLyricsView.FONT_PREFERENCE,36).commit();view[0].update("first",first.getAbsolutePath(),false,2000);check(Math.abs(((TextView)view[0].getChildAt(1)).getTextSize()/activity.getResources().getDisplayMetrics().scaledDensity-36)<.01f,"font preference applies to existing lyric view");prefs.edit().putInt(MusicLyricsView.FONT_PREFERENCE,999).commit();check(MusicLyricsView.fontSize(activity)==36,"out-of-range font preference clamped");});
            write(second,"[00:00]A long lyric line that wraps over multiple visual lines without clipping the next cue\n[00:02]Next\n");
            main(()->{view[0].update("first",first.getAbsolutePath(),false,3000);view[0].refresh();view[0].update("second",Uri.fromFile(second).toString(),false,0);});waitFor(()->row(view[0],1).startsWith("A long lyric"),"file URI and latest generation loaded");Thread.sleep(200);main(()->{check(!row(view[0],1).equals("Third"),"old-track result cannot overwrite new lyrics");view[0].measure(View.MeasureSpec.makeMeasureSpec(280,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));view[0].layout(0,0,280,view[0].getMeasuredHeight());TextView current=(TextView)view[0].getChildAt(1);check(current.getLineCount()>1&&current.getHeight()>=current.getLayout().getHeight(),"long lyric wraps without cutting off its block");Bitmap bitmap=Bitmap.createBitmap(280,view[0].getHeight(),Bitmap.Config.ARGB_8888);view[0].draw(new Canvas(bitmap));bitmap.recycle();});
            write(second,"[00:00]Updated lyrics\n[00:02]Next\n");main(()->view[0].refresh());waitFor(()->row(view[0],1).equals("Updated lyrics"),"explicit same-source refresh");
            if(Build.VERSION.SDK_INT>=29){ContentValues values=new ContentValues();values.put(MediaStore.Downloads.DISPLAY_NAME,"lyrics070-fixture.lrc");values.put(MediaStore.Downloads.MIME_TYPE,"text/plain");values.put(MediaStore.Downloads.RELATIVE_PATH,"Download/MusicLyricsChecks");document[0]=activity.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values);check(document[0]!=null,"owned content lyrics created");try(OutputStream out=activity.getContentResolver().openOutputStream(document[0])){out.write("[00:00]Document lyrics\n[00:02]Next".getBytes(StandardCharsets.UTF_8));}main(()->view[0].update("document",document[0].toString(),false,0));waitFor(()->row(view[0],1).equals("Document lyrics"),"content resolver lyrics source");}
            main(()->{view[0].update("pure","",true,0);check(row(view[0],1).equals(Texts.get(activity,"library_instrumental_lyrics"))&&row(view[0],0).isEmpty()&&row(view[0],2).isEmpty(),"instrumental state clears neighboring cues");view[0].update("none","",false,0);check(row(view[0],1).equals(Texts.get(activity,"music070_lyrics_none")),"missing lyrics explained");view[0].update("missing",new File(activity.getCacheDir(),"missing-lyrics070.lrc").getAbsolutePath(),false,0);});waitFor(()->row(view[0],1).equals(Texts.get(activity,"music070_lyrics_unavailable")),"missing source failure explained");
            main(()->{view[0].close();view[0].close();check(((java.util.concurrent.ExecutorService)field(view[0],"loader")).isShutdown(),"closing lyrics cancels loader idempotently");});
            return checks;
        }finally{main(()->{if(view[0]!=null)view[0].close();SharedPreferences.Editor edit=prefs.edit().remove(MusicLyricsView.FONT_PREFERENCE);if(had&&old instanceof Integer)edit.putInt(MusicLyricsView.FONT_PREFERENCE,(Integer)old);edit.commit();});if(document[0]!=null)activity.getContentResolver().delete(document[0],null,null);first.delete();second.delete();}
    }
}
