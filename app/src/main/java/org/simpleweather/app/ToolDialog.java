package org.simpleweather.app;
import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;

/** Apply the optional outline to platform dialog buttons and input regions too. */
final class ToolDialog extends AlertDialog.Builder {
    private static final java.util.WeakHashMap<AlertDialog,Boolean> OPEN=new java.util.WeakHashMap<>();
    ToolDialog(Context context){super(context);}
    @Override public AlertDialog create(){return prepare(super.create());}
    /** Inflate and style before the first measurement; keep caller OnShow validation intact. */
    static <T extends AlertDialog> T prepare(T dialog){
        dialog.create();OPEN.put(dialog,true);
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
        if(window!=null){decorate(context,window.getDecorView());window.getDecorView().post(()->decorate(context,window.getDecorView()));}
        return dialog;
    }
    @Override public AlertDialog show(){AlertDialog dialog=create();dialog.show();return dialog;}
    static void refresh(Context context){for(AlertDialog d:new java.util.ArrayList<>(OPEN.keySet()))if(d!=null&&d.isShowing()&&d.getWindow()!=null)decorate(d.getContext(),d.getWindow().getDecorView());}
    private static void decorate(Context context,View v){
        android.content.SharedPreferences prefs=context.getSharedPreferences("toolbox",0);int seed=prefs.getInt("theme_color",prefs.getInt("text_accent_color",ThemeColors.BLUE)),dark=ThemeColors.dark(seed),pale=ThemeColors.pale(seed);
        boolean primary="theme-primary".equals(v.getTag()),button=v instanceof Button&&!(v instanceof CompoundButton)||"theme-secondary".equals(v.getTag())||primary;
        if(v instanceof TextView){((TextView)v).setTextColor(primary?Color.WHITE:dark);if(v instanceof EditText)((EditText)v).setHintTextColor(dark);}
        if(v instanceof CompoundButton)((CompoundButton)v).setButtonTintList(android.content.res.ColorStateList.valueOf(dark));
        if(v instanceof CheckedTextView)((CheckedTextView)v).setCheckMarkTintList(android.content.res.ColorStateList.valueOf(dark));
        if(v instanceof SeekBar){((SeekBar)v).setProgressTintList(android.content.res.ColorStateList.valueOf(dark));((SeekBar)v).setThumbTintList(android.content.res.ColorStateList.valueOf(dark));}
        if(button||v instanceof EditText||v instanceof Spinner){float density=context.getResources().getDisplayMetrics().density;GradientDrawable shape=new GradientDrawable();shape.setColor(v instanceof EditText?Color.WHITE:primary?dark:pale);shape.setCornerRadius(10*density);if(prefs.getBoolean("outline",false))shape.setStroke(Math.max(1,Math.round(density)),prefs.getInt("outline_color",Color.BLACK));v.setBackground(new RippleDrawable(android.content.res.ColorStateList.valueOf(0x223577CC),shape,null));if(v instanceof Button)v.setPadding(Math.round(10*density),Math.round(6*density),Math.round(10*density),Math.round(6*density));}
        if(v instanceof ListView||v instanceof Spinner)((ViewGroup)v).setOnHierarchyChangeListener(new ViewGroup.OnHierarchyChangeListener(){public void onChildViewAdded(View parent,View child){decorate(context,child);}public void onChildViewRemoved(View parent,View child){}});
        if(v instanceof ViewGroup){ViewGroup group=(ViewGroup)v;for(int n=0;n<group.getChildCount();n++)decorate(context,group.getChildAt(n));}
    }
}
