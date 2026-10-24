package org.simpleweather.app;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Original vector drawing; no downloaded icon set or remote assets. */
public final class WeatherIcon extends View {
    private final int code;
    private final boolean isDay;
    private final Paint p = new Paint(3);
    public WeatherIcon(Context context, int code, boolean day) {
        super(context); this.code = code; this.isDay = day;
        setContentDescription(WeatherData.condition(code) + (day ? "" : "，夜间"));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    private void color(int c) { p.setColor(c); p.setStyle(Paint.Style.FILL); }
    private void cloud(Canvas c, int color) {
        color(color);
        c.drawRoundRect(16, 46, 86, 75, 15, 15, p);
        c.drawCircle(38, 45, 18, p); c.drawCircle(60, 48, 14, p);
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float size = Math.min(getWidth(), getHeight());
        canvas.save(); canvas.translate((getWidth()-size)/2, (getHeight()-size)/2); canvas.scale(size/100, size/100);
        if (code < 0) {
            cloud(canvas, 0xFFBDD1EB); color(0xFF38699F); p.setTextSize(30); p.setTextAlign(Paint.Align.CENTER); canvas.drawText("?", 51, 65, p);
            canvas.restore(); return;
        }
        if (code <= 2) {
            if (isDay) {
                color(0xFFFFBD55); canvas.drawCircle(61, 34, 18, p);
                p.setStrokeWidth(3.5f); p.setStrokeCap(Paint.Cap.ROUND);
                for (int i=0;i<8;i++) {
                    double a=i*Math.PI/4;
                    canvas.drawLine(61+(float)Math.cos(a)*25,34+(float)Math.sin(a)*25,61+(float)Math.cos(a)*29,34+(float)Math.sin(a)*29,p);
                }
            } else {
                color(0xFF7A91D8); canvas.drawCircle(60, 33, 21, p);
                color(0xFFEDF4FF); canvas.drawCircle(69, 25, 18, p);
            }
            if (code == 0) { canvas.restore(); return; }
        }
        cloud(canvas, code >= 51 ? 0xFF85A9D2 : 0xFFADC7E7);
        if (code == 45 || code == 48) {
            color(0xFF7B9CBE); p.setStrokeWidth(3); p.setStrokeCap(Paint.Cap.ROUND);
            canvas.drawLine(20,81,82,81,p); canvas.drawLine(30,89,72,89,p);
        } else if ((code >= 71 && code <= 77) || code == 85 || code == 86) {
            color(0xFF5B8ECA); p.setStrokeWidth(2); p.setStrokeCap(Paint.Cap.ROUND);
            for (int x=28;x<=76;x+=22) {
                canvas.drawLine(x,80,x,92,p); canvas.drawLine(x-5,83,x+5,89,p); canvas.drawLine(x-5,89,x+5,83,p);
            }
        } else if (code >= 95) {
            color(0xFFF3AF39); Path bolt=new Path();bolt.moveTo(52,65);bolt.lineTo(38,85);bolt.lineTo(50,85);bolt.lineTo(44,99);bolt.lineTo(65,77);bolt.lineTo(53,77);bolt.close();canvas.drawPath(bolt,p);
        } else if (code >= 51) {
            color(0xFF387BCC); p.setStrokeWidth(4); p.setStrokeCap(Paint.Cap.ROUND);
            for(int x=30;x<80;x+=21)canvas.drawLine(x,81,x-4,89,p);
        }
        canvas.restore();
    }
}
