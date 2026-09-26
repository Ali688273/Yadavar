package com.yadavar.app

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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

@Composable
fun CompleteFeaturesScreen(
    context: Context,
    tasks: List<TodoItem>,
    onUpdate: (TodoItem) -> Unit,
    onDelete: (Int) -> Unit
) {
    val today = LocalDate.now()
    var autoCarry by remember {
        mutableStateOf(context.getSharedPreferences("yadavar_data", 0).getBoolean("smart_auto_carry", true))
    }
    val overdue = tasks.filter { !it.done && parseAdvancedDate(it.dueDate)?.isBefore(today) == true }
    val dueToday = tasks.filter { !it.done && parseAdvancedDate(it.dueDate) == today }
    val high = tasks.filter { !it.done && it.priority == "high" }
    val upcoming = tasks.filter { !it.done && it.reminders.any { r -> reminderMinutesFromNow(r.hour, r.minute) in 0..120 } }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        item {
            Text("امکانات کامل", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("همه قابلیت‌های تکمیلی، آفلاین و بدون سرویس پولی.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            FeatureCard("🧠 روز من هوشمند", "عقب‌افتاده‌ها، امروز، مهم‌ها و یادآوری‌های نزدیک را اولویت‌بندی می‌کند.") {
                SmartDaySection(overdue, dueToday, high, upcoming, autoCarry) { value ->
                    autoCarry = value
                    context.getSharedPreferences("yadavar_data", 0).edit().putBoolean("smart_auto_carry", value).apply()
                }
            }
        }
        item {
            FeatureCard("🔁 تکرار پیشرفته", "تکرار روزانه، هفتگی، ماهانه، سالانه، روزهای کاری، آخرهفته و سفارشی در هسته اعلان‌ها فعال است.") {
                Text("تا ۸ زمان یادآوری برای هر کار پشتیبانی می‌شود.")
                Text("برای تکرار سفارشی، فاصله و واحد روز/هفته/ماه قابل تعیین است.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            FeatureCard("📅 تقویم کامل", "نمای روز، هفته و ماه و اتصال مستقیم کارها به تاریخ.") {
                Text("تقویم حرفه‌ای فعلی در بخش «حرفه‌ای» قابل استفاده است.")
            }
        }
        item {
            FeatureCard("🔎 فیلتر و جستجوی پیشرفته", "عنوان، یادداشت، برچسب، وضعیت، اولویت، امروز و عقب‌افتاده.") {
                Text("فیلترهای اصلی در «همه کارها» فعال‌اند و مرتب‌سازی بر اساس سررسید، اولویت، عنوان و زمان ایجاد انجام می‌شود.")
            }
        }
        item {
            FeatureCard("📊 آمار حرفه‌ای", "شاخص‌های اصلی عملکرد و روند انجام کارها.") {
                val total = tasks.size
                val done = tasks.count { it.done }
                val rate = if (total == 0) 0 else done * 100 / total
                Text("کل: " + total + " • انجام‌شده: " + done + " • عقب‌افتاده: " + overdue.size)
                LinearProgressIndicator(progress = { rate / 100f }, modifier = Modifier.fillMaxWidth())
                Text("نرخ تکمیل: " + rate + "٪")
            }
        }
        item {
            FeatureCard("🔔 یادآوری هوشمند", "یادآوری‌های نزدیک و خلاصه روز در همین دستگاه مدیریت می‌شوند.") {
                Text("اعلان‌ها بدون سرور و با AlarmManager دستگاه کار می‌کنند.")
                Text(if (upcoming.isNotEmpty()) upcoming.size.toString() + " یادآوری در دو ساعت آینده نزدیک است." else "در دو ساعت آینده یادآوری نزدیکی ثبت نشده است.")
            }
        }
        item {
            FeatureCard("📝 زیرکارهای کامل‌تر", "برای هر کار چند زیرکار داشته باشید و وضعیت هرکدام را جداگانه نگه دارید.") {
                tasks.filter { it.subtasks.isNotBlank() }.take(8).forEach { task ->
                    SubtaskProgress(task, onUpdate)
                }
            }
        }
        item {
            FeatureCard("🎨 شخصی‌سازی", "تنظیمات ظاهری و رفتاری برنامه بدون اینترنت ذخیره می‌شود.") {
                val prefs = context.getSharedPreferences("yadavar_data", 0)
                var compact by remember { mutableStateOf(prefs.getBoolean("compact_mode", false)) }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("نمای فشرده", Modifier.weight(1f))
                    Switch(checked = compact, onCheckedChange = {
                        compact = it
                        prefs.edit().putBoolean("compact_mode", it).apply()
                    })
                }
            }
        }
        item {
            FeatureCard("📱 ویجت کامل‌تر", "خلاصه وضعیت روز را روی صفحه اصلی نشان می‌دهد.") {
                Text("تعداد کل، انجام‌شده، باقی‌مانده، عقب‌افتاده و کار بعدی نمایش داده می‌شود.")
            }
        }
        item {
            FeatureCard("🛡️ پشتیبان‌گیری قوی‌تر", "کارها، تولدها، عادت‌ها، خرید و تنظیمات تکمیلی داخل JSON ذخیره می‌شوند.") {
                Text("نسخه جدید Backup با نسخه‌های قبلی سازگار می‌ماند.")
            }
        }
    }
}

