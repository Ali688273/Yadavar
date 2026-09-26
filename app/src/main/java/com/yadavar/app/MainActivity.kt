package com.yadavar.app

import android.Manifest
import android.content.Context\nimport android.content.pm.ShortcutInfo\nimport android.content.pm.ShortcutManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.net.Uri
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import com.yadavar.app.core.BackupManager
import com.yadavar.app.core.BirthdayNotificationScheduler
import com.yadavar.app.core.BirthdayReminderEngine
import com.yadavar.app.core.StoredBirthday
import com.yadavar.app.core.TaskNotificationScheduler
import com.yadavar.app.core.TaskReminder
import com.yadavar.app.core.TaskReminderCodec
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class TodoItem(
    val id: Int,
    val title: String,
    val done: Boolean = false,
    val reminderHour: Int? = null,
    val reminderMinute: Int? = null,
    val repeat: String = "none",
    val category: String = "عمومی",
    val priority: String = "normal",
    val startDate: String = "",
    val dueDate: String = "",
    val note: String = "",
    val tags: String = "",
    val subtasks: String = "",
    val location: String = "",
    val customEvery: Int = 1,
    val customUnit: String = "day",
    val reminders: List<TaskReminder> = emptyList()
) {
    val hasReminder: Boolean get() = reminders.isNotEmpty() || (reminderHour != null && reminderMinute != null)
}

