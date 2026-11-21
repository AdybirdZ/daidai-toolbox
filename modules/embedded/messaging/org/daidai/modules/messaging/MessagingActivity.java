package org.daidai.modules.messaging;

import android.Manifest;
import android.app.*;
import android.bluetooth.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.provider.Settings;
import android.text.*;
import android.widget.*;
import org.daidai.modules.shared.*;
import org.simpleweather.app.ModuleRuntime;
import java.net.*;
import java.util.*;

/** Page binds to the session; leaving the page never disconnects an active session. */
public final class MessagingActivity extends org.simpleweather.app.ModulePage {
    public MessagingActivity(Activity activity){super(activity);}
    private static final int BT=201,NOTIFICATIONS=203;
    private MessagingService session;private boolean bound,visible,bluetooth,settingsMode;private String pending;
    private BluetoothAdapter adapter;private BluetoothDevice selected;
    private EditText host,code,message;private TextView status,myCode,device;private LinearLayout chat,lan;private Button send,waitForPeer;
    private HistoryScrollView historyScroll;private List<MessagingService.Row> renderedMessages=new ArrayList<>();
    private final Runnable changed=this::update;
    private final ServiceConnection connection=new ServiceConnection(){
        public void onServiceConnected(ComponentName name,IBinder binder){session=((MessagingService.LocalBinder)binder).session();bluetooth=session.active()?session.bluetooth():getSharedPreferences("messaging_module",0).getBoolean("bluetooth",false);render();if(visible)session.addListener(changed);update();}
        public void onServiceDisconnected(ComponentName name){session=null;update();}
    };
    private String t(String key,Object... args){return Texts.get(activity,"msg_"+key,args);}
    @Override public void onCreate(Bundle state){settingsMode=getIntent().getBooleanExtra("settings_mode",false);if(settingsMode){new org.simpleweather.app.ToolDialog(activity).setTitle(t("settings")).setView(settingsContent(activity)).setNegativeButton(t("close"),null).show();return;}BluetoothManager manager=getSystemService(BluetoothManager.class);adapter=manager==null?null:manager.getAdapter();bluetooth=getSharedPreferences("messaging_module",0).getBoolean("bluetooth",false);render();}
    private void render(){
        LinearLayout page=Ui.page(activity,t("title"),t("subtitle")),body=Ui.scroll(page),mode=Ui.card(body,t("transport"));
        RadioGroup modes=new RadioGroup(activity);modes.setOrientation(0);mode.addView(modes);
        for(int i=0;i<2;i++){RadioButton button=new RadioButton(activity);button.setId(410+i);button.setText(t(i==0?"lan":"bluetooth"));button.setButtonTintList(android.content.res.ColorStateList.valueOf(Ui.BLUE));modes.addView(button,new RadioGroup.LayoutParams(0,-2,1));}
        modes.check(bluetooth?411:410);modes.setOnCheckedChangeListener((g,id)->{stop();bluetooth=id==411;getSharedPreferences("messaging_module",0).edit().putBoolean("bluetooth",bluetooth).apply();render();});
        LinearLayout box=Ui.card(body,t("connect_devices"));myCode=Ui.text(activity,"",18);box.addView(myCode);
        Ui.button(box,t("copy_code"),()->{if(session!=null){ClipData clip=ClipData.newPlainText(t("pairing_code"),session.code());if(Build.VERSION.SDK_INT>=33){PersistableBundle extra=new PersistableBundle();extra.putBoolean("android.content.extra.IS_SENSITIVE",true);clip.getDescription().setExtras(extra);}getSystemService(android.content.ClipboardManager.class).setPrimaryClip(clip);Ui.toast(activity,t("copied"),0).show();}});
        lan=Ui.column(activity);box.addView(lan);lan.setVisibility(bluetooth?android.view.View.GONE:android.view.View.VISIBLE);
        lan.addView(Ui.text(activity,t("local_address",localIps(),LanPeer.PORT),14));host=Ui.input(lan,t("host"),InputType.TYPE_CLASS_PHONE);host.setText(getSharedPreferences("messaging_module",0).getString("last_host",""));
        if(bluetooth){device=Ui.text(activity,selected==null?t("no_device"):deviceName(selected),15);box.addView(device);Ui.button(box,t("choose_device"),this::chooseDevice);}
        code=Ui.input(box,t("enter_code"),InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);code.setFilters(new InputFilter[]{new InputFilter.LengthFilter(24)});
        LinearLayout actions=Ui.row(box);waitForPeer=Ui.cell(actions,t("wait"),()->{if(session!=null&&session.waitingForConnection())stop();else request("wait");});Ui.cell(actions,t("connect"),()->request("connect"));Ui.cell(actions,t("stop"),this::stop);
        status=Ui.text(activity,"",16);box.addView(status);
        LinearLayout conversation=Ui.card(body,t("conversation"));historyScroll=new HistoryScrollView(activity);conversation.addView(historyScroll,new LinearLayout.LayoutParams(-1,Ui.dp(activity,230)));chat=Ui.column(activity);historyScroll.addView(chat);renderedMessages=new ArrayList<>();
        message=Ui.input(conversation,t("message"),InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);message.setMaxLines(4);message.setFilters(new InputFilter[]{new InputFilter.LengthFilter(4096)});
        send=Ui.button(conversation,t("send"),()->{if(session==null)return;String value=message.getText().toString();if(value.trim().isEmpty())return;try{session.send(value);message.setText("");}catch(Exception e){Ui.error(activity,e);}});
        Ui.button(body,t("settings"),this::settings);Ui.button(body,t("guide_title"),()->Ui.note(activity,t("guide_title"),t("guide")));update();
    }
    private void settings(){org.simpleweather.app.SettingsActivity.open(activity,"messaging");}
    public static android.view.View settingsContent(Activity activity){
        LinearLayout box=Ui.column(activity);CheckBox preview=new CheckBox(activity);
        preview.setText(Texts.get(activity,"msg_notification_preview"));preview.setButtonTintList(android.content.res.ColorStateList.valueOf(Ui.BLUE));
        preview.setChecked(activity.getSharedPreferences("messaging_module",0).getBoolean("notification_preview",false));
        preview.setOnCheckedChangeListener((b,on)->activity.getSharedPreferences("messaging_module",0).edit().putBoolean("notification_preview",on).apply());
        box.addView(preview);box.addView(Ui.text(activity,Texts.get(activity,"msg_settings_note"),13));return box;
    }
    private void request(String action){if(session==null)return;pending=action;
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},NOTIFICATIONS);return;}perform();}
    private void perform(){if(pending==null)return;
        if(bluetooth){if(Build.VERSION.SDK_INT>=31&&checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT},BT);return;}if(adapter==null){Ui.note(activity,t("title"),t("no_bluetooth"));pending=null;return;}if(!adapter.isEnabled()){try{startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE),202);}catch(Exception e){Ui.error(activity,e);}return;}if(pending.equals("connect")&&selected==null){chooseDevice();return;}}
        try{Intent intent=ModuleRuntime.serviceIntent(activity,"messaging").setAction(pending).putExtra("bluetooth",bluetooth);
            if(pending.equals("connect")){String pairing=SecureChannel.normalize(code.getText().toString());intent.putExtra("code",pairing);if(bluetooth)intent.putExtra("address",selected.getAddress());else{String address=host.getText().toString().trim();if(!SecureChannel.isLocalAddress(address))throw new IllegalArgumentException(t("invalid_host"));intent.putExtra("address",address);getSharedPreferences("messaging_module",0).edit().putString("last_host",address).apply();}}
            activity.startForegroundService(intent);pending=null;
        }catch(Exception e){pending=null;Ui.error(activity,e);}
    }
    private void chooseDevice(){if(Build.VERSION.SDK_INT>=31&&checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT},BT);return;}if(adapter==null||!adapter.isEnabled()){Ui.note(activity,t("bluetooth"),t("pair_first"));return;}ArrayList<BluetoothDevice> devices=new ArrayList<>(adapter.getBondedDevices());devices.sort(Comparator.comparing(this::deviceName));if(devices.isEmpty()){new AlertDialog.Builder(activity).setMessage(t("pair_first")).setNegativeButton(t("close"),null).setPositiveButton(t("bluetooth_settings"),(d,w)->activity.startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS))).show();return;}String[] names=new String[devices.size()];for(int i=0;i<names.length;i++)names[i]=deviceName(devices.get(i));new AlertDialog.Builder(activity).setTitle(t("choose_device")).setItems(names,(d,n)->{selected=devices.get(n);if(device!=null)device.setText(deviceName(selected));if(pending!=null)perform();}).setNegativeButton(t("close"),null).show();}
    private String deviceName(BluetoothDevice d){try{String name=d.getName();return (name==null?t("unnamed"):name)+" · "+d.getAddress();}catch(SecurityException e){return t("unnamed");}}
    private void stop(){pending=null;if(session!=null)session.disconnect();}
    private void update(){if(status==null)return;if(waitForPeer!=null){waitForPeer.setText(t(session!=null&&session.waitingForConnection()?"stop_wait":"wait"));waitForPeer.setEnabled(session!=null);}status.setText(session==null?t("starting"):session.label());myCode.setText(t("my_code",session==null?"—":session.prettyCode()));send.setEnabled(session!=null&&session.connected());updateMessages(session==null?Collections.emptyList():session.messages());}
    /** Connection-state updates do not rebuild history or interrupt reading older messages. */
    private void updateMessages(List<MessagingService.Row> messages){
        if(renderedMessages.equals(messages))return;
        final LinearLayout target=chat;final HistoryScrollView scroll=historyScroll;
        final int previousY=scroll.getScrollY();boolean atEnd=!scroll.canScrollVertically(1);int removedHeight=0;
        int first=messages.isEmpty()?-1:renderedMessages.indexOf(messages.get(0));
        if(first<0){target.removeAllViews();renderedMessages.clear();}
        else for(int i=0;i<first;i++){removedHeight+=target.getChildAt(0).getHeight();target.removeViewAt(0);renderedMessages.remove(0);}
        // Row identities stay stable in the service, including when its 100-message limit trims the head.
        int common=0;while(common<renderedMessages.size()&&common<messages.size()&&renderedMessages.get(common)==messages.get(common))common++;
        while(target.getChildCount()>common)target.removeViewAt(common);
        for(int i=common;i<messages.size();i++){
            MessagingService.Row row=messages.get(i);String time=new java.text.SimpleDateFormat("HH:mm:ss",Locale.getDefault()).format(new Date(row.time));
            TextView item=Ui.text(activity,t(row.mine?"me":"peer")+" · "+time+"\n"+row.text,16);item.setPadding(Ui.dp(activity,8),Ui.dp(activity,7),Ui.dp(activity,8),Ui.dp(activity,7));item.setBackgroundColor(row.mine?0xffeaf2ff:0xfff2f4f7);target.addView(item);
        }
        renderedMessages=new ArrayList<>(messages);final int retainedY=Math.max(0,previousY-removedHeight);final boolean follow=atEnd;
        target.post(()->{if(chat==target){if(follow)scroll.scrollTo(0,Math.max(0,target.getHeight()-scroll.getHeight()));else scroll.scrollTo(0,retainedY);}});
    }
    private String localIps(){ArrayList<String> ips=new ArrayList<>();try{Enumeration<NetworkInterface> interfaces=NetworkInterface.getNetworkInterfaces();while(interfaces.hasMoreElements()){NetworkInterface n=interfaces.nextElement();if(!n.isUp()||n.isLoopback())continue;Enumeration<InetAddress> addresses=n.getInetAddresses();while(addresses.hasMoreElements()){InetAddress a=addresses.nextElement();if(a instanceof Inet4Address&&SecureChannel.isLocalAddress(a.getHostAddress()))ips.add(a.getHostAddress());}}}catch(Exception ignored){}return ips.isEmpty()?t("no_address"):android.text.TextUtils.join(" / ",ips);}
    @Override protected void onStart(){if(settingsMode)return;bound=activity.bindService(ModuleRuntime.serviceIntent(activity,"messaging"),connection,Context.BIND_AUTO_CREATE);}
    @Override protected void onResume(){visible=true;if(session!=null)session.addListener(changed);}
    @Override protected void onPause(){visible=false;if(session!=null)session.removeListener(changed);}
    @Override protected void onStop(){if(session!=null)session.removeListener(changed);if(bound){activity.unbindService(connection);bound=false;}session=null;}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){if(request==NOTIFICATIONS){perform();return;}if(request==BT&&grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED){if(pending!=null)perform();else chooseDevice();}}
    @Override protected void onActivityResult(int request,int result,Intent data){if(request==202&&result==Activity.RESULT_OK)perform();else if(request==202)pending=null;}
}
