package org.daidai.modules.colorvision;
import android.content.Context;import android.graphics.*;import android.view.View;import org.daidai.modules.shared.*;

/** Draws the same labelled layout over images and the shared live camera surface. */
public final class ComparisonView extends View {
 private final Paint paint=new Paint(3);private final String[] labels;private Bitmap[] images;private int mask=15;private boolean camera;
 public ComparisonView(Context context){super(context);labels=new String[]{Texts.get(context,"cv_reference"),Texts.get(context,"cv_protan"),Texts.get(context,"cv_deutan"),Texts.get(context,"cv_tritan")};}
 public int gap(){return Ui.dp(getContext(),6);}public int labelHeight(){return Ui.dp(getContext(),28);}
 public void setMask(int value){mask=value&15;invalidate();}public void setImages(Bitmap[] value){images=value;invalidate();}public void setCamera(boolean value){camera=value;invalidate();}
 @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);if(!camera)canvas.drawColor(0xff101010);
  for(ComparisonLayout.Panel panel:ComparisonLayout.panels(mask,getWidth(),getHeight(),gap())){
   paint.setColor(0xff30333a);canvas.drawRect(panel.x,panel.y,panel.x+panel.width,panel.y+labelHeight(),paint);
   paint.setColor(Color.WHITE);paint.setTextSize(Ui.dp(getContext(),14));paint.setTypeface(Typeface.DEFAULT_BOLD);paint.setTextAlign(Paint.Align.CENTER);float baseline=panel.y+(labelHeight()-paint.ascent()-paint.descent())/2;canvas.drawText(labels[panel.mode],panel.x+panel.width/2f,baseline,paint);
   if(!camera&&images!=null&&images[panel.mode]!=null){Bitmap image=images[panel.mode];int available=Math.max(1,panel.height-labelHeight());float scale=Math.min(panel.width/(float)image.getWidth(),available/(float)image.getHeight());float w=image.getWidth()*scale,h=image.getHeight()*scale;float x=panel.x+(panel.width-w)/2,y=panel.y+labelHeight()+(available-h)/2;canvas.drawBitmap(image,null,new RectF(x,y,x+w,y+h),paint);}
  }
 }
 public Bitmap snapshot(){float scale=Math.min(1,2048f/Math.max(getWidth(),getHeight()));Bitmap result=Bitmap.createBitmap(Math.max(1,Math.round(getWidth()*scale)),Math.max(1,Math.round(getHeight()*scale)),Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(result);canvas.scale(scale,scale);draw(canvas);return result;}
}
