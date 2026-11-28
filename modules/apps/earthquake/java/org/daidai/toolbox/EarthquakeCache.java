package org.daidai.toolbox;

import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.util.*;

/** Isolated cache namespace and a generation guard against in-flight resurrection. */
public final class EarthquakeCache {
    public static final class Entry {public String raw;public long fetched,since;public double minimum;public List<EarthquakeData.Report> reports;}
    private final File dir;
    private static final Map<String,Guard> GUARDS=new HashMap<>();
    private static final class Guard {public int generation;}
    private final Guard guard;
    public EarthquakeCache(File files){dir=new File(files,"earthquake_cache");synchronized(GUARDS){guard=GUARDS.computeIfAbsent(dir.getAbsolutePath(),key->new Guard());}}
    public int generation(){synchronized(guard){return guard.generation;}}
    public Entry read(String source){
        try{File file=new File(dir,source+".json");if(file.length()>16*1024*1024)return null;JSONObject j=new JSONObject(new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8));Entry e=new Entry();e.raw=j.getString("raw");e.fetched=j.getLong("fetched");e.since=j.getLong("since");e.minimum=j.getDouble("minimum");e.reports=EarthquakeApi.parse(source,e.raw);return e;}catch(Exception ex){return null;}
    }
    public boolean save(String source,Entry e,int expected){synchronized(guard){
        if(expected!=guard.generation)return false;
        try{if(!dir.isDirectory()&&!dir.mkdirs())return false;File tmp=new File(dir,source+".json.tmp"),target=new File(dir,source+".json");JSONObject j=new JSONObject().put("raw",e.raw).put("fetched",e.fetched).put("since",e.since).put("minimum",e.minimum);try(FileOutputStream out=new FileOutputStream(tmp)){out.write(j.toString().getBytes(StandardCharsets.UTF_8));}try{Files.move(tmp.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException ex){Files.move(tmp.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING);}return true;}catch(Exception ex){return false;}
    }}
    public boolean clear(){synchronized(guard){guard.generation++;boolean ok=true;for(String source:EarthquakeData.SOURCES)for(String suffix:new String[]{".json",".json.tmp"}){File f=new File(dir,source+suffix);if(f.exists()&&!f.delete())ok=false;}return ok;}}
}
