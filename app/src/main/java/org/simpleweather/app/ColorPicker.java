package org.simpleweather.app;

import android.app.AlertDialog;
import android.widget.*;
import java.util.*;
import java.util.function.IntConsumer;

/** Named presets and a validated hexadecimal color; selection retains its parent. */
final class ColorPicker {
    static String name(MainActivity a,int color){int n=ThemeColors.presetIndex(color);if(n>=0)return a.getResources().getStringArray(R.array.color_names_seven)[n];if(color==0xFF000000)return a.getString(R.string.color_black);if(color==ThemeColors.GRAY)return a.getString(R.string.color_gray);return ThemeColors.hex(color);}
    static void show(MainActivity a,String title,int selected,IntConsumer save){show(a,title,selected,ThemeColors.BLUE,false,false,save);}
    static void show(MainActivity a,String title,int selected,int defaultColor,boolean black,boolean gray,IntConsumer save){
        final int[] chosen={selected};List<Integer> colors=new ArrayList<>();for(int c:ThemeColors.PRESETS)colors.add(c);if(black)colors.add(0xFF000000);if(gray)colors.add(ThemeColors.GRAY);
        String[] labels=new String[colors.size()+1];int current=colors.size();for(int i=0;i<colors.size();i++){int c=colors.get(i);labels[i]=name(a,c)+(c==defaultColor?a.getString(R.string.color_default_suffix):"");if(selected==c)current=i;}labels[colors.size()]=a.getString(R.string.color_custom);
        new ToolDialog(a).setTitle(title).setSingleChoiceItems(labels,current,(d,n)->{if(n<colors.size()){chosen[0]=colors.get(n);save.accept(chosen[0]);}else custom(a,chosen[0],value->{chosen[0]=value;save.accept(value);int index=colors.indexOf(value);((AlertDialog)d).getListView().setItemChecked(index<0?colors.size():index,true);});}).setNegativeButton(a.getString(R.string.action_back),null).show();
    }
    private static void custom(MainActivity a,int selected,IntConsumer save){
        LinearLayout form=a.column();form.setPadding(a.dp(24),a.dp(8),a.dp(24),a.dp(12));EditText input=new EditText(a);input.setSingleLine(true);input.setText(ThemeColors.hex(selected));input.setHint("#246BCE");input.setSelectAllOnFocus(true);form.addView(input);
        AlertDialog dialog=new ToolDialog(a).setTitle(a.getString(R.string.color_hex_title)).setView(form).setPositiveButton(a.getString(R.string.action_save),null).setNegativeButton(a.getString(R.string.action_back),null).create();dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(b->{String raw=input.getText().toString().trim();if(!raw.matches("#[0-9a-fA-F]{6}")){a.toast(a.getString(R.string.color_invalid));return;}save.accept(ThemeColors.parse(raw));dialog.dismiss();}));dialog.show();
    }
}
