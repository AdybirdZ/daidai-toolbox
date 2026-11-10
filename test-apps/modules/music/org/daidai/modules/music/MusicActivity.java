package org.daidai.modules.music;

import android.app.*;
import android.os.*;
import android.content.*;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.widget.*;
import android.view.*;
import android.content.pm.PackageManager;
import org.daidai.modules.shared.Ui;
import java.util.*;
import java.io.*;
import java.util.concurrent.*;
import org.daidai.modules.shared.Texts;

public class MusicActivity extends Activity {
    private final Handler main=new Handler(Looper.getMainLooper());
    private PlaybackService service;private boolean bound,started;private int tab;private LinearLayout list,analysisSlot;private MusicAnalysisPanel analysis;
    private TextView title,state,clock,lyrics,volumeLabel;private ImageView cover;private Button favorite,toggle,mode,speedButton,removeQueue,analysisToggle;
    private String displayedCover="";private SeekBar seek,volume;private boolean dragging,removing;private final Set<String> removal=new HashSet<>();
    private final ExecutorService worker=Executors.newSingleThreadExecutor();private Future<?> exportTask;private File pendingWav;private ProgressDialog exportProgress,importProgress;
    private final Runnable listener=this::update;private final Runnable tick=new Runnable(){public void run(){update();main.postDelayed(this,250);}};
    private final ServiceConnection connection=new ServiceConnection(){public void onServiceConnected(ComponentName n,IBinder b){service=((PlaybackService.LocalBinder)b).service();service.reload();service.addListener(listener);attachAnalysis();update();}public void onServiceDisconnected(ComponentName n){closeAnalysis();service=null;}};
    @Override public void onCreate(Bundle b){super.onCreate(b);if(b!=null)tab=b.getInt("tab",0);File[] old=getCacheDir().listFiles((d,n)->(n.startsWith("trim-")||n.startsWith("scope-")||n.startsWith("music-analysis-"))&&n.endsWith(".wav"));if(old!=null)for(File file:old)file.delete();render();}
    @Override protected void onSaveInstanceState(Bundle b){b.putInt("tab",tab);super.onSaveInstanceState(b);}
    private void render(){
        closeAnalysis();title=null;list=null;volume=null;analysisSlot=null;displayedCover="";
        LinearLayout page=Ui.page(this,txt("music_title"),txt("music_subtitle"));LinearLayout body=Ui.scroll(page);
        if(tab==0)renderPlayback(body);else if(tab==1){Ui.button(body,txt("music_import"),this::importAudio);body.addView(MusicLibraryScreen.createPage(this,this::libraryChanged));}
        else MusicSettingsScreen.populate(this,body,()->service,this::chooseFolder);
        LinearLayout navigation=Ui.row(page);String[] names={"music_tab_playback","library_my_songs","music_tab_settings"};
        for(int i=0;i<names.length;i++){final int next=i;Button button=Ui.cell(navigation,txt(names[i]),()->{if(tab!=next){tab=next;render();}});if(tab==i){button.setTextColor(Color.WHITE);button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Ui.BLUE));}}
        attachAnalysis();showList();update();
    }
    private void renderPlayback(LinearLayout body){
        LinearLayout now=Ui.card(body,"");cover=new ImageView(this);cover.setImageResource(getResources().getIdentifier("music_default_cover","drawable",getPackageName()));cover.setAdjustViewBounds(true);cover.setScaleType(ImageView.ScaleType.FIT_CENTER);cover.setAlpha(getSharedPreferences("music_module",0).getFloat("cover_alpha",1f));cover.setVisibility(getSharedPreferences("music_module",0).getBoolean("cover_show",true)?View.VISIBLE:View.GONE);now.addView(cover,new LinearLayout.LayoutParams(-1,Ui.dp(this,190)));
        cover.setContentDescription(txt("music_artwork_preview"));cover.setOnClickListener(v->{MusicLibrary.Track selected=service==null?null:service.selected();ArtworkPreviewActivity.open(this,selected);});
        analysisSlot=Ui.column(this);now.addView(analysisSlot);title=Ui.text(this,txt("music_no_track"),21);now.addView(title);state=Ui.text(this,"",14);now.addView(state);lyrics=Ui.text(this,"",17);lyrics.setGravity(Gravity.CENTER);lyrics.setMinHeight(Ui.dp(this,42));now.addView(lyrics);
        seek=new SeekBar(this);now.addView(seek);clock=Ui.text(this,"00:00 / 00:00",14);now.addView(clock);seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar s){dragging=true;}public void onStopTrackingTouch(SeekBar s){if(service!=null)service.seek(s.getProgress());dragging=false;}public void onProgressChanged(SeekBar s,int p,boolean user){if(user&&service!=null)clock.setText(time(p)+" / "+time(service.duration()));}});
        LinearLayout controls=Ui.row(now);Ui.cell(controls,txt("music_previous"),()->command(PlaybackService.PREV,-1));toggle=Ui.cell(controls,txt("music_play"),()->{if(service!=null&&service.isPlaying())command(PlaybackService.TOGGLE,-1);else command(PlaybackService.PLAY,service==null?0:Math.max(0,service.index()));});Ui.cell(controls,txt("music_next"),()->command(PlaybackService.NEXT,-1));
        LinearLayout extra=Ui.row(now);mode=Ui.cell(extra,txt("music_repeat_all"),()->{if(service!=null)service.cycleMode();});Ui.cell(extra,txt("music_stop"),()->{if(service!=null)service.rewindAndPause();});
        volumeLabel=Ui.text(this,"",14);now.addView(volumeLabel);volume=new SeekBar(this);now.addView(volume);volume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}public void onProgressChanged(SeekBar s,int p,boolean user){if(user&&service!=null)service.setVolume(p);}});now.addView(new MusicPercentScale(this,()->service==null?100:service.maxVolume()),new LinearLayout.LayoutParams(-1,Ui.dp(this,28)));
        LinearLayout editing=Ui.row(now);speedButton=Ui.cell(editing,txt("music_speed",1f),this::chooseSpeed);Ui.cell(editing,txt("music_trim"),this::trim);Ui.cell(editing,txt("music_tap_bpm"),this::tapBpm);
        LinearLayout tools=Ui.row(now);favorite=Ui.cell(tools,"♡ "+txt("library_favorite"),()->{if(service!=null&&service.selected()!=null)MusicLibraryScreen.toggleFavorite(this,service.selected(),this::libraryChanged);});analysisToggle=Ui.cell(tools,txt("music_analysis_show"),()->{boolean enabled=!getSharedPreferences("music_module",0).getBoolean("analysis_show",false);getSharedPreferences("music_module",0).edit().putBoolean("analysis_show",enabled).apply();attachAnalysis();});
        list=Ui.card(body,txt("music_playlist"));
    }
    private void attachAnalysis(){
        if(analysisSlot==null||service==null)return;boolean show=getSharedPreferences("music_module",0).getBoolean("analysis_show",false);
        if(analysisToggle!=null)analysisToggle.setText(txt(show?"music_analysis_hide":"music_analysis_show"));if(!show){closeAnalysis();analysisSlot.removeAllViews();return;}if(analysis==null){analysis=new MusicAnalysisPanel(this,service);analysisSlot.addView(analysis.view());}
        analysis.setSpectrumLogarithmic(getSharedPreferences("music_module",0).getBoolean("spectrum_log",true));analysis.setPeakCount(getSharedPreferences("music_module",0).getInt("spectrum_peaks",3));if(started)analysis.resume();
    }
    private void closeAnalysis(){if(analysis!=null){analysis.close();analysis=null;}if(analysisSlot!=null)analysisSlot.removeAllViews();}
    private String txt(String key,Object... args){return Texts.get(this,key,args);}
    private void chooseSpeed(){
        LinearLayout box=Ui.column(this);box.setPadding(Ui.dp(this,22),Ui.dp(this,10),Ui.dp(this,22),Ui.dp(this,10));
        float[] values={.5f,.75f,1,1.25f,1.5f,2};RadioGroup speeds=new RadioGroup(this);box.addView(speeds);
        float current=service==null?getSharedPreferences("music_module",0).getFloat("speed",1):service.speed();
        for(int i=0;i<values.length;i++){RadioButton radio=new RadioButton(this);radio.setId(8400+i);radio.setText(String.format(Locale.ROOT,"%.2f ×",values[i]));speeds.addView(radio);if(Math.abs(current-values[i])<.01f)speeds.check(8400+i);}
        speeds.setOnCheckedChangeListener((g,id)->{float value=values[id-8400];if(service!=null)service.setSpeed(value);else getSharedPreferences("music_module",0).edit().putFloat("speed",value).apply();});
        Switch pitch=new Switch(this);pitch.setText(txt("music_pitch_follow"));pitch.setChecked(service==null?getSharedPreferences("music_module",0).getBoolean("pitch_follow_speed",false):service.pitchFollowsSpeed());box.addView(pitch);pitch.setOnCheckedChangeListener((v,on)->{if(service!=null)service.setPitchFollowsSpeed(on);else getSharedPreferences("music_module",0).edit().putBoolean("pitch_follow_speed",on).apply();});
        box.addView(Ui.text(this,txt("music_pitch_note"),13));new AlertDialog.Builder(this).setTitle(txt("music_speed_title")).setView(box).setPositiveButton(txt("music_close"),null).show();
    }
    private void tapBpm(){
        LinearLayout box=Ui.column(this);box.setPadding(Ui.dp(this,22),Ui.dp(this,10),Ui.dp(this,22),Ui.dp(this,10));
        box.addView(Ui.text(this,txt("music_tap_bpm_note"),14));
        TextView status=Ui.text(this,txt("music_tap_bpm_ready"),16);status.setGravity(Gravity.CENTER);box.addView(status);
        TextView stats=Ui.text(this,txt("music_tap_bpm_stats",0,"0.0","—"),15);stats.setGravity(Gravity.CENTER);box.addView(stats);
        Button tap=Ui.button(box,txt("music_tap_bpm_tap"),()->{});tap.setTextSize(20);tap.setMinHeight(Ui.dp(this,64));
        LinearLayout controls=Ui.row(box);Button reset=Ui.cell(controls,txt("music_tap_bpm_reset"),()->{});Button finish=Ui.cell(controls,txt("music_tap_bpm_finish"),()->{});
        final MusicLibrary.Track measuredTrack=service==null?null:service.selected();
        Button write=Ui.button(box,txt("music_bpm_write"),()->{});write.setEnabled(false);
        final long[] first={0},last={0};final int[] count={0};final boolean[] ended={false};
        write.setOnClickListener(v->{if(measuredTrack==null||count[0]<2||last[0]<=first[0])return;String measured=java.math.BigDecimal.valueOf((count[0]-1)*60000.0/(last[0]-first[0])).toPlainString();String value=MusicLibrary.measuredBpm(measured,getSharedPreferences("music_module",0).getBoolean("bpm_keep_decimal",false));MusicLibrary.updateBpm(this,measuredTrack.uri,value);libraryChanged();Toast.makeText(this,txt("music_bpm_written",value),Toast.LENGTH_SHORT).show();});
        Runnable refresh=()->{if(first[0]==0)return;long now=ended[0]?last[0]:SystemClock.uptimeMillis();double seconds=Math.max(0,(now-first[0])/1000.0);double bpm=count[0]>=2&&last[0]>first[0]?(count[0]-1)*60000.0/(last[0]-first[0]):0;stats.setText(txt("music_tap_bpm_stats",count[0],String.format(Locale.ROOT,"%.1f",seconds),bpm>0?String.format(Locale.ROOT,"%.1f",bpm):"—"));if(!ended[0])status.setText(txt("music_tap_bpm_running"));};
        final AlertDialog[] dialogRef={null};final Handler handler=main;final Runnable ticker=new Runnable(){public void run(){refresh.run();if(!ended[0]&&dialogRef[0]!=null&&dialogRef[0].isShowing())handler.postDelayed(this,100);}};
        tap.setOnTouchListener((v,event)->{if(event.getActionMasked()==MotionEvent.ACTION_DOWN){long now=event.getEventTime();if(ended[0])return true;if(first[0]==0)first[0]=now;last[0]=now;count[0]++;refresh.run();}return true;});
        reset.setOnClickListener(v->{write.setEnabled(false);first[0]=last[0]=0;count[0]=0;ended[0]=false;status.setText(txt("music_tap_bpm_ready"));stats.setText(txt("music_tap_bpm_stats",0,"0.0","—"));handler.removeCallbacks(ticker);handler.post(ticker);});
        finish.setOnClickListener(v->{if(ended[0])return;ended[0]=true;refresh.run();write.setEnabled(count[0]>=2&&last[0]>first[0]&&measuredTrack!=null);if(count[0]<2){status.setText(txt("music_tap_bpm_insufficient"));}else{long span=last[0]-first[0];double bpm=span>0?(count[0]-1)*60000.0/span:0;status.setText(txt("music_tap_bpm_result",String.format(Locale.ROOT,"%.1f",bpm)));}});
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(txt("music_tap_bpm_title")).setView(box).setNegativeButton(txt("music_close"),null).create();dialogRef[0]=dialog;dialog.setOnShowListener(d->{handler.post(ticker);});dialog.setOnDismissListener(d->{ended[0]=true;handler.removeCallbacks(ticker);});dialog.show();
    }
    private void trim(){if(exportTask!=null&&!exportTask.isDone())return;MusicTrimDialog.open(this,service,this::decodeEdit);}
    private void decodeEdit(MusicLibrary.Track track,long[][] ranges,float speed,boolean preservePitch){
        exportProgress=new ProgressDialog(this);exportProgress.setMessage(txt("music_exporting"));exportProgress.setCancelable(false);exportProgress.setButton(-2,txt("music_cancel"),(d,w)->{if(exportTask!=null)exportTask.cancel(true);});exportProgress.show();
        exportTask=worker.submit(()->{File file=null,processed=null;try{
            file=File.createTempFile("trim-",".wav",getCacheDir());AudioTrimmer.exportRanges(this,Uri.parse(track.uri),ranges,file,t->{});
            if(Math.abs(speed-1f)>.001f){processed=File.createTempFile("trim-",".wav",getCacheDir());WavSpeedProcessor.process(file,processed,speed,preservePitch);file.delete();file=processed;processed=null;}
            if(Thread.currentThread().isInterrupted())throw new InterruptedException();final File ready=file;
            main.post(()->{if(exportProgress!=null)exportProgress.dismiss();if(isDestroyed()){ready.delete();return;}pendingWav=ready;try{startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("audio/wav").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"Daidai-clip.wav"),21);}catch(Exception e){ready.delete();pendingWav=null;Ui.error(this,e);}});
        }catch(Exception e){if(file!=null)file.delete();if(processed!=null)processed.delete();main.post(()->{if(exportProgress!=null)exportProgress.dismiss();if(!isDestroyed()&&!(e instanceof InterruptedException))Ui.error(this,e);});}});
    }
    private void finishExport(int result,Intent data){File file=pendingWav;pendingWav=null;if(file==null)return;if(result!=RESULT_OK||data==null||data.getData()==null){file.delete();return;}Uri destination=data.getData();exportTask=worker.submit(()->{try(InputStream in=new FileInputStream(file);OutputStream out=getContentResolver().openOutputStream(destination,"wt")){if(out==null)throw new IOException(txt("music_export_failed"));byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedException();out.write(b,0,n);}main.post(()->{if(!isDestroyed())Toast.makeText(this,txt("music_exported"),Toast.LENGTH_LONG).show();});}catch(Exception e){main.post(()->{if(!isDestroyed())Ui.error(this,e);});}finally{file.delete();}});}
    @Override protected void onDestroy(){MusicLibraryScreen.destroy(this);if(exportTask!=null)exportTask.cancel(true);worker.shutdownNow();if(pendingWav!=null)pendingWav.delete();if(exportProgress!=null)exportProgress.dismiss();if(importProgress!=null)importProgress.dismiss();closeAnalysis();super.onDestroy();}
    private void command(String action,int index){if(service==null)return;Intent i=new Intent(this,PlaybackService.class).setAction(action);if(index>=0)i.putExtra("index",index);
        try{if(action.equals(PlaybackService.PLAY)){if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},12);startForegroundService(i);}else startService(i);}catch(Exception e){Ui.error(this,e);}}
    private void chooseFolder(){try{startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION),31);}catch(Exception e){Ui.error(this,e);}}
    private void importAudio(){if(getSharedPreferences("music_module",0).getString("storage_tree_uri","").isEmpty()){new AlertDialog.Builder(this).setMessage(txt("music_folder_required")).setNegativeButton(txt("music_cancel"),null).setPositiveButton(txt("music_choose_folder"),(d,w)->chooseFolder()).show();return;}Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("audio/*").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);try{startActivityForResult(i,11);}catch(Exception e){Ui.error(this,e);}}
    private void storageSettings(){LinearLayout c=Ui.column(this);c.setPadding(Ui.dp(this,22),Ui.dp(this,10),Ui.dp(this,22),Ui.dp(this,10));MusicSettingsScreen.populate(this,c,()->service,this::chooseFolder);new AlertDialog.Builder(this).setTitle(txt("music_tab_settings")).setView(wrap(c)).setPositiveButton(txt("music_close"),null).show();}
    private ScrollView wrap(LinearLayout c){ScrollView sc=new ScrollView(this);sc.addView(c);return sc;}
    private void libraryChanged(){if(service!=null)service.reload();showList();update();}
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);if(MusicLibraryScreen.onActivityResult(this,request,result,data,this::libraryChanged))return;if(request==21){finishExport(result,data);return;}
        if(request==31&&result==RESULT_OK&&data!=null&&data.getData()!=null){Uri u=data.getData();try{getContentResolver().takePersistableUriPermission(u,data.getFlags()&(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION));getSharedPreferences("music_module",0).edit().putString("storage_tree_uri",u.toString()).apply();if(tab!=0)render();}catch(Exception e){Ui.error(this,e);}return;}
        if(request!=11||result!=RESULT_OK||data==null)return;ArrayList<Uri> uris=new ArrayList<>();if(data.getClipData()!=null){for(int j=0;j<data.getClipData().getItemCount();j++)uris.add(data.getClipData().getItemAt(j).getUri());}else if(data.getData()!=null)uris.add(data.getData());
        ProgressDialog importing=new ProgressDialog(this);importProgress=importing;importing.setMessage(txt("music_importing"));importing.setCancelable(false);importing.show();
        worker.submit(()->{ArrayList<MusicLibrary.Track> added=new ArrayList<>();int denied=0;for(Uri uri:uris){if(Thread.currentThread().isInterrupted())break;try{

            MusicLibrary.Track meta=MusicLibrary.readMetadata(this,uri);Uri stored=copyToFolder(uri,meta.title);if(stored!=null)meta=MusicLibrary.readMetadata(this,stored);added.add(meta);
        }catch(Exception e){if(Thread.currentThread().isInterrupted())break;denied++;}}
        MusicLibrary.addToQueue(this,added);final int failures=denied;main.post(()->{if(isDestroyed())return;importing.dismiss();libraryChanged();if(tab==1)render();Toast.makeText(this,txt("music_imported")+added.size()+(failures>0?txt("music_grant_denied"):""),Toast.LENGTH_LONG).show();});});
    }
    private Uri copyToFolder(Uri source,String title)throws IOException{return MusicImporter.copyToFolder(this,source);}
    private void showList(){if(list==null)return;list.removeAllViews();LinearLayout actions=Ui.row(list);removeQueue=Ui.cell(actions,txt(removing?"music_queue_remove_done":"music_queue_remove"),()->{if(removing){MusicLibrary.removeFromQueue(this,new ArrayList<>(removal));removal.clear();removing=false;libraryChanged();}else{removing=true;showList();}});Ui.cell(actions,txt("music_import"),this::importAudio);
        ArrayList<MusicLibrary.Track> tracks=MusicLibrary.load(this);Set<String> valid=new HashSet<>();for(MusicLibrary.Track t:tracks)valid.add(t.uri);removal.retainAll(valid);
        if(tracks.isEmpty())list.addView(Ui.text(this,txt("music_empty"),16));for(int i=0;i<tracks.size();i++){final int n=i;MusicLibrary.Track track=tracks.get(i);Button b=Ui.button(list,(i+1)+". "+track.title+(track.vocalist.isEmpty()?"":" · "+track.vocalist)+(MusicCollections.isFavorite(this,track.uri)?"  ♥":""),()->{if(removing){if(!removal.remove(track.uri))removal.add(track.uri);showList();}else command(PlaybackService.PLAY,n);});b.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);if(removal.contains(track.uri)){b.setTextColor(Color.WHITE);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xffb83232));}}
    }
    private String time(int ms){int s=Math.max(0,ms/1000);return String.format(Locale.ROOT,"%02d:%02d",s/60,s%60);}
    private void update(){if(service==null||title==null)return;setText(title,service.title());MusicLibrary.Track selected=service.selected();setText(lyrics,selected==null?"":MusicLibrary.lyricAt(this,selected,service.position()));String path=selected==null?"":selected.coverPath;String coverKey=path+(path.isEmpty()?"":new File(path).lastModified());if(!displayedCover.equals(coverKey)){displayedCover=coverKey;cover.setImageResource(getResources().getIdentifier("music_default_cover","drawable",getPackageName()));if(!path.isEmpty()){BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeFile(path,o);o.inSampleSize=1;while(Math.max(o.outWidth,o.outHeight)/o.inSampleSize>1024)o.inSampleSize*=2;o.inJustDecodeBounds=false;android.graphics.Bitmap b=BitmapFactory.decodeFile(path,o);if(b!=null)cover.setImageBitmap(b);}}if(favorite!=null){favorite.setEnabled(selected!=null);setText(favorite,(selected!=null&&MusicCollections.isFavorite(this,selected.uri)?"♥ ":"♡ ")+txt(selected!=null&&MusicCollections.isFavorite(this,selected.uri)?"library_unfavorite":"library_favorite"));}if(volume!=null){volume.setMax(service.maxVolume());if(!volume.isPressed())volume.setProgress(service.volume());setText(volumeLabel,txt("music_app_volume",service.volume(),service.maxVolume()));}setText(speedButton,txt("music_speed",service.speed()));setText(state,!service.error().isEmpty()?service.error():service.loading()?org.daidai.modules.shared.Texts.get(this,"music_loading"):service.isPlaying()?org.daidai.modules.shared.Texts.get(this,"music_playing"):org.daidai.modules.shared.Texts.get(this,"music_paused"));setText(toggle,service.isPlaying()?org.daidai.modules.shared.Texts.get(this,"music_pause"):org.daidai.modules.shared.Texts.get(this,"music_play"));String[] modes={org.daidai.modules.shared.Texts.get(this,"music_repeat_all"),org.daidai.modules.shared.Texts.get(this,"music_repeat_one"),org.daidai.modules.shared.Texts.get(this,"music_shuffle")};setText(mode,modes[service.mode().ordinal()]);if(!dragging){seek.setMax(Math.max(1,service.duration()));seek.setProgress(service.position());setText(clock,time(service.position())+" / "+time(service.duration()));}}
    private void setText(TextView v,String text){if(!v.getText().toString().equals(text))v.setText(text);}
    @Override protected void onStart(){super.onStart();started=true;bound=bindService(new Intent(this,PlaybackService.class),connection,BIND_AUTO_CREATE);main.post(tick);}
    @Override protected void onStop(){started=false;closeAnalysis();main.removeCallbacks(tick);if(service!=null)service.removeListener(listener);if(bound){unbindService(connection);bound=false;}service=null;super.onStop();}
}
