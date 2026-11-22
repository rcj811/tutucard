package tw.knox.dailytasks.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import tw.knox.dailytasks.MainActivity
import tw.knox.dailytasks.R
import tw.knox.dailytasks.WidgetTaskDialogActivity
import tw.knox.dailytasks.data.AppDatabase
import tw.knox.dailytasks.data.TaskEntity
import tw.knox.dailytasks.data.lunarDate
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

abstract class BaseTaskWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                updateWidgets(context, manager, ids)
            } finally {
                pending.finish()
            }
        }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        onUpdate(context, manager, intArrayOf(id))
    }

    companion object {
        @Suppress("DEPRECATION")
        suspend fun updateWidgets(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val tasks = AppDatabase.get(context).taskDao().getAllEntities().filterNot { it.isCompleted }
                .sortedWith(compareBy<TaskEntity> { it.widgetDisplayTime() }.thenBy { it.id })
            val today = LocalDate.now()
            val firstDate = tasks.firstOrNull()?.widgetDisplayTime()?.widgetLocalDate() ?: today
            ids.forEach { widgetId ->
                val views = RemoteViews(context.packageName, R.layout.widget_tasks)
                views.setTextViewText(R.id.widget_heading, widgetDateHeading(firstDate))
                views.setTextViewText(R.id.widget_count, "${tasks.size} 項")
                views.setOnClickPendingIntent(R.id.widget_add, openAppIntent(context, true))
                val openApp = openAppIntent(context, false)
                views.setOnClickPendingIntent(R.id.widget_header_content, openApp)
                views.setOnClickPendingIntent(R.id.widget_heading, openApp)
                views.setOnClickPendingIntent(R.id.widget_count, openApp)

                val adapter = Intent(context, TaskWidgetService::class.java)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                    .setData(Uri.parse("tutucard://widget/collection/$widgetId"))
                views.setRemoteAdapter(R.id.widget_list, adapter)
                views.setScrollPosition(R.id.widget_list, 0)
                views.setEmptyView(R.id.widget_list, R.id.widget_empty)
                views.setPendingIntentTemplate(R.id.widget_list, taskClickTemplate(context, widgetId))
                manager.updateAppWidget(widgetId, views)
            }
            manager.notifyAppWidgetViewDataChanged(ids, R.id.widget_list)
            if (ids.isNotEmpty()) WidgetDateReceiver.scheduleNextMidnight(context)
        }

        private fun taskClickTemplate(context: Context, widgetId: Int): PendingIntent {
            val intent = Intent(context, WidgetTaskDialogActivity::class.java)
                .setData(Uri.parse("tutucard://widget/item/$widgetId"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            // Collection rows supply their task ID and action through fill-in extras.
            val mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            return PendingIntent.getActivity(context, widgetId, intent, PendingIntent.FLAG_UPDATE_CURRENT or mutable)
        }

        private fun openAppIntent(context: Context, add: Boolean): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_ADD, add)
            return PendingIntent.getActivity(context, if (add) 700_001 else 700_000, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
    }
}

internal fun TaskEntity.widgetDisplayTime(): Long = nextReminderAt ?: reminderAt ?: startDateTime

internal fun Long.widgetLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

internal fun widgetDateHeading(date: LocalDate): String =
    "${date.format(DateTimeFormatter.ofPattern("M月d日 (E)", Locale.TAIWAN))} ${lunarDate(date).fullLabel()}"

class SmallTaskWidget : BaseTaskWidget()
class MediumTaskWidget : BaseTaskWidget()
class LargeTaskWidget : BaseTaskWidget()

object TaskWidgetUpdater {
    private val providers = listOf(SmallTaskWidget::class.java, MediumTaskWidget::class.java, LargeTaskWidget::class.java)

    /** 清單捲回第一筆，避免標頭日期與畫面上的項目不一致。 */
    fun scrollToTop(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        providers.forEach { provider ->
            val ids = manager.getAppWidgetIds(ComponentName(context, provider))
            if (ids.isNotEmpty()) {
                val views = RemoteViews(context.packageName, R.layout.widget_tasks)
                views.setScrollPosition(R.id.widget_list, 0)
                manager.partiallyUpdateAppWidget(ids, views)
            }
        }
    }

    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        providers.forEach { provider ->
            val component = ComponentName(context, provider)
            val ids = manager.getAppWidgetIds(component)
            if (ids.isNotEmpty()) {
                context.sendBroadcast(Intent(context, provider).setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE).putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids))
            }
        }
    }
}

/** 跨日（午夜）、手動改時間或時區時重新整理小工具，讓標頭日期與農曆即時更新。 */
class WidgetDateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        TaskWidgetUpdater.updateAll(context)
    }

    companion object {
        private const val ACTION_MIDNIGHT = "tw.knox.dailytasks.WIDGET_MIDNIGHT"

        fun scheduleNextMidnight(context: Context) {
            val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
            val zone = ZoneId.systemDefault()
            val nextMidnight = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).plusSeconds(5).toInstant().toEpochMilli()
            val intent = Intent(context, WidgetDateReceiver::class.java).setAction(ACTION_MIDNIGHT)
            val pending = PendingIntent.getBroadcast(context, 700_100, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            // 不喚醒手機；午夜後螢幕一亮起就會更新，不需精準鬧鐘權限
            alarmManager.setWindow(AlarmManager.RTC, nextMidnight, 60_000L, pending)
        }
    }
}
