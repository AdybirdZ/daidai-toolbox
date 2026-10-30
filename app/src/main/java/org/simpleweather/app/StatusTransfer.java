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
    private String title(){return "routes".equals(kind)?"路线":"地点";}
    private boolean begin(String kind){if(busy){a.toast("请先完成当前导入或导出");return false;}this.kind=kind;busy=true;return true;}
    void exportFile(String kind){
        if(!begin(kind))return;
        new Thread(()->{try{
            JSONArray rows=kind.equals("routes")?routes.exportRows():places.exportRows();
            if(rows.length()==0){main.post(()->fail("暂无可导出的"+title()));return;}
            byte[] bytes=StatusTransferData.encode(kind,rows).getBytes(StandardCharsets.UTF_8);if(bytes.length>StatusTransferData.MAX_BYTES)throw new IOException("size");
            main.post(()->{if(!alive()){busy=false;return;}pending=bytes;Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/json").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"呆呆工具箱-"+title()+".json");try{a.startActivityForResult(intent,EXPORT);}catch(Exception e){fail("无法打开系统文件选择器");}});
        }catch(Exception e){main.post(()->fail("无法导出，请检查数据或文件大小（上限 20 MB）"));}},"status-export").start();
    }
    void importFile(String kind){
        if(!begin(kind))return;
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try{a.startActivityForResult(intent,IMPORT);}catch(Exception e){fail("无法打开系统文件选择器");}
    }
    void result(int request,int result,Intent intent){
        if(request!=EXPORT&&request!=IMPORT)return;
        if(result!=Activity.RESULT_OK||intent==null||intent.getData()==null){busy=false;pending=null;return;}
        if(!busy||kind==null){fail("操作已中断，请重新选择文件");return;}
        Uri uri=intent.getData();
        if(request==EXPORT){byte[] bytes=pending;pending=null;if(bytes==null){fail("导出已中断，请重试");return;}
            new Thread(()->{try(OutputStream stream=a.getContentResolver().openOutputStream(uri,"wt")){if(stream==null)throw new IOException();stream.write(bytes);stream.flush();main.post(()->fail(title()+"已导出"));}catch(Exception e){main.post(()->fail("导出失败，请检查目标文件是否可写"));}},"status-export").start();
        }else new Thread(()->{try{StatusTransferData.Document document=read(uri,kind);main.post(()->confirm(document));}catch(Exception e){main.post(()->fail("无法导入：文件格式、版本或记录无效，或超过 20 MB；原有数据未修改"));}},"status-import").start();
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
        new ToolDialog(a).setTitle("导入"+title()).setMessage("文件包含 "+document.rows.length()+" 条"+title()+"。导入将保留现有数据，跳过内容相同的记录；标识相同但内容不同的记录将另存，不覆盖原记录。")
            .setNegativeButton("取消",null).setPositiveButton("导入",(d,n)->apply(document)).setOnDismissListener(d->{if(!applying)busy=false;}).show();
    }
    private void apply(StatusTransferData.Document document){
        busy=applying=true;
        android.app.AlertDialog progress=new ToolDialog(a).setTitle("正在导入"+title()).setMessage("请稍候…").setCancelable(false).show();
        new Thread(()->{try{
            StatusTransferData.Merge merged=StatusTransferData.merge(document.kind.equals("routes")?routes.exportRows():places.exportRows(),document);
            // Keep the stored selection and the currently loaded playback untouched.
            main.post(()->{try{
                boolean saved=document.kind.equals("routes")?routes.importRows(merged.rows):places.importRows(merged.rows);
                if(!saved)throw new IOException();fail("已导入 "+merged.added+" 条"+title()+"，跳过 "+merged.skipped+" 条重复记录");
            }catch(Exception e){fail("导入失败，请检查设备存储空间");}finally{applying=false;if(alive())progress.dismiss();}});
        }catch(Exception e){main.post(()->{applying=false;if(alive())progress.dismiss();fail("导入失败，原有数据未修改");});}},"status-merge").start();
    }
    private void fail(String message){busy=false;pending=null;if(alive())a.toast(message);}
}
