package org.daidai.modules.messaging;

import android.bluetooth.*;
import java.io.*;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Secure classic Bluetooth RFCOMM, paired Android devices, no BLE/scan/cloud dependency. */
public final class BluetoothPeer implements MessagePeer {
    public static final UUID SERVICE_UUID=UUID.fromString("5f917b67-4f75-4af0-b2ca-a73c471a20ee");
    private final BluetoothAdapter adapter;
    private final Listener listener;
    private final StreamSession session;
    private final ScheduledExecutorService timer=Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean closed=new AtomicBoolean();
    private volatile BluetoothServerSocket server;
    private volatile BluetoothSocket socket;

    public BluetoothPeer(BluetoothAdapter adapter,Listener listener){
        if(adapter==null)throw new IllegalArgumentException("Bluetooth unavailable");
        this.adapter=adapter;this.listener=listener;
        session=new StreamSession(new Listener(){
            public void state(String s){if(!closed.get())listener.state(s);}
            public void received(String t){if(!closed.get())listener.received(t);}
            public void sent(String t){if(!closed.get())listener.sent(t);}
        });
    }
    @Override public boolean connected(){return !closed.get()&&session.connected();}
    public void host(String code){SecureChannel.normalize(code);worker(true,null,code);}
    public void connect(BluetoothDevice device,String code){
        SecureChannel.normalize(code);
        if(device==null||device.getBondState()!=BluetoothDevice.BOND_BONDED)throw new IllegalArgumentException("Pair the devices in system Bluetooth settings first");
        worker(false,device,code);
    }
    private void worker(boolean hosting,BluetoothDevice device,String code){
        Thread thread=new Thread(()->{
            Future<?> timeout=null;
            try{
                if(hosting){
                    BluetoothServerSocket listening=adapter.listenUsingRfcommWithServiceRecord("Daidai Messenger",SERVICE_UUID);
                    server=listening;
                    if(closed.get()){listening.close();return;}
                    notifyState("waiting");
                    BluetoothSocket accepted=listening.accept();socket=accepted;
                    listening.close();server=null;
                    if(closed.get()){accepted.close();return;}
                }else{
                    BluetoothSocket outgoing=device.createRfcommSocketToServiceRecord(SERVICE_UUID);socket=outgoing;
                    if(closed.get()){outgoing.close();return;}
                    notifyState("connecting");
                    timeout=timer.schedule(()->fail("connection_timeout"),25,TimeUnit.SECONDS);
                    outgoing.connect();timeout.cancel(false);
                }
                final BluetoothSocket current=socket;
                if(closed.get()||current==null)return;
                session.run(new StreamSession.Link(){
                    public InputStream input()throws IOException{return current.getInputStream();}
                    public OutputStream output()throws IOException{return current.getOutputStream();}
                    public void close()throws IOException{current.close();}
                },code,hosting,15);
            }catch(SecurityException e){fail("bluetooth_permission_denied");}
            catch(Exception e){fail("bluetooth_connection_failed");}
            finally{if(timeout!=null)timeout.cancel(false);close();}
        },"Daidai Bluetooth peer");thread.setDaemon(true);thread.start();
    }
    private synchronized void notifyState(String state){if(!closed.get())listener.state(state);}
    private synchronized void fail(String state){if(!closed.get()){listener.state(state);close();}}
    @Override public void send(String text){session.send(text);}
    @Override public synchronized void close(){
        if(!closed.compareAndSet(false,true))return;
        session.close();
        try{if(socket!=null)socket.close();}catch(Exception ignored){}
        try{if(server!=null)server.close();}catch(Exception ignored){}
        timer.shutdownNow();
    }
}
