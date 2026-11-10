package org.daidai.prototype.qa;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.concurrent.Callable;
import org.daidai.modules.music.ArtworkPreviewActivity;
import org.daidai.modules.music.PlaybackService;

/** Integration checks for volume caps, rewind state and byte-preserving artwork export. */
public final class MusicPlayback050Checks {
    private static int count;
    private static void check(boolean value,String message){count++;if(!value)throw new AssertionError(message);}
    private static Field field(Class<?> type,String name)throws Exception{Field field=type.getDeclaredField(name);field.setAccessible(true);return field;}
    private static void waitFor(Instrumentation instrumentation,Callable<Boolean> predicate)throws Exception{
        long deadline=System.currentTimeMillis()+12000;
        while(System.currentTimeMillis()<deadline){boolean[] ready={false};instrumentation.runOnMainSync(()->{try{ready[0]=predicate.call();}catch(Exception e){throw new RuntimeException(e);}});if(ready[0])return;Thread.sleep(40);}
        throw new AssertionError("Timed out waiting for playback state");
    }
    public static int run(Instrumentation instrumentation,Activity activity,PlaybackService service)throws Exception{
        count=0;Context context=instrumentation.getTargetContext();int savedVolume=service.volume(),savedMax=service.maxVolume();
        AudioManager audio=(AudioManager)context.getSystemService(Context.AUDIO_SERVICE);int systemVolume=audio.getStreamVolume(AudioManager.STREAM_MUSIC);
        instrumentation.runOnMainSync(service::pause);
        Field playerField=field(PlaybackService.class,"player");MediaPlayer original=(MediaPlayer)playerField.get(service);
        VolumeProbe probe=new VolumeProbe();
        try{
            instrumentation.runOnMainSync(()->{try{playerField.set(service,probe);}catch(Exception e){throw new RuntimeException(e);}service.setMaxVolume(100);service.setVolume(37);});
            check(service.volume()==37&&Math.abs(probe.level-.37f)<.0001f,"app percentage reaches player");
            instrumentation.runOnMainSync(()->service.setMaxVolume(20));
            check(service.maxVolume()==20&&service.volume()==20&&Math.abs(probe.level-.2f)<.0001f,"lower maximum immediately clamps player");
            check(context.getSharedPreferences("music_module",0).getInt("volume_percent",-1)==20,"clamped volume persisted");
            instrumentation.runOnMainSync(()->service.setVolume(90));check(service.volume()==20,"cannot exceed configured maximum");
            instrumentation.runOnMainSync(()->service.setVolume(-3));check(service.volume()==0&&probe.level==0,"negative volume remains silent");
            instrumentation.runOnMainSync(()->{service.setMaxVolume(0);service.setVolume(100);});check(service.volume()==0&&service.maxVolume()==0,"zero volume maximum is supported");
            instrumentation.runOnMainSync(()->{service.setMaxVolume(200);service.setVolume(200);});check(service.volume()==100&&service.maxVolume()==100,"volume maximum bounded to 100");
            Method focus=PlaybackService.class.getDeclaredMethod("handleAudioFocus",int.class);focus.setAccessible(true);
            instrumentation.runOnMainSync(()->{service.setVolume(40);try{focus.invoke(service,AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK);}catch(Exception e){throw new RuntimeException(e);}});
            check(Math.abs(probe.level-.08f)<.0001f,"duck scales the selected volume");
            instrumentation.runOnMainSync(()->{try{focus.invoke(service,AudioManager.AUDIOFOCUS_GAIN);}catch(Exception e){throw new RuntimeException(e);}});
            check(Math.abs(probe.level-.4f)<.0001f,"focus gain restores selected volume");
            check(audio.getStreamVolume(AudioManager.STREAM_MUSIC)==systemVolume,"app volume does not change system volume");
        }finally{instrumentation.runOnMainSync(()->{try{playerField.set(service,original);}catch(Exception e){throw new RuntimeException(e);}service.setMaxVolume(savedMax);service.setVolume(savedVolume);});probe.release();}
        if(original!=null&&service.duration()>1500){String selected=service.selected().uri;
            instrumentation.runOnMainSync(()->{service.setSpeed(1);service.seek(1000);service.resume();});Thread.sleep(200);
            instrumentation.runOnMainSync(service::rewindAndPause);waitFor(instrumentation,()->!service.isPlaying()&&service.position()<80);
            check(playerField.get(service)==original&&service.selected().uri.equals(selected),"rewind retains source and selected track");
            Thread.sleep(250);check(!service.isPlaying()&&service.position()<80,"seek completion keeps rewind paused");
            instrumentation.runOnMainSync(service::resume);waitFor(instrumentation,()->service.isPlaying()&&service.position()>80);check(service.selected().uri.equals(selected),"same track resumes from start");
            instrumentation.runOnMainSync(()->{service.play(service.index());service.rewindAndPause();});waitFor(instrumentation,()->!service.loading()&&service.duration()>0);
            Thread.sleep(150);check(!service.isPlaying()&&service.position()<80,"rewind during preparation does not autostart");
        }
        checkRetiredSession(instrumentation,context,service);
        checkArtwork(instrumentation,context);return count;
    }
    private static void checkRetiredSession(Instrumentation instrumentation,Context context,PlaybackService service)throws Exception{
        if(service.selected()==null||service.duration()==0)return;final int index=service.index();
        MediaSession prior=(MediaSession)field(PlaybackService.class,"session").get(service);
        MediaController oldController=new MediaController(context,prior.getSessionToken());
        instrumentation.runOnMainSync(()->{service.stopPlayback();context.startForegroundService(new Intent(context,PlaybackService.class).setAction(PlaybackService.PLAY).putExtra("index",index));});
        waitFor(instrumentation,service::isPlaying);
        MediaSession current=(MediaSession)field(PlaybackService.class,"session").get(service);
        check(!prior.getSessionToken().equals(current.getSessionToken()),"stopped playback retires the old media-session token");
        instrumentation.runOnMainSync(()->oldController.getTransportControls().stop());
        Thread.sleep(250);check(service.isPlaying()&&service.duration()>0,"old notification controller cannot stop a newly started track");
        MediaController currentController=new MediaController(context,current.getSessionToken());
        instrumentation.runOnMainSync(()->currentController.getTransportControls().stop());
        waitFor(instrumentation,()->!service.isPlaying()&&service.duration()==0&&!service.loading());
        check(true,"current media controller can still stop playback");
        instrumentation.runOnMainSync(()->context.startForegroundService(new Intent(context,PlaybackService.class).setAction(PlaybackService.PLAY).putExtra("index",index)));waitFor(instrumentation,service::isPlaying);
        check(true,"playback can restart after a current controller stop");
    }
    private static void checkArtwork(Instrumentation instrumentation,Context context)throws Exception{
        File fixture=new File(context.getFilesDir(),"artwork-export-fixture.png");Bitmap bitmap=Bitmap.createBitmap(4,5,Bitmap.Config.ARGB_8888);bitmap.eraseColor(0xff3478c1);
        try(FileOutputStream output=new FileOutputStream(fixture)){bitmap.compress(Bitmap.CompressFormat.PNG,100,output);}bitmap.recycle();
        ArtworkPreviewActivity viewer=(ArtworkPreviewActivity)instrumentation.startActivitySync(new Intent(context,ArtworkPreviewActivity.class).putExtra("cover_path",fixture.toString()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try{
            Field button=field(ArtworkPreviewActivity.class,"save");waitFor(instrumentation,()->((android.widget.Button)button.get(viewer)).isEnabled());
            check(true,"artwork decoded and save enabled");
            if(Build.VERSION.SDK_INT>=29){long since=System.currentTimeMillis()/1000-2;Method save=ArtworkPreviewActivity.class.getDeclaredMethod("requestSave");save.setAccessible(true);
                instrumentation.runOnMainSync(()->{try{save.invoke(viewer);}catch(Exception e){throw new RuntimeException(e);}});
                waitFor(instrumentation,()->((android.widget.Button)button.get(viewer)).isEnabled());
                Uri exported=null;
                try(Cursor cursor=context.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,new String[]{MediaStore.Images.Media._ID,MediaStore.Images.Media.RELATIVE_PATH,MediaStore.Images.Media.IS_PENDING},MediaStore.Images.Media.DISPLAY_NAME+" LIKE ? AND "+MediaStore.Images.Media.DATE_ADDED+">=?",new String[]{"artwork-%",String.valueOf(since)},MediaStore.Images.Media._ID+" DESC")){
                    if(cursor!=null&&cursor.moveToFirst()){exported=Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,cursor.getString(0));check(cursor.getString(1).equals("Pictures/呆呆音乐/"),"artwork saved to requested album");check(cursor.getInt(2)==0,"artwork media row published");}
                }
                check(exported!=null,"artwork export registered in media library");
                if(exported!=null)try{byte[] copied;try(InputStream input=context.getContentResolver().openInputStream(exported);ByteArrayOutputStream output=new ByteArrayOutputStream()){byte[] data=new byte[1024];int length;while((length=input.read(data))!=-1)output.write(data,0,length);copied=output.toByteArray();}
                    check(Arrays.equals(java.nio.file.Files.readAllBytes(fixture.toPath()),copied),"artwork export preserves source bytes");
                    Bitmap decoded=BitmapFactory.decodeByteArray(copied,0,copied.length);check(decoded.getWidth()==4&&decoded.getHeight()==5,"original artwork dimensions retained");decoded.recycle();
                }finally{context.getContentResolver().delete(exported,null,null);}
            }
        }finally{instrumentation.runOnMainSync(viewer::finish);fixture.delete();}
    }
    public static final class VolumeProbe extends MediaPlayer{
        float level;@Override public void setVolume(float left,float right){level=left;}
        @Override public boolean isPlaying(){return false;}@Override public int getCurrentPosition(){return 0;}@Override public int getDuration(){return 4000;}
    }
}
