package org.simpleweather.app;

import android.app.*;
import android.content.*;
import android.os.*;
import android.provider.Settings;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.security.SecureRandom;
import java.util.*;

/** One screen owns the draw UI; the model and its saved lists have no Activity dependency. */
final class RandomChoiceScreen {
    private final MainActivity a;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final SecureRandom random=new SecureRandom();
    RandomChoiceModel model=new RandomChoiceModel();
    private TextView result,status,start,skip;
    private boolean animating,rendered,importing;
    private int frame;
    private final Runnable persist=this::flush;
    private final Runnable animate=this::nextFrame;
    RandomChoiceScreen(MainActivity a){this.a=a;try{String s=a.getSharedPreferences("random_choice",0).getString("data","");if(!s.isEmpty())model=RandomChoiceModel.parse(s);}catch(Exception e){a.toast(a.getString(R.string.random_restore_failed));}}
    private boolean exclude(){return a.preferences.getBoolean("random_remove_drawn",false);}
    void flush(){handler.removeCallbacks(persist);if(importing)return;try{a.getSharedPreferences("random_choice",0).edit().putString("data",model.json().toString()).apply();}catch(Exception e){a.toast(a.getString(R.string.random_save_failed));}}
    void pause(){handler.removeCallbacks(animate);if(animating)finishAnimation();flush();rendered=false;}
    void stopForImport(){pause();importing=true;}
    void resume(){rendered=true;updateStatus();}
    private void changed(){handler.removeCallbacks(persist);handler.postDelayed(persist,350);updateStatus();}
    private void note(LinearLayout c,int id){a.gap(c,12);c.addView(a.text(a.getString(id),13,a.MUTED,false));}
    private EditText input(LinearLayout c,int label,String value,boolean multiline){
        c.addView(a.text(a.getString(label),15,a.TITLE,true));a.gap(c,8);EditText e=new EditText(a);e.setText(value);e.setTextSize(16);e.setPadding(a.dp(14),a.dp(12),a.dp(14),a.dp(12));e.setTextColor(a.primaryTextColor());e.setBackground(a.shape(a.PALE,12));e.setContentDescription(a.getString(label));
        e.setInputType(multiline?android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE:android.text.InputType.TYPE_CLASS_NUMBER);e.setGravity(Gravity.TOP);e.setSingleLine(!multiline);if(multiline){e.setMinLines(4);e.setMaxLines(8);e.setFilters(new InputFilter[]{new InputFilter.LengthFilter(RandomChoiceModel.MAX_INPUT)});e.setOnTouchListener((v,event)->{if(e.canScrollVertically(1)||e.canScrollVertically(-1))v.getParent().requestDisallowInterceptTouchEvent(true);return false;});}else e.setFilters(new InputFilter[]{new InputFilter.LengthFilter(6)});
        c.addView(e,a.lp(-1,-2));return e;
    }
    private void watch(EditText e,boolean draft){e.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int count,int after){}public void onTextChanged(CharSequence s,int st,int before,int count){if(draft)model.draft=s.toString();else model.quantity=s.toString();changed();}public void afterTextChanged(Editable s){}});}
    void render(){
        rendered=true;LinearLayout c=a.card();watch(input(c,R.string.random_candidates,model.draft,true),true);a.gap(c,16);watch(input(c,R.string.random_quantity,model.quantity,false),false);a.gap(c,12);
        status=a.text("",14,a.MUTED,false);c.addView(status);a.gap(c,12);
        LinearLayout actions=a.row();start=a.button(a.getString(R.string.random_start),true,this::draw);actions.addView(start,new LinearLayout.LayoutParams(0,-2,1));a.gapHorizontal(actions,8);actions.addView(a.button(a.getString(R.string.random_remaining),false,this::remaining),new LinearLayout.LayoutParams(0,-2,1));c.addView(actions);a.gap(c,10);
        LinearLayout listsRow=a.row();listsRow.addView(a.button(a.getString(R.string.random_save_list),false,()->name(null)),new LinearLayout.LayoutParams(0,-2,1));a.gapHorizontal(listsRow,8);listsRow.addView(a.button(a.getString(R.string.random_lists),false,this::lists),new LinearLayout.LayoutParams(0,-2,1));c.addView(listsRow);note(c,R.string.random_input_note);a.gap(a.body,16);
        LinearLayout output=a.card();output.addView(a.text(a.getString(R.string.random_result),18,a.TITLE,true));a.gap(output,12);result=a.text("",25,a.INK,true);result.setGravity(Gravity.CENTER);result.setMinHeight(a.dp(96));output.addView(result,a.lp(-1,-2));a.gap(output,10);
        skip=a.button(a.getString(R.string.random_reveal),false,this::finishAnimation);output.addView(skip);skip.setVisibility(View.GONE);
        LinearLayout controls=a.row();controls.addView(a.button(a.getString(R.string.random_copy),false,()->{if(model.result.isEmpty()){a.toast(a.getString(R.string.random_no_result));return;}((android.content.ClipboardManager)a.getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText(a.getString(R.string.random_result),String.join("\n",model.result)));a.toast(a.getString(R.string.random_copied));}),new LinearLayout.LayoutParams(0,-2,1));a.gapHorizontal(controls,8);
        controls.addView(a.button(a.getString(R.string.random_reset),false,()->new ToolDialog(a).setTitle(a.getString(R.string.random_reset)).setMessage(a.getString(R.string.random_reset_confirm)).setNegativeButton(a.getString(R.string.action_cancel),null).setPositiveButton(a.getString(R.string.random_confirm),(d,n)->{finishAnimation();try{model.reset();flush();showResult();updateStatus();}catch(Exception e){a.toast(a.getString(R.string.random_invalid_input));}}).show()),new LinearLayout.LayoutParams(0,-2,1));a.gap(output,10);output.addView(controls);
        showResult();updateStatus();
    }
    private void updateStatus(){if(status==null||!rendered)return;try{status.setText(a.getString(R.string.random_counts,RandomChoiceModel.items(model.draft).size(),model.available(exclude()))+(exclude()?a.getString(R.string.random_excluding):""));}catch(Exception e){status.setText(a.getString(R.string.random_invalid_input));}}
    private void draw(){
        if(animating)return;try{int n=Integer.parseInt(model.quantity);if(RandomChoiceModel.items(model.draft).isEmpty()){a.toast(a.getString(R.string.random_empty));return;}if(n<1||n>model.available(exclude())){a.toast(a.getString(R.string.random_invalid_quantity,model.available(exclude())));return;}
            model.draw(n,exclude(),random);a.hideKeyboard();flush();updateStatus();
            float scale=Settings.Global.getFloat(a.getContentResolver(),Settings.Global.ANIMATOR_DURATION_SCALE,1);
            if(a.preferences.getBoolean("random_animation",true)&&scale>0){animating=true;frame=0;reserveAnimationHeight();start.setEnabled(false);skip.setVisibility(View.VISIBLE);nextFrame();}else showResult();
        }catch(NumberFormatException e){a.toast(a.getString(R.string.random_invalid_quantity,safeAvailable()));}catch(Exception e){a.toast(a.getString(R.string.random_invalid_input));}
    }
    private int safeAvailable(){try{return model.available(exclude());}catch(Exception e){return 0;}}
    private void reserveAnimationHeight(){
        int measuredWidth=result.getWidth()>0?result.getWidth():Math.max(a.dp(48),a.getResources().getDisplayMetrics().widthPixels-a.dp(72));
        int width=Math.max(1,measuredWidth-result.getCompoundPaddingLeft()-result.getCompoundPaddingRight());
        ArrayList<Integer> heights=new ArrayList<>();
        for(String word:model.pool)heights.add(StaticLayout.Builder.obtain(word,0,word.length(),result.getPaint(),width).setLineSpacing(result.getLineSpacingExtra(),result.getLineSpacingMultiplier()).setIncludePad(true).build().getHeight());
        heights.sort(Collections.reverseOrder());int height=result.getCompoundPaddingTop()+result.getCompoundPaddingBottom();
        for(int i=0;i<model.result.size();i++)height+=heights.get(i);
        height+=(int)Math.ceil(Math.max(0,model.result.size()-1)*result.getLineSpacingExtra());
        result.setMaxHeight(Integer.MAX_VALUE);result.setEllipsize(null);result.setMinHeight(Math.max(a.dp(120),height));
    }
    private void nextFrame(){if(!animating)return;if(frame>=16){finishAnimation();return;}List<String> words=new ArrayList<>(model.pool);Collections.shuffle(words,random);int count=Math.min(model.result.size(),words.size());result.setText(String.join("\n",words.subList(0,count)));frame++;handler.postDelayed(animate,65+frame*frame*2);}
    private void finishAnimation(){handler.removeCallbacks(animate);animating=false;if(result!=null){result.setMinHeight(a.dp(96));result.setMaxHeight(Integer.MAX_VALUE);result.setEllipsize(null);}if(start!=null)start.setEnabled(true);if(skip!=null)skip.setVisibility(View.GONE);showResult();}
    private void showResult(){if(result!=null)result.setText(model.result.isEmpty()?a.getString(R.string.random_no_result):String.join("\n",model.result));}
    private void remaining(){
        try{
            List<String> all=RandomChoiceModel.items(model.draft),left=new ArrayList<>(all);
            if(all.equals(model.pool))left.removeAll(model.used);
            LinearLayout c=a.column();c.setPadding(a.dp(18),a.dp(8),a.dp(18),a.dp(18));
            if(left.isEmpty())c.addView(a.text(a.getString(R.string.random_remaining_empty),15,a.MUTED,false));else{c.addView(a.text(a.getString(R.string.random_remaining_count,left.size()),16,a.TITLE,true));a.gap(c,10);c.addView(a.text(String.join("\n",left),16,a.INK,false));}
            note(c,R.string.random_remaining_note);ScrollView scroll=new ScrollView(a);scroll.addView(c);new ToolDialog(a).setTitle(a.getString(R.string.random_remaining_title)).setView(scroll).setPositiveButton(a.getString(R.string.action_back),null).show();
        }catch(Exception e){a.toast(a.getString(R.string.random_invalid_input));}
    }
    private void name(RandomChoiceModel.Saved saved){name(saved,()->{});}
    private void name(RandomChoiceModel.Saved saved,Runnable afterSave){
        if(saved==null)try{if(RandomChoiceModel.items(model.draft).isEmpty()){a.toast(a.getString(R.string.random_empty));return;}}catch(Exception e){a.toast(a.getString(R.string.random_invalid_input));return;}
        LinearLayout c=a.column();c.setPadding(a.dp(20),a.dp(12),a.dp(20),a.dp(12));EditText input=new EditText(a);input.setSingleLine(true);input.setHint(a.getString(R.string.random_list_name));input.setTextSize(16);input.setTextColor(a.primaryTextColor());input.setPadding(a.dp(14),a.dp(12),a.dp(14),a.dp(12));input.setBackground(a.shape(a.PALE,12));input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(80)});if(saved!=null)input.setText(saved.name);c.addView(input);
        AlertDialog d=new ToolDialog(a).setTitle(a.getString(saved==null?R.string.random_save_list:R.string.random_rename)).setView(c).setNegativeButton(a.getString(R.string.action_cancel),null).setPositiveButton(a.getString(R.string.action_save),null).create();d.show();d.getButton(-1).setOnClickListener(b->{try{if(saved==null)model.save(input.getText().toString());else model.rename(saved,input.getText().toString());flush();d.dismiss();afterSave.run();a.toast(a.getString(R.string.random_saved));}catch(Exception e){a.toast(a.getString(R.string.random_invalid_name));}});
    }
    private void lists(){
        LinearLayout c=a.column();c.setPadding(a.dp(18),a.dp(8),a.dp(18),a.dp(18));ScrollView scroll=new ScrollView(a);scroll.addView(c);AlertDialog dialog=new ToolDialog(a).setTitle(a.getString(R.string.random_lists)).setView(scroll).setNegativeButton(a.getString(R.string.action_back),null).create();
        Runnable[] refresh=new Runnable[1];refresh[0]=()->{c.removeAllViews();if(model.lists.isEmpty())c.addView(a.text(a.getString(R.string.random_no_lists),15,a.MUTED,false));for(RandomChoiceModel.Saved list:new ArrayList<>(model.lists)){c.addView(a.button(a.getString(R.string.random_list_entry,list.name,list.items.size()),false,()->listActions(dialog,list,refresh[0])),new LinearLayout.LayoutParams(-1,-2));a.gap(c,10);}};refresh[0].run();dialog.show();
    }
    private void listActions(AlertDialog listDialog,RandomChoiceModel.Saved list,Runnable refresh){
        LinearLayout c=a.column();c.setPadding(a.dp(18),a.dp(8),a.dp(18),a.dp(16));
        final AlertDialog[] actionDialog=new AlertDialog[1];
        TextView load=a.button(a.getString(R.string.random_load),false,()->{finishAnimation();model.load(list);flush();if(actionDialog[0]!=null)actionDialog[0].dismiss();listDialog.dismiss();a.refreshScreen();});c.addView(load);a.gap(c,10);
        TextView rename=a.button(a.getString(R.string.random_rename),false,()->name(list,()->{if(actionDialog[0]!=null)actionDialog[0].dismiss();refresh.run();}));c.addView(rename);a.gap(c,10);
        TextView remove=a.button(a.getString(R.string.random_delete),false,()->new ToolDialog(a).setTitle(a.getString(R.string.random_delete)).setMessage(a.getString(R.string.random_delete_confirm,list.name)).setNegativeButton(a.getString(R.string.action_cancel),null).setPositiveButton(a.getString(R.string.random_confirm),(x,k)->{model.lists.remove(list);flush();if(actionDialog[0]!=null)actionDialog[0].dismiss();refresh.run();}).show());c.addView(remove);
        actionDialog[0]=new ToolDialog(a).setTitle(list.name).setView(c).setNegativeButton(a.getString(R.string.action_back),null).create();actionDialog[0].show();
    }
    void settings(){LinearLayout c=a.column();c.setPadding(a.dp(18),a.dp(8),a.dp(18),a.dp(18));toggle(c,R.string.random_remove,"random_remove_drawn",false);toggle(c,R.string.random_animation,"random_animation",true);note(c,R.string.random_settings_note);new ToolDialog(a).setTitle(a.getString(R.string.tab_random)).setView(c).setNegativeButton(a.getString(R.string.action_back),null).show();}
    private void toggle(LinearLayout c,int title,String key,boolean fallback){Switch s=new Switch(a);s.setText(a.getString(title));s.setTextSize(15);s.setChecked(a.preferences.getBoolean(key,fallback));c.addView(s,a.lp(-1,60));s.setOnCheckedChangeListener((v,on)->{a.preferences.edit().putBoolean(key,on).apply();if(key.equals("random_animation")&&!on)finishAnimation();updateStatus();});}
}
