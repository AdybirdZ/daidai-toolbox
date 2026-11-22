package org.simpleweather.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.widget.Toast;

/** Hands Android's mandatory package confirmation screen to the user. */
public final class HostUpdateReceiver extends BroadcastReceiver {
    static final String ACTION="org.simpleweather.app.HOST_UPDATE_INSTALL";
    @Override public void onReceive(Context context,Intent intent){
        int status=intent.getIntExtra(PackageInstaller.EXTRA_STATUS,PackageInstaller.STATUS_FAILURE);
        if(status==PackageInstaller.STATUS_PENDING_USER_ACTION){
            Intent confirmation=intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if(confirmation!=null){confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);context.startActivity(confirmation);}
        }else if(status==PackageInstaller.STATUS_SUCCESS){
            HostUpdateManager.pendingFile(context).delete();
            Toast.makeText(context,context.getString(R.string.host_update_install_complete),Toast.LENGTH_LONG).show();
        }else{
            Toast.makeText(context,context.getString(R.string.host_update_install_failed),Toast.LENGTH_LONG).show();
        }
    }
}
