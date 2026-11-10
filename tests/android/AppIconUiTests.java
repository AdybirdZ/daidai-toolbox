package org.simpleweather.qa;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.inspector.WindowInspector;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONObject;

/** Real picker/result, crop, storage, configuration and bilingual settings regression. */
public final class AppIconUiTests extends Instrumentation {
    private static final String[] GROUPS={"toolbox","weather","status_places","status_routes","timetable","random_choice","todos"};
    private final Map<String,Map<String,?>> before=new HashMap<>();
    private Activity activity,cropActivity;
    private String language="zh-CN";
    private byte[] originalIcon,originalBackground;
    private int checks;
    private Object field(Object target,String name){try{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}catch(Exception e){throw new RuntimeException(e);}}
    private void field(Object target,String name,Object value){try{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);f.set(target,value);}catch(Exception e){throw new RuntimeException(e);}}
    private Object call(Object target,String name,Class<?>[] types,Object...values){try{Method m=target.getClass().getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(target,values);}catch(Exception e){throw new RuntimeException(e);}}
    private Object staticCall(String type,String name,Class<?>[] types,Object...values){try{Class<?> c=activity.getClassLoader().loadClass("org.simpleweather.app."+type);Method m=c.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(null,values);}catch(Exception e){throw new RuntimeException(e);}}
    private void ui(Runnable work){AtomicReference<Throwable> error=new AtomicReference<>();runOnMainSync(()->{try{work.run();}catch(Throwable e){error.set(e);}});if(error.get()!=null)throw new RuntimeException(error.get());}
    private void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    private SharedPreferences prefs(String group){return getTargetContext().getSharedPreferences(group,0);}
    private String label(String key){return activity.getString(activity.getResources().getIdentifier(key,"string",activity.getPackageName()));}
    private TextView text(View root,String value){if(root instanceof TextView&&value.contentEquals(((TextView)root).getText()))return (TextView)root;if(root instanceof ViewGroup){ViewGroup group=(ViewGroup)root;for(int i=0;i<group.getChildCount();i++){TextView found=text(group.getChildAt(i),value);if(found!=null)return found;}}return null;}
    private View dialog(String title){int id=getTargetContext().getResources().getIdentifier("alertTitle","id","android");for(View root:WindowInspector.getGlobalWindowViews()){View heading=root.findViewById(id);if(root.isShown()&&heading instanceof TextView&&title.contentEquals(((TextView)heading).getText()))return root;}throw new AssertionError("Missing dialog "+title);}
    private void tap(View root,String key){TextView button=text(root,label(key));check(button!=null,"Button exists: "+key);button.performClick();}
    private File icon(){return new File(getTargetContext().getFilesDir(),"launcher-icon.png");}
    private void idleTransfer(Object transfer){for(int n=0;n<200;n++){AtomicReference<Boolean> busy=new AtomicReference<>();ui(()->busy.set((Boolean)field(transfer,"busy")));if(!busy.get())return;SystemClock.sleep(50);}throw new AssertionError("Configuration transfer timed out");}
    @Override public void onCreate(Bundle args){super.onCreate(args);if(args!=null)language=args.getString("language","zh-CN");start();}
    @Override public void onStart(){Bundle result=new Bundle();int status=Activity.RESULT_OK;
        try{
            for(String group:GROUPS)before.put(group,new HashMap<>(prefs(group).getAll()));
            if(icon().exists())originalIcon=Files.readAllBytes(icon().toPath());File background=new File(getTargetContext().getFilesDir(),"background.jpg");if(background.exists())originalBackground=Files.readAllBytes(background.toPath());
            Files.deleteIfExists(icon().toPath());prefs("toolbox").edit().putString("app_language",language).putInt("seen_version",Integer.MAX_VALUE).putInt("last_tab",0).putBoolean("location_continuous",false).putBoolean("status_camera_background",false).commit();
            activity=startActivitySync(new Intent().setClassName(getTargetContext(),"org.simpleweather.app.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();
            ui(()->{
                call(activity,"settings",new Class[]{});tap(dialog(label("action_settings")),"settings_014");tap(dialog(label("settings_014")),"app_icon_title");
                View page=dialog(label("app_icon_title"));check(text(page,label("app_icon_default"))!=null,"Default icon state is visible");
                check(text(page,label("app_icon_explanation").split("\n\n")[0])!=null,"Launcher limitation and import behavior are explained in selected language");
                check(label("app_icon_choose").equals(language.equals("en")?"Choose image":"选择图片"),"Icon resources use selected language");
            });
            File input=new File(getTargetContext().getCacheDir(),"app-icon-input.png");Bitmap source=Bitmap.createBitmap(1000,500,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(source);Paint paint=new Paint();canvas.drawColor(Color.BLUE);paint.setColor(Color.RED);canvas.drawRect(0,0,250,500,paint);paint.setColor(Color.GREEN);canvas.drawRect(750,0,1000,500,paint);try(FileOutputStream out=new FileOutputStream(input)){source.compress(Bitmap.CompressFormat.PNG,100,out);}source.recycle();
            ActivityMonitor cropMonitor=new ActivityMonitor("org.simpleweather.app.AppIconCropActivity",null,false);addMonitor(cropMonitor);
            ActivityMonitor picker=new ActivityMonitor(){@Override public ActivityResult onStartActivity(Intent intent){if(Intent.ACTION_OPEN_DOCUMENT.equals(intent.getAction())){check("image/*".equals(intent.getType())&&intent.hasCategory(Intent.CATEGORY_OPENABLE),"Uses bounded image document selection contract");return new ActivityResult(Activity.RESULT_OK,new Intent().setData(Uri.fromFile(input)));}return null;}};addMonitor(picker);
            ui(()->tap(dialog(label("app_icon_title")),"app_icon_choose"));cropActivity=waitForMonitorWithTimeout(cropMonitor,8000);removeMonitor(cropMonitor);removeMonitor(picker);check(cropActivity!=null,"Selected image opens crop Activity");
            for(int n=0;n<160;n++){AtomicReference<Object> view=new AtomicReference<>();ui(()->view.set(field(cropActivity,"crop")));if(view.get()!=null&&((View)view.get()).getWidth()>0)break;SystemClock.sleep(50);}
            ui(()->{
                Object crop=field(cropActivity,"crop");check(crop!=null&&((View)crop).getWidth()>0,"Crop image is loaded and laid out");
                Bitmap selected=(Bitmap)call(crop,"export",new Class[]{});check(selected.getWidth()==512&&selected.getHeight()==512,"Crop output is a bounded square");
                check(selected.getPixel(20,256)==Color.BLUE&&selected.getPixel(492,256)==Color.BLUE,"Initial square crop selects the source center");selected.recycle();
                call(crop,"setZoom",new Class[]{float.class},2f);check(Math.abs((Float)field(crop,"zoom")-2f)<.001f,"Crop zoom control updates image scale");
                call(crop,"reset",new Class[]{});check((Float)field(crop,"zoom")==1f,"Crop reset restores initial framing");
                tap(cropActivity.getWindow().getDecorView(),"app_icon_crop_use");
            });
            for(int n=0;n<160&&!cropActivity.isFinishing();n++)SystemClock.sleep(50);waitForIdleSync();check(icon().isFile(),"Confirmed crop persists privately");byte[] cropped=Files.readAllBytes(icon().toPath());
            staticCall("AppIconStore","validate",new Class[]{byte[].class},cropped);
            ui(()->{View page=dialog(label("app_icon_title"));check(text(page,label("app_icon_custom"))!=null,"Crop result refreshes icon settings without reopening it");tap(page,"action_back");check(dialog(label("settings_014"))!=null,"Back returns to Global settings");tap(dialog(label("settings_014")),"action_back");tap(dialog(label("action_settings")),"action_close");});
            boolean pinnedBefore=(Boolean)staticCall("AppIconStore","pinned",new Class[]{Context.class},activity);
            File exported=new File(getTargetContext().getCacheDir(),"app-icon-configuration.json");Object exportTransfer=field(activity,"configuration");
            ActivityMonitor output=new ActivityMonitor(){@Override public ActivityResult onStartActivity(Intent intent){if(Intent.ACTION_CREATE_DOCUMENT.equals(intent.getAction()))return new ActivityResult(Activity.RESULT_OK,new Intent().setData(Uri.fromFile(exported)));return null;}};addMonitor(output);ui(()->call(exportTransfer,"exportFile",new Class[]{}));idleTransfer(exportTransfer);removeMonitor(output);
            JSONObject document=new JSONObject(new String(Files.readAllBytes(exported.toPath()),StandardCharsets.UTF_8));check(Arrays.equals(cropped,Base64.getDecoder().decode(document.getString("appIcon"))),"Real configuration export contains exact icon bytes");
            Bitmap alternative=Bitmap.createBitmap(512,512,Bitmap.Config.ARGB_8888);alternative.eraseColor(Color.GREEN);staticCall("AppIconStore","save",new Class[]{Context.class,Bitmap.class},activity,alternative);alternative.recycle();
            importConfiguration(exported);check(Arrays.equals(cropped,Files.readAllBytes(icon().toPath())),"Real configuration import restores custom icon artwork");
            check((Boolean)staticCall("AppIconStore","pinned",new Class[]{Context.class},activity)==pinnedBefore,"Configuration import does not create a desktop shortcut");
            document.remove("appIcon");File legacy=new File(getTargetContext().getCacheDir(),"app-icon-old-configuration.json");Files.write(legacy.toPath(),document.toString().getBytes(StandardCharsets.UTF_8));importConfiguration(legacy);check(Arrays.equals(cropped,Files.readAllBytes(icon().toPath())),"Older configuration without icon preserves current artwork");
            document.put("appIcon",Base64.getEncoder().encodeToString(Files.readAllBytes(input.toPath())));File invalid=new File(getTargetContext().getCacheDir(),"app-icon-invalid-configuration.json");Files.write(invalid.toPath(),document.toString().getBytes(StandardCharsets.UTF_8));Object invalidTransfer=field(activity,"configuration");deliver(invalidTransfer,invalid);idleTransfer(invalidTransfer);check(Arrays.equals(cropped,Files.readAllBytes(icon().toPath())),"Non-square or oversized icon rejected before writes");
            ui(()->{call(activity,"appIconSettings",new Class[]{});View page=dialog(label("app_icon_title"));tap(page,"app_icon_restore");check(!icon().exists(),"Restore removes custom artwork");check(text(page,label("app_icon_default"))!=null,"Restore updates preview state");tap(page,"action_back");});
            check((Boolean)staticCall("AppIconStore","pinned",new Class[]{Context.class},activity)==pinnedBefore,"Restore does not create a new shortcut");
            result.putString("stream","\nPASS: "+checks+" app icon checks ("+language+"). Launcher confirmation and launcher image rendering require device verification.\n");
        }catch(Throwable error){status=Activity.RESULT_CANCELED;result.putString("stream","\nFAIL: "+android.util.Log.getStackTraceString(error));}
        finally{
            if(cropActivity!=null&&!cropActivity.isFinishing())ui(()->cropActivity.finish());if(activity!=null)ui(()->activity.finish());waitForIdleSync();
            for(Map.Entry<String,Map<String,?>> group:before.entrySet())restore(prefs(group.getKey()),group.getValue());
            try{restoreFile(icon(),originalIcon);restoreFile(new File(getTargetContext().getFilesDir(),"background.jpg"),originalBackground);}catch(Exception ignored){}
        }
        finish(status,result);
    }
    private void deliver(Object transfer,File file){ui(()->{field(transfer,"busy",true);call(activity,"onActivityResult",new Class[]{int.class,int.class,Intent.class},71,Activity.RESULT_OK,new Intent().setData(Uri.fromFile(file)));});}
    private void importConfiguration(File file){Object transfer=field(activity,"configuration");deliver(transfer,file);View confirmation=null;for(int n=0;n<160;n++){AtomicReference<View> current=new AtomicReference<>();ui(()->{try{current.set(dialog(label("config_import_title")));}catch(AssertionError ignored){}});if(current.get()!=null){confirmation=current.get();break;}SystemClock.sleep(50);}check(confirmation!=null,"Valid import requests confirmation");ActivityMonitor reload=new ActivityMonitor("org.simpleweather.app.MainActivity",null,false);addMonitor(reload);final View ready=confirmation;ui(()->tap(ready,"config_confirm"));Activity next=waitForMonitorWithTimeout(reload,8000);removeMonitor(reload);check(next!=null,"Configuration import reloads activity");activity=next;waitForIdleSync();}
    private static void restoreFile(File file,byte[] bytes)throws Exception{if(bytes==null)Files.deleteIfExists(file.toPath());else Files.write(file.toPath(),bytes);}
    private static void restore(SharedPreferences prefs,Map<String,?> values){SharedPreferences.Editor editor=prefs.edit().clear();for(Map.Entry<String,?> entry:values.entrySet()){Object value=entry.getValue();String key=entry.getKey();if(value instanceof Boolean)editor.putBoolean(key,(Boolean)value);else if(value instanceof Integer)editor.putInt(key,(Integer)value);else if(value instanceof Long)editor.putLong(key,(Long)value);else if(value instanceof Float)editor.putFloat(key,(Float)value);else if(value instanceof String)editor.putString(key,(String)value);else if(value instanceof Set)editor.putStringSet(key,(Set<String>)value);}editor.commit();}
}
