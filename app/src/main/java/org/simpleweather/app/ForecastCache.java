package org.simpleweather.app;
import java.io.File;
/** Only forecast files owned by this app; never preferences, courses or backgrounds. */
final class ForecastCache {
    static boolean clear(File directory){
        File[] files=directory.listFiles((dir,name)->name.startsWith("forecast_")&&(name.endsWith(".json")||name.endsWith(".json.tmp")));
        if(files==null)return false;boolean ok=true;
        for(File file:files)if(file.isFile()&&!file.delete())ok=false;
        return ok;
    }
}
