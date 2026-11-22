package org.simpleweather.app;

import android.app.*;
import android.content.*;
import android.os.Build;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Settings pages live in the activity view hierarchy, with a normal back stack. */
public final class SettingsNavigation {
    private static final WeakHashMap<Activity,SettingsNavigation> HOSTS=new WeakHashMap<>();
    private final Activity activity;
    private final FrameLayout frame;
    private final ArrayList<SettingsPanel> stack=new ArrayList<>();
    private boolean closing;
    public SettingsNavigation(Activity activity){
        this.activity=activity;frame=new FrameLayout(activity);frame.setBackgroundColor(MainActivity.BG);
        frame.setOnApplyWindowInsetsListener((v,insets)->{
            if(Build.VERSION.SDK_INT>=30){android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());frame.setPadding(bars.left,bars.top,bars.right,Math.max(bars.bottom,insets.getInsets(WindowInsets.Type.ime()).bottom));}
            else frame.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            return insets;
        });
        activity.setContentView(frame);frame.requestApplyInsets();HOSTS.put(activity,this);
    }
    static SettingsNavigation find(Context context){for(int i=0;i<10&&context!=null;i++){if(context instanceof Activity)return HOSTS.get((Activity)context);if(!(context instanceof ContextWrapper))break;Context next=((ContextWrapper)context).getBaseContext();if(next==context)break;context=next;}return null;}
    void push(SettingsPanel panel,View view){
        if(!stack.isEmpty())stack.get(stack.size()-1).pageView().setVisibility(View.GONE);
        stack.add(panel);frame.addView(view,new FrameLayout.LayoutParams(-1,-1));
    }
    void remove(SettingsPanel panel){
        int index=stack.indexOf(panel);if(index<0)return;
        stack.remove(index);frame.removeView(panel.pageView());
        if(!stack.isEmpty())stack.get(stack.size()-1).pageView().setVisibility(View.VISIBLE);
        else if(!closing&&!activity.isFinishing())activity.finish();
    }
    public boolean back(){if(stack.isEmpty())return false;stack.get(stack.size()-1).cancel();return true;}
    public boolean empty(){return stack.isEmpty();}
    public void refresh(){for(SettingsPanel panel:stack)UiColors.decorate(activity,panel.pageView());}
    public void preparingRecreation(){closing=true;}
    public void captureState(android.os.Bundle state){try{org.json.JSONArray rows=new org.json.JSONArray();for(SettingsPanel panel:stack)rows.put(new org.json.JSONObject().put("title",String.valueOf(panel.spec.pageTitle)).put("views",UiSession.viewState(panel.pageView())));String value=rows.toString();byte[] bytes=value.getBytes(java.nio.charset.StandardCharsets.UTF_8);if(bytes.length<=256000){java.io.File directory=new java.io.File(activity.getCacheDir(),"settings-task");if(!directory.isDirectory()&&!directory.mkdirs())return;java.io.File[] old=directory.listFiles();if(old!=null){java.util.Arrays.sort(old,java.util.Comparator.comparingLong(java.io.File::lastModified).reversed());for(int n=0;n<old.length;n++)if(n>=23||System.currentTimeMillis()-old[n].lastModified()>48*60*60*1000L)old[n].delete();}String token=java.util.UUID.randomUUID().toString();java.nio.file.Files.write(new java.io.File(directory,token+".json").toPath(),bytes);state.putString("embedded_settings_pages",token);}}catch(Exception e){android.util.Log.w("Settings","Cannot save settings controls",e);}}
    public void restoreState(android.os.Bundle state,String app){
        if(state==null||!app.equals("music"))return;String token=state.getString("embedded_settings_pages","");if(!token.matches("[0-9a-f-]{36}"))return;final String value;try{java.io.File file=new java.io.File(new java.io.File(activity.getCacheDir(),"settings-task"),token+".json");long age=System.currentTimeMillis()-file.lastModified();if(!file.isFile()||file.length()>256000||age<0||age>48*60*60*1000L)return;value=new String(java.nio.file.Files.readAllBytes(file.toPath()),java.nio.charset.StandardCharsets.UTF_8);}catch(Exception missing){return;}
        frame.post(()->{try{
            org.json.JSONArray rows=new org.json.JSONArray(value);if(rows.length()>3||stack.isEmpty())return;
            UiSession.restoreViews(stack.get(0).pageView(),rows.getJSONObject(0).optJSONArray("views"));
            String[] keys={"music_storage_settings","music_artwork_settings","music060_credits_settings","music070_lyrics_settings","music_max_volume","music_bpm_rounding","music_analysis_settings"};
            Set<String> safe=new HashSet<>();for(String key:keys){int id=activity.getResources().getIdentifier(key,"string","org.simpleweather.module.music");if(id!=0)safe.add(activity.getString(id));}
            for(int i=1;i<rows.length();i++){org.json.JSONObject row=rows.getJSONObject(i);String title=row.optString("title");if(!safe.contains(title))break;View button=matchingButton(stack.get(stack.size()-1).pageView(),title);if(button==null)break;int before=stack.size();button.performClick();if(stack.size()!=before+1)break;UiSession.restoreViews(stack.get(stack.size()-1).pageView(),row.optJSONArray("views"));}
        }catch(Exception e){android.util.Log.w("Settings","Cannot restore settings controls",e);}});
    }
    private View matchingButton(View view,String title){if(view instanceof TextView&&view.isClickable()&&title.contentEquals(((TextView)view).getText()))return view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View found=matchingButton(group.getChildAt(i),title);if(found!=null)return found;}}return null;}
    public void close(){closing=true;for(SettingsPanel panel:new ArrayList<>(stack))panel.dismiss();HOSTS.remove(activity);}
    public static void layoutDesktopBar(LinearLayout bar,View button){
        float density=bar.getResources().getDisplayMetrics().density;
        int side=Math.round(8*density), vertical=Math.round(4*density);
        bar.setPadding(side,vertical,side,vertical);
        bar.setBackgroundColor(0xF2FFFFFF);
        bar.setGravity(Gravity.CENTER);
        TextView label=(TextView)button;
        label.setMinWidth(0);
        button.setMinimumWidth(0);
        label.setTextSize(14);
        button.setPadding(Math.round(8*density),0,Math.round(8*density),0);
        label.setGravity(Gravity.CENTER);
        button.setLayoutParams(new LinearLayout.LayoutParams(Math.round(112*density),Math.round(48*density)));
    }
    public static void desktop(Activity activity){Intent intent=new Intent(activity,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);intent.putExtra("last_tab",-1);activity.startActivity(intent);activity.finish();}
}
