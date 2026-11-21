package org.simpleweather.qa;

import android.app.*;
import android.content.*;
import android.graphics.Rect;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/** Regression checks for nested message scrolling and explicit camera lifetime. */
public final class Embedded301UiTests extends Instrumentation {
    private Context context;private Activity host,child;private Object apps;private Bundle args;private int checks;
    public void onCreate(Bundle values){super.onCreate(values);args=values;start();}
    private Object field(Object owner,String name)throws Exception{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
    private Object call(Object owner,String name,Class<?>[] signature,Object... values)throws Exception{Method m=owner.getClass().getDeclaredMethod(name,signature);m.setAccessible(true);return m.invoke(owner,values);}
    private void ui(Runnable action){AtomicReference<Throwable> error=new AtomicReference<>();runOnMainSync(()->{try{action.run();}catch(Throwable failure){error.set(failure);}});if(error.get()!=null)throw new RuntimeException(error.get());waitForIdleSync();}
    private void check(boolean pass,String text){if(!pass)throw new AssertionError(text);checks++;}
    private void await(BooleanSupplier condition,String label)throws Exception{long until=SystemClock.uptimeMillis()+8000;while(SystemClock.uptimeMillis()<until){if(condition.getAsBoolean()){checks++;return;}SystemClock.sleep(30);}throw new AssertionError(label);}
    private Activity open(String id)throws Exception{Class<?> runtime=Class.forName("org.simpleweather.app.ModuleRuntime",true,context.getClassLoader());Intent intent=(Intent)runtime.getMethod("pageIntent",Context.class,String.class,String.class).invoke(null,context,id,"main");Activity result=startActivitySync(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();return result;}
    private void install(String id)throws Exception{try(InputStream in=new FileInputStream("/data/local/tmp/internal-apps/"+id+".ddapp")){call(apps,"install",new Class[]{InputStream.class,String.class,String.class},in,id,null);}}
    private void add(Object service,String text){ui(()->{try{call(service,"add",new Class[]{boolean.class,String.class},false,text);}catch(Exception failure){throw new RuntimeException(failure);}});}
    private void messaging()throws Exception{
        child=open("messaging");Object page=field(child,"page");await(()->{try{return field(page,"session")!=null;}catch(Exception failure){return false;}},"messaging service bound");Object service=field(page,"session");
        for(int i=0;i<30;i++)add(service,"Scroll regression message "+i+"\nSecond line "+i);
        ScrollView history=(ScrollView)field(page,"historyScroll");LinearLayout rows=(LinearLayout)field(page,"chat");
        await(()->history.canScrollVertically(-1),"new history follows the latest message");
        ScrollView outer=null;for(ViewParent parent=history.getParent();parent!=null;parent=parent.getParent())if(parent instanceof ScrollView){outer=(ScrollView)parent;break;}
        check(outer!=null,"conversation has an outer connection-form scroll view");final ScrollView form=outer;
        ui(()->{Rect rect=new Rect(0,0,history.getWidth(),history.getHeight());form.offsetDescendantRectToMyCoords(history,rect);form.scrollTo(0,Math.max(0,rect.top-30));history.scrollTo(0,rows.getHeight());});SystemClock.sleep(150);waitForIdleSync();
        int before=history.getScrollY(),formBefore=form.getScrollY();check(before>history.getHeight(),"enough messages exist for a real scroll gesture");
        int[] location=new int[2];ui(()->history.getLocationOnScreen(location));float x=location[0]+history.getWidth()/2f,startY=location[1]+history.getHeight()*.30f,endY=location[1]+history.getHeight()*.75f;
        long down=SystemClock.uptimeMillis();sendPointerSync(MotionEvent.obtain(down,down,MotionEvent.ACTION_DOWN,x,startY,0));
        for(int i=1;i<=10;i++){SystemClock.sleep(16);sendPointerSync(MotionEvent.obtain(down,SystemClock.uptimeMillis(),MotionEvent.ACTION_MOVE,x,startY+(endY-startY)*i/10f,0));}
        sendPointerSync(MotionEvent.obtain(down,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,x,endY,0));SystemClock.sleep(350);waitForIdleSync();
        check(history.getScrollY()<before-20,"drag scrolls older messages inside the conversation");check(Math.abs(form.getScrollY()-formBefore)<=3,"drag does not move the surrounding connection form");
        ui(()->history.scrollTo(0,rows.getHeight()/2));int readingY=history.getScrollY();View first=rows.getChildAt(0);
        ui(()->{try{call(page,"update",new Class[]{});}catch(Exception failure){throw new RuntimeException(failure);}});
        check(rows.getChildAt(0)==first,"connection status refresh keeps existing message views");check(history.getScrollY()==readingY,"connection status refresh keeps the reading position");
        add(service,"Appended while reading older messages");SystemClock.sleep(100);waitForIdleSync();check(Math.abs(history.getScrollY()-readingY)<=3,"receiving a message does not interrupt older history reading");
        ui(()->history.scrollTo(0,rows.getHeight()));add(service,"Follow latest message");SystemClock.sleep(100);waitForIdleSync();check(!history.canScrollVertically(1),"reader already at the end follows a new message");
        for(int i=0;i<80;i++)add(service,"Retention regression "+i);
        check(rows.getChildCount()==100,"retained history remains bounded to 100 messages");
        ui(()->child.finish());child=null;
    }
    private void shell(String command)throws Exception{try(ParcelFileDescriptor fd=getUiAutomation().executeShellCommand(command);InputStream in=new FileInputStream(fd.getFileDescriptor())){while(in.read()!=-1){}}}
    private void camera()throws Exception{
        shell("pm grant "+context.getPackageName()+" android.permission.CAMERA");child=open("colorvision");Object page=field(child,"page");Button toggle=(Button)field(page,"cameraToggle");String start=toggle.getText().toString();
        check(!(Boolean)field(page,"sourceCamera")&&field(page,"camera")==null,"camera is off on a fresh app launch");
        ui(()->{
            toggle.performClick();try{check((Boolean)field(page,"sourceCamera")&&field(page,"camera")!=null,"start control creates a camera source");check(!start.equals(toggle.getText().toString()),"running camera control changes to stop");}catch(Exception failure){throw new RuntimeException(failure);}
            toggle.performClick();
        });
        check(!(Boolean)field(page,"sourceCamera")&&field(page,"camera")==null,"stop control closes and removes the camera");check(start.equals(toggle.getText().toString()),"stopped control changes back to start");
        Class<?> module=page.getClass().getSuperclass();Method dispatch=module.getMethod("dispatch",String.class,Bundle.class);ui(()->{try{dispatch.invoke(page,"pause",null);dispatch.invoke(page,"resume",null);}catch(Exception failure){throw new RuntimeException(failure);}});SystemClock.sleep(200);waitForIdleSync();
        check(!(Boolean)field(page,"sourceCamera")&&field(page,"camera")==null,"pause and resume do not restart an explicitly stopped camera");
        FrameLayout frame=(FrameLayout)field(page,"frame");check(frame.getChildCount()==1,"stopped camera leaves only the image-comparison view");ui(()->child.finish());child=null;
    }
    public void onStart(){Bundle result=new Bundle();try{
        context=getTargetContext();check(Build.HARDWARE.contains("ranchu")||Build.HARDWARE.contains("goldfish"),"isolated emulator required");String lang=args.getString("language","zh-CN");context.getSharedPreferences("toolbox",0).edit().putString("app_language",lang).putInt("seen_version",Integer.MAX_VALUE).commit();host=startActivitySync(new Intent().setClassName(context,"org.simpleweather.app.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();apps=field(host,"apps");install("messaging");install("colorvision");messaging();camera();result.putString("result","PASS: "+checks+" messaging scroll and camera toggle checks ("+lang+")");
    }catch(Throwable failure){StringWriter text=new StringWriter();failure.printStackTrace(new PrintWriter(text));result.putString("result","FAIL: "+text);}finally{if(child!=null)ui(()->child.finish());if(host!=null)ui(()->host.finish());finish(Activity.RESULT_OK,result);}}
}
