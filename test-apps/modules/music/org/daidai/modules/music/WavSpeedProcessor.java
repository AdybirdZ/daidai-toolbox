package org.daidai.modules.music;

import java.io.*;

/** Offline speed processing for the PCM16 WAV produced by AudioTrimmer. */
public final class WavSpeedProcessor {
    private WavSpeedProcessor() {}

    /**
     * preservePitch=false changes the sample-rate header. preservePitch=true
     * uses waveform-similarity overlap/add so duration changes independently
     * of the fundamental frequency. No third-party library is used.
     */
    public static void process(File input, File output, float speed, boolean preservePitch) throws Exception {
        if (!Float.isFinite(speed) || speed < .5f || speed > 2f) throw new IllegalArgumentException("Invalid speed");
        if(input.getCanonicalFile().equals(output.getCanonicalFile()))throw new IOException("Input and output must differ");
        try (RandomAccessFile source = new RandomAccessFile(input, "r")) {
            Header h = header(source);
            check(0);long bytes=preservePitch?Math.round(h.frames/(double)speed)*h.channels*2:h.frames*h.channels*2;long available=output.getAbsoluteFile().getParentFile().getUsableSpace();if(bytes>512L*1024*1024||available>0&&available<bytes+16L*1024*1024)throw new IOException("Insufficient temporary storage or output exceeds 512 MiB");
            if (!preservePitch || Math.abs(speed - 1) < .0001f) {
                try (WaveWriter writer = new WaveWriter(output, Math.round(h.rate * speed), h.channels)) {
                    source.seek(h.offset);long remaining=h.frames*h.channels*2;byte[] raw=new byte[65536];
                    while(remaining>0){check(remaining);int count=(int)Math.min(remaining,raw.length);source.readFully(raw,0,count);for(int i=0;i<count;i+=2)writer.sample((short)((raw[i]&255)|(raw[i+1]<<8)));remaining-=count;}
                }
                return;
            }
            stretch(source, h, output, speed);
        } catch (Exception e) { output.delete(); throw e; }
    }

    private static void stretch(RandomAccessFile source, Header h, File output, float speed) throws Exception {
        int overlap = Math.max(32, h.rate / 100), hop = overlap * 3, sequence = hop + overlap, search = Math.max(16, h.rate / 200);
        long target = Math.max(1, Math.round(h.frames / speed)), written = 0;
        short[][] previous = readFrames(source, h, 0, sequence);
        try (WaveWriter writer = new WaveWriter(output, h.rate, h.channels)) {
            int first = (int)Math.min(hop, target);
            write(writer, previous, 0, first); written = first;
            double expected = hop * speed;
            while (written < target) {
                check(written); long center = Math.round(expected), best = Math.max(0, Math.min(h.frames - 1, center)); double score = -Double.MAX_VALUE;long firstCandidate=Math.max(0,center-search);short[][] candidates=readFrames(source,h,firstCandidate,search*2+sequence);
                int step = Math.max(1, h.rate / 4000);
                for (long p = Math.max(0, center - search); p <= Math.min(h.frames - 1, center + search); p += step) {
                    int candidateOffset=(int)(p-firstCandidate);double cross = 0, a2 = 1, b2 = 1;
                    for (int i = 0; i < overlap; i += 4) {for(int ch=0;ch<h.channels;ch++){double a=previous[ch][hop+i],b=candidates[ch][candidateOffset+i];cross+=a*b;a2+=a*a;b2+=b*b;}}
                    double s = cross / Math.sqrt(a2 * b2); if (s > score) { score = s; best = p; }
                }
                short[][] current=new short[h.channels][sequence];int selectedOffset=(int)Math.max(0,best-firstCandidate);for(int ch=0;ch<h.channels;ch++)System.arraycopy(candidates[ch],selectedOffset,current[ch],0,Math.min(sequence,candidates[ch].length-selectedOffset));
                int block = (int)Math.min(hop, target - written);
                for (int i = 0; i < block; i++) for (int ch = 0; ch < h.channels; ch++) {
                    short value = current[ch][i];
                    if (i < overlap) { double t = i / (double)overlap; value = (short)Math.round(previous[ch][hop + i] * (1 - t) + current[ch][i] * t); }
                    writer.sample(value);
                }
                written += block; previous = current; expected += hop * speed;
            }
        }
    }

    private static void write(WaveWriter out, short[][] samples, int from, int count) throws Exception { for (int i = from; i < from + count; i++) for (short[] channel : samples) out.sample(channel[i]); }
    private static short[][] readFrames(RandomAccessFile source, Header h, long at, int count) throws Exception {
        short[][] frames = new short[h.channels][count];
        if (at >= h.frames) return frames;
        int available=(int)Math.min(count,h.frames-at);byte[] raw=new byte[available*h.channels*2];source.seek(h.offset+Math.max(0,at)*h.channels*2L);source.readFully(raw);int p=0;for(int i=0;i<available;i++)for(int ch=0;ch<h.channels;ch++){frames[ch][i]=(short)((raw[p]&255)|(raw[p+1]<<8));p+=2;}
        return frames;
    }
    private static short readShort(RandomAccessFile in) throws IOException { return (short)((in.readUnsignedByte()) | in.readByte() << 8); }
    private static int intLE(RandomAccessFile in) throws IOException { return in.readUnsignedByte() | in.readUnsignedByte() << 8 | in.readUnsignedByte() << 16 | in.readUnsignedByte() << 24; }
    private static String four(RandomAccessFile in) throws IOException { byte[] b = new byte[4]; in.readFully(b); return new String(b, "US-ASCII"); }
    private static Header header(RandomAccessFile in) throws Exception {
        if (!"RIFF".equals(four(in))) throw new IOException("Not WAV"); intLE(in); if (!"WAVE".equals(four(in))) throw new IOException("Not WAV"); Header h = new Header();
        while (in.getFilePointer() + 8 <= in.length()) {
            String id = four(in); long size = intLE(in) & 0xffffffffL, start = in.getFilePointer();if(size>in.length()-start)throw new IOException("Truncated WAV chunk");
            if ("fmt ".equals(id)) {if(size<16)throw new IOException("Invalid WAV format"); int format = readShort(in) & 65535; h.channels = readShort(in) & 65535; h.rate = intLE(in); intLE(in); readShort(in); int bits = readShort(in) & 65535; if (format != 1 || bits != 16 || h.channels < 1 || h.channels > 8 || h.rate < 1||h.rate>768000) throw new IOException("Unsupported WAV"); }
            if ("data".equals(id)) { h.offset = start; h.frames = size / Math.max(1, h.channels * 2); if (h.channels == 0||h.frames==0||size%(h.channels*2)!=0) throw new IOException("Invalid WAV data"); return h; }
            in.seek(start + size + (size & 1));
        }
        throw new IOException("Missing WAV data");
    }
    private static void check(long i) throws InterruptedException { if (Thread.currentThread().isInterrupted()) throw new InterruptedException("Cancelled"); }
    private static final class Header { int rate, channels; long offset, frames; }
}
