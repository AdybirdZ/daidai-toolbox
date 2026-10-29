package org.simpleweather.app;

import android.content.*;
import android.graphics.*;
import android.location.Location;
import android.view.*;
import org.json.*;
import java.util.*;

final class RoutePoint {
    final double lon,lat; final long time; final float accuracy;
    RoutePoint(double lon,double lat,long time,float accuracy){this.lon=lon;this.lat=lat;this.time=time;this.accuracy=accuracy;}
}

/** Small on-device route recorder. It stores only completed routes in a JSON preference. */
final class RouteRecorder {
    private static final Object LOCK=new Object();
    private static boolean active; private static long last=-1; private static ArrayList<RoutePoint> points=new ArrayList<>();
    static void start(long interval){synchronized(LOCK){active=true;last=-1;points.clear();}}
    static boolean active(){synchronized(LOCK){return active;}}
    static int size(){synchronized(LOCK){return points.size();}}
    static void stop(){synchronized(LOCK){active=false;last=-1;}}
    static void onLocation(Location l){if(l==null)return;synchronized(LOCK){if(!active)return;long t=l.getTime()>0?l.getTime():System.currentTimeMillis();long interval=RouteStore.intervalMs();if(last>=0&&t-last<interval)return;last=t;points.add(new RoutePoint(l.getLongitude(),l.getLatitude(),t,l.hasAccuracy()?l.getAccuracy():Float.NaN));}}
    static ArrayList<RoutePoint> points(){synchronized(LOCK){return new ArrayList<>(points);}}
}

final class RouteStore {
    static final String PREF="status_routes";
    static long intervalMs(){return 1000L*Math.max(2,Math.min(60,RouteStoreHolder.context==null?5:RouteStoreHolder.context.getSharedPreferences("toolbox",0).getInt("route_interval",5)));}
    private static final class RouteStoreHolder {static Context context;}
    private final SharedPreferences p;
    RouteStore(Context c){p=c.getSharedPreferences(PREF,0);RouteStoreHolder.context=c.getApplicationContext();}
    static final class Saved {String id,name;ArrayList<RoutePoint> points=new ArrayList<>();}
    ArrayList<Saved> all(){ArrayList<Saved> out=new ArrayList<>();try{JSONArray rows=new JSONArray(p.getString("routes","[]"));for(int i=0;i<rows.length();i++){JSONObject r=rows.optJSONObject(i);if(r==null)continue;Saved s=new Saved();s.id=r.optString("id",UUID.randomUUID().toString());s.name=r.optString("name","未命名路线");JSONArray ps=r.optJSONArray("points");if(ps!=null)for(int n=0;n<ps.length();n++){JSONObject q=ps.optJSONObject(n);if(q!=null) s.points.add(new RoutePoint(q.optDouble("lon"),q.optDouble("lat"),q.optLong("time"),Float.NaN));}if(!s.points.isEmpty())out.add(s);}}catch(Exception ignored){}return out;}
    void save(String name,List<RoutePoint> points){try{JSONArray rows=new JSONArray();for(Saved old:all())rows.put(encode(old.id,old.name,old.points));rows.put(encode(UUID.randomUUID().toString(),name,points));p.edit().putString("routes",rows.toString()).apply();}catch(Exception ignored){}}
    void remove(String id){try{JSONArray rows=new JSONArray();for(Saved old:all())if(!old.id.equals(id))rows.put(encode(old.id,old.name,old.points));p.edit().putString("routes",rows.toString()).apply();}catch(Exception ignored){}}
    private JSONObject encode(String id,String name,List<RoutePoint> points)throws JSONException{JSONObject r=new JSONObject();r.put("id",id);r.put("name",name);JSONArray ps=new JSONArray();for(RoutePoint q:points){JSONObject x=new JSONObject();x.put("lon",q.lon);x.put("lat",q.lat);x.put("time",q.time);ps.put(x);}r.put("points",ps);return r;}
}

