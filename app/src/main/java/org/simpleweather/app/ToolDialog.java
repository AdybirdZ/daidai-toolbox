package org.simpleweather.app;
import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;

/** Apply the optional outline to platform dialog buttons and input regions too. */
public final class ToolDialog extends AlertDialog.Builder {
    private static final java.util.WeakHashMap<AlertDialog,Boolean> OPEN=new java.util.WeakHashMap<>();
    private final UiSession.Route sessionRoute;
    CharSequence pageTitle,pageMessage;
    View pageView;
    CharSequence[] pageItems;
    int pageSelected=-1;
    boolean pageChoice,pageMulti;
    boolean[] pageChecked;
    DialogInterface.OnClickListener pageClick;
    DialogInterface.OnMultiChoiceClickListener pageMultiClick;
    final java.util.Map<Integer,CharSequence> pageButtons=new java.util.HashMap<>();
    final java.util.Map<Integer,DialogInterface.OnClickListener> pageListeners=new java.util.HashMap<>();
    public ToolDialog(Context context){super(context);sessionRoute=UiSession.inferRoute();}
    @Override public ToolDialog setTitle(CharSequence title){pageTitle=title;super.setTitle(title);return this;}
    @Override public ToolDialog setTitle(int title){return setTitle(getContext().getText(title));}
    @Override public ToolDialog setMessage(CharSequence message){pageMessage=message;super.setMessage(message);return this;}
    @Override public ToolDialog setMessage(int message){return setMessage(getContext().getText(message));}
    @Override public ToolDialog setView(View view){pageView=view;super.setView(view);return this;}
    @Override public ToolDialog setPositiveButton(CharSequence text,DialogInterface.OnClickListener listener){pageButtons.put(-1,text);pageListeners.put(-1,listener);super.setPositiveButton(text,listener);return this;}
    @Override public ToolDialog setNegativeButton(CharSequence text,DialogInterface.OnClickListener listener){pageButtons.put(-2,text);pageListeners.put(-2,listener);super.setNegativeButton(text,listener);return this;}
    @Override public ToolDialog setNeutralButton(CharSequence text,DialogInterface.OnClickListener listener){pageButtons.put(-3,text);pageListeners.put(-3,listener);super.setNeutralButton(text,listener);return this;}
    @Override public ToolDialog setPositiveButton(int text,DialogInterface.OnClickListener listener){return setPositiveButton(getContext().getText(text),listener);}
    @Override public ToolDialog setNegativeButton(int text,DialogInterface.OnClickListener listener){return setNegativeButton(getContext().getText(text),listener);}
    @Override public ToolDialog setNeutralButton(int text,DialogInterface.OnClickListener listener){return setNeutralButton(getContext().getText(text),listener);}
    @Override public ToolDialog setItems(CharSequence[] items,DialogInterface.OnClickListener listener){pageItems=items;pageClick=listener;super.setItems(items,listener);return this;}
    @Override public ToolDialog setSingleChoiceItems(CharSequence[] items,int selected,DialogInterface.OnClickListener listener){pageItems=items;pageSelected=selected;pageClick=listener;pageChoice=true;super.setSingleChoiceItems(items,selected,listener);return this;}
    @Override public ToolDialog setMultiChoiceItems(CharSequence[] items,boolean[] checked,DialogInterface.OnMultiChoiceClickListener listener){pageItems=items;pageChecked=checked;pageMultiClick=listener;pageMulti=true;super.setMultiChoiceItems(items,checked,listener);return this;}
    @Override public AlertDialog create(){SettingsNavigation navigation=SettingsNavigation.find(getContext());AlertDialog dialog=prepare(navigation!=null&&pageMessage==null&&(pageView!=null||pageItems!=null)?new SettingsPanel(getContext(),this,navigation):super.create());UiSession.register(dialog,sessionRoute);return dialog;}
    /** Inflate and style before the first measurement; keep caller OnShow validation intact. */
    public static <T extends AlertDialog> T prepare(T dialog){
        dialog.create();OPEN.put(dialog,true);UiSession.register(dialog,null);
        Window window=dialog.getWindow();
        if(window!=null)window.setWindowAnimations(0);
        Context context=dialog.getContext();
        int gap=Math.round(4*context.getResources().getDisplayMetrics().density);
        for(int which:new int[]{-1,-2,-3}){
            Button button=dialog.getButton(which);
            if(button==null||button.getVisibility()!=View.VISIBLE||!(button.getLayoutParams() instanceof ViewGroup.MarginLayoutParams))continue;
            ViewGroup.MarginLayoutParams params=(ViewGroup.MarginLayoutParams)button.getLayoutParams();
            params.leftMargin+=gap;params.rightMargin+=gap;params.topMargin+=gap;params.bottomMargin+=gap;
            button.setLayoutParams(params);
            if(button.getParent() instanceof ViewGroup){
                ViewGroup panel=(ViewGroup)button.getParent();
                panel.setPadding(panel.getPaddingLeft(),Math.max(panel.getPaddingTop(),gap*2),panel.getPaddingRight(),Math.max(panel.getPaddingBottom(),gap*2));
                panel.setClipToPadding(false);
            }
        }
        if(!(dialog instanceof SettingsPanel)){
            Button positive=dialog.getButton(-1),negative=dialog.getButton(-2),neutral=dialog.getButton(-3);
            if(positive!=null&&negative!=null&&neutral!=null&&neutral.getVisibility()==View.VISIBLE&&positive.getParent() instanceof LinearLayout&&positive.getParent()==negative.getParent()&&positive.getParent()==neutral.getParent()){
                stackNativeActions(context,(LinearLayout)positive.getParent(),new Button[]{positive,neutral,negative},gap);
            }
        }
        if(dialog instanceof SettingsPanel){View page=((SettingsPanel)dialog).pageView();decorate(context,page);page.post(()->decorate(context,page));}
        else if(window!=null){decorate(context,window.getDecorView());window.getDecorView().post(()->decorate(context,window.getDecorView()));}
        return dialog;
    }
    /** Platform ButtonBarLayout can undo setOrientation() and collapse an action to zero width. */
    private static void stackNativeActions(Context context,LinearLayout platform,Button[] buttons,int gap){
        if("tool-dialog-actions".equals(platform.getTag())||!(platform.getParent() instanceof ViewGroup))return;
        ViewGroup container=(ViewGroup)platform.getParent();int index=container.indexOfChild(platform);ViewGroup.LayoutParams original=platform.getLayoutParams();
        LinearLayout footer=new LinearLayout(context);footer.setId(platform.getId());footer.setTag("tool-dialog-actions");footer.setOrientation(LinearLayout.VERTICAL);footer.setGravity(Gravity.FILL_HORIZONTAL);footer.setPadding(Math.max(platform.getPaddingLeft(),gap*3),gap*2,Math.max(platform.getPaddingRight(),gap*3),gap*2);
        int minimum=footer.getPaddingTop()+footer.getPaddingBottom(),touch=Math.round(48*context.getResources().getDisplayMetrics().density);
        for(Button button:buttons){platform.removeView(button);button.setMinWidth(0);button.setMinimumWidth(0);button.setMinHeight(touch);button.setMinimumHeight(touch);button.setSingleLine(false);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=gap;p.bottomMargin=gap;footer.addView(button,p);if(button.getVisibility()!=View.GONE)minimum+=touch+p.topMargin+p.bottomMargin;}
        footer.setMinimumHeight(minimum);container.removeViewAt(index);container.addView(footer,index,original);
        // AlertDialogLayout otherwise reserves only one platform button's minimum height,
        // allowing a large custom form to consume the rest of a three-action footer.
        if(container instanceof ScrollView){container.setMinimumHeight(minimum);((ScrollView)container).setFillViewport(false);footer.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(container.getMinimumHeight()<footer.getMeasuredHeight())container.setMinimumHeight(footer.getMeasuredHeight());});}
    }
    @Override public AlertDialog show(){AlertDialog dialog=create();dialog.show();return dialog;}
    public static void closeAll(){for(AlertDialog d:new java.util.ArrayList<>(OPEN.keySet()))if(d!=null&&d.isShowing())d.dismiss();}
    public static void refresh(Context context){for(AlertDialog d:new java.util.ArrayList<>(OPEN.keySet()))if(d!=null&&d.isShowing()&&d.getWindow()!=null)decorate(d.getContext(),d instanceof SettingsPanel?((SettingsPanel)d).pageView():d.getWindow().getDecorView());}
    private static void decorate(Context context,View v){UiColors.decorate(context,v);}
}
