package com.yadavar.app

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class YadavarWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.widget_yadavar)
            val tasks = context.getSharedPreferences("yadavar_data", 0)
                .getString("tasks", null).orEmpty()
            val total = if (tasks.isBlank()) 0 else tasks.lines().count { it.isNotBlank() }
            val done = if (tasks.isBlank()) 0 else tasks.lines().count { it.isNotBlank() && it.split("\t").getOrNull(1) == "1" }
            views.setTextViewText(R.id.widget_title, "یادآور")
            views.setTextViewText(R.id.widget_count, "‎$done از $total کار انجام شده")
            views.setOnClickPendingIntent(
                R.id.widget_root,
                android.app.PendingIntent.getActivity(
                    context, 9100,
                    Intent(context, MainActivity::class.java),
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                )
            )
            manager.updateAppWidget(id, views)
        }
    }
}