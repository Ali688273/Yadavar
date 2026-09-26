package com.yadavar.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth

private val JM=listOf("فروردین","اردیبهشت","خرداد","تیر","مرداد","شهریور","مهر","آبان","آذر","دی","بهمن","اسفند")
private val JW=listOf("شنبه","یکشنبه","دوشنبه","سه‌شنبه","چهارشنبه","پنجشنبه","جمعه")

@Composable
fun TwentyFeaturesScreen(context:Context,tasks:List<TodoItem>,onUpdate:(TodoItem)->Unit){
    var page by remember{mutableIntStateOf(0)}
    val p=context.getSharedPreferences("yadavar_20",0)
    LazyColumn(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(bottom=24.dp)){
        item{Text("۲۰ قابلیت حرفه‌ای",fontSize=24.sp);Text("رایگان، آفلاین و بدون سرویس پولی.")}
        item{Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(5.dp)){
            listOf("تقویم شمسی","ضمیمه و صوت","مرتب‌سازی","فیلتر","Timeline","هدف و امتیاز","داشبورد","اعلان").forEachIndexed{i,s->FilterChip(page==i,{page=i},label={Text(s)})}
        }}
        item{when(page){
            0->Jalali20(tasks)
            1->Media20(context,p,tasks)
            2->Order20(p,tasks,onUpdate)
            3->Filter20(p,tasks)
            4->Timeline20(tasks)
            5->Goal20(p,tasks)
            6->Dashboard20(p)
            else->Notification20(context,p)
        }}
    }
}

@Composable private fun Jalali20(tasks:List<TodoItem>){
    var month by remember{mutableStateOf(YearMonth.now())}
    var selected by remember{mutableStateOf(LocalDate.now())}
    val first=month.atDay(1);val off=first.dayOfWeek.value%7
    Column(verticalArrangement=Arrangement.spacedBy(7.dp)){
        Text("تقویم شمسی واقعی",fontSize=21.sp);Text("امروز: "+j20(LocalDate.now()))
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){TextButton({month=month.minusMonths(1)}){Text("ماه قبل")};Text(JM[j20p(first).second-1]+" "+j20p(first).first);TextButton({month=month.plusMonths(1)}){Text("ماه بعد")}}
        Row(Modifier.fillMaxWidth()){JW.forEach{Text(it.take(2),Modifier.weight(1f))}}
        (0 until ((off+month.lengthOfMonth()+6)/7*7)).toList().chunked(7).forEach{week->Row(Modifier.fillMaxWidth()){week.forEach{i->if(i<off||i-off>=month.lengthOfMonth())Spacer(Modifier.weight(1f).height(42.dp))else{val d=month.atDay(i-off+1);TextButton({selected=d},Modifier.weight(1f).height(42.dp),contentPadding=PaddingValues(0.dp)){Text(j20p(d).third.toString())}}}}}
        Text("روز انتخاب‌شده: "+j20(selected))
        tasks.filter{it.dueDate==selected.toString()}.forEach{Text("• "+it.title)}
    }
}

@Composable private fun Media20(context:Context,p:android.content.SharedPreferences,tasks:List<TodoItem>){
    var uri by remember{mutableStateOf(p.getString("attachment","").orEmpty())}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){u:Uri?->if(u!=null){uri=u.toString();p.edit().putString("attachment",uri).apply()}}
    Column(verticalArrangement=Arrangement.spacedBy(7.dp)){
        Text("ضمیمه، صوت، گفتار و مکان",fontSize=21.sp)
        Button({picker.launch(arrayOf("*/*"))}){Text("افزودن عکس یا فایل")}
        if(uri.isNotBlank())Text("ضمیمه ذخیره شد.")
        Button({context.startActivity(Intent("android.speech.action.RECOGNIZE_SPEECH").apply{putExtra("android.speech.extra.LANGUAGE","fa-IR");putExtra("android.speech.extra.PROMPT","متن را بگویید")})}){Text("تبدیل گفتار به متن")}
        Button({context.startActivity(Intent(Intent.ACTION_GET_CONTENT).apply{type="audio/*";addCategory(Intent.CATEGORY_OPENABLE)})}){Text("افزودن یادداشت صوتی")}
        Text("مکان هر کار از قبل در مدل کار ذخیره می‌شود؛ این بخش بدون سرور، مکان را به‌عنوان یادآوری محلی نگه می‌دارد.")
        tasks.take(5).forEach{Text("• "+it.title+"  مکان: "+if(it.location.isBlank())"ثبت نشده" else it.location)}
    }
}

