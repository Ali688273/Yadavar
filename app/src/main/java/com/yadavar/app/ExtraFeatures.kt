package com.yadavar.app

import android.content.Context
import android.content.Intent
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import org.json.JSONObject
import java.security.MessageDigest
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val EXTRA_DATE = DateTimeFormatter.ISO_LOCAL_DATE

object ExtraFeaturesStore {
    private const val PREF = "yadavar_extra"
    private fun p(c: Context) = c.getSharedPreferences(PREF, Context.MODE_PRIVATE)
    fun archived(c: Context): Set<Int> = p(c).getStringSet("archived_ids", emptySet()) ?: emptySet()
    fun setArchived(c: Context, id: Int, value: Boolean) { val s=archived(c).toMutableSet(); if(value)s.add(id) else s.remove(id); p(c).edit().putStringSet("archived_ids",s).apply() }
    fun undo(c: Context): TodoItem? {
        val raw = p(c).getString("undo_task", null) ?: return null
        return runCatching {
            val o = JSONObject(raw)
            TodoItem(
                id = o.optInt("id", -1),
                title = o.optString("title"),
                done = o.optBoolean("done", false),
                reminderHour = o.optInt("reminderHour", -1).takeIf { it in 0..23 },
                reminderMinute = o.optInt("reminderMinute", -1).takeIf { it in 0..59 },
                repeat = o.optString("repeat", "none"),
                category = o.optString("category", "عمومی"),
                priority = o.optString("priority", "normal"),
                startDate = o.optString("startDate"),
                dueDate = o.optString("dueDate"),
                note = o.optString("note"),
                tags = o.optString("tags"),
                subtasks = o.optString("subtasks"),
                location = o.optString("location"),
                customEvery = o.optInt("customEvery", 1),
                customUnit = o.optString("customUnit", "day"),
                reminders = TaskReminderCodec.decode(o.optString("reminders"))
            )
        }.getOrNull()
    }
    fun saveUndo(c: Context,t:TodoItem){
        val o=JSONObject().put("id",t.id).put("title",t.title).put("done",t.done)
            .put("reminderHour",t.reminderHour ?: -1).put("reminderMinute",t.reminderMinute ?: -1)
            .put("repeat",t.repeat).put("category",t.category).put("priority",t.priority)
            .put("startDate",t.startDate).put("dueDate",t.dueDate).put("note",t.note).put("tags",t.tags)
            .put("subtasks",t.subtasks).put("location",t.location).put("customEvery",t.customEvery)
            .put("customUnit",t.customUnit).put("reminders",TaskReminderCodec.encode(t.reminders))
        p(c).edit().putString("undo_task",o.toString()).apply()
    }
    fun clearUndo(c:Context){p(c).edit().remove("undo_task").apply()}
    fun templates(c:Context):List<TodoItem>{return p(c).getString("templates","").orEmpty().split("\\n").mapNotNull{val x=it.split("\\t",limit=5);if(x.size==5)TodoItem(0,x[0],category=x[1],priority=x[2],note=x[3],subtasks=x[4])else null}}
    fun saveTemplates(c:Context,list:List<TodoItem>){p(c).edit().putString("templates",list.joinToString("\\n"){listOf(it.title,it.category,it.priority,it.note,it.subtasks).joinToString("\\t")}).apply()}
    fun inbox(c:Context):List<String>=p(c).getString("inbox","").orEmpty().split("\\n").filter{it.isNotBlank()}
    fun addInbox(c:Context,text:String){p(c).edit().putString("inbox",(inbox(c)+text.trim()).distinct().joinToString("\\n")).apply()}
    fun removeInbox(c:Context,text:String){p(c).edit().putString("inbox",inbox(c).filter{it!=text}.joinToString("\\n")).apply()}
    fun weeklyGoal(c:Context):Int=p(c).getInt("weekly_goal",10)
    fun setWeeklyGoal(c:Context,v:Int)=p(c).edit().putInt("weekly_goal",v.coerceIn(1,999)).apply()
    fun pin(c:Context):String=p(c).getString("app_pin","").orEmpty()
    fun setPin(c:Context,v:String)=p(c).edit().putString("app_pin",hashPin(v.filter(Char::isDigit).take(8))).apply()
    fun verifyPin(c:Context,v:String):Boolean {
        val stored=pin(c)
        return stored.isNotBlank() && stored==hashPin(v.filter(Char::isDigit).take(8))
    }
    private fun hashPin(v:String):String=MessageDigest.getInstance("SHA-256").digest(v.toByteArray(Charsets.UTF_8)).joinToString(""){"%02x".format(it)}
}

