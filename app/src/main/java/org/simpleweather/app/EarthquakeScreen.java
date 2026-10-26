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
    static final String[] SECTION_NAMES={"筛选与刷新","更新状态","地震列表"};
    static final String[] DAYS={"最近 1 天","最近 3 天","最近 7 天（默认）","最近 30 天"};
    static final int[] DAY_VALUES={1,3,7,30};
    static final String[] AREAS={"全球（默认）","中国及周边","参考位置附近"};
    static final String[] SORTS={"按最新发生排序（默认）","按震级从大到小排序","按距离从近到远排序"};
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
    private final Runnable tick=new Runnable(){public void run(){if(visible&&!dead){if(ticks++%15==0){refresh(false);updateStatus();}updateReference();String key=referenceKey();if(!key.equals(locationKey)){locationKey=key;updateList();}ui.postDelayed(this,1000);}}};
    EarthquakeScreen(MainActivity activity){a=activity;p=a.preferences;if(p.contains("quake_intensity_scale")||p.contains("quake_mmi_default_113"))p.edit().remove("quake_intensity_scale").remove("quake_mmi_default_113").apply();limits=a.getSharedPreferences("earthquake_limits",0);cache=new EarthquakeCache(a.getFilesDir());location=new EarthquakeLocation(a,()->{updateReference();updateList();});}

    private int days(){int v=p.getInt("quake_days",7);for(int d:DAY_VALUES)if(v==d)return d;return 7;}
    private double global(){return EarthquakeOptions.magnitude(p.getFloat("quake_global_min",1));}
    private double regional(){return EarthquakeOptions.magnitude(p.getFloat("quake_regional_min",1));}
    private boolean enabled(String source){return source.equals("Wolfx")?p.getBoolean("quake_wolfx_ack_112",false)&&p.getBoolean("quake_source_Wolfx",false):p.getBoolean("quake_source_"+source,true);}
    void render(){
        visible=true;statusBox=null;listBox=null;summary=null;refresh=null;referenceText=null;locationControls=null;
        for(int section:DisplayOrder.parse(p.getString("quake_order",""),SECTION_NAMES.length)){
            if(!p.getBoolean("quake_show_"+section,true))continue;
            LinearLayout c=a.card();c.addView(a.text(SECTION_NAMES[section],17,a.INK,true));a.gap(c,12);
            if(section==0)controls(c);
            else if(section==1){statusBox=a.column();c.addView(statusBox);}
            else if(section==2){listBox=a.column();c.addView(listBox);}
            a.gap(a.body,16);
        }
        updateStatus();updateList();
        if(!cacheLoaded){cacheLoaded=true;int generation=cache.generation();workers.execute(()->{Map<String,EarthquakeCache.Entry> loaded=new HashMap<>();for(String source:EarthquakeData.SOURCES){EarthquakeCache.Entry e=cache.read(source);if(e!=null)loaded.put(source,e);}ui.post(()->{if(dead||generation!=cache.generation())return;data.putAll(loaded);fromCache.addAll(loaded.keySet());updateStatus();updateList();refresh(false);});});}
        resume();
    }
    private void controls(LinearLayout c){
        summary=a.text("",13,a.MUTED,false);c.addView(summary);updateControls();a.gap(c,12);
        refresh=a.button(inFlight.isEmpty()?"刷新目录":"正在更新…",true,()->refresh(true));c.addView(refresh);
        locationControls=a.column();c.addView(locationControls);updateLocationControls();
    }
    private void updateControls(){if(summary!=null)summary.setText("最近 "+days()+" 天 · "+AREAS[area()].replace("（默认）","")+(area()==2?" · "+p.getInt("quake_radius",1000)+" km":"")+" · 全球 M≥"+String.format(Locale.ROOT,"%.1f",global())+" / 中国及周边 M≥"+String.format(Locale.ROOT,"%.1f",regional())+"\n"+SORTS[sort()].replace("（默认）",""));}
    private void updateLocationControls(){if(locationControls==null)return;locationControls.removeAllViews();if(!p.getBoolean("quake_manual_location",false)){a.gap(locationControls,10);locationControls.addView(a.button("刷新定位",false,()->location.start()));}}
    private void updateReference(){if(referenceText!=null)referenceText.setText(referenceLabel()+(location.status.isEmpty()?"":"\n"+location.status));}
    private String referenceKey(){double[] ref=reference();Location l=LocationService.last(a);return Arrays.toString(ref)+":"+(l==null?0:l.getTime());}
    void locationPermissionResult(){location.permissionResult(visible&&!dead);}

    private int dayIndex(){for(int i=0;i<DAY_VALUES.length;i++)if(DAY_VALUES[i]==days())return i;return 2;}
    private int area(){return Math.max(0,Math.min(2,p.getInt("quake_area",0)));}
    private int sort(){return Math.max(0,Math.min(2,p.getInt("quake_sort",0)));}
    private ZoneId zone(){return EarthquakeOptions.zone(p.getInt("quake_timezone",0));}
    private String time(long t){return EarthquakeOptions.time(t,zone());}

    private double[] reference(){
        if(p.getBoolean("quake_manual_location",false)){if(!p.contains("quake_ref_lat")||!p.contains("quake_ref_lon"))return null;double lat=p.getFloat("quake_ref_lat",0),lon=p.getFloat("quake_ref_lon",0);return new double[]{lat,lon};}
        Location l=LocationService.last(a);return l==null?null:new double[]{l.getLatitude(),l.getLongitude()};
    }
    private String referenceLabel(){if(p.getBoolean("quake_manual_location",false)&&(!p.contains("quake_ref_lat")||!p.contains("quake_ref_lon")))return "手动参考位置 · 尚未保存经纬度";if(p.getBoolean("quake_manual_location",false))return "手动参考位置 · "+CoordinateFormat.pair(p.getFloat("quake_ref_lon",0),p.getFloat("quake_ref_lat",0),5);Location l=LocationService.last(a);if(l==null)return "未获取定位，距离暂不可用";long age=LocationService.ageSeconds(l);return "最近定位 · "+age+" 秒前"+(age>300?"（较旧）":"")+(" · "+CoordinateFormat.pair(l.getLongitude(),l.getLatitude(),5));}

    private String distance(EarthquakeData.Report r){double[] ref=reference();return ref==null?"未获取位置":String.format(Locale.ROOT,"距%s %.0f km",p.getBoolean("quake_manual_location",false)?"参考位置":"最近定位",EarthquakeData.distance(ref[0],ref[1],r.latitude,r.longitude));}
    private String depth(EarthquakeData.Report r){return Double.isFinite(r.depth)?String.format(Locale.ROOT,"深度 %.1f km",r.depth):"未提供深度";}
    private String intensity(EarthquakeData.Event event){return EarthquakeIntensity.summary(event);}
    private void updateStatus(){
        if(!visible||statusBox==null)return;statusBox.removeAllViews();long now=System.currentTimeMillis();
        for(String source:EarthquakeData.SOURCES){EarthquakeCache.Entry e=data.get(source);String s=EarthquakeData.sourceName(source)+" · ";if(!enabled(source))s+="已关闭";else if(inFlight.contains(source))s+="更新中";else if(failures.containsKey(source))s+=failures.get(source)+(e==null?"":"，保留上次结果");else s+=e==null?"尚未获取":now-e.fetched>Math.max(900_000,p.getLong("quake_interval",900_000))?"缓存较旧":fromCache.contains(source)?"本机缓存":"已获取";
            statusBox.addView(a.text(s,14,a.INK,false));if(e!=null){if(!source.equals("Wolfx")&&(e.since>now-days()*86400_000L+60_000||e.minimum>Math.min(global(),regional())))statusBox.addView(a.text("现有缓存未覆盖当前筛选范围，等待联网刷新。",12,a.MUTED,false));statusBox.addView(a.text("本机成功获取："+time(e.fetched),12,a.MUTED,false));if(source.equals("Wolfx"))statusBox.addView(a.text("仅含接口最近最多 50 条，不保证覆盖所选天数；本次 "+e.reports.size()+" 条"+(e.reports.isEmpty()?"":" · 最早一条 "+time(Collections.min(e.reports,Comparator.comparingLong(r->r.time)).time)),12,a.MUTED,false));if(!source.equals("Wolfx")&&e.reports.size()>=EarthquakeData.LIMIT)statusBox.addView(a.text("达到单次条数上限，结果可能不完整，请缩小范围。",12,a.MUTED,false));}a.gap(statusBox,10);
        }
        if(sort()==2&&reference()==null)statusBox.addView(a.text("尚无参考位置，暂按最新发生排列。",12,a.MUTED,false));
        referenceText=a.text("",12,a.MUTED,false);statusBox.addView(referenceText);updateReference();
        if(refresh!=null)refresh.setText(inFlight.isEmpty()?"刷新目录":"正在更新…");
    }
    private void updateList(){
        if(!visible||listBox==null)return;listBox.removeAllViews();List<EarthquakeData.Report> all=new ArrayList<>();for(String source:EarthquakeData.SOURCES)if(enabled(source)&&data.containsKey(source))all.addAll(data.get(source).reports);
        long now=System.currentTimeMillis();List<EarthquakeData.Event> events=EarthquakeData.filter(EarthquakeData.merge(all),now-days()*86400_000L,now,area(),global(),regional(),reference(),p.getInt("quake_radius",1000),sort());
        listBox.addView(a.text("符合筛选："+events.size()+" 条 · 点击记录展开详情",13,a.MUTED,false));a.gap(listBox,12);
        if(events.isEmpty()){listBox.addView(a.text(area()==2&&reference()==null?"尚未获取参考位置，请先定位或手动设置。":all.isEmpty()?"尚无可显示目录。联网刷新后可查看；请求失败时会保留已有缓存。":"当前已获取目录中没有符合筛选的记录，可调整时间范围和最低震级。这不表示该区域没有发生地震。",14,a.INK,false));return;}
        for(int i=0;i<Math.min(shown,events.size());i++){
            EarthquakeData.Event event=events.get(i);EarthquakeData.Report r=event.main();LinearLayout item=a.column();item.setPadding(a.dp(14),a.dp(16),a.dp(14),a.dp(16));
            item.addView(a.text(r.magnitudeLabel()+" · "+region(r.region),17,r.magnitude>=6?0xFF9D4618:a.INK,true));a.gap(item,10);item.addView(a.text("发生时刻："+time(r.time),14,a.INK,false));a.gap(item,7);
            List<String> facts=new ArrayList<>();if(p.getBoolean("quake_field_depth",true))facts.add(depth(r));if(p.getBoolean("quake_field_distance",true))facts.add(distance(r));if(!facts.isEmpty())item.addView(a.text(String.join(" · ",facts),13,a.MUTED,false));
            if(p.getBoolean("quake_field_intensity",true)){a.gap(item,6);item.addView(a.text(intensity(event),13,a.MUTED,false));}
            if(p.getBoolean("quake_field_source",true)){a.gap(item,6);item.addView(a.text("来源："+EarthquakeDisplay.sources(event),12,a.MUTED,false));}
            if(p.getBoolean("quake_field_review",true)){a.gap(item,6);item.addView(a.text(EarthquakeDisplay.review(r),12,a.MUTED,false));}
            a.tap(item,0xFFF5F8FD,16,()->details(event));listBox.addView(item,a.lp(-1,-2));a.gap(listBox,10);
        }
        if(shown<events.size())listBox.addView(a.button("加载更多（还有 "+(events.size()-shown)+" 条）",false,()->{shown+=30;updateList();}));
    }
    private void details(EarthquakeData.Event event){
        LinearLayout c=form();EarthquakeData.Report main=event.main();c.addView(a.text(region(main.region),18,a.INK,true));a.gap(c,12);c.addView(a.text("不同来源的震级类型、深度和位置可能不同。本应用不取平均；优先展示人工处理结果，其次德国地学研究中心，同优先级保留最新版本。近时近地且匹配明确才合并，密集余震可能仍分开显示。",13,a.MUTED,false));a.gap(c,16);
        for(EarthquakeData.Report r:event.reports){c.addView(a.text(EarthquakeData.sourceName(r.source)+" · "+r.magnitudeLabel(),17,a.BLUE,true));a.gap(c,9);TextView info=a.text("事件 ID："+r.id+"\n发生时间："+time(r.time)+"\n"+(r.source.equals("Wolfx")?"来源报告时间：":r.source.equals("GEOFON")?"来源参数生成时刻：":"来源更新时间：")+time(r.updated)+"\n"+EarthquakeOptions.offset(zone(),r.time)+"\n震中："+region(r.region)+("\n"+CoordinateFormat.pair(r.longitude,r.latitude,5))+"\n"+depth(r)+(r.depthNote.isEmpty()?"":"\n"+r.depthNote)+"\n"+distance(r)+"\n状态："+EarthquakeDisplay.review(r)+(EarthquakeDisplay.review(r).equals("状态未知")?"":"\n来源处理标记："+r.review)+(r.agency.isEmpty()?"":"\n贡献机构："+EarthquakeAgencies.name(r.agency)),14,a.INK,false);info.setTextIsSelectable(true);c.addView(info);a.gap(c,10);a.link(c,EarthquakeData.sourceName(r.source)+(r.source.equals("Wolfx")?" · 中国地震台网中心官网":" · 查看原始详情"),r.url());if(r.source.equals("Wolfx")){a.link(c,"Wolfx · 目录接口说明","https://wolfx.jp/zh/docs/open-api/");c.addView(a.text("这是机构官网入口，并非此事件的官方详情链接。转发参数仅供参考；未采用接口中含义及标度尚未核实的 intensity 字段。",13,a.MUTED,false));}a.gap(c,12);}
        c.addView(a.text(EarthquakeIntensity.label(event),14,a.INK,false));a.gap(c,10);c.addView(a.text("来源公众震感汇总不等同于官方实测最大烈度；来源模型与本地经验模型始终标为估算。本应用烈度统一采用修订麦加利烈度（MMI）。相关计算范围、限制及许可见关于 → 数据来源与隐私。",13,a.MUTED,false));dialog("地震详情",c);
    }

    private static String region(String value){String v=value.toUpperCase(Locale.ROOT);String[][] names={{"XIZANG","西藏"},{"XIZANG-QINGHAI BORDER REGION","西藏—青海交界地区"},{"SOUTHERN XINJIANG, CHINA","中国新疆南部"},{"NORTHERN XINJIANG, CHINA","中国新疆北部"},{"SICHUAN, CHINA","中国四川"},{"QINGHAI, CHINA","中国青海"},{"YUNNAN, CHINA","中国云南"},{"TAIWAN REGION","台湾地区"},{"TAIWAN","台湾"},{"NEAR EAST COAST OF HONSHU, JAPAN","日本本州东岸附近"}};for(String[] n:names)if(v.equals(n[0]))return n[1]+"（"+value+"）";return value;}

    void refresh(boolean manual){
        if(dead||!cacheLoaded||!visible||cleared&&!manual)return;if(manual)cleared=false;if(!inFlight.isEmpty()){if(manual)a.toast("正在更新，请稍候");return;}
        if(!manual&&p.getLong("quake_interval",900_000)<=0)return;
        long now=System.currentTimeMillis(),since=now-days()*86400_000L;double min=Math.min(global(),regional());List<String> jobs=new ArrayList<>();
        for(String source:EarthquakeData.SOURCES){if(!enabled(source)||limits.getLong("until_"+source,0)>now)continue;EarthquakeCache.Entry e=data.get(source);boolean covers=e!=null&&(source.equals("Wolfx")||e.since<=since+60_000&&e.minimum<=min);long interval=p.getLong("quake_interval",900_000);String query=queryKey(source,days(),min);boolean sameQuery=query.equals(limits.getString("query_"+source,""));long previous=limits.getLong("attempt_"+source,0);if(!manual&&sameQuery&&previous>0&&now-previous<Math.max(60_000,interval))continue;if(manual||!covers||interval>0&&now-e.fetched>=interval)jobs.add(source);}
        if(jobs.isEmpty()){if(manual)a.toast("刚刚更新过或来源冷却中，请稍后再试");return;}
        cycle++;int thisCycle=cycle;cycleGood=0;cycleTotal=jobs.size();cycleManual=manual;inFlight.addAll(jobs);updateStatus();int generation=cache.generation();
        for(String source:jobs){String query=queryKey(source,days(),min);workers.execute(()->{
            // A queued job must recheck consent, source selection and foreground state before requesting.
            if(dead||!visible||!enabled(source)||generation!=cache.generation()){ui.post(()->{if(dead||generation!=cache.generation()||thisCycle!=cycle)return;inFlight.remove(source);cycleTotal--;updateStatus();});return;}
            limits.edit().putLong("attempt_"+source,System.currentTimeMillis()).putString("query_"+source,query).apply();
            EarthquakeCache.Entry entry=null;String failure=null;boolean saved=false;
            try{EarthquakeApi.Result result=EarthquakeApi.fetch(source,since,min,new EarthquakeApi.Limits(){public long until(String s){return limits.getLong("until_"+s,0);}public void until(String s,long t){limits.edit().putLong("until_"+s,t).commit();}});entry=new EarthquakeCache.Entry();entry.raw=result.raw;entry.reports=result.reports;entry.fetched=System.currentTimeMillis();entry.since=since;entry.minimum=min;saved=cache.save(source,entry,generation);}catch(Exception ex){android.util.Log.w("Earthquake",source+" catalogue request failed",ex);String msg=ex.getMessage();failure=msg!=null&&msg.matches(".*[\\u4e00-\\u9fff].*")?msg:"连接失败，请检查网络后重试";}
            final EarthquakeCache.Entry result=entry;final String error=failure;final boolean persisted=saved;
            ui.post(()->{if(dead||generation!=cache.generation()||thisCycle!=cycle)return;inFlight.remove(source);if(result!=null){data.put(source,result);fromCache.remove(source);cycleGood++;if(persisted)failures.remove(source);else failures.put(source,"已获取，但缓存保存失败");}else failures.put(source,error);updateStatus();updateList();if(inFlight.isEmpty()&&cycleManual)a.toast(cycleGood==cycleTotal?"刷新成功！":cycleGood>0?"部分数据源刷新成功":"刷新失败，保留已有缓存");});
        });}
    }
    private static String queryKey(String source,int days,double min){return source.equals("Wolfx")?"latest50":days+":"+min;}
    void pause(){visible=false;ui.removeCallbacks(tick);location.pause();}
    void resume(){visible=true;location.resume();ui.removeCallbacks(tick);ui.postDelayed(tick,1000);}
    void destroy(){dead=true;pause();location.destroy();workers.shutdownNow();ui.removeCallbacksAndMessages(null);}
    boolean clear(){cleared=true;cycle++;inFlight.clear();data.clear();fromCache.clear();failures.clear();boolean ok=cache.clear();updateStatus();updateList();return ok;}
    void changed(){shown=30;if(visible){updateControls();updateLocationControls();updateStatus();updateList();refresh(false);}}
    private LinearLayout form(){LinearLayout c=a.column();c.setPadding(a.dp(18),a.dp(8),a.dp(18),a.dp(16));return c;}
    private void dialog(String title,LinearLayout c){ScrollView s=new ScrollView(a);s.addView(c);new ToolDialog(a).setTitle(title).setView(s).setNegativeButton("返回",null).show();}
    private void action(LinearLayout c,String title,Runnable r){c.addView(a.button(title,false,r));a.gap(c,10);}
    private interface Choice {void select(int n);}
    private void choose(String title,String[] labels,int selected,Choice callback){new ToolDialog(a).setTitle(title).setSingleChoiceItems(labels,selected,(d,n)->callback.select(n)).setNegativeButton("返回",null).show();}
    void settings(){
        LinearLayout c=form();action(c,"查询时间范围",()->choose("查询时间范围",DAYS,dayIndex(),n->{p.edit().putInt("quake_days",DAY_VALUES[n]).apply();changed();}));
        action(c,"查询区域",this::areaSettings);
        action(c,"最低震级",this::magnitudeSettings);

        action(c,"排序方式",()->choose("排序方式",SORTS,sort(),n->{p.edit().putInt("quake_sort",n).apply();changed();}));action(c,"时间显示",()->choose("时间显示",new String[]{"手机当地时间（默认）","北京时间","UTC"},p.getInt("quake_timezone",0),n->{p.edit().putInt("quake_timezone",n).apply();changed();}));
        action(c,"自动刷新间隔",()->choose("自动刷新间隔 · 仅页面前台",new String[]{"关闭","5 分钟","15 分钟（默认）","30 分钟","1 小时"},index(new int[]{0,300000,900000,1800000,3600000},(int)p.getLong("quake_interval",900000)),n->{p.edit().putLong("quake_interval",new long[]{0,300000,900000,1800000,3600000}[n]).apply();changed();}));
        action(c,"距离计算参考位置",this::referenceSettings);action(c,"列表信息显示",this::fields);action(c,"栏目显示与排序",()->new SettingsScreen(a).earthquakeOrder());action(c,"数据源",this::sources);action(c,"地震术语介绍",()->EarthquakeInfo.terms(a));action(c,"清除地震缓存",()->new ToolDialog(a).setTitle("清除地震缓存？").setMessage("仅删除已下载的地震目录，保留设置、最近定位和来源限流记录。").setPositiveButton("清除",(d,n)->{a.toast(clear()?"地震缓存已清除":"部分地震缓存未能删除");}).setNegativeButton("取消",null).show());
        dialog("地震设置",c);
    }
    private int index(int[] values,int v){for(int i=0;i<values.length;i++)if(values[i]==v)return i;return 0;}
    private void magnitudeSettings(){LinearLayout c=form();magnitudeSlider(c,"全球最低震级","quake_global_min");magnitudeSlider(c,"中国及周边最低震级","quake_regional_min");c.addView(a.text("默认均为 M≥1.0；范围 M≥0.0～M≥7.0，步进 0.1 级。这里只是显示筛选值，不保证小震完整收录。任一来源达到门槛即可入列，仍保留各自震级类型。",13,a.MUTED,false));dialog("最低震级",c);}
    private void magnitudeSlider(LinearLayout c,String name,String key){
        TextView value=a.text("",16,a.INK,true);c.addView(value);SeekBar slider=new SeekBar(a);slider.setMax(70);slider.setKeyProgressIncrement(1);slider.setProgress(Math.round(EarthquakeOptions.magnitude(p.getFloat(key,1))*10));Runnable label=()->{String text=name+" · M≥"+String.format(Locale.ROOT,"%.1f",slider.getProgress()/10f);value.setText(text);slider.setContentDescription(text);};label.run();
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int n,boolean user){label.run();if(user){p.edit().putFloat(key,n/10f).apply();updateControls();updateList();}}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){changed();}});c.addView(slider,a.lp(-1,56));a.gap(c,12);
    }
    private void areaSettings(){
        LinearLayout c=form();c.addView(a.text("“中国及周边”采用经度 70° E～140° E、纬度 15° N～55° N 的宽范围筛选，不是行政边界。参考位置附近按本机参考位置计算距离。",13,a.MUTED,false));a.gap(c,12);
        RadioGroup group=new RadioGroup(a);for(int i=0;i<AREAS.length;i++){RadioButton b=new RadioButton(a);b.setId(200+i);b.setText(AREAS[i]);b.setMinHeight(a.dp(48));group.addView(b);}group.check(200+area());c.addView(group);
        LinearLayout nearby=a.column();a.gap(nearby,12);nearby.addView(a.text("附近半径 · 50～5000 km 的整数",14,a.INK,true));EditText radius=new EditText(a);radius.setSingleLine(true);radius.setInputType(InputType.TYPE_CLASS_NUMBER);radius.setText(Integer.toString(p.getInt("quake_radius",1000)));radius.setContentDescription("附近半径（km）");nearby.addView(radius);a.gap(nearby,10);nearby.addView(a.button("保存附近半径",false,()->{int n=EarthquakeOptions.radius(radius.getText().toString());if(n<0){radius.setError("请输入 50～5000 之间的整数");return;}p.edit().putInt("quake_radius",n).apply();changed();a.toast("附近半径已保存");}));nearby.setVisibility(area()==2?View.VISIBLE:View.GONE);c.addView(nearby);
        group.setOnCheckedChangeListener((g,id)->{p.edit().putInt("quake_area",id-200).apply();nearby.setVisibility(id==202?View.VISIBLE:View.GONE);changed();});dialog("查询区域",c);
    }
    private void referenceSettings(){
        LinearLayout c=form();RadioGroup group=new RadioGroup(a);String[] names={"定位位置（默认）","手动参考位置"};for(int i=0;i<2;i++){RadioButton b=new RadioButton(a);b.setId(210+i);b.setText(names[i]);b.setMinHeight(a.dp(48));group.addView(b);}group.check(p.getBoolean("quake_manual_location",false)?211:210);c.addView(group);a.gap(c,12);
        LinearLayout inputs=a.column();EditText lon=coordinate(inputs,"参考经度（-180～180；E / W）",p.getFloat("quake_ref_lon",0)),lat=coordinate(inputs,"参考纬度（-90～90；N / S）",p.getFloat("quake_ref_lat",0));lon.setContentDescription("参考经度");lat.setContentDescription("参考纬度");TextView preview=a.text("",13,a.MUTED,false);inputs.addView(preview);a.gap(inputs,10);android.text.TextWatcher watcher=new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){try{preview.setText(CoordinateFormat.pair(Double.parseDouble(lon.getText().toString()),Double.parseDouble(lat.getText().toString()),5));}catch(Exception ex){preview.setText("请输入完整经纬度");}}public void afterTextChanged(android.text.Editable s){}};lon.addTextChangedListener(watcher);lat.addTextChangedListener(watcher);watcher.onTextChanged("",0,0,0);inputs.addView(a.button("保存参考位置",false,()->{try{float x=Float.parseFloat(lat.getText().toString().trim()),y=Float.parseFloat(lon.getText().toString().trim());if(!Float.isFinite(x)||!Float.isFinite(y)||Math.abs(x)>90||Math.abs(y)>180)throw new IllegalArgumentException();p.edit().putFloat("quake_ref_lat",x).putFloat("quake_ref_lon",y).apply();changed();a.toast("参考位置已保存，距离已更新");}catch(Exception ex){a.toast("请输入有效的经度与纬度");}}));inputs.setVisibility(p.getBoolean("quake_manual_location",false)?View.VISIBLE:View.GONE);c.addView(inputs);a.gap(c,12);c.addView(a.text("定位位置使用最近结果，可在地震页点“刷新定位”按定位设置获取一次。选择手动参考位置后，先输入经度，再输入纬度并保存；经度正数为东经 E、负数为西经 W，纬度正数为北纬 N、负数为南纬 S。坐标仅用于本机距离和附近筛选，不发送给目录来源。",13,a.MUTED,false));
        group.setOnCheckedChangeListener((g,id)->{boolean manual=id==211;p.edit().putBoolean("quake_manual_location",manual).apply();if(manual){location.pause();location.status="";}inputs.setVisibility(manual?View.VISIBLE:View.GONE);changed();});dialog("距离计算参考位置",c);
    }

    private EditText coordinate(LinearLayout c,String name,float value){c.addView(a.text(name,14,a.INK,false));EditText e=new EditText(a);e.setSingleLine(true);e.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL|InputType.TYPE_NUMBER_FLAG_SIGNED);e.setText(Float.toString(value));c.addView(e);a.gap(c,12);return e;}
    private void fields(){LinearLayout c=form();String[] keys={"depth","distance","intensity","source","review"},names={"深度","距参考位置的距离","烈度信息","来源","复核状态"};for(int i=0;i<keys.length;i++)toggle(c,names[i],"quake_field_"+keys[i],true);c.addView(a.text("默认全部显示。来源署名和计算说明统一在关于 → 数据来源与隐私；烈度区分来源报告与模型估算。",13,a.MUTED,false));dialog("列表信息显示",c);}
    private void toggle(LinearLayout c,String name,String key,boolean def){CheckBox b=new CheckBox(a);b.setText(name);b.setChecked(p.getBoolean(key,def));b.setOnCheckedChangeListener((v,on)->{p.edit().putBoolean(key,on).apply();changed();});c.addView(b,a.lp(-1,52));}
    private void sources(){LinearLayout c=form();for(String source:EarthquakeData.SOURCES){
        CheckBox b=new CheckBox(a);b.setText(EarthquakeData.sourceName(source)+(source.equals("Wolfx")?" · 默认关闭":source.equals("USGS")?" · 全球补充":" · CC BY 4.0"));b.setChecked(enabled(source));boolean[] updating={false};
        b.setOnCheckedChangeListener((v,on)->{if(updating[0])return;if(source.equals("Wolfx")&&on){updating[0]=true;b.setChecked(false);updating[0]=false;new ToolDialog(a).setTitle("启用 Wolfx 转发目录前请确认").setMessage(EarthquakeInfo.WOLFX_NOTICE).setPositiveButton("我已阅读并确认",(d,n)->{p.edit().putBoolean("quake_wolfx_ack_112",true).putBoolean("quake_source_Wolfx",true).apply();updating[0]=true;b.setChecked(true);updating[0]=false;changed();}).setNegativeButton("取消",null).show();return;}
            if(!on){int count=0;for(String item:EarthquakeData.SOURCES)if(enabled(item))count++;if(count==1){updating[0]=true;b.setChecked(true);updating[0]=false;a.toast("至少保留一个地震数据源");return;}}
            p.edit().putBoolean("quake_source_"+source,on).apply();changed();});b.setMinHeight(a.dp(52));c.addView(b,a.lp(-1,-2));a.gap(c,6);
        }a.gap(c,10);c.addView(a.text("默认开启德国地学研究中心、欧洲—地中海地震中心和美国地质调查局；Wolfx 默认关闭，确认后才发请求。Wolfx 目前仅中国地震台网近期目录，全球覆盖由其他来源补充。不同来源同次地震在匹配明确时合并，原参数仍可分别查看。",13,a.MUTED,false));dialog("地震数据源",c);}


}
