package com.yadavar.app

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private object YadavarProgressStore {
    private fun p(c: Context) = c.getSharedPreferences("yadavar_progress", Context.MODE_PRIVATE)
    fun completionDates(c: Context): Set<String> = p(c).getStringSet("completion_dates", emptySet())?.toSet() ?: emptySet()
    fun recordCompletion(c: Context, date: LocalDate = LocalDate.now()) { p(c).edit().putStringSet("completion_dates", completionDates(c) + date.toString()).apply() }
    fun focusMinutes(c: Context) = p(c).getInt("focus_minutes", 0)
    fun addFocus(c: Context, minutes: Int) { p(c).edit().putInt("focus_minutes", focusMinutes(c) + minutes.coerceAtLeast(0)).apply() }
    fun theme(c: Context) = p(c).getString("theme", "system") ?: "system"
    fun setTheme(c: Context, value: String) = p(c).edit().putString("theme", value).apply()
    fun privateNotifications(c: Context) = p(c).getBoolean("private_notifications", false)
    fun setPrivateNotifications(c: Context, value: Boolean) = p(c).edit().putBoolean("private_notifications", value).apply()
}

@Composable
fun YadavarAppTheme(context: Context, content: @Composable () -> Unit) {
    val choice = YadavarProgressStore.theme(context)
    val scheme = when (choice) {
        "dark" -> darkColorScheme()
        "ocean" -> lightColorScheme(primary = androidx.compose.ui.graphics.Color(0xFF1565C0), secondary = androidx.compose.ui.graphics.Color(0xFF00838F))
        "nature" -> lightColorScheme(primary = androidx.compose.ui.graphics.Color(0xFF2E7D32), secondary = androidx.compose.ui.graphics.Color(0xFF558B2F))
        "sunset" -> lightColorScheme(primary = androidx.compose.ui.graphics.Color(0xFFE65100), secondary = androidx.compose.ui.graphics.Color(0xFFAD1457))
        "minimal" -> lightColorScheme(primary = androidx.compose.ui.graphics.Color(0xFF455A64), secondary = androidx.compose.ui.graphics.Color(0xFF607D8B))
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
fun FeatureHubScreen(context: Context, tasks: List<TodoItem>, onAddTask: (TodoItem) -> Unit, onUpdateTask: (TodoItem) -> Unit) {
    var quickText by remember { mutableStateOf("") }
    var quickMessage by remember { mutableStateOf("") }
    var focusSeconds by remember { mutableLongStateOf(25L * 60L) }
    var focusRunning by remember { mutableStateOf(false) }
    var focusMinutes by remember { mutableIntStateOf(25) }

    LaunchedEffect(focusRunning) {
        while (focusRunning && focusSeconds > 0) {
            kotlinx.coroutines.delay(1000)
            focusSeconds--
        }
        if (focusRunning && focusSeconds <= 0) {
            focusRunning = false
            YadavarProgressStore.addFocus(context, focusMinutes)
        }
    }

    val today = LocalDate.now()
    val done = tasks.count { it.done }
    val open = tasks.size - done
    val overdue = tasks.count { !it.done && parseHubDate(it.dueDate)?.isBefore(today) == true }
    val todayDone = tasks.count { it.done && it.dueDate == today.toString() }
    val rate = if (tasks.isEmpty()) 0 else done * 100 / tasks.size
    val dates = YadavarProgressStore.completionDates(context)
    val streak = calculateStreak(dates, today)
    val best = calculateBestStreak(dates)
    val focusTotal = YadavarProgressStore.focusMinutes(context)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            HubCard("📊 امروز من", "داشبورد سریع") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatBox("کل", tasks.size.toString(), Modifier.weight(1f))
                    StatBox("باز", open.toString(), Modifier.weight(1f))
                    StatBox("انجام", done.toString(), Modifier.weight(1f))
                    StatBox("عقب", overdue.toString(), Modifier.weight(1f))
                }
                Text("نرخ تکمیل: " + rate + "٪ • امروز انجام‌شده: " + todayDone)
                LinearProgressIndicator(progress = { rate / 100f }, Modifier.fillMaxWidth())
            }
        }
        item {
            HubCard("🔥 Streak و رکوردها", "زنجیره انجام کارهای روزانه") {
                Text("Streak فعلی: " + streak + " روز", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("بهترین Streak: " + best + " روز • روزهای فعال: " + dates.size)
            }
        }
        item {
            HubCard("🏆 Achievement و Level", "دستاوردهای محلی و بدون حساب") {
                val level = done / 10 + 1
                val xp = done * 25 + focusTotal / 5
                Text("Level " + level + " • " + xp + " XP", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                buildAchievements(tasks, streak, focusTotal).forEach { (title, earned) -> Text(if (earned) "🏆 " + title else "🔒 " + title) }
            }
        }
        item {
            HubCard("⏱️ Focus / Pomodoro", "تایمر واقعی تمرکز") {
                Text(formatTimer(focusSeconds), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(onClick = {
                        if (!focusRunning && focusSeconds <= 0) focusSeconds = focusMinutes * 60L
                        focusRunning = !focusRunning
                    }) { Text(if (focusRunning) "مکث" else "شروع") }
                    OutlinedButton(onClick = { focusRunning = false; focusSeconds = focusMinutes * 60L }) { Text("بازنشانی") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(15, 25, 45).forEach { m ->
                        FilterChip(selected = focusMinutes == m, onClick = { focusMinutes = m; if (!focusRunning) focusSeconds = m * 60L }, label = { Text(m.toString() + " دقیقه") })
                    }
                }
                Text("تمرکز ثبت‌شده: " + focusTotal + " دقیقه")
            }
        }
        item {
            HubCard("⚡ Quick Add", "افزودن سریع با تشخیص تاریخ و ساعت") {
                OutlinedTextField(quickText, { quickText = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("مثلاً: فردا ساعت ۸ قبض را پرداخت کنم") })
                Button(onClick = {
                    val parsed = parseQuickTask(quickText)
                    if (parsed != null) { onAddTask(parsed); quickText = ""; quickMessage = "کار اضافه شد." }
                    else quickMessage = "عنوان کار را وارد کنید."
                }, Modifier.fillMaxWidth()) { Text("افزودن سریع") }
                if (quickMessage.isNotBlank()) Text(quickMessage, color = MaterialTheme.colorScheme.primary)
            }
        }
        item {
            HubCard("🧠 Smart Reschedule", "پیشنهاد جابه‌جایی کارهای عقب‌افتاده") {
                val candidates = tasks.filter { !it.done && parseHubDate(it.dueDate)?.isBefore(today) == true }.take(8)
                if (candidates.isEmpty()) Text("کار عقب‌افتاده‌ای نیست.")
                candidates.forEach { task ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(task.title, Modifier.weight(1f), maxLines = 1)
                        TextButton(onClick = { onUpdateTask(task.copy(dueDate = today.plusDays(1).toString())) }) { Text("فردا") }
                    }
                }
                Text("تغییر تاریخ فقط با تأیید شما انجام می‌شود.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            HubCard("📈 گزارش من", "روند روزانه، هفتگی و ماهانه") {
                val weekDone = tasks.count { it.done && parseHubDate(it.dueDate)?.let { d -> d in today.minusDays(6)..today } == true }
                val monthDone = tasks.count { it.done && parseHubDate(it.dueDate)?.let { d -> d.year == today.year && d.month == today.month } == true }
                Text("۷ روز اخیر: " + weekDone + " • این ماه: " + monthDone)
                Text("باز: " + open + " • انجام‌شده: " + done + " • عقب‌افتاده: " + overdue)
            }
        }
        item {
            HubCard("🎨 تم‌های قابل انتخاب", "۶ ظاهر آفلاین") {
                val themes = listOf("system" to "پیش‌فرض", "minimal" to "Minimal", "dark" to "Dark", "ocean" to "Ocean", "nature" to "Nature", "sunset" to "Sunset")
                var selected by remember { mutableStateOf(YadavarProgressStore.theme(context)) }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    themes.forEach { (key, label) ->
                        FilterChip(selected = selected == key, onClick = { selected = key; YadavarProgressStore.setTheme(context, key) }, label = { Text(label) })
                    }
                }
                Text("برای اعمال فوری، صفحه را یک‌بار باز و بسته کنید.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            HubCard("🔐 حریم خصوصی", "قفل و اعلان خصوصی") {
                val pinSet = ExtraFeaturesStore.pin(context).isNotBlank()
                var privateNotifications by remember { mutableStateOf(YadavarProgressStore.privateNotifications(context)) }
                Text(if (pinSet) "PIN برنامه فعال است." else "PIN را از تنظیمات فعال کنید.")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("اعلان خصوصی", Modifier.weight(1f))
                    Switch(privateNotifications, { privateNotifications = it; YadavarProgressStore.setPrivateNotifications(context, it) })
                }
            }
        }
        item {
            HubCard("🧭 مرکز موفقیت", "جمع‌بندی پیشرفت") {
                val level = done / 10 + 1
                val xp = done * 25 + focusTotal / 5
                Text("Level " + level + " • " + xp + " XP • Streak " + streak + " روز • تمرکز " + focusTotal + " دقیقه")
                Text("همه اطلاعات روی دستگاه ذخیره می‌شوند.")
            }
        }
    }
}

