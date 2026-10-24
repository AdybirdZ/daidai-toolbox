package org.simpleweather.app;

import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;

final class AstronomyScreen {
    private final MainActivity a;
    private final City city;
    private final WeatherData data;
    private final Instant at;
    private final Consumer<Instant> change;
    private final ZoneId zone;
    AstronomyScreen(MainActivity a,City city,WeatherData data,Instant at,Consumer<Instant> change){this.a=a;this.city=city;this.data=data;this.at=at;this.change=change;ZoneId z;try{z=ZoneId.of(data!=null?data.zone.getId():city.timezone);}catch(Exception e){z=ZoneId.of("UTC");}zone=z;}
    void render(){
        a.body.addView(a.text(city.label()+" · "+at.atZone(zone).format(DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm"))+" 当地时间",14,a.MUTED,false));a.gap(a.body,12);
        LinearLayout clock=a.row();clock.addView(a.button("此刻",false,()->change.accept(Instant.now())),new LinearLayout.LayoutParams(0,-2,1));clock.addView(a.button("-15分钟",false,()->change.accept(at.minusSeconds(900))),new LinearLayout.LayoutParams(0,-2,1));clock.addView(a.button("+15分钟",false,()->change.accept(at.plusSeconds(900))),new LinearLayout.LayoutParams(0,-2,1));a.body.addView(clock);a.gap(a.body,8);
        LinearLayout days=a.row();days.addView(a.button("-1天",false,()->change.accept(at.atZone(zone).minusDays(1).toInstant())),new LinearLayout.LayoutParams(0,-2,1));days.addView(a.button("设置时间",false,()->NumericInputs.dateTime(a,at.atZone(zone),change)),new LinearLayout.LayoutParams(0,-2,1));days.addView(a.button("+1天",false,()->change.accept(at.atZone(zone).plusDays(1).toInstant())),new LinearLayout.LayoutParams(0,-2,1));a.body.addView(days);a.gap(a.body,16);
        observing();a.gap(a.body,16);
        LinearLayout sky=a.card();sky.addView(a.text("当地星空 · 仰视示意",17,a.INK,true));a.gap(sky,12);sky.addView(new Sky(a,at,city.latitude,city.longitude),a.lp(-1,300));a.gap(sky,10);sky.addView(a.text("北在上、东在左。显示地平线上方的亮星与太阳、月亮；白天的星点仅为方位示意。点击上方切换时间。",12,a.MUTED,false));
        a.gap(a.body,16);positions();a.gap(a.body,16);events();a.gap(a.body,16);meteor();a.gap(a.body,16);
        a.gap(a.body,20);a.body.addView(a.text("日月和星图为本地低精度天文估算，忽略地形遮挡、大气变化及部分轨道摄动，不用于测量或导航。月升落误差可达数分钟以上；流星雨日期为常年参考。",12,a.MUTED,false));a.link(a.body,"计算公式与方法参考","https://stjarnhimlen.se/comp/ppcomp.html");
    }
    private void observing(){
        LinearLayout c=a.card();c.addView(a.text("观星条件 · 经验参考",17,a.INK,true));a.gap(c,12);
        double sun=Astronomy.sunPosition(at,city.latitude,city.longitude)[0],moon=Astronomy.moonPosition(at,city.latitude,city.longitude)[0],lit=Astronomy.illumination(at);
        c.addView(a.text(Astronomy.skyDescription(at,city.latitude,city.longitude),16,a.INK,true));a.gap(c,10);
        c.addView(a.text(moon>0?"月亮在地平线上 · 照亮比例约 "+Math.round(lit*100)+"%":"月亮在地平线下，月光影响较小",13,a.MUTED,false));a.gap(c,10);
        if(data==null||data.currentIsOld(Instant.now())){c.addView(a.text("天气数据缺失或较旧，请先回天气页刷新。天文位置可离线计算。",13,a.MUTED,false));return;}
        int best=-1;long delta=Long.MAX_VALUE;org.json.JSONArray times=data.hourly.optJSONArray("time");
        for(int i=0;i<times.length();i++)try{long d=Math.abs(Duration.between(LocalDateTime.parse(times.getString(i)).atZone(zone).toInstant(),at).toMinutes());if(d<delta){delta=d;best=i;}}catch(Exception ignored){}
        double cloud=WeatherData.at(data.hourly,"cloud_cover",best),rain=WeatherData.at(data.hourly,"precipitation_probability",best),wind=WeatherData.at(data.hourly,"wind_speed_10m",best),humidity=WeatherData.at(data.hourly,"relative_humidity_2m",best),visibility=WeatherData.at(data.hourly,"visibility",best);
        if(delta>90||!Double.isFinite(cloud)||!Double.isFinite(rain)||!Double.isFinite(wind)||!Double.isFinite(humidity)||!Double.isFinite(visibility)){c.addView(a.text("暂无完整的逐时观星天气条件，可返回天气页更新。",13,a.MUTED,false));return;}
        c.addView(a.text("云量 "+WeatherData.number(cloud)+"% · 降水概率 "+WeatherData.number(rain)+"%\n湿度 "+WeatherData.number(humidity)+"% · 风速 "+WeatherData.number(wind)+" km/h\n能见度 "+String.format(Locale.ROOT,"%.1f",visibility/1000)+" km",13,a.INK,false));a.gap(c,10);
        boolean good=sun<-12&&cloud<=40&&rain<=20&&wind<=25&&humidity<=85&&visibility>=8000;
        c.addView(a.text(good?"天气条件相对适合观星；需考虑月光及光污染。":"至少一项条件不理想，建议结合实际天空判断。",13,good?0xFF287C60:0xFF8B6021,false));a.gap(c,8);c.addView(a.text("经验筛选：太阳低于 −12°、云量≤40%、降水概率≤20%、湿度≤85%、风速≤25 km/h、能见度≥8 km。天气来源 Open-Meteo。未计算本地光污染。",11,a.MUTED,false));
    }
    private void positions(){
        LinearLayout c=a.card();c.addView(a.text("日月位置与月相",17,a.INK,true));a.gap(c,14);double[] sun=Astronomy.sunPosition(at,city.latitude,city.longitude),moon=Astronomy.moonPosition(at,city.latitude,city.longitude);double phase=Astronomy.phase(at);
        c.addView(a.text("太阳  "+String.format(Locale.ROOT,"高度 %.1f° · 方位 %.1f°",sun[0],sun[1])+" "+Astronomy.compass(sun[1]),14,a.INK,false));a.gap(c,12);c.addView(a.text("月亮  "+String.format(Locale.ROOT,"高度 %.1f° · 方位 %.1f°",moon[0],moon[1])+" "+Astronomy.compass(moon[1]),14,a.INK,false));a.gap(c,16);
        c.addView(a.text(Astronomy.phaseName(phase)+" · 照亮约 "+Math.round(Astronomy.illumination(at)*100)+"%",21,a.INK,true));a.gap(c,10);double age=phase*29.53059,untilFull=(.5-phase+1)%1*29.53059;c.addView(a.text(String.format(Locale.ROOT,"近似月龄 %.1f 天 · 距下次满月约 %.1f 天",age,untilFull),12,a.MUTED,false));
    }
    private String event(List<Astronomy.Event> events,boolean rise){for(Astronomy.Event e:events)if(e.rising==rise)return e.at.atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm"));return "当日无此事件";}
    private void events(){
        LinearLayout c=a.card();c.addView(a.text("当天日月事件与曙暮光",17,a.INK,true));a.gap(c,14);LocalDate date=at.atZone(zone).toLocalDate();String[] labels={"日出 / 日落","民用曙光 / 暮光","航海曙光 / 暮光","天文曙光 / 暮光","月升 / 月落"};double[] alt={-.833,-6,-12,-18,-.3};
        for(int n=0;n<labels.length;n++){List<Astronomy.Event> events=Astronomy.crossings(date,zone,city.latitude,city.longitude,n==4,alt[n]);c.addView(a.text(labels[n],12,a.MUTED,false));a.gap(c,7);c.addView(a.text(event(events,true)+"  /  "+event(events,false),15,a.INK,true));a.gap(c,16);}c.addView(a.text("均为所选地区当地时间。高纬度可能出现整日无升落或无天文夜。霞光需结合云层与现场观察，不能据此保证火烧云。",11,a.MUTED,false));
    }
    private void meteor(){
        LinearLayout c=a.card();c.addView(a.text("流星雨 · 常年极大日期参考",17,a.INK,true));a.gap(c,12);String[][] rains={{"象限仪座","01-04"},{"天琴座","04-22"},{"宝瓶座η","05-06"},{"英仙座","08-12"},{"猎户座","10-21"},{"狮子座","11-17"},{"双子座","12-14"},{"小熊座","12-22"}};
        LocalDate today=at.atZone(zone).toLocalDate();List<String> upcoming=new ArrayList<>();for(int offset=0;offset<=1;offset++)for(String[] r:rains){LocalDate day=LocalDate.parse((today.getYear()+offset)+"-"+r[1]);if(!day.isBefore(today)&&upcoming.size()<4)upcoming.add(day+"  "+r[0]);}for(String item:upcoming){c.addView(a.text(item,14,a.INK,false));a.gap(c,9);}c.addView(a.text("每年的准确时刻、月光与可见区域不同，请查阅当年的 IMO 日历。",12,a.MUTED,false));a.link(c,"IMO · 年度流星雨日历","https://www.imo.net/resources/calendar/");
    }
    private static final class Sky extends View {
        private final Instant at;private final double lat,lon;private final Paint p=new Paint(3);
        Sky(MainActivity a,Instant at,double lat,double lon){super(a);this.at=at;this.lat=lat;this.lon=lon;setContentDescription("亮星及日月方位的近似星图，北上东左");}
        @Override protected void onDraw(Canvas c){float cx=getWidth()/2f,cy=getHeight()/2f,r=Math.min(getWidth(),getHeight())*.42f;c.drawColor(0xFF13253D);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1);p.setColor(0xFF4D627A);for(int i=1;i<=3;i++)c.drawCircle(cx,cy,r*i/3,p);c.drawLine(cx-r,cy,cx+r,cy,p);c.drawLine(cx,cy-r,cx,cy+r,p);p.setStyle(Paint.Style.FILL);p.setTextSize(12*getResources().getDisplayMetrics().scaledDensity);p.setTextAlign(Paint.Align.CENTER);p.setColor(0xFFADC7E7);c.drawText("北",cx,cy-r-10,p);c.drawText("南",cx,cy+r+20,p);c.drawText("东",cx-r-15,cy+4,p);c.drawText("西",cx+r+15,cy+4,p);
            for(Astronomy.Star s:Astronomy.STARS){double[] h=Astronomy.horizontal(s.ra,s.dec,at,lat,lon);if(h[0]<0)continue;float[] xy=xy(cx,cy,r,h);p.setColor(0xFFE9F2FF);c.drawCircle(xy[0],xy[1],(float)Math.max(1.5,3-s.mag*.45),p);if(s.mag<1.5||s.name.equals("北极星")){p.setTextSize(10*getResources().getDisplayMetrics().scaledDensity);p.setTextAlign(xy[0]>cx+r*.6?Paint.Align.RIGHT:xy[0]<cx-r*.6?Paint.Align.LEFT:Paint.Align.CENTER);c.drawText(s.name,xy[0],xy[1]-7,p);}}
            body(c,cx,cy,r,Astronomy.sunPosition(at,lat,lon),"太阳",0xFFFFCB67);body(c,cx,cy,r,Astronomy.moonPosition(at,lat,lon),"月亮",0xFFBCD3FF);
        }
        private float[] xy(float cx,float cy,float r,double[] h){double d=(90-h[0])/90*r,az=Math.toRadians(h[1]);return new float[]{cx-(float)(d*Math.sin(az)),cy-(float)(d*Math.cos(az))};}
        private void body(Canvas c,float cx,float cy,float r,double[] h,String label,int color){if(h[0]<0)return;float[] xy=xy(cx,cy,r,h);p.setColor(color);c.drawCircle(xy[0],xy[1],6,p);p.setTextSize(12*getResources().getDisplayMetrics().scaledDensity);p.setTextAlign(Paint.Align.CENTER);c.drawText(label,xy[0],xy[1]+20,p);}
    }
}
