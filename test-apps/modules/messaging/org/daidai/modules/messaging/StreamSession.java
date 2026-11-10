package org.daidai.modules.messaging;

import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Transport-neutral encrypted session. Closing cancels blocked Bluetooth I/O and deadlines. */
public final class StreamSession implements MessagePeer {
    public interface Link extends AutoCloseable {
        InputStream input() throws IOException;
        OutputStream output() throws IOException;
        @Override void close() throws IOException;
    }
    private final Listener listener;
    private final ExecutorService sender=Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService timer=Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean closed=new AtomicBoolean();
    private volatile boolean connected;
    private volatile Link link;
    private volatile SecureChannel channel;

    public StreamSession(Listener listener){this.listener=listener;}
    @Override public boolean connected(){return connected&&!closed.get();}

    /** Called by the transport worker after it has connected. */
    public void run(Link connection,String code,boolean server,int handshakeSeconds){
        link=connection;
        if(closed.get()){closeLink(connection);return;}
        Future<?> timeout;
        try{timeout=timer.schedule(()->fail("authentication_timeout"),handshakeSeconds,TimeUnit.SECONDS);}
        catch(RejectedExecutionException e){closeLink(connection);return;}
        try{
            notifyState("authenticating");
            SecureChannel authenticated=SecureChannel.establish(connection.input(),connection.output(),code,server);
            timeout.cancel(false);
            synchronized(this){
                if(closed.get())return;
                channel=authenticated;connected=true;
                listener.state("connected");
            }
            while(!closed.get()){
                String message=authenticated.readMessage();
                synchronized(this){if(!closed.get())listener.received(message);}
            }
        }catch(Exception e){
            fail(e instanceof javax.crypto.AEADBadTagException?"authentication_failed":"disconnected");
        }finally{timeout.cancel(false);close();}
    }
    private synchronized void notifyState(String state){if(!closed.get())listener.state(state);}
    private void fail(String state){
        synchronized(this){if(closed.get())return;listener.state(state);close();}
    }
    @Override public void send(String text){
        if(!connected())throw new IllegalStateException("Not connected");
        int length=text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        if(length==0||length>SecureChannel.MAX_TEXT_BYTES)throw new IllegalArgumentException("Message: 1–4096 UTF-8 bytes");
        try{sender.execute(()->{
            try{SecureChannel current=channel;if(current==null||closed.get())return;current.writeMessage(text);
                synchronized(this){if(!closed.get())listener.sent(text);}
            }catch(Exception e){fail("send_failed");}
        });}catch(RejectedExecutionException e){throw new IllegalStateException("Not connected");}
    }
    private static void closeLink(Link link){try{link.close();}catch(Exception ignored){}}
    @Override public synchronized void close(){
        if(!closed.compareAndSet(false,true))return;
        connected=false;channel=null;
        Link current=link;if(current!=null)closeLink(current);
        sender.shutdownNow();timer.shutdownNow();
    }
}
