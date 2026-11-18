package org.simpleweather.qa;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.json.*;

/** Uses the production desktop, folder dialogs, settings and explicit shortcut routing. */
public final class LauncherUiTests extends Instrumentation {
    private Activity activity;private ClassLoader loader;private int checks;private Bundle args;private SharedPreferences prefs;
    private final Map<String,Object> old=new HashMap<>();
    @Override public void onCreate(Bundle a){super.onCreate(a);args=a;start();}
    private Class<?> type(String name)throws Exception{return Class.forName("org.simpleweather.app."+name,true,loader);}
    private Object call(Object o,String name,Class<?>[] types,Object...v)throws Exception{Method m=o.getClass().getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(o,v);}
    private void ui(Runnable r){AtomicReference<Throwable> failure=new AtomicReference<>();runOnMainSync(()->{try{r.run();}catch(Throwable t){failure.set(t);}});if(failure.get()!=null)throw new RuntimeException(failure.get());waitForIdleSync();}
    private void check(boolean b,String label){checks++;if(!b)throw new AssertionError(label);}
    private String text(String key){return activity.getString(activity.getResources().getIdentifier(key,"string",activity.getPackageName()));}
    private void collect(View root,List<View> out){out.add(root);if(root instanceof ViewGroup)for(int n=0;n<((ViewGroup)root).getChildCount();n++)collect(((ViewGroup)root).getChildAt(n),out);}
    private List<View> views(View root){List<View> out=new ArrayList<>();collect(root,out);return out;}
    private boolean contains(View root,String value){for(View view:views(root))if(view instanceof TextView&&value.equals(((TextView)view).getText().toString()))return true;return false;}
    private List<AlertDialog> dialogs(){try{Field f=type("ToolDialog").getDeclaredField("OPEN");f.setAccessible(true);List<AlertDialog> out=new ArrayList<>();for(Object o:new ArrayList<>(((Map<?,?>)f.get(null)).keySet()))if(o instanceof AlertDialog&&((AlertDialog)o).isShowing())out.add((AlertDialog)o);return out;}catch(Exception e){throw new RuntimeException(e);}}
    private View find(String value){for(AlertDialog dialog:dialogs())for(View view:views(dialog.getWindow().getDecorView()))if(view instanceof TextView&&value.equals(((TextView)view).getText().toString()))return view;for(View view:views(activity.getWindow().getDecorView()))if(view instanceof TextView&&value.equals(((TextView)view).getText().toString()))return view;throw new AssertionError("Missing view: "+value);}
    private void click(String key){ui(()->find(text(key)).performClick());}
    private void desktop(){ui(()->{try{call(activity,"switchTab",new Class[]{int.class},-1);}catch(Exception e){throw new RuntimeException(e);}});}
    private String raw(int columns){return "{\"version\":1,\"icon\":72,\"columns\":"+columns+",\"text\":14,\"folders\":[],\"order\":[]}";}
    private JSONObject layout(String key)throws Exception{return new JSONObject(prefs.getString(key,"{}"));}
    private void closeDialogs(){ui(()->{try{type("ToolDialog").getMethod("closeAll").invoke(null);}catch(Exception e){throw new RuntimeException(e);}});}
    @Override public void onStart(){Bundle result=new Bundle();try{
        prefs=getTargetContext().getSharedPreferences("toolbox",0);for(String key:new String[]{"launcher_layout_phone","launcher_layout_tablet","launcher_order"})old.put(key,prefs.getAll().get(key));
        prefs.edit().putString("app_language",args.getString("language","zh-CN")).putInt("seen_version",getTargetContext().getPackageManager().getPackageInfo(getTargetContext().getPackageName(),0).versionCode).putString("launcher_layout_phone",raw(3)).putString("launcher_layout_tablet",raw(5)).commit();
        loader=getTargetContext().getClassLoader();activity=startActivitySync(new Intent().setClassName(getTargetContext(),"org.simpleweather.app.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();desktop();
        boolean tablet=activity.getResources().getConfiguration().smallestScreenWidthDp>=600;String key=tablet?"launcher_layout_tablet":"launcher_layout_phone",other=tablet?"launcher_layout_phone":"launcher_layout_tablet";
        check(contains(activity.getWindow().getDecorView(),text("launcher_edit")),"desktop has edit control");
        click("launcher_edit");check(contains(activity.getWindow().getDecorView(),text("launcher_finish_edit")),"editing toggle updates label");click("launcher_new_folder");
        AlertDialog editor=null;for(AlertDialog d:dialogs())if(contains(d.getWindow().getDecorView(),text("launcher_folder_members")))editor=d;check(editor!=null,"folder editor opens");final AlertDialog edit=editor;
        ui(()->{for(View view:views(edit.getWindow().getDecorView()))if(view instanceof EditText)((EditText)view).setText("观星 Astro");int selected=0;for(View view:views(edit.getWindow().getDecorView()))if(view instanceof CheckBox&&selected++<2)((CheckBox)view).setChecked(true);edit.getButton(AlertDialog.BUTTON_POSITIVE).performClick();});
        JSONObject saved=layout(key);check(saved.getJSONArray("folders").length()==1,"folder persists in current profile");check(layout(other).getJSONArray("folders").length()==0,"other profile remains independent");
        String folderId=saved.getJSONArray("folders").getJSONObject(0).getString("id");check(saved.getJSONArray("folders").getJSONObject(0).getJSONArray("apps").length()==2,"selected apps become folder members");
        check(contains(activity.getWindow().getDecorView(),"观星 Astro"),"new folder visible on desktop");ui(()->((View)find("观星 Astro").getParent()).performClick());
        boolean opened=false;for(AlertDialog d:dialogs())if(contains(d.getWindow().getDecorView(),text("launcher_folder_manage")))opened=true;check(opened,"folder opens installed members");closeDialogs();
        click("launcher_customization");AlertDialog settings=null;for(AlertDialog d:dialogs())if(contains(d.getWindow().getDecorView(),text("launcher_profile_phone")))settings=d;check(settings!=null,"desktop settings grouped");final AlertDialog setting=settings;
        ui(()->{List<SeekBar> sliders=new ArrayList<>();for(View view:views(setting.getWindow().getDecorView()))if(view instanceof SeekBar)sliders.add((SeekBar)view);if(sliders.size()!=3)throw new AssertionError("Expected geometry controls");Bundle progress=new Bundle();progress.putFloat(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE,38f);if(!sliders.get(0).performAccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.getId(),progress))throw new AssertionError("Slider accessibility progress action failed");});
        check(layout(key).getInt("icon")==86,"icon size saved");check(layout(other).getInt("icon")==72,"icon geometry isolated between profiles");closeDialogs();
        Intent shortcut=(Intent)type("LauncherShortcuts").getMethod("intent",Context.class,String.class).invoke(null,activity,"weather");check(shortcut.getComponent().getClassName().equals("org.simpleweather.app.MainActivity"),"shortcut targets explicit host activity");check("weather".equals(shortcut.getStringExtra("open_app_id")),"shortcut retains stable app identity");check(shortcut.getData().toString().equals("daidai-toolbox://application/weather"),"shortcut has distinct app URI");
        Intent invalid=new Intent().putExtra("open_app_id","calculator");boolean handled=(Boolean)type("LauncherScreen").getMethod("openRequested",type("MainActivity"),Intent.class).invoke(null,activity,invalid);check(!handled&&!invalid.hasExtra("open_app_id"),"unknown shortcut consumed without opening cancelled module");
        desktop(); // Long press outside edit mode opens folder management.
        ui(()->{View label=find("观星 Astro");((View)label.getParent()).performLongClick();});click("launcher_folder_delete");
        AlertDialog confirm=null;for(AlertDialog d:dialogs())if(contains(d.getWindow().getDecorView(),text("launcher_folder_delete_note")))confirm=d;check(confirm!=null,"delete folder requires confirmation");final AlertDialog confirmation=confirm;ui(()->confirmation.getButton(AlertDialog.BUTTON_POSITIVE).performClick());
        check(layout(key).getJSONArray("folders").length()==0,"folder deletion removes metadata");JSONArray order=layout(key).getJSONArray("order");boolean absent=true;for(int n=0;n<order.length();n++)if(folderId.equals(order.getString(n)))absent=false;check(absent,"folder deletion restores root placement");
        result.putString("result","PASS: "+checks+" desktop interaction checks ("+args.getString("language","zh-CN")+")");
    }catch(Throwable failure){StringWriter out=new StringWriter();failure.printStackTrace(new PrintWriter(out));result.putString("result","FAIL: "+out);}finally{
        if(activity!=null){closeDialogs();ui(()->activity.finish());}if(prefs!=null){SharedPreferences.Editor restore=prefs.edit();for(Map.Entry<String,Object> e:old.entrySet())if(e.getValue() instanceof String)restore.putString(e.getKey(),(String)e.getValue());else restore.remove(e.getKey());restore.commit();}finish(Activity.RESULT_OK,result);
    }}
}
