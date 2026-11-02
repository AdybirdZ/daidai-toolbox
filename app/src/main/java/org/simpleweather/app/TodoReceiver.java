package org.simpleweather.app;
import android.content.*;
public final class TodoReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){if(intent.getAction()!=null&&intent.getAction().startsWith("course."))ScheduleNotifier.receive(context,intent);else TodoScheduler.receive(context,intent);}
}
