package org.simpleweather.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;

/** Six explicit text roles; semantic diagram and warning colors are preserved. */
final class UiColors {
    static final String BUTTON="ui_button_color",LINK="ui_link_color",BODY="ui_body_color",TITLE="ui_title_color",CHOICE="ui_choice_color",NOTE="ui_note_color";
    private static int stored(SharedPreferences p,String key,int fallback){try{return ThemeColors.opaque(p.getInt(key,fallback));}catch(ClassCastException e){return fallback;}}
    static int seed(Context c,String role){SharedPreferences p=c.getSharedPreferences("toolbox",0);int fallback=ThemeColors.roleDefault(role);if(BUTTON.equals(role))fallback=stored(p,"theme_color",fallback);return stored(p,role,fallback);}
    static int ink(Context c,String role){return ThemeColors.dark(seed(c,role));}
    static int pale(Context c,String role){return ThemeColors.pale(seed(c,role));}
    static void mark(TextView v,String role){v.setTag(role);v.setTextColor(ink(v.getContext(),role));}
    static void decorate(Context c,View root){decorate(c,root,false);}
    private static void decorate(Context c,View v,boolean choices){
        String tag=v.getTag() instanceof String?(String)v.getTag():"";
        boolean primary="theme-primary".equals(tag),secondary="theme-secondary".equals(tag),link=LINK.equals(tag)||"theme-link-button".equals(tag);
        boolean button=primary||secondary||"theme-link-button".equals(tag)||v instanceof Button&&!(v instanceof CompoundButton);
        boolean choice=choices||v instanceof CompoundButton||v instanceof CheckedTextView;
        int titleId=c.getResources().getIdentifier("alertTitle","id","android");
        String role=link?LINK:tag.startsWith("ui_")?tag:button?BUTTON:v instanceof EditText?BODY:choice?CHOICE:v.getId()==titleId?TITLE:BODY;
        int color=primary?Color.WHITE:ink(c,role),buttonColor=ink(c,BUTTON);float density=c.getResources().getDisplayMetrics().density;
        if(v instanceof TextView&&!"text-fixed".equals(tag)){((TextView)v).setTextColor(color);if(v instanceof EditText)((EditText)v).setHintTextColor(ink(c,NOTE));}
        if(v instanceof CompoundButton){((CompoundButton)v).setButtonTintList(ColorStateList.valueOf(ThemeColors.BLUE));if(v instanceof Switch){((Switch)v).setThumbTintList(ColorStateList.valueOf(ThemeColors.BLUE));((Switch)v).setTrackTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{0x99246BCE,0x33246BCE}));}}
        if(v instanceof CheckedTextView)((CheckedTextView)v).setCheckMarkTintList(ColorStateList.valueOf(ThemeColors.BLUE));
        if(v instanceof SeekBar){((SeekBar)v).setProgressTintList(ColorStateList.valueOf(buttonColor));((SeekBar)v).setThumbTintList(ColorStateList.valueOf(buttonColor));}
        if(button||v instanceof EditText||v instanceof Spinner){SharedPreferences p=c.getSharedPreferences("toolbox",0);GradientDrawable shape=new GradientDrawable();shape.setColor(v instanceof EditText?Color.WHITE:link?pale(c,LINK):primary?buttonColor:pale(c,BUTTON));shape.setCornerRadius(10*density);if(p.getBoolean("outline",false))shape.setStroke(Math.max(1,Math.round(density)),p.getInt("outline_color",Color.BLACK));v.setBackground(new RippleDrawable(ColorStateList.valueOf(0x223577CC),shape,null));if(v instanceof Button||v instanceof EditText)v.setPadding(Math.max(v.getPaddingLeft(),Math.round(14*density)),Math.max(v.getPaddingTop(),Math.round(6*density)),Math.max(v.getPaddingRight(),Math.round(14*density)),Math.max(v.getPaddingBottom(),Math.round(6*density)));}
        if(v instanceof ViewGroup){final boolean childChoices=choices||v instanceof ListView||v instanceof Spinner||v instanceof RadioGroup;((ViewGroup)v).setOnHierarchyChangeListener(new ViewGroup.OnHierarchyChangeListener(){public void onChildViewAdded(View parent,View child){decorate(c,child,childChoices);}public void onChildViewRemoved(View parent,View child){}});}
        if(v instanceof ViewGroup){ViewGroup group=(ViewGroup)v;boolean nested=choices||v instanceof ListView||v instanceof Spinner||v instanceof RadioGroup;for(int i=0;i<group.getChildCount();i++)decorate(c,group.getChildAt(i),nested);}
    }
}
