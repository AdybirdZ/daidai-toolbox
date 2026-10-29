package org.simpleweather.qa;

import android.app.*;
import android.content.*;
import android.graphics.Rect;
import android.location.Location;
import android.os.*;
import android.view.*;
import android.widget.*;
import android.text.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/** Emulator-only integration checks; excluded from the application APK. */
public final class StatusUiTests extends Instrumentation {
    private Activity activity;private Object gps,hardware;private ScrollView scroll;
    private SharedPreferences prefs;private Map<String,?> original;
    private int checks;private int minScroll=Integer.MAX_VALUE,maxScroll;
    private final ArrayList<Integer> pitchUpdates=new ArrayList<>(),sensorUpdates=new ArrayList<>(),positionUpdates=new ArrayList<>();
    private Class<?> type(String name){try{return getTargetContext().getClassLoader().loadClass("org.simpleweather.app."+name);}catch(Exception e){throw new RuntimeException(e);}}
    private Object field(Object o,String name){try{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}catch(Exception e){throw new RuntimeException(e);}}
    private void field(Object o,String name,Object value){try{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.set(o,value);}catch(Exception e){throw new RuntimeException(e);}}
    private Object call(Object o,String name,Class<?>[] params,Object... args){try{Method m=o.getClass().getDeclaredMethod(name,params);m.setAccessible(true);return m.invoke(o,args);}catch(Exception e){throw new RuntimeException(e);}}
    private Object make(String name,Class<?>[] params,Object... args){try{Constructor<?> c=type(name).getDeclaredConstructor(params);c.setAccessible(true);return c.newInstance(args);}catch(Exception e){throw new RuntimeException(e);}}
    private void ui(Runnable r){AtomicReference<Throwable> failure=new AtomicReference<>();runOnMainSync(()->{try{r.run();}catch(Throwable e){failure.set(e);}});if(failure.get()!=null)throw new RuntimeException(failure.get());}
    private void check(boolean ok,String reason){checks++;if(!ok)throw new AssertionError(reason);}
    private void near(double x,double y,String reason){check(Math.abs(x-y)<.05,reason+": "+x+" != "+y);}
    private TextView text(View view,String value){if(view instanceof TextView&&value.contentEquals(((TextView)view).getText()))return (TextView)view;if(view instanceof ViewGroup){ViewGroup g=(ViewGroup)view;for(int n=0;n<g.getChildCount();n++){TextView t=text(g.getChildAt(n),value);if(t!=null)return t;}}return null;}
    @Override public void onCreate(Bundle arguments){super.onCreate(arguments);start();}
    @Override public void onStart(){
        Bundle result=new Bundle();int code=Activity.RESULT_OK;
        try{
            prefs=getTargetContext().getSharedPreferences("toolbox",0);original=new HashMap<>(prefs.getAll());
            SharedPreferences.Editor e=prefs.edit().putInt("seen_version",27).putInt("last_tab",2).putString("status_order","2,0,1,3,8,4,5,6,7");
            for(int n=0;n<9;n++)e.putBoolean("status_show_"+n,n==2);e.commit();
            Intent launch=new Intent().setClassName(getTargetContext(),"org.simpleweather.app.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity=startActivitySync(launch);waitForIdleSync();
            gps=field(activity,"gps");hardware=field(gps,"hardware");scroll=(ScrollView)field(activity,"pageScroll");
            satelliteScroll();zoomCenter();defaultOrder();refreshRates();
            result.putString("stream","\nPASS: "+checks+" native Android checks; satellite updates keep scroll "+minScroll+".."+maxScroll+"; pitch="+pitchUpdates.size()+", sensor="+sensorUpdates.size()+", GPS="+positionUpdates.size()+" updates.\n");
        }catch(Throwable error){code=Activity.RESULT_CANCELED;result.putString("stream","\nFAIL: "+android.util.Log.getStackTraceString(error));}
        finally{if(activity!=null)ui(()->activity.finish());if(original!=null){SharedPreferences.Editor e=prefs.edit().clear();for(Map.Entry<String,?> item:original.entrySet()){String k=item.getKey();Object v=item.getValue();if(v instanceof String)e.putString(k,(String)v);else if(v instanceof Integer)e.putInt(k,(Integer)v);else if(v instanceof Long)e.putLong(k,(Long)v);else if(v instanceof Float)e.putFloat(k,(Float)v);else if(v instanceof Boolean)e.putBoolean(k,(Boolean)v);else if(v instanceof Set)e.putStringSet(k,(Set<String>)v);}e.commit();}}
        finish(code,result);
    }
    private void satellites(int count,int round){
        java.util.List rows=(java.util.List)field(hardware,"satellites");rows.clear();
        for(int i=0;i<count;i++)rows.add(make("StatusHardware$Satellite",new Class[]{int.class,int.class,float.class,float.class,float.class,boolean.class},i%2==0?5:1,i+1,10f+i%75,(float)(i*17%360),20f+(i+round)%25,i%3==0));
        field(hardware,"gnssAt",SystemClock.elapsedRealtime());
    }
    private void satelliteScroll(){
        ui(()->{satellites(70,0);TextView expand=text(activity.getWindow().getDecorView(),"展开 / 收起卫星明细");check(expand!=null,"satellite expand control exists");expand.performClick();expand.requestFocusFromTouch();});
        SystemClock.sleep(200);ui(()->scroll.scrollTo(0,5000));SystemClock.sleep(100);
        ui(()->{check(scroll.getScrollY()==5000,"can scroll far into satellite list");scroll.getViewTreeObserver().addOnPreDrawListener(()->{minScroll=Math.min(minScroll,scroll.getScrollY());maxScroll=Math.max(maxScroll,scroll.getScrollY());return true;});});
        for(int i=0;i<10;i++){final int round=i;ui(()->satellites(60+(round%3)*10,round));SystemClock.sleep(110);ui(()->check(scroll.getScrollY()==5000,"satellite count/signal update keeps reading position"));}
        check(minScroll==5000&&maxScroll==5000,"no intermediate frame flashes to focused expand button");
    }
    private void zoomCenter(){
        ui(()->{
            Object canvas=make("RouteCanvas",new Class[]{type("MainActivity")},activity);
            ArrayList<Object> points=new ArrayList<>();points.add(make("RoutePoint",new Class[]{double.class,double.class,long.class,float.class},120.6,31.3,1L,3f));points.add(make("RoutePoint",new Class[]{double.class,double.class,long.class,float.class},120.61,31.31,2L,3f));
            View view=(View)canvas;view.layout(0,0,400,320);call(canvas,"setRoute",new Class[]{java.util.List.class,int.class,boolean.class},points,2,true);
            long now=SystemClock.uptimeMillis();for(int[] event:new int[][]{{MotionEvent.ACTION_DOWN,200,160},{MotionEvent.ACTION_MOVE,250,200},{MotionEvent.ACTION_UP,250,200}}){MotionEvent m=MotionEvent.obtain(now,now,event[0],event[1],event[2],0);view.onTouchEvent(m);m.recycle();}
            float[] before=(float[])call(canvas,"xy",new Class[]{type("RoutePoint")},points.get(0));call(canvas,"zoom",new Class[]{float.class},2f);float[] after=(float[])call(canvas,"xy",new Class[]{type("RoutePoint")},points.get(0));
            near(after[0]-200,(before[0]-200)*2,"actual Canvas zoom pivots at horizontal viewport center after pan");near(after[1]-160,(before[1]-160)*2,"actual Canvas zoom pivots at vertical viewport center after pan");
        });
    }
    private void defaultOrder(){
        ui(()->{
            SharedPreferences.Editor e=prefs.edit().remove("status_order");for(int n=0;n<9;n++)e.putBoolean("status_show_"+n,true);e.apply();call(activity,"switchTab",new Class[]{int.class},2);
            LinearLayout body=(LinearLayout)field(activity,"body");
            int places=body.indexOfChild((View)text(body,"地点").getParent()),route=body.indexOfChild((View)text(body,"路线绘制").getParent()),sensors=body.indexOfChild((View)text(body,"传感器").getParent());
            check(places>=0&&route>places&&sensors>route,"default rendered order places route between places and sensors");
        });
    }
    private TextWatcher watch(ArrayList<Integer> events){return new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int b,int c){events.add(1);}public void afterTextChanged(Editable e){}};}
    private void refreshRates(){
        ui(()->{
            SharedPreferences.Editor e=prefs.edit();for(int n=0;n<9;n++)e.putBoolean("status_show_"+n,n==0||n==1||n==4);e.apply();call(activity,"switchTab",new Class[]{int.class},2);
            gps=field(activity,"gps");hardware=field(gps,"hardware");call(hardware,"pause",new Class[]{});
            ((TextView)field(gps,"pitchView")).addTextChangedListener(watch(pitchUpdates));
            Map<Integer,TextView> sensors=(Map<Integer,TextView>)field(gps,"sensorViews");sensors.get(4).addTextChangedListener(watch(sensorUpdates));((TextView)field(gps,"longitude")).addTextChangedListener(watch(positionUpdates));
        });
        for(int i=0;i<40;i++){final int index=i;ui(()->{
            field(hardware,"pitch",(double)index);field(hardware,"roll",(double)-index);
            ((Map)field(hardware,"values")).put(4,new float[]{index/100f,0,0});((Map)field(hardware,"sampleAt")).put(4,SystemClock.elapsedRealtime());
            Location l=new Location("gps");l.setLongitude(120+index/1000.0);l.setLatitude(31);l.setTime(System.currentTimeMillis());
            try{Method m=type("LocationService").getDeclaredMethod("record",Context.class,Location.class);m.setAccessible(true);m.invoke(null,activity,l);}catch(Exception e){throw new RuntimeException(e);}
        });SystemClock.sleep(20);}
        check(pitchUpdates.size()>=10,"pitch updates faster than 1 Hz");check(sensorUpdates.size()>=10,"sensor text updates faster than 1 Hz");check(positionUpdates.size()<=3,"GPS display stays independently limited");
    }
}
