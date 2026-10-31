package org.simpleweather.app;
import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;

/** Apply the optional outline to platform dialog buttons and input regions too. */
final class ToolDialog extends AlertDialog.Builder {
    private static final java.util.WeakHashMap<AlertDialog,Boolean> OPEN=new java.util.WeakHashMap<>();
    ToolDialog(Context context){super(context);}
    @Override public AlertDialog create(){return prepare(super.create());}
    /** Inflate and style before the first measurement; keep caller OnShow validation intact. */
    static <T extends AlertDialog> T prepare(T dialog){
        dialog.create();OPEN.put(dialog,true);
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
        if(window!=null){decorate(context,window.getDecorView());window.getDecorView().post(()->decorate(context,window.getDecorView()));}
        return dialog;
    }
    @Override public AlertDialog show(){AlertDialog dialog=create();dialog.show();return dialog;}
    static void refresh(Context context){for(AlertDialog d:new java.util.ArrayList<>(OPEN.keySet()))if(d!=null&&d.isShowing()&&d.getWindow()!=null)decorate(d.getContext(),d.getWindow().getDecorView());}
    private static void decorate(Context context,View v){UiColors.decorate(context,v);}
}
