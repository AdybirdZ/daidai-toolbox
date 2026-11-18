package org.daidai.modules.messaging;

import android.app.*;
import android.bluetooth.*;
import android.content.*;
import android.net.wifi.WifiManager;
import android.os.*;
import org.daidai.modules.shared.Texts;
import org.simpleweather.app.ModuleRuntime;
import java.util.*;

/** Owns sockets independently of the window. Session secrets/history are memory-only. */
public final class MessagingService extends org.simpleweather.app.ModuleWorker {
    public MessagingService(Service service,Context context){super(service,context);}
    public final class LocalBinder extends Binder{public MessagingService session(){return MessagingService.this;}}
    public static final class Row{public final boolean mine;public final String text;public final long time;Row(boolean mine,String text){this.mine=mine;this.text=text;time=System.currentTimeMillis();}}
    private final LocalBinder binder=new LocalBinder();private final Handler main=new Handler(Looper.getMainLooper());
    private final ArrayList<Runnable> listeners=new ArrayList<>();private final ArrayDeque<Row> history=new ArrayDeque<>();
    private String ownCode=SecureChannel.newCode(),state="idle";private MessagePeer peer;private boolean bluetooth,foreground,destroyed,hosting;
    private long generation;private PowerManager.WakeLock cpu;private WifiManager.WifiLock wifi;
    private String t(String key,Object... args){return Texts.get(this,"msg_"+key,args);}
    @Override public void onCreate(){NotificationManager nm=getSystemService(NotificationManager.class);nm.createNotificationChannel(new NotificationChannel("messaging_connection",t("connection_channel"),NotificationManager.IMPORTANCE_LOW));nm.createNotificationChannel(new NotificationChannel("messaging_incoming",t("message_channel"),NotificationManager.IMPORTANCE_DEFAULT));}
    @Override public IBinder onBind(Intent intent){return binder;}
    @Override public int onStartCommand(Intent intent,int flags,int id){if(intent==null)return START_NOT_STICKY;if("stop".equals(intent.getAction())){disconnect();return START_NOT_STICKY;}
        generation++;if(peer!=null){peer.close();peer=null;}release(false);bluetooth=intent.getBooleanExtra("bluetooth",false);hosting="wait".equals(intent.getAction());state="connecting";foreground=true;startForeground(240010,notification());long token=++generation;
        try{PowerManager pm=getSystemService(PowerManager.class);cpu=pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,getPackageName()+":message-session");cpu.setReferenceCounted(false);cpu.acquire();
            if(!bluetooth){WifiManager wm=(WifiManager)getSystemService(Context.WIFI_SERVICE);if(wm!=null){wifi=wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF,"Daidai message session");wifi.setReferenceCounted(false);wifi.acquire();}}
            MessagePeer.Listener listener=new MessagePeer.Listener(){public void state(String value){main.post(()->{if(token!=generation||destroyed)return;state=value;changed();if(!active())release();});}public void received(String value){main.post(()->{if(token==generation&&!destroyed)add(false,value);});}public void sent(String value){main.post(()->{if(token==generation&&!destroyed)add(true,value);});}};
            boolean waiting=hosting;String code=waiting?ownCode:SecureChannel.normalize(intent.getStringExtra("code"));
            if(bluetooth){BluetoothManager manager=getSystemService(BluetoothManager.class);BluetoothAdapter adapter=manager==null?null:manager.getAdapter();if(adapter==null)throw new IllegalStateException("Bluetooth unavailable");BluetoothPeer p=new BluetoothPeer(adapter,listener);peer=p;if(waiting)p.host(code);else p.connect(adapter.getRemoteDevice(intent.getStringExtra("address")),code);}
            else{LanPeer p=new LanPeer(listener);peer=p;if(waiting)p.host(code);else p.connect(intent.getStringExtra("address"),code);}
        }catch(Exception e){state="failed";if(peer!=null){peer.close();peer=null;}release();changed();}return START_NOT_STICKY;
    }
    public String code(){return ownCode;}public String prettyCode(){return ownCode.substring(0,4)+"-"+ownCode.substring(4,8)+"-"+ownCode.substring(8,12)+"-"+ownCode.substring(12);}
    public boolean waitingForConnection(){return hosting&&active()&&!connected();}
    public boolean bluetooth(){return bluetooth;}public boolean connected(){return peer!=null&&peer.connected();}public boolean active(){return state.equals("connecting")||state.equals("waiting")||state.equals("authenticating")||state.equals("connected")||state.equals("auth_failed_waiting");}
    public String label(){if(state.equals("idle"))return t("disconnected");if(state.equals("connected"))return t(bluetooth?"connected_bluetooth":"connected_lan");if(state.equals("waiting")||state.equals("auth_failed_waiting"))return t("waiting");if(state.equals("authenticating"))return t("authenticating");if(state.equals("connecting"))return t("connecting");return t("connection_failed");}
    public List<Row> messages(){return new ArrayList<>(history);}public void addListener(Runnable r){if(!listeners.contains(r))listeners.add(r);r.run();}public void removeListener(Runnable r){listeners.remove(r);}
    public void send(String text){if(!connected())return;if(text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>4096)throw new IllegalArgumentException(t("message_too_long"));peer.send(text);}
    private void add(boolean mine,String value){history.addLast(new Row(mine,value));while(history.size()>100)history.removeFirst();if(!mine&&listeners.isEmpty()){boolean preview=getSharedPreferences("messaging_module",0).getBoolean("notification_preview",false);Notification note=new Notification.Builder(this,"messaging_incoming").setSmallIcon(android.R.drawable.ic_dialog_email).setContentTitle(t("title")).setContentText(preview?value:t("new_message")).setVisibility(preview?Notification.VISIBILITY_PRIVATE:Notification.VISIBILITY_SECRET).setContentIntent(open()).setAutoCancel(true).build();try{getSystemService(NotificationManager.class).notify(240011,note);}catch(SecurityException ignored){}}changed();}
    private PendingIntent open(){return PendingIntent.getActivity(this,240010,ModuleRuntime.pageIntent(this,"messaging","main"),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);}
    private Notification notification(){PendingIntent stop=PendingIntent.getService(this,240010,ModuleRuntime.serviceIntent(this,"messaging").setAction("stop"),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);return new Notification.Builder(this,"messaging_connection").setSmallIcon(android.R.drawable.stat_notify_chat).setContentTitle(t("title")).setContentText(label()).setContentIntent(open()).setOngoing(true).setOnlyAlertOnce(true).setVisibility(Notification.VISIBILITY_PRIVATE).addAction(new Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel,t(waitingForConnection()?"stop_wait":"stop"),stop).build()).build();}
    private void changed(){if(destroyed)return;if(foreground)try{getSystemService(NotificationManager.class).notify(240010,notification());}catch(SecurityException ignored){}for(Runnable r:new ArrayList<>(listeners))r.run();}
    private void release(){release(true);}
    private void release(boolean stop){if(wifi!=null){if(wifi.isHeld())wifi.release();wifi=null;}if(cpu!=null){if(cpu.isHeld())cpu.release();cpu=null;}if(foreground){stopForeground(true);foreground=false;}if(stop)stopSelf();}
    public void disconnect(){generation++;if(peer!=null){peer.close();peer=null;}state="idle";release();changed();}
    @Override public void onDestroy(){disconnect();destroyed=true;listeners.clear();history.clear();ownCode="";main.removeCallbacksAndMessages(null);}
}
