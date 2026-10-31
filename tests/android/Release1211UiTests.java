package org.simpleweather.qa;
import android.app.*;
import android.content.*;
import android.graphics.*;
import android.hardware.*;
import android.os.*;
import android.view.*;
import android.view.inspector.WindowInspector;
import android.widget.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.json.*;

/** Exercise actual dialog navigation, color selection, fixed chart projection and Camera2. */
public final class Release1211UiTests extends Instrumentation {
    Activity a;View courseEditor;int checks;final Map<String,Map<String,?>> before=new HashMap<>();String cameraResult="";
    Object field(Object o,String name){try{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}catch(Exception e){throw new RuntimeException(e);}}
    void field(Object o,String name,Object value){try{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.set(o,value);}catch(Exception e){throw new RuntimeException(e);}}
    Object call(Object o,String name,Class<?>[] types,Object... args){try{Method m=o.getClass().getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(o,args);}catch(Exception e){throw new RuntimeException(e);}}
    void ui(Runnable task){AtomicReference<Throwable> error=new AtomicReference<>();runOnMainSync(()->{try{task.run();}catch(Throwable e){error.set(e);}});if(error.get()!=null)throw new RuntimeException(error.get());}
    void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    SharedPreferences prefs(String name){return getTargetContext().getSharedPreferences(name,0);}
    TextView text(View root,String label){if(root instanceof TextView&&label.contentEquals(((TextView)root).getText()))return (TextView)root;if(root instanceof ViewGroup){ViewGroup g=(ViewGroup)root;for(int i=0;i<g.getChildCount();i++){TextView t=text(g.getChildAt(i),label);if(t!=null)return t;}}return null;}
    TextView prefix(View root,String label){if(root instanceof TextView&&((TextView)root).getText().toString().startsWith(label))return (TextView)root;if(root instanceof ViewGroup){ViewGroup g=(ViewGroup)root;for(int i=0;i<g.getChildCount();i++){TextView t=prefix(g.getChildAt(i),label);if(t!=null)return t;}}return null;}
    View dialog(String title){int id=getTargetContext().getResources().getIdentifier("alertTitle","id","android");for(View root:WindowInspector.getGlobalWindowViews()){View heading=root.findViewById(id);if(root.isShown()&&heading instanceof TextView&&title.contentEquals(((TextView)heading).getText()))return root;}throw new AssertionError("Missing dialog: "+title);}
    View sky(View root){if(root.getClass().getName().endsWith("AstronomyScreen$Sky"))return root;if(root instanceof ViewGroup){ViewGroup g=(ViewGroup)root;for(int i=0;i<g.getChildCount();i++){View v=sky(g.getChildAt(i));if(v!=null)return v;}}return null;}
    void tap(View root,String label){TextView t=text(root,label);if(t!=null){check(true,"Button exists: "+label);t.performClick();return;}ListView list=list(root);if(list!=null)for(int i=0;i<list.getAdapter().getCount();i++)if(label.equals(String.valueOf(list.getAdapter().getItem(i)))){check(true,"List option exists: "+label);list.performItemClick(list.getAdapter().getView(i,null,list),i,list.getAdapter().getItemId(i));return;}throw new AssertionError("Missing button or option: "+label);}
    ListView list(View root){if(root instanceof ListView)return (ListView)root;if(root instanceof ViewGroup){ViewGroup g=(ViewGroup)root;for(int i=0;i<g.getChildCount();i++){ListView v=list(g.getChildAt(i));if(v!=null)return v;}}return null;}
    int value(String method){return (Integer)call(a,method,new Class[]{});}
    Object create(String name,Class<?>[] types,Object... args){try{Class<?> c=a.getClassLoader().loadClass("org.simpleweather.app."+name);Constructor<?> ctor=c.getDeclaredConstructor(types);ctor.setAccessible(true);return ctor.newInstance(args);}catch(Exception e){throw new RuntimeException(e);}}
    void closeDialogs(){for(View root:WindowInspector.getGlobalWindowViews())if(root!=a.getWindow().getDecorView()&&root.isShown()){root.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BACK));root.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BACK));}}
    @Override public void onCreate(Bundle b){super.onCreate(b);start();}
    @Override public void onStart(){Bundle result=new Bundle();int code=Activity.RESULT_OK;Object camera=null;
        try{
            for(String group:new String[]{"toolbox","weather","timetable","status_places","status_routes"})before.put(group,new HashMap<>(prefs(group).getAll()));
            prefs("toolbox").edit().putInt("seen_version",Integer.MAX_VALUE).putInt("last_tab",0).putBoolean("outline",false).putBoolean("status_camera_background",false).putString("status_light_camera","back").putBoolean("tab_show_4",true).putBoolean("astro_align_sky",true).putString("astro_selected","").putInt("astro_coordinates",0).putLong("quake_interval",0).commit();
            a=startActivitySync(new Intent().setClassName(getTargetContext(),"org.simpleweather.app.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();
            ui(()->{
                call(a,"settings",new Class[]{});View root=dialog("设置");check(text(root,"底部栏目")==null&&text(root,"缓存管理")==null&&text(root,"恢复默认设置")==null,"Global utilities removed from root settings");tap(root,"全局设置");View global=dialog("全局设置");check(text(global,"底部栏目")!=null&&text(global,"缓存管理")!=null&&text(global,"恢复默认设置")!=null,"Global utilities available inside global settings");tap(global,"文字与按钮颜色");View colors=dialog("文字与按钮颜色");check(list(colors)!=null&&list(colors).getAdapter().getCount()==8&&text(colors,"主要文字")==null,"Theme entry opens colors directly");tap(colors,"红色");check(value("primaryTextColor")==value("secondaryTextColor")&&value("primaryTextColor")==value("accentColor")&&value("primaryTextColor")==value("buttonPrimaryColor"),"One color controls all text roles and dark surfaces");prefs("toolbox").edit().putInt("button_primary_text_color",Color.BLACK).commit();check(value("buttonPrimaryText")==Color.WHITE,"Primary button text cannot be changed by old preference");check(value("buttonSecondaryColor")!=value("buttonPrimaryColor"),"Pale and dark surfaces differ");tap(colors,"返回");check(text(dialog("全局设置"),"底部栏目")!=null,"Returning from color selection retains parent");
                tap(global,"底部栏目");tap(dialog("底部栏目显示与排序"),"返回");check(text(dialog("全局设置"),"缓存管理")!=null,"Bottom-tab settings return to global page");tap(global,"恢复默认设置");tap(dialog("恢复默认设置"),"取消");check(prefs("toolbox").contains("theme_color"),"Cancel defaults leaves settings unchanged");tap(global,"返回");tap(dialog("设置"),"关闭");
                TextView weatherNote=prefix(a.getWindow().getDecorView(),"数据时刻 ");check(weatherNote!=null&&weatherNote.getCurrentTextColor()==value("secondaryTextColor"),"Weather card explanation follows selected color");call(a,"showPicker",new Class[]{});((EditText)field(a,"search")).setText("通化市");View results=(View)field(a,"results");check(prefix(results,"经度 ")!=null&&prefix(results,"经度 ").getText().toString().contains("纬度 "),"Search result displays longitude before latitude");check(prefix(results,"经度 ").getText().toString().matches(".*[EW].*[NS]"),"Search coordinates contain hemisphere markers");
                call(a,"switchTab",new Class[]{int.class},1);Object schedule=field(a,"schedule");call(schedule,"addCourse",new Class[]{});View edit=dialog("新增课程");TextView picker=prefix(edit,"课程颜色 · ");check(picker!=null,"Course editor offers color picker instead of fixed index spinner");picker.performClick();tap(dialog("课程颜色"),"自定义十六进制颜色");courseEditor=edit;});waitForIdleSync();ui(()->{View edit=courseEditor;View custom=dialog("输入 #RRGGBB 颜色");EditText input=findInput(custom);input.setText("#123456");tap(custom,"保存");check(prefix(edit,"课程颜色 · #123456")!=null,"Custom course color selected without closing course editor");tap(dialog("课程颜色"),"返回");tap(edit,"取消");
                call(a,"switchTab",new Class[]{int.class},4);
            });waitForIdleSync();
            ui(()->{
                View sky=sky(a.getWindow().getDecorView());check(sky!=null&&sky.getWidth()==sky.getHeight(),"Actual sky chart remains square");SensorManager sensors=(SensorManager)field(sky,"sensors");if(sensors!=null)sensors.unregisterListener((SensorEventListener)sky);
                float[] before=(float[])call(sky,"xy",new Class[]{float.class,float.class,float.class,double.class,double.class,double.class,double.class},100f,100f,80f,0.,0.,30.,100.);
                float[] r={1,0,0,0,0,-1,0,1,0};call(sky,"updatePointing",new Class[]{float[].class},r);
                float[] after=(float[])call(sky,"xy",new Class[]{float.class,float.class,float.class,double.class,double.class,double.class,double.class},100f,100f,80f,0.,0.,30.,100.);check(Arrays.equals(before,after),"Sensor changes do not move star projection");
                field(sky,"aimAltitude",30.);field(sky,"aimAzimuth",100.);Bitmap bitmap=Bitmap.createBitmap(sky.getWidth(),sky.getHeight(),Bitmap.Config.ARGB_8888);sky.draw(new Canvas(bitmap));float radius=(Float)call(sky,"chartRadius",new Class[]{});int x=Math.round(sky.getWidth()/2f-(float)(2./3*Math.sin(Math.toRadians(100)))*radius),y=Math.round(sky.getHeight()/2f-(float)(2./3*Math.cos(Math.toRadians(100)))*radius);check(bitmap.getPixel(x,y)==0xFFE53935,"Red marker rendered at azimuth 100 altitude 30");check(bitmap.getPixel(sky.getWidth()/2,sky.getHeight()/2)!=0xFFE53935,"Red marker no longer fixed at chart center");bitmap.recycle();
                prefs("toolbox").edit().putInt("astro_coordinates",1).commit();call(a,"refreshScreen",new Class[]{});View polar=sky(a.getWindow().getDecorView());check(!(Boolean)field(polar,"aligned")&&field(polar,"sensors")==null,"Equatorial mode does not receive pointing sensors");
                prefs("toolbox").edit().putInt("astro_coordinates",2).commit();call(a,"refreshScreen",new Class[]{});check((Boolean)field(sky(a.getWindow().getDecorView()),"aligned"),"No-grid mode supports pointing");
                call(a,"switchTab",new Class[]{int.class},0);
            });
            AtomicReference<Object> ref=new AtomicReference<>();ui(()->{ref.set(create("CameraLightReader",new Class[]{a.getClass()},a));check((Boolean)call(ref.get(),"start",new Class[]{}),"Camera2 opens selected rear camera");});camera=ref.get();
            for(int i=0;i<160&&((Float)field(camera,"sample")<0);i++)SystemClock.sleep(50);
            check((Float)field(camera,"sample")>=0,"Actual Camera2 image stream supplies luminance samples");
            for(int i=0;i<80&&(Integer)field(camera,"exposureMode")==0;i++)SystemClock.sleep(50);
            int mode=(Integer)field(camera,"exposureMode");check(mode>=1&&mode<=3,"Actual exposure status is reported");long time=(Long)field(camera,"exposureNanos");int iso=(Integer)field(camera,"sensitivity");SystemClock.sleep(600);
            if(mode==1||mode==2){check(time==(Long)field(camera,"exposureNanos")&&iso==(Integer)field(camera,"sensitivity"),"Exposure time and sensitivity remain fixed across image frames");}
            cameraResult="rear: "+(String)call(camera,"text",new Class[]{});Object toStop=camera;ui(()->call(toStop,"stop",new Class[]{}));check(!(Boolean)field(camera,"active")&&field(camera,"device")==null&&field(camera,"reader")==null,"Camera stops and releases capture resources");
            prefs("toolbox").edit().putString("status_light_camera","front").commit();ui(()->check((Boolean)call(toStop,"start",new Class[]{}),"Front camera starts without preview window"));for(int i=0;i<160&&(Float)field(camera,"sample")<0;i++)SystemClock.sleep(50);check((Float)field(camera,"sample")>=0,"Front camera supplies actual luminance frames");cameraResult+="; front: "+call(camera,"text",new Class[]{});ui(()->call(toStop,"stop",new Class[]{}));
            result.putString("stream","\nPASS: "+checks+" 1.2.11 Android checks.\nCamera: "+cameraResult+"\n");
        }catch(Throwable error){code=Activity.RESULT_CANCELED;result.putString("stream","\nFAIL: "+android.util.Log.getStackTraceString(error));}
        finally{Object c=camera;if(c!=null)ui(()->call(c,"stop",new Class[]{}));if(a!=null)ui(()->a.finish());for(Map.Entry<String,Map<String,?>> group:before.entrySet())restore(prefs(group.getKey()),group.getValue());}
        finish(code,result);
    }
    EditText findInput(View v){if(v instanceof EditText)return (EditText)v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){EditText input=findInput(g.getChildAt(i));if(input!=null)return input;}}return null;}
    void restore(SharedPreferences p,Map<String,?> values){SharedPreferences.Editor e=p.edit().clear();for(Map.Entry<String,?> x:values.entrySet()){Object v=x.getValue();String k=x.getKey();if(v instanceof Boolean)e.putBoolean(k,(Boolean)v);else if(v instanceof Integer)e.putInt(k,(Integer)v);else if(v instanceof Long)e.putLong(k,(Long)v);else if(v instanceof Float)e.putFloat(k,(Float)v);else if(v instanceof String)e.putString(k,(String)v);else if(v instanceof Set)e.putStringSet(k,(Set<String>)v);}e.commit();}
}
