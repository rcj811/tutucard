package tw.knox.dailytasks.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import tw.knox.dailytasks.data.TaskEntity
import tw.knox.dailytasks.data.TaskRules

class AlarmScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun schedule(task: TaskEntity) {
        if (task.isCompleted || !task.reminderEnabled) return
        val now = System.currentTimeMillis()
        val triggers = buildSet {
            TaskRules.reminderTimes(task).filterTo(this) { it >= now - 1_000L }
            task.nextReminderAt?.takeIf { it >= now - 1_000L }?.let(::add)
        }
        triggers.forEach { schedule(task.id, it) }
    }

    private fun schedule(taskId: Long, triggerAt: Long) {
        val intent = pendingIntent(taskId, triggerAt)
        val safeTrigger = triggerAt.coerceAtLeast(System.currentTimeMillis() + 1_000L)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, safeTrigger, intent)
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, safeTrigger, intent)
        }
    }

    fun cancel(taskId: Long) {
        alarmManager.cancel(legacyPendingIntent(taskId))
        NotificationHelper.cancel(context, taskId)
    }

    fun cancel(task: TaskEntity) {
        buildSet {
            addAll(TaskRules.reminderTimes(task))
            task.nextReminderAt?.let(::add)
        }.forEach { alarmManager.cancel(pendingIntent(task.id, it)) }
        cancel(task.id)
    }

    private fun pendingIntent(taskId: Long, triggerAt: Long): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(ReminderReceiver.ACTION_TRIGGER)
            .setData(Uri.parse("tutucard://reminder/$taskId/$triggerAt"))
            .putExtra(ReminderReceiver.EXTRA_TASK_ID, taskId)
            .putExtra(ReminderReceiver.EXTRA_TRIGGER_AT, triggerAt)
        return PendingIntent.getBroadcast(
            context,
            31 * taskId.hashCode() + triggerAt.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun legacyPendingIntent(taskId: Long): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(ReminderReceiver.ACTION_TRIGGER)
            .putExtra(ReminderReceiver.EXTRA_TASK_ID, taskId)
        return PendingIntent.getBroadcast(
            context,
            taskId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
