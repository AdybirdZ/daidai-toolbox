package org.simpleweather.app;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.widget.*;
import java.util.*;

final class StatusPlaces {
    private final MainActivity a;private final SharedPreferences store;private final Runnable changed;
    private android.app.AlertDialog manager;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private LocationManager locationManager;
    private LocationListener pendingLocation;
    private Runnable pendingPermission;
    StatusPlaces(MainActivity a,Runnable changed){this.a=a;this.changed=changed;store=a.getSharedPreferences("status_places",0);}
    private String encoded;private List<StatusPlace> cached=Collections.emptyList();
    private List<StatusPlace> read(){String raw=store.getString("places","[]");if(!Objects.equals(raw,encoded)){cached=StatusPlace.decode(raw);encoded=raw;}return cached;}
    List<StatusPlace> all(){return new ArrayList<>(read());}
    StatusPlace selected(){String id=store.getString("selected","");for(StatusPlace p:read())if(p.id.equals(id))return p;return null;}
    private void save(List<StatusPlace> rows){store.edit().putString("places",StatusPlace.encode(rows)).apply();changed.run();if(manager!=null&&manager.isShowing()){manager.dismiss();manage();}}
    private EditText input(LinearLayout c,String title,String value,boolean number){c.addView(a.text(title,14,a.INK,true));EditText e=new EditText(a);e.setSingleLine(true);e.setText(value);e.setContentDescription(title);e.setInputType(number?InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL|InputType.TYPE_NUMBER_FLAG_SIGNED:InputType.TYPE_CLASS_TEXT);c.addView(e,a.lp(-1,52));a.gap(c,12);return e;}
    void addCurrent(){Location fix=LocationService.last(a);if(fix==null){a.toast("请先获取当前位置，或手动添加地点");return;}edit(null,fix);}
    void edit(StatusPlace old,Location fix){
        LinearLayout c=a.column();c.setPadding(a.dp(20),a.dp(8),a.dp(20),a.dp(12));
        EditText name=input(c,"地点名称（最多 60 字）",old!=null?old.name:"",false);
        EditText lon=input(c,"经度（负数 W / 正数 E）",old!=null?Double.toString(old.longitude):fix!=null?Double.toString(fix.getLongitude()):"",true);
        EditText lat=input(c,"纬度（负数 S / 正数 N）",old!=null?Double.toString(old.latitude):fix!=null?Double.toString(fix.getLatitude()):"",true);
        TextView note=a.text(fix==null?"暂无最近定位。点击“重新定位”可自动填写坐标。":"最近定位距今 "+LocationService.ageSeconds(fix)+" 秒；"+(fix.hasAccuracy()?String.format(Locale.ROOT,"水平定位误差半径约 %.1f 米。",fix.getAccuracy()):"未提供精度。")+"请确认坐标。",13,a.MUTED,false);c.addView(note);
        Runnable ageTick=new Runnable(){public void run(){if(note.getWindowToken()!=null){Location current=LocationService.last(a);if(current!=null)note.setText("最近定位距今 "+LocationService.ageSeconds(current)+" 秒；"+(current.hasAccuracy()?String.format(Locale.ROOT,"水平定位误差半径约 %.1f 米。",current.getAccuracy()):"未提供精度。")+"请确认坐标。");handler.postDelayed(this,1000);}}};handler.post(ageTick);
        LinearLayout actions=a.row();TextView relocate=a.button("重新定位",false,()->requestLocation(lon,lat,note));actions.addView(relocate,new LinearLayout.LayoutParams(0,-2,1));a.gapHorizontal(actions,8);actions.addView(a.text("点击“重新定位”后，将使用新定位结果填写本表单经纬度；不会自动修改已保存地点。",12,a.MUTED,false),new LinearLayout.LayoutParams(0,-2,2));c.addView(actions);a.gap(c,12);
        ScrollView scroll=new ScrollView(a);scroll.addView(c);android.app.AlertDialog d=new ToolDialog(a).setTitle(old==null?"保存地点":"编辑地点").setView(scroll).setNegativeButton("取消",null).setPositiveButton("保存",null).create();
        d.setOnShowListener(v->d.getButton(-1).setOnClickListener(w->{try{StatusPlace p=new StatusPlace(old==null?UUID.randomUUID().toString():old.id,name.getText().toString(),Double.parseDouble(lon.getText().toString().trim()),Double.parseDouble(lat.getText().toString().trim()),old==null?System.currentTimeMillis():old.created);List<StatusPlace> rows=all();if(old!=null)rows.removeIf(x->x.id.equals(old.id));rows.add(p);store.edit().putString("selected",p.id).apply();save(rows);d.dismiss();a.toast("地点已保存，仅存于本机");}catch(Exception ex){a.toast("请填写 1～60 字的名称、−180°～180° 的经度和 −90°～90° 的纬度");}}));d.setOnDismissListener(v->{handler.removeCallbacks(ageTick);stopRequest();});d.show();
    }
    private boolean network(){return a.preferences.getString("location_provider","gps").equals("network");}
    private boolean permitted(){return a.checkSelfPermission(network()?Manifest.permission.ACCESS_COARSE_LOCATION:Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;}
    private void requestLocation(EditText lon,EditText lat,TextView note){if(!permitted()){pendingPermission=()->requestLocation(lon,lat,note);a.requestPermissions(network()?new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}:new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},47);return;}stopRequest();locationManager=(LocationManager)a.getSystemService(Context.LOCATION_SERVICE);String provider=network()?LocationManager.NETWORK_PROVIDER:LocationManager.GPS_PROVIDER;try{if(locationManager==null||!locationManager.getAllProviders().contains(provider)||!locationManager.isProviderEnabled(provider)){note.setText("所选定位方式未开启，请先打开系统定位设置。");return;}note.setText("正在重新定位，请稍候…");pendingLocation=new LocationListener(){public void onLocationChanged(Location l){LocationService.record(a,l);lon.setText(Double.toString(l.getLongitude()));lat.setText(Double.toString(l.getLatitude()));note.setText("已重新定位，距今 0 秒；"+(l.hasAccuracy()?String.format(Locale.ROOT,"水平定位误差半径约 %.1f 米。",l.getAccuracy()):"未提供精度。")+"请确认坐标。");stopRequest();}public void onProviderEnabled(String p){}public void onProviderDisabled(String p){}public void onStatusChanged(String p,int s,android.os.Bundle b){}};locationManager.requestLocationUpdates(provider,0,0,pendingLocation,Looper.getMainLooper());handler.postDelayed(()->{if(pendingLocation!=null){stopRequest();note.setText("重新定位超时，请检查权限、定位开关和环境后重试。");}},15000);}catch(SecurityException e){stopRequest();note.setText("定位权限不足，请重新授权。" );}catch(Exception e){stopRequest();note.setText("重新定位暂不可用，请检查系统设置。" );}}
    private void stopRequest(){if(locationManager!=null&&pendingLocation!=null)try{locationManager.removeUpdates(pendingLocation);}catch(Exception ignored){}pendingLocation=null;}
    void permissionResult(){Runnable r=pendingPermission;pendingPermission=null;if(r!=null){if(permitted())r.run();else a.toast("未授予定位权限，无法重新定位");}}
    void manage(){
        LinearLayout c=a.column();c.setPadding(a.dp(18),a.dp(8),a.dp(18),a.dp(18));
        List<StatusPlace> rows=all();c.addView(a.text("共 "+rows.size()+" 个地点 · 仅本机保存",13,a.MUTED,false));a.gap(c,12);
        if(rows.isEmpty())c.addView(a.text("尚未保存地点",15,a.INK,false));
        for(StatusPlace p:rows){c.addView(a.text(p.name,17,a.INK,true));a.gap(c,8);c.addView(a.text(CoordinateFormat.pair(p.longitude,p.latitude,6),13,a.MUTED,false));a.gap(c,10);LinearLayout buttons=a.row();
            buttons.addView(a.button("选为目标",false,()->{store.edit().putString("selected",p.id).apply();changed.run();if(manager!=null)manager.dismiss();a.toast("目标："+p.name);}),new LinearLayout.LayoutParams(0,-2,1));a.gapHorizontal(buttons,8);
            buttons.addView(a.button("编辑",false,()->edit(p,null)),new LinearLayout.LayoutParams(0,-2,1));a.gapHorizontal(buttons,8);
            buttons.addView(a.button("删除",false,()->new ToolDialog(a).setTitle("删除“"+p.name+"”？").setMessage("此操作只删除本机保存的地点。").setNegativeButton("取消",null).setPositiveButton("删除",(d,n)->{List<StatusPlace> next=all();next.removeIf(x->x.id.equals(p.id));if(p.id.equals(store.getString("selected","")))store.edit().remove("selected").apply();save(next);a.toast("地点已删除");}).show()),new LinearLayout.LayoutParams(0,-2,1));c.addView(buttons);a.gap(c,20);
        }
        c.addView(a.button("手动添加地点",false,()->edit(null,null)));ScrollView s=new ScrollView(a);s.addView(c);manager=new ToolDialog(a).setTitle("保存的地点").setView(s).setNegativeButton("返回",null).show();
    }
}
