package org.simpleweather.app;

import android.app.*;
import android.content.*;
import android.location.Location;
import android.os.*;
import android.text.InputType;
import android.view.Gravity;
import android.widget.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

final class EarthquakeScreen {
    static final String[] SECTION_NAMES={"筛选与刷新","更新状态","地震列表","数据说明"};
    static final String[] DAYS={"最近 24 小时","最近 3 天","最近 7 天（默认）","最近 30 天"};
    static final int[] DAY_VALUES={1,3,7,30};
    static final String[] AREAS={"全球（默认）","中国及周边","参考位置附近"};
    static final String[] SORTS={"最新发生（默认）","震级从大到小","距离从近到远"};
    private final MainActivity a;
    private final SharedPreferences p,limits;
    private final EarthquakeCache cache;
    private final ExecutorService workers=Executors.newFixedThreadPool(2);
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final Map<String,EarthquakeCache.Entry> data=new HashMap<>();
    private final Map<String,String> failures=new HashMap<>();
    private final Set<String> inFlight=new HashSet<>(),fromCache=new HashSet<>();
    private LinearLayout statusBox,listBox;
    private TextView refresh,rangeButton,summary;
    private boolean visible,dead,cacheLoaded,cleared;
    private int shown=30,cycle,cycleGood,cycleTotal;
    private boolean cycleManual;
    private final Runnable tick=new Runnable(){public void run(){if(visible&&!dead){refresh(false);updateStatus();ui.postDelayed(this,15_000);}}};
    EarthquakeScreen(MainActivity activity){a=activity;p=a.preferences;limits=a.getSharedPreferences("earthquake_limits",0);cache=new EarthquakeCache(a.getFilesDir());}
    private int days(){int v=p.getInt("quake_days",7);for(int d:DAY_VALUES)if(v==d)return d;return 7;}
    private double global(){return Math.max(3,Math.min(7,p.getFloat("quake_global_min",5)));}
    private double regional(){return Math.max(3,Math.min(7,p.getFloat("quake_regional_min",4)));}
    private boolean enabled(String source){return p.getBoolean("quake_source_"+source,true);}
    void render(){
        visible=true;statusBox=null;listBox=null;rangeButton=null;summary=null;refresh=null;
        for(int section:DisplayOrder.parse(p.getString("quake_order",""),SECTION_NAMES.length)){
            if(!p.getBoolean("quake_show_"+section,true))continue;
            LinearLayout c=a.card();c.addView(a.text(SECTION_NAMES[section],17,a.INK,true));a.gap(c,12);
            if(section==0)controls(c);
            else if(section==1){statusBox=a.column();c.addView(statusBox);}
            else if(section==2){c.addView(a.text("GEOFON（GFZ／GEVN）、EMSC · CC BY 4.0\nCENC（Wolfx 转发）· 上游公开展示适用依据尚未完全确认，仅供参考。\n合并与筛选由本应用处理，点击记录查看各来源参数。",12,a.MUTED,false));a.gap(c,14);listBox=a.column();c.addView(listBox);}
            else notes(c);
            a.gap(a.body,16);
        }
        updateStatus();updateList();
        if(!cacheLoaded){cacheLoaded=true;int generation=cache.generation();workers.execute(()->{Map<String,EarthquakeCache.Entry> loaded=new HashMap<>();for(String source:EarthquakeData.SOURCES){EarthquakeCache.Entry e=cache.read(source);if(e!=null)loaded.put(source,e);}ui.post(()->{if(dead||generation!=cache.generation())return;data.putAll(loaded);fromCache.addAll(loaded.keySet());updateStatus();updateList();refresh(false);});});}
        resume();
    }
    private void controls(LinearLayout c){
        LinearLayout row=a.row();rangeButton=a.button(dayLabel(),false,()->choose("查询时间范围",DAYS,dayIndex(),n->{p.edit().putInt("quake_days",DAY_VALUES[n]).apply();changed();}));row.addView(rangeButton,new LinearLayout.LayoutParams(0,-2,1));a.gapHorizontal(row,8);row.addView(a.button("筛选设置",false,()->settings()),new LinearLayout.LayoutParams(0,-2,1));c.addView(row);a.gap(c,10);
        summary=a.text("",13,a.MUTED,false);c.addView(summary);updateControls();a.gap(c,12);
        refresh=a.button(inFlight.isEmpty()?"刷新目录":"正在更新…",true,()->refresh(true));c.addView(refresh);a.gap(c,10);c.addView(a.button("更新距离 / 参考位置",false,()->{updateList();updateStatus();if(reference()==null)new ToolDialog(a).setTitle("尚未获取参考位置").setMessage("可先去定位页获取位置，或在地震设置中手动指定。查询地震目录不需要定位权限。").setPositiveButton("前往定位",(d,n)->a.switchTab(2)).setNegativeButton("返回",null).show();else a.toast("已按当前参考位置更新距离");}));
    }
    private void updateControls(){if(rangeButton!=null)rangeButton.setText(dayLabel());if(summary!=null)summary.setText(AREAS[area()].replace("（默认）","")+" · 全球 M≥"+global()+" / 中国及周边 M≥"+regional()+"\n"+SORTS[sort()].replace("（默认）","")+" · "+zoneLabel());}
    private String dayLabel(){return "最近 "+days()+" 天";}
    private int dayIndex(){for(int i=0;i<DAY_VALUES.length;i++)if(DAY_VALUES[i]==days())return i;return 2;}
    private int area(){return Math.max(0,Math.min(2,p.getInt("quake_area",0)));}
    private int sort(){return Math.max(0,Math.min(2,p.getInt("quake_sort",0)));}
    private ZoneId zone(){int z=p.getInt("quake_timezone",0);return z==1?ZoneId.of("Asia/Shanghai"):z==2?ZoneOffset.UTC:ZoneId.systemDefault();}
    private String zoneLabel(){return p.getInt("quake_timezone",0)==0?"手机当地时间（"+zone().getId()+"）":p.getInt("quake_timezone",0)==1?"北京时间":"UTC";}
    private String time(long t){return t<=0?"未提供":DateTimeFormatter.ofPattern("MM-dd HH:mm:ss").withZone(zone()).format(Instant.ofEpochMilli(t));}
    private double[] reference(){
        if(p.getBoolean("quake_manual_location",false)){double lat=p.getFloat("quake_ref_lat",0),lon=p.getFloat("quake_ref_lon",0);return new double[]{lat,lon};}
        Location l=LocationService.last(a);return l==null?null:new double[]{l.getLatitude(),l.getLongitude()};
    }
    private String referenceLabel(){if(p.getBoolean("quake_manual_location",false))return "手动参考位置";Location l=LocationService.last(a);if(l==null)return "未获取定位，距离暂不可用";long age=LocationService.ageSeconds(l);return "最近定位 · "+(age<60?age+" 秒前":age<3600?age/60+" 分钟前":age/3600+" 小时前")+(age>300?"（较旧）":"");}
    private String distance(EarthquakeData.Report r){double[] ref=reference();return ref==null?"未获取位置":String.format(Locale.ROOT,"距%s %.0f km",p.getBoolean("quake_manual_location",false)?"参考位置":"最近定位",EarthquakeData.distance(ref[0],ref[1],r.latitude,r.longitude));}
    private String depth(EarthquakeData.Report r){return Double.isFinite(r.depth)?String.format(Locale.ROOT,"深度 %.1f km",r.depth):"未提供深度";}
    private String intensity(){return p.getInt("quake_intensity_scale",0)==0?"中国地震烈度：暂无可靠结果":"MMI：暂无可靠结果";}
    private void updateStatus(){
        if(!visible||statusBox==null)return;statusBox.removeAllViews();long now=System.currentTimeMillis();
        for(String source:EarthquakeData.SOURCES){EarthquakeCache.Entry e=data.get(source);String s=EarthquakeData.sourceName(source)+" · ";if(!enabled(source))s+="已关闭";else if(inFlight.contains(source))s+="更新中";else if(failures.containsKey(source))s+=failures.get(source)+(e==null?"":"，保留上次结果");else s+=e==null?"尚未获取":now-e.fetched>Math.max(900_000,p.getLong("quake_interval",900_000))?"缓存较旧":fromCache.contains(source)?"本机缓存":"已获取";
            statusBox.addView(a.text(s,14,a.INK,false));if(e!=null){if(!source.equals("Wolfx")&&(e.since>now-days()*86400_000L+60_000||e.minimum>Math.min(global(),regional())))statusBox.addView(a.text("现有缓存未覆盖当前筛选范围，等待联网刷新。",12,a.MUTED,false));statusBox.addView(a.text("本机成功获取："+time(e.fetched),12,a.MUTED,false));if(source.equals("Wolfx"))statusBox.addView(a.text("仅含接口最近最多 50 条，不保证覆盖所选天数；本次 "+e.reports.size()+" 条"+(e.reports.isEmpty()?"":" · 最早发生 "+time(Collections.min(e.reports,Comparator.comparingLong(r->r.time)).time)),12,a.MUTED,false));if(!source.equals("Wolfx")&&e.reports.size()>=EarthquakeData.LIMIT)statusBox.addView(a.text("达到单次条数上限，结果可能不完整，请缩小范围。",12,a.MUTED,false));}a.gap(statusBox,10);
        }
        if(sort()==2&&reference()==null)statusBox.addView(a.text("尚无参考位置，暂按最新发生排列。",12,a.MUTED,false));
        statusBox.addView(a.text(referenceLabel()+"\n"+zoneLabel()+" · 查询不保证不漏报或固定发布延迟",12,a.MUTED,false));
        if(refresh!=null)refresh.setText(inFlight.isEmpty()?"刷新目录":"正在更新…");
    }
    private void updateList(){
        if(!visible||listBox==null)return;listBox.removeAllViews();List<EarthquakeData.Report> all=new ArrayList<>();for(String source:EarthquakeData.SOURCES)if(enabled(source)&&data.containsKey(source))all.addAll(data.get(source).reports);
        long now=System.currentTimeMillis();List<EarthquakeData.Event> events=EarthquakeData.filter(EarthquakeData.merge(all),now-days()*86400_000L,now,area(),global(),regional(),reference(),p.getInt("quake_radius",1000),sort());
        listBox.addView(a.text("符合筛选："+events.size()+" 条 · 点击记录展开详情",13,a.MUTED,false));a.gap(listBox,12);
        if(events.isEmpty()){listBox.addView(a.text(area()==2&&reference()==null?"尚未获取参考位置，请先定位或手动设置。":all.isEmpty()?"尚无可显示目录。联网刷新后可查看；请求失败时会保留已有缓存。":"当前已获取目录中没有符合筛选的记录，可调整时间范围和最低震级。这不表示该区域没有发生地震。",14,a.INK,false));return;}
        for(int i=0;i<Math.min(shown,events.size());i++){
            EarthquakeData.Event event=events.get(i);EarthquakeData.Report r=event.main();LinearLayout item=a.column();item.setPadding(a.dp(14),a.dp(16),a.dp(14),a.dp(16));
            item.addView(a.text(r.magnitudeLabel()+" · "+region(r.region),17,r.magnitude>=6?0xFF9D4618:a.INK,true));a.gap(item,10);item.addView(a.text("发生："+time(r.time),14,a.INK,false));a.gap(item,7);
            List<String> facts=new ArrayList<>();if(p.getBoolean("quake_field_depth",true))facts.add(depth(r));if(p.getBoolean("quake_field_distance",true))facts.add(distance(r));if(!facts.isEmpty())item.addView(a.text(String.join(" · ",facts),13,a.MUTED,false));
            if(p.getBoolean("quake_field_intensity",true)){a.gap(item,6);item.addView(a.text(intensity(),13,a.MUTED,false));}
            if(p.getBoolean("quake_field_source",true)){a.gap(item,6);item.addView(a.text("来源："+event.sources(),12,a.MUTED,false));}
            if(p.getBoolean("quake_field_review",true)){a.gap(item,6);item.addView(a.text(r.review,12,a.MUTED,false));}
            a.tap(item,0xFFF5F8FD,16,()->details(event));listBox.addView(item,a.lp(-1,-2));a.gap(listBox,10);
        }
        if(shown<events.size())listBox.addView(a.button("加载更多（还有 "+(events.size()-shown)+" 条）",false,()->{shown+=30;updateList();}));
    }
    private void details(EarthquakeData.Event event){
        LinearLayout c=form();EarthquakeData.Report main=event.main();c.addView(a.text(region(main.region),18,a.INK,true));a.gap(c,12);c.addView(a.text("不同来源的震级类型、深度和位置可能不同。本应用不取平均；优先展示人工处理结果，其次 GEOFON，同优先级保留最新版本。近时近地且匹配明确才合并，密集余震可能仍分开显示。",13,a.MUTED,false));a.gap(c,16);
        for(EarthquakeData.Report r:event.reports){c.addView(a.text(EarthquakeData.sourceName(r.source)+" · "+r.magnitudeLabel(),17,a.BLUE,true));a.gap(c,9);TextView info=a.text("事件 ID："+r.id+"\n发生时间："+time(r.time)+"\n"+(r.source.equals("Wolfx")?"来源报告时间：":r.source.equals("GEOFON")?"来源参数生成时刻：":"来源更新时间：")+time(r.updated)+"\n"+zoneLabel()+"\n震中："+region(r.region)+String.format(Locale.ROOT,"\n纬度 %.5f° · 经度 %.5f°",r.latitude,r.longitude)+"\n"+depth(r)+"\n"+r.depthNote+"\n"+distance(r)+"\n复核："+r.review+(r.agency.isEmpty()?"":"\n贡献机构："+r.agency),14,a.INK,false);info.setTextIsSelectable(true);c.addView(info);a.gap(c,10);a.link(c,EarthquakeData.sourceName(r.source)+(r.source.equals("Wolfx")?" · CENC 官方网站":" · 查看原始详情"),r.url());if(r.source.equals("Wolfx")){a.link(c,"Wolfx · 目录接口说明","https://wolfx.jp/zh/docs/open-api/");c.addView(a.text("这是机构官网入口，并非此事件的官方详情链接。转发参数仅供参考；未采用接口中含义及标度尚未核实的 intensity 字段。",13,a.MUTED,false));}a.gap(c,12);}
        c.addView(a.text(intensity()+"。当前接入目录未提供已核实可直接当作正式最大烈度的结果；本版本不把震级、默认深度或其他标度机械换算成烈度。经核验的估算模型尚未启用。",13,a.MUTED,false));dialog("地震详情",c);
    }
    private static String region(String value){String v=value.toUpperCase(Locale.ROOT);String[][] names={{"XIZANG","西藏"},{"XIZANG-QINGHAI BORDER REGION","西藏—青海交界地区"},{"SOUTHERN XINJIANG, CHINA","中国新疆南部"},{"NORTHERN XINJIANG, CHINA","中国新疆北部"},{"SICHUAN, CHINA","中国四川"},{"QINGHAI, CHINA","中国青海"},{"YUNNAN, CHINA","中国云南"},{"TAIWAN REGION","台湾地区"},{"TAIWAN","台湾"},{"NEAR EAST COAST OF HONSHU, JAPAN","日本本州东岸附近"}};for(String[] n:names)if(v.equals(n[0]))return n[1]+"（"+value+"）";return value;}
    private void notes(LinearLayout c){
        c.addView(a.text("近期震后目录查询，不提供预警、地震波倒计时或后台地震通知。自动刷新只在此页面前台进行。筛选门槛不是完整收录承诺，参数可能修订。\n\n距离为参考位置到震中的地表大圆距离，在手机本地计算；经纬度不随地震请求上传。\n\nGEOFON 的 10 km 可能是默认浅源深度；固定深度不代表可靠实测。中国地震烈度与 MMI 是不同标度，本版本没有经验证的全球估算模型，缺少可靠结果时如实显示。\n\n来源署名：Event locations were obtained from the GEOFON program of the GFZ Helmholtz Centre for Geosciences using data from the GEVN partner networks. EMSC 数据由 SeismicPortal 提供。两个来源数据均按 CC BY 4.0 使用；本应用进行了匹配、筛选、时间格式化和少量地区名翻译。\n\nCENC 近期目录由非官方服务 Wolfx 转发，仅供参考，不能替代官方渠道。接口最多约 50 条，可能不足以覆盖所选时间范围，无法保证中国及周边中、大地震完整收录或固定延迟。按北京时间（UTC+8）解析后转换到所选时区；震级类型未提供时仅标 M。未采用含义与标度尚未核实的 intensity 字段。\n\n目前情况：Wolfx 可访问及 CORS 支持仅说明技术可行性，不等于上游再发布授权。Wolfx 一般条款保留上游权利，尚未发现 cenc_eqlist 的专项公开展示许可；CENC 此目录的现行分级及面向不特定用户展示的适用依据尚未完全确认。个人非商业使用、来源署名和免责声明均不能替代授权。本应用已接入，可在“地震设置 → 数据源”关闭；不将 CENC／Wolfx 标为 CC BY 4.0，也未接入任何预警流。",13,a.MUTED,false));a.gap(c,12);
        a.link(c,"GEOFON · 数据说明与许可","https://geofon.gfz.de/eqinfo/faq/");a.link(c,"EMSC · 数据许可与服务条款","https://www.seismicportal.eu/terms.html");a.link(c,"CC BY 4.0 · 仅 GEOFON / EMSC 数据许可","https://creativecommons.org/licenses/by/4.0/");a.link(c,"Wolfx · 开放 API 文档","https://wolfx.jp/zh/docs/open-api/");a.link(c,"Wolfx · 使用条款","https://wolfx.jp/zh/legal/terms/");a.link(c,"CENC · 官方网站","https://www.cenc.ac.cn/");
    }
    void refresh(boolean manual){
        if(dead||!cacheLoaded||!visible||cleared&&!manual)return;if(manual)cleared=false;if(!inFlight.isEmpty()){if(manual)a.toast("正在更新，请稍候");return;}
        if(!manual&&p.getLong("quake_interval",900_000)<=0)return;
        long now=System.currentTimeMillis(),since=now-days()*86400_000L;double min=Math.min(global(),regional());List<String> jobs=new ArrayList<>();
        for(String source:EarthquakeData.SOURCES){if(!enabled(source)||limits.getLong("until_"+source,0)>now)continue;EarthquakeCache.Entry e=data.get(source);boolean covers=e!=null&&(source.equals("Wolfx")||e.since<=since+60_000&&e.minimum<=min);long interval=p.getLong("quake_interval",900_000);String query=queryKey(source,days(),min);boolean sameQuery=query.equals(limits.getString("query_"+source,""));long previous=limits.getLong("attempt_"+source,0);if(!manual&&sameQuery&&previous>0&&now-previous<Math.max(60_000,interval))continue;if(manual||!covers||interval>0&&now-e.fetched>=interval)jobs.add(source);}
        if(jobs.isEmpty()){if(manual)a.toast("刚刚更新过或来源冷却中，请稍后再试");return;}
        cycle++;int thisCycle=cycle;cycleGood=0;cycleTotal=jobs.size();cycleManual=manual;inFlight.addAll(jobs);updateStatus();int generation=cache.generation();
        for(String source:jobs){limits.edit().putLong("attempt_"+source,now).putString("query_"+source,queryKey(source,days(),min)).apply();workers.execute(()->{
            EarthquakeCache.Entry entry=null;String failure=null;boolean saved=false;
            try{EarthquakeApi.Result result=EarthquakeApi.fetch(source,since,min,new EarthquakeApi.Limits(){public long until(String s){return limits.getLong("until_"+s,0);}public void until(String s,long t){limits.edit().putLong("until_"+s,t).commit();}});entry=new EarthquakeCache.Entry();entry.raw=result.raw;entry.reports=result.reports;entry.fetched=System.currentTimeMillis();entry.since=since;entry.minimum=min;saved=cache.save(source,entry,generation);}catch(Exception ex){android.util.Log.w("Earthquake",source+" catalogue request failed",ex);String msg=ex.getMessage();failure=msg!=null&&msg.matches(".*[\\u4e00-\\u9fff].*")?msg:"连接失败，请检查网络后重试";}
            final EarthquakeCache.Entry result=entry;final String error=failure;final boolean persisted=saved;
            ui.post(()->{if(dead||generation!=cache.generation()||thisCycle!=cycle)return;inFlight.remove(source);if(result!=null){data.put(source,result);fromCache.remove(source);cycleGood++;if(persisted)failures.remove(source);else failures.put(source,"已获取，但缓存保存失败");}else failures.put(source,error);updateStatus();updateList();if(inFlight.isEmpty()&&cycleManual)a.toast(cycleGood==cycleTotal?"刷新成功！":cycleGood>0?"部分数据源刷新成功":"刷新失败，保留已有缓存");});
        });}
    }
    private static String queryKey(String source,int days,double min){return source.equals("Wolfx")?"latest50":days+":"+min;}
    void pause(){visible=false;ui.removeCallbacks(tick);}
    void resume(){visible=true;ui.removeCallbacks(tick);ui.postDelayed(tick,1000);}
    void destroy(){dead=true;pause();workers.shutdownNow();ui.removeCallbacksAndMessages(null);}
    boolean clear(){cleared=true;cycle++;inFlight.clear();data.clear();fromCache.clear();failures.clear();boolean ok=cache.clear();updateStatus();updateList();return ok;}
    void changed(){shown=30;if(visible){updateControls();updateStatus();updateList();refresh(false);}}
    private LinearLayout form(){LinearLayout c=a.column();c.setPadding(a.dp(18),a.dp(8),a.dp(18),a.dp(16));return c;}
    private void dialog(String title,LinearLayout c){ScrollView s=new ScrollView(a);s.addView(c);new ToolDialog(a).setTitle(title).setView(s).setNegativeButton("返回",null).show();}
    private void action(LinearLayout c,String title,Runnable r){c.addView(a.button(title,false,r));a.gap(c,10);}
    private interface Choice {void select(int n);}
    private void choose(String title,String[] labels,int selected,Choice callback){new ToolDialog(a).setTitle(title).setSingleChoiceItems(labels,selected,(d,n)->callback.select(n)).setNegativeButton("返回",null).show();}
    void settings(){
        LinearLayout c=form();action(c,"查询时间范围",()->choose("查询时间范围",DAYS,dayIndex(),n->{p.edit().putInt("quake_days",DAY_VALUES[n]).apply();changed();}));
        action(c,"查询区域",()->choose("查询区域",AREAS,area(),n->{p.edit().putInt("quake_area",n).apply();changed();}));
        c.addView(a.text("“中国及周边”采用北纬 15°～55°、东经 70°～140° 的宽范围筛选，不是行政边界。附近查询需要参考位置。",12,a.MUTED,false));a.gap(c,10);
        action(c,"最低震级",this::magnitudeSettings);action(c,"附近范围",()->choose("参考位置附近范围",new String[]{"300 km","500 km","1000 km（默认）","2000 km","5000 km"},index(new int[]{300,500,1000,2000,5000},p.getInt("quake_radius",1000)),n->{p.edit().putInt("quake_radius",new int[]{300,500,1000,2000,5000}[n]).apply();changed();}));
        action(c,"排序方式",()->choose("排序方式",SORTS,sort(),n->{p.edit().putInt("quake_sort",n).apply();changed();}));action(c,"时间显示",()->choose("时间显示",new String[]{"手机当地时间（默认）","北京时间","UTC"},p.getInt("quake_timezone",0),n->{p.edit().putInt("quake_timezone",n).apply();changed();}));
        action(c,"烈度标度",()->choose("烈度标度",new String[]{"中国地震烈度（默认）","MMI"},p.getInt("quake_intensity_scale",0),n->{p.edit().putInt("quake_intensity_scale",n).apply();changed();}));
        action(c,"自动刷新间隔",()->choose("自动刷新间隔 · 仅页面前台",new String[]{"关闭","5 分钟","15 分钟（默认）","30 分钟","1 小时"},index(new int[]{0,300000,900000,1800000,3600000},(int)p.getLong("quake_interval",900000)),n->{p.edit().putLong("quake_interval",new long[]{0,300000,900000,1800000,3600000}[n]).apply();changed();}));
        action(c,"距离计算参考位置",this::referenceSettings);action(c,"列表信息显示",this::fields);action(c,"栏目显示与排序",()->new SettingsScreen(a).earthquakeOrder());action(c,"数据源",this::sources);action(c,"清除地震缓存",()->new ToolDialog(a).setTitle("清除地震缓存？").setMessage("仅删除已下载的地震目录，保留设置、最近定位和来源限流记录。").setPositiveButton("清除",(d,n)->{a.toast(clear()?"地震缓存已清除":"部分地震缓存未能删除");}).setNegativeButton("取消",null).show());
        dialog("地震设置",c);
    }
    private int index(int[] values,int v){for(int i=0;i<values.length;i++)if(values[i]==v)return i;return 0;}
    private void magnitudeSettings(){LinearLayout c=form();action(c,"全球最低震级",()->magnitudeChoice("quake_global_min",5));action(c,"中国及周边最低震级",()->magnitudeChoice("quake_regional_min",4));c.addView(a.text("默认全球 M≥5.0，中国及周边 M≥4.0；可设置 M3.0～7.0。这些是显示筛选值，不保证小震完整收录。任一来源达到门槛即可入列，展示参数仍保留所选来源的原始震级。",13,a.MUTED,false));dialog("最低震级",c);}
    private void magnitudeChoice(String key,float def){String[] names=new String[9];for(int i=0;i<9;i++)names[i]="M≥"+(3+i*.5)+(3+i*.5==def?"（默认）":"");choose("最低震级",names,Math.round((p.getFloat(key,def)-3)*2),n->{p.edit().putFloat(key,3+n*.5f).apply();changed();});}
    private void referenceSettings(){LinearLayout c=form();Switch manual=new Switch(a);manual.setText("使用手动参考位置");manual.setChecked(p.getBoolean("quake_manual_location",false));manual.setOnCheckedChangeListener((v,on)->{p.edit().putBoolean("quake_manual_location",on).apply();changed();});c.addView(manual,a.lp(-1,54));a.gap(c,10);action(c,"设置参考经纬度",()->manualCoordinates(manual));c.addView(a.text("默认使用最近一次定位；本页不自动启动定位。手动参考位置仅用于本机计算距离和附近筛选，不随目录查询发送给来源。",13,a.MUTED,false));dialog("距离计算参考位置",c);}
    private void manualCoordinates(Switch manual){LinearLayout c=form();EditText lat=coordinate(c,"纬度（-90～90）",p.getFloat("quake_ref_lat",0)),lon=coordinate(c,"经度（-180～180）",p.getFloat("quake_ref_lon",0));ScrollView scroll=new ScrollView(a);scroll.addView(c);AlertDialog d=new ToolDialog(a).setTitle("手动参考位置 · WGS84").setView(scroll).setPositiveButton("保存",null).setNegativeButton("取消",null).create();d.setOnShowListener(v->d.getButton(-1).setOnClickListener(b->{try{float x=Float.parseFloat(lat.getText().toString().trim()),y=Float.parseFloat(lon.getText().toString().trim());if(!Float.isFinite(x)||!Float.isFinite(y)||Math.abs(x)>90||Math.abs(y)>180)throw new IllegalArgumentException();p.edit().putFloat("quake_ref_lat",x).putFloat("quake_ref_lon",y).putBoolean("quake_manual_location",true).apply();manual.setChecked(true);changed();d.dismiss();}catch(Exception e){a.toast("请输入有效的纬度与经度");}}));d.show();}
    private EditText coordinate(LinearLayout c,String name,float value){c.addView(a.text(name,14,a.INK,false));EditText e=new EditText(a);e.setSingleLine(true);e.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL|InputType.TYPE_NUMBER_FLAG_SIGNED);e.setText(Float.toString(value));c.addView(e);a.gap(c,12);return e;}
    private void fields(){LinearLayout c=form();String[] keys={"depth","distance","intensity","source","review"},names={"深度","距参考位置的距离","烈度信息","来源","复核状态"};for(int i=0;i<keys.length;i++)toggle(c,names[i],"quake_field_"+keys[i],true);c.addView(a.text("默认全部显示。来源署名仍在列表上方保留；缺乏可靠烈度结果时不会编造数值。",13,a.MUTED,false));dialog("列表信息显示",c);}
    private void toggle(LinearLayout c,String name,String key,boolean def){CheckBox b=new CheckBox(a);b.setText(name);b.setChecked(p.getBoolean(key,def));b.setOnCheckedChangeListener((v,on)->{p.edit().putBoolean(key,on).apply();changed();});c.addView(b,a.lp(-1,52));}
    private void sources(){LinearLayout c=form();for(String source:EarthquakeData.SOURCES){CheckBox b=new CheckBox(a);b.setText(EarthquakeData.sourceName(source)+(source.equals("Wolfx")?" · 上游适用依据待确认":" · CC BY 4.0"));b.setChecked(enabled(source));b.setOnCheckedChangeListener((v,on)->{if(!on){int count=0;for(String s:EarthquakeData.SOURCES)if(enabled(s))count++;if(count==1){b.setChecked(true);a.toast("至少保留一个地震数据源");return;}}p.edit().putBoolean("quake_source_"+source,on).apply();changed();});b.setMinHeight(a.dp(52));c.addView(b,a.lp(-1,-2));}a.gap(c,12);c.addView(a.text("默认三个来源均开启；可分别关闭，至少保留一个来源。GFZ 是 EMSC 的重要节点，来源并非完全独立，不能保证互补后不漏报。\n\nWolfx · CENC 目录：已接入最近最多 50 条。上游面向不特定用户公开展示的适用依据尚未完全确认，不宣称已取得 CENC 再发布授权。仅供参考；可关闭此来源。非商业用途与技术可访问不等于授权。不接入 cenc_eew 等预警流。",13,a.MUTED,false));dialog("地震数据源",c);}
}
