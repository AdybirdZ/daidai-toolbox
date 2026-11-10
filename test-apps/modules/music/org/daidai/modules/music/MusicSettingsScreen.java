package org.daidai.modules.music;

import android.app.*;
import android.content.*;
import android.widget.*;
import java.util.function.Supplier;
import org.daidai.modules.shared.Ui;
import org.daidai.modules.shared.Texts;

/** Settings presentation stays inside the reusable music module. */
public final class MusicSettingsScreen {
    private MusicSettingsScreen(){}
    private static String t(Activity a,String key,Object...args){return Texts.get(a,key,args);}
    private static LinearLayout box(Activity a){LinearLayout b=Ui.column(a);int d=Ui.dp(a,22);b.setPadding(d,Ui.dp(a,12),d,Ui.dp(a,12));return b;}
    private static void show(Activity a,String key,LinearLayout b){ScrollView sc=new ScrollView(a);sc.addView(b);new AlertDialog.Builder(a).setTitle(t(a,key)).setView(sc).setPositiveButton(t(a,"music_close"),null).show();}
    public static void populate(Activity a,LinearLayout parent,Supplier<PlaybackService> playback,Runnable chooseFolder){
        SharedPreferences pref=a.getSharedPreferences("music_module",0);
        Ui.button(parent,t(a,"music_storage_settings"),()->{LinearLayout b=box(a);Ui.button(b,t(a,"music_choose_folder"),chooseFolder);b.addView(Ui.text(a,t(a,pref.getString("storage_tree_uri","").isEmpty()?"music_folder_unset":"music_folder_set"),14));b.addView(Ui.text(a,t(a,"music_storage_note"),13));show(a,"music_storage_settings",b);});
        Ui.button(parent,t(a,"music_artwork_settings"),()->{LinearLayout b=box(a);Switch visible=new Switch(a);visible.setText(t(a,"music_cover_show"));visible.setChecked(pref.getBoolean("cover_show",true));visible.setOnCheckedChangeListener((v,on)->pref.edit().putBoolean("cover_show",on).apply());b.addView(visible);b.addView(Ui.text(a,t(a,"music_cover_clarity"),15));SeekBar opacity=new SeekBar(a);opacity.setMax(100);opacity.setProgress(Math.round(pref.getFloat("cover_alpha",1)*100));opacity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}public void onProgressChanged(SeekBar s,int p,boolean user){if(user)pref.edit().putFloat("cover_alpha",p/100f).apply();}});b.addView(opacity);show(a,"music_artwork_settings",b);});
        Ui.button(parent,t(a,"music_max_volume"),()->{LinearLayout b=box(a);int max=playback.get()==null?pref.getInt("max_volume_percent",100):playback.get().maxVolume();TextView label=Ui.text(a,t(a,"music_max_volume_value",max),16);b.addView(label);SeekBar slider=new SeekBar(a);slider.setMax(100);slider.setProgress(max);b.addView(slider);b.addView(new MusicPercentScale(a,()->100),new LinearLayout.LayoutParams(-1,Ui.dp(a,28)));slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}public void onProgressChanged(SeekBar s,int p,boolean user){label.setText(t(a,"music_max_volume_value",p));if(user){PlaybackService service=playback.get();if(service!=null)service.setMaxVolume(p);else pref.edit().putInt("max_volume_percent",p).putInt("volume_percent",Math.min(p,pref.getInt("volume_percent",0))).apply();}}});b.addView(Ui.text(a,t(a,"music_volume_note"),13));show(a,"music_max_volume",b);});
        Ui.button(parent,t(a,"music_bpm_rounding"),()->{LinearLayout b=box(a);RadioGroup choices=new RadioGroup(a);b.addView(choices);String[] keys={"music_bpm_integer","music_bpm_decimal"};for(int i=0;i<keys.length;i++){RadioButton r=new RadioButton(a);r.setId(9500+i);r.setText(t(a,keys[i]));choices.addView(r);}choices.check(pref.getBoolean("bpm_keep_decimal",false)?9501:9500);choices.setOnCheckedChangeListener((g,id)->pref.edit().putBoolean("bpm_keep_decimal",id==9501).apply());b.addView(Ui.text(a,t(a,"music_bpm_rounding_note"),13));show(a,"music_bpm_rounding",b);});
        Ui.button(parent,t(a,"music_analysis_settings"),()->{LinearLayout b=box(a);RadioGroup axes=new RadioGroup(a);b.addView(axes);for(int i=0;i<2;i++){RadioButton r=new RadioButton(a);r.setId(9600+i);r.setText(t(a,i==0?"music_frequency_log":"music_frequency_linear"));axes.addView(r);}axes.check(pref.getBoolean("spectrum_log",true)?9600:9601);axes.setOnCheckedChangeListener((g,id)->pref.edit().putBoolean("spectrum_log",id==9600).apply());int peaks=pref.getInt("spectrum_peaks",3);TextView label=Ui.text(a,t(a,"music_spectral_peak_count",peaks),15);b.addView(label);SeekBar slider=new SeekBar(a);slider.setMax(10);slider.setProgress(peaks);b.addView(slider);slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}public void onProgressChanged(SeekBar s,int p,boolean user){label.setText(t(a,"music_spectral_peak_count",p));if(user)pref.edit().putInt("spectrum_peaks",p).apply();}});b.addView(Ui.text(a,t(a,"music_analysis_note"),13));show(a,"music_analysis_settings",b);});
        Ui.button(parent,t(a,"music_guide_title"),()->Ui.note(a,t(a,"music_local_music"),t(a,"music_guide")));
    }
}
