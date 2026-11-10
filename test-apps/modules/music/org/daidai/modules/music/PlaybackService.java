package org.daidai.modules.music;

import android.app.*;
import android.content.*;
import android.media.*;
import android.media.session.*;
import android.net.Uri;
import android.os.*;
import org.daidai.modules.shared.Ui;
import java.util.*;

/** Reusable playback service; all entry intents are internal to the host app. */
public final class PlaybackService extends Service {
    public static final String PLAY="play",TOGGLE="toggle",NEXT="next",PREV="previous",STOP="stop";
    private static final int NOTIFICATION=41;
    public final class LocalBinder extends Binder {public PlaybackService service(){return PlaybackService.this;}}
    private final LocalBinder binder=new LocalBinder();private final Random random=new Random();
    private final Handler main=new Handler(Looper.getMainLooper());
    private MediaPlayer player;private MediaSession session;private AudioManager audio;private AudioFocusRequest focus;
    private ArrayList<MusicLibrary.Track> queue=new ArrayList<>();private int index=-1;private boolean prepared,loading,foreground,resumeOnGain;
    private String error="";private QueuePlanner.Mode mode=QueuePlanner.Mode.ALL;
    private boolean muted=true,ducked,previewSeeking;private boolean pitchFollowsSpeed;private float speed=1;private int previewEnd=-1;
    private final Runnable previewTick=new Runnable(){public void run(){if(previewEnd<0)return;if(position()>=previewEnd){previewEnd=-1;pause();}else main.postDelayed(this,40);}};
    private final ArrayList<Runnable> listeners=new ArrayList<>();
    private final BroadcastReceiver noisy=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){pause();}};
    @Override public void onCreate(){super.onCreate();queue=MusicLibrary.load(this);muted=getSharedPreferences("music_module",0).getBoolean("muted",true);pitchFollowsSpeed=getSharedPreferences("music_module",0).getBoolean("pitch_follow_speed",false);speed=getSharedPreferences("music_module",0).getFloat("speed",1);if(!Float.isFinite(speed)||speed<.5f||speed>2)speed=1;String saved=getSharedPreferences("music_module",0).getString("mode","ALL");try{mode=QueuePlanner.Mode.valueOf(saved);}catch(Exception ignored){}
        audio=(AudioManager)getSystemService(AUDIO_SERVICE);focus=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()).setOnAudioFocusChangeListener(this::handleAudioFocus).build();
        session=new MediaSession(this,"DaidaiMusic");session.setCallback(new MediaSession.Callback(){@Override public void onPlay(){resume();}@Override public void onPause(){pause();}@Override public void onSkipToNext(){play(QueuePlanner.next(queue.size(),index,mode,false,random));}@Override public void onSkipToPrevious(){play(QueuePlanner.previous(queue.size(),index));}@Override public void onSeekTo(long p){seek((int)p);}@Override public void onStop(){stopPlayback();}});
        session.setActive(true);NotificationManager nm=getSystemService(NotificationManager.class);nm.createNotificationChannel(new NotificationChannel("music_playback",org.daidai.modules.shared.Texts.get(this,"music_channel"),NotificationManager.IMPORTANCE_LOW));
        if(Build.VERSION.SDK_INT>=33)registerReceiver(noisy,new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),Context.RECEIVER_NOT_EXPORTED);else registerReceiver(noisy,new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));}
    private void handleAudioFocus(int change){if(change==AudioManager.AUDIOFOCUS_LOSS){resumeOnGain=false;pause();}else if(change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT){resumeOnGain=isPlaying();pause();}else if(change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK){ducked=true;applyVolume();}else if(change==AudioManager.AUDIOFOCUS_GAIN){ducked=false;applyVolume();if(resumeOnGain){resumeOnGain=false;resume();}}}
    @Override public IBinder onBind(Intent i){return binder;}
    @Override public int onStartCommand(Intent intent,int flags,int id){if(intent!=null){String action=intent.getAction();if(PLAY.equals(action)){ensureForeground();int selected=intent.getIntExtra("index",Math.max(0,index));if(selected==index&&prepared)resume();else play(selected);}else if(TOGGLE.equals(action)){if(isPlaying())pause();else resume();}else if(NEXT.equals(action))play(QueuePlanner.next(queue.size(),index,mode,false,random));else if(PREV.equals(action))play(QueuePlanner.previous(queue.size(),index));else if(STOP.equals(action))stopPlayback();}return START_NOT_STICKY;}
    public void addListener(Runnable r){if(!listeners.contains(r))listeners.add(r);r.run();}
    public void removeListener(Runnable r){listeners.remove(r);}
    public void reload(){String current=index>=0&&index<queue.size()?queue.get(index).uri:null;queue=MusicLibrary.load(this);int found=-1;if(current!=null)for(int i=0;i<queue.size();i++)if(queue.get(i).uri.equals(current))found=i;
        if(current!=null&&found<0)stopPlayback();index=found;changed();}
    public int index(){return index;}public boolean isPlaying(){return player!=null&&prepared&&player.isPlaying();}public boolean loading(){return loading;}
    public String error(){return error;}public String title(){return index>=0&&index<queue.size()?queue.get(index).title:org.daidai.modules.shared.Texts.get(this,"music_no_track");}
    public int position(){try{return prepared?player.getCurrentPosition():0;}catch(Exception e){return 0;}}public int duration(){try{return prepared?player.getDuration():0;}catch(Exception e){return 0;}}
    public QueuePlanner.Mode mode(){return mode;}public void cycleMode(){mode=QueuePlanner.Mode.values()[(mode.ordinal()+1)%3];getSharedPreferences("music_module",0).edit().putString("mode",mode.name()).apply();changed();}
    public void play(int selected){if(selected<0||selected>=queue.size()){stopPlayback();return;}releasePlayer();index=selected;error="";loading=true;ensureForeground();
        try{MediaPlayer p=new MediaPlayer();player=p;applyVolume();p.setWakeMode(this,PowerManager.PARTIAL_WAKE_LOCK);p.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
            p.setOnPreparedListener(mp->{if(player!=mp)return;prepared=true;loading=false;setSpeed(speed);resume();});
            p.setOnSeekCompleteListener(mp->{if(player==mp&&previewSeeking){previewSeeking=false;resume();main.post(previewTick);}});
            p.setOnCompletionListener(mp->play(QueuePlanner.next(queue.size(),index,mode,true,random)));
            p.setOnErrorListener((mp,what,extra)->{if(player==mp){error=org.daidai.modules.shared.Texts.get(this,"music_unplayable");releasePlayer();audio.abandonAudioFocusRequest(focus);if(foreground){stopForeground(true);foreground=false;}stopSelf();changed();}return true;});
            p.setDataSource(this,Uri.parse(queue.get(index).uri));p.prepareAsync();changed();
        }catch(Exception e){error=org.daidai.modules.shared.Texts.get(this,"music_open_failed")+e.getMessage();releasePlayer();if(foreground){stopForeground(true);foreground=false;}stopSelf();changed();}}
    public void resume(){if(loading)return;if(!prepared){if(!queue.isEmpty())play(Math.max(0,index));return;}ensureForeground();if(audio.requestAudioFocus(focus)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED){error=org.daidai.modules.shared.Texts.get(this,"music_focus_unavailable");changed();return;}applyVolume();player.start();changed();}
    public void pause(){if(prepared&&player.isPlaying())player.pause();changed();}
    public void seek(int position){previewEnd=-1;previewSeeking=false;main.removeCallbacks(previewTick);if(prepared){player.seekTo(Math.max(0,Math.min(duration(),position)));changed();}}
    public void stopPlayback(){resumeOnGain=false;releasePlayer();audio.abandonAudioFocusRequest(focus);if(foreground){stopForeground(true);foreground=false;}stopSelf();changed();}
    public MusicLibrary.Track selected(){return index>=0&&index<queue.size()?queue.get(index):null;}
    public boolean muted(){return muted;}public float speed(){return speed;}public boolean pitchFollowsSpeed(){return pitchFollowsSpeed;}public int audioSessionId(){try{return player==null?0:player.getAudioSessionId();}catch(Exception e){return 0;}}
    public void setMuted(boolean value){muted=value;getSharedPreferences("music_module",0).edit().putBoolean("muted",value).apply();applyVolume();changed();}
    public void setPitchFollowsSpeed(boolean value){pitchFollowsSpeed=value;getSharedPreferences("music_module",0).edit().putBoolean("pitch_follow_speed",value).apply();try{applySpeed();}catch(Exception e){error=org.daidai.modules.shared.Texts.get(this,"music_speed_failed");}changed();}
    private void applyVolume(){if(player!=null){float value=muted?0:ducked?.2f:1;player.setVolume(value,value);}}
    private void applySpeed(){if(!prepared)return;boolean playing=isPlaying();player.setVolume(0,0);try{player.setPlaybackParams(new PlaybackParams().setSpeed(speed).setPitch(pitchFollowsSpeed?speed:1));}finally{if(!playing&&player.isPlaying())player.pause();applyVolume();}}
    public void setSpeed(float value){if(!Float.isFinite(value)||value<.5f||value>2)return;float old=speed;speed=value;try{applySpeed();getSharedPreferences("music_module",0).edit().putFloat("speed",speed).apply();}catch(Exception e){speed=old;error=org.daidai.modules.shared.Texts.get(this,"music_speed_failed");try{applySpeed();}catch(Exception ignored){pause();}}changed();}
    public void preview(int from,int to){if(!prepared||from<0||to<=from||to>duration())return;pause();previewEnd=to;previewSeeking=true;main.removeCallbacks(previewTick);player.seekTo(from,MediaPlayer.SEEK_CLOSEST);}
    public void endPreview(){if(previewEnd>=0){previewEnd=-1;previewSeeking=false;main.removeCallbacks(previewTick);pause();}}
    private void releasePlayer(){previewEnd=-1;previewSeeking=false;main.removeCallbacks(previewTick);prepared=false;loading=false;if(player!=null){player.release();player=null;}}
    private void ensureForeground(){if(!foreground){startForeground(NOTIFICATION,notification());foreground=true;}}
    private PendingIntent action(String a){return PendingIntent.getService(this,a.hashCode(),new Intent(this,PlaybackService.class).setAction(a),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    private Notification notification(){Intent launch=getPackageManager().getLaunchIntentForPackage(getPackageName());PendingIntent open=PendingIntent.getActivity(this,0,launch,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this,"music_playback").setSmallIcon(android.R.drawable.ic_media_play).setContentTitle(title()).setContentText(loading?org.daidai.modules.shared.Texts.get(this,"music_loading"):org.daidai.modules.shared.Texts.get(this,"music_notification")).setSubText(org.daidai.modules.shared.Texts.get(this,muted?"music_muted":"music_unmuted")).setContentIntent(open).setVisibility(Notification.VISIBILITY_PUBLIC).setOngoing(isPlaying()||loading).setOnlyAlertOnce(true)
            .addAction(new Notification.Action.Builder(android.R.drawable.ic_media_previous,org.daidai.modules.shared.Texts.get(this,"music_previous"),action(PREV)).build())
            .addAction(new Notification.Action.Builder(isPlaying()?android.R.drawable.ic_media_pause:android.R.drawable.ic_media_play,isPlaying()?org.daidai.modules.shared.Texts.get(this,"music_pause"):org.daidai.modules.shared.Texts.get(this,"music_play"),action(TOGGLE)).build())
            .addAction(new Notification.Action.Builder(android.R.drawable.ic_media_next,org.daidai.modules.shared.Texts.get(this,"music_next"),action(NEXT)).build())
            .addAction(new Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel,org.daidai.modules.shared.Texts.get(this,"music_stop"),action(STOP)).build())
            .setStyle(new Notification.MediaStyle().setMediaSession(session.getSessionToken()).setShowActionsInCompactView(0,1,2)).build();}
    private void changed(){int state=loading?PlaybackState.STATE_BUFFERING:isPlaying()?PlaybackState.STATE_PLAYING:prepared?PlaybackState.STATE_PAUSED:PlaybackState.STATE_STOPPED;
        session.setPlaybackState(new PlaybackState.Builder().setActions(PlaybackState.ACTION_PLAY|PlaybackState.ACTION_PAUSE|PlaybackState.ACTION_PLAY_PAUSE|PlaybackState.ACTION_SKIP_TO_NEXT|PlaybackState.ACTION_SKIP_TO_PREVIOUS|PlaybackState.ACTION_SEEK_TO|PlaybackState.ACTION_STOP).setState(state,position(),isPlaying()?speed:0).build());
        session.setMetadata(new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE,title()).putLong(MediaMetadata.METADATA_KEY_DURATION,duration()).build());
        if(foreground)getSystemService(NotificationManager.class).notify(NOTIFICATION,notification());for(Runnable listener:new ArrayList<>(listeners))listener.run();}
    @Override public void onDestroy(){releasePlayer();audio.abandonAudioFocusRequest(focus);session.release();unregisterReceiver(noisy);listeners.clear();main.removeCallbacksAndMessages(null);super.onDestroy();}
}
