package com.yadavar.app

import android.content.Context
import java.security.MessageDigest

object UltimateHistory {
    private const val PREF="yadavar_history_state"

    fun sync(context:Context,tasks:List<TodoItem>){
        val p=context.getSharedPreferences(PREF,0)
        val old=p.getStringSet("ids",emptySet()).orEmpty()
        val oldMap=p.all.filterKeys{it.startsWith("task_")}.mapKeys{it.key.removePrefix("task_")}.mapValues{it.value.toString()}
        val now=tasks.associate{it.id.toString() to fingerprint(it)}
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

    private fun fingerprint(t:TodoItem):String{
        val raw=listOf(
            t.title,t.done.toString(),t.reminderHour?.toString().orEmpty(),
            t.reminderMinute?.toString().orEmpty(),t.repeat,t.category,t.priority,
            t.startDate,t.dueDate,t.note,t.tags,t.subtasks,t.location,
            t.customEvery.toString(),t.customUnit,t.reminders.joinToString{it.label()}
        ).joinToString("|")
        return MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray(Charsets.UTF_8))
            .joinToString(""){"%02x".format(it)}
    }
}
