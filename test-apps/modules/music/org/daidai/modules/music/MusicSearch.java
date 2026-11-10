package org.daidai.modules.music;

import android.icu.text.Transliterator;
import java.util.*;
import java.math.BigDecimal;

/** Stable, language-aware catalogue grouping; no user data is modified. */
public final class MusicSearch {
    public enum Category { TITLE, COMPOSER, ARRANGER, VOCALIST, RATING, BPM }
    public static final class Group {
        /** Internal key, such as zh:T, en:A, rating:0 or bpm:100-130. */
        public final String key;
        public final List<MusicLibrary.Track> all;
        public final List<MusicLibrary.Track> matches;
        Group(String key,List<MusicLibrary.Track> all,List<MusicLibrary.Track> matches){this.key=key;this.all=Collections.unmodifiableList(all);this.matches=Collections.unmodifiableList(matches);}
        public boolean highlighted(String query){return query!=null&&!query.trim().isEmpty()&&!matches.isEmpty();}
    }
    private static final Transliterator PINYIN=Transliterator.getInstance("Han-Latin; Latin-ASCII");
    private static final LinkedHashMap<String,String> ROMANIZED=new LinkedHashMap<String,String>(256,.75f,true){@Override protected boolean removeEldestEntry(Map.Entry<String,String> eldest){return size()>2048;}};
    private MusicSearch(){}
    public static boolean matches(MusicLibrary.Track t,String query){String q=query==null?"":query.trim().toLowerCase(Locale.ROOT);return q.isEmpty()||haystack(t).toLowerCase(Locale.ROOT).contains(q);}
    public static List<MusicLibrary.Track> filter(List<MusicLibrary.Track> tracks,String query){ArrayList<MusicLibrary.Track> out=new ArrayList<>();for(MusicLibrary.Track t:tracks)if(matches(t,query))out.add(t);return out;}
    /** Includes every category. Matching categories move first without changing their normal relative order. */
    public static List<Group> groups(List<MusicLibrary.Track> source,String query,Category category){
        LinkedHashMap<String,ArrayList<MusicLibrary.Track>> all=new LinkedHashMap<>();
        for(MusicLibrary.Track t:source)for(String key:keys(t,category)){ArrayList<MusicLibrary.Track> list=all.get(key);if(list==null){list=new ArrayList<>();all.put(key,list);}if(!containsUri(list,t.uri))list.add(t);}
        ArrayList<Group> groups=new ArrayList<>();
        for(Map.Entry<String,ArrayList<MusicLibrary.Track>> e:all.entrySet()){e.getValue().sort((a,b)->compareTrack(a,b,category));ArrayList<MusicLibrary.Track> hits=new ArrayList<>();for(MusicLibrary.Track t:e.getValue())if(matches(t,query))hits.add(t);groups.add(new Group(e.getKey(),e.getValue(),hits));}
        final boolean searching=query!=null&&!query.trim().isEmpty();groups.sort((a,b)->{if(searching&&a.matches.isEmpty()!=b.matches.isEmpty())return a.matches.isEmpty()?1:-1;return compareKey(a.key,b.key);});return groups;
    }
    /** Compatibility view: matching rows only, while all category keys remain present. */
    public static LinkedHashMap<String,ArrayList<MusicLibrary.Track>> grouped(List<MusicLibrary.Track> source,String query,Category category){LinkedHashMap<String,ArrayList<MusicLibrary.Track>> out=new LinkedHashMap<>();for(Group g:groups(source,query,category))out.put(g.key,new ArrayList<>(g.matches));return out;}
    public static String key(MusicLibrary.Track t,Category category){return keys(t,category).get(0);}
    public static List<String> keys(MusicLibrary.Track t,Category category){
        if(category==Category.RATING)return Collections.singletonList(t.rating<0?"missing":"rating:"+t.rating);
        if(category==Category.BPM){if(t.bpmText.isEmpty())return Collections.singletonList("missing");BigDecimal bpm=new BigDecimal(t.bpmText);return Collections.singletonList(bpm.compareTo(BigDecimal.valueOf(100))<0?"bpm:under100":bpm.compareTo(BigDecimal.valueOf(130))<=0?"bpm:100-130":bpm.compareTo(BigDecimal.valueOf(160))<=0?"bpm:130-160":"bpm:over160");}
        String v=category==Category.TITLE?t.title:category==Category.COMPOSER?t.composer:category==Category.ARRANGER?t.arranger:t.vocalist;if(v.trim().isEmpty())return Collections.singletonList("missing");LinkedHashSet<String> keys=new LinkedHashSet<>();
        List<String> names=category==Category.TITLE?Collections.singletonList(v):MusicPeople.split(v);for(String name:names)if(!name.trim().isEmpty()){String language=language(name);keys.add(language+":"+(language.equals("other")?nativeInitial(name):initial(name)));}if(keys.isEmpty())keys.add("missing");return new ArrayList<>(keys);
    }
    public static String value(MusicLibrary.Track t,Category category){switch(category){case COMPOSER:return MusicPeople.display(t.composer,"; ");case ARRANGER:return MusicPeople.display(t.arranger,"; ");case VOCALIST:return MusicPeople.display(t.vocalist,"; ");case RATING:return Integer.toString(t.rating);case BPM:return t.bpmText;default:return safe(t.title);}}
    public static String language(String text){for(int offset=0;offset<text.length();){int cp=text.codePointAt(offset);offset+=Character.charCount(cp);if(Character.isWhitespace(cp)||!Character.isLetterOrDigit(cp))continue;Character.UnicodeScript script=Character.UnicodeScript.of(cp);if(script==Character.UnicodeScript.HAN)return "zh";if(cp>='A'&&cp<='Z'||cp>='a'&&cp<='z')return "en";return "other";}return "other";}
    public static String initial(String value){String s=romanized(value);for(int i=0;i<s.length();i++){char c=Character.toUpperCase(s.charAt(i));if(c>='A'&&c<='Z')return Character.toString(c);if(Character.isLetterOrDigit(c))return "#";}return "#";}
    private static String nativeInitial(String value){for(int offset=0;offset<value.length();){int cp=value.codePointAt(offset);offset+=Character.charCount(cp);if(Character.isLetter(cp))return new String(Character.toChars(Character.toUpperCase(cp)));if(Character.isDigit(cp))return "#";}return "#";}
    public static int compareKey(String a,String b){int x=rank(a),y=rank(b);return x!=y?Integer.compare(x,y):a.compareToIgnoreCase(b);}
    private static int rank(String key){if(key.startsWith("zh:"))return 0;if(key.startsWith("en:"))return 1;if(key.startsWith("other:"))return 2;if(key.startsWith("rating:")){try{return 10+Integer.parseInt(key.substring(7));}catch(Exception ignored){return 10;}}if(key.equals("bpm:under100"))return 30;if(key.equals("bpm:100-130"))return 31;if(key.equals("bpm:130-160"))return 32;if(key.equals("bpm:over160"))return 33;return 100;}
    private static int compareTrack(MusicLibrary.Track a,MusicLibrary.Track b,Category category){int byName=romanized(value(a,category)).compareToIgnoreCase(romanized(value(b,category)));return byName!=0?byName:romanized(a.title).compareToIgnoreCase(romanized(b.title));}
    private static synchronized String romanized(String s){String raw=safe(s).trim(),cached=ROMANIZED.get(raw);if(cached!=null)return cached;String value;try{value=PINYIN.transliterate(raw);}catch(Exception e){value=raw;}ROMANIZED.put(raw,value);return value;}
    private static String safe(String s){return s==null?"":s;}
    private static String haystack(MusicLibrary.Track t){long seconds=Math.max(0,t.duration/1000);String time=String.format(Locale.ROOT,"%d:%02d",seconds/60,seconds%60);return t.title+" "+MusicPeople.display(t.composer," ")+" "+MusicPeople.display(t.arranger," ")+" "+MusicPeople.display(t.vocalist," ")+" "+t.notes+" "+MusicTags.display(t.tags," ")+" "+t.bpmText+" "+(t.rating<0?"":t.rating)+" "+t.duration+" "+time;}
    private static boolean containsUri(List<MusicLibrary.Track> tracks,String uri){for(MusicLibrary.Track t:tracks)if(t.uri.equals(uri))return true;return false;}
}
