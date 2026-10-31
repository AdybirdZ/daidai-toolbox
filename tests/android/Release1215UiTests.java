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
public final class Release1215UiTests extends Instrumentation {
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

    Object hw;Object reader;ScrollView statusScroll;int scrollBefore;
    Object sample(int p){return call(field(reader,"samples"),"get",new Class[]{int.class},p);}
    void waitSamples(){for(int i=0;i<240;i++){if(sample(0)!=null&&sample(1)!=null)return;SystemClock.sleep(50);}throw new AssertionError("Camera did not supply matched samples: "+call(reader,"text",new Class[]{}));}
    void actual(int p,long ms,int iso){Object r=sample(p);check(r!=null,"Profile has real image/result match: "+p);check((Long)field(r,"exposureNanos")==ms*1000000L&&(Integer)field(r,"iso")==iso,"Actual exposure matches profile "+p+": "+field(r,"exposureNanos")+" / "+field(r,"iso"));}
    void collectInputs(View v,ArrayList<EditText> list){if(v instanceof EditText)list.add((EditText)v);if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)collectInputs(((ViewGroup)v).getChildAt(i),list);}
    String all(View v){String out=v instanceof TextView?((TextView)v).getText().toString()+"\n":"";if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)out+=all(((ViewGroup)v).getChildAt(i));return out;}
    View permissionHub;
    void activePackage(String expected){
        for(int i=0;i<60;i++){
            android.view.accessibility.AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();
            if(root!=null&&expected.contentEquals(root.getPackageName())){check(true,"Active package: "+expected);return;}
            SystemClock.sleep(100);
        }
        throw new AssertionError("Did not reach active package "+expected);
    }
    void systemBack(){
        try(android.os.ParcelFileDescriptor fd=getUiAutomation().executeShellCommand("input keyevent 4");java.io.InputStream in=new android.os.ParcelFileDescriptor.AutoCloseInputStream(fd)){while(in.read()!=-1){}}
        catch(Exception e){throw new RuntimeException(e);}
        activePackage(a.getPackageName());waitForIdleSync();
    }
    void systemEntry(String label){
        ui(()->tap(permissionHub,label));activePackage("com.android.settings");systemBack();
        ui(()->check(dialog("权限与系统限制")==permissionHub,"System Back retains permission hub: "+label));
    }
    void permissions(){
        Object settings=create("SettingsScreen",new Class[]{a.getClass()},a);
        final View[] parents=new View[2];
        ui(()->{call(settings,"show",new Class[]{});parents[0]=dialog("设置");tap(parents[0],"全局设置");parents[1]=dialog("全局设置");tap(parents[1],"权限与系统限制");permissionHub=dialog("权限与系统限制");check(text(permissionHub,"打开应用权限设置")!=null&&text(permissionHub,"打开系统定位设置")!=null&&text(permissionHub,"关闭电池优化")!=null,"All three existing entries grouped under Global settings");});
        waitForIdleSync();
        systemEntry("打开应用权限设置");systemEntry("打开系统定位设置");
        ui(()->{tap(permissionHub,"关闭电池优化");View opt=dialog("关闭电池优化");check(prefix(opt,"当前")!=null&&prefix(opt,"当前").getText().toString().contains("电池优化"),"System optimization state shown");check(text(opt,"前往系统设置")!=null,"Explicit user action to system settings");tap(opt,"返回");check(dialog("权限与系统限制")==permissionHub,"Optimization Back returns to permissions");tap(permissionHub,"关闭电池优化");tap(dialog("关闭电池优化"),"前往系统设置");});
        activePackage("com.android.settings");systemBack();
        ui(()->{check(dialog("权限与系统限制")==permissionHub,"System optimization Back returns to permissions");tap(permissionHub,"返回");check(dialog("全局设置")==parents[1],"Permissions Back returns to Global settings");tap(parents[1],"返回");check(dialog("设置")==parents[0],"Global Back returns to Settings");tap(parents[0],"关闭");});
    }
    @Override public void onStart(){Bundle result=new Bundle();int code=Activity.RESULT_OK;
        try{
            for(String group:new String[]{"toolbox","weather","timetable","status_places","status_routes","random_choice"})before.put(group,new HashMap<>(prefs(group).getAll()));
            prefs("toolbox").edit().putInt("seen_version",Integer.MAX_VALUE).remove("status_battery_current_unit").remove("status_battery_charge_unit").putBoolean("status_battery_reverse_current",false).putInt("last_tab",0).putBoolean("status_camera_background",false).putString("status_light_source","sensor").putString("status_light_camera","back").putInt("status_camera_iso",640).putInt("status_camera_exposure_ms",25).remove("status_camera_outdoor_iso").remove("status_camera_outdoor_exposure_ms").putLong("quake_interval",0).putBoolean("status_sensor_show_5",true).commit();
            for(int id=0;id<9;id++)prefs("toolbox").edit().putBoolean("status_show_"+id,id==4||id==7).commit();
            a=startActivitySync(new Intent().setClassName(getTargetContext(),"org.simpleweather.app.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();
            permissions();
            ui(()->{
                Object settings=create("SettingsScreen",new Class[]{a.getClass()},a);
                call(settings,"batterySettings",new Class[]{});View b=dialog("电池");check(text(b,"关闭电池优化")==null,"Battery monitoring settings contain no system restriction entry");tap(b,"读数兼容设置");View compat=dialog("读数兼容设置");check(prefix(compat,"系统原始读数")!=null,"Raw system readings available for diagnostics");tap(compat,"系统电流单位");View cu=dialog("系统电流单位");check(((RadioButton)text(cu,"微安 · μA（默认）")).isChecked(),"Current defaults to Android microampere units");tap(cu,"毫安 · mA");check(dialog("系统电流单位")==cu&&prefs("toolbox").getString("status_battery_current_unit","").equals("mA"),"Current unit changes without closing settings");String[] readings=(String[])call(field(a,"gps"),"batteryReadingText",new Class[]{int.class,int.class},-900,3600);check(readings[0].equals("-900.0 mA")&&readings[1].startsWith("4 mAh"),"Displayed units change independently; charge remains standard");tap(cu,"返回");check(dialog("读数兼容设置")==compat,"Current units Back retains compatibility group");tap(compat,"系统电荷单位");View qu=dialog("系统电荷单位");check(((RadioButton)text(qu,"微安时 · μAh（默认）")).isChecked(),"Charge defaults to Android microampere-hours");tap(qu,"毫安时 · mAh");check(dialog("系统电荷单位")==qu&&prefs("toolbox").getString("status_battery_charge_unit","").equals("mAh"),"Charge unit changes without closing settings");readings=(String[])call(field(a,"gps"),"batteryReadingText",new Class[]{int.class,int.class},-900,3600);check(readings[0].equals("-900.0 mA")&&readings[1].startsWith("3600 mAh"),"Compatibility rendering uses both explicitly selected units");tap(qu,"返回");tap(compat,"返回");check(dialog("电池")==b,"Compatibility Back returns to battery settings");tap(b,"返回");
                call(settings,"cameraSettings",new Class[]{});View camera=dialog("相机设置");check(text(camera,"室内测量参数")!=null&&text(camera,"室外测量参数")!=null,"Both profiles at camera settings root");tap(camera,"室内测量参数");View indoor=dialog("室内测量参数");ArrayList<EditText> inputs=new ArrayList<>();collectInputs(indoor,inputs);check(inputs.size()==2&&inputs.get(0).getText().toString().equals("640")&&inputs.get(1).getText().toString().equals("25"),"Existing user parameters retained as indoor profile");tap(indoor,"恢复默认");check(inputs.get(0).getText().toString().equals("1000")&&inputs.get(1).getText().toString().equals("200"),"Indoor restore fields");check(prefs("toolbox").getInt("status_camera_iso",0)==640,"Restore does not write before Save");tap(indoor,"保存");check(dialog("室内测量参数")==indoor,"Save retains leaf page");tap(indoor,"返回");check(dialog("相机设置")==camera,"Indoor back returns to camera group");
                tap(camera,"室外测量参数");View outdoor=dialog("室外测量参数");inputs.clear();collectInputs(outdoor,inputs);check(inputs.get(0).getText().toString().equals("50")&&inputs.get(1).getText().toString().equals("5"),"Outdoor defaults are ISO 50 and 5 ms");inputs.get(0).setText("0");tap(outdoor,"保存");check(!prefs("toolbox").contains("status_camera_outdoor_iso"),"Reject invalid outdoor ISO without writing");inputs.get(0).setText("100");inputs.get(1).setText("10");tap(outdoor,"保存");check(prefs("toolbox").getInt("status_camera_iso",0)==1000&&prefs("toolbox").getInt("status_camera_outdoor_iso",0)==100,"Editing outdoor leaves indoor unchanged");tap(outdoor,"恢复默认");tap(outdoor,"保存");tap(outdoor,"返回");tap(camera,"返回");
                call(a,"updates",new Class[]{});View history=dialog("版本更新记录");check(prefix(history,"1.2.15 ·")!=null&&prefix(history,"1.0.0 ·")!=null,"New and historical updates retained");tap(history,"关闭");
                call(a,"switchTab",new Class[]{int.class},2);
            });waitForIdleSync();ui(()->{
                check(text(a.getWindow().getDecorView(),"打开应用权限设置")==null&&text(a.getWindow().getDecorView(),"打开系统定位设置")==null,"Status information no longer duplicates permission entries");check(text(a.getWindow().getDecorView(),"暂停环境光采集")==null,"No camera button in system sensor mode");check(!all(a.getWindow().getDecorView()).contains("双参数测量需要"),"No unrelated long camera explanation in sensor mode");
                prefs("toolbox").edit().putString("status_light_source","camera").commit();call(a,"refreshScreen",new Class[]{});
            });waitForIdleSync();ui(()->{
                hw=field(field(a,"gps"),"hardware");reader=field(hw,"cameraReader");check(reader!=null,"Camera begins without a start dialog");statusScroll=(ScrollView)field(a,"pageScroll");statusScroll.scrollTo(0,250);
            });waitSamples();actual(0,200,1000);
            // The emulated camera clamps ISO 50 to its actual minimum of 100.
            Object out=sample(1);check((Long)field(out,"exposureNanos")==5000000L&&(Integer)field(out,"iso")>=50,"Outdoor result reports actual hardware sensitivity");
            String displayed=(String)call(reader,"text",new Class[]{});check(displayed.contains("室内参数：")&&displayed.contains("室外参数：")&&displayed.contains("200.00 ms")&&displayed.contains("5.00 ms"),"Both real profile readings and actual parameters displayed simultaneously");cameraResult=displayed;
            ui(()->{scrollBefore=statusScroll.getScrollY();tap(a.getWindow().getDecorView(),"暂停环境光采集");check((Boolean)call(hw,"cameraPaused",new Class[]{}),"Pause runtime state");check(field(hw,"cameraReader")==null&&!(Boolean)field(reader,"active")&&field(reader,"device")==null&&field(reader,"reader")==null,"Pause closes all camera resources");check(text(a.getWindow().getDecorView(),"恢复环境光采集")!=null&&prefix(a.getWindow().getDecorView(),"环境光采集已暂停")!=null,"Button and reading switch to paused state");});waitForIdleSync();ui(()->{check(statusScroll.getScrollY()==scrollBefore,"Pause does not rebuild page or scroll");call(a,"refreshScreen",new Class[]{});hw=field(field(a,"gps"),"hardware");check(field(hw,"cameraReader")==null,"Re-render does not reopen a manually paused camera");tap(a.getWindow().getDecorView(),"恢复环境光采集");reader=field(hw,"cameraReader");});waitSamples();
            ui(()->{call(a,"switchTab",new Class[]{int.class},0);check(field(hw,"cameraReader")==null,"Leaving Status closes camera by default");call(a,"switchTab",new Class[]{int.class},2);reader=field(hw,"cameraReader");});waitSamples();
            ui(()->{prefs("toolbox").edit().putInt("status_camera_iso",640).putInt("status_camera_exposure_ms",25).putInt("status_camera_outdoor_iso",200).putInt("status_camera_outdoor_exposure_ms",10).commit();call(a,"refreshScreen",new Class[]{});reader=field(hw,"cameraReader");});waitSamples();actual(0,25,640);actual(1,10,200);
            ui(()->{call(hw,"toggleCamera",new Class[]{});prefs("toolbox").edit().putString("status_light_camera","front").commit();call(a,"refreshScreen",new Class[]{});call(hw,"toggleCamera",new Class[]{});reader=field(hw,"cameraReader");});waitSamples();actual(0,25,640);actual(1,10,200);
            ui(()->{field(reader,"manual",false);call(field(reader,"samples"),"clear",new Class[]{});call(reader,"repeat",new Class[]{int.class,boolean.class},(Integer)field(reader,"generation"),false);});
            for(int i=0;i<160&&sample(2)==null;i++)SystemClock.sleep(50);
            check(sample(2)!=null,"Automatic fallback still samples actual frames");String fallback=(String)call(reader,"text",new Class[]{});check(fallback.contains("仅为自动曝光参考")&&!fallback.contains("室内参数：")&&!fallback.contains("室外参数："),"Fallback does not falsely label either manual profile");
            ui(()->{call(hw,"toggleCamera",new Class[]{});check(field(hw,"cameraReader")==null,"Front camera also closes on pause");});
            result.putString("stream","\nPASS: "+checks+" 1.2.15 Android checks.\nActual samples: "+cameraResult+"\n");
        }catch(Throwable error){code=Activity.RESULT_CANCELED;result.putString("stream","\nFAIL: "+android.util.Log.getStackTraceString(error));}
        finally{if(a!=null)ui(()->a.finish());for(Map.Entry<String,Map<String,?>> g:before.entrySet())restore(prefs(g.getKey()),g.getValue());}finish(code,result);
    }
    int choiceColor(){try{Class<?> c=a.getClassLoader().loadClass("org.simpleweather.app.UiColors");Method m=c.getDeclaredMethod("ink",Context.class,String.class);m.setAccessible(true);return (Integer)m.invoke(null,a,"ui_choice_color");}catch(Exception e){throw new RuntimeException(e);}}
    CheckBox firstCheck(View v){if(v instanceof CheckBox)return (CheckBox)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){CheckBox found=firstCheck(((ViewGroup)v).getChildAt(i));if(found!=null)return found;}return null;}
    Spinner findSpinner(View v){if(v instanceof Spinner)return (Spinner)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){Spinner found=findSpinner(((ViewGroup)v).getChildAt(i));if(found!=null)return found;}return null;}
    EditText findInput(View v){if(v instanceof EditText)return (EditText)v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){EditText input=findInput(g.getChildAt(i));if(input!=null)return input;}}return null;}
    void restore(SharedPreferences p,Map<String,?> values){SharedPreferences.Editor e=p.edit().clear();for(Map.Entry<String,?> x:values.entrySet()){Object v=x.getValue();String k=x.getKey();if(v instanceof Boolean)e.putBoolean(k,(Boolean)v);else if(v instanceof Integer)e.putInt(k,(Integer)v);else if(v instanceof Long)e.putLong(k,(Long)v);else if(v instanceof Float)e.putFloat(k,(Float)v);else if(v instanceof String)e.putString(k,(String)v);else if(v instanceof Set)e.putStringSet(k,(Set<String>)v);}e.commit();}
}
