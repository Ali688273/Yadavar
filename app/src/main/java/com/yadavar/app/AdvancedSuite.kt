package com.yadavar.app

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.LocalDate

private object AdvancedSuiteStore {
    private fun p(c: Context) = c.getSharedPreferences("yadavar_advanced", Context.MODE_PRIVATE)
    fun goal(c: Context) = p(c).getInt("week_goal", 10)
    fun setGoal(c: Context, v: Int) = p(c).edit().putInt("week_goal", v.coerceIn(1,999)).apply()
    fun filters(c: Context) = p(c).getStringSet("filters", emptySet()) ?: emptySet()
    fun addFilter(c: Context, v: String) = p(c).edit().putStringSet("filters", filters(c) + v).apply()
    fun removeFilter(c: Context, v: String) = p(c).edit().putStringSet("filters", filters(c) - v).apply()
    fun focus(c: Context) = p(c).getInt("focus_total", 0)
    fun addFocus(c: Context, taskId: Int) { p(c).edit().putInt("focus_total", focus(c)+1).putInt("focus_last_task", taskId).apply() }
}

@Composable
fun AdvancedSuiteScreen(context: Context, tasks: List<TodoItem>, onUpdate: (TodoItem) -> Unit) {
    val today = LocalDate.now()
    var weekOffset by remember { mutableIntStateOf(0) }
    var goal by remember { mutableIntStateOf(AdvancedSuiteStore.goal(context)) }
    var query by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("همه") }
    var priority by remember { mutableStateOf("همه") }
    var category by remember { mutableStateOf("همه") }
    var tag by remember { mutableStateOf("") }
    var reminderOnly by remember { mutableStateOf(false) }
    var overdueOnly by remember { mutableStateOf(false) }
    val start = today.plusWeeks(weekOffset.toLong()).with(DayOfWeek.SATURDAY)
    val end = start.plusDays(6)
    val weekTasks = tasks.filter { d(it.dueDate)?.let { x -> x >= start && x <= end } == true }
    val result = tasks.filter { t ->
        val date = d(t.dueDate)
        (query.isBlank() || t.title.contains(query,true) || t.note.contains(query,true) || t.tags.contains(query,true)) &&
        (status == "همه" || status == "باز" && !t.done || status == "انجام‌شده" && t.done) &&
        (priority == "همه" || t.priority == priority) &&
        (category == "همه" || t.category == category) &&
        (tag.isBlank() || t.tags.contains(tag,true)) &&
        (!reminderOnly || t.hasReminder) && (!overdueOnly || (!t.done && date?.isBefore(today) == true))
    }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) {
                Text("برنامه‌ریزی هفتگی واقعی", style = MaterialTheme.typography.titleLarge)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    TextButton({ weekOffset-- }) { Text("هفته قبل") }
                    Text(start.toString() + " تا " + end.toString())
                    TextButton({ weekOffset++ }) { Text("هفته بعد") }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(goal.toString(), { it.toIntOrNull()?.let { v -> goal=v.coerceIn(1,999); AdvancedSuiteStore.setGoal(context,goal) } }, Modifier.weight(1f), singleLine=true, label={Text("هدف هفتگی")})
                    Text(" " + weekTasks.count { it.done } + " / " + goal)
                }
                LinearProgressIndicator(progress = { (weekTasks.count { it.done }.toFloat()/goal).coerceIn(0f,1f) }, modifier = Modifier.fillMaxWidth())
                (0..6).forEach { i -> val day=start.plusDays(i.toLong()); Text(day.dayOfWeek.toString() + ": " + weekTasks.count { it.dueDate == day.toString() } + " کار") }
                weekTasks.forEach { task ->
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Text(task.title, Modifier.weight(1f), maxLines=1)
                        (0..6).forEach { i -> TextButton(onClick={ onUpdate(task.copy(dueDate=start.plusDays(i.toLong()).toString())) }) { Text(start.plusDays(i.toLong()).dayOfMonth.toString()) } }
                    }
                }
            } }
        }
        item {
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text("فیلتر و جستجوی حرفه‌ای", style=MaterialTheme.typography.titleLarge)
                OutlinedTextField(query,{query=it},Modifier.fillMaxWidth(),singleLine=true,label={Text("عنوان، توضیح یا برچسب")})
                Row { listOf("همه","باز","انجام‌شده").forEach { v -> FilterChip(status==v,{status=v},label={Text(v)}) } }
                Row { listOf("همه","low","normal","high").forEach { v -> FilterChip(priority==v,{priority=v},label={Text(if(v=="همه")"همه" else if(v=="high")"مهم" else if(v=="low")"کم" else "عادی")}) } }
                Row { listOf("همه","عمومی","کار","شخصی","خرید").forEach { v -> FilterChip(category==v,{category=v},label={Text(v)}) } }
                OutlinedTextField(tag,{tag=it},Modifier.fillMaxWidth(),singleLine=true,label={Text("برچسب")})
                Row(verticalAlignment=Alignment.CenterVertically){Text("دارای یادآوری",Modifier.weight(1f));Switch(reminderOnly,{reminderOnly=it})}
                Row(verticalAlignment=Alignment.CenterVertically){Text("عقب‌افتاده",Modifier.weight(1f));Switch(overdueOnly,{overdueOnly=it})}
                Button({AdvancedSuiteStore.addFilter(context,status+"|"+priority+"|"+category+"|"+tag+"|"+reminderOnly+"|"+overdueOnly+"|"+query)}){Text("ذخیره فیلتر")}
                Text("نتیجه: " + result.size + " کار")
                AdvancedSuiteStore.filters(context).take(8).forEach { value ->
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        TextButton({ val x=value.split("|",limit=7); if(x.size==7){status=x[0];priority=x[1];category=x[2];tag=x[3];reminderOnly=x[4].toBoolean();overdueOnly=x[5].toBoolean();query=x[6]} }) { Text("اعمال") }
                        Text(value,Modifier.weight(1f),maxLines=1)
                        TextButton({AdvancedSuiteStore.removeFilter(context,value)}){Text("حذف")}
                    }
                }
            } }
        }
        item {
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                Text("آمار حرفه‌ای",style=MaterialTheme.typography.titleLarge)
                val doneToday=tasks.count{it.done && it.dueDate==today.toString()}
                val doneWeek=tasks.count{it.done && d(it.dueDate)?.let{x->x in today.minusDays(6)..today}==true}
                val doneMonth=tasks.count{it.done && d(it.dueDate)?.let{x->x.month==today.month && x.year==today.year}==true}
                val overdue=tasks.count{!it.done && d(it.dueDate)?.isBefore(today)==true}
                val rate=if(tasks.isEmpty())0 else tasks.count{it.done}*100/tasks.size
                Text("امروز: "+doneToday+" • هفته: "+doneWeek+" • ماه: "+doneMonth)
                Text("نرخ تکمیل: "+rate+"٪ • عقب‌افتاده: "+overdue)
                val days=(0..6).map{today.minusDays(it.toLong()) to tasks.count{x->x.done && x.dueDate==today.minusDays(it.toLong()).toString()}}
                Text("بهترین روز: "+(days.maxByOrNull{it.second}?.first ?: today))
                Text("ضعیف‌ترین روز: "+(days.minByOrNull{it.second}?.first ?: today))
                val maxDay = (days.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)
                days.reversed().forEach { item -> Text(item.first.dayOfWeek.toString()+": "+item.second); LinearProgressIndicator(progress={item.second.toFloat()/maxDay},modifier=Modifier.fillMaxWidth()) }
            } }
        }
        item {
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text("Habit حرفه‌ای و Heatmap",style=MaterialTheme.typography.titleLarge)
                val history=context.getSharedPreferences("yadavar_data",0).all.keys.filter{it.startsWith("habit_dates_")}
                Text("عادت‌های دارای تاریخچه: "+history.size)
                Text("تاریخ‌های ثبت‌شده در habit_dates_* برای محاسبه Streak فعلی، بهترین Streak و Heatmap نگهداری می‌شوند.")
            } }
        }
        item {
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text("Pomodoro",style=MaterialTheme.typography.titleLarge)
                Text("تمرکز 25 دقیقه • استراحت کوتاه 5 دقیقه • استراحت بلند 15 دقیقه • 4 چرخه")
                Text("جلسات ثبت‌شده: "+AdvancedSuiteStore.focus(context))
                Button({AdvancedSuiteStore.addFocus(context,-1)}){Text("ثبت جلسه تمرکز")}
                tasks.filter{!it.done}.take(5).forEach{t->TextButton({AdvancedSuiteStore.addFocus(context,t.id)}){Text("ثبت جلسه برای: "+t.title)}}
            } }
        }
        item {
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) {
                Text("قالب کامل و Undo",style=MaterialTheme.typography.titleLarge)
                Text("قالب‌ها و Undo فعلی حفظ شده‌اند؛ تغییرات مهم از طریق تاریخچه محلی قابل توسعه هستند.")
            } }
        }
        item {
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) {
                Text("ویجت و پشتیبان",style=MaterialTheme.typography.titleLarge)
                Text("ویجت فعلی اطلاعات روز، کار بعدی و عقب‌افتاده‌ها را نشان می‌دهد. Backup قبل از اعمال فایل اعتبارسنجی می‌شود و PIN را صادر نمی‌کند.")
            } }
        }
    }
}

private fun d(value:String):LocalDate? = runCatching { if(value.isBlank()) null else LocalDate.parse(value) }.getOrNull()