@Composable
private fun HubCard(title: String, description: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

@Composable
private fun StatBox(title: String, value: String, modifier: Modifier) {
    Card(modifier) { Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold)
        Text(title, style = MaterialTheme.typography.labelSmall)
    } }
}

private fun buildAchievements(tasks: List<TodoItem>, streak: Int, focus: Int): List<Pair<String, Boolean>> = listOf(
    "اولین کار" to (tasks.count { it.done } >= 1),
    "۱۰ کار انجام‌شده" to (tasks.count { it.done } >= 10),
    "۱۰۰ کار انجام‌شده" to (tasks.count { it.done } >= 100),
    "Streak هفت‌روزه" to (streak >= 7),
    "Streak سی‌روزه" to (streak >= 30),
    "۶۰ دقیقه تمرکز" to (focus >= 60),
    "۱۰ یادآوری" to (tasks.count { it.hasReminder } >= 10)
)

private fun calculateStreak(dates: Set<String>, today: LocalDate): Int {
    var day = if (dates.contains(today.toString())) today else today.minusDays(1)
    var count = 0
    while (dates.contains(day.toString())) { count++; day = day.minusDays(1) }
    return count
}

private fun calculateBestStreak(dates: Set<String>): Int {
    val sorted = dates.mapNotNull { parseHubDate(it) }.distinct().sorted()
    if (sorted.isEmpty()) return 0
    var best = 1
    var current = 1
    for (i in 1 until sorted.size) {
        if (ChronoUnit.DAYS.between(sorted[i - 1], sorted[i]) == 1L) { current++; best = maxOf(best, current) }
        else current = 1
    }
    return best
}

