package com.yadavar.app.core

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import com.yadavar.app.TodoItem

object BackupManager {

    private const val BACKUP_VERSION = 3

    data class BackupData(
        val tasks: List<TodoItem>,
        val birthdays: List<StoredBirthday>,
        val habits: Map<String, Int>,
        val shopping: List<String>,
        val settings: Pair<Boolean, Boolean> = true to false,\n        val extra: JSONObject = JSONObject()
    )

    fun createBackup(context: Context): String {
        val prefs = context.getSharedPreferences("yadavar_data", Context.MODE_PRIVATE)
        val root = JSONObject()
            .put("format", "yadavar-backup")
            .put("version", BACKUP_VERSION)
            .put("createdAt", System.currentTimeMillis())
            .put("settings", JSONObject().put("smartAutoCarry", prefs.getBoolean("smart_auto_carry", true)).put("compactMode", prefs.getBoolean("compact_mode", false)))

        val tasks = JSONArray()
        loadTasksFromPrefs(prefs).forEach { task ->
            tasks.put(JSONObject()
                .put("id", task.id)
                .put("title", task.title)
                .put("done", task.done)
                .putNullable("reminderHour", task.reminderHour)
                .putNullable("reminderMinute", task.reminderMinute)
                .put("repeat", task.repeat)
                .put("category", task.category)
                .put("priority", task.priority)
                .put("startDate", task.startDate)
                .put("dueDate", task.dueDate)
                .put("note", task.note)
                .put("tags", task.tags)
                .put("subtasks", task.subtasks)
                .put("location", task.location)
                .put("customEvery", task.customEvery)
                .put("customUnit", task.customUnit)
                .put("reminders", JSONArray(TaskReminderCodec.encode(if (task.reminders.isNotEmpty()) task.reminders else if (task.hasReminder) listOf(TaskReminder(task.reminderHour!!, task.reminderMinute!!)) else emptyList()))))
        }

        val birthdays = JSONArray()
        loadBirthdaysFromPrefs(prefs).forEach { birthday ->
            birthdays.put(JSONObject()
                .put("id", birthday.id)
                .put("name", birthday.name)
                .put("month", birthday.month)
                .put("day", birthday.day))
        }

        val habits = JSONObject()
        loadHabits(prefs).forEach { (name, streak) ->
            habits.put(name, streak)
        }

        val shopping = JSONArray()
        loadShopping(prefs).forEach { shopping.put(it) }

        root.put("tasks", tasks)
        root.put("birthdays", birthdays)
        root.put("habits", habits)
        root.put("shopping", shopping)\n        val extra = JSONObject()\n        val ep = context.getSharedPreferences("yadavar_extra", Context.MODE_PRIVATE)\n        extra.put("inbox", JSONArray(ep.getString("inbox", "").orEmpty().split("\\n").filter { it.isNotBlank() }))\n        extra.put("archivedIds", JSONArray(ep.getStringSet("archived_ids", emptySet()) ?: emptySet<String>()))\n        extra.put("templates", ep.getString("templates", "").orEmpty())\n        extra.put("weeklyGoal", ep.getInt("weekly_goal", 10))\n        extra.put("appPin", ep.getString("app_pin", "").orEmpty())\n        root.put("extra", extra)
        return root.toString(2)
    }

    fun restoreFromUri(context: Context, uri: Uri): BackupData {
        val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: error("فایل پشتیبان قابل خواندن نیست.")

        return parseBackup(json)
    }

    fun applyBackup(context: Context, data: BackupData) {
        val prefs = context.getSharedPreferences("yadavar_data", Context.MODE_PRIVATE)
        val editor = prefs.edit()

        editor.putString("tasks", data.tasks.joinToString("\n") {
            it.id.toString() + "\t" +
                (if (it.done) "1" else "0") + "\t" +
                clean(it.title) + "\t" +
                (it.reminderHour?.toString() ?: "") + "\t" +
                (it.reminderMinute?.toString() ?: "") + "\t" +
                it.repeat + "\t" + clean(it.category) + "\t" + it.priority + "\t" +
                it.startDate + "\t" + it.dueDate + "\t" + clean(it.note) + "\t" +
                clean(it.tags) + "\t" + clean(it.subtasks) + "\t" + clean(it.location) + "\t" +
                it.customEvery.coerceAtLeast(1) + "\t" + clean(it.customUnit) + "\t" +
                TaskReminderCodec.encode(if (it.reminders.isNotEmpty()) it.reminders else if (it.hasReminder) listOf(TaskReminder(it.reminderHour!!, it.reminderMinute!!)) else emptyList())
        })
        editor.putString("tasks_date", java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date()))

