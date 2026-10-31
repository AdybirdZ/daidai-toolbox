package org.simpleweather.app;

import android.graphics.*;
import android.view.*;
import android.widget.*;
import android.hardware.*;
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
    private TextView selectedInfo;

    AstronomyScreen(MainActivity a,City city,WeatherData data,Instant at,Consumer<Instant> change){this.a=a;this.city=city;this.data=data;this.at=at;this.change=change;ZoneId z;try{z=ZoneId.of(data!=null?data.zone.getId():city.timezone);}catch(Exception e){z=ZoneId.of("UTC");}int tm=a.preferences.getInt("global_time_mode",0);if(tm==1)z=ZoneId.of("Asia/Shanghai");else if(tm==2)z=ZoneOffset.UTC;zone=z;}
    static final String[] SECTION_NAMES={"时间控制","观星条件","当地星空","选中的天体信息","月球信息","日月与行星位置","当日日月事件与曙暮光","流星雨","计算说明与来源"};
    void render(){
        a.body.addView(a.text(city.label()+" · "+at.atZone(zone).format(DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm"))+" · "+EarthquakeOptions.offset(zone,at.toEpochMilli()),14,a.MUTED,false));a.gap(a.body,12);
        // Create the selection model before either card, so arbitrary ordering remains valid.
        Sky view=new Sky(a,at,city.latitude,city.longitude,at.atZone(zone).toLocalDate(),description->{if(selectedInfo!=null)selectedInfo.setText(description);});
        selectedInfo=a.text(view.selectionDescription(),13,a.MUTED,false);
        for(int id:DisplayOrder.parse(a.preferences.getString("astro_order",""),SECTION_NAMES.length)){
            if(!a.preferences.getBoolean("astro_show_"+id,true))continue;
            switch(id){
                case 0:timeControl();break;
                case 1:observing();break;
                case 2:LinearLayout sky=a.card();sky.addView(a.text("当地星空 · 仰视示意",17,a.INK,true));a.gap(sky,12);sky.addView(a.button(a.preferences.getBoolean("astro_align_sky",false)?"关闭对准天空":"对准天空",false,()->{a.preferences.edit().putBoolean("astro_align_sky",!a.preferences.getBoolean("astro_align_sky",false)).apply();a.refreshScreen();}),a.lp(-1,48));a.gap(sky,8);sky.addView(view,a.lp(-1,-2));a.gap(sky,10);TextParagraphs.append(a,sky,view.chartNote(),12,a.MUTED);break;
                case 3:LinearLayout info=a.card();info.addView(a.text("选中的天体信息",17,a.INK,true));a.gap(info,10);info.addView(selectedInfo);break;
                case 4:moonInfo();break;
                case 5:positions();break;
                case 6:events();break;
                case 7:meteor();break;
                case 8:LinearLayout note=a.card();note.addView(a.text("计算说明与来源",17,a.INK,true));a.gap(note,12);TextParagraphs.append(a,note,"日月、行星和星图采用本地低精度近似计算，不请求第三方天文服务。计算未完整考虑地形遮挡、大气变化和轨道摄动，不用于测量或导航。\n\n流星雨按常年参考极大日前后 3 天显示，该区间不代表实际完整活跃期。恒星及深空天体的坐标与视星等为内置近似参考；深空天体的总视星等不直接代表肉眼可见程度。",12,a.MUTED);a.gap(note,10);a.link(note,"计算公式与方法参考","https://stjarnhimlen.se/comp/ppcomp.html");a.link(note,"内置天体坐标与星等 · CC0","https://www.wikidata.org/wiki/Wikidata:Licensing");break;
            }
            a.gap(a.body,16);
        }
    }
    private void timeButton(LinearLayout row,String title,Runnable click){if(row.getChildCount()>0)a.gapHorizontal(row,8);TextView button=a.button(title,false,click);button.setPadding(a.dp(4),a.dp(10),a.dp(4),a.dp(10));row.addView(button,new LinearLayout.LayoutParams(0,-2,1));}
    private void timeControl(){
        LinearLayout card=a.card();card.addView(a.text("时间控制",17,a.INK,true));a.gap(card,12);
        LinearLayout clock=a.row();timeButton(clock,"−15 分钟",()->change.accept(at.minusSeconds(900)));timeButton(clock,"当前时刻",()->change.accept(Instant.now()));timeButton(clock,"+15 分钟",()->change.accept(at.plusSeconds(900)));card.addView(clock);a.gap(card,8);
        LinearLayout days=a.row();timeButton(days,"−1 天",()->change.accept(at.atZone(zone).minusDays(1).toInstant()));timeButton(days,"设置时间",()->NumericInputs.dateTime(a,at.atZone(zone),change));timeButton(days,"+1 天",()->change.accept(at.atZone(zone).plusDays(1).toInstant()));card.addView(days);
    }
    private void observing(){
        LinearLayout c=a.card();c.addView(a.text("观星条件 · 经验参考",17,a.INK,true));a.gap(c,12);
        double sun=Astronomy.sunPosition(at,city.latitude,city.longitude)[0],moon=Astronomy.moonPosition(at,city.latitude,city.longitude)[0],lit=Astronomy.illumination(at);
        c.addView(a.text(Astronomy.skyDescription(at,city.latitude,city.longitude),16,a.INK,true));a.gap(c,10);
        c.addView(a.text(moon>0?"月球在地平线上方 · 照亮比例约 "+Math.round(lit*100)+"%":"月球在地平线下方，月光影响较小",13,a.MUTED,false));a.gap(c,10);
        if(data==null||data.currentIsOld(Instant.now())){c.addView(a.text("天气数据缺失或已过期，请在天气页刷新。天体位置仍可离线计算。",13,a.MUTED,false));return;}
        int best=-1;long delta=Long.MAX_VALUE;org.json.JSONArray times=data.hourly.optJSONArray("time");
        for(int i=0;i<times.length();i++)try{long d=Math.abs(Duration.between(LocalDateTime.parse(times.getString(i)).atZone(zone).toInstant(),at).toMinutes());if(d<delta){delta=d;best=i;}}catch(Exception ignored){}
        double cloud=WeatherData.at(data.hourly,"cloud_cover",best),rain=WeatherData.at(data.hourly,"precipitation_probability",best),wind=WeatherData.at(data.hourly,"wind_speed_10m",best),humidity=WeatherData.at(data.hourly,"relative_humidity_2m",best),visibility=WeatherData.at(data.hourly,"visibility",best);
        if(delta>90||!Double.isFinite(cloud)||!Double.isFinite(rain)||!Double.isFinite(wind)||!Double.isFinite(humidity)||!Double.isFinite(visibility)){c.addView(a.text("所选时刻暂无完整天气数据，请在天气页刷新，或调整天文时间。",13,a.MUTED,false));return;}
        c.addView(a.text("云量 "+WeatherData.number(cloud)+"% · 降水概率 "+WeatherData.number(rain)+"%\n湿度 "+WeatherData.number(humidity)+"% · 风速 "+WeatherData.number(wind)+" km/h\n能见度 "+String.format(Locale.ROOT,"%.1f",visibility/1000)+" km",13,a.INK,false));a.gap(c,10);
        boolean good=sun<-12&&cloud<=40&&rain<=20&&wind<=25&&humidity<=85&&visibility>=8000;
        c.addView(a.text(good?"天气条件相对适合观星；需考虑月光及光污染。":"至少一项条件未达到参考标准，请结合实际观测环境判断。",13,good?0xFF287C60:0xFF8B6021,false));a.gap(c,8);c.addView(a.text("经验筛选：太阳低于 −12°、云量 ≤ 40%、降水概率 ≤ 20%、湿度 ≤ 85%、风速 ≤ 25 km/h、能见度 ≥ 8 km。天气来源 Open-Meteo。未计算本地光污染。",11,a.MUTED,false));
    }
    private void moonInfo(){
        LinearLayout c=a.card();c.addView(a.text("月球信息",17,a.INK,true));a.gap(c,10);double phase=Astronomy.phase(at);
        c.addView(new MoonPhase(a,phase),a.lp(-1,160));a.gap(c,10);
        c.addView(a.text(Astronomy.phaseName(phase)+" · 照亮约 "+Math.round(Astronomy.illumination(at)*100)+"%",21,a.INK,true));a.gap(c,10);
        c.addView(a.text(String.format(Locale.ROOT,"近似月龄 %.1f 天 · 距下次满月约 %.1f 天",phase*29.53059,(.5-phase+1)%1*29.53059),12,a.MUTED,false));a.gap(c,10);
        c.addView(a.text("月相随上方所选时间更新；示意图盈月右侧亮、亏月左侧亮，未按当地观测视角旋转，不包含月食与月面纹理。月龄及距满月时间为平均朔望月近似。",12,a.MUTED,false));
    }
    private static final class MoonPhase extends View {
        private final Paint paint=new Paint(3);private final double phase;
        MoonPhase(MainActivity a,double phase){super(a);this.phase=phase;setContentDescription("月相示意 · "+Astronomy.phaseName(phase)+" · 照亮约 "+Math.round((1-Math.cos(phase*Math.PI*2))*50)+"%");}
        @Override protected void onDraw(Canvas canvas){
            float x=getWidth()/2f,y=getHeight()/2f,r=Math.min(getWidth(),getHeight())*.43f;
            paint.setStyle(Paint.Style.FILL);paint.setColor(0xFF253B55);canvas.drawCircle(x,y,r,paint);
            Path light=new Path();int steps=160;
            for(int i=0;i<=steps;i++){double v=-1+2.0*i/steps;double[] span=Astronomy.moonDiscSpan(phase,v);float px=x+(float)span[0]*r,py=y+(float)v*r;if(i==0)light.moveTo(px,py);else light.lineTo(px,py);}
            for(int i=steps;i>=0;i--){double v=-1+2.0*i/steps;double[] span=Astronomy.moonDiscSpan(phase,v);light.lineTo(x+(float)span[1]*r,y+(float)v*r);}light.close();paint.setColor(0xFFE0E9F5);canvas.drawPath(light,paint);
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(getResources().getDisplayMetrics().density);paint.setColor(0xFF8198B4);canvas.drawCircle(x,y,r,paint);
        }
    }
    private void positions(){
        LinearLayout c=a.card();c.addView(a.text("日月与行星位置",17,a.INK,true));a.gap(c,14);double[] sun=Astronomy.sunPosition(at,city.latitude,city.longitude),moon=Astronomy.moonPosition(at,city.latitude,city.longitude);
        c.addView(a.text("太阳  "+String.format(Locale.ROOT,"高度角 %.1f° · 方位角 %.1f°",sun[0],sun[1])+" "+Astronomy.compass(sun[1]),14,a.INK,false));a.gap(c,10);c.addView(a.text("月球  "+String.format(Locale.ROOT,"高度角 %.1f° · 方位角 %.1f°",moon[0],moon[1])+" "+Astronomy.compass(moon[1]),14,a.INK,false));a.gap(c,10);
        for(Astronomy.Planet p:Astronomy.planets(at)){double[] h=Astronomy.planetPosition(p,at,city.latitude,city.longitude);c.addView(a.text(p.name+"  "+String.format(Locale.ROOT,"高度角 %.1f° · 方位角 %.1f° · 视星等 %.2f",h[0],h[1],p.magnitude),13,a.INK,false));a.gap(c,7);}

    }
    private String event(List<Astronomy.Event> events,boolean rise){for(Astronomy.Event e:events)if(e.rising==rise)return e.at.atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm"));return "当日无此事件";}
    private void events(){
        LinearLayout c=a.card();c.addView(a.text("当日日月事件与曙暮光",17,a.INK,true));a.gap(c,14);LocalDate date=at.atZone(zone).toLocalDate();String[] labels={"日出 / 日落","民用曙光 / 暮光","航海曙光 / 暮光","天文曙光 / 暮光","月升 / 月落"};double[] alt={-.833,-6,-12,-18,-.3};
        for(int n=0;n<labels.length;n++){List<Astronomy.Event> events=Astronomy.crossings(date,zone,city.latitude,city.longitude,n==4,alt[n]);c.addView(a.text(labels[n],12,a.MUTED,false));a.gap(c,7);c.addView(a.text(event(events,true)+"  /  "+event(events,false),15,a.INK,true));a.gap(c,16);}c.addView(a.text("时间按当前天文显示时区标注。高纬度地区可能出现当日无升落事件或无天文夜的情况。曙暮光时刻不用于预测霞光。",11,a.MUTED,false));
    }
    private void meteor(){
        LinearLayout c=a.card();c.addView(a.text("流星雨 · 参考极大日前后 3 天",17,a.INK,true));a.gap(c,12);LocalDate today=at.atZone(zone).toLocalDate();List<String> active=new ArrayList<>(),upcoming=new ArrayList<>();
        List<Astronomy.Shower> next=new ArrayList<>();
        for(Astronomy.Shower m:Astronomy.SHOWERS){
            if(m.activeOn(today)){double[] h=Astronomy.horizontal(m.ra,m.dec,at,city.latitude,city.longitude);active.add(m.name+" · 参考显示期（极大日 "+m.peak.toString().substring(2)+"）\n辐射点高度角 "+String.format(Locale.ROOT,"%.1f°",h[0])+" · 方位角 "+String.format(Locale.ROOT,"%.1f°",h[1])+" "+Astronomy.compass(h[1]));}
            else next.add(m);
        }
        next.sort(Comparator.comparing(m->m.nextPeak(today)));for(Astronomy.Shower m:next){if(upcoming.size()==4)break;upcoming.add(m.nextPeak(today).toString()+"  "+m.name);}
        if(!active.isEmpty()){c.addView(a.text("所选日期的参考流星雨",13,a.BLUE,true));a.gap(c,8);for(String item:active){c.addView(a.text(item,14,a.INK,false));a.gap(c,10);}}
        if(active.isEmpty()){c.addView(a.text("所选日期不在内置流星雨的参考显示期内。",13,a.MUTED,false));a.gap(c,8);}if(!upcoming.isEmpty()){c.addView(a.text("后续参考极大日",13,a.MUTED,true));a.gap(c,8);for(String item:upcoming){c.addView(a.text(item,14,a.INK,false));a.gap(c,9);}}
        c.addView(a.text("辐射点位置由近似赤经、赤纬换算。此处按常年参考极大日前后 3 天显示，不代表实际完整活跃期；每年的极大时刻及观测条件请查阅当年 IMO 日历。",12,a.MUTED,false));a.link(c,"IMO · 年度流星雨日历","https://www.imo.net/resources/calendar/");
    }
    private static final class Target {
        final String name,key;final double ra,dec,alt,az,mag;final int kind;final boolean selectable;
        Target(String name,String key,double ra,double dec,double mag,int kind,boolean selectable,Instant at,double lat,double lon){this.name=name;this.key=key;this.ra=ra;this.dec=dec;this.mag=mag;this.kind=kind;this.selectable=selectable;double[] h=Astronomy.horizontal(ra,dec,at,lat,lon);alt=h[0];az=h[1];}
        Target(String name,int kind,Instant at,double lat,double lon){
            this.name=name;this.key=null;this.kind=kind;selectable=true;mag=kind==4?Astronomy.moonMagnitude(at):Double.NaN;
            double[] h=kind==3?Astronomy.sunPosition(at,lat,lon):Astronomy.moonPosition(at,lat,lon);alt=h[0];az=h[1];
            double[] eq=SkyGeometry.equatorial(alt,az,at,lat,lon);ra=eq[0];dec=eq[1];
        }
    }
    private static final class Hit {final Target target;final float x,y;Hit(Target t,float x,float y){target=t;this.x=x;this.y=y;}}
    private static final class Sky extends View implements SensorEventListener {
        private final MainActivity a;private final Instant at;private final LocalDate localDate;private final double lat,lon;private final Paint p=new Paint(3);private final Consumer<String> select;private final List<Hit> hits=new ArrayList<>();private final List<Target> targets=new ArrayList<>();private final List<SkyLabels.Box> labelBoxes=new ArrayList<>();private final List<SkyLabels.Candidate> labels=new ArrayList<>();private final Map<String,Integer> labelColors=new HashMap<>();private final int mode;
        private String selected;private float downX,downY;private boolean dragged;private double aimAltitude=Double.NaN,aimAzimuth=Double.NaN;private SensorManager sensors;private boolean aligned;
        Sky(MainActivity a,Instant at,double lat,double lon,LocalDate localDate,Consumer<String> select){super(a);this.a=a;this.at=at;this.localDate=localDate;this.lat=lat;this.lon=lon;this.select=select;mode=Math.max(0,Math.min(2,a.preferences.getInt("astro_coordinates",0)));selected=a.preferences.getString("astro_selected","");
            for(Astronomy.Star s:Astronomy.STARS)targets.add(new Target(s.name,"astro_star_"+s.name,s.ra,s.dec,s.mag,0,true,at,lat,lon));
            for(Astronomy.Planet planet:Astronomy.planets(at))targets.add(new Target(planet.name,"astro_planet_"+planet.name,planet.ra,planet.dec,planet.magnitude,1,true,at,lat,lon));
            for(Astronomy.Star object:Astronomy.DEEP_SKY)targets.add(new Target(object.name,"astro_deep_"+object.name,object.ra,object.dec,object.mag,2,true,at,lat,lon));
            targets.add(new Target("太阳",3,at,lat,lon));targets.add(new Target("月球",4,at,lat,lon));
            aligned=a.preferences.getBoolean("astro_align_sky",false)&&SkyPointing.supported(mode);if(aligned){sensors=(SensorManager)a.getSystemService(android.content.Context.SENSOR_SERVICE);Sensor rotation=sensors==null?null:sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);if(rotation!=null)sensors.registerListener(this,rotation,SensorManager.SENSOR_DELAY_GAME);}
            updateDescription();setClickable(true);
        }
        private void updateDescription(){setContentDescription("当地星图 · "+(mode==1?"赤道坐标系，北天极居中":mode==2?"不显示坐标网格":"地平坐标系，北上东左")+(selected.isEmpty()?"":" · 选中 "+selected));}
        String chartNote(){return (mode==1?"赤道坐标系：北天极居中，赤经 0h 在上、6h 在左；青色曲线为当地地平线。勾陈一靠近北天极，并非中心本身。":mode==2?"不显示坐标网格；保留地平投影，北在上、东在左。":"地平坐标系：天顶居中，北在上、东在左；圆周为地平线，内圈为高度 30°、60°。")+"\n\n"+a.getString(R.string.sky_objects_note)+"\n\n"+a.getString(R.string.sky_aim_note);}
        private boolean enabled(Target t){return SkyVisibility.visible(a.preferences.getInt("astro_visibility_mode",0)==1,t.key==null||a.preferences.getBoolean(t.key,true),t.mag,a.preferences.getFloat("astro_magnitude_limit",6),t.key==null);}
        private Target selection(){for(Target t:targets)if(t.name.equals(selected)&&t.selectable)return t;return null;}
        String selectionDescription(){Target t=selection();if(t==null)return "点击星空中的太阳、月球、亮星、行星或深空天体中心，查看方位角和高度角。";
            return t.name+(t.kind==2?" · 大致中心":"")+"\n高度角 "+String.format(Locale.ROOT,"%.1f°",t.alt)+" · 方位角 "+String.format(Locale.ROOT,"%.1f°",t.az)+" "+Astronomy.compass(t.az)+"\n赤经 "+String.format(Locale.ROOT,"%.2fh",t.ra/15)+" · 赤纬 "+String.format(Locale.ROOT,"%.2f°",t.dec)+(t.kind==3?"":t.kind==4?(Double.isFinite(t.mag)?"\n视星等约 "+String.format(Locale.ROOT,"%.1f",t.mag)+"（V 波段估算）\n不含大气消光、地球照及月食影响。":"\n视星等：近新月，暂不估算"):(t.kind==2?"\n近似总视星等："+SkyVisibility.magnitude(t.mag)+"（参考值）\n扩展天体的光分散在较大区域，总星等不等同于肉眼可见程度。":"\n视星等 "+String.format(Locale.ROOT,"%.2f",t.mag)))+(t.alt<0?"\n当前在地平线下，保留选中但不绘制十字。":!enabled(t)?"\n当前显示模式已隐藏此天体，保留选中但不绘制十字。":"");}
        private float[] xy(float cx,float cy,float r,double ra,double dec,double alt,double az){double[] q=mode==1?SkyGeometry.northPolar(ra,dec):SkyGeometry.horizontal(alt,az);return new float[]{cx+(float)q[0]*r,cy+(float)q[1]*r};}
        private void label(Canvas c,String text,float x,float y,int color,float size){p.setColor(color);p.setStyle(Paint.Style.FILL);p.setTextSize(size*getResources().getDisplayMetrics().scaledDensity);p.setTextAlign(x>getWidth()*.8f?Paint.Align.RIGHT:x<getWidth()*.2f?Paint.Align.LEFT:Paint.Align.CENTER);c.drawText(text,x,y,p);float w=p.measureText(text),left=p.getTextAlign()==Paint.Align.RIGHT?x-w:p.getTextAlign()==Paint.Align.LEFT?x:x-w/2;labelBoxes.add(new SkyLabels.Box(left-a.dp(2),y+p.getFontMetrics().ascent-a.dp(1),left+w+a.dp(2),y+p.getFontMetrics().descent+a.dp(1)));}
        private void objectLabel(String text,float x,float y,int color){p.setTextSize((mode==1?9:10)*getResources().getDisplayMetrics().scaledDensity);labels.add(new SkyLabels.Candidate(text,x,y,p.measureText(text),p.getFontMetrics().ascent,p.getFontMetrics().descent));labelColors.put(text,color);}
        private void drawObjectLabels(Canvas c){
            p.setTextAlign(Paint.Align.CENTER);p.setStyle(Paint.Style.FILL);p.setTextSize((mode==1?9:10)*getResources().getDisplayMetrics().scaledDensity);
            for(SkyLabels.Placed label:SkyLabels.place(labels,labelBoxes,getWidth(),getHeight(),a.dp(8),a.dp(2),a.dp(1))){p.setColor(labelColors.get(label.candidate.id));c.drawText(label.candidate.id,label.candidate.x,label.baseline,p);}
        }
        private float chartRadius(){
            float radius=Math.min(getWidth(),getHeight())*.43f;
            if(mode!=2){p.setTextSize((mode==1?9:11)*getResources().getDisplayMetrics().scaledDensity);Paint.FontMetrics f=p.getFontMetrics();float inset=Math.max(a.dp(mode==1?9:5)-f.ascent,a.dp(15)+f.descent)+a.dp(2);radius=Math.min(radius,getWidth()/2f-inset);}
            return Math.max(0,radius);
        }
        @Override protected void onMeasure(int widthSpec,int heightSpec){int width=MeasureSpec.getSize(widthSpec);if(width==0)width=a.dp(300);setMeasuredDimension(width,width);}
        private void grid(Canvas c,float cx,float cy,float r){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(a.dp(1));p.setColor(0xFF4D627A);c.drawCircle(cx,cy,r,p);
            if(mode==0){for(int i=1;i<=2;i++)c.drawCircle(cx,cy,r*i/3,p);for(int i=0;i<4;i++){double z=Math.toRadians(i*90);c.drawLine(cx,cy,cx+(float)Math.sin(z)*r,cy+(float)Math.cos(z)*r,p);}label(c,"北",cx,cy-r-a.dp(5),0xFFADC7E7,11);label(c,"南",cx,cy+r+a.dp(15),0xFFADC7E7,11);label(c,"东",cx-r-a.dp(12),cy,0xFFADC7E7,11);label(c,"西",cx+r+a.dp(12),cy,0xFFADC7E7,11);}
            if(mode==1){for(int d=-60;d<=60;d+=30){p.setStyle(Paint.Style.STROKE);p.setColor(0xFF4D627A);c.drawCircle(cx,cy,r*(90-d)/180,p);label(c,d+"°",cx+a.dp(3),cy-r*(90-d)/180,0xFF7E96B2,9);}for(int h=0;h<24;h+=3){double z=Math.toRadians(h*15);p.setStyle(Paint.Style.STROKE);p.setColor(0xFF4D627A);c.drawLine(cx,cy,cx-(float)Math.sin(z)*r,cy-(float)Math.cos(z)*r,p);label(c,h+"h",cx-(float)Math.sin(z)*(r+a.dp(12)),cy-(float)Math.cos(z)*(r+a.dp(12))+a.dp(3),0xFFADC7E7,9);}label(c,"北天极",cx,cy+a.dp(11),0xFFADC7E7,9);
                Path horizon=new Path();for(int az=0;az<=360;az+=2){double[] eq=SkyGeometry.equatorial(0,az,at,lat,lon);float[] q=xy(cx,cy,r,eq[0],eq[1],0,az);if(az==0)horizon.moveTo(q[0],q[1]);else horizon.lineTo(q[0],q[1]);}p.setStyle(Paint.Style.STROKE);p.setColor(0xFF66CFC4);c.drawPath(horizon,p);
            }p.setStyle(Paint.Style.FILL);
        }
        @Override protected void onDraw(Canvas c){float cx=getWidth()/2f,cy=getHeight()/2f,r=chartRadius();hits.clear();labelBoxes.clear();labels.clear();labelColors.clear();c.drawColor(0xFF13253D);grid(c,cx,cy,r);
            Hit chosen=null;
            for(Target t:targets){if(!enabled(t)||t.alt<0)continue;float[] q=xy(cx,cy,r,t.ra,t.dec,t.alt,t.az);p.setStyle(Paint.Style.FILL);p.setColor(t.kind==3?0xFFFFCB67:t.kind==4?0xFFBCD3FF:t.kind==1?0xFFFFD36B:t.kind==2?0xFF92E4D6:0xFFE9F2FF);
                if(t.kind==2){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(a.dp(1));if(t.name.contains("星团"))c.drawCircle(q[0],q[1],a.dp(5),p);else c.drawOval(q[0]-a.dp(7),q[1]-a.dp(3),q[0]+a.dp(7),q[1]+a.dp(3),p);p.setStyle(Paint.Style.FILL);}else c.drawCircle(q[0],q[1],a.dp(t.kind>=3?4:t.kind==1?3:(float)Math.max(1.2,2.4-t.mag*.3)),p);
                objectLabel(t.name,q[0],q[1],t.name.equals(selected)?0xFF8CEAF5:p.getColor());
                if(t.selectable){Hit hit=new Hit(t,q[0],q[1]);hits.add(hit);if(t.name.equals(selected))chosen=hit;}
            }
            for(Astronomy.Shower meteor:Astronomy.SHOWERS){if(!meteor.activeOn(localDate))continue;double[] h=Astronomy.horizontal(meteor.ra,meteor.dec,at,lat,lon);if(h[0]<0)continue;float[] q=xy(cx,cy,r,meteor.ra,meteor.dec,h[0],h[1]);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(a.dp(1));p.setColor(0xFFFF9B54);c.drawCircle(q[0],q[1],a.dp(5),p);c.drawLine(q[0]-a.dp(7),q[1],q[0]+a.dp(7),q[1],p);c.drawLine(q[0],q[1]-a.dp(7),q[0],q[1]+a.dp(7),p);objectLabel(meteor.name+"流星雨",q[0],q[1],0xFFFF9B54);}
            drawObjectLabels(c);
            double[] aim=aligned?SkyPointing.marker(mode,aimAltitude,aimAzimuth):null;
            if(aim!=null){float x=cx+(float)aim[0]*r,y=cy+(float)aim[1]*r;p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeWidth(a.dp(1.5f));p.setColor(0xFFE53935);c.drawLine(x-a.dp(14),y,x+a.dp(14),y,p);c.drawLine(x,y-a.dp(14),x,y+a.dp(14),p);p.setStyle(Paint.Style.FILL);}
            if(chosen!=null){c.save();c.rotate(SkyGeometry.selectionAngle(android.os.SystemClock.uptimeMillis()),chosen.x,chosen.y);p.setColor(0xFF8CEAF5);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(a.dp(1.5f));for(int arm=0;arm<4;arm++){c.drawLine(chosen.x+a.dp(8),chosen.y,chosen.x+a.dp(16),chosen.y,p);c.rotate(90,chosen.x,chosen.y);}c.restore();p.setStyle(Paint.Style.FILL);if(isShown()&&hasWindowFocus()&&getWindowVisibility()==VISIBLE)postInvalidateDelayed(33);}
        }
        @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(focused)invalidate();}
        @Override public void onSensorChanged(SensorEvent event){if(event.sensor.getType()!=Sensor.TYPE_ROTATION_VECTOR)return;float[] matrix=new float[9];SensorManager.getRotationMatrixFromVector(matrix,event.values);updatePointing(matrix);}
        private void updatePointing(float[] matrix){double declination=new GeomagneticField((float)lat,(float)lon,0,System.currentTimeMillis()).getDeclination();double[] direction=SkyPointing.direction(matrix,"top".equals(a.preferences.getString("astro_orientation_reference","camera")),a.getWindowManager().getDefaultDisplay().getRotation(),declination);aimAltitude=direction[0];aimAzimuth=direction[1];invalidate();}
        @Override public void onAccuracyChanged(Sensor sensor,int accuracy){}
        @Override protected void onDetachedFromWindow(){if(sensors!=null)sensors.unregisterListener(this);super.onDetachedFromWindow();}
        @Override public boolean onTouchEvent(MotionEvent e){if(e.getActionMasked()==MotionEvent.ACTION_DOWN){downX=e.getX();downY=e.getY();dragged=false;return true;}if(e.getActionMasked()==MotionEvent.ACTION_MOVE){if(Math.hypot(e.getX()-downX,e.getY()-downY)>ViewConfiguration.get(getContext()).getScaledTouchSlop())dragged=true;return true;}if(e.getActionMasked()==MotionEvent.ACTION_CANCEL){dragged=true;return true;}if(e.getActionMasked()!=MotionEvent.ACTION_UP)return true;if(dragged)return true;
            Hit best=null;float d=Float.MAX_VALUE;for(Hit h:hits){float dx=e.getX()-h.x,dy=e.getY()-h.y,dist=dx*dx+dy*dy;if(dist<d){d=dist;best=h;}}float threshold=a.dp(24);if(best!=null&&d<=threshold*threshold){selected=best.target.name;a.preferences.edit().putString("astro_selected",selected).apply();select.accept(selectionDescription());updateDescription();invalidate();performClick();}return true;}
        @Override public boolean performClick(){super.performClick();return true;}

    }
}
