package org.simpleweather.app;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Original vector weather symbols. All marks below clouds are dark blue except lightning. */
public final class WeatherIcon extends View {
    private static final int PALE=0xFFADC7E7,DARK=0xFF85A9D2,BLUE=0xFF24538B;
    private final int code;
    private final boolean isDay;
    private final Paint p=new Paint(3);
    public WeatherIcon(Context context,int code,boolean day){super(context);this.code=code;isDay=day;setContentDescription(WeatherData.condition(code)+(day?"":"，夜间"));setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
    private void color(int value){p.setColor(value);p.setStyle(Paint.Style.FILL);}
    private void stroke(int value,float width){color(value);p.setStrokeWidth(width);p.setStrokeCap(Paint.Cap.ROUND);}
    private void cloud(Canvas c,int value){color(value);c.drawRoundRect(16,46,86,75,15,15,p);c.drawCircle(38,45,18,p);c.drawCircle(60,48,14,p);}
    private void sky(Canvas c,boolean clear){float cx=clear?50:(isDay?66:69),cy=clear?50:(isDay?30:27),r=clear?25:(isDay?17:20);
        if(isDay){color(0xFFFFBD55);c.drawCircle(cx,cy,r,p);p.setStrokeWidth(clear?4:3);p.setStrokeCap(Paint.Cap.ROUND);for(int i=0;i<8;i++){double angle=i*Math.PI/4;c.drawLine(cx+(float)Math.cos(angle)*(r+6),cy+(float)Math.sin(angle)*(r+6),cx+(float)Math.cos(angle)*(r+11),cy+(float)Math.sin(angle)*(r+11),p);}}
        else{color(0xFF7A91D8);Path moon=new Path();moon.addCircle(cx,cy,r+3,Path.Direction.CW);Path cut=new Path();cut.addCircle(cx+r*.5f,cy-r*.4f,r+1,Path.Direction.CW);moon.op(cut,Path.Op.DIFFERENCE);c.drawPath(moon,p);}}
    private void rain(Canvas c,int count){stroke(BLUE,3.8f);for(int i=0;i<count;i++){float x=51+(i-(count-1)/2f)*21;c.drawLine(x+3,81,x-3,92,p);}}
    private void snowflake(Canvas c,float x,float y,float r,int value){stroke(value,2.1f);for(int i=0;i<3;i++){double angle=i*Math.PI/3;c.drawLine(x-(float)Math.sin(angle)*r,y-(float)Math.cos(angle)*r,x+(float)Math.sin(angle)*r,y+(float)Math.cos(angle)*r,p);}}
    private void snow(Canvas c,int count){for(int i=0;i<count;i++)snowflake(c,51+(i-(count-1)/2f)*21,86,6,BLUE);}
    private void bolt(Canvas c){color(0xFFF3AF39);Path b=new Path();b.moveTo(52,65);b.lineTo(38,85);b.lineTo(50,85);b.lineTo(44,99);b.lineTo(65,77);b.lineTo(53,77);b.close();c.drawPath(b,p);}
    @Override protected void onDraw(Canvas c){super.onDraw(c);float size=Math.min(getWidth(),getHeight());c.save();c.translate((getWidth()-size)/2,(getHeight()-size)/2);c.scale(size/100,size/100);
        if(!WeatherData.isSupportedCode(code)){cloud(c,PALE);color(BLUE);p.setTextSize(30);p.setTextAlign(Paint.Align.CENTER);c.drawText("?",51,65,p);c.restore();return;}
        boolean shower=code==80||code==81||code==82||code==85||code==86;
        if(code<=2||shower){sky(c,code==0);if(code==0){c.restore();return;}}
        boolean drizzle=code==51||code==53||code==55||code==56||code==57;
        boolean pale=code<=48||drizzle||shower||code==77;
        if(code==1){c.save();c.translate(0,28);c.scale(.66f,.66f);cloud(c,PALE);c.restore();}else cloud(c,pale?PALE:DARK);
        if(code==45||code==48){if(code==48)snowflake(c,51,55,10,BLUE);stroke(BLUE,3);c.drawLine(20,81,82,81,p);c.drawLine(30,89,72,89,p);}
        else if(code==56||code==57||code==66||code==67){snowflake(c,51,55,10,pale?BLUE:0xFFF7FAFF);rain(c,code==67?3:1);}
        else if(code==77){snowflake(c,51,55,10,BLUE);color(BLUE);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2.5f);Path triangle=new Path();triangle.moveTo(51,80);triangle.lineTo(44,92);triangle.lineTo(58,92);triangle.close();c.drawPath(triangle,p);p.setStyle(Paint.Style.FILL);}
        else if(code==71||code==73||code==75){snow(c,code==71?1:code==73?2:3);}
        else if(code==85||code==86){snow(c,code==85?1:3);}
        else if(code==95||code==97){bolt(c);stroke(BLUE,3.8f);c.drawLine(29,81,23,92,p);c.drawLine(80,81,74,92,p);if(code==97){c.drawLine(18,81,12,92,p);c.drawLine(91,81,85,92,p);}}
        else if(code==96||code==99){bolt(c);color(BLUE);c.drawCircle(28,86,code==96?4:5,p);c.drawCircle(77,86,code==96?4:5,p);if(code==99){c.drawCircle(16,95,4.5f,p);c.drawCircle(88,95,4.5f,p);}}
        else if(drizzle||shower){rain(c,code==81?2:code==82?3:1);}
        else if(code==61||code==63||code==65){rain(c,code==61?1:code==63?2:3);}
        c.restore();
    }
}