@Composable
private fun SmartDaySection(
    overdue: List<TodoItem>,
    today: List<TodoItem>,
    high: List<TodoItem>,
    upcoming: List<TodoItem>,
    autoCarry: Boolean,
    onAutoCarry: (Boolean) -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("پیشنهاد خودکار کارهای عقب‌افتاده", Modifier.weight(1f))
        Switch(checked = autoCarry, onCheckedChange = onAutoCarry)
    }
    Text("عقب‌افتاده: " + overdue.size + " • امروز: " + today.size + " • مهم: " + high.size + " • نزدیک: " + upcoming.size)
    val suggestions = (overdue + today + high + upcoming).distinctBy { it.id }.take(10)
    suggestions.forEach {
        Text("• " + it.title + if (it.priority == "high") "  ⭐" else "")
    }
    if (suggestions.isEmpty()) Text("برای امروز پیشنهاد خاصی وجود ندارد.")
}

@Composable
private fun SubtaskProgress(task: TodoItem, onUpdate: (TodoItem) -> Unit) {
    val parts = task.subtasks.split("|").map { it.trim() }.filter { it.isNotBlank() }
    val done = parts.count { it.startsWith("[x]", true) }
    Column(Modifier.fillMaxWidth()) {
        Text(task.title + ": " + done + " از " + parts.size + " زیرکار")
        LinearProgressIndicator(progress = { if (parts.isEmpty()) 0f else done.toFloat() / parts.size }, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            parts.take(3).forEachIndexed { index, raw ->
                val checked = raw.startsWith("[x]", true)
                TextButton(onClick = {
                    val clean = raw.removePrefix("[x]").removePrefix("[ ]").trim()
                    val updated = parts.toMutableList()
                    updated[index] = if (checked) "[ ] " + clean else "[x] " + clean
                    onUpdate(task.copy(subtasks = updated.joinToString(" | ")))
                }) {
                    Text(if (checked) "✓ " + raw.removePrefix("[x]").trim() else "□ " + raw.removePrefix("[ ]").trim())
                }
            }
        }
    }
}

@Composable
private fun FeatureCard(title: String, description: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

private fun parseAdvancedDate(value: String): LocalDate? =
    runCatching { if (value.isBlank()) null else LocalDate.parse(value) }.getOrNull()

private fun reminderMinutesFromNow(hour: Int, minute: Int): Int {
    val now = java.time.LocalTime.now()
    var delta = hour * 60 + minute - now.hour * 60 - now.minute
    if (delta < -720) delta += 1440
    return delta
}
