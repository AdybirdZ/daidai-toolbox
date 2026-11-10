package org.daidai.modules.music;

import android.app.*;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import org.daidai.modules.shared.Ui;
import org.daidai.modules.shared.Texts;
import java.util.*;

/** Separate full-screen search page; categories start collapsed on every render. */
public final class MusicSearchScreen {
    public interface TrackAction { void open(MusicLibrary.Track track); }
    private MusicSearchScreen(){}
    private static String text(Context c,String key,Object...args){return Texts.get(c,key,args);}
    public static Dialog open(Activity activity,List<MusicLibrary.Track> tracks,TrackAction action){
        Dialog dialog=new Dialog(activity,android.R.style.Theme_Material_Light_NoActionBar);
        LinearLayout page=Ui.column(activity);page.setPadding(Ui.dp(activity,18),Ui.dp(activity,12),Ui.dp(activity,18),Ui.dp(activity,12));page.setBackgroundColor(0xfff4f6fa);
        if(android.os.Build.VERSION.SDK_INT>=30){page.setOnApplyWindowInsetsListener((v,i)->{android.graphics.Insets bars=i.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.ime());v.setPadding(Ui.dp(activity,18)+bars.left,Ui.dp(activity,12)+bars.top,Ui.dp(activity,18)+bars.right,Ui.dp(activity,12)+bars.bottom);return i;});}
        TextView heading=Ui.text(activity,text(activity,"music_search_title"),22);page.addView(heading);
        EditText query=Ui.input(page,text(activity,"music_search_input"),android.text.InputType.TYPE_CLASS_TEXT);query.setSingleLine(true);query.setImeOptions(EditorInfo.IME_ACTION_SEARCH);query.setPadding(Ui.dp(activity,12),Ui.dp(activity,10),Ui.dp(activity,12),Ui.dp(activity,10));
        LinearLayout actions=Ui.row(page);Button search=Ui.cell(actions,text(activity,"music_search_apply"),()->{}),clear=Ui.cell(actions,text(activity,"music_search_reset"),()->{});
        page.addView(Ui.text(activity,text(activity,"music_search_category"),15));Button[] categoryButtons=new Button[6];String[] keys={"music_search_by_title","music_search_by_composer","music_search_by_arranger","music_search_by_vocalist","music_search_by_rating","music_search_by_bpm"};LinearLayout first=Ui.row(page),second=Ui.row(page);
        TextView summary=Ui.text(activity,"",14);page.addView(summary);LinearLayout groups=Ui.scroll(page);final MusicSearch.Category[] current={MusicSearch.Category.TITLE};final String[] applied={""};
        Runnable render=()->render(activity,groups,summary,tracks,applied[0],current[0],action);
        for(int i=0;i<keys.length;i++){final int n=i;categoryButtons[i]=Ui.cell(i<3?first:second,text(activity,keys[i]),()->{current[0]=MusicSearch.Category.values()[n];updateButtons(categoryButtons,current[0]);render.run();});}
        Runnable apply=()->{applied[0]=query.getText().toString().trim();render.run();};search.setOnClickListener(v->apply.run());clear.setOnClickListener(v->{query.setText("");applied[0]="";render.run();});query.setOnEditorActionListener((v,id,event)->{if(id==EditorInfo.IME_ACTION_SEARCH){apply.run();return true;}return false;});updateButtons(categoryButtons,current[0]);render.run();
        Ui.button(page,text(activity,"music_search_back"),dialog::dismiss);dialog.setContentView(page);dialog.show();if(dialog.getWindow()!=null){dialog.getWindow().setLayout(-1,-1);dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);}
        return dialog;
    }
    private static void updateButtons(Button[] buttons,MusicSearch.Category selected){for(int i=0;i<buttons.length;i++){boolean on=MusicSearch.Category.values()[i]==selected;style(buttons[i],on);}}
    private static void style(Button button,boolean selected){Context c=button.getContext();GradientDrawable bg=new GradientDrawable();bg.setColor(selected?0xff246bce:0xffedf3fd);bg.setCornerRadius(Ui.dp(c,10));bg.setStroke(Ui.dp(c,1),selected?0xff246bce:0xffdce6f5);button.setBackground(bg);button.setTextColor(selected?Color.WHITE:0xff246bce);}
    private static void render(Context c,LinearLayout groups,TextView summary,List<MusicLibrary.Track> source,String query,MusicSearch.Category category,TrackAction action){
        groups.removeAllViews();List<MusicSearch.Group> result=MusicSearch.groups(source,query,category);int matches=MusicSearch.filter(source,query).size();summary.setText(query.isEmpty()?text(c,"music_search_total",source.size()):text(c,"music_search_matches",matches,source.size()));
        if(result.isEmpty()){groups.addView(Ui.text(c,text(c,"music_search_empty"),15));return;}
        for(MusicSearch.Group group:result){boolean searching=!query.isEmpty(),hit=group.highlighted(query);String label=label(c,group.key)+(searching?text(c,"music_search_group_matches",group.matches.size(),group.all.size()):text(c,"music_search_group_count",group.all.size()));final Button[] button={null};LinearLayout details=Ui.column(c);details.setVisibility(View.GONE);button[0]=Ui.button(groups,label,()->{boolean open=details.getVisibility()!=View.VISIBLE;details.setVisibility(open?View.VISIBLE:View.GONE);button[0].setTag(open);});button[0].setTag(false);style(button[0],hit);groups.addView(details);
            List<MusicLibrary.Track> visible=searching?group.matches:group.all;if(visible.isEmpty())details.addView(Ui.text(c,text(c,"music_search_group_no_match"),14));for(MusicLibrary.Track track:visible)MusicCredits.songRow(details,track,track.title,()->action.open(track));
        }
    }
    private static String label(Context c,String key){if(key.equals("missing"))return text(c,"music_search_unset");if(key.startsWith("rating:"))return text(c,"music_search_rating_value",Integer.parseInt(key.substring(7)));if(key.equals("bpm:under100"))return text(c,"music_search_bpm_under100");if(key.equals("bpm:100-130"))return text(c,"music_search_bpm_100_130");if(key.equals("bpm:130-160"))return text(c,"music_search_bpm_130_160");if(key.equals("bpm:over160"))return text(c,"music_search_bpm_over160");String[] parts=key.split(":",2);String language=parts[0].equals("zh")?"music_search_lang_zh":parts[0].equals("en")?"music_search_lang_en":"music_search_lang_other";return text(c,"music_search_language_initial",text(c,language),parts.length>1?parts[1]:"#");}
}
