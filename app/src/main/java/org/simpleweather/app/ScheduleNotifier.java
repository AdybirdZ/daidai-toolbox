package org.simpleweather.app;

import android.app.*;
import android.content.*;
import android.os.Build;
import java.time.*;
import java.time.format.DateTimeFormatter;

/** A system notification chronometer updates without per-minute application wakeups. */
public final class ScheduleNotifier {
    private static final String CHANNEL="course_reminders_v1";private static final int ALARM=230001,NOTE=230002;
    private static PendingIntent alarm(Context c){return PendingIntent.getBroadcast(c,ALARM,new Intent(c,TodoReceiver.class).setAction("course.tick"),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    public static void receive(Context context,Intent intent){refresh(context,true);}
    public static synchronized void refresh(Context context,boolean dispatch){refreshAt(context,System.currentTimeMillis(),dispatch);}
    public static synchronized void refreshAt(Context original,long now,boolean dispatch){
        Context c=AppLanguage.wrap(original.getApplicationContext());AlarmManager am=c.getSystemService(AlarmManager.class);am.cancel(alarm(c));NotificationManager nm=c.getSystemService(NotificationManager.class);
        if(!AppModules.present(c,"schedule")||!c.getSharedPreferences("toolbox",0).getBoolean("course_notify",true)){nm.cancel("course",NOTE);return;}
        NotificationChannel channel=new NotificationChannel(CHANNEL,c.getString(R.string.course_notify_channel),NotificationManager.IMPORTANCE_HIGH);channel.setDescription(c.getString(R.string.course_notify_channel_note));nm.createNotificationChannel(channel);
        ScheduleModel m;try{String raw=c.getSharedPreferences("timetable",0).getString("data","");m=raw.isEmpty()?new ScheduleModel():ScheduleModel.parse(raw);}catch(Exception e){nm.cancel("course",NOTE);return;}
        ZoneId zone=ZoneId.systemDefault();int lead=c.getSharedPreferences("toolbox",0).getInt("course_notify_lead",30),remove=c.getSharedPreferences("toolbox",0).getInt("course_notify_remove",10);
        long next=Long.MAX_VALUE,activeStart=Long.MAX_VALUE;ScheduleModel.Course active=null;LocalDate today=Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
        for(int d=0;d<14;d++){LocalDate day=today.plusDays(d);int week=m.week(day);if(week<1||week>m.weeks)continue;
            for(ScheduleModel.Course course:m.courses)if(course.day==day.getDayOfWeek().getValue()&&course.runs(week)){
                long start=day.atTime(LocalTime.parse(m.slots.get(course.first-1).start)).atZone(zone).toInstant().toEpochMilli(),show=start-lead*60000L,hide=start+remove*60000L;
                if(now>=show&&now<hide&&(active==null||start<activeStart)){active=course;activeStart=start;}
                if(show>now)next=Math.min(next,show);if(start>now)next=Math.min(next,start);if(hide>now)next=Math.min(next,hide);
            }
        }
        if(active!=null&&dispatch&&allowed(c)){
            boolean countdown=now<activeStart;ZoneId display=EarthquakeOptions.zone(c.getSharedPreferences("toolbox",0).getInt("global_time_mode",0));
            String at=DateTimeFormatter.ofPattern("HH:mm").withZone(display).format(Instant.ofEpochMilli(activeStart))+" · "+EarthquakeOptions.offset(display,activeStart);
            String body=countdown?c.getString(R.string.course_notify_starts_at,at):c.getString(R.string.course_notify_started);
            Notification.Builder b=new Notification.Builder(c,CHANNEL).setSmallIcon(android.R.drawable.ic_menu_agenda).setContentTitle(active.name).setContentText(body).setStyle(new Notification.BigTextStyle().bigText(body))
                .setContentIntent(PendingIntent.getActivity(c,231,new Intent(c,MainActivity.class).putExtra("last_tab",1),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE))
                .setAutoCancel(false).setOngoing(true).setOnlyAlertOnce(true).setWhen(activeStart).setShowWhen(countdown).setUsesChronometer(countdown).setChronometerCountDown(countdown);
            try{nm.notify("course",NOTE,b.build());}catch(SecurityException ignored){}
        }else nm.cancel("course",NOTE);
        if(next!=Long.MAX_VALUE){try{am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,next,alarm(c));}catch(SecurityException e){am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,next,alarm(c));}}
    }
    private static boolean allowed(Context c){return c.getSystemService(NotificationManager.class).areNotificationsEnabled()&&(Build.VERSION.SDK_INT<33||c.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)==android.content.pm.PackageManager.PERMISSION_GRANTED);}
}