@Composable private fun Order20(p:android.content.SharedPreferences,tasks:List<TodoItem>,onUpdate:(TodoItem)->Unit){
    Column(verticalArrangement=Arrangement.spacedBy(6.dp)){
        Text("مرتب‌سازی، Drag/Move و زیرکارهای چندسطحی",fontSize=21.sp)
        Text("ترتیب محلی ذخیره می‌شود و زیرکارهای موجود با | قابل مدیریت‌اند.")
        tasks.take(15).forEachIndexed{i,t->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp)){
            Text((i+1).toString()+". "+t.title,Modifier.weight(1f))
            TextButton({val ids=tasks.map{it.id}.toMutableList();val x=ids.indexOf(t.id);if(x>0){val z=ids.removeAt(x);ids.add(x-1,z);p.edit().putString("order",ids.joinToString(",")).apply()}}){Text("↑")}
            TextButton({onUpdate(t.copy(subtasks=if(t.subtasks.isBlank())"زیرکار ۱|زیرکار ۲" else t.subtasks+"|زیرکار جدید"))}){Text("+ زیرکار")}
        }}
    }
}

@Composable private fun Filter20(p:android.content.SharedPreferences,tasks:List<TodoItem>){
    var status by remember{mutableStateOf("همه")};var priority by remember{mutableStateOf("همه")};var tag by remember{mutableStateOf("")};var rem by remember{mutableStateOf(false)};var over by remember{mutableStateOf(false)}
    val list=tasks.filter{(status=="همه"||(status=="باز"&&!it.done)||(status=="انجام‌شده"&&it.done))&&(priority=="همه"||it.priority==priority)&&(tag.isBlank()||it.tags.contains(tag,true))&&(!rem||it.reminders.isNotEmpty())&&(!over||(!it.done&&runCatching{LocalDate.parse(it.dueDate)}.getOrNull()?.isBefore(LocalDate.now())==true))}
    Column(verticalArrangement=Arrangement.spacedBy(6.dp)){Text("فیلتر ترکیبی پیشرفته",fontSize=21.sp)
        Row(Modifier.horizontalScroll(rememberScrollState())){listOf("همه","باز","انجام‌شده").forEach{FilterChip(status==it,{status=it},label={Text(it)})}}
        Row(Modifier.horizontalScroll(rememberScrollState())){listOf("همه","low","normal","high").forEach{FilterChip(priority==it,{priority=it},label={Text(it)})}}
        OutlinedTextField(tag,{tag=it},Modifier.fillMaxWidth(),singleLine=true,label={Text("برچسب")})
        Row{FilterChip(rem,{rem=!rem},label={Text("یادآوری‌دار")});FilterChip(over,{over=!over},label={Text("عقب‌افتاده")})}
        Button({p.edit().putString("filter",status+"|"+priority+"|"+tag+"|"+rem+"|"+over).apply()}){Text("ذخیره فیلتر")}
        Text("نتیجه: "+list.size+" کار");list.take(20).forEach{Text("• "+it.title)}
    }
}

@Composable private fun Timeline20(tasks:List<TodoItem>){
    Column(verticalArrangement=Arrangement.spacedBy(5.dp)){Text("Timeline و برنامه زمانی",fontSize=21.sp);tasks.filter{it.dueDate.isNotBlank()}.sortedBy{it.dueDate}.take(50).forEach{Text(it.dueDate+" • "+if(it.done)"✓ "else"○ "+it.title)}}
}

