package com.yadavar.app

import android.content.Context
import java.security.MessageDigest

object UltimateHistory {
    private const val PREF="yadavar_history_state"

    fun sync(context:Context,tasks:List<TodoItem>){
        val p=context.getSharedPreferences(PREF,0)
        val old=p.getStringSet("ids",emptySet()).orEmpty()
        val oldMap=p.all.filterKeys{it.startsWith("task_")}.mapKeys{it.key.removePrefix("task_")}.mapValues{it.value.toString()}
        val now=tasks.associate{it.id.toString() to snapshot(it)}
        tasks.forEach{t->
            val id=t.id.toString()
            val fp=now[id]
            val before=oldMap[id]
            if(before==null) UltimateStore.history(context,t,"کار ایجاد شد",null,fp)
            else if(before!=fp) UltimateStore.history(context,t,"کار تغییر کرد",before,fp)
            if(t.done && UltimateStore.taskCompletionDate(context,t.id).isBlank()) UltimateStore.markCompleted(context,t.id)
            if(t.done) UltimateStore.award(context,t)
        }
        old.filterNot{it in now.keys}.forEach{id->
            val fake=TodoItem(id.toIntOrNull()?:-1,"کار حذف‌شده")
            UltimateStore.history(context,fake,"کار حذف شد",oldMap[id],null)
        }
        val e=p.edit().clear().putStringSet("ids",now.keys)
        now.forEach{(id,fp)->e.putString("task_"+id,fp)}
        e.apply()
    }

    private fun snapshot(t:TodoItem):String{
        return org.json.JSONObject()
            .put("id",t.id).put("title",t.title).put("done",t.done)
            .put("reminderHour",t.reminderHour?:org.json.JSONObject.NULL).put("reminderMinute",t.reminderMinute?:org.json.JSONObject.NULL)
            .put("repeat",t.repeat).put("category",t.category).put("priority",t.priority)
            .put("startDate",t.startDate).put("dueDate",t.dueDate).put("note",t.note)
            .put("tags",t.tags).put("subtasks",t.subtasks).put("location",t.location)
            .put("customEvery",t.customEvery).put("customUnit",t.customUnit)
            .put("reminders",TaskReminderCodec.encode(t.reminders)).toString()
    }
}