class MainActivity : ComponentActivity() {
    private val permission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) permission.launch(Manifest.permission.POST_NOTIFICATIONS)

        if (Build.VERSION.SDK_INT >= 25) {\n            val manager = getSystemService(ShortcutManager::class.java)\n            manager.dynamicShortcuts = listOf(\n                ShortcutInfo.Builder(this, "new_task").setShortLabel("کار جدید").setLongLabel("افزودن کار جدید").setIcon(android.graphics.drawable.Icon.createWithResource(this, android.R.drawable.ic_input_add)).setIntent(android.content.Intent(this, MainActivity::class.java)).build(),\n                ShortcutInfo.Builder(this, "birthdays").setShortLabel("تولدها").setLongLabel("باز کردن تولدها").setIcon(android.graphics.drawable.Icon.createWithResource(this, android.R.drawable.ic_menu_my_calendar)).setIntent(android.content.Intent(this, MainActivity::class.java)).build()\n            )\n        }\n        setContent { YadavarApp(this) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YadavarApp(context: Context) {
    var tab by remember { mutableIntStateOf(0) }
    var addTask by remember { mutableStateOf(false) }
    var addBirthday by remember { mutableStateOf(false) }
    var editingBirthday by remember { mutableStateOf<StoredBirthday?>(null) }
    var editingTask by remember { mutableStateOf<TodoItem?>(null) }
    var showDeleteCompleted by remember { mutableStateOf(false) }
    var taskToDelete by remember { mutableStateOf<TodoItem?>(null) }
    var birthdayToDelete by remember { mutableStateOf<StoredBirthday?>(null) }
    var taskQuery by remember { mutableStateOf("") }\n    var unlocked by rememberSaveable { mutableStateOf(ExtraFeaturesStore.pin(context).isBlank()) }\n    var unlockPin by remember { mutableStateOf("") }

    val tasks = remember { mutableStateListOf<TodoItem>().apply { addAll(loadTasks(context)) } }
    val birthdays = remember { mutableStateListOf<StoredBirthday>().apply { addAll(loadBirthdays(context)) } }

    val exportBackupLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
                it.write(BackupManager.createBackup(context))
            } ?: error("فایل خروجی باز نشد.")
            Toast.makeText(context, "پشتیبان با موفقیت ذخیره شد.", Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(context, "ذخیره پشتیبان انجام نشد.", Toast.LENGTH_LONG).show()
        }
    }

    val importBackupLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val backup = BackupManager.restoreFromUri(context, uri)
            BackupManager.applyBackup(context, backup)
            tasks.clear()
            tasks.addAll(loadTasks(context))
            birthdays.clear()
            birthdays.addAll(loadBirthdays(context))
            TaskNotificationScheduler.scheduleAll(context, tasks)
            BirthdayNotificationScheduler.scheduleAll(context, birthdays)
            Toast.makeText(context, "بازیابی انجام شد: " + backup.tasks.size + " کار و " + backup.birthdays.size + " تولد", Toast.LENGTH_LONG).show()
        }.onFailure {
            Toast.makeText(context, "بازیابی انجام نشد: " + (it.message ?: "فایل نامعتبر است"), Toast.LENGTH_LONG).show()
        }
    }


    fun saveT() {
        saveTasks(context, tasks)
        TaskNotificationScheduler.scheduleAll(context, tasks)
    }
    fun saveB() {
        saveBirthdays(context, birthdays)
        BirthdayNotificationScheduler.scheduleAll(context, birthdays)
    }

    LaunchedEffect(Unit) {
        BirthdayNotificationScheduler.scheduleAll(context, birthdays)
        TaskNotificationScheduler.scheduleAll(context, tasks)
    }

    Scaffold(
        topBar = {
            TopAppBar(title = {
                Text(
                    if (tab == 0) "یادآور" else if (tab == 1) "تولدها 🎂" else if (tab == 2) "حرفه‌ای" else "تنظیمات",
                    fontWeight = FontWeight.Bold
                )
            })
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Icon(Icons.Default.Checklist, "کارها") }, label = { Text("کارها") })
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Icon(Icons.Default.Cake, "تولدها") }, label = { Text("تولدها") })
                NavigationBarItem(selected = tab == 2, onClick = { tab = 2 }, icon = { Icon(Icons.Default.AutoAwesome, "حرفه‌ای") }, label = { Text("حرفه‌ای") })
                NavigationBarItem(selected = tab == 3, onClick = { tab = 3 }, icon = { Icon(Icons.Default.Settings, "تنظیمات") }, label = { Text("تنظیمات") })
            }
        },
        floatingActionButton = {
            if (tab < 2) {
                FloatingActionButton(onClick = { if (tab == 0) addTask = true else addBirthday = true }) {
                    Icon(Icons.Default.Add, "افزودن")
                }
            }
        }
    ) { pad ->
        when (tab) {
            0 -> TodoScreen(
                tasks = tasks,
                query = taskQuery,
                onQueryChange = { taskQuery = it },
                toggle = { id ->
                    val i = tasks.indexOfFirst { it.id == id }
                    if (i >= 0) {
                        tasks[i] = tasks[i].copy(done = !tasks[i].done)
                        saveT()
                    }
                },
                edit = { editingTask = it },
                delete = { id ->
                    taskToDelete = tasks.firstOrNull { it.id == id }
                },
                onRequestClear = { showDeleteCompleted = true },
                modifier = Modifier.padding(pad)
            )
            1 -> BirthdayScreen(
                list = birthdays,
                edit = { editingBirthday = it },
                delete = { b ->
                    birthdayToDelete = b
                },
                modifier = Modifier.padding(pad)
            )
            2 -> ProfessionalScreen(context = context, tasks = tasks, onAdd = { tasks.add(it); saveT() }, onUpdate = { item -> val i = tasks.indexOfFirst { it.id == item.id }; if (i >= 0) { tasks[i] = item; saveT() } }, onDelete = { id -> tasks.removeAll { it.id == id }; saveT() }, modifier = Modifier.padding(pad))
            else -> SettingsScreen(
                total = tasks.size,
                done = tasks.count { it.done },
                birthdays = birthdays.size,
                onExportBackup = {
                    exportBackupLauncher.launch("yadavar-backup-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".json")
                },
                onImportBackup = {
                    importBackupLauncher.launch(arrayOf("application/json", "text/*", "application/octet-stream"))
                },
                modifier = Modifier.padding(pad)
            )
        }
    }

    if (!unlocked) {\n        AlertDialog(onDismissRequest = {}, title = { Text("قفل برنامه") }, text = { OutlinedTextField(unlockPin, { unlockPin = it.filter(Char::isDigit).take(8) }, singleLine = true, label = { Text("PIN") }) }, confirmButton = { Button(onClick = { if (unlockPin == ExtraFeaturesStore.pin(context)) { unlocked = true; unlockPin = "" } }) { Text("ورود") } })\n    }\n\n    if (taskToDelete != null) {
        val task = taskToDelete!!
        AlertDialog(
            onDismissRequest = { taskToDelete = null },
            title = { Text("حذف کار") },
            text = { Text("کار «" + task.title + "» حذف شود؟") },
            confirmButton = {
                Button(onClick = {
                    TaskNotificationScheduler.cancelTask(context, task.id)
                    tasks.removeAll { it.id == task.id }
                    saveT()
                    taskToDelete = null
                }) { Text("حذف") }
            },
            dismissButton = { TextButton(onClick = { taskToDelete = null }) { Text("انصراف") } }
        )
    }

    if (birthdayToDelete != null) {
        val birthday = birthdayToDelete!!
        AlertDialog(
            onDismissRequest = { birthdayToDelete = null },
            title = { Text("حذف تولد") },
            text = { Text("تولد «" + birthday.name + "» حذف شود؟") },
            confirmButton = {
                Button(onClick = {
                    BirthdayNotificationScheduler.cancelBirthday(
                        context,
                        birthday.id,
                        birthday.month,
                        birthday.day
                    )
                    birthdays.removeAll { it.id == birthday.id }
                    saveB()
                    birthdayToDelete = null
                }) { Text("حذف") }
            },
            dismissButton = { TextButton(onClick = { birthdayToDelete = null }) { Text("انصراف") } }
        )
    }

    if (showDeleteCompleted) {
        AlertDialog(
            onDismissRequest = { showDeleteCompleted = false },
            title = { Text("حذف کارهای انجام‌شده") },
            text = { Text("همه کارهای انجام‌شده حذف شوند؟") },
            confirmButton = {
                Button(onClick = {
                    tasks.removeAll { it.done }
                    saveT()
                    showDeleteCompleted = false
                }) { Text("حذف") }
            },
            dismissButton = { TextButton(onClick = { showDeleteCompleted = false }) { Text("انصراف") } }
        )
    }

    if (addTask) {
        AddTaskDialog(dismiss = { addTask = false }) { title, hour, minute, repeat, category, priority, reminders ->
            if (title.trim().isNotEmpty()) {
                tasks.add(
                    TodoItem(
                        id = (tasks.maxOfOrNull { it.id } ?: 0) + 1,
                        title = title.trim(),
                        reminderHour = hour,
                        reminderMinute = minute,
                        repeat = repeat,
                        category = category,
                        priority = priority,
                        reminders = reminders
                    )
                )
                saveT()
            }
            addTask = false
        }
    }

    if (editingTask != null) {
        val task = editingTask!!
        EditTaskDialog(
            task = task,
            dismiss = { editingTask = null },
            save = { title, hour, minute, repeat, category, priority, reminders ->
                val index = tasks.indexOfFirst { it.id == task.id }
                if (index >= 0) {
                    tasks[index] = task.copy(
                        title = title.trim(),
                        reminderHour = hour,
                        reminderMinute = minute,
                        repeat = repeat,
                        category = category,
                        priority = priority,
                        reminders = reminders
                    )
                    saveT()
                }
                editingTask = null
            }
        )
    }

    if (addBirthday) {
        AddBirthdayDialog(dismiss = { addBirthday = false }) { name, m, d ->
            if (name.trim().isNotEmpty() && isValidBirthdayDate(m, d)) {
                birthdays.add(
                    StoredBirthday(
                        (birthdays.maxOfOrNull { it.id } ?: 0) + 1,
                        name.trim(),
                        m,
                        d
                    )
                )
                saveB()
            }
            addBirthday = false
        }
    }

    editingBirthday?.let { birthday ->
        EditBirthdayDialog(
            birthday = birthday,
            dismiss = { editingBirthday = null },
            save = { name, month, day ->
                val index = birthdays.indexOfFirst { it.id == birthday.id }
                if (index >= 0) {
                    BirthdayNotificationScheduler.cancelBirthday(
                        context,
                        birthday.id,
                        birthday.month,
                        birthday.day
                    )
                    birthdays[index] = birthday.copy(
                        name = name.trim(),
                        month = month,
                        day = day
                    )
                    saveB()
                }
                editingBirthday = null
            }
        )
    }
}

