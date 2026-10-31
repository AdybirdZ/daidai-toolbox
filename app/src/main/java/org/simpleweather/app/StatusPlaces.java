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
    List<StatusPlace> shown(){List<StatusPlace> rows=new ArrayList<>();for(StatusPlace p:read())if(p.visible)rows.add(p);return rows;}
    org.json.JSONArray exportRows()throws org.json.JSONException{return new org.json.JSONArray(StatusPlace.encode(StatusPlace.decode(store.getString("places","[]"))));}
    boolean importRows(org.json.JSONArray rows){boolean saved=store.edit().putString("places",rows.toString()).commit();if(saved){changed.run();if(manager!=null&&manager.isShowing()){manager.dismiss();manage();}}return saved;}
    void select(String id){store.edit().putString("selected",id).apply();changed.run();}
    private void visibility(String id,boolean shown){List<StatusPlace> rows=all();for(int i=0;i<rows.size();i++)if(rows.get(i).id.equals(id))rows.set(i,rows.get(i).withVisibility(shown));store.edit().putString("places",StatusPlace.encode(rows)).apply();changed.run();}
    private void move(String id,int delta){List<StatusPlace> rows=all();int at=-1;for(int i=0;i<rows.size();i++)if(rows.get(i).id.equals(id)){at=i;break;}int to=at+delta;if(at<0||to<0||to>=rows.size())return;Collections.swap(rows,at,to);save(rows);}
    StatusPlace selected(){String id=store.getString("selected","");for(StatusPlace p:read())if(p.id.equals(id))return p;return null;}
    private void save(List<StatusPlace> rows){store.edit().putString("places",StatusPlace.encode(rows)).apply();changed.run();if(manager!=null&&manager.isShowing()){manager.dismiss();manage();}}
    private EditText input(LinearLayout c,String title,String value,boolean number){c.addView(a.text(title,14,a.INK,true));EditText e=new EditText(a);e.setSingleLine(true);e.setText(value);e.setContentDescription(title);e.setInputType(number?InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL|InputType.TYPE_NUMBER_FLAG_SIGNED:InputType.TYPE_CLASS_TEXT);c.addView(e,a.lp(-1,52));a.gap(c,12);return e;}
    void addCurrent(){Location fix=LocationService.last(a);if(fix==null){a.toast(UiText.get("status_places_001"));return;}edit(null,fix);}
    void edit(StatusPlace old,Location fix){
        LinearLayout c=a.column();c.setPadding(a.dp(20),a.dp(8),a.dp(20),a.dp(12));
        EditText name=input(c,UiText.get("status_places_002"),old!=null?old.name:"",false);
        EditText lon=input(c,UiText.get("status_places_003"),old!=null?Double.toString(old.longitude):fix!=null?Double.toString(fix.getLongitude()):"",true);
        EditText lat=input(c,UiText.get("status_places_004"),old!=null?Double.toString(old.latitude):fix!=null?Double.toString(fix.getLatitude()):"",true);
        int[] colors={StatusPlace.DEFAULT_COLOR,0xFFB83636,0xFF287C60,0xFFE58323,0xFF7852A8,0xFF167D9A,0xFFB3407D,0xFF333333};
        String[] colorNames={UiText.get("status_places_005"),UiText.get("settings_004"),UiText.get("settings_003"),UiText.get("status_places_006"),UiText.get("settings_005"),UiText.get("status_places_007"),UiText.get("status_places_008"),UiText.get("color_black")};
        c.addView(a.text(UiText.get("status_places_009"),14,a.INK,true));Spinner color=new Spinner(a);color.setContentDescription(UiText.get("status_places_009"));color.setAdapter(new ChoiceAdapter(a,colorNames));int selectedColor=0;for(int i=0;i<colors.length;i++)if(old!=null&&old.color==colors[i])selectedColor=i;color.setSelection(selectedColor);c.addView(color,a.lp(-1,52));a.gap(c,12);
        CheckBox shown=new CheckBox(a);shown.setText(UiText.get("status_places_010"));shown.setChecked(old==null||old.visible);c.addView(shown,a.lp(-1,52));a.gap(c,12);
        TextView note=a.text(fix==null?UiText.get("status_places_011"):UiText.get("status_places_012")+LocationService.ageSeconds(fix)+UiText.get("status_places_013")+(fix.hasAccuracy()?String.format(Locale.ROOT,UiText.get("status_places_014"),fix.getAccuracy()):UiText.get("status_places_015"))+UiText.get("status_places_016"),13,a.MUTED,false);c.addView(note);
        Runnable ageTick=new Runnable(){public void run(){if(note.getWindowToken()!=null){Location current=LocationService.last(a);if(current!=null)note.setText(UiText.get("status_places_012")+LocationService.ageSeconds(current)+UiText.get("status_places_013")+(current.hasAccuracy()?String.format(Locale.ROOT,UiText.get("status_places_014"),current.getAccuracy()):UiText.get("status_places_015"))+UiText.get("status_places_016"));handler.postDelayed(this,1000);}}};handler.post(ageTick);
        LinearLayout actions=a.row();TextView relocate=a.button(UiText.get("status_places_017"),false,()->requestLocation(lon,lat,note));actions.addView(relocate,new LinearLayout.LayoutParams(0,-2,1));a.gapHorizontal(actions,8);actions.addView(a.text(UiText.get("status_places_018"),12,a.MUTED,false),new LinearLayout.LayoutParams(0,-2,2));c.addView(actions);a.gap(c,12);
        ScrollView scroll=new ScrollView(a);scroll.addView(c);android.app.AlertDialog d=new ToolDialog(a).setTitle(old==null?UiText.get("status_places_019"):UiText.get("status_places_020")).setView(scroll).setNegativeButton(UiText.get("action_cancel"),null).setPositiveButton(UiText.get("action_save"),null).create();
        d.setOnShowListener(v->d.getButton(-1).setOnClickListener(w->{try{StatusPlace p=new StatusPlace(old==null?UUID.randomUUID().toString():old.id,name.getText().toString(),Double.parseDouble(lon.getText().toString().trim()),Double.parseDouble(lat.getText().toString().trim()),old==null?System.currentTimeMillis():old.created,colors[color.getSelectedItemPosition()],shown.isChecked());List<StatusPlace> rows=all();int original=rows.size();if(old!=null){for(int i=0;i<rows.size();i++)if(rows.get(i).id.equals(old.id)){original=i;break;}rows.removeIf(x->x.id.equals(old.id));}rows.add(Math.min(original,rows.size()),p);store.edit().putString("selected",p.id).apply();save(rows);d.dismiss();a.toast(UiText.get("status_places_021"));}catch(Exception ex){a.toast(UiText.get("status_places_022"));}}));d.setOnDismissListener(v->{handler.removeCallbacks(ageTick);stopRequest();});d.show();
    }
    private boolean network(){return a.preferences.getString("location_provider","gps").equals("network");}
    private boolean permitted(){return a.checkSelfPermission(network()?Manifest.permission.ACCESS_COARSE_LOCATION:Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;}
    private void requestLocation(EditText lon,EditText lat,TextView note){if(!permitted()){pendingPermission=()->requestLocation(lon,lat,note);a.requestPermissions(network()?new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}:new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},47);return;}stopRequest();locationManager=(LocationManager)a.getSystemService(Context.LOCATION_SERVICE);String provider=network()?LocationManager.NETWORK_PROVIDER:LocationManager.GPS_PROVIDER;try{if(locationManager==null||!locationManager.getAllProviders().contains(provider)||!locationManager.isProviderEnabled(provider)){note.setText(UiText.get("status_places_023"));return;}note.setText(UiText.get("status_places_024"));pendingLocation=new LocationListener(){public void onLocationChanged(Location l){LocationService.record(a,l);lon.setText(Double.toString(l.getLongitude()));lat.setText(Double.toString(l.getLatitude()));note.setText(UiText.get("status_places_025")+(l.hasAccuracy()?String.format(Locale.ROOT,UiText.get("status_places_014"),l.getAccuracy()):UiText.get("status_places_015"))+UiText.get("status_places_016"));stopRequest();}public void onProviderEnabled(String p){}public void onProviderDisabled(String p){}public void onStatusChanged(String p,int s,android.os.Bundle b){}};locationManager.requestLocationUpdates(provider,0,0,pendingLocation,Looper.getMainLooper());handler.postDelayed(()->{if(pendingLocation!=null){stopRequest();note.setText(UiText.get("status_places_026"));}},15000);}catch(SecurityException e){stopRequest();note.setText(UiText.get("status_places_027") );}catch(Exception e){stopRequest();note.setText(UiText.get("status_places_028") );}}
    private void stopRequest(){if(locationManager!=null&&pendingLocation!=null)try{locationManager.removeUpdates(pendingLocation);}catch(Exception ignored){}pendingLocation=null;}
    void permissionResult(){Runnable r=pendingPermission;pendingPermission=null;if(r!=null){if(permitted())r.run();else a.toast(UiText.get("status_places_029"));}}
    void manage(){
        LinearLayout c=a.column();c.setPadding(a.dp(18),a.dp(8),a.dp(18),a.dp(18));
        List<StatusPlace> rows=all();c.addView(a.text(UiText.get("status_places_030")+rows.size()+UiText.get("status_places_031"),13,a.MUTED,false));a.gap(c,12);
        if(rows.isEmpty())c.addView(a.text(UiText.get("status_places_032"),15,a.INK,false));
        for(StatusPlace p:rows){TextView title=a.text(p.name,17,p.color,true);c.addView(title);a.gap(c,8);CheckBox shown=new CheckBox(a);shown.setText(UiText.get("status_places_010"));shown.setChecked(p.visible);shown.setOnCheckedChangeListener((v,on)->visibility(p.id,on));c.addView(shown,a.lp(-1,48));a.gap(c,8);c.addView(a.text(CoordinateFormat.pair(p.longitude,p.latitude,6),13,a.MUTED,false));a.gap(c,10);LinearLayout buttons=a.row();
            buttons.addView(a.button(UiText.get("status_places_033"),false,()->{store.edit().putString("selected",p.id).apply();changed.run();if(manager!=null)manager.dismiss();a.toast(UiText.get("gps_168")+p.name);}),new LinearLayout.LayoutParams(0,-2,1));a.gapHorizontal(buttons,8);
            buttons.addView(a.button(UiText.get("gps_099"),false,()->edit(p,null)),new LinearLayout.LayoutParams(0,-2,1));a.gapHorizontal(buttons,8);
            buttons.addView(a.button(UiText.get("gps_092"),false,()->new ToolDialog(a).setTitle(UiText.get("schedule_086")+p.name+UiText.get("place_delete_suffix")).setMessage(UiText.get("status_places_034")).setNegativeButton(UiText.get("action_cancel"),null).setPositiveButton(UiText.get("gps_092"),(d,n)->{List<StatusPlace> next=all();next.removeIf(x->x.id.equals(p.id));if(p.id.equals(store.getString("selected","")))store.edit().remove("selected").apply();save(next);a.toast(UiText.get("status_places_035"));}).show()),new LinearLayout.LayoutParams(0,-2,1));c.addView(buttons);a.gap(c,8);
            LinearLayout sort=a.row();sort.addView(a.button(UiText.get("gps_101"),false,()->move(p.id,-1)),new LinearLayout.LayoutParams(0,-2,1));a.gapHorizontal(sort,8);sort.addView(a.button(UiText.get("gps_102"),false,()->move(p.id,1)),new LinearLayout.LayoutParams(0,-2,1));c.addView(sort);a.gap(c,20);
        }
        c.addView(a.button(UiText.get("gps_057"),false,()->edit(null,null)));ScrollView s=new ScrollView(a);s.addView(c);manager=new ToolDialog(a).setTitle(UiText.get("status_places_036")).setView(s).setNegativeButton(UiText.get("action_back"),null).show();
    }
}
