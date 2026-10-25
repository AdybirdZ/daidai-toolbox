package org.simpleweather.app;

import org.json.*;
import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

/** Parametric facts only. Different agencies' solutions are preserved, never averaged. */
final class EarthquakeData {
    static final int LIMIT=3000;
    static final String[] SOURCES={"GEOFON","EMSC","Wolfx"};
    static String sourceName(String source){return source.equals("Wolfx")?"CENC（Wolfx 转发）":source;}
    static final class Report {
        String source="",id="",region="",magnitudeType="M",agency="",review="未提供复核状态",depthNote="来源未说明深度是否固定";
        long time,updated;
        double latitude,longitude,magnitude,depth=Double.NaN;
        String magnitudeLabel(){return magnitudeType+" "+String.format(Locale.ROOT,"%.1f",magnitude);}
        String url(){String encoded;try{encoded=java.net.URLEncoder.encode(id,"UTF-8");}catch(Exception e){encoded="";}return source.equals("Wolfx")?"https://www.cenc.ac.cn/":source.equals("GEOFON")?"https://geofon.gfz.de/eqinfo/event.php?id="+encoded:"https://www.seismicportal.eu/eventdetails.html?unid="+encoded;}
    }
    static final class Event {
        final List<Report> reports=new ArrayList<>();
        Report main(){
            Report best=reports.get(0);
            for(Report r:reports)if(priority(r)>priority(best)||(priority(r)==priority(best)&&r.updated>best.updated))best=r;
            return best;
        }
        String sources(){StringJoiner s=new StringJoiner("、");for(Report r:reports)s.add(sourceName(r.source));return s.toString();}
        double maxMagnitude(){double m=-99;for(Report r:reports)m=Math.max(m,r.magnitude);return m;}
    }
    private static int priority(Report r){return (r.review.contains("人工")?2:0)+(r.source.equals("GEOFON")?1:0);}
    static long instant(String value){
        if(value==null||value.isEmpty())return 0;
        try{return Instant.parse(value.endsWith("Z")?value:value+"Z").toEpochMilli();}catch(Exception ignored){}
        try{return OffsetDateTime.parse(value).toInstant().toEpochMilli();}catch(Exception ignored){return 0;}
    }
    private static boolean valid(Report r){return r.time>0&&!r.id.isEmpty()&&Double.isFinite(r.latitude)&&Math.abs(r.latitude)<=90&&Double.isFinite(r.longitude)&&Math.abs(r.longitude)<=180&&Double.isFinite(r.magnitude)&&r.magnitude>=-2&&r.magnitude<=10.5;}
    static List<Report> emsc(String raw)throws Exception{
        JSONObject root=new JSONObject(raw);if(!root.optString("type").equals("FeatureCollection"))throw new IOException("EMSC 返回格式异常");
        JSONArray features=root.getJSONArray("features");List<Report> result=new ArrayList<>();int eligible=0;
        for(int i=0;i<features.length();i++)try{
            JSONObject f=features.getJSONObject(i),p=f.getJSONObject("properties");
            String type=p.optString("evtype","");if(!type.isEmpty()&&!Arrays.asList("ke","se","le").contains(type))continue;
            eligible++;Report r=new Report();r.source="EMSC";r.id=p.optString("unid",f.optString("id"));r.time=instant(p.optString("time"));r.updated=instant(p.optString("lastupdate"));
            r.latitude=number(p,"lat");r.longitude=number(p,"lon");r.depth=number(p,"depth");r.magnitude=number(p,"mag");r.region=p.optString("flynn_region","未提供地区名称");r.agency=p.optString("auth","");
            String mt=p.optString("magtype","");r.magnitudeType=mt.isEmpty()?"M":mt;
            if(r.depth<0||r.depth>800)r.depth=Double.NaN;
            if(valid(r))result.add(r);
        }catch(JSONException ignored){}
        if(eligible>0&&result.isEmpty())throw new IOException("EMSC 事件参数无效");
        return result;
    }
    private static double number(JSONObject p,String key){Object v=p.opt(key);if(v==null||v==JSONObject.NULL||v instanceof Boolean)return Double.NaN;try{return Double.parseDouble(v.toString());}catch(Exception e){return Double.NaN;}}
    /** Wolfx documents catalogue times as UTC+8. Row numbers are not event IDs. */
    static long wolfxTime(String value){try{return LocalDateTime.parse(value,java.time.format.DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss").withResolverStyle(java.time.format.ResolverStyle.STRICT)).toInstant(ZoneOffset.ofHours(8)).toEpochMilli();}catch(Exception e){return 0;}}
    static List<Report> wolfx(String raw)throws Exception{
        JSONObject root=new JSONObject(raw);List<Report> result=new ArrayList<>();int rows=0;
        for(Iterator<String> it=root.keys();it.hasNext();){String key=it.next();if(!key.matches("No[1-9][0-9]*"))continue;rows++;JSONObject p=root.optJSONObject(key);if(p==null)continue;
            Report r=new Report();r.source="Wolfx";r.time=wolfxTime(p.optString("time"));r.updated=wolfxTime(p.optString("ReportTime"));r.latitude=number(p,"latitude");r.longitude=number(p,"longitude");r.magnitude=number(p,"magnitude");r.depth=number(p,"depth");
            r.id=p.optString("EventID","").trim();if(r.id.isEmpty()||r.id.equals("null"))r.id="derived:"+r.time+":"+r.latitude+":"+r.longitude;
            r.region=p.optString("placeName","").trim();if(r.region.isEmpty()||r.region.equals("null"))r.region=p.optString("location","未提供地区名称");r.agency="CENC（经 Wolfx 转发，非官方接口）";
            String type=p.optString("type");if(type.equals("reviewed"))r.review="来源标记已复核";else if(type.equals("automatic"))r.review="自动解，参数可能修订";
            if(r.depth<0||r.depth>800)r.depth=Double.NaN;
            if(valid(r))result.add(r);
        }
        if(rows==0||result.isEmpty())throw new IOException("Wolfx 目录格式或事件参数无效");
        return result;
    }
    static List<Report> geofon(String raw)throws Exception{
        if(raw.toUpperCase(Locale.ROOT).contains("<!DOCTYPE")||raw.toUpperCase(Locale.ROOT).contains("<!ENTITY"))throw new IOException("不接受包含外部实体的数据");
        DocumentBuilderFactory f=DocumentBuilderFactory.newInstance();f.setNamespaceAware(true);f.setExpandEntityReferences(false);
        for(String feature:new String[]{"http://xml.org/sax/features/external-general-entities","http://xml.org/sax/features/external-parameter-entities","http://apache.org/xml/features/nonvalidating/load-external-dtd"})try{f.setFeature(feature,false);}catch(Exception ignored){}
        javax.xml.parsers.DocumentBuilder b=f.newDocumentBuilder();b.setEntityResolver((publicId,systemId)->{throw new org.xml.sax.SAXException("外部实体已禁用");});
        Document doc=b.parse(new ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8)));
        if(doc.getElementsByTagNameNS("*","eventParameters").getLength()==0)throw new IOException("GEOFON 返回格式异常");
        NodeList events=doc.getElementsByTagNameNS("*","event");List<Report> result=new ArrayList<>();int eligible=0;
        for(int i=0;i<events.getLength();i++){
            Element e=(Element)events.item(i);String type=value(e,"type");if(!type.isEmpty()&&!type.equals("earthquake"))continue;
            eligible++;Element o=preferred(e,"origin",value(e,"preferredOriginID")),m=preferred(e,"magnitude",value(e,"preferredMagnitudeID"));if(o==null||m==null)continue;
            Report r=new Report();r.source="GEOFON";String publicId=e.getAttribute("publicID");r.id=publicId.substring(publicId.lastIndexOf('/')+1);
            r.time=instant(quantity(o,"time"));r.latitude=parse(quantity(o,"latitude"));r.longitude=parse(quantity(o,"longitude"));r.magnitude=parse(quantity(m,"mag"));r.depth=parse(quantity(o,"depth"))/1000;
            r.region=value(child(e,"description"),"text");if(r.region.isEmpty())r.region="未提供地区名称";
            r.magnitudeType=value(m,"type");if(r.magnitudeType.isEmpty())r.magnitudeType="M";
            r.agency=value(child(o,"creationInfo"),"agencyID");r.updated=Math.max(instant(value(child(o,"creationInfo"),"creationTime")),instant(value(child(e,"creationInfo"),"creationTime")));
            String mode=value(o,"evaluationMode"),status=value(o,"evaluationStatus");
            if(mode.equals("manual"))r.review="人工处理";else if(status.equals("reviewed")||status.equals("confirmed"))r.review="已复核";else if(mode.equals("automatic"))r.review="自动解，参数可能修订";
            String depthType=value(o,"depthType");
            if(depthType.contains("assigned")||value(o,"depthFixed").equals("true"))r.depthNote="来源标记为固定／指定深度，不能视为可靠实测";
            else if(Double.isFinite(r.depth)&&Math.abs(r.depth-10)<.001)r.depthNote="10 km 可能为默认浅源深度，不能据此确定实际深度";
            if(r.depth<0||r.depth>800)r.depth=Double.NaN;
            if(valid(r))result.add(r);
        }
        if(eligible>0&&result.isEmpty())throw new IOException("GEOFON 事件参数无效");
        return result;
    }
    private static Element child(Element e,String name){if(e!=null)for(Node n=e.getFirstChild();n!=null;n=n.getNextSibling())if(n instanceof Element&&(name.equals(n.getLocalName())||name.equals(n.getNodeName())))return (Element)n;return null;}
    private static String value(Element e,String name){Element c=child(e,name);return c==null?"":c.getTextContent().trim();}
    private static String quantity(Element e,String name){return value(child(e,name),"value");}
    private static double parse(String s){try{return Double.parseDouble(s);}catch(Exception e){return Double.NaN;}}
    private static Element preferred(Element e,String name,String id){Element first=null;for(Node n=e.getFirstChild();n!=null;n=n.getNextSibling())if(n instanceof Element&&name.equals(n.getLocalName())){Element c=(Element)n;if(first==null)first=c;if(c.getAttribute("publicID").equals(id))return c;}return first;}
    static double distance(double a,double b,double c,double d){double lat=Math.toRadians(c-a),lon=Math.toRadians(d-b);double h=Math.pow(Math.sin(lat/2),2)+Math.cos(Math.toRadians(a))*Math.cos(Math.toRadians(c))*Math.pow(Math.sin(lon/2),2);return 6371.0088*2*Math.asin(Math.sqrt(Math.max(0,Math.min(1,h))));}
    /** Broad geographic filter only, not an administrative boundary. */
    static boolean nearbyChina(double lat,double lon){return lat>=15&&lat<=55&&lon>=70&&lon<=140;}
    static List<Event> merge(Collection<Report> input){
        Map<String,Report> unique=new LinkedHashMap<>();
        for(Report r:input){String key=r.source+":"+r.id;Report old=unique.get(key);if(old==null||r.updated>=old.updated)unique.put(key,r);}
        List<Report> list=new ArrayList<>(unique.values());list.sort(Comparator.comparingLong(r->r.time));List<Event> result=new ArrayList<>();
        Map<Report,Map<String,Report>> closest=new IdentityHashMap<>();
        for(int index=0;index<list.size();index++){
            Report r=list.get(index);Map<String,Report> matches=new HashMap<>();
            for(String source:SOURCES){if(source.equals(r.source))continue;Report best=null;double cost=Double.POSITIVE_INFINITY,second=Double.POSITIVE_INFINITY;
            for(int direction:new int[]{-1,1})for(int i=index+direction;i>=0&&i<list.size();i+=direction){
                Report other=list.get(i);long dt=Math.abs(other.time-r.time);if(dt>20_000)break;if(!other.source.equals(source))continue;
                double km=distance(r.latitude,r.longitude,other.latitude,other.longitude);if(km>35||Math.abs(r.magnitude-other.magnitude)>1)continue;
                double score=dt/1000.0+km;if(score<cost){second=cost;cost=score;best=other;}else second=Math.min(second,score);
            }
            if(best!=null&&second-cost>=5)matches.put(source,best);
            }closest.put(r,matches);
        }
        Set<Report> used=Collections.newSetFromMap(new IdentityHashMap<Report,Boolean>());
        for(Report r:list){if(used.contains(r))continue;Event event=new Event();event.reports.add(r);used.add(r);
            for(String source:SOURCES){Report match=closest.get(r).get(source);if(match==null||used.contains(match))continue;boolean mutual=true;for(Report member:event.reports)if(closest.get(member).get(match.source)!=match||closest.get(match).get(member.source)!=member){mutual=false;break;}if(mutual){event.reports.add(match);used.add(match);}}result.add(event);
        }
        return result;
    }
    static List<Event> filter(List<Event> all,long since,long now,int area,double globalMin,double regionalMin,double[] reference,double radius,int sort){
        List<Event> out=new ArrayList<>();for(Event e:all){Report r=e.main();if(r.time<since||r.time>now+600_000)continue;
            boolean region=nearbyChina(r.latitude,r.longitude);double min=region?Math.min(globalMin,regionalMin):globalMin;
            if(e.maxMagnitude()<min||(area==1&&!region))continue;if(area==2&&(reference==null||distance(reference[0],reference[1],r.latitude,r.longitude)>radius))continue;out.add(e);
        }
        if(sort==1)out.sort((a,b)->Double.compare(b.maxMagnitude(),a.maxMagnitude()));
        else if(sort==2&&reference!=null)out.sort(Comparator.comparingDouble(e->distance(reference[0],reference[1],e.main().latitude,e.main().longitude)));
        else out.sort((a,b)->Long.compare(b.main().time,a.main().time));return out;
    }
}
