package org.simpleweather.app;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.location.*;
import android.os.*;
import android.widget.*;
import java.util.ArrayList;
import java.util.Locale;

/** Location service control and display. The Activity never owns background callbacks. */
final class GpsScreen {
    private final MainActivity a;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private TextView status,latitude,longitude,accuracy,altitude,verticalAccuracy,age,speed,bearing,motion,locationToggle,satelliteToggle;
    private boolean visible,pendingPermission,locationStarting;
    private final Runnable tick=new Runnable(){public void run(){if(visible){long next=SystemClock.uptimeMillis()+50;update();handler.postAtTime(this,next);}}};
    GpsScreen(MainActivity activity){a=activity;hardware=new StatusHardware(a);places=new StatusPlaces(a,()->{lastSecond=-1;update();});routeStore=new RouteStore(a);transfer=new StatusTransfer(a,routeStore,places);}
    static String[] sectionNames(){return new String[]{UiText.get("settings_compass"),UiText.get("gps_001"),UiText.get("gps_002"),UiText.get("gps_003"),UiText.get("settings_sensors"),UiText.get("settings_battery"),UiText.get("settings_device"),UiText.get("gps_004"),UiText.get("settings_route")};}
    private final StatusHardware hardware;
    private final StatusPlaces places;
    private final RouteStore routeStore;
    private final StatusTransfer transfer;
    void fileResult(int request,int result,Intent intent){transfer.result(request,result,intent);}
    private RouteCanvas routeCanvas; private TextView routeInfo,routeToggle,routeFollow,playToggle,previousPoint,nextPoint,firstPoint,lastPoint;
    private LinearLayout replayControls;private SeekBar routeZoom;
    private final RoutePlayback replay=new RoutePlayback();
    private String routeStatus=UiText.get("gps_005"),replayName="";
    private ArrayList<RoutePoint> displayedRoute=new ArrayList<>();
    private StatusViews.Compass compass;private StatusViews.Treasure treasure;private StatusViews.Sky sky;
    private TextView compassInfo,pitchView,rollView,satelliteStatus,satelliteInfo,satelliteDetails,treasureInfo,batteryInfo,deviceInfo;
    private LinearLayout satelliteDetailBox;private boolean details,pendingSatellite,batteryRegistered;
    private Intent battery;
    private long lastSecond=-1;
    private String selectedSatellite="";
    private final java.util.Map<Integer,TextView> sensorViews=new java.util.HashMap<>();
    private final java.util.Map<Integer,TextView> batteryViews=new java.util.HashMap<>();
    private final java.util.Map<Integer,TextView> deviceViews=new java.util.HashMap<>();
    static String[] batteryLabels(){return new String[]{UiText.get("gps_006"),UiText.get("tab_status"),UiText.get("gps_007"),UiText.get("gps_008"),UiText.get("gps_009"),UiText.get("gps_010"),UiText.get("gps_011"),UiText.get("gps_012"),UiText.get("gps_013")};}
    static String[] deviceLabels(){return new String[]{UiText.get("gps_014"),"Android",UiText.get("gps_015"),UiText.get("gps_016"),UiText.get("gps_017"),UiText.get("gps_018"),UiText.get("gps_019")};}
    private final BroadcastReceiver batteryReceiver=new BroadcastReceiver(){public void onReceive(Context c,Intent i){battery=i;}};
    void render(){
        status=latitude=longitude=accuracy=altitude=verticalAccuracy=locationToggle=satelliteToggle=age=speed=bearing=motion=compassInfo=pitchView=rollView=satelliteStatus=satelliteInfo=satelliteDetails=treasureInfo=batteryInfo=deviceInfo=null;
        routeCanvas=null;routeInfo=null;routeToggle=null;replayControls=null;routeZoom=null;routeFollow=null;compass=null;treasure=null;sky=null;satelliteDetailBox=null;sensorViews.clear();batteryViews.clear();deviceViews.clear();lastSecond=-1;
        for(int id:DisplayOrder.status(a.preferences.getString("status_order",""))){
            if(!a.preferences.getBoolean("status_show_"+id,true))continue;
            switch(id){case 0:compassCard();break;case 1:locationCard();break;case 2:satellitesCard();break;case 3:placesCard();break;case 4:sensorsCard();break;case 5:batteryCard();break;case 6:deviceCard();break;case 7:infoCard();break;case 8:routeCard();break;}
            a.gap(a.body,16);
        }
        if(sky==null){pendingSatellite=false;hardware.stopGnss(UiText.get("gps_020"));}
        resume();
    }
    private LinearLayout card(String title){LinearLayout c=a.card();c.addView(a.text(title,18,a.TITLE,true));a.gap(c,12);return c;}
    private void compassCard(){LinearLayout c=card(UiText.get("settings_compass"));compass=new StatusViews.Compass(a,hardware);c.addView(compass,a.lp(-1,-2));compassInfo=a.text(UiText.get("gps_021"),14,a.MUTED,false);c.addView(compassInfo);a.gap(c,10);pitchView=a.text(UiText.get("gps_022"),25,a.INK,true);rollView=a.text(UiText.get("gps_023"),25,a.INK,true);c.addView(pitchView);a.gap(c,6);c.addView(rollView);a.gap(c,12);c.addView(a.text(UiText.get("gps_024"),13,a.MUTED,false));}
    private void locationCard(){
        LinearLayout c=a.card();c.addView(a.text(UiText.get("gps_001"),18,a.TITLE,true));a.gap(c,12);status=a.text(LocationService.status(),14,a.MUTED,false);c.addView(status);a.gap(c,22);
        longitude=value(c,UiText.get("gps_025"));latitude=value(c,UiText.get("gps_026"));accuracy=value(c,UiText.get("gps_027"));altitude=value(c,UiText.get("gps_028"));verticalAccuracy=value(c,UiText.get("gps_029"));speed=value(c,UiText.get("gps_030"));bearing=value(c,UiText.get("gps_031"));age=a.text(UiText.get("gps_032"),12,a.MUTED,false);c.addView(age);a.gap(c,10);motion=a.text(UiText.get("gps_033"),13,a.MUTED,false);c.addView(motion);a.gap(c,16);
        locationToggle=a.button(LocationService.running?UiText.get("gps_034"):UiText.get("gps_035"),true,()->{if(LocationService.running||locationStarting||pendingPermission)stopLocation();else start();});c.addView(locationToggle);a.gap(c,12);c.addView(a.text(UiText.get("gps_036")+mode()+UiText.get("gps_037"),12,a.MUTED,false));a.gap(a.body,16);
    }
    private void satellitesCard(){LinearLayout c=card(UiText.get("gps_038"));satelliteStatus=a.text(hardware.gnssMessage,14,a.MUTED,false);c.addView(satelliteStatus);a.gap(c,12);
        satelliteToggle=a.button(hardware.gnssRunning?UiText.get("gps_039"):UiText.get("gps_040"),true,()->{if(hardware.gnssRunning||pendingSatellite){pendingSatellite=false;hardware.stopGnss(UiText.get("gps_041"));lastSecond=-1;update();}else startSatellites();});c.addView(satelliteToggle);a.gap(c,12);
        sky=new StatusViews.Sky(a,hardware,this::satelliteSelection);sky.selected=selectedSatellite;c.addView(sky,a.lp(-1,-2));
        c.addView(a.text(UiText.get("gps_042"),13,a.MUTED,false));a.gap(c,12);
        satelliteInfo=a.text(UiText.get("gps_043"),14,a.INK,false);c.addView(satelliteInfo);a.gap(c,12);
        c.addView(a.button(UiText.get("gps_044"),false,()->{details=!details;if(satelliteDetailBox!=null)satelliteDetailBox.setVisibility(details?android.view.View.VISIBLE:android.view.View.GONE);lastSecond=-1;update();}));a.gap(c,10);satelliteDetailBox=a.column();satelliteDetails=a.text(UiText.get("gps_045"),13,a.INK,false);satelliteDetailBox.addView(satelliteDetails);satelliteDetailBox.setVisibility(details?android.view.View.VISIBLE:android.view.View.GONE);c.addView(satelliteDetailBox);
        a.gap(c,12);c.addView(a.text(UiText.get("gps_046"),13,a.MUTED,false));
    }
    private void startSatellites(){if(a.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){pendingSatellite=true;a.requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},46);return;}pendingSatellite=false;if(hardware.startGnss()&&!LocationService.running&&!locationStarting)start();lastSecond=-1;update();}
    void satellitePermissionResult(){if(!pendingSatellite)return;if(a.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){pendingSatellite=false;hardware.gnssMessage=UiText.get("gps_047");a.toast(hardware.gnssMessage);}else if(visible)startSatellites();}
    void placesPermissionResult(){places.permissionResult();}
    private void satelliteSelection(){if(satelliteInfo==null||sky==null)return;selectedSatellite=sky.selected;for(StatusHardware.Satellite s:hardware.shown())if(s.key().equals(sky.selected)){set(satelliteInfo,satelliteLine(s));return;}set(satelliteInfo,sky.selected.isEmpty()?UiText.get("gps_043"):UiText.get("gps_048"));}
    private String satelliteLine(StatusHardware.Satellite s){return s.label()+UiText.get("astronomy_177")+(s.validAngles()?String.format(Locale.ROOT,UiText.get("gps_049"),s.elevation,s.azimuth,StatusMath.direction(s.azimuth))+(s.elevation<0?UiText.get("gps_050"):""):UiText.get("gps_051"))+UiText.get("gps_052")+(Float.isFinite(s.cn0)&&s.cn0>0?String.format(Locale.ROOT,"%.1f dB-Hz",s.cn0):UiText.get("earthquake_options_001"))+" · "+(s.used?UiText.get("gps_053"):UiText.get("gps_054"));}
    private void placesCard(){LinearLayout c=card(UiText.get("gps_003"));treasureInfo=a.text(UiText.get("gps_055"),14,a.INK,false);c.addView(treasureInfo);a.gap(c,12);treasure=new StatusViews.Treasure(a,hardware,places);c.addView(treasure,a.lp(-1,-2));
        c.addView(a.button(UiText.get("gps_056"),true,places::addCurrent));a.gap(c,10);c.addView(a.button(UiText.get("gps_057"),false,()->places.edit(null,null)));a.gap(c,10);c.addView(a.button(UiText.get("gps_058"),false,places::manage));a.gap(c,10);transferButtons(c,"places");a.gap(c,12);
        c.addView(a.text(UiText.get("gps_059"),13,a.MUTED,false));
    }
    private void transferButtons(LinearLayout c,String kind){LinearLayout row=a.row();String title=kind.equals("routes")?UiText.get("gps_060"):UiText.get("gps_003");row.addView(a.button(UiText.get("gps_061")+title,false,()->transfer.importFile(kind)),new LinearLayout.LayoutParams(0,-2,1));a.gapHorizontal(row,8);row.addView(a.button(UiText.get("gps_062")+title,false,()->transfer.exportFile(kind)),new LinearLayout.LayoutParams(0,-2,1));c.addView(row);}
    private void syncRouteViewport(){if(routeCanvas==null)return;if(routeFollow!=null)set(routeFollow,routeCanvas.following()?UiText.get("gps_063"):UiText.get("gps_064"));if(routeZoom!=null)routeZoom.setProgress(RouteViewport.progressAt(routeCanvas.sideMeters()));}
    private void routeCard(){
        LinearLayout c=card(UiText.get("settings_route"));routeInfo=a.text(routeStatus,14,a.INK,false);c.addView(routeInfo);a.gap(c,10);
        routeCanvas=new RouteCanvas(a);routeCanvas.playback(replay.loaded());if(RouteRecorder.active())displayedRoute=RouteRecorder.points();
        routeCanvas.setRoute(displayedRoute,replay.loaded()?replay.index()+1:displayedRoute.size(),true);c.addView(routeCanvas,a.lp(-1,-2));routeCanvas.changed=this::syncRouteViewport;a.gap(c,10);
        LinearLayout actions=a.row();routeToggle=a.button(RouteRecorder.active()?UiText.get("gps_065"):UiText.get("gps_066"),true,()->{if(RouteRecorder.active())stopRoute();else startRoute();});
        actions.addView(routeToggle,new LinearLayout.LayoutParams(0,-2,1));a.gapHorizontal(actions,8);routeFollow=a.button(UiText.get("gps_064"),false,()->{if(routeCanvas.following())routeCanvas.stopFollowing();else routeCanvas.centerSelf();syncRouteViewport();});actions.addView(routeFollow,new LinearLayout.LayoutParams(0,-2,1));c.addView(actions);a.gap(c,8);
        LinearLayout zoomRow=a.row();zoomRow.addView(a.text(UiText.get("gps_067"),14,a.INK,false));routeZoom=new SeekBar(a);routeZoom.setMax(RouteViewport.SLIDER_MAX);routeZoom.setProgress(RouteViewport.progressAt(routeCanvas.sideMeters()));routeZoom.setContentDescription(UiText.get("gps_068"));
        routeZoom.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int n,boolean user){if(user&&routeCanvas!=null)routeCanvas.sideMeters(RouteViewport.metersAt(n));}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}});
        zoomRow.addView(routeZoom,new LinearLayout.LayoutParams(0,a.dp(48),1));c.addView(zoomRow);a.gap(c,8);
        c.addView(a.button(UiText.get("gps_069"),false,this::routeList));a.gap(c,8);
        replayControls=a.column();LinearLayout transport=a.row();
        firstPoint=replayButton(transport,"◀◀",UiText.get("gps_070"),()->seekRoute(0));
        previousPoint=replayButton(transport,"◀",UiText.get("gps_071"),()->seekRoute(replay.index()-1));
        playToggle=replayButton(transport,UiText.get("gps_072"),UiText.get("gps_073"),this::togglePlayback);
        nextPoint=replayButton(transport,"▶",UiText.get("gps_074"),()->seekRoute(replay.index()+1));
        lastPoint=replayButton(transport,"▶▶",UiText.get("gps_075"),()->seekRoute(replay.size()-1));
        replayControls.addView(transport);a.gap(replayControls,8);
        TextView rate=a.text(UiText.get("gps_076")+replay.speed()+UiText.get("gps_077"),14,a.INK,false);replayControls.addView(rate);
        SeekBar rateSlider=new SeekBar(a);rateSlider.setMax(19);rateSlider.setProgress(replay.speed()-1);rateSlider.setContentDescription(UiText.get("gps_078"));rateSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int n,boolean user){replay.speed(n+1,SystemClock.elapsedRealtime());rate.setText(UiText.get("gps_076")+(n+1)+UiText.get("gps_077"));updateReplay();}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}});replayControls.addView(rateSlider,a.lp(-1,48));c.addView(replayControls);a.gap(c,10);transferButtons(c,"routes");
        a.gap(c,10);TextParagraphs.append(a,c,UiText.get("gps_079"),13,a.MUTED);
        if(replay.loaded())updateReplay();else updateLiveRoute();
    }
    private TextView replayButton(LinearLayout row,String label,String description,Runnable action){if(row.getChildCount()>0)a.gapHorizontal(row,6);TextView b=a.button(label,false,action);b.setTextSize(16);b.setPadding(a.dp(2),a.dp(10),a.dp(2),a.dp(10));b.setContentDescription(description);row.addView(b,new LinearLayout.LayoutParams(0,-2,1));return b;}
    private void startRoute(){
        if(!a.preferences.getBoolean("location_continuous",false)||!LocationService.running){a.toast(UiText.get("gps_080"));return;}
        replay.load(0);if(routeCanvas!=null)routeCanvas.playback(false);displayedRoute.clear();RouteRecorder.start(a.preferences.getInt("route_interval",5));routeStatus=UiText.get("gps_081");
        if(routeCanvas!=null){routeCanvas.setRoute(displayedRoute,0,true);syncRouteViewport();}updateLiveRoute();
    }
    private void updateLiveRoute(){
        if(replayControls!=null)replayControls.setVisibility(android.view.View.GONE);
        if(RouteRecorder.active()){
            if(routeCanvas!=null&&routeCanvas.pointCount()!=RouteRecorder.size()){displayedRoute=RouteRecorder.points();routeCanvas.setRoute(displayedRoute,displayedRoute.size(),routeCanvas.pointCount()==0&&!routeCanvas.following());}
            routeStatus=displayedRoute.isEmpty()?UiText.get("gps_081"):UiText.get("gps_082")+displayedRoute.size()+UiText.get("gps_083");
        }
        set(routeInfo,routeStatus);set(routeToggle,RouteRecorder.active()?UiText.get("gps_065"):UiText.get("gps_066"));
    }
    private void stopRoute(){
        RouteRecorder.stop();ArrayList<RoutePoint> pts=RouteRecorder.points();displayedRoute=new ArrayList<>(pts);routeStatus=UiText.get("gps_084")+pts.size()+UiText.get("gps_083");
        if(routeCanvas!=null)routeCanvas.setRoute(pts,pts.size(),false);updateLiveRoute();
        if(pts.isEmpty()){a.toast(UiText.get("gps_085"));return;}
        LinearLayout form=a.column();form.setPadding(a.dp(24),a.dp(8),a.dp(24),a.dp(8));
        EditText name=new EditText(a);name.setSingleLine(true);name.setHint(UiText.get("gps_086"));form.addView(name,a.lp(-1,-2));
        AlertDialog saveDialog=new ToolDialog(a).setTitle(UiText.get("gps_087")).setMessage(UiText.get("gps_088")+pts.size()+UiText.get("gps_089"))
            .setView(form).setPositiveButton(UiText.get("action_save"),(d,w)->{String n=name.getText().toString().trim();routeStore.save(n.isEmpty()?UiText.get("gps_090"):n,pts);routeStatus=UiText.get("gps_091")+pts.size()+UiText.get("gps_083");updateLiveRoute();})
            .setNeutralButton(UiText.get("gps_092"),null).setNegativeButton(UiText.get("action_cancel"),null).show();
        saveDialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->confirmRouteDeletion(()->{saveDialog.dismiss();displayedRoute.clear();routeStatus=UiText.get("gps_093");if(routeCanvas!=null)routeCanvas.setRoute(displayedRoute,0,true);updateLiveRoute();}));
    }
    private void confirmRouteDeletion(Runnable remove){new ToolDialog(a).setTitle(UiText.get("gps_094")).setMessage(UiText.get("gps_095")).setPositiveButton(UiText.get("gps_092"),(d,w)->remove.run()).setNegativeButton(UiText.get("action_cancel"),null).show();}
    private void routeList(){
        if(RouteRecorder.active()){a.toast(UiText.get("gps_096"));return;}
        LinearLayout c=a.column();c.setPadding(a.dp(20),a.dp(8),a.dp(20),a.dp(8));ArrayList<RouteStore.Saved> rows=routeStore.all();
        final AlertDialog[] dialog=new AlertDialog[1];if(rows.isEmpty())c.addView(a.text(UiText.get("gps_097"),14,a.MUTED,false));
        for(RouteStore.Saved row:rows){LinearLayout r=a.row();TextView open=a.button(row.name+" · "+row.points.size()+UiText.get("gps_098"),false,()->{
            dialog[0].dismiss();displayedRoute=new ArrayList<>(row.points);replayName=row.name;replay.load(row.points.size());
            if(routeCanvas!=null){routeCanvas.setRoute(displayedRoute,displayedRoute.size(),true);syncRouteViewport();}updateReplay();
        });r.addView(open,new LinearLayout.LayoutParams(0,-2,1));a.gapHorizontal(r,6);r.addView(a.button(UiText.get("gps_099"),false,()->editRoute(row,dialog)),a.lp(64,48));a.gapHorizontal(r,6);r.addView(a.button(UiText.get("gps_092"),false,()->confirmRouteDeletion(()->{routeStore.remove(row.id);dialog[0].dismiss();a.toast(UiText.get("gps_100"));routeList();})),a.lp(64,48));c.addView(r);a.gap(c,6);
            LinearLayout sort=a.row();sort.addView(a.button(UiText.get("gps_101"),false,()->{routeStore.move(row.id,-1);dialog[0].dismiss();routeList();}),new LinearLayout.LayoutParams(0,-2,1));a.gapHorizontal(sort,6);sort.addView(a.button(UiText.get("gps_102"),false,()->{routeStore.move(row.id,1);dialog[0].dismiss();routeList();}),new LinearLayout.LayoutParams(0,-2,1));c.addView(sort);a.gap(c,12);}
        a.gap(c,12);TextParagraphs.append(a,c,a.getString(R.string.saved_order_note),13,a.MUTED);ScrollView scroll=new ScrollView(a);scroll.addView(c);dialog[0]=new ToolDialog(a).setTitle(UiText.get("gps_103")).setView(scroll).setNegativeButton(UiText.get("action_back"),null).show();
    }
    private void editRoute(RouteStore.Saved row,AlertDialog[] holder){
        EditText input=new EditText(a);input.setSingleLine(true);input.setText(row.name);input.setSelectAllOnFocus(true);input.setHint(UiText.get("gps_104"));
        new ToolDialog(a).setTitle(UiText.get("gps_105")).setView(input).setNegativeButton(UiText.get("action_cancel"),null).setPositiveButton(UiText.get("action_save"),(d,w)->{String name=input.getText().toString().trim();if(name.isEmpty()){a.toast(UiText.get("gps_106"));return;}routeStore.rename(row.id,name);if(holder[0]!=null)holder[0].dismiss();routeList();}).show();
    }
    private void seekRoute(int index){if(replay.playing())return;replay.seek(index);if(routeCanvas!=null){if(routeCanvas.following())routeCanvas.showThrough(replay.index());else routeCanvas.centerOn(replay.index());}syncRouteViewport();updateReplay();}
    private void togglePlayback(){
        if(replay.playing())replay.pause();else{
            replay.play(SystemClock.elapsedRealtime());
        }
        updateReplay();
    }
    private void updateReplay(){
        if(replayControls==null)return;replayControls.setVisibility(replay.loaded()?android.view.View.VISIBLE:android.view.View.GONE);if(!replay.loaded())return;
        if(routeCanvas!=null){routeCanvas.playback(true);routeCanvas.showThrough(replay.index());}
        set(playToggle,replay.playing()?UiText.get("gps_107"):UiText.get("gps_072"));playToggle.setContentDescription(replay.playing()?UiText.get("gps_108"):UiText.get("gps_073"));
        boolean paused=!replay.playing();setReplayEnabled(firstPoint,paused&&replay.index()>0);setReplayEnabled(previousPoint,paused&&replay.index()>0);setReplayEnabled(nextPoint,paused&&replay.index()<replay.size()-1);setReplayEnabled(lastPoint,paused&&replay.index()<replay.size()-1);
        routeStatus=UiText.get("gps_109")+replayName+" · "+(replay.index()+1)+" / "+replay.size()+UiText.get("gps_110")+(replay.playing()?UiText.get("gps_111"):UiText.get("gps_112"));set(routeInfo,routeStatus);
    }
    private void setReplayEnabled(TextView b,boolean enabled){b.setEnabled(enabled);b.setAlpha(enabled?1f:.35f);}
    static String[] sensorNames(){return new String[]{UiText.get("gps_113"),UiText.get("gps_114"),UiText.get("gps_115"),UiText.get("gps_116"),UiText.get("gps_117"),UiText.get("gps_118"),UiText.get("gps_119"),UiText.get("gps_120"),UiText.get("gps_121"),UiText.get("gps_122")};}
    static final String[] UNITS={"rad/s","m/s²","m/s²","m/s²","μT","hPa","lx","cm","℃","%"};
    private TextView cameraToggle;
    private void sensorsCard(){
        cameraToggle=null;LinearLayout c=card(UiText.get("settings_sensors"));
        boolean camera="camera".equals(a.preferences.getString("status_light_source","sensor"));
        for(int index:DisplayOrder.parse(a.preferences.getString("status_sensor_order",""),StatusHardware.TYPES.length)){
            int type=StatusHardware.TYPES[index];if(!a.preferences.getBoolean("status_sensor_show_"+type,true))continue;
            c.addView(a.text(sensorNames()[index],15,a.TITLE,true));a.gap(c,8);
            TextView data=a.text(UiText.get("gps_123"),14,a.MUTED,false);c.addView(data);sensorViews.put(type,data);
            if(type==android.hardware.Sensor.TYPE_LIGHT&&camera){
                a.gap(c,10);cameraToggle=a.button(a.getString(hardware.cameraPaused()?R.string.camera_resume:R.string.camera_pause),false,()->{
                    hardware.toggleCamera();set(cameraToggle,a.getString(hardware.cameraPaused()?R.string.camera_resume:R.string.camera_pause));updateSensors();
                });c.addView(cameraToggle);
                a.gap(c,10);TextParagraphs.append(a,c,a.getString(R.string.camera_samples_note),13,a.MUTED);
            }else{android.hardware.Sensor sensor=hardware.sensor(type);if(sensor!=null){a.gap(c,5);c.addView(a.text(sensor.getName(),11,a.MUTED,false));}}
            a.gap(c,16);
        }
        if(sensorViews.isEmpty())c.addView(a.text(UiText.get("gps_124"),14,a.MUTED,false));
        else TextParagraphs.append(a,c,UiText.get("gps_125"),13,a.MUTED);
    }
    private void batteryCard(){LinearLayout c=card(UiText.get("settings_battery"));java.util.List<Integer> order=DisplayOrder.parse(a.preferences.getString("status_battery_order",""),batteryLabels().length);for(int id:order){if(!a.preferences.getBoolean("status_battery_show_"+id,true))continue;c.addView(a.text(batteryLabels()[id],12,a.MUTED,false));a.gap(c,6);TextView value=a.text(UiText.get("gps_126"),15,a.INK,true);batteryViews.put(id,value);c.addView(value);a.gap(c,16);}if(batteryViews.isEmpty())c.addView(a.text(UiText.get("gps_127"),14,a.MUTED,false));c.addView(a.text(UiText.get("gps_128"),13,a.MUTED,false));}
    private void deviceCard(){LinearLayout c=card(UiText.get("settings_device"));java.util.List<Integer> order=DisplayOrder.parse(a.preferences.getString("status_device_order",""),deviceLabels().length);for(int id:order){if(!a.preferences.getBoolean("status_device_show_"+id,true))continue;c.addView(a.text(deviceLabels()[id],12,a.MUTED,false));a.gap(c,6);TextView value=a.text(UiText.get("gps_129"),14,a.INK,false);deviceViews.put(id,value);c.addView(value);a.gap(c,16);}if(deviceViews.isEmpty())c.addView(a.text(UiText.get("gps_130"),14,a.MUTED,false));}
    private void infoCard(){
        LinearLayout info=a.card();info.addView(a.text(UiText.get("gps_131"),17,a.TITLE,true));a.gap(info,12);TextParagraphs.append(a,info,UiText.get("gps_132"),13,a.MUTED);
        info.addView(a.text(UiText.get("gps_133"),13,a.MUTED,false));
    }
    private TextView value(LinearLayout c,String title){c.addView(a.text(title,12,a.MUTED,false));a.gap(c,8);TextView t=a.text("—",25,a.INK,true);c.addView(t);a.gap(c,20);return t;}
    private boolean network(){return a.preferences.getString("location_provider","gps").equals("network");}
    private String mode(){return network()?UiText.get("earthquake_location_005"):UiText.get("earthquake_location_006");}
    void configurationChanged(){hardware.configurationChanged();lastSecond=-1;update();}
    void destroy(){pause();hardware.shutdown();}
    void prepareNextLaunch(){hardware.prepareNextLaunch();}
    void stopForImport(){pause();hardware.shutdown();LocationService.stop(a);}
    void modeChanged(){locationStarting=false;pendingPermission=false;pendingSatellite=false;LocationService.stop(a);hardware.stopGnss(UiText.get("gps_136"));}
    void intervalChanged(){if(LocationService.running)startService();}
    void continuousChanged(){if(LocationService.running)startService();}
    private boolean allowed(){return a.checkSelfPermission(network()?Manifest.permission.ACCESS_COARSE_LOCATION:Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;}
    void permissionResult(){if(!pendingPermission)return;if(allowed()){if(visible){pendingPermission=false;start();}}else{pendingPermission=false;LocationService.status("gps_137");lastSecond=-1;update();}}
    void notificationPermissionResult(){if(LocationService.running&&LocationService.continuous(a))startService();if(Build.VERSION.SDK_INT>=33&&a.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)a.toast(UiText.get("gps_138"));}
    private void stopLocation(){pendingPermission=false;pendingSatellite=false;locationStarting=false;LocationService.stop(a);hardware.stopGnss(UiText.get("gps_041"));lastSecond=-1;update();}
    private void start(){
        if(!allowed()){pendingPermission=true;a.requestPermissions(network()?new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}:new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},41);return;}
        if(LocationService.continuous(a)&&Build.VERSION.SDK_INT>=33&&a.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED&&!a.preferences.getBoolean("location_notice_asked",false)){
            a.preferences.edit().putBoolean("location_notice_asked",true).apply();a.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},44);
        }
        startService();
    }
    private void startService(){locationStarting=true;LocationService.status("gps_139");try{Intent i=new Intent(a,LocationService.class);if(LocationService.continuous(a))a.startForegroundService(i);else a.startService(i);}catch(Exception e){locationStarting=false;LocationService.status("gps_140");a.toast(LocationService.status());}handler.removeCallbacks(tick);if(visible)handler.post(tick);}
    void resume(){visible=true;if(replay.loaded())updateReplay();lastSecond=-1;hardware.resume(compass!=null||treasure!=null,!sensorViews.isEmpty());if(hardware.gnssRunning&&!LocationService.running&&!locationStarting)start();if(pendingPermission&&allowed()){pendingPermission=false;start();}if(batteryViews.isEmpty()&&batteryRegistered){try{a.unregisterReceiver(batteryReceiver);}catch(Exception ignored){}batteryRegistered=false;}if(!batteryViews.isEmpty()&&!batteryRegistered){try{battery=a.registerReceiver(batteryReceiver,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));batteryRegistered=true;}catch(Exception ignored){}}if(pendingSatellite&&a.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED)startSatellites();handler.removeCallbacks(tick);handler.post(tick);}
    void pause(){replay.pause();locationStarting=false;visible=false;hardware.pause();if(batteryRegistered){try{a.unregisterReceiver(batteryReceiver);}catch(Exception ignored){}batteryRegistered=false;}handler.removeCallbacks(tick);if(!LocationService.continuous(a)&&!pendingPermission)LocationService.stop(a);}
    void stopForNavigation(){pendingPermission=false;pendingSatellite=false;pause();}
    private void set(TextView view,String value){if(view!=null&&!value.contentEquals(view.getText()))view.setText(value);}
    private void update(){
        if(!visible)return;
        if(locationStarting&&(LocationService.running||!UiText.get("gps_139").equals(LocationService.status())))locationStarting=false;
        set(locationToggle,LocationService.running||locationStarting||pendingPermission?UiText.get("gps_034"):UiText.get("gps_035"));
        set(satelliteToggle,hardware.gnssRunning||pendingSatellite?UiText.get("gps_039"):UiText.get("gps_040"));
        if(compass!=null)compass.invalidate();if(treasure!=null)treasure.invalidate();if(routeCanvas!=null){if(RouteRecorder.active())updateLiveRoute();else if(replay.advance(SystemClock.elapsedRealtime()))updateReplay();if(routeCanvas.following())routeCanvas.invalidate();}
        a.updateReadingValues(()->{updateCompass();updateSatellites();updateTreasure();updateSensors();updateBattery();updateDevice();});
        // Position refresh stays separate from the 50 ms device/sensor display loop.
        long second=SystemClock.elapsedRealtime()/1000;if(second==lastSecond)return;lastSecond=second;
        if(status==null)return;set(status,LocationService.status());Location fix=LocationService.last(a);if(fix==null){set(longitude,"—");set(latitude,"—");set(accuracy,"—");set(altitude,"—");set(verticalAccuracy,"—");set(speed,"—");set(bearing,"—");set(age,UiText.get("gps_032"));set(motion,UiText.get("gps_033"));return;}
        set(longitude,CoordinateFormat.longitude(fix.getLongitude(),6));set(latitude,CoordinateFormat.latitude(fix.getLatitude(),6));
        set(accuracy,fix.hasAccuracy()?String.format(Locale.ROOT,UiText.get("gps_141"),fix.getAccuracy()):UiText.get("gps_142"));set(altitude,fix.hasAltitude()?String.format(Locale.ROOT,UiText.get("gps_141"),fix.getAltitude()):UiText.get("gps_143"));
        set(verticalAccuracy,fix.hasVerticalAccuracy()?String.format(Locale.ROOT,UiText.get("gps_141"),fix.getVerticalAccuracyMeters()):UiText.get("gps_144"));
        set(speed,fix.hasSpeed()?String.format(Locale.ROOT,"%.2f m/s · %.1f km/h",fix.getSpeed(),fix.getSpeed()*3.6):UiText.get("earthquake_options_001"));
        set(bearing,fix.hasBearing()?String.format(Locale.ROOT,"%.1f° · %s",fix.getBearing(),StatusMath.direction(fix.getBearing())):UiText.get("earthquake_options_001"));
        set(motion,UiText.get("gps_033"));
        long seconds=LocationService.ageSeconds(fix);String actual="network".equals(fix.getProvider())?UiText.get("earthquake_location_005"):UiText.get("earthquake_location_006");set(age,actual+UiText.get("gps_145")+seconds+UiText.get("gps_146")+(seconds>30?UiText.get("gps_147"):""));
        if(LocationService.running&&seconds>30)set(status,UiText.get("gps_148"));
    }
    private void updateCompass(){
        if(compassInfo==null)return;Location fix=LocationService.last(a);boolean trueNorth=a.preferences.getBoolean("status_true_north",true);double heading=trueNorth?hardware.trueHeading(fix):hardware.magneticHeading;
        String quality=hardware.compassAccuracy==3?UiText.get("gps_149"):hardware.compassAccuracy==2?UiText.get("gps_150"):hardware.compassAccuracy==1?UiText.get("gps_151"):UiText.get("gps_152");
        String info=hardware.compassSource()+" · "+quality;
        if(hardware.headingAvailable()&&Double.isFinite(heading))info+=UiText.get("gps_153")+StatusMath.direction(heading)+String.format(Locale.ROOT," · %.1f°",StatusMath.norm(heading));
        else info+="\n"+(trueNorth&&fix==null?UiText.get("gps_154"):UiText.get("gps_155"));
        if(pitchView!=null)set(pitchView,Double.isFinite(hardware.pitch)?String.format(Locale.ROOT,UiText.get("gps_156"),hardware.pitch):UiText.get("gps_022"));
        if(rollView!=null)set(rollView,Double.isFinite(hardware.roll)?String.format(Locale.ROOT,UiText.get("gps_157"),hardware.roll):UiText.get("gps_023"));
        if(fix!=null&&Double.isFinite(hardware.declination(fix)))info+=String.format(Locale.ROOT,UiText.get("gps_158"),hardware.declination(fix),LocationService.ageSeconds(fix));set(compassInfo,info);
    }
    private void updateSatellites(){if(satelliteStatus==null)return;int total=hardware.satellites.size(),used=0;for(StatusHardware.Satellite s:hardware.satellites)if(s.used)used++;
        long age=hardware.gnssAt==0?0:(SystemClock.elapsedRealtime()-hardware.gnssAt)/1000;String stamp=hardware.gnssAt==0?"":UiText.get("gps_159")+age+UiText.get("earthquake_025")+(age>15?UiText.get("gps_160"):"");
        set(satelliteStatus,hardware.gnssMessage+UiText.get("gps_161")+total+UiText.get("gps_162")+used+UiText.get("gps_163")+hardware.shown().size()+UiText.get("gps_164")+stamp);
        if(sky!=null)sky.invalidate();satelliteSelection();if(satelliteDetails!=null&&details){StringBuilder lines=new StringBuilder();for(StatusHardware.Satellite s:hardware.shown()){if(lines.length()>0)lines.append("\n\n");lines.append(satelliteLine(s));}set(satelliteDetails,lines.length()==0?UiText.get("gps_165"):lines.toString());}
    }
    private void updateTreasure(){if(treasureInfo==null)return;StatusPlace target=places.selected();Location fix=LocationService.last(a);if(target==null){set(treasureInfo,UiText.get("gps_166")+places.all().size()+UiText.get("gps_167"));return;}
        String info=UiText.get("gps_168")+target.name+"\n"+CoordinateFormat.pair(target.longitude,target.latitude,6);if(fix==null){set(treasureInfo,info+UiText.get("gps_169"));return;}
        double distance=StatusMath.distance(fix.getLongitude(),fix.getLatitude(),target.longitude,target.latitude),bearing=StatusMath.bearing(fix.getLongitude(),fix.getLatitude(),target.longitude,target.latitude);
        info+=UiText.get("gps_170")+StatusMath.distanceText(distance)+(Double.isFinite(bearing)?String.format(Locale.ROOT,UiText.get("gps_171"),bearing,StatusMath.direction(bearing)):UiText.get("gps_172"));
        double heading=hardware.trueHeading(fix);if(Double.isFinite(heading)&&Double.isFinite(bearing)){double delta=StatusMath.delta(bearing,heading);info+=Math.abs(delta)<5?UiText.get("gps_173"):String.format(Locale.ROOT,UiText.get("gps_174"),delta>=0?UiText.get("gps_175"):UiText.get("gps_176"),Math.abs(delta));}
        info+=UiText.get("gps_177")+LocationService.ageSeconds(fix)+UiText.get("earthquake_025")+(LocationService.ageSeconds(fix)>30?UiText.get("gps_178"):"");if(fix.hasAccuracy())info+=String.format(Locale.ROOT,UiText.get("gps_179"),fix.getAccuracy());set(treasureInfo,info);
    }
    private void updateSensors(){if(sensorViews.isEmpty())return;for(int n=0;n<StatusHardware.TYPES.length;n++){int type=StatusHardware.TYPES[n];set(sensorViews.get(type),hardware.sensorText(type,UNITS[n]));}}
    private static String chargeStatus(int status){switch(status){case BatteryManager.BATTERY_STATUS_CHARGING:return UiText.get("gps_180");case BatteryManager.BATTERY_STATUS_DISCHARGING:return UiText.get("gps_181");case BatteryManager.BATTERY_STATUS_FULL:return UiText.get("gps_182");case BatteryManager.BATTERY_STATUS_NOT_CHARGING:return UiText.get("gps_183");default:return UiText.get("earthquake_display_002");}}
    private void updateBattery(){if(batteryViews.isEmpty())return;if(battery==null){for(TextView v:batteryViews.values())set(v,UiText.get("gps_184"));return;}if(battery.hasExtra(BatteryManager.EXTRA_PRESENT)&&!battery.getBooleanExtra(BatteryManager.EXTRA_PRESENT,true)){for(TextView v:batteryViews.values())set(v,UiText.get("gps_185"));return;}
        int level=battery.getIntExtra(BatteryManager.EXTRA_LEVEL,-1),scale=battery.getIntExtra(BatteryManager.EXTRA_SCALE,-1),voltage=battery.getIntExtra(BatteryManager.EXTRA_VOLTAGE,-1),temperature=battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE,Integer.MIN_VALUE),plugged=battery.getIntExtra(BatteryManager.EXTRA_PLUGGED,-1),health=battery.getIntExtra(BatteryManager.EXTRA_HEALTH,0);
        String healthText=health==BatteryManager.BATTERY_HEALTH_GOOD?UiText.get("gps_186"):health==BatteryManager.BATTERY_HEALTH_OVERHEAT?UiText.get("gps_187"):health==BatteryManager.BATTERY_HEALTH_COLD?UiText.get("gps_188"):health==BatteryManager.BATTERY_HEALTH_DEAD?UiText.get("gps_189"):health==BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE?UiText.get("gps_190"):health==BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE?UiText.get("gps_191"):UiText.get("earthquake_options_001");
        String source=plugged<0?UiText.get("earthquake_options_001"):plugged==0?UiText.get("gps_192"):(plugged&BatteryManager.BATTERY_PLUGGED_AC)!=0?UiText.get("gps_193"):(plugged&BatteryManager.BATTERY_PLUGGED_USB)!=0?"USB":(plugged&BatteryManager.BATTERY_PLUGGED_WIRELESS)!=0?UiText.get("gps_194"):UiText.get("gps_195");
        String technology=battery.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY);if(technology==null||technology.isEmpty())technology=UiText.get("earthquake_options_001");
        BatteryManager bm=(BatteryManager)a.getSystemService(Context.BATTERY_SERVICE);int rawCurrent=Integer.MIN_VALUE,rawCharge=Integer.MIN_VALUE;if(bm!=null){try{rawCurrent=bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);}catch(Exception ignored){}try{rawCharge=bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);}catch(Exception ignored){}}
        String[] readings=batteryReadingText(rawCurrent,rawCharge);String currentText=readings[0],chargeText=readings[1];
        String[] values={level>=0&&scale>0?Math.round(level*100f/scale)+"%":UiText.get("earthquake_options_001"),chargeStatus(battery.getIntExtra(BatteryManager.EXTRA_STATUS,0)),temperature!=Integer.MIN_VALUE?String.format(Locale.ROOT,"%.1f ℃",temperature/10.0):UiText.get("earthquake_options_001"),voltage>0?String.format(Locale.ROOT,"%.3f V",voltage/1000.0):UiText.get("earthquake_options_001"),healthText,source,technology,currentText,chargeText};for(int id:batteryViews.keySet()){String value=values[id];set(batteryViews.get(id),value);}
    }
    private String[] batteryReadingText(int current,int charge){
        double milliamps=BatteryReadings.current(current,a.preferences.getString(BatteryReadings.CURRENT_UNIT,"uA"),a.preferences.getBoolean("status_battery_reverse_current",false));
        double milliampHours=BatteryReadings.charge(charge,a.preferences.getString(BatteryReadings.CHARGE_UNIT,"uAh"));
        String missing=UiText.get("earthquake_options_001");
        return new String[]{Double.isNaN(milliamps)?missing:String.format(Locale.ROOT,"%.1f mA",milliamps),Double.isNaN(milliampHours)?missing:String.format(Locale.ROOT,UiText.get("gps_196"),milliampHours)};
    }
    private void updateDevice(){if(deviceViews.isEmpty())return;String[] values=new String[deviceLabels().length];values[0]=Build.MANUFACTURER+" "+Build.MODEL;values[1]=Build.VERSION.RELEASE+" · API "+Build.VERSION.SDK_INT;values[2]=Build.VERSION.SECURITY_PATCH;
        try{ActivityManager am=(ActivityManager)a.getSystemService(Context.ACTIVITY_SERVICE);ActivityManager.MemoryInfo m=new ActivityManager.MemoryInfo();am.getMemoryInfo(m);values[3]=bytes(m.availMem)+" / "+bytes(m.totalMem);StatFs fs=new StatFs(a.getFilesDir().getPath());values[4]=bytes(fs.getAvailableBytes())+" / "+bytes(fs.getTotalBytes());}catch(Exception ignored){values[3]=values[4]=UiText.get("earthquake_options_001");}
        long seconds=SystemClock.elapsedRealtime()/1000;values[5]=(seconds/86400)+UiText.get("gps_197")+(seconds/3600%24)+UiText.get("gps_198")+(seconds/60%60)+UiText.get("gps_199");
        android.util.DisplayMetrics dm=a.getResources().getDisplayMetrics();values[6]=dm.widthPixels+" × "+dm.heightPixels+" px · "+dm.densityDpi+" dpi";
        for(int id:deviceViews.keySet())set(deviceViews.get(id),values[id]);
    }
    private static String bytes(long bytes){return String.format(Locale.ROOT,"%.2f GiB",bytes/1073741824.0);}
}