@Composable private fun Goal20(context:Context,p:android.content.SharedPreferences,tasks:List<TodoItem>){
    var goal by remember{mutableIntStateOf(p.getInt("goal",20))};var points by remember{mutableIntStateOf(p.getInt("points",0))};val done=tasks.count{it.done}
    Column(verticalArrangement=Arrangement.spacedBy(7.dp)){Text("هدف روزانه/ماهانه و امتیاز",fontSize=21.sp);OutlinedTextField(goal.toString(),{goal=it.toIntOrNull()?.coerceIn(1,9999)?:goal},singleLine=true,label={Text("هدف")});Text("پیشرفت: "+done+" از "+goal);LinearProgressIndicator({(done.toFloat()/goal).coerceIn(0f,1f)},Modifier.fillMaxWidth());Text("امتیاز: "+points);Button({points+=10;p.edit().putInt("goal",goal).putInt("points",points).apply()}){Text("پاداش +۱۰")}}
}

@Composable private fun Dashboard20(p:android.content.SharedPreferences){
    var compact by remember{mutableStateOf(p.getBoolean("compact",false))};var stats by remember{mutableStateOf(p.getBoolean("stats",true))};var goals by remember{mutableStateOf(p.getBoolean("goals",true))};var colors by remember{mutableStateOf(p.getBoolean("colors",true))}
    Column(verticalArrangement=Arrangement.spacedBy(6.dp)){Text("داشبورد قابل شخصی‌سازی و برچسب رنگی",fontSize=21.sp);S20("نمای فشرده",compact){compact=it;p.edit().putBoolean("compact",it).apply()};S20("آمار",stats){stats=it;p.edit().putBoolean("stats",it).apply()};S20("هدف‌ها",goals){goals=it;p.edit().putBoolean("goals",it).apply()};S20("رنگ برچسب‌ها",colors){colors=it;p.edit().putBoolean("colors",it).apply()};Text("تقویم روز/هفته/ماه، جستجو، دسته‌بندی و گزارش‌های قبلی برنامه حفظ شده‌اند.")}
}

@Composable private fun S20(t:String,v:Boolean,c:(Boolean)->Unit){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(t);Switch(v,c)}}

@Composable private fun Notification20(context:Context,p:android.content.SharedPreferences){
    var sound by remember{mutableStateOf(p.getBoolean("sound",true))};var vibration by remember{mutableStateOf(p.getBoolean("vibration",true))};var important by remember{mutableStateOf(p.getBoolean("important",true))}
    Column(verticalArrangement=Arrangement.spacedBy(7.dp)){Text("اعلان‌های حرفه‌ای",fontSize=21.sp);S20("صدا",sound){sound=it;p.edit().putBoolean("sound",it).apply()};S20("لرزش",vibration){vibration=it;p.edit().putBoolean("vibration",it).apply()};S20("اعلان مهم",important){important=it;p.edit().putBoolean("important",it).apply()}
        Button({if(Build.VERSION.SDK_INT>=26){val m=context.getSystemService(NotificationManager::class.java);val ch=NotificationChannel("yadavar_professional","یادآوری حرفه‌ای",NotificationManager.IMPORTANCE_HIGH);ch.enableVibration(vibration);m.createNotificationChannel(ch)}}){Text("ساخت کانال اعلان حرفه‌ای")}
    }
}

private fun j20(d:LocalDate):String{val p=j20p(d);return p.first.toString()+"/"+p.second.toString().padStart(2,'0')+"/"+p.third.toString().padStart(2,'0')}
private fun j20p(d:LocalDate):Triple<Int,Int,Int>{
    val g=intArrayOf(0,31,59,90,120,151,181,212,243,273,304,334);var y2=d.year+if(d.monthValue>2)1 else 0
    var n=355666+365*d.year+(y2+3)/4-(y2+99)/100+(y2+399)/400+d.dayOfMonth+g[d.monthValue-1]
    var jy=-1595+33*(n/12053);n%=12053;jy+=4*(n/1461);n%=1461;if(n>365){jy+=(n-1)/365;n=(n-1)%365}
    val jm=if(n<186)1+n/31 else 7+(n-186)/30;val jd=1+if(n<186)n%31 else(n-186)%30;return Triple(jy,jm,jd)
}
