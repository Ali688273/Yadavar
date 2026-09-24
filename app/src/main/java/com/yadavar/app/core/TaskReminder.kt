package com.yadavar.app.core

import org.json.JSONArray
import org.json.JSONObject

data class TaskReminder(
    val hour: Int,
    val minute: Int
) {
    fun isValid(): Boolean = hour in 0..23 && minute in 0..59

    fun label(): String =
        hour.toString().padStart(2, '0') + ":" + minute.toString().padStart(2, '0')
}

object TaskReminderCodec {
    fun encode(items: List<TaskReminder>): String {
        val array = JSONArray()
        items.filter { it.isValid() }.distinctBy { it.hour * 60 + it.minute }.take(8).forEach {
            array.put(JSONObject().put("hour", it.hour).put("minute", it.minute))
        }
        return array.toString()
    }

    fun decode(raw: String?): List<TaskReminder> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val o = array.optJSONObject(i) ?: continue
                    val reminder = TaskReminder(o.optInt("hour", -1), o.optInt("minute", -1))
                    if (reminder.isValid()) add(reminder)
                }
            }.distinctBy { it.hour * 60 + it.minute }.take(8)
        }.getOrDefault(emptyList())
    }
}
