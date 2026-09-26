package com.yadavar.app

import android.content.Context
import android.content.Intent
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
import java.time.DayOfWeek
import java.time.temporal.ChronoUnit
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

private val ISO = DateTimeFormatter.ISO_LOCAL_DATE

@Composable
fun ProfessionalScreen(
    context: Context,
    tasks: List<TodoItem>,
    onAdd: (TodoItem) -> Unit,
    onUpdate: (TodoItem) -> Unit,
    onDelete: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var section by remember { mutableIntStateOf(0) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var editing by remember { mutableStateOf<TodoItem?>(null) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("all") }
    var sort by remember { mutableStateOf("due") }
    val today = LocalDate.now()
    val overdue = tasks.count { !it.done && parseDate(it.dueDate)?.isBefore(today) == true }
    val todayTasks = tasks.filter { t ->
        val due = parseDate(t.dueDate)
        !ExtraFeaturesStore.archived(context).contains(t.id) &&
            (due == today || (t.dueDate.isBlank() && !t.done))
    }
    val visible = tasks.filter { t ->
        val q = query.trim()
        val matchesQ = q.isBlank() || t.title.contains(q, true) || t.tags.contains(q, true) || t.note.contains(q, true)
        val notArchived = !ExtraFeaturesStore.archived(context).contains(t.id)
        val matchesFilter = when (filter) {
            "open" -> !t.done
            "done" -> t.done
            "overdue" -> !t.done && parseDate(t.dueDate)?.isBefore(today) == true
            "high" -> t.priority == "high"
            "today" -> parseDate(t.dueDate) == today
            else -> true
        }
        notArchived && matchesQ && matchesFilter
    }.let { list ->
        when (sort) {
            "priority" -> list.sortedByDescending { priorityRank(it.priority) }
            "title" -> list.sortedBy { it.title.lowercase() }
            "created" -> list.sortedBy { it.id }
            else -> list.sortedWith(compareBy<TodoItem> { parseDate(it.dueDate) ?: LocalDate.MAX }.thenByDescending { priorityRank(it.priority) })
        }
    }

    Column(modifier.fillMaxSize().padding(12.dp)) {
        Text("مدیریت حرفه‌ای", fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Text("امروز: ${jalaliDate(today)} • عقب‌افتاده: $overdue", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        ScrollableTabRow(selectedTabIndex = section, edgePadding = 0.dp) {
            listOf("امروز", "تقویم", "همه کارها", "عادت‌ها", "تمرکز", "آمار", "ابزارها", "تکمیل").forEachIndexed { i, title ->
                Tab(section == i, { section = i }, text = { Text(title) })
            }
        }
        Spacer(Modifier.height(10.dp))
        when (section) {
            0 -> TodayPlan(context, tasks = todayTasks, overdue = overdue, onEdit = { editing = it }, onToggle = onUpdate, onCreate = {
                editing = TodoItem(id = -1, title = "", startDate = today.toString(), dueDate = today.toString())
            })
            1 -> CalendarPlanner(context, tasks = tasks, selected = selectedDate, onSelected = { selectedDate = it }, onEdit = { editing = it })
            2 -> {
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("جستجو در عنوان، یادداشت و برچسب") }, leadingIcon = { Icon(Icons.Default.Search, null) })
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("all" to "همه", "open" to "باز", "done" to "انجام‌شده", "overdue" to "عقب‌افتاده", "high" to "مهم", "today" to "امروز").forEach { (v, l) ->
                        FilterChip(filter == v, { filter = v }, label = { Text(l) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("مرتب‌سازی:")
                    listOf("due" to "سررسید", "priority" to "اولویت", "title" to "عنوان", "created" to "ایجاد").forEach { (v, l) ->
                        FilterChip(sort == v, { sort = v }, label = { Text(l) })
                    }
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(visible, key = { it.id }) { t ->
                        AdvancedTaskCard(context, t, { onUpdate(t.copy(done = !t.done)) }, { editing = t }, { ExtraFeaturesStore.saveUndo(context, t); onDelete(t.id) })
                    }
                }
            }
            3 -> HabitPanel(context)
            4 -> FocusPanel()
            5 -> StatsPanel(tasks)
            6 -> ToolsPanel(context)
            else -> ExtraFeaturesScreen(context, tasks, onAdd, onUpdate, onDelete)
        }
    }
    if (editing != null) {
        AdvancedTaskDialog(task = editing!!, dismiss = { editing = null }, save = {
            if (it.title.trim().isNotEmpty()) {
                if (it.id < 0) onAdd(it.copy(id = (tasks.maxOfOrNull { x -> x.id } ?: 0) + 1)) else onUpdate(it)
            }
            editing = null
        })
    }
}

@Composable
private fun TodayPlan(context: Context, tasks: List<TodoItem>, overdue: Int, onEdit: (TodoItem) -> Unit, onToggle: (TodoItem) -> Unit, onCreate: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text("امروز من", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("کارهای امروز + کارهای عقب‌افتاده را یکجا ببین.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            if (overdue > 0) Text("⚠ $overdue کار عقب‌افتاده داری.", color = MaterialTheme.colorScheme.error)
            if (tasks.isEmpty()) Text("برای امروز کاری نداری؛ زمان را برای یک هدف مهم استفاده کن.")
            tasks.sortedWith(compareBy<TodoItem> { it.done }.thenByDescending { priorityRank(it.priority) }).forEach {
                AdvancedTaskCard(context, it, { onToggle(it) }, { onEdit(it) }, {})
            }
            TextButton(onClick = onCreate) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(4.dp)); Text("کار برای امروز") }
        }
    }
}

@Composable
private fun CalendarPlanner(context: Context, tasks: List<TodoItem>, selected: LocalDate, onSelected: (LocalDate) -> Unit, onEdit: (TodoItem) -> Unit) {
    var mode by remember { mutableStateOf("month") }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("day" to "روز", "week" to "هفته", "month" to "ماه").forEach { (v, l) ->
            FilterChip(mode == v, { mode = v }, label = { Text(l) })
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = {
            onSelected(when (mode) {
                "day" -> selected.minusDays(1)
                "week" -> selected.minusWeeks(1)
                else -> selected.minusMonths(1)
            })
        }) { Icon(Icons.Default.ChevronLeft, "قبلی") }
        TextButton(onClick = { onSelected(LocalDate.now()) }) { Text("امروز") }
        IconButton(onClick = {
            onSelected(when (mode) {
                "day" -> selected.plusDays(1)
                "week" -> selected.plusWeeks(1)
                else -> selected.plusMonths(1)
            })
        }) { Icon(Icons.Default.ChevronRight, "بعدی") }
    }
    Spacer(Modifier.height(8.dp))
    val dates = when (mode) {
        "day" -> listOf(selected)
        "week" -> {
            val delta = (selected.dayOfWeek.value - DayOfWeek.SATURDAY.value + 7) % 7
            val start = selected.minusDays(delta.toLong())
            (0..6).map { start.plusDays(it.toLong()) }
        }
        else -> {
            val first = selected.withDayOfMonth(1)
            val start = first.minusDays(first.dayOfWeek.value.toLong() % 7)
            (0..41).map { start.plusDays(it.toLong()) }
        }
    }
    if (mode == "month") {
        Column {
            dates.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { d ->
                        val count = tasks.count { parseDate(it.dueDate) == d }
                        OutlinedButton(onClick = { onSelected(d) }, modifier = Modifier.weight(1f).padding(2.dp), contentPadding = PaddingValues(2.dp)) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(jalaliDay(d))
                                if (count > 0) Text(count.toString(), fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }
    } else {
        Text("تاریخ انتخاب‌شده: ${jalaliDate(selected)}", fontWeight = FontWeight.Bold)
        val dayTasks = tasks.filter { parseDate(it.dueDate) == selected }
        if (dayTasks.isEmpty()) Text("کاری برای این روز ثبت نشده.")
        dayTasks.forEach { AdvancedTaskCard(context, it, {}, { onEdit(it) }, {}) }
    }
}

@Composable
private fun AdvancedTaskCard(context: Context, task: TodoItem, toggle: () -> Unit, edit: () -> Unit, delete: () -> Unit) {
    var archived by remember(task.id) { mutableStateOf(ExtraFeaturesStore.archived(context).contains(task.id)) }
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(task.done, { toggle() })
            Column(Modifier.weight(1f).padding(horizontal = 6.dp)) {
                Text(task.title, fontWeight = if (task.priority == "high") FontWeight.Bold else FontWeight.Normal)
                val meta = buildList {
                    if (task.dueDate.isNotBlank()) add("سررسید " + task.dueDate)
                    if (task.tags.isNotBlank()) add("#" + task.tags.replace(",", " #"))
                    if (task.subtasks.isNotBlank()) add("زیرکار " + task.subtasks.split("|").size)
                }.joinToString(" • ")
                if (meta.isNotBlank()) Text(meta, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (task.note.isNotBlank()) Text(task.note, fontSize = 12.sp)
            }
            IconButton(edit) { Icon(Icons.Default.Edit, "ویرایش") }
            IconButton({ archived = !archived; ExtraFeaturesStore.setArchived(context, task.id, archived) }) { Icon(if (archived) Icons.Default.Unarchive else Icons.Default.Archive, "آرشیو") }
            IconButton(delete) { Icon(Icons.Default.Delete, "حذف") }
        }
    }
}

@Composable
private fun AdvancedTaskDialog(task: TodoItem, dismiss: () -> Unit, save: (TodoItem) -> Unit) {
    var title by remember(task.id) { mutableStateOf(task.title) }
    var start by remember(task.id) { mutableStateOf(task.startDate) }
    var due by remember(task.id) { mutableStateOf(task.dueDate) }
    var note by remember(task.id) { mutableStateOf(task.note) }
    var tags by remember(task.id) { mutableStateOf(task.tags) }
    var subtasks by remember(task.id) { mutableStateOf(task.subtasks) }
    var location by remember(task.id) { mutableStateOf(task.location) }
    var repeat by remember(task.id) { mutableStateOf(task.repeat) }
    var customEvery by remember(task.id) { mutableStateOf(task.customEvery.toString()) }
    var customUnit by remember(task.id) { mutableStateOf(task.customUnit) }

    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(if (task.id < 0) "کار حرفه‌ای جدید" else "ویرایش حرفه‌ای کار") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("عنوان") })
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(start, { start = it }, Modifier.weight(1f), singleLine = true, label = { Text("شروع YYYY-MM-DD") })
                    OutlinedTextField(due, { due = it }, Modifier.weight(1f), singleLine = true, label = { Text("سررسید YYYY-MM-DD") })
                }
                OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), minLines = 2, label = { Text("یادداشت") })
                OutlinedTextField(tags, { tags = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("برچسب‌ها با ویرگول") })
                OutlinedTextField(subtasks, { subtasks = it }, Modifier.fillMaxWidth(), minLines = 2, label = { Text("زیرکارها با | جدا شوند") })
                OutlinedTextField(location, { location = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("مکان یادآوری") })
                Text("تکرار سفارشی", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("none" to "یک‌بار", "daily" to "روزانه", "weekly" to "هفتگی", "monthly" to "ماهانه", "yearly" to "سالانه", "custom" to "سفارشی").forEach { (v,l) ->
                        FilterChip(repeat == v, { repeat = v }, label = { Text(l) })
                    }
                }
                if (repeat == "custom") {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(customEvery, { customEvery = it.filter(Char::isDigit).take(3) }, Modifier.weight(1f), singleLine = true, label = { Text("هر چند") })
                        OutlinedTextField(customUnit, { customUnit = it }, Modifier.weight(1f), singleLine = true, label = { Text("روز/هفته/ماه") })
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val normalizedStart = parseDate(start)?.toString() ?: ""
                val normalizedDue = parseDate(due)?.toString() ?: normalizedStart
                save(task.copy(title = title.trim(), startDate = normalizedStart, dueDate = normalizedDue, note = note.trim(), tags = tags.trim(), subtasks = subtasks.trim(), location = location.trim(), repeat = repeat, customEvery = customEvery.toIntOrNull()?.coerceAtLeast(1) ?: 1, customUnit = customUnit.trim().ifBlank { "day" }))
            }, enabled = title.trim().isNotEmpty() && (start.isBlank() || parseDate(start) != null) && (due.isBlank() || parseDate(due) != null)) { Text("ذخیره") }
        },
        dismissButton = { TextButton(dismiss) { Text("انصراف") } }
    )
}

