package org.simpleweather.app;

import android.app.Activity;
import android.content.*;
import android.net.Uri;
import android.os.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** User-selected files only; no broad storage permission or network transfer. */
final class StatusTransfer {
    static final int EXPORT=60,IMPORT=61;
    private final MainActivity a;private final RouteStore routes;private final StatusPlaces places;
    private final Handler main=new Handler(Looper.getMainLooper());
    private boolean busy,applying;private String kind;private byte[] pending;
    StatusTransfer(MainActivity a,RouteStore routes,StatusPlaces places){this.a=a;this.routes=routes;this.places=places;}
    private boolean alive(){return !a.isFinishing()&&!a.isDestroyed();}
    private String title(){return "routes".equals(kind)?UiText.get("gps_060"):UiText.get("gps_003");}
    private boolean begin(String kind){if(busy){a.toast(UiText.get("status_transfer_001"));return false;}this.kind=kind;busy=true;return true;}
    void exportFile(String kind){
        if(!begin(kind))return;
        new Thread(()->{try{
            JSONArray rows=kind.equals("routes")?routes.exportRows():places.exportRows();
            if(rows.length()==0){main.post(()->fail(UiText.get("status_transfer_002")+title()));return;}
            byte[] bytes=StatusTransferData.encode(kind,rows).getBytes(StandardCharsets.UTF_8);if(bytes.length>StatusTransferData.MAX_BYTES)throw new IOException("size");
            main.post(()->{if(!alive()){busy=false;return;}pending=bytes;Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/json").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,UiText.get("status_transfer_003")+title()+".json");try{a.startActivityForResult(intent,EXPORT);}catch(Exception e){fail(UiText.get("config_picker_failed"));}});
        }catch(Exception e){main.post(()->fail(UiText.get("status_transfer_004")));}},"status-export").start();
    }
    void importFile(String kind){
        if(!begin(kind))return;
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try{a.startActivityForResult(intent,IMPORT);}catch(Exception e){fail(UiText.get("config_picker_failed"));}
    }
    void result(int request,int result,Intent intent){
        if(request!=EXPORT&&request!=IMPORT)return;
        if(result!=Activity.RESULT_OK||intent==null||intent.getData()==null){busy=false;pending=null;return;}
        if(!busy||kind==null){fail(UiText.get("config_interrupted"));return;}
        Uri uri=intent.getData();
        if(request==EXPORT){byte[] bytes=pending;pending=null;if(bytes==null){fail(UiText.get("status_transfer_005"));return;}
            new Thread(()->{try(OutputStream stream=a.getContentResolver().openOutputStream(uri,"wt")){if(stream==null)throw new IOException();stream.write(bytes);stream.flush();main.post(()->fail(title()+UiText.get("status_transfer_006")));}catch(Exception e){main.post(()->fail(UiText.get("status_transfer_007")));}},"status-export").start();
        }else new Thread(()->{try{StatusTransferData.Document document=read(uri,kind);main.post(()->confirm(document));}catch(Exception e){main.post(()->fail(UiText.get("status_transfer_008")));}},"status-import").start();
    }
    private StatusTransferData.Document read(Uri uri,String kind)throws Exception {
        try(InputStream stream=a.getContentResolver().openInputStream(uri);ByteArrayOutputStream out=new ByteArrayOutputStream()){
            if(stream==null)throw new IOException();byte[] buffer=new byte[8192];int n;
            while((n=stream.read(buffer))!=-1){if(out.size()+n>StatusTransferData.MAX_BYTES)throw new IOException("size");out.write(buffer,0,n);}
            return StatusTransferData.parse(new String(out.toByteArray(),StandardCharsets.UTF_8),kind);
        }
    }
    private void confirm(StatusTransferData.Document document){
        if(!alive()){busy=false;return;}
        new ToolDialog(a).setTitle(UiText.get("gps_061")+title()).setMessage(UiText.get("status_transfer_009")+document.rows.length()+UiText.get("earthquake_043")+title()+UiText.get("status_transfer_010"))
            .setNegativeButton(UiText.get("action_cancel"),null).setPositiveButton(UiText.get("gps_061"),(d,n)->apply(document)).setOnDismissListener(d->{if(!applying)busy=false;}).show();
    }
    private void apply(StatusTransferData.Document document){
        busy=applying=true;
        android.app.AlertDialog progress=new ToolDialog(a).setTitle(UiText.get("status_transfer_011")+title()).setMessage(UiText.get("status_transfer_012")).setCancelable(false).show();
        new Thread(()->{try{
            StatusTransferData.Merge merged=StatusTransferData.merge(document.kind.equals("routes")?routes.exportRows():places.exportRows(),document);
            // Keep the stored selection and the currently loaded playback untouched.
            main.post(()->{try{
                boolean saved=document.kind.equals("routes")?routes.importRows(merged.rows):places.importRows(merged.rows);
                if(!saved)throw new IOException();fail(UiText.get("status_transfer_013")+merged.added+UiText.get("earthquake_043")+title()+UiText.get("status_transfer_014")+merged.skipped+UiText.get("status_transfer_015"));
            }catch(Exception e){fail(UiText.get("status_transfer_016"));}finally{applying=false;if(alive())progress.dismiss();}});
        }catch(Exception e){main.post(()->{applying=false;if(alive())progress.dismiss();fail(UiText.get("status_transfer_017"));});}},"status-merge").start();
    }
    private void fail(String message){busy=false;pending=null;if(alive())a.toast(message);}
}