        editor.putString("birthdays", data.birthdays.joinToString("\n") {
            it.id.toString() + "\t" + clean(it.name) + "\t" + it.month + "\t" + it.day
        })

        editor.putString(
            "habits",
            data.habits.entries.joinToString("\n") { clean(it.key) + "\t" + it.value.coerceAtLeast(0) }
        )

        editor.putString("shopping", data.shopping.map(::clean).filter { it.isNotBlank() }.joinToString("\n"))
        editor.putBoolean("smart_auto_carry", data.settings.first)
        editor.putBoolean("compact_mode", data.settings.second)
        editor.apply()
    }

    private fun parseBackup(json: String): BackupData {
        val root = JSONObject(json)
        if (root.optString("format") != "yadavar-backup") {
            error("این فایل، پشتیبان معتبر یادآور نیست.")
        }
        val version = root.optInt("version", 0)
        if (version !in 1..BACKUP_VERSION) {
            error("نسخه پشتیبان با این نسخه از برنامه سازگار نیست.")
        }

        val tasks = mutableListOf<TodoItem>()
        val tasksJson = root.optJSONArray("tasks") ?: JSONArray()
        for (i in 0 until tasksJson.length()) {
            val o = tasksJson.optJSONObject(i) ?: continue
            val id = o.optInt("id", -1)
            val title = o.optString("title").trim()
            if (id < 0 || title.isBlank()) continue

            val hour = if (o.has("reminderHour") && !o.isNull("reminderHour")) o.optInt("reminderHour").takeIf { it in 0..23 } else null
            val minute = if (o.has("reminderMinute") && !o.isNull("reminderMinute")) o.optInt("reminderMinute").takeIf { it in 0..59 } else null

            tasks += TodoItem(
                id = id,
                title = title,
                done = o.optBoolean("done", false),
                reminderHour = hour,
                reminderMinute = minute,
                repeat = o.optString("repeat", "none"),
                category = o.optString("category", "عمومی"),
                priority = o.optString("priority", "normal"),
                startDate = o.optString("startDate"),
                dueDate = o.optString("dueDate"),
                note = o.optString("note"),
                tags = o.optString("tags"),
                subtasks = o.optString("subtasks"),
                location = o.optString("location"),
                customEvery = o.optInt("customEvery", 1).coerceAtLeast(1),
                customUnit = o.optString("customUnit", "day").ifBlank { "day" },
                reminders = decodeBackupReminders(o.optJSONArray("reminders")).ifEmpty {
                    if (hour != null && minute != null) listOf(TaskReminder(hour, minute)) else emptyList()
                }
            )
        }

        val birthdays = mutableListOf<StoredBirthday>()
        val birthdaysJson = root.optJSONArray("birthdays") ?: JSONArray()
        for (i in 0 until birthdaysJson.length()) {
            val o = birthdaysJson.optJSONObject(i) ?: continue
            val id = o.optInt("id", -1)
            val name = o.optString("name").trim()
            val month = o.optInt("month", 0)
            val day = o.optInt("day", 0)
            if (id >= 0 && name.isNotBlank() && month in 1..12 && day in 1..31) {
                birthdays += StoredBirthday(id, name, month, day)
            }
        }

        val habits = linkedMapOf<String, Int>()
        val habitsJson = root.optJSONObject("habits")
        if (habitsJson != null) {
            val keys = habitsJson.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                habits[key] = habitsJson.optInt(key, 0).coerceAtLeast(0)
            }
        }

        val shopping = mutableListOf<String>()
        val shoppingJson = root.optJSONArray("shopping") ?: JSONArray()
        for (i in 0 until shoppingJson.length()) {
            val value = shoppingJson.optString(i).trim()
            if (value.isNotBlank()) shopping += value
        }

        val settingsJson = root.optJSONObject("settings")
        val settings = (settingsJson?.optBoolean("smartAutoCarry", true) ?: true) to (settingsJson?.optBoolean("compactMode", false) ?: false)\n        val extra = root.optJSONObject("extra") ?: JSONObject()
        return BackupData(
            tasks = tasks.distinctBy { it.id },
            birthdays = birthdays.distinctBy { it.id },
            habits = habits,
            shopping = shopping.distinct(),
            settings = settings
        )
    }

    private fun decodeBackupReminders(array: JSONArray?): List<TaskReminder> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                val h = o.optInt("hour", -1)
                val m = o.optInt("minute", -1)
                val r = TaskReminder(h, m)
                if (r.isValid()) add(r)
            }
        }.distinctBy { it.hour * 60 + it.minute }.take(8)
    }

    private fun loadTasksFromPrefs(prefs: android.content.SharedPreferences): List<TodoItem> {
        val raw = prefs.getString("tasks", null) ?: return emptyList()
        return raw.split("\n").mapNotNull { row ->
            val x = row.split("\t", limit = 17)
            if (x.size < 3) return@mapNotNull null
            val id = x[0].toIntOrNull() ?: return@mapNotNull null
            val hour = x.getOrNull(3)?.toIntOrNull()?.takeIf { it in 0..23 }
            val minute = x.getOrNull(4)?.toIntOrNull()?.takeIf { it in 0..59 }
            TodoItem(
                id = id,
                title = x[2],
                done = x.getOrNull(1) == "1",
                reminderHour = hour,
                reminderMinute = minute,
                repeat = x.getOrNull(5) ?: "none",
                category = x.getOrNull(6).orEmpty().ifBlank { "عمومی" },
                priority = x.getOrNull(7).orEmpty().ifBlank { "normal" },
                startDate = x.getOrNull(8).orEmpty(),
                dueDate = x.getOrNull(9).orEmpty(),
                note = x.getOrNull(10).orEmpty(),
                tags = x.getOrNull(11).orEmpty(),
                subtasks = x.getOrNull(12).orEmpty(),
                location = x.getOrNull(13).orEmpty(),
                customEvery = x.getOrNull(14)?.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                customUnit = x.getOrNull(15).orEmpty().ifBlank { "day" },
                reminders = TaskReminderCodec.decode(x.getOrNull(16)).ifEmpty {
                    if (hour != null && minute != null) listOf(TaskReminder(hour, minute)) else emptyList()
                }
            )
        }
    }

    private fun loadBirthdaysFromPrefs(prefs: android.content.SharedPreferences): List<StoredBirthday> {
        val raw = prefs.getString("birthdays", null) ?: return emptyList()
        return raw.split("\n").mapNotNull { row ->
            val x = row.split("\t", limit = 4)
            if (x.size != 4) return@mapNotNull null
            val id = x[0].toIntOrNull() ?: return@mapNotNull null
            val month = x[2].toIntOrNull() ?: return@mapNotNull null
            val day = x[3].toIntOrNull() ?: return@mapNotNull null
            if (month !in 1..12 || day !in 1..31) null else StoredBirthday(id, x[1], month, day)
        }
    }

    private fun loadHabits(prefs: android.content.SharedPreferences): Map<String, Int> =
        prefs.getString("habits", "").orEmpty().split("\n").mapNotNull {
            val x = it.split("\t", limit = 2)
            if (x.size == 2) x[0] to (x[1].toIntOrNull() ?: 0) else null
        }.toMap()

    private fun loadShopping(prefs: android.content.SharedPreferences): List<String> =
        prefs.getString("shopping", "").orEmpty().split("\n").filter { it.isNotBlank() }

    private fun clean(value: String): String =
        value.replace("\n", " ").replace("\r", " ").replace("\t", " ").trim()

    private fun JSONObject.putNullable(key: String, value: Int?): JSONObject {
        if (value == null) put(key, JSONObject.NULL) else put(key, value)
        return this
    }
}
