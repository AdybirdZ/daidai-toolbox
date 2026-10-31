package org.simpleweather.app;

import android.app.*;
import android.content.*;
import android.location.Location;
import android.os.*;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

final class EarthquakeScreen {
    static String[] sectionNames(){return new String[]{UiText.get("earthquake_001"),UiText.get("earthquake_002"),UiText.get("earthquake_003")};}
    static String[] dayNames(){return new String[]{UiText.get("earthquake_004"),UiText.get("earthquake_005"),UiText.get("earthquake_006"),UiText.get("earthquake_007")};}
    static final int[] DAY_VALUES={1,3,7,30};
    static String[] areas(){return new String[]{UiText.get("earthquake_008"),UiText.get("earthquake_009"),UiText.get("earthquake_010")};}
    static String[] sorts(){return new String[]{UiText.get("earthquake_011"),UiText.get("earthquake_012"),UiText.get("earthquake_013")};}
    private final MainActivity a;
    private final SharedPreferences p,limits;
    private final EarthquakeCache cache;
    private final ExecutorService workers=Executors.newFixedThreadPool(2);
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final Map<String,EarthquakeCache.Entry> data=new HashMap<>();
    private final Map<String,String> failures=new HashMap<>();
    private final Set<String> inFlight=new HashSet<>(),fromCache=new HashSet<>();
    private LinearLayout statusBox,listBox;
    private TextView refresh,summary,referenceText;
    private LinearLayout locationControls;
    private final EarthquakeLocation location;
    private String locationKey="";
    private int ticks;
    private volatile boolean visible,dead;
    private boolean cacheLoaded,cleared;
    private int shown=30,cycle,cycleGood,cycleTotal;
    private boolean cycleManual;
    private String restoredMetadata;
    void restoreState(Bundle state){if(state!=null){restoredMetadata=state.getString("quake_session");shown=Math.max(30,Math.min(10000,state.getInt("quake_shown",30)));}}
    void saveState(Bundle state){state.putString("quake_session",cacheLoaded&&restoredMetadata==null?EarthquakeRestore.snapshot(data,fromCache,failures):restoredMetadata);state.putInt("quake_shown",shown);}
    private final Runnable tick=new Runnable(){public void run(){if(visible&&!dead){if(ticks++%15==0){refresh(false);updateStatus();}updateReference();String key=referenceKey();if(!key.equals(locationKey)){locationKey=key;updateList();}ui.postDelayed(this,1000);}}};
    EarthquakeScreen(MainActivity activity){a=activity;p=a.preferences;if(p.contains("quake_intensity_scale")||p.contains("quake_mmi_default_113"))p.edit().remove("quake_intensity_scale").remove("quake_mmi_default_113").apply();limits=a.getSharedPreferences("earthquake_limits",0);cache=new EarthquakeCache(a.getFilesDir());location=new EarthquakeLocation(a,()->{updateReference();updateList();});}

