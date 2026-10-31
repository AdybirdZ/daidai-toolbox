package org.simpleweather.app;

import android.app.*;
import android.content.*;
import android.graphics.Typeface;
import android.view.*;
import android.widget.*;
import android.text.InputType;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Original native weekly timetable, inspired by the reference project's feature layout. */
final class ScheduleScreen {
    private final MainActivity a;
    private final android.content.SharedPreferences prefs;
    private ScheduleModel model;
    private int selectedWeek;
    private static final String[] DAYS={"周一","周二","周三","周四","周五","周六","周日"};
    ScheduleScreen(MainActivity activity){a=activity;prefs=a.getSharedPreferences("timetable",0);String raw=prefs.getString("data","");try{model=raw.isEmpty()?new ScheduleModel():ScheduleModel.parse(raw);}catch(Exception e){prefs.edit().putString("unreadable_backup",raw).apply();model=new ScheduleModel();a.toast("原课表读取失败，原始数据已保留在本机备份");}selectedWeek=Math.min(model.weeks,Math.max(1,model.week(LocalDate.now())));}
    private void save(){try{prefs.edit().putString("data",model.json().toString()).apply();}catch(Exception e){a.toast("保存失败，请重试");}}
    void addCourse(){edit(null);}
    void render(){

        int current=model.week(LocalDate.now());String semesterStatus=current<1?"学期尚未开始":current>model.weeks?"学期已结束":"当前第 "+current+" 周";
        a.body.addView(a.text(semesterStatus+" · 本机时间 · 周一为每周起点",12,a.MUTED,false));a.gap(a.body,12);
        LinearLayout nav=a.row();nav.addView(a.button("‹",false,()->{if(selectedWeek>1){selectedWeek--;a.switchTab(1);}}),a.lp(48,48));a.gapHorizontal(nav,8);TextView week=a.button("第 "+selectedWeek+" / "+model.weeks+" 周",false,this::selectWeek);nav.addView(week,new LinearLayout.LayoutParams(0,-2,1));a.gapHorizontal(nav,8);nav.addView(a.button("›",false,()->{if(selectedWeek<model.weeks){selectedWeek++;a.switchTab(1);}}),a.lp(48,48));a.body.addView(nav);a.gap(a.body,10);
        LocalDate monday=model.semester.plusWeeks(selectedWeek-1);a.body.addView(a.text(monday+"  至  "+monday.plusDays(6),12,a.MUTED,false));a.gap(a.body,16);
        today(current);a.gap(a.body,16);grid(monday);a.gap(a.body,16);a.body.addView(a.text("全部课程 · 点击编辑或删除",17,a.TITLE,true));a.gap(a.body,12);
        if(model.courses.isEmpty()){LinearLayout empty=a.card();empty.addView(a.text("暂无课程",17,a.TITLE,true));a.gap(empty,10);empty.addView(a.text("请先在课程表设置中设置学期及上课时间，再添加课程。",13,a.MUTED,false));a.gap(empty,12);empty.addView(a.button("新增课程",true,()->edit(null)));}
        List<ScheduleModel.Course> sorted=new ArrayList<>(model.courses);sorted.sort(Comparator.comparingInt((ScheduleModel.Course c)->c.day).thenComparingInt(c->c.first).thenComparingInt(c->c.weekStart));
        for(ScheduleModel.Course c:sorted){LinearLayout card=a.card();card.setBackground(a.shape(ThemeColors.pale(c.seedColor()),18));card.addView(a.text(nameText(c),18,a.TITLE,true));a.gap(card,8);card.addView(a.text(DAYS[c.day-1]+" · 第 "+c.first+"–"+c.last+" 节 · "+model.courseTime(c),13,a.INK,false));a.gap(card,7);card.addView(a.text(roomText(c)+"\n"+teacherText(c)+"\n"+model.weekLabel(c),13,a.MUTED,false));a.tap(card,ThemeColors.pale(c.seedColor()),18,()->edit(c));a.gap(a.body,10);}

    }
    private void today(int week){LinearLayout c=a.card();c.addView(a.text("今日课程 · "+DAYS[LocalDate.now().getDayOfWeek().getValue()-1],17,a.TITLE,true));a.gap(c,12);int n=0;for(ScheduleModel.Course course:model.courses)if(course.day==LocalDate.now().getDayOfWeek().getValue()&&course.runs(week)){c.addView(a.text(model.courseTime(course)+"  "+nameText(course)+"  "+roomText(course)+"  "+teacherText(course),13,a.INK,false));a.gap(c,7);n++;}if(n==0)c.addView(a.text("今日无课程安排",13,a.MUTED,false));}
    private void selectWeek(){LinearLayout c=form();EditText input=field(c,"周次（1–"+model.weeks+"）",String.valueOf(selectedWeek),true);AlertDialog dialog=new ToolDialog(a).setTitle("输入周次").setView(c).setPositiveButton("确定",null).setNegativeButton("取消",null).create();dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{try{int week=Integer.parseInt(input.getText().toString().trim());if(week<1||week>model.weeks)throw new NumberFormatException();selectedWeek=week;dialog.dismiss();a.switchTab(1);}catch(NumberFormatException e){input.setError("请输入 1–"+model.weeks+" 之间的周次");}}));dialog.show();}
    private void grid(LocalDate monday){
        LinearLayout c=a.card();c.addView(a.text("一周课表 · 左右滑动",17,a.TITLE,true));a.gap(c,12);HorizontalScrollView scroll=new HorizontalScrollView(a);LinearLayout grid=a.column();int label=58,width=112,height=126;
        LinearLayout heading=a.row();heading.addView(a.text("节次",12,a.MUTED,true),a.lp(label,48));for(int day=1;day<=7;day++){LocalDate date=monday.plusDays(day-1);TextView t=a.text(DAYS[day-1]+"\n"+date.format(DateTimeFormatter.ofPattern("M/d")),12,date.equals(LocalDate.now())?a.BLUE:a.INK,true);t.setGravity(Gravity.CENTER);heading.addView(t,a.lp(width,48));}grid.addView(heading);
        FrameLayout matrix=new FrameLayout(a);matrix.setLayoutParams(a.lp(label+7*width,model.slots.size()*height));
        for(int i=0;i<model.slots.size();i++){
            ScheduleModel.Slot slot=model.slots.get(i);TextView time=a.text((i+1)+"\n"+slot.start+"\n"+slot.end,11,a.MUTED,false);time.setGravity(Gravity.CENTER);FrameLayout.LayoutParams timeLp=new FrameLayout.LayoutParams(a.dp(label),a.dp(height));timeLp.topMargin=a.dp(i*height);matrix.addView(time,timeLp);
            for(int day=0;day<7;day++){View cell=new View(a);cell.setBackground(a.shape(0xFFF5F7FB,10));FrameLayout.LayoutParams cellLp=new FrameLayout.LayoutParams(a.dp(width-4),a.dp(height-4));cellLp.leftMargin=a.dp(label+day*width);cellLp.topMargin=a.dp(i*height);matrix.addView(cell,cellLp);}
        }
        for(ScheduleModel.Course course:model.courses)if(course.runs(selectedWeek)){
            TextView block=a.text(nameText(course)+(course.room.isEmpty()?"":"\n"+roomText(course))+(course.teacher.isEmpty()?"":"\n"+teacherText(course)),12,a.INK,true);block.setPadding(a.dp(7),a.dp(8),a.dp(7),a.dp(6));block.setMaxLines(Math.max(6,(course.last-course.first+1)*7));block.setEllipsize(android.text.TextUtils.TruncateAt.END);a.tap(block,ThemeColors.pale(course.seedColor()),10,()->edit(course));FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(a.dp(width-4),a.dp((course.last-course.first+1)*height-4));p.leftMargin=a.dp(label+(course.day-1)*width);p.topMargin=a.dp((course.first-1)*height);matrix.addView(block,p);
        }
        grid.addView(matrix);scroll.addView(grid);c.addView(scroll);
    }
    void resetSettings(){model.resetSettings();selectedWeek=1;save();}
    void menu(){LinearLayout c=form();c.addView(a.button("学期设置",false,this::semester));a.gap(c,10);c.addView(a.button("自定义上课时间",false,this::times));a.gap(c,10);c.addView(a.button("课程信息前缀",false,this::prefixes));new ToolDialog(a).setTitle("课程表设置").setView(scroll(c)).setNegativeButton("返回",null).show();}
    private String nameText(ScheduleModel.Course c){return (a.preferences.getBoolean("course_name_prefix",true)?"课程名称：":"")+c.name;}
    private String roomText(ScheduleModel.Course c){int p=a.preferences.getInt("course_room_prefix",1);return (p==0?"@":p==1?"地点：":"")+(c.room.isEmpty()?"未填写":c.room);}
    private String teacherText(ScheduleModel.Course c){return (a.preferences.getBoolean("course_teacher_prefix",true)?"教师：":"")+(c.teacher.isEmpty()?"未填写":c.teacher);}
    private void prefixes(){LinearLayout c=form();String[] names={"上课地点前缀","课程名称前缀","教师前缀"};for(int n=0;n<names.length;n++){final int id=n;c.addView(a.button(names[n],false,()->prefixChoice(id)));a.gap(c,10);}new ToolDialog(a).setTitle("课程信息前缀").setView(c).setNegativeButton("返回",null).show();}
    private void prefixChoice(int n){
        String[] options=n==0?new String[]{"显示“地点：”（默认）","显示“@”","不显示前缀"}:n==1?new String[]{"显示“课程名称：”（默认）","不显示前缀"}:new String[]{"显示“教师：”（默认）","不显示前缀"};
        int saved=n==0?a.preferences.getInt("course_room_prefix",1):0;int selected=n==0?(saved==1?0:saved==0?1:2):(a.preferences.getBoolean(n==1?"course_name_prefix":"course_teacher_prefix",true)?0:1);
        new ToolDialog(a).setTitle(n==0?"上课地点前缀":n==1?"课程名称前缀":"教师前缀").setSingleChoiceItems(options,selected,(x,p)->{if(n==0)a.preferences.edit().putInt("course_room_prefix",p==0?1:p==1?0:2).apply();else a.preferences.edit().putBoolean(n==1?"course_name_prefix":"course_teacher_prefix",p==0).apply();a.refreshScreen();}).setNegativeButton("返回",null).show();
    }
    private EditText field(LinearLayout parent,String label,String value,boolean number){parent.addView(a.text(label,13,a.MUTED,false));EditText t=new EditText(a);t.setContentDescription(label);t.setText(value);t.setTextSize(16);t.setSingleLine(true);t.setPadding(a.dp(14),a.dp(6),a.dp(14),a.dp(6));t.setInputType(number?InputType.TYPE_CLASS_NUMBER:InputType.TYPE_CLASS_TEXT);t.setSelectAllOnFocus(true);t.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(number?3:80)});parent.addView(t,a.lp(-1,52));a.gap(parent,10);return t;}
    private Spinner spinner(LinearLayout c,String title,String[] labels,int selected){c.addView(a.text(title,13,a.MUTED,false));Spinner s=new Spinner(a);ArrayAdapter<String> adapter=new ChoiceAdapter(a,labels);s.setAdapter(adapter);s.setSelection(Math.max(0,Math.min(labels.length-1,selected)));c.addView(s,a.lp(-1,52));a.gap(c,10);return s;}
    private LinearLayout form(){LinearLayout c=a.column();c.setPadding(a.dp(20),a.dp(8),a.dp(20),a.dp(16));return c;}
    private ScrollView scroll(LinearLayout c){ScrollView s=new ScrollView(a);s.addView(c);return s;}
    private void semester(){
        LinearLayout c=form();c.addView(a.text("默认学期总周数为 17 周。",13,a.MUTED,false));a.gap(c,12);final LocalDate[] date={model.semester};TextView pick=a.button("第一周周一："+date[0],false,()->{ToolDialog.prepare(new DatePickerDialog(a,(v,y,m,d)->{LocalDate selected=LocalDate.of(y,m+1,d);if(selected.getDayOfWeek()!=DayOfWeek.MONDAY){a.toast("请选择第一周的周一");return;}date[0]=selected;semesterDate.setText("第一周周一："+selected);},date[0].getYear(),date[0].getMonthValue()-1,date[0].getDayOfMonth())).show();});semesterDate=pick;c.addView(pick);a.gap(c,14);EditText weeks=field(c,"学期总周数（1～60）",String.valueOf(model.weeks),true);
        AlertDialog dialog=new ToolDialog(a).setTitle("学期设置").setView(c).setPositiveButton("保存",null).setNegativeButton("取消",null).create();dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{try{int count=Integer.parseInt(weeks.getText().toString());if(count<1||count>60)throw new Exception();for(ScheduleModel.Course x:model.courses)if(x.parity==3?!x.customWeeks.isEmpty()&&x.customWeeks.last()>count:x.weekEnd>count){a.toast("已有课程安排到第 "+x.weekEnd+" 周，请先修改课程");return;}model.semester=date[0];model.weeks=count;selectedWeek=Math.max(1,Math.min(count,model.week(LocalDate.now())));save();dialog.dismiss();a.switchTab(1);}catch(Exception e){a.toast("请输入 1～60 之间的总周数");}}));dialog.show();
    }
    private TextView semesterDate;
    private void times(){
        LinearLayout c=form();c.addView(a.text("默认采用苏州大学课程时间。可逐节修改上课及下课时间，各节次须按时间先后排列且不得重叠。",13,a.MUTED,false));a.gap(c,14);
        for(int i=0;i<model.slots.size();i++){final int index=i;ScheduleModel.Slot slot=model.slots.get(i);c.addView(a.button("第 "+(i+1)+" 节    "+slot.start+"–"+slot.end,false,()->timeEditor(index)));a.gap(c,8);}
        c.addView(a.button("新增节次",false,()->{if(model.slots.size()>=20){a.toast("最多可设置 20 个节次");return;}timeEditor(model.slots.size());}));a.gap(c,8);
        c.addView(a.button("删除最后一个节次",false,()->{int last=model.slots.size();if(last<=1){a.toast("至少保留一个节次");return;}for(ScheduleModel.Course x:model.courses)if(x.last>=last){a.toast("该节次已被课程使用，请先修改相关课程");return;}new ToolDialog(a).setMessage("删除第 "+last+" 节？").setPositiveButton("删除",(d,n)->{model.slots.remove(last-1);save();timesDialog.dismiss();times();}).setNegativeButton("取消",null).show();}));
        timesDialog=new ToolDialog(a).setTitle("自定义上课时间").setView(scroll(c)).setNegativeButton("返回",(d,n)->a.refreshScreen()).create();timesDialog.show();
    }
    private AlertDialog timesDialog;
    private void timeEditor(int index){
        boolean adding=index==model.slots.size();String start=adding?(ScheduleModel.minute(model.slots.get(index-1).end)<=1370?LocalTime.parse(model.slots.get(index-1).end).plusMinutes(10).toString():"23:00"):model.slots.get(index).start;String end=adding?(ScheduleModel.minute(start)<=1394?LocalTime.parse(start).plusMinutes(45).toString():"23:59"):model.slots.get(index).end;final String[] values={start,end};LinearLayout c=form();TextView begin=a.button("上课："+start,false,()->{}),finish=a.button("下课："+end,false,()->{});begin.setOnClickListener(v->pickTime(values,0,begin,"上课"));finish.setOnClickListener(v->pickTime(values,1,finish,"下课"));c.addView(begin);a.gap(c,12);c.addView(finish);
        AlertDialog dialog=new ToolDialog(a).setTitle("第 "+(index+1)+" 节").setView(c).setPositiveButton("保存",null).setNegativeButton("取消",null).create();dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{String error=model.validateSlot(index,values[0],values[1]);if(error!=null){a.toast(error);return;}if(adding)model.slots.add(new ScheduleModel.Slot(values[0],values[1]));else model.slots.set(index,new ScheduleModel.Slot(values[0],values[1]));save();dialog.dismiss();timesDialog.dismiss();times();}));dialog.show();
    }
    private void pickTime(String[] values,int index,TextView view,String label){NumericInputs.time(a,LocalTime.parse(values[index]),time->{values[index]=time.toString();view.setText(label+"："+values[index]);});}
    private void edit(ScheduleModel.Course old){
        if(old==null&&model.courses.size()>=300){a.toast("课程数量已达上限");return;}ScheduleModel.Course course=old==null?new ScheduleModel.Course():old.copy();if(old==null)course.weekEnd=model.weeks;
        LinearLayout c=form();EditText name=field(c,"课程名称（必填）",course.name,false),room=field(c,"上课地点",course.room,false),teacher=field(c,"任课教师",course.teacher,false);Spinner day=spinner(c,"星期",DAYS,course.day-1);
        String[] periods=new String[model.slots.size()];for(int i=0;i<periods.length;i++)periods[i]="第 "+(i+1)+" 节 · "+model.slots.get(i).start+"–"+model.slots.get(i).end;
        Spinner first=spinner(c,"开始节次",periods,course.first-1),last=spinner(c,"结束节次",periods,course.last-1);
        Spinner parity=spinner(c,"授课周设置",new String[]{"每周（默认）","单周","双周","自定义"},course.parity);
        LinearLayout range=a.column();EditText weekStart=field(range,"开始周次",String.valueOf(course.weekStart),true),weekEnd=field(range,"结束周次",String.valueOf(course.weekEnd),true);c.addView(range);
        TextView custom=a.button("选择自定义周次",false,()->{});custom.setText("自定义周："+(course.customWeeks.isEmpty()?"点击选择":ScheduleModel.customLabel(course.customWeeks)));custom.setOnClickListener(v->{String[] labels=new String[model.weeks];boolean[] chosen=new boolean[model.weeks];for(int i=0;i<labels.length;i++){labels[i]="第 "+(i+1)+" 周";chosen[i]=course.customWeeks.contains(i+1);}new ToolDialog(a).setTitle("自定义授课周").setMultiChoiceItems(labels,chosen,(d,n,on)->chosen[n]=on).setPositiveButton("确定",(d,n)->{course.customWeeks.clear();for(int i=0;i<chosen.length;i++)if(chosen[i])course.customWeeks.add(i+1);custom.setText("自定义周："+(course.customWeeks.isEmpty()?"点击选择":ScheduleModel.customLabel(course.customWeeks)));}).setNegativeButton("取消",null).show();});c.addView(custom);a.gap(c,12);
        Runnable visibility=()->{boolean own=parity.getSelectedItemPosition()==3;range.setVisibility(own?View.GONE:View.VISIBLE);custom.setVisibility(own?View.VISIBLE:View.GONE);};visibility.run();parity.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View v,int n,long id){visibility.run();}});
        TextView color=a.button(a.getString(R.string.course_color,ColorPicker.name(a,course.seedColor())),false,()->{});color.setOnClickListener(v->ColorPicker.show(a,a.getString(R.string.course_color_title),course.seedColor(),value->{course.colorHex=ThemeColors.hex(value);color.setText(a.getString(R.string.course_color,ColorPicker.name(a,value)));}));c.addView(color);a.gap(c,12);
        AlertDialog.Builder b=new ToolDialog(a).setTitle(old==null?"新增课程":"编辑课程").setView(scroll(c)).setPositiveButton("保存",null).setNegativeButton("取消",null);if(old!=null)b.setNeutralButton("删除",null);AlertDialog dialog=b.create();dialog.setOnShowListener(d->{dialog.getButton(-1).setOnClickListener(v->{try{course.name=name.getText().toString().trim();course.room=room.getText().toString().trim();course.teacher=teacher.getText().toString().trim();course.day=day.getSelectedItemPosition()+1;course.first=first.getSelectedItemPosition()+1;course.last=last.getSelectedItemPosition()+1;course.parity=parity.getSelectedItemPosition();if(course.parity==3){if(course.customWeeks.isEmpty()){a.toast("请选择自定义授课周");return;}course.weekStart=course.customWeeks.first();course.weekEnd=course.customWeeks.last();}else{course.weekStart=Integer.parseInt(weekStart.getText().toString());course.weekEnd=Integer.parseInt(weekEnd.getText().toString());}String error=model.validateCourse(course);if(error!=null){a.toast(error);return;}model.put(course);save();dialog.dismiss();a.switchTab(1);}catch(Exception e){a.toast("请填写有效的授课周次");}});if(old!=null)dialog.getButton(-3).setOnClickListener(v->new ToolDialog(a).setTitle("删除课程").setMessage("删除“"+old.name+"”的这条安排？").setPositiveButton("删除",(x,n)->{model.courses.removeIf(y->y.id.equals(old.id));save();dialog.dismiss();a.switchTab(1);}).setNegativeButton("取消",null).show());});dialog.show();
    }
}
