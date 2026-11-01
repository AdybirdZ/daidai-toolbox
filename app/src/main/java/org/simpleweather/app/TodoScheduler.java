package org.simpleweather.app;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import java.time.*;
import java.util.*;

/** One durable next alarm; no polling service. Delivery history is local runtime state. */
final class TodoScheduler {
    static final String REMIND="todo_reminders_v1",SUMMARY="todo_summary_v1";
    private static final int SUMMARY_ID=210000;
    private static boolean suspended;
    private static Context local(Context c){return AppLanguage.wrap(c.getApplicationContext());}
    static boolean allowed(Context c){return (Build.VERSION.SDK_INT<33||c.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED)&&c.getSystemService(NotificationManager.class).areNotificationsEnabled();}
    static boolean exact(Context c){return Build.VERSION.SDK_INT<31||c.getSystemService(AlarmManager.class).canScheduleExactAlarms();}
    static void notificationSettings(Context c){try{c.startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,c.getPackageName()));}catch(Exception ignored){}}
    static void exactSettings(Context c){try{if(Build.VERSION.SDK_INT>=31)c.startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+c.getPackageName())));}catch(Exception ignored){}}
    private static SharedPreferences prefs(Context c){return c.getSharedPreferences("toolbox",0);}
    private static SharedPreferences runtime(Context c){return c.getSharedPreferences("todo_runtime",0);}
    private static PendingIntent alarm(Context c){return PendingIntent.getBroadcast(c,0,new Intent(c,TodoReceiver.class).setAction("todo.tick"),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    private static int id(String id){return 220001;}
    static synchronized void suspend(Context c){suspended=true;c.getSystemService(AlarmManager.class).cancel(alarm(c));c.getSystemService(NotificationManager.class).cancel("todo",SUMMARY_ID);try{for(TodoModel.Item i:TodoStore.load(c).items)c.getSystemService(NotificationManager.class).cancel("todo:"+i.id,id(i.id));}catch(Exception ignored){}}
    static synchronized void afterImport(Context c){suspended=false;runtime(c).edit().clear().putLong("not_before",System.currentTimeMillis()).commit();refresh(c,false);}
    static synchronized void changed(Context c){NotificationManager nm=c.getSystemService(NotificationManager.class);for(android.service.notification.StatusBarNotification n:nm.getActiveNotifications())if(n.getTag()!=null&&n.getTag().startsWith("todo"))nm.cancel(n.getTag(),n.getId());refresh(c,false);}
    static synchronized void receive(Context c,Intent intent){
        if(suspended)return;
        String action=intent.getAction();
        if("todo.complete".equals(action)||"todo.snooze".equals(action))try{
            TodoModel m=TodoStore.load(c);TodoModel.Item i=m.find(intent.getStringExtra("id"));
            if(i!=null&&System.currentTimeMillis()<TodoModel.deleteAt(i)&&i.updated==intent.getLongExtra("updated",-1)){
                LocalDate day=LocalDate.parse(intent.getStringExtra("day"));
                if(TodoModel.occurs(i,day)){
                    if("todo.complete".equals(action)){TodoModel.complete(i,day,System.currentTimeMillis());TodoStore.save(c,m);runtime(c).edit().remove("snooze:"+i.id).commit();}
                    else runtime(c).edit().putString("snooze:"+i.id,day+"|"+i.updated+"|"+(System.currentTimeMillis()+600000)).commit();
                }
                c.getSystemService(NotificationManager.class).cancel("todo:"+i.id,id(i.id));
            }
        }catch(Exception ignored){}
        refresh(c,true);
    }
    private static void channels(Context c){NotificationManager nm=c.getSystemService(NotificationManager.class);NotificationChannel reminder=new NotificationChannel(REMIND,c.getString(R.string.todo_channel_reminder),NotificationManager.IMPORTANCE_HIGH);reminder.setDescription(c.getString(R.string.todo_channel_note));reminder.enableVibration(true);nm.createNotificationChannel(reminder);NotificationChannel summary=new NotificationChannel(SUMMARY,c.getString(R.string.todo_channel_summary),NotificationManager.IMPORTANCE_LOW);summary.setSound(null,null);summary.setShowBadge(false);nm.createNotificationChannel(summary);}
    private static PendingIntent open(Context c){return PendingIntent.getActivity(c,210,new Intent(c,MainActivity.class).putExtra("open_todo",true).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    private static PendingIntent action(Context c,String action,TodoModel.Item i,LocalDate day){return PendingIntent.getBroadcast(c,0,new Intent(c,TodoReceiver.class).setAction(action).setData(Uri.parse("todo:"+i.id+":"+i.updated+":"+day+":"+action)).putExtra("id",i.id).putExtra("updated",i.updated).putExtra("day",day.toString()),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    private static Notification.Builder base(Context c,String channel){return new Notification.Builder(c,channel).setSmallIcon(android.R.drawable.ic_menu_my_calendar).setContentIntent(open(c)).setCategory(Notification.CATEGORY_REMINDER).setVisibility(prefs(c).getBoolean("todo_private",false)?Notification.VISIBILITY_SECRET:Notification.VISIBILITY_PUBLIC);}
    private static void post(Context c,TodoModel.Event event){
        TodoModel.Item i=event.item;boolean privacy=prefs(c).getBoolean("todo_private",false);
        String title=privacy?c.getString(R.string.tab_todo):i.title;
        String body=c.getString(event.end?R.string.todo_due_end:R.string.todo_due_start)+(privacy?"":" · "+event.day+(i.note.isEmpty()?"":"\n"+i.note));
        Notification n=base(c,REMIND).setContentTitle(title).setContentText(body).setStyle(new Notification.BigTextStyle().bigText(body)).setAutoCancel(true).setOnlyAlertOnce(false).setWhen(event.when)
            .addAction(new Notification.Action.Builder(null,c.getString(R.string.todo_complete),action(c,"todo.complete",i,event.day)).build())
            .addAction(new Notification.Action.Builder(null,c.getString(R.string.todo_snooze),action(c,"todo.snooze",i,event.day)).build()).build();
        try{c.getSystemService(NotificationManager.class).notify("todo:"+i.id,id(i.id),n);}catch(SecurityException ignored){}
    }
    static synchronized void refresh(Context original,boolean dispatch){
        Context c=local(original);SharedPreferences rt=runtime(c),p=prefs(c);if(suspended)return;
        try{
            channels(c);TodoModel model=TodoStore.load(c);long now=System.currentTimeMillis();ZoneId zone=ZoneId.systemDefault();
            List<TodoModel.Item> expired=new ArrayList<>();for(TodoModel.Item i:model.items)if(now>=TodoModel.deleteAt(i))expired.add(i);
            if(model.prune(now)){TodoStore.save(c,model);for(TodoModel.Item i:expired){c.getSystemService(NotificationManager.class).cancel("todo:"+i.id,id(i.id));runtime(c).edit().remove("snooze:"+i.id).commit();}}
            long next=LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
            for(TodoModel.Item i:model.items)next=Math.min(next,TodoModel.deleteAt(i));
            Map<String,?> old=rt.getAll();SharedPreferences.Editor edit=rt.edit();for(Map.Entry<String,?> e:old.entrySet())if(e.getKey().startsWith("sent:")&&e.getValue() instanceof Long&&((Long)e.getValue())<now-172800000L)edit.remove(e.getKey());
            Set<String> delivered=new HashSet<>();
            if(p.getBoolean("todo_notify",true)&&allowed(c))for(TodoModel.Item i:model.items){
                List<TodoModel.Event> events=TodoModel.events(i,now,zone,p.getInt("todo_day_minute",540),rt.getLong("not_before",0));events.sort(Comparator.comparingLong(e->e.when));
                TodoModel.Event due=null;
                for(TodoModel.Event event:events){if(rt.contains("sent:"+event.key))continue;if(event.when<=now){if(dispatch){edit.putLong("sent:"+event.key,now);due=event;}else next=Math.min(next,now+1000);}else next=Math.min(next,event.when);}
                String snooze=rt.getString("snooze:"+i.id,"");if(!snooze.isEmpty())try{String[] s=snooze.split("\\|");LocalDate day=LocalDate.parse(s[0]);long updated=Long.parseLong(s[1]),at=Long.parseLong(s[2]);if(updated!=i.updated||!TodoModel.occurs(i,day)){edit.remove("snooze:"+i.id);}else if(at<=now&&dispatch){edit.remove("snooze:"+i.id);if(at>=now-86400000L)due=new TodoModel.Event(i,day,-1,at,false);}else next=Math.min(next,Math.max(now+1000,at));}catch(Exception e){edit.remove("snooze:"+i.id);}
                if(due!=null&&delivered.size()<5){post(c,due);delivered.add(i.id);}
            }
            edit.commit();summary(c,model,now,zone);
            AlarmManager manager=c.getSystemService(AlarmManager.class);manager.cancel(alarm(c));
            boolean tasks=false;for(TodoModel.Item i:model.items)if(!i.done||TodoModel.deleteAt(i)!=Long.MAX_VALUE){tasks=true;break;}
            if(tasks){try{if(exact(c))manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,next,alarm(c));else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,next,alarm(c));}catch(SecurityException e){manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,next,alarm(c));}}
        }catch(Exception ignored){} // Corrupt data remains intact; the screen reports the problem.
    }
    private static void summary(Context c,TodoModel m,long now,ZoneId zone){NotificationManager nm=c.getSystemService(NotificationManager.class);SharedPreferences p=prefs(c);List<TodoModel.Occurrence> rows=new ArrayList<>();int scope=p.getInt("todo_summary_scope",0);long tomorrow=LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();for(TodoModel.Item i:m.items){TodoModel.Occurrence o=TodoModel.next(i,now,zone);if(o!=null&&(scope==1||scope==2&&i.pin||scope==0&&o.start<tomorrow))rows.add(o);}rows.sort(Comparator.comparingLong(o->o.start));if(!p.getBoolean("todo_summary",true)||!allowed(c)||rows.isEmpty()){nm.cancel("todo",SUMMARY_ID);return;}
        String title=c.getString(R.string.todo_pending_count,rows.size());StringJoiner lines=new StringJoiner("\n");if(!p.getBoolean("todo_private",false))for(int n=0;n<Math.min(rows.size(),p.getInt("todo_summary_count",3));n++)lines.add(rows.get(n).item.title);String body=lines.length()==0?c.getString(R.string.todo_open):lines.toString();try{nm.notify("todo",SUMMARY_ID,base(c,SUMMARY).setContentTitle(title).setContentText(body).setStyle(new Notification.BigTextStyle().bigText(body)).setOngoing(true).setOnlyAlertOnce(true).setShowWhen(false).build());}catch(SecurityException ignored){}
    }
}
