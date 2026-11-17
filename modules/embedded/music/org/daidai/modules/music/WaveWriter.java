package org.daidai.modules.music;
import java.io.*;
/** Streaming little-endian PCM16 RIFF, finalized only after a successful decode. */
public final class WaveWriter implements Closeable {
 private final RandomAccessFile out;private final int rate,channels;private long bytes;private final byte[] buffer=new byte[65536];private int used;
 public WaveWriter(File file,int sampleRate,int channelCount)throws IOException {if(sampleRate<1||channelCount<1||channelCount>32)throw new IOException("Invalid PCM format");rate=sampleRate;channels=channelCount;out=new RandomAccessFile(file,"rw");out.setLength(0);out.write(new byte[44]);}
 public void sample(short value)throws IOException {if(bytes>=0xfffffff0L-44)throw new IOException("WAV exceeds RIFF size limit");buffer[used++]=(byte)value;buffer[used++]=(byte)(value>>8);bytes+=2;if(used==buffer.length){out.write(buffer);used=0;}}
 public long frames(){return bytes/(2*channels);}
 private void le(long value,int count)throws IOException {for(int i=0;i<count;i++)out.write((int)(value>>(8*i))&255);}
 public void close()throws IOException {try{if(used>0)out.write(buffer,0,used);out.seek(0);out.writeBytes("RIFF");le(bytes+36,4);out.writeBytes("WAVEfmt ");le(16,4);le(1,2);le(channels,2);le(rate,4);le((long)rate*channels*2,4);le(channels*2,2);le(16,2);out.writeBytes("data");le(bytes,4);}finally{out.close();}}
}
