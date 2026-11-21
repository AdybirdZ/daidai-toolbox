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
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    @Override public android.content.res.Resources getResources(){return ModuleAssets.resources(this,super.getResources());}
    @Override public android.content.res.AssetManager getAssets(){return getResources().getAssets();}
    @Override protected void attachBaseContext(android.content.Context base){super.attachBaseContext(AppLanguage.wrap(base));}
    public static final int TITLE=0xFF010102;
    public static final int BG=0xFFF5F7FB, WHITE=Color.WHITE, INK=0xFF20324C, MUTED=0xFF718097, BLUE=0xFF246BCE, PALE=0xFFEAF2FF;
    private final ExecutorService workers=Executors.newFixedThreadPool(2);
    private final Handler main=new Handler(Looper.getMainLooper());
    private WeatherClient api;
    private final Set<String> inFlight=new HashSet<>();
    private final Map<String,Long> attempts=new HashMap<>();
    public WeatherStore store;
    private ConfigurationTransfer configuration;
    private ModuleTransfer moduleTransfer;
    private AppIconSettings appIcons;
    private List<City> catalog;
    public City city;
    public WeatherData data;
    private LinearLayout host, results;
    public LinearLayout body;
    public int tab=-1;
    public AppModules apps;
    private Bundle initialScreenState;
    private final Map<Integer,ScreenHandle> screens=new HashMap<>();
    private ScrollView pageScroll;
    private AppStoreScreen appStoreScreen;
    private LinearLayout navigation;
    private int navigationOffset;
    private Instant astronomyTime=Instant.now();
    private int pendingScrollY=-1;
    private ScreenHandle maps;
    private ScreenHandle schedule;
    private ScreenHandle gps;
    private ScreenHandle earthquakes;
    private ScreenHandle randomChoice;
    private ScreenHandle todo;
    private android.graphics.Bitmap background;
    public android.content.SharedPreferences preferences;
    private EditText search;
    private TextView searchStatus;
    public boolean picking, loading, dead;
    public String notice="";
    public int searchVersion;
    private int reopenSettings;
    private int cacheGeneration;
    private boolean deferMapReload;
    private String activityLanguage;
    private static final Set<MainActivity> ACTIVE=Collections.newSetFromMap(new WeakHashMap<MainActivity,Boolean>());
    private boolean reloadSharedData;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ACTIVE.add(this);
        activityLanguage=AppLanguage.choice(this);
        if(state!=null)reopenSettings=state.getInt("toolbox_language_settings",0);
        configuration=new ConfigurationTransfer(this);moduleTransfer=new ModuleTransfer(this);appIcons=new AppIconSettings(this);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        if(Build.VERSION.SDK_INT>=29)getWindow().setNavigationBarContrastEnforced(false);
        store=new WeatherStore(this);catalog=store.catalog();
        if(catalog.isEmpty()) {
            try {catalog.add(new City(new JSONObject(UiText.raw("identity_main_001"))));}catch(Exception ignored){}
        }
        city=store.selected(catalog.get(0));data=store.cached(city);
        preferences=getSharedPreferences("toolbox",0);
        applyKeepScreenAwake();
        if(preferences.getInt("settings_schema",0)<4){SharedPreferences.Editor e=preferences.edit();if(!preferences.contains("background_brightness"))e.putInt("background_brightness",100-Math.max(0,Math.min(100,preferences.getInt("shade",80))));if(preferences.getInt("radar_region",0)>7)e.putInt("radar_region",0);if(preferences.getInt("map_product",0)==4)e.putInt("map_product",0);e.remove("shade").putInt("settings_schema",4).apply();}
        if(preferences.getInt("star_names_schema",0)<1){String[][] renamed={{UiText.raw("identity_main_002"),UiText.raw("identity_astronomy_036")},{UiText.raw("identity_main_003"),UiText.raw("identity_astronomy_042")},{UiText.raw("identity_main_004"),UiText.raw("identity_astronomy_050")}};SharedPreferences.Editor e=preferences.edit();for(String[] pair:renamed){String oldKey="astro_star_"+pair[0],newKey="astro_star_"+pair[1];if(preferences.contains(oldKey)&&!preferences.contains(newKey))e.putBoolean(newKey,preferences.getBoolean(oldKey,true));if(preferences.getString("astro_selected","").equals(pair[0]))e.putString("astro_selected",pair[1]);}e.putInt("star_names_schema",1).apply();}
        if(preferences.getInt("deep_names_schema",0)<1){
            String[][] renamed={{UiText.raw("identity_main_005"),UiText.raw("identity_astronomy_017")},{UiText.raw("identity_main_006"),UiText.raw("identity_astronomy_018")},{UiText.raw("identity_main_007"),UiText.raw("identity_astronomy_019")},{UiText.raw("identity_main_008"),UiText.raw("identity_astronomy_020")},{UiText.raw("identity_main_009"),UiText.raw("identity_astronomy_021")},{UiText.raw("identity_main_010"),UiText.raw("identity_astronomy_030")},{UiText.raw("identity_main_011"),UiText.raw("identity_astronomy_031")}};
            SharedPreferences.Editor e=preferences.edit();for(String[] pair:renamed){String oldKey="astro_deep_"+pair[0],newKey="astro_deep_"+pair[1];if(preferences.contains(oldKey)&&!preferences.contains(newKey))e.putBoolean(newKey,preferences.getBoolean(oldKey,true));if(preferences.getString("astro_selected","").equals(pair[0]))e.putString("astro_selected",pair[1]);e.remove(oldKey);}e.putInt("deep_names_schema",1).apply();
        }
        initialScreenState=state;apps=new AppModules(this);apps.reconcile();apps.initialize();ModuleAssets.invalidate();catalog=store.catalog();
        background=android.graphics.BitmapFactory.decodeFile(new java.io.File(getFilesDir(),"background.jpg").getPath());
        if(state==null&&getIntent().getBooleanExtra("open_todo",false))preferences.edit().putInt("last_tab",7).putBoolean("tab_show_7",true).apply();
        if(state==null&&getIntent().getBooleanExtra("open_location",false))preferences.edit().putInt("last_tab",2).putBoolean("tab_show_2",true).apply();
        tab=state==null?-1:state.getInt("toolbox_tab",-1);
        if(state!=null){astronomyTime=Instant.ofEpochMilli(state.getLong("toolbox_astro_time",astronomyTime.toEpochMilli()));pendingScrollY=state.getInt("toolbox_scroll",0);}
        if(state==null&&getIntent().hasExtra("last_tab"))tab=getIntent().getIntExtra("last_tab",-1);
        if(state==null&&getIntent().getBooleanExtra("open_location",false))tab=2;
        if(state==null&&getIntent().getBooleanExtra("open_todo",false))tab=7;
        if(getIntent().getBooleanExtra("configuration_applied",false)){tab=-1;getIntent().removeExtra("configuration_applied");}
        switchTab(tab);if(tab==0)load(false);LauncherScreen.openRequested(this,getIntent());


        onShellReady(state);
        boolean languageReopen=reopenSettings>0;if(languageReopen){int level=reopenSettings;reopenSettings=0;new SettingsScreen(this).settingsPath(level==2);}
        if(!languageReopen)UiSession.restore(this,state,id->{int index=Arrays.asList(AppModules.IDS).indexOf(id);return index<0?null:screen(index);});
        if(state==null&&preferences.getInt("seen_version",0)<versionCode())main.postDelayed(()->{if(!dead){updates();preferences.edit().putInt("seen_version",versionCode()).apply();}},700);
    }
    protected void onShellReady(Bundle state){}
    public int dp(float n) {return Math.round(n*getResources().getDisplayMetrics().density);}
    public LinearLayout column() {LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    public LinearLayout row() {LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    public LinearLayout.LayoutParams lp(int w,int h) {return new LinearLayout.LayoutParams(w<0?w:dp(w),h<0?h:dp(h));}
    public TextView text(String value,int size,int color,boolean bold) {
        TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(resolveTextColor(color));t.setTag(color==TITLE?UiColors.TITLE:color==INK||color==BLUE?UiColors.BODY:color==MUTED?UiColors.NOTE:"text-fixed");
        t.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));t.setIncludeFontPadding(false);
        t.setLineSpacing(dp(3),1);t.setTextIsSelectable(false);t.setLongClickable(false);return t;
    }
    public GradientDrawable shape(int color,int radius) {GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));if(outlined())d.setStroke(dp(1),outlineColor());return d;}
    public void tap(View v,int color,int radius,Runnable action) {
        v.setBackground(new RippleDrawable(android.content.res.ColorStateList.valueOf(0x223577CC),shape(color,radius),shape(WHITE,radius)));
        v.setOnClickListener(view->action.run());v.setClickable(true);v.setFocusable(true);
    }
    public TextView button(String title, boolean primary, Runnable click) {
        TextView t=text(title,14,primary?buttonPrimaryText():buttonSecondaryText(),true);t.setGravity(Gravity.CENTER);
        t.setTextColor(primary?buttonPrimaryText():buttonSecondaryText());t.setMinHeight(dp(48));t.setPadding(dp(16),dp(10),dp(16),dp(10));tap(t,primary?buttonPrimaryColor():buttonSecondaryColor(),16,click);t.setTag(primary?"theme-primary":"theme-secondary");return t;
    }
    public void gap(LinearLayout parent,int h) {View v=new View(this);parent.addView(v,lp(1,h));}
    public void spacer(LinearLayout parent) {parent.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));}
    public void line(LinearLayout parent) {View v=new View(this);v.setBackgroundColor(0xFFF0F3F8);parent.addView(v,new LinearLayout.LayoutParams(-1,dp(1)));}
    public void startScreen() {
        if(maps!=null){maps.call("destroy");maps=null;screens.remove(3);}
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
        else{pageScroll=new ReadingScrollView(this);pageScroll.setFillViewport(true);pageScroll.setClipToPadding(false);pageScroll.setVerticalScrollBarEnabled(false);final int restore=pendingScrollY;pendingScrollY=-1;pageScroll.addOnLayoutChangeListener(new View.OnLayoutChangeListener(){public void onLayoutChange(View v,int l,int t,int r,int b,int ol,int ot,int or,int ob){if(restore>=0){v.scrollTo(0,restore);v.removeOnLayoutChangeListener(this);}}});FrameLayout.LayoutParams fit=new FrameLayout.LayoutParams(Math.min(getResources().getDisplayMetrics().widthPixels,dp(620)),-1,Gravity.CENTER_HORIZONTAL);frame.addView(pageScroll,fit);pageScroll.addView(body);}
        pageHeader();
        navigation=row();navigation.setPadding(dp(8),dp(6),dp(8),dp(6));navigation.setBackground(shape(0xF2FFFFFF,0));host.addView(navigation,lp(-1,-2));renderNavigation();
    }
    public static final int[] TAB_NAME_IDS={R.string.tab_weather,R.string.tab_schedule,R.string.tab_status,R.string.tab_weather_map,R.string.tab_astronomy,R.string.tab_earthquake,R.string.tab_random,R.string.tab_todo};
    public static final int[] WEATHER_NAME_IDS={R.string.weather_area,R.string.weather_current,R.string.weather_hourly,R.string.weather_daily,R.string.weather_details,R.string.weather_sources};
    public String tabName(int id){return getString(TAB_NAME_IDS[id]);}
    public String weatherName(int id){return getString(WEATHER_NAME_IDS[id]);}
    public String[] tabNames(){String[] out=new String[TAB_NAME_IDS.length];for(int i=0;i<out.length;i++)out[i]=tabName(i);return out;}
    public String[] weatherNames(){String[] out=new String[WEATHER_NAME_IDS.length];for(int i=0;i<out.length;i++)out[i]=weatherName(i);return out;}
    public boolean outlined(){return preferences!=null&&preferences.getBoolean("outline",false);}
    public int outlineColor(){return preferences.getInt("outline_color",Color.BLACK);}
    public int themeColor(){return UiColors.seed(this,UiColors.BUTTON);}
    public int primaryTextColor(){return UiColors.ink(this,UiColors.BODY);}
    public int secondaryTextColor(){return UiColors.ink(this,UiColors.NOTE);}
    public int accentColor(){return primaryTextColor();}
    public int titleTextColor(){return UiColors.ink(this,UiColors.TITLE);}
    public int linkTextColor(){return UiColors.ink(this,UiColors.LINK);}
    public int buttonPrimaryColor(){return UiColors.ink(this,UiColors.BUTTON);}
    public int buttonPrimaryText(){return Color.WHITE;}
    public int buttonSecondaryColor(){return UiColors.pale(this,UiColors.BUTTON);}
    public int buttonSecondaryText(){return buttonPrimaryColor();}
    public void applyTheme(){ToolDialog.refresh(this);refreshScreen();}
    public void applyKeepScreenAwake(){if(preferences!=null&&preferences.getBoolean("keep_screen_awake",false))getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);}
    public void markLink(TextView view){boolean button="theme-secondary".equals(view.getTag())||"theme-primary".equals(view.getTag());UiColors.mark(view,UiColors.LINK);if(button){view.setTag("theme-link-button");view.setBackground(new RippleDrawable(android.content.res.ColorStateList.valueOf(0x22000000),shape(UiColors.pale(this,UiColors.LINK),16),null));}}
    public int resolveTextColor(int color){if(color==TITLE)return titleTextColor();if(color==INK)return primaryTextColor();if(color==MUTED)return secondaryTextColor();if(color==BLUE)return accentColor();if(color==WHITE)return buttonPrimaryText();return color;}
    public ZoneId displayZone(ZoneId fallback){int mode=preferences.getInt("global_time_mode",0);return mode==1?ZoneId.of("Asia/Shanghai"):mode==2?ZoneOffset.UTC:fallback;}
    public List<Integer> visibleTabs(){List<Integer> out=new ArrayList<>();for(int id:DisplayOrder.parse(preferences.getString("tab_order",""),8))if(apps!=null&&apps.installed(AppModules.IDS[id]))out.add(id);return out;}

    public void updateReadingValues(Runnable change){if(pageScroll instanceof ReadingScrollView)((ReadingScrollView)pageScroll).updateValues(change);else change.run();}
    public void refreshScreen(){switchTab(tab);}
    public void installedAppsChanged(){if(tab==-2&&appStoreScreen!=null)appStoreScreen.installedChanged();else{pendingScrollY=pageScroll==null?0:pageScroll.getScrollY();refreshScreen();}}
    public void prepareApplicationDataChange(String id){for(MainActivity owner:new ArrayList<>(ACTIVE)){if(owner.dead)continue;if(owner!=this){owner.releaseModule(id);owner.reloadSharedData=true;}if(id.equals("weather")){owner.cacheGeneration++;owner.searchVersion++;owner.loading=false;owner.data=null;owner.notice="";owner.inFlight.clear();}}}
    public void applicationDataChanged(String id,boolean erased){if(id.equals("weather")){if(erased){store=new WeatherStore(this);catalog=store.catalog();try{City fallback=new City(new JSONObject(UiText.raw("identity_main_001")));city=store.selected(catalog.isEmpty()?fallback:catalog.get(0));}catch(Exception e){android.util.Log.e("WeatherStore","Cannot reset selected location",e);}}data=store.cached(city);notice="";}if(tab>=0&&AppModules.IDS[tab].equals(id))refreshScreen();}
    private void pageHeader(){
        TextView title=text(tab<0?getString(R.string.app_name):getString(R.string.app_name)+" · "+tabName(tab),25,TITLE,true);body.addView(title);gap(body,7);body.addView(text(getString(R.string.app_footer),12,MUTED,false));gap(body,12);
        if(tab<0)return;LinearLayout actions=row();if(tab==0||tab==4){actions.addView(button(getString(R.string.weather_area),false,this::showPicker));gapHorizontal(actions,8);}actions.addView(button(getString(R.string.action_settings),false,this::settings));if(tab==1){gapHorizontal(actions,8);actions.addView(button(getString(R.string.action_add_course),true,()->{ScreenHandle s=screen(1);if(s!=null)s.call("addCourse");}));}body.addView(actions);gap(body,16);
    }
    public void gapHorizontal(LinearLayout parent,int width){parent.addView(new View(this),lp(width,1));}
    private void renderNavigation(){navigation.removeAllViews();navigation.setVisibility(tab==-1?View.GONE:View.VISIBLE);if(tab!=-1){navigation.addView(button(getString(R.string.launcher_home),false,()->switchTab(-1)),new LinearLayout.LayoutParams(0,dp(48),1));}}
    private ScreenHandle screen(int id){
        if(id<0||id>=8||!apps.installed(AppModules.IDS[id]))return null;
        ScreenHandle value=screens.get(id);if(value!=null)return value;
        try{value=apps.create(id,this,city,data,astronomyTime,this::showAstronomy);screens.put(id,value);if(id==1)schedule=value;if(id==2)gps=value;if(id==3)maps=value;if(id==5){earthquakes=value;if(initialScreenState!=null){value.call("restoreState",initialScreenState);initialScreenState=null;}}if(id==6)randomChoice=value;if(id==7)todo=value;return value;}catch(Throwable e){moduleFailed(id,e);return null;}
    }
    public void releaseModule(String id){ModuleLaunch.cancel(this,id);int n=Arrays.asList(AppModules.IDS).indexOf(id);ScreenHandle s=screens.remove(n);if(s!=null)for(String hook:new String[]{"flush","pause","destroy"})try{s.call(hook);}catch(Throwable e){android.util.Log.w("AppModules","Cannot stop "+id,e);}if(n==0)api=null;if(n==2){LocationService.stop(this);gps=null;}if(n==1)schedule=null;if(n==3)maps=null;if(n==5)earthquakes=null;if(n==6)randomChoice=null;if(n==7)todo=null;}
    private void moduleFailed(int index,Throwable failure){String id=AppModules.IDS[index];android.util.Log.e("AppModules","Cannot open "+id,failure);releaseModule(id);boolean recovered=false;try{recovered=apps.recoverPrevious(id);}catch(Exception e){android.util.Log.e("AppModules","Cannot recover "+id,e);}toast(getString(recovered?R.string.module_recovered:R.string.module_open_failed));main.post(()->{if(!dead)launcher();});}
    private void renderModule(int id,boolean mapReload){ModuleLaunch.begin(this,AppModules.IDS[id]);ScreenHandle s=screen(id);if(s==null)return;try{if(id==3)s.call("render",mapReload);else s.call("render");View rendered=body;ModuleLaunch.observe(this,AppModules.IDS[id],rendered,()->tab==id&&body==rendered);}catch(Throwable e){moduleFailed(id,e);}}
    public void launcher(){switchTab(-1);}
    private void showLauncher(){startScreen();new LauncherScreen(this).render();}

    public LinearLayout card() {LinearLayout c=column();c.setPadding(dp(18),dp(20),dp(18),dp(18));c.setBackground(shape(WHITE,24));body.addView(c,lp(-1,-2));return c;}
    private void showWeather(){renderModule(0,false);}

    public void load(boolean force) {
        if(dead)return;
        if(!apps.installed("weather")){toast(getString(R.string.cross_weather_missing));return;}
        String key=city.key();WeatherData cache=store.cached(city);
        if(inFlight.contains(key)){toast(UiText.get("earthquake_082"));return;}
        long now=System.currentTimeMillis();
        if(!force&&cache!=null&&cache.fresh(now)) {data=cache;notice=cache.currentIsOld(Instant.now())?UiText.get("main_034"):"";if(weatherVisible())showWeather();return;}
        Long previous=attempts.get(key);
        if(previous!=null&&now-previous<60_000){toast(UiText.get("main_035"));return;}
        attempts.put(key,now);inFlight.add(key);loading=true;notice="";if(weatherVisible())showWeather();final City requested=city;final int generation=cacheGeneration;
        workers.execute(()->{
            WeatherData received=null;String error=null;
            try {received=new WeatherData(weatherClient().forecast(requested),System.currentTimeMillis());}
            catch(Exception e){error=e instanceof java.io.IOException?e.getMessage():UiText.get("main_036");if(error==null||!error.matches(".*[\\u4e00-\\u9fff].*"))error=UiText.get("main_037");}
            final WeatherData result=received;final String failure=error;
            main.post(()->{if(dead)return;inFlight.remove(key);if(generation!=cacheGeneration){loading=inFlight.contains(city.key());if(weatherVisible())showWeather();return;}if(result!=null)store.save(requested,result);if(!city.key().equals(key))return;loading=false;
                if(result!=null){data=result;notice="";if(force)toast(UiText.get("earthquake_086"));}else{data=store.cached(requested);notice=data==null?failure:failure+UiText.get("main_038");}
                if(weatherVisible())showWeather();
            });
        });
    }
    private WeatherClient weatherClient()throws Exception {
        if(api==null)api=(WeatherClient)Class.forName("org.simpleweather.app.WeatherApi",true,ModuleRuntime.loader(this,"weather")).getConstructor(WeatherClient.RequestPolicy.class).newInstance(store);
        return api;
    }
    private void manualReferenceCity(){
        LinearLayout fields=column();fields.setPadding(dp(18),dp(8),dp(18),dp(8));
        EditText longitude=new EditText(this),latitude=new EditText(this);
        longitude.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL|android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
        latitude.setInputType(longitude.getInputType());longitude.setSingleLine(true);latitude.setSingleLine(true);
        longitude.setHint(getString(R.string.cross_longitude));latitude.setHint(getString(R.string.cross_latitude));
        longitude.setText(Double.toString(city.longitude));latitude.setText(Double.toString(city.latitude));
        fields.addView(longitude,lp(-1,56));gap(fields,10);fields.addView(latitude,lp(-1,56));gap(fields,12);
        TextParagraphs.append(this,fields,getString(R.string.cross_reference_note),13,MUTED);
        AlertDialog dialog=new ToolDialog(this).setTitle(getString(R.string.cross_reference_title)).setView(fields).setPositiveButton(getString(R.string.action_save),null).setNegativeButton(getString(R.string.action_cancel),null).create();
        dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(b->{try{
            double lon=Double.parseDouble(longitude.getText().toString()),lat=Double.parseDouble(latitude.getText().toString());
            if(!Double.isFinite(lon)||!Double.isFinite(lat)||lon< -180||lon>180||lat< -90||lat>90)throw new IllegalArgumentException();
            City next=new City(new JSONObject().put("id",0).put("name",getString(R.string.cross_reference_title)).put("latitude",lat).put("longitude",lon).put("timezone",java.time.ZoneId.systemDefault().getId()));
            store.select(next);city=next;data=store.cached(next);dialog.dismiss();switchTab(tab);
        }catch(Exception e){toast(getString(R.string.cross_coordinates_invalid));}}));dialog.show();
    }
    private boolean clearEarthquakeFiles(){File directory=new File(getFilesDir(),"earthquake_cache");File[] files=directory.listFiles();boolean ok=true;if(files!=null)for(File f:files)if(f.isFile())ok=f.delete()&&ok;return ok;}
    private void choose(City selected) {
        hideKeyboard();city=selected;store.select(city);data=store.cached(city);notice="";loading=inFlight.contains(city.key());switchTab(tab);if(!loading)load(false);
    }
    public void showPicker() {
        if(!apps.installed("weather")){manualReferenceCity();return;}
        picking=true;searchVersion++;startScreen();
        LinearLayout heading=row();heading.addView(text(UiText.get("main_039"),25,TITLE,true));spacer(heading);heading.addView(button(UiText.get("main_040"),false,()->{hideKeyboard();switchTab(tab);}));body.addView(heading);gap(body,10);
        body.addView(text(UiText.get("main_041"),13,MUTED,false));gap(body,18);
        LinearLayout input=row();
        search=new EditText(this);search.setSingleLine(true);search.setTextSize(16);search.setTextColor(primaryTextColor());search.setHintTextColor(secondaryTextColor());search.setHint(UiText.get("main_042"));search.setBackground(shape(WHITE,18));search.setPadding(dp(13),dp(15),dp(10),dp(15));search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);search.setInputType(android.text.InputType.TYPE_CLASS_TEXT);input.addView(search,new LinearLayout.LayoutParams(0,-2,1));
        gapHorizontal(input,10);input.addView(button(UiText.get("main_043"),true,this::searchOnline));body.addView(input);gap(body,20);
        results=column();body.addView(results);searchStatus=text("",13,MUTED,false);
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){searchVersion++;renderLocal(s.toString());}public void afterTextChanged(Editable e){}});
        search.setOnEditorActionListener((v,action,event)->{if(action==EditorInfo.IME_ACTION_SEARCH){searchOnline();return true;}return false;});
        renderLocal("");
        gap(body,20);TextView credit=text(getString(R.string.city_credit),11,MUTED,false);credit.setLineSpacing(dp(5),1);credit.setMinHeight(dp(48));markLink(credit);tap(credit,Color.TRANSPARENT,8,()->open("https://github.com/AdybirdZ/daidai-toolbox/blob/main/docs/CITY-CATALOG.md"));credit.setPadding(dp(12),dp(8),dp(12),dp(8));body.addView(credit,new LinearLayout.LayoutParams(-2,-2));
    }
    private void renderLocal(String query) {
        results.removeAllViews();String q=query.trim();
        if(q.isEmpty()) {
            List<City> saved=store.saved();if(!saved.isEmpty()){results.addView(text(UiText.get("main_044"),16,TITLE,true));gap(results,10);cityList(saved);gap(results,22);}
            results.addView(text(UiText.get("main_045"),13,MUTED,false));
        } else {
            List<City> matched=new ArrayList<>();
            for(City c:store.saved())if(c.matches(q))addUniquePlace(matched,c);
            for(City c:catalog)if(c.matches(q))addUniquePlace(matched,c);
            results.addView(text(matched.isEmpty()?UiText.get("main_046"):UiText.get("main_047"),15,INK,true));gap(results,10);cityList(matched);
            gap(results,8);results.addView(text(UiText.get("main_048"),12,MUTED,false));
        }
    }
    private void cityList(List<City> cities) {
        for(City c:cities) {
            LinearLayout item=row();item.setPadding(dp(14),dp(14),dp(12),dp(14));LinearLayout labels=column();labels.addView(text(c.label(),16,TITLE,true));gap(labels,6);labels.addView(text(c.detail(),12,MUTED,false));gap(labels,5);labels.addView(text(CoordinateFormat.pair(c.longitude,c.latitude,6),12,MUTED,false));item.addView(labels,new LinearLayout.LayoutParams(0,-2,1));item.addView(text(city.key().equals(c.key())?UiText.get("main_049"):"›",14,BLUE,false));tap(item,WHITE,16,()->choose(c));results.addView(item,lp(-1,-2));gap(results,8);
        }
    }
    private void searchOnline() {
        if(search==null)return;final String query=search.getText().toString().trim();if(query.length()<2){toast(UiText.get("main_050"));return;}
        final int version=++searchVersion;hideKeyboard();results.removeAllViews();results.addView(text(UiText.get("main_051")+query+"”…",14,MUTED,false));
        workers.execute(()->{
            List<City> found=new ArrayList<>();String failure=null;try{found=weatherClient().search(query);}catch(Exception e){failure=UiText.get("main_052");}
            final List<City> list=new ArrayList<>();
            for(City c:catalog)if(c.matches(query))addUniquePlace(list,c);
            for(City c:found)addUniquePlace(list,c);
            final String error=failure;
            main.post(()->{if(dead||!picking||version!=searchVersion)return;results.removeAllViews();
                results.addView(text(error!=null?error:list.isEmpty()?UiText.get("main_053"):UiText.get("main_054"),14,error!=null?0xFF8B6021:INK,false));gap(results,14);cityList(list);
            });
        });
    }
    private void addUniquePlace(List<City> list,City candidate){candidate=City.resolve(catalog,candidate);if(candidate.excludedAdministrativeArea())return;for(City existing:list)if(existing.samePlace(candidate))return;list.add(candidate);}
    public boolean weatherVisible(){return tab==0&&!picking;}
    public void switchTab(int next){hideKeyboard();for(ScreenHandle s:screens.values())s.call("pause");if(next!=2&&gps!=null)gps.call("stopForNavigation");ModuleLaunch.leave(this);if(next>=0&&!apps.installed(AppModules.IDS[next]))next=-1;tab=next;picking=false;preferences.edit().putInt("last_tab",tab).apply();if(next==-1){showLauncher();return;}if(next==-2){startScreen();appStoreScreen=new AppStoreScreen(this);appStoreScreen.render();return;}if(next==0){showWeather();load(false);return;}if(next==4){ScreenHandle old=screens.remove(4);if(old!=null)old.call("destroy");}startScreen();renderModule(next,!deferMapReload);deferMapReload=false;}

    public void official(String title,String url){Intent i=new Intent(this,OfficialPageActivity.class);i.putExtra("title",title);i.putExtra("url",url);startActivity(i);}
    private void showAstronomy(Instant at){pendingScrollY=pageScroll==null?0:pageScroll.getScrollY();astronomyTime=at;switchTab(4);}
    public void changeLanguage(String choice){
        if(AppLanguage.choice(this).equals(choice))return;
        if(randomChoice!=null)randomChoice.call("flush");
        reopenSettings=2;preferences.edit().putString(LanguagePolicy.KEY,choice).apply();
        ToolDialog.closeAll();LocationService.languageChanged();TodoScheduler.refresh(this,false);recreate();
    }
    public void settings(){SettingsActivity.open(this,tab>=0&&tab<AppModules.IDS.length?AppModules.IDS[tab]:"");}
    public void exportConfiguration(){if(randomChoice!=null)randomChoice.call("flush");configuration.exportFile();}
    public void importConfiguration(){configuration.importFile();}
    public void exportModule(String id){moduleTransfer.exportModule(id);}
    public void importModule(String id){moduleTransfer.importModule(id);}
    public void randomChoiceSettings(){if(!(this instanceof SettingsActivity)){SettingsActivity.open(this,"random");return;}ScreenHandle s=screen(6);if(s!=null)s.call("settings");}
    public void todoSettings(){if(!(this instanceof SettingsActivity)){SettingsActivity.open(this,"todo");return;}ScreenHandle s=screen(7);if(s!=null)s.call("settings");}
    public void prepareConfigurationImport(){for(MainActivity owner:new ArrayList<>(ACTIVE)){if(owner.dead)continue;if(owner.todo!=null)owner.todo.call("pause");TodoScheduler.suspend(owner);if(owner.randomChoice!=null)owner.randomChoice.call("stopForImport");owner.searchVersion++;owner.cacheGeneration++;if(owner.gps!=null)owner.gps.call("stopForImport");if(owner.earthquakes!=null)owner.earthquakes.call("destroy");if(owner!=this){for(String id:AppModules.IDS)owner.releaseModule(id);owner.reloadSharedData=true;}}}
    public void configurationImportFinished(boolean success,boolean restored){
        toast(getString(success?R.string.config_imported:restored?R.string.config_import_failed:R.string.config_restore_failed));
        // A singleTop launcher must keep ordinary re-entry intact; configuration import
        // deliberately rebuilds the current owner after all imported models are committed.
        getIntent().putExtra("configuration_applied",true);recreate();
    }
    public String versionName(){try{return getPackageManager().getPackageInfo(getPackageName(),0).versionName;}catch(Exception e){return "—";}}
    private int versionCode(){try{return getPackageManager().getPackageInfo(getPackageName(),0).versionCode;}catch(Exception e){return 0;}}
    public void scheduleSettings(){if(!(this instanceof SettingsActivity)){SettingsActivity.open(this,"schedule");return;}ScreenHandle s=screen(1);if(s!=null)s.call("menu");}
    public boolean restoreDefaults(){boolean languageChanged=!AppLanguage.choice(this).equals("system");for(MainActivity owner:new ArrayList<>(ACTIVE))if(!owner.dead&&owner.gps!=null)owner.gps.call("modeChanged");preferences.edit().clear().putInt("settings_schema",4).putInt("seen_version",versionCode()).apply();ScreenHandle timetable=screen(1);if(timetable!=null)timetable.call("resetSettings");for(MainActivity owner:new ArrayList<>(ACTIVE))if(owner!=this&&!owner.dead){owner.releaseModule("schedule");owner.reloadSharedData=true;}TodoScheduler.changed(this);navigationOffset=0;tab=0;boolean iconRestored=AppIconStore.reset(this);resetBackground();if(languageChanged){reopenSettings=1;ToolDialog.closeAll();LocationService.languageChanged();TodoScheduler.refresh(this,false);recreate();}return iconRestored;}
    public void locationIntervalChanged(){if(gps!=null)gps.call("intervalChanged");}
    public void locationContinuousChanged(){if(!LocationService.continuous(this)&&tab!=2)LocationService.stop(this);else if(gps!=null)gps.call("continuousChanged");}
    public void earthquakeSettings(){if(!(this instanceof SettingsActivity)){SettingsActivity.open(this,"earthquake");return;}ScreenHandle s=screen(5);if(s!=null)s.call("settings");}
    public void locationModeChanged(){if(gps!=null)gps.call("modeChanged");if(tab==2)switchTab(2);}
    public void appIconSettings(){appIcons.show();}
    public void selectBackground(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("image/*");try{startActivityForResult(i,42);}catch(ActivityNotFoundException e){toast(UiText.get("main_055"));}}
    public void resetBackground(){new java.io.File(getFilesDir(),"background.jpg").delete();background=null;refreshScreen();}
    public void reloadBackground(){background=android.graphics.BitmapFactory.decodeFile(new java.io.File(getFilesDir(),"background.jpg").getPath());refreshScreen();}
    private void updateEntry(LinearLayout c,String title,String details){
        LinearLayout entry=column();TextView detail=text(details,14,INK,false);detail.setPadding(dp(12),dp(12),dp(12),dp(8));detail.setVisibility(View.GONE);
        TextView toggle=button(title+"  ＋",false,()->{});toggle.setGravity(Gravity.CENTER_VERTICAL);toggle.setOnClickListener(v->{boolean open=detail.getVisibility()!=View.VISIBLE;detail.setVisibility(open?View.VISIBLE:View.GONE);toggle.setText(title+(open?"  −":"  ＋"));toggle.setContentDescription(title+(open?UiText.get("main_056"):UiText.get("main_057")));});toggle.setContentDescription(title+UiText.get("main_057"));entry.addView(toggle,lp(-1,-2));entry.addView(detail);c.addView(entry);gap(c,10);
    }
    public void updates(){
        LinearLayout c=column();c.setPadding(dp(20),dp(8),dp(20),dp(16));
        updateEntry(c,getString(R.string.update_301_title),getString(R.string.update_301_detail));
        updateEntry(c,getString(R.string.update_300_title),getString(R.string.update_300_detail));
        updateEntry(c,getString(R.string.update_pre3004_title),getString(R.string.changes_pre3_4));
        updateEntry(c,getString(R.string.update_pre3003_title),getString(R.string.changes_pre3_3));
        updateEntry(c,getString(R.string.update_pre3002_title),getString(R.string.changes_pre3_2));
        updateEntry(c,getString(R.string.update_pre3001_title),getString(R.string.update_pre3001_detail));
        updateEntry(c,getString(R.string.update_221_title),getString(R.string.update_221_detail));
        updateEntry(c,getString(R.string.update_220_title),getString(R.string.update_220_detail));
        updateEntry(c,getString(R.string.update_215_title),getString(R.string.update_215_detail));
        updateEntry(c,getString(R.string.update_214_title),getString(R.string.update_214_detail));
        updateEntry(c,getString(R.string.update_213_title),getString(R.string.update_213_detail));
        updateEntry(c,getString(R.string.update_212_title),getString(R.string.update_212_detail));
        updateEntry(c,getString(R.string.update_211_title),getString(R.string.update_211_detail));
        updateEntry(c,getString(R.string.update_210_title),getString(R.string.update_210));
        updateEntry(c,getString(R.string.update_200_title),getString(R.string.update_200_detail));
        updateEntry(c,getString(R.string.update_132_title),getString(R.string.update_132_detail));
        updateEntry(c,getString(R.string.update_131_title),getString(R.string.update_131_detail));
        updateEntry(c,getString(R.string.update_130_title),getString(R.string.update_130_detail));
        updateEntry(c,getString(R.string.update_1215_title),getString(R.string.update_1215_detail));
        updateEntry(c,getString(R.string.update_1214_title),getString(R.string.update_1214_detail));
        updateEntry(c,getString(R.string.update_1213_title),getString(R.string.update_1213_detail));
        updateEntry(c,getString(R.string.update_1212_title),getString(R.string.update_1212_detail));
        updateEntry(c,getString(R.string.update_1211_title),getString(R.string.update_1211_detail));
        updateEntry(c,getString(R.string.update_1210_title),getString(R.string.update_1210_detail));
        updateEntry(c,UiText.get("update_129_title_001"),UiText.get("update_history_001") );
        updateEntry(c,UiText.get("update_128_title_001"),UiText.get("update_history_002") );
        updateEntry(c,UiText.get("update_127_title_001"),UiText.get("update_history_003"));
        updateEntry(c,UiText.get("update_126_title_001"),UiText.get("update_history_004"));
        updateEntry(c,UiText.get("update_125_title_001"),UiText.get("update_history_005"));
        updateEntry(c,UiText.get("update_124_title_001"),UiText.get("update_history_006"));
        updateEntry(c,UiText.get("update_123_title_001"),UiText.get("update_history_007") );
        updateEntry(c,UiText.get("update_122_title_001"),UiText.get("update_history_008") );
        updateEntry(c,UiText.get("update_121_title_001"),UiText.get("update_history_009"));
        updateEntry(c,UiText.get("update_120_title_001"),UiText.get("update_history_010"));
        updateEntry(c,UiText.get("update_116_title_001"),UiText.get("update_history_011"));
        updateEntry(c,UiText.get("update_115_title_001"),UiText.get("update_history_012"));
        updateEntry(c,UiText.get("update_114_title_001"),UiText.get("update_history_013"));
        updateEntry(c,UiText.get("update_113_title_001"),UiText.get("update_history_014"));
        updateEntry(c,UiText.get("update_112_title_001"),UiText.get("update_history_015"));
        updateEntry(c,UiText.get("update_111_title_001"),UiText.get("update_history_016"));
        updateEntry(c,UiText.get("update_110_title_001"),UiText.get("update_history_017"));
        updateEntry(c,UiText.get("update_1012_title_001"),UiText.get("update_history_018"));
        updateEntry(c,UiText.get("update_1011_title_001"),UiText.get("update_history_019"));
        updateEntry(c,UiText.get("update_1010_title_001"),UiText.get("update_history_020"));
        updateEntry(c,UiText.get("update_109_title_001"),UiText.get("update_history_021"));
        updateEntry(c,UiText.get("update_108_title_001"),UiText.get("update_history_022"));
        updateEntry(c,UiText.get("update_107_title_001"),UiText.get("update_history_023"));
        updateEntry(c,UiText.get("update_106_title_001"),UiText.get("update_history_024"));
        updateEntry(c,UiText.get("update_105_title_001"),UiText.get("update_history_025"));
        updateEntry(c,UiText.get("update_104_title_001"),UiText.get("update_history_026"));
        updateEntry(c,UiText.get("update_103_title_001"),UiText.get("update_history_027"));
        updateEntry(c,UiText.get("update_102_title_001"),UiText.get("update_history_028"));
        updateEntry(c,UiText.get("update_101_title_001"),UiText.get("update_history_029"));
        updateEntry(c,UiText.get("update_100_title_001"),UiText.get("update_history_030"));
        updateEntry(c,UiText.get("update_010_test_title_001"),UiText.get("main_058"));
        ScrollView scroll=new ScrollView(this);scroll.addView(c);new ToolDialog(this).setTitle(getString(R.string.updates_title)).setView(scroll).setPositiveButton(UiText.get("action_close"),null).show();
    }
    public void clearCache(Runnable done){
        for(MainActivity owner:new ArrayList<>(ACTIVE))if(owner!=this&&!owner.dead){owner.cacheGeneration++;owner.searchVersion++;owner.data=null;owner.notice="";if(owner.earthquakes!=null)owner.earthquakes.call("clear");}
        cacheGeneration++;searchVersion++;data=null;notice="";
        deferMapReload=tab==3;
        if(maps!=null){maps.call("destroy");maps=null;screens.remove(3);}
        boolean filesCleared=store.clearCache() & (earthquakes==null?clearEarthquakeFiles():Boolean.TRUE.equals(earthquakes.call("clear")));
        android.webkit.WebView web=new android.webkit.WebView(this);web.clearCache(true);web.clearHistory();web.destroy();
        android.webkit.WebStorage.getInstance().deleteAllData();
        android.webkit.CookieManager.getInstance().removeAllCookies(removed->{android.webkit.CookieManager.getInstance().flush();if(!dead){refreshScreen();done.run();toast(filesCleared?UiText.get("main_059"):UiText.get("main_060"));}});
    }
    public long cacheBytes(){long total=directoryBytes(getCacheDir());java.io.File[] files=getFilesDir().listFiles();if(files!=null)for(java.io.File f:files)if(f.getName().startsWith("forecast_")&&f.getName().endsWith(".json"))total+=directoryBytes(f);total+=directoryBytes(new java.io.File(getFilesDir(),"earthquake_cache"));return total;}
    private long directoryBytes(java.io.File file){if(file==null||!file.exists())return 0;if(file.isFile())return file.length();long total=0;java.io.File[] children=file.listFiles();if(children!=null)for(java.io.File child:children)total+=directoryBytes(child);return total;}
    public String cacheBytesText(){long n=cacheBytes();if(n<1024)return n+" B";if(n<1024*1024)return String.format(Locale.ROOT,"%.1f KiB",n/1024.0);if(n<1024L*1024*1024)return String.format(Locale.ROOT,"%.2f MiB",n/1024.0/1024.0);return String.format(Locale.ROOT,"%.2f GiB",n/1024.0/1024.0/1024.0);}
    public void about() {
        LinearLayout content=column();content.setPadding(dp(22),dp(8),dp(22),dp(10));
        TextParagraphs.append(this,content,getString(R.string.about_version,versionName()),14,INK);
        TextParagraphs.append(this,content,UiText.get("main_061"),14,INK);
        TextParagraphs.append(this,content,UiText.get("main_062"),14,INK);
        TextParagraphs.append(this,content,getString(R.string.city_sources),14,INK);
        TextParagraphs.append(this,content,getString(R.string.todo_local_note),14,INK);
        FeatureAccess.earthquakeInfo(this,content,false);
        link(content,UiText.get("main_063"),"https://open-meteo.com/");link(content,UiText.get("main_064"),"https://open-meteo.com/en/terms");link(content,UiText.get("main_065"),"https://www.geonames.org/");link(content,getString(R.string.city_sources_link),"https://github.com/AdybirdZ/daidai-toolbox/blob/main/docs/CITY-CATALOG.md");link(content,UiText.get("main_066"),"https://creativecommons.org/licenses/by/4.0/");link(content,UiText.get("main_067"),"https://www.nmc.cn/");link(content,UiText.get("main_068"),"https://stjarnhimlen.se/comp/ppcomp.html");link(content,UiText.get("main_069"),"https://developer.android.google.cn/reference/android/location/GnssStatus");link(content,UiText.get("main_070"),"https://developer.android.google.cn/develop/sensors-and-location/sensors/sensors_overview");link(content,UiText.get("main_071"),"https://www.wikidata.org/wiki/Wikidata:Licensing");
        FeatureAccess.earthquakeInfo(this,content,true);
        gap(content,10);content.addView(text(UiText.get("main_072"),17,TITLE,true));gap(content,10);content.addView(text(UiText.get("main_073"),14,MUTED,false));gap(content,12);link(content,UiText.get("main_074"),"https://github.com/anyin233/weather-fighter-global");link(content,UiText.get("main_075"),"https://github.com/ShiGuangSchedule/shiguangschedule");link(content,UiText.get("main_076"),"https://github.com/AdybirdZ/daidai-toolbox");
        ScrollView scroll=new ScrollView(this);scroll.addView(content);new ToolDialog(this).setTitle(getString(R.string.about_title)).setView(scroll).setPositiveButton(UiText.get("action_close"),null).show();
    }
    public void link(LinearLayout parent,String label,String url) {TextView t=text(label+" ↗",13,BLUE,false);markLink(t);t.setMinHeight(dp(44));t.setPadding(dp(12),dp(7),dp(12),dp(7));t.setGravity(Gravity.CENTER_VERTICAL);tap(t,Color.TRANSPARENT,8,()->open(url));parent.addView(t,new LinearLayout.LayoutParams(-2,-2));gap(parent,8);}
    public void open(String url) {try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(ActivityNotFoundException e){toast(UiText.get("main_077"));}}
    public void hideKeyboard() {View focused=getCurrentFocus();if(focused!=null){((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(focused.getWindowToken(),0);focused.clearFocus();}}
    public void toast(String message) {Toast toast=new Toast(this);LinearLayout box=row();box.setPadding(dp(16),dp(12),dp(16),dp(12));box.setBackground(shape(0xFF26364D,20));ImageView icon=new ImageView(this);icon.setImageResource(org.simpleweather.app.R.drawable.ic_launcher);box.addView(icon,lp(28,28));gapHorizontal(box,10);TextView label=text(TransientText.notice(message),16,WHITE,false);label.setMaxWidth(Math.max(dp(120),Math.min(dp(260),getResources().getDisplayMetrics().widthPixels-dp(100))));box.addView(label);toast.setView(box);toast.setDuration(Toast.LENGTH_SHORT);toast.setGravity(Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL,0,dp(100));toast.show();}
    @Override public void onConfigurationChanged(android.content.res.Configuration config){
        super.onConfigurationChanged(config);
        // Keep the existing screen, session and open dialogs; only remeasure the viewport.
        if(pageScroll!=null){android.view.ViewGroup.LayoutParams fit=pageScroll.getLayoutParams();fit.width=Math.min(getResources().getDisplayMetrics().widthPixels,dp(620));pageScroll.setLayoutParams(fit);}
        if(body!=null)body.requestLayout();if(gps!=null)gps.call("configurationChanged");
    }
    @Override protected void onSaveInstanceState(Bundle state){
        if(randomChoice!=null)randomChoice.call("flush");state.putInt("toolbox_language_settings",reopenSettings);state.putInt("toolbox_tab",tab);state.putInt("toolbox_navigation",navigationOffset);
        state.putInt("toolbox_scroll",pageScroll==null?0:pageScroll.getScrollY());state.putLong("toolbox_astro_time",astronomyTime.toEpochMilli());
        if(earthquakes!=null)earthquakes.call("saveState",state);UiSession.capture(this,state);super.onSaveInstanceState(state);
    }
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);LauncherScreen.openRequested(this,intent);if(intent.hasExtra("last_tab")){switchTab(intent.getIntExtra("last_tab",-1));}if(intent.getBooleanExtra("open_todo",false)){preferences.edit().putBoolean("tab_show_7",true).apply();switchTab(7);}if(intent.getBooleanExtra("open_location",false)){preferences.edit().putBoolean("tab_show_2",true).apply();switchTab(2);}}
    @Override public void onBackPressed() {if(picking){hideKeyboard();switchTab(tab);}else if(tab!=-1)switchTab(-1);else super.onBackPressed();}
    @Override protected void onPause(){if(todo!=null)todo.call("pause");if(randomChoice!=null)randomChoice.call("pause");if(earthquakes!=null)earthquakes.call("pause");if(gps!=null)gps.call("pause");if(maps!=null)maps.call("pause");ModuleLaunch.pause(this);super.onPause();}
    @Override protected void onResume(){super.onResume();if(appIcons!=null)appIcons.refresh();TodoScheduler.refresh(this,true);ScheduleNotifier.refresh(this,true);if(todo!=null&&tab==7)todo.call("resume");if(randomChoice!=null&&tab==6)randomChoice.call("resume");if(gps!=null&&tab==2)gps.call("resume");if(maps!=null)maps.call("resume");if(earthquakes!=null&&tab==5)earthquakes.call("resume");ModuleLaunch.resume(this);}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){super.onRequestPermissionsResult(request,permissions,grants);if(gps==null&&(request==41||request==44||request==46||request==47))return;if(request==41)gps.call("permissionResult");else if(request==44)gps.call("notificationPermissionResult");else if(request==45&&earthquakes!=null)earthquakes.call("locationPermissionResult");else if(request==46)gps.call("satellitePermissionResult");else if(request==47)gps.call("placesPermissionResult");else if(request==48&&tab==2)refreshScreen();else if(request==49)TodoScheduler.refresh(this,true);}
    @Override protected void onActivityResult(int request,int result,Intent intent){super.onActivityResult(request,result,intent);if(request==SettingsActivity.REQUEST){if(reloadSharedData){reloadSharedData=false;store=new WeatherStore(this);catalog=store.catalog();if(!catalog.isEmpty())city=store.selected(catalog.get(0));data=store.cached(city);}if(!java.util.Objects.equals(activityLanguage,AppLanguage.choice(this))){recreate();return;}pendingScrollY=pageScroll==null?0:pageScroll.getScrollY();applyKeepScreenAwake();reloadBackground();return;}if(apps!=null&&apps.result(request,result,intent))return;if(request==AppIconSettings.PICK||request==AppIconSettings.CROP){appIcons.result(request,result,intent);return;}if(request==StatusTransfer.EXPORT||request==StatusTransfer.IMPORT){if(gps==null)gps=screen(2);if(gps!=null)gps.call("fileResult",request,result,intent);return;}if(request==ConfigurationTransfer.EXPORT||request==ConfigurationTransfer.IMPORT){configuration.result(request,result,intent);return;}if(request==ModuleTransfer.EXPORT||request==ModuleTransfer.IMPORT){moduleTransfer.result(request,result,intent);return;}if(request==42&&result==RESULT_OK&&intent!=null&&intent.getData()!=null){Intent crop=new Intent(this,BackgroundCropActivity.class);crop.setData(intent.getData());crop.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivityForResult(crop,43);}else if(request==43&&result==RESULT_OK)reloadBackground();}
    @Override protected void onDestroy(){ACTIVE.remove(this);UiSession.release(this);dead=true;if(todo!=null)todo.call("destroy");if(randomChoice!=null)randomChoice.call("pause");if(gps!=null)gps.call("prepareNextLaunch");if(earthquakes!=null)earthquakes.call("destroy");if(maps!=null)maps.call("destroy");if(gps!=null)gps.call("destroy");main.removeCallbacksAndMessages(null);workers.shutdownNow();ModuleLaunch.destroy(this);super.onDestroy();}
}










