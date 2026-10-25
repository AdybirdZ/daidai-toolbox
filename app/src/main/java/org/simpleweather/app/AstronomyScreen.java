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
    private TextView selectedInfo;

    AstronomyScreen(MainActivity a,City city,WeatherData data,Instant at,Consumer<Instant> change){this.a=a;this.city=city;this.data=data;this.at=at;this.change=change;ZoneId z;try{z=ZoneId.of(data!=null?data.zone.getId():city.timezone);}catch(Exception e){z=ZoneId.of("UTC");}zone=z;}
    void render(){
        a.body.addView(a.text(city.label()+" · "+at.atZone(zone).format(DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm"))+" 当地时间",14,a.MUTED,false));a.gap(a.body,12);
        LinearLayout clock=a.row();clock.addView(a.button("-15分钟",false,()->change.accept(at.minusSeconds(900))),new LinearLayout.LayoutParams(0,-2,1));clock.addView(a.button("当前",false,()->change.accept(Instant.now())),new LinearLayout.LayoutParams(0,-2,1));clock.addView(a.button("+15分钟",false,()->change.accept(at.plusSeconds(900))),new LinearLayout.LayoutParams(0,-2,1));a.body.addView(clock);a.gap(a.body,8);
        LinearLayout days=a.row();days.addView(a.button("-1天",false,()->change.accept(at.atZone(zone).minusDays(1).toInstant())),new LinearLayout.LayoutParams(0,-2,1));days.addView(a.button("设置时间",false,()->NumericInputs.dateTime(a,at.atZone(zone),change)),new LinearLayout.LayoutParams(0,-2,1));days.addView(a.button("+1天",false,()->change.accept(at.atZone(zone).plusDays(1).toInstant())),new LinearLayout.LayoutParams(0,-2,1));a.body.addView(days);a.gap(a.body,16);
        observing();a.gap(a.body,16);
        LinearLayout sky=a.card();sky.addView(a.text("当地星空 · 仰视示意",17,a.INK,true));a.gap(sky,12);
        Sky view=new Sky(a,at,city.latitude,city.longitude,at.atZone(zone).toLocalDate(),description->{if(selectedInfo!=null)selectedInfo.setText(description);});
        sky.addView(view,a.lp(-1,300));a.gap(sky,10);sky.addView(a.text(view.chartNote(),12,a.MUTED,false));
        a.gap(a.body,16);LinearLayout info=a.card();info.addView(a.text("星点信息",17,a.INK,true));a.gap(info,10);selectedInfo=a.text(view.selectionDescription(),13,a.MUTED,false);info.addView(selectedInfo);a.gap(a.body,16);
        positions();a.gap(a.body,16);events();a.gap(a.body,16);meteor();a.gap(a.body,16);
        a.gap(a.body,20);a.body.addView(a.text("日月、行星和星图为本地低精度天文估算，使用本地计算，不请求第三方天文服务；忽略地形遮挡、大气变化及部分轨道摄动，不用于测量或导航。流星雨日期为常年参考，活跃期按极大日前后3天。",12,a.MUTED,false));a.link(a.body,"计算公式与方法参考","https://stjarnhimlen.se/comp/ppcomp.html");a.link(a.body,"深空天体中心坐标 · CC0","https://www.wikidata.org/wiki/Wikidata:Licensing");
    }
    private void observing(){
        LinearLayout c=a.card();c.addView(a.text("观星条件 · 经验参考",17,a.INK,true));a.gap(c,12);
        double sun=Astronomy.sunPosition(at,city.latitude,city.longitude)[0],moon=Astronomy.moonPosition(at,city.latitude,city.longitude)[0],lit=Astronomy.illumination(at);
        c.addView(a.text(Astronomy.skyDescription(at,city.latitude,city.longitude),16,a.INK,true));a.gap(c,10);
        c.addView(a.text(moon>0?"月球在地平线上 · 照亮比例约 "+Math.round(lit*100)+"%":"月球在地平线下，月光影响较小",13,a.MUTED,false));a.gap(c,10);
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
        LinearLayout c=a.card();c.addView(a.text("日月与行星位置",17,a.INK,true));a.gap(c,14);double[] sun=Astronomy.sunPosition(at,city.latitude,city.longitude),moon=Astronomy.moonPosition(at,city.latitude,city.longitude);double phase=Astronomy.phase(at);
        c.addView(a.text("太阳  "+String.format(Locale.ROOT,"高度 %.1f° · 方位 %.1f°",sun[0],sun[1])+" "+Astronomy.compass(sun[1]),14,a.INK,false));a.gap(c,10);c.addView(a.text("月球  "+String.format(Locale.ROOT,"高度 %.1f° · 方位 %.1f°",moon[0],moon[1])+" "+Astronomy.compass(moon[1]),14,a.INK,false));a.gap(c,10);
        for(Astronomy.Planet p:Astronomy.planets(at)){double[] h=Astronomy.planetPosition(p,at,city.latitude,city.longitude);c.addView(a.text(p.name+"  "+String.format(Locale.ROOT,"高度 %.1f° · 方位 %.1f° · 星等 %.2f",h[0],h[1],p.magnitude),13,a.INK,false));a.gap(c,7);}
        c.addView(a.text(Astronomy.phaseName(phase)+" · 照亮约 "+Math.round(Astronomy.illumination(at)*100)+"%",21,a.INK,true));a.gap(c,10);double age=phase*29.53059,untilFull=(.5-phase+1)%1*29.53059;c.addView(a.text(String.format(Locale.ROOT,"近似月龄 %.1f 天 · 距下次满月约 %.1f 天",age,untilFull),12,a.MUTED,false));
    }
    private String event(List<Astronomy.Event> events,boolean rise){for(Astronomy.Event e:events)if(e.rising==rise)return e.at.atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm"));return "当日无此事件";}
    private void events(){
        LinearLayout c=a.card();c.addView(a.text("当天日月事件与曙暮光",17,a.INK,true));a.gap(c,14);LocalDate date=at.atZone(zone).toLocalDate();String[] labels={"日出 / 日落","民用曙光 / 暮光","航海曙光 / 暮光","天文曙光 / 暮光","月升 / 月落"};double[] alt={-.833,-6,-12,-18,-.3};
        for(int n=0;n<labels.length;n++){List<Astronomy.Event> events=Astronomy.crossings(date,zone,city.latitude,city.longitude,n==4,alt[n]);c.addView(a.text(labels[n],12,a.MUTED,false));a.gap(c,7);c.addView(a.text(event(events,true)+"  /  "+event(events,false),15,a.INK,true));a.gap(c,16);}c.addView(a.text("均为所选地区当地时间。高纬度可能出现整日无升落或无天文夜。霞光需结合云层与现场观察，不能据此保证火烧云。",11,a.MUTED,false));
    }
    private void meteor(){
        LinearLayout c=a.card();c.addView(a.text("流星雨 · 极大日前后3天",17,a.INK,true));a.gap(c,12);LocalDate today=at.atZone(zone).toLocalDate();List<String> active=new ArrayList<>(),upcoming=new ArrayList<>();
        List<Astronomy.Shower> next=new ArrayList<>();
        for(Astronomy.Shower m:Astronomy.SHOWERS){
            if(m.activeOn(today)){double[] h=Astronomy.horizontal(m.ra,m.dec,at,city.latitude,city.longitude);active.add(m.name+" · 活跃期（参考极大日 "+m.peak.toString().substring(2)+"）\n辐射点高度 "+String.format(Locale.ROOT,"%.1f°",h[0])+" · 方位 "+String.format(Locale.ROOT,"%.1f°",h[1])+" "+Astronomy.compass(h[1]));}
            else next.add(m);
        }
        next.sort(Comparator.comparing(m->m.nextPeak(today)));for(Astronomy.Shower m:next){if(upcoming.size()==4)break;upcoming.add(m.nextPeak(today).toString()+"  "+m.name);}
        if(!active.isEmpty()){c.addView(a.text("当前活跃流星雨",13,a.BLUE,true));a.gap(c,8);for(String item:active){c.addView(a.text(item,14,a.INK,false));a.gap(c,10);}}
        if(active.isEmpty()){c.addView(a.text("当前不在已列流星雨的活跃期。",13,a.MUTED,false));a.gap(c,8);}if(!upcoming.isEmpty()){c.addView(a.text("接近的极大日",13,a.MUTED,true));a.gap(c,8);for(String item:upcoming){c.addView(a.text(item,14,a.INK,false));a.gap(c,9);}}
        c.addView(a.text("辐射点为近似赤经/赤纬换算结果；每年的准确时刻、月光与可见区域不同，请查阅当年的 IMO 日历。",12,a.MUTED,false));a.link(c,"IMO · 年度流星雨日历","https://www.imo.net/resources/calendar/");
    }
    private static final class Target {
        final String name,key;final double ra,dec,alt,az,mag;final int kind;final boolean selectable;
        Target(String name,String key,double ra,double dec,double mag,int kind,boolean selectable,Instant at,double lat,double lon){this.name=name;this.key=key;this.ra=ra;this.dec=dec;this.mag=mag;this.kind=kind;this.selectable=selectable;double[] h=Astronomy.horizontal(ra,dec,at,lat,lon);alt=h[0];az=h[1];}
    }
    private static final class Hit {final Target target;final float x,y;Hit(Target t,float x,float y){target=t;this.x=x;this.y=y;}}
    private static final class Sky extends View {
        private final MainActivity a;private final Instant at;private final LocalDate localDate;private final double lat,lon;private final Paint p=new Paint(3);private final Consumer<String> select;private final List<Hit> hits=new ArrayList<>();private final List<Target> targets=new ArrayList<>();private final List<RectF> labelBoxes=new ArrayList<>();private final int mode;
        private String selected;private float downX,downY;private boolean dragged;
        Sky(MainActivity a,Instant at,double lat,double lon,LocalDate localDate,Consumer<String> select){super(a);this.a=a;this.at=at;this.localDate=localDate;this.lat=lat;this.lon=lon;this.select=select;mode=Math.max(0,Math.min(2,a.preferences.getInt("astro_coordinates",0)));selected=a.preferences.getString("astro_selected","");
            for(Astronomy.Star s:Astronomy.STARS)targets.add(new Target(s.name,"astro_star_"+s.name,s.ra,s.dec,s.mag,0,!Astronomy.isDipper(s.name),at,lat,lon));
            for(Astronomy.Planet planet:Astronomy.planets(at))targets.add(new Target(planet.name,"astro_planet_"+planet.name,planet.ra,planet.dec,planet.magnitude,1,true,at,lat,lon));
            for(Astronomy.Star object:Astronomy.DEEP_SKY)targets.add(new Target(object.name,"astro_deep_"+object.name,object.ra,object.dec,Double.NaN,2,true,at,lat,lon));
            updateDescription();setClickable(true);
        }
        private void updateDescription(){setContentDescription("当地星图 · "+(mode==1?"赤道坐标系，北天极居中":mode==2?"不显示坐标网格":"地平坐标系，北上东左")+(selected.isEmpty()?"":" · 选中 "+selected));}
        String chartNote(){return (mode==1?"赤道坐标系：北天极居中，赤经 0h 在上、6h 在左；青色曲线为当地地平线。勾陈一靠近北天极，并非中心本身。":mode==2?"不显示坐标网格；保留地平投影，北在上、东在左。":"地平坐标系：天顶居中，北在上、东在左；圆周为地平线，内圈为高度 30°、60°。")+"只绘制地平线上方的天体；白天仅作方位示意。点击亮星、行星或深空天体中心查看详情，旋转的断开十字表示选中。太阳、月球及北斗七星仅作图示。";}
        private boolean enabled(Target t){return !t.selectable||a.preferences.getBoolean(t.key,true);}
        private Target selection(){for(Target t:targets)if(t.name.equals(selected)&&t.selectable)return t;return null;}
        String selectionDescription(){Target t=selection();if(t==null)return "点击星空中的亮星、行星或深空天体中心，查看方位角和高度角。北斗七星固定显示但仅作图示。";
            return t.name+(t.kind==2?" · 大致中心":"")+"\n高度角 "+String.format(Locale.ROOT,"%.1f°",t.alt)+" · 方位角 "+String.format(Locale.ROOT,"%.1f°",t.az)+" "+Astronomy.compass(t.az)+"\n赤经 "+String.format(Locale.ROOT,"%.2fh",t.ra/15)+" · 赤纬 "+String.format(Locale.ROOT,"%.2f°",t.dec)+(t.kind==2?"\n星等：未提供（扩展天体，不作单颗恒星亮度比较）":"\n星等 "+String.format(Locale.ROOT,"%.2f",t.mag))+(t.alt<0?"\n当前在地平线下，保留选中但不绘制十字。":!enabled(t)?"\n已在显示设置中隐藏，保留选中但不绘制十字。":"");}
        private float[] xy(float cx,float cy,float r,double ra,double dec,double alt,double az){double[] q=mode==1?SkyGeometry.northPolar(ra,dec):SkyGeometry.horizontal(alt,az);return new float[]{cx+(float)q[0]*r,cy+(float)q[1]*r};}
        private void label(Canvas c,String text,float x,float y,int color,float size){p.setColor(color);p.setStyle(Paint.Style.FILL);p.setTextSize(size*getResources().getDisplayMetrics().scaledDensity);p.setTextAlign(x>getWidth()*.8f?Paint.Align.RIGHT:x<getWidth()*.2f?Paint.Align.LEFT:Paint.Align.CENTER);c.drawText(text,x,y,p);labelBoxes.add(textBox(text,x,y,size));}
        private RectF textBox(String text,float x,float y,float size){p.setTextSize(size*getResources().getDisplayMetrics().scaledDensity);float w=p.measureText(text),left=x>getWidth()*.8f?x-w:x<getWidth()*.2f?x:x-w/2;return new RectF(left-a.dp(2),y+p.getFontMetrics().ascent-a.dp(1),left+w+a.dp(2),y+p.getFontMetrics().descent+a.dp(1));}
        private void objectLabel(Canvas c,String text,float x,float y,int color,boolean force){float size=mode==1?9:10;for(float offset:new float[]{-8,17,-20,29,-32,41}){float baseline=y+a.dp(offset);RectF box=textBox(text,x,baseline,size);boolean clear=box.left>=1&&box.right<getWidth()-1&&box.top>=1&&box.bottom<getHeight()-1;for(RectF previous:labelBoxes)if(RectF.intersects(box,previous)){clear=false;break;}if(clear){label(c,text,x,baseline,color,size);return;}}if(force)label(c,text,x,y-a.dp(8),color,size);}
        private void grid(Canvas c,float cx,float cy,float r){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(a.dp(1));p.setColor(0xFF4D627A);c.drawCircle(cx,cy,r,p);
            if(mode==0){for(int i=1;i<=2;i++)c.drawCircle(cx,cy,r*i/3,p);for(int i=0;i<4;i++){double z=Math.toRadians(i*90);c.drawLine(cx,cy,cx+(float)Math.sin(z)*r,cy+(float)Math.cos(z)*r,p);}label(c,"北",cx,cy-r-a.dp(5),0xFFADC7E7,11);label(c,"南",cx,cy+r+a.dp(15),0xFFADC7E7,11);label(c,"东",cx-r-a.dp(12),cy,0xFFADC7E7,11);label(c,"西",cx+r+a.dp(12),cy,0xFFADC7E7,11);}
            if(mode==1){for(int d=-60;d<=60;d+=30){p.setStyle(Paint.Style.STROKE);p.setColor(0xFF4D627A);c.drawCircle(cx,cy,r*(90-d)/180,p);label(c,d+"°",cx+a.dp(3),cy-r*(90-d)/180,0xFF7E96B2,9);}for(int h=0;h<24;h+=3){double z=Math.toRadians(h*15);p.setStyle(Paint.Style.STROKE);p.setColor(0xFF4D627A);c.drawLine(cx,cy,cx-(float)Math.sin(z)*r,cy-(float)Math.cos(z)*r,p);label(c,h+"h",cx-(float)Math.sin(z)*(r+a.dp(12)),cy-(float)Math.cos(z)*(r+a.dp(12))+a.dp(3),0xFFADC7E7,9);}label(c,"北天极",cx,cy+a.dp(11),0xFFADC7E7,9);
                Path horizon=new Path();for(int az=0;az<=360;az+=2){double[] eq=SkyGeometry.equatorial(0,az,at,lat,lon);float[] q=xy(cx,cy,r,eq[0],eq[1],0,az);if(az==0)horizon.moveTo(q[0],q[1]);else horizon.lineTo(q[0],q[1]);}p.setStyle(Paint.Style.STROKE);p.setColor(0xFF66CFC4);c.drawPath(horizon,p);
            }p.setStyle(Paint.Style.FILL);
        }
        @Override protected void onDraw(Canvas c){float cx=getWidth()/2f,cy=getHeight()/2f,r=Math.min(getWidth(),getHeight())*.40f;hits.clear();labelBoxes.clear();c.drawColor(0xFF13253D);grid(c,cx,cy,r);Target priority=selection();if(priority!=null&&priority.alt>=0&&enabled(priority)){float[] q=xy(cx,cy,r,priority.ra,priority.dec,priority.alt,priority.az);objectLabel(c,priority.name,q[0],q[1],0xFF8CEAF5,true);}
            Hit chosen=null;
            for(Target t:targets){if(!enabled(t)||t.alt<0)continue;float[] q=xy(cx,cy,r,t.ra,t.dec,t.alt,t.az);p.setStyle(Paint.Style.FILL);p.setColor(t.kind==1?0xFFFFD36B:t.kind==2?0xFF92E4D6:0xFFE9F2FF);
                if(t.kind==2){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(a.dp(1));if(t.name.equals("昴星团"))c.drawCircle(q[0],q[1],a.dp(5),p);else c.drawOval(q[0]-a.dp(7),q[1]-a.dp(3),q[0]+a.dp(7),q[1]+a.dp(3),p);p.setStyle(Paint.Style.FILL);}else c.drawCircle(q[0],q[1],a.dp(t.kind==1?3:(float)Math.max(1.2,2.4-t.mag*.3)),p);
                if(!t.name.equals(selected)&&(t.kind>0||t.mag<1.5||t.name.equals("勾陈一")))objectLabel(c,t.name,q[0],q[1],t.kind==2?0xFF92E4D6:t.kind==1?0xFFFFD36B:0xFFE9F2FF,false);
                if(t.selectable){Hit hit=new Hit(t,q[0],q[1]);hits.add(hit);if(t.name.equals(selected))chosen=hit;}
            }
            for(Astronomy.Shower meteor:Astronomy.SHOWERS){if(!meteor.activeOn(localDate))continue;double[] h=Astronomy.horizontal(meteor.ra,meteor.dec,at,lat,lon);if(h[0]<0)continue;float[] q=xy(cx,cy,r,meteor.ra,meteor.dec,h[0],h[1]);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(a.dp(1));p.setColor(0xFFFF9B54);c.drawCircle(q[0],q[1],a.dp(5),p);c.drawLine(q[0]-a.dp(7),q[1],q[0]+a.dp(7),q[1],p);c.drawLine(q[0],q[1]-a.dp(7),q[0],q[1]+a.dp(7),p);objectLabel(c,meteor.name+"流星雨",q[0],q[1],0xFFFF9B54,false);}
            double[] sun=Astronomy.sun(at),moon=Astronomy.moon(at);body(c,cx,cy,r,sun,Astronomy.sunPosition(at,lat,lon),"太阳",0xFFFFCB67);body(c,cx,cy,r,moon,Astronomy.moonPosition(at,lat,lon),"月球",0xFFBCD3FF);
            if(chosen!=null){c.save();c.rotate(SkyGeometry.selectionAngle(android.os.SystemClock.uptimeMillis()),chosen.x,chosen.y);p.setColor(0xFF8CEAF5);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(a.dp(1.5f));for(int arm=0;arm<4;arm++){c.drawLine(chosen.x+a.dp(8),chosen.y,chosen.x+a.dp(16),chosen.y,p);c.rotate(90,chosen.x,chosen.y);}c.restore();p.setStyle(Paint.Style.FILL);if(isShown()&&hasWindowFocus()&&getWindowVisibility()==VISIBLE)postInvalidateDelayed(33);}
        }
        @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(focused)invalidate();}
        @Override public boolean onTouchEvent(MotionEvent e){if(e.getActionMasked()==MotionEvent.ACTION_DOWN){downX=e.getX();downY=e.getY();dragged=false;return true;}if(e.getActionMasked()==MotionEvent.ACTION_MOVE){if(Math.hypot(e.getX()-downX,e.getY()-downY)>ViewConfiguration.get(getContext()).getScaledTouchSlop())dragged=true;return true;}if(e.getActionMasked()==MotionEvent.ACTION_CANCEL){dragged=true;return true;}if(e.getActionMasked()!=MotionEvent.ACTION_UP)return true;if(dragged)return true;
            Hit best=null;float d=Float.MAX_VALUE;for(Hit h:hits){float dx=e.getX()-h.x,dy=e.getY()-h.y,dist=dx*dx+dy*dy;if(dist<d){d=dist;best=h;}}float threshold=a.dp(24);if(best!=null&&d<=threshold*threshold){selected=best.target.name;a.preferences.edit().putString("astro_selected",selected).apply();select.accept(selectionDescription());updateDescription();invalidate();performClick();}return true;}
        @Override public boolean performClick(){super.performClick();return true;}
        private void body(Canvas c,float cx,float cy,float r,double[] eq,double[] h,String text,int color){if(h[0]<0)return;double[] apparent=mode==1?SkyGeometry.equatorial(h[0],h[1],at,lat,lon):eq;float[] q=xy(cx,cy,r,apparent[0],apparent[1],h[0],h[1]);p.setStyle(Paint.Style.FILL);p.setColor(color);c.drawCircle(q[0],q[1],a.dp(4),p);objectLabel(c,text,q[0],q[1]+a.dp(24),color,false);}
    }
}