    private int days(){int v=p.getInt("quake_days",7);for(int d:DAY_VALUES)if(v==d)return d;return 7;}
    private double global(){return EarthquakeOptions.magnitude(p.getFloat("quake_global_min",1));}
    private double regional(){return EarthquakeOptions.magnitude(p.getFloat("quake_regional_min",1));}
    private boolean enabled(String source){return source.equals("Wolfx")?p.getBoolean("quake_wolfx_ack_112",false)&&p.getBoolean("quake_source_Wolfx",false):p.getBoolean("quake_source_"+source,true);}
    void render(){
        visible=true;statusBox=null;listBox=null;summary=null;refresh=null;referenceText=null;locationControls=null;
        for(int section:DisplayOrder.parse(p.getString("quake_order",""),sectionNames().length)){
            if(!p.getBoolean("quake_show_"+section,true))continue;
            LinearLayout c=a.card();c.addView(a.text(sectionNames()[section],17,a.TITLE,true));a.gap(c,12);
            if(section==0)controls(c);
            else if(section==1){statusBox=a.column();c.addView(statusBox);}
            else if(section==2){listBox=a.column();c.addView(listBox);}
            a.gap(a.body,16);
        }
        updateStatus();updateList();
        if(!cacheLoaded){cacheLoaded=true;int generation=cache.generation();workers.execute(()->{Map<String,EarthquakeCache.Entry> loaded=new HashMap<>();for(String source:EarthquakeData.SOURCES){EarthquakeCache.Entry e=cache.read(source);if(e!=null)loaded.put(source,e);}ui.post(()->{if(dead||generation!=cache.generation())return;data.putAll(loaded);fromCache.addAll(loaded.keySet());EarthquakeRestore.apply(restoredMetadata,loaded,fromCache,failures);restoredMetadata=null;updateStatus();updateList();refresh(false);});});}
        resume();
    }
    private void controls(LinearLayout c){
        summary=a.text("",13,a.MUTED,false);c.addView(summary);updateControls();a.gap(c,12);
        refresh=a.button(inFlight.isEmpty()?UiText.get("earthquake_014"):UiText.get("earthquake_015"),true,()->refresh(true));c.addView(refresh);
        locationControls=a.column();c.addView(locationControls);updateLocationControls();
    }
    private void updateControls(){if(summary!=null)summary.setText(UiText.get("earthquake_016")+days()+UiText.get("earthquake_017")+areas()[area()].replace(UiText.get("color_default_suffix"),"")+(area()==2?" · "+p.getInt("quake_radius",1000)+" km":"")+UiText.get("earthquake_018")+String.format(Locale.ROOT,"%.1f",global())+UiText.get("earthquake_019")+String.format(Locale.ROOT,"%.1f",regional())+"\n"+sorts()[sort()].replace(UiText.get("color_default_suffix"),""));}
    private void updateLocationControls(){if(locationControls==null)return;locationControls.removeAllViews();if(!p.getBoolean("quake_manual_location",false)){a.gap(locationControls,10);locationControls.addView(a.button(UiText.get("earthquake_020"),false,()->location.start()));}}
    private void updateReference(){if(referenceText!=null)referenceText.setText(referenceLabel()+(location.status.isEmpty()?"":"\n"+location.status));}
    private String referenceKey(){double[] ref=reference();Location l=LocationService.last(a);return Arrays.toString(ref)+":"+(l==null?0:l.getTime());}
    void locationPermissionResult(){location.permissionResult(visible&&!dead);}

    private int dayIndex(){for(int i=0;i<DAY_VALUES.length;i++)if(DAY_VALUES[i]==days())return i;return 2;}
    private int area(){return Math.max(0,Math.min(2,p.getInt("quake_area",0)));}
    private int sort(){return Math.max(0,Math.min(2,p.getInt("quake_sort",0)));}
    private ZoneId zone(){return EarthquakeOptions.zone(p.getInt("global_time_mode",p.getInt("quake_timezone",0)));}
    private String time(long t){return EarthquakeOptions.time(t,zone());}

    private double[] reference(){
        if(p.getBoolean("quake_manual_location",false)){if(!p.contains("quake_ref_lat")||!p.contains("quake_ref_lon"))return null;double lat=p.getFloat("quake_ref_lat",0),lon=p.getFloat("quake_ref_lon",0);return new double[]{lat,lon};}
        Location l=LocationService.last(a);return l==null?null:new double[]{l.getLatitude(),l.getLongitude()};
    }
    private String referenceLabel(){if(p.getBoolean("quake_manual_location",false)&&(!p.contains("quake_ref_lat")||!p.contains("quake_ref_lon")))return UiText.get("earthquake_021");if(p.getBoolean("quake_manual_location",false))return UiText.get("earthquake_022")+CoordinateFormat.pair(p.getFloat("quake_ref_lon",0),p.getFloat("quake_ref_lat",0),5);Location l=LocationService.last(a);if(l==null)return UiText.get("earthquake_023");long age=LocationService.ageSeconds(l);return UiText.get("earthquake_024")+age+UiText.get("earthquake_025")+(age>300?UiText.get("earthquake_026"):"")+(" · "+CoordinateFormat.pair(l.getLongitude(),l.getLatitude(),5));}

