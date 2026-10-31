package org.simpleweather.app;

import org.json.*;
import java.util.*;

public final class RandomChoiceTests {
    static int checks;
    static void check(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
    static void rejected(Runnable action,String why){boolean bad=false;try{action.run();}catch(Exception e){bad=true;}check(bad,why);}
    static void invalid(JSONObject j,String why){rejected(()->{try{RandomChoiceModel.parse(j.toString());}catch(JSONException e){throw new IllegalArgumentException(e);}},why);}
    public static void main(String[] args)throws Exception{
        check(RandomChoiceModel.items(" 小明，小美\n小刚、呆呆鸟;小明；\n").equals(Arrays.asList("小明","小美","小刚","呆呆鸟")),"Mixed separators, empty items and duplicates");
        check(RandomChoiceModel.items("e\u0301,é").size()==1,"Canonical Unicode equivalents share a single chance");
        rejected(()->RandomChoiceModel.items(String.join("",Collections.nCopies(201,"a"))),"Reject overlong candidate");
        rejected(()->RandomChoiceModel.items("a\tb"),"Reject embedded control characters");
        List<String> excessive=new ArrayList<>();for(int i=0;i<2001;i++)excessive.add("item"+i);rejected(()->RandomChoiceModel.items(String.join("\n",excessive)),"Bound item count");
        // Every possible random branch for a 2-of-4 sample produces one ordered pair.
        Set<String> outcomes=new HashSet<>();for(int first=0;first<4;first++)for(int second=0;second<3;second++){
            final int x=first,y=second;RandomChoiceModel m=new RandomChoiceModel();m.draft="a,b,c,d";
            List<String> result=m.draw(2,false,new Random(){int step;@Override public int nextInt(int bound){int n=step++==0?x:y;check(n<bound,"Branch within RNG bound");return n;}});
            check(result.size()==2&&new HashSet<>(result).size()==2,"No duplicate within a draw");check(outcomes.add(result.toString()),"Each ordered sample occurs once");
        }check(outcomes.size()==12,"All ordered samples reachable without branch bias");
        RandomChoiceModel m=new RandomChoiceModel();m.draft="a,b,c,d";List<String> first=m.draw(2,true,new Random(1)),second=m.draw(2,true,new Random(2));Set<String> all=new HashSet<>(first);all.addAll(second);check(all.size()==4&&m.available(true)==0,"Exclusion exhausts the pool exactly");
        String before=m.json().toString();rejected(()->m.draw(1,true),"Exhausted pool cannot be silently reset");check(m.json().toString().equals(before),"Failed draw does not mutate results or progress");
        rejected(()->m.draw(0,false),"Zero count rejected");rejected(()->m.draw(5,false),"Oversized count rejected");
        check(m.draw(4,false).size()==4,"Disabled exclusion uses full pool");
        m.reset();check(m.available(true)==4&&m.result.isEmpty(),"Reset returns all candidates");m.draw(2,true);m.draft="a,b,c,e";check(m.available(true)==4,"Changed candidates get a new pool");m.draw(4,true);check(m.pool.contains("e")&&!m.used.contains("d"),"New draw discards old pool progress");
        RandomChoiceModel.Saved saved=m.save("Class");m.draft="x,y";check(saved.items.equals(Arrays.asList("a","b","c","e")),"Saved list is a snapshot");m.load(saved);check(m.used.isEmpty()&&m.result.isEmpty()&&m.available(true)==4,"Loading resets progress");
        rejected(()->m.save(" Class "),"Duplicate list name rejected");rejected(()->m.save("  "),"Empty name rejected");m.rename(saved,"Group");check(saved.name.equals("Group")&&m.lists.get(0)==saved,"Rename preserves list order and identity");
        m.quantity="2";m.draw(2,true);RandomChoiceModel restored=RandomChoiceModel.parse(m.json().toString());check(restored.result.equals(m.result)&&restored.used.equals(m.used)&&restored.quantity.equals("2")&&restored.lists.get(0).id.equals(saved.id),"Serialization preserves results, progress and lists");
        m.draft=String.join("",Collections.nCopies(250,"x"));check(RandomChoiceModel.parse(m.json().toString()).lists.size()==1,"Invalid unfinished input cannot destroy saved lists on relaunch");
        JSONObject j=restored.json();invalid(new JSONObject(j.toString()).put("version",1.1),"Reject fractional schema");invalid(new JSONObject(j.toString()).put("draft",1),"Reject non-string draft");invalid(new JSONObject(j.toString()).put("used",new JSONArray("[\"absent\"]")),"Used entries must belong to pool");invalid(new JSONObject(j.toString()).put("pool",new JSONArray("[\"a\",\"a\"]")),"Reject duplicated pool entries");invalid(new JSONObject(j.toString()).put("quantity","-1"),"Reject invalid quantity draft");
        JSONArray lists=j.getJSONArray("lists");lists.put(lists.getJSONObject(0));invalid(j,"Reject duplicate saved identity");
        Map<String,Map<String,?>> source=new HashMap<>();source.put("random_choice",Collections.singletonMap("data",restored.json().toString()));source.put("toolbox",new HashMap<String,Object>(){{put("random_remove_drawn",true);put("random_animation",false);put("tab_show_6",true);}});
        JSONObject config=ConfigurationData.encode(source,null);ConfigurationData.Document doc=ConfigurationData.parse(config.toString());check(doc.randomSpecified&&doc.lists==1&&doc.groups.get("toolbox").get("random_remove_drawn").equals(true),"Configuration includes new group and preferences");
        check(RandomChoiceModel.parse((String)doc.groups.get("random_choice").get("data")).used.equals(restored.used),"Configuration preserves exclusion progress");
        config.remove("random_choice");doc=ConfigurationData.parse(config.toString());check(!doc.randomSpecified&&doc.groups.get("random_choice").isEmpty(),"Legacy configuration marks new data as absent, so it can be retained");
        JSONObject bad=ConfigurationData.encode(source,null);bad.getJSONObject("random_choice").getJSONObject("data").put("value","{}");rejected(()->{try{ConfigurationData.parse(bad.toString());}catch(Exception e){throw new IllegalArgumentException(e);}},"Invalid embedded model rejected before import");
        check(DisplayOrder.parse("5,2,0,1,3,4",7).equals(Arrays.asList(5,2,0,1,3,4,6)),"New tab appended while user order is preserved");
        System.out.println("Random choice tests: "+checks+" checks passed");
    }
}
