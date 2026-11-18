package org.simpleweather.app;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Each child dialog retains its parent; selections apply without closing the page. */
public final class WeatherSettings {
    private final MainActivity a;
    private final android.content.SharedPreferences prefs;
    private static final long[] INTERVALS={1000,5000,10000,30000,60000,180000,300000,600000};
    public WeatherSettings(MainActivity a){this.a=a;prefs=a.preferences;}
    public void show(){weather();}
    private LinearLayout form(){LinearLayout c=a.column();c.setPadding(a.dp(18),a.dp(8),a.dp(18),a.dp(16));return c;}
    private ScrollView scroll(LinearLayout c){ScrollView s=new ScrollView(a);s.addView(c);return s;}
    private AlertDialog dialog(String title,LinearLayout c){return new ToolDialog(a).setTitle(title).setView(scroll(c)).setNegativeButton(UiText.get("action_back"),null).show();}
    private TextView action(LinearLayout c,String label,Runnable click){TextView b=a.button(label,false,click);c.addView(b);a.gap(c,10);return b;}
    public void weather(){LinearLayout c=form();action(c,UiText.get("earthquake_101"),this::displayOrder);action(c,UiText.get("settings_032"),this::legend);dialog(UiText.get("settings_033"),c);}
    private void legend(){
        LinearLayout c=form();
        int[] supported=WeatherData.supportedCodes();int[] codes=Arrays.copyOf(supported,supported.length+1);codes[codes.length-1]=-1;
        for(int code:codes){
            LinearLayout r=a.row();r.addView(new WeatherIcon(a,code,true),a.lp(50,50));r.addView(new WeatherIcon(a,code,false),a.lp(50,50));
            r.addView(a.text(WeatherData.condition(code),14,a.INK,false),new LinearLayout.LayoutParams(0,-2,1));
            TextView number=a.text(code>=0?Integer.toString(code):"—",13,a.MUTED,false);number.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);number.setPadding(a.dp(4),0,0,0);r.addView(number,a.lp(32,50));
            c.addView(r);a.gap(c,6);
        }
        a.link(c,UiText.get("settings_052"),"https://open-meteo.com/en/docs#weather_variable_documentation");
        a.link(c,UiText.get("settings_053"),"https://codes.wmo.int/bufr4/codeflag/0-20-003?_view=all");
        note(c,UiText.get("settings_050"));note(c,UiText.get("settings_051"));dialog(UiText.get("settings_054"),c);
    }
    private void note(LinearLayout c,String text){View last=c.getChildCount()==0?null:c.getChildAt(c.getChildCount()-1);if(last!=null&&last.getClass()==View.class){ViewGroup.LayoutParams p=last.getLayoutParams();p.height=a.dp(TextParagraphs.GAP_DP);last.setLayoutParams(p);}else a.gap(c,TextParagraphs.GAP_DP);TextParagraphs.append(a,c,text,13,a.MUTED);}
    private void defaultOrder(LinearLayout c,String[] labels,List<Integer> ids){List<String> names=new ArrayList<>();for(int id:ids)names.add(labels[id]);note(c,a.getString(R.string.default_order,String.join(a.getString(R.string.order_separator),names)));}
    private void displayOrder(){order("weather");}
    private void order(boolean weather){order(weather?"weather":"tab");}
    private void order(String key){
        boolean weather=key.equals("weather"),astro=key.equals("astro"),quake=key.equals("quake"),status=key.equals("status");
        String[] labels=status?ScreenLabels.GpsScreensectionNames():quake?ScreenLabels.EarthquakeScreensectionNames():astro?ScreenLabels.AstronomyScreensectionNames():weather?a.weatherNames():a.tabNames();List<Integer> ids=status?DisplayOrder.status(prefs.getString(key+"_order","")):DisplayOrder.parse(prefs.getString(key+"_order",""),labels.length);LinearLayout c=form();LinearLayout list=a.column();c.addView(list);Runnable[] render=new Runnable[1];render[0]=()->{list.removeAllViews();for(int pos=0;pos<ids.size();pos++){final int index=pos,id=ids.get(pos);LinearLayout r=a.row();CheckBox check=new CheckBox(a);check.setText(labels[id]);check.setTextSize(14);check.setChecked(prefs.getBoolean(key+"_show_"+id,status||quake||astro||weather||id<3||id==5||id==6||id==7));check.setOnCheckedChangeListener((v,on)->{if(!status&&!weather&&!astro&&!quake&&!on&&a.visibleTabs().size()<=1){check.setChecked(true);a.toast(UiText.get("settings_106"));return;}prefs.edit().putBoolean(key+"_show_"+id,on).apply();a.refreshScreen();});r.addView(check,new LinearLayout.LayoutParams(0,-2,1));TextView up=a.button("↑",false,()->{if(index>0){Collections.swap(ids,index,index-1);prefs.edit().putString(key+"_order",DisplayOrder.encode(ids)).apply();a.refreshScreen();render[0].run();}});up.setContentDescription(labels[id]+UiText.get("gps_101"));up.setPadding(0,0,0,0);a.gapHorizontal(r,8);r.addView(up,a.lp(42,48));a.gapHorizontal(r,8);TextView down=a.button("↓",false,()->{if(index+1<ids.size()){Collections.swap(ids,index,index+1);prefs.edit().putString(key+"_order",DisplayOrder.encode(ids)).apply();a.refreshScreen();render[0].run();}});down.setContentDescription(labels[id]+UiText.get("gps_102"));down.setPadding(0,0,0,0);r.addView(down,a.lp(42,48));list.addView(r);a.gap(list,8);}};render[0].run();note(c,UiText.get(weather?"order_weather_note":key.equals("tab")?"order_tabs_note":"order_sections_note"));defaultOrder(c,labels,status?DisplayOrder.status(""):DisplayOrder.parse("",labels.length));dialog(status?UiText.get("settings_107"):quake?UiText.get("settings_108"):astro?UiText.get("settings_109"):weather?UiText.get("settings_110"):UiText.get("settings_111"),c);
    }
}
