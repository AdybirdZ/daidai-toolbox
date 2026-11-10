import org.daidai.modules.music.MusicSpectrum;
import org.daidai.modules.music.StereoPcmReader;
import org.daidai.modules.music.WaveWriter;
import java.io.File;
import java.util.List;

/** Known-tone amplitude/frequency checks and overlapping decode-window continuity. */
public final class MusicAnalysisChecks {
    static int checks;
    static void check(boolean value,String name){checks++;if(!value)throw new AssertionError(name);}
    static float[][] tone(int size,int rate,double frequency,double amplitude){float[][] out=new float[2][size];for(int i=0;i<size;i++)out[0][i]=out[1][i]=(float)(amplitude*Math.sin(2*Math.PI*frequency*i/rate));return out;}
    public static void main(String[] args)throws Exception{
        int size=4096,rate=48000,bin=86;double frequency=bin*(double)rate/size;
        MusicSpectrum.Frame full=MusicSpectrum.analyze(tone(size,rate,frequency,1),rate,size);
        check(Math.abs(full.db[bin])<.02,"full-scale sine calibrates to 0 dBFS");
        MusicSpectrum.Frame half=MusicSpectrum.analyze(tone(size,rate,frequency,.5),rate,size);
        check(Math.abs(half.db[bin]+6.0206)<.03,"half-amplitude tone is -6.02 dBFS");
        List<MusicSpectrum.Peak> peaks=MusicSpectrum.peaks(full,3,20,20000);
        check(!peaks.isEmpty()&&Math.abs(peaks.get(0).frequency-frequency)<1,"FFT reports known bin-centered frequency");
        MusicSpectrum.Frame offBin=MusicSpectrum.analyze(tone(size,rate,1017.4,.7),rate,size);
        check(Math.abs(MusicSpectrum.peaks(offBin,1,20,20000).get(0).frequency-1017.4)<1,"interpolation locates off-bin frequency");
        float[][] inverted=tone(size,rate,frequency,.5);for(int i=0;i<size;i++)inverted[1][i]*=-1;
        check(Math.abs(MusicSpectrum.analyze(inverted,rate,size).db[bin]+6.0206)<.03,"antiphase stereo cannot cancel spectrum");
        float[][] rightOnly=tone(size,rate,frequency,.5);java.util.Arrays.fill(rightOnly[0],0);
        check(Math.abs(MusicSpectrum.analyze(rightOnly,rate,size).db[bin]+6.0206)<.03,"right-only tone remains visible");
        float[][] multiple=tone(size,rate,frequency,.5);for(int i=0;i<size;i++)for(int ch=0;ch<2;ch++)multiple[ch][i]+=(float)(.2*Math.sin(2*Math.PI*3000*i/rate));
        List<MusicSpectrum.Peak> two=MusicSpectrum.peaks(MusicSpectrum.analyze(multiple,rate,size),2,20,20000);
        check(two.size()==2&&Math.abs(two.get(0).frequency-frequency)<1&&Math.abs(two.get(1).frequency-3000)<1,"peak count and magnitude ranking");
        check(MusicSpectrum.peaks(full,0,20,20000).isEmpty(),"zero peak markers");
        check(MusicSpectrum.peaks(full,3,2000,3000).isEmpty(),"peak markers obey visible frequency range");
        MusicSpectrum.Frame silent=MusicSpectrum.analyze(new float[2][size],rate,size);
        check(silent.db[100]==MusicSpectrum.FLOOR_DB&&MusicSpectrum.peaks(silent,10,20,20000).isEmpty(),"silence has a finite floor and no invented peaks");
        boolean rejected=false;try{MusicSpectrum.analyze(null,rate,123);}catch(IllegalArgumentException expected){rejected=true;}check(rejected,"non-power-of-two FFT rejected");
        float[][] nonfinite=tone(size,rate,frequency,.5);nonfinite[0][0]=Float.NaN;nonfinite[1][1]=Float.POSITIVE_INFINITY;
        MusicSpectrum.Frame clean=MusicSpectrum.analyze(nonfinite,rate,size);check(Float.isFinite(clean.db[bin]),"invalid sample cannot poison all FFT bins");
        File directory=new File(args.length==0?System.getProperty("java.io.tmpdir"):args[0]);directory.mkdirs();File a=File.createTempFile("analysis-a-",".wav",directory),b=File.createTempFile("analysis-b-",".wav",directory);
        try{
            // Simulate two contiguous decoded 8 s windows with a 200 ms prefetch overlap.
            try(WaveWriter w=new WaveWriter(a,8000,2)){for(int i=0;i<64000;i++){short value=(short)(10000*Math.sin(2*Math.PI*125*i/8000));w.sample(value);w.sample((short)-value);}}
            try(WaveWriter w=new WaveWriter(b,8000,2)){for(int i=62400;i<126400;i++){short value=(short)(10000*Math.sin(2*Math.PI*125*i/8000));w.sample(value);w.sample((short)-value);}}
            try(StereoPcmReader first=new StereoPcmReader(a);StereoPcmReader prefetched=new StereoPcmReader(b)){
                float[][] before=first.read(7800,1024),after=prefetched.read(0,1024);boolean equal=true;for(int ch=0;ch<2;ch++)for(int i=0;i<1024;i++)if(before[ch][i]!=after[ch][i])equal=false;
                check(equal,"prefetched overlap preserves exact left/right PCM samples");
                float[][] boundary=prefetched.read(200,1024);check(Math.abs(boundary[0][1])>.01,"boundary analysis uses prefetched audio, not zero padding");
            }
        }finally{a.delete();b.delete();}
        System.out.println("PASS: "+checks+" music analysis checks");
    }
}
