package org.simpleweather.app;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.util.*;

/** AlertDialog API compatibility for existing settings; never opens a dialog window. */
final class SettingsPanel extends AlertDialog {
    final SettingsNavigation navigation;
    final ToolDialog spec;
    private LinearLayout root;
    private ListView list;
    private final Map<Integer,Button> buttons=new HashMap<>();
    private boolean showing;
    private DialogInterface.OnShowListener onShow;
    private DialogInterface.OnDismissListener onDismiss;
    private DialogInterface.OnCancelListener onCancel;
    SettingsPanel(Context context,ToolDialog spec,SettingsNavigation navigation){super(context,android.R.style.Theme_Material_Light_NoActionBar);this.spec=spec;this.navigation=navigation;}
    private int dp(float n){return Math.round(n*getContext().getResources().getDisplayMetrics().density);}
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);Context c=getContext();root=new LinearLayout(c);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(MainActivity.BG);
        LinearLayout header=new LinearLayout(c);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(dp(16),dp(10),dp(16),dp(10));
        Button back=new Button(c);back.setText("‹");back.setContentDescription(SettingsActivity.hostText(c,R.string.action_back));back.setOnClickListener(v->cancel());header.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));
        TextView title=new TextView(c);title.setText(spec.pageTitle);title.setTextSize(23);title.setTypeface(null,1);title.setTag(UiColors.TITLE);title.setPadding(dp(12),0,0,0);header.addView(title,new LinearLayout.LayoutParams(0,-2,1));root.addView(header);
        FrameLayout content=new FrameLayout(c);root.addView(content,new LinearLayout.LayoutParams(-1,0,1));
        View body=spec.pageView;
        if(spec.pageItems!=null){
            list=new ListView(c);list.setDividerHeight(0);list.setPadding(dp(18),0,dp(18),0);
            int layout=spec.pageMulti?android.R.layout.simple_list_item_multiple_choice:spec.pageChoice?android.R.layout.simple_list_item_single_choice:android.R.layout.simple_list_item_1;
            list.setAdapter(new ArrayAdapter<CharSequence>(c,layout,spec.pageItems));
            list.setChoiceMode(spec.pageMulti?ListView.CHOICE_MODE_MULTIPLE:spec.pageChoice?ListView.CHOICE_MODE_SINGLE:ListView.CHOICE_MODE_NONE);
            if(spec.pageMulti){if(spec.pageChecked!=null)for(int i=0;i<spec.pageChecked.length;i++)list.setItemChecked(i,spec.pageChecked[i]);}
            else if(spec.pageSelected>=0)list.setItemChecked(spec.pageSelected,true);
            list.setOnItemClickListener((p,v,index,id)->{if(spec.pageMulti){boolean on=list.isItemChecked(index);if(spec.pageChecked!=null&&index<spec.pageChecked.length)spec.pageChecked[index]=on;if(spec.pageMultiClick!=null)spec.pageMultiClick.onClick(this,index,on);}else{if(spec.pageClick!=null)spec.pageClick.onClick(this,index);if(!spec.pageChoice)dismiss();}});
            body=list;
        }
        if(body!=null){if(body.getParent() instanceof ViewGroup)((ViewGroup)body.getParent()).removeView(body);if(!(body instanceof ScrollView)&&!(body instanceof ListView)){ScrollView sc=new ReadingScrollView(c);sc.setFillViewport(false);sc.addView(body);body=sc;}FrameLayout.LayoutParams fit=new FrameLayout.LayoutParams(Math.min(c.getResources().getDisplayMetrics().widthPixels,dp(680)),-1,Gravity.CENTER_HORIZONTAL);content.addView(body,fit);}
        LinearLayout actions=new LinearLayout(c);actions.setOrientation(LinearLayout.VERTICAL);actions.setPadding(dp(18),dp(6),dp(18),dp(8));
        for(int which:new int[]{BUTTON_POSITIVE,BUTTON_NEUTRAL,BUTTON_NEGATIVE}){CharSequence label=spec.pageButtons.get(which);if(label==null)continue;Button b=new Button(c);b.setText(label);b.setMinHeight(dp(48));b.setAllCaps(false);b.setOnClickListener(v->{DialogInterface.OnClickListener listener=spec.pageListeners.get(which);if(listener!=null)listener.onClick(this,which);dismiss();});buttons.put(which,b);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(4);p.bottomMargin=dp(4);actions.addView(b,p);}
        if(actions.getChildCount()>0)root.addView(actions);
        Button desktop=new Button(c);desktop.setText(SettingsActivity.hostText(c,R.string.launcher_home));desktop.setAllCaps(false);desktop.setOnClickListener(v->{Activity owner=SettingsActivity.owner(c);if(owner!=null)SettingsNavigation.desktop(owner);});LinearLayout.LayoutParams home=new LinearLayout.LayoutParams(-1,dp(52));home.setMargins(dp(18),0,dp(18),dp(4));root.addView(desktop,home);
        flatten(root);
        UiColors.decorate(c,root);
    }
    View pageView(){return root;}
    private void flatten(View view){if(view instanceof Button){view.setStateListAnimator(null);view.setElevation(0);view.setTranslationZ(0);}if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)flatten(group.getChildAt(i));}}
    @Override public void show(){if(showing)return;create();showing=true;navigation.push(this,pageView());if(onShow!=null)onShow.onShow(this);}
    @Override public boolean isShowing(){return showing;}
    @Override public void dismiss(){if(!showing)return;showing=false;navigation.remove(this);if(onDismiss!=null)onDismiss.onDismiss(this);}
    void closedByHost(){if(!showing)return;showing=false;if(onDismiss!=null)onDismiss.onDismiss(this);}
    @Override public void cancel(){if(!showing)return;if(onCancel!=null)onCancel.onCancel(this);dismiss();}
    @Override public Button getButton(int which){return buttons.get(which);}
    @Override public ListView getListView(){return list;}
    @Override public void setOnShowListener(DialogInterface.OnShowListener listener){onShow=listener;}
    @Override public void setOnDismissListener(DialogInterface.OnDismissListener listener){onDismiss=listener;}
    @Override public void setOnCancelListener(DialogInterface.OnCancelListener listener){onCancel=listener;}
}
