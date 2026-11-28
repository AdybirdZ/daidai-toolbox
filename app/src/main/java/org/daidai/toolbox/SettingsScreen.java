package org.daidai.toolbox;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Each child dialog retains its parent; selections apply without closing the page. */
public final class SettingsScreen {
    private final MainActivity a;
    private final SharedPreferences prefs;
    public static String[] colorNames(){return new String[]{UiText.get("settings_001"),UiText.get("settings_002"),UiText.get("settings_003"),UiText.get("settings_004"),UiText.get("settings_005"),UiText.get("settings_006")};}
    private static final int[] COLORS={Color.BLACK,0xFF246BCE,0xFF287C60,0xFFB83636,0xFF7852A8,Color.WHITE};

    public SettingsScreen(MainActivity a){this.a=a;prefs=a.preferences;}
    private LinearLayout form(){LinearLayout c=a.column();c.setPadding(a.dp(18),a.dp(8),a.dp(18),a.dp(16));return c;}
    private ScrollView scroll(LinearLayout c){ScrollView s=new ScrollView(a);s.addView(c);return s;}
    private AlertDialog dialog(String title,LinearLayout c){return new ToolDialog(a).setTitle(title).setView(scroll(c)).setNegativeButton(UiText.get("action_back"),null).show();}
    private TextView action(LinearLayout c,String label,Runnable click){TextView b=a.button(label,false,click);c.addView(b);a.gap(c,10);return b;}
    public void show(){LinearLayout c=form();action(c,UiText.get("settings_014"),this::global);String[] names={"weather","weather-map","astronomy","status","earthquake","schedule","random","todo"};Runnable[] actions={this::weather,this::maps,this::astronomy,this::location,a::earthquakeSettings,a::scheduleSettings,a::randomChoiceSettings,a::todoSettings};for(int i=0;i<names.length;i++)if(a.apps.installed(names[i]))action(c,a.apps.title(names[i]),actions[i]);for(String id:AppModules.EMBEDDED)if(a.apps.installed(id))action(c,a.apps.title(id),()->SettingsActivity.open(a,id));action(c,UiText.get("settings_015"),this::about);new ToolDialog(a).setTitle(UiText.get("action_settings")).setView(scroll(c)).setNegativeButton(UiText.get("action_close"),null).show();}

