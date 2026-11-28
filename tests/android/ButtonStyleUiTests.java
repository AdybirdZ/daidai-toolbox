package org.simpleweather.qa;

import android.app.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.drawable.*;
import android.os.*;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.*;

/** Exercises production factories and platform dialogs in the installed host/app themes. */
public final class ButtonStyleUiTests extends Instrumentation {
    private Activity host,child;
    private Context context;
    private ClassLoader hostLoader;
    private Object apps;
    private Bundle arguments;
    private int checks;
    private final List<Dialog> openDialogs=new ArrayList<>();
    private final Map<String,Map<String,?>> preferences=new LinkedHashMap<>();
    private final Map<String,Boolean> installedBefore=new LinkedHashMap<>();

    private static final class Probe {
        final TextView view;final String name;final AtomicInteger clicks;
        Probe(TextView view,String name,AtomicInteger clicks){this.view=view;this.name=name;this.clicks=clicks;}
    }
    @Override public void onCreate(Bundle args){super.onCreate(args);arguments=args;start();}
    private Class<?> type(String name)throws Exception{return Class.forName("org.daidai.toolbox."+name,true,hostLoader);}
    private Object field(Object object,String name)throws Exception{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    private Object call(Object object,String name,Class<?>[] types,Object...values)throws Exception{
        Class<?> owner=object instanceof Class?(Class<?>)object:object.getClass();Method method=owner.getDeclaredMethod(name,types);method.setAccessible(true);return method.invoke(object instanceof Class?null:object,values);
    }
    private <T>T ui(Callable<T> action){
        AtomicReference<T> result=new AtomicReference<>();AtomicReference<Throwable> error=new AtomicReference<>();
        runOnMainSync(()->{try{result.set(action.call());}catch(Throwable failure){error.set(failure);}});
        if(error.get()!=null)throw new RuntimeException(error.get());waitForIdleSync();return result.get();
    }
    private void check(boolean condition,String message){if(!condition)throw new AssertionError(message);checks++;}
    private TextView findLabel(View root,String label){if(root instanceof TextView&&label.contentEquals(((TextView)root).getText()))return (TextView)root;if(root instanceof ViewGroup){ViewGroup group=(ViewGroup)root;for(int i=0;i<group.getChildCount();i++){TextView found=findLabel(group.getChildAt(i),label);if(found!=null)return found;}}return null;}
    private float cornerRadius(View view){Drawable background=view.getBackground();if(background instanceof RippleDrawable)background=((RippleDrawable)background).getDrawable(0);if(background instanceof InsetDrawable)background=((InsetDrawable)background).getDrawable();return background instanceof GradientDrawable?((GradientDrawable)background).getCornerRadius():-1;}
    private void visibleTopBorder(View view,String origin){Bitmap screenshot=getUiAutomation().takeScreenshot();try{int[] location=new int[2];view.getLocationOnScreen(location);int center=location[0]+view.getWidth()/2;boolean visible=false;for(int y=location[1];y<Math.min(screenshot.getHeight(),location[1]+Math.max(5,Math.round(context.getResources().getDisplayMetrics().density*3)));y++){int color=screenshot.getPixel(center,y);if(android.graphics.Color.red(color)<80&&android.graphics.Color.green(color)<80&&android.graphics.Color.blue(color)<80){visible=true;break;}}check(visible,origin+" displays the full top outline");}finally{screenshot.recycle();}}
    private LinearLayout.LayoutParams desktopLayout(Activity activity,String origin)throws Exception{
        int homeId=context.getResources().getIdentifier("launcher_home","string","org.daidai.toolbox");String home=(String)call(type("SettingsActivity"),"hostText",new Class[]{Context.class,int.class},activity,homeId);TextView button=findLabel(activity.getWindow().getDecorView(),home);check(button!=null&&button.isClickable(),origin+" has a clickable desktop button");
        LinearLayout.LayoutParams layout=(LinearLayout.LayoutParams)button.getLayoutParams();float density=activity.getResources().getDisplayMetrics().density;
        check(layout.width==LinearLayout.LayoutParams.MATCH_PARENT&&layout.height==LinearLayout.LayoutParams.WRAP_CONTENT,origin+" desktop button fills the settings-style width and sizes to its label");check(button.getHeight()>=Math.round(52*density),origin+" desktop button preserves the 52 dp minimum height");
        check(layout.leftMargin==Math.round(18*density)&&layout.rightMargin==Math.round(18*density)&&layout.topMargin==Math.round(4*density)&&layout.bottomMargin==Math.round(4*density),origin+" desktop button has safe top inset and settings-style placement");
        check(Math.abs(cornerRadius(button)-10*density)<1,origin+" desktop button has the settings-style rounded corners");check(button.getLayout()!=null&&button.getLayout().getHeight()+button.getPaddingTop()+button.getPaddingBottom()<=button.getHeight(),origin+" desktop label fits inside the button");visibleTopBorder(button,origin);
        float originalTextSize=button.getTextSize();ui(()->{button.setTextSize(TypedValue.COMPLEX_UNIT_SP,30);button.requestLayout();return null;});check(button.getHeight()>Math.round(52*density),origin+" desktop button grows for enlarged text");check(button.getLayout()!=null&&button.getLayout().getHeight()+button.getPaddingTop()+button.getPaddingBottom()<=button.getHeight(),origin+" enlarged desktop label is not clipped");ui(()->{button.setTextSize(TypedValue.COMPLEX_UNIT_PX,originalTextSize);button.requestLayout();return null;});return layout;
    }
    private boolean ripple(Drawable drawable){
        if(drawable==null)return false;
        if(drawable instanceof RippleDrawable)return true;
        if(drawable instanceof InsetDrawable)return ripple(((InsetDrawable)drawable).getDrawable());
        if(drawable instanceof ScaleDrawable)return ripple(((ScaleDrawable)drawable).getDrawable());
        if(drawable instanceof LayerDrawable){LayerDrawable layers=(LayerDrawable)drawable;for(int i=0;i<layers.getNumberOfLayers();i++)if(ripple(layers.getDrawable(i)))return true;}
        Drawable current=drawable.getCurrent();return current!=drawable&&ripple(current);
    }
    private void flat(Probe probe,String state){
        check(probe.view.getStateListAnimator()==null,probe.name+" has no elevation animator "+state);
        check(Math.abs(probe.view.getElevation())<.001f,probe.name+" elevation stays zero "+state);
        check(Math.abs(probe.view.getTranslationZ())<.001f,probe.name+" translationZ stays zero "+state);
        check(ripple(probe.view.getBackground()),probe.name+" retains a ripple "+state);
        check(probe.view.isEnabled()&&probe.view.isClickable(),probe.name+" remains actionable "+state);
    }
    private void exercise(AlertDialog dialog,List<Probe> probes)throws Exception{
        float decorElevation=ui(()->dialog.getWindow().getDecorView().getElevation());
        Drawable windowBackground=ui(()->dialog.getWindow().getDecorView().getBackground());
        ui(()->{TypedValue floating=new TypedValue();check(dialog.getContext().getTheme().resolveAttribute(android.R.attr.windowIsFloating,floating,true)&&floating.data!=0,"dialog remains a floating window");check(windowBackground!=null,"dialog retains its window background");for(Probe p:probes){flat(p,"at rest");p.view.setPressed(true);p.view.refreshDrawableState();flat(p,"at press start");}return null;});
        // Holding the pressed state exposes the framework's delayed elevation animator.
        SystemClock.sleep(220);
        ui(()->{for(Probe p:probes){flat(p,"while pressed");p.view.setPressed(false);p.view.refreshDrawableState();}check(dialog.isShowing(),"press feedback does not dismiss the dialog");check(Math.abs(dialog.getWindow().getDecorView().getElevation()-decorElevation)<.001f,"button interaction preserves dialog elevation");check(dialog.getWindow().getDecorView().getBackground()==windowBackground,"button interaction preserves dialog background");return null;});
        SystemClock.sleep(220);
        ui(()->{for(Probe p:probes){flat(p,"after release");if(p.clicks!=null){int before=p.clicks.get();p.view.performClick();check(p.clicks.get()==before+1,p.name+" dispatches exactly one click");}}return null;});
    }
    private LinearLayout column(Context c){LinearLayout box=new LinearLayout(c);box.setOrientation(LinearLayout.VERTICAL);return box;}
    private AlertDialog showFixture(Activity activity,LinearLayout content,List<Probe> probes,String label){
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle(label).setView(content).setPositiveButton("Accept",null).setNegativeButton("Cancel",null).setNeutralButton("Other",null).create();dialog.show();openDialogs.add(dialog);
        for(int which:new int[]{AlertDialog.BUTTON_POSITIVE,AlertDialog.BUTTON_NEGATIVE,AlertDialog.BUTTON_NEUTRAL})probes.add(new Probe(dialog.getButton(which),label+" dialog button "+which,null));return dialog;
    }
    private Probe factoryButton(Object factory,LinearLayout box,String label)throws Exception{
        AtomicInteger clicks=new AtomicInteger();TextView view=(TextView)call(factory,"button",new Class[]{LinearLayout.class,String.class,Runnable.class},box,label,(Runnable)clicks::incrementAndGet);return new Probe(view,label,clicks);
    }
    private void choices(Context c)throws Exception{
        ui(()->{
            LinearLayout box=column(c);RadioButton radio=new RadioButton(c);CheckBox checkbox=new CheckBox(c);radio.setText("Radio control");checkbox.setText("Checkbox control");box.addView(radio);box.addView(checkbox);
            Drawable radioIndicator=radio.getButtonDrawable(),checkboxIndicator=checkbox.getButtonDrawable(),radioBackground=radio.getBackground(),checkboxBackground=checkbox.getBackground();
            call(type("UiColors"),"decorate",new Class[]{Context.class,View.class},c,box);
            check(radioIndicator!=null&&radio.getButtonDrawable()==radioIndicator,"radio indicator is not replaced by button styling");check(checkboxIndicator!=null&&checkbox.getButtonDrawable()==checkboxIndicator,"checkbox indicator is not replaced by button styling");
            check(radio.getBackground()==radioBackground,"radio background is not replaced by button styling");check(checkbox.getBackground()==checkboxBackground,"checkbox background is not replaced by button styling");
            for(CompoundButton control:new CompoundButton[]{radio,checkbox}){ColorStateList tint=control.getButtonTintList();check(tint!=null&&tint.getColorForState(new int[]{android.R.attr.state_enabled,android.R.attr.state_checked},0)==0xff246bce,"selection indicator remains blue");check(!control.isChecked(),"choice begins unchecked");control.performClick();check(control.isChecked(),"choice remains operable after styling");}
            checkbox.performClick();check(!checkbox.isChecked(),"checkbox toggles off");radio.performClick();check(radio.isChecked(),"selected radio remains selected");return null;
        });
    }
    /** Invoke the exact crop/back-button helpers without loading images or remote pages. */
    private Activity themedHelper(String name)throws Exception{
        Class<?> component=type(name);Intent intent=new Intent(host,component);
        android.content.pm.ActivityInfo info=host.getPackageManager().getActivityInfo(new ComponentName(host,component),0);
        // Activity.setTheme also themes its Window. Use Instrumentation's attachment
        // path to supply that Window; do not call onCreate (which starts I/O here).
        Activity activity=newActivity(component,host,host.getWindow().getDecorView().getWindowToken(),host.getApplication(),intent,info,name,null,null,null);
        int style=type("R$style").getField("AppTheme").getInt(null);activity.setTheme(style);return activity;
    }
    private void hostFactories()throws Exception{
        List<Probe> probes=new ArrayList<>();AlertDialog dialog=ui(()->{
            LinearLayout box=column(host);
            for(boolean primary:new boolean[]{false,true}){AtomicInteger clicks=new AtomicInteger();TextView view=(TextView)call(host,"button",new Class[]{String.class,boolean.class,Runnable.class},primary?"Primary action":"Secondary action",primary,(Runnable)clicks::incrementAndGet);box.addView(view);probes.add(new Probe(view,primary?"host primary":"host secondary",clicks));}
            Button nativeButton=new Button(host);nativeButton.setText("Default theme button");AtomicInteger nativeClicks=new AtomicInteger();nativeButton.setOnClickListener(v->nativeClicks.incrementAndGet());box.addView(nativeButton);probes.add(new Probe(nativeButton,"host native button",nativeClicks));
            Activity icon=themedHelper("AppIconCropActivity"),background=themedHelper("BackgroundCropActivity"),official=themedHelper("OfficialPageActivity");
            for(Activity helper:new Activity[]{icon,background}){LinearLayout inner=column(helper);AtomicInteger clicks=new AtomicInteger();if(helper==icon)call(helper,"button",new Class[]{LinearLayout.class,int.class,Runnable.class},inner,android.R.string.cancel,(Runnable)clicks::incrementAndGet);else call(helper,"button",new Class[]{LinearLayout.class,String.class,Runnable.class},inner,"Background crop action",(Runnable)clicks::incrementAndGet);TextView button=(TextView)inner.getChildAt(0);box.addView(inner);probes.add(new Probe(button,helper==icon?"icon crop button":"background crop button",clicks));}
            Button back=new Button(official);back.setText("Official-page back action");AtomicInteger backClicks=new AtomicInteger();back.setOnClickListener(v->backClicks.incrementAndGet());call(official,"buttonColor",new Class[]{Button.class},back);box.addView(back);probes.add(new Probe(back,"official-page back button",backClicks));
            return showFixture(host,box,probes,"Host factories");
        });exercise(dialog,probes);ui(()->{dialog.dismiss();return null;});choices(host);
        AtomicInteger accepted=new AtomicInteger();List<Probe> toolProbes=new ArrayList<>();AlertDialog tool=ui(()->{
            AlertDialog.Builder builder=(AlertDialog.Builder)type("ToolDialog").getConstructor(Context.class).newInstance(host);builder.setTitle("Tool dialog").setMessage("Dialog actions retain their styling and behavior").setPositiveButton("Accept",(d,w)->accepted.incrementAndGet()).setNegativeButton("Cancel",null).setNeutralButton("Other",null);
            AlertDialog shown=builder.show();openDialogs.add(shown);for(int which:new int[]{-1,-2,-3})toolProbes.add(new Probe(shown.getButton(which),"ToolDialog button "+which,null));return shown;
        });exercise(tool,toolProbes);ui(()->{tool.getButton(AlertDialog.BUTTON_POSITIVE).performClick();return null;});check(accepted.get()==1&&!tool.isShowing(),"ToolDialog positive action executes and dismisses");
    }
    private void install(String id)throws Exception{
        installedBefore.put(id,(Boolean)call(apps,"installed",new Class[]{String.class},id));try(InputStream input=new FileInputStream("/data/local/tmp/internal-apps/"+id+".ddapp")){call(apps,"install",new Class[]{InputStream.class,String.class,String.class},input,id,null);}
    }
    private void embedded(String id)throws Exception{
        install(id);Intent intent=(Intent)call(type("ModuleRuntime"),"pageIntent",new Class[]{Context.class,String.class,String.class},host,id,"main");child=startActivitySync(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();check(field(child,"page")!=null,id+" production page opens");ClassLoader loader=field(child,"page").getClass().getClassLoader();check(loader!=hostLoader,id+" executes from its installed package");Class<?> uiType=Class.forName("org.daidai.modules.shared.Ui",true,loader);
        desktopLayout(child,id+" main page");
        List<Probe> probes=new ArrayList<>();AlertDialog dialog=ui(()->{
            LinearLayout box=column(child);probes.add(factoryButton(uiType,box,id+" shared action"));
            Button nativeButton=new Button(child);nativeButton.setText("Default app-theme button");AtomicInteger nativeClicks=new AtomicInteger();nativeButton.setOnClickListener(v->nativeClicks.incrementAndGet());box.addView(nativeButton);probes.add(new Probe(nativeButton,id+" native button",nativeClicks));
            if(id.equals("music")){
                Class<?> trackType=Class.forName("org.daidai.modules.music.MusicLibrary$Track",true,loader),creditsType=Class.forName("org.daidai.modules.music.MusicCredits",true,loader),searchType=Class.forName("org.daidai.modules.music.MusicSearchScreen",true,loader);
                Object track=trackType.getConstructor(String.class,String.class,String.class,String.class,String.class,String.class,String.class,String.class,String.class,int.class,boolean.class,long.class,String.class).newInstance("content://qa/not-opened","Fixture song","Singer","Composer","Arranger","","","","120.5",-1,false,60000L,"");AtomicInteger songClicks=new AtomicInteger();Button song=(Button)call(creditsType,"songRow",new Class[]{LinearLayout.class,trackType,String.class,Runnable.class},box,track,"Fixture song",(Runnable)songClicks::incrementAndGet);check(song.getClass()!=Button.class,"production SongButton subclass is covered");probes.add(new Probe(song,"music credit song row",songClicks));
                for(boolean selected:new boolean[]{false,true}){Probe category=factoryButton(uiType,box,selected?"Selected search category":"Unselected search category");call(searchType,"style",new Class[]{Button.class,boolean.class},category.view,selected);probes.add(category);}
            }
            return showFixture(child,box,probes,id+" factories");
        });exercise(dialog,probes);ui(()->{dialog.dismiss();return null;});choices(child);
        if(id.equals("music")){
            List<Probe> progressProbes=new ArrayList<>();ProgressDialog progress=ui(()->{ProgressDialog p=new ProgressDialog(child);p.setTitle("Audio processing");p.setMessage("Working");p.setButton(DialogInterface.BUTTON_NEGATIVE,"Cancel",(d,w)->{});p.show();openDialogs.add(p);progressProbes.add(new Probe(p.getButton(DialogInterface.BUTTON_NEGATIVE),"music progress cancel",null));return p;});exercise(progress,progressProbes);ui(()->{progress.dismiss();return null;});
        }
        ui(()->{child.finish();return null;});child=null;
        Intent settings=new Intent().setClassName(context,"org.daidai.toolbox.InternalAppActivity").putExtra("internal_app",id).putExtra("internal_page","settings").putExtra("settings_mode",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);child=startActivitySync(settings);waitForIdleSync();desktopLayout(child,id+" settings page");ui(()->{child.finish();return null;});child=null;
    }
    private void restorePreferences(){for(Map.Entry<String,Map<String,?>> group:preferences.entrySet()){SharedPreferences.Editor edit=context.getSharedPreferences(group.getKey(),0).edit().clear();for(Map.Entry<String,?> entry:group.getValue().entrySet()){String key=entry.getKey();Object value=entry.getValue();if(value instanceof String)edit.putString(key,(String)value);else if(value instanceof Integer)edit.putInt(key,(Integer)value);else if(value instanceof Long)edit.putLong(key,(Long)value);else if(value instanceof Boolean)edit.putBoolean(key,(Boolean)value);else if(value instanceof Float)edit.putFloat(key,(Float)value);else if(value instanceof Set)edit.putStringSet(key,new HashSet<>((Set<String>)value));}edit.commit();}}
    @Override public void onStart(){Bundle result=new Bundle();int status=Activity.RESULT_OK;try{
        context=getTargetContext();hostLoader=context.getClassLoader();for(String name:new String[]{"toolbox","music_module","messaging_module","colorvision_module"})preferences.put(name,new HashMap<>(context.getSharedPreferences(name,0).getAll()));String language=arguments==null?"zh-CN":arguments.getString("language","zh-CN");context.getSharedPreferences("toolbox",0).edit().putString("app_language",language).putBoolean("outline",true).putInt("outline_color",android.graphics.Color.BLACK).putInt("seen_version",Integer.MAX_VALUE).putInt("last_tab",-1).commit();
        host=startActivitySync(new Intent().setClassName(context,"org.daidai.toolbox.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();apps=field(host,"apps");ui(()->{try{call(host,"switchTab",new Class[]{int.class},0);}catch(Exception failure){throw new RuntimeException(failure);}return null;});desktopLayout(host,"host application page");ui(()->{try{call(host,"switchTab",new Class[]{int.class},-1);}catch(Exception failure){throw new RuntimeException(failure);}return null;});hostFactories();for(String id:new String[]{"music","messaging","colorvision"})embedded(id);result.putString("result","PASS: "+checks+" button elevation, ripple, choice-control and desktop-layout checks ("+language+")");
    }catch(Throwable failure){status=Activity.RESULT_CANCELED;StringWriter text=new StringWriter();failure.printStackTrace(new PrintWriter(text));result.putString("result","FAIL: "+text);}finally{
        try{ui(()->{for(Dialog d:openDialogs)if(d.isShowing())d.dismiss();if(child!=null)child.finish();return null;});if(apps!=null)for(Map.Entry<String,Boolean> entry:installedBefore.entrySet())if(!entry.getValue()){ui(()->{call(apps,"uninstall",new Class[]{String.class},entry.getKey());return null;});long deadline=SystemClock.elapsedRealtime()+12000;while((Boolean)field(apps,"busy")&&SystemClock.elapsedRealtime()<deadline)SystemClock.sleep(30);if((Boolean)field(apps,"busy"))throw new IllegalStateException("Uninstall fixture timeout: "+entry.getKey());}if(host!=null)ui(()->{host.finish();return null;});restorePreferences();}
        catch(Throwable failure){status=Activity.RESULT_CANCELED;result.putString("result",result.getString("result")+"\nFixture restore failed: "+failure);}
        finish(status,result);
    }}
}
