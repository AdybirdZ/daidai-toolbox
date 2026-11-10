package org.simpleweather.app;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.media.ExifInterface;
import android.os.Build;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import java.io.IOException;
import java.io.InputStream;

/** A bounded image decode followed by a square crop; the original file is untouched. */
public final class AppIconCropActivity extends Activity {
    private LinearLayout root;
    private TextView title;
    private CropView crop;
    private Bitmap source;
    private volatile boolean dead;
    private boolean saving;
    @Override protected void attachBaseContext(Context base){super.attachBaseContext(AppLanguage.wrap(base));}
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16),dp(12),dp(16),dp(12));root.setBackgroundColor(0xFFF5F7FB);root.setFitsSystemWindows(true);setContentView(root);
        root.setOnApplyWindowInsetsListener((view,insets)->{if(Build.VERSION.SDK_INT>=30){Insets bars=insets.getInsets(WindowInsets.Type.systemBars());root.setPadding(dp(16)+bars.left,dp(12)+bars.top,dp(16)+bars.right,dp(12)+bars.bottom);}return insets;});
        title=new TextView(this);title.setText(R.string.app_icon_loading);title.setTextSize(21);title.setTextColor(UiColors.ink(this,UiColors.TITLE));title.setTextIsSelectable(false);root.addView(title);
        new Thread(()->{
            try{Bitmap image=load();runOnUiThread(()->{if(dead)image.recycle();else ready(image);});}
            catch(Exception|OutOfMemoryError error){runOnUiThread(()->{if(!dead){title.setText(R.string.app_icon_load_failed);button(root,R.string.action_back,this::finish);}});}
        },"app-icon-load").start();
    }
    private Bitmap load()throws IOException {
        if(getIntent().getData()==null)throw new IOException();BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;
        try(InputStream input=getContentResolver().openInputStream(getIntent().getData())){BitmapFactory.decodeStream(input,null,options);}
        if(options.outWidth<=0||options.outHeight<=0)throw new IOException();options.inSampleSize=1;
        while((long)(options.outWidth/options.inSampleSize)*(options.outHeight/options.inSampleSize)>4_000_000L||Math.max(options.outWidth,options.outHeight)/options.inSampleSize>3200)options.inSampleSize*=2;
        options.inJustDecodeBounds=false;Bitmap image;
        try(InputStream input=getContentResolver().openInputStream(getIntent().getData())){image=BitmapFactory.decodeStream(input,null,options);}
        if(image==null)throw new IOException();
        try(InputStream input=getContentResolver().openInputStream(getIntent().getData())){
            ExifInterface exif=new ExifInterface(input);int orientation=exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL);Matrix matrix=new Matrix();
            switch(orientation){case 2:matrix.setScale(-1,1);break;case 3:matrix.setRotate(180);break;case 4:matrix.setScale(1,-1);break;case 5:matrix.setRotate(90);matrix.postScale(-1,1);break;case 6:matrix.setRotate(90);break;case 7:matrix.setRotate(270);matrix.postScale(-1,1);break;case 8:matrix.setRotate(270);break;}
            if(!matrix.isIdentity()){Bitmap rotated=Bitmap.createBitmap(image,0,0,image.getWidth(),image.getHeight(),matrix,true);if(rotated!=image)image.recycle();image=rotated;}
        }catch(IOException ignored){}
        return image;
    }
    private void ready(Bitmap image){
        source=image;title.setText(R.string.app_icon_crop_title);crop=new CropView(image);root.addView(crop,new LinearLayout.LayoutParams(-1,0,1));
        SeekBar zoom=new SeekBar(this);zoom.setMax(300);zoom.setContentDescription(getString(R.string.app_icon_zoom));int color=ThemeColors.dark(UiColors.seed(this,UiColors.BUTTON));
        zoom.setProgressTintList(ColorStateList.valueOf(color));zoom.setThumbTintList(ColorStateList.valueOf(color));
        zoom.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar view,int value,boolean user){if(user)crop.setZoom(1+value/100f);}public void onStartTrackingTouch(SeekBar view){}public void onStopTrackingTouch(SeekBar view){}});
        crop.zoomChanged=value->zoom.setProgress(Math.round((value-1)*100));root.addView(zoom,new LinearLayout.LayoutParams(-1,dp(44)));
        LinearLayout actions=new LinearLayout(this);button(actions,R.string.action_cancel,this::finish);button(actions,R.string.app_icon_crop_reset,()->crop.reset());button(actions,R.string.app_icon_crop_use,this::save);root.addView(actions);
        TextView help=new TextView(this);help.setText(R.string.app_icon_crop_note);help.setTextSize(13);help.setTextColor(UiColors.ink(this,UiColors.NOTE));help.setPadding(0,dp(12),0,0);help.setTextIsSelectable(false);root.addView(help);
    }
    private void button(LinearLayout parent,int label,Runnable action){
        Button button=new Button(this);button.setText(label);button.setTextSize(14);button.setAllCaps(false);
        int seed=UiColors.seed(this,UiColors.BUTTON);GradientDrawable background=new GradientDrawable();background.setColor(ThemeColors.pale(seed));background.setCornerRadius(dp(12));
        button.setTextColor(ThemeColors.dark(seed));button.setBackground(background);button.setOnClickListener(view->{if(!saving)action.run();});
        if(parent.getOrientation()==LinearLayout.HORIZONTAL&&parent.getChildCount()>0)parent.addView(new View(this),new LinearLayout.LayoutParams(dp(8),1));
        parent.addView(button,parent.getOrientation()==LinearLayout.VERTICAL?new LinearLayout.LayoutParams(-1,dp(48)):new LinearLayout.LayoutParams(0,dp(48),1));
    }
    private void save(){
        if(saving||crop==null||crop.frame.width()<=0)return;saving=true;title.setText(R.string.app_icon_saving);
        final Bitmap image;try{image=crop.export();}catch(RuntimeException|OutOfMemoryError error){saving=false;title.setText(R.string.app_icon_save_failed);return;}
        new Thread(()->{
            try{AppIconStore.save(this,image);runOnUiThread(()->{if(!dead){setResult(RESULT_OK);finish();}});}
            catch(Exception error){runOnUiThread(()->{if(!dead){saving=false;title.setText(R.string.app_icon_save_failed);}});}
            finally{image.recycle();}
        },"app-icon-save").start();
    }
    @Override protected void onDestroy(){dead=true;if(source!=null){source.recycle();source=null;}super.onDestroy();}

    private final class CropView extends View {
        final Bitmap image;final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);final RectF frame=new RectF();
        final ScaleGestureDetector detector;float zoom=1,base=1,x,y,lastX,lastY;java.util.function.Consumer<Float> zoomChanged;
        CropView(Bitmap image){super(AppIconCropActivity.this);this.image=image;setContentDescription(getString(R.string.app_icon_crop_accessibility));detector=new ScaleGestureDetector(AppIconCropActivity.this,new ScaleGestureDetector.SimpleOnScaleGestureListener(){@Override public boolean onScale(ScaleGestureDetector value){setZoom(zoom*value.getScaleFactor());return true;}});}
        @Override protected void onSizeChanged(int width,int height,int oldWidth,int oldHeight){float side=Math.max(1,Math.min(width,height)-dp(24));frame.set((width-side)/2,(height-side)/2,(width+side)/2,(height+side)/2);base=Math.max(side/image.getWidth(),side/image.getHeight());reset();}
        void reset(){zoom=1;x=frame.centerX()-image.getWidth()*base/2;y=frame.centerY()-image.getHeight()*base/2;if(zoomChanged!=null)zoomChanged.accept(zoom);invalidate();}
        void setZoom(float value){float next=Math.max(1,Math.min(4,value)),factor=next/zoom;x=frame.centerX()+(x-frame.centerX())*factor;y=frame.centerY()+(y-frame.centerY())*factor;zoom=next;clamp();if(zoomChanged!=null)zoomChanged.accept(zoom);invalidate();}
        private void clamp(){x=Math.min(frame.left,Math.max(frame.right-image.getWidth()*base*zoom,x));y=Math.min(frame.top,Math.max(frame.bottom-image.getHeight()*base*zoom,y));}
        @Override protected void onDraw(Canvas canvas){
            canvas.drawColor(0xFF182334);float scale=base*zoom;canvas.drawBitmap(image,null,new RectF(x,y,x+image.getWidth()*scale,y+image.getHeight()*scale),paint);
            paint.setColor(0xAA000000);canvas.drawRect(0,0,getWidth(),frame.top,paint);canvas.drawRect(0,frame.bottom,getWidth(),getHeight(),paint);canvas.drawRect(0,frame.top,frame.left,frame.bottom,paint);canvas.drawRect(frame.right,frame.top,getWidth(),frame.bottom,paint);
            paint.setColor(Color.WHITE);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(2));canvas.drawRect(frame,paint);paint.setStrokeWidth(dp(.5f));for(int n=1;n<3;n++){canvas.drawLine(frame.left+frame.width()*n/3,frame.top,frame.left+frame.width()*n/3,frame.bottom,paint);canvas.drawLine(frame.left,frame.top+frame.height()*n/3,frame.right,frame.top+frame.height()*n/3,paint);}paint.setStyle(Paint.Style.FILL);
        }
        @Override public boolean onTouchEvent(MotionEvent event){
            detector.onTouchEvent(event);int action=event.getActionMasked();
            if(action==MotionEvent.ACTION_DOWN){lastX=event.getX();lastY=event.getY();return true;}
            if(action==MotionEvent.ACTION_POINTER_UP){int keep=event.getActionIndex()==0?1:0;lastX=event.getX(keep);lastY=event.getY(keep);return true;}
            if(action==MotionEvent.ACTION_MOVE){if(!detector.isInProgress()&&event.getPointerCount()==1){x+=event.getX()-lastX;y+=event.getY()-lastY;clamp();invalidate();}lastX=event.getX();lastY=event.getY();return true;}
            if(action==MotionEvent.ACTION_UP)performClick();return true;
        }
        @Override public boolean performClick(){super.performClick();return true;}
        Bitmap export(){Bitmap out=Bitmap.createBitmap(AppIconStore.SIZE,AppIconStore.SIZE,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(out);canvas.drawColor(Color.WHITE);canvas.scale(AppIconStore.SIZE/frame.width(),AppIconStore.SIZE/frame.height());canvas.translate(-frame.left,-frame.top);float scale=base*zoom;canvas.drawBitmap(image,null,new RectF(x,y,x+image.getWidth()*scale,y+image.getHeight()*scale),new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG));return out;}
    }
}
