package org.simpleweather.app;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.*;
import android.widget.*;
import java.util.*;
import org.json.*;

/** Home stays usable with zero optional apps; layout identities survive uninstall and reinstall. */
public final class LauncherScreen {
    private final MainActivity a;
    private final boolean tablet;
    private LauncherLayout layout;
    private LinearLayout grid;
    private boolean editing;
    private AlertDialog openedFolderDialog;private String openedFolderId;private Runnable openedFolderRedraw,folderListRedraw;
    private static final class Drag {final String id,folder;Drag(String id,String folder){this.id=id;this.folder=folder;}}
    public LauncherScreen(MainActivity a){this.a=a;tablet=a.getResources().getConfiguration().smallestScreenWidthDp>=600;layout=read(tablet);}
    private LauncherLayout read(boolean profile){String raw=a.preferences.getString(LauncherLayout.key(profile),"");try{return raw.isEmpty()?LauncherLayout.legacy(profile,a.preferences.getString("launcher_order","")):LauncherLayout.parse(raw,profile);}catch(Exception failure){return LauncherLayout.legacy(profile,a.preferences.getString("launcher_order",""));}}
    private void save(LauncherLayout model,boolean profile){a.preferences.edit().putString(LauncherLayout.key(profile),model.json()).apply();if(profile==tablet)layout=model;}
    private List<String> installed(){List<String> out=new ArrayList<>();for(String id:LauncherLayout.APPS)if(a.apps.installed(id))out.add(id);return out;}
    public List<String> ordered(){List<String> result=new ArrayList<>();for(String id:layout.visible(installed())){if(layout.folders.containsKey(id))result.addAll(layout.visibleFolder(id,installed()));else result.add(id);}return result;}
    public void render(){
        if(HostBuildProfile.BALL_RACE_TEST_HOST){grid=a.column();a.body.addView(grid);drawDesktop();return;}
        a.gap(a.body,14);LinearLayout controls=a.row();TextView edit=a.button(a.getString(R.string.launcher_edit),false,()->{});
        edit.setOnClickListener(v->{editing=!editing;edit.setText(a.getString(editing?R.string.launcher_finish_edit:R.string.launcher_edit));drawDesktop();});
        controls.addView(edit,new LinearLayout.LayoutParams(0,-2,1));a.gapHorizontal(controls,8);controls.addView(a.button(a.getString(R.string.launcher_customization),false,this::order),new LinearLayout.LayoutParams(0,-2,1));a.body.addView(controls);a.gap(a.body,12);grid=a.column();a.body.addView(grid);drawDesktop();
    }
    private void drawDesktop(){
        if(grid==null)return;grid.removeAllViews();List<String> ids;if(HostBuildProfile.BALL_RACE_TEST_HOST){ids=new ArrayList<>();if(a.apps.installed("ballrace"))ids.add("ballrace");}else{ids=layout.visible(installed());ids.add("store");ids.add("settings");}drawGrid(grid,ids,null,null);
        if(editing&&!HostBuildProfile.BALL_RACE_TEST_HOST){a.gap(grid,10);grid.addView(a.button(a.getString(R.string.launcher_new_folder),false,()->folderEditor(layout,tablet,null,null)));a.gap(grid,10);TextParagraphs.append(a,grid,a.getString(R.string.launcher_drag_note),13,a.MUTED);}
    }
    private void drawGrid(LinearLayout parent,List<String> ids,String folder,Runnable redraw){
        int widthDp=Math.max(220,Math.min(a.getResources().getConfiguration().screenWidthDp,620)-40),columns=Math.min(layout.columns,Math.max(2,widthDp/64));
        int available=(widthDp-(columns-1)*10)/columns-8,iconSize=Math.max(36,Math.min(layout.icon,available));
        for(int start=0;start<ids.size();start+=columns){LinearLayout row=a.row();row.setGravity(Gravity.TOP);
            for(int col=0;col<columns;col++){
                if(col>0)a.gapHorizontal(row,10);if(start+col>=ids.size()){row.addView(new View(a),new LinearLayout.LayoutParams(0,1,1));continue;}
                String id=ids.get(start+col);LauncherLayout.Folder f=layout.folders.get(id);boolean fixed=id.equals("settings")||id.equals("store");LinearLayout tile=a.column();tile.setGravity(Gravity.CENTER);tile.setPadding(a.dp(4),a.dp(12),a.dp(4),a.dp(12));
                View icon;if(f!=null)icon=new FolderIcon(f);else{ImageView image=new ImageView(a);int resource=id.equals("ballrace")?a.getResources().getIdentifier("app_ballrace","drawable","org.simpleweather.app"):0;if(resource==0&&id.equals("ballrace"))resource=a.getResources().getIdentifier("app_ballrace","drawable",a.getPackageName());image.setImageResource(resource==0?AppIcons.resource(id):resource);image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);icon=image;}tile.addView(icon,a.lp(iconSize,iconSize));a.gap(tile,10);
                String title=f==null?a.apps.title(id):f.name;TextView name=a.text(title,layout.text,a.INK,true);name.setGravity(Gravity.CENTER);name.setMinHeight(a.dp(38));name.setMaxLines(3);tile.addView(name,a.lp(-1,-2));
                a.tap(tile,editing&&!fixed?0x225286C2:Color.TRANSPARENT,20,()->{if(f!=null)openFolder(id);else if(editing&&!fixed)appMenu(id,folder,redraw);else open(a,id);});tile.setContentDescription(title);
                if(!fixed){
                    tile.setOnLongClickListener(v->{if(!editing){if(f!=null)folderMenu(id);else appMenu(id,folder,redraw);return true;}v.startDragAndDrop(ClipData.newPlainText("desktop",id),new View.DragShadowBuilder(v),new Drag(id,folder),0);return true;});
                    tile.setOnDragListener((v,e)->{if(!(e.getLocalState() instanceof Drag))return false;Drag from=(Drag)e.getLocalState();if(!Objects.equals(from.folder,folder))return false;if(e.getAction()==DragEvent.ACTION_DRAG_ENTERED){v.setAlpha(.55f);return true;}if(e.getAction()==DragEvent.ACTION_DRAG_EXITED||e.getAction()==DragEvent.ACTION_DRAG_ENDED){v.setAlpha(1);return true;}if(e.getAction()==DragEvent.ACTION_DROP){v.setAlpha(1);layout.move(from.id,id,folder);save(layout,tablet);if(redraw!=null)redraw.run();drawDesktop();return true;}return true;});
                }row.addView(tile,new LinearLayout.LayoutParams(0,-2,1));
            }parent.addView(row);a.gap(parent,14);
        }
    }
    public static boolean openRequested(MainActivity a,Intent intent){if(intent==null||!intent.hasExtra(LauncherShortcuts.EXTRA))return false;String id=intent.getStringExtra(LauncherShortcuts.EXTRA);intent.removeExtra(LauncherShortcuts.EXTRA);if(!LauncherLayout.application(id))return false;open(a,id);return true;}
    public static void open(MainActivity a,String id){
        if(HostBuildProfile.BALL_RACE_TEST_HOST){if(id.equals("ballrace"))a.apps.openInternal(id);return;}
        if(id.equals("settings")){a.settings();return;}if(id.equals("store")){a.switchTab(-2);return;}if(!LauncherLayout.application(id))return;
        if(!a.apps.installed(id)){new ToolDialog(a).setTitle(a.apps.title(id)).setMessage(a.getString(R.string.launcher_shortcut_missing)).setPositiveButton(a.getString(R.string.launcher_store),(d,w)->a.switchTab(-2)).setNegativeButton(a.getString(R.string.action_cancel),null).show();return;}
        if(Arrays.asList(AppModules.EMBEDDED).contains(id))a.apps.openInternal(id);else a.switchTab(Arrays.asList(AppModules.IDS).indexOf(id));
    }
    public void manage(String id){new ToolDialog(a).setTitle(a.apps.title(id)).setMessage(a.getString(R.string.module_remove_note)).setPositiveButton(a.getString(R.string.module_uninstall),(d,w)->{if(!a.apps.busy)a.apps.uninstall(id);}).setNegativeButton(a.getString(R.string.action_cancel),null).show();}
    private LinearLayout form(){LinearLayout c=a.column();c.setPadding(a.dp(18),a.dp(8),a.dp(18),a.dp(16));return c;}
    private void button(LinearLayout c,String title,Runnable action){c.addView(a.button(title,false,action));a.gap(c,10);}
    private AlertDialog dialog(String title,LinearLayout content){ScrollView scroll=new ScrollView(a);scroll.addView(content);return new ToolDialog(a).setTitle(title).setView(scroll).setNegativeButton(a.getString(R.string.action_back),null).show();}
    private void appMenu(String id,String folder,Runnable redraw){
        LinearLayout c=form();final AlertDialog[] d=new AlertDialog[1];button(c,a.getString(R.string.launcher_pin_shortcut),()->{d[0].dismiss();LauncherShortcuts.pin(a,id);});button(c,a.getString(R.string.launcher_move_to_folder),()->{d[0].dismiss();chooseFolder(id,redraw);});
        if(folder!=null)button(c,a.getString(R.string.launcher_move_to_desktop),()->{layout.put(id,null);save(layout,tablet);d[0].dismiss();if(redraw!=null)redraw.run();drawDesktop();});button(c,a.getString(R.string.module_uninstall),()->{d[0].dismiss();manage(id);});d[0]=dialog(a.apps.title(id),c);
    }
    private void chooseFolder(String app,Runnable redraw){LinearLayout c=form();final AlertDialog[] d=new AlertDialog[1];for(LauncherLayout.Folder f:layout.folders.values())button(c,f.name,()->{layout.put(app,f.id);save(layout,tablet);d[0].dismiss();if(redraw!=null)redraw.run();drawDesktop();});button(c,a.getString(R.string.launcher_new_folder),()->{d[0].dismiss();folderEditor(layout,tablet,null,app);});d[0]=dialog(a.getString(R.string.launcher_move_to_folder),c);}
    private JSONObject sessionFolder(boolean profile,String id,String initial){try{return new JSONObject().put("tablet",profile).put("id",id==null?"":id).put("initial",initial==null?"":initial);}catch(JSONException impossible){return new JSONObject();}}
    public void restoreFolderEditor(JSONObject data){if(data==null)return;boolean profile=data.optBoolean("tablet",tablet);String id=data.optString("id"),initial=data.optString("initial");LauncherLayout model=read(profile);if(!id.isEmpty()&&!model.folders.containsKey(id)||!initial.isEmpty()&&!LauncherLayout.application(initial))return;folderEditor(model,profile,id.isEmpty()?null:id,initial.isEmpty()?null:initial);}
    public void restoreFolder(JSONObject data){if(data!=null&&layout.folders.containsKey(data.optString("id")))openFolder(data.optString("id"));}
    public void restoreFolderMenu(JSONObject data){if(data!=null&&layout.folders.containsKey(data.optString("id")))folderMenu(data.optString("id"));}
    public void restoreFolderList(JSONObject data){boolean profile=data!=null&&data.optBoolean("tablet",tablet);folderList(read(profile),profile);}
    public void restoreSort(JSONObject data){boolean profile=data!=null&&data.optBoolean("tablet",tablet);sort(read(profile),profile);}
    private void refreshFolder(){if(openedFolderDialog==null||!openedFolderDialog.isShowing())return;LauncherLayout.Folder folder=layout.folders.get(openedFolderId);if(folder==null){openedFolderDialog.dismiss();return;}openedFolderDialog.setTitle(folder.name);if(openedFolderRedraw!=null)openedFolderRedraw.run();}
    private void openFolder(String id){
        LauncherLayout.Folder f=layout.folders.get(id);if(f==null)return;LinearLayout c=form(),apps=a.column();button(c,a.getString(R.string.launcher_folder_manage),()->folderMenu(id));c.addView(apps);Runnable[] redraw=new Runnable[1];redraw[0]=()->{apps.removeAllViews();if(!layout.folders.containsKey(id))return;List<String> items=layout.visibleFolder(id,installed());if(items.isEmpty())apps.addView(a.text(a.getString(R.string.launcher_folder_empty),14,a.MUTED,false));else drawGrid(apps,items,id,redraw[0]);};redraw[0].run();if(editing){a.gap(c,10);TextParagraphs.append(a,c,a.getString(R.string.launcher_drag_folder_note),13,a.MUTED);}openedFolderDialog=dialog(f.name,c);openedFolderId=id;openedFolderRedraw=redraw[0];UiSession.bindDraft(openedFolderDialog,"LauncherScreen","restoreFolder",()->sessionFolder(tablet,id,null));
    }
    private void folderMenu(String id){
        LauncherLayout.Folder f=layout.folders.get(id);if(f==null)return;LinearLayout c=form();final AlertDialog[] d=new AlertDialog[1];button(c,a.getString(R.string.launcher_folder_edit),()->{d[0].dismiss();folderEditor(layout,tablet,id,null);});button(c,a.getString(R.string.launcher_folder_delete),()->deleteFolder(layout,tablet,id,()->{d[0].dismiss();drawDesktop();}));d[0]=dialog(f.name,c);UiSession.bindDraft(d[0],"LauncherScreen","restoreFolderMenu",()->sessionFolder(tablet,id,null));
    }
    private void deleteFolder(LauncherLayout model,boolean profile,String id,Runnable done){new ToolDialog(a).setTitle(a.getString(R.string.launcher_folder_delete)).setMessage(a.getString(R.string.launcher_folder_delete_note)).setNegativeButton(a.getString(R.string.action_cancel),null).setPositiveButton(a.getString(R.string.launcher_folder_delete),(x,w)->{model.deleteFolder(id);save(model,profile);done.run();refreshFolder();}).show();}
    private void folderEditor(LauncherLayout model,boolean profile,String id,String initial){folderEditor(model,profile,id,initial,null);}
    private void folderEditor(LauncherLayout model,boolean profile,String id,String initial,Runnable after){
        LauncherLayout.Folder current=model.folders.get(id);LinearLayout c=form();c.addView(a.text(a.getString(R.string.launcher_folder_name),14,a.INK,true));a.gap(c,8);EditText name=new EditText(a);name.setSingleLine(true);name.setTextSize(16);name.setPadding(a.dp(14),a.dp(12),a.dp(14),a.dp(12));name.setBackground(a.shape(a.buttonSecondaryColor(),14));name.setTextColor(a.primaryTextColor());name.setText(current==null?"":current.name);name.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(40)});c.addView(name,a.lp(-1,-2));a.gap(c,14);
        c.addView(a.text(a.getString(R.string.launcher_folder_members),14,a.INK,true));a.gap(c,8);Set<String> selected=new LinkedHashSet<>();if(current!=null)selected.addAll(current.apps);if(initial!=null)selected.add(initial);List<String> choices=installed();if(current!=null)for(String app:current.apps)if(!choices.contains(app))choices.add(app);
        for(String app:choices){CheckBox box=new CheckBox(a);box.setText(a.apps.title(app));box.setTextSize(15);box.setTextColor(a.primaryTextColor());box.setButtonTintList(android.content.res.ColorStateList.valueOf(0xFF3577CC));box.setChecked(selected.contains(app));box.setPadding(a.dp(8),a.dp(6),a.dp(8),a.dp(6));box.setOnCheckedChangeListener((b,checked)->{if(checked)selected.add(app);else selected.remove(app);});c.addView(box,a.lp(-1,-2));}
        a.gap(c,12);TextParagraphs.append(a,c,a.getString(R.string.launcher_folder_members_note),13,a.MUTED);ScrollView scroll=new ScrollView(a);scroll.addView(c);AlertDialog d=new ToolDialog(a).setTitle(a.getString(current==null?R.string.launcher_new_folder:R.string.launcher_folder_edit)).setView(scroll).setPositiveButton(a.getString(R.string.action_save),null).setNegativeButton(a.getString(R.string.action_cancel),null).create();
        UiSession.bindDraft(d,"LauncherScreen","restoreFolderEditor",()->sessionFolder(profile,id,initial));
        d.setOnShowListener(v->d.getButton(-1).setOnClickListener(b->{String value=name.getText().toString().trim();if(!LauncherLayout.validName(value)){a.toast(a.getString(R.string.launcher_folder_name_invalid));return;}if(current==null&&model.folders.size()>=32){a.toast(a.getString(R.string.launcher_folder_limit));return;}if(current==null)model.create(value,selected);else{current.name=value;for(String app:new ArrayList<>(current.apps))if(!selected.contains(app))model.put(app,null);for(String app:selected)if(!current.apps.contains(app))model.put(app,id);}save(model,profile);d.dismiss();drawDesktop();refreshFolder();if(after!=null)after.run();else if(folderListRedraw!=null)folderListRedraw.run();}));d.show();
    }
    /** Existing global application-order entry now opens the complete desktop customization group. */
    public void order(){
        LinearLayout c=form(),body=a.column();RadioGroup profiles=new RadioGroup(a);profiles.setOrientation(LinearLayout.HORIZONTAL);RadioButton phone=new RadioButton(a),large=new RadioButton(a);phone.setId(View.generateViewId());large.setId(View.generateViewId());phone.setText(a.getString(R.string.launcher_profile_phone));large.setText(a.getString(R.string.launcher_profile_tablet));phone.setButtonTintList(android.content.res.ColorStateList.valueOf(0xFF3577CC));large.setButtonTintList(android.content.res.ColorStateList.valueOf(0xFF3577CC));profiles.addView(phone,new RadioGroup.LayoutParams(0,-2,1));profiles.addView(large,new RadioGroup.LayoutParams(0,-2,1));profiles.check(tablet?large.getId():phone.getId());c.addView(profiles);a.gap(c,14);c.addView(body);
        java.util.function.Consumer<Boolean> draw=profile->{body.removeAllViews();LauncherLayout model=read(profile);slider(body,R.string.launcher_icon_size,model.icon,48,96,n->{model.icon=n;save(model,profile);});slider(body,R.string.launcher_columns,model.columns,2,8,n->{model.columns=n;save(model,profile);});slider(body,R.string.launcher_text_size,model.text,10,22,n->{model.text=n;save(model,profile);});button(body,a.getString(R.string.launcher_sort_items),()->sort(model,profile));button(body,a.getString(R.string.launcher_folder_manage),()->folderList(model,profile));a.gap(body,4);TextParagraphs.append(a,body,a.getString(R.string.launcher_customization_note),13,a.MUTED);};profiles.setOnCheckedChangeListener((g,id)->draw.accept(id==large.getId()));draw.accept(tablet);dialog(a.getString(R.string.launcher_customization),c);
    }
    private void slider(LinearLayout parent,int title,int value,int min,int max,java.util.function.IntConsumer changed){TextView label=a.text(a.getString(title,value),15,a.INK,true);parent.addView(label);a.gap(parent,4);SeekBar slider=new SeekBar(a);slider.setMax(max-min);slider.setProgress(value-min);slider.setContentDescription(a.getString(title,value));slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar bar,int n,boolean user){label.setText(a.getString(title,n+min));if(user)changed.accept(n+min);}public void onStartTrackingTouch(SeekBar bar){}public void onStopTrackingTouch(SeekBar bar){drawDesktop();}});parent.addView(slider,a.lp(-1,52));a.gap(parent,10);}
    private void sort(LauncherLayout model,boolean profile){
        LinearLayout c=form();Runnable[] draw=new Runnable[1];draw[0]=()->{c.removeAllViews();List<String> ids=model.visible(installed());for(int i=0;i<ids.size();i++){final int index=i;String id=ids.get(i);LinearLayout row=a.row();row.addView(a.text(model.folders.containsKey(id)?model.folders.get(id).name:a.apps.title(id),15,a.INK,false),new LinearLayout.LayoutParams(0,-2,1));a.gapHorizontal(row,8);TextView up=a.button("↑",false,()->{if(index>0){model.move(id,ids.get(index-1),null);save(model,profile);draw[0].run();drawDesktop();}});up.setContentDescription(a.getString(R.string.launcher_move_up));up.setEnabled(i>0);row.addView(up,a.lp(48,48));a.gapHorizontal(row,8);TextView down=a.button("↓",false,()->{if(index+1<ids.size()){model.move(id,ids.get(index+1),null);save(model,profile);draw[0].run();drawDesktop();}});down.setContentDescription(a.getString(R.string.launcher_move_down));down.setEnabled(i+1<ids.size());row.addView(down,a.lp(48,48));c.addView(row);a.gap(c,10);}a.gap(c,4);TextParagraphs.append(a,c,a.getString(R.string.launcher_sort_note),13,a.MUTED);};draw[0].run();AlertDialog dialog=dialog(a.getString(R.string.launcher_sort_items),c);UiSession.bindDraft(dialog,"LauncherScreen","restoreSort",()->sessionFolder(profile,null,null));
    }
    private void folderList(LauncherLayout model,boolean profile){
        LinearLayout c=form();Runnable[] draw=new Runnable[1];draw[0]=()->{
            LauncherLayout latest=read(profile);c.removeAllViews();
            for(LauncherLayout.Folder folder:latest.folders.values()){
                LinearLayout row=a.row();row.addView(a.button(folder.name,false,()->folderEditor(latest,profile,folder.id,null,draw[0])),new LinearLayout.LayoutParams(0,-2,1));a.gapHorizontal(row,8);
                row.addView(a.button(a.getString(R.string.launcher_folder_delete),false,()->deleteFolder(latest,profile,folder.id,()->{draw[0].run();drawDesktop();})),a.lp(-2,-2));c.addView(row);a.gap(c,10);
            }
            if(latest.folders.isEmpty())c.addView(a.text(a.getString(R.string.launcher_no_folders),14,a.MUTED,false));a.gap(c,10);button(c,a.getString(R.string.launcher_new_folder),()->folderEditor(latest,profile,null,null,draw[0]));
        };draw[0].run();folderListRedraw=draw[0];AlertDialog dialog=dialog(a.getString(R.string.launcher_folder_manage),c);UiSession.bindDraft(dialog,"LauncherScreen","restoreFolderList",()->sessionFolder(profile,null,null));
    }
    private final class FolderIcon extends View {
        private final LauncherLayout.Folder folder;private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);FolderIcon(LauncherLayout.Folder folder){super(a);this.folder=folder;setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);float w=getWidth(),h=getHeight();paint.setColor(0xFFDCE7F2);canvas.drawRoundRect(0,0,w,h,w*.18f,h*.18f,paint);List<String> items=layout.visibleFolder(folder.id,installed());for(int n=0;n<Math.min(4,items.size());n++){Drawable drawable=a.getDrawable(AppIcons.resource(items.get(n)));int size=(int)(w*.35f),left=(int)(w*(.1f+(n%2)*.45f)),top=(int)(h*(.1f+(n/2)*.45f));drawable.setBounds(left,top,left+size,top+size);drawable.draw(canvas);}}
    }
}