    private String distance(EarthquakeData.Report r){double[] ref=reference();return ref==null?UiText.get("earthquake_027"):String.format(Locale.ROOT,UiText.get("earthquake_028"),p.getBoolean("quake_manual_location",false)?UiText.get("earthquake_029"):UiText.get("earthquake_030"),EarthquakeData.distance(ref[0],ref[1],r.latitude,r.longitude));}
    private String depth(EarthquakeData.Report r){return Double.isFinite(r.depth)?String.format(Locale.ROOT,UiText.get("earthquake_031"),r.depth):UiText.get("earthquake_032");}
    private String intensity(EarthquakeData.Event event){return EarthquakeIntensity.summary(event);}
    private void updateStatus(){
        if(!visible||statusBox==null)return;statusBox.removeAllViews();long now=System.currentTimeMillis();
        for(String source:EarthquakeData.SOURCES){EarthquakeCache.Entry e=data.get(source);String s=EarthquakeData.sourceName(source)+" · ";if(!enabled(source))s+=UiText.get("earthquake_033");else if(inFlight.contains(source))s+=UiText.get("earthquake_034");else if(failures.containsKey(source))s+=failures.get(source)+(e==null?"":UiText.get("earthquake_035"));else s+=e==null?UiText.get("earthquake_036"):now-e.fetched>Math.max(900_000,p.getLong("quake_interval",900_000))?UiText.get("earthquake_037"):fromCache.contains(source)?UiText.get("earthquake_038"):UiText.get("earthquake_039");
            statusBox.addView(a.text(s,14,a.INK,false));if(e!=null){if(!source.equals("Wolfx")&&(e.since>now-days()*86400_000L+60_000||e.minimum>Math.min(global(),regional())))statusBox.addView(a.text(UiText.get("earthquake_040"),12,a.MUTED,false));statusBox.addView(a.text(UiText.get("earthquake_041")+time(e.fetched),12,a.MUTED,false));if(source.equals("Wolfx"))statusBox.addView(a.text(UiText.get("earthquake_042")+e.reports.size()+UiText.get("earthquake_043")+(e.reports.isEmpty()?"":UiText.get("earthquake_044")+time(Collections.min(e.reports,Comparator.comparingLong(r->r.time)).time)),12,a.MUTED,false));if(!source.equals("Wolfx")&&e.reports.size()>=EarthquakeData.LIMIT)statusBox.addView(a.text(UiText.get("earthquake_045"),12,a.MUTED,false));}a.gap(statusBox,10);
        }
        if(sort()==2&&reference()==null)statusBox.addView(a.text(UiText.get("earthquake_046"),12,a.MUTED,false));
        referenceText=a.text("",12,a.MUTED,false);statusBox.addView(referenceText);updateReference();
        if(refresh!=null)refresh.setText(inFlight.isEmpty()?UiText.get("earthquake_014"):UiText.get("earthquake_015"));
    }
    private void updateList(){
        if(!visible||listBox==null)return;listBox.removeAllViews();List<EarthquakeData.Report> all=new ArrayList<>();for(String source:EarthquakeData.SOURCES)if(enabled(source)&&data.containsKey(source))all.addAll(data.get(source).reports);
        long now=System.currentTimeMillis();List<EarthquakeData.Event> events=EarthquakeData.filter(EarthquakeData.merge(all),now-days()*86400_000L,now,area(),global(),regional(),reference(),p.getInt("quake_radius",1000),sort());
        listBox.addView(a.text(UiText.get("earthquake_047")+events.size()+UiText.get("earthquake_048"),13,a.MUTED,false));a.gap(listBox,12);
        if(events.isEmpty()){listBox.addView(a.text(area()==2&&reference()==null?UiText.get("earthquake_049"):all.isEmpty()?UiText.get("earthquake_050"):UiText.get("earthquake_051"),14,a.INK,false));return;}
        for(int i=0;i<Math.min(shown,events.size());i++){
            EarthquakeData.Event event=events.get(i);EarthquakeData.Report r=event.main();LinearLayout item=a.column();item.setPadding(a.dp(14),a.dp(16),a.dp(14),a.dp(16));
            item.addView(a.text(r.magnitudeLabel()+" · "+region(r.region),17,r.magnitude>=6?0xFF9D4618:a.INK,true));a.gap(item,10);item.addView(a.text(UiText.get("earthquake_052")+time(r.time),14,a.INK,false));a.gap(item,7);
            List<String> facts=new ArrayList<>();if(p.getBoolean("quake_field_depth",true))facts.add(depth(r));if(p.getBoolean("quake_field_distance",true))facts.add(distance(r));if(!facts.isEmpty())item.addView(a.text(String.join(" · ",facts),13,a.MUTED,false));
            if(p.getBoolean("quake_field_intensity",true)){a.gap(item,6);item.addView(a.text(intensity(event),13,a.MUTED,false));}
            if(p.getBoolean("quake_field_source",true)){a.gap(item,6);item.addView(a.text(UiText.get("earthquake_053")+EarthquakeDisplay.sources(event),12,a.MUTED,false));}
            if(p.getBoolean("quake_field_review",true)){a.gap(item,6);item.addView(a.text(EarthquakeDisplay.review(r),12,a.MUTED,false));}
            a.tap(item,0xFFF5F8FD,16,()->details(event));listBox.addView(item,a.lp(-1,-2));a.gap(listBox,10);
        }
        if(shown<events.size())listBox.addView(a.button(UiText.get("earthquake_054")+(events.size()-shown)+UiText.get("earthquake_055"),false,()->{shown+=30;updateList();}));
    }
    private void details(EarthquakeData.Event event){
        LinearLayout c=form();EarthquakeData.Report main=event.main();c.addView(a.text(region(main.region),18,a.TITLE,true));a.gap(c,12);c.addView(a.text(UiText.get("earthquake_056"),13,a.MUTED,false));a.gap(c,16);
        for(EarthquakeData.Report r:event.reports){c.addView(a.text(EarthquakeData.sourceName(r.source)+" · "+r.magnitudeLabel(),17,a.TITLE,true));a.gap(c,9);TextView info=a.text(UiText.get("earthquake_057")+r.id+UiText.get("earthquake_058")+time(r.time)+"\n"+(r.source.equals("Wolfx")?UiText.get("earthquake_059"):r.source.equals("GEOFON")?UiText.get("earthquake_060"):UiText.get("earthquake_061"))+time(r.updated)+"\n"+EarthquakeOptions.offset(zone(),r.time)+UiText.get("earthquake_062")+region(r.region)+("\n"+CoordinateFormat.pair(r.longitude,r.latitude,5))+"\n"+depth(r)+(r.depthNote.isEmpty()?"":"\n"+r.depthNote)+"\n"+distance(r)+UiText.get("earthquake_063")+EarthquakeDisplay.review(r)+(EarthquakeDisplay.review(r).equals(UiText.get("earthquake_display_002"))?"":UiText.get("earthquake_064")+UiText.label(r.review))+(r.agency.isEmpty()?"":UiText.get("earthquake_065")+EarthquakeAgencies.name(r.agency)),14,a.INK,false);c.addView(info);a.gap(c,10);a.link(c,EarthquakeData.sourceName(r.source)+(r.source.equals("Wolfx")?UiText.get("earthquake_066"):UiText.get("earthquake_067")),r.url());if(r.source.equals("Wolfx")){a.link(c,UiText.get("earthquake_068"),"https://wolfx.jp/zh/docs/open-api/");c.addView(a.text(UiText.get("earthquake_069"),13,a.MUTED,false));}a.gap(c,12);}
        c.addView(a.text(EarthquakeIntensity.label(event),14,a.INK,false));a.gap(c,10);c.addView(a.text(UiText.get("earthquake_070"),13,a.MUTED,false));dialog(UiText.get("earthquake_071"),c);
    }

