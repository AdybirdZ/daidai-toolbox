package org.daidai.modules.music;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Dependency-free, Hann-windowed FFT calibrated to peak digital full scale. */
public final class MusicSpectrum {
    public static final float FLOOR_DB = -96f;
    public static final class Frame {
        public final int sampleRate, fftSize;
        public final float[] db;
        Frame(int rate, int size, float[] values) { sampleRate=rate;fftSize=size;db=values; }
        public double frequency(int bin) { return bin*(double)sampleRate/fftSize; }
    }
    public static final class Peak {
        public final double frequency; public final float db;
        Peak(double f,float value){frequency=f;db=value;}
    }
    private MusicSpectrum(){}
    public static Frame analyze(float[][] channels,int rate,int size) {
        if(rate<1||size<8||size>65536||(size&(size-1))!=0)throw new IllegalArgumentException("Invalid FFT format");
        float[] bins=new float[size/2+1]; java.util.Arrays.fill(bins,FLOOR_DB);
        if(channels==null)return new Frame(rate,size,bins);
        // The stronger channel avoids cancellation of antiphase stereo samples.
        for(int channel=0;channel<Math.min(2,channels.length);channel++){
            double[] real=new double[size],imag=new double[size];double weight=0;
            float[] source=channels[channel];
            for(int i=0;i<size;i++){double w=.5-.5*Math.cos(2*Math.PI*i/(size-1));weight+=w;double s=source!=null&&i<source.length&&Float.isFinite(source[i])?source[i]:0;real[i]=s*w;}
            fft(real,imag);
            for(int i=0;i<bins.length;i++){
                double amplitude=Math.hypot(real[i],imag[i])*(i==0||i==size/2?1:2)/weight;
                float db=(float)Math.max(FLOOR_DB,20*Math.log10(Math.max(1e-12,amplitude)));
                bins[i]=Math.max(bins[i],db);
            }
        }
        return new Frame(rate,size,bins);
    }
    private static void fft(double[] real,double[] imag){
        int n=real.length;
        for(int i=1,j=0;i<n;i++){int bit=n>>1;for(; (j&bit)!=0;bit>>=1)j^=bit;j^=bit;if(i<j){double v=real[i];real[i]=real[j];real[j]=v;}}
        for(int length=2;length<=n;length<<=1){double angle=-2*Math.PI/length,wr=Math.cos(angle),wi=Math.sin(angle);for(int start=0;start<n;start+=length){double ar=1,ai=0;for(int j=0;j<length/2;j++){int p=start+j,q=p+length/2;double br=real[q]*ar-imag[q]*ai,bi=real[q]*ai+imag[q]*ar;real[q]=real[p]-br;imag[q]=imag[p]-bi;real[p]+=br;imag[p]+=bi;double next=ar*wr-ai*wi;ai=ar*wi+ai*wr;ar=next;}}}
    }
    /** Local maxima ranked by amplitude, with parabolic-bin interpolation. */
    public static List<Peak> peaks(Frame frame,int count,double low,double high){
        int limit=Math.max(0,Math.min(10,count));List<Integer> candidates=new ArrayList<>();
        for(int i=1;i<frame.db.length-1;i++)if(frame.frequency(i)>=low&&frame.frequency(i)<=high&&frame.db[i]>FLOOR_DB+12&&frame.db[i]>frame.db[i-1]&&frame.db[i]>=frame.db[i+1])candidates.add(i);
        Collections.sort(candidates,(a,b)->Float.compare(frame.db[b],frame.db[a]));
        List<Peak> result=new ArrayList<>();
        for(int bin:candidates){if(result.size()>=limit)break;double a=frame.db[bin-1],b=frame.db[bin],c=frame.db[bin+1],den=a-2*b+c,delta=Math.abs(den)<1e-12?0:.5*(a-c)/den;delta=Math.max(-.5,Math.min(.5,delta));double hz=(bin+delta)*frame.sampleRate/frame.fftSize;boolean near=false;for(Peak p:result)if(Math.abs(p.frequency-hz)<3.*frame.sampleRate/frame.fftSize){near=true;break;}if(!near)result.add(new Peak(hz,frame.db[bin]));}
        return result;
    }
}
