package org.daidai.modules.music;

import android.content.*;
import android.view.Gravity;
import android.widget.*;
import java.util.*;
import org.daidai.modules.shared.*;

/** One presentation policy for song credits in the library, queue and search results. */
public final class MusicCredits {
    public static final String COMPOSER="composer",ARRANGER="arranger",VOCALIST="vocalist";
    public static final List<String> DEFAULT_ORDER=Collections.unmodifiableList(Arrays.asList(COMPOSER,ARRANGER,VOCALIST));
    private MusicCredits(){}
    public static ArrayList<String> order(Context c){String raw=c.getSharedPreferences("music_module",0).getString("credit_roles_order","composer,arranger,vocalist");ArrayList<String> order=new ArrayList<>();for(String id:raw.split(","))if(DEFAULT_ORDER.contains(id)&&!order.contains(id))order.add(id);for(String id:DEFAULT_ORDER)if(!order.contains(id))order.add(id);return order;}
    public static Set<String> visible(Context c){return new HashSet<>(c.getSharedPreferences("music_module",0).getStringSet("credit_roles_visible",new HashSet<>(Arrays.asList(COMPOSER,ARRANGER))));}
    public static void save(Context c,List<String> order,Set<String> visible){StringBuilder joined=new StringBuilder();for(String role:order)if(DEFAULT_ORDER.contains(role)){if(joined.length()>0)joined.append(',');joined.append(role);}HashSet<String> shown=new HashSet<>(visible);shown.retainAll(DEFAULT_ORDER);c.getSharedPreferences("music_module",0).edit().putString("credit_roles_order",joined.toString()).putStringSet("credit_roles_visible",shown).apply();}
    public static String label(Context c,String role){return Texts.get(c,role.equals(COMPOSER)?"music060_role_composer":role.equals(ARRANGER)?"music060_role_arranger":"music060_role_vocalist");}
    public static String value(MusicLibrary.Track t,String role){return role.equals(COMPOSER)?t.composer:role.equals(ARRANGER)?t.arranger:t.vocalist;}
    public static String format(Context c,MusicLibrary.Track t){Set<String> visible=visible(c);StringBuilder text=new StringBuilder();for(String role:order(c)){String names=MusicPeople.display(value(t,role),Texts.get(c,"music060_person_separator"));if(!visible.contains(role)||names.isEmpty())continue;if(text.length()>0)text.append(Texts.get(c,"music060_role_separator"));text.append(Texts.get(c,"music060_credit",label(c,role),names));}return text.toString();}
    public static Button songRow(LinearLayout host,MusicLibrary.Track track,String title,Runnable action){
        LinearLayout row=Ui.row(host);Button button=Ui.button(row,title,action);button.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);LinearLayout.LayoutParams titleLayout=(LinearLayout.LayoutParams)button.getLayoutParams();titleLayout.width=0;titleLayout.weight=1;
        String text=format(host.getContext(),track);if(!text.isEmpty()){TextView credits=Ui.text(host.getContext(),text,12);credits.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);credits.setTextAlignment(android.view.View.TEXT_ALIGNMENT_VIEW_END);credits.setPadding(Ui.dp(host.getContext(),8),Ui.dp(host.getContext(),6),Ui.dp(host.getContext(),4),Ui.dp(host.getContext(),6));row.addView(credits,new LinearLayout.LayoutParams(0,-2,1));}return button;
    }
}
