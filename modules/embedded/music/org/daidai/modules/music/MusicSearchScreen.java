package org.daidai.modules.music;

import android.app.*;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import org.daidai.modules.shared.Ui;
import org.daidai.modules.shared.Texts;
import java.util.*;
import java.util.function.Supplier;

/** Search edits the library's selection; song-name taps open the library metadata editor. */
public final class MusicSearchScreen {
    public interface TrackEditor { void open(MusicLibrary.Track track,Runnable saved); }
    private MusicSearchScreen(){}
    private static String text(Context c,String key,Object...args){return Texts.get(c,key,args);}
    public static Dialog open(Activity activity,Supplier<List<MusicLibrary.Track>> source,Set<String> selection,TrackEditor editor,Runnable returned){
        return new Session(activity,source,selection,editor,returned).show();
    }
    private static final class Session {
        final Activity activity;
        final Supplier<List<MusicLibrary.Track>> source;
        final Set<String> selected;
        final TrackEditor editor;
        final Runnable returned;
        final Dialog dialog;
        final Map<CheckBox,String> checks=new LinkedHashMap<>();
        final Set<String> matches=new LinkedHashSet<>();
        LinearLayout groups;
        TextView summary,selectionSummary;
        Button selectAll;
        MusicSearch.Category category=MusicSearch.Category.TITLE;
        String applied="";
        boolean closed,syncing;
        int generation;
        Session(Activity activity,Supplier<List<MusicLibrary.Track>> source,Set<String> selection,TrackEditor editor,Runnable returned){
            this.activity=activity;this.source=source;selected=selection;this.editor=editor;this.returned=returned;
            dialog=new Dialog(activity,android.R.style.Theme_Material_Light_NoActionBar);
        }
        Dialog show(){
            LinearLayout page=Ui.column(activity);page.setPadding(Ui.dp(activity,18),Ui.dp(activity,12),Ui.dp(activity,18),Ui.dp(activity,12));page.setBackgroundColor(0xfff4f6fa);
            if(android.os.Build.VERSION.SDK_INT>=30){page.setOnApplyWindowInsetsListener((v,i)->{android.graphics.Insets bars=i.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.ime());v.setPadding(Ui.dp(activity,18)+bars.left,Ui.dp(activity,12)+bars.top,Ui.dp(activity,18)+bars.right,Ui.dp(activity,12)+bars.bottom);return i;});}
            page.addView(Ui.text(activity,text(activity,"music_search_title"),22));
            EditText query=Ui.input(page,text(activity,"music_search_input"),android.text.InputType.TYPE_CLASS_TEXT);query.setSingleLine(true);query.setImeOptions(EditorInfo.IME_ACTION_SEARCH);query.setPadding(Ui.dp(activity,12),Ui.dp(activity,10),Ui.dp(activity,12),Ui.dp(activity,10));
            LinearLayout actions=Ui.row(page);Button search=Ui.cell(actions,text(activity,"music_search_apply"),()->{}),clear=Ui.cell(actions,text(activity,"music_search_reset"),()->{});
            selectAll=Ui.button(page,text(activity,"music_search_select_matches"),()->{selected.addAll(matches);syncSelection();});
            selectionSummary=Ui.text(activity,"",14);page.addView(selectionSummary);
            page.addView(Ui.text(activity,text(activity,"music_search_category"),15));Button[] categories=new Button[6];String[] keys={"music_search_by_title","music_search_by_composer","music_search_by_arranger","music_search_by_vocalist","music_search_by_rating","music_search_by_bpm"};LinearLayout first=Ui.row(page),second=Ui.row(page);
            summary=Ui.text(activity,"",14);page.addView(summary);groups=Ui.scroll(page);
            for(int i=0;i<keys.length;i++){final int n=i;categories[i]=Ui.cell(i<3?first:second,text(activity,keys[i]),()->{category=MusicSearch.Category.values()[n];updateButtons(categories,category);render();});}
            Runnable apply=()->{applied=query.getText().toString().trim();render();};search.setOnClickListener(v->apply.run());clear.setOnClickListener(v->{query.setText("");applied="";render();});query.setOnEditorActionListener((v,id,event)->{if(id==EditorInfo.IME_ACTION_SEARCH){apply.run();return true;}return false;});updateButtons(categories,category);
            Ui.button(page,text(activity,"music_search_back"),dialog::dismiss);
            dialog.setOnDismissListener(d->{closed=true;generation++;checks.clear();if(returned!=null)returned.run();});
            dialog.setContentView(page);render();dialog.show();if(dialog.getWindow()!=null){dialog.getWindow().setLayout(-1,-1);dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);}
            return dialog;
        }
        void syncSelection(){
            syncing=true;try{for(Map.Entry<CheckBox,String> entry:checks.entrySet())entry.getKey().setChecked(selected.contains(entry.getValue()));}finally{syncing=false;}
            selectAll.setEnabled(!matches.isEmpty()&&!selected.containsAll(matches));
            selectionSummary.setText(text(activity,"music_search_selected_count",selected.size()));
        }
        void render(){
            if(closed||activity.isDestroyed())return;
            int current=++generation;groups.removeAllViews();checks.clear();matches.clear();
            List<MusicLibrary.Track> tracks=source.get();for(MusicLibrary.Track track:MusicSearch.filter(tracks,applied))matches.add(track.uri);
            List<MusicSearch.Group> result=MusicSearch.groups(tracks,applied,category);
            summary.setText(applied.isEmpty()?text(activity,"music_search_total",tracks.size()):text(activity,"music_search_matches",matches.size(),tracks.size()));
            if(result.isEmpty())groups.addView(Ui.text(activity,text(activity,"music_search_empty"),15));
            for(MusicSearch.Group group:result)new ResultGroup(group,current).add();
            syncSelection();
        }
        final class ResultGroup {
            final MusicSearch.Group group;
            final int searchGeneration;
            final List<CheckBox> owned=new ArrayList<>();
            final LinearLayout details=Ui.column(activity);
            int rowsGeneration;
            ResultGroup(MusicSearch.Group group,int generation){this.group=group;searchGeneration=generation;}
            void add(){
                boolean searching=!applied.isEmpty();String label=label(activity,group.key)+(searching?text(activity,"music_search_group_matches",group.matches.size(),group.all.size()):text(activity,"music_search_group_count",group.all.size()));
                details.setVisibility(View.GONE);final Button[] toggle={null};toggle[0]=Ui.button(groups,label,()->{
                    boolean open=details.getVisibility()!=View.VISIBLE;toggle[0].setTag(open);details.setVisibility(open?View.VISIBLE:View.GONE);rowsGeneration++;
                    for(CheckBox check:owned)checks.remove(check);owned.clear();details.removeAllViews();if(open)populate();
                });toggle[0].setTag(false);style(toggle[0],group.highlighted(applied));groups.addView(details);
            }
            void populate(){
                List<MusicLibrary.Track> visible=applied.isEmpty()?group.all:group.matches;
                if(visible.isEmpty()){details.addView(Ui.text(activity,text(activity,"music_search_group_no_match"),14));return;}
                final int rows=rowsGeneration;
                MusicRows.append(details,visible.size(),()->!closed&&generation==searchGeneration&&rowsGeneration==rows&&details.getVisibility()==View.VISIBLE,i->{
                    MusicLibrary.Track track=visible.get(i);LinearLayout row=Ui.row(details);
                    CheckBox check=new CheckBox(activity);check.setButtonTintList(ColorStateList.valueOf(Ui.BLUE));check.setContentDescription(text(activity,"music060_select_song",track.title));check.setChecked(selected.contains(track.uri));checks.put(check,track.uri);owned.add(check);row.addView(check);
                    check.setOnCheckedChangeListener((button,on)->{if(syncing)return;if(on)selected.add(track.uri);else selected.remove(track.uri);syncSelection();});
                    LinearLayout song=Ui.column(activity);row.addView(song,new LinearLayout.LayoutParams(0,-2,1));
                    MusicCredits.songRow(song,track,track.title,()->editor.open(track,Session.this::render));
                });
            }
        }
    }
    private static void updateButtons(Button[] buttons,MusicSearch.Category selected){for(int i=0;i<buttons.length;i++)style(buttons[i],MusicSearch.Category.values()[i]==selected);}
    private static void style(Button button,boolean selected){Context c=button.getContext();GradientDrawable bg=new GradientDrawable();bg.setColor(selected?0xff246bce:0xffedf3fd);bg.setCornerRadius(Ui.dp(c,10));bg.setStroke(Ui.dp(c,1),selected?0xff246bce:0xffdce6f5);button.setBackground(bg);button.setTextColor(selected?Color.WHITE:0xff246bce);}
    private static String label(Context c,String key){if(key.equals("missing"))return text(c,"music_search_unset");if(key.startsWith("rating:"))return text(c,"music_search_rating_value",Integer.parseInt(key.substring(7)));if(key.equals("bpm:under100"))return text(c,"music_search_bpm_under100");if(key.equals("bpm:100-130"))return text(c,"music_search_bpm_100_130");if(key.equals("bpm:130-160"))return text(c,"music_search_bpm_130_160");if(key.equals("bpm:over160"))return text(c,"music_search_bpm_over160");String[] parts=key.split(":",2);String language=parts[0].equals("zh")?"music_search_lang_zh":parts[0].equals("en")?"music_search_lang_en":"music_search_lang_other";return text(c,"music_search_language_initial",text(c,language),parts.length>1?parts[1]:"#");}
}
