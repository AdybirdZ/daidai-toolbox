package org.daidai.modules.music;

import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
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
        Context context=host.getContext();Button button=new SongButton(context,format(context,track));button.setText(title);button.setTextSize(15);button.setAllCaps(false);button.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);button.setMinWidth(0);button.setMinimumWidth(0);button.setMinHeight(Ui.dp(context,46));button.setMinimumHeight(Ui.dp(context,46));button.setTextColor(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{0xff8995a6,Ui.BLUE}));
        GradientDrawable shape=new GradientDrawable();shape.setColor(0xffedf3fd);shape.setCornerRadius(Ui.dp(context,10));shape.setStroke(Ui.dp(context,1),0xffdce6f5);button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22246bce),shape,null));button.setPadding(Ui.dp(context,10),Ui.dp(context,8),Ui.dp(context,10),Ui.dp(context,8));
        String credits=format(context,track);if(!credits.isEmpty())button.setContentDescription(title+"\n"+credits);LinearLayout.LayoutParams layout=new LinearLayout.LayoutParams(-1,-2);layout.setMargins(Ui.dp(context,3),Ui.dp(context,3),Ui.dp(context,3),Ui.dp(context,3));host.addView(button,layout);button.setOnClickListener(view->action.run());return button;
    }
    /** Both text columns belong to one Button, including its background, ripple and hit target. */
    private static final class SongButton extends Button {
        private final String credits;
        private StaticLayout titleLayout,creditLayout;
        private int titleWidth,gap;
        SongButton(Context context,String credits){super(context);this.credits=credits;setTextIsSelectable(false);}
        protected void onMeasure(int widthSpec,int heightSpec){
            super.onMeasure(widthSpec,heightSpec);if(credits.isEmpty())return;
            int width=getMeasuredWidth(),available=Math.max(2,width-getPaddingLeft()-getPaddingRight());gap=Math.min(Ui.dp(getContext(),12),available/5);int creditWidth=Math.max(1,(available-gap)*45/100);titleWidth=Math.max(1,available-gap-creditWidth);
            TextPaint titlePaint=new TextPaint(getPaint()),creditPaint=new TextPaint(getPaint());creditPaint.setTextSize(12*getResources().getDisplayMetrics().scaledDensity);
            titleLayout=StaticLayout.Builder.obtain(getText(),0,getText().length(),titlePaint,titleWidth).setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false).build();creditLayout=StaticLayout.Builder.obtain(credits,0,credits.length(),creditPaint,creditWidth).setAlignment(Layout.Alignment.ALIGN_OPPOSITE).setIncludePad(false).build();
            int height=Math.max(getSuggestedMinimumHeight(),Math.max(titleLayout.getHeight(),creditLayout.getHeight())+getPaddingTop()+getPaddingBottom());setMeasuredDimension(width,resolveSizeAndState(height,heightSpec,0));
        }
        protected void onDraw(Canvas canvas){
            if(credits.isEmpty()||titleLayout==null||creditLayout==null){super.onDraw(canvas);return;}int color=getCurrentTextColor();titleLayout.getPaint().setColor(color);creditLayout.getPaint().setColor(color);int inner=getHeight()-getPaddingTop()-getPaddingBottom();
            int saved=canvas.save();canvas.translate(getPaddingLeft(),getPaddingTop()+Math.max(0,(inner-titleLayout.getHeight())/2f));titleLayout.draw(canvas);canvas.restoreToCount(saved);
            saved=canvas.save();canvas.translate(getPaddingLeft()+titleWidth+gap,getPaddingTop()+Math.max(0,(inner-creditLayout.getHeight())/2f));creditLayout.draw(canvas);canvas.restoreToCount(saved);
        }
    }
}
