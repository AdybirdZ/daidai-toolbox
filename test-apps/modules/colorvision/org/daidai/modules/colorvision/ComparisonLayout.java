package org.daidai.modules.colorvision;

/** Shared panel geometry for CPU images, camera viewports and labels. */
public final class ComparisonLayout {
 public static final class Panel {
  public final int mode,x,y,width,height;
  Panel(int m,int xx,int yy,int w,int h){mode=m;x=xx;y=yy;width=w;height=h;}
 }
 public static Panel[] panels(int mask,int width,int height,int gap){
  int count=Integer.bitCount(mask&15);if(count==0)throw new IllegalArgumentException("Select a view");
  int columns=count==1?1:2,rows=(count+columns-1)/columns;Panel[] result=new Panel[count];int index=0;
  for(int mode=0;mode<4;mode++)if((mask&(1<<mode))!=0){int col=index%columns,row=index/columns;
   int left=col*(width+gap)/columns,top=row*(height+gap)/rows;
   int right=(col+1)*(width+gap)/columns-gap,bottom=(row+1)*(height+gap)/rows-gap;
   result[index++]=new Panel(mode,left,top,Math.max(1,right-left),Math.max(1,bottom-top));
  }return result;
 }
 private ComparisonLayout(){}
}
