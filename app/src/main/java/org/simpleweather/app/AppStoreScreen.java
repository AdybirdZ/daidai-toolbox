package org.simpleweather.app;

import android.app.*;
import android.os.Handler;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.InterruptedIOException;
import java.util.*;

/** The same list stays mounted during downloads and install-state refreshes. */
public final class AppStoreScreen {
    private final MainActivity a;
    private final LinearLayout owner;
    private final Handler main=new Handler();
    private LinearLayout list,progressPanel;
    private TextView status,progressText,cancelButton,retryButton;
    private TextView hostUpdateStatus,hostUpdateButton;
    private ProgressBar progressBar;
    private HostUpdateManager.Release hostRelease;
    private HostUpdateManager.DownloadTask hostTask;
    private java.io.File hostApk;
    private final Runnable hostProgressTick=new Runnable(){public void run(){if(!current()||hostTask==null)return;preserveViewport(()->hostUpdateStatus.setText(a.getString(R.string.host_update_downloading,bytes(hostTask.received),bytes(hostTask.total))));main.postDelayed(this,500);}};
    private JSONArray catalog=new JSONArray();
    private static volatile AppModules.DownloadTask downloadTask;
    private static volatile boolean batchRunning;
    private static java.lang.ref.WeakReference<AppStoreScreen> latest=new java.lang.ref.WeakReference<>(null);
    private static int lastSuccess,lastReason;
    private static boolean lastCancelled;
    private static volatile boolean cancelQueue;
    private static List<JSONObject> retryItems=new ArrayList<>();
    private static int completed,queueSize;
    private static String activeId="";
    private ViewportAnchor pendingAnchor;
    private final Runnable progressTick=new Runnable(){public void run(){
        if(!current())return;AppModules.DownloadTask task=downloadTask;
        if(task!=null)preserveViewport(()->{progressText.setText(a.getString(task.verifying?R.string.store_verifying_progress:R.string.store_download_progress,a.apps.title(activeId),completed+1,queueSize,bytes(task.received),bytes(task.total)));progressBar.setIndeterminate(task.total<=0);if(task.total>0)progressBar.setProgress((int)Math.min(1000,task.received*1000/task.total));cancelButton.setEnabled(!task.completed&&!task.cancelled);});if(batchRunning)main.postDelayed(this,100);
    }};

