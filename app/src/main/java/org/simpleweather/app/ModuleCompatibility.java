package org.simpleweather.app;

/** The interface revision is independent of the host's display/release version. */
public final class ModuleCompatibility {
    private ModuleCompatibility(){}
    public static boolean accepts(String requiredApi,String hostApi,int minimumHost,int currentHost,int minimumAndroid,int currentAndroid){
        return requiredApi!=null&&requiredApi.equals(hostApi)&&minimumHost>=0&&minimumHost<=currentHost&&minimumAndroid>=0&&minimumAndroid<=currentAndroid;
    }
    public static boolean validLength(long advertised,long received,long maximum){return received>=0&&received<=maximum&&(advertised<=0||advertised==received);}
}
