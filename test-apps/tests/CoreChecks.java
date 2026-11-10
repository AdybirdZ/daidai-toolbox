import org.daidai.modules.music.*;
import org.daidai.modules.messaging.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public final class CoreChecks {
    static int checks;
    static void ok(boolean value,String name){if(!value)throw new AssertionError(name);checks++;}
    static void near(double actual,double expected,double tolerance,String name){ok(Double.isFinite(actual)&&Math.abs(actual-expected)<=tolerance,name+": "+actual+" != "+expected);}
    interface Checked {void run()throws Exception;}
    static void fails(Checked task,String name)throws Exception{try{task.run();}catch(Exception expected){checks++;return;}throw new AssertionError(name);}
    public static void main(String[] args)throws Exception {
        Random random=new Random(14);ok(QueuePlanner.next(0,0,QueuePlanner.Mode.ALL,true,random)==-1,"empty queue");ok(QueuePlanner.next(3,2,QueuePlanner.Mode.ALL,true,random)==0,"wrap queue");ok(QueuePlanner.next(3,1,QueuePlanner.Mode.ONE,true,random)==1,"repeat one completion");ok(QueuePlanner.next(3,1,QueuePlanner.Mode.ONE,false,random)==2,"manual skips repeat one");ok(QueuePlanner.previous(3,0)==2,"previous wrap");for(int i=0;i<40;i++)ok(QueuePlanner.next(3,1,QueuePlanner.Mode.SHUFFLE,true,random)!=1,"shuffle excludes current");
        for(String host:new String[]{"10.1.2.3","192.168.0.4","172.16.0.1","172.31.2.3","169.254.4.2","127.0.0.1"})ok(SecureChannel.isLocalAddress(host),"local "+host);for(String host:new String[]{"8.8.8.8","172.32.0.1","192.0.0.1","example.com","192.168.1.999","192.168.1"})ok(!SecureChannel.isLocalAddress(host),"reject address "+host);for(int i=0;i<20;i++)ok(SecureChannel.normalize(SecureChannel.newCode()).length()==16,"generated code");fails(()->SecureChannel.normalize("123"),"short code");
        secureRoundTrip();wrongCode();peerRoundTrip();System.out.println("PASS: "+checks+" checks (playback queue, encrypted channels, real TCP peers)");
    }
    static final class Recording extends FilterOutputStream {final ByteArrayOutputStream copy=new ByteArrayOutputStream();Recording(OutputStream o){super(o);}public void write(int v)throws IOException{out.write(v);copy.write(v);}public void write(byte[] a,int o,int n)throws IOException{out.write(a,o,n);copy.write(a,o,n);}}
    static void secureRoundTrip()throws Exception{ExecutorService worker=Executors.newSingleThreadExecutor();String code=SecureChannel.newCode();try(ServerSocket server=new ServerSocket(0,1,InetAddress.getLoopbackAddress());Socket client=new Socket("127.0.0.1",server.getLocalPort());Socket receiver=server.accept()){
            client.setSoTimeout(5000);receiver.setSoTimeout(5000);Future<SecureChannel> future=worker.submit(()->SecureChannel.establish(receiver.getInputStream(),receiver.getOutputStream(),code,true));Recording out=new Recording(client.getOutputStream());SecureChannel a=SecureChannel.establish(client.getInputStream(),out,code,false);SecureChannel b=future.get(5,TimeUnit.SECONDS);
            int offset=out.copy.size();String message="Hello secure 你好，离线消息 🌏";a.writeMessage(message);near(b.readMessage().equals(message)?1:0,1,0,"UTF-8 secure roundtrip");b.writeMessage("reply");ok(a.readMessage().equals("reply"),"bidirectional");byte[] packet=Arrays.copyOfRange(out.copy.toByteArray(),offset,out.copy.size());ok(!new String(packet,"ISO-8859-1").contains("Hello secure"),"packet is encrypted");client.getOutputStream().write(packet);client.getOutputStream().flush();fails(()->b.readMessage(),"replay rejected by sequence authentication");fails(()->a.writeMessage(new String(new char[4097]).replace('\0','x')),"oversized message rejected");new java.io.DataOutputStream(client.getOutputStream()).writeInt(Integer.MAX_VALUE);fails(()->b.readMessage(),"oversized frame rejected before allocation");
        }finally{worker.shutdownNow();}}
    static void wrongCode()throws Exception{ExecutorService worker=Executors.newSingleThreadExecutor();try(ServerSocket server=new ServerSocket(0,1,InetAddress.getLoopbackAddress());Socket client=new Socket("127.0.0.1",server.getLocalPort());Socket receiver=server.accept()){
            client.setSoTimeout(5000);receiver.setSoTimeout(5000);Future<Boolean> future=worker.submit(()->{try{SecureChannel.establish(receiver.getInputStream(),receiver.getOutputStream(),"AAAAAAAAAAAAAAAA",true);return false;}catch(Exception expected){return true;}});
            fails(()->SecureChannel.establish(client.getInputStream(),client.getOutputStream(),"BBBBBBBBBBBBBBBB",false),"wrong pairing client");ok(future.get(5,TimeUnit.SECONDS),"wrong pairing server");
        }finally{worker.shutdownNow();}}
    static void peerRoundTrip()throws Exception{CountDownLatch waiting=new CountDownLatch(1),connected=new CountDownLatch(2),received=new CountDownLatch(1);List<String> errors=Collections.synchronizedList(new ArrayList<>());String code=SecureChannel.newCode();LanPeer.Listener serverListener=new LanPeer.Listener(){public void state(String s){if(s.equals("waiting"))waiting.countDown();if(s.equals("connected"))connected.countDown();}public void received(String text){if(text.equals("端到端 TCP"))received.countDown();else errors.add(text);}public void sent(String s){}};
        try(LanPeer host=new LanPeer(serverListener);LanPeer guest=new LanPeer(new LanPeer.Listener(){public void state(String s){if(s.equals("connected"))connected.countDown();}public void received(String s){}public void sent(String s){}})){
            host.host(code);ok(waiting.await(5,TimeUnit.SECONDS),"host waiting");guest.connect("127.0.0.1",code);ok(connected.await(10,TimeUnit.SECONDS),"peers connected");guest.send("端到端 TCP");ok(received.await(5,TimeUnit.SECONDS)&&errors.isEmpty(),"peer delivery");}
    }
}
