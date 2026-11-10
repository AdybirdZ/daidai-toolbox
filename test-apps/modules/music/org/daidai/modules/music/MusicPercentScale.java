package org.daidai.modules.music;
import android.content.Context;
import android.graphics.*;
import android.view.View;
import java.util.function.IntSupplier;
/** Percent ticks aligned with the standard seek bar track insets. */
final class MusicPercentScale extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private final IntSupplier maximum;
    MusicPercentScale(Context context,IntSupplier maximum){super(context);this.maximum=maximum;paint.setColor(0xff526278);paint.setTextSize(11*getResources().getDisplayMetrics().scaledDensity);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
    protected void onDraw(Canvas canvas){super.onDraw(canvas);float d=getResources().getDisplayMetrics().density,inset=16*d,width=getWidth()-2*inset;for(int i=0;i<=4;i++){float x=inset+width*i/4;canvas.drawLine(x,0,x,4*d,paint);String value=Integer.toString(Math.round(maximum.getAsInt()*i/4f));float tx=x-paint.measureText(value)/2;canvas.drawText(value,Math.max(0,Math.min(getWidth()-paint.measureText(value),tx)),18*d,paint);}}
}
