package org.simpleweather.app;

/** One selected hue, with legible dark foregrounds and a pale companion surface. */
public final class ThemeColors {
    public static final int BLUE=0xFF246BCE;
    public static final int[] PRESETS={0xFFB83232,0xFFAD540D,0xFF806500,0xFF247446,0xFF087681,BLUE,0xFF7852A8};
    public static final int GRAY=0xFF616161;
    public static int roleDefault(String role){return "ui_button_color".equals(role)?BLUE:"ui_note_color".equals(role)?GRAY:0xFF000000;}
    public static int presetIndex(int color){for(int i=0;i<PRESETS.length;i++)if(PRESETS[i]==color)return i;return -1;}
    public static int opaque(int value){return (value>>>24)==255?value:BLUE;}
    public static int dark(int seed){int c=opaque(seed);while(contrast(c,0xFFFFFFFF)<4.8||contrast(c,pale(seed))<4.8)c=mix(c,0xFF000000,.96);return c;}
    public static int pale(int seed){return mix(opaque(seed),0xFFFFFFFF,.10);}
    private static int mix(int a,int b,double portion){int out=0xFF000000;for(int shift:new int[]{16,8,0})out|=((int)Math.round(((a>>shift)&255)*portion+((b>>shift)&255)*(1-portion)))<<shift;return out;}
    public static double luminance(int c){double[] channel=new double[3];for(int i=0;i<3;i++){double x=((c>>(16-i*8))&255)/255.;channel[i]=x<=.04045?x/12.92:Math.pow((x+.055)/1.055,2.4);}return .2126*channel[0]+.7152*channel[1]+.0722*channel[2];}
    public static double contrast(int a,int b){double x=luminance(a),y=luminance(b);return (Math.max(x,y)+.05)/(Math.min(x,y)+.05);}
    public static String hex(int c){return String.format(java.util.Locale.ROOT,"#%06X",c&0xFFFFFF);}
    public static int parse(String text){if(text==null||!text.matches("#[0-9a-fA-F]{6}"))return BLUE;return 0xFF000000|Integer.parseInt(text.substring(1),16);}
    public static int legacyCourse(int index){int[] colors={BLUE,0xFF247446,0xFFAD540D,0xFF7852A8,0xFFB83232,0xFF087681};return index>=0&&index<colors.length?colors[index]:BLUE;}
}