@Composable
private fun HabitPanel(context: Context) {
    val prefs = context.getSharedPreferences("yadavar_data", Context.MODE_PRIVATE)
    var habits by remember { mutableStateOf(loadHabits(prefs)) }
    var newHabit by remember { mutableStateOf("") }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("عادت‌ها و Streak", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("ذخیره‌سازی کاملاً آفلاین است.")
            OutlinedTextField(newHabit, { newHabit = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("نام عادت") })
            Button(onClick = {
                val n = newHabit.trim()
                if (n.isNotEmpty() && !habits.containsKey(n)) {
                    habits = habits + (n to 0)
                    saveHabits(prefs, habits)
                    newHabit = ""
                }
            }) { Text("افزودن عادت") }
        }
        items(habits.toList(), key = { it.first }) { pair ->
            val name = pair.first
            val streak = pair.second
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(name, Modifier.weight(1f))
                    Text("🔥 $streak")
                    Spacer(Modifier.width(6.dp))
                    Button(onClick = {
                        val today = LocalDate.now().toString()
                        val key = "habit_dates_" + name
                        val dates = (prefs.getStringSet(key, emptySet()) ?: emptySet()).toMutableSet()
                        if (today !in dates) {
                            dates.add(today)
                            prefs.edit().putStringSet(key, dates).apply()
                            val newStreak = calculateHabitStreak(dates)
                            habits = habits + (name to newStreak)
                            saveHabits(prefs, habits)
                        }
                    }) { Text("امروز") }
                    val dates = prefs.getStringSet("habit_dates_" + name, emptySet()) ?: emptySet()
                    Text("۷ روز اخیر: " + (0..6).count { LocalDate.now().minusDays(it.toLong()).toString() in dates } + " • بهترین: " + calculateBestStreak(dates), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun FocusPanel() {
    var seconds by remember { mutableIntStateOf(25 * 60) }
    var running by remember { mutableStateOf(false) }
    LaunchedEffect(running) {
        while (running && seconds > 0) {
            kotlinx.coroutines.delay(1000)
            seconds--
        }
        if (seconds == 0) running = false
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text("پومودورو", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("%02d:%02d".format(seconds / 60, seconds % 60), fontSize = 48.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { running = !running }) { Text(if (running) "توقف" else "شروع") }
            OutlinedButton(onClick = { running = false; seconds = 25 * 60 }) { Text("بازنشانی") }
        }
        Spacer(Modifier.height(16.dp))
        Text("ماتریس اهمیت/فوریت: مهم+فوری را اول انجام بده، مهم+غیرفوری را برنامه‌ریزی کن.")
    }
}

@Composable
private fun IdeasPanel(context: Context) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("امکانات تکمیلی", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("تقویم شمسی و حالت آفلاین در هسته برنامه فعال است.")
        Text("لیست خرید: از دسته «خرید» برای نگهداری اقلام استفاده کن.")
        Text("مکان در هر کار ذخیره می‌شود و برای مرحله Geofence آماده است.")
        Button(onClick = {
            val body = "یادآور\nتعداد کارها: ${loadTasks(context).size}\nتعداد تولدها: ${loadBirthdays(context).size}"
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, body) }, "اشتراک‌گذاری پشتیبان"))
        }) { Icon(Icons.Default.Share, null); Spacer(Modifier.width(6.dp)); Text("خروجی پشتیبان") }
    }
}

