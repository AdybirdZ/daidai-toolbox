package org.daidai.modules.messaging;

import android.Manifest;
import android.app.*;
import android.bluetooth.*;
import android.os.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.text.*;
import android.view.View;
import android.widget.*;
import org.daidai.modules.shared.Ui;
import java.net.*;
import java.util.*;

public class MessagingActivity extends Activity {
    private static final int BT_PERMISSION=201,ENABLE_BT=202,WAIT=1,CHOOSE=2,CONNECT=3;
    private MessagePeer peer;
    private TextView status,addresses,bluetoothStatus,selectedLabel;
    private EditText host,code,message;
    private LinearLayout chat,lanDetails,bluetoothDetails,hostInput;
    private ScrollView chatScroll;
    private Button send;
    private BluetoothAdapter adapter;
    private BluetoothDevice selectedDevice;
    private boolean bluetooth,active;
    private int pendingAction;
    private final String ownCode=SecureChannel.newCode();
    private long generation;

    @Override public void onCreate(Bundle b){
        super.onCreate(b);BluetoothManager manager=getSystemService(BluetoothManager.class);
        adapter=manager==null?null:manager.getAdapter();
        bluetooth=getSharedPreferences("messaging_module",0).getBoolean("bluetooth",false);render();
    }
    private String version(){try{return getPackageManager().getPackageInfo(getPackageName(),0).versionName;}catch(Exception e){return "0.2.0";}}
    private void render(){
        LinearLayout page=Ui.page(this,Ui.t("呆呆传话","Daidai Messenger"),Ui.t("独立测试版 ","Standalone test version ")+version()+Ui.t(" · 蓝牙 / 局域网 · 一对一文字"," · Bluetooth / LAN · One-to-one text"));
        LinearLayout scroll=Ui.scroll(page),transport=Ui.card(scroll,Ui.t("通信方式","Transport"));
        RadioGroup modes=new RadioGroup(this);modes.setOrientation(0);
        String[] labels={Ui.t("局域网","LAN"),Ui.t("蓝牙","Bluetooth")};
        for(int i=0;i<2;i++){RadioButton r=new RadioButton(this);r.setId(101+i);r.setText(labels[i]);r.setTextSize(16);r.setTextColor(Ui.INK);modes.addView(r,new LinearLayout.LayoutParams(0,-2,1));}
        modes.check(bluetooth?102:101);transport.addView(modes);
        transport.addView(Ui.text(this,Ui.t("切换通信方式会断开当前连接。","Switching transport disconnects the current session."),13));
        LinearLayout box=Ui.card(scroll,Ui.t("建立连接","Connect devices"));
        lanDetails=Ui.column(this);box.addView(lanDetails);addresses=Ui.text(this,"",14);addresses.setTextIsSelectable(true);lanDetails.addView(addresses);
        bluetoothDetails=Ui.column(this);box.addView(bluetoothDetails);bluetoothStatus=Ui.text(this,"",14);bluetoothDetails.addView(bluetoothStatus);
        bluetoothDetails.addView(Ui.text(this,Ui.t("先在系统蓝牙设置中将两台 Android 手机配对，再返回本程序。一台等待连接，另一台选择前者并输入前者的配对码。","Pair two Android phones in system Bluetooth settings first. Then one waits here; the other selects it and enters its code."),14));
        LinearLayout devices=Ui.row(bluetoothDetails);
        Ui.cell(devices,Ui.t("选择已配对手机","Choose paired phone"),()->requestBluetooth(CHOOSE));
        Ui.cell(devices,Ui.t("系统蓝牙设置","Bluetooth settings"),this::openBluetoothSettings);
        selectedLabel=Ui.text(this,Ui.t("未选择对方手机","No peer phone selected"),15);bluetoothDetails.addView(selectedLabel);
        TextView codeLabel=Ui.text(this,Ui.t("本机配对码：","My pairing code: ")+pretty(ownCode),16);codeLabel.setTextIsSelectable(true);box.addView(codeLabel);
        LinearLayout receiveActions=Ui.row(box);
        Ui.cell(receiveActions,Ui.t("等待另一台连接","Wait for peer"),this::listen);
        Ui.cell(receiveActions,Ui.t("复制配对码","Copy code"),()->{
            ClipData clip=ClipData.newPlainText("Daidai pairing code",ownCode);
            if(Build.VERSION.SDK_INT>=33){PersistableBundle extra=new PersistableBundle();extra.putBoolean("android.content.extra.IS_SENSITIVE",true);clip.getDescription().setExtras(extra);}
            getSystemService(android.content.ClipboardManager.class).setPrimaryClip(clip);
            Toast.makeText(this,Ui.t("已复制，请通过可信方式告知另一台设备","Copied; share privately with your peer"),0).show();
        });
        hostInput=Ui.column(this);box.addView(hostInput);
        host=Ui.input(hostInput,Ui.t("对方局域网 IPv4，例如 192.168.1.23","Peer private IPv4, e.g. 192.168.1.23"),InputType.TYPE_CLASS_PHONE);
        host.setText(getSharedPreferences("messaging_module",0).getString("last_host",""));
        code=Ui.input(box,Ui.t("输入对方的 16 位配对码","Enter peer's 16-character pairing code"),InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);code.setFilters(new InputFilter[]{new InputFilter.LengthFilter(24)});
        LinearLayout actions=Ui.row(box);
        Ui.cell(actions,Ui.t("连接对方","Connect to peer"),this::connect);
        Ui.cell(actions,Ui.t("断开","Disconnect"),()->{pendingAction=0;disconnect();setState("idle");});
        status=Ui.text(this,"",16);box.addView(status);setState("idle");
        LinearLayout conversation=Ui.card(scroll,Ui.t("本次会话","Current conversation"));
        conversation.addView(Ui.text(this,Ui.t("仅保留最近 100 条消息；结束此界面后清除。","Last 100 messages in memory; cleared when this screen ends."),13));
        chatScroll=new ScrollView(this);conversation.addView(chatScroll,new LinearLayout.LayoutParams(-1,Ui.dp(this,220)));chat=Ui.column(this);chatScroll.addView(chat);
        message=Ui.input(conversation,Ui.t("输入消息，最多 4096 个 UTF-8 字节","Message, up to 4096 UTF-8 bytes"),InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);message.setMaxLines(4);message.setFilters(new InputFilter[]{new InputFilter.LengthFilter(4096)});
        send=Ui.button(conversation,Ui.t("发送","Send"),()->{if(peer==null||!peer.connected())return;String text=message.getText().toString();if(text.trim().isEmpty())return;try{peer.send(text);message.setText("");}catch(Exception e){Ui.error(this,e);}});send.setEnabled(false);
        Ui.button(scroll,Ui.t("使用说明与隐私","Guide and privacy"),this::guide);
        modes.setOnCheckedChangeListener((group,id)->{
            pendingAction=0;disconnect();bluetooth=id==102;
            getSharedPreferences("messaging_module",0).edit().putBoolean("bluetooth",bluetooth).apply();
            chat.removeAllViews();message.setText("");code.setText("");updateTransport();setState("idle");
        });updateTransport();
    }
    private void updateTransport(){
        lanDetails.setVisibility(bluetooth?View.GONE:View.VISIBLE);hostInput.setVisibility(bluetooth?View.GONE:View.VISIBLE);bluetoothDetails.setVisibility(bluetooth?View.VISIBLE:View.GONE);
        addresses.setText(Ui.t("本机局域网地址：","Local IPv4: ")+localIps()+"\n"+Ui.t("端口：","Port: ")+LanPeer.PORT);refreshBluetooth();
    }
    private boolean bluetoothPermission(){return Build.VERSION.SDK_INT<31||checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED;}
    private void refreshBluetooth(){
        if(bluetoothStatus==null)return;
        if(adapter==null){bluetoothStatus.setText(Ui.t("此设备不支持经典蓝牙，请使用局域网方式。","Classic Bluetooth unavailable; use LAN instead."));return;}
        if(!bluetoothPermission()){bluetoothStatus.setText(Ui.t("使用时需要允许“附近的设备”权限；无需定位权限。","Allow Nearby devices when prompted; location permission is not needed."));return;}
        try{
            String name=adapter.getName();bluetoothStatus.setText(adapter.isEnabled()?Ui.t("蓝牙已开启 · 本机：","Bluetooth on · This phone: ")+(name==null?Ui.t("未命名设备","Unnamed device"):name):Ui.t("蓝牙未开启，点击连接相关按钮后可请求开启。","Bluetooth is off. A connection action will ask to turn it on."));
            if(selectedDevice!=null&&selectedDevice.getBondState()!=BluetoothDevice.BOND_BONDED){selectedDevice=null;selectedLabel.setText(Ui.t("配对已取消，请重新选择手机。","Pairing removed; choose a phone again."));}
        }catch(SecurityException e){bluetoothStatus.setText(Ui.t("蓝牙权限不可用，请重新允许“附近的设备”。","Bluetooth permission unavailable. Allow Nearby devices again."));}
    }
    private void requestBluetooth(int action){
        if(!active||!bluetooth)return;
        if(adapter==null){setState("bluetooth_unavailable");return;}
        pendingAction=action;
        if(!bluetoothPermission()){
            if(shouldShowRequestPermissionRationale(Manifest.permission.BLUETOOTH_CONNECT)){
                new AlertDialog.Builder(this).setTitle(Ui.t("蓝牙权限","Bluetooth permission"))
                    .setMessage(Ui.t("连接已配对手机需要“附近的设备”权限，不需要定位或扫描权限。","Nearby devices permission is needed to connect paired phones. No location or scan permission is required."))
                    .setNegativeButton(Ui.t("取消","Cancel"),(d,w)->pendingAction=0)
                    .setPositiveButton(Ui.t("继续","Continue"),(d,w)->requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT},BT_PERMISSION)).show();
            }else requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT},BT_PERMISSION);return;
        }
        try{
            if(!adapter.isEnabled()){startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE),ENABLE_BT);return;}
            pendingAction=0;refreshBluetooth();if(action==CHOOSE)chooseDevice();else if(action==WAIT)startBluetoothHost();else startBluetoothConnection();
        }catch(Exception e){pendingAction=0;disconnect();setState("bluetooth_connection_failed");Ui.error(this,e);}
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){
        super.onRequestPermissionsResult(request,permissions,grants);if(request!=BT_PERMISSION)return;refreshBluetooth();
        if(grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED){int action=pendingAction;if(action!=0)requestBluetooth(action);}
        else{
            pendingAction=0;setState("bluetooth_permission_denied");
            if(!shouldShowRequestPermissionRationale(Manifest.permission.BLUETOOTH_CONNECT))new AlertDialog.Builder(this)
                .setMessage(Ui.t("如需使用蓝牙，请在应用权限设置中允许“附近的设备”。也可继续使用局域网。","To use Bluetooth, allow Nearby devices in app settings. LAN remains available."))
                .setNegativeButton(Ui.t("关闭","Close"),null).setPositiveButton(Ui.t("应用权限设置","App settings"),(d,w)->startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:"+getPackageName())))).show();
        }
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);if(request==ENABLE_BT){if(result!=RESULT_OK){pendingAction=0;setState("bluetooth_off");}}
    }
    private void chooseDevice(){
        ArrayList<BluetoothDevice> devices=new ArrayList<>(adapter.getBondedDevices());devices.sort(Comparator.comparing(this::deviceName));
        if(devices.isEmpty()){
            new AlertDialog.Builder(this).setTitle(Ui.t("还没有已配对设备","No paired devices"))
                .setMessage(Ui.t("请先打开系统蓝牙设置，将两台 Android 手机配对；完成后返回并重新选择。","Pair two Android phones in system Bluetooth settings, then return and choose again."))
                .setNegativeButton(Ui.t("取消","Cancel"),null).setPositiveButton(Ui.t("系统蓝牙设置","Bluetooth settings"),(d,w)->openBluetoothSettings()).show();return;
        }
        String[] names=new String[devices.size()];for(int i=0;i<names.length;i++)names[i]=deviceName(devices.get(i));
        new AlertDialog.Builder(this).setTitle(Ui.t("选择另一台运行本程序的手机","Choose another phone running this app"))
            .setItems(names,(d,which)->{disconnect();selectedDevice=devices.get(which);selectedLabel.setText(Ui.t("对方手机：","Peer phone: ")+deviceName(selectedDevice));setState("idle");})
            .setNegativeButton(Ui.t("取消","Cancel"),null).show();
    }
    private String deviceName(BluetoothDevice device){String name=device.getName();return (name==null||name.trim().isEmpty()?Ui.t("未命名设备","Unnamed device"):name)+"\n"+device.getAddress();}
    private void openBluetoothSettings(){pendingAction=0;try{startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));}catch(Exception e){Ui.error(this,e);}}
    private void startBluetoothHost(){disconnect();BluetoothPeer p=new BluetoothPeer(adapter,listener());peer=p;p.host(ownCode);}
    private void startBluetoothConnection(){if(selectedDevice==null){chooseDevice();return;}String pairing=pairingCode();disconnect();BluetoothPeer p=new BluetoothPeer(adapter,listener());peer=p;p.connect(selectedDevice,pairing);}
    private String pairingCode(){try{return SecureChannel.normalize(code.getText().toString());}catch(IllegalArgumentException e){throw new IllegalArgumentException(Ui.t("请输入对方的 16 位配对码，可包含分隔短横线。","Enter the peer's 16-character pairing code; hyphens are allowed."));}}
    private String pretty(String code){return code.substring(0,4)+"-"+code.substring(4,8)+"-"+code.substring(8,12)+"-"+code.substring(12);}
    private String localIps(){ArrayList<String> ips=new ArrayList<>();try{Enumeration<NetworkInterface> interfaces=NetworkInterface.getNetworkInterfaces();while(interfaces.hasMoreElements()){NetworkInterface n=interfaces.nextElement();if(!n.isUp()||n.isLoopback())continue;Enumeration<InetAddress> addresses=n.getInetAddresses();while(addresses.hasMoreElements()){InetAddress a=addresses.nextElement();String value=a.getHostAddress();if(a instanceof Inet4Address&&SecureChannel.isLocalAddress(value))ips.add(value);}}}catch(Exception ignored){}return ips.isEmpty()?Ui.t("未找到，请先连接 Wi-Fi 或热点","Not found; join Wi-Fi / hotspot first"):android.text.TextUtils.join(" / ",ips);}
    private MessagePeer.Listener listener(){final long token=++generation;return new MessagePeer.Listener(){
        public void state(String s){runOnUiThread(()->{if(generation==token&&!isDestroyed())setState(s);});}
        public void received(String text){runOnUiThread(()->{if(generation==token&&!isDestroyed())addMessage(false,text);});}
        public void sent(String text){runOnUiThread(()->{if(generation==token&&!isDestroyed())addMessage(true,text);});}
    };}
    private void listen(){try{if(bluetooth){requestBluetooth(WAIT);return;}disconnect();LanPeer p=new LanPeer(listener());peer=p;p.host(ownCode);}catch(Exception e){disconnect();setState("idle");Ui.error(this,e);}}
    private void connect(){try{if(bluetooth){requestBluetooth(CONNECT);return;}String address=host.getText().toString().trim(),pairing=pairingCode();if(!SecureChannel.isLocalAddress(address))throw new IllegalArgumentException(Ui.t("请输入同一局域网的私有 IPv4 地址","Enter a private IPv4 on the same LAN"));disconnect();LanPeer p=new LanPeer(listener());peer=p;p.connect(address,pairing);getSharedPreferences("messaging_module",0).edit().putString("last_host",address).apply();}catch(Exception e){disconnect();setState("idle");Ui.error(this,e);}}
    private void disconnect(){generation++;if(peer!=null){peer.close();peer=null;}if(send!=null)send.setEnabled(false);}
    private void addMessage(boolean mine,String text){String time=new java.text.SimpleDateFormat("HH:mm:ss",Locale.getDefault()).format(new Date());TextView item=Ui.text(this,(mine?Ui.t("我","Me"):Ui.t("对方","Peer"))+" · "+time+"\n"+text,16);item.setTextIsSelectable(true);item.setPadding(Ui.dp(this,8),Ui.dp(this,7),Ui.dp(this,8),Ui.dp(this,7));item.setBackgroundColor(mine?0xffeaf2ff:0xfff2f4f7);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,Ui.dp(this,5),0,Ui.dp(this,5));chat.addView(item,p);while(chat.getChildCount()>100)chat.removeViewAt(0);chatScroll.post(()->chatScroll.fullScroll(android.view.View.FOCUS_DOWN));}
    private void setState(String s){String label;switch(s){
        case "idle":label=Ui.t("未连接","Disconnected");break;
        case "waiting":label=bluetooth?Ui.t("蓝牙正在等待另一台手机连接…","Bluetooth waiting for peer…"):Ui.t("正在等待另一台设备…","Waiting for peer…");break;
        case "connecting":label=Ui.t("正在连接…","Connecting…");break;
        case "authenticating":label=Ui.t("正在验证配对码…","Verifying pairing code…");break;
        case "connected":label=bluetooth?Ui.t("蓝牙已连接 · 加密会话","Bluetooth connected · Encrypted session"):Ui.t("已连接 · 加密会话","Connected · Encrypted session");break;
        case "auth_failed_waiting":label=Ui.t("配对验证失败，继续等待；请核对配对码。","Authentication failed; still waiting. Check the code.");break;
        case "authentication_failed":label=Ui.t("配对码验证失败。请核对配对码，并让接收方重新点击等待连接。","Code verification failed. Check the code and ask the receiver to wait again.");break;
        case "authentication_timeout":label=Ui.t("配对验证超时。请确认对方运行本程序，并重新连接。","Verification timed out. Check that the peer runs this app, then reconnect.");break;
        case "connection_timeout":label=Ui.t("蓝牙连接超时。请确认附近的对方手机已点击等待连接。","Bluetooth connection timed out. Make sure the nearby peer is waiting in this app.");break;
        case "bluetooth_unavailable":label=Ui.t("此设备不支持经典蓝牙，请使用局域网方式。","Classic Bluetooth unavailable; use LAN instead.");break;
        case "bluetooth_off":label=Ui.t("蓝牙未开启；仍可使用局域网方式。","Bluetooth is off; LAN remains available.");break;
        case "bluetooth_permission_denied":label=Ui.t("未允许“附近的设备”权限，无法使用蓝牙；仍可使用局域网。","Nearby devices permission denied; LAN remains available.");break;
        case "bluetooth_connection_failed":label=Ui.t("蓝牙连接失败或已断开。请检查系统配对、权限及对方是否正在等待连接。","Bluetooth connection failed or closed. Check system pairing, permission and that the peer is waiting.");break;
        case "send_failed":label=Ui.t("发送失败，连接已关闭。","Send failed. Connection closed.");break;
        default:label=Ui.t("连接已断开或未能建立，请检查对方设备、配对码和通信设置。","Connection closed or failed. Check the peer, pairing code and transport settings.");}
        status.setText(label);if(send!=null)send.setEnabled(s.equals("connected"));
    }
    private void guide(){Ui.note(this,Ui.t("传话说明与隐私","Messaging guide and privacy"),Ui.t(
        "蓝牙：先在系统蓝牙设置中将两台 Android 手机配对。返回本程序并选择蓝牙，一台点击等待连接；另一台选择前者，输入前者在本程序里的 16 位配对码后连接。系统蓝牙配对确认与本程序配对码是两件事。无需联网，不支持与 iPhone 的普通经典蓝牙互通。\n\n局域网：两台设备接入同一 Wi-Fi 或热点，一台等待连接，另一台输入前者的地址及配对码连接。路由器的客户端隔离可能阻止连接。\n\n两种方式都使用配对码与 AES-256-GCM 校验消息，无云服务器。请勿公开配对码。协议为原型，尚未独立安全审计。\n\n切到后台、打开系统设置或切换通信方式会断开连接。一次仅连接一台设备，不提供后台收信。只保存上次局域网地址与所选通信方式，不保存蓝牙设备地址、配对码或聊天记录。当前界面内最多保留 100 条消息。",
        "Bluetooth: Pair two Android phones in system Bluetooth settings first. Return here and choose Bluetooth. One waits; the other selects it and enters its 16-character code shown in this app. System Bluetooth pairing and the app code are separate. No Internet required. Standard iPhone classic Bluetooth chat is not supported.\n\nLAN: Join the same Wi-Fi/hotspot. One waits; the other enters its address and code. Router client isolation can prevent connections.\n\nBoth transports use a code and AES-256-GCM to authenticate messages. No cloud server. Keep the code private. Prototype protocol; not independently audited.\n\nBackgrounding, opening system settings or switching transport disconnects. One peer; no background receiving. Only the last LAN address and transport preference are saved. No saved Bluetooth address, code or chat history. At most 100 messages stay in this screen's memory."));}
    @Override protected void onResume(){super.onResume();active=true;refreshBluetooth();if(pendingAction!=0&&bluetoothPermission()){int action=pendingAction;pendingAction=0;requestBluetooth(action);}}
    @Override protected void onPause(){active=false;disconnect();if(status!=null)setState("idle");super.onPause();}
    @Override protected void onStop(){active=false;disconnect();if(status!=null)setState("idle");super.onStop();}
}
