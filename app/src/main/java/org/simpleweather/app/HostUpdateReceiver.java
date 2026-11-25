package org.simpleweather.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.widget.Toast;
import android.util.Log;

/** Deprecated compatibility receiver; manual update downloads no longer use it. */
@Deprecated public final class HostUpdateReceiver extends BroadcastReceiver {
    static final String ACTION="org.simpleweather.app.HOST_UPDATE_INSTALL";
    @Override public void onReceive(Context context,Intent intent){
        int status=intent.getIntExtra(PackageInstaller.EXTRA_STATUS,PackageInstaller.STATUS_FAILURE);
        String message=intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
        context.getSharedPreferences("toolbox",0).edit().putInt("host_update_last_status",status).putString("host_update_last_message",message==null?"":message).apply();
        Log.w("HostUpdate","Deprecated automatic update callback ignored; install the APK from Downloads manually");
    }
}
