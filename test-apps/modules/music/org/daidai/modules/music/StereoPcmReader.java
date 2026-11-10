package org.daidai.modules.music;

import java.io.*;

/** Bounded, bulk-reading PCM16 RIFF reader. Closing does not delete the file. */
public final class StereoPcmReader implements Closeable {
    private final RandomAccessFile input;
    private int rate,channels;
    private long offset,frames;
    public StereoPcmReader(File file)throws IOException {
        input=new RandomAccessFile(file,"r");
        try {
            if(!four().equals("RIFF"))throw new IOException("Not RIFF");number();if(!four().equals("WAVE"))throw new IOException("Not WAVE");
            while(input.getFilePointer()+8<=input.length()) {
                String type=four();long size=number()&0xffffffffL,start=input.getFilePointer();
                if(size>input.length()-start)throw new IOException("Truncated WAV chunk");
                if(type.equals("fmt ")) { if(size<16)throw new IOException("Invalid WAV format");int format=small();channels=small();rate=number();number();small();int bits=small();if(format!=1||bits!=16||channels<1||channels>32||rate<1||rate>768000)throw new IOException("Unsupported WAV"); }
                if(type.equals("data")) { if(channels==0||size%(channels*2)!=0)throw new IOException("Invalid WAV data");offset=start;frames=size/(channels*2);return; }
                input.seek(start+size+(size&1));
            }
            throw new IOException("Missing WAV data");
        } catch(IOException|RuntimeException e) { input.close();throw e; }
    }
    public int sampleRate(){return rate;}public int channels(){return channels;}public long frames(){return frames;}
    public synchronized float[][] read(long millis,int count)throws IOException {
        if(count<0||count>65536)throw new IOException("Invalid sample window");float[][] out=new float[2][count];long at=Math.max(0,Math.min(frames,Math.max(0,millis)*rate/1000));int available=(int)Math.min(count,frames-at);byte[] raw=new byte[available*channels*2];input.seek(offset+at*channels*2);input.readFully(raw);
        for(int i=0;i<available;i++){int base=i*channels*2;short l=(short)((raw[base]&255)|(raw[base+1]<<8));short r=channels<2?l:(short)((raw[base+2]&255)|(raw[base+3]<<8));out[0][i]=l/32768f;out[1][i]=r/32768f;}return out;
    }
    private int small()throws IOException{return input.readUnsignedByte()|input.readUnsignedByte()<<8;}private int number()throws IOException{return small()|small()<<16;}private String four()throws IOException{byte[] b=new byte[4];input.readFully(b);return new String(b,"US-ASCII");}
    public synchronized void close()throws IOException{input.close();}
}
