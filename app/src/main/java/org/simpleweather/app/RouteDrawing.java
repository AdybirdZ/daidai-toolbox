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
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private final MainActivity a;private ArrayList<RoutePoint> points=new ArrayList<>();private boolean center=true,selfCenter,move;private float zoom=1,panX,panY,lastX,lastY,pinchStart;private long downTime;private float downX,downY;private Runnable pointClick;
    RouteCanvas(MainActivity a){super(a);this.a=a;setMinimumHeight(a.dp(300));setBackgroundColor(0xFFF9FBFF);setFocusable(false);}
    void setPoints(List<RoutePoint> p){points=new ArrayList<>(p);if(center){panX=panY=0;}invalidate();}
    void setCenter(boolean b){center=b;selfCenter=b;if(b){panX=panY=0;}invalidate();}
    boolean isMove(){return move;}
    void setMove(boolean b){move=b;requestParentDisallow(b);invalidate();}
    private void requestParentDisallow(boolean disallow){ViewParent parent=getParent();if(parent!=null)parent.requestDisallowInterceptTouchEvent(disallow);}
    void onPointClick(Runnable r){pointClick=r;}
    protected void onDraw(Canvas c){super.onDraw(c);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(a.dp(2));paint.setColor(0xFF246BCE);if(points.size()>1){for(int i=1;i<points.size();i++){float[] p1=xy(points.get(i-1)),p2=xy(points.get(i));c.drawLine(p1[0],p1[1],p2[0],p2[1],paint);}}paint.setStyle(Paint.Style.FILL);paint.setColor(0xFF246BCE);for(int i=0;i<points.size();i++){float[] q=xy(points.get(i));c.drawCircle(q[0],q[1],i==points.size()-1?a.dp(5):a.dp(3),paint);}if(points.isEmpty()){paint.setColor(a.MUTED);paint.setTextSize(a.dp(14));c.drawText("等待定位点…",a.dp(16),a.dp(30),paint);}}
    private float[] xy(RoutePoint q){if(points.isEmpty())return new float[]{getWidth()/2f,getHeight()/2f};double minLon=Double.POSITIVE_INFINITY,maxLon=Double.NEGATIVE_INFINITY,minLat=Double.POSITIVE_INFINITY,maxLat=Double.NEGATIVE_INFINITY;for(RoutePoint p:points){minLon=Math.min(minLon,p.lon);maxLon=Math.max(maxLon,p.lon);minLat=Math.min(minLat,p.lat);maxLat=Math.max(maxLat,p.lat);}double cx=(minLon+maxLon)/2,cy=(minLat+maxLat)/2;if(selfCenter){Location current=LocationService.last(a);if(current!=null){cx=current.getLongitude();cy=current.getLatitude();}}double span=Math.max(maxLon-minLon,(maxLat-minLat)*1.35);if(span<0.0001)span=0.002;float scale=(float)(Math.min(getWidth(),getHeight())*.78/span*zoom);return new float[]{getWidth()/2f+(float)((q.lon-cx)*scale)+panX,getHeight()/2f-(float)((q.lat-cy)*scale)+panY};}
    public boolean onTouchEvent(android.view.MotionEvent e){int action=e.getActionMasked();if(action==MotionEvent.ACTION_CANCEL){pinchStart=0;requestParentDisallow(false);return true;}if(action==MotionEvent.ACTION_POINTER_UP){pinchStart=0;return true;}if(e.getPointerCount()>1){requestParentDisallow(true);if(action==MotionEvent.ACTION_POINTER_DOWN||action==MotionEvent.ACTION_DOWN){float dx=e.getX(1)-e.getX(0),dy=e.getY(1)-e.getY(0);pinchStart=(float)Math.sqrt(dx*dx+dy*dy);lastX=pinchStart;downTime=System.currentTimeMillis();return true;}if(action==MotionEvent.ACTION_MOVE){float dx=e.getX(1)-e.getX(0),dy=e.getY(1)-e.getY(0);float d=(float)Math.sqrt(dx*dx+dy*dy);if(pinchStart>0){zoom=Math.max(.5f,Math.min(12,zoom*(d/Math.max(1,lastX))));}lastX=d;invalidate();}return true;}switch(action){case MotionEvent.ACTION_DOWN:requestParentDisallow(move);downX=lastX=e.getX();downY=lastY=e.getY();downTime=System.currentTimeMillis();return true;case MotionEvent.ACTION_MOVE:if(move){requestParentDisallow(true);panX+=e.getX()-lastX;panY+=e.getY()-lastY;lastX=e.getX();lastY=e.getY();invalidate();}return true;case MotionEvent.ACTION_UP:requestParentDisallow(false);if(!move&&System.currentTimeMillis()-downTime<300&&Math.hypot(e.getX()-downX,e.getY()-downY)<a.dp(12)&&pointClick!=null){int hit=nearest(e.getX(),e.getY());if(hit>=0){final RoutePoint q=points.get(hit);new ToolDialog(a).setTitle("路线定位点").setMessage("经度、纬度："+CoordinateFormat.pair(q.lon,q.lat,6)+"\n时间："+new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.ROOT).format(new Date(q.time))+(Float.isFinite(q.accuracy)?"\n水平误差半径："+String.format(Locale.ROOT,"%.1f 米",q.accuracy):"" )).setPositiveButton("知道了",null).show();}}return true;}return true;}
    private int nearest(float x,float y){int best=-1;float d=Float.MAX_VALUE;for(int i=0;i<points.size();i++){float[] q=xy(points.get(i));float n=(q[0]-x)*(q[0]-x)+(q[1]-y)*(q[1]-y);if(n<d){d=n;best=i;}}return d<a.dp(22)*a.dp(22)?best:-1;}
}
