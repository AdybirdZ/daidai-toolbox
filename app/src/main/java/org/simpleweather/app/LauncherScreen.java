package org.simpleweather.app;
import android.app.*;
import android.content.*;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Home is always available, including when every optional app has been removed. */
public final class LauncherScreen {
    private final MainActivity a;
    public LauncherScreen(MainActivity a){this.a=a;}
    public List<String> ordered(){List<String> all=new ArrayList<>();for(int n:a.visibleTabs())all.add(AppModules.IDS[n]);for(String id:AppModules.EMBEDDED)if(a.apps.installed(id))all.add(id);List<String> out=new ArrayList<>();for(String id:a.preferences.getString("launcher_order","").split(","))if(all.remove(id))out.add(id);out.addAll(all);return out;}
    public void render(){
        a.gap(a.body,14);List<String> ids=ordered();ids.add("store");ids.add("settings");
        int count=a.getResources().getConfiguration().screenWidthDp>=600?5:3;
        for(int start=0;start<ids.size();start+=count){LinearLayout row=a.row();row.setGravity(Gravity.TOP);for(int col=0;col<count;col++){if(col>0)a.gapHorizontal(row,10);if(start+col>=ids.size()){row.addView(new View(a),new LinearLayout.LayoutParams(0,1,1));continue;}String id=ids.get(start+col);LinearLayout tile=a.column();tile.setGravity(Gravity.CENTER);tile.setPadding(a.dp(4),a.dp(12),a.dp(4),a.dp(12));ImageView icon=new ImageView(a);icon.setImageResource(AppIcons.resource(id));icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);tile.addView(icon,a.lp(72,72));a.gap(tile,10);TextView name=a.text(a.apps.title(id),14,a.INK,true);name.setGravity(Gravity.CENTER);name.setMinHeight(a.dp(38));tile.addView(name,a.lp(-1,-2));a.tap(tile,android.graphics.Color.TRANSPARENT,20,()->open(id));tile.setContentDescription(a.apps.title(id));if(!id.equals("settings")&&!id.equals("store"))tile.setOnLongClickListener(v->{manage(id);return true;});row.addView(tile,new LinearLayout.LayoutParams(0,-2,1));}a.body.addView(row);a.gap(a.body,16);}
    }
    private void open(String id){if(id.equals("settings"))a.settings();else if(id.equals("store"))a.switchTab(-2);else if(Arrays.asList(AppModules.EMBEDDED).contains(id))a.apps.openInternal(id);else a.switchTab(Arrays.asList(AppModules.IDS).indexOf(id));}
    public void manage(String id){new ToolDialog(a).setTitle(a.apps.title(id)).setMessage(a.getString(R.string.module_remove_note)).setPositiveButton(a.getString(R.string.module_uninstall),(d,w)->{if(!a.apps.busy)a.apps.uninstall(id);}).setNegativeButton(a.getString(R.string.action_cancel),null).show();}
    public void order(){LinearLayout c=a.column();c.setPadding(a.dp(18),a.dp(8),a.dp(18),a.dp(16));List<String> ids=ordered();Runnable[] draw=new Runnable[1];draw[0]=()->{c.removeAllViews();for(int i=0;i<ids.size();i++){final int n=i;String id=ids.get(i);LinearLayout row=a.row();row.addView(a.text(a.apps.title(id),15,a.INK,false),new LinearLayout.LayoutParams(0,-2,1));a.gapHorizontal(row,8);TextView up=a.button("↑",false,()->{if(n>0){Collections.swap(ids,n,n-1);save(ids);draw[0].run();}});up.setEnabled(n>0);row.addView(up,a.lp(48,48));a.gapHorizontal(row,8);TextView down=a.button("↓",false,()->{if(n+1<ids.size()){Collections.swap(ids,n,n+1);save(ids);draw[0].run();}});down.setEnabled(n+1<ids.size());row.addView(down,a.lp(48,48));c.addView(row);a.gap(c,10);}a.gap(c,10);c.addView(a.text(a.getString(R.string.launcher_order_note),13,a.MUTED,false));};draw[0].run();ScrollView scroll=new ScrollView(a);scroll.addView(c);new ToolDialog(a).setTitle(a.getString(R.string.launcher_order)).setView(scroll).setNegativeButton(a.getString(R.string.action_back),null).show();}
    private void save(List<String> ids){a.preferences.edit().putString("launcher_order",android.text.TextUtils.join(",",ids)).apply();a.refreshScreen();}
}