@Composable
fun TodoScreen(
    tasks: List<TodoItem>,
    query: String,
    onQueryChange: (String) -> Unit,
    edit: (TodoItem) -> Unit,
    toggle: (Int) -> Unit,
    delete: (Int) -> Unit,
    onRequestClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val done = tasks.count { it.done }
    val filtered = if (query.isBlank()) tasks else tasks.filter {
        it.title.contains(query.trim(), ignoreCase = true)
    }
    val progress = if (tasks.isEmpty()) 0f else done.toFloat() / tasks.size.toFloat()

    Column(modifier.fillMaxSize().padding(16.dp)) {
        Text("کارهای امروز", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            if (tasks.isEmpty()) "هنوز کاری ثبت نشده است."
            else done.toString() + " از " + tasks.size + " کار انجام شده",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (tasks.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "پاک کردن")
                    }
                }
            },
            label = { Text("جستجوی کارها") }
        )
        if (done > 0) {
            TextButton(onClick = onRequestClear) {
                Icon(Icons.Default.RemoveDone, null)
                Spacer(Modifier.width(4.dp))
                Text("حذف انجام‌شده‌ها")
            }
        }
        if (tasks.isEmpty()) {
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.CheckCircle, null)
                Spacer(Modifier.height(10.dp))
                Text("برای شروع روی + بزنید.")
            }
        } else {
            if (filtered.isEmpty()) {
                Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.SearchOff, null)
                    Spacer(Modifier.height(8.dp))
                    Text("کاری با این عبارت پیدا نشد.")
                }
            } else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(filtered, key = { it.id }) { t ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(t.done, { toggle(t.id) })
                            Column(Modifier.weight(1f).padding(horizontal = 6.dp)) {
                                Text(t.title, fontSize = 17.sp, fontWeight = if (t.priority == "high") FontWeight.Bold else FontWeight.Normal)
                                Text(t.category + " • " + priorityLabel(t.priority),
                                    fontSize = 12.sp,
                                    color = if (t.priority == "high") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                                if (t.hasReminder) {
                                    val reminderText = if (t.reminders.isNotEmpty()) {
                                        t.reminders.joinToString("، ") { it.label() }
                                    } else {
                                        t.reminderHour.toString().padStart(2, '0') + ":" +
                                            t.reminderMinute.toString().padStart(2, '0')
                                    }
                                    Text(
                                        "⏰ " + reminderText + " • " + repeatLabel(t.repeat),
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            IconButton({ edit(t) }) { Icon(Icons.Default.Edit, "ویرایش") }
                            IconButton({ delete(t.id) }) { Icon(Icons.Default.Delete, "حذف") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EditTaskDialog(
    task: TodoItem,
    dismiss: () -> Unit,
    save: (String, Int?, Int?, String, String, String, List<TaskReminder>) -> Unit
) {
    var title by remember(task.id) { mutableStateOf(task.title) }
    var category by remember(task.id) { mutableStateOf(task.category) }
    var priority by remember(task.id) { mutableStateOf(task.priority) }
    var reminderEnabled by remember(task.id) { mutableStateOf(task.hasReminder) }
    var hour by remember(task.id) { mutableStateOf((task.reminderHour ?: 9).toString()) }
    var minute by remember(task.id) { mutableStateOf((task.reminderMinute ?: 0).toString().padStart(2, '0')) }
    var repeat by remember(task.id) { mutableStateOf(task.repeat) }
    var reminders by remember(task.id) {
        mutableStateOf(
            if (task.reminders.isNotEmpty()) task.reminders
            else if (task.hasReminder) listOf(TaskReminder(task.reminderHour ?: 9, task.reminderMinute ?: 0))
            else emptyList()
        )
    }

    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("ویرایش کار") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    title, { title = it }, Modifier.fillMaxWidth(),
                    singleLine = true, label = { Text("عنوان کار") }
                )
                CategorySelector(category, { category = it })
                PrioritySelector(priority, { priority = it })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(reminderEnabled, { reminderEnabled = it })
                    Text("یادآوری زمان‌دار")
                }
                if (reminderEnabled) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            hour, { hour = it.filter(Char::isDigit).take(2) },
                            Modifier.weight(1f), singleLine = true, label = { Text("ساعت") }
                        )
                        OutlinedTextField(
                            minute, { minute = it.filter(Char::isDigit).take(2) },
                            Modifier.weight(1f), singleLine = true, label = { Text("دقیقه") }
                        )
                    }
                    RepeatSelector(repeat = repeat, onRepeatChange = { repeat = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val h = hour.toIntOrNull()?.takeIf { it in 0..23 }
                    val m = minute.toIntOrNull()?.takeIf { it in 0..59 }
                    save(title, if (reminderEnabled) h else null, if (reminderEnabled) m else null, if (reminderEnabled) repeat else "none", category, priority, if (reminderEnabled) reminders else emptyList())
                },
                enabled = title.trim().isNotEmpty() &&
                    (!reminderEnabled || (hour.toIntOrNull() in 0..23 && minute.toIntOrNull() in 0..59))
            ) { Text("ذخیره تغییرات") }
        },
        dismissButton = { TextButton(dismiss) { Text("انصراف") } }
    )
}

