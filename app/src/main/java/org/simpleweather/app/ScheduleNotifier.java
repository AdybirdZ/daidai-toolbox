package org.simpleweather.app;
import android.app.*;
import android.content.*;
/** Android broadcast bridge; course scheduling belongs to Timetable. */
public final class ScheduleNotifier {
 public static void receive(Context c,Intent i){refresh(c,true);}
 public static void refresh(Context c,boolean dispatch){refreshAt(c,System.currentTimeMillis(),dispatch);}
 public static void refreshAt(Context c,long now,boolean dispatch){
  if(AppModules.present(c,"schedule"))FeatureAccess.call(c,"schedule","ScheduleNotifierImpl","refreshAt",new Class<?>[]{Context.class,long.class,boolean.class},c,now,dispatch);
  else{c.getSystemService(AlarmManager.class).cancel(PendingIntent.getBroadcast(c,230001,new Intent(c,TodoReceiver.class).setAction("course.tick"),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));c.getSystemService(NotificationManager.class).cancel("course",230002);}
 }
}
