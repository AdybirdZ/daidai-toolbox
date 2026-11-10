package org.daidai.modules.music;

import android.content.Context;
import android.media.*;
import android.net.Uri;
import java.io.*;
import java.nio.*;
import java.util.*;

/** Platform decoder to PCM16 WAV. Source remains untouched. */
public final class AudioTrimmer {
    public interface Progress { void update(long timeUs); }
    private final Context context;
    private final long[][] ranges;
    private final Progress progress;
    private WaveWriter writer;
    private int rate, channels, encoding;
    private long frames,plannedBytes;
    private static final long MAX_TEMP_BYTES=512L*1024*1024,RESERVE_BYTES=16L*1024*1024;

    private AudioTrimmer(Context c, long[][] selected, Progress p) { context = c; ranges = normalize(selected); progress = p; }
    public static long export(Context c, Uri source, long startUs, long endUs, File out, Progress p) throws Exception {
        if (startUs < 0 || endUs <= startUs) throw new IllegalArgumentException("Invalid interval");
        return new AudioTrimmer(c, new long[][]{{startUs, endUs}}, p).run(source, out);
    }
    /** Export several retained ranges in order, joining them into one WAV. */
    public static long exportRanges(Context c, Uri source, long[][] selected, File out, Progress p) throws Exception { return new AudioTrimmer(c, selected, p).run(source, out); }
    private static long[][] normalize(long[][] selected) {
        if (selected == null || selected.length == 0) throw new IllegalArgumentException("No ranges");
        ArrayList<long[]> sorted = new ArrayList<>();
        for (long[] r : selected) { if (r == null || r.length < 2 || r[0] < 0 || r[1] <= r[0]) throw new IllegalArgumentException("Invalid interval"); sorted.add(new long[]{r[0], r[1]}); }
        Collections.sort(sorted, (a, b) -> Long.compare(a[0], b[0]));
        ArrayList<long[]> merged = new ArrayList<>();
        for (long[] r : sorted) { if (merged.isEmpty() || r[0] > merged.get(merged.size() - 1)[1]) merged.add(r); else merged.get(merged.size() - 1)[1] = Math.max(merged.get(merged.size() - 1)[1], r[1]); }
        return merged.toArray(new long[0][]);
    }
    private void check() throws InterruptedException { if (Thread.currentThread().isInterrupted()) throw new InterruptedException("Cancelled"); }
    private void format(MediaFormat f) throws IOException {
        int r = f.getInteger(MediaFormat.KEY_SAMPLE_RATE), c = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT);if(r<1||r>768000||c<1||c>32)throw new IOException("Unsupported PCM format");
        if (writer != null && (r != rate || c != channels)) throw new IOException("Audio format changed during decoding");
        rate = r; channels = c; encoding = f.containsKey(MediaFormat.KEY_PCM_ENCODING) ? f.getInteger(MediaFormat.KEY_PCM_ENCODING) : AudioFormat.ENCODING_PCM_16BIT;double duration=0;for(long[] range:ranges)duration+=(range[1]-range[0])/1000000.;double bytes=duration*rate*channels*2;if(bytes>MAX_TEMP_BYTES)throw new IOException("Selected audio exceeds the 512 MiB temporary WAV limit");plannedBytes=(long)Math.ceil(bytes);
    }
    private long ceil(long a, long b) { return -Math.floorDiv(-a, b); }
    private boolean pcm(ByteBuffer data, long time, File output) throws Exception {
        check();
        int sampleBytes = encoding == AudioFormat.ENCODING_PCM_FLOAT || encoding == AudioFormat.ENCODING_PCM_32BIT ? 4 : encoding == AudioFormat.ENCODING_PCM_24BIT_PACKED ? 3 : encoding == AudioFormat.ENCODING_PCM_8BIT ? 1 : encoding == AudioFormat.ENCODING_PCM_16BIT ? 2 : 0;
        if (sampleBytes == 0) throw new IOException("Unsupported PCM encoding: " + encoding);
        data.order(ByteOrder.LITTLE_ENDIAN); int count = data.remaining() / (sampleBytes * channels);
        long baseFrame = Math.round(time * rate / 1000000.0), lastEnd = ceil(ranges[ranges.length - 1][1] * rate, 1000000); int base = data.position(); boolean wrote = false;
        for (long[] range : ranges) {
            int first = (int)Math.max(0, Math.min(count, ceil(range[0] * rate, 1000000) - baseFrame)); int last = (int)Math.max(0, Math.min(count, ceil(range[1] * rate, 1000000) - baseFrame)); if (last <= first) continue;
            wrote = true; if (writer == null) {long available=output.getAbsoluteFile().getParentFile().getUsableSpace();if(available>0&&available<plannedBytes+RESERVE_BYTES)throw new IOException("Insufficient temporary storage");writer = new WaveWriter(output, rate, channels);} data.position(base + first * sampleBytes * channels);
            for (int i = first; i < last; i++) {
                for (int ch = 0; ch < channels; ch++) {
                    short value;
                    if (encoding == AudioFormat.ENCODING_PCM_FLOAT) { float v = data.getFloat(); if (!Float.isFinite(v)) v = 0; value = (short)Math.round(Math.max(-1, Math.min(1, v)) * 32767); }
                    else if (sampleBytes == 1) value = (short)(((data.get() & 255) - 128) << 8);
                    else if (sampleBytes == 2) value = data.getShort();
                    else if (sampleBytes == 3) { int b0 = data.get() & 255, b1 = data.get() & 255, b2 = data.get(); value = (short)((b0 | (b1 << 8) | (b2 << 16)) >> 8); }
                    else value = (short)(data.getInt() >> 16);
                    writer.sample(value);
                }
                frames++; if ((i & 4095) == 0) check();
            }
        }
        if (wrote && progress != null) progress.update((baseFrame + count) * 1000000 / rate); return baseFrame + count >= lastEnd;
    }
    private long run(Uri uri, File output) throws Exception {
        MediaExtractor extractor = new MediaExtractor(); MediaCodec decoder = null; boolean success = false;
        try {
            extractor.setDataSource(context, uri, null); int track = -1; MediaFormat input = null;
            for (int i = 0; i < extractor.getTrackCount(); i++) { MediaFormat f = extractor.getTrackFormat(i); String mime = f.getString(MediaFormat.KEY_MIME); if (mime != null && mime.startsWith("audio/")) { track = i; input = f; break; } }
            if (track < 0) throw new IOException("No audio track"); extractor.selectTrack(track); extractor.seekTo(ranges[0][0], MediaExtractor.SEEK_TO_PREVIOUS_SYNC); String mime = input.getString(MediaFormat.KEY_MIME); format(input);
            if ("audio/raw".equals(mime)) {
                ByteBuffer buffer = ByteBuffer.allocateDirect(2 * 1024 * 1024);
                while (true) { check(); buffer.clear(); int size = extractor.readSampleData(buffer, 0); if (size < 0) break; long time = extractor.getSampleTime(); buffer.position(0); buffer.limit(size); if (pcm(buffer, time, output)) break; if (!extractor.advance()) break; }
            } else {
                decoder = MediaCodec.createDecoderByType(mime); decoder.configure(input, null, null, 0); decoder.start(); boolean inputDone = false, done = false; MediaCodec.BufferInfo info = new MediaCodec.BufferInfo(); long lastProgress = System.nanoTime();
                while (!done) {
                    check(); boolean moved = false;
                    if (!inputDone) { int index = decoder.dequeueInputBuffer(10000); if (index >= 0) { ByteBuffer b = decoder.getInputBuffer(index); int size = extractor.readSampleData(b, 0); if (size < 0) { decoder.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone = true; } else { long time = extractor.getSampleTime(); decoder.queueInputBuffer(index, 0, size, time, 0); extractor.advance(); } moved = true; } }
                    int index = decoder.dequeueOutputBuffer(info, 10000);
                    if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) { format(decoder.getOutputFormat()); moved = true; }
                    else if (index >= 0) { ByteBuffer b = decoder.getOutputBuffer(index); boolean reached = false; if (info.size > 0 && (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) { b.position(info.offset); b.limit(info.offset + info.size); reached = pcm(b, info.presentationTimeUs, output); } decoder.releaseOutputBuffer(index, false); done = reached || (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0; moved = true; }
                    if (moved) lastProgress = System.nanoTime(); else if (System.nanoTime() - lastProgress > 15000000000L) throw new IOException("Decoder timed out");
                }
            }
            if (frames == 0) throw new IOException("No samples in selected interval"); success = true; return frames;
        } finally { try { if (writer != null) writer.close(); }catch(Exception e){success=false;throw e;} finally {try{if (decoder != null) decoder.release();}finally{extractor.release();if (!success) output.delete();}} }
    }
}
