package org.daidai.toolbox;
import android.app.*;
import android.content.*;
import android.os.Build;
/** Android entry bridge; reminder scheduling lives in the installed To-do package. */
public final class TodoScheduler {
 public static final String REMIND="todo_reminders_v1",SUMMARY="todo_summary_v1";
 private static void invoke(Context c,String name,Class<?>[] types,Object...args){FeatureAccess.call(c,"todo","TodoSchedulerImpl",name,types,args);}
 public static boolean allowed(Context c){return (Build.VERSION.SDK_INT<33||c.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)==android.content.pm.PackageManager.PERMISSION_GRANTED)&&c.getSystemService(NotificationManager.class).areNotificationsEnabled();}
 public static boolean exact(Context c){return Build.VERSION.SDK_INT<31||c.getSystemService(AlarmManager.class).canScheduleExactAlarms();}
 public static void notificationSettings(Context c){try{c.startActivity(new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,c.getPackageName()));}catch(Exception ignored){}}
 public static void exactSettings(Context c){try{if(Build.VERSION.SDK_INT>=31)c.startActivity(new Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,android.net.Uri.parse("package:"+c.getPackageName())));}catch(Exception ignored){}}
 public static void suspend(Context c){if(AppModules.present(c,"todo"))invoke(c,"suspend",new Class<?>[]{Context.class},c);else clear(c);}
 private static void clear(Context c){AlarmManager am=c.getSystemService(AlarmManager.class);am.cancel(PendingIntent.getBroadcast(c,0,new Intent(c,TodoReceiver.class).setAction("todo.tick"),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));NotificationManager nm=c.getSystemService(NotificationManager.class);for(android.service.notification.StatusBarNotification n:nm.getActiveNotifications())if(n.getTag()!=null&&n.getTag().startsWith("todo"))nm.cancel(n.getTag(),n.getId());}
 public static void afterImport(Context c){c.getSharedPreferences("todo_runtime",0).edit().clear().putLong("not_before",System.currentTimeMillis()).commit();invoke(c,"afterImport",new Class<?>[]{Context.class},c);}
 public static void changed(Context c){invoke(c,"changed",new Class<?>[]{Context.class},c);}
 public static void receive(Context c,Intent i){invoke(c,"receive",new Class<?>[]{Context.class,Intent.class},c,i);}
 public static void refresh(Context c,boolean dispatch){if(AppModules.present(c,"todo"))invoke(c,"refresh",new Class<?>[]{Context.class,boolean.class},c,dispatch);else clear(c);}
}
