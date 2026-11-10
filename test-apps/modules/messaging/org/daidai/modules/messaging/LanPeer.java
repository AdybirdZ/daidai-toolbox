package org.daidai.modules.messaging;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;

/** One peer per session, UI-independent transport; caller owns lifetime. */
public final class LanPeer implements MessagePeer {
    public static final int PORT=42424;
    public interface Listener extends MessagePeer.Listener {}
    private final MessagePeer.Listener listener;
    private final ExecutorService sender=Executors.newSingleThreadExecutor();
    private volatile ServerSocket server;private volatile Socket socket;private volatile SecureChannel channel;
    private volatile boolean closed,connected;
    public LanPeer(MessagePeer.Listener l){listener=l;}
    public boolean connected(){return connected;}
    public void host(String code){start(true,null,code);}
    public void connect(String host,String code){if(!SecureChannel.isLocalAddress(host))throw new IllegalArgumentException("Enter a private IPv4 address on the same LAN");start(false,host,code);}
    private void start(boolean hosting,String host,String code){SecureChannel.normalize(code);Thread thread=new Thread(()->{
        try{if(hosting){ServerSocket listening=new ServerSocket();server=listening;if(closed){listening.close();return;}listening.setReuseAddress(true);listening.bind(new InetSocketAddress(PORT));listener.state("waiting");
                while(!closed){Socket accepted=listening.accept();socket=accepted;if(closed){accepted.close();break;}
                    if(!SecureChannel.isLocalAddress(accepted.getInetAddress().getHostAddress())){accepted.close();continue;}
                    try{authenticate(accepted,code,true);break;}catch(Exception e){accepted.close();socket=null;if(!closed)listener.state("auth_failed_waiting");}}
                listening.close();server=null;
            }else{Socket outgoing=new Socket();socket=outgoing;if(closed){outgoing.close();return;}listener.state("connecting");outgoing.connect(new InetSocketAddress(host,PORT),10000);authenticate(outgoing,code,false);}
            if(!closed&&channel!=null){connected=true;listener.state("connected");while(!closed)listener.received(channel.readMessage());}
        }catch(Exception e){if(!closed)listener.state(e instanceof javax.crypto.AEADBadTagException?"authentication_failed":"disconnected: "+e.getClass().getSimpleName());}
        finally{close();}
    },"Daidai LAN peer");thread.setDaemon(true);thread.start();}
    private void authenticate(Socket s,String code,boolean hosting)throws Exception{s.setSoTimeout(10000);s.setTcpNoDelay(true);listener.state("authenticating");channel=SecureChannel.establish(s.getInputStream(),s.getOutputStream(),code,hosting);s.setSoTimeout(0);}
    public void send(String text){if(!connected||closed)throw new IllegalStateException("Not connected");if(text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>SecureChannel.MAX_TEXT_BYTES)throw new IllegalArgumentException("Message exceeds 4096 UTF-8 bytes");sender.execute(()->{try{SecureChannel current=channel;if(current==null)return;current.writeMessage(text);listener.sent(text);}catch(Exception e){if(!closed)listener.state("send_failed");close();}});}
    @Override public void close(){closed=true;connected=false;channel=null;try{if(socket!=null)socket.close();}catch(Exception ignored){}try{if(server!=null)server.close();}catch(Exception ignored){}sender.shutdownNow();}
}
