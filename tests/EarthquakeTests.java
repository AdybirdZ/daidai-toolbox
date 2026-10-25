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
            check(store.clear()&&store.read("EMSC")==null,"Clearing removes existing catalogue");
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
