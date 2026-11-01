package org.simpleweather.app;
import android.content.*;
public final class TodoReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){TodoScheduler.receive(context,intent);}
}
