package org.simpleweather.app;

import android.graphics.*;
import android.location.Location;
import android.os.SystemClock;
import android.view.*;
import java.util.*;

/** Native canvas illustrations, independently drawn; no map tiles or third-party artwork. */
final class StatusViews {
    static abstract class Square extends View {
        final MainActivity a;final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        Square(MainActivity a){super(a);this.a=a;}
        @Override protected void onMeasure(int w,int h){int size=MeasureSpec.getSize(w);setMeasuredDimension(size,size);}
        void text(Canvas c,String t,float x,float y,int size,int color){p.setStyle(Paint.Style.FILL);p.setColor(color);p.setTextSize(a.dp(size));p.setTextAlign(Paint.Align.CENTER);c.drawText(t,x,y,p);}
        void circle(Canvas c,float x,float y,float r,int color,float width){p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(a.dp(width));c.drawCircle(x,y,r,p);p.setStyle(Paint.Style.FILL);}
        float[] point(float x,float y,float r,double degrees){double t=Math.toRadians(degrees);return new float[]{x+r*(float)Math.sin(t),y-r*(float)Math.cos(t)};}
    }
    static final class Compass extends Square {
        final StatusHardware hw;Compass(MainActivity a,StatusHardware hw){super(a);this.hw=hw;setContentDescription("指南针表盘 · 顶部代表屏幕上沿朝向");}
        @Override protected void onDraw(Canvas c){float x=getWidth()/2f,y=getHeight()/2f,r=getWidth()*.37f;circle(c,x,y,r,0xFFDCE5F1,2);
            boolean available=hw.headingAvailable();Location fix=LocationService.last(a);boolean wantTrue=a.preferences.getBoolean("status_true_north",false);double heading=wantTrue?hw.trueHeading(fix):hw.magneticHeading;
            if(!available||!Double.isFinite(heading)){text(c,"等待方向",x,y,19,a.MUTED);text(c,"请平放设备，远离磁性物体",x,y+a.dp(27),12,a.MUTED);return;}
            for(int angle=0;angle<360;angle+=15){float[] s=point(x,y,r,angle-heading),e=point(x,y,r-a.dp(angle%90==0?13:6),angle-heading);p.setStrokeWidth(a.dp(2));p.setColor(angle==0?0xFFD24646:a.MUTED);c.drawLine(s[0],s[1],e[0],e[1],p);}
            for(int i=0;i<4;i++){float[] q=point(x,y,r+a.dp(17),i*90-heading);text(c,new String[]{"北","东","南","西"}[i],q[0],q[1]+a.dp(5),16,i==0?0xFFD24646:a.INK);}
            text(c,StatusMath.direction(heading),x,y-a.dp(8),25,a.INK);text(c,String.format(Locale.ROOT,"%.1f°",StatusMath.norm(heading)),x,y+a.dp(25),21,a.BLUE);
            Path arrow=new Path();arrow.moveTo(x,y-r+a.dp(18));arrow.lineTo(x-a.dp(7),y-r+a.dp(32));arrow.lineTo(x+a.dp(7),y-r+a.dp(32));arrow.close();p.setColor(a.BLUE);c.drawPath(arrow,p);
            text(c,wantTrue?"真北":"磁北",x,y+r-a.dp(29),13,a.MUTED);
        }
    }
    static final class Treasure extends Square {
        final StatusHardware hw;final StatusPlaces places;Treasure(MainActivity a,StatusHardware hw,StatusPlaces places){super(a);this.hw=hw;this.places=places;setContentDescription("地点寻宝方位图 · 蓝点为保存的目标");}
        @Override protected void onDraw(Canvas c){float x=getWidth()/2f,y=getHeight()/2f,r=getWidth()*.37f;circle(c,x,y,r,0xFFDCE5F1,1);circle(c,x,y,r/2,0xFFDCE5F1,1);p.setColor(a.BLUE);c.drawCircle(x,y,a.dp(5),p);
            Location fix=LocationService.last(a);StatusPlace target=places.selected();double heading=hw.trueHeading(fix);boolean relative=Double.isFinite(heading);text(c,relative?"屏幕上沿":"真北向上",x,a.dp(20),13,a.MUTED);
            if(fix==null||target==null){text(c,fix==null?"请先获取当前位置":"请选择一个保存的地点",x,y+a.dp(30),14,a.MUTED);return;}
            double meters=StatusMath.distance(fix.getLongitude(),fix.getLatitude(),target.longitude,target.latitude),bearing=StatusMath.bearing(fix.getLongitude(),fix.getLatitude(),target.longitude,target.latitude);double range=meters<=100?100:meters<=1000?1000:Math.pow(10,Math.ceil(Math.log10(meters)));
            double display=relative?bearing-heading:bearing;if(!Double.isFinite(display))display=0;
            float[] q=point(x,y,(float)(r*Math.min(.95,meters/range)),display);p.setColor(a.BLUE);c.drawCircle(q[0],q[1],a.dp(8),p);circle(c,q[0],q[1],a.dp(12),0xFF99BBEE,1);
            for(int i=0;i<4;i++){float[] n=point(x,y,r+a.dp(15),i*90-(relative?heading:0));text(c,new String[]{"北","东","南","西"}[i],n[0],n[1]+a.dp(4),13,a.MUTED);}
            text(c,"外圈 "+StatusMath.distanceText(range),x,getHeight()-a.dp(8),12,a.MUTED);
        }
    }
    static final class Sky extends Square {
        final StatusHardware hw;String selected="";final Runnable changed;final Map<String,float[]> hits=new HashMap<>();
        Sky(MainActivity a,StatusHardware hw,Runnable changed){super(a);this.hw=hw;this.changed=changed;setContentDescription("定位卫星天空图 · 北上南下东右西左，圆周为地平线");setClickable(true);}
        static int color(int type){switch(type){case 1:return 0xFF246BCE;case 5:return 0xFFB64C37;case 6:return 0xFF337C68;case 3:return 0xFF8051B5;default:return 0xFF8A7021;}}
        @Override protected void onDraw(Canvas c){float x=getWidth()/2f,y=getHeight()/2f,r=getWidth()*.38f;hits.clear();for(int level=0;level<3;level++)circle(c,x,y,r*(3-level)/3,0xFFDCE5F1,1);
            double heading=0;boolean follow=a.preferences.getBoolean("status_satellite_rotate",true);if(follow){Location fix=LocationService.last(a);boolean trueNorth=a.preferences.getBoolean("status_true_north",false);double h=trueNorth?hw.trueHeading(fix):hw.magneticHeading;if(Double.isFinite(h))heading=h;}
            p.setColor(0xFFDCE5F1);p.setStrokeWidth(a.dp(1));float[] east=point(x,y,r,90-heading),west=point(x,y,r,270-heading),north=point(x,y,r,0-heading),south=point(x,y,r,180-heading);c.drawLine(west[0],west[1],east[0],east[1],p);c.drawLine(north[0],north[1],south[0],south[1],p);
            float[] qn=point(x,y,r+a.dp(12),0-heading),qs=point(x,y,r+a.dp(22),180-heading),qe=point(x,y,r+a.dp(17),90-heading),qw=point(x,y,r+a.dp(17),270-heading);text(c,"北",qn[0],qn[1]-a.dp(2),14,a.INK);text(c,"南",qs[0],qs[1]+a.dp(7),14,a.INK);text(c,"东",qe[0],qe[1]+a.dp(5),14,a.INK);text(c,"西",qw[0],qw[1]+a.dp(5),14,a.INK);
            text(c,"60°",x+a.dp(14),y-r/3+a.dp(13),10,a.MUTED);text(c,"30°",x+a.dp(14),y-r*2/3+a.dp(13),10,a.MUTED);
            boolean old=hw.gnssAt==0||SystemClock.elapsedRealtime()-hw.gnssAt>15000;
            if(old){text(c,"等待设备报告卫星",x,y+a.dp(6),15,a.MUTED);return;}
            List<SkyLabels.Candidate> labels=new ArrayList<>();Map<String,Integer> colors=new HashMap<>();
            for(StatusHardware.Satellite s:hw.shown()){if(!s.plotted())continue;double[] pos=StatusMath.sky(s.elevation,s.azimuth-heading);float xx=x+(float)pos[0]*r,yy=y+(float)pos[1]*r;int color=color(s.constellation);p.setColor(color);p.setStyle(s.used?Paint.Style.FILL:Paint.Style.STROKE);p.setStrokeWidth(a.dp(2));c.drawCircle(xx,yy,a.dp(7),p);p.setStyle(Paint.Style.FILL);p.setTextSize(a.dp(11));Paint.FontMetrics fm=p.getFontMetrics();String label=s.label();labels.add(new SkyLabels.Candidate(label,xx,yy,p.measureText(label),fm.ascent,fm.descent));colors.put(label,color);hits.put(s.key(),new float[]{xx,yy});if(s.key().equals(selected)){c.save();c.rotate((SystemClock.uptimeMillis()%12000L)*.03f,xx,yy);p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(a.dp(1.5f));for(int arm=0;arm<4;arm++){c.drawLine(xx+a.dp(9),yy,xx+a.dp(17),yy,p);c.rotate(90,xx,yy);}c.restore();p.setStyle(Paint.Style.FILL);}}
            List<SkyLabels.Box> reserved=Arrays.asList(new SkyLabels.Box(x-a.dp(12),y-r-a.dp(30),x+a.dp(12),y-r-a.dp(3)),new SkyLabels.Box(x-a.dp(12),y+r+a.dp(3),x+a.dp(12),y+r+a.dp(30)));
            for(SkyLabels.Placed label:SkyLabels.place(labels,reserved,getWidth(),getHeight(),a.dp(12),a.dp(2),a.dp(1)))text(c,label.candidate.id,label.candidate.x,label.baseline,11,colors.get(label.candidate.id));
            if(!selected.isEmpty()&&isShown()&&hasWindowFocus()&&getWindowVisibility()==VISIBLE)postInvalidateDelayed(50);
        }
        private float downX,downY;
        @Override public boolean onTouchEvent(android.view.MotionEvent e){if(e.getAction()==MotionEvent.ACTION_DOWN){downX=e.getX();downY=e.getY();return true;}if(e.getAction()==MotionEvent.ACTION_UP){if(Math.hypot(e.getX()-downX,e.getY()-downY)>a.dp(12))return true;double best=a.dp(22);String key="";for(Map.Entry<String,float[]> hit:hits.entrySet()){double d=Math.hypot(e.getX()-hit.getValue()[0],e.getY()-hit.getValue()[1]);if(d<best){best=d;key=hit.getKey();}}if(!key.isEmpty()){selected=key;changed.run();invalidate();performClick();}return true;}return true;}
        @Override public boolean performClick(){super.performClick();return true;}
    }
}
