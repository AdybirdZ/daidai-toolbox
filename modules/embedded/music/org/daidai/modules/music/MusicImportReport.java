package org.daidai.modules.music;

import java.io.*;
import java.util.*;

/** Local operation results. Display text is resolved by the UI, never from raw provider errors. */
final class MusicImportReport {
    enum Mode {COPY,FOLDER}
    enum Phase {SCAN,COPY,METADATA,ARTWORK,SAVE}
    enum Result {SUCCESS,SKIPPED,FAILED}
    static final class Entry {
        final String name,source,retryUri,reason;
        final Phase phase;
        final Result result;
        Entry(String name,String source,String retryUri,Phase phase,Result result,String reason){
            this.name=name==null?"":name;this.source=source==null?"":source;this.retryUri=retryUri==null?this.source:retryUri;this.phase=phase;this.result=result;this.reason=reason==null?"":reason;
        }
    }
    final Mode mode;
    final long started=System.currentTimeMillis();
    private final ArrayList<Entry> entries=new ArrayList<>();
    volatile boolean cancelled;
    MusicImportReport(Mode mode){this.mode=mode;}
    synchronized void add(Entry entry){entries.add(entry);}
    synchronized List<Entry> entries(){return new ArrayList<>(entries);}
    synchronized int count(Result result){int count=0;for(Entry entry:entries)if(entry.result==result)count++;return count;}
    synchronized int warnings(){int count=0;for(Entry entry:entries)if(entry.result==Result.SUCCESS&&!entry.reason.isEmpty())count++;return count;}
    synchronized List<Entry> failures(){ArrayList<Entry> out=new ArrayList<>();HashSet<String> seen=new HashSet<>();for(Entry entry:entries)if(entry.result==Result.FAILED&&!entry.retryUri.isEmpty()&&seen.add(entry.retryUri))out.add(entry);return out;}
    static String reason(Throwable error){
        Set<Throwable> seen=Collections.newSetFromMap(new IdentityHashMap<Throwable,Boolean>());
        for(Throwable cause=error;cause!=null&&seen.add(cause);cause=cause.getCause()){
            if(cause instanceof SecurityException)return "music_import_reason_permission";
            if(cause instanceof FileNotFoundException)return "music_import_reason_missing";
            if(cause instanceof InterruptedIOException||cause instanceof InterruptedException)return "music_import_reason_cancelled";
        }
        return error instanceof IOException?"music_import_reason_io":"music_import_reason_read";
    }
}
