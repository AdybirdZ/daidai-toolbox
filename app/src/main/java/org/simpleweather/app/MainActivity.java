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
    private android.graphics.Bitmap background;
    android.content.SharedPreferences preferences;
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
        preferences=getSharedPreferences("toolbox",0);
        if(preferences.getInt("settings_schema",0)<4){SharedPreferences.Editor e=preferences.edit();if(!preferences.contains("background_brightness"))e.putInt("background_brightness",100-Math.max(0,Math.min(100,preferences.getInt("shade",80))));if(preferences.getInt("radar_region",0)>7)e.putInt("radar_region",0);if(preferences.getInt("map_product",0)==4)e.putInt("map_product",0);e.remove("shade").putInt("settings_schema",4).apply();}
        schedule=new ScheduleScreen(this);gps=new GpsScreen(this);
        background=android.graphics.BitmapFactory.decodeFile(new java.io.File(getFilesDir(),"background.jpg").getPath());
        tab=preferences.getInt("last_tab",0);if(!visibleTabs().contains(tab))tab=visibleTabs().get(0);switchTab(tab);load(false);
        if(preferences.getInt("seen_version",0)<9)main.postDelayed(()->{if(!dead){updates();preferences.edit().putInt("seen_version",9).apply();}},700);
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
    static final String[] TAB_NAMES={"天气","课程表","定位","天气地图","天文"};
    static final String[] WEATHER_NAMES={"地区","当前天气","未来24小时","未来7天","天气详情","数据来源入口"};
    boolean outlined(){return preferences!=null&&preferences.getBoolean("outline",true);}
    int outlineColor(){return preferences.getInt("outline_color",Color.BLACK);}
    List<Integer> visibleTabs(){List<Integer> all=DisplayOrder.parse(preferences.getString("tab_order",""),5);List<Integer> result=new ArrayList<>();for(int id:all)if(preferences.getBoolean("tab_show_"+id,id<3))result.add(id);if(result.isEmpty())result.add(0);return result;}
    void refreshScreen(){List<Integer> visible=visibleTabs();if(!visible.contains(tab))tab=visible.get(0);switchTab(tab);}
    private void pageHeader(){
        TextView title=text("呆呆工具箱 · "+TAB_NAMES[tab],25,INK,true);body.addView(title);gap(body,7);body.addView(text("呆呆鸟使用Codex制作",12,MUTED,false));gap(body,12);
        LinearLayout actions=row();if(tab==0||tab==4){actions.addView(button("地区",false,this::showPicker));gapHorizontal(actions,8);}actions.addView(button("设置",false,this::settings));if(tab==1){gapHorizontal(actions,8);actions.addView(button("新增课程",true,()->schedule.addCourse()));}body.addView(actions);gap(body,16);
    }
    void gapHorizontal(LinearLayout parent,int width){parent.addView(new View(this),lp(width,1));}
    private void renderNavigation(){
        navigation.removeAllViews();List<Integer> visible=visibleTabs();navigation.setVisibility(visible.size()<2?View.GONE:View.VISIBLE);if(visible.size()<2)return;
        navigationOffset=Math.max(0,Math.min(navigationOffset,Math.max(0,visible.size()-3)));
        if(visible.size()>=5){TextView left=button("‹",false,()->{navigationOffset=Math.max(0,navigationOffset-1);renderNavigation();});left.setContentDescription("底栏向左翻页");left.setEnabled(navigationOffset>0);left.setAlpha(navigationOffset>0?1:.35f);left.setPadding(0,0,0,0);navigation.addView(left,lp(40,50));}
        for(int id:DisplayOrder.window(visible,navigationOffset)){TextView item=button(TAB_NAMES[id],false,()->switchTab(id));item.setContentDescription("切换到"+TAB_NAMES[id]);item.setTextSize(visible.size()==4?12:14);item.setPadding(dp(3),dp(8),dp(3),dp(8));item.setGravity(Gravity.CENTER);if(tab!=id){item.setTextColor(MUTED);tap(item,Color.TRANSPARENT,16,()->switchTab(id));}navigation.addView(item,new LinearLayout.LayoutParams(0,dp(50),1));}
        if(visible.size()>=5){TextView right=button("›",false,()->{navigationOffset=Math.min(visible.size()-3,navigationOffset+1);renderNavigation();});right.setContentDescription("底栏向右翻页");right.setEnabled(navigationOffset<visible.size()-3);right.setAlpha(navigationOffset<visible.size()-3?1:.35f);right.setPadding(0,0,0,0);navigation.addView(right,lp(40,50));}
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
        TextView cityTitle=text(city.label()+"  ⌄",26,INK,true);cityTitle.setMinHeight(dp(48));cityTitle.setGravity(Gravity.CENTER_VERTICAL);tap(cityTitle,Color.TRANSPARENT,10,this::showPicker);
        cityRow.addView(cityTitle,new LinearLayout.LayoutParams(0,-2,1));
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
        TextView about=text("数据来源与隐私 · v1.0.7",12,BLUE,false);about.setGravity(Gravity.CENTER);about.setMinHeight(dp(48));tap(about,Color.TRANSPARENT,10,this::about);body.addView(about);
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
        LinearLayout input=row();input.setBackground(shape(WHITE,18));input.setPadding(dp(13),0,dp(4),0);
        search=new EditText(this);search.setSingleLine(true);search.setTextSize(16);search.setTextColor(INK);search.setHintTextColor(MUTED);search.setHint("例如：杭州、海淀、Tokyo");search.setBackgroundColor(Color.TRANSPARENT);search.setPadding(0,dp(15),dp(6),dp(15));search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);search.setInputType(android.text.InputType.TYPE_CLASS_TEXT);input.addView(search,new LinearLayout.LayoutParams(0,-2,1));
        input.addView(button("搜索",true,this::searchOnline));body.addView(input);gap(body,20);
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
            for(int i=0;i<catalog.size();i+=3){LinearLayout r=row();r.setPadding(0,dp(4),0,dp(4));for(int j=i;j<Math.min(i+3,catalog.size());j++){City c=catalog.get(j);TextView chip=button(c.label(),false,()->choose(c));chip.setTextSize(13);chip.setPadding(dp(4),dp(10),dp(4),dp(10));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.setMargins(0,0,dp(7),0);r.addView(chip,p);}results.addView(r);}
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
    void switchTab(int next){hideKeyboard();if(next!=2)gps.stopForNavigation();tab=next;List<Integer> items=visibleTabs();if(items.size()>=5){int pos=items.indexOf(tab);if(pos<navigationOffset)navigationOffset=pos;else if(pos>=navigationOffset+3)navigationOffset=pos-2;}picking=false;preferences.edit().putInt("last_tab",tab).apply();if(next==0)showWeather();else{startScreen();if(next==1)schedule.render();else if(next==2)gps.render();else if(next==3){maps=new MapScreen(this);maps.render();}else new AstronomyScreen(this,city,data,astronomyTime,this::showAstronomy).render();}}
    void official(String title,String url){Intent i=new Intent(this,OfficialPageActivity.class);i.putExtra("title",title);i.putExtra("url",url);startActivity(i);}
    private void showAstronomy(Instant at){pendingScrollY=pageScroll==null?0:pageScroll.getScrollY();astronomyTime=at;switchTab(4);}
    void settings(){new SettingsScreen(this).show();}
    void scheduleSettings(){schedule.menu();}
    void restoreDefaults(){gps.modeChanged();preferences.edit().clear().putInt("settings_schema",4).putInt("seen_version",9).apply();schedule.resetSettings();navigationOffset=0;tab=0;resetBackground();}
    void locationIntervalChanged(){gps.intervalChanged();}
    void locationModeChanged(){gps.modeChanged();if(tab==2)switchTab(2);}
    void selectBackground(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("image/*");try{startActivityForResult(i,42);}catch(ActivityNotFoundException e){toast("未找到系统图片选择器");}}
    void resetBackground(){new java.io.File(getFilesDir(),"background.jpg").delete();background=null;refreshScreen();}
    void reloadBackground(){background=android.graphics.BitmapFactory.decodeFile(new java.io.File(getFilesDir(),"background.jpg").getPath());refreshScreen();}
    void updates(){
        LinearLayout c=column();c.setPadding(dp(20),dp(8),dp(20),dp(16));
        c.addView(text("1.0.7 · 雷暴命名与WMO原文",19,INK,true));gap(c,12);
        c.addView(text("• 95、96、97、99分别改为“小或中雷暴”“小或中雷暴伴冰雹”“大雷暴”“大雷暴伴冰雹”，按WMO原文区分雷暴强度。\n• 99的日夜图标各增加两个冰雹圆点，共四个。\n• 图例说明中的“数字”改为“数”，补充数值代码与本地翻译、模型计算及WMO命名依据。\n• 图例底部新增WMO官方英文电码原文链接。其他代码的差异已整理供后续版本参考。",14,INK,false));gap(c,20);
        c.addView(text("1.0.6 · 天气图例与代码说明",19,INK,true));gap(c,12);
        c.addView(text("• 天气图例说明最右侧数字的含义，补充代码不连续的原因及官方天气代码文档链接。\n• 按当前官方文档说明96、99代码取决于模型的冰雹预报能力，补齐97强雷暴的名称与图标。\n• 大冻雨云朵下方改为三个深蓝色斜线雨点。\n• 说明“天气未知”的触发条件；缺失、无效或未识别代码统一显示问号，避免误画为晴天或普通云朵。",14,INK,false));gap(c,20);
        c.addView(text("1.0.5 · 星空与天气图例完善",19,INK,true));gap(c,12);
        c.addView(text("• 北斗七星移出亮星显示设置，在地平线上方时固定显示，旧的隐藏开关不再影响它们。\n• 当地星空加入参考极大日及前后3天的流星雨辐射点，用橙色十字圆环标注；与下方流星雨列表使用同一日期判断。\n• 删除数据来源与隐私中的中国天气网入口，统一链接间距；地区数据署名框增加四边留白。\n• 少云图标缩小云朵；沉积雾凇的雾加入深蓝雪花；云内雪花提高对比度；中、大阵雨与大阵雪按对应雨雪等级修正数量；95改称“小或中雷雨”。",14,INK,false));gap(c,20);
        c.addView(text("1.0.4 · 行星算法校准",19,INK,true));gap(c,12);
        c.addView(text("• 按参考项目 weather-fighter-global 的 JPL 简化开普勒元素重算地心行星位置，修正行星出现在错误方位的问题。\r\n• 行星坐标使用行星日心坐标减地球日心坐标，并统一黄道到赤道坐标转换；水星到海王星的星等公式同步校准。\r\n• 版本号升至 1.0.4，保留下面各版本的独立更新说明。",14,INK,false));gap(c,20);
        c.addView(text("1.0.3 · 天文与布局修复",19,INK,true));gap(c,12);
        c.addView(text("• 修复天文时间按钮重建页面时先回到顶部造成的闪屏，切换后保持原滚动位置。\r\n• 星图加入本地计算的水星、金星、火星、木星、土星、天王星和海王星，并显示行星星等。\r\n• 点击亮星或行星显示方位角、高度角和星等；新增亮星显示设置，北斗七星仍只作图示。\r\n• 流星雨按极大日前后3天标记活跃期并显示辐射点；来源与隐私链接、IMO链接框和常用地区卡片统一留白。\r\n• 厦门、台北调整到香港之前。",14,INK,false));gap(c,20);
        c.addView(text("1.0.2 · 细节与定位改进",19,INK,true));gap(c,12);
        c.addView(text("• 天文第一行按钮顺序固定为“-15分钟、当前、+15分钟”；太阳与月球标签按字号计算下移间距，避免遮挡圆点；统一使用“月球”。\r\n• 天气地图、天气代码、背景裁剪、描边颜色、设置逐级返回和定位间隔按本版本记录调整。\r\n• versionCode 升至 4，沿用包名与签名。",14,INK,false));gap(c,20);
        c.addView(text("1.0.1 · 自由布局与个性设置",19,INK,true));gap(c,12);
        c.addView(text("• 分级设置、背景选区与缩放、可选颜色描边。\r\n• 天气栏目和底部栏目可显隐排序，新增独立天气地图与天文栏目（默认隐藏）。\r\n• 定位可选 GPS 硬件或网络定位。\r\n• 课程支持自定义授课周与信息前缀，时间改为数字输入。\r\n• 改进天气图标、摄氏度、居中排版、图例与刷新提示。\r\n• 天文支持指定年月日时分、前后15分钟和前后一天，保留滚动位置并区分曙光暮光。\r\n• 更新隐私说明，移除极光功能。",14,INK,false));gap(c,20);
        c.addView(text("1.0.0 · 呆呆工具箱",19,INK,true));gap(c,12);
        c.addView(text("• 正式名称改为呆呆工具箱，新增天气 / 课程表 / GPS 底部导航。\r\n• 新增手动周课表、自定义节次、地点、老师与单双周。\r\n• 新增手机图片背景及遮罩调整。\r\n• 新增前台 GPS 经纬度、水平精度与状态。\r\n• 新增官方全国降水、温度和分区雷达网页。\r\n• 新增观星条件、星图、日月位置、月相和曙暮光。\r\n• 整理来源与隐私，Open-Meteo 入口放在一起。\r\n• 页脚改为“呆呆鸟使用Codex制作”。",14,INK,false));gap(c,20);
        c.addView(text("0.1.0-test · 简天气测试版",17,INK,true));gap(c,10);
        c.addView(text("早期版本（原标注 1.0.0）统一归为测试版：地区搜索与收藏、当前天气、24 小时及 7 日预报、离线缓存。",14,MUTED,false));
        ScrollView s=new ScrollView(this);s.addView(c);new ToolDialog(this).setTitle("每个版本的更新内容").setView(s).setPositiveButton("知道了",null).show();
    }
    void about() {
        LinearLayout content=column();content.setPadding(dp(22),dp(8),dp(22),dp(10));
        TextView details=text("呆呆工具箱 1.0.7 · 个人非商业版\r\n\r\n天气由 Open-Meteo 提供；地区检索基于 GeoNames。两者均按 CC BY 4.0 署名。应用对返回数据进行了中文描述、取整、单位标注与排版。\r\n\r\n当前天气为数值模型估算，非气象台站现场观测，也不替代官方预警。日期、小时与日出日落均按所选地区的当地时区显示。\r\n\r\n隐私：无需账号。定位仅在你授权后于前台获取，应用不上传坐标。GPS 硬件模式无需应用联网；网络定位依赖系统位置服务与网络，系统可能向其服务商发送 IP、基站或 Wi-Fi 信息；课程表与导入的背景图只保存在手机内部。天气查询需要联网。你的 IP、搜索词及所选地区坐标会发送给 Open-Meteo。收藏与天气缓存保存在本机；没有广告、统计或自建服务器。\r\n\r\n免费 API 仅限非商业使用。天气缓存 15 分钟，手动刷新间隔至少 1 分钟；服务限流时暂停请求。商业用途应另行取得服务授权。\r\n\r\n全国降水、气温、雷达和卫星云图通过应用内官方网页查看，版权归中央气象台等原始权利人。天文计算为本地近似结果。官方网页会按其自身规则处理访问数据。",14,INK,false);details.setLineSpacing(dp(4),1);content.addView(details);gap(content,16);
        link(content,"Open-Meteo · 天气数据来源","https://open-meteo.com/");link(content,"Open-Meteo · 使用条款与隐私","https://open-meteo.com/en/terms");link(content,"GeoNames · 地区数据来源","https://www.geonames.org/");link(content,"CC BY 4.0 · 数据许可","https://creativecommons.org/licenses/by/4.0/");link(content,"中央气象台 · 官方页面","https://www.nmc.cn/");link(content,"天文计算方法参考","https://stjarnhimlen.se/comp/ppcomp.html");
        ScrollView scroll=new ScrollView(this);scroll.addView(content);new ToolDialog(this).setTitle("数据来源与隐私").setView(scroll).setPositiveButton("知道了",null).show();
    }
    void link(LinearLayout parent,String label,String url) {TextView t=text(label+" ↗",13,BLUE,false);t.setMinHeight(dp(44));t.setPadding(dp(12),dp(7),dp(12),dp(7));t.setGravity(Gravity.CENTER_VERTICAL);tap(t,Color.TRANSPARENT,8,()->open(url));parent.addView(t,new LinearLayout.LayoutParams(-2,-2));gap(parent,8);}
    void open(String url) {try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(ActivityNotFoundException e){toast("未找到可打开链接的浏览器");}}
    private void hideKeyboard() {View focused=getCurrentFocus();if(focused!=null){((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(focused.getWindowToken(),0);focused.clearFocus();}}
    void toast(String message) {Toast toast=new Toast(this);LinearLayout box=row();box.setPadding(dp(16),dp(12),dp(16),dp(12));box.setBackground(shape(0xFF26364D,20));ImageView icon=new ImageView(this);icon.setImageResource(org.simpleweather.app.R.drawable.ic_launcher);box.addView(icon,lp(28,28));gapHorizontal(box,10);TextView label=text(message,16,WHITE,false);label.setMaxWidth(Math.max(dp(120),Math.min(dp(260),getResources().getDisplayMetrics().widthPixels-dp(100))));box.addView(label);toast.setView(box);toast.setDuration(Toast.LENGTH_SHORT);toast.setGravity(Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL,0,dp(100));toast.show();}
    @Override public void onBackPressed() {if(picking){hideKeyboard();switchTab(tab);}else if(tab!=visibleTabs().get(0))switchTab(visibleTabs().get(0));else super.onBackPressed();}
    @Override protected void onPause(){if(gps!=null)gps.pause();if(maps!=null)maps.pause();super.onPause();}
    @Override protected void onResume(){super.onResume();if(gps!=null&&tab==2)gps.resume();if(maps!=null)maps.resume();}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){super.onRequestPermissionsResult(request,permissions,grants);if(request==41)gps.permissionResult();}
    @Override protected void onActivityResult(int request,int result,Intent intent){super.onActivityResult(request,result,intent);if(request==42&&result==RESULT_OK&&intent!=null&&intent.getData()!=null){Intent crop=new Intent(this,BackgroundCropActivity.class);crop.setData(intent.getData());crop.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivityForResult(crop,43);}else if(request==43&&result==RESULT_OK)reloadBackground();}
    @Override protected void onDestroy(){dead=true;if(maps!=null)maps.destroy();if(gps!=null)gps.pause();main.removeCallbacksAndMessages(null);workers.shutdownNow();super.onDestroy();}
}
