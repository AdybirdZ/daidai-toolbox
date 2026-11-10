package org.daidai.modules.music;

import android.app.*;
import android.widget.*;
import android.text.InputType;
import org.daidai.modules.shared.*;
import java.util.*;

/** Original-timeline, repeatable keep/remove editor. Export is delegated to the player. */
public final class MusicTrimDialog {
    public interface Export {void run(MusicLibrary.Track track,long[][] ranges,float speed,boolean preservePitch);}
    private static String text(Activity a,String key,Object... args){return Texts.get(a,key,args);}
    public static void open(Activity a,PlaybackService service,Export export){
        if(service==null||service.selected()==null||service.duration()<=0){Ui.note(a,text(a,"music_trim"),text(a,"music_trim_select"));return;}
        MusicLibrary.Track track=service.selected();long duration=service.duration()*1000L;
        AudioEditPlan plan=new AudioEditPlan(duration);
        LinearLayout box=Ui.column(a);box.setPadding(Ui.dp(a,22),Ui.dp(a,10),Ui.dp(a,22),Ui.dp(a,10));
        box.addView(Ui.text(a,track.title,18));
        EditText from=input(a,box,"music_from","0.000"),to=input(a,box,"music_to",seconds(duration));
        LinearLayout marks=Ui.row(box);Ui.cell(marks,text(a,"music_mark_from"),()->from.setText(seconds(service.position()*1000L)));Ui.cell(marks,text(a,"music_mark_to"),()->to.setText(seconds(service.position()*1000L)));
        RadioGroup mode=new RadioGroup(a);mode.setOrientation(RadioGroup.HORIZONTAL);RadioButton keep=new RadioButton(a),remove=new RadioButton(a);keep.setText(text(a,"music_trim_keep"));remove.setText(text(a,"music_trim_remove"));keep.setId(341);remove.setId(342);mode.addView(keep,new RadioGroup.LayoutParams(0,-2,1));mode.addView(remove,new RadioGroup.LayoutParams(0,-2,1));mode.check(341);box.addView(mode);
        TextView result=Ui.text(a,"",15);box.addView(result);
        Runnable refresh=()->{StringBuilder s=new StringBuilder(text(a,"music_trim_plan",plan.operations(),seconds(plan.durationUs())));for(long[] r:plan.ranges())s.append("\n").append(seconds(r[0])).append(" – ").append(seconds(r[1])).append(" s");result.setText(s.toString());};
        Ui.button(box,text(a,"music_trim_apply"),()->{try{long[] selected=interval(a,from,to,duration);plan.apply(selected[0],selected[1],keep.isChecked());refresh.run();}catch(Exception e){Ui.note(a,text(a,"music_trim"),text(a,"music_trim_empty_or_invalid"));}});
        LinearLayout history=Ui.row(box);Ui.cell(history,text(a,"music_trim_undo"),()->{plan.undo();refresh.run();});Ui.cell(history,text(a,"music_trim_reset"),()->{plan.reset();refresh.run();});
        Ui.button(box,text(a,"music_preview"),()->{try{long[] selected=interval(a,from,to,duration);if(service.selected()!=null&&track.uri.equals(service.selected().uri))service.preview((int)(selected[0]/1000),(int)(selected[1]/1000));}catch(Exception e){Ui.error(a,e);}});
        Switch effect=new Switch(a);effect.setText(text(a,"music_trim_export_speed"));effect.setChecked(false);box.addView(effect);
        box.addView(Ui.text(a,text(a,"music_trim_edit_note"),13));
        ScrollView scroll=new ScrollView(a);scroll.addView(box);
        AlertDialog dialog=new AlertDialog.Builder(a).setTitle(text(a,"music_trim")).setView(scroll).setNegativeButton(text(a,"music_cancel"),null).setPositiveButton(text(a,"music_export_wav"),null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{float speed=effect.isChecked()?service.speed():1f;boolean preserve=!service.pitchFollowsSpeed();dialog.dismiss();export.run(track,plan.ranges(),speed,preserve);}));
        dialog.setOnDismissListener(d->service.endPreview());refresh.run();dialog.show();
    }
    private static EditText input(Activity a,LinearLayout box,String key,String value){box.addView(Ui.text(a,text(a,key),14));EditText e=Ui.input(box,text(a,key),InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);e.setText(value);e.setSingleLine(true);android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();bg.setColor(0xffedf3fd);bg.setCornerRadius(Ui.dp(a,8));e.setBackground(bg);e.setPadding(Ui.dp(a,12),Ui.dp(a,10),Ui.dp(a,12),Ui.dp(a,10));return e;}
    private static String seconds(long us){return String.format(Locale.ROOT,"%.3f",us/1000000d);}
    private static long[] interval(Activity a,EditText from,EditText to,long duration){try{double x=Double.parseDouble(from.getText().toString().replace(',','.')),y=Double.parseDouble(to.getText().toString().replace(',','.'));if(!Double.isFinite(x)||!Double.isFinite(y)||x<0||y<=x||y>duration/1000000d+.000001)throw new Exception();return new long[]{Math.round(x*1000000d),Math.round(y*1000000d)};}catch(Exception e){throw new IllegalArgumentException(text(a,"music_bad_interval"));}}
}
