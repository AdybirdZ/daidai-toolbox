package org.daidai.modules.messaging;

import javax.crypto.*;
import javax.crypto.spec.*;
import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.security.*;
import java.util.*;

/** V1 authenticated channel. No third-party runtime libraries; not a general chat protocol. */
public final class SecureChannel {
    public static final int MAX_TEXT_BYTES=4096;
    private static final int MAGIC=0x44444d31,MAX_PACKET=8192;
    private final DataInputStream in;private final DataOutputStream out;
    private final SecretKey key;private final byte[] session;private final byte sendRole,receiveRole;
    private long sendSequence,receiveSequence;private final SecureRandom random=new SecureRandom();
    public static String newCode(){SecureRandom random=new SecureRandom();String alphabet="ABCDEFGHJKLMNPQRSTUVWXYZ23456789";StringBuilder s=new StringBuilder();for(int i=0;i<16;i++)s.append(alphabet.charAt(random.nextInt(alphabet.length())));return s.toString();}
    public static String normalize(String code){String s=code.replace("-","").replace(" ","").toUpperCase(Locale.ROOT);if(!s.matches("[A-HJ-NP-Z2-9]{16}"))throw new IllegalArgumentException("Pairing code: 16 letters/digits (no I, O, 0, 1)");return s;}
    public static SecureChannel establish(InputStream input,OutputStream output,String pairing,boolean server)throws Exception{
        String code=normalize(pairing);DataInputStream in=new DataInputStream(input);DataOutputStream out=new DataOutputStream(output);SecureRandom random=new SecureRandom();byte[] local=new byte[32];random.nextBytes(local);
        out.writeInt(MAGIC);out.write(local);out.flush();if(in.readInt()!=MAGIC)throw new IOException("Unsupported protocol");byte[] remote=new byte[32];in.readFully(remote);
        byte[] transcript=new byte[64];System.arraycopy(server?local:remote,0,transcript,0,32);System.arraycopy(server?remote:local,0,transcript,32,32);
        byte[] session=MessageDigest.getInstance("SHA-256").digest(transcript);PBEKeySpec spec=new PBEKeySpec(code.toCharArray(),session,100000,256);
        byte[] derived;try{derived=SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();}finally{spec.clearPassword();}
        SecretKey key=new SecretKeySpec(derived,"AES");Arrays.fill(derived,(byte)0);
        SecureChannel c=new SecureChannel(in,out,key,session,server);c.writeMessage("DDM/1 authenticated");if(!c.readMessage().equals("DDM/1 authenticated"))throw new IOException("Authentication failed");return c;
    }
    private SecureChannel(DataInputStream i,DataOutputStream o,SecretKey k,byte[] s,boolean server){in=i;out=o;key=k;session=s;sendRole=(byte)(server?1:2);receiveRole=(byte)(server?2:1);}
    private byte[] aad(byte role,long sequence){return ByteBuffer.allocate(session.length+9).put(session).put(role).putLong(sequence).array();}
    public synchronized void writeMessage(String text)throws Exception{
        byte[] bytes=text.getBytes(StandardCharsets.UTF_8);if(bytes.length==0||bytes.length>MAX_TEXT_BYTES)throw new IllegalArgumentException("Message: 1–4096 UTF-8 bytes");
        byte[] nonce=new byte[12];random.nextBytes(nonce);Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key,new GCMParameterSpec(128,nonce));cipher.updateAAD(aad(sendRole,sendSequence));byte[] encrypted=cipher.doFinal(bytes);
        out.writeInt(nonce.length+encrypted.length);out.write(nonce);out.write(encrypted);out.flush();sendSequence++;
    }
    public String readMessage()throws Exception{int size=in.readInt();if(size<29||size>MAX_PACKET)throw new IOException("Invalid packet size");byte[] nonce=new byte[12],payload=new byte[size-12];in.readFully(nonce);in.readFully(payload);Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,nonce));cipher.updateAAD(aad(receiveRole,receiveSequence));byte[] plain=cipher.doFinal(payload);if(plain.length==0||plain.length>MAX_TEXT_BYTES)throw new IOException("Invalid message length");receiveSequence++;
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(plain)).toString();}
    /** Numeric private IPv4 only: no DNS lookup and no public Internet destination. */
    public static boolean isLocalAddress(String host){String[] parts=host.split("\\.",-1);if(parts.length!=4)return false;int[] n=new int[4];for(int i=0;i<4;i++){if(!parts[i].matches("[0-9]{1,3}"))return false;n[i]=Integer.parseInt(parts[i]);if(n[i]>255)return false;}return n[0]==10||n[0]==127||(n[0]==192&&n[1]==168)||(n[0]==172&&n[1]>=16&&n[1]<=31)||(n[0]==169&&n[1]==254);}
}
