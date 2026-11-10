import org.daidai.modules.colorvision.ColorVision;
import org.daidai.modules.music.WaveWriter;
import java.io.*;import java.nio.*;
public final class ColorMusicChecks {
 static int checks;static void check(boolean b,String message){checks++;if(!b)throw new AssertionError(message);}
 public static void main(String[] args)throws Exception {
  for(int mode=0;mode<=3;mode++)for(int i=0;i<256;i++){int color=0x7f000000|i*0x010101,result=ColorVision.pixel(color,mode);check((result>>>24)==127,"alpha preserved");check(Math.abs(((result>>16)&255)-i)<=1&&Math.abs(((result>>8)&255)-i)<=1&&Math.abs((result&255)-i)<=1,"neutral grey");}
  int[] expected={0xffff0000,0xff6d5f00,0xffa39000,0xffff000f};for(int mode=0;mode<4;mode++)check(ColorVision.pixel(0xffff0000,mode)==expected[mode],"reference red: "+mode+" got "+Integer.toHexString(ColorVision.pixel(0xffff0000,mode)));
  check(ColorVision.pixel(0x12abcdef,0)==0x12abcdef,"original identity");boolean bad=false;try{ColorVision.pixel(0,4);}catch(IllegalArgumentException e){bad=true;}check(bad,"reject invalid mode");
  File file=File.createTempFile("wave-check",".wav");try{try(WaveWriter out=new WaveWriter(file,48000,2)){for(int i=0;i<48000;i++){out.sample((short)i);out.sample((short)-i);}check(out.frames()==48000,"streamed frames");}try(RandomAccessFile in=new RandomAccessFile(file,"r")){byte[] b=new byte[44];in.readFully(b);ByteBuffer h=ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN);check(file.length()==192044,"WAV size");check(h.getInt(4)==192036&&h.getInt(40)==192000,"RIFF and data lengths");check(h.getInt(24)==48000&&h.getShort(22)==2&&h.getShort(34)==16,"PCM format");in.seek(44+400);check(Short.reverseBytes(in.readShort())==100&&Short.reverseBytes(in.readShort())==-100,"sample interleave");}}finally{file.delete();}
  System.out.println("PASS: "+checks+" color and WAV checks");
 }
}
