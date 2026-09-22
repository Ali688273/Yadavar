package com.yadavar.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import com.yadavar.app.core.BirthdayReminderEngine

data class TodoItem(val id: Int, val title: String, val done: Boolean = false)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { YadavarApp(this) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YadavarApp(context: Context) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showAddDialog by remember { mutableStateOf(false) }
    val tasks = remember { mutableStateListOf<TodoItem>().apply { addAll(loadTasks(context)) } }

    fun save() = saveTasks(context, tasks)
    fun addTask(title: String) {
        val clean = title.trim()
        if (clean.isNotEmpty()) {
            tasks.add(TodoItem((tasks.maxOfOrNull { it.id } ?: 0) + 1, clean))
            save()
        }
    }
    fun toggleTask(id: Int) {
        val i = tasks.indexOfFirst { it.id == id }
        if (i >= 0) { tasks[i] = tasks[i].copy(done = !tasks[i].done); save() }
    }
    fun deleteTask(id: Int) { tasks.removeAll { it.id == id }; save() }
    fun clearCompleted() { tasks.removeAll { it.done }; save() }

    MaterialTheme {
        Scaffold(
            topBar = { TopAppBar(title = { Text(when (selectedTab) { 0 -> "یادآور"; 1 -> "تولدها 🎂"; else -> "تنظیمات" }, fontWeight = FontWeight.Bold) }) },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(selected = selectedTab == 0, onClick = { selectedTab = 0 }, icon = { Icon(Icons.Default.Checklist, "کارها") }, label = { Text("کارها") })
                    NavigationBarItem(selected = selectedTab == 1, onClick = { selectedTab = 1 }, icon = { Icon(Icons.Default.Cake, "تولدها") }, label = { Text("تولدها") })
                    NavigationBarItem(selected = selectedTab == 2, onClick = { selectedTab = 2 }, icon = { Icon(Icons.Default.Settings, "تنظیمات") }, label = { Text("تنظیمات") })
                }
            },
            floatingActionButton = { if (selectedTab == 0) FloatingActionButton(onClick = { showAddDialog = true }) { Icon(Icons.Default.Add, "افزودن") } }
        ) { padding ->
            when (selectedTab) {
                0 -> TodoScreen(tasks, ::toggleTask, ::deleteTask, ::clearCompleted, Modifier.padding(padding))
                1 -> BirthdayScreen(Modifier.padding(padding))
                2 -> SettingsScreen(tasks.size, tasks.count { it.done }, Modifier.padding(padding))
            }
        }
    }

    if (showAddDialog) AddTaskDialog({ showAddDialog = false }) { addTask(it); showAddDialog = false }
}

@Composable
fun TodoScreen(tasks: List<TodoItem>, onToggle: (Int) -> Unit, onDelete: (Int) -> Unit, onClearCompleted: () -> Unit, modifier: Modifier = Modifier) {
    val completed = tasks.count { it.done }
    Column(modifier.fillMaxSize().padding(16.dp)) {
        Text("کارهای امروز", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(if (tasks.isEmpty()) "هنوز کاری ثبت نشده است." else completed.toString() + " از " + tasks.size + " کار انجام شده", color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (completed > 0) TextButton(onClick = onClearCompleted) { Icon(Icons.Default.RemoveDone, null); Spacer(Modifier.width(4.dp)); Text("حذف کارهای انجام‌شده") }
        Spacer(Modifier.height(6.dp))
        if (tasks.isEmpty()) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.Default.CheckCircle, null)
                Spacer(Modifier.height(10.dp))
                Text("برای شروع، روی + بزنید.")
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(tasks, key = { it.id }) { task -> TodoCard(task, { onToggle(task.id) }, { onDelete(task.id) }) }
            }
        }
    }
}

@Composable
fun TodoCard(task: TodoItem, onToggle: () -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(3.dp)) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = task.done, onCheckedChange = { onToggle() })
            Text(task.title, Modifier.weight(1f).padding(horizontal = 6.dp), fontSize = 17.sp)
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "حذف") }
        }
    }
}

@Composable
fun AddTaskDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var title by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("کار جدید") },
        text = { OutlinedTextField(value = title, onValueChange = { title = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("عنوان کار") }) },
        confirmButton = { Button(onClick = { onAdd(title) }, enabled = title.trim().isNotEmpty()) { Text("افزودن") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}

@Composable
fun BirthdayScreen(modifier: Modifier = Modifier) {
    val reminder = BirthdayReminderEngine.reminder("نمونه تولد", 1, 1)
    Column(modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Default.Cake, null)
        Spacer(Modifier.height(16.dp))
        Text("تولد عزیزانت", fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp)) {
                Text("یادآوری نمونه", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(if (reminder.isToday) "امروز تولد " + reminder.name + " است 🎂" else reminder.name + ": " + reminder.daysUntil + " روز تا تولد")
                Spacer(Modifier.height(8.dp))
                Text("محاسبه زمان باقی‌مانده آماده است.")
            }
        }
    }
}

@Composable
fun SettingsScreen(totalTasks: Int, completedTasks: Int, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(20.dp)) {
        Text("تنظیمات", fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("آمار کارها", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("کل کارها: " + totalTasks)
                Text("انجام‌شده: " + completedTasks)
                Text("باقی‌مانده: " + (totalTasks - completedTasks))
            }
        }
        Spacer(Modifier.height(20.dp))
        Text("اعلان‌های تولد در مرحله بعد به زمان‌بندی واقعی متصل می‌شوند.")
        Spacer(Modifier.height(20.dp))
        Text("نسخه 1.0.0")
        Spacer(Modifier.height(8.dp))
        Text("یادآور | چک‌لیست و یادآوری تولد عزیزان")
    }
}

private fun loadTasks(context: Context): List<TodoItem> {
    val raw = context.getSharedPreferences("yadavar_data", Context.MODE_PRIVATE).getString("tasks", null) ?: return emptyList()
    return raw.split("\n").mapNotNull { line ->
        val p = line.split("\t", limit = 3)
        if (p.size != 3) return@mapNotNull null
        val id = p[0].toIntOrNull() ?: return@mapNotNull null
        TodoItem(id, p[2], p[1] == "1")
    }
}

private fun saveTasks(context: Context, tasks: List<TodoItem>) {
    val encoded = tasks.joinToString("\n") {
        it.id.toString() + "\t" + if (it.done) "1" else "0" + "\t" + it.title.replace("\n", " ").replace("\t", " ")
    }
    context.getSharedPreferences("yadavar_data", Context.MODE_PRIVATE).edit().putString("tasks", encoded).apply()
}
