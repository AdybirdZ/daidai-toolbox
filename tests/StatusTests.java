package org.simpleweather.app;

import java.util.*;

public final class StatusTests {
    private static int checks;
    private static void check(boolean result,String why){checks++;if(!result)throw new AssertionError(why);}
    private static boolean close(double a,double b,double tolerance){return Math.abs(a-b)<=tolerance;}
    public static void main(String[] args){
        check(StatusMath.norm(-1)==359&&StatusMath.norm(721)==1,"Heading wraps across north");
        check(Double.isNaN(StatusMath.norm(Double.NaN)),"Invalid compass input is not north");
        for(int i=0;i<16;i++)check(StatusMath.direction(i*22.5).equals(StatusMath.directions()[i]),"Sixteen named sectors: "+i);
        check(StatusMath.direction(348.75).equals("北")&&StatusMath.direction(11.249).equals("北")&&StatusMath.direction(11.25).equals("东北偏北"),"North and half-sector boundaries");
        check(StatusMath.direction(157.5).equals("东南偏南"),"Southeast by south requested name");
        check(StatusMath.direction(-90).equals("西")&&StatusMath.direction(Double.NaN).equals("方向不可用"),"Negative and invalid directions");
        check(StatusMath.delta(1,359)==2&&StatusMath.delta(359,1)==-2,"Shortest signed turn across north");
        check(close(StatusMath.smooth(359,1,.5),0,1e-8),"Smoothing crosses north without spinning south");
        check(StatusMath.smooth(Double.NaN,90,.2)==90,"First sample initializes direction");
        check(StatusMath.distance(116,39,116,39)==0,"Same coordinates have zero distance");
        check(close(StatusMath.distance(0,0,1,0),111195.08,.1),"Equatorial degree agrees with spherical reference");
        check(close(StatusMath.distance(179,0,-179,0),222390.16,.2),"Distance crosses antimeridian by short path");
        check(close(StatusMath.distance(0,0,180,0),Math.PI*6371008.8,1e-5),"Antipodes remain finite");
        check(StatusMath.bearing(0,0,0,1)==0&&StatusMath.bearing(0,0,1,0)==90,"North and east bearings");
        check(StatusMath.bearing(0,0,-1,0)==270&&StatusMath.bearing(0,0,0,-1)==180,"West and south bearings");
        check(Double.isNaN(StatusMath.bearing(0,0,0,0)),"Coincident points have no fabricated bearing");
        check(Double.isNaN(StatusMath.distance(181,0,0,0))&&!StatusMath.coordinate(0,91),"Out of bounds coordinates rejected");
        check(StatusMath.coordinate(-180,-90)&&StatusMath.coordinate(180,90),"Coordinate endpoints accepted");
        check(StatusMath.satelliteAngles(-10,360),"Negative elevation is valid metadata even below plot horizon");
        check(!StatusMath.satelliteAngles(91,180)&&!StatusMath.satelliteAngles(20,361),"Invalid GNSS angles do not plot");
        check(!StatusMath.satelliteAngles(Double.NaN,0)&&!StatusMath.satelliteAngles(0,Double.POSITIVE_INFINITY),"Missing GNSS geometry is not a north horizon point");
        double[] zenith=StatusMath.sky(90,120),east=StatusMath.sky(0,90),north=StatusMath.sky(0,0);
        check(close(zenith[0],0,1e-8)&&close(zenith[1],0,1e-8),"Zenith at sky center");
        check(close(east[0],1,1e-8)&&close(east[1],0,1e-8)&&north[1]==-1,"Azimuth maps to normalized sky coordinates");
        check(StatusMath.constellation(1).equals("美国 GPS")&&StatusMath.constellation(5).equals("中国 BDS")&&StatusMath.constellation(6).equals("欧洲 Galileo")&&StatusMath.constellation(3).equals("俄罗斯 GLONASS"),"Android constellation identifiers mapped correctly");
        check(StatusMath.constellation(99).equals("其他系统")&&StatusMath.satelliteId(5,12).equals("卫星 12"),"Unknown systems retain honest fallback");
        StatusPlace p=new StatusPlace("id1","  寻宝地点  ",-122.4000000123,-33.8000000456,123);
        List<StatusPlace> rows=StatusPlace.decode(StatusPlace.encode(Arrays.asList(p)));
        check(rows.size()==1&&rows.get(0).name.equals("寻宝地点")&&rows.get(0).created==123,"Unicode waypoint round trip and trim");
        check(rows.get(0).longitude==p.longitude&&rows.get(0).latitude==p.latitude,"Signed coordinates retain double precision");
        String good=StatusPlace.encode(Arrays.asList(p));String element=good.substring(1,good.length()-1);
        check(StatusPlace.decode("["+element+","+element+",{},null]").size()==1,"Duplicates and malformed records do not create extra targets");
        check(StatusPlace.decode("broken").isEmpty()&&StatusPlace.decode(null).isEmpty(),"Malformed store safely yields empty list");
        boolean rejected=false;try{new StatusPlace("bad","x",Double.NaN,0,0);}catch(IllegalArgumentException ex){rejected=true;}check(rejected,"Nonfinite coordinates cannot be saved");
        rejected=false;try{new StatusPlace("bad"," ",0,0,0);}catch(IllegalArgumentException ex){rejected=true;}check(rejected,"Empty waypoint name rejected");
        StringBuilder longName=new StringBuilder();for(int i=0;i<61;i++)longName.append('字');rejected=false;try{new StatusPlace("bad",longName.toString(),0,0,0);}catch(IllegalArgumentException ex){rejected=true;}check(rejected,"Waypoint name length is bounded");
        List<StatusPlace> many=new ArrayList<>();for(int i=0;i<201;i++)many.add(new StatusPlace("id"+i,"x",0,0,0));check(StatusPlace.decode(StatusPlace.encode(many)).size()==201,"Waypoint persistence has no artificial record count");
        check(StatusMath.distanceText(123).equals("123.0 米")&&StatusMath.distanceText(1234).equals("1.23 km"),"Distance units switch at one kilometer");
        StatusPlace legacy=StatusPlace.decode("[{\"id\":\"old\",\"name\":\"旧地点\",\"lon\":120,\"lat\":31}]").get(0);
        check(legacy.visible&&legacy.color==StatusPlace.DEFAULT_COLOR,"Legacy places default to visible blue without data loss");
        StatusPlace colored=new StatusPlace("red","红点",120,31,456,0xFFB83636,false);
        StatusPlace restored=StatusPlace.decode(StatusPlace.encode(Arrays.asList(colored))).get(0);
        check(!restored.visible&&restored.color==0xFFB83636&&restored.created==456,"Color and hidden setting persist across round trip");
        StatusPlace shown=restored.withVisibility(true);
        check(shown.visible&&shown.id.equals(restored.id)&&shown.color==restored.color&&shown.longitude==restored.longitude,"Visibility update preserves identity, color and coordinates");
        check(!restored.visible,"Visibility change leaves original snapshot intact");
        check(new StatusPlace("opaque","点",0,0,0,0x00246BCE,true).color==0xFF246BCE,"Saved marker colors remain opaque and visible");
        check(BatteryReadings.current(-900000,"uA",false)==-900,"Standard microampere conversion");
        check(BatteryReadings.current(-900,"mA",false)==-900,"Explicit milliampere compatibility");
        check(BatteryReadings.current(-900,"uA",false)==-.9,"Small raw values do not trigger heuristic multiplication");
        check(BatteryReadings.current(-900,"mA",true)==900,"Sign override remains independent of units");
        check(BatteryReadings.current(500000,"uA",false)==500,"Positive charging current preserved");
        check(BatteryReadings.current(Integer.MAX_VALUE,"mA",true)==-2147483647.,"No integer negation overflow");
        check(Double.isNaN(BatteryReadings.current(Integer.MIN_VALUE,"mA",true)),"Missing current is not a reading");
        check(BatteryReadings.current(0,"mA",false)==0,"Zero current is valid");
        check(BatteryReadings.charge(3600000,"uAh")==3600,"Standard charge conversion");
        check(BatteryReadings.charge(3600,"mAh")==3600,"Explicit milliampere-hour compatibility");
        check(BatteryReadings.charge(3600,"uAh")==3.6,"No unit inference based on charge magnitude");
        check(Double.isNaN(BatteryReadings.charge(Integer.MIN_VALUE,"mAh"))&&Double.isNaN(BatteryReadings.charge(-1,"mAh")),"Missing or invalid charge remains unavailable");
        check(BatteryReadings.charge(0,"uAh")==0,"Empty charge remains valid zero");
        check(BatteryReadings.current(-900000,"unknown",false)==-900&&BatteryReadings.charge(3600000,"unknown")==3600,"Unexpected preferences safely fall back to standard units");
        System.out.println("StatusTests: "+checks+" checks passed");
    }
}


