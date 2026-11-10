import org.daidai.modules.messaging.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Exercises the exact byte-stream session used by Bluetooth, without requiring a radio. */
public final class StreamSessionChecks {
    static int checks;
    static void ok(boolean value,String label){if(!value)throw new AssertionError(label);checks++;}
    interface Checked{void run()throws Exception;}
    static void fails(Checked task,String label)throws Exception{try{task.run();}catch(Exception expected){checks++;return;}throw new AssertionError(label);}
    static final class Pipe extends InputStream {
        final BlockingQueue<Integer> bytes=new LinkedBlockingQueue<>();volatile boolean closed;
        void put(int value)throws IOException{if(closed)throw new IOException("Closed");bytes.add(value&255);}
        @Override public int read()throws IOException{if(closed&&bytes.isEmpty())return -1;try{return bytes.take();}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException(e);}}
        @Override public int read(byte[] out,int off,int length)throws IOException{
            if(length==0)return 0;int v=read();if(v<0)return -1;out[off]=(byte)v;
            // Deliberately return tiny chunks, unlike a TCP or RFCOMM packet boundary.
            int n=1;while(n<Math.min(3,length)){Integer next=bytes.poll();if(next==null)break;if(next<0){bytes.add(-1);break;}out[off+n++]=(byte)(int)next;}return n;
        }
        @Override public void close(){if(!closed){closed=true;bytes.add(-1);}}
    }
    static final class Link implements StreamSession.Link {
        final Pipe input,other;volatile boolean closed;
        Link(Pipe input,Pipe other){this.input=input;this.other=other;}
        public InputStream input(){return input;}
        public OutputStream output(){return new OutputStream(){public void write(int b)throws IOException{if(closed)throw new IOException("Closed");other.put(b);}public void close(){Link.this.close();}};}
        public void close(){closed=true;input.close();other.close();}
    }
    static Link[] pair(){Pipe a=new Pipe(),b=new Pipe();return new Link[]{new Link(a,b),new Link(b,a)};}
    static final class Events implements MessagePeer.Listener {
        final List<String> states=new CopyOnWriteArrayList<>();
        final BlockingQueue<String> received=new LinkedBlockingQueue<>(),sent=new LinkedBlockingQueue<>();
        final CountDownLatch connected=new CountDownLatch(1),authenticating=new CountDownLatch(1);
        public void state(String s){states.add(s);if(s.equals("connected"))connected.countDown();if(s.equals("authenticating"))authenticating.countDown();}
        public void received(String t){received.add(t);}public void sent(String t){sent.add(t);}
    }
    public static void main(String[] args)throws Exception{
        roundTrip();badCode();timeout();cancelDuringHandshake();closeBeforeRun();
        System.out.println("PASS: "+checks+" Bluetooth stream-session checks (no physical radio)");
    }
    static void roundTrip()throws Exception{
        Link[] links=pair();Events ae=new Events(),be=new Events();ExecutorService pool=Executors.newFixedThreadPool(2);
        try(StreamSession a=new StreamSession(ae);StreamSession b=new StreamSession(be)){
            String code=SecureChannel.newCode();Future<?> af=pool.submit(()->a.run(links[0],code,true,5)),bf=pool.submit(()->b.run(links[1],code,false,5));
            ok(ae.connected.await(5,TimeUnit.SECONDS)&&be.connected.await(5,TimeUnit.SECONDS),"both authenticated on fragmented streams");
            a.send("蓝牙消息 🌏");ok("蓝牙消息 🌏".equals(be.received.poll(2,TimeUnit.SECONDS)),"UTF-8 stream delivery");ok("蓝牙消息 🌏".equals(ae.sent.poll(2,TimeUnit.SECONDS)),"sender success callback");
            String boundary=new String(new char[4096]).replace('\0','x');b.send(boundary);ok(boundary.equals(ae.received.poll(2,TimeUnit.SECONDS)),"4096-byte boundary");
            fails(()->a.send(new String(new char[1366]).replace('\0','汉')),"UTF-8 byte limit enforced");fails(()->a.send(""),"empty message refused");
            a.send("still usable");ok("still usable".equals(be.received.poll(2,TimeUnit.SECONDS)),"validation failure does not corrupt session");
            a.close();af.get(3,TimeUnit.SECONDS);bf.get(3,TimeUnit.SECONDS);
            ok(!a.connected()&&!b.connected(),"disconnect clears both connection states");ok(be.states.contains("disconnected"),"remote close signalled");
            ok(!ae.states.contains("disconnected"),"intentional close has no stale failure callback");ok(links[0].closed&&links[1].closed,"both underlying links released");
            fails(()->a.send("after close"),"send after close refused");a.close();b.close();
        }finally{pool.shutdownNow();}
    }
    static void badCode()throws Exception{
        Link[] links=pair();Events ae=new Events(),be=new Events();ExecutorService pool=Executors.newFixedThreadPool(2);
        try(StreamSession a=new StreamSession(ae);StreamSession b=new StreamSession(be)){
            Future<?> af=pool.submit(()->a.run(links[0],"AAAAAAAAAAAAAAAA",true,5)),bf=pool.submit(()->b.run(links[1],"BBBBBBBBBBBBBBBB",false,5));
            af.get(6,TimeUnit.SECONDS);bf.get(6,TimeUnit.SECONDS);
            ok(!a.connected()&&!b.connected(),"wrong code cannot connect");ok(!ae.states.contains("connected")&&!be.states.contains("connected"),"wrong code never exposes connected UI state");
            ok(ae.states.contains("authentication_failed")||be.states.contains("authentication_failed"),"authentication failure explained");
        }finally{pool.shutdownNow();}
    }
    static void timeout()throws Exception{
        Link[] links=pair();Events events=new Events();ExecutorService pool=Executors.newSingleThreadExecutor();
        try(StreamSession session=new StreamSession(events)){
            Future<?> f=pool.submit(()->session.run(links[0],SecureChannel.newCode(),true,1));f.get(4,TimeUnit.SECONDS);
            ok(events.states.contains("authentication_timeout"),"stalled handshake has deadline");ok(links[0].closed,"timeout closes blocked stream");ok(!session.connected(),"timeout stays disconnected");
        }finally{pool.shutdownNow();links[1].close();}
    }
    static void cancelDuringHandshake()throws Exception{
        Link[] links=pair();Events events=new Events();ExecutorService pool=Executors.newSingleThreadExecutor();
        try(StreamSession session=new StreamSession(events)){
            Future<?> f=pool.submit(()->session.run(links[0],SecureChannel.newCode(),true,15));ok(events.authenticating.await(3,TimeUnit.SECONDS),"handshake started");
            session.close();f.get(3,TimeUnit.SECONDS);ok(links[0].closed,"user cancellation releases blocked read");ok(events.states.equals(Arrays.asList("authenticating")),"no success or failure callback after manual cancellation");
        }finally{pool.shutdownNow();links[1].close();}
    }
    static void closeBeforeRun(){Link[] links=pair();Events events=new Events();StreamSession session=new StreamSession(events);session.close();session.run(links[0],SecureChannel.newCode(),true,1);ok(links[0].closed&&events.states.isEmpty(),"late connection closes after owner was stopped");links[1].close();}
}
