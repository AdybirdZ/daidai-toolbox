package org.simpleweather.qa;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.LauncherApps;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/** Isolated emulator only: real launcher cancel, confirmation, update and restoration. */
public final class AppIconPinUiTests extends Instrumentation {
    private Activity activity;
    private int checks;
    private byte[] beforeImage;
    private Map<String,?> beforePrefs;
    private boolean cleanupAllowed;
    private String language="zh-CN";
    private final String id="custom-launcher-icon";
    private void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    private void ui(Runnable work){AtomicReference<Throwable> error=new AtomicReference<>();runOnMainSync(()->{try{work.run();}catch(Throwable e){error.set(e);}});if(error.get()!=null)throw new RuntimeException(error.get());}
    private Object call(String method,Class<?>[] types,Object...args){try{Class<?> type=activity.getClassLoader().loadClass("org.simpleweather.app.AppIconStore");Method m=type.getDeclaredMethod(method,types);m.setAccessible(true);return m.invoke(null,args);}catch(Exception e){throw new RuntimeException(e);}}
    private SharedPreferences prefs(){return getTargetContext().getSharedPreferences("toolbox",0);}
    private File image(){return new File(getTargetContext().getFilesDir(),"launcher-icon.png");}
    private ShortcutManager manager(){return getTargetContext().getSystemService(ShortcutManager.class);}
    private ShortcutInfo pinned(){for(ShortcutInfo shortcut:manager().getPinnedShortcuts())if(id.equals(shortcut.getId()))return shortcut;return null;}
    private void launch(){activity=startActivitySync(new Intent().setClassName(getTargetContext(),"org.simpleweather.app.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP));waitForIdleSync();}
    private void artwork(int color){Bitmap bitmap=Bitmap.createBitmap(512,512,Bitmap.Config.ARGB_8888);bitmap.eraseColor(color);call("save",new Class[]{Context.class,Bitmap.class},activity,bitmap);bitmap.recycle();}
    private String apply(){AtomicReference<String> result=new AtomicReference<>();ui(()->result.set(call("apply",new Class[]{Context.class},activity).toString()));return result.get();}
    private AccessibilityNodeInfo button(AccessibilityNodeInfo root,String...labels){
        if(root==null)return null;CharSequence text=root.getText();if(text!=null)for(String label:labels)if(label.equalsIgnoreCase(text.toString().trim()))return root;
        for(int n=0;n<root.getChildCount();n++){AccessibilityNodeInfo found=button(root.getChild(n),labels);if(found!=null)return found;}return null;
    }
    private void launcherButton(String...labels){
        UiAutomation automation=getUiAutomation();
        for(int n=0;n<200;n++){
            AccessibilityNodeInfo root=automation.getRootInActiveWindow();
            if(root!=null&&root.getPackageName()!=null&&!getTargetContext().getPackageName().contentEquals(root.getPackageName())){
                AccessibilityNodeInfo node=button(root,labels);if(node!=null){
                    AccessibilityNodeInfo action=node;while(action!=null&&!action.isClickable())action=action.getParent();
                    if(action!=null&&action.performAction(AccessibilityNodeInfo.ACTION_CLICK))return;
                    Rect bounds=new Rect();node.getBoundsInScreen(bounds);long time=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(time,time,MotionEvent.ACTION_DOWN,bounds.centerX(),bounds.centerY(),0),up=MotionEvent.obtain(time,time+60,MotionEvent.ACTION_UP,bounds.centerX(),bounds.centerY(),0);automation.injectInputEvent(down,true);automation.injectInputEvent(up,true);down.recycle();up.recycle();return;
                }
            }
            SystemClock.sleep(50);
        }
        throw new AssertionError("Launcher confirmation did not expose the expected button");
    }
    private void awaitPinned(){for(int n=0;n<160;n++){if(pinned()!=null)return;SystemClock.sleep(50);}throw new AssertionError("Launcher did not pin shortcut after confirmation");}
    private Bitmap publishedIcon(){
        getUiAutomation().adoptShellPermissionIdentity();
        try{
            LauncherApps launcher=getTargetContext().getSystemService(LauncherApps.class);check(launcher.hasShortcutHostPermission(),"Isolated shell can inspect launcher shortcut artwork");
            Drawable icon=launcher.getShortcutIconDrawable(pinned(),160);check(icon!=null,"Published shortcut exposes actual system icon artwork");
            Bitmap bitmap=Bitmap.createBitmap(256,256,Bitmap.Config.ARGB_8888);icon.setBounds(0,0,256,256);icon.draw(new Canvas(bitmap));return bitmap;
        }finally{getUiAutomation().dropShellPermissionIdentity();}
    }
    private long hash(Bitmap bitmap){long hash=0;for(int y=32;y<224;y+=4)for(int x=32;x<224;x+=4)hash=hash*31+bitmap.getPixel(x,y);return hash;}
    private int count(Bitmap bitmap,boolean red){int count=0;for(int y=32;y<224;y++)for(int x=32;x<224;x++){int color=bitmap.getPixel(x,y);if(Color.alpha(color)>200&&(red?Color.red(color)>180&&Color.green(color)<80:Color.green(color)>180&&Color.red(color)<80))count++;}return count;}
    @Override public void onCreate(Bundle args){super.onCreate(args);if(args!=null)language=args.getString("language","zh-CN");start();}
    @Override public void onStart(){Bundle result=new Bundle();int status=Activity.RESULT_OK;
        try{
            beforePrefs=new HashMap<>(prefs().getAll());if(image().exists())beforeImage=Files.readAllBytes(image().toPath());
            check(manager()!=null&&manager().isRequestPinShortcutSupported(),"Isolated launcher supports pinned shortcuts");
            check(manager().getPinnedShortcuts().isEmpty()&&manager().getDynamicShortcuts().isEmpty()&&manager().getManifestShortcuts().isEmpty(),"Launcher test starts without existing app shortcuts");cleanupAllowed=true;
            prefs().edit().putString("app_language",language).putInt("seen_version",Integer.MAX_VALUE).putInt("last_tab",0).putBoolean("location_continuous",false).commit();launch();artwork(Color.RED);
            check(apply().equals("REQUESTED"),"Accepted launcher request is reported as requested, not confirmed");
            check(pinned()==null,"Pin request alone is not treated as successful installation");
            launcherButton("Cancel","取消");SystemClock.sleep(500);check(pinned()==null,"Cancelling launcher confirmation creates no shortcut");
            launch();check(apply().equals("REQUESTED"),"A cancelled request can be submitted again");launcherButton("Add","Add to home screen","Add automatically","添加","添加到主屏幕","自动添加");awaitPinned();
            ShortcutInfo first=pinned();check(first.isPinned()&&id.equals(first.getId()),"Launcher confirmation creates the stable shortcut ID");Bitmap red=publishedIcon();long redHash=hash(red);check(count(red,true)>15000,"System shortcut icon contains selected red artwork");red.recycle();
            launch();artwork(Color.GREEN);check(apply().equals("UPDATED"),"Existing pinned shortcut updates without another pin request");
            check(manager().getPinnedShortcuts().size()==1&&pinned().getId().equals(first.getId()),"Updating reuses one pinned shortcut, without duplication");Bitmap green=publishedIcon();long greenHash=hash(green);check(greenHash!=redHash&&count(green,false)>15000,"Published system icon changes to new green artwork");green.recycle();
            AtomicReference<Boolean> restored=new AtomicReference<>();ui(()->restored.set((Boolean)call("reset",new Class[]{Context.class},activity)));check(restored.get()&&!image().exists(),"Default restoration removes custom image and updates pinned shortcut");
            check(pinned()!=null&&id.equals(pinned().getId())&&manager().getPinnedShortcuts().size()==1,"Restoration retains the same shortcut ID");Bitmap original=publishedIcon();check(hash(original)!=greenHash&&hash(original)!=redHash,"Restoration changes the published system icon to default artwork");original.recycle();
            result.putString("stream","\nPASS: "+checks+" real launcher pin/cancel/update/restore checks ("+language+").\n");
        }catch(Throwable error){status=Activity.RESULT_CANCELED;result.putString("stream","\nFAIL: "+android.util.Log.getStackTraceString(error));}
        finally{
            if(activity!=null)ui(()->activity.finish());waitForIdleSync();
            if(cleanupAllowed){try(ParcelFileDescriptor fd=getUiAutomation().executeShellCommand("cmd shortcut clear-shortcuts --user 0 org.simpleweather.app");FileInputStream in=new FileInputStream(fd.getFileDescriptor())){byte[] buffer=new byte[4096];while(in.read(buffer)!=-1){}}catch(Exception ignored){}}
            if(beforePrefs!=null)restore(prefs(),beforePrefs);try{if(beforeImage==null)Files.deleteIfExists(image().toPath());else Files.write(image().toPath(),beforeImage);}catch(Exception ignored){}
        }
        finish(status,result);
    }
    private static void restore(SharedPreferences prefs,Map<String,?> values){SharedPreferences.Editor editor=prefs.edit().clear();for(Map.Entry<String,?> entry:values.entrySet()){Object value=entry.getValue();String key=entry.getKey();if(value instanceof Boolean)editor.putBoolean(key,(Boolean)value);else if(value instanceof Integer)editor.putInt(key,(Integer)value);else if(value instanceof Long)editor.putLong(key,(Long)value);else if(value instanceof Float)editor.putFloat(key,(Float)value);else if(value instanceof String)editor.putString(key,(String)value);else if(value instanceof Set)editor.putStringSet(key,(Set<String>)value);}editor.commit();}
}
