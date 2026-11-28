package org.daidai.toolbox;

import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.Uri;
import java.util.*;

/** Pinning asks the system launcher; submitting a request does not mean the user accepted it. */
public final class LauncherShortcuts {
    public static final String EXTRA="open_app_id";
    private LauncherShortcuts(){}
    public static Intent intent(Context context,String id){return new Intent(Intent.ACTION_VIEW,Uri.parse("daidai-toolbox://application/"+id),context,MainActivity.class).putExtra(EXTRA,id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);}
    public static void pin(MainActivity a,String id){
        if(!LauncherLayout.application(id)||!a.apps.installed(id))return;
        ShortcutManager manager=a.getSystemService(ShortcutManager.class);
        if(manager==null||!manager.isRequestPinShortcutSupported()){a.toast(a.getString(R.string.launcher_shortcut_unsupported));return;}
        try{
            Drawable drawable=a.getDrawable(AppIcons.resource(id));Bitmap bitmap=Bitmap.createBitmap(192,192,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);drawable.setBounds(0,0,192,192);drawable.draw(canvas);
            ShortcutInfo shortcut=new ShortcutInfo.Builder(a,"internal-app-"+id).setShortLabel(a.apps.title(id)).setLongLabel(a.apps.title(id)).setIcon(Icon.createWithBitmap(bitmap)).setIntent(intent(a,id)).build();
            manager.updateShortcuts(Collections.singletonList(shortcut));
            boolean requested=manager.requestPinShortcut(shortcut,null);
            a.toast(a.getString(requested?R.string.launcher_shortcut_requested:R.string.launcher_shortcut_failed));
        }catch(Exception failure){a.toast(a.getString(R.string.launcher_shortcut_failed));}
    }
}
