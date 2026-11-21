package org.daidai.modules.music;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.widget.*;
import org.daidai.modules.shared.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Explicit export/import through the document picker; the old app needs export only. */
public final class MusicMigrationUi {
    private static final int EXPORT=804,IMPORT=805,FOLDER=806,LINK=807;
    private static final WeakHashMap<Activity,MusicMigrationUi> LIVE=new WeakHashMap<>();
    private final Activity a;private final ExecutorService worker=Executors.newSingleThreadExecutor();private final Handler main=new Handler(Looper.getMainLooper());
    private File exportFile,importFile;private MusicMigration.Prepared prepared;private Runnable changed;private ProgressDialog progress;private boolean closed,busy;
    private MusicMigrationUi(Activity a){this.a=a;}
    private String t(String key,Object...args){return Texts.get(a,"music_migration_"+key,args);}
    public static void open(Activity a,Runnable changed){MusicMigrationUi ui=LIVE.get(a);if(ui==null){ui=new MusicMigrationUi(a);LIVE.put(a,ui);}ui.changed=changed;ui.open();}
    private void open(){LinearLayout box=Ui.column(a);int p=Ui.dp(a,22);box.setPadding(p,Ui.dp(a,10),p,Ui.dp(a,16));Ui.button(box,t("export"),this::exportData);if(!a.getPackageName().equals("org.daidai.prototype.music"))Ui.button(box,t("import"),this::chooseImport);box.addView(Ui.text(a,t("note"),14));new AlertDialog.Builder(a).setTitle(t("title")).setView(box).setNegativeButton(t("close"),null).show();}
    private boolean begin(){if(busy||closed)return false;busy=true;progress=new ProgressDialog(a);progress.setMessage(t("working"));progress.setCancelable(false);progress.show();MusicLibraryScreen.destroy(a);return true;}
    private void finish(){busy=false;if(progress!=null){progress.dismiss();progress=null;}}
    private void error(Exception e){finish();if(!closed&&!a.isDestroyed())Ui.note(a,t("title"),t("failed")+"\n\n"+(e.getMessage()==null?"":e.getMessage()));}
    private final Map<String,String> choices = new HashMap<>();
    private final Set<String> skipped = new HashSet<>();
    private String pendingSong;

    private void exportData() {
        choices.clear();
        skipped.clear();
        continueExport();
    }

    private void resolve(MusicMigration.Unlinked problem) {
        finish();
        if (closed || a.isDestroyed()) return;
        String old = problem.track.uri;
        String message = t(problem.getMessage(), problem.track.title, Uri.decode(old));
        new AlertDialog.Builder(a).setTitle(t("link_title")).setMessage(message)
            .setPositiveButton(t("link"), (dialog, which) -> {
                pendingSong = old;
                try {
                    a.startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT)
                        .addCategory(Intent.CATEGORY_OPENABLE).setType("audio/*"), LINK);
                } catch (Exception e) { error(e); }
            })
            .setNeutralButton(t("skip"), (dialog, which) -> {
                skipped.add(old);
                continueExport();
            })
            .setNegativeButton(t("cancel"), null).show();
    }

    private void continueExport(){if(!begin())return;worker.execute(()->{try{File file=MusicMigration.exportData(a, choices, skipped);main.post(()->{if(closed){file.delete();return;}finish();exportFile=file;try{a.startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/zip").putExtra(Intent.EXTRA_TITLE,"DaidaiMusic-migration.zip"),EXPORT);}catch(Exception e){file.delete();error(e);}});}catch(MusicMigration.Unlinked e){main.post(()->resolve(e));}catch(Exception e){main.post(()->error(e));}});}
    private void chooseImport(){if(busy)return;try{a.startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"),IMPORT);}catch(Exception e){error(e);}}
    public static boolean result(Activity a,int request,int result,Intent data){if(request!=EXPORT&&request!=IMPORT&&request!=FOLDER&&request!=LINK)return false;MusicMigrationUi ui=LIVE.get(a);if(ui!=null)ui.result(request,result,data);return true;}
    private void result(int request,int result,Intent data){if(result!=Activity.RESULT_OK||data==null||data.getData()==null){clean();return;}Uri uri=data.getData();
        if(request==LINK){
            if(pendingSong!=null){choices.put(pendingSong,uri.toString());pendingSong=null;continueExport();}
            return;
        }
        if(request==EXPORT){File file=exportFile;exportFile=null;if(file==null)return;if(!begin()){file.delete();return;}worker.execute(()->{try(InputStream in=new FileInputStream(file);OutputStream out=a.getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new IOException();byte[] bytes=new byte[32768];int n;while((n=in.read(bytes))!=-1)out.write(bytes,0,n);main.post(()->{finish();if(!closed)Ui.note(a,t("title"),t("exported"));});}catch(Exception e){main.post(()->error(e));}finally{file.delete();}});return;}
        if(request==IMPORT){if(!begin())return;worker.execute(()->{try{File file=MusicMigration.copyArchive(a,uri);MusicMigration.inspect(file);main.post(()->{if(closed){file.delete();return;}finish();importFile=file;new AlertDialog.Builder(a).setTitle(t("folder_title")).setMessage(t("folder_note")).setNegativeButton(t("cancel"),(d,w)->clean()).setPositiveButton(t("choose_folder"),(d,w)->{try{a.startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION),FOLDER);}catch(Exception e){clean();error(e);}}).setOnCancelListener(d->clean()).show();});}catch(Exception e){main.post(()->error(e));}});return;}
        if(request==FOLDER&&importFile!=null){try{a.getContentResolver().takePersistableUriPermission(uri,data.getFlags()&(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION));}catch(Exception e){error(e);clean();return;}if(!begin())return;File archive=importFile;worker.execute(()->{try{MusicMigration.Prepared next=MusicMigration.prepare(a,archive,uri.toString());main.post(()->{if(closed){next.close();return;}finish();prepared=next;MusicMigration.Summary summary=next.summary;new AlertDialog.Builder(a).setTitle(t("confirm_title")).setMessage(t("summary",summary.songs,summary.playlists,summary.lyrics,summary.artwork)+"\n\n"+t("replace_note")).setNegativeButton(t("cancel"),(d,w)->clean()).setPositiveButton(t("confirm"),(d,w)->commit()).setOnCancelListener(d->clean()).show();});}catch(Exception e){main.post(()->{error(e);clean();});}});}
    }
    private void commit(){if(prepared==null||!begin())return;MusicMigration.Prepared item=prepared;worker.execute(()->{try{item.commit(a);main.post(()->{finish();clean();if(!closed){if(changed!=null)changed.run();Ui.note(a,t("title"),t("imported"));}});}catch(Exception e){main.post(()->{error(e);clean();});}});}
    private void clean(){if(exportFile!=null)exportFile.delete();exportFile=null;if(importFile!=null)importFile.delete();importFile=null;if(prepared!=null)prepared.close();prepared=null;}
    public static void destroy(Activity a){MusicMigrationUi ui=LIVE.remove(a);if(ui!=null){ui.closed=true;ui.worker.shutdownNow();ui.main.removeCallbacksAndMessages(null);ui.finish();ui.clean();}}
}
