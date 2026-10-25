package org.simpleweather.app;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

/** Regression tests for real provider formats, close aftershocks and cache invalidation. */
public final class EarthquakeTests {
    private static int checks;
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    private static String read(Path p)throws Exception{return new String(Files.readAllBytes(p),StandardCharsets.UTF_8);}
    private static EarthquakeData.Report report(String source,String id,long time,double lat,double lon,double magnitude){EarthquakeData.Report r=new EarthquakeData.Report();r.source=source;r.id=id;r.time=time;r.latitude=lat;r.longitude=lon;r.magnitude=magnitude;r.updated=time;return r;}
    public static void main(String[] args)throws Exception{
        Path fixtures=Paths.get(args[0]);String xml=read(fixtures.resolve("geofon-earthquakes.xml")),json=read(fixtures.resolve("emsc-earthquakes.json"));
        List<EarthquakeData.Report> gfz=EarthquakeData.geofon(xml),emsc=EarthquakeData.emsc(json);
        check(gfz.size()==2&&emsc.size()==2,"Both real catalogue samples parse");
        EarthquakeData.Report g=gfz.get(0),e=emsc.get(0);
        check(g.id.equals("gfz2026uxnb")&&g.magnitudeType.equals("Mw")&&Math.abs(g.magnitude-5.98)<.001,"GEOFON preferred magnitude and ID preserved");
        check(g.depth==10&&Math.abs(g.latitude-32.482)<.0001,"QuakeML meters converted to km, coordinates not swapped");
        check(g.depthNote.contains("默认")&&gfz.get(1).depthNote.contains("固定"),"Potential default 10km and explicitly assigned depth identified separately");
        check(g.review.contains("自动")&&g.updated>=g.time,"Evaluation mode and source creation time retained");
        check(e.id.equals("20261025_0000153")&&e.magnitudeType.equals("mw")&&e.depth==10,"EMSC property depth retained, not negative GeoJSON elevation");
        check(e.agency.equals("GSRAS")&&e.updated>e.time,"EMSC contribution agency and revision time retained");
        check(EarthquakeData.instant("2026-10-25T11:25:58.0Z")==EarthquakeData.instant("2026-10-25T19:25:58+08:00"),"Explicit UTC and offsets refer to same instant");
        check(EarthquakeData.instant("nonsense")==0,"Invalid date rejected rather than replaced with now");
        // Synthetic data only: no redistribution of the live Wolfx catalogue as a fixture.
        JSONObject wxRow=new JSONObject().put("EventID","synthetic-event").put("time","2026-10-26 00:46:46").put("ReportTime","2026-10-26 00:53:38").put("placeName","合成测试地点").put("location","processed name").put("latitude","32.57").put("longitude","93.98").put("magnitude","4.2").put("depth","10").put("type","reviewed").put("intensity","5");
        String wx=new JSONObject().put("No1",wxRow).put("md5","synthetic").toString();
        EarthquakeData.Report w=EarthquakeApi.parse("Wolfx",wx).get(0);
        check(w.time==EarthquakeData.instant("2026-10-25T16:46:46Z")&&w.updated==EarthquakeData.instant("2026-10-25T16:53:38Z"),"Wolfx occurrence and report times explicitly UTC+8, not event-ID timestamp");
        check(w.source.equals("Wolfx")&&w.region.equals("合成测试地点")&&w.magnitudeType.equals("M")&&w.depth==10,"Wolfx original place name and unspecified magnitude scale preserved");
        check(w.review.equals("来源标记已复核")&&w.url().equals("https://www.cenc.ac.cn/"),"Source review label does not invent manual processing or per-event official URL");
        check(EarthquakeData.wolfxTime("2026-02-30 00:00:00")==0&&EarthquakeData.wolfxTime("invalid")==0,"Wolfx invalid calendar date rejected");
        wxRow.remove("EventID");String derived=EarthquakeData.wolfx(new JSONObject().put("No1",wxRow).toString()).get(0).id;
        check(derived.equals(EarthquakeData.wolfx(new JSONObject().put("No23",wxRow).toString()).get(0).id),"Fallback ID remains stable when catalogue rows shift");
        wxRow.put("placeName",JSONObject.NULL).put("depth",JSONObject.NULL).put("type","automatic");w=EarthquakeData.wolfx(new JSONObject().put("No1",wxRow).toString()).get(0);
        check(w.region.equals("processed name")&&Double.isNaN(w.depth)&&w.review.contains("自动"),"Wolfx missing original name falls back, missing depth stays unavailable");
        wxRow.put("latitude",true);boolean wxRejected=false;try{EarthquakeData.wolfx(new JSONObject().put("No1",wxRow).toString());}catch(Exception ex){wxRejected=true;}check(wxRejected,"Wolfx boolean coordinates do not become valid epicentre");
        wxRejected=false;try{EarthquakeData.wolfx("{\"error\":\"maintenance\"}");}catch(Exception ex){wxRejected=true;}check(wxRejected,"Wolfx service error cannot replace cache with empty catalogue");
        wxRow.put("latitude",91);wxRejected=false;try{EarthquakeData.wolfx(new JSONObject().put("No1",wxRow).toString());}catch(Exception ex){wxRejected=true;}check(wxRejected,"Wolfx out-of-range coordinates rejected");
        JSONObject missing=new JSONObject(json);JSONObject p=missing.getJSONArray("features").getJSONObject(0).getJSONObject("properties");p.put("lat",JSONObject.NULL);
        check(EarthquakeData.emsc(missing.toString()).size()==1,"Missing latitude cannot become a false zero-degree epicentre");
        p.put("lat",91);check(EarthquakeData.emsc(missing.toString()).size()==1,"Out-of-range coordinates rejected");
        p.put("lat",32);p.put("mag",true);check(EarthquakeData.emsc(missing.toString()).size()==1,"Boolean magnitude rejected");
        p.put("mag",5);p.put("depth",JSONObject.NULL);check(Double.isNaN(EarthquakeData.emsc(missing.toString()).get(0).depth),"Missing depth stays unavailable");
        p.put("depth",0);check(EarthquakeData.emsc(missing.toString()).get(0).depth==0,"Explicit zero depth is not confused with absent value");
        p.put("evtype","qb");check(EarthquakeData.emsc(missing.toString()).size()==1,"Known quarry blasts excluded from earthquake list");
        boolean rejected=false;try{EarthquakeData.geofon("<!DOCTYPE q [<!ENTITY x SYSTEM 'file:///not-readable'>]>"+xml);}catch(Exception ex){rejected=true;}check(rejected,"External entities rejected before parsing");
        rejected=false;try{EarthquakeData.emsc("{\"error\":\"maintenance\"}");}catch(Exception ex){rejected=true;}check(rejected,"Error response cannot be misreported as an empty valid catalogue");
        rejected=false;try{EarthquakeData.geofon(xml.replace("<value>5.98</value>","<value>NaN</value>").replace("<value>5.16</value>","<value>NaN</value>"));}catch(Exception ex){rejected=true;}check(rejected,"Invalid entire catalogue cannot look like no earthquakes");
        check(EarthquakeApi.parse("GEOFON","").isEmpty(),"HTTP no-content represented by a valid empty response");
        String extra="<origin publicID=\"wrong\"><time><value>2020-01-01T00:00:00Z</value></time><latitude><value>0</value></latitude><longitude><value>0</value></longitude></origin>";
        check(EarthquakeData.geofon(xml.replace("<description>",extra+"<description>")).get(0).latitude==g.latitude,"Preferred origin chosen instead of first origin");
        List<EarthquakeData.Report> all=new ArrayList<>(gfz);all.addAll(emsc);List<EarthquakeData.Event> merged=EarthquakeData.merge(all);
        check(merged.size()==2&&merged.get(0).reports.size()==2&&merged.get(1).reports.size()==2,"Real close-time cross-provider solutions merge, distinct aftershock retained");
        check(Math.abs(merged.get(1).main().magnitude-5.98)<.001,"Display uses a source magnitude, never an average");
        long t=1_800_000_000_000L;
        EarthquakeData.Report a=report("GEOFON","a",t,30,100,5),b=report("GEOFON","b",t+6000,30,100,5),c=report("EMSC","c",t+3000,30,100,5);
        check(EarthquakeData.merge(Arrays.asList(a,b,c)).size()==3,"Ambiguous dense aftershocks not collapsed");
        check(EarthquakeData.merge(Arrays.asList(a,b)).size()==2,"Distinct same-provider events never merged");
        EarthquakeData.Report revised=report("GEOFON","a",t,30,100,5.2);revised.updated=t+1000;
        check(EarthquakeData.merge(Arrays.asList(revised,a)).get(0).main().magnitude==5.2,"Newer same-ID revision wins regardless of input order");
        EarthquakeData.Report distant=report("EMSC","d",t,31,100,5);check(EarthquakeData.merge(Arrays.asList(a,distant)).size()==2,"Distant simultaneous events stay separate");
        EarthquakeData.Report second=report("EMSC","second",t+1000,30.01,100,5.1),third=report("Wolfx","third",t+2000,30.02,100,5.2);
        List<EarthquakeData.Event> triple=EarthquakeData.merge(Arrays.asList(third,a,second));
        check(triple.size()==1&&triple.get(0).reports.size()==3,"All three unambiguous sources merge into one event");
        check(triple.get(0).sources().contains("CENC（Wolfx 转发）")&&triple.get(0).main()==a,"Attribution identifies upstream and relay; original priority retained");
        EarthquakeData.Report chainA=report("GEOFON","chainA",t,30,100,5),chainB=report("EMSC","chainB",t+15000,30,100,5),chainC=report("Wolfx","chainC",t+30000,30,100,5);
        check(EarthquakeData.merge(Arrays.asList(chainA,chainB,chainC)).size()==2,"Three-way merge requires pairwise compatibility, not a time chain");
        check(EarthquakeData.merge(Arrays.asList(a,b,c,report("Wolfx","ambiguous",t+3000,30,100,5))).size()==3,"Third source does not make ambiguous aftershocks collapse");
        check(EarthquakeData.distance(0,0,0,0)==0,"Coincident epicentral distance zero");
        check(Math.abs(EarthquakeData.distance(0,0,0,1)-111.195)<.01,"One-degree great-circle distance in km");
        check(EarthquakeData.distance(0,179.9,0,-179.9)<23,"Distance works across the date line");
        check(Math.abs(EarthquakeData.distance(0,0,0,180)-20015.114)<.02,"Antipodal roundoff remains finite");
        EarthquakeData.Report abroad=report("EMSC","x",t,0,0,4.5),regional=report("GEOFON","y",t,35,110,4.5),future=report("GEOFON","future",t+700000,35,110,7);
        List<EarthquakeData.Event> candidates=EarthquakeData.merge(Arrays.asList(abroad,regional,future));
        check(EarthquakeData.filter(candidates,t-86400000,t,0,5,4,null,1000,0).size()==1,"Regional threshold applied only within broad geographic box; future event excluded");
        check(EarthquakeData.filter(candidates,t-86400000,t,2,3,3,null,1000,0).isEmpty(),"Nearby query without reference cannot silently use zero coordinates");
        check(EarthquakeData.filter(candidates,t-86400000,t,2,3,3,new double[]{35,110},300,0).size()==1,"Local distance filter uses reference point");
        check(EarthquakeData.filter(candidates,t+1,t,0,3,3,null,1000,0).isEmpty(),"Old records excluded from selected window");
        check(EarthquakeData.filter(candidates,t-86400000,t,0,3,3,new double[]{0,0},1000,2).get(0).main().id.equals("x"),"Distance ordering has closest record first");
        String url=EarthquakeApi.url("GEOFON",t,4);check(url.startsWith("https://geofon.gfz.de/fdsnws/event/1/query?")&&url.contains("minmagnitude=4.0")&&!url.contains("latitude")&&!url.contains("longitude"),"Query only sends time and magnitude, not location");
        check(EarthquakeApi.url("Wolfx",t,4).equals("https://api.wolfx.jp/cenc_eqlist.json")&&EarthquakeApi.url("Wolfx",t+1,7).equals(EarthquakeApi.url("Wolfx",t,4)),"Only fixed Wolfx catalogue endpoint, no coordinates, filter parameters or warning stream");
        rejected=false;try{EarthquakeApi.url("unknown",t,4);}catch(IllegalArgumentException ex){rejected=true;}check(rejected,"Unknown source cannot silently request EMSC");
        check(EarthquakeApi.retryAfter("120",t)==t+120000,"Numeric Retry-After respected");
        check(EarthquakeApi.retryAfter("bad",t)==t+60000,"Invalid Retry-After falls back to one minute");
        check(EarthquakeApi.retryAfter("Mon, 26 Oct 2026 00:00:00 GMT",t)>t,"HTTP-date Retry-After supported");
        Path dir=Files.createTempDirectory("quake-cache-");
        try{
            Files.write(dir.resolve("background.jpg"),new byte[]{7,8});Files.write(dir.resolve("courses.json"),new byte[]{9});
            EarthquakeCache store=new EarthquakeCache(dir.toFile());int generation=store.generation();EarthquakeCache.Entry entry=new EarthquakeCache.Entry();entry.raw=json;entry.reports=emsc;entry.fetched=t;entry.since=t-86400000;entry.minimum=4;
            check(store.save("EMSC",entry,generation),"Catalogue cache written");
            EarthquakeCache.Entry cached=store.read("EMSC");check(cached.reports.size()==2&&cached.since==entry.since&&cached.minimum==4,"Cache preserves catalogue and fetched query coverage");
            entry.fetched=t+1000;check(store.save("EMSC",entry,generation)&&store.read("EMSC").fetched==t+1000,"Existing cache safely replaced");
            EarthquakeCache.Entry wxEntry=new EarthquakeCache.Entry();wxEntry.raw=wx;wxEntry.reports=EarthquakeData.wolfx(wx);wxEntry.fetched=t;wxEntry.since=t;wxEntry.minimum=3;
            check(store.save("Wolfx",wxEntry,generation)&&store.read("Wolfx").reports.get(0).id.equals("synthetic-event"),"Wolfx independent cache round-trips synthetic catalogue");
            check(store.clear()&&store.read("EMSC")==null,"Clearing removes existing catalogue");
            check(store.read("Wolfx")==null&&!store.save("Wolfx",wxEntry,generation),"Clear includes Wolfx and prevents old response resurrection");
            check(!store.save("EMSC",entry,generation)&&store.read("EMSC")==null,"In-flight pre-clear response cannot resurrect deleted cache");
            check(Arrays.equals(Files.readAllBytes(dir.resolve("background.jpg")),new byte[]{7,8})&&Files.readAllBytes(dir.resolve("courses.json"))[0]==9,"Clearing quake cache preserves all user content");
            check(store.save("EMSC",entry,store.generation()),"New post-clear request can persist normally");
            int oldGeneration=store.generation();EarthquakeCache recreated=new EarthquakeCache(dir.toFile());recreated.clear();check(!store.save("EMSC",entry,oldGeneration),"Recreated Activity cache shares clear generation with older in-flight requests");
        }finally{try(java.util.stream.Stream<Path> stream=Files.walk(dir)){stream.sorted(Comparator.reverseOrder()).forEach(pth->{try{Files.delete(pth);}catch(Exception ex){throw new RuntimeException(ex);}});}}
        check(DisplayOrder.parse("4,0,2,1,3",6).equals(Arrays.asList(4,0,2,1,3,5)),"New earthquake tab appended without rewriting old order");
        check(DisplayOrder.window(Arrays.asList(0,1,2,3,4,5),3).equals(Arrays.asList(3,4,5)),"Arrow paging supports six tabs");
        System.out.println("EarthquakeTests: "+checks+" checks passed");
    }
}
