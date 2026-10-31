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
    static String[] sectionNames(){return new String[]{UiText.get("astronomy_106"),UiText.get("astronomy_107"),UiText.get("astronomy_108"),UiText.get("astronomy_109"),UiText.get("astronomy_110"),UiText.get("astronomy_111"),UiText.get("astronomy_112"),UiText.get("astronomy_113"),UiText.get("astronomy_114")};}
    void render(){
        a.body.addView(a.text(city.label()+" · "+at.atZone(zone).format(DateTimeFormatter.ofPattern(UiText.get("astronomy_115")))+" · "+EarthquakeOptions.offset(zone,at.toEpochMilli()),14,a.MUTED,false));a.gap(a.body,12);
        // Create the selection model before either card, so arbitrary ordering remains valid.
        Sky view=new Sky(a,at,city.latitude,city.longitude,at.atZone(zone).toLocalDate(),description->{if(selectedInfo!=null)selectedInfo.setText(description);});
        selectedInfo=a.text(view.selectionDescription(),13,a.MUTED,false);
        for(int id:DisplayOrder.parse(a.preferences.getString("astro_order",""),sectionNames().length)){
            if(!a.preferences.getBoolean("astro_show_"+id,true))continue;
            switch(id){
                case 0:timeControl();break;
                case 1:observing();break;
                case 2:skyCard(view);break;
                case 3:LinearLayout info=a.card();info.addView(a.text(UiText.get("astronomy_109"),17,a.TITLE,true));a.gap(info,10);info.addView(selectedInfo);break;
                case 4:moonInfo();break;
                case 5:positions();break;
                case 6:events();break;
                case 7:meteor();break;
                case 8:LinearLayout note=a.card();note.addView(a.text(UiText.get("astronomy_114"),17,a.TITLE,true));a.gap(note,12);TextParagraphs.append(a,note,UiText.get("astronomy_119"),12,a.MUTED);a.gap(note,10);a.link(note,UiText.get("astronomy_120"),"https://stjarnhimlen.se/comp/ppcomp.html");a.link(note,UiText.get("astronomy_121"),"https://www.wikidata.org/wiki/Wikidata:Licensing");break;
            }
            a.gap(a.body,16);
        }
    }
    private void timeButton(LinearLayout row,String title,Runnable click){if(row.getChildCount()>0)a.gapHorizontal(row,8);TextView button=a.button(title,false,click);button.setPadding(a.dp(4),a.dp(10),a.dp(4),a.dp(10));row.addView(button,new LinearLayout.LayoutParams(0,-2,1));}
    private void skyCard(Sky view){
        LinearLayout sky=a.card();sky.addView(a.text(UiText.get("astronomy_116"),17,a.TITLE,true));a.gap(sky,12);
        TextView toggle=a.button(a.preferences.getBoolean("astro_align_sky",false)?UiText.get("astronomy_117"):UiText.get("astronomy_118"),false,()->{});
        toggle.setOnClickListener(v->a.updateReadingValues(()->{boolean on=!a.preferences.getBoolean("astro_align_sky",false);a.preferences.edit().putBoolean("astro_align_sky",on).apply();view.setAligned(on);toggle.setText(on?UiText.get("astronomy_117"):UiText.get("astronomy_118"));}));
        sky.addView(toggle,a.lp(-1,48));a.gap(sky,8);sky.addView(view,a.lp(-1,-2));a.gap(sky,10);TextParagraphs.append(a,sky,view.chartNote(),12,a.MUTED);
    }
    private void timeControl(){
        LinearLayout card=a.card();card.addView(a.text(UiText.get("astronomy_106"),17,a.TITLE,true));a.gap(card,12);
        LinearLayout clock=a.row();timeButton(clock,UiText.get("astronomy_122"),()->change.accept(at.minusSeconds(900)));timeButton(clock,UiText.get("astronomy_123"),()->change.accept(Instant.now()));timeButton(clock,UiText.get("astronomy_124"),()->change.accept(at.plusSeconds(900)));card.addView(clock);a.gap(card,8);
        LinearLayout days=a.row();timeButton(days,UiText.get("astronomy_125"),()->change.accept(at.atZone(zone).minusDays(1).toInstant()));timeButton(days,UiText.get("astronomy_126"),()->NumericInputs.dateTime(a,at.atZone(zone),change));timeButton(days,UiText.get("astronomy_127"),()->change.accept(at.atZone(zone).plusDays(1).toInstant()));card.addView(days);
    }
    private void observing(){
        LinearLayout c=a.card();c.addView(a.text(UiText.get("astronomy_128"),17,a.TITLE,true));a.gap(c,12);
        double sun=Astronomy.sunPosition(at,city.latitude,city.longitude)[0],moon=Astronomy.moonPosition(at,city.latitude,city.longitude)[0],lit=Astronomy.illumination(at);
        c.addView(a.text(Astronomy.skyDescription(at,city.latitude,city.longitude),16,a.INK,true));a.gap(c,10);
        c.addView(a.text(moon>0?UiText.get("astronomy_129")+Math.round(lit*100)+"%":UiText.get("astronomy_130"),13,a.MUTED,false));a.gap(c,10);
        if(data==null||data.currentIsOld(Instant.now())){c.addView(a.text(UiText.get("astronomy_131"),13,a.MUTED,false));return;}
        int best=-1;long delta=Long.MAX_VALUE;org.json.JSONArray times=data.hourly.optJSONArray("time");
        for(int i=0;i<times.length();i++)try{long d=Math.abs(Duration.between(LocalDateTime.parse(times.getString(i)).atZone(zone).toInstant(),at).toMinutes());if(d<delta){delta=d;best=i;}}catch(Exception ignored){}
        double cloud=WeatherData.at(data.hourly,"cloud_cover",best),rain=WeatherData.at(data.hourly,"precipitation_probability",best),wind=WeatherData.at(data.hourly,"wind_speed_10m",best),humidity=WeatherData.at(data.hourly,"relative_humidity_2m",best),visibility=WeatherData.at(data.hourly,"visibility",best);
        if(delta>90||!Double.isFinite(cloud)||!Double.isFinite(rain)||!Double.isFinite(wind)||!Double.isFinite(humidity)||!Double.isFinite(visibility)){c.addView(a.text(UiText.get("astronomy_132"),13,a.MUTED,false));return;}
        c.addView(a.text(UiText.get("astronomy_133")+WeatherData.number(cloud)+UiText.get("astronomy_134")+WeatherData.number(rain)+UiText.get("astronomy_135")+WeatherData.number(humidity)+UiText.get("astronomy_136")+WeatherData.number(wind)+UiText.get("astronomy_137")+String.format(Locale.ROOT,"%.1f",visibility/1000)+" km",13,a.INK,false));a.gap(c,10);
        boolean good=sun<-12&&cloud<=40&&rain<=20&&wind<=25&&humidity<=85&&visibility>=8000;
        c.addView(a.text(good?UiText.get("astronomy_138"):UiText.get("astronomy_139"),13,good?0xFF287C60:0xFF8B6021,false));a.gap(c,8);c.addView(a.text(UiText.get("astronomy_140"),11,a.MUTED,false));
    }
    private void moonInfo(){
        LinearLayout c=a.card();c.addView(a.text(UiText.get("astronomy_110"),17,a.TITLE,true));a.gap(c,10);double phase=Astronomy.phase(at);
        c.addView(new MoonPhase(a,phase),a.lp(-1,160));a.gap(c,10);
        c.addView(a.text(Astronomy.phaseName(phase)+UiText.get("astronomy_141")+Math.round(Astronomy.illumination(at)*100)+"%",21,a.INK,true));a.gap(c,10);
        c.addView(a.text(String.format(Locale.ROOT,UiText.get("astronomy_142"),phase*29.53059,(.5-phase+1)%1*29.53059),12,a.MUTED,false));a.gap(c,10);
        c.addView(a.text(UiText.get("astronomy_143"),12,a.MUTED,false));
    }
    private static final class MoonPhase extends View {
        private final Paint paint=new Paint(3);private final double phase;
        MoonPhase(MainActivity a,double phase){super(a);this.phase=phase;setContentDescription(UiText.get("astronomy_144")+Astronomy.phaseName(phase)+UiText.get("astronomy_141")+Math.round((1-Math.cos(phase*Math.PI*2))*50)+"%");}
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
        LinearLayout c=a.card();c.addView(a.text(UiText.get("astronomy_111"),17,a.TITLE,true));a.gap(c,14);double[] sun=Astronomy.sunPosition(at,city.latitude,city.longitude),moon=Astronomy.moonPosition(at,city.latitude,city.longitude);
        c.addView(a.text(UiText.get("astronomy_145")+String.format(Locale.ROOT,UiText.get("astronomy_146"),sun[0],sun[1])+" "+Astronomy.compass(sun[1]),14,a.INK,false));a.gap(c,10);c.addView(a.text(UiText.get("astronomy_147")+String.format(Locale.ROOT,UiText.get("astronomy_146"),moon[0],moon[1])+" "+Astronomy.compass(moon[1]),14,a.INK,false));a.gap(c,10);
        for(Astronomy.Planet p:Astronomy.planets(at)){double[] h=Astronomy.planetPosition(p,at,city.latitude,city.longitude);c.addView(a.text(UiText.label(p.name)+"  "+String.format(Locale.ROOT,UiText.get("astronomy_148"),h[0],h[1],p.magnitude),13,a.INK,false));a.gap(c,7);}

    }
    private String event(List<Astronomy.Event> events,boolean rise){for(Astronomy.Event e:events)if(e.rising==rise)return e.at.atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm"));return UiText.get("astronomy_149");}
    private void events(){
        LinearLayout c=a.card();c.addView(a.text(UiText.get("astronomy_112"),17,a.TITLE,true));a.gap(c,14);LocalDate date=at.atZone(zone).toLocalDate();String[] labels={UiText.get("astronomy_150"),UiText.get("astronomy_151"),UiText.get("astronomy_152"),UiText.get("astronomy_153"),UiText.get("astronomy_154")};double[] alt={-.833,-6,-12,-18,-.3};
        for(int n=0;n<labels.length;n++){List<Astronomy.Event> events=Astronomy.crossings(date,zone,city.latitude,city.longitude,n==4,alt[n]);c.addView(a.text(labels[n],12,a.MUTED,false));a.gap(c,7);c.addView(a.text(event(events,true)+"  /  "+event(events,false),15,a.INK,true));a.gap(c,16);}c.addView(a.text(UiText.get("astronomy_155"),11,a.MUTED,false));
    }
    private void meteor(){
        LinearLayout c=a.card();c.addView(a.text(UiText.get("astronomy_156"),17,a.TITLE,true));a.gap(c,12);LocalDate today=at.atZone(zone).toLocalDate();List<String> active=new ArrayList<>(),upcoming=new ArrayList<>();
        List<Astronomy.Shower> next=new ArrayList<>();
        for(Astronomy.Shower m:Astronomy.SHOWERS){
            if(m.activeOn(today)){double[] h=Astronomy.horizontal(m.ra,m.dec,at,city.latitude,city.longitude);active.add(UiText.label(m.name)+UiText.get("astronomy_157")+m.peak.toString().substring(2)+UiText.get("astronomy_158")+String.format(Locale.ROOT,"%.1f°",h[0])+UiText.get("astronomy_159")+String.format(Locale.ROOT,"%.1f°",h[1])+" "+Astronomy.compass(h[1]));}
            else next.add(m);
        }
        next.sort(Comparator.comparing(m->m.nextPeak(today)));for(Astronomy.Shower m:next){if(upcoming.size()==4)break;upcoming.add(m.nextPeak(today).toString()+"  "+UiText.label(m.name));}
        if(!active.isEmpty()){c.addView(a.text(UiText.get("astronomy_160"),13,a.TITLE,true));a.gap(c,8);for(String item:active){c.addView(a.text(item,14,a.INK,false));a.gap(c,10);}}
        if(active.isEmpty()){c.addView(a.text(UiText.get("astronomy_161"),13,a.MUTED,false));a.gap(c,8);}if(!upcoming.isEmpty()){c.addView(a.text(UiText.get("astronomy_162"),13,a.MUTED,true));a.gap(c,8);for(String item:upcoming){c.addView(a.text(item,14,a.INK,false));a.gap(c,9);}}
        c.addView(a.text(UiText.get("astronomy_163"),12,a.MUTED,false));a.link(c,UiText.get("astronomy_164"),"https://www.imo.net/resources/calendar/");
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
            targets.add(new Target(UiText.raw("identity_astronomy_081"),3,at,lat,lon));targets.add(new Target(UiText.raw("identity_astronomy_082"),4,at,lat,lon));
            setAligned(a.preferences.getBoolean("astro_align_sky",false));
            updateDescription();setClickable(true);
        }
        void setAligned(boolean on){
            if(sensors!=null){sensors.unregisterListener(this);sensors=null;}
            aligned=on&&SkyPointing.supported(mode);aimAltitude=aimAzimuth=Double.NaN;
            if(aligned){sensors=(SensorManager)a.getSystemService(android.content.Context.SENSOR_SERVICE);Sensor rotation=sensors==null?null:sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);if(rotation!=null)sensors.registerListener(this,rotation,SensorManager.SENSOR_DELAY_GAME);}
            invalidate();
        }
        private void updateDescription(){setContentDescription(UiText.get("astronomy_167")+(mode==1?UiText.get("astronomy_168"):mode==2?UiText.get("astronomy_169"):UiText.get("astronomy_170"))+(selected.isEmpty()?"":UiText.get("astronomy_171")+UiText.label(selected)));}
        String chartNote(){return (mode==1?UiText.get("astronomy_172"):mode==2?UiText.get("astronomy_173"):UiText.get("astronomy_174"))+"\n\n"+a.getString(R.string.sky_objects_note)+"\n\n"+a.getString(R.string.sky_aim_note);}
        private boolean enabled(Target t){return SkyVisibility.visible(a.preferences.getInt("astro_visibility_mode",0)==1,t.key==null||a.preferences.getBoolean(t.key,true),t.mag,a.preferences.getFloat("astro_magnitude_limit",6),t.key==null);}
        private Target selection(){for(Target t:targets)if(t.name.equals(selected)&&t.selectable)return t;return null;}
        String selectionDescription(){Target t=selection();if(t==null)return UiText.get("astronomy_175");
            return UiText.label(t.name)+(t.kind==2?UiText.get("astronomy_176"):"")+UiText.get("astronomy_177")+String.format(Locale.ROOT,"%.1f°",t.alt)+UiText.get("astronomy_159")+String.format(Locale.ROOT,"%.1f°",t.az)+" "+Astronomy.compass(t.az)+UiText.get("astronomy_178")+String.format(Locale.ROOT,"%.2fh",t.ra/15)+UiText.get("astronomy_179")+String.format(Locale.ROOT,"%.2f°",t.dec)+(t.kind==3?"":t.kind==4?(Double.isFinite(t.mag)?UiText.get("astronomy_180")+String.format(Locale.ROOT,"%.1f",t.mag)+UiText.get("astronomy_181"):UiText.get("astronomy_182")):(t.kind==2?UiText.get("astronomy_183")+SkyVisibility.magnitude(t.mag)+UiText.get("astronomy_184"):UiText.get("astronomy_185")+String.format(Locale.ROOT,"%.2f",t.mag)))+(t.alt<0?UiText.get("astronomy_186"):!enabled(t)?UiText.get("astronomy_187"):"");}
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
            if(mode==0){for(int i=1;i<=2;i++)c.drawCircle(cx,cy,r*i/3,p);for(int i=0;i<4;i++){double z=Math.toRadians(i*90);c.drawLine(cx,cy,cx+(float)Math.sin(z)*r,cy+(float)Math.cos(z)*r,p);}label(c,UiText.get("astronomy_026"),cx,cy-r-a.dp(5),0xFFADC7E7,11);label(c,UiText.get("astronomy_030"),cx,cy+r+a.dp(15),0xFFADC7E7,11);label(c,UiText.get("astronomy_028"),cx-r-a.dp(12),cy,0xFFADC7E7,11);label(c,UiText.get("astronomy_032"),cx+r+a.dp(12),cy,0xFFADC7E7,11);}
            if(mode==1){for(int d=-60;d<=60;d+=30){p.setStyle(Paint.Style.STROKE);p.setColor(0xFF4D627A);c.drawCircle(cx,cy,r*(90-d)/180,p);label(c,d+"°",cx+a.dp(3),cy-r*(90-d)/180,0xFF7E96B2,9);}for(int h=0;h<24;h+=3){double z=Math.toRadians(h*15);p.setStyle(Paint.Style.STROKE);p.setColor(0xFF4D627A);c.drawLine(cx,cy,cx-(float)Math.sin(z)*r,cy-(float)Math.cos(z)*r,p);label(c,h+"h",cx-(float)Math.sin(z)*(r+a.dp(12)),cy-(float)Math.cos(z)*(r+a.dp(12))+a.dp(3),0xFFADC7E7,9);}label(c,UiText.get("astronomy_188"),cx,cy+a.dp(11),0xFFADC7E7,9);
                Path horizon=new Path();for(int az=0;az<=360;az+=2){double[] eq=SkyGeometry.equatorial(0,az,at,lat,lon);float[] q=xy(cx,cy,r,eq[0],eq[1],0,az);if(az==0)horizon.moveTo(q[0],q[1]);else horizon.lineTo(q[0],q[1]);}p.setStyle(Paint.Style.STROKE);p.setColor(0xFF66CFC4);c.drawPath(horizon,p);
            }p.setStyle(Paint.Style.FILL);
        }
        @Override protected void onDraw(Canvas c){float cx=getWidth()/2f,cy=getHeight()/2f,r=chartRadius();hits.clear();labelBoxes.clear();labels.clear();labelColors.clear();c.drawColor(0xFF13253D);grid(c,cx,cy,r);
            Hit chosen=null;
            for(Target t:targets){if(!enabled(t)||t.alt<0)continue;float[] q=xy(cx,cy,r,t.ra,t.dec,t.alt,t.az);p.setStyle(Paint.Style.FILL);p.setColor(t.kind==3?0xFFFFCB67:t.kind==4?0xFFBCD3FF:t.kind==1?0xFFFFD36B:t.kind==2?0xFF92E4D6:0xFFE9F2FF);
                if(t.kind==2){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(a.dp(1));if(t.name.contains(UiText.raw("identity_astronomy_083")))c.drawCircle(q[0],q[1],a.dp(5),p);else c.drawOval(q[0]-a.dp(7),q[1]-a.dp(3),q[0]+a.dp(7),q[1]+a.dp(3),p);p.setStyle(Paint.Style.FILL);}else c.drawCircle(q[0],q[1],a.dp(t.kind>=3?4:t.kind==1?3:(float)Math.max(1.2,2.4-t.mag*.3)),p);
                objectLabel(UiText.label(t.name),q[0],q[1],t.name.equals(selected)?0xFF8CEAF5:p.getColor());
                if(t.selectable){Hit hit=new Hit(t,q[0],q[1]);hits.add(hit);if(t.name.equals(selected))chosen=hit;}
            }
            for(Astronomy.Shower meteor:Astronomy.SHOWERS){if(!meteor.activeOn(localDate))continue;double[] h=Astronomy.horizontal(meteor.ra,meteor.dec,at,lat,lon);if(h[0]<0)continue;float[] q=xy(cx,cy,r,meteor.ra,meteor.dec,h[0],h[1]);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(a.dp(1));p.setColor(0xFFFF9B54);c.drawCircle(q[0],q[1],a.dp(5),p);c.drawLine(q[0]-a.dp(7),q[1],q[0]+a.dp(7),q[1],p);c.drawLine(q[0],q[1]-a.dp(7),q[0],q[1]+a.dp(7),p);objectLabel(String.format(Locale.ROOT,UiText.get("meteor_radiant_label"),UiText.label(meteor.name)),q[0],q[1],0xFFFF9B54);}
            drawObjectLabels(c);
            double[] aim=aligned?SkyPointing.marker(mode,aimAltitude,aimAzimuth):null;
            if(aim!=null){float x=cx+(float)aim[0]*r,y=cy+(float)aim[1]*r;p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeWidth(a.dp(1.5f));p.setColor(0xFFE53935);c.drawLine(x-a.dp(14),y,x+a.dp(14),y,p);c.drawLine(x,y-a.dp(14),x,y+a.dp(14),p);p.setStyle(Paint.Style.FILL);}
            if(chosen!=null){c.save();c.rotate(SkyGeometry.selectionAngle(android.os.SystemClock.uptimeMillis()),chosen.x,chosen.y);p.setColor(0xFF8CEAF5);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(a.dp(1.5f));for(int arm=0;arm<4;arm++){c.drawLine(chosen.x+a.dp(8),chosen.y,chosen.x+a.dp(16),chosen.y,p);c.rotate(90,chosen.x,chosen.y);}c.restore();p.setStyle(Paint.Style.FILL);if(isShown()&&hasWindowFocus()&&getWindowVisibility()==VISIBLE)postInvalidateDelayed(33);}
        }
        @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(focused)invalidate();}
        @Override public void onSensorChanged(SensorEvent event){if(!aligned||event.sensor.getType()!=Sensor.TYPE_ROTATION_VECTOR)return;float[] matrix=new float[9];SensorManager.getRotationMatrixFromVector(matrix,event.values);updatePointing(matrix);}
        private void updatePointing(float[] matrix){double declination=new GeomagneticField((float)lat,(float)lon,0,System.currentTimeMillis()).getDeclination();double[] direction=SkyPointing.direction(matrix,"top".equals(a.preferences.getString("astro_orientation_reference","camera")),a.getWindowManager().getDefaultDisplay().getRotation(),declination);aimAltitude=direction[0];aimAzimuth=direction[1];invalidate();}
        @Override public void onAccuracyChanged(Sensor sensor,int accuracy){}
        @Override protected void onDetachedFromWindow(){if(sensors!=null)sensors.unregisterListener(this);super.onDetachedFromWindow();}
        @Override public boolean onTouchEvent(MotionEvent e){if(e.getActionMasked()==MotionEvent.ACTION_DOWN){downX=e.getX();downY=e.getY();dragged=false;return true;}if(e.getActionMasked()==MotionEvent.ACTION_MOVE){if(Math.hypot(e.getX()-downX,e.getY()-downY)>ViewConfiguration.get(getContext()).getScaledTouchSlop())dragged=true;return true;}if(e.getActionMasked()==MotionEvent.ACTION_CANCEL){dragged=true;return true;}if(e.getActionMasked()!=MotionEvent.ACTION_UP)return true;if(dragged)return true;
            Hit best=null;float d=Float.MAX_VALUE;for(Hit h:hits){float dx=e.getX()-h.x,dy=e.getY()-h.y,dist=dx*dx+dy*dy;if(dist<d){d=dist;best=h;}}float threshold=a.dp(24);if(best!=null&&d<=threshold*threshold){selected=best.target.name;a.preferences.edit().putString("astro_selected",selected).apply();select.accept(selectionDescription());updateDescription();invalidate();performClick();}return true;}
        @Override public boolean performClick(){super.performClick();return true;}

    }
}
