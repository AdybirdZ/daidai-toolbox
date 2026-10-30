package org.simpleweather.app;

import org.json.*;
import java.util.*;

public final class StatusTransferTests {
    private static int checks;
    private static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    private static void near(double x,double y,String label){check(Math.abs(x-y)<.01,label+": "+x+" != "+y);}
    private static void reject(String text,String kind,String label){try{StatusTransferData.parse(text,kind);throw new AssertionError(label);}catch(JSONException expected){checks++;}}
    private static String file(String kind,JSONArray rows)throws Exception{return new JSONObject().put("format",StatusTransferData.FORMAT).put("version",1).put("coordinateSystem","WGS84").put("kind",kind).put("records",rows).toString();}
    private static JSONObject route(String id,String name)throws Exception{return new JSONObject().put("id",id).put("name",name).put("points",new JSONArray().put(new JSONObject().put("lon",179.99).put("lat",-31).put("time",1760000000000L).put("accuracy",3.25)).put(new JSONObject().put("lon",-179.99).put("lat",-31.001).put("time",1760000005000L)));}
    public static void main(String[] args)throws Exception {
        JSONArray places=new JSONArray(StatusPlace.encode(Arrays.asList(new StatusPlace("a","红色隐藏地点",-120,-31,1000,0xFFB83636,false))));
        StatusTransferData.Document p=StatusTransferData.parse(StatusTransferData.encode("places",places),"places");
        StatusPlace restored=StatusPlace.decode(p.rows.toString()).get(0);check(restored.color==0xFFB83636&&!restored.visible,"place color and visibility survive interchange");near(restored.longitude,-120,"west longitude preserved");check(restored.created==1000,"place timestamp preserved");
        StatusTransferData.Document r=StatusTransferData.parse(StatusTransferData.encode("routes",new JSONArray().put(route("r","跨日界线路线"))),"routes");
        check(r.rows.getJSONObject(0).getJSONArray("points").length()==2,"all route points restored");near(r.rows.getJSONObject(0).getJSONArray("points").getJSONObject(0).getDouble("accuracy"),3.25,"accuracy preserved");check(r.rows.getJSONObject(0).getJSONArray("points").getJSONObject(1).getLong("time")==1760000005000L,"record times preserved");
        StatusTransferData.Merge first=StatusTransferData.merge(new JSONArray(),r);check(first.added==1&&first.skipped==0,"initial import adds route");
        StatusTransferData.Merge again=StatusTransferData.merge(first.rows,r);check(again.added==0&&again.skipped==1&&again.rows.length()==1,"repeated import skips exact content");
        JSONObject changed=route("r","同标识的新路线");StatusTransferData.Document conflict=StatusTransferData.parse(file("routes",new JSONArray().put(changed)),"routes");
        StatusTransferData.Merge both=StatusTransferData.merge(first.rows,conflict);check(both.rows.length()==2&&both.rows.getJSONObject(0).getString("name").equals("跨日界线路线"),"ID conflict preserves original route");check(!both.rows.getJSONObject(1).getString("id").equals("r"),"conflicting imported route gets fresh ID");
        check(StatusTransferData.merge(both.rows,conflict).skipped==1,"conflicting import is still deduplicated next time");
        JSONObject fractional=route("fractional","小数精度");fractional.getJSONArray("points").getJSONObject(0).put("accuracy",3.3);
        StatusTransferData.Document fractionalDoc=StatusTransferData.parse(file("routes",new JSONArray().put(fractional)),"routes");
        JSONArray storedFractional=StatusTransferData.merge(new JSONArray(),fractionalDoc).rows;
        storedFractional.getJSONObject(0).getJSONArray("points").getJSONObject(0).put("accuracy",(double)(float)3.3);
        check(StatusTransferData.merge(storedFractional,fractionalDoc).skipped==1,"storage float precision does not cause repeated imports to duplicate route");
        JSONObject duplicate=route("other-id","跨日界线路线");check(StatusTransferData.merge(first.rows,StatusTransferData.parse(file("routes",new JSONArray().put(duplicate)),"routes")).skipped==1,"same route under different ID skipped");
        StatusTransferData.Merge pm=StatusTransferData.merge(places,p);check(pm.added==0&&pm.skipped==1,"places deduplicated with their display attributes");
        reject(file("routes",new JSONArray().put(route("bad","bad").put("points",new JSONArray()))),"routes","empty route invalid");
        JSONObject bad=route("bad","bad");bad.getJSONArray("points").getJSONObject(1).put("lon",181);reject(file("routes",new JSONArray().put(bad)),"routes","out-of-range coordinates rejected");
        bad=route("bad","bad");bad.getJSONArray("points").getJSONObject(1).put("time",1);reject(file("routes",new JSONArray().put(bad)),"routes","decreasing timestamps rejected");
        bad=route("bad","bad");bad.getJSONArray("points").getJSONObject(1).put("accuracy",-1);reject(file("routes",new JSONArray().put(bad)),"routes","negative accuracy rejected");
        reject(file("routes",new JSONArray().put(route("r","valid")).put(bad)),"routes","one bad record rejects whole file");
        reject(file("places",places),"routes","wrong file kind rejected");reject(new JSONObject(file("places",places)).put("version",2).toString(),"places","future format version rejected");reject(new JSONObject(file("places",places)).put("version",1.5).toString(),"places","fractional format version rejected");
        reject(file("places",places)+" trailing","places","trailing content rejected");reject("{broken","places","broken JSON rejected");
        bad=new JSONObject(places.getJSONObject(0).toString()).put("visible","false");reject(file("places",new JSONArray().put(bad)),"places","string cannot silently become visibility boolean");
        bad=new JSONObject(places.getJSONObject(0).toString()).put("lat","-31");reject(file("places",new JSONArray().put(bad)),"places","coordinate must be numeric");
        reject(file("places",new JSONArray().put(places.get(0)).put(places.get(0))),"places","duplicate IDs inside file rejected");
        check(StatusTransferData.parse("\uFEFF"+file("places",places),"places").rows.length()==1,"UTF-8 BOM accepted");
        near(RouteViewport.metersAt(0),30,"left edge is 30 meters");near(RouteViewport.metersAt(1000),10000,"right edge is 10 kilometers");
        double previous=0;for(int n=0;n<=1000;n+=10){double meters=RouteViewport.metersAt(n);check(meters>previous,"slider range grows monotonically");check(RouteViewport.progressAt(meters)==n,"slider preserves scale round trip");previous=meters;}
        RouteViewport v=new RouteViewport();v.sideMeters(30);near(v.sideMeters(),30,"minimum range clamped");v.sideMeters(10000);near(v.sideMeters(),10000,"maximum range clamped");v.sideMeters(10);near(v.sideMeters(),30,"too-small range clamps to minimum");v.sideMeters(30000);near(v.sideMeters(),10000,"too-large range clamps to maximum");
        near(StatusMath.placeRange(50,100000),100,"nearby selected target ignores far visible place");near(StatusMath.placeRange(500,100000),1000,"selected target controls kilometer range");near(StatusMath.placeRange(Double.NaN,100000),100000,"no target falls back to farthest visible place");
        System.out.println("StatusTransferTests: "+checks+" checks passed");
    }
}
