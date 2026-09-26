package com.yadavar.app

import android.content.Context
import java.security.MessageDigest

object UltimateHistory {
    private const val PREF="yadavar_history_state"
    fun sync(context:Context,tasks:List<TodoItem>){
        val p=context.getSharedPreferences(PREF,0)
        val old=p.getStringSet("ids",emptySet())?.toSet()?:emptySet()
        val now=tasks.associate{it.id.toString() to fingerprint(it)}
        val oldMap=p.all.filterKeys{it.startsWith("task_")}.mapKeys{it.key.removePrefix("task_")}.mapValues{it.value.toString()}
        tasks.forEach{t->
            val key=t.id.toString();val fp=now[key]
            if(oldMap[key]!=fp) UltimateStore.history(context,t,if(key in old) "تغییر خودکار ثبت شد" else "کار ایجاد شد")
        }
        old.filterNot{it in now.keys}.forEach{id->UltimateStore.history(context,TodoItem(id.toIntOrNull()?:-1,"کار حذف‌شده"),"کار حذف شد")}
        val e=p.edit().clear().putStringSet("ids",now.keys)
        now.forEach{(id,fp)->e.putString("task_"+id,fp)}
        e.apply()
    }
    private fun fingerprint(t:TodoItem):String{
        val s=listOf(t.title,t.done.toString(),t.repeat,t.category,t.priority,t.startDate,t.dueDate,t.note,t.tags,t.subtasks,t.location,t.customEvery.toString(),t.customUnit,t.reminders.joinToString{it.label()}).joinToString("|")
        return MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString(""){"%02x".format(it)}
    }
}
