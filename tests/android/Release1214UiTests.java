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

/** Verify in-place sky toggles, fixed selection indicators and settings explanations. */
public final class Release1214UiTests extends Instrumentation {
    Activity a;View courseEditor;int checks;final Map<String,Map<String,?>> before=new HashMap<>();
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

    ScrollView scroll;View originalSky;int savedY;final ArrayList<Integer> scrollEvents=new ArrayList<>();
    @Override public void onStart(){Bundle result=new Bundle();int code=Activity.RESULT_OK;
        try{
            for(String group:new String[]{"toolbox","weather","timetable","status_places","status_routes"})before.put(group,new HashMap<>(prefs(group).getAll()));
            prefs("toolbox").edit().putInt("seen_version",Integer.MAX_VALUE).putInt("last_tab",0).putBoolean("tab_show_4",true).putBoolean("tab_show_3",true).putBoolean("astro_align_sky",false).putString("astro_selected","织女一").putInt("astro_coordinates",0).putLong("quake_interval",0).putInt("ui_choice_color",0xffb83232).putInt("ui_button_color",0xff287c60).commit();
            a=startActivitySync(new Intent().setClassName(getTargetContext(),"org.simpleweather.app.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();
            ui(()->{
                Object settings=create("SettingsScreen",new Class[]{a.getClass()},a);
                call(settings,"locationInterval",new Class[]{});View interval=dialog("网络定位间隔");TextView explanation=prefix(interval,"此间隔是向系统请求");RadioButton last=(RadioButton)text(interval,"10 分钟");check(explanation!=null,"Network explanation retained");checkAfter(explanation,last,"Explanation follows all interval options");blue((RadioButton)text(interval,"5 秒（默认）"));tap(interval,"10 秒");check(dialog("网络定位间隔")==interval,"Selection stays on leaf page");blue((RadioButton)text(interval,"10 秒"));tap(interval,"返回");
                call(settings,"satelliteLifecycle",new Class[]{});View satellites=dialog("卫星");Switch toggle=(Switch)text(satellites,"卫星天空图跟随设备转动");check(toggle.getThumbTintList().getDefaultColor()==0xff246bce,"Switch thumb stays blue under green button/red choice theme");toggle.performClick();check(toggle.getThumbTintList().getDefaultColor()==0xff246bce,"Unchecked thumb also blue");tap(satellites,"返回");
                call(settings,"statusConstellations",new Class[]{});View systems=dialog("卫星系统显示");CheckBox cb=firstCheck(systems);blue(cb);cb.performClick();blue(cb);tap(systems,"返回");
                call(settings,"locationProvider",new Class[]{});courseEditor=dialog("定位方式");
            });waitForIdleSync();ui(()->{
                screenshot(courseEditor,"1214-native-choice.png");ListView options=list(courseEditor);View row=options.getChildAt(0);check(row instanceof CheckedTextView,"Platform single-choice row instantiated");check(((CheckedTextView)row).getCheckMarkTintList().getDefaultColor()==0xff246bce,"Platform selected dot stays blue");tap(courseEditor,"返回");
                Object settings=create("SettingsScreen",new Class[]{a.getClass()},a);
                for(String key:new String[]{"weather","astro","status","quake","tab"}){
                    call(settings,"order",new Class[]{String.class},key);View d=topDialog();TextView note=prefix(d,"默认排序为：");check(note!=null,"Default sequence present: "+key);check(lastText((View)note.getParent())==note,"Default sequence is bottom explanatory paragraph: "+key);
                    if(key.equals("status"))check(note.getText().toString().contains("地点、路线绘制、传感器"),"Status default sequence reflects actual default order");
                    if(key.equals("weather")){String initial=note.getText().toString();tap(d,"↓");check(initial.equals(note.getText().toString()),"Default explanation stays fixed when current order changes");blue(firstCheck(d));}
                    checkGap(note,12);tap(d,"返回");
                }
                call(settings,"statusSensors",new Class[]{});View d=topDialog();check(prefix(d,"默认排序为：陀螺仪")!=null,"Sensor default order documented");tap(d,"返回");
                Class<?> gps;try{gps=a.getClassLoader().loadClass("org.simpleweather.app.GpsScreen");}catch(Exception e){throw new RuntimeException(e);}
                for(String type:new String[]{"battery","device"}){String[] labels=(String[])staticField(gps,type.equals("battery")?"BATTERY_LABELS":"DEVICE_LABELS");call(settings,"statusFieldOrder",new Class[]{String.class,String.class,String[].class},type,"QA fields",labels);d=dialog("QA fields");check(prefix(d,"默认排序为：")!=null,"Field default order at bottom: "+type);tap(d,"返回");}
                call(settings,"statusSatelliteSorting",new Class[]{});d=topDialog();check(prefix(d,"默认排序为：中国 BDS、美国 GPS、欧洲 Galileo、俄罗斯 GLONASS、日本 QZSS、印度 NavIC")!=null,"Satellite system default order documented");tap(d,"返回");
                call(settings,"background",new Class[]{});d=dialog("背景");check(prefix(d,"默认亮度为 20")!=null,"Background explanation at bottom");tap(d,"返回");
                call(a,"details",new Class[]{int.class},0);d=a.getWindow().getDecorView();String all=allText(d);check(all.contains("3 至不足 6")&&all.contains("6 至不足 8")&&all.contains("8 至不足 11")&&all.contains("11 及以上"),"UV five categories explained");check(all.contains("SPF 30")&&all.contains("每 2 小时")&&all.contains("不是当前实测值"),"UV protection advice and forecast semantics present");check(all.contains("美国环境保护署")&&all.contains("美国疾病控制与预防中心"),"Official UV references retained");
                call(a,"earthquakeSettings",new Class[]{});View quake=topDialog();tap(quake,"数据源");d=topDialog();check(text(d,"中国地震台网（Wolfx 转发）")!=null&&text(d,"中国地震台网（默认关闭）")==null,"CENC option has no default-off suffix");tap(d,"返回");tap(quake,"返回");
                call(a,"switchTab",new Class[]{int.class},3);
            });waitForIdleSync();ui(()->{
                View root=a.getWindow().getDecorView();TextView link=text(root,"在浏览器中打开 ↗");check(link!=null&&!(link instanceof Button)&&"ui_link_color".equals(link.getTag()),"Browser action is an external link");Bitmap image=Bitmap.createBitmap(link.getWidth(),link.getHeight(),Bitmap.Config.ARGB_8888);link.getBackground().draw(new Canvas(image));check(Color.alpha(image.getPixel(1,1))==0,"Browser link background transparent");image.recycle();
                call(a,"switchTab",new Class[]{int.class},4);
            });waitForIdleSync();ui(()->{
                originalSky=sky(a.getWindow().getDecorView());scroll=(ScrollView)field(a,"pageScroll");scroll.scrollTo(0,360);scroll.setOnScrollChangeListener((v,x,y,ox,oy)->scrollEvents.add(y));
            });waitForIdleSync();
            for(int i=0;i<8;i++){
                final boolean on=i%2==0;ui(()->{savedY=scroll.getScrollY();check(savedY>0,"Sky toggle tested away from page top");scrollEvents.clear();tap(a.getWindow().getDecorView(),on?"对准天空":"停止对准天空");check(sky(a.getWindow().getDecorView())==originalSky,"Sky object retained while toggling");check((Boolean)field(originalSky,"aligned")==on,"Sensor state matches button");check("织女一".equals(field(originalSky,"selected")),"Sky selection retained");if(!on)check(field(originalSky,"sensors")==null,"Disabling releases sensor listener");});waitForIdleSync();SystemClock.sleep(80);ui(()->{check(scroll.getScrollY()==savedY,"Reading position retained after layout");for(int y:scrollEvents)check(y==savedY,"No transient scroll or top flash");});
            }
            ui(()->screenshot(a.getWindow().getDecorView(),"1214-sky.png"));result.putString("stream","\nPASS: "+checks+" 1.2.14 Android checks.\n");
        }catch(Throwable error){code=Activity.RESULT_CANCELED;result.putString("stream","\nFAIL: "+android.util.Log.getStackTraceString(error));}
        finally{if(a!=null)ui(()->a.finish());for(Map.Entry<String,Map<String,?>> g:before.entrySet())restore(prefs(g.getKey()),g.getValue());}finish(code,result);
    }
    void screenshot(View v,String name){try{Bitmap b=Bitmap.createBitmap(v.getWidth(),v.getHeight(),Bitmap.Config.ARGB_8888);v.draw(new Canvas(b));try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(getTargetContext().getExternalFilesDir(null),name))){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();}catch(Exception e){throw new RuntimeException(e);}}
    Object staticField(Class<?> c,String name){try{Field f=c.getDeclaredField(name);f.setAccessible(true);return f.get(null);}catch(Exception e){throw new RuntimeException(e);}}
    View topDialog(){List<View> views=WindowInspector.getGlobalWindowViews();for(int i=views.size()-1;i>=0;i--){View v=views.get(i);if(v!=a.getWindow().getDecorView()&&v.isShown())return v;}throw new AssertionError("No dialog");}
    void blue(CompoundButton b){check(b!=null&&b.getButtonTintList()!=null&&b.getButtonTintList().getDefaultColor()==0xff246bce,"Choice indicator fixed blue");check(b.getCurrentTextColor()==choiceColor(),"Choice text still customizable");}
    void checkAfter(View first,View second,String why){ViewGroup p=(ViewGroup)second.getParent();while(p!=null&&p!=first.getParent()) {second=p;p=(ViewGroup)p.getParent();}check(p!=null&&p.indexOfChild(first)>p.indexOfChild(second),why);}
    void checkGap(View v,int dp){LinearLayout p=(LinearLayout)v.getParent();int index=p.indexOfChild(v);View gap=p.getChildAt(index-1);check(gap.getClass()==View.class&&gap.getLayoutParams().height==Math.round(dp*getTargetContext().getResources().getDisplayMetrics().density),"Paragraph gap is exactly 12 dp");}
    TextView lastText(View root){if(root instanceof ScrollView)return lastText(((ScrollView)root).getChildAt(0));if(root instanceof ViewGroup){ViewGroup g=(ViewGroup)root;for(int i=g.getChildCount()-1;i>=0;i--){TextView v=lastText(g.getChildAt(i));if(v!=null&&!v.getText().toString().equals("返回")&&!v.getText().toString().equals("关闭"))return v;}}return root instanceof TextView?(TextView)root:null;}
    String allText(View v){String s=v instanceof TextView?((TextView)v).getText().toString()+"\n":"";if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)s+=allText(((ViewGroup)v).getChildAt(i));return s;}
    void collectInputs(View v,List<EditText> out){if(v instanceof EditText)out.add((EditText)v);if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)collectInputs(((ViewGroup)v).getChildAt(i),out);}
    int choiceColor(){try{Class<?> c=a.getClassLoader().loadClass("org.simpleweather.app.UiColors");Method m=c.getDeclaredMethod("ink",Context.class,String.class);m.setAccessible(true);return (Integer)m.invoke(null,a,"ui_choice_color");}catch(Exception e){throw new RuntimeException(e);}}
    CheckBox firstCheck(View v){if(v instanceof CheckBox)return (CheckBox)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){CheckBox found=firstCheck(((ViewGroup)v).getChildAt(i));if(found!=null)return found;}return null;}
    Spinner findSpinner(View v){if(v instanceof Spinner)return (Spinner)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){Spinner found=findSpinner(((ViewGroup)v).getChildAt(i));if(found!=null)return found;}return null;}
    EditText findInput(View v){if(v instanceof EditText)return (EditText)v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){EditText input=findInput(g.getChildAt(i));if(input!=null)return input;}}return null;}
    void restore(SharedPreferences p,Map<String,?> values){SharedPreferences.Editor e=p.edit().clear();for(Map.Entry<String,?> x:values.entrySet()){Object v=x.getValue();String k=x.getKey();if(v instanceof Boolean)e.putBoolean(k,(Boolean)v);else if(v instanceof Integer)e.putInt(k,(Integer)v);else if(v instanceof Long)e.putLong(k,(Long)v);else if(v instanceof Float)e.putFloat(k,(Float)v);else if(v instanceof String)e.putString(k,(String)v);else if(v instanceof Set)e.putStringSet(k,(Set<String>)v);}e.commit();}
}
