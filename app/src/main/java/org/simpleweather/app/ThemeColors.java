package org.simpleweather.app;

/** One selected hue, with legible dark foregrounds and a pale companion surface. */
final class ThemeColors {
    static final int BLUE=0xFF246BCE;
    static final int[] PRESETS={0xFFB83232,0xFFAD540D,0xFF806500,0xFF247446,0xFF087681,BLUE,0xFF7852A8};
    static int opaque(int value){return (value>>>24)==255?value:BLUE;}
    static int dark(int seed){int c=opaque(seed);while(contrast(c,0xFFFFFFFF)<4.8||contrast(c,pale(seed))<4.8)c=mix(c,0xFF000000,.96);return c;}
    static int pale(int seed){return mix(opaque(seed),0xFFFFFFFF,.10);}
    private static int mix(int a,int b,double portion){int out=0xFF000000;for(int shift:new int[]{16,8,0})out|=((int)Math.round(((a>>shift)&255)*portion+((b>>shift)&255)*(1-portion)))<<shift;return out;}
    static double luminance(int c){double[] channel=new double[3];for(int i=0;i<3;i++){double x=((c>>(16-i*8))&255)/255.;channel[i]=x<=.04045?x/12.92:Math.pow((x+.055)/1.055,2.4);}return .2126*channel[0]+.7152*channel[1]+.0722*channel[2];}
    static double contrast(int a,int b){double x=luminance(a),y=luminance(b);return (Math.max(x,y)+.05)/(Math.min(x,y)+.05);}
    static String hex(int c){return String.format(java.util.Locale.ROOT,"#%06X",c&0xFFFFFF);}
    static int parse(String text){if(text==null||!text.matches("#[0-9a-fA-F]{6}"))return BLUE;return 0xFF000000|Integer.parseInt(text.substring(1),16);}
    static int legacyCourse(int index){int[] colors={BLUE,0xFF247446,0xFFAD540D,0xFF7852A8,0xFFB83232,0xFF087681};return index>=0&&index<colors.length?colors[index]:BLUE;}
}