@Composable
fun ExtraFeaturesScreen(context:Context,tasks:List<TodoItem>,onAdd:(TodoItem)->Unit,onUpdate:(TodoItem)->Unit,onDelete:(Int)->Unit){
    var section by remember{mutableIntStateOf(0)}
    var inboxText by remember{mutableStateOf("")}
    var templateName by remember{mutableStateOf("")}
    var pin by remember{mutableStateOf("")}
    var goal by remember{mutableIntStateOf(ExtraFeaturesStore.weeklyGoal(context))}
    var selectedWeek by remember{mutableStateOf(LocalDate.now())}
    Column(Modifier.fillMaxSize().padding(12.dp)){
        Text("۱۵ قابلیت تکمیلی",fontSize=24.sp,fontWeight=FontWeight.Bold)
        Text("همه آفلاین و بدون هزینه.",color=MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        ScrollableTabRow(selectedTabIndex=section,edgePadding=0.dp){listOf("Inbox","آرشیو","Undo","هفتگی","گزارش","قالب‌ها","قفل").forEachIndexed{i,t->Tab(section==i,{section=i},text={Text(t)})}}
        Spacer(Modifier.height(10.dp))
        when(section){
            0->InboxPanel(context,inboxText,{inboxText=it},{val s=inboxText.trim();if(s.isNotEmpty()){ExtraFeaturesStore.addInbox(context,s);inboxText=""}},{text->onAdd(TodoItem((tasks.maxOfOrNull{it.id}?:0)+1,text));ExtraFeaturesStore.removeInbox(context,text)})
            1->ArchivePanel(context,tasks,onDelete)
            2->UndoPanel(context,onAdd)
            3->WeeklyPanel(context,tasks,selectedWeek,{selectedWeek=it},goal,{goal=it;ExtraFeaturesStore.setWeeklyGoal(context,it)})
            4->ReportPanel(tasks)
            5->TemplatePanel(context,templateName,{templateName=it},tasks,onAdd)
            else->LockPanel(context,pin,{pin=it})
        }
    }
}

@Composable private fun InboxPanel(c:Context,text:String,onText:(String)->Unit,onAdd:()->Unit,onConvert:(String)->Unit){
    var values by remember { mutableStateOf(ExtraFeaturesStore.inbox(c)) }
    LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)){item{Text("صندوق ورودی سریع",fontSize=20.sp,fontWeight=FontWeight.Bold);Text("ایده یا کار را سریع ذخیره کن و مستقیماً به کار تبدیل کن.")}
        item{Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){OutlinedTextField(text,onText,Modifier.weight(1f),singleLine=true,label={Text("یک فکر یا کار")});Button(onClick={onAdd();values=ExtraFeaturesStore.inbox(c)}){Text("ثبت")}}}
        items(values){value->Card(Modifier.fillMaxWidth()){Row(Modifier.fillMaxWidth().padding(10.dp),verticalAlignment=Alignment.CenterVertically){
            Text(value,Modifier.weight(1f))
            TextButton(onClick={ExtraFeaturesStore.removeInbox(c,value);values=ExtraFeaturesStore.inbox(c);}){Text("تبدیل به کار")}
            IconButton({ExtraFeaturesStore.removeInbox(c,value);values=ExtraFeaturesStore.inbox(c)}){Icon(Icons.Default.Delete,"حذف")}
        }}}
    }
}
@Composable private fun ArchivePanel(c:Context,tasks:List<TodoItem>,onDelete:(Int)->Unit){
    var archived by remember { mutableStateOf(ExtraFeaturesStore.archived(c)) }
    LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)){item{Text("آرشیو حرفه‌ای",fontSize=20.sp,fontWeight=FontWeight.Bold);Text("کارهای آرشیوشده از فهرست عادی جدا می‌مانند.")}
        items(tasks.filter{it.id in archived},key={it.id}){t->Card(Modifier.fillMaxWidth()){Row(Modifier.fillMaxWidth().padding(10.dp),verticalAlignment=Alignment.CenterVertically){Text(t.title,Modifier.weight(1f));TextButton({ExtraFeaturesStore.setArchived(c,t.id,false);archived=ExtraFeaturesStore.archived(c)}){Text("بازگردانی")};IconButton({onDelete(t.id);ExtraFeaturesStore.setArchived(c,t.id,false);archived=ExtraFeaturesStore.archived(c)}){Icon(Icons.Default.Delete,"حذف")}}}}
        if(tasks.none{it.id in archived})item{Text("آرشیوی وجود ندارد.")}
    }
}
@Composable private fun UndoPanel(c:Context,onAdd:(TodoItem)->Unit){
    val saved=ExtraFeaturesStore.undo(c)
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)){Text("بازگشت آخرین حذف",fontSize=20.sp,fontWeight=FontWeight.Bold)
        if(saved==null)Text("عملیات قابل بازگردانی وجود ندارد.")else{Text("آخرین مورد: "+saved.title);Button(onClick={onAdd(saved);ExtraFeaturesStore.clearUndo(c)}){Text("بازگردانی")}}
    }
}
@Composable private fun WeeklyPanel(c:Context,tasks:List<TodoItem>,week:LocalDate,onWeek:(LocalDate)->Unit,goal:Int,onGoal:(Int)->Unit){
    val start=week.minusDays(((week.dayOfWeek.value+1)%7).toLong());val days=(0..6).map{start.plusDays(it.toLong())}
    val count=tasks.count{it.done&&it.dueDate.isNotBlank()&&runCatching{LocalDate.parse(it.dueDate)}.getOrNull() in days}
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)){Row(verticalAlignment=Alignment.CenterVertically){IconButton({onWeek(week.minusWeeks(1))}){Icon(Icons.Default.ChevronLeft,"هفته قبل")};Text("هفته "+start.format(EXTRA_DATE),Modifier.weight(1f),fontWeight=FontWeight.Bold);IconButton({onWeek(week.plusWeeks(1))}){Icon(Icons.Default.ChevronRight,"هفته بعد")}}
        Text("انجام‌شده این هفته: "+count+" از هدف "+goal);LinearProgressIndicator(progress={(count.toFloat()/goal).coerceIn(0f,1f)},Modifier.fillMaxWidth())
        OutlinedTextField(goal.toString(),{onGoal(it.toIntOrNull()?:goal)},singleLine=true,label={Text("هدف هفتگی")});days.forEach{d->Text(d.dayOfWeek.name+": "+tasks.count{it.dueDate==d.toString()}+" کار")}
    }
}
@Composable private fun ReportPanel(tasks:List<TodoItem>){
    val now=LocalDate.now();val start=now.minusDays(((now.dayOfWeek.value+1)%7).toLong());val week=tasks.count{it.done&&it.dueDate.isNotBlank()&&runCatching{LocalDate.parse(it.dueDate)}.getOrNull() in start..now};val month=tasks.count{it.done&&it.dueDate.startsWith(now.toString().substring(0,7))};val overdue=tasks.count{!it.done&&it.dueDate.isNotBlank()&&runCatching{LocalDate.parse(it.dueDate)}.getOrNull()?.isBefore(now)==true};val done=tasks.count{it.done};val total=tasks.size
    LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)){item{Text("گزارش هفتگی و ماهانه",fontSize=20.sp,fontWeight=FontWeight.Bold)};item{ReportCard("کل کارها",total)};item{ReportCard("انجام‌شده",done)};item{ReportCard("انجام‌شده در هفته جاری",week)};item{ReportCard("انجام‌شده در ماه جاری",month)};item{ReportCard("عقب‌افتاده",overdue)};item{ReportCard("نرخ تکمیل",if(total==0)0 else done*100/total,"٪")};item{Button(onClick={val csv="عنوان,وضعیت,اولویت,سررسید\\n"+tasks.joinToString("\\n"){it.title.replace(","," ") + "," + if(it.done)"انجام‌شده" else "باز" + "," + it.priority + "," + it.dueDate};c.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply{type="text/csv";putExtra(Intent.EXTRA_TEXT,csv)},"اشتراک CSV"))}){Icon(Icons.Default.Share,null);Spacer(Modifier.width(6.dp));Text("خروجی CSV")}}}
}
@Composable private fun ReportCard(t:String,v:Int,s:String=""){Card(Modifier.fillMaxWidth()){Row(Modifier.fillMaxWidth().padding(12.dp),horizontalArrangement=Arrangement.SpaceBetween){Text(t);Text("$v$s",fontWeight=FontWeight.Bold)}}}
@Composable private fun TemplatePanel(c:Context,name:String,onName:(String)->Unit,tasks:List<TodoItem>,onAdd:(TodoItem)->Unit){
    var templates by remember { mutableStateOf(ExtraFeaturesStore.templates(c)) }
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)){Text("قالب‌های آماده",fontSize=20.sp,fontWeight=FontWeight.Bold);Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){OutlinedTextField(name,onName,Modifier.weight(1f),singleLine=true,label={Text("نام قالب")});Button(onClick={if(name.isNotBlank()){templates=templates+TodoItem(0,name);ExtraFeaturesStore.saveTemplates(c,templates);onName("")}}){Text("ذخیره")}};templates.forEach{t->Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text(t.title,Modifier.weight(1f));TextButton({onAdd(t.copy(id=(tasks.maxOfOrNull{it.id}?:0)+1))}){Text("ایجاد کار")};TextButton({templates=templates.filterNot{it.title==t.title};ExtraFeaturesStore.saveTemplates(c,templates)}){Text("حذف")}}}}
}
@Composable private fun LockPanel(c:Context,pin:String,onPin:(String)->Unit){
    val current=ExtraFeaturesStore.pin(c)
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)){Text("قفل برنامه",fontSize=20.sp,fontWeight=FontWeight.Bold);Text("قفل کاملاً محلی است و هیچ اطلاعاتی به اینترنت ارسال نمی‌شود.");OutlinedTextField(pin,onPin,singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),label={Text("PIN حداقل ۴ رقم")});Button(onClick={if(pin.length>=4){ExtraFeaturesStore.setPin(c,pin);onPin("")}}){Text(if(current.isBlank())"فعال‌سازی قفل" else "تغییر PIN")};if(current.isNotBlank())Text("قفل فعال است.")}
}