@Composable
fun AddTaskDialog(
    dismiss: () -> Unit,
    add: (String, Int?, Int?, String, String, String, List<TaskReminder>) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("عمومی") }
    var priority by remember { mutableStateOf("normal") }
    var reminderEnabled by remember { mutableStateOf(false) }
    var hour by remember { mutableStateOf("9") }
    var minute by remember { mutableStateOf("00") }
    var repeat by remember { mutableStateOf("daily") }
    var reminders by remember { mutableStateOf(listOf<TaskReminder>()) }

    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("کار جدید") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    title, { title = it }, Modifier.fillMaxWidth(),
                    singleLine = true, label = { Text("عنوان کار") }
                )
                CategorySelector(category, { category = it })
                PrioritySelector(priority, { priority = it })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(reminderEnabled, { reminderEnabled = it })
                    Text("یادآوری زمان‌دار")
                }
                if (reminderEnabled) {
                    ReminderEditor(reminders) { reminders = it }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            hour, { hour = it.filter(Char::isDigit).take(2) },
                            Modifier.weight(1f), singleLine = true, label = { Text("ساعت") }
                        )
                        OutlinedTextField(
                            minute, { minute = it.filter(Char::isDigit).take(2) },
                            Modifier.weight(1f), singleLine = true, label = { Text("دقیقه") }
                        )
                    }
                    RepeatSelector(repeat = repeat, onRepeatChange = { repeat = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val h = hour.toIntOrNull()?.takeIf { it in 0..23 }
                    val m = minute.toIntOrNull()?.takeIf { it in 0..59 }
                    add(title, if (reminderEnabled) h else null, if (reminderEnabled) m else null, if (reminderEnabled) repeat else "none", category, priority, if (reminderEnabled) reminders else emptyList())
                },
                enabled = title.trim().isNotEmpty() &&
                    (!reminderEnabled || (hour.toIntOrNull() in 0..23 && minute.toIntOrNull() in 0..59))
            ) { Text("افزودن") }
        },
        dismissButton = { TextButton(dismiss) { Text("انصراف") } }
    )
}

