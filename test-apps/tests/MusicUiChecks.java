package org.daidai.prototype.qa;

import android.app.*;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.view.*;
import android.view.inspector.WindowInspector;
import android.widget.*;
import java.lang.reflect.*;
import java.io.*;
import java.util.*;
import org.daidai.modules.music.*;
import org.daidai.modules.shared.Texts;

/** Exercises actual dialogs and their persisted outcomes, without launching document pickers. */
public final class MusicUiChecks {
    private static int count;
    private static Instrumentation instrumentation;
    private static Activity activity;
    private static void check(boolean value,String message){count++;if(!value)throw new AssertionError(message);}
    private static String t(String key,Object...args){return Texts.get(activity,key,args);}
    private static void main(Runnable action){final Throwable[] failure={null};instrumentation.runOnMainSync(()->{try{action.run();}catch(Throwable e){failure[0]=e;}});instrumentation.waitForIdleSync();if(failure[0]!=null)throw new AssertionError("UI action failed",failure[0]);}
    // show() registers its decor before the first layout; filtering by attachment or visibility
    // during the same main-thread callback incorrectly selects the underlying player window.
    private static View top(){List<View> roots=WindowInspector.getGlobalWindowViews();if(!roots.isEmpty())return roots.get(roots.size()-1);throw new AssertionError("No window");}
    private static <T extends View> ArrayList<T> views(View root,Class<T> type){ArrayList<T> out=new ArrayList<>();if(type.isInstance(root))out.add(type.cast(root));if(root instanceof ViewGroup){ViewGroup g=(ViewGroup)root;for(int i=0;i<g.getChildCount();i++)out.addAll(views(g.getChildAt(i),type));}return out;}
    private static Button button(View root,String label){for(Button b:views(root,Button.class))if(b.getText().toString().equals(label))return b;throw new AssertionError("Missing button: "+label);}
    private static Button contains(View root,String label){for(Button b:views(root,Button.class))if(b.getText().toString().contains(label))return b;throw new AssertionError("Missing button containing: "+label);}
    private static RadioButton radio(View root,String label){for(RadioButton b:views(root,RadioButton.class))if(b.getText().toString().equals(label))return b;throw new AssertionError("Missing option: "+label);}
    private static CheckBox songCheck(View root,String title){for(CheckBox b:views(root,CheckBox.class)){CharSequence description=b.getContentDescription();if(description!=null&&t("music060_select_song",title).contentEquals(description))return b;}throw new AssertionError("Missing song checkbox: "+title);}
    private static boolean textExists(View root,String label){for(TextView v:views(root,TextView.class))if(v.getText().toString().equals(label))return true;return false;}
    private static void call(String name){try{Method m=activity.getClass().getSuperclass().getDeclaredMethod(name);m.setAccessible(true);m.invoke(activity);}catch(Exception e){throw new RuntimeException(e);}}
    private static void dismissDialogs(){for(int i=0;i<16;i++){final boolean[] open={false};main(()->open[0]=top()!=activity.getWindow().getDecorView());if(!open[0])return;instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);instrumentation.waitForIdleSync();}throw new AssertionError("Dialog did not close");}
    private static void press(Button b,long at){MotionEvent down=MotionEvent.obtain(at,at,MotionEvent.ACTION_DOWN,8,8,0),up=MotionEvent.obtain(at,at+20,MotionEvent.ACTION_UP,8,8,0);b.dispatchTouchEvent(down);b.dispatchTouchEvent(up);down.recycle();up.recycle();}
    private static void screenshot(String name)throws Exception{instrumentation.waitForIdleSync();Thread.sleep(600);Bitmap image=instrumentation.getUiAutomation().takeScreenshot();if(image==null)throw new AssertionError("Screenshot unavailable: "+name);File directory=new File(activity.getFilesDir(),"ui-checks");directory.mkdirs();try(OutputStream out=new FileOutputStream(new File(directory,name+".png"))){image.compress(Bitmap.CompressFormat.PNG,100,out);}finally{image.recycle();}}
    private static void waitForText(String label)throws Exception{long deadline=SystemClock.uptimeMillis()+10000;while(SystemClock.uptimeMillis()<deadline){final boolean[] found={false};main(()->found[0]=textExists(top(),label));if(found[0]){check(true,"Scope decodes current song waveform");return;}Thread.sleep(50);}throw new AssertionError("Timed out waiting for: "+label);}
    private static void waitForScopeSamples()throws Exception{Field samples=OscilloscopeView.class.getDeclaredField("left");samples.setAccessible(true);long deadline=SystemClock.uptimeMillis()+10000;while(SystemClock.uptimeMillis()<deadline){final boolean[] ready={false};main(()->{try{ArrayList<OscilloscopeView> scopes=views(top(),OscilloscopeView.class);if(!scopes.isEmpty()){float[] values=(float[])samples.get(scopes.get(0));for(float v:values)if(Math.abs(v)>.001f){ready[0]=true;break;}}}catch(Exception e){throw new RuntimeException(e);}});if(ready[0]){check(true,"Scope view receives nonzero PCM samples");return;}Thread.sleep(50);}throw new AssertionError("Scope decoded track but displayed no PCM samples");}
    public static int run(Instrumentation runner,Activity screen)throws Exception {
        count=0;instrumentation=runner;activity=screen;
        SharedPreferences prefs=activity.getSharedPreferences("music_module",0);
        String[] keys={"catalogue","queue","collections","storage_tree_uri"};Map<String,String> saved=new HashMap<>();for(String k:keys)saved.put(k,prefs.contains(k)?prefs.getString(k,""):null);
        MusicLibrary.Track original=MusicLibrary.load(activity).get(0),extra=new MusicLibrary.Track("test:ui-second","UI second song");
        Field field=activity.getClass().getSuperclass().getDeclaredField("service");field.setAccessible(true);PlaybackService service=(PlaybackService)field.get(activity);
        try {
            main(()->{check(button(activity.getWindow().getDecorView(),t("music_tab_playback"))!=null,"Playback bottom tab");button(activity.getWindow().getDecorView(),t("music_tab_settings")).performClick();check(button(activity.getWindow().getDecorView(),t("music_max_volume"))!=null,"Settings bottom tab");check(!textExists(activity.getWindow().getDecorView(),t("music_tap_bpm")),"Playback actions do not clutter settings");button(activity.getWindow().getDecorView(),t("music_bpm_rounding")).performClick();check(radio(top(),t("music_bpm_integer")).isChecked(),"Integer measurement write is default");button(top(),t("music_close")).performClick();button(activity.getWindow().getDecorView(),t("library_my_songs")).performClick();check(button(activity.getWindow().getDecorView(),t("library_playlists"))!=null,"My songs includes playlists");});
            screenshot("my-songs");
            main(()->{button(activity.getWindow().getDecorView(),t("music_tab_playback")).performClick();MusicLibrary.addToQueue(activity,Collections.singleton(extra));call("libraryChanged");View root=activity.getWindow().getDecorView();check(!button(root,t("music060_remove_selected_queue")).isEnabled(),"Queue removal waits for checkbox selection");CheckBox selected=songCheck(root,extra.title);selected.performClick();check(selected.isChecked()&&button(root,t("music060_remove_selected_queue")).isEnabled(),"Persistent checkbox selects a queue song");check(selected.getButtonTintList().getDefaultColor()==org.daidai.modules.shared.Ui.BLUE,"Queue checkboxes retain blue selection color");check(contains(root,extra.title).getCurrentTextColor()!=android.graphics.Color.WHITE,"Checkbox removal does not recolor song row");selected.performClick();check(!button(root,t("music060_remove_selected_queue")).isEnabled(),"Second checkbox tap cancels removal selection");selected.performClick();button(root,t("music060_remove_selected_queue")).performClick();check(MusicLibrary.load(activity).size()==1,"Batch removal changes queue");check(MusicLibrary.find(activity,extra.uri)!=null,"Batch removal preserves library");check(!contains(activity.getWindow().getDecorView(),original.title).performLongClick(),"Queue does not open metadata editing");});
            main(()->{button(activity.getWindow().getDecorView(),t("music_tap_bpm")).performClick();View root=top();Button tap=button(root,t("music_tap_bpm_tap"));long at=SystemClock.uptimeMillis();press(tap,at);press(tap,at+501);press(tap,at+1002);button(root,t("music_tap_bpm_finish")).performClick();check(textExists(root,t("music_tap_bpm_result","119.8")),"BPM measures press-down time");button(root,t("music_bpm_write")).performClick();check(MusicLibrary.find(activity,original.uri).bpmText.equals("120"),"BPM write rounds integer by default");button(root,t("music_tap_bpm_reset")).performClick();check(!button(root,t("music_bpm_write")).isEnabled(),"Reset prevents writing stale BPM");press(tap,at+2000);press(tap,at+3000);button(root,t("music_tap_bpm_finish")).performClick();check(textExists(root,t("music_tap_bpm_result","60.0")),"BPM reset starts fresh");button(root,t("music_close")).performClick();});
            main(()->{prefs.edit().putBoolean("bpm_keep_decimal",true).apply();button(activity.getWindow().getDecorView(),t("music_tap_bpm")).performClick();View root=top();Button tap=button(root,t("music_tap_bpm_tap"));long at=SystemClock.uptimeMillis();press(tap,at);press(tap,at+501);button(root,t("music_tap_bpm_finish")).performClick();button(root,t("music_bpm_write")).performClick();check(MusicLibrary.find(activity,original.uri).bpmText.equals("119.8"),"BPM decimal mode keeps measured decimal");button(root,t("music_close")).performClick();prefs.edit().putBoolean("bpm_keep_decimal",false).apply();});
            final long[][][] exported={null};final float[] speed={0};
            main(()->MusicTrimDialog.open(activity,service,(track,ranges,value,preserve)->{exported[0]=ranges;speed[0]=value;}));
            main(()->{View root=top();ArrayList<EditText> fields=views(root,EditText.class);fields.get(0).setText("0.5");fields.get(1).setText("1.0");button(root,t("music_trim_apply")).performClick();radio(root,t("music_trim_remove")).setChecked(true);fields.get(0).setText("0.6");fields.get(1).setText("0.8");button(root,t("music_trim_apply")).performClick();check(textExists(root,t("music_trim_plan",2,"0.300")+"\n0.500 – 0.600 s\n0.800 – 1.000 s"),"Keep/remove composes original timeline");button(root,t("music_export_wav")).performClick();check(exported[0]!=null&&exported[0].length==2&&exported[0][1][0]==800000,"Export receives joined intervals");check(speed[0]==1f,"Export does not apply preview speed by default");});
            screenshot("playback");
            main(()->button(activity.getWindow().getDecorView(),t("music_tab_settings")).performClick());screenshot("settings");main(()->button(activity.getWindow().getDecorView(),t("music_tab_playback")).performClick());
            return count;
        } finally {
            dismissDialogs();main(()->{SharedPreferences.Editor e=prefs.edit();for(String k:keys){String v=saved.get(k);if(v==null)e.remove(k);else e.putString(k,v);}e.commit();call("libraryChanged");});
        }
    }
}
