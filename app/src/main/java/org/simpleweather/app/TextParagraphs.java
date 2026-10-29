package org.simpleweather.app;

import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Shared spacing for explanatory paragraphs, independent of font size. */
final class TextParagraphs {
    static final int GAP_DP=12;
    static final int LINE_SPACING_DP=4;

    static void append(MainActivity a,LinearLayout parent,String value,int size,int color){
        for(String part:value.replace("\r\n","\n").replace('\r','\n').trim().split("\n[\t ]*\n(?:[\t ]*\n)*")){
            if(part.trim().isEmpty())continue;
            TextView text=a.text(part.trim(),size,color,false);
            text.setLineSpacing(a.dp(LINE_SPACING_DP),1);
            parent.addView(text);
            a.gap(parent,GAP_DP);
        }
    }

    static ScrollView notice(MainActivity a,String value){
        LinearLayout content=a.column();
        content.setPadding(a.dp(22),a.dp(8),a.dp(22),a.dp(4));
        append(a,content,value,14,a.INK);
        ScrollView scroll=new ScrollView(a);scroll.addView(content);return scroll;
    }
}