@Composable
fun ReminderEditor(
    reminders: List<TaskReminder>,
    onChange: (List<TaskReminder>) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("چند زمان یادآوری (حداکثر ۸)", fontWeight = FontWeight.Bold)
        reminders.forEachIndexed { index, r ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(r.label(), Modifier.weight(1f))
                IconButton(onClick = {
                    onChange(reminders.toMutableList().also { it.removeAt(index) })
                }) { Icon(Icons.Default.Delete, "حذف زمان") }
            }
        }
        var newHour by remember { mutableStateOf("09") }
        var newMinute by remember { mutableStateOf("00") }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(newHour, { newHour = it.filter(Char::isDigit).take(2) }, Modifier.weight(1f), singleLine = true, label = { Text("ساعت") })
            OutlinedTextField(newMinute, { newMinute = it.filter(Char::isDigit).take(2) }, Modifier.weight(1f), singleLine = true, label = { Text("دقیقه") })
            OutlinedButton(
                onClick = {
                    val h = newHour.toIntOrNull()
                    val m = newMinute.toIntOrNull()
                    if (h != null && m != null && h in 0..23 && m in 0..59 && reminders.none { it.hour == h && it.minute == m }) {
                        onChange((reminders + TaskReminder(h, m)).sortedBy { it.hour * 60 + it.minute }.take(8))
                    }
                },
                enabled = reminders.size < 8
            ) { Text("+") }
        }
    }
}

