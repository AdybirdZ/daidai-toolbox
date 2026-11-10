import org.daidai.modules.messaging.SecureChannel;
import java.net.*;

/** Desktop-side peer for emulator interoperability QA; never included in APKs. */
public final class AndroidPeerCheck {
    public static void main(String[] args)throws Exception {
        boolean hosting=args[0].equals("host");
        try(ServerSocket server=hosting?new ServerSocket(42424):null;
            Socket socket=hosting?server.accept():new Socket("127.0.0.1",42424)) {
            socket.setSoTimeout(25000);
            SecureChannel channel=SecureChannel.establish(socket.getInputStream(),socket.getOutputStream(),args[1],hosting);
            channel.writeMessage("Hello from desktop");
            if(!channel.readMessage().equals("android_reply"))throw new AssertionError("Unexpected reply");
            channel.writeMessage("Bidirectional test passed");
            try {channel.readMessage();throw new AssertionError("Expected disconnect after background");}
            catch(java.io.EOFException|java.net.SocketException expected){System.out.println("PASS: Android / desktop encrypted roundtrip and background disconnect");}
        }
    }
}