    private static String region(String value){String v=value.toUpperCase(Locale.ROOT);String[][] names={{"XIZANG",UiText.get("earthquake_072")},{"XIZANG-QINGHAI BORDER REGION",UiText.get("earthquake_073")},{"SOUTHERN XINJIANG, CHINA",UiText.get("earthquake_074")},{"NORTHERN XINJIANG, CHINA",UiText.get("earthquake_075")},{"SICHUAN, CHINA",UiText.get("earthquake_076")},{"QINGHAI, CHINA",UiText.get("earthquake_077")},{"YUNNAN, CHINA",UiText.get("earthquake_078")},{"TAIWAN REGION",UiText.get("earthquake_079")},{"TAIWAN",UiText.get("earthquake_080")},{"NEAR EAST COAST OF HONSHU, JAPAN",UiText.get("earthquake_081")}};for(String[] n:names)if(v.equals(n[0]))return n[1]+UiText.get("earthquake_info_005")+value+UiText.get("earthquake_api_004");return value;}

    void refresh(boolean manual){
        if(dead||!cacheLoaded||!visible||cleared&&!manual)return;if(manual)cleared=false;if(!inFlight.isEmpty()){if(manual)a.toast(UiText.get("earthquake_082"));return;}
        if(!manual&&p.getLong("quake_interval",900_000)<=0)return;
        long now=System.currentTimeMillis(),since=now-days()*86400_000L;double min=Math.min(global(),regional());List<String> jobs=new ArrayList<>();
        for(String source:EarthquakeData.SOURCES){if(!enabled(source)||limits.getLong("until_"+source,0)>now)continue;EarthquakeCache.Entry e=data.get(source);boolean covers=e!=null&&(source.equals("Wolfx")||e.since<=since+60_000&&e.minimum<=min);long interval=p.getLong("quake_interval",900_000);String query=queryKey(source,days(),min);boolean sameQuery=query.equals(limits.getString("query_"+source,""));long previous=limits.getLong("attempt_"+source,0);if(!manual&&sameQuery&&previous>0&&now-previous<Math.max(60_000,interval))continue;if(manual||!covers||interval>0&&now-e.fetched>=interval)jobs.add(source);}
        if(jobs.isEmpty()){if(manual)a.toast(UiText.get("earthquake_083"));return;}
        cycle++;int thisCycle=cycle;cycleGood=0;cycleTotal=jobs.size();cycleManual=manual;inFlight.addAll(jobs);updateStatus();int generation=cache.generation();
        for(String source:jobs){String query=queryKey(source,days(),min);workers.execute(()->{
            // A queued job must recheck consent, source selection and foreground state before requesting.
            if(dead||!visible||!enabled(source)||generation!=cache.generation()){ui.post(()->{if(dead||generation!=cache.generation()||thisCycle!=cycle)return;inFlight.remove(source);cycleTotal--;updateStatus();});return;}
            limits.edit().putLong("attempt_"+source,System.currentTimeMillis()).putString("query_"+source,query).apply();
            EarthquakeCache.Entry entry=null;String failure=null;boolean saved=false;
            try{EarthquakeApi.Result result=EarthquakeApi.fetch(source,since,min,new EarthquakeApi.Limits(){public long until(String s){return limits.getLong("until_"+s,0);}public void until(String s,long t){limits.edit().putLong("until_"+s,t).commit();}});entry=new EarthquakeCache.Entry();entry.raw=result.raw;entry.reports=result.reports;entry.fetched=System.currentTimeMillis();entry.since=since;entry.minimum=min;saved=cache.save(source,entry,generation);}catch(Exception ex){android.util.Log.w("Earthquake",source+" catalogue request failed",ex);String msg=ex.getMessage();failure=msg!=null&&msg.matches(".*[\\u4e00-\\u9fff].*")?msg:UiText.get("earthquake_084");}
            final EarthquakeCache.Entry result=entry;final String error=failure;final boolean persisted=saved;
            ui.post(()->{if(dead||generation!=cache.generation()||thisCycle!=cycle)return;inFlight.remove(source);if(result!=null){data.put(source,result);fromCache.remove(source);cycleGood++;if(persisted)failures.remove(source);else failures.put(source,UiText.get("earthquake_085"));}else failures.put(source,error);updateStatus();updateList();if(inFlight.isEmpty()&&cycleManual)a.toast(cycleGood==cycleTotal?UiText.get("earthquake_086"):cycleGood>0?UiText.get("earthquake_087"):UiText.get("earthquake_088"));});
        });}
    }
    private static String queryKey(String source,int days,double min){return source.equals("Wolfx")?"latest50":days+":"+min;}
    void pause(){visible=false;ui.removeCallbacks(tick);location.pause();}
    void resume(){visible=true;location.resume();ui.removeCallbacks(tick);ui.postDelayed(tick,1000);}
    void destroy(){dead=true;pause();location.destroy();workers.shutdownNow();ui.removeCallbacksAndMessages(null);}
    boolean clear(){cleared=true;cycle++;inFlight.clear();data.clear();fromCache.clear();failures.clear();boolean ok=cache.clear();updateStatus();updateList();return ok;}
    void changed(){shown=30;if(visible){updateControls();updateLocationControls();updateStatus();updateList();refresh(false);}}
    private LinearLayout form(){LinearLayout c=a.column();c.setPadding(a.dp(18),a.dp(8),a.dp(18),a.dp(16));return c;}
    private void dialog(String title,LinearLayout c){ScrollView s=new ScrollView(a);s.addView(c);new ToolDialog(a).setTitle(title).setView(s).setNegativeButton(UiText.get("action_back"),null).show();}
    private void action(LinearLayout c,String title,Runnable r){c.addView(a.button(title,false,r));a.gap(c,10);}
    private interface Choice {void select(int n);}
    private void choose(String title,String[] labels,int selected,Choice callback){new SettingsScreen(a).singleChoice(title,title.equals(UiText.get("earthquake_092"))?"":null,labels,selected,callback::select);}
    void settings(){
        LinearLayout c=form();action(c,UiText.get("earthquake_089"),()->choose(UiText.get("earthquake_089"),dayNames(),dayIndex(),n->{p.edit().putInt("quake_days",DAY_VALUES[n]).apply();changed();}));
        action(c,UiText.get("earthquake_090"),this::areaSettings);
        action(c,UiText.get("earthquake_091"),this::magnitudeSettings);

        action(c,UiText.get("earthquake_092"),()->choose(UiText.get("earthquake_092"),sorts(),sort(),n->{p.edit().putInt("quake_sort",n).apply();changed();}));
        action(c,UiText.get("earthquake_093"),()->choose(UiText.get("earthquake_094"),new String[]{UiText.get("action_close"),UiText.get("earthquake_095"),UiText.get("earthquake_096"),UiText.get("earthquake_097"),UiText.get("earthquake_098")},index(new int[]{0,300000,900000,1800000,3600000},(int)p.getLong("quake_interval",900000)),n->{p.edit().putLong("quake_interval",new long[]{0,300000,900000,1800000,3600000}[n]).apply();changed();}));
        action(c,UiText.get("earthquake_099"),this::referenceSettings);action(c,UiText.get("earthquake_100"),this::fields);action(c,UiText.get("earthquake_101"),()->new SettingsScreen(a).earthquakeOrder());action(c,UiText.get("earthquake_102"),this::sources);action(c,UiText.get("earthquake_info_038"),()->EarthquakeInfo.terms(a));action(c,UiText.get("earthquake_103"),()->new ToolDialog(a).setTitle(UiText.get("earthquake_104")).setMessage(UiText.get("earthquake_105")).setPositiveButton(UiText.get("earthquake_106"),(d,n)->{a.toast(clear()?UiText.get("earthquake_107"):UiText.get("earthquake_108"));}).setNegativeButton(UiText.get("action_cancel"),null).show());
        dialog(UiText.get("earthquake_109"),c);
    }
    private int index(int[] values,int v){for(int i=0;i<values.length;i++)if(values[i]==v)return i;return 0;}
    private void magnitudeSettings(){LinearLayout c=form();magnitudeSlider(c,UiText.get("earthquake_110"),"quake_global_min");magnitudeSlider(c,UiText.get("earthquake_111"),"quake_regional_min");c.addView(a.text(UiText.get("earthquake_112"),13,a.MUTED,false));dialog(UiText.get("earthquake_091"),c);}
    private void magnitudeSlider(LinearLayout c,String name,String key){
        TextView value=a.text("",16,a.INK,true);c.addView(value);SeekBar slider=new SeekBar(a);slider.setMax(70);slider.setKeyProgressIncrement(1);slider.setProgress(Math.round(EarthquakeOptions.magnitude(p.getFloat(key,1))*10));Runnable label=()->{String text=name+" · M≥"+String.format(Locale.ROOT,"%.1f",slider.getProgress()/10f);value.setText(text);slider.setContentDescription(text);};label.run();
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int n,boolean user){label.run();if(user){p.edit().putFloat(key,n/10f).apply();updateControls();updateList();}}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){changed();}});c.addView(slider,a.lp(-1,56));a.gap(c,12);
    }
    private void areaSettings(){
        LinearLayout c=form();
        RadioGroup group=new RadioGroup(a);for(int i=0;i<areas().length;i++){RadioButton b=new RadioButton(a);b.setId(200+i);b.setText(areas()[i]);b.setMinHeight(a.dp(48));group.addView(b);}group.check(200+area());c.addView(group);
        LinearLayout nearby=a.column();a.gap(nearby,12);nearby.addView(a.text(UiText.get("earthquake_114"),14,a.INK,true));EditText radius=new EditText(a);radius.setSingleLine(true);radius.setInputType(InputType.TYPE_CLASS_NUMBER);radius.setText(Integer.toString(p.getInt("quake_radius",1000)));radius.setContentDescription(UiText.get("earthquake_115"));nearby.addView(radius);a.gap(nearby,10);nearby.addView(a.button(UiText.get("earthquake_116"),false,()->{int n=EarthquakeOptions.radius(radius.getText().toString());if(n<0){radius.setError(UiText.get("earthquake_117"));return;}p.edit().putInt("quake_radius",n).apply();changed();a.toast(UiText.get("earthquake_118"));}));nearby.setVisibility(area()==2?View.VISIBLE:View.GONE);c.addView(nearby);
        group.setOnCheckedChangeListener((g,id)->{p.edit().putInt("quake_area",id-200).apply();nearby.setVisibility(id==202?View.VISIBLE:View.GONE);changed();});a.gap(c,12);TextParagraphs.append(a,c,UiText.get("earthquake_113"),13,a.MUTED);dialog(UiText.get("earthquake_090"),c);
    }
    private void referenceSettings(){
        LinearLayout c=form();RadioGroup group=new RadioGroup(a);String[] names={UiText.get("earthquake_119"),UiText.get("earthquake_120")};for(int i=0;i<2;i++){RadioButton b=new RadioButton(a);b.setId(210+i);b.setText(names[i]);b.setMinHeight(a.dp(48));group.addView(b);}group.check(p.getBoolean("quake_manual_location",false)?211:210);c.addView(group);a.gap(c,12);
        LinearLayout inputs=a.column();EditText lon=coordinate(inputs,UiText.get("earthquake_121"),p.getFloat("quake_ref_lon",0)),lat=coordinate(inputs,UiText.get("earthquake_122"),p.getFloat("quake_ref_lat",0));lon.setContentDescription(UiText.get("earthquake_123"));lat.setContentDescription(UiText.get("earthquake_124"));TextView preview=a.text("",13,a.MUTED,false);inputs.addView(preview);a.gap(inputs,10);android.text.TextWatcher watcher=new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){try{preview.setText(CoordinateFormat.pair(Double.parseDouble(lon.getText().toString()),Double.parseDouble(lat.getText().toString()),5));}catch(Exception ex){preview.setText(UiText.get("earthquake_125"));}}public void afterTextChanged(android.text.Editable s){}};lon.addTextChangedListener(watcher);lat.addTextChangedListener(watcher);watcher.onTextChanged("",0,0,0);inputs.addView(a.button(UiText.get("earthquake_126"),false,()->{try{float x=Float.parseFloat(lat.getText().toString().trim()),y=Float.parseFloat(lon.getText().toString().trim());if(!Float.isFinite(x)||!Float.isFinite(y)||Math.abs(x)>90||Math.abs(y)>180)throw new IllegalArgumentException();p.edit().putFloat("quake_ref_lat",x).putFloat("quake_ref_lon",y).apply();changed();a.toast(UiText.get("earthquake_127"));}catch(Exception ex){a.toast(UiText.get("earthquake_128"));}}));inputs.setVisibility(p.getBoolean("quake_manual_location",false)?View.VISIBLE:View.GONE);c.addView(inputs);a.gap(c,12);c.addView(a.text(UiText.get("earthquake_129"),13,a.MUTED,false));
        group.setOnCheckedChangeListener((g,id)->{boolean manual=id==211;p.edit().putBoolean("quake_manual_location",manual).apply();if(manual){location.pause();location.status="";}inputs.setVisibility(manual?View.VISIBLE:View.GONE);changed();});dialog(UiText.get("earthquake_099"),c);
    }

    private EditText coordinate(LinearLayout c,String name,float value){c.addView(a.text(name,14,a.INK,false));EditText e=new EditText(a);e.setSingleLine(true);e.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL|InputType.TYPE_NUMBER_FLAG_SIGNED);e.setText(Float.toString(value));c.addView(e);a.gap(c,12);return e;}
    private void fields(){LinearLayout c=form();String[] keys={"depth","distance","intensity","source","review"},names={UiText.get("earthquake_130"),UiText.get("earthquake_131"),UiText.get("earthquake_132"),UiText.get("earthquake_133"),UiText.get("earthquake_134")};for(int i=0;i<keys.length;i++)toggle(c,names[i],"quake_field_"+keys[i],true);c.addView(a.text(UiText.get("earthquake_135"),13,a.MUTED,false));dialog(UiText.get("earthquake_100"),c);}
    private void toggle(LinearLayout c,String name,String key,boolean def){CheckBox b=new CheckBox(a);b.setText(name);b.setChecked(p.getBoolean(key,def));b.setOnCheckedChangeListener((v,on)->{p.edit().putBoolean(key,on).apply();changed();});c.addView(b,a.lp(-1,52));}
    private void sources(){LinearLayout c=form();for(String source:EarthquakeData.SOURCES){
        CheckBox b=new CheckBox(a);b.setText(EarthquakeData.sourceName(source)+(source.equals("Wolfx")?"":source.equals("USGS")?UiText.get("earthquake_137"):" · CC BY 4.0"));b.setChecked(enabled(source));boolean[] updating={false};
        b.setOnCheckedChangeListener((v,on)->{if(updating[0])return;if(source.equals("Wolfx")&&on){updating[0]=true;b.setChecked(false);updating[0]=false;new ToolDialog(a).setTitle(UiText.get("earthquake_138")).setView(TextParagraphs.notice(a,EarthquakeInfo.wolfxNotice())).setPositiveButton(UiText.get("earthquake_139"),(d,n)->{p.edit().putBoolean("quake_wolfx_ack_112",true).putBoolean("quake_source_Wolfx",true).apply();updating[0]=true;b.setChecked(true);updating[0]=false;changed();}).setNegativeButton(UiText.get("action_cancel"),null).show();return;}
            if(!on){int count=0;for(String item:EarthquakeData.SOURCES)if(enabled(item))count++;if(count==1){updating[0]=true;b.setChecked(true);updating[0]=false;a.toast(UiText.get("earthquake_140"));return;}}
            p.edit().putBoolean("quake_source_"+source,on).apply();changed();});b.setMinHeight(a.dp(52));c.addView(b,a.lp(-1,-2));a.gap(c,6);
        }a.gap(c,10);c.addView(a.text(UiText.get("earthquake_141"),13,a.MUTED,false));dialog(UiText.get("earthquake_142"),c);}


}