@Composable
fun RepeatSelector(repeat: String, onRepeatChange: (String) -> Unit) {
    Text("تکرار یادآوری", fontWeight = FontWeight.Bold)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        FilterChip(
            selected = repeat == "daily",
            onClick = { onRepeatChange("daily") },
            label = { Text("روزانه") }
        )
        FilterChip(
            selected = repeat == "weekly",
            onClick = { onRepeatChange("weekly") },
            label = { Text("هفتگی") }
        )
        FilterChip(
            selected = repeat == "monthly",
            onClick = { onRepeatChange("monthly") },
            label = { Text("ماهانه") }
        )
        FilterChip(
            selected = repeat == "weekdays",
            onClick = { onRepeatChange("weekdays") },
            label = { Text("روزهای کاری") }
        )
        FilterChip(
            selected = repeat == "weekends",
            onClick = { onRepeatChange("weekends") },
            label = { Text("آخرهفته") }
        )
    }
}



@Composable
fun CategorySelector(value: String, onChange: (String) -> Unit) {
    Text("دسته‌بندی", fontWeight = FontWeight.Bold)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf("عمومی", "کار", "شخصی", "خرید").forEach { item ->
            FilterChip(
                selected = value == item,
                onClick = { onChange(item) },
                label = { Text(item) }
            )
        }
    }
}

@Composable
fun PrioritySelector(value: String, onChange: (String) -> Unit) {
    Text("اولویت", fontWeight = FontWeight.Bold)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        FilterChip(selected = value == "low", onClick = { onChange("low") }, label = { Text("کم") })
        FilterChip(selected = value == "normal", onClick = { onChange("normal") }, label = { Text("عادی") })
        FilterChip(selected = value == "high", onClick = { onChange("high") }, label = { Text("مهم") })
    }
}

private fun priorityLabel(value: String): String = when (value) {
    "low" -> "اولویت کم"
    "high" -> "اولویت مهم"
    else -> "اولویت عادی"
}

@Composable
fun BirthdayScreen(
    list: List<StoredBirthday>,
    edit: (StoredBirthday) -> Unit,
    delete: (StoredBirthday) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxSize().padding(16.dp)) {
        Text("تولد عزیزان", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("یک روز قبل و روز تولد اعلان ارسال می‌شود.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(14.dp))
        if (list.isEmpty()) {
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.Cake, null)
                Spacer(Modifier.height(10.dp))
                Text("اولین تولد را با + اضافه کنید 🎂")
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(list, key = { it.id }) { b ->
                    val r = BirthdayReminderEngine.reminder(b.name, b.month, b.day)
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Cake, null)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(b.name, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                Text("تاریخ: " + b.day + "/" + b.month, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(if (r.isToday) "امروز تولدشه 🎉" else r.daysUntil.toString() + " روز تا تولد")
                            }
                            IconButton({ edit(b) }) { Icon(Icons.Default.Edit, "ویرایش") }
                            IconButton({ delete(b) }) { Icon(Icons.Default.Delete, "حذف") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AddBirthdayDialog(dismiss: () -> Unit, add: (String, Int, Int) -> Unit) {
    var name by remember { mutableStateOf("") }
    var day by remember { mutableStateOf("") }
    var month by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    val d = day.toIntOrNull()
    val m = month.toIntOrNull()

    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("افزودن تولد") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("نام") })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(day, { day = it.filter(Char::isDigit) }, Modifier.weight(1f), singleLine = true, label = { Text("روز") })
                    OutlinedTextField(month, { month = it.filter(Char::isDigit) }, Modifier.weight(1f), singleLine = true, label = { Text("ماه") })
                }
                Text("مثال: 15 / 7", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            Button(onClick = {
                when {
                    name.isBlank() -> error = "نام را وارد کنید"
                    m == null || d == null -> error = "روز و ماه را وارد کنید"
                    !isValidBirthdayDate(m, d) -> error = "این تاریخ معتبر نیست"
                    else -> add(name, m, d)
                }
            }) { Text("ذخیره") }
        },
        dismissButton = { TextButton(dismiss) { Text("انصراف") } }
    )
}

@Composable
fun EditBirthdayDialog(
    birthday: StoredBirthday,
    dismiss: () -> Unit,
    save: (String, Int, Int) -> Unit
) {
    var name by remember(birthday.id) { mutableStateOf(birthday.name) }
    var day by remember(birthday.id) { mutableStateOf(birthday.day.toString()) }
    var month by remember(birthday.id) { mutableStateOf(birthday.month.toString()) }
    var error by remember(birthday.id) { mutableStateOf("") }

    val d = day.toIntOrNull()
    val m = month.toIntOrNull()

    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("ویرایش تولد") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    name,
                    { name = it },
                    Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("نام") }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        day,
                        { day = it.filter(Char::isDigit) },
                        Modifier.weight(1f),
                        singleLine = true,
                        label = { Text("روز") }
                    )
                    OutlinedTextField(
                        month,
                        { month = it.filter(Char::isDigit) },
                        Modifier.weight(1f),
                        singleLine = true,
                        label = { Text("ماه") }
                    )
                }
                Text("مثال: 15 / 7", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            Button(onClick = {
                when {
                    name.isBlank() -> error = "نام را وارد کنید"
                    m == null || d == null -> error = "روز و ماه را وارد کنید"
                    !isValidBirthdayDate(m, d) -> error = "این تاریخ معتبر نیست"
                    else -> save(name, m, d)
                }
            }) { Text("ذخیره تغییرات") }
        },
        dismissButton = { TextButton(dismiss) { Text("انصراف") } }
    )
}

