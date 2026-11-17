package org.simpleweather.qa;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/** Exercises actual production signatures, private package storage and child DEX loading. */
public final class ModuleHostUiTests extends Instrumentation {
    private Activity activity;private ClassLoader loader;private int checks;private Bundle args;
    @Override public void onCreate(Bundle arguments){super.onCreate(arguments);args=arguments;start();}
    private Class<?> type(String name)throws Exception{return Class.forName("org.simpleweather.app."+name,true,loader);}
    private Object field(Object owner,String name)throws Exception{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
    private Object invoke(Object owner,String name,Class<?>[] signature,Object...values)throws Exception{Method m=owner.getClass().getDeclaredMethod(name,signature);m.setAccessible(true);return m.invoke(owner,values);}
    private void ui(Runnable task){AtomicReference<Throwable> error=new AtomicReference<>();runOnMainSync(()->{try{task.run();}catch(Throwable t){error.set(t);}});if(error.get()!=null)throw new RuntimeException(error.get());waitForIdleSync();}
    private void check(boolean condition,String label){if(!condition)throw new AssertionError(label);checks++;}
    private void tab(int n){ui(()->{try{invoke(activity,"switchTab",new Class[]{int.class},n);}catch(Exception e){throw new RuntimeException(e);}});}
    private void has(View v,List<String> text){if(v instanceof TextView)text.add(((TextView)v).getText().toString());if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)has(((ViewGroup)v).getChildAt(i),text);}
    private List<String> text(){List<String> out=new ArrayList<>();has(activity.getWindow().getDecorView(),out);return out;}
    private void install(Object apps,String id)throws Exception{File f=new File("/data/local/tmp/module-host-test/"+id+".ddapp");try(InputStream in=new FileInputStream(f)){invoke(apps,"install",new Class[]{InputStream.class,String.class,String.class},in,id,null);}}
    private boolean present(Object apps,String id)throws Exception{return (Boolean)invoke(apps,"installed",new Class[]{String.class},id);}
    @Override public void onStart(){Bundle result=new Bundle();try{
        loader=getTargetContext().getClassLoader();String language=args==null?"zh-CN":args.getString("language","zh-CN");getTargetContext().getSharedPreferences("toolbox",0).edit().putString("app_language",language).putInt("seen_version",53).commit();
        activity=startActivitySync(new Intent().setClassName(getTargetContext(),"org.simpleweather.app.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();Object apps=field(activity,"apps");
        check(present(apps,"weather")&&present(apps,"status"),"two default apps installed");
        for(String name:new String[]{"WeatherScreen","GpsScreen","TodoScreen","ScheduleScreen","AstronomyScreen","EarthquakeScreen","RandomChoiceScreen","MapScreen"}){try{type(name);throw new AssertionError("Screen embedded in host "+name);}catch(ClassNotFoundException expected){checks++;}}
        for(String id:new String[]{"weather","status"}){File file=new File(getTargetContext().getFilesDir(),"apps/"+id+".ddapp");check((file.lastModified()>0),"default executable exists");}
        for(String id:new String[]{"weather","status"}){android.system.StructStat stat=android.system.Os.stat(new File(getTargetContext().getFilesDir(),"apps/"+id+".ddapp").getPath());check((stat.st_mode&0222)==0,"executable is read-only "+id);}
        Class<?> registry=type("ModuleRegistry"),descriptor=type("ModuleRegistry$Module");Method find=registry.getDeclaredMethod("find",String.class),allowed=registry.getDeclaredMethod("allowed",descriptor,String.class,String.class);find.setAccessible(true);allowed.setAccessible(true);
        check((Boolean)allowed.invoke(null,find.invoke(null,"weather-map"),"toolbox","map_product"),"weather-map owns product selection");
        check(!(Boolean)allowed.invoke(null,find.invoke(null,"astronomy"),"toolbox","quake_global_min"),"astronomy cannot change earthquake settings");
        check(!(Boolean)allowed.invoke(null,find.invoke(null,"status"),"toolbox","ui_body_color"),"status cannot overwrite global colors");
        check((Boolean)allowed.invoke(null,find.invoke(null,"status"),"toolbox","location_interval"),"status includes positioning settings");
        tab(0);check(text().size()>8,"weather renders from module");tab(2);check(text().size()>20,"status renders from module");tab(-1);
        // Package integrity and atomic replacement: signed, corrupt and wrong-app payloads.
        File existing=new File(getTargetContext().getFilesDir(),"apps/weather.ddapp");String old=(String)type("AppModules").getMethod("sha",File.class).invoke(null,existing);
        for(String name:new String[]{"corrupt.ddapp","wrong-signer.ddapp","wrong-api.ddapp","zip-slip.ddapp","metadata-only.ddapp"}){boolean rejected=false;try(InputStream in=new FileInputStream("/data/local/tmp/module-host-test/"+name)){invoke(apps,"install",new Class[]{InputStream.class,String.class,String.class},in,"weather",null);}catch(InvocationTargetException expected){rejected=true;}check(rejected,"reject "+name);check(old.equals(type("AppModules").getMethod("sha",File.class).invoke(null,existing)),"original retained after "+name);}
        boolean mismatch=false;try(InputStream in=new FileInputStream("/data/local/tmp/module-host-test/weather.ddapp")){invoke(apps,"install",new Class[]{InputStream.class,String.class,String.class},in,"weather",String.join("",Collections.nCopies(64,"0")));}catch(InvocationTargetException expected){mismatch=true;}check(mismatch,"reject download checksum mismatch");
        SharedPreferences data=getTargetContext().getSharedPreferences("random_choice",0);String before=data.getString("data","");
        for(int n:new int[]{1,3,4,5,6,7}){String id=new String[]{"weather","schedule","status","weather-map","astronomy","earthquake","random","todo"}[n];install(apps,id);check(present(apps,id),"installed "+id);tab(n);check(text().size()>6,"render "+id);Object handle=((Map<?,?>)field(activity,"screens")).get(n);check(handle!=null,"actual screen loaded "+id);Object target=field(handle,"target");check(target.getClass().getClassLoader()!=loader,"separate executable "+id);tab(-1);}
        check(!getTargetContext().getSharedPreferences("todos",0).getAll().containsKey("module_qa"),"no foreign test writes");
        // Uninstall stops executable access but preserves personal data and works with zero apps.
        String randomSaved=data.getString("data","");
        for(String id:new String[]{"weather","status","schedule","weather-map","astronomy","earthquake","random","todo"}){ui(()->{try{invoke(apps,"uninstall",new Class[]{String.class},id);}catch(Exception e){throw new RuntimeException(e);}});while((Boolean)field(apps,"busy"))Thread.sleep(20);check(!present(apps,id),"uninstalled "+id);}
        waitForIdleSync();
        check(text().contains(language.equals("en")?"App store":"应用商店"),"store retained with no apps");check(text().contains(language.equals("en")?"Settings":"设置"),"settings retained with no apps");check(randomSaved.equals(data.getString("data","")),"personal data retained");
        install(apps,"random");tab(6);check(text().size()>6,"reinstall can open");tab(-1);
        install(apps,"weather");install(apps,"status");
        // Receipt of a scheduled tick must not activate a missing application's scheduler.
        ui(()->{try{invoke(apps,"uninstall",new Class[]{String.class},"todo");}catch(Exception e){throw new RuntimeException(e);}});
        type("TodoScheduler").getMethod("refresh",Context.class,boolean.class).invoke(null,getTargetContext(),true);
        check(!present(apps,"todo"),"reminder refresh does not reinstall missing app");
        android.graphics.Bitmap sheet=android.graphics.Bitmap.createBitmap(1240,680,android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas=new android.graphics.Canvas(sheet);canvas.drawColor(0xfff5f7fb);android.graphics.Paint paint=new android.graphics.Paint(3);paint.setColor(0xff20324c);paint.setTextSize(22);paint.setTextAlign(android.graphics.Paint.Align.CENTER);
        String[] iconIds={"weather","status","weather-map","astronomy","earthquake","schedule","random","todo","music","messaging","colorvision","store","settings"};
        for(int n=0;n<iconIds.length;n++){int x=30+n%7*172,y=22+n/7*300;int res=(Integer)type("AppIcons").getMethod("resource",String.class).invoke(null,iconIds[n]);android.graphics.drawable.Drawable d=activity.getDrawable(res);d.setBounds(x,y,x+140,y+140);d.draw(canvas);String title=(String)invoke(apps,"title",new Class[]{String.class},iconIds[n]);canvas.drawText(title,x+70,y+180,paint);}
        try(OutputStream out=new FileOutputStream(new File(getTargetContext().getFilesDir(),"icon-contact-sheet-"+language+".png"))){sheet.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}sheet.recycle();
        result.putString("result","PASS: "+checks+" module-host behavior checks ("+language+")");
    }catch(Throwable e){StringWriter s=new StringWriter();e.printStackTrace(new PrintWriter(s));result.putString("result","FAIL: "+s);}finally{if(activity!=null)ui(()->activity.finish());finish(Activity.RESULT_OK,result);}}
    private static byte[] read(InputStream in)throws Exception{try(InputStream source=in;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] bytes=new byte[8192];int n;while((n=source.read(bytes))!=-1)out.write(bytes,0,n);return out.toByteArray();}}
}
