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
    private ScrollView pageScroll;
    private LinearLayout navigation;
    private int navigationOffset;
    private Instant astronomyTime=Instant.now();
    private int pendingScrollY=-1;
    private MapScreen maps;
    private ScheduleScreen schedule;
    private GpsScreen gps;
    private EarthquakeScreen earthquakes;
    private android.graphics.Bitmap background;
    android.content.SharedPreferences preferences;
    private EditText search;
    private TextView searchStatus;
    private boolean picking, loading, dead;
    private String notice="";
    private int searchVersion;
    private int cacheGeneration;
    private boolean deferMapReload;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if(getResources().getConfiguration().smallestScreenWidthDp>=600)setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        if(Build.VERSION.SDK_INT>=29)getWindow().setNavigationBarContrastEnforced(false);
        store=new WeatherStore(this);api=new WeatherApi(store);catalog=store.catalog();
        if(catalog.isEmpty()) {
            try {catalog.add(new City(new JSONObject("{\"name\":\"北京\",\"latitude\":39.9042,\"longitude\":116.4074,\"country\":\"中国\",\"timezone\":\"Asia/Shanghai\"}")));}catch(Exception ignored){}
        }
        city=store.selected(catalog.get(0));data=store.cached(city);
        preferences=getSharedPreferences("toolbox",0);
        if(preferences.getInt("settings_schema",0)<4){SharedPreferences.Editor e=preferences.edit();if(!preferences.contains("background_brightness"))e.putInt("background_brightness",100-Math.max(0,Math.min(100,preferences.getInt("shade",80))));if(preferences.getInt("radar_region",0)>7)e.putInt("radar_region",0);if(preferences.getInt("map_product",0)==4)e.putInt("map_product",0);e.remove("shade").putInt("settings_schema",4).apply();}
        if(preferences.getInt("star_names_schema",0)<1){String[][] renamed={{"织女星","织女一"},{"牛郎星","河鼓二"},{"北极星","勾陈一"}};SharedPreferences.Editor e=preferences.edit();for(String[] pair:renamed){String oldKey="astro_star_"+pair[0],newKey="astro_star_"+pair[1];if(preferences.contains(oldKey)&&!preferences.contains(newKey))e.putBoolean(newKey,preferences.getBoolean(oldKey,true));if(preferences.getString("astro_selected","").equals(pair[0]))e.putString("astro_selected",pair[1]);}e.putInt("star_names_schema",1).apply();}
        schedule=new ScheduleScreen(this);gps=new GpsScreen(this);earthquakes=new EarthquakeScreen(this);earthquakes.restoreState(state);
        background=android.graphics.BitmapFactory.decodeFile(new java.io.File(getFilesDir(),"background.jpg").getPath());
        if(state==null&&getIntent().getBooleanExtra("open_location",false))preferences.edit().putInt("last_tab",2).putBoolean("tab_show_2",true).apply();
        tab=state==null?preferences.getInt("last_tab",0):state.getInt("toolbox_tab",preferences.getInt("last_tab",0));if(state!=null){astronomyTime=Instant.ofEpochMilli(state.getLong("toolbox_astro_time",astronomyTime.toEpochMilli()));navigationOffset=state.getInt("toolbox_navigation",0);pendingScrollY=state.getInt("toolbox_scroll",0);}if(!visibleTabs().contains(tab))tab=visibleTabs().get(0);switchTab(tab);load(false);
        if(preferences.getInt("seen_version",0)<21)main.postDelayed(()->{if(!dead){updates();preferences.edit().putInt("seen_version",21).apply();}},700);
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
    GradientDrawable shape(int color,int radius) {GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));if(outlined())d.setStroke(dp(1),outlineColor());return d;}
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
        if(maps!=null){maps.destroy();maps=null;}
        android.widget.FrameLayout base=new android.widget.FrameLayout(this);
        if(background!=null){ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setImageBitmap(background);base.addView(image,new FrameLayout.LayoutParams(-1,-1));View shade=new View(this);shade.setBackgroundColor(android.graphics.Color.argb((100-Math.max(0,Math.min(100,preferences.getInt("background_brightness",20))))*255/100,245,247,251));base.addView(shade,new FrameLayout.LayoutParams(-1,-1));}else base.setBackgroundColor(BG);
        host=column();base.addView(host,new FrameLayout.LayoutParams(-1,-1));setContentView(base);
        host.setOnApplyWindowInsetsListener((view,insets)->{
            if(Build.VERSION.SDK_INT>=30){android.graphics.Insets i=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());int bottom=Math.max(i.bottom,insets.getInsets(WindowInsets.Type.ime()).bottom);view.setPadding(i.left,i.top,i.right,bottom);}
            else view.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            return insets;
        });host.requestApplyInsets();
        FrameLayout frame=new FrameLayout(this);host.addView(frame,new LinearLayout.LayoutParams(-1,0,1));
        body=column();body.setPadding(dp(20),dp(18),dp(20),dp(28));
        if(tab==3){pageScroll=null;body.setPadding(dp(12),dp(12),dp(12),0);frame.addView(body,new FrameLayout.LayoutParams(-1,-1));}
        else{pageScroll=new ScrollView(this);pageScroll.setFillViewport(true);pageScroll.setClipToPadding(false);pageScroll.setVerticalScrollBarEnabled(false);final int restore=pendingScrollY;pendingScrollY=-1;pageScroll.addOnLayoutChangeListener(new View.OnLayoutChangeListener(){public void onLayoutChange(View v,int l,int t,int r,int b,int ol,int ot,int or,int ob){if(restore>=0){v.scrollTo(0,restore);v.removeOnLayoutChangeListener(this);}}});FrameLayout.LayoutParams fit=new FrameLayout.LayoutParams(Math.min(getResources().getDisplayMetrics().widthPixels,dp(620)),-1,Gravity.CENTER_HORIZONTAL);frame.addView(pageScroll,fit);pageScroll.addView(body);}
        pageHeader();
        navigation=row();navigation.setPadding(dp(8),dp(6),dp(8),dp(6));navigation.setBackground(shape(0xF2FFFFFF,0));host.addView(navigation,lp(-1,-2));renderNavigation();
    }
    static final String[] TAB_NAMES={"天气","课程表","定位","天气地图","天文","地震"};
    static final String[] WEATHER_NAMES={"地区","当前天气","未来24小时","未来7天","天气详情","数据来源入口"};
    boolean outlined(){return preferences!=null&&preferences.getBoolean("outline",false);}
    int outlineColor(){return preferences.getInt("outline_color",Color.BLACK);}
    List<Integer> visibleTabs(){List<Integer> all=DisplayOrder.parse(preferences.getString("tab_order",""),TAB_NAMES.length);List<Integer> result=new ArrayList<>();for(int id:all)if(preferences.getBoolean("tab_show_"+id,id<3||id==5))result.add(id);if(result.isEmpty())result.add(0);return result;}
    void refreshScreen(){List<Integer> visible=visibleTabs();if(!visible.contains(tab))tab=visible.get(0);switchTab(tab);}
    private void pageHeader(){
        TextView title=text("呆呆工具箱 · "+TAB_NAMES[tab],25,INK,true);body.addView(title);gap(body,7);body.addView(text("呆呆鸟使用 Codex 制作",12,MUTED,false));gap(body,12);
        LinearLayout actions=row();if(tab==0||tab==4){actions.addView(button("地区",false,this::showPicker));gapHorizontal(actions,8);}actions.addView(button("设置",false,this::settings));if(tab==1){gapHorizontal(actions,8);actions.addView(button("新增课程",true,()->schedule.addCourse()));}body.addView(actions);gap(body,16);
    }
    void gapHorizontal(LinearLayout parent,int width){parent.addView(new View(this),lp(width,1));}
    private void renderNavigation(){
        navigation.removeAllViews();List<Integer> visible=visibleTabs();navigation.setVisibility(visible.size()<2?View.GONE:View.VISIBLE);if(visible.size()<2)return;
        navigationOffset=Math.max(0,Math.min(navigationOffset,Math.max(0,visible.size()-3)));
        if(visible.size()>=5){TextView left=button("‹",false,()->{navigationOffset=Math.max(0,navigationOffset-1);renderNavigation();});left.setContentDescription("底栏向左翻页");left.setEnabled(navigationOffset>0);left.setAlpha(navigationOffset>0?1:.35f);left.setPadding(0,0,0,0);navigation.addView(left,lp(40,50));}
        for(int id:DisplayOrder.window(visible,navigationOffset)){TextView item=button(TAB_NAMES[id],false,()->switchTab(id));item.setContentDescription("切换到"+TAB_NAMES[id]);item.setMaxLines(1);item.setHorizontallyScrolling(false);item.setAutoSizeTextTypeUniformWithConfiguration(10,visible.size()==4?12:14,1,android.util.TypedValue.COMPLEX_UNIT_SP);item.setPadding(dp(3),dp(8),dp(3),dp(8));item.setGravity(Gravity.CENTER);if(tab!=id){item.setTextColor(MUTED);tap(item,Color.TRANSPARENT,16,()->switchTab(id));}if(navigation.getChildCount()>0)gapHorizontal(navigation,6);navigation.addView(item,new LinearLayout.LayoutParams(0,dp(50),1));}
        if(visible.size()>=5){TextView right=button("›",false,()->{navigationOffset=Math.min(visible.size()-3,navigationOffset+1);renderNavigation();});right.setContentDescription("底栏向右翻页");right.setEnabled(navigationOffset<visible.size()-3);right.setAlpha(navigationOffset<visible.size()-3?1:.35f);right.setPadding(0,0,0,0);gapHorizontal(navigation,6);navigation.addView(right,lp(40,50));}
    }

    LinearLayout card() {LinearLayout c=column();c.setPadding(dp(18),dp(20),dp(18),dp(18));c.setBackground(shape(WHITE,24));body.addView(c,lp(-1,-2));return c;}
    private void section(LinearLayout parent,String name,String aside) {
        LinearLayout r=row();r.addView(text(name,17,INK,true));spacer(r);
        TextView source=text(aside,11,MUTED,false);source.setPadding(dp(5),dp(8),0,dp(8));source.setMinHeight(dp(32));
        tap(source,Color.TRANSPARENT,6,()->open("https://open-meteo.com/"));r.addView(source);parent.addView(r);gap(parent,12);
    }
    private void showWeather() {
        tab=0;picking=false;searchVersion++;startScreen();
        if(!notice.isEmpty()){TextView n=text(notice,14,0xFF8B6021,false);n.setPadding(dp(14),dp(12),dp(14),dp(12));n.setBackground(shape(0xFFFFF3DD,14));body.addView(n);gap(body,12);}
        Instant now=Instant.now();int today=data==null?0:data.todayIndex(now);
        for(int id:DisplayOrder.parse(preferences.getString("weather_order",""),WEATHER_NAMES.length)){
            if(!preferences.getBoolean("weather_show_"+id,true))continue;
            if(id==0)weatherLocation();
            else if(id==5)footer();
            else if(data==null){if(id==1)emptyWeather();}
            else if(id==1)currentWeather(now);
            else if(id==2)hourly(now);
            else if(id==3)daily(now);
            else if(id==4)details(today);
            gap(body,16);
        }
    }
    private void weatherLocation(){
        LinearLayout cityRow=row();
        TextView cityTitle=text(city.label(),26,INK,true);cityTitle.setMinHeight(dp(48));cityTitle.setGravity(Gravity.CENTER_VERTICAL);cityTitle.setPadding(dp(4),0,dp(4),0);
        cityRow.addView(cityTitle,new LinearLayout.LayoutParams(0,-2,1));gapHorizontal(cityRow,10);
        TextView save=button(store.isSaved(city)?"★":"☆",false,()->{boolean saved=store.toggle(city);toast(saved?"已收藏地区":"已取消收藏");showWeather();});
        save.setTextSize(25);save.setPadding(0,0,0,0);save.setContentDescription(store.isSaved(city)?"取消收藏当前地区":"收藏当前地区");cityRow.addView(save,lp(48,48));body.addView(cityRow);
        TextView detail=text(city.detail(),12,MUTED,false);detail.setPadding(0,dp(5),0,dp(14));body.addView(detail);
    }
    private void currentWeather(Instant now){
        boolean old=data.currentIsOld(now);
        LinearLayout hero=column();hero.setPadding(dp(22),dp(22),dp(18),dp(20));
        GradientDrawable gradient=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xFFE1EDFF,0xFFF1F6FF});gradient.setCornerRadius(dp(28));if(outlined())gradient.setStroke(dp(1),outlineColor());hero.setBackground(gradient);body.addView(hero,lp(-1,-2));
        LinearLayout heroTop=row();
        TextView label=text(old?"上次天气 · 历史缓存":"当前天气 · 模型估算",12,0xFF53749B,false);heroTop.addView(label);spacer(heroTop);
        TextView refresh=text(loading?"更新中…":"刷新 ↻",13,BLUE,true);refresh.setMinHeight(dp(40));refresh.setGravity(Gravity.CENTER);refresh.setPadding(dp(8),0,dp(5),0);tap(refresh,Color.TRANSPARENT,10,()->load(true));heroTop.addView(refresh);hero.addView(heroTop);
        LinearLayout currentRow=row();LinearLayout temperature=column();
        TextView number=text(WeatherData.degrees(WeatherData.value(data.current,"temperature_2m")),54,INK,false);number.setTypeface(Typeface.create("sans-serif-light",Typeface.NORMAL));temperature.addView(number);
        int code=WeatherData.code(data.current,"weather_code",-1);
        temperature.addView(text(WeatherData.condition(code)+"   体感 "+WeatherData.degrees(WeatherData.value(data.current,"apparent_temperature")),14,0xFF4F698C,false));
        currentRow.addView(temperature,new LinearLayout.LayoutParams(0,-2,1));currentRow.addView(new WeatherIcon(this,code,data.current.optInt("is_day",1)==1),lp(110,110));hero.addView(currentRow);
        gap(hero,12);
        int today=data.todayIndex(now);
        hero.addView(text("最高 "+WeatherData.degrees(WeatherData.at(data.daily,"temperature_2m_max",today))+"    最低 "+WeatherData.degrees(WeatherData.at(data.daily,"temperature_2m_min",today)),14,INK,false));gap(hero,13);
        String updated=DateTimeFormatter.ofPattern("M月d日 HH:mm").format(data.currentTime);
        hero.addView(text("数据时刻 "+updated+" · 地区当地时间",11,0xFF617D9E,false));
        gap(hero,8);
        LinearLayout attribution=row();attribution.setGravity(Gravity.RIGHT);TextView source=text("天气：Open-Meteo · CC BY 4.0 ↗",11,MUTED,false);source.setMinHeight(dp(42));source.setPadding(dp(12),dp(9),dp(12),dp(9));source.setGravity(Gravity.CENTER);tap(source,Color.TRANSPARENT,8,()->open("https://open-meteo.com/"));attribution.addView(source);hero.addView(attribution);


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
            cell.addView(new WeatherIcon(this,WeatherData.code(data.hourly,"weather_code",index),WeatherData.at(data.hourly,"is_day",index)!=0),lp(48,48));gap(cell,9);
            cell.addView(text(WeatherData.degrees(WeatherData.at(data.hourly,"temperature_2m",index)),18,INK,true));gap(cell,10);
            cell.addView(text(WeatherData.number(WeatherData.at(data.hourly,"precipitation_probability",index))+"%",11,BLUE,false));for(int j=0;j<cell.getChildCount();j++)if(cell.getChildAt(j) instanceof TextView)((TextView)cell.getChildAt(j)).setGravity(Gravity.CENTER);hours.addView(cell,lp(76,-2));
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
            LinearLayout desc=column();desc.setPadding(dp(6),0,0,0);LinearLayout values=row();values.addView(text(WeatherData.condition(code),12,INK,false),new LinearLayout.LayoutParams(0,-2,1));TextView range=text(WeatherData.degrees(WeatherData.at(data.daily,"temperature_2m_min",i))+" / "+WeatherData.degrees(WeatherData.at(data.daily,"temperature_2m_max",i)),13,INK,true);values.addView(range);desc.addView(values);gap(desc,7);desc.addView(text(WeatherData.number(WeatherData.at(data.daily,"precipitation_probability_max",i))+"% 降水概率",11,MUTED,false));r.addView(desc,new LinearLayout.LayoutParams(0,-2,1));c.addView(r);

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
        TextView about=text("数据来源与隐私 · v1.1.6",12,BLUE,false);about.setGravity(Gravity.CENTER);about.setMinHeight(dp(48));tap(about,Color.TRANSPARENT,10,this::about);body.addView(about);
    }

    private void load(boolean force) {
        if(dead)return;
        String key=city.key();WeatherData cache=store.cached(city);
        if(inFlight.contains(key)){toast("正在更新，请稍候");return;}
        long now=System.currentTimeMillis();
        if(!force&&cache!=null&&cache.fresh(now)) {data=cache;notice=cache.currentIsOld(Instant.now())?"当前显示的是较早的数据，可点击刷新更新。":"";if(weatherVisible())showWeather();return;}
        Long previous=attempts.get(key);
        if(previous!=null&&now-previous<60_000){toast("刚刚更新过，请一分钟后再试");return;}
        attempts.put(key,now);inFlight.add(key);loading=true;notice="";if(weatherVisible())showWeather();final City requested=city;final int generation=cacheGeneration;
        workers.execute(()->{
            WeatherData received=null;String error=null;
            try {received=new WeatherData(api.forecast(requested),System.currentTimeMillis());}
            catch(Exception e){error=e instanceof java.io.IOException?e.getMessage():"天气数据暂不可用，请稍后重试";if(error==null||!error.matches(".*[\\u4e00-\\u9fff].*"))error="网络连接失败，请检查网络后重试";}
            final WeatherData result=received;final String failure=error;
            main.post(()->{if(dead)return;inFlight.remove(key);if(generation!=cacheGeneration){loading=inFlight.contains(city.key());if(weatherVisible())showWeather();return;}if(result!=null)store.save(requested,result);if(!city.key().equals(key))return;loading=false;
                if(result!=null){data=result;notice="";if(force)toast("刷新成功！");}else{data=store.cached(requested);notice=data==null?failure:failure+"；当前显示上次保存的数据。";}
                if(weatherVisible())showWeather();
            });
        });
    }
    private void choose(City selected) {
        hideKeyboard();city=selected;store.select(city);data=store.cached(city);notice="";loading=inFlight.contains(city.key());switchTab(tab);if(!loading)load(false);
    }
    private void showPicker() {
        picking=true;searchVersion++;startScreen();
        LinearLayout heading=row();heading.addView(text("选择地区",25,INK,true));spacer(heading);heading.addView(button("完成",false,()->{hideKeyboard();switchTab(tab);}));body.addView(heading);gap(body,10);
        body.addView(text("搜索城市、区县或海外地区",13,MUTED,false));gap(body,18);
        LinearLayout input=row();
        search=new EditText(this);search.setSingleLine(true);search.setTextSize(16);search.setTextColor(INK);search.setHintTextColor(MUTED);search.setHint("例如：杭州、海淀、Tokyo");search.setBackground(shape(WHITE,18));search.setPadding(dp(13),dp(15),dp(10),dp(15));search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);search.setInputType(android.text.InputType.TYPE_CLASS_TEXT);input.addView(search,new LinearLayout.LayoutParams(0,-2,1));
        gapHorizontal(input,10);input.addView(button("搜索",true,this::searchOnline));body.addView(input);gap(body,20);
        results=column();body.addView(results);searchStatus=text("",13,MUTED,false);
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){searchVersion++;renderLocal(s.toString());}public void afterTextChanged(Editable e){}});
        search.setOnEditorActionListener((v,action,event)->{if(action==EditorInfo.IME_ACTION_SEARCH){searchOnline();return true;}return false;});
        renderLocal("");
        gap(body,20);TextView credit=text("地区数据：GeoNames，经 Open-Meteo 提供 ↗\r\nCC BY 4.0 · 可搜索范围以数据源收录为准",11,MUTED,false);credit.setLineSpacing(dp(5),1);credit.setMinHeight(dp(48));tap(credit,Color.TRANSPARENT,8,()->open("https://www.geonames.org/"));credit.setPadding(dp(12),dp(8),dp(12),dp(8));body.addView(credit,new LinearLayout.LayoutParams(-2,-2));
    }
    private void renderLocal(String query) {
        results.removeAllViews();String q=query.trim();
        if(q.isEmpty()) {
            List<City> saved=store.saved();if(!saved.isEmpty()){results.addView(text("我的地区",16,INK,true));gap(results,10);cityList(saved);gap(results,22);}
            results.addView(text("常用地区",16,INK,true));gap(results,12);
            for(int i=0;i<catalog.size();i+=3){LinearLayout r=row();r.setPadding(0,dp(4),0,dp(4));for(int col=0;col<3;col++){if(col>0)gapHorizontal(r,8);int j=i+col;if(j>=catalog.size()){r.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));continue;}City c=catalog.get(j);TextView chip=button(c.label(),false,()->choose(c));chip.setTextSize(13);chip.setPadding(dp(4),dp(10),dp(4),dp(10));r.addView(chip,new LinearLayout.LayoutParams(0,-2,1));}results.addView(r);}
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
    private boolean weatherVisible(){return tab==0&&!picking;}
    void switchTab(int next){hideKeyboard();if(next!=2)gps.stopForNavigation();if(earthquakes!=null)earthquakes.pause();tab=next;List<Integer> items=visibleTabs();if(items.size()>=5){int pos=items.indexOf(tab);if(pos<navigationOffset)navigationOffset=pos;else if(pos>=navigationOffset+3)navigationOffset=pos-2;}picking=false;preferences.edit().putInt("last_tab",tab).apply();if(next==0)showWeather();else{startScreen();if(next==1)schedule.render();else if(next==2)gps.render();else if(next==3){maps=new MapScreen(this);maps.render(!deferMapReload);deferMapReload=false;}else if(next==4)new AstronomyScreen(this,city,data,astronomyTime,this::showAstronomy).render();else if(next==5)earthquakes.render();}}
    void official(String title,String url){Intent i=new Intent(this,OfficialPageActivity.class);i.putExtra("title",title);i.putExtra("url",url);startActivity(i);}
    private void showAstronomy(Instant at){pendingScrollY=pageScroll==null?0:pageScroll.getScrollY();astronomyTime=at;switchTab(4);}
    void settings(){new SettingsScreen(this).show();}
    void scheduleSettings(){schedule.menu();}
    void restoreDefaults(){gps.modeChanged();preferences.edit().clear().putInt("settings_schema",4).putInt("seen_version",21).apply();schedule.resetSettings();navigationOffset=0;tab=0;resetBackground();}
    void locationIntervalChanged(){gps.intervalChanged();}
    void locationContinuousChanged(){if(!LocationService.continuous(this)&&tab!=2)LocationService.stop(this);else gps.continuousChanged();}
    void earthquakeSettings(){earthquakes.settings();}
    void locationModeChanged(){gps.modeChanged();if(tab==2)switchTab(2);}
    void selectBackground(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("image/*");try{startActivityForResult(i,42);}catch(ActivityNotFoundException e){toast("未找到系统图片选择器");}}
    void resetBackground(){new java.io.File(getFilesDir(),"background.jpg").delete();background=null;refreshScreen();}
    void reloadBackground(){background=android.graphics.BitmapFactory.decodeFile(new java.io.File(getFilesDir(),"background.jpg").getPath());refreshScreen();}
    private void updateEntry(LinearLayout c,String title,String details){
        LinearLayout entry=column();TextView detail=text(details,14,INK,false);detail.setPadding(dp(12),dp(12),dp(12),dp(8));detail.setVisibility(View.GONE);
        TextView toggle=button(title+"  ＋",false,()->{});toggle.setGravity(Gravity.CENTER_VERTICAL);toggle.setOnClickListener(v->{boolean open=detail.getVisibility()!=View.VISIBLE;detail.setVisibility(open?View.VISIBLE:View.GONE);toggle.setText(title+(open?"  −":"  ＋"));toggle.setContentDescription(title+(open?"，已展开":"，已收起"));});toggle.setContentDescription(title+"，已收起");entry.addView(toggle,lp(-1,-2));entry.addView(detail);c.addView(entry);gap(c,10);
    }
    void updates(){
        LinearLayout c=column();c.setPadding(dp(20),dp(8),dp(20),dp(16));
        updateEntry(c,"1.1.6 · 平板状态恢复与星空扩充","• 修复平板横竖屏和窗口尺寸变化时地震已获取变为本机缓存、栏目或天文时间丢失的问题；平板支持横竖屏，页面重建也恢复获取状态。\n• 新增北半球常见亮星及较亮星团、星云和星系，深空天体显示近似总视星等。北斗七星支持点选与显示开关。\n• 亮星显示设置支持手动选择或按星等上限筛选，步进 0.1 等；原手动选择保留。\n• 参考经度明确负数为西经 W、正数为东经 E；参考纬度明确负数为南纬 S、正数为北纬 N。");
        updateEntry(c,"1.1.5 · 固定竖屏与地震来源优化","• 应用各页面固定竖屏，避免转动手机触发页面重建、栏目变化或已获取状态变化。\n• 同次地震优先采用中国地震台网的已复核结果，其次其他机构的已复核结果，最后自动结果或状态未知；同级采用较新结果，不再额外优先德国来源。\n• 地震列表显示智利、泰国等具体贡献机构的中文名，合并后去重；详情继续保留聚合平台与转发信息。");
        updateEntry(c,"1.1.4 · 地震信息简化与坐标统一","• 地震状态统一为已复核、自动结果、状态未知，详情保留来源处理标记。\n• 发生改为发生时刻，列表简化烈度及中国地震台网来源，模型和转发说明保留在详情；未达阈值时明确提示。\n• 仅保留 MMI，删除烈度标度设置及当前说明中的其他标度；不再显示来源未说明深度是否固定的空泛提示。\n• 贡献机构改用中文名，经纬度统一经度在前、纬度在后，使用 E/W/N/S 标明方向。更新状态改称最早一条。");
        updateEntry(c,"1.1.3 · 地震界面与扩展烈度参考","• 筛选与刷新仅保留刷新目录、刷新定位按钮，查询范围等统一从设置进入；时间选项统一称“最近 1 天”。移除地震设置内重复的数据来源与隐私入口。\n• 关于说明按统一的一行间隔分段。MMI 默认项置顶，本次升级统一切至 MMI 一次，之后保留手动选择。\n• 超出原论文模型保守条件时，对有有效震级和深度的记录使用 GlobalQuake 旧开源版参考算法，明确注明来源震级近似、默认／固定深度及弱震动显示阈值；不冒充正式最大烈度。保留 MIT 许可。\n• 自动解改为“自动解算”；术语中分别解释来源标记已复核、已复核和人工处理，删除主震、前震与余震段落。");
        updateEntry(c,"1.1.2 · 地震筛选、定位与烈度","• 最低震级改为 0～7、步进 0.1 的滑块，默认 M≥1.0；附近范围在查询区域内输入 50～5000 km。参考位置改为单选，手动经纬度条件显示，保存后自动更新距离。\n• 地震页可按定位设置刷新一次位置，显示经纬度；日期补全年份，排序说明更清楚，时区和数据说明合并进关于 → 数据来源与隐私，按段展示；新增地震术语介绍。\n• 新增美国地质调查局全球补充目录，明确匹配的同次地震合并；各机构使用中文名。Wolfx 默认关闭，须确认说明才启用；补充配合官方停用及下架的承诺。\n• 烈度区分公众震感报告、来源模型与本地 MMI 经验估算，不将模型当实测；中国标度暂不由 MMI 换算，超出估算范围时不强算。");
        updateEntry(c,"1.1.1 · Wolfx 国内目录","• 接入 CENC（Wolfx 转发）近期最多 50 条目录，独立缓存、刷新与数据源开关；与 GEOFON、EMSC 明确匹配才合并，保留各来源参数。\n• 如实说明：上游公开展示适用依据尚未完全确认，不宣称已获 CENC 再发布授权；CORS 和非商业用途不等于授权。可关闭此来源，仅供参考。\n• 按北京时间解析发生及报告时刻；不承诺覆盖所选天数或完整收录；未采用未核实的 intensity 字段，未接入任何预警流。");
        updateEntry(c,"1.1.0 · 近期地震与持续定位","• 新增地震栏目：GEOFON 与 EMSC 双源近期目录、相近事件合并、震级类型、发生时间、深度、震中、地表距离及各来源详情。\n• 可设置时间范围、区域、最低震级、附近范围、排序、时区、烈度标度、列表字段、栏目顺序和前台刷新间隔；保留离线缓存，区分各来源更新与失败。\n• 来源使用 CC BY 4.0 并保留署名；Wolfx 国内目录的上游依据尚待确认，未启用，也未接入预警流。缺乏可靠烈度资料时不生成数值。\n• 定位新增“切换栏目和后台继续定位”，默认关闭；主动开启并开始后使用 Android 前台服务，提供持续通知和停止按钮。最近定位供地震距离本地计算，不上传。\n• 缓存管理新增地震目录，清理期间的旧请求不会回写。新增栏目保留原有底栏排序及其他设置。");
        updateEntry(c,"1.0.12 · 设置弹窗与地图按钮","• 设置弹窗直接在中央显示，取消系统滑入/退出动画。按钮间距与描边改为首次布局前完成，避免显示时再次调整尺寸。日期选择也使用相同处理，保留逐级返回和输入校验。\n• 天气地图的“浏览器”改为“在浏览器中打开”，与图种按钮保持相同宽高、圆角和间距，小屏可完整换行显示。\n• 扩展地震来源及调用许可调研，不在本版本接入地震功能。");
        updateEntry(c,"1.0.11 · 间距与栏目管理","• 统一底栏、排序、时间控制、课表、裁剪和弹窗按钮间距，地区名称不再重复打开选择器，搜索按钮移到输入框外。\n• 常用地区新增莫斯科、巴黎，保持每行三列等宽。调整背景裁剪标题与说明，课表入口改为“学期设置”。\n• 版本记录按版本折叠，每次进入默认收起；新增缓存管理，保留课程、收藏、背景、设置与限流记录。\n• 天文新增“时间控制”卡片及栏目显示与排序设置，切换时间保留星图选中与滚动位置。\n• 课表跳周改为输入周次；缩小工具箱图标，四周留白。地震功能仅调研，未加入本版本。");
        updateEntry(c,"1.0.10 · 星空与月球信息","• 当地星空改为随屏幕宽度扩展的正方形，放大坐标。标签只在星点正上方或正下方，冲突时隐藏标签但星点仍可选中。\n• “星点信息”改为“选中的星星信息”；太阳和月球可选中，太阳不显示星等，月球显示本地估算视星等。\n• 新增独立“月球信息”，移入月相、照亮比例、近似月龄及距下次满月时间，并绘制随所选时间更新的月相示意。\n• 天气地图在卫星云图后新增中央气象台“天气图”原页入口。\n• 课程信息前缀各项的默认选项移到最前，保留原选择。");
        updateEntry(c,"1.0.9 · 天球坐标与星图选中","• 精简天气图例说明，WMO 专业表统一称“电码”，修正未知天气提示。\n• 调整默认文案；课表三个信息前缀默认显示完整名称，定位默认间隔改为 5 秒。\n• 天文设置新增行星与深空天体显示开关，以及地平坐标、北天极居中的赤道坐标、不显示坐标网格三种模式。\n• 选中天体显示缓慢顺时针旋转的断开十字，调整时间保留选中；天体落到地平线下时隐藏十字。\n• 恒星名改为织女一、河鼓二、勾陈一，保留原来的显隐偏好；新增昴星团、仙女座星系大致中心，采用 CC0 坐标离线换算。\n• 定位改为“水平定位误差半径”，移除正负号，说明未提供海拔的情况。");
        updateEntry(c,"1.0.8 · 默认设置与来源补充","• 57、67 改为“中或大冻毛毛雨”“中或大冻雨”。\n• 补齐中英文间距；设置中标注默认值。描边默认关闭，颜色入口简化为“设置描边颜色”，网络定位去掉括号说明。\n• 数据来源与隐私新增天气战士 · 全球版、拾光课程表参考项目，最底部新增本项目 GitHub 链接。\n• 新课表与恢复默认采用 17 周、12 节上下课时间；升级保留已有课表与自定义时间。");
        updateEntry(c,"1.0.7 · 雷暴命名与 WMO 原文","• 95、96、97、99分别改为“小或中雷暴”“小或中雷暴伴冰雹”“大雷暴”“大雷暴伴冰雹”，按 WMO 原文区分雷暴强度。\n• 99的日夜图标各增加两个冰雹圆点，共四个。\n• 图例说明中的“数字”改为“数”，补充数值代码与本地翻译、模型计算及 WMO 命名依据。\n• 图例底部新增 WMO 官方英文电码原文链接。其他代码的差异已整理供后续版本参考。");
        updateEntry(c,"1.0.6 · 天气图例与代码说明","• 天气图例说明最右侧数字的含义，补充代码不连续的原因及官方天气代码文档链接。\n• 按当前官方文档说明96、99代码取决于模型的冰雹预报能力，补齐97强雷暴的名称与图标。\n• 大冻雨云朵下方改为三个深蓝色斜线雨点。\n• 说明“天气未知”的触发条件；缺失、无效或未识别代码统一显示问号，避免误画为晴天或普通云朵。");
        updateEntry(c,"1.0.5 · 星空与天气图例完善","• 北斗七星移出亮星显示设置，在地平线上方时固定显示，旧的隐藏开关不再影响它们。\n• 当地星空加入参考极大日及前后3天的流星雨辐射点，用橙色十字圆环标注；与下方流星雨列表使用同一日期判断。\n• 删除数据来源与隐私中的中国天气网入口，统一链接间距；地区数据署名框增加四边留白。\n• 少云图标缩小云朵；沉积雾凇的雾加入深蓝雪花；云内雪花提高对比度；中、大阵雨与大阵雪按对应雨雪等级修正数量；95改称“小或中雷雨”。");
        updateEntry(c,"1.0.4 · 行星算法校准","• 按参考项目 weather-fighter-global 的 JPL 简化开普勒元素重算地心行星位置，修正行星出现在错误方位的问题。\r\n• 行星坐标使用行星日心坐标减地球日心坐标，并统一黄道到赤道坐标转换；水星到海王星的星等公式同步校准。\r\n• 版本号升至 1.0.4，保留下面各版本的独立更新说明。");
        updateEntry(c,"1.0.3 · 天文与布局修复","• 修复天文时间按钮重建页面时先回到顶部造成的闪屏，切换后保持原滚动位置。\r\n• 星图加入本地计算的水星、金星、火星、木星、土星、天王星和海王星，并显示行星星等。\r\n• 点击亮星或行星显示方位角、高度角和星等；新增亮星显示设置，北斗七星仍只作图示。\r\n• 流星雨按极大日前后3天标记活跃期并显示辐射点；来源与隐私链接、IMO 链接框和常用地区卡片统一留白。\r\n• 厦门、台北调整到香港之前。");
        updateEntry(c,"1.0.2 · 细节与定位改进","• 天文第一行按钮顺序固定为“-15分钟、当前、+15分钟”；太阳与月球标签按字号计算下移间距，避免遮挡圆点；统一使用“月球”。\r\n• 天气地图、天气代码、背景裁剪、描边颜色、设置逐级返回和定位间隔按本版本记录调整。\r\n• versionCode 升至 4，沿用包名与签名。");
        updateEntry(c,"1.0.1 · 自由布局与个性设置","• 分级设置、背景选区与缩放、可选颜色描边。\r\n• 天气栏目和底部栏目可显隐排序，新增独立天气地图与天文栏目（默认隐藏）。\r\n• 定位可选 GPS 硬件或网络定位。\r\n• 课程支持自定义授课周与信息前缀，时间改为数字输入。\r\n• 改进天气图标、摄氏度、居中排版、图例与刷新提示。\r\n• 天文支持指定年月日时分、前后15分钟和前后一天，保留滚动位置并区分曙光暮光。\r\n• 更新隐私说明，移除极光功能。");
        updateEntry(c,"1.0.0 · 呆呆工具箱","• 正式名称改为呆呆工具箱，新增天气 / 课程表 / GPS 底部导航。\r\n• 新增手动周课表、自定义节次、地点、老师与单双周。\r\n• 新增手机图片背景及遮罩调整。\r\n• 新增前台 GPS 经纬度、水平精度与状态。\r\n• 新增官方全国降水、温度和分区雷达网页。\r\n• 新增观星条件、星图、日月位置、月相和曙暮光。\r\n• 整理来源与隐私，Open-Meteo 入口放在一起。\r\n• 页脚改为“呆呆鸟使用 Codex 制作”。");
        updateEntry(c,"0.1.0-test · 简天气测试版","早期版本（原标注 1.0.0）统一归为测试版：地区搜索与收藏、当前天气、24 小时及 7 日预报、离线缓存。");
        ScrollView scroll=new ScrollView(this);scroll.addView(c);new ToolDialog(this).setTitle("每个版本的更新内容").setView(scroll).setPositiveButton("知道了",null).show();
    }
    void clearCache(Runnable done){
        cacheGeneration++;searchVersion++;data=null;notice="";
        deferMapReload=tab==3;
        if(maps!=null){maps.destroy();maps=null;}
        boolean filesCleared=store.clearCache() & earthquakes.clear();
        android.webkit.WebView web=new android.webkit.WebView(this);web.clearCache(true);web.clearHistory();web.destroy();
        android.webkit.WebStorage.getInstance().deleteAllData();
        android.webkit.CookieManager.getInstance().removeAllCookies(removed->{android.webkit.CookieManager.getInstance().flush();if(!dead){refreshScreen();done.run();toast(filesCleared?"缓存已清除":"部分缓存未能删除，请重试");}});
    }
    void about() {
        LinearLayout content=column();content.setPadding(dp(22),dp(8),dp(22),dp(10));
        EarthquakeInfo.paragraph(this,content,"呆呆工具箱 1.1.6 · 个人非商业版\r\n\r\n天气由 Open-Meteo 提供；地区检索基于 GeoNames。两者均按 CC BY 4.0 署名。应用对返回数据进行了中文描述、取整、单位标注与排版。\r\n\r\n当前天气为数值模型估算，非气象台站现场观测，也不替代官方预警。日期、小时与日出日落均按所选地区的当地时区显示。\r\n\r\n隐私：无需账号。定位默认切换栏目或后台即停止；可在设置中主动开启持续定位，开始后由前台服务工作并提供通知和停止入口。应用不上传定位坐标；最近一次定位保存在本机，地震距离也在本机计算。GPS 硬件模式无需应用联网；网络定位依赖系统位置服务与网络，系统可能向其服务商发送 IP、基站或 Wi-Fi 信息；课程表与导入的背景图只保存在手机内部。天气查询需要联网。你的 IP、搜索词及所选地区坐标会发送给 Open-Meteo。收藏与天气缓存保存在本机；没有广告、统计或自建服务器。\r\n\r\nOpen-Meteo 免费 API 仅限非商业使用。天气缓存 15 分钟，手动刷新间隔至少 1 分钟；服务限流时暂停请求。商业用途应另行取得服务授权。\r\n\r\n全国降水、气温、雷达、卫星云图和天气图通过应用内官方网页查看，版权归中央气象台等原始权利人。天文计算为本地近似结果。官方网页会按其自身规则处理访问数据。");
        EarthquakeInfo.append(this,content);
        link(content,"Open-Meteo · 天气数据来源","https://open-meteo.com/");link(content,"Open-Meteo · 使用条款与隐私","https://open-meteo.com/en/terms");link(content,"GeoNames · 地区数据来源","https://www.geonames.org/");link(content,"CC BY 4.0 · 数据许可","https://creativecommons.org/licenses/by/4.0/");link(content,"中央气象台 · 官方页面","https://www.nmc.cn/");link(content,"天文计算方法参考","https://stjarnhimlen.se/comp/ppcomp.html");link(content,"天体坐标与星等 · Wikidata CC0","https://www.wikidata.org/wiki/Wikidata:Licensing");
        EarthquakeInfo.links(this,content);
        gap(content,10);content.addView(text("参考开源项目",17,INK,true));gap(content,10);content.addView(text("天气与课程表功能参考以下项目；本应用的原生界面与逻辑独立实现。",14,MUTED,false));gap(content,12);link(content,"天气战士 · 全球版 · GitHub","https://github.com/anyin233/weather-fighter-global");link(content,"拾光课程表 · GitHub","https://github.com/ShiGuangSchedule/shiguangschedule");link(content,"呆呆工具箱 · GitHub","https://github.com/AdybirdZ/daidai-toolbox");
        ScrollView scroll=new ScrollView(this);scroll.addView(content);new ToolDialog(this).setTitle("数据来源与隐私").setView(scroll).setPositiveButton("知道了",null).show();
    }
    void link(LinearLayout parent,String label,String url) {TextView t=text(label+" ↗",13,BLUE,false);t.setMinHeight(dp(44));t.setPadding(dp(12),dp(7),dp(12),dp(7));t.setGravity(Gravity.CENTER_VERTICAL);tap(t,Color.TRANSPARENT,8,()->open(url));parent.addView(t,new LinearLayout.LayoutParams(-2,-2));gap(parent,8);}
    void open(String url) {try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(ActivityNotFoundException e){toast("未找到可打开链接的浏览器");}}
    private void hideKeyboard() {View focused=getCurrentFocus();if(focused!=null){((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(focused.getWindowToken(),0);focused.clearFocus();}}
    void toast(String message) {Toast toast=new Toast(this);LinearLayout box=row();box.setPadding(dp(16),dp(12),dp(16),dp(12));box.setBackground(shape(0xFF26364D,20));ImageView icon=new ImageView(this);icon.setImageResource(org.simpleweather.app.R.drawable.ic_launcher);box.addView(icon,lp(28,28));gapHorizontal(box,10);TextView label=text(message,16,WHITE,false);label.setMaxWidth(Math.max(dp(120),Math.min(dp(260),getResources().getDisplayMetrics().widthPixels-dp(100))));box.addView(label);toast.setView(box);toast.setDuration(Toast.LENGTH_SHORT);toast.setGravity(Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL,0,dp(100));toast.show();}
    @Override public void onConfigurationChanged(android.content.res.Configuration config){
        super.onConfigurationChanged(config);
        // Keep the existing screen, session and open dialogs; only remeasure the viewport.
        if(pageScroll!=null){android.view.ViewGroup.LayoutParams fit=pageScroll.getLayoutParams();fit.width=Math.min(getResources().getDisplayMetrics().widthPixels,dp(620));pageScroll.setLayoutParams(fit);}
        if(body!=null)body.requestLayout();
    }
    @Override protected void onSaveInstanceState(Bundle state){
        state.putInt("toolbox_tab",tab);state.putInt("toolbox_navigation",navigationOffset);
        state.putInt("toolbox_scroll",pageScroll==null?0:pageScroll.getScrollY());state.putLong("toolbox_astro_time",astronomyTime.toEpochMilli());
        if(earthquakes!=null)earthquakes.saveState(state);super.onSaveInstanceState(state);
    }
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);if(intent.getBooleanExtra("open_location",false)){preferences.edit().putBoolean("tab_show_2",true).apply();switchTab(2);}}
    @Override public void onBackPressed() {if(picking){hideKeyboard();switchTab(tab);}else if(tab!=visibleTabs().get(0))switchTab(visibleTabs().get(0));else super.onBackPressed();}
    @Override protected void onPause(){if(earthquakes!=null)earthquakes.pause();if(gps!=null)gps.pause();if(maps!=null)maps.pause();super.onPause();}
    @Override protected void onResume(){super.onResume();if(gps!=null&&tab==2)gps.resume();if(maps!=null)maps.resume();if(earthquakes!=null&&tab==5)earthquakes.resume();}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){super.onRequestPermissionsResult(request,permissions,grants);if(request==41)gps.permissionResult();else if(request==44)gps.notificationPermissionResult();else if(request==45)earthquakes.locationPermissionResult();}
    @Override protected void onActivityResult(int request,int result,Intent intent){super.onActivityResult(request,result,intent);if(request==42&&result==RESULT_OK&&intent!=null&&intent.getData()!=null){Intent crop=new Intent(this,BackgroundCropActivity.class);crop.setData(intent.getData());crop.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivityForResult(crop,43);}else if(request==43&&result==RESULT_OK)reloadBackground();}
    @Override protected void onDestroy(){dead=true;if(earthquakes!=null)earthquakes.destroy();if(maps!=null)maps.destroy();if(gps!=null)gps.pause();main.removeCallbacksAndMessages(null);workers.shutdownNow();super.onDestroy();}
}