final class RouteCanvas extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final MainActivity a;
    private ArrayList<RoutePoint> points=new ArrayList<>();
    private int visibleCount;
    private double originLon,originLat,cosLat=1,span=.002;
    private double viewLon,viewLat;
    private boolean followSelf,dragged,multiTouch;
    private final RouteViewport viewport=new RouteViewport();
    private float lastX,lastY,downX,downY;
    RouteCanvas(MainActivity a){super(a);this.a=a;setMinimumHeight(a.dp(300));setBackgroundColor(0xFFF9FBFF);setFocusable(false);setContentDescription("路线画布，单指拖动，使用下方滑块缩放，点按记录点查看详情");}
    void setRoute(List<RoutePoint> route,int count,boolean fit){
        points=new ArrayList<>(route);visibleCount=Math.max(0,Math.min(points.size(),count));
        if(!points.isEmpty()){
            originLon=points.get(0).lon;originLat=points.get(0).lat;cosLat=Math.max(.01,Math.cos(Math.toRadians(originLat)));
            double minX=0,maxX=0,minY=0,maxY=0;
            for(RoutePoint p:points){double x=longitudeDelta(p.lon-originLon)*cosLat,y=p.lat-originLat;minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);}
            span=Math.max(maxX-minX,maxY-minY);if(span<.00001)span=.002;
            if(fit){viewLon=originLon+(minX+maxX)/2/cosLat;viewLat=originLat+(minY+maxY)/2;viewport.reset();followSelf=false;}
        }
        invalidate();
    }
    int pointCount(){return points.size();}
    void showThrough(int index){visibleCount=Math.max(0,Math.min(points.size(),index+1));invalidate();}
    void centerOn(int index){if(index<0||index>=points.size())return;RoutePoint q=points.get(index);viewLon=q.lon;viewLat=q.lat;followSelf=false;viewport.center();invalidate();}
    void centerSelf(){Location fix=LocationService.last(a);if(fix==null){a.toast("请先获取当前位置");return;}followSelf=true;viewLon=fix.getLongitude();viewLat=fix.getLatitude();viewport.center();invalidate();}
    void zoom(float value){viewport.zoomTo(value);invalidate();}
    static double longitudeDelta(double value){return ((value+540)%360+360)%360-180;}
    private void intercept(boolean disallow){ViewParent parent=getParent();if(parent!=null)parent.requestDisallowInterceptTouchEvent(disallow);}
    private float scale(){return (float)(Math.min(getWidth(),getHeight())*.78/span*viewport.zoom);}
    private float[] xy(RoutePoint q){return new float[]{getWidth()/2f+(float)(longitudeDelta(q.lon-viewLon)*cosLat)*scale()+viewport.panX,getHeight()/2f-(float)(q.lat-viewLat)*scale()+viewport.panY};}
    protected void onDraw(Canvas c){
        super.onDraw(c);if(followSelf){Location fix=LocationService.last(a);if(fix!=null){viewLon=fix.getLongitude();viewLat=fix.getLatitude();}}
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(a.dp(2));paint.setColor(0xFF246BCE);
        for(int i=1;i<visibleCount;i++){float[] q=xy(points.get(i-1)),r=xy(points.get(i));c.drawLine(q[0],q[1],r[0],r[1],paint);}
        paint.setStyle(Paint.Style.FILL);
        for(int i=0;i<visibleCount;i++){float[] q=xy(points.get(i));paint.setColor(i==visibleCount-1?0xFFE58323:0xFF246BCE);c.drawCircle(q[0],q[1],a.dp(i==visibleCount-1?5:3),paint);}
        if(visibleCount==0){paint.setColor(a.MUTED);paint.setTextSize(a.dp(14));c.drawText("暂无路线定位点",a.dp(16),a.dp(30),paint);}
    }
    @Override public boolean onTouchEvent(MotionEvent e){
        int action=e.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){intercept(true);downX=lastX=e.getX();downY=lastY=e.getY();dragged=false;multiTouch=false;return true;}
        if(action==MotionEvent.ACTION_CANCEL){intercept(false);return true;}
        if(action==MotionEvent.ACTION_POINTER_DOWN){multiTouch=true;return true;}
        if(action==MotionEvent.ACTION_POINTER_UP){int remaining=e.getActionIndex()==0?1:0;lastX=e.getX(remaining);lastY=e.getY(remaining);return true;}
        if(action==MotionEvent.ACTION_MOVE){
            intercept(true);if(e.getPointerCount()!=1)return true;
            if(Math.hypot(e.getX()-downX,e.getY()-downY)>android.view.ViewConfiguration.get(a).getScaledTouchSlop())dragged=true;
            if(dragged){followSelf=false;viewport.pan(e.getX()-lastX,e.getY()-lastY);invalidate();}
            lastX=e.getX();lastY=e.getY();return true;
        }
        if(action==MotionEvent.ACTION_UP){intercept(false);if(!dragged&&!multiTouch){performClick();select(e.getX(),e.getY());}return true;}
        return true;
    }
    @Override public boolean performClick(){super.performClick();return true;}
    private void select(float x,float y){
        ArrayList<Integer> hits=new ArrayList<>();double limit=a.dp(20)*a.dp(20);
        for(int i=0;i<visibleCount;i++){float[] q=xy(points.get(i));double d=(q[0]-x)*(q[0]-x)+(q[1]-y)*(q[1]-y);if(d<=limit)hits.add(i);}
        if(hits.isEmpty())return;
        if(hits.size()==1){showPoint(hits.get(0));return;}
        String[] labels=new String[hits.size()];for(int i=0;i<hits.size();i++)labels[i]="记录点 "+(hits.get(i)+1)+" / "+points.size();
        new ToolDialog(a).setTitle("选择重叠或邻近的记录点").setItems(labels,(d,n)->showPoint(hits.get(n))).setNegativeButton("返回",null).show();
    }
    private void showPoint(int index){
        RoutePoint q=points.get(index);java.text.SimpleDateFormat format=new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss XXX",Locale.ROOT);format.setTimeZone(TimeZone.getTimeZone(a.displayZone(java.time.ZoneId.systemDefault())));
        new ToolDialog(a).setTitle("记录点 "+(index+1)+" / "+points.size()).setMessage("坐标（经度、纬度）："+CoordinateFormat.pair(q.lon,q.lat,6)+"\n记录时间："+format.format(new Date(q.time))+(Float.isFinite(q.accuracy)?"\n水平误差半径："+String.format(Locale.ROOT,"%.1f 米",q.accuracy):"")).setPositiveButton("关闭",null).show();
    }
}
