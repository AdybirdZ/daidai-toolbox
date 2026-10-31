package org.simpleweather.app;

import org.json.*;
import java.text.Normalizer;
import java.security.SecureRandom;
import java.util.*;

/** Local-only draw state. Each ordered sample is equally likely (partial Fisher–Yates). */
final class RandomChoiceModel {
    static final int MAX_INPUT=262144,MAX_ITEMS=2000,MAX_ITEM=200,MAX_LISTS=100,MAX_BYTES=8*1024*1024;
    String draft="",quantity="1";
    final List<String> pool=new ArrayList<>(),result=new ArrayList<>();
    final Set<String> used=new LinkedHashSet<>();
    final List<Saved> lists=new ArrayList<>();
    static final class Saved {
        final String id;String name;final List<String> items;
        Saved(String id,String name,List<String> items){this.id=id;this.name=name;this.items=new ArrayList<>(items);}
    }
    static List<String> items(String input){
        if(input==null||input.length()>MAX_INPUT)throw new IllegalArgumentException("input");
        LinkedHashSet<String> unique=new LinkedHashSet<>();
        for(String raw:input.split("[\\r\\n,，、;；]+")){
            String item=Normalizer.normalize(raw.trim(),Normalizer.Form.NFC);
            if(item.isEmpty())continue;
            if(item.length()>MAX_ITEM||item.matches("(?s).*[\\p{Cntrl}].*"))throw new IllegalArgumentException("item");
            unique.add(item);if(unique.size()>MAX_ITEMS)throw new IllegalArgumentException("items");
        }
        return new ArrayList<>(unique);
    }
    int available(boolean exclude){List<String> current=items(draft);if(!current.equals(pool)||!exclude)return current.size();return pool.size()-used.size();}
    List<String> draw(int count,boolean exclude,Random random){
        List<String> current=items(draft),eligible=new ArrayList<>(current);
        boolean same=current.equals(pool);if(exclude&&same)eligible.removeAll(used);
        if(count<1||count>eligible.size())throw new IllegalArgumentException("count");
        // Do not mutate progress on a failed draw.
        for(int i=0;i<count;i++){int j=i+random.nextInt(eligible.size()-i);Collections.swap(eligible,i,j);}
        if(!same){pool.clear();pool.addAll(current);used.clear();}
        result.clear();result.addAll(eligible.subList(0,count));used.addAll(result);
        return new ArrayList<>(result);
    }
    List<String> draw(int count,boolean exclude){return draw(count,exclude,new SecureRandom());}
    void reset(){pool.clear();pool.addAll(items(draft));used.clear();result.clear();}
    Saved save(String name){name=checkedName(name);List<String> entries=items(draft);if(entries.isEmpty()||lists.size()>=MAX_LISTS)throw new IllegalArgumentException("list");
        for(Saved list:lists)if(list.name.equals(name))throw new IllegalArgumentException("name");
        int total=0;for(Saved old:lists)for(String item:old.items)total+=item.length();for(String item:entries)total+=item.length();if(total>400000)throw new IllegalArgumentException("storage");
        Saved saved=new Saved(UUID.randomUUID().toString(),name,entries);lists.add(saved);return saved;
    }
    void rename(Saved list,String name){name=checkedName(name);for(Saved other:lists)if(other!=list&&other.name.equals(name))throw new IllegalArgumentException("name");list.name=name;}
    void load(Saved list){draft=String.join("\n",list.items);reset();}
    private static String checkedName(String name){if(name==null)throw new IllegalArgumentException("name");name=name.trim();if(name.isEmpty()||name.length()>80||name.matches("(?s).*[\\p{Cntrl}].*"))throw new IllegalArgumentException("name");return name;}
    JSONObject json()throws JSONException{
        JSONArray saved=new JSONArray();for(Saved list:lists)saved.put(new JSONObject().put("id",list.id).put("name",list.name).put("items",new JSONArray(list.items)));
        return new JSONObject().put("version",1).put("draft",draft).put("quantity",quantity).put("pool",new JSONArray(pool)).put("used",new JSONArray(used)).put("result",new JSONArray(result)).put("lists",saved);
    }
    static RandomChoiceModel parse(String text)throws JSONException{
        try{
            if(text==null||text.length()>MAX_BYTES)throw new IllegalArgumentException();
            boolean quoted=false,escaped=false;int depth=0;for(int i=0;i<text.length();i++){char c=text.charAt(i);if(quoted){if(escaped)escaped=false;else if(c=='\\')escaped=true;else if(c=='"')quoted=false;}else if(c=='"')quoted=true;else if(c=='{'||c=='['){if(++depth>8)throw new IllegalArgumentException();}else if(c=='}'||c==']'){if(--depth<0)throw new IllegalArgumentException();}}if(depth!=0||quoted)throw new IllegalArgumentException();
            JSONTokener tokener=new JSONTokener(text);Object value=tokener.nextValue();if(!(value instanceof JSONObject)||tokener.nextClean()!=0)throw new IllegalArgumentException();
            JSONObject j=(JSONObject)value;if(!(j.get("version") instanceof Number)||j.getDouble("version")!=1)throw new IllegalArgumentException();
            RandomChoiceModel m=new RandomChoiceModel();m.draft=string(j,"draft");if(m.draft.length()>MAX_INPUT)throw new IllegalArgumentException();m.quantity=string(j,"quantity");if(!m.quantity.matches("[0-9]{0,6}"))throw new IllegalArgumentException();
            m.pool.addAll(entries(j.getJSONArray("pool")));m.used.addAll(entries(j.getJSONArray("used")));m.result.addAll(entries(j.getJSONArray("result")));
            if(!m.pool.containsAll(m.used)||!m.used.containsAll(m.result))throw new IllegalArgumentException();
            JSONArray lists=j.getJSONArray("lists");if(lists.length()>MAX_LISTS)throw new IllegalArgumentException();Set<String> ids=new HashSet<>(),names=new HashSet<>();int total=0;
            for(int i=0;i<lists.length();i++){JSONObject row=lists.getJSONObject(i);String id=string(row,"id"),name=checkedName(string(row,"name"));if(id.length()>80||id.isEmpty()||!ids.add(id)||!names.add(name))throw new IllegalArgumentException();List<String> entries=entries(row.getJSONArray("items"));if(entries.isEmpty())throw new IllegalArgumentException();for(String item:entries)total+=item.length();if(total>400000)throw new IllegalArgumentException();m.lists.add(new Saved(id,name,entries));}
            return m;
        }catch(Exception e){throw new JSONException("Invalid random choice data");}
    }
    private static String string(JSONObject j,String key)throws JSONException{Object v=j.get(key);if(!(v instanceof String))throw new JSONException("Expected string");return (String)v;}
    private static List<String> entries(JSONArray array)throws JSONException{
        if(array.length()>MAX_ITEMS)throw new JSONException("Too many items");List<String> out=new ArrayList<>();Set<String> unique=new HashSet<>();
        for(int i=0;i<array.length();i++){Object v=array.get(i);if(!(v instanceof String))throw new JSONException("Expected string");String s=(String)v;List<String> parsed=items(s);if(parsed.size()!=1||!parsed.get(0).equals(s)||!unique.add(s))throw new JSONException("Invalid item");out.add(s);}return out;
    }
}
