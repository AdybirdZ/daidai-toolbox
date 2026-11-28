package org.daidai.toolbox;

import java.util.*;
import org.json.*;

/** Desktop placement must retain identities across module uninstall, reinstall and profile transfer. */
public final class LauncherLayoutTests {
    private static int checks;
    private static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    private static void reject(JSONObject j,String label){boolean refused=false;try{LauncherLayout.parse(j.toString(),false);}catch(Exception expected){refused=true;}check(refused,label);}
    public static void main(String[] args)throws Exception {
        LauncherLayout phone=new LauncherLayout(false),tablet=new LauncherLayout(true);
        check(phone.columns==3&&tablet.columns==5,"profiles have independent defaults");
        check(phone.icon==72&&phone.text==14,"geometry defaults");
        check(phone.visible(LauncherLayout.APPS).equals(LauncherLayout.APPS),"stable default order");
        check(phone.visible(Collections.emptyList()).isEmpty(),"zero apps remains valid");
        LauncherLayout legacy=LauncherLayout.legacy(false,"music,status,music,unknown,weather");
        check(legacy.order.equals(Arrays.asList("music","status","weather")),"legacy duplicates and unknown IDs ignored");
        check(legacy.visible(Arrays.asList("weather","music")).equals(Arrays.asList("music","weather")),"legacy relative order retained");
        phone.move("weather","status",null);
        check(phone.visible(LauncherLayout.APPS).subList(0,3).equals(Arrays.asList("schedule","status","weather")),"forward drag targets final position");
        phone.move("weather","schedule",null);
        check(phone.visible(LauncherLayout.APPS).equals(LauncherLayout.APPS),"reverse drag restores order");
        String folder=phone.create("出行",Arrays.asList("weather","status","weather"));
        check(phone.folders.get(folder).apps.equals(Arrays.asList("weather","status")),"folder members deduplicated");
        check(!phone.visible(LauncherLayout.APPS).contains("weather"),"member not duplicated on desktop");
        check(phone.visibleFolder(folder,Arrays.asList("status")).equals(Arrays.asList("status")),"uninstalled member hidden");
        check(phone.folders.get(folder).apps.equals(Arrays.asList("weather","status")),"uninstall does not delete placement");
        check(!phone.visible(Collections.singleton("music")).contains(folder),"empty installed folder hidden");
        check(phone.visibleFolder(folder,LauncherLayout.APPS).equals(Arrays.asList("weather","status")),"reinstall restores member order");
        phone.move("weather","status",folder);
        check(phone.folders.get(folder).apps.equals(Arrays.asList("status","weather")),"folder drag forward");
        String second=phone.create("工具",Collections.singleton("music"));phone.put("weather",second);
        check(!phone.folders.get(folder).apps.contains("weather")&&phone.folders.get(second).apps.contains("weather"),"move across folders has one owner");
        phone.put("weather",null);
        check(phone.order.contains("weather")&&phone.folderOf("weather")==null,"move to desktop clears folder ownership");
        int secondIndex=phone.order.indexOf(second);phone.deleteFolder(second);
        check(phone.order.get(secondIndex).equals("music"),"delete folder places members at previous folder position");
        check(!phone.folders.containsKey(second),"folder metadata removed");
        phone.icon=96;phone.columns=7;phone.text=22;
        LauncherLayout decoded=LauncherLayout.parse(phone.json(),false);
        check(decoded.json().equals(phone.json()),"layout and Unicode folder name round trip");
        check(tablet.icon==72&&tablet.columns==5&&tablet.text==14,"phone changes do not mutate tablet");
        check(LauncherLayout.parse(phone.json(),true).columns==7,"import preserves chosen columns rather than applying profile defaults");
        check(LauncherLayout.key(false).equals("launcher_layout_phone")&&LauncherLayout.key(true).equals("launcher_layout_tablet"),"separate storage identities");
        check(LauncherLayout.validName("观星 · 2026")&&!LauncherLayout.validName(" ")&&!LauncherLayout.validName("abc\ndef")&&!LauncherLayout.validName(" a"),"folder names validated");
        JSONObject base=new JSONObject(phone.json());
        reject(new JSONObject(phone.json()).put("version",2),"unknown layout version rejected");
        reject(new JSONObject(phone.json()).put("icon",47),"too-small icon rejected");
        reject(new JSONObject(phone.json()).put("icon",97),"too-large icon rejected");
        reject(new JSONObject(phone.json()).put("columns",1),"invalid columns rejected");
        reject(new JSONObject(phone.json()).put("text",23),"invalid label size rejected");
        reject(new JSONObject(phone.json()).put("columns",3.5),"fractional column count rejected");
        reject(new JSONObject(phone.json()).put("text","14"),"string pretending to be number rejected");
        reject(new JSONObject(phone.json()).put("order",new JSONArray(Arrays.asList("weather","weather"))),"duplicate root app rejected");
        reject(new JSONObject(phone.json()).put("order",new JSONArray(Arrays.asList("../../escape"))),"unknown identity rejected");
        JSONObject duplicate=new JSONObject(phone.json());duplicate.getJSONArray("order").put("status");reject(duplicate,"folder member duplicated on desktop rejected");
        JSONObject nested=new JSONObject(phone.json());nested.getJSONArray("folders").getJSONObject(0).getJSONArray("apps").put(folder);reject(nested,"nested folder rejected");
        JSONObject missing=new JSONObject(phone.json());missing.put("order",new JSONArray());reject(missing,"unreachable folder rejected");
        JSONObject name=new JSONObject(phone.json());name.getJSONArray("folders").getJSONObject(0).put("name"," ");reject(name,"blank folder rejected");
        JSONObject unknown=new JSONObject(phone.json());unknown.getJSONArray("folders").getJSONObject(0).getJSONArray("apps").put("calculator");reject(unknown,"cancelled application cannot be imported");
        LauncherLayout masked=LauncherLayout.legacy(false,"weather,music,status");masked.move("weather","status",null);
        check(masked.visible(Arrays.asList("weather","status")).equals(Arrays.asList("status","weather")),"reordering visible apps tolerates uninstalled apps between them");
        LauncherLayout all=new LauncherLayout(false);String every=all.create("全部",LauncherLayout.APPS);all.deleteFolder(every);
        check(all.visible(LauncherLayout.APPS).equals(LauncherLayout.APPS),"delete full folder retains every app exactly once");
        Set<String> once=new HashSet<>(decoded.visible(LauncherLayout.APPS));once.removeAll(decoded.folders.keySet());for(String id:decoded.folders.keySet())once.addAll(decoded.visibleFolder(id,LauncherLayout.APPS));
        check(once.equals(new HashSet<>(LauncherLayout.APPS)),"all app identities preserved through folder operations");
        System.out.println("PASS: "+checks+" desktop layout checks");
    }
}
