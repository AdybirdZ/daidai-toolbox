package org.daidai.toolbox;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Each child dialog retains its parent; selections apply without closing the page. */
public final class AstronomySettings {
    private final MainActivity a;
    private final android.content.SharedPreferences prefs;
    private static final long[] INTERVALS={1000,5000,10000,30000,60000,180000,300000,600000};
    public AstronomySettings(MainActivity a){this.a=a;prefs=a.preferences;}
    public void show(){astronomy();}
    private LinearLayout form(){LinearLayout c=a.column();c.setPadding(a.dp(18),a.dp(8),a.dp(18),a.dp(16));return c;}
    private ScrollView scroll(LinearLayout c){ScrollView s=new ScrollView(a);s.addView(c);return s;}
    private AlertDialog dialog(String title,LinearLayout c){return new ToolDialog(a).setTitle(title).setView(scroll(c)).setNegativeButton(UiText.get("action_back"),null).show();}
    private TextView action(LinearLayout c,String label,Runnable click){TextView b=a.button(label,false,click);c.addView(b);a.gap(c,10);return b;}
    private void astronomy(){LinearLayout c=form();action(c,UiText.get("earthquake_101"),this::displayOrder);action(c,UiText.get("settings_034"),this::astronomyStars);action(c,UiText.get("settings_035"),this::astronomyCoordinates);action(c,UiText.get("settings_036"),this::astronomyOrientation);dialog(UiText.get("settings_037"),c);}
    private void astronomyOrientation(){String[] labels={UiText.get("settings_038"),UiText.get("settings_039")};int current=prefs.getString("astro_orientation_reference","camera").equals("top")?1:0;new ToolDialog(a).setTitle(UiText.get("settings_036")).setSingleChoiceItems(labels,current,(d,n)->{prefs.edit().putString("astro_orientation_reference",n==0?"camera":"top").apply();a.refreshScreen();}).setNegativeButton(UiText.get("action_back"),null).show();}
    private void astronomyCoordinates(){String[] labels={UiText.get("settings_040"),UiText.get("settings_041"),UiText.get("astronomy_169")};int current=prefs.getInt("astro_coordinates",0);new ToolDialog(a).setTitle(UiText.get("settings_035")).setSingleChoiceItems(labels,Math.max(0,Math.min(2,current)),(d,n)->{prefs.edit().putInt("astro_coordinates",n).apply();a.refreshScreen();}).setNegativeButton(UiText.get("action_back"),null).show();}
    private void astronomyStars(){
        LinearLayout c=form();
        RadioGroup modes=new RadioGroup(a);String[] names={UiText.get("settings_043"),UiText.get("settings_044")};
        for(int i=0;i<2;i++){RadioButton b=new RadioButton(a);b.setId(710+i);b.setText(names[i]);b.setTextSize(15);modes.addView(b,a.lp(-1,52));}
        modes.check(prefs.getInt("astro_visibility_mode",0)==1?711:710);c.addView(modes);a.gap(c,12);
        LinearLayout manual=a.column(),magnitude=a.column();
        manual.addView(a.text(UiText.get("settings_045"),16,a.INK,true));
        for(Astronomy.Star star:Astronomy.starsByMagnitude())skyToggle(manual,UiText.label(star.name)+" · "+SkyVisibility.magnitude(star.mag),"astro_star_"+star.name);
        a.gap(manual,12);manual.addView(a.text(UiText.get("settings_046"),16,a.INK,true));for(Astronomy.Planet planet:Astronomy.planets(java.time.Instant.now()))skyToggle(manual,UiText.label(planet.name),"astro_planet_"+planet.name);
        a.gap(manual,12);manual.addView(a.text(UiText.get("settings_047"),16,a.INK,true));for(Astronomy.Star object:Astronomy.DEEP_SKY)skyToggle(manual,UiText.label(object.name)+" · "+SkyVisibility.magnitude(object.mag),"astro_deep_"+object.name);
        TextView value=a.text("",16,a.INK,true);magnitude.addView(value);SeekBar slider=new SeekBar(a);slider.setMax(100);slider.setKeyProgressIncrement(1);slider.setProgress(Math.round((SkyVisibility.limit(prefs.getFloat("astro_magnitude_limit",6))+2)*10));
        Runnable label=()->{String text=String.format(Locale.ROOT,UiText.get("settings_048"),(slider.getProgress()-20)/10f);value.setText(text);slider.setContentDescription(text);};label.run();
        Runnable repaint=()->{if(!a.isFinishing()&&!a.isDestroyed())a.refreshScreen();};
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int n,boolean user){label.run();if(user){prefs.edit().putFloat("astro_magnitude_limit",(n-20)/10f).apply();b.removeCallbacks(repaint);b.postDelayed(repaint,120);}}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){b.removeCallbacks(repaint);repaint.run();}});magnitude.addView(slider,a.lp(-1,56));
        magnitude.addView(a.text(UiText.get("settings_049"),13,a.MUTED,false));
        boolean byMagnitude=prefs.getInt("astro_visibility_mode",0)==1;manual.setVisibility(byMagnitude?View.GONE:View.VISIBLE);magnitude.setVisibility(byMagnitude?View.VISIBLE:View.GONE);c.addView(manual);c.addView(magnitude);note(c,UiText.get("settings_042"));
        modes.setOnCheckedChangeListener((g,id)->{boolean on=id==711;prefs.edit().putInt("astro_visibility_mode",on?1:0).apply();manual.setVisibility(on?View.GONE:View.VISIBLE);magnitude.setVisibility(on?View.VISIBLE:View.GONE);a.refreshScreen();});dialog(UiText.get("settings_034"),c);
    }
    private void skyToggle(LinearLayout c,String label,String key){CheckBox check=new CheckBox(a);check.setText(label);check.setTextSize(14);check.setChecked(prefs.getBoolean(key,true));check.setOnCheckedChangeListener((v,on)->{prefs.edit().putBoolean(key,on).apply();a.refreshScreen();});c.addView(check,a.lp(-1,52));}
    private void note(LinearLayout c,String text){View last=c.getChildCount()==0?null:c.getChildAt(c.getChildCount()-1);if(last!=null&&last.getClass()==View.class){ViewGroup.LayoutParams p=last.getLayoutParams();p.height=a.dp(TextParagraphs.GAP_DP);last.setLayoutParams(p);}else a.gap(c,TextParagraphs.GAP_DP);TextParagraphs.append(a,c,text,13,a.MUTED);}
    private void defaultOrder(LinearLayout c,String[] labels,List<Integer> ids){List<String> names=new ArrayList<>();for(int id:ids)names.add(labels[id]);note(c,a.getString(R.string.default_order,String.join(a.getString(R.string.order_separator),names)));}
    private void displayOrder(){order("astro");}
    private void order(boolean weather){order(weather?"weather":"tab");}
    private void order(String key){
        boolean weather=key.equals("weather"),astro=key.equals("astro"),quake=key.equals("quake"),status=key.equals("status");
        String[] labels=status?ScreenLabels.GpsScreensectionNames():quake?ScreenLabels.EarthquakeScreensectionNames():astro?ScreenLabels.AstronomyScreensectionNames():weather?a.weatherNames():a.tabNames();List<Integer> ids=status?DisplayOrder.status(prefs.getString(key+"_order","")):DisplayOrder.parse(prefs.getString(key+"_order",""),labels.length);LinearLayout c=form();LinearLayout list=a.column();c.addView(list);Runnable[] render=new Runnable[1];render[0]=()->{list.removeAllViews();for(int pos=0;pos<ids.size();pos++){final int index=pos,id=ids.get(pos);LinearLayout r=a.row();CheckBox check=new CheckBox(a);check.setText(labels[id]);check.setTextSize(14);check.setChecked(prefs.getBoolean(key+"_show_"+id,status||quake||astro||weather||id<3||id==5||id==6||id==7));check.setOnCheckedChangeListener((v,on)->{if(!status&&!weather&&!astro&&!quake&&!on&&a.visibleTabs().size()<=1){check.setChecked(true);a.toast(UiText.get("settings_106"));return;}prefs.edit().putBoolean(key+"_show_"+id,on).apply();a.refreshScreen();});r.addView(check,new LinearLayout.LayoutParams(0,-2,1));TextView up=a.button("↑",false,()->{if(index>0){Collections.swap(ids,index,index-1);prefs.edit().putString(key+"_order",DisplayOrder.encode(ids)).apply();a.refreshScreen();render[0].run();}});up.setContentDescription(labels[id]+UiText.get("gps_101"));up.setPadding(0,0,0,0);a.gapHorizontal(r,8);r.addView(up,a.lp(42,48));a.gapHorizontal(r,8);TextView down=a.button("↓",false,()->{if(index+1<ids.size()){Collections.swap(ids,index,index+1);prefs.edit().putString(key+"_order",DisplayOrder.encode(ids)).apply();a.refreshScreen();render[0].run();}});down.setContentDescription(labels[id]+UiText.get("gps_102"));down.setPadding(0,0,0,0);r.addView(down,a.lp(42,48));list.addView(r);a.gap(list,8);}};render[0].run();note(c,UiText.get(weather?"order_weather_note":key.equals("tab")?"order_tabs_note":"order_sections_note"));defaultOrder(c,labels,status?DisplayOrder.status(""):DisplayOrder.parse("",labels.length));dialog(status?UiText.get("settings_107"):quake?UiText.get("settings_108"):astro?UiText.get("settings_109"):weather?UiText.get("settings_110"):UiText.get("settings_111"),c);
    }
}
