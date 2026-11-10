import org.daidai.modules.colorvision.*;
public final class ColorLayoutChecks {
 static int checks;static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
 public static void main(String[] args){
  for(int mask=1;mask<16;mask++){
   ComparisonLayout.Panel[] panels=ComparisonLayout.panels(mask,803,907,6);check(panels.length==Integer.bitCount(mask),"selected count");int previous=-1;
   for(ComparisonLayout.Panel p:panels){check(p.mode>previous&&(mask&(1<<p.mode))!=0,"stable selected order");previous=p.mode;check(p.x>=0&&p.y>=0&&p.x+p.width<=803&&p.y+p.height<=907,"inside frame");}
   for(int a=0;a<panels.length;a++)for(int b=a+1;b<panels.length;b++){ComparisonLayout.Panel p=panels[a],q=panels[b];check(p.x+p.width<q.x||q.x+q.width<p.x||p.y+p.height<q.y||q.y+q.height<p.y,"panel gaps");}
  }
  float[] sensor90={0,-1,0,0,-1,0,0,0,0,0,1,0,1,1,0,1};
  float[] flipY={1,0,0,0,0,-1,0,0,0,0,1,0,0,1,0,1};
  check(PreviewGeometry.swapsAxes(sensor90)&&!PreviewGeometry.swapsAxes(flipY),"detect texture sensor transform");
  check(Math.abs(PreviewGeometry.aspect(1024,768,sensor90,0)-.75)<1e-6,"portrait aspect consumes sensor rotation once");
  check(Math.abs(PreviewGeometry.aspect(1024,768,sensor90,90)-4f/3)<1e-6,"landscape aspect");
  check(Math.abs(PreviewGeometry.aspect(1024,768,flipY,0)-4f/3)<1e-6,"native landscape sensor");
  float[] original=PreviewGeometry.uv(0),expected={0,0,1,0,0,1,1,1};for(int i=0;i<8;i++)check(Math.abs(original[i]-expected[i])<1e-6,"no duplicate sensor rotation in UV");
  float[] rotated=PreviewGeometry.uv(90),expected90={0,1,0,0,1,1,1,0};for(int i=0;i<8;i++)check(Math.abs(rotated[i]-expected90[i])<1e-6,"display compensation");
  for(int rotation:new int[]{0,90,180,270})for(float v:PreviewGeometry.uv(rotation))check(v>=-1e-6&&v<=1.000001,"UV bounds");
  System.out.println("PASS: "+checks+" comparison layout and camera orientation checks");
 }
}
