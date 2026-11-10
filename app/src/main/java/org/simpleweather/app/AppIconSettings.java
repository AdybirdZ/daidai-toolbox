package org.simpleweather.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Image selection and launcher requests are separate, explicit user actions. */
final class AppIconSettings {
    static final int PICK=46,CROP=47;
    private final MainActivity a;
    private AlertDialog dialog;
    private ImageView preview;
    private TextView selection,status,apply;
    private Bitmap shown;
    AppIconSettings(MainActivity activity){a=activity;}
    void show(){
        if(dialog!=null&&dialog.isShowing())return;
        LinearLayout content=a.column();content.setPadding(a.dp(18),a.dp(8),a.dp(18),a.dp(16));
        preview=new ImageView(a);preview.setScaleType(ImageView.ScaleType.FIT_CENTER);preview.setContentDescription(a.getString(R.string.app_icon_preview));
        LinearLayout.LayoutParams imageParams=a.lp(112,112);imageParams.gravity=Gravity.CENTER_HORIZONTAL;content.addView(preview,imageParams);a.gap(content,12);
        selection=a.text("",15,a.INK,true);selection.setGravity(Gravity.CENTER);content.addView(selection);a.gap(content,8);
        status=a.text("",13,a.MUTED,false);status.setGravity(Gravity.CENTER);content.addView(status);a.gap(content,14);
        add(content,a.getString(R.string.app_icon_choose),this::choose);
        apply=add(content,"",this::apply);
        add(content,a.getString(R.string.app_icon_restore),this::restore);
        TextParagraphs.append(a,content,a.getString(R.string.app_icon_explanation),13,a.MUTED);
        ScrollView scroll=new ScrollView(a);scroll.addView(content);
        dialog=new ToolDialog(a).setTitle(a.getString(R.string.app_icon_title)).setView(scroll)
            .setNegativeButton(a.getString(R.string.action_back),null).create();
        dialog.setOnDismissListener(d->{if(shown!=null){preview.setImageDrawable(null);shown.recycle();shown=null;}});
        dialog.show();refresh();
    }
    private TextView add(LinearLayout parent,String text,Runnable action){TextView button=a.button(text,false,action);parent.addView(button);a.gap(parent,10);return button;}
    void refresh(){
        if(dialog==null||!dialog.isShowing())return;
        Bitmap previous=shown;shown=AppIconStore.image(a);preview.setImageBitmap(shown);if(previous!=null)previous.recycle();
        selection.setText(AppIconStore.custom(a)?R.string.app_icon_custom:R.string.app_icon_default);
        boolean pinned=AppIconStore.pinned(a),supported=AppIconStore.supported(a);
        status.setText(pinned?R.string.app_icon_pinned:supported?R.string.app_icon_not_pinned:R.string.app_icon_unsupported);
        apply.setText(pinned?R.string.app_icon_update:R.string.app_icon_add);apply.setEnabled(pinned||supported);
    }
    private void choose(){
        try{a.startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),PICK);}
        catch(Exception e){a.toast(a.getString(R.string.app_icon_picker_failed));}
    }
    private void apply(){
        AppIconStore.Applied result=AppIconStore.apply(a);
        a.toast(a.getString(result==AppIconStore.Applied.UPDATED?R.string.app_icon_updated:result==AppIconStore.Applied.REQUESTED?R.string.app_icon_requested:result==AppIconStore.Applied.UNSUPPORTED?R.string.app_icon_unsupported:R.string.app_icon_apply_failed));refresh();
    }
    private void restore(){
        boolean okay=AppIconStore.reset(a);refresh();a.toast(a.getString(okay?R.string.app_icon_restored:R.string.app_icon_restore_failed));
    }
    void result(int request,int result,Intent data){
        if(request==PICK&&result==Activity.RESULT_OK&&data!=null&&data.getData()!=null){
            try{a.startActivityForResult(new Intent(a,AppIconCropActivity.class).setData(data.getData()).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),CROP);}
            catch(Exception e){a.toast(a.getString(R.string.app_icon_picker_failed));}
        }else if(request==CROP){refresh();if(result==Activity.RESULT_OK)a.toast(a.getString(R.string.app_icon_image_saved));}
    }
}
