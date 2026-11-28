package org.daidai.toolbox;

import org.json.*;
import java.util.*;

/** Persistent desktop geometry and stable identities; no Android or installed-state dependency. */
public final class LauncherLayout {
    public static final String PHONE_KEY="launcher_layout_phone",TABLET_KEY="launcher_layout_tablet";
    public static final List<String> APPS=Collections.unmodifiableList(Arrays.asList("weather","schedule","status","weather-map","astronomy","earthquake","random","todo","music","messaging","colorvision"));
    public static final class Folder {
        public final String id;
        public String name;
        public final List<String> apps=new ArrayList<>();
        Folder(String id,String name){this.id=id;this.name=name;}
    }
    public int icon=72,columns,text=14;
    public final List<String> order=new ArrayList<>();
    public final Map<String,Folder> folders=new LinkedHashMap<>();
    public LauncherLayout(boolean tablet){columns=tablet?5:3;}
    public static boolean application(String id){return APPS.contains(id);}
    public static String key(boolean tablet){return tablet?TABLET_KEY:PHONE_KEY;}
    public static LauncherLayout legacy(boolean tablet,String oldOrder){LauncherLayout l=new LauncherLayout(tablet);for(String id:oldOrder.split(","))if(application(id)&&!l.order.contains(id))l.order.add(id);return l;}
    public static LauncherLayout parse(String raw,boolean tablet)throws JSONException {
        LauncherLayout l=new LauncherLayout(tablet);if(raw==null||raw.isEmpty())return l;
        if(raw.length()>65536)throw new JSONException("Desktop layout too large");
        JSONObject j=new JSONObject(raw);if(j.getInt("version")!=1)throw new JSONException("Unsupported desktop layout");
        l.icon=integer(j,"icon",48,96);l.columns=integer(j,"columns",2,8);l.text=integer(j,"text",10,22);
        JSONArray f=j.getJSONArray("folders"),o=j.getJSONArray("order");if(f.length()>32||o.length()>43)throw new JSONException("Too many desktop items");
        Set<String> assigned=new HashSet<>();
        for(int n=0;n<f.length();n++){
            JSONObject v=f.getJSONObject(n);String id=v.getString("id"),name=v.getString("name");
            if(!id.matches("folder:[a-zA-Z0-9_-]{1,64}")||l.folders.containsKey(id)||!validName(name))throw new JSONException("Invalid folder");
            Folder folder=new Folder(id,name);JSONArray items=v.getJSONArray("apps");if(items.length()>APPS.size())throw new JSONException("Too many folder entries");
            for(int i=0;i<items.length();i++){String app=items.getString(i);if(!application(app)||!assigned.add(app))throw new JSONException("Duplicate or unknown application");folder.apps.add(app);}
            l.folders.put(id,folder);
        }
        Set<String> roots=new HashSet<>();
        for(int i=0;i<o.length();i++){
            String id=o.getString(i);if(!roots.add(id))throw new JSONException("Duplicate desktop item");
            if(l.folders.containsKey(id))l.order.add(id);
            else{if(!application(id)||!assigned.add(id))throw new JSONException("Duplicate or unknown application");l.order.add(id);}
        }
        for(String id:l.folders.keySet())if(!roots.contains(id))throw new JSONException("Folder missing from desktop order");
        return l;
    }
    private static int integer(JSONObject j,String key,int min,int max)throws JSONException {Object v=j.get(key);if(!(v instanceof Integer)&&!(v instanceof Long))throw new JSONException("Invalid desktop number");long n=((Number)v).longValue();if(n<min||n>max)throw new JSONException("Desktop number out of range");return (int)n;}
    public static boolean validName(String name){return name!=null&&!name.trim().isEmpty()&&name.equals(name.trim())&&name.length()<=40&&!name.matches("(?s).*[\\p{Cntrl}].*");}
    public String json(){try{
        JSONObject j=new JSONObject().put("version",1).put("icon",icon).put("columns",columns).put("text",text);JSONArray f=new JSONArray();
        for(Folder folder:folders.values())f.put(new JSONObject().put("id",folder.id).put("name",folder.name).put("apps",new JSONArray(folder.apps)));
        return j.put("folders",f).put("order",new JSONArray(order)).toString();
    }catch(JSONException e){throw new IllegalStateException(e);}}
    public List<String> visible(Collection<String> installed){
        Set<String> live=new HashSet<>(installed),assigned=new HashSet<>();for(Folder f:folders.values())assigned.addAll(f.apps);
        List<String> out=new ArrayList<>();
        for(String id:order)if(folders.containsKey(id)){if(!visibleFolder(id,live).isEmpty())out.add(id);}else if(live.contains(id))out.add(id);
        for(String id:APPS)if(live.contains(id)&&!assigned.contains(id)&&!out.contains(id))out.add(id);
        return out;
    }
    public List<String> visibleFolder(String id,Collection<String> installed){List<String> out=new ArrayList<>();Folder f=folders.get(id);if(f!=null)for(String app:f.apps)if(installed.contains(app))out.add(app);return out;}
    public String folderOf(String app){for(Folder f:folders.values())if(f.apps.contains(app))return f.id;return null;}
    public String create(String name,Collection<String> members){
        if(!validName(name))throw new IllegalArgumentException("Invalid folder name");
        for(String id:members)if(!application(id))throw new IllegalArgumentException("Unknown application");
        if(folders.size()>=32)throw new IllegalStateException("Too many folders");
        complete();String id="folder:"+UUID.randomUUID().toString();Folder f=new Folder(id,name);folders.put(id,f);order.add(id);for(String app:new LinkedHashSet<>(members))put(app,id);return id;
    }
    public void put(String app,String folderId){
        if(!application(app)||folderId!=null&&!folders.containsKey(folderId))throw new IllegalArgumentException("Invalid desktop destination");
        order.remove(app);for(Folder f:folders.values())f.apps.remove(app);
        if(folderId==null)order.add(app);else folders.get(folderId).apps.add(app);
    }
    public void deleteFolder(String id){Folder f=folders.remove(id);if(f==null)return;int position=order.indexOf(id);order.remove(id);order.addAll(position<0?order.size():position,f.apps);}
    public void move(String source,String target,String folderId){
        List<String> sequence;
        if(folderId==null){complete();sequence=order;}
        else{Folder f=folders.get(folderId);if(f==null)return;sequence=f.apps;}
        int at=sequence.indexOf(target);if(at<0||!sequence.contains(source)||source.equals(target))return;sequence.remove(source);sequence.add(Math.min(at,sequence.size()),source);
    }
    private void complete(){for(String app:APPS)if(folderOf(app)==null&&!order.contains(app))order.add(app);}
}