    private void resetDefaults(){new ToolDialog(a).setTitle(a.getString(R.string.settings_reset)).setMessage(a.getString(R.string.settings_reset_description)).setPositiveButton(a.getString(R.string.action_restore),(d,n)->{boolean iconRestored=a.restoreDefaults();ToolDialog.refresh(a);a.toast(a.getString(iconRestored?R.string.settings_reset_done:R.string.app_icon_reset_partial));}).setNegativeButton(a.getString(R.string.action_cancel),null).show();}
    private void global(){LinearLayout c=form();action(c,UiText.get("settings_016"),this::background);action(c,a.getString(R.string.app_icon_title),a::appIconSettings);action(c,UiText.get("settings_017"),this::outline);action(c,a.getString(R.string.theme_color_title),this::textColors);action(c,UiText.get("settings_018"),this::timeDisplay);action(c,UiText.get("language_title"),this::language);action(c,a.getString(R.string.settings_permissions),this::permissions);action(c,a.getString(R.string.launcher_customization),()->new LauncherScreen(a).order());action(c,a.getString(R.string.settings_cache),this::cache);action(c,a.getString(R.string.app_data_manager_title),this::internalData);action(c,a.getString(R.string.settings_reset),this::resetDefaults);action(c,a.getString(R.string.config_export_title),a::exportConfiguration);action(c,a.getString(R.string.config_import_title),a::importConfiguration);action(c,a.getString(R.string.module_transfer_title),this::moduleTransfer);note(c,a.getString(R.string.config_archive_note));dialog(UiText.get("settings_014"),c);}
    private void internalData(){new ModuleDataManager(a).show();}
    private void moduleTransfer(){LinearLayout c=form();for(ModuleRegistry.Module m:ModuleRegistry.ALL){String title=a.getString(a.getResources().getIdentifier(m.title,"string",a.getPackageName()));LinearLayout row=a.row();TextView name=a.text(title,15,a.INK,true);row.addView(name,new LinearLayout.LayoutParams(0,-2,1));a.gapHorizontal(row,8);row.addView(a.button(a.getString(R.string.module_export_short),false,()->a.exportModule(m.id)),a.lp(74,48));a.gapHorizontal(row,8);row.addView(a.button(a.getString(R.string.module_import_short),false,()->a.importModule(m.id)),a.lp(74,48));c.addView(row);a.gap(c,8);}note(c,a.getString(R.string.module_transfer_note));dialog(a.getString(R.string.module_transfer_title),c);}
    private void keepScreenAwake(){LinearLayout c=form();Switch toggle=new Switch(a);toggle.setText(a.getString(R.string.keep_screen_awake_title));toggle.setTextSize(15);toggle.setChecked(prefs.getBoolean("keep_screen_awake",false));toggle.setOnCheckedChangeListener((v,on)->{prefs.edit().putBoolean("keep_screen_awake",on).apply();a.applyKeepScreenAwake();});c.addView(toggle,a.lp(-1,58));note(c,a.getString(R.string.keep_screen_awake_note));dialog(a.getString(R.string.keep_screen_awake_title),c);}
    public void settingsPath(boolean language){show();global();if(language)language();}
    private void language(){
        LinearLayout c=form();RadioGroup group=new RadioGroup(a);
        int[] labels={R.string.language_system,R.string.language_chinese,R.string.language_english};
        for(int i=0;i<labels.length;i++){RadioButton b=new RadioButton(a);b.setId(9200+i);b.setText(a.getString(labels[i]));b.setTextSize(15);group.addView(b,a.lp(-1,56));}
        group.check(9200+Arrays.asList(LanguagePolicy.CHOICES).indexOf(AppLanguage.choice(a)));
        group.setOnCheckedChangeListener((g,id)->a.changeLanguage(LanguagePolicy.CHOICES[id-9200]));c.addView(group);
        note(c,a.getString(R.string.language_note));dialog(a.getString(R.string.language_title),c);
    }
    private void timeDisplay(){singleChoice(UiText.get("settings_018"),a.getString(R.string.time_display_note),new String[]{UiText.get("settings_020"),UiText.get("earthquake_info_007"),UiText.get("earthquake_info_008")},prefs.getInt("global_time_mode",0),n->{prefs.edit().putInt("global_time_mode",n).putInt("quake_timezone",n).apply();a.refreshScreen();});}
    private void background(){LinearLayout c=form();action(c,UiText.get("settings_021"),a::selectBackground);action(c,UiText.get("settings_022"),a::resetBackground);TextView value=a.text(UiText.get("settings_023")+prefs.getInt("background_brightness",20),14,a.INK,true);c.addView(value);SeekBar brightness=new SeekBar(a);brightness.setContentDescription(UiText.get("settings_025"));brightness.setMax(100);brightness.setProgress(prefs.getInt("background_brightness",20));brightness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int n,boolean user){if(user){prefs.edit().putInt("background_brightness",n).apply();value.setText(UiText.get("settings_023")+n);}}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){a.refreshScreen();}});c.addView(brightness,a.lp(-1,48));note(c,UiText.get("settings_024"));dialog(UiText.get("settings_016"),c);}
    private void outline(){LinearLayout c=form();Switch toggle=new Switch(a);toggle.setText(UiText.get("settings_026"));toggle.setTextSize(15);toggle.setChecked(prefs.getBoolean("outline",false));toggle.setOnCheckedChangeListener((v,on)->{prefs.edit().putBoolean("outline",on).apply();a.refreshScreen();});c.addView(toggle,a.lp(-1,54));a.gap(c,10);action(c,UiText.get("settings_027"),this::colors);c.addView(a.text(UiText.get("settings_028"),13,a.MUTED,false));dialog(UiText.get("settings_017"),c);}
    private void colors(){String[] labels=Arrays.copyOf(colorNames(),colorNames().length+1);labels[labels.length-1]=UiText.get("color_custom");int current=COLORS.length;for(int n=0;n<COLORS.length;n++)if(a.outlineColor()==COLORS[n])current=n;new ToolDialog(a).setTitle(UiText.get("settings_029")).setSingleChoiceItems(labels,current,(x,n)->{if(n<COLORS.length){prefs.edit().putInt("outline_color",COLORS[n]).apply();a.refreshScreen();}else customColor(()->((AlertDialog)x).getListView().setItemChecked(COLORS.length,true));}).setNegativeButton(UiText.get("action_back"),null).show();}
    private void customColor(Runnable changed){EditText input=new EditText(a);input.setSingleLine(true);input.setHint("#000000");input.setText(String.format(Locale.ROOT,"#%06X",a.outlineColor()&0xffffff));input.setSelectAllOnFocus(true);AlertDialog d=new ToolDialog(a).setTitle(UiText.get("color_hex_title")).setView(input).setPositiveButton(UiText.get("action_save"),null).setNegativeButton(UiText.get("action_back"),null).create();d.setOnShowListener(v->d.getButton(-1).setOnClickListener(b->{String s=input.getText().toString().trim();if(!s.matches("#[0-9a-fA-F]{6}")){a.toast(UiText.get("settings_030"));return;}prefs.edit().putInt("outline_color",Color.parseColor(s)).apply();a.refreshScreen();changed.run();a.toast(UiText.get("settings_031"));}));d.show();}
    private void textColors(){LinearLayout c=form();String[] roles={UiColors.BUTTON,UiColors.LINK,UiColors.BODY,UiColors.TITLE,UiColors.CHOICE,UiColors.NOTE};int[] names={R.string.color_role_button,R.string.color_role_link,R.string.color_role_body,R.string.color_role_title,R.string.color_role_choice,R.string.color_role_note};for(int i=0;i<roles.length;i++){final String role=roles[i];final int name=names[i];action(c,a.getString(name),()->ColorPicker.show(a,a.getString(name),UiColors.seed(a,role),ThemeColors.roleDefault(role),true,role.equals(UiColors.NOTE),color->{prefs.edit().putInt(role,color).apply();a.applyTheme();}));}dialog(a.getString(R.string.theme_color_title),c);}
    public void singleChoice(String title,String note,String[] labels,int selected,java.util.function.IntConsumer changed){LinearLayout c=form();RadioGroup choices=new RadioGroup(a);for(int i=0;i<labels.length;i++){RadioButton item=new RadioButton(a);item.setId(7500+i);item.setText(labels[i]);item.setTextSize(15);choices.addView(item,a.lp(-1,52));}choices.check(7500+Math.max(0,Math.min(labels.length-1,selected)));choices.setOnCheckedChangeListener((g,id)->changed.accept(id-7500));c.addView(choices);if(note!=null&&!note.isEmpty())note(c,note);dialog(title,c);}
    public void weather(){FeatureAccess.settings(a,"weather");}
    public void astronomy(){FeatureAccess.settings(a,"astronomy");}

    public void location(){FeatureAccess.settings(a,"status");}

    private void permissions(){
        LinearLayout c=form();
        action(c,a.getString(R.string.permissions_app_settings),()->openSystemSettings(appDetails()));
        if(a.apps.installed("todo")||a.apps.installed("schedule")||a.apps.installed("status")||a.apps.installed("music")||a.apps.installed("messaging"))action(c,a.getString(R.string.permissions_notification_settings),()->TodoScheduler.notificationSettings(a));
        if(android.os.Build.VERSION.SDK_INT>=31&&(a.apps.installed("todo")||a.apps.installed("schedule")))action(c,a.getString(R.string.permissions_alarm_settings),()->TodoScheduler.exactSettings(a));
        if(a.apps.installed("status")||a.apps.installed("earthquake"))action(c,a.getString(R.string.permissions_location_settings),()->openSystemSettings(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)));
        if(a.apps.installed("messaging"))action(c,a.getString(R.string.permissions_bluetooth_settings),()->openSystemSettings(new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)));
        action(c,a.getString(R.string.permissions_shortcut_settings),this::shortcutSettings);
        action(c,a.getString(R.string.permissions_battery_settings),this::batteryOptimization);
        action(c,a.getString(R.string.keep_screen_awake_title),this::keepScreenAwake);
        dialog(a.getString(R.string.settings_permissions),c);
    }
    private Intent appDetails(){return new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:"+a.getPackageName()));}
    private void shortcutSettings(){
        LinearLayout c=form();
        action(c,a.getString(R.string.permissions_open_app_settings),()->openSystemSettings(appDetails()));
        action(c,a.getString(R.string.permissions_open_home_settings),()->{
            Intent home=new Intent(android.provider.Settings.ACTION_HOME_SETTINGS);
            // Launchers may expose shortcut controls here. This is not a portable permission API.
            try{if(home.resolveActivity(a.getPackageManager())!=null)a.startActivity(home);else openSystemSettings(appDetails());}
            catch(Exception unavailable){openSystemSettings(appDetails());}
        });
        note(c,a.getString(R.string.permissions_shortcut_note));
        dialog(a.getString(R.string.permissions_shortcut_settings),c);
    }
    private void openSystemSettings(Intent intent){
        try{a.startActivity(intent);}catch(Exception e){a.toast(a.getString(R.string.system_settings_unavailable));}
    }
    private void batteryOptimization(){
        android.os.PowerManager power=(android.os.PowerManager)a.getSystemService(Context.POWER_SERVICE);
        boolean exempt=power!=null&&power.isIgnoringBatteryOptimizations(a.getPackageName());
        LinearLayout c=form();
        action(c,a.getString(R.string.battery_optimization_open),()->{
            try{a.startActivity(new Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));}
            catch(Exception e){try{a.startActivity(appDetails());}catch(Exception unavailable){a.toast(a.getString(R.string.battery_optimization_unavailable));}}
        });
        note(c,a.getString(exempt?R.string.battery_optimization_off:R.string.battery_optimization_on)+"\n\n"+a.getString(R.string.battery_optimization_note));
        dialog(a.getString(R.string.battery_optimization_title),c);
    }

    public void maps(){FeatureAccess.settings(a,"weather-map");}
    private void cache(){LinearLayout c=form();TextView usage=a.text(UiText.get("settings_096")+a.cacheBytesText(),14,a.BLUE,true);c.addView(usage);a.gap(c,12);action(c,UiText.get("settings_098"),()->new ToolDialog(a).setTitle(UiText.get("settings_098")).setMessage(UiText.get("settings_099")).setPositiveButton(UiText.get("earthquake_106"),(d,n)->a.clearCache(()->usage.setText(UiText.get("settings_096")+a.cacheBytesText()))).setNegativeButton(UiText.get("action_cancel"),null).show());note(c,UiText.get("settings_095"));note(c,UiText.get("settings_097"));dialog(UiText.get("settings_cache"),c);}
    private void about(){LinearLayout c=form();action(c,UiText.get("settings_100"),a::updates);action(c,UiText.get("about_title"),a::about);c.addView(a.text(a.getString(R.string.app_name)+" "+a.versionName(),14,a.MUTED,false));dialog(UiText.get("settings_015"),c);}
    private void note(LinearLayout c,String text){View last=c.getChildCount()==0?null:c.getChildAt(c.getChildCount()-1);if(last!=null&&last.getClass()==View.class){ViewGroup.LayoutParams p=last.getLayoutParams();p.height=a.dp(TextParagraphs.GAP_DP);last.setLayoutParams(p);}else a.gap(c,TextParagraphs.GAP_DP);TextParagraphs.append(a,c,text,13,a.MUTED);}
    private void defaultOrder(LinearLayout c,String[] labels,List<Integer> ids){List<String> names=new ArrayList<>();for(int id:ids)names.add(labels[id]);note(c,a.getString(R.string.default_order,String.join(a.getString(R.string.order_separator),names)));}
    private void order(boolean weather){order(weather?"weather":"tab");}
    public void earthquakeOrder(){order("quake");}
    private void order(String key){
        boolean weather=key.equals("weather"),astro=key.equals("astro"),quake=key.equals("quake"),status=key.equals("status");
        String[] labels=status?ScreenLabels.GpsScreensectionNames():quake?ScreenLabels.EarthquakeScreensectionNames():astro?ScreenLabels.AstronomyScreensectionNames():weather?a.weatherNames():a.tabNames();List<Integer> ids=status?DisplayOrder.status(prefs.getString(key+"_order","")):DisplayOrder.parse(prefs.getString(key+"_order",""),labels.length);LinearLayout c=form();LinearLayout list=a.column();c.addView(list);Runnable[] render=new Runnable[1];render[0]=()->{list.removeAllViews();for(int pos=0;pos<ids.size();pos++){final int index=pos,id=ids.get(pos);LinearLayout r=a.row();CheckBox check=new CheckBox(a);check.setText(labels[id]);check.setTextSize(14);check.setChecked(prefs.getBoolean(key+"_show_"+id,status||quake||astro||weather||id<3||id==5||id==6||id==7));check.setOnCheckedChangeListener((v,on)->{if(!status&&!weather&&!astro&&!quake&&!on&&a.visibleTabs().size()<=1){check.setChecked(true);a.toast(UiText.get("settings_106"));return;}prefs.edit().putBoolean(key+"_show_"+id,on).apply();a.refreshScreen();});r.addView(check,new LinearLayout.LayoutParams(0,-2,1));TextView up=a.button("↑",false,()->{if(index>0){Collections.swap(ids,index,index-1);prefs.edit().putString(key+"_order",DisplayOrder.encode(ids)).apply();a.refreshScreen();render[0].run();}});up.setContentDescription(labels[id]+UiText.get("gps_101"));up.setPadding(0,0,0,0);a.gapHorizontal(r,8);r.addView(up,a.lp(42,48));a.gapHorizontal(r,8);TextView down=a.button("↓",false,()->{if(index+1<ids.size()){Collections.swap(ids,index,index+1);prefs.edit().putString(key+"_order",DisplayOrder.encode(ids)).apply();a.refreshScreen();render[0].run();}});down.setContentDescription(labels[id]+UiText.get("gps_102"));down.setPadding(0,0,0,0);r.addView(down,a.lp(42,48));list.addView(r);a.gap(list,8);}};render[0].run();note(c,UiText.get(weather?"order_weather_note":key.equals("tab")?"order_tabs_note":"order_sections_note"));defaultOrder(c,labels,status?DisplayOrder.status(""):DisplayOrder.parse("",labels.length));dialog(status?UiText.get("settings_107"):quake?UiText.get("settings_108"):astro?UiText.get("settings_109"):weather?UiText.get("settings_110"):UiText.get("settings_111"),c);
    }
}

