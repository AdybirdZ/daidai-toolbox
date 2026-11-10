package org.simpleweather.app;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collections;

/** User artwork is local data; the launcher retains control of pinned shortcuts. */
final class AppIconStore {
    static final String FILE_NAME="launcher-icon.png";
    static final String SHORTCUT_ID="custom-launcher-icon";
    static final int SIZE=512;
    enum Applied { UPDATED, REQUESTED, UNSUPPORTED, FAILED }

    static File file(Context context){return new File(context.getFilesDir(),FILE_NAME);}
    static boolean custom(Context context){return file(context).isFile();}
    static byte[] read(Context context)throws IOException {
        File image=file(context);if(!image.exists())return null;
        if(image.length()>ConfigurationData.MAX_APP_ICON)throw new IOException();
        return Files.readAllBytes(image.toPath());
    }
    static Bitmap image(Context context){
        Bitmap bitmap=null;
        try{byte[] bytes=read(context);if(bytes!=null){validate(bytes);bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length);}}catch(Exception|OutOfMemoryError ignored){}
        return bitmap==null?defaultImage(context):bitmap;
    }
    static Bitmap defaultImage(Context context){
        Bitmap bitmap=Bitmap.createBitmap(SIZE,SIZE,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(bitmap);Drawable icon=context.getApplicationInfo().loadIcon(context.getPackageManager());
        icon.setBounds(0,0,SIZE,SIZE);icon.draw(canvas);return bitmap;
    }
    static void validate(byte[] bytes)throws IOException {
        if(bytes==null)return;
        if(bytes.length==0||bytes.length>ConfigurationData.MAX_APP_ICON)throw new IOException();
        BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
        BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);
        if(bounds.outWidth<48||bounds.outWidth>SIZE||bounds.outWidth!=bounds.outHeight)throw new IOException();
        Bitmap bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length);
        if(bitmap==null)throw new IOException();bitmap.recycle();
    }
    static void save(Context context,Bitmap image)throws IOException {
        File temporary=new File(context.getFilesDir(),"launcher-icon.tmp");
        try{
            try(FileOutputStream out=new FileOutputStream(temporary)){
                if(!image.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException();
            }
            if(temporary.length()>ConfigurationData.MAX_APP_ICON)throw new IOException();
            Files.move(temporary.toPath(),file(context).toPath(),StandardCopyOption.REPLACE_EXISTING);
        }finally{temporary.delete();}
    }
    static boolean supported(Context context){
        try{ShortcutManager manager=context.getSystemService(ShortcutManager.class);return manager!=null&&manager.isRequestPinShortcutSupported();}catch(RuntimeException e){return false;}
    }
    static boolean pinned(Context context){
        try{ShortcutManager manager=context.getSystemService(ShortcutManager.class);if(manager!=null)for(ShortcutInfo shortcut:manager.getPinnedShortcuts())if(SHORTCUT_ID.equals(shortcut.getId()))return true;}catch(RuntimeException ignored){}
        return false;
    }
    private static ShortcutInfo shortcut(Context context,Bitmap image){
        Intent launch=new Intent(context,MainActivity.class).setAction(Intent.ACTION_MAIN)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return new ShortcutInfo.Builder(context,SHORTCUT_ID).setShortLabel(context.getString(R.string.app_name))
            .setLongLabel(context.getString(R.string.app_name)).setIcon(Icon.createWithAdaptiveBitmap(image)).setIntent(launch).build();
    }
    static Applied apply(Context context){
        Bitmap image=null;
        try{
            ShortcutManager manager=context.getSystemService(ShortcutManager.class);
            if(manager==null)return Applied.UNSUPPORTED;
            image=image(context);ShortcutInfo shortcut=shortcut(context,image);
            if(pinned(context))return manager.updateShortcuts(Collections.singletonList(shortcut))?Applied.UPDATED:Applied.FAILED;
            if(!manager.isRequestPinShortcutSupported())return Applied.UNSUPPORTED;
            return manager.requestPinShortcut(shortcut,null)?Applied.REQUESTED:Applied.FAILED;
        }catch(RuntimeException|OutOfMemoryError e){return Applied.FAILED;}
        finally{if(image!=null)image.recycle();}
    }
    /** Reset never creates a shortcut. Existing pinned artwork is updated when possible. */
    static boolean reset(Context context){
        try{Files.deleteIfExists(file(context).toPath());}catch(IOException e){return false;}
        return !pinned(context)||apply(context)==Applied.UPDATED;
    }
}
