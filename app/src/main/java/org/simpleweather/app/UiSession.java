package org.simpleweather.app;

import android.app.AlertDialog;
import android.content.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.function.*;

/** A task-local recovery snapshot. Never resumes device work or confirms an operation. */
public final class UiSession {
    private static final String KEY="toolbox_ui_session";
    private static final int MAX_BYTES=2*1024*1024,MAX_DIALOGS=12,MAX_VIEWS=1600;
    private static final long MAX_AGE=48*60*60*1000L;
    private static final WeakHashMap<AlertDialog,Entry> OPEN=new WeakHashMap<>();
    private static long next;
    private static final Map<String,Set<String>> SAFE=new HashMap<>();
    private static final Map<String,String> MODULES=new HashMap<>();
    private static final Map<String,Set<String>> DRAFTS=new HashMap<>();
    static {
        allow("SettingsScreen","", "show global internalData moduleTransfer keepScreenAwake language timeDisplay background outline colors textColors permissions shortcutSettings cache about");
        allow("WeatherSettings","weather","show weather legend displayOrder");
        allow("MapSettings","weather-map","show maps");
        allow("AstronomySettings","astronomy","show astronomy astronomyOrientation astronomyCoordinates astronomyStars displayOrder");
        allow("StatusSettings","status","show location positionSettings sensorSettings batterySettings batteryReadings satelliteLifecycle locationLifecycle lightSource cameraSettings cameraChoice routeInterval statusNorth batteryCurrent statusConstellations statusSensors statusSatelliteSorting locationProvider locationInterval displayOrder deviceFields batteryFields currentUnit chargeUnit indoorExposure outdoorExposure");
        allow("EarthquakeScreen","earthquake","settings magnitudeSettings areaSettings referenceSettings sources");
        allow("TodoScreen","todo","settings displaySettings defaultSettings notificationSettings");
        allow("RandomChoiceScreen","random","settings lists remaining");
        allow("ScheduleScreen","schedule","menu notifySettings prefixes semester times selectWeek");
        allow("LauncherScreen","","order");
        allow("GpsScreen","status","");
        DRAFTS.put("TodoScreen",new HashSet<>(Arrays.asList("restoreEditor","restoreSlotEditor")));
        DRAFTS.put("ScheduleScreen",new HashSet<>(Arrays.asList("restoreCourseEditor","restoreSemester","restoreTimeEditor")));
        DRAFTS.put("GpsScreen",new HashSet<>(Arrays.asList("restorePlaceEditor","restorePlacesManager")));
        DRAFTS.put("LauncherScreen",new HashSet<>(Arrays.asList("restoreFolderEditor","restoreFolder","restoreFolderMenu","restoreFolderList","restoreSort")));
    }
    private static void allow(String owner,String module,String names){SAFE.put(owner,new HashSet<>(Arrays.asList(names.split(" "))));MODULES.put(owner,module);}
    static final class Route {
        final String owner,method,module;
        Route(String owner,String method){this.owner=owner;this.method=method;this.module=MODULES.get(owner);}
    }
    private static final class Entry {
        final long sequence=++next;
        Route route;
        Supplier<JSONObject> extra;
        Entry(Route route){this.route=route;}
    }
    private UiSession(){}
    static Route inferRoute(){
        for(StackTraceElement frame:Thread.currentThread().getStackTrace()){
            String name=frame.getClassName();if(!name.startsWith("org.simpleweather.app."))continue;
            String owner=name.substring(name.lastIndexOf('.')+1),method=frame.getMethodName();
            if(!SAFE.containsKey(owner))continue;
            if(method.equals("dialog")||method.equals("singleChoice")||method.startsWith("lambda$")||method.equals("<init>"))continue;
            if(owner.endsWith("Settings")&&Arrays.asList("order","statusFieldOrder","batteryUnit","cameraExposure").contains(method))continue;
            // The innermost route must be explicitly safe: never fall back past a delete/editor helper.
            return SAFE.get(owner).contains(method)?new Route(owner,method):null;
        }
        return null;
    }
    static void register(AlertDialog dialog,Route route){Entry entry=OPEN.get(dialog);if(entry==null)OPEN.put(dialog,new Entry(route));else if(route!=null)entry.route=route;}
    public static void release(MainActivity a){for(AlertDialog dialog:new ArrayList<>(OPEN.keySet()))if(dialog!=null&&activity(dialog.getContext())==a){OPEN.remove(dialog);if(dialog.isShowing())dialog.dismiss();}}
    public static void todoDraft(AlertDialog dialog,Supplier<JSONObject> extra){Entry entry=OPEN.get(dialog);if(entry==null){entry=new Entry(null);OPEN.put(dialog,entry);}entry.route=new Route("TodoScreen","restoreEditor");entry.extra=extra;}
    public static void todoSlotDraft(AlertDialog dialog,int index){Entry entry=OPEN.get(dialog);if(entry==null){entry=new Entry(null);OPEN.put(dialog,entry);}entry.route=new Route("TodoScreen","restoreSlotEditor");entry.extra=()->{try{return new JSONObject().put("index",index);}catch(JSONException ignored){return new JSONObject();}};}
    public static void bindDraft(AlertDialog dialog,String owner,String method,Supplier<JSONObject> extra){if(!DRAFTS.containsKey(owner)||!DRAFTS.get(owner).contains(method))throw new IllegalArgumentException("Unsupported task draft");Entry entry=OPEN.get(dialog);if(entry==null){entry=new Entry(null);OPEN.put(dialog,entry);}entry.route=new Route(owner,method);entry.extra=extra;}
    private static MainActivity activity(Context c){for(int i=0;i<8&&c!=null;i++){if(c instanceof MainActivity)return (MainActivity)c;if(!(c instanceof ContextWrapper))break;Context next=((ContextWrapper)c).getBaseContext();if(next==c)break;c=next;}return null;}
    private static List<Map.Entry<AlertDialog,Entry>> dialogs(MainActivity a){List<Map.Entry<AlertDialog,Entry>> rows=new ArrayList<>();for(Map.Entry<AlertDialog,Entry> row:OPEN.entrySet())if(row.getKey()!=null&&row.getKey().isShowing()&&activity(row.getKey().getContext())==a&&row.getValue().route!=null)rows.add(row);rows.sort(Comparator.comparingLong(row->row.getValue().sequence));return rows;}
    public static void capture(MainActivity a,Bundle state){
        if(state==null)return;
        try{
            JSONArray rows=new JSONArray();for(Map.Entry<AlertDialog,Entry> row:dialogs(a)){
                if(rows.length()>=MAX_DIALOGS)break;Route route=row.getValue().route;
                JSONObject item=new JSONObject().put("owner",route.owner).put("method",route.method).put("module",route.module).put("views",viewState(dialogView(row.getKey())));
                if(row.getValue().extra!=null)item.put("extra",row.getValue().extra.get());rows.put(item);
            }
            JSONArray root=new JSONArray();if(a.getWindow()!=null){JSONArray candidates=viewState(a.getWindow().getDecorView());for(int i=0;i<candidates.length();i++){JSONObject value=candidates.getJSONObject(i);if(value.has("text"))root.put(value);}}
            if(rows.length()==0&&root.length()==0){state.remove(KEY);return;}
            JSONObject snapshot=new JSONObject().put("format",1).put("at",System.currentTimeMillis()).put("dialogs",rows).put("root",root);
            byte[] bytes=snapshot.toString().getBytes(StandardCharsets.UTF_8);if(bytes.length>MAX_BYTES)return;
            File dir=directory(a);if(!dir.isDirectory()&&!dir.mkdirs())return;prune(dir);
            String token=UUID.randomUUID().toString();File target=new File(dir,token+".json");try(FileOutputStream out=new FileOutputStream(target)){out.write(bytes);}state.putString(KEY,token);
        }catch(Exception failure){android.util.Log.w("UiSession","Cannot capture task interface",failure);}
    }
    public static void restore(MainActivity a,Bundle state,Function<String,ScreenHandle> screens){
        if(state==null)return;String token=state.getString(KEY);if(token==null||!token.matches("[0-9a-f-]{36}"))return;
        try{
            File file=new File(directory(a),token+".json");if(!file.isFile()||file.length()>MAX_BYTES)return;
            JSONObject snapshot=new JSONObject(new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8));long age=System.currentTimeMillis()-snapshot.getLong("at");if(snapshot.getInt("format")!=1||age<0||age>MAX_AGE)return;
            JSONArray rows=snapshot.getJSONArray("dialogs");if(rows.length()>MAX_DIALOGS)return;
            JSONArray root=snapshot.optJSONArray("root");a.getWindow().getDecorView().post(()->{restoreViews(a.getWindow().getDecorView(),root);restoreRows(a,rows,screens);});
        }catch(Exception failure){android.util.Log.w("UiSession","Task interface snapshot unavailable",failure);}
    }
    private static void restoreRows(MainActivity a,JSONArray rows,Function<String,ScreenHandle> screens){
        if(a.isFinishing()||a.isDestroyed())return;
        Map<String,Object> targets=new HashMap<>();
        for(int i=0;i<rows.length();i++)try{
            JSONObject row=rows.getJSONObject(i);String owner=row.getString("owner"),method=row.getString("method"),module=MODULES.get(owner);
            boolean draft=DRAFTS.containsKey(owner)&&DRAFTS.get(owner).contains(method);
            if(module==null||(!draft&&!SAFE.get(owner).contains(method))||!module.equals(row.optString("module")))continue;
            if(!module.isEmpty()&&!a.apps.installed(module))continue;
            Object target;
            if(owner.endsWith("Screen")&&!owner.equals("SettingsScreen")&&!owner.equals("LauncherScreen")){
                ScreenHandle handle=screens.apply(module);if(handle==null)continue;target=handle.sessionTarget();
            }else{
                target=targets.get(owner);if(target==null){ClassLoader loader=module.isEmpty()?UiSession.class.getClassLoader():ModuleRuntime.loader(a,module);target=Class.forName("org.simpleweather.app."+owner,true,loader).getConstructor(MainActivity.class).newInstance(a);targets.put(owner,target);}
            }
            long before=next;Method call=target.getClass().getDeclaredMethod(method,draft?new Class<?>[]{JSONObject.class}:new Class<?>[0]);call.setAccessible(true);if(draft)call.invoke(target,row.optJSONObject("extra"));else call.invoke(target);
            AlertDialog opened=null;for(Map.Entry<AlertDialog,Entry> dialog:dialogs(a))if(dialog.getValue().sequence>before)opened=dialog.getKey();
            if(opened!=null&&opened.getWindow()!=null){final AlertDialog current=opened;JSONArray values=row.optJSONArray("views");restoreViews(dialogView(current),values);dialogView(current).post(()->restoreViews(dialogView(current),values));}
        }catch(Exception failure){android.util.Log.w("UiSession","Skipped unavailable interface",failure);}
    }
    private static View dialogView(AlertDialog dialog){return dialog instanceof SettingsPanel?((SettingsPanel)dialog).pageView():dialog.getWindow().getDecorView();}
    private static File directory(Context c){return new File(c.getCacheDir(),"ui-session");}
    private static void prune(File directory){File[] files=directory.listFiles();if(files==null)return;Arrays.sort(files,Comparator.comparingLong(File::lastModified).reversed());long cutoff=System.currentTimeMillis()-MAX_AGE;for(int i=0;i<files.length;i++)if(files[i].lastModified()<cutoff||i>=24)files[i].delete();}
    static JSONArray viewState(View root)throws JSONException{JSONArray out=new JSONArray();captureView(root,"",out);return out;}
    private static void captureView(View view,String path,JSONArray out)throws JSONException{
        if(out.length()>=MAX_VIEWS)return;JSONObject value=new JSONObject().put("path",path).put("class",view.getClass().getName());boolean keep=false;
        if(view instanceof EditText){EditText edit=(EditText)view;String text=edit.getText().toString();if(text.length()<=16000){value.put("text",text).put("start",edit.getSelectionStart()).put("end",edit.getSelectionEnd());keep=true;}}
        else if(view instanceof CompoundButton){value.put("checked",((CompoundButton)view).isChecked());keep=true;}
        else if(view instanceof Spinner){value.put("selected",((Spinner)view).getSelectedItemPosition());keep=true;}
        if(view instanceof ScrollView){value.put("scroll",view.getScrollY());keep=true;}
        if(view instanceof ListView){ListView list=(ListView)view;value.put("first",list.getFirstVisiblePosition());if(list.getChildCount()>0)value.put("top",list.getChildAt(0).getTop());if(list.getChoiceMode()==ListView.CHOICE_MODE_SINGLE)value.put("choice",list.getCheckedItemPosition());keep=true;}
        if(view.isFocused()){value.put("focus",true);keep=true;}if(keep)out.put(value);
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)captureView(group.getChildAt(i),path+"/"+i,out);}
    }
    static void restoreViews(View root,JSONArray values){
        if(values==null||values.length()>MAX_VIEWS)return;
        for(int i=0;i<values.length();i++)try{JSONObject value=values.getJSONObject(i);View view=find(root,value.getString("path"));if(view==null||!view.getClass().getName().equals(value.getString("class")))continue;
            if(view instanceof CompoundButton&&value.has("checked"))((CompoundButton)view).setChecked(value.getBoolean("checked"));
            if(view instanceof Spinner&&value.has("selected")){Spinner spinner=(Spinner)view;int selected=value.getInt("selected");if(selected>=0&&selected<spinner.getCount()&&spinner.getSelectedItemPosition()!=selected)spinner.setSelection(selected);}
            if(view instanceof EditText&&value.has("text")){EditText edit=(EditText)view;String text=value.getString("text");if(text.length()<=16000&&!text.contentEquals(edit.getText()))edit.setText(text);int start=value.optInt("start",0),end=value.optInt("end",start);if(start>=0&&end>=start&&end<=edit.length())edit.setSelection(start,end);}
            if(value.optBoolean("focus")&&view.isShown())view.requestFocus();
            if(view instanceof ScrollView&&value.has("scroll"))((ScrollView)view).scrollTo(0,Math.max(0,value.getInt("scroll")));
            if(view instanceof ListView){ListView list=(ListView)view;if(value.has("choice")){int choice=value.getInt("choice");if(choice>=0&&choice<list.getCount())list.setItemChecked(choice,true);}if(value.has("first"))list.setSelectionFromTop(Math.max(0,value.getInt("first")),value.optInt("top"));}
        }catch(Exception ignored){}
    }
    private static View find(View root,String path){if(!path.matches("(?:/[0-9]{1,4})*"))return null;View value=root;if(path.isEmpty())return root;for(String part:path.substring(1).split("/")){if(!(value instanceof ViewGroup))return null;int index=Integer.parseInt(part);ViewGroup group=(ViewGroup)value;if(index>=group.getChildCount())return null;value=group.getChildAt(index);}return value;}
}
