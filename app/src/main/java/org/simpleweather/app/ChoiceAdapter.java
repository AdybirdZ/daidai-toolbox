package org.simpleweather.app;
import android.content.Context;
import android.view.*;
import android.widget.*;
/** Apply choice text color to both collapsed and popup spinner items. */
final class ChoiceAdapter extends ArrayAdapter<String> {
    ChoiceAdapter(Context c,String[] labels){super(c,android.R.layout.simple_spinner_dropdown_item,labels);}
    private View style(View v){if(v instanceof TextView)UiColors.mark((TextView)v,UiColors.CHOICE);return v;}
    @Override public View getView(int position,View convert,ViewGroup parent){return style(super.getView(position,convert,parent));}
    @Override public View getDropDownView(int position,View convert,ViewGroup parent){return style(super.getDropDownView(position,convert,parent));}
}
