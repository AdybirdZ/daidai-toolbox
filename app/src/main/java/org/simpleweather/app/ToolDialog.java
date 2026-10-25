package org.simpleweather.app;
import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;

/** Apply the optional outline to platform dialog buttons and input regions too. */
final class ToolDialog extends AlertDialog.Builder {
    ToolDialog(Context context){super(context);}
    @Override public AlertDialog create(){return prepare(super.create());}
    /** Inflate and style before the first measurement; keep caller OnShow validation intact. */
    static <T extends AlertDialog> T prepare(T dialog){
        dialog.create();
        Window window=dialog.getWindow();
        if(window!=null)window.setWindowAnimations(0);
        Context context=dialog.getContext();
        int gap=Math.round(4*context.getResources().getDisplayMetrics().density);
        for(int which:new int[]{-1,-2,-3}){
            Button button=dialog.getButton(which);
            if(button==null||button.getVisibility()!=View.VISIBLE||!(button.getLayoutParams() instanceof ViewGroup.MarginLayoutParams))continue;
            ViewGroup.MarginLayoutParams params=(ViewGroup.MarginLayoutParams)button.getLayoutParams();
            params.leftMargin+=gap;params.rightMargin+=gap;params.topMargin+=gap;params.bottomMargin+=gap;
            button.setLayoutParams(params);
            if(button.getParent() instanceof ViewGroup){
                ViewGroup panel=(ViewGroup)button.getParent();
                panel.setPadding(panel.getPaddingLeft(),Math.max(panel.getPaddingTop(),gap*2),panel.getPaddingRight(),Math.max(panel.getPaddingBottom(),gap*2));
                panel.setClipToPadding(false);
            }
        }
        if(window!=null&&context.getSharedPreferences("toolbox",0).getBoolean("outline",false))decorate(context,window.getDecorView());
        return dialog;
    }
    @Override public AlertDialog show(){AlertDialog dialog=create();dialog.show();return dialog;}
    private static void decorate(Context context,View v){if(v instanceof Button&&!(v instanceof CompoundButton)||v instanceof EditText||v instanceof Spinner){float density=context.getResources().getDisplayMetrics().density;GradientDrawable shape=new GradientDrawable();shape.setColor(v instanceof EditText?Color.WHITE:0xFFEAF2FF);shape.setCornerRadius(10*density);shape.setStroke(Math.max(1,Math.round(density)),context.getSharedPreferences("toolbox",0).getInt("outline_color",Color.BLACK));v.setBackground(new RippleDrawable(android.content.res.ColorStateList.valueOf(0x223577CC),shape,null));v.setPadding(Math.round(10*density),Math.round(6*density),Math.round(10*density),Math.round(6*density));}if(v instanceof ViewGroup){ViewGroup group=(ViewGroup)v;for(int n=0;n<group.getChildCount();n++)decorate(context,group.getChildAt(n));}}
}
