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

public final class WeatherScreen {
 private final MainActivity a;
 public WeatherScreen(MainActivity a){this.a=a;}
    private void section(LinearLayout parent,String name,String aside) {
        LinearLayout r=a.row();r.addView(a.text(name,17,a.TITLE,true));a.spacer(r);
        TextView source=a.text(aside,11,a.MUTED,false);source.setPadding(a.dp(5),a.dp(8),0,a.dp(8));source.setMinHeight(a.dp(32));
        a.markLink(source);a.tap(source,Color.TRANSPARENT,6,()->a.open("https://open-meteo.com/"));r.addView(source);parent.addView(r);a.gap(parent,12);
    }
    public void render() {
        a.tab=0;a.picking=false;a.searchVersion++;a.startScreen();
        if(!a.notice.isEmpty()){TextView n=a.text(a.notice,14,0xFF8B6021,false);n.setPadding(a.dp(14),a.dp(12),a.dp(14),a.dp(12));n.setBackground(a.shape(0xFFFFF3DD,14));a.body.addView(n);a.gap(a.body,12);}
        Instant now=Instant.now();int today=a.data==null?0:a.data.todayIndex(now);
        for(int id:DisplayOrder.parse(a.preferences.getString("weather_order",""),MainActivity.WEATHER_NAME_IDS.length)){
            if(!a.preferences.getBoolean("weather_show_"+id,true))continue;
            if(id==0)weatherLocation();
            else if(id==5)footer();
            else if(a.data==null){if(id==1)emptyWeather();}
            else if(id==1)currentWeather(now);
            else if(id==2)hourly(now);
            else if(id==3)daily(now);
            else if(id==4)details(today);
            a.gap(a.body,16);
        }
    }
    private void weatherLocation(){
        LinearLayout cityRow=a.row();
        TextView cityTitle=a.text(a.city.label(),26,a.TITLE,true);cityTitle.setMinHeight(a.dp(48));cityTitle.setGravity(Gravity.CENTER_VERTICAL);cityTitle.setPadding(a.dp(4),0,a.dp(4),0);
        cityRow.addView(cityTitle,new LinearLayout.LayoutParams(0,-2,1));a.gapHorizontal(cityRow,10);
        TextView save=a.button(a.store.isSaved(a.city)?"★":"☆",false,()->{boolean saved=a.store.toggle(a.city);a.toast(saved?UiText.get("main_004"):UiText.get("main_005"));render();});
        save.setTextSize(25);save.setPadding(0,0,0,0);save.setContentDescription(a.store.isSaved(a.city)?UiText.get("main_006"):UiText.get("main_007"));cityRow.addView(save,a.lp(48,48));a.body.addView(cityRow);
        TextView detail=a.text(a.city.detail(),12,a.MUTED,false);detail.setPadding(0,a.dp(5),0,a.dp(14));a.body.addView(detail);
    }
    private void currentWeather(Instant now){
        boolean old=a.data.currentIsOld(now);
        LinearLayout hero=a.column();hero.setPadding(a.dp(22),a.dp(22),a.dp(18),a.dp(20));
        GradientDrawable gradient=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{a.PALE,a.WHITE});gradient.setCornerRadius(a.dp(28));if(a.outlined())gradient.setStroke(a.dp(1),a.outlineColor());hero.setBackground(gradient);a.body.addView(hero,a.lp(-1,-2));
        LinearLayout heroTop=a.row();
        TextView label=a.text(old?UiText.get("main_008"):UiText.get("main_009"),12,a.MUTED,false);heroTop.addView(label);a.spacer(heroTop);
        TextView refresh=a.text(a.loading?UiText.get("main_010"):UiText.get("main_011"),13,a.BLUE,true);UiColors.mark(refresh,UiColors.BUTTON);refresh.setMinHeight(a.dp(40));refresh.setGravity(Gravity.CENTER);refresh.setPadding(a.dp(8),0,a.dp(5),0);a.tap(refresh,Color.TRANSPARENT,10,()->a.load(true));heroTop.addView(refresh);hero.addView(heroTop);
        LinearLayout currentRow=a.row();LinearLayout temperature=a.column();
        TextView number=a.text(WeatherData.degrees(WeatherData.value(a.data.current,"temperature_2m")),54,a.INK,false);number.setTypeface(Typeface.create("sans-serif-light",Typeface.NORMAL));temperature.addView(number);
        int code=WeatherData.code(a.data.current,"weather_code",-1);
        temperature.addView(a.text(WeatherData.condition(code)+UiText.get("main_012")+WeatherData.degrees(WeatherData.value(a.data.current,"apparent_temperature")),14,a.MUTED,false));
        currentRow.addView(temperature,new LinearLayout.LayoutParams(0,-2,1));currentRow.addView(new WeatherIcon(a,code,a.data.current.optInt("is_day",1)==1),a.lp(110,110));hero.addView(currentRow);
        a.gap(hero,12);
        int today=a.data.todayIndex(now);
        hero.addView(a.text(UiText.get("main_013")+WeatherData.degrees(WeatherData.at(a.data.daily,"temperature_2m_max",today))+UiText.get("main_014")+WeatherData.degrees(WeatherData.at(a.data.daily,"temperature_2m_min",today)),14,a.INK,false));a.gap(hero,13);
        ZoneId shownZone=a.displayZone(a.data.zone);Instant weatherAt=a.data.currentTime.atZone(a.data.zone).toInstant();String updated=DateTimeFormatter.ofPattern(UiText.get("main_015")).withZone(shownZone).format(weatherAt);
        hero.addView(a.text(UiText.get("main_016")+updated+" · "+EarthquakeOptions.offset(shownZone,weatherAt.toEpochMilli()),11,a.MUTED,false));
        a.gap(hero,8);
        LinearLayout attribution=a.row();attribution.setGravity(Gravity.RIGHT);TextView source=a.text(a.getString(R.string.source_open_meteo),11,a.MUTED,false);source.setMinHeight(a.dp(42));source.setPadding(a.dp(12),a.dp(9),a.dp(12),a.dp(9));source.setGravity(Gravity.CENTER);a.markLink(source);a.tap(source,Color.TRANSPARENT,8,()->a.open("https://open-meteo.com/"));attribution.addView(source);hero.addView(attribution);


    }
    private void emptyWeather() {
        LinearLayout c=a.card();c.setGravity(Gravity.CENTER);a.gap(c,20);c.addView(new WeatherIcon(a,-1,true),a.lp(110,110));a.gap(c,16);
        c.addView(a.text(a.loading?UiText.get("main_018"):UiText.get("main_019"),19,a.INK,true));a.gap(c,12);
        TextView help=a.text(a.loading?UiText.get("main_020"):UiText.get("main_021"),13,a.MUTED,false);help.setGravity(Gravity.CENTER);c.addView(help);a.gap(c,20);
        if(!a.loading)c.addView(a.button(UiText.get("main_022"),true,()->a.load(true)));a.gap(c,18);a.gap(a.body,24);
    }
    private void hourly(Instant now) {
        LinearLayout c=a.card();section(c,UiText.get("weather_hourly"),"Open-Meteo ↗");
        List<Integer> indices=a.data.nextHours(now,24);
        if(indices.isEmpty()){c.addView(a.text(UiText.get("main_023"),13,a.MUTED,false));return;}
        HorizontalScrollView scroll=new HorizontalScrollView(a);scroll.setHorizontalScrollBarEnabled(false);LinearLayout hours=a.row();hours.setGravity(Gravity.TOP);
        ZoneId shownZone=a.displayZone(a.data.zone);int currentHour=now.atZone(shownZone).getHour();LocalDate today=now.atZone(shownZone).toLocalDate();
        for(int index:indices) {
            String time=WeatherData.textAt(a.data.hourly,"time",index);LinearLayout cell=a.column();cell.setGravity(Gravity.CENTER);cell.setPadding(a.dp(4),a.dp(10),a.dp(4),a.dp(10));
            LocalDateTime date;try{date=LocalDateTime.parse(time).atZone(a.data.zone).withZoneSameInstant(shownZone).toLocalDateTime();}catch(Exception e){continue;}
            boolean current=date.toLocalDate().equals(today)&&date.getHour()==currentHour;
            if(current)cell.setBackground(a.shape(a.buttonSecondaryColor(),16));
            cell.addView(a.text(current?UiText.get("main_024"):String.format(Locale.ROOT,UiText.get("main_025"),date.getHour()),12,current?a.BLUE:a.MUTED,current));a.gap(cell,7);
            cell.addView(new WeatherIcon(a,WeatherData.code(a.data.hourly,"weather_code",index),WeatherData.at(a.data.hourly,"is_day",index)!=0),a.lp(48,48));a.gap(cell,9);
            cell.addView(a.text(WeatherData.degrees(WeatherData.at(a.data.hourly,"temperature_2m",index)),18,a.INK,true));a.gap(cell,10);
            cell.addView(a.text(WeatherData.number(WeatherData.at(a.data.hourly,"precipitation_probability",index))+"%",11,a.BLUE,false));for(int j=0;j<cell.getChildCount();j++)if(cell.getChildAt(j) instanceof TextView)((TextView)cell.getChildAt(j)).setGravity(Gravity.CENTER);hours.addView(cell,a.lp(76,-2));
        }
        scroll.addView(hours);c.addView(scroll);a.gap(c,10);c.addView(a.text(UiText.get("main_026"),11,a.MUTED,false));
    }
    private void daily(Instant now) {
        LinearLayout c=a.card();section(c,UiText.get("weather_daily"),"Open-Meteo ↗");JSONArray times=a.data.daily.optJSONArray("time");LocalDate today=now.atZone(a.data.zone).toLocalDate();int count=0;
        for(int i=0;i<times.length();i++) {
            String iso=times.optString(i);try{if(LocalDate.parse(iso).isBefore(today))continue;}catch(Exception e){continue;}
            if(count++>0)a.line(c);
            LinearLayout r=a.row();r.setPadding(0,a.dp(12),0,a.dp(12));
            LinearLayout day=a.column();day.addView(a.text(WeatherData.day(iso,today),14,a.INK,true));a.gap(day,5);day.addView(a.text(iso.length()>=10?iso.substring(5).replace('-','/') : "—",11,a.MUTED,false));r.addView(day,a.lp(48,-2));
            int code=WeatherData.code(a.data.daily,"weather_code",i);r.addView(new WeatherIcon(a,code,true),a.lp(33,36));
            LinearLayout desc=a.column();desc.setPadding(a.dp(6),0,0,0);LinearLayout values=a.row();values.addView(a.text(WeatherData.condition(code),12,a.INK,false),new LinearLayout.LayoutParams(0,-2,1));TextView range=a.text(WeatherData.degrees(WeatherData.at(a.data.daily,"temperature_2m_min",i))+" / "+WeatherData.degrees(WeatherData.at(a.data.daily,"temperature_2m_max",i)),13,a.INK,true);values.addView(range);desc.addView(values);a.gap(desc,7);desc.addView(a.text(WeatherData.number(WeatherData.at(a.data.daily,"precipitation_probability_max",i))+UiText.get("main_027"),11,a.MUTED,false));r.addView(desc,new LinearLayout.LayoutParams(0,-2,1));c.addView(r);

        }
        if(count==0)c.addView(a.text(UiText.get("main_023"),13,a.MUTED,false));
    }
    private void details(int today) {
        LinearLayout c=a.card();section(c,UiText.get("weather_details"),"Open-Meteo ↗");
        metricRow(c,UiText.get("gps_122"),WeatherData.number(WeatherData.value(a.data.current,"relative_humidity_2m"))+"%",UiText.get("main_028"),WeatherData.number(WeatherData.value(a.data.current,"wind_speed_10m"))+" km/h");
        a.gap(c,19);a.line(c);a.gap(c,19);
        double uv=WeatherData.at(a.data.daily,"uv_index_max",today);
        metricRow(c,UiText.get("main_029"),WeatherData.wind(WeatherData.value(a.data.current,"wind_direction_10m")).isEmpty()?"—":WeatherData.wind(WeatherData.value(a.data.current,"wind_direction_10m")),UiText.get("main_030"),Double.isFinite(uv)?String.format(Locale.ROOT,"%.1f",uv):"—");
        a.gap(c,19);a.line(c);a.gap(c,19);
        metricRow(c,UiText.get("main_031"),WeatherData.clock(WeatherData.textAt(a.data.daily,"sunrise",today)),UiText.get("main_032"),WeatherData.clock(WeatherData.textAt(a.data.daily,"sunset",today)));
        a.gap(c,19);a.line(c);a.gap(c,14);
        TextParagraphs.append(a,c,a.getString(R.string.uv_guidance),13,a.MUTED);a.link(c,a.getString(R.string.uv_guidance_source),"https://www.epa.gov/sites/default/files/documents/uviguide.pdf");a.link(c,a.getString(R.string.uv_safety_source),"https://www.cdc.gov/skin-cancer/sun-safety/index.html");
    }
    private void metricRow(LinearLayout parent,String leftLabel,String av,String b,String bv) {
        LinearLayout r=a.row();String[] labels={leftLabel,b},values={av,bv};
        for(int i=0;i<2;i++){LinearLayout col=a.column();col.addView(a.text(labels[i],12,a.MUTED,false));a.gap(col,8);col.addView(a.text(values[i],19,a.INK,true));r.addView(col,new LinearLayout.LayoutParams(0,-2,1));}parent.addView(r);
    }
    private void footer() {
        TextView about=a.text(a.getString(R.string.about_title)+" · "+a.versionName(),12,a.BLUE,false);a.markLink(about);about.setGravity(Gravity.CENTER);about.setMinHeight(a.dp(48));a.tap(about,Color.TRANSPARENT,10,a::about);a.body.addView(about);
    }

}
