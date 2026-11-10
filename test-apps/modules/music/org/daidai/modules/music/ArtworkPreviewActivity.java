package org.daidai.modules.music;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import org.daidai.modules.shared.Texts;
import org.daidai.modules.shared.Ui;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Module-local artwork viewer. It uses only host resources and the Android platform API. */
public final class ArtworkPreviewActivity extends Activity {
    private static final int CREATE_IMAGE=710;
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private String coverPath="",mime="image/png",extension="png";
    private ArtworkView artwork;
    private Button save;
    private volatile boolean destroyed;

    public static void open(Activity activity,MusicLibrary.Track track){
        Intent intent=new Intent(activity,ArtworkPreviewActivity.class);
        if(track!=null)intent.putExtra("cover_path",track.coverPath).putExtra("track_title",track.title);
        activity.startActivity(intent);
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        String supplied=getIntent().getStringExtra("cover_path");coverPath=supplied==null?"":supplied;
        getWindow().setStatusBarColor(Color.BLACK);getWindow().setNavigationBarColor(Color.BLACK);
        getWindow().getDecorView().setSystemUiVisibility(0);
        LinearLayout root=Ui.column(this);root.setBackgroundColor(Color.BLACK);
        int padding=Ui.dp(this,10);root.setPadding(padding,padding,padding,padding);
        setContentView(root);
        if(Build.VERSION.SDK_INT>=30){getWindow().setDecorFitsSystemWindows(false);root.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());v.setPadding(padding+bars.left,padding+bars.top,padding+bars.right,padding+bars.bottom);return insets;});}
        else root.setFitsSystemWindows(true);
        LinearLayout actions=Ui.row(root);
        Ui.cell(actions,Texts.get(this,"music_artwork_back"),this::finish);
        save=Ui.cell(actions,Texts.get(this,"music_artwork_save"),this::requestSave);save.setEnabled(false);
        artwork=new ArtworkView(this);root.addView(artwork,new LinearLayout.LayoutParams(-1,0,1));
        if(Build.VERSION.SDK_INT<29){TextView note=Ui.text(this,Texts.get(this,"music_artwork_save_legacy"),13);note.setTextColor(0xffdddddd);note.setPadding(padding,padding,padding,padding);root.addView(note);}
        io.execute(()->{
            Bitmap bitmap=null;
            try{BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
                try(InputStream input=openSource()){BitmapFactory.decodeStream(input,null,bounds);}
                if(bounds.outWidth<=0||bounds.outHeight<=0)throw new IOException("Invalid image");
                String detected=bounds.outMimeType;
                if("image/jpeg".equals(detected)){mime=detected;extension="jpg";}
                else if("image/webp".equals(detected)){mime=detected;extension="webp";}
                else if("image/gif".equals(detected)){mime=detected;extension="gif";}
                else if("image/png".equals(detected)){mime=detected;extension="png";}
                BitmapFactory.Options options=new BitmapFactory.Options();int sample=1;
                while(Math.max(bounds.outWidth/sample,bounds.outHeight/sample)>2048)sample*=2;
                options.inSampleSize=sample;
                try(InputStream input=openSource()){bitmap=BitmapFactory.decodeStream(input,null,options);}
                if(bitmap==null)throw new IOException("Unable to decode image");
                Bitmap ready=bitmap;main.post(()->{if(destroyed){ready.recycle();return;}artwork.setBitmap(ready);save.setEnabled(true);});
            }catch(Exception e){if(bitmap!=null)bitmap.recycle();main.post(()->{if(!destroyed)Toast.makeText(this,Texts.get(this,"music_artwork_open_failed"),Toast.LENGTH_LONG).show();});}
        });
    }
    private InputStream openSource()throws IOException{
        if(!coverPath.isEmpty()&&new File(coverPath).isFile())return new FileInputStream(coverPath);
        int resource=getResources().getIdentifier("music_default_cover","drawable",getPackageName());
        if(resource==0)throw new IOException("Default artwork is unavailable");
        return getResources().openRawResource(resource);
    }
    private String fileName(){return "artwork-"+System.currentTimeMillis()+"."+extension;}
    private void requestSave(){
        if(Build.VERSION.SDK_INT>=29){save.setEnabled(false);io.execute(this::saveToAlbum);}
        else {Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(mime).putExtra(Intent.EXTRA_TITLE,fileName());
            try{startActivityForResult(intent,CREATE_IMAGE);}catch(Exception e){Toast.makeText(this,Texts.get(this,"music_artwork_save_failed"),Toast.LENGTH_LONG).show();}}
    }
    private void saveToAlbum(){
        Uri created=null;boolean complete=false;
        try{ContentValues values=new ContentValues();values.put(MediaStore.Images.Media.DISPLAY_NAME,fileName());values.put(MediaStore.Images.Media.MIME_TYPE,mime);
            values.put(MediaStore.Images.Media.RELATIVE_PATH,Environment.DIRECTORY_PICTURES+"/"+Texts.get(this,"music_artwork_album"));values.put(MediaStore.Images.Media.IS_PENDING,1);
            created=getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);if(created==null)throw new IOException("Unable to create media entry");
            writeSource(created);values.clear();values.put(MediaStore.Images.Media.IS_PENDING,0);
            if(getContentResolver().update(created,values,null,null)!=1)throw new IOException("Unable to publish image");complete=true;
        }catch(Exception ignored){}finally{
            if(!complete&&created!=null)try{getContentResolver().delete(created,null,null);}catch(Exception ignored){}
            boolean success=complete;main.post(()->finishSave(success));
        }
    }
    private void writeSource(Uri destination)throws IOException{
        try(InputStream input=openSource();OutputStream output=getContentResolver().openOutputStream(destination,"w")){
            if(output==null)throw new IOException("Unable to open destination");byte[] buffer=new byte[32768];int size;
            while((size=input.read(buffer))!=-1){if(Thread.currentThread().isInterrupted())throw new IOException("Interrupted");output.write(buffer,0,size);}output.flush();
        }
    }
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);
        if(request==CREATE_IMAGE&&result==RESULT_OK&&data!=null&&data.getData()!=null){Uri destination=data.getData();save.setEnabled(false);io.execute(()->{boolean success=false;try{writeSource(destination);success=true;}catch(Exception ignored){}boolean finished=success;main.post(()->finishSave(finished));});}
    }
    private void finishSave(boolean success){if(destroyed)return;save.setEnabled(true);Toast.makeText(this,Texts.get(this,success?"music_artwork_saved":"music_artwork_save_failed"),Toast.LENGTH_LONG).show();}
    @Override protected void onDestroy(){destroyed=true;io.shutdownNow();main.removeCallbacksAndMessages(null);if(artwork!=null)artwork.release();super.onDestroy();}

    /** Fits artwork initially; pinch zoom keeps its focal point stable and drag is bounded. */
    private static final class ArtworkView extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        private final ScaleGestureDetector pinch;
        private Bitmap bitmap;private float zoom=1,offsetX,offsetY,lastX,lastY;private int pointer=-1;
        ArtworkView(android.content.Context context){super(context);setContentDescription(Texts.get(context,"music_artwork_preview"));
            pinch=new ScaleGestureDetector(context,new ScaleGestureDetector.SimpleOnScaleGestureListener(){
                @Override public boolean onScale(ScaleGestureDetector detector){float old=zoom;zoom=Math.max(1,Math.min(8,zoom*detector.getScaleFactor()));float ratio=zoom/old;
                    offsetX=detector.getFocusX()-getWidth()/2f-(detector.getFocusX()-getWidth()/2f-offsetX)*ratio;
                    offsetY=detector.getFocusY()-getHeight()/2f-(detector.getFocusY()-getHeight()/2f-offsetY)*ratio;bound();invalidate();return true;}});
        }
        void setBitmap(Bitmap value){bitmap=value;zoom=1;offsetX=offsetY=0;invalidate();}
        void release(){if(bitmap!=null){bitmap.recycle();bitmap=null;}}
        private float fit(){return bitmap==null?1:Math.min((float)getWidth()/bitmap.getWidth(),(float)getHeight()/bitmap.getHeight());}
        private void bound(){if(bitmap==null)return;float s=fit()*zoom;float x=Math.max(0,(bitmap.getWidth()*s-getWidth())/2f),y=Math.max(0,(bitmap.getHeight()*s-getHeight())/2f);offsetX=Math.max(-x,Math.min(x,offsetX));offsetY=Math.max(-y,Math.min(y,offsetY));}
        @Override protected void onSizeChanged(int w,int h,int oldW,int oldH){super.onSizeChanged(w,h,oldW,oldH);bound();}
        @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);if(bitmap==null)return;float s=fit()*zoom;canvas.save();canvas.translate(getWidth()/2f+offsetX,getHeight()/2f+offsetY);canvas.scale(s,s);canvas.drawBitmap(bitmap,-bitmap.getWidth()/2f,-bitmap.getHeight()/2f,paint);canvas.restore();}
        @Override public boolean onTouchEvent(MotionEvent event){pinch.onTouchEvent(event);int action=event.getActionMasked();
            if(action==MotionEvent.ACTION_DOWN){pointer=event.getPointerId(0);lastX=event.getX();lastY=event.getY();return true;}
            if(action==MotionEvent.ACTION_POINTER_DOWN){pointer=-1;return true;}
            if(action==MotionEvent.ACTION_POINTER_UP){int next=event.getActionIndex()==0?1:0;if(next<event.getPointerCount()){pointer=event.getPointerId(next);lastX=event.getX(next);lastY=event.getY(next);}return true;}
            if(action==MotionEvent.ACTION_MOVE&&!pinch.isInProgress()){int index=event.findPointerIndex(pointer);if(index>=0){offsetX+=event.getX(index)-lastX;offsetY+=event.getY(index)-lastY;lastX=event.getX(index);lastY=event.getY(index);bound();invalidate();}return true;}
            if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){pointer=-1;performClick();return true;}return true;
        }
        @Override public boolean performClick(){super.performClick();return true;}
    }
}