private fun parseQuickTask(input: String): TodoItem? {
    var text = input.trim().replace('۰','0').replace('۱','1').replace('۲','2').replace('۳','3').replace('۴','4').replace('۵','5').replace('۶','6').replace('۷','7').replace('۸','8').replace('۹','9')
    if (text.isBlank()) return null
    val today = LocalDate.now()
    var date = ""
    when {
        text.contains("پس‌فردا") || text.contains("پس فردا") -> { date = today.plusDays(2).toString(); text = text.replace("پس‌فردا","").replace("پس فردا","") }
        text.contains("فردا") -> { date = today.plusDays(1).toString(); text = text.replace("فردا","") }
        text.contains("امروز") -> { date = today.toString(); text = text.replace("امروز","") }
        else -> Regex("""\\b(20\\d{2}-\\d{2}-\\d{2})\\b""").find(text)?.let { date = it.value; text = text.replace(it.value, "") }
    }
    val timeMatch = Regex("""(?:ساعت|at)\\s*(\\d{1,2})(?::|[٫.]?)(\\d{2})?""").find(text)
    var hour: Int? = null
    var minute: Int? = null
    if (timeMatch != null) {
        hour = timeMatch.groupValues[1].toIntOrNull()?.coerceIn(0,23)
        minute = (timeMatch.groupValues[2].ifBlank { "0" }).toIntOrNull()?.coerceIn(0,59)
        text = text.replace(timeMatch.value, "")
    }
    text = text.replace(Regex("""\\s+"""), " ").trim(' ', '-', '،', ',')
    if (text.isBlank()) return null
    return TodoItem(id = 0, title = text, reminderHour = hour, reminderMinute = minute, dueDate = date)
}

private fun parseHubDate(value: String): LocalDate? = runCatching { if (value.isBlank()) null else LocalDate.parse(value) }.getOrNull()
private fun formatTimer(seconds: Long): String = "%02d:%02d".format(seconds / 60, seconds % 60)