private fun parseDate(value: String): LocalDate? = try { if (value.isBlank()) null else LocalDate.parse(value, ISO) } catch (_: DateTimeParseException) { null }
private fun priorityRank(value: String): Int = when (value) { "high" -> 3; "normal" -> 2; else -> 1 }
private fun jalaliDate(date: LocalDate): String { val (jy, jm, jd) = gregorianToJalali(date.year, date.monthValue, date.dayOfMonth); return "$jy/$jm/$jd" }
private fun jalaliDay(date: LocalDate): String { val (_, _, d) = gregorianToJalali(date.year, date.monthValue, date.dayOfMonth); return d.toString() }

private fun gregorianToJalali(gy: Int, gm: Int, gd: Int): Triple<Int, Int, Int> {
    val gdm = intArrayOf(0,31,59,90,120,151,181,212,243,273,304,334)
    var gy2 = gy
    if (gm > 2) gy2++
    var days = 355666 + 365 * gy + ((gy2 + 3) / 4) - ((gy2 + 99) / 100) + ((gy2 + 399) / 400) + gd + gdm[gm - 1]
    var jy = -1595 + 33 * (days / 12053)
    days %= 12053
    jy += 4 * (days / 1461)
    days %= 1461
    if (days > 365) { jy += (days - 1) / 365; days = (days - 1) % 365 }
    val jm = if (days < 186) 1 + days / 31 else 7 + (days - 186) / 30
    val jd = 1 + if (days < 186) days % 31 else (days - 186) % 30
    return Triple(jy, jm, jd)
}

