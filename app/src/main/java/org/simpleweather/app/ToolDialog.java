package org.simpleweather.app;
import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;

/** Apply the optional outline to platform dialog buttons and input regions too. */
final class ToolDialog extends AlertDialog.Builder {
    private final Context context;
    ToolDialog(Context context){super(context);this.context=context;}
    @Override public AlertDialog create(){AlertDialog d=super.create();View root=d.getWindow().getDecorView();root.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener(){public void onGlobalLayout(){root.getViewTreeObserver().removeOnGlobalLayoutListener(this);if(context.getSharedPreferences("toolbox",0).getBoolean("outline",true))decorate(root);}});return d;}
    @Override public AlertDialog show(){AlertDialog dialog=create();dialog.show();return dialog;}
    private void decorate(View v){if(v instanceof Button&&!(v instanceof CompoundButton)||v instanceof EditText||v instanceof Spinner){float density=context.getResources().getDisplayMetrics().density;GradientDrawable shape=new GradientDrawable();shape.setColor(v instanceof EditText?Color.WHITE:0xFFEAF2FF);shape.setCornerRadius(10*density);shape.setStroke(Math.max(1,Math.round(density)),context.getSharedPreferences("toolbox",0).getInt("outline_color",Color.BLACK));v.setBackground(new RippleDrawable(android.content.res.ColorStateList.valueOf(0x223577CC),shape,null));v.setPadding(Math.round(10*density),Math.round(6*density),Math.round(10*density),Math.round(6*density));}if(v instanceof ViewGroup){ViewGroup group=(ViewGroup)v;for(int n=0;n<group.getChildCount();n++)decorate(group.getChildAt(n));}}
}
