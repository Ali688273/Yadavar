package com.yadavar.app

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class TodayWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        super.onUpdate(context, manager, ids)
        ids.forEach { id -> manager.getAppWidgetInfo(id); val v=RemoteViews(context.packageName,R.layout.widget_yadavar); v.setTextViewText(R.id.widget_title,"امروز من"); manager.updateAppWidget(id,v) }
    }
}

class OverdueWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        super.onUpdate(context, manager, ids)
        ids.forEach { id -> val v=RemoteViews(context.packageName,R.layout.widget_yadavar); v.setTextViewText(R.id.widget_title,"کارهای عقب‌افتاده"); manager.updateAppWidget(id,v) }
    }
}

class QuickAddWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id ->
            val v=RemoteViews(context.packageName,R.layout.widget_yadavar)
            v.setTextViewText(R.id.widget_title,"افزودن سریع")
            val intent=Intent(context,MainActivity::class.java).putExtra("new_task",true)
            val pi=android.app.PendingIntent.getActivity(context,9200+id,intent,android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
            v.setOnClickPendingIntent(R.id.widget_root,pi)
            manager.updateAppWidget(id,v)
        }
    }
}