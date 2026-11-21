package org.daidai.modules.shared;

import android.app.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.*;
import android.widget.*;
import java.util.Locale;

/** Small reusable view helpers; no standalone package or generated R dependency. */
public final class Ui {
    /** Short transient notices omit a terminal full stop; diagnostic paragraphs stay intact. */
    public static Toast toast(android.content.Context context,String message,int duration) {
        String value=message==null?"":message.trim();
        if(value.length()<160&&value.indexOf('\n')<0&&!value.endsWith("..."))
            while(value.endsWith("。")||value.endsWith("."))value=value.substring(0,value.length()-1);
        return Toast.makeText(context,value,duration);
    }
    public static final int INK = Color.rgb(30, 46, 70), BLUE = Color.rgb(36, 107, 206);
    public static int dp(android.content.Context c, float v) { return Math.round(v*c.getResources().getDisplayMetrics().density); }
    public static LinearLayout column(android.content.Context c) {
        LinearLayout v=new LinearLayout(c); v.setOrientation(1); return v;
    }
    public static LinearLayout page(Activity a, String title, String subtitle) {
        LinearLayout page=column(a); page.setPadding(dp(a,18),dp(a,12),dp(a,18),dp(a,12));
        page.setBackgroundColor(Color.rgb(244,246,250));
        LinearLayout shell=column(a);
        shell.addView(page,new LinearLayout.LayoutParams(-1,0,1));
        button(shell,org.simpleweather.app.SettingsActivity.hostText(a,org.simpleweather.app.R.string.launcher_home),()->org.simpleweather.app.SettingsNavigation.desktop(a));
        a.setContentView(shell);
        if(Build.VERSION.SDK_INT>=30) {
            a.getWindow().setDecorFitsSystemWindows(false);
            shell.setOnApplyWindowInsetsListener((v,i)->{
                android.graphics.Insets bars=i.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.ime());
                v.setPadding(bars.left,bars.top,bars.right,bars.bottom); return i;
            });
        } else shell.setFitsSystemWindows(true);
        TextView heading=text(a,title,25); heading.setTypeface(null,Typeface.BOLD); page.addView(heading);
        page.addView(text(a,subtitle,13)); return page;
    }
    public static TextView text(android.content.Context c,String s,float size) {
        TextView v=new TextView(c); v.setText(s); v.setTextSize(size); v.setTextColor(INK);
        v.setPadding(0,dp(c,5),0,dp(c,5)); return v;
    }
    public static LinearLayout card(LinearLayout parent,String title) {
        android.content.Context c=parent.getContext(); LinearLayout box=column(c);
        GradientDrawable bg=new GradientDrawable(); bg.setColor(Color.WHITE); bg.setCornerRadius(dp(c,16));
        box.setBackground(bg); box.setPadding(dp(c,14),dp(c,10),dp(c,14),dp(c,10));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(c,12),0,0);
        parent.addView(box,p); if(!title.isEmpty()) { TextView h=text(c,title,18);h.setTypeface(null,Typeface.BOLD);box.addView(h); } return box;
    }
    public static LinearLayout scroll(LinearLayout parent) {
        ScrollView s=new ScrollView(parent.getContext()); s.setFillViewport(true);
        parent.addView(s,new LinearLayout.LayoutParams(-1,0,1)); LinearLayout inner=column(parent.getContext());s.addView(inner);return inner;
    }
    public static Button button(LinearLayout p,String label,Runnable action) {
        Button b=new Button(p.getContext()); b.setText(label);b.setTextSize(15);b.setAllCaps(false);
        b.setMinWidth(0); b.setMinimumWidth(0);b.setMinHeight(dp(p.getContext(),46));b.setMinimumHeight(dp(p.getContext(),46));
        styleButton(b,false);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(dp(p.getContext(),3),dp(p.getContext(),3),dp(p.getContext(),3),dp(p.getContext(),3));
        if(p.getContext() instanceof Activity && ((Activity)p.getContext()).getIntent().getBooleanExtra("settings_mode",false)){
            b.setMinHeight(dp(p.getContext(),48));b.setMinimumHeight(dp(p.getContext(),48));
            b.setTextSize(14);b.setTypeface(null,Typeface.BOLD);
            lp.setMargins(0,0,0,dp(p.getContext(),10));
        }
        p.addView(b,lp); b.setOnClickListener(v->action.run());return b;
    }
    public static void styleButton(Button b,boolean selected){
        b.setTag(selected?"theme-primary":"theme-secondary");
        b.setBackgroundTintList(null);
        b.setStateListAnimator(null);
        b.setElevation(0);
        org.simpleweather.app.UiColors.decorate(b.getContext(),b);
    }
    public static LinearLayout settingsForm(Activity a){
        LinearLayout form=column(a);
        form.setPadding(dp(a,18),dp(a,8),dp(a,18),dp(a,16));
        return form;
    }
    public static LinearLayout row(LinearLayout p) {LinearLayout r=new LinearLayout(p.getContext());r.setGravity(Gravity.CENTER_VERTICAL);p.addView(r);return r;}
    public static Button cell(LinearLayout r,String label,Runnable action) {
        Button b=button(r,label,action);LinearLayout.LayoutParams lp=(LinearLayout.LayoutParams)b.getLayoutParams();lp.width=0;lp.weight=1;return b;
    }
    public static EditText input(LinearLayout p,String hint,int type) {
        EditText e=new EditText(p.getContext());e.setTextSize(16);e.setTextColor(INK);e.setHint(hint);e.setInputType(type);p.addView(e,new LinearLayout.LayoutParams(-1,-2));return e;
    }
    public static void error(Activity a,Exception e){new AlertDialog.Builder(a).setTitle(org.daidai.modules.shared.Texts.get(a,"shared_1")).setMessage(e.getMessage()==null?e.toString():e.getMessage()).setPositiveButton(org.daidai.modules.shared.Texts.get(a,"shared_2"),null).show();}
    public static void note(Activity a,String title,String text){new AlertDialog.Builder(a).setTitle(title).setMessage(text).setPositiveButton(org.daidai.modules.shared.Texts.get(a,"shared_3"),null).show();}
}
