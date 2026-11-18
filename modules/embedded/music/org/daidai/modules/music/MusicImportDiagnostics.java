package org.daidai.modules.music;

import android.app.*;
import android.widget.*;
import org.daidai.modules.shared.*;
import org.json.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

/** Latest report stays in this app's local cache; no upload or external path is displayed. */
final class MusicImportDiagnostics {
    private static final String FILE="music-import-report.json";
    private static final int LIMIT=8*1024*1024;
    private MusicImportDiagnostics(){}
    static void open(Activity a,java.util.concurrent.ExecutorService worker,Consumer<MusicImportReport> retry){worker.execute(()->{MusicImportReport report=read(a);new android.os.Handler(android.os.Looper.getMainLooper()).post(()->{if(!a.isDestroyed())show(a,report,retry);});});}
    static void save(Activity a,MusicImportReport report){
        File temp=null;
        try{
            JSONObject object=new JSONObject().put("version",1).put("mode",report.mode.name()).put("cancelled",report.cancelled);
            JSONArray rows=new JSONArray();for(MusicImportReport.Entry e:report.entries())rows.put(new JSONObject().put("name",e.name).put("source",e.source).put("retryUri",e.retryUri).put("phase",e.phase.name()).put("result",e.result.name()).put("reason",e.reason));object.put("entries",rows);
            byte[] data=object.toString().getBytes("UTF-8");if(data.length>LIMIT)return;
            temp=File.createTempFile("music-import-",".tmp",a.getCacheDir());try(OutputStream out=new FileOutputStream(temp)){out.write(data);}
            Files.move(temp.toPath(),new File(a.getCacheDir(),FILE).toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);temp=null;
        }catch(Exception ignored){}finally{if(temp!=null)temp.delete();}
    }
    static MusicImportReport read(Activity a){
        try{
            File file=new File(a.getCacheDir(),FILE);if(!file.isFile()||file.length()>LIMIT)return null;
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(InputStream in=new FileInputStream(file)){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(bytes.size()+n>LIMIT)return null;bytes.write(b,0,n);}}
            JSONObject object=new JSONObject(new String(bytes.toByteArray(),"UTF-8"));if(object.getInt("version")!=1)return null;
            MusicImportReport report=new MusicImportReport(MusicImportReport.Mode.valueOf(object.getString("mode")));report.cancelled=object.optBoolean("cancelled");JSONArray rows=object.getJSONArray("entries");if(rows.length()>50000)return null;
            for(int i=0;i<rows.length();i++){JSONObject e=rows.getJSONObject(i);String reason=e.optString("reason");if(!reason.isEmpty()&&!Arrays.asList("music_import_reason_permission","music_import_reason_missing","music_import_reason_cancelled","music_import_reason_io","music_import_reason_read","music_import_reason_optional","music_import_reason_non_audio").contains(reason))return null;report.add(new MusicImportReport.Entry(e.getString("name"),e.getString("source"),e.getString("retryUri"),MusicImportReport.Phase.valueOf(e.getString("phase")),MusicImportReport.Result.valueOf(e.getString("result")),reason));}return report;
        }catch(Exception ignored){return null;}
    }
    static AlertDialog show(Activity a,MusicImportReport report,Consumer<MusicImportReport> retry){
        if(a.isDestroyed())return null;if(report==null){Ui.note(a,Texts.get(a,"music_import_results"),Texts.get(a,"music_import_no_results"));return null;}
        LinearLayout body=Ui.column(a);body.setPadding(Ui.dp(a,22),Ui.dp(a,12),Ui.dp(a,22),Ui.dp(a,16));
        body.addView(Ui.text(a,Texts.get(a,"music_import_summary",report.count(MusicImportReport.Result.SUCCESS),report.count(MusicImportReport.Result.SKIPPED),report.count(MusicImportReport.Result.FAILED)),16));
        if(report.cancelled)body.addView(Ui.text(a,Texts.get(a,"music_import_cancelled_note"),14));
        if(report.warnings()>0)body.addView(Ui.text(a,Texts.get(a,"music_import_warning_count",report.warnings()),14));
        LinearLayout filters=Ui.row(body);LinearLayout rows=Ui.column(a);body.addView(rows);
        for(MusicImportReport.Result result:MusicImportReport.Result.values())Ui.cell(filters,Texts.get(a,"music_import_result_"+result.name().toLowerCase(Locale.ROOT)),()->draw(a,rows,report,result));
        AlertDialog[] dialog={null};if(retry!=null&&!report.failures().isEmpty())Ui.button(body,Texts.get(a,"music_import_retry_failed"),()->{dialog[0].dismiss();retry.accept(report);});
        body.addView(Ui.text(a,Texts.get(a,"music_import_diagnostic_note"),13));
        ScrollView scroll=new ScrollView(a);scroll.addView(body);dialog[0]=new AlertDialog.Builder(a).setTitle(Texts.get(a,"music_import_results")).setView(scroll).setPositiveButton(Texts.get(a,"music_close"),null).create();dialog[0].show();draw(a,rows,report,report.count(MusicImportReport.Result.FAILED)>0?MusicImportReport.Result.FAILED:MusicImportReport.Result.SUCCESS);return dialog[0];
    }
    private static void draw(Activity a,LinearLayout rows,MusicImportReport report,MusicImportReport.Result result){
        rows.removeAllViews();Object generation=new Object();rows.setTag(generation);ArrayList<MusicImportReport.Entry> visible=new ArrayList<>();for(MusicImportReport.Entry e:report.entries())if(e.result==result)visible.add(e);
        int size=Math.min(1000,visible.size());if(size==0){rows.addView(Ui.text(a,Texts.get(a,"music_import_result_empty"),14));return;}
        MusicRows.append(rows,size,()->!a.isDestroyed()&&rows.getTag()==generation,i->{MusicImportReport.Entry e=visible.get(i);String name=e.name.replaceAll("[\\p{Cntrl}]"," ");if(name.length()>512)name=name.substring(0,512);String text=name+"\n"+Texts.get(a,"music_import_phase_"+e.phase.name().toLowerCase(Locale.ROOT));if(!e.reason.isEmpty())text+=" · "+Texts.get(a,e.reason);TextView line=Ui.text(a,text,14);line.setPadding(0,Ui.dp(a,8),0,Ui.dp(a,8));rows.addView(line);});
        if(visible.size()>size)rows.addView(Ui.text(a,Texts.get(a,"music_import_results_limited",size),13));
    }
}