@Composable
fun SettingsScreen(
    total: Int,
    done: Int,
    birthdays: Int,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxSize().padding(20.dp)) {
        Text("تنظیمات", fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("آمار", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("کل کارها: " + total)
                Text("انجام‌شده: " + done)
                Text("باقی‌مانده: " + (total - done))
                Text("درصد انجام: " + (if (total == 0) 0 else done * 100 / total) + "%")
                Spacer(Modifier.height(8.dp))
                if (total > 0) {
                    LinearProgressIndicator(progress = { done.toFloat() / total.toFloat() }, modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(8.dp))
                Text("تولدهای ذخیره‌شده: " + birthdays)
                Spacer(Modifier.height(12.dp))
                PersonalizationSettings(context)
            }
        }
        Spacer(Modifier.height(12.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("پشتیبان آفلاین", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("از کارها، تولدها، عادت‌ها و لیست خرید یک فایل JSON روی گوشی بساز یا آن را بازیابی کن.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = onExportBackup, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Upload, null)
                    Spacer(Modifier.width(8.dp))
                    Text("ساخت و ذخیره پشتیبان")
                }
                OutlinedButton(onClick = onImportBackup, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Download, null)
                    Spacer(Modifier.width(8.dp))
                    Text("بازیابی از فایل")
                }
                Text("بازیابی اطلاعات فعلی این برنامه را با اطلاعات فایل جایگزین می‌کند.", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

fun loadTasks(context: Context): List<TodoItem> {
    val prefs = context.getSharedPreferences("yadavar_data", 0)
    val raw = prefs.getString("tasks", null) ?: return emptyList()
    val savedDate = prefs.getString("tasks_date", null)
    val today = currentTaskDate()
    val shouldReset = savedDate == null || savedDate != today

    val list = raw.split("\n").mapNotNull { p ->
        val x = p.split("\t", limit = 17)
        if (x.size < 3) null
        else {
            val id = x[0].toIntOrNull()
            if (id == null) null
            else {
                val hour = x.getOrNull(3)?.toIntOrNull()
                val minute = x.getOrNull(4)?.toIntOrNull()
                val repeat = x.getOrNull(5)?.takeIf {
                    it == "none" || it == "daily" || it == "weekly" || it == "monthly" || it == "yearly" || it == "custom" || it == "weekdays" || it == "weekends"
                } ?: "none"
                val category = x.getOrNull(6)?.ifBlank { "عمومی" } ?: "عمومی"
                val priority = x.getOrNull(7)?.takeIf { it == "low" || it == "normal" || it == "high" } ?: "normal"
                TodoItem(
                    id = id,
                    title = x[2],
                    done = if (shouldReset) false else x[1] == "1",
                    reminderHour = hour?.takeIf { it in 0..23 },
                    reminderMinute = minute?.takeIf { it in 0..59 },
                    repeat = repeat,
                    category = category,
                    priority = priority,
                    startDate = x.getOrNull(8).orEmpty(),
                    dueDate = x.getOrNull(9).orEmpty(),
                    note = x.getOrNull(10).orEmpty(),
                    tags = x.getOrNull(11).orEmpty(),
                    subtasks = x.getOrNull(12).orEmpty(),
                    location = x.getOrNull(13).orEmpty(),
                    customEvery = x.getOrNull(14)?.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                    customUnit = x.getOrNull(15).orEmpty().ifBlank { "day" },
                    reminders = TaskReminderCodec.decode(x.getOrNull(16)).ifEmpty {
                        val h = hour?.takeIf { it in 0..23 }
                        val m = minute?.takeIf { it in 0..59 }
                        if (h != null && m != null) listOf(TaskReminder(h, m)) else emptyList()
                    }
                )
            }
        }
    }

    prefs.edit().putString("tasks_date", today).apply()
    return list
}

private fun saveTasks(context: Context, list: List<TodoItem>) {
    context.getSharedPreferences("yadavar_data", 0).edit()
        .putString(
            "tasks",
            list.joinToString("\n") {
                it.id.toString() + "\t" +
                    (if (it.done) "1" else "0") + "\t" +
                    it.title.replace("\n", " ").replace("\t", " ") + "\t" +
                    (it.reminderHour?.toString() ?: "") + "\t" +
                    (it.reminderMinute?.toString() ?: "") + "\t" +
                    it.repeat + "\t" + it.category.replace("\n", " ").replace("\t", " ") +
                    "\t" + it.priority + "\t" + it.startDate + "\t" + it.dueDate + "\t" +
                    it.note.replace("\n", " ").replace("\t", " ") + "\t" + it.tags.replace("\n", " ").replace("\t", " ") + "\t" +
                    it.subtasks.replace("\n", " ").replace("\t", " ") + "\t" + it.location.replace("\n", " ").replace("\t", " ") + "\t" +
                    it.customEvery + "\t" + it.customUnit + "\t" +
                    TaskReminderCodec.encode(if (it.reminders.isNotEmpty()) it.reminders else if (it.hasReminder) listOf(TaskReminder(it.reminderHour!!, it.reminderMinute!!)) else emptyList())
            }
        )
        .putString("tasks_date", currentTaskDate())
        .apply()
}

fun loadBirthdays(context: Context): List<StoredBirthday> {
    val raw = context.getSharedPreferences("yadavar_data", 0)
        .getString("birthdays", null) ?: return emptyList()

    return raw.split("\n").mapNotNull { p ->
        val x = p.split("\t", limit = 4)
        if (x.size != 4) null
        else {
            val id = x[0].toIntOrNull()
            val m = x[2].toIntOrNull()
            val d = x[3].toIntOrNull()
            if (id == null || m == null || d == null || !isValidBirthdayDate(m, d)) null
            else StoredBirthday(id, x[1], m, d)
        }
    }
}

private fun saveBirthdays(context: Context, list: List<StoredBirthday>) {
    context.getSharedPreferences("yadavar_data", 0).edit()
        .putString(
            "birthdays",
            list.joinToString("\n") {
                it.id.toString() + "\t" +
                    it.name.replace("\n", " ").replace("\t", " ") +
                    "\t" + it.month + "\t" + it.day
            }
        ).apply()
}

private fun repeatLabel(value: String): String = when (value) {
    "weekly" -> "هفتگی"
    "monthly" -> "ماهانه"
    "daily" -> "روزانه"
    "yearly" -> "سالانه"
    "custom" -> "سفارشی"
    "weekdays" -> "روزهای کاری"
    "weekends" -> "آخرهفته"
    else -> "یک‌بار"
}

private fun currentTaskDate(): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

private fun isValidBirthdayDate(month: Int, day: Int): Boolean {
    if (month !in 1..12) return false
    val maxDay = when (month) {
        2 -> 29
        4, 6, 9, 11 -> 30
        else -> 31
    }
    return day in 1..maxDay
}

@Composable
private fun PersonalizationSettings(context: Context) {
    val prefs = context.getSharedPreferences("yadavar_data", 0)
    var compact by remember { mutableStateOf(prefs.getBoolean("compact_mode", false)) }
    var autoCarry by remember { mutableStateOf(prefs.getBoolean("smart_auto_carry", true)) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("شخصی‌سازی و رفتار هوشمند", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("نمای فشرده", Modifier.weight(1f))
                Switch(checked = compact, onCheckedChange = { compact = it; prefs.edit().putBoolean("compact_mode", it).apply() })
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("پیشنهاد خودکار کارهای عقب‌افتاده", Modifier.weight(1f))
                Switch(checked = autoCarry, onCheckedChange = { autoCarry = it; prefs.edit().putBoolean("smart_auto_carry", it).apply() })
            }
        }
    }
}
