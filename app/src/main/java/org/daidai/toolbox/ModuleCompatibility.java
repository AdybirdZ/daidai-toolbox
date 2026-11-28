package org.daidai.toolbox;

/** The interface revision is independent of the host's display/release version. */
public final class ModuleCompatibility {
    private ModuleCompatibility(){}
    public static boolean accepts(String requiredApi,String hostApi,int minimumHost,int currentHost,int minimumAndroid,int currentAndroid){
        return requiredApi!=null&&requiredApi.equals(hostApi)&&minimumHost>=0&&minimumHost<=currentHost&&minimumAndroid>=0&&minimumAndroid<=currentAndroid;
    }
    /** Store packages may match or trail the host release, but can never get ahead of it. */
    public static boolean releaseAllowed(String appVersion,String hostVersion){
        int[] app=releaseParts(appVersion),host=releaseParts(hostVersion);if(app==null||host==null)return false;
        for(int i=0;i<3;i++)if(app[i]!=host[i])return app[i]<host[i];return true;
    }
    private static int[] releaseParts(String value){
        if(value==null||!value.matches("[0-9]+\\.[0-9]+\\.[0-9]+"))return null;
        String[] parts=value.split("\\.");try{return new int[]{Integer.parseInt(parts[0]),Integer.parseInt(parts[1]),Integer.parseInt(parts[2])};}catch(NumberFormatException invalid){return null;}
    }
    public static boolean validLength(long advertised,long received,long maximum){return received>=0&&received<=maximum&&(advertised<=0||advertised==received);}
}
