package org.simpleweather.qa;

import android.app.*;
import android.content.*;
import android.graphics.Rect;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.inspector.WindowInspector;
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
            SharedPreferences.Editor e=prefs.edit().putInt("seen_version",Integer.MAX_VALUE).putInt("last_tab",2).putString("status_order","2,0,1,3,8,4,5,6,7");
            for(int n=0;n<9;n++)e.putBoolean("status_show_"+n,n==2);e.commit();
            Intent launch=new Intent().setClassName(getTargetContext(),"org.simpleweather.app.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity=startActivitySync(launch);waitForIdleSync();
            gps=field(activity,"gps");hardware=field(gps,"hardware");scroll=(ScrollView)field(activity,"pageScroll");
            satelliteScroll();zoomCenter();defaultOrder();refreshRates();playbackFollowing();placeMarkers();locationControls();pointDialogs();
            result.putString("stream","\nPASS: "+checks+" native Android checks; satellite updates keep scroll "+minScroll+".."+maxScroll+"; pitch="+pitchUpdates.size()+", sensor="+sensorUpdates.size()+", GPS="+positionUpdates.size()+" updates.\n");
        }catch(Throwable error){code=Activity.RESULT_CANCELED;result.putString("stream","\nFAIL: "+android.util.Log.getStackTraceString(error));}
        finally{if(activity!=null)ui(()->{call(gps,"stopLocation",new Class[]{});activity.finish();});if(original!=null){SharedPreferences.Editor e=prefs.edit().clear();for(Map.Entry<String,?> item:original.entrySet()){String k=item.getKey();Object v=item.getValue();if(v instanceof String)e.putString(k,(String)v);else if(v instanceof Integer)e.putInt(k,(Integer)v);else if(v instanceof Long)e.putLong(k,(Long)v);else if(v instanceof Float)e.putFloat(k,(Float)v);else if(v instanceof Boolean)e.putBoolean(k,(Boolean)v);else if(v instanceof Set)e.putStringSet(k,(Set<String>)v);}e.commit();}}
        finish(code,result);
    }
    private void satellites(int count,int round){
        java.util.List rows=(java.util.List)field(hardware,"satellites");rows.clear();
        for(int i=0;i<count;i++)rows.add(make("StatusHardware$Satellite",new Class[]{int.class,int.class,float.class,float.class,float.class,boolean.class},i%2==0?5:1,i+1,10f+i%75,(float)(i*17%360),20f+(i+round)%25,i%3==0));
        field(hardware,"gnssAt",SystemClock.elapsedRealtime());
    }
    private void satelliteScroll(){
        ui(()->{satellites(70,0);TextView expand=text(activity.getWindow().getDecorView(),"展开或收起卫星明细");check(expand!=null,"satellite expand control exists");expand.performClick();expand.requestFocusFromTouch();});
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
    private void draw(View view){view.draw(new Canvas(Bitmap.createBitmap(view.getWidth(),view.getHeight(),Bitmap.Config.ARGB_8888)));}
    private ArrayList<Object> routePoints(){ArrayList<Object> rows=new ArrayList<>();for(int i=0;i<4;i++)rows.add(make("RoutePoint",new Class[]{double.class,double.class,long.class,float.class},120+i*.001,31+i*.001,1000L+i*5000,3f));return rows;}
    private void playbackFollowing(){ui(()->{
        Object canvas=make("RouteCanvas",new Class[]{type("MainActivity")},activity);View view=(View)canvas;view.layout(0,0,400,320);ArrayList<Object> points=routePoints();
        call(canvas,"setRoute",new Class[]{List.class,int.class,boolean.class},points,1,true);call(canvas,"playback",new Class[]{boolean.class},true);call(canvas,"centerSelf",new Class[]{});
        Location far=new Location("gps");far.setLongitude(20);far.setLatitude(-20);far.setTime(System.currentTimeMillis());try{Method m=type("LocationService").getDeclaredMethod("record",Context.class,Location.class);m.setAccessible(true);m.invoke(null,activity,far);}catch(Exception e){throw new RuntimeException(e);}
        for(int i=0;i<4;i++){call(canvas,"showThrough",new Class[]{int.class},i);draw(view);float[] xy=(float[])call(canvas,"xy",new Class[]{type("RoutePoint")},points.get(i));near(xy[0],200,"replay orange point stays at horizontal center");near(xy[1],160,"replay orange point stays at vertical center");}
        call(canvas,"zoom",new Class[]{float.class},4f);draw(view);float[] xy=(float[])call(canvas,"xy",new Class[]{type("RoutePoint")},points.get(3));near(xy[0],200,"zoom retains replay following");
        long now=SystemClock.uptimeMillis();for(int[] e:new int[][]{{0,200,160},{2,250,180},{1,250,180}}){MotionEvent event=MotionEvent.obtain(now,now,e[0],e[1],e[2],0);view.onTouchEvent(event);event.recycle();}
        check(!(Boolean)call(canvas,"following",new Class[]{}),"manual drag cancels route following");
    });}
    private void placeMarkers(){
        SharedPreferences store=getTargetContext().getSharedPreferences("status_places",0);String old=store.getString("places","[]"),selected=store.getString("selected","");
        try{ui(()->{
            store.edit().putString("places","[{\"id\":\"a\",\"name\":\"红点\",\"lon\":20.001,\"lat\":-20,\"color\":-4704714,\"visible\":true},{\"id\":\"b\",\"name\":\"绿点\",\"lon\":20,\"lat\":-19.999,\"color\":-14123936,\"visible\":true},{\"id\":\"c\",\"name\":\"隐藏点\",\"lon\":20.002,\"lat\":-20,\"visible\":false}]").putString("selected","a").commit();
            Object places=field(gps,"places"),chart=make("StatusViews$Treasure",new Class[]{type("MainActivity"),type("StatusHardware"),type("StatusPlaces")},activity,hardware,places);View view=(View)chart;view.layout(0,0,600,600);draw(view);
            Map<String,float[]> hits=(Map)field(chart,"hits");check(hits.size()==2&&hits.containsKey("a")&&hits.containsKey("b")&&!hits.containsKey("c"),"multiple visible places plot together; hidden target excluded");
            call(places,"visibility",new Class[]{String.class,boolean.class},"c",true);draw(view);check(((Map)field(chart,"hits")).size()==3,"visibility setting immediately adds marker");
            call(places,"visibility",new Class[]{String.class,boolean.class},"a",false);draw(view);check(!((Map)field(chart,"hits")).containsKey("a"),"selected target still obeys hidden setting");
        });}finally{store.edit().putString("places",old).putString("selected",selected).commit();}
    }
    private void locationControls(){
        getUiAutomation().grantRuntimePermission(getTargetContext().getPackageName(),"android.permission.ACCESS_FINE_LOCATION");getUiAutomation().grantRuntimePermission(getTargetContext().getPackageName(),"android.permission.ACCESS_COARSE_LOCATION");
        ui(()->{prefs.edit().putString("location_provider","gps").putBoolean("location_continuous",true).putBoolean("location_notice_asked",true).apply();for(int id:new int[]{0,1,2,8})prefs.edit().putBoolean("status_show_"+id,true).apply();call(activity,"switchTab",new Class[]{int.class},2);gps=field(activity,"gps");hardware=field(gps,"hardware");call(gps,"stopLocation",new Class[]{});
            LinearLayout body=(LinearLayout)field(activity,"body");check(text(body,"开始定位")!=null&&text(body,"停止定位")==null,"location uses one start toggle");check(text(body,"开始卫星接收")!=null&&text(body,"停止卫星接收")==null,"satellite uses one start toggle");
            TextView vertical=(TextView)field(gps,"verticalAccuracy"),horizontal=(TextView)field(gps,"accuracy");near(vertical.getTextSize(),horizontal.getTextSize(),"vertical accuracy uses same large type as horizontal");
            LinearLayout card=(LinearLayout)vertical.getParent();check(card.indexOfChild(vertical)>card.indexOfChild((View)field(gps,"altitude")),"vertical accuracy follows altitude");
            text(body,"开始卫星接收").performClick();
        });SystemClock.sleep(500);
        ui(()->{call(gps,"update",new Class[]{});check((Boolean)staticField("LocationService","running"),"starting satellite reception starts actual location service");check((Boolean)field(hardware,"gnssRunning"),"satellite callback running");LinearLayout body=(LinearLayout)field(activity,"body");check(text(body,"停止定位")!=null&&text(body,"开始定位")==null,"location toggle shows stop after linked start");check(text(body,"停止卫星接收")!=null,"satellite toggle shows stop");call(gps,"startRoute",new Class[]{});check((Boolean)staticCall("RouteRecorder","active"),"linked location allows route drawing");staticCall("RouteRecorder","stop");text(body,"停止卫星接收").performClick();check((Boolean)staticField("LocationService","running"),"stopping satellite keeps independently started location service");text(body,"停止定位").performClick();check(!(Boolean)staticField("LocationService","running")&&!(Boolean)field(hardware,"gnssRunning"),"stop toggle clears location and satellite reception");check(text(body,"开始定位")!=null&&text(body,"开始卫星接收")!=null,"both toggles return to start");});
    }
    private Object staticField(String name,String key){try{Field f=type(name).getDeclaredField(key);f.setAccessible(true);return f.get(null);}catch(Exception e){throw new RuntimeException(e);}}
    private Object staticCall(String name,String method){try{Method m=type(name).getDeclaredMethod(method);m.setAccessible(true);return m.invoke(null);}catch(Exception e){throw new RuntimeException(e);}}
    private View rootWith(String title){List<View> roots=WindowInspector.getGlobalWindowViews();for(int i=roots.size()-1;i>=0;i--){View root=roots.get(i);if(root.isShown()&&text(root,title)!=null)return root;}return null;}
    private ListView list(View view){if(view instanceof ListView)return (ListView)view;if(view instanceof ViewGroup){ViewGroup g=(ViewGroup)view;for(int i=0;i<g.getChildCount();i++){ListView result=list(g.getChildAt(i));if(result!=null)return result;}}return null;}
    private void pointDialogs(){ui(()->{
        Object canvas=make("RouteCanvas",new Class[]{type("MainActivity")},activity);View view=(View)canvas;view.layout(0,0,400,320);ArrayList<Object> points=routePoints();points.set(1,points.get(0));call(canvas,"setRoute",new Class[]{List.class,int.class,boolean.class},points,2,true);float[] xy=(float[])call(canvas,"xy",new Class[]{type("RoutePoint")},points.get(0));call(canvas,"select",new Class[]{float.class,float.class},xy[0],xy[1]);
        View choices=rootWith("选择重叠或邻近的记录点");check(choices!=null,"overlap point picker opened");ListView list=list(choices);if(list==null)throw new AssertionError("picker list missing");list.performItemClick(null,0,0);
        View detail=rootWith("记录点 1 / 4");check(detail!=null&&text(detail,"返回")!=null,"point detail uses return button");text(detail,"返回").performClick();check(choices.isShown()&&rootWith("选择重叠或邻近的记录点")!=null,"return restores existing overlap picker");text(choices,"返回").performClick();
    });}

}