@Composable
private fun StatsPanel(tasks: List<TodoItem>) {
    val total = tasks.size
    val done = tasks.count { it.done }
    val overdue = tasks.count { !it.done && parseDate(it.dueDate)?.isBefore(LocalDate.now()) == true }
    val high = tasks.count { it.priority == "high" }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("آمار حرفه‌ای", fontSize = 21.sp, fontWeight = FontWeight.Bold) }
        item { StatRow("کل کارها", total) }
        item { StatRow("انجام‌شده", done) }
        item { StatRow("باز", total - done) }
        item { StatRow("عقب‌افتاده", overdue) }
        item { StatRow("مهم", high) }
        item { StatRow("درصد انجام", if (total == 0) 0 else done * 100 / total, "٪") }
    }
}
@Composable
private fun StatRow(title: String, value: Int, suffix: String = "") {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title)
            Text("$value$suffix", fontWeight = FontWeight.Bold)
        }
    }
}
@Composable
private fun ToolsPanel(context: Context) {
    var shopping by remember { mutableStateOf(loadShopping(context)) }
    var item by remember { mutableStateOf("") }
    var countdownTitle by remember { mutableStateOf("") }
    var countdownDate by remember { mutableStateOf("") }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("ابزارهای رایگان", fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Text("بدون سرور، اشتراک یا خرید درون‌برنامه‌ای.")
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("لیست خرید", fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(item, { item = it }, Modifier.weight(1f), singleLine = true, label = { Text("قلم خرید") })
                        Button(onClick = {
                            if (item.isNotBlank()) {
                                shopping = shopping + item.trim()
                                saveShopping(context, shopping)
                                item = ""
                            }
                        }) { Text("+") }
                    }
                    shopping.forEachIndexed { index, value ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(value, Modifier.weight(1f))
                            IconButton(onClick = {
                                shopping = shopping.toMutableList().also { it.removeAt(index) }
                                saveShopping(context, shopping)
                            }) { Icon(Icons.Default.Delete, "حذف") }
                        }
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("شمارش معکوس", fontWeight = FontWeight.Bold)
                    OutlinedTextField(countdownTitle, { countdownTitle = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("عنوان") })
                    OutlinedTextField(countdownDate, { countdownDate = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("تاریخ YYYY-MM-DD") })
                    parseDate(countdownDate)?.let {
                        val days = ChronoUnit.DAYS.between(LocalDate.now(), it)
                        Text(if (days >= 0) "$countdownTitle: $days روز باقی‌مانده" else "$countdownTitle: " + (-days) + " روز گذشته")
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("پشتیبان سریع", fontWeight = FontWeight.Bold)
                    Text("خلاصه داده‌ها را برای نگهداری یا ارسال کپی کن.")
                    Button(onClick = {
                        val body = "یادآور\nکارها: " + loadTasks(context).size + "\nتولدها: " + loadBirthdays(context).size
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, body)
                        }, "اشتراک‌گذاری"))
                    }) { Icon(Icons.Default.Share, null); Spacer(Modifier.width(6.dp)); Text("اشتراک خلاصه") }
                }
            }
        }
    }
}
private fun loadHabits(prefs: android.content.SharedPreferences): Map<String, Int> =
    prefs.getString("habits", "").orEmpty().split("\n").mapNotNull {
        val x = it.split("\t", limit = 2)
        if (x.size == 2) x[0] to (x[1].toIntOrNull() ?: 0) else null
    }.toMap()
private fun saveHabits(prefs: android.content.SharedPreferences, habits: Map<String, Int>) {
    prefs.edit().putString("habits", habits.entries.joinToString("\n") { it.key.replace("\t", " ") + "\t" + it.value }).apply()
}
private fun loadShopping(context: Context): List<String> =
    context.getSharedPreferences("yadavar_data", 0).getString("shopping", "").orEmpty().split("\n").filter { it.isNotBlank() }
private fun saveShopping(context: Context, values: List<String>) {
    context.getSharedPreferences("yadavar_data", 0).edit().putString("shopping", values.joinToString("\n")).apply()
}


private fun calculateHabitStreak(dates: Set<String>): Int {
    var streak = 0
    var day = LocalDate.now()
    while (day.toString() in dates) {
        streak++
        day = day.minusDays(1)
    }
    return streak
}

private fun calculateBestStreak(dates: Set<String>): Int {
    var best = 0
    var current = 0
    var day = dates.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.minOrNull()
    while (day != null) {
        if (day.toString() in dates) current++ else current = 0
        best = maxOf(best, current)
        day = day.plusDays(1)
        if (day.isAfter(LocalDate.now())) break
    }
    return best
}
