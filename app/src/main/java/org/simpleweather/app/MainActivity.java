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
    static final int BG=0xFFF5F7FB, WHITE=Color.WHITE, INK=0xFF20324C, MUTED=0xFF718097, BLUE=0xFF246BCE, PALE=0xFFEAF2FF;
    private final ExecutorService workers=Executors.newFixedThreadPool(2);
    private final Handler main=new Handler(Looper.getMainLooper());
    private WeatherApi api;
    private final Set<String> inFlight=new HashSet<>();
    private final Map<String,Long> attempts=new HashMap<>();
    private WeatherStore store;
    private List<City> catalog;
    private City city;
    private WeatherData data;
    private LinearLayout host, results;
    LinearLayout body;
    private int tab=0;
    private boolean astronomy=false;
    private ScheduleScreen schedule;
    private GpsScreen gps;
    private android.graphics.Bitmap background;
    private android.content.SharedPreferences preferences;
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
        preferences=getSharedPreferences("toolbox",0);schedule=new ScheduleScreen(this);gps=new GpsScreen(this);
        background=android.graphics.BitmapFactory.decodeFile(new java.io.File(getFilesDir(),"background.jpg").getPath());
        showWeather();load(false);
        if(preferences.getInt("seen_version",0)<2)main.postDelayed(()->{if(!dead){updates();preferences.edit().putInt("seen_version",2).apply();}},700);
    }
    int dp(float n) {return Math.round(n*getResources().getDisplayMetrics().density);}
    LinearLayout column() {LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    LinearLayout row() {LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    LinearLayout.LayoutParams lp(int w,int h) {return new LinearLayout.LayoutParams(w<0?w:dp(w),h<0?h:dp(h));}
    TextView text(String value,int size,int color,boolean bold) {
        TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);
        t.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));t.setIncludeFontPadding(false);
        t.setLineSpacing(dp(3),1);return t;
    }
    GradientDrawable shape(int color,int radius) {GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    void tap(View v,int color,int radius,Runnable action) {
        v.setBackground(new RippleDrawable(android.content.res.ColorStateList.valueOf(0x223577CC),shape(color,radius),shape(WHITE,radius)));
        v.setOnClickListener(view->action.run());v.setClickable(true);v.setFocusable(true);
    }
    TextView button(String title, boolean primary, Runnable click) {
        TextView t=text(title,14,primary?WHITE:BLUE,true);t.setGravity(Gravity.CENTER);
        t.setMinHeight(dp(48));t.setPadding(dp(16),dp(10),dp(16),dp(10));tap(t,primary?BLUE:PALE,16,click);return t;
    }
    void gap(LinearLayout parent,int h) {View v=new View(this);parent.addView(v,lp(1,h));}
    void spacer(LinearLayout parent) {parent.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));}
    void line(LinearLayout parent) {View v=new View(this);v.setBackgroundColor(0xFFF0F3F8);parent.addView(v,new LinearLayout.LayoutParams(-1,dp(1)));}
    private void startScreen() {
        android.widget.FrameLayout base=new android.widget.FrameLayout(this);
        if(background!=null){ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setImageBitmap(background);base.addView(image,new FrameLayout.LayoutParams(-1,-1));View shade=new View(this);shade.setBackgroundColor(android.graphics.Color.argb(preferences.getInt("shade",80)*255/100,245,247,251));base.addView(shade,new FrameLayout.LayoutParams(-1,-1));}else base.setBackgroundColor(BG);
        host=column();base.addView(host,new FrameLayout.LayoutParams(-1,-1));setContentView(base);
        host.setOnApplyWindowInsetsListener((view,insets)->{
            if(Build.VERSION.SDK_INT>=30){android.graphics.Insets i=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());int bottom=Math.max(i.bottom,insets.getInsets(WindowInsets.Type.ime()).bottom);view.setPadding(i.left,i.top,i.right,bottom);}
            else view.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            return insets;
        });host.requestApplyInsets();
        FrameLayout frame=new FrameLayout(this);host.addView(frame,new LinearLayout.LayoutParams(-1,0,1));
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);scroll.setVerticalScrollBarEnabled(false);
        FrameLayout.LayoutParams fit=new FrameLayout.LayoutParams(Math.min(getResources().getDisplayMetrics().widthPixels,dp(620)),-1,Gravity.CENTER_HORIZONTAL);
        frame.addView(scroll,fit);body=column();body.setPadding(dp(20),dp(18),dp(20),dp(28));scroll.addView(body);
        LinearLayout navigation=row();navigation.setPadding(dp(10),dp(7),dp(10),dp(7));navigation.setBackgroundColor(0xF2FFFFFF);
        String[] titles={"天气","课程表","GPS定位"};for(int n=0;n<3;n++){final int target=n;TextView item=button(titles[n],false,()->switchTab(target));if(tab!=n){item.setTextColor(MUTED);tap(item,Color.TRANSPARENT,16,()->switchTab(target));}navigation.addView(item,new LinearLayout.LayoutParams(0,dp(50),1));}host.addView(navigation,lp(-1,-2));
    }
    LinearLayout card() {LinearLayout c=column();c.setPadding(dp(18),dp(20),dp(18),dp(18));c.setBackground(shape(WHITE,24));body.addView(c,lp(-1,-2));return c;}
    private void section(LinearLayout parent,String name,String aside) {
        LinearLayout r=row();r.addView(text(name,17,INK,true));spacer(r);
        TextView source=text(aside,11,MUTED,false);source.setPadding(dp(5),dp(8),0,dp(8));source.setMinHeight(dp(32));
        tap(source,Color.TRANSPARENT,6,()->open("https://open-meteo.com/"));r.addView(source);parent.addView(r);gap(parent,12);
    }
    private void showWeather() {
        tab=0;astronomy=false;picking=false;searchVersion++;startScreen();
        LinearLayout header=row();
        header.addView(text("呆呆工具箱",21,INK,true));spacer(header);
        header.addView(button("地区",false,this::showPicker));header.addView(button("设置",false,this::settings));body.addView(header);gap(body,22);
        LinearLayout cityRow=row();
        TextView cityTitle=text(city.label()+"  ⌄",26,INK,true);cityTitle.setMinHeight(dp(48));cityTitle.setGravity(Gravity.CENTER_VERTICAL);tap(cityTitle,Color.TRANSPARENT,10,this::showPicker);
        cityRow.addView(cityTitle,new LinearLayout.LayoutParams(0,-2,1));
        TextView save=button(store.isSaved(city)?"★":"☆",false,()->{boolean saved=store.toggle(city);toast(saved?"已收藏地区":"已取消收藏");showWeather();});
        save.setTextSize(25);save.setPadding(0,0,0,0);save.setContentDescription(store.isSaved(city)?"取消收藏当前地区":"收藏当前地区");cityRow.addView(save,lp(48,48));body.addView(cityRow);
        TextView detail=text(city.detail(),12,MUTED,false);detail.setPadding(0,dp(5),0,dp(14));body.addView(detail);
        if(!notice.isEmpty()) {
            TextView n=text(notice,13,0xFF8B6021,false);n.setPadding(dp(14),dp(12),dp(14),dp(12));n.setBackground(shape(0xFFFFF3DD,14));body.addView(n);gap(body,12);
        }
        if(data==null) {emptyWeather();weatherTools();footer();return;}
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
        hourly(now);gap(body,16);daily(now);gap(body,16);details(today);gap(body,16);weatherTools();gap(body,20);footer();
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
        TextView note=text("呆呆鸟使用Codex制作",12,MUTED,false);note.setGravity(Gravity.CENTER);body.addView(note);gap(body,8);
        TextView about=text("数据来源与隐私  ·  v1.0.0",12,BLUE,false);about.setGravity(Gravity.CENTER);about.setMinHeight(dp(48));tap(about,Color.TRANSPARENT,10,this::about);body.addView(about);
    }
    private void load(boolean force) {
        if(dead)return;
        String key=city.key();WeatherData cache=store.cached(city);
        if(inFlight.contains(key)){toast("正在更新，请稍候");return;}
        long now=System.currentTimeMillis();
        if(!force&&cache!=null&&cache.fresh(now)) {data=cache;notice=cache.currentIsOld(Instant.now())?"当前显示的是较早的数据，可点击刷新更新。":"";if(weatherVisible())showWeather();return;}
        Long previous=attempts.get(key);
        if(previous!=null&&now-previous<60_000){toast("刚刚更新过，请一分钟后再试");return;}
        attempts.put(key,now);inFlight.add(key);loading=true;notice="";if(weatherVisible())showWeather();final City requested=city;
        workers.execute(()->{
            WeatherData received=null;String error=null;
            try {received=new WeatherData(api.forecast(requested),System.currentTimeMillis());store.save(requested,received);}
            catch(Exception e){error=e instanceof java.io.IOException?e.getMessage():"天气数据暂不可用，请稍后重试";if(error==null||!error.matches(".*[\\u4e00-\\u9fff].*"))error="网络连接失败，请检查网络后重试";}
            final WeatherData result=received;final String failure=error;
            main.post(()->{if(dead)return;inFlight.remove(key);if(!city.key().equals(key))return;loading=false;
                if(result!=null){data=result;notice="";}else{data=store.cached(requested);notice=data==null?failure:failure+"；当前显示上次保存的数据。";}
                if(weatherVisible())showWeather();
            });
        });
    }
    private void choose(City selected) {
        hideKeyboard();city=selected;store.select(city);data=store.cached(city);notice="";loading=inFlight.contains(city.key());showWeather();if(!loading)load(false);
    }
    private void showPicker() {
        tab=0;astronomy=false;picking=true;searchVersion++;startScreen();
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
    private boolean weatherVisible(){return tab==0&&!picking&&!astronomy;}
    void switchTab(int next){hideKeyboard();if(next!=2)gps.stopForNavigation();tab=next;picking=false;astronomy=false;if(next==0)showWeather();else if(next==1){startScreen();schedule.render();}else{startScreen();gps.render();}}
    private void weatherTools(){
        gap(body,16);LinearLayout c=card();c.addView(text("天气图与天文",17,INK,true));gap(c,12);
        c.addView(button("全国降水 · 气温 · 区域雷达",false,this::weatherMaps));gap(c,10);
        c.addView(button("天文 · 观星 / 星图 / 日月",false,()->showAstronomy(Instant.now())));
    }
    private void weatherMaps(){String[] names={"全国 1 小时降水实况","全国 24 小时降水实况","全国 24 小时降水预报","全国逐时气温","雷达 · 全国","雷达 · 华北","雷达 · 东北","雷达 · 华东","雷达 · 华中","雷达 · 华南","雷达 · 西南","雷达 · 西北","中国天气网 · 雷达"};String[] paths={"observations/hourly-precipitation.html","observations/24hour-precipitation.html","precipitation/1-day.html","observations/hourly-temperature.html","radar/chinaall.html","radar/huabei.html","radar/dongbei.html","radar/huadong.html","radar/huazhong.html","radar/huanan.html","radar/xinan.html","radar/xibei.html"};new AlertDialog.Builder(this).setTitle("官方天气图 · 原站页面").setItems(names,(d,n)->official(names[n],n<paths.length?"https://www.nmc.cn/publish/"+paths[n]:"https://www.weather.com.cn/radar/")).setNegativeButton("返回",null).show();}
    void official(String title,String url){Intent i=new Intent(this,OfficialPageActivity.class);i.putExtra("title",title);i.putExtra("url",url);startActivity(i);}
    private void showAstronomy(Instant at){tab=0;picking=false;astronomy=true;startScreen();new AstronomyScreen(this,city,data,at,this::showAstronomy).render();}
    void settings(){LinearLayout c=column();c.setPadding(dp(20),dp(8),dp(20),dp(10));c.addView(button("从手机导入背景图片",false,()->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("image/*");try{startActivityForResult(i,42);}catch(ActivityNotFoundException e){toast("未找到系统图片选择器");}}));gap(c,10);c.addView(button("恢复默认背景",false,()->{new java.io.File(getFilesDir(),"background.jpg").delete();background=null;switchTab(tab);}));gap(c,16);c.addView(text("背景遮罩 · 调整文字清晰度",14,INK,true));SeekBar shade=new SeekBar(this);shade.setMax(90);shade.setProgress(preferences.getInt("shade",80)-10);shade.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int n,boolean user){if(user)preferences.edit().putInt("shade",n+10).apply();}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){switchTab(tab);}});c.addView(shade,lp(-1,48));c.addView(button("版本更新记录",false,this::updates));gap(c,10);c.addView(button("数据来源与隐私",false,this::about));new AlertDialog.Builder(this).setTitle("呆呆工具箱 · 设置").setView(c).setPositiveButton("完成",null).show();}
    private void importBackground(Uri uri){toast("正在处理背景图片…");workers.execute(()->{android.graphics.Bitmap bitmap=null;try{android.graphics.BitmapFactory.Options opts=new android.graphics.BitmapFactory.Options();opts.inJustDecodeBounds=true;try(java.io.InputStream in=getContentResolver().openInputStream(uri)){android.graphics.BitmapFactory.decodeStream(in,null,opts);}if(opts.outWidth<=0||opts.outHeight<=0)throw new java.io.IOException();opts.inSampleSize=1;while((long)(opts.outWidth/opts.inSampleSize)*(opts.outHeight/opts.inSampleSize)>4_000_000L||Math.max(opts.outWidth,opts.outHeight)/opts.inSampleSize>2400)opts.inSampleSize*=2;opts.inJustDecodeBounds=false;try(java.io.InputStream in=getContentResolver().openInputStream(uri)){bitmap=android.graphics.BitmapFactory.decodeStream(in,null,opts);}if(bitmap==null)throw new java.io.IOException();try(java.io.InputStream in=getContentResolver().openInputStream(uri)){android.media.ExifInterface exif=new android.media.ExifInterface(in);int orientation=exif.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION,1);android.graphics.Matrix matrix=new android.graphics.Matrix();switch(orientation){case 2:matrix.setScale(-1,1);break;case 3:matrix.setRotate(180);break;case 4:matrix.setScale(1,-1);break;case 5:matrix.setRotate(90);matrix.postScale(-1,1);break;case 6:matrix.setRotate(90);break;case 7:matrix.setRotate(270);matrix.postScale(-1,1);break;case 8:matrix.setRotate(270);break;}if(!matrix.isIdentity())bitmap=android.graphics.Bitmap.createBitmap(bitmap,0,0,bitmap.getWidth(),bitmap.getHeight(),matrix,true);}catch(Exception ignored){}java.io.File temp=new java.io.File(getFilesDir(),"background.tmp"),dest=new java.io.File(getFilesDir(),"background.jpg");try(java.io.FileOutputStream out=new java.io.FileOutputStream(temp)){if(!bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG,88,out))throw new java.io.IOException();}java.nio.file.Files.move(temp.toPath(),dest.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);final android.graphics.Bitmap loaded=bitmap;main.post(()->{if(!dead){background=loaded;switchTab(tab);toast("背景已保存到手机内部");}});}catch(Exception|OutOfMemoryError e){main.post(()->{if(!dead)toast("无法读取图片，请换一张较小的图片重试");});}});}
    private void updates(){LinearLayout c=column();c.setPadding(dp(20),dp(8),dp(20),dp(16));c.addView(text("1.0.0 · 呆呆工具箱",19,INK,true));gap(c,12);c.addView(text("• 正式名称改为呆呆工具箱，新增天气 / 课程表 / GPS 底部导航。\n• 新增手动周课表、自定义节次、地点、老师与单双周。\n• 新增手机图片背景及遮罩调整。\n• 新增前台 GPS 经纬度、水平精度与状态。\n• 新增官方全国降水、温度和分区雷达网页。\n• 新增观星条件、星图、日月位置、月相和曙暮光。\n• 整理来源与隐私，Open-Meteo 入口放在一起。\n• 页脚改为“呆呆鸟使用Codex制作”。",14,INK,false));gap(c,20);c.addView(text("0.1.0-test · 简天气测试版",17,INK,true));gap(c,10);c.addView(text("早期版本（原标注 1.0.0）统一归为测试版：地区搜索与收藏、当前天气、24 小时及 7 日预报、离线缓存。",14,MUTED,false));ScrollView s=new ScrollView(this);s.addView(c);new AlertDialog.Builder(this).setTitle("每个版本的更新内容").setView(s).setPositiveButton("知道了",null).show();}
    private void about() {
        LinearLayout content=column();content.setPadding(dp(22),dp(8),dp(22),dp(10));
        TextView details=text("呆呆工具箱 1.0.0 · 个人非商业版\n\n天气由 Open-Meteo 提供；地区检索基于 GeoNames。两者均按 CC BY 4.0 署名。应用对返回数据进行了中文描述、取整、单位标注与排版。\n\n当前天气为数值模型估算，非气象台站现场观测，也不替代官方预警。日期、小时与日出日落均按所选地区的当地时区显示。\n\n隐私：无需账号。GPS 仅在你授权后于前台获取，坐标不上传；课程表与导入的背景图只保存在手机内部。天气查询需要联网。你的 IP、搜索词及所选地区坐标会发送给 Open-Meteo。收藏与天气缓存保存在本机；没有广告、统计或自建服务器。\n\n免费 API 仅限非商业使用。天气缓存 15 分钟，手动刷新间隔至少 1 分钟；服务限流时暂停请求。商业用途应另行取得服务授权。",14,INK,false);details.setLineSpacing(dp(4),1);content.addView(details);gap(content,16);
        link(content,"Open-Meteo · 天气数据来源","https://open-meteo.com/");link(content,"Open-Meteo · 使用条款与隐私","https://open-meteo.com/en/terms");link(content,"GeoNames · 地区数据来源","https://www.geonames.org/");link(content,"CC BY 4.0 · 数据许可","https://creativecommons.org/licenses/by/4.0/");gap(content,10);content.addView(text("全国降水、气温和雷达通过应用内官方网页查看，版权归中国天气网 / 中央气象台。天文计算为本地近似结果。官方网页会按其自身规则处理访问数据。",12,MUTED,false));link(content,"中国天气网 · 官方页面","https://www.weather.com.cn/");link(content,"中央气象台 · 官方页面","https://www.nmc.cn/");link(content,"天文计算方法参考","https://stjarnhimlen.se/comp/ppcomp.html");content.addView(button("版本更新记录",false,this::updates));
        ScrollView scroll=new ScrollView(this);scroll.addView(content);new AlertDialog.Builder(this).setTitle("数据来源与隐私").setView(scroll).setPositiveButton("知道了",null).show();
    }
    void link(LinearLayout parent,String label,String url) {TextView t=text(label+" ↗",13,BLUE,false);t.setMinHeight(dp(48));t.setGravity(Gravity.CENTER_VERTICAL);tap(t,Color.TRANSPARENT,8,()->open(url));parent.addView(t);}
    void open(String url) {try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(ActivityNotFoundException e){toast("未找到可打开链接的浏览器");}}
    private void hideKeyboard() {View focused=getCurrentFocus();if(focused!=null){((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(focused.getWindowToken(),0);focused.clearFocus();}}
    void toast(String message) {Toast.makeText(this,message,Toast.LENGTH_SHORT).show();}
    @Override public void onBackPressed() {if(picking||astronomy){hideKeyboard();showWeather();}else if(tab!=0)switchTab(0);else super.onBackPressed();}
    @Override protected void onPause(){if(gps!=null)gps.pause();super.onPause();}
    @Override protected void onResume(){super.onResume();if(gps!=null&&tab==2)gps.resume();}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){super.onRequestPermissionsResult(request,permissions,grants);if(request==41)gps.permissionResult();}
    @Override protected void onActivityResult(int request,int result,Intent intent){super.onActivityResult(request,result,intent);if(request==42&&result==RESULT_OK&&intent!=null&&intent.getData()!=null)importBackground(intent.getData());}
    @Override protected void onDestroy(){dead=true;if(gps!=null)gps.pause();main.removeCallbacksAndMessages(null);workers.shutdownNow();super.onDestroy();}
}
