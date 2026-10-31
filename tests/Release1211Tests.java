package org.simpleweather.app;
import org.json.*;
public final class Release1211Tests {
    private static int checks;
    static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    static void near(double a,double b,String why){check(Math.abs(a-b)<1e-4,why+": "+a+" / "+b);}
    public static void main(String[] args)throws Exception{
        int[] colors={0xFF000000,0xFFFFFFFF,0xFFFFFF00,0xFFFF0000,0xFF00FF00,0xFF00FFFF,0xFF0000FF,0xFFFF00FF,0xFF808080};
        for(int c:colors){int dark=ThemeColors.dark(c),pale=ThemeColors.pale(c);check(ThemeColors.contrast(dark,0xFFFFFFFF)>=4.5,"White primary button labels remain legible");check(ThemeColors.contrast(dark,pale)>=4.5,"Pale buttons retain foreground contrast");check(ThemeColors.parse(ThemeColors.hex(c))==c,"Hex round trip");}
        check(ThemeColors.parse("#bad")==ThemeColors.BLUE&&ThemeColors.opaque(123)==ThemeColors.BLUE,"Invalid historical colors fall back to blue");
        float[] identity={1,0,0,0,1,0,0,0,1};double[] camera=SkyPointing.direction(identity,false,0,0);near(camera[0],-90,"Back camera points down when display lies face-up");
        near(SkyPointing.direction(identity,true,0,0)[1],0,"Top edge north");near(SkyPointing.direction(identity,true,1,0)[1],270,"Rotated screen top west");near(SkyPointing.direction(identity,true,2,0)[1],180,"Inverted screen top south");near(SkyPointing.direction(identity,true,3,0)[1],90,"Rotated screen top east");
        double az=Math.toRadians(100),alt=Math.toRadians(30);double ex=Math.sin(az)*Math.cos(alt),ny=Math.cos(az)*Math.cos(alt),uz=Math.sin(alt);float[] r={(float)Math.cos(az),(float)(-Math.sin(az)*Math.sin(alt)),(float)-ex,(float)-Math.sin(az),(float)(-Math.cos(az)*Math.sin(alt)),(float)-ny,0,(float)Math.cos(alt),(float)-uz};
        double[] aim=SkyPointing.direction(r,false,0,0);near(aim[0],30,"Camera altitude from all three axes");near(aim[1],100,"Camera azimuth from all three axes");near(SkyPointing.direction(r,false,0,5)[1],105,"Declination converts magnetic azimuth to true north");
        double[] point=SkyPointing.marker(0,30,100),direct=SkyGeometry.horizontal(30,100);near(point[0],direct[0],"Aim uses chart horizontal x");near(point[1],direct[1],"Aim uses chart horizontal y");check(SkyPointing.marker(1,30,100)==null,"No marker in equatorial mode");check(SkyPointing.marker(2,30,100)!=null,"Marker without coordinate grid");check(SkyPointing.marker(0,-1,100)==null,"Below horizon hidden");check(SkyPointing.marker(0,Double.NaN,100)==null,"No fabricated marker before sensor result");
        for(int i:new int[]{-999,6,999,Integer.MIN_VALUE,Integer.MAX_VALUE})check(ThemeColors.legacyCourse(i)==ThemeColors.BLUE,"Invalid course indices never wrap into a random color");
        ScheduleModel m=new ScheduleModel();ScheduleModel.Course c=new ScheduleModel.Course();c.name="Test";c.colorHex="#ffcc77";m.put(c);ScheduleModel restored=ScheduleModel.parse(m.json().toString());check(restored.courses.get(0).seedColor()==0xFFFFCC77,"Custom course color persists");check(c.copy().seedColor()==c.seedColor(),"Editing retains course color");
        JSONObject doc=m.json();doc.getJSONArray("courses").getJSONObject(0).put("colorHex","bad");check(ScheduleModel.parse(doc.toString()).courses.get(0).seedColor()==ThemeColors.BLUE,"Bad custom color preserves course, falls back to blue");
        for(int i=0;i<6;i++){doc=m.json();doc.getJSONArray("courses").getJSONObject(0).remove("colorHex");doc.getJSONArray("courses").getJSONObject(0).put("color",i);check(ScheduleModel.parse(doc.toString()).courses.get(0).seedColor()==ThemeColors.legacyCourse(i),"Old course color migrated");}
        doc=m.json();doc.getJSONArray("courses").getJSONObject(0).remove("colorHex");doc.getJSONArray("courses").getJSONObject(0).put("color",Integer.MAX_VALUE);check(ScheduleModel.parse(doc.toString()).courses.get(0).seedColor()==ThemeColors.BLUE,"Corrupt old color cannot crash upgrade");
        System.out.println("Release1211Tests: "+checks+" color, pointing and migration checks passed");
    }
}
