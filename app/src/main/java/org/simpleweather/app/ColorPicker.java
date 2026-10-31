package org.simpleweather.app;

import android.app.AlertDialog;
import android.widget.*;
import java.util.function.IntConsumer;

/** Presets and a validated hexadecimal color; selection keeps the parent page open. */
final class ColorPicker {
    static void show(MainActivity a,String title,int selected,IntConsumer save){
        final int[] chosen={selected};String[] names=a.getResources().getStringArray(R.array.seven_colors);String[] labels=java.util.Arrays.copyOf(names,names.length+1);labels[names.length]=a.getString(R.string.color_custom);int current=names.length;
        for(int i=0;i<ThemeColors.PRESETS.length;i++)if(selected==ThemeColors.PRESETS[i])current=i;
        new ToolDialog(a).setTitle(title).setSingleChoiceItems(labels,current,(d,n)->{if(n<ThemeColors.PRESETS.length){chosen[0]=ThemeColors.PRESETS[n];save.accept(chosen[0]);}else custom(a,chosen[0],value->{chosen[0]=value;save.accept(value);((AlertDialog)d).getListView().setItemChecked(names.length,true);});}).setNegativeButton(a.getString(R.string.action_back),null).show();
    }
    private static void custom(MainActivity a,int selected,IntConsumer save){
        LinearLayout form=a.column();form.setPadding(a.dp(24),a.dp(8),a.dp(24),a.dp(12));EditText input=new EditText(a);input.setSingleLine(true);input.setText(ThemeColors.hex(selected));input.setHint("#246BCE");input.setSelectAllOnFocus(true);form.addView(input);
        AlertDialog dialog=new ToolDialog(a).setTitle(a.getString(R.string.color_hex_title)).setView(form).setPositiveButton(a.getString(R.string.action_save),null).setNegativeButton(a.getString(R.string.action_back),null).create();dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(b->{String raw=input.getText().toString().trim();if(!raw.matches("#[0-9a-fA-F]{6}")){a.toast(a.getString(R.string.color_invalid));return;}save.accept(ThemeColors.parse(raw));dialog.dismiss();}));dialog.show();
    }
}