    public AppStoreScreen(MainActivity a){this.a=a;owner=a.body;}
    private boolean current(){return !a.isFinishing()&&!a.isDestroyed()&&a.body==owner;}
    private String bytes(long n){return n>=1024*1024?String.format(Locale.ROOT,"%.2f MiB",n/(1024.0*1024)):String.format(Locale.ROOT,"%.1f KiB",Math.max(0,n)/1024.0);}
    /** Preserve the visible application, not just absolute scroll Y when controls above it grow. */
    private final class ViewportAnchor {
        final ScrollView scroll;final String id;final int top,scrollY;
        ViewportAnchor(ScrollView scroll,String id,int top){this.scroll=scroll;this.id=id;this.top=top;scrollY=scroll.getScrollY();}
        void restore(){if(!current())return;if(id!=null){View card=list.findViewWithTag(id);if(card!=null){int[] now=new int[2];card.getLocationInWindow(now);scroll.scrollTo(scroll.getScrollX(),scroll.getScrollY()+now[1]-top);return;}}scroll.scrollTo(scroll.getScrollX(),scrollY);}
    }
    private ViewportAnchor captureViewport(){
        ViewParent parent=owner.getParent();while(parent instanceof View&&!(parent instanceof ScrollView))parent=parent.getParent();if(!(parent instanceof ScrollView))return null;ScrollView scroll=(ScrollView)parent;
        if(scroll.getScrollY()==0||list==null)return new ViewportAnchor(scroll,null,0);int[] viewport=new int[2];scroll.getLocationInWindow(viewport);
        for(int n=0;n<list.getChildCount();n++){View card=list.getChildAt(n);if(!(card.getTag() instanceof String))continue;int[] position=new int[2];card.getLocationInWindow(position);if(position[1]+card.getHeight()>viewport[1]+scroll.getPaddingTop())return new ViewportAnchor(scroll,(String)card.getTag(),position[1]);}
        return new ViewportAnchor(scroll,null,0);
    }
    private void preserveViewport(Runnable change){
        if(pendingAnchor==null){pendingAnchor=captureViewport();if(pendingAnchor!=null)owner.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener(){public boolean onPreDraw(){if(owner.getViewTreeObserver().isAlive())owner.getViewTreeObserver().removeOnPreDrawListener(this);ViewportAnchor anchor=pendingAnchor;pendingAnchor=null;if(anchor!=null)anchor.restore();return true;}});}
        a.updateReadingValues(change);
    }
    public void render(){
        owner.addView(a.text(a.getString(R.string.launcher_store),24,a.TITLE,true));a.gap(owner,14);
        LinearLayout controls=a.row();controls.addView(a.button(a.getString(R.string.module_catalog_refresh),false,this::refresh),new LinearLayout.LayoutParams(0,-2,1));a.gapHorizontal(controls,8);controls.addView(a.button(a.getString(R.string.module_import_package),false,a.apps::pick),new LinearLayout.LayoutParams(0,-2,1));owner.addView(controls);a.gap(owner,8);
        owner.addView(a.button(a.getString(R.string.store_update_all),false,this::updateAll));a.gap(owner,12);
        LinearLayout hostPanel=a.column();hostPanel.setPadding(a.dp(14),a.dp(10),a.dp(14),a.dp(10));hostPanel.setBackground(a.shape(a.WHITE,18));hostUpdateStatus=a.text(a.getString(R.string.host_update_checking),14,a.INK,false);hostPanel.addView(hostUpdateStatus);hostPanel.addView(a.text(a.getString(R.string.host_update_note),12,a.MUTED,false));hostUpdateButton=a.button(a.getString(R.string.host_update_check),false,()->{if(hostTask!=null)cancelHostDownload();else if(hostRelease!=null)downloadOrInstallHost();else checkHostUpdate();});hostPanel.addView(hostUpdateButton);owner.addView(hostPanel);a.gap(owner,12);
        status=a.text(a.getString(R.string.module_catalog_local),13,a.MUTED,false);owner.addView(status);a.gap(owner,12);
        progressPanel=a.column();progressText=a.text("",13,a.INK,false);progressPanel.addView(progressText);a.gap(progressPanel,8);progressBar=new ProgressBar(a,null,android.R.attr.progressBarStyleHorizontal);progressBar.setMax(1000);progressBar.setProgressTintList(android.content.res.ColorStateList.valueOf(0xff2979ff));progressBar.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(0xff2979ff));progressPanel.addView(progressBar,a.lp(-1,12));a.gap(progressPanel,8);cancelButton=a.button(a.getString(R.string.store_cancel_download),false,()->{cancelQueue=true;AppModules.DownloadTask task=downloadTask;if(task!=null)task.cancel();});progressPanel.addView(cancelButton);progressPanel.setVisibility(View.GONE);owner.addView(progressPanel);
        retryButton=a.button(a.getString(R.string.store_retry_failed),false,()->start(new ArrayList<>(retryItems)));retryButton.setVisibility(View.GONE);owner.addView(retryButton);a.gap(owner,12);
        list=a.column();owner.addView(list);try{draw(a.apps.bundledCatalog());}catch(Exception e){status.setText(a.getString(R.string.module_catalog_failed));}a.gap(owner,16);TextParagraphs.append(a,owner,a.getString(R.string.module_store_note),13,a.MUTED);latest=new java.lang.ref.WeakReference<>(this);if(batchRunning){progressPanel.setVisibility(View.VISIBLE);status.setText(a.getString(R.string.module_downloading));main.post(progressTick);}else{if(!retryItems.isEmpty()){retryButton.setVisibility(View.VISIBLE);showResult();}else refresh();}
    }
    private int state(JSONObject item){String id=item.optString("id");if(!a.apps.compatible(item))return R.string.store_status_host_update;if(a.apps.updateAvailable(item))return R.string.store_status_update;if(a.apps.installed(id))return R.string.store_status_installed;if(ModuleRuntime.packageFile(a,id).exists())return R.string.store_status_app_update;return R.string.store_status_uninstalled;}
    private void draw(JSONArray entries)throws Exception{
        catalog=entries;list.removeAllViews();Set<String> seen=new HashSet<>();
        for(int i=0;i<entries.length();i++){
            JSONObject item=entries.getJSONObject(i);String id=item.getString("id");if(!AppModules.known(id)||!seen.add(id))continue;
            LinearLayout card=a.column();card.setTag(id);card.setPadding(a.dp(16),a.dp(16),a.dp(16),a.dp(16));card.setBackground(a.shape(a.WHITE,22));LinearLayout heading=a.row();ImageView icon=new ImageView(a);icon.setImageResource(AppIcons.resource(id));heading.addView(icon,a.lp(48,48));a.gapHorizontal(heading,12);
            LinearLayout labels=a.column();labels.addView(a.text(a.apps.title(id),18,a.INK,true));a.gap(labels,5);labels.addView(a.text(item.optString("version")+" · "+bytes(item.optLong("bytes")),12,a.MUTED,false));a.gap(labels,4);labels.addView(a.text(a.getString(state(item)),13,a.MUTED,false));heading.addView(labels,new LinearLayout.LayoutParams(0,-2,1));card.addView(heading);a.gap(card,12);
            LinearLayout buttons=a.row();boolean installed=a.apps.installed(id),compatible=a.apps.compatible(item);boolean update=a.apps.updateAvailable(item);int label=installed?R.string.module_open:ModuleRuntime.packageFile(a,id).exists()?R.string.module_update:R.string.module_install;
            TextView install=a.button(a.getString(label),!installed,()->{if(installed){if(Arrays.asList(AppModules.EMBEDDED).contains(id))a.apps.openInternal(id);else a.switchTab(Arrays.asList(AppModules.IDS).indexOf(id));}else confirm(item);});install.setEnabled(installed||compatible);buttons.addView(install,new LinearLayout.LayoutParams(0,-2,1));a.gapHorizontal(buttons,8);buttons.addView(a.button(a.getString(R.string.store_details),false,()->details(item)),new LinearLayout.LayoutParams(0,-2,1));card.addView(buttons);
            if(installed){a.gap(card,8);LinearLayout management=a.row();if(update&&compatible){management.addView(a.button(a.getString(R.string.module_update),true,()->confirm(item)),new LinearLayout.LayoutParams(0,-2,1));a.gapHorizontal(management,8);}management.addView(a.button(a.getString(R.string.module_uninstall),false,()->new LauncherScreen(a).manage(id)),new LinearLayout.LayoutParams(0,-2,1));card.addView(management);}
            list.addView(card);a.gap(list,12);
        }
    }
    public void installedChanged(){if(!current())return;preserveViewport(()->{try{draw(catalog);if(downloadTask==null)status.setText("");}catch(Exception e){status.setText(a.getString(R.string.module_catalog_failed));}});}
    private void refresh(){
        checkHostUpdate();if(a.apps.busy)return;preserveViewport(()->status.setText(a.getString(R.string.module_catalog_loading)));new Thread(()->{try{JSONArray items=a.apps.remoteCatalog();a.runOnUiThread(()->{if(!current())return;preserveViewport(()->{try{draw(items);status.setText(a.getString(R.string.module_catalog_online));}catch(Exception e){status.setText(a.getString(R.string.module_catalog_failed));}});});}catch(Exception e){a.runOnUiThread(()->{if(current())preserveViewport(()->status.setText(a.getString(R.string.module_catalog_failed)));});}},"store-catalog").start();
    }
    private void checkHostUpdate(){
        if(hostUpdateStatus==null||!current()||hostTask!=null)return;hostRelease=null;hostApk=null;preserveViewport(()->{hostUpdateStatus.setText(a.getString(R.string.host_update_checking));hostUpdateButton.setText(a.getString(R.string.host_update_checking));hostUpdateButton.setEnabled(false);});
        new Thread(()->{HostUpdateManager.Release release=null;java.io.File ready=null;Exception error=null;try{release=HostUpdateManager.latest(a);if(release!=null){java.io.File saved=HostUpdateManager.pendingFile(a);if(saved.isFile()){try{HostUpdateManager.verify(a,saved,release.version);ready=saved;}catch(Exception stale){saved.delete();}}}else HostUpdateManager.pendingFile(a).delete();}catch(Exception failure){error=failure;}final HostUpdateManager.Release result=release;final java.io.File staged=ready;final Exception problem=error;a.runOnUiThread(()->{if(!current())return;hostRelease=result;hostApk=staged;preserveViewport(()->{if(problem!=null){hostUpdateStatus.setText(a.getString(R.string.host_update_check_failed));hostUpdateButton.setText(a.getString(R.string.host_update_check));hostUpdateButton.setEnabled(true);}else if(result==null){hostUpdateStatus.setText(a.getString(R.string.host_update_latest));hostUpdateButton.setText(a.getString(R.string.host_update_check));hostUpdateButton.setEnabled(true);}else{hostUpdateStatus.setText(a.getString(R.string.host_update_available,result.version));hostUpdateButton.setText(a.getString(staged==null?R.string.host_update_download:R.string.host_update_install));hostUpdateButton.setEnabled(true);}});});},"host-update-check").start();
    }
    private void downloadOrInstallHost(){
        if(hostRelease==null)return;if(hostApk!=null){installHostUpdate();return;}if(hostTask!=null)return;HostUpdateManager.Release release=hostRelease;HostUpdateManager.DownloadTask task=new HostUpdateManager.DownloadTask();hostTask=task;preserveViewport(()->{hostUpdateStatus.setText(a.getString(R.string.host_update_downloading,"0 KiB",bytes(release.bytes)));hostUpdateButton.setText(a.getString(R.string.host_update_cancel));hostUpdateButton.setEnabled(true);});main.removeCallbacks(hostProgressTick);main.post(hostProgressTick);
        new Thread(()->{java.io.File downloaded=null;Exception error=null;try{downloaded=HostUpdateManager.download(a,release,task);}catch(Exception failure){error=failure;}final java.io.File result=downloaded;final Exception problem=error;a.runOnUiThread(()->{main.removeCallbacks(hostProgressTick);hostTask=null;if(!current())return;preserveViewport(()->{if(problem!=null){hostApk=null;hostUpdateStatus.setText(task.isCancelled()?a.getString(R.string.host_update_cancelled):a.getString(R.string.host_update_download_failed));hostUpdateButton.setText(a.getString(R.string.host_update_download));hostUpdateButton.setEnabled(true);}else{hostApk=result;hostUpdateStatus.setText(a.getString(R.string.host_update_downloaded,release.version));hostUpdateButton.setText(a.getString(R.string.host_update_install));hostUpdateButton.setEnabled(true);}});});},"host-update-download").start();
    }
    private void cancelHostDownload(){HostUpdateManager.DownloadTask task=hostTask;if(task==null)return;task.cancel();preserveViewport(()->{hostUpdateStatus.setText(a.getString(R.string.host_update_cancelling));hostUpdateButton.setEnabled(false);});}
    private void installHostUpdate(){
        try{if(!HostUpdateManager.hasInstallPermission(a)){preserveViewport(()->hostUpdateStatus.setText(a.getString(R.string.host_update_permission_needed)));HostUpdateManager.requestInstallPermission(a);return;}HostUpdateManager.installPending(a);preserveViewport(()->hostUpdateStatus.setText(a.getString(R.string.host_update_confirmation)));}catch(Exception failure){preserveViewport(()->hostUpdateStatus.setText(a.getString(R.string.host_update_install_failed)));}
    }
    private String localized(JSONObject item,String key,String fallback){Object value=item.opt(key);if(value instanceof JSONObject){JSONObject texts=(JSONObject)value;String language=a.getResources().getConfiguration().getLocales().get(0).getLanguage();return texts.optString(language,texts.optString("en",fallback));}return value instanceof String?(String)value:fallback;}
    private void details(JSONObject item){
        String id=item.optString("id");int description=a.getResources().getIdentifier("store_description_"+id.replace('-','_'),"string",a.getPackageName());String summary=description==0?"":a.getString(description);
        LinearLayout content=a.column();content.setPadding(a.dp(18),a.dp(8),a.dp(18),a.dp(16));content.addView(a.text(item.optString("version")+" · "+bytes(item.optLong("bytes")),14,a.INK,false));a.gap(content,10);content.addView(a.text(a.getString(state(item)),14,a.INK,true));a.gap(content,14);TextParagraphs.append(a,content,localized(item,"description",summary),14,a.INK);a.gap(content,14);
        content.addView(a.text(a.getString(R.string.store_release_notes),16,a.TITLE,true));a.gap(content,8);TextParagraphs.append(a,content,localized(item,"releaseNotes",a.getString(R.string.store_release_notes_fallback)),13,a.MUTED);a.gap(content,14);content.addView(a.text(a.getString(R.string.store_permissions),16,a.TITLE,true));a.gap(content,8);content.addView(a.text(permissionText(id,item),13,a.MUTED,false));a.gap(content,14);TextParagraphs.append(a,content,a.getString(R.string.module_trust_note),13,a.MUTED);
        ScrollView scroll=new ScrollView(a);scroll.addView(content);new ToolDialog(a).setTitle(a.apps.title(id)).setView(scroll).setNegativeButton(a.getString(R.string.action_close),null).show();
    }
    private String permissionText(String id,JSONObject item){
        JSONArray permissions=item.optJSONArray("permissions");if(permissions==null)try{if(a.apps.installed(id))permissions=AppModules.inspect(a,ModuleRuntime.packageFile(a,id),id).optJSONArray("permissions");}catch(Exception ignored){}
        if(permissions==null){if(id.equals("weather")||id.equals("weather-map"))return a.getString(R.string.store_permission_internet);if(id.equals("astronomy")||id.equals("random"))return a.getString(R.string.store_permission_none);return a.getString(R.string.store_permission_unspecified);}
        LinkedHashSet<String> names=new LinkedHashSet<>();for(int n=0;n<permissions.length();n++){String permission=permissions.optString(n);int resource=permission.contains("LOCATION")?R.string.store_permission_location:permission.endsWith("CAMERA")?R.string.store_permission_camera:permission.contains("BLUETOOTH")?R.string.store_permission_bluetooth:permission.endsWith("POST_NOTIFICATIONS")?R.string.store_permission_notifications:permission.contains("EXACT_ALARM")?R.string.store_permission_alarms:permission.endsWith("INTERNET")?R.string.store_permission_internet:permission.contains("FOREGROUND_SERVICE")?R.string.store_permission_background:permission.endsWith("WAKE_LOCK")?R.string.store_permission_wake:permission.endsWith("ACCESS_NETWORK_STATE")?R.string.store_permission_network:permission.endsWith("RECEIVE_BOOT_COMPLETED")?R.string.store_permission_boot:R.string.store_permission_other;names.add(a.getString(resource));}return names.isEmpty()?a.getString(R.string.store_permission_none):android.text.TextUtils.join("\n",names);
    }
    private void confirm(JSONObject item){if(!a.apps.compatible(item))return;new ToolDialog(a).setTitle(a.apps.title(item.optString("id"))).setMessage(a.getString(R.string.module_trust_note)).setPositiveButton(a.getString(a.apps.installed(item.optString("id"))?R.string.module_update:R.string.module_install),(d,w)->start(Collections.singletonList(item))).setNegativeButton(a.getString(R.string.action_cancel),null).show();}
    private void updateAll(){
        checkHostUpdate();if(a.apps.busy){a.toast(a.getString(R.string.module_busy));return;}List<JSONObject> items=availableUpdates();long bytes=0;for(JSONObject item:items)bytes+=item.optLong("bytes");
        if(items.isEmpty()){a.toast(a.getString(R.string.store_no_updates));return;}new ToolDialog(a).setTitle(a.getString(R.string.store_update_all)).setMessage(a.getString(R.string.store_batch_confirm,items.size(),bytes(bytes))).setPositiveButton(a.getString(R.string.module_update),(d,w)->start(items)).setNegativeButton(a.getString(R.string.action_cancel),null).show();
    }
    private List<JSONObject> availableUpdates(){List<JSONObject> items=new ArrayList<>();Set<String> seen=new HashSet<>();for(int n=0;n<catalog.length();n++){JSONObject item=catalog.optJSONObject(n);if(item!=null&&seen.add(item.optString("id"))&&a.apps.updateAvailable(item))items.add(item);}return items;}
    private void showResult(){status.setText(lastCancelled?a.getString(R.string.store_batch_cancelled,lastSuccess):retryItems.isEmpty()?a.getString(R.string.store_batch_complete,lastSuccess):a.getString(R.string.store_batch_failed,lastSuccess,retryItems.size(),a.getString(lastReason)));}
    private void start(List<JSONObject> items){
        if(items.isEmpty())return;if(a.apps.busy){a.toast(a.getString(R.string.module_busy));return;}a.apps.busy=true;batchRunning=true;cancelQueue=false;completed=0;queueSize=items.size();retryItems=new ArrayList<>();preserveViewport(()->{retryButton.setVisibility(View.GONE);progressPanel.setVisibility(View.VISIBLE);status.setText(a.getString(R.string.module_downloading));});
        new Thread(()->{
            List<JSONObject> failures=new ArrayList<>();int succeeded=0;int failureText=R.string.store_failure_package;
            for(int index=0;index<items.size();index++){
                JSONObject item=items.get(index);if(cancelQueue){failures.addAll(items.subList(index,items.size()));break;}activeId=item.optString("id");completed=index;AppModules.DownloadTask task=new AppModules.DownloadTask();downloadTask=task;main.removeCallbacks(progressTick);main.post(progressTick);
                try{a.apps.download(item,task);succeeded++;a.runOnUiThread(()->{a.releaseModule(item.optString("id"));if(current())installedChanged();});}catch(Exception e){failures.add(item);failureText=a.apps.failureMessage(e);if(task.cancelled||e instanceof InterruptedIOException&&!(e instanceof java.net.SocketTimeoutException)){cancelQueue=true;if(index+1<items.size())failures.addAll(items.subList(index+1,items.size()));break;}}
            }
            lastSuccess=succeeded;lastReason=failureText;lastCancelled=cancelQueue;retryItems=failures;downloadTask=null;batchRunning=false;a.apps.busy=false;main.removeCallbacks(progressTick);a.runOnUiThread(()->{AppStoreScreen target=latest.get();if(target==null||!target.current())return;target.preserveViewport(()->{target.progressPanel.setVisibility(View.GONE);target.retryButton.setVisibility(failures.isEmpty()?View.GONE:View.VISIBLE);try{target.draw(target.catalog);}catch(Exception ignored){}target.showResult();});});
        },"store-install-queue").start();
    }
}
