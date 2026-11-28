package org.simpleweather.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Forwards system-held reminder PendingIntents created before the source namespace migration. */
public final class TodoReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        new org.daidai.toolbox.TodoReceiver().onReceive(context, intent);
    }
}
