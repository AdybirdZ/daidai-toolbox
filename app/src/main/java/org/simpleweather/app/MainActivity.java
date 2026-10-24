package org.simpleweather.app;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.*;
import android.net.Uri;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;
import android.text.*;
import org.json.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    private static final int BG=0xFFF5F7FB, WHITE=Color.WHITE, INK=0xFF20324C, MUTED=0xFF718097, BLUE=0xFF246BCE, PALE=0xFFEAF2FF;
    private final ExecutorService workers=Executors.newFixedThreadPool(2);
    private final Handler main=new Handler(Looper.getMainLooper());
    private WeatherApi api;
    private final Set<String> inFlight=new HashSet<>();
    private final Map<String,Long> attempts=new HashMap<>();
    private WeatherStore store;
    private List<City> catalog;
    private City city;
    private WeatherData data;
    private LinearLayout host, body, results;
    private EditText search;
    private TextView searchStatus;
    private boolean picking, loading, dead;
    private String notice="";
    private int searchVersion;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        if(Build.VERSION.SDK_INT>=29)getWindow().setNavigationBarContrastEnforced(false);
        store=new WeatherStore(this);api=new WeatherApi(store);catalog=store.catalog();
        if(catalog.isEmpty()) {
            try {catalog.add(new City(new JSONObject("{\"name\":\"北京\",\"latitude\":39.9042,\"longitude\":116.4074,\"country\":\"中国\",\"timezone\":\"Asia/Shanghai\"}")));}catch(Exception ignored){}
        }
        city=store.selected(catalog.get(0));data=store.cached(city);
        showWeather();load(false);
    }
    private int dp(float n) {return Math.round(n*getResources().getDisplayMetrics().density);}
    private LinearLayout column() {LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout row() {LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private LinearLayout.LayoutParams lp(int w,int h) {return new LinearLayout.LayoutParams(w<0?w:dp(w),h<0?h:dp(h));}
    private TextView text(String value,int size,int color,boolean bold) {
        TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);
        t.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));t.setIncludeFontPadding(false);
        t.setLineSpacing(dp(3),1);return t;
    }
    private GradientDrawable shape(int color,int radius) {GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private void tap(View v,int color,int radius,Runnable action) {
        v.setBackground(new RippleDrawable(android.content.res.ColorStateList.valueOf(0x223577CC),shape(color,radius),shape(WHITE,radius)));
        v.setOnClickListener(view->action.run());v.setClickable(true);v.setFocusable(true);
    }
    private TextView button(String title, boolean primary, Runnable click) {
        TextView t=text(title,14,primary?WHITE:BLUE,true);t.setGravity(Gravity.CENTER);
        t.setMinHeight(dp(48));t.setPadding(dp(16),dp(10),dp(16),dp(10));tap(t,primary?BLUE:PALE,16,click);return t;
    }
    private void gap(LinearLayout parent,int h) {View v=new View(this);parent.addView(v,lp(1,h));}
    private void spacer(LinearLayout parent) {parent.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));}
    private void line(LinearLayout parent) {View v=new View(this);v.setBackgroundColor(0xFFF0F3F8);parent.addView(v,new LinearLayout.LayoutParams(-1,dp(1)));}
    private void startScreen() {
        host=column();host.setBackgroundColor(BG);setContentView(host);
        host.setOnApplyWindowInsetsListener((view,insets)->{
            if(Build.VERSION.SDK_INT>=30){android.graphics.Insets i=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());view.setPadding(i.left,i.top,i.right,i.bottom);}
            else view.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            return insets;
        });host.requestApplyInsets();
        FrameLayout frame=new FrameLayout(this);host.addView(frame,new LinearLayout.LayoutParams(-1,-1));
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);scroll.setVerticalScrollBarEnabled(false);
        FrameLayout.LayoutParams fit=new FrameLayout.LayoutParams(Math.min(getResources().getDisplayMetrics().widthPixels,dp(620)),-1,Gravity.CENTER_HORIZONTAL);
        frame.addView(scroll,fit);body=column();body.setPadding(dp(20),dp(18),dp(20),dp(28));scroll.addView(body);
    }
    private LinearLayout card() {LinearLayout c=column();c.setPadding(dp(18),dp(20),dp(18),dp(18));c.setBackground(shape(WHITE,24));body.addView(c,lp(-1,-2));return c;}
    private void section(LinearLayout parent,String name,String aside) {
        LinearLayout r=row();r.addView(text(name,17,INK,true));spacer(r);
        TextView source=text(aside,11,MUTED,false);source.setPadding(dp(5),dp(8),0,dp(8));source.setMinHeight(dp(32));
        tap(source,Color.TRANSPARENT,6,()->open("https://open-meteo.com/"));r.addView(source);parent.addView(r);gap(parent,12);
    }
    private void showWeather() {
        picking=false;searchVersion++;startScreen();
        LinearLayout header=row();
        LinearLayout brand=column();brand.addView(text("简天气",24,INK,true),lp(-2,-2));gap(brand,6);brand.addView(text("把天气看简单",12,MUTED,false),lp(-2,-2));header.addView(brand,lp(-2,-2));spacer(header);
        header.addView(button("地区  ＋",false,this::showPicker));body.addView(header);gap(body,22);
        LinearLayout cityRow=row();
        TextView cityTitle=text(city.label()+"  ⌄",26,INK,true);cityTitle.setMinHeight(dp(48));cityTitle.setGravity(Gravity.CENTER_VERTICAL);tap(cityTitle,Color.TRANSPARENT,10,this::showPicker);
        cityRow.addView(cityTitle,new LinearLayout.LayoutParams(0,-2,1));
        TextView save=button(store.isSaved(city)?"★":"☆",false,()->{boolean saved=store.toggle(city);toast(saved?"已收藏地区":"已取消收藏");showWeather();});
        save.setTextSize(25);save.setPadding(0,0,0,0);save.setContentDescription(store.isSaved(city)?"取消收藏当前地区":"收藏当前地区");cityRow.addView(save,lp(48,48));body.addView(cityRow);
        TextView detail=text(city.detail(),12,MUTED,false);detail.setPadding(0,dp(5),0,dp(14));body.addView(detail);
        if(!notice.isEmpty()) {
            TextView n=text(notice,13,0xFF8B6021,false);n.setPadding(dp(14),dp(12),dp(14),dp(12));n.setBackground(shape(0xFFFFF3DD,14));body.addView(n);gap(body,12);
        }
        if(data==null) {emptyWeather();footer();return;}
        Instant now=Instant.now();boolean old=data.currentIsOld(now);
        LinearLayout hero=column();hero.setPadding(dp(22),dp(22),dp(18),dp(20));
        GradientDrawable gradient=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xFFE1EDFF,0xFFF1F6FF});gradient.setCornerRadius(dp(28));hero.setBackground(gradient);body.addView(hero,lp(-1,-2));
        LinearLayout heroTop=row();
        TextView label=text(old?"上次天气 · 历史缓存":"当前天气 · 模型估算",12,0xFF53749B,false);heroTop.addView(label);spacer(heroTop);
        TextView refresh=text(loading?"更新中…":"刷新 ↻",13,BLUE,true);refresh.setMinHeight(dp(40));refresh.setGravity(Gravity.CENTER);refresh.setPadding(dp(8),0,dp(5),0);tap(refresh,Color.TRANSPARENT,10,()->load(true));heroTop.addView(refresh);hero.addView(heroTop);
        LinearLayout currentRow=row();LinearLayout temperature=column();
        TextView number=text(WeatherData.degrees(WeatherData.value(data.current,"temperature_2m")),76,INK,false);number.setTypeface(Typeface.create("sans-serif-light",Typeface.NORMAL));temperature.addView(number);
        int code=WeatherData.code(data.current,"weather_code",-1);
        temperature.addView(text(WeatherData.condition(code)+"   体感 "+WeatherData.degrees(WeatherData.value(data.current,"apparent_temperature")),14,0xFF4F698C,false));
        currentRow.addView(temperature,new LinearLayout.LayoutParams(0,-2,1));currentRow.addView(new WeatherIcon(this,code,data.current.optInt("is_day",1)==1),lp(108,108));hero.addView(currentRow);gap(hero,20);
        int today=data.todayIndex(now);
        hero.addView(text("最高 "+WeatherData.degrees(WeatherData.at(data.daily,"temperature_2m_max",today))+"    最低 "+WeatherData.degrees(WeatherData.at(data.daily,"temperature_2m_min",today)),14,INK,false));gap(hero,13);
        String updated=DateTimeFormatter.ofPattern("M月d日 HH:mm").format(data.currentTime);
        hero.addView(text("数据时刻 "+updated+" · 地区当地时间",11,0xFF617D9E,false));
        LinearLayout attribution=row();TextView source=text("天气：Open-Meteo ↗  ·  CC BY 4.0",11,MUTED,false);source.setMinHeight(dp(42));source.setGravity(Gravity.CENTER_VERTICAL);tap(source,Color.TRANSPARENT,8,()->open("https://open-meteo.com/"));attribution.addView(source);body.addView(attribution);gap(body,3);
        hourly(now);gap(body,16);daily(now);gap(body,16);details(today);gap(body,20);footer();
    }
    private void emptyWeather() {
        LinearLayout c=card();c.setGravity(Gravity.CENTER);gap(c,20);c.addView(new WeatherIcon(this,-1,true),lp(110,110));gap(c,16);
        c.addView(text(loading?"正在获取天气":"还没有天气数据",19,INK,true));gap(c,12);
        TextView help=text(loading?"稍等片刻，看看这个地方的天气。":"联网后点击重试，也可以先切换地区。",13,MUTED,false);help.setGravity(Gravity.CENTER);c.addView(help);gap(c,20);
        if(!loading)c.addView(button("重新获取",true,()->load(true)));gap(c,18);gap(body,24);
    }
    private void hourly(Instant now) {
        LinearLayout c=card();section(c,"未来 24 小时","Open-Meteo ↗");
        List<Integer> indices=data.nextHours(now,24);
        if(indices.isEmpty()){c.addView(text("缓存预报已过期，联网刷新后查看",13,MUTED,false));return;}
        HorizontalScrollView scroll=new HorizontalScrollView(this);scroll.setHorizontalScrollBarEnabled(false);LinearLayout hours=row();hours.setGravity(Gravity.TOP);
        int currentHour=now.atZone(data.zone).getHour();LocalDate today=now.atZone(data.zone).toLocalDate();
        for(int index:indices) {
            String time=WeatherData.textAt(data.hourly,"time",index);LinearLayout cell=column();cell.setGravity(Gravity.CENTER);cell.setPadding(dp(4),dp(10),dp(4),dp(10));
            LocalDateTime date;try{date=LocalDateTime.parse(time);}catch(Exception e){continue;}
            boolean current=date.toLocalDate().equals(today)&&date.getHour()==currentHour;
            if(current)cell.setBackground(shape(PALE,16));
            cell.addView(text(current?"现在":String.format(Locale.ROOT,"%02d时",date.getHour()),12,current?BLUE:MUTED,current));gap(cell,7);
            cell.addView(new WeatherIcon(this,WeatherData.code(data.hourly,"weather_code",index),WeatherData.at(data.hourly,"is_day",index)!=0),lp(42,42));gap(cell,9);
            cell.addView(text(WeatherData.degrees(WeatherData.at(data.hourly,"temperature_2m",index)),18,INK,true));gap(cell,10);
            cell.addView(text(WeatherData.number(WeatherData.at(data.hourly,"precipitation_probability",index))+"%",11,BLUE,false));hours.addView(cell,lp(66,-2));
        }
        scroll.addView(hours);c.addView(scroll);gap(c,10);c.addView(text("底部百分比为降水概率 · 左右滑动查看",11,MUTED,false));
    }
    private void daily(Instant now) {
        LinearLayout c=card();section(c,"未来 7 天","Open-Meteo ↗");JSONArray times=data.daily.optJSONArray("time");LocalDate today=now.atZone(data.zone).toLocalDate();int count=0;
        for(int i=0;i<times.length();i++) {
            String iso=times.optString(i);try{if(LocalDate.parse(iso).isBefore(today))continue;}catch(Exception e){continue;}
            if(count++>0)line(c);
            LinearLayout r=row();r.setPadding(0,dp(12),0,dp(12));
            LinearLayout day=column();day.addView(text(WeatherData.day(iso,today),14,INK,true));gap(day,5);day.addView(text(iso.length()>=10?iso.substring(5).replace('-','/') : "—",11,MUTED,false));r.addView(day,lp(48,-2));
            int code=WeatherData.code(data.daily,"weather_code",i);r.addView(new WeatherIcon(this,code,true),lp(33,36));
            LinearLayout desc=column();desc.setPadding(dp(6),0,0,0);desc.addView(text(WeatherData.condition(code),12,INK,false));gap(desc,5);desc.addView(text(WeatherData.number(WeatherData.at(data.daily,"precipitation_probability_max",i))+"% 降水",10,MUTED,false));r.addView(desc,new LinearLayout.LayoutParams(0,-2,1));
            TextView range=text(WeatherData.degrees(WeatherData.at(data.daily,"temperature_2m_min",i))+"  /  "+WeatherData.degrees(WeatherData.at(data.daily,"temperature_2m_max",i)),15,INK,true);r.addView(range);c.addView(r);
        }
        if(count==0)c.addView(text("缓存预报已过期，请联网刷新",13,MUTED,false));
    }
    private void details(int today) {
        LinearLayout c=card();section(c,"天气详情","Open-Meteo ↗");
        metricRow(c,"相对湿度",WeatherData.number(WeatherData.value(data.current,"relative_humidity_2m"))+"%","风速",WeatherData.number(WeatherData.value(data.current,"wind_speed_10m"))+" km/h");
        gap(c,19);line(c);gap(c,19);
        double uv=WeatherData.at(data.daily,"uv_index_max",today);
        metricRow(c,"风向",WeatherData.wind(WeatherData.value(data.current,"wind_direction_10m")).isEmpty()?"—":WeatherData.wind(WeatherData.value(data.current,"wind_direction_10m")),"今日紫外线",Double.isFinite(uv)?String.format(Locale.ROOT,"%.1f",uv):"—");
        gap(c,19);line(c);gap(c,19);
        metricRow(c,"日出",WeatherData.clock(WeatherData.textAt(data.daily,"sunrise",today)),"日落",WeatherData.clock(WeatherData.textAt(data.daily,"sunset",today)));
    }
    private void metricRow(LinearLayout parent,String a,String av,String b,String bv) {
        LinearLayout r=row();String[] labels={a,b},values={av,bv};
        for(int i=0;i<2;i++){LinearLayout col=column();col.addView(text(labels[i],12,MUTED,false));gap(col,8);col.addView(text(values[i],19,INK,true));r.addView(col,new LinearLayout.LayoutParams(0,-2,1));}parent.addView(r);
    }
    private void footer() {
        TextView note=text("简单看天气，从容过每一天。",12,MUTED,false);note.setGravity(Gravity.CENTER);body.addView(note);gap(body,8);
        TextView about=text("数据来源与隐私  ·  v0.1.0-test",12,BLUE,false);about.setGravity(Gravity.CENTER);about.setMinHeight(dp(48));tap(about,Color.TRANSPARENT,10,this::about);body.addView(about);
    }
    private void load(boolean force) {
        if(dead)return;
        String key=city.key();WeatherData cache=store.cached(city);
        if(inFlight.contains(key)){toast("正在更新，请稍候");return;}
        long now=System.currentTimeMillis();
        if(!force&&cache!=null&&cache.fresh(now)) {data=cache;notice=cache.currentIsOld(Instant.now())?"当前显示的是较早的数据，可点击刷新更新。":"";if(!picking)showWeather();return;}
        Long previous=attempts.get(key);
        if(previous!=null&&now-previous<60_000){toast("刚刚更新过，请一分钟后再试");return;}
        attempts.put(key,now);inFlight.add(key);loading=true;notice="";if(!picking)showWeather();final City requested=city;
        workers.execute(()->{
            WeatherData received=null;String error=null;
            try {received=new WeatherData(api.forecast(requested),System.currentTimeMillis());store.save(requested,received);}
            catch(Exception e){error=e instanceof java.io.IOException?e.getMessage():"天气数据暂不可用，请稍后重试";if(error==null||!error.matches(".*[\\u4e00-\\u9fff].*"))error="网络连接失败，请检查网络后重试";}
            final WeatherData result=received;final String failure=error;
            main.post(()->{if(dead)return;inFlight.remove(key);if(!city.key().equals(key))return;loading=false;
                if(result!=null){data=result;notice="";}else{data=store.cached(requested);notice=data==null?failure:failure+"；当前显示上次保存的数据。";}
                if(!picking)showWeather();
            });
        });
    }
    private void choose(City selected) {
        hideKeyboard();city=selected;store.select(city);data=store.cached(city);notice="";loading=inFlight.contains(city.key());showWeather();if(!loading)load(false);
    }
    private void showPicker() {
        picking=true;searchVersion++;startScreen();
        LinearLayout heading=row();heading.addView(text("选择地区",25,INK,true));spacer(heading);heading.addView(button("完成",false,()->{hideKeyboard();showWeather();}));body.addView(heading);gap(body,10);
        body.addView(text("搜索城市、区县或海外地区",13,MUTED,false));gap(body,18);
        LinearLayout input=row();input.setBackground(shape(WHITE,18));input.setPadding(dp(13),0,dp(4),0);
        search=new EditText(this);search.setSingleLine(true);search.setTextSize(16);search.setTextColor(INK);search.setHintTextColor(MUTED);search.setHint("例如：杭州、海淀、Tokyo");search.setBackgroundColor(Color.TRANSPARENT);search.setPadding(0,dp(15),dp(6),dp(15));search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);search.setInputType(android.text.InputType.TYPE_CLASS_TEXT);input.addView(search,new LinearLayout.LayoutParams(0,-2,1));
        input.addView(button("搜索",true,this::searchOnline));body.addView(input);gap(body,20);
        results=column();body.addView(results);searchStatus=text("",13,MUTED,false);
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){searchVersion++;renderLocal(s.toString());}public void afterTextChanged(Editable e){}});
        search.setOnEditorActionListener((v,action,event)->{if(action==EditorInfo.IME_ACTION_SEARCH){searchOnline();return true;}return false;});
        renderLocal("");
        gap(body,20);TextView credit=text("地区数据：GeoNames，经 Open-Meteo 提供 ↗\nCC BY 4.0 · 可搜索范围以数据源收录为准",11,MUTED,false);credit.setLineSpacing(dp(5),1);credit.setMinHeight(dp(48));tap(credit,Color.TRANSPARENT,8,()->open("https://www.geonames.org/"));body.addView(credit);
    }
    private void renderLocal(String query) {
        results.removeAllViews();String q=query.trim();
        if(q.isEmpty()) {
            List<City> saved=store.saved();if(!saved.isEmpty()){results.addView(text("我的地区",16,INK,true));gap(results,10);cityList(saved);gap(results,22);}
            results.addView(text("常用地区",16,INK,true));gap(results,12);
            for(int i=0;i<catalog.size();i+=3){LinearLayout r=row();for(int j=i;j<Math.min(i+3,catalog.size());j++){City c=catalog.get(j);TextView chip=button(c.label(),false,()->choose(c));chip.setTextSize(13);chip.setPadding(dp(4),dp(10),dp(4),dp(10));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.setMargins(0,0,dp(7),dp(8));r.addView(chip,p);}results.addView(r);}
        } else {
            List<City> matched=new ArrayList<>();Set<String> keys=new HashSet<>();
            for(City c:store.saved())if(c.matches(q)&&keys.add(c.key()))matched.add(c);
            for(City c:catalog)if(c.matches(q)&&keys.add(c.key()))matched.add(c);
            results.addView(text(matched.isEmpty()?"点击搜索，查找更多地区":"本地匹配",15,INK,true));gap(results,10);cityList(matched);
            gap(results,8);results.addView(text("点击“搜索”获取全球城市与区县结果。",12,MUTED,false));
        }
    }
    private void cityList(List<City> cities) {
        for(City c:cities) {
            LinearLayout item=row();item.setPadding(dp(14),dp(14),dp(12),dp(14));LinearLayout labels=column();labels.addView(text(c.label(),16,INK,true));gap(labels,6);labels.addView(text(c.detail(),12,MUTED,false));item.addView(labels,new LinearLayout.LayoutParams(0,-2,1));item.addView(text(city.key().equals(c.key())?"当前":"›",14,BLUE,false));tap(item,WHITE,16,()->choose(c));results.addView(item,lp(-1,-2));gap(results,8);
        }
    }
    private void searchOnline() {
        if(search==null)return;final String query=search.getText().toString().trim();if(query.length()<2){toast("请输入至少两个字符");return;}
        final int version=++searchVersion;hideKeyboard();results.removeAllViews();results.addView(text("正在查找“"+query+"”…",14,MUTED,false));
        workers.execute(()->{
            List<City> found=new ArrayList<>();String failure=null;try{found=api.search(query);}catch(Exception e){failure="地区搜索暂不可用，请检查网络或稍后重试。";}
            final List<City> list=new ArrayList<>();Set<String> seen=new HashSet<>();
            for(City c:catalog)if(c.matches(query)&&seen.add(c.key()))list.add(c);
            for(City c:found)if(seen.add(c.key()))list.add(c);
            final String error=failure;
            main.post(()->{if(dead||!picking||version!=searchVersion)return;results.removeAllViews();
                results.addView(text(error!=null?error:list.isEmpty()?"没有找到该地区，试试城市名或拼音。":"搜索结果 · 注意区分同名地区",14,error!=null?0xFF8B6021:INK,false));gap(results,14);cityList(list);
            });
        });
    }
    private void about() {
        LinearLayout content=column();content.setPadding(dp(22),dp(8),dp(22),dp(10));
        TextView details=text("简天气 0.1.0-test（测试版） · 个人非商业版\n\n天气由 Open-Meteo 提供；地区检索基于 GeoNames。两者均按 CC BY 4.0 署名。应用对返回数据进行了中文描述、取整、单位标注与排版。\n\n当前天气为数值模型估算，非气象台站现场观测，也不替代官方预警。日期、小时与日出日落均按所选地区的当地时区显示。\n\n隐私：无需账号、无需定位权限；仅联网查询。你的 IP、搜索词及所选地区坐标会发送给 Open-Meteo。收藏与天气缓存保存在本机；没有广告、统计或自建服务器。\n\n免费 API 仅限非商业使用。天气缓存 15 分钟，手动刷新间隔至少 1 分钟；服务限流时暂停请求。商业用途应另行取得服务授权。",14,INK,false);details.setLineSpacing(dp(4),1);content.addView(details);gap(content,16);
        link(content,"Open-Meteo · 天气数据来源","https://open-meteo.com/");link(content,"GeoNames · 地区数据来源","https://www.geonames.org/");link(content,"CC BY 4.0 · 数据许可","https://creativecommons.org/licenses/by/4.0/");link(content,"Open-Meteo · 使用条款与隐私","https://open-meteo.com/en/terms");
        ScrollView scroll=new ScrollView(this);scroll.addView(content);new AlertDialog.Builder(this).setTitle("数据来源与隐私").setView(scroll).setPositiveButton("知道了",null).show();
    }
    private void link(LinearLayout parent,String label,String url) {TextView t=text(label+" ↗",13,BLUE,false);t.setMinHeight(dp(48));t.setGravity(Gravity.CENTER_VERTICAL);tap(t,Color.TRANSPARENT,8,()->open(url));parent.addView(t);}
    private void open(String url) {try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(ActivityNotFoundException e){toast("未找到可打开链接的浏览器");}}
    private void hideKeyboard() {View focused=getCurrentFocus();if(focused!=null){((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(focused.getWindowToken(),0);focused.clearFocus();}}
    private void toast(String message) {Toast.makeText(this,message,Toast.LENGTH_SHORT).show();}
    @Override public void onBackPressed() {if(picking){hideKeyboard();showWeather();}else super.onBackPressed();}
    @Override protected void onDestroy(){dead=true;main.removeCallbacksAndMessages(null);workers.shutdownNow();super.onDestroy();}
}
