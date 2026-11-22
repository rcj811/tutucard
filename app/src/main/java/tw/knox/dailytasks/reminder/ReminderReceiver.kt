package tw.knox.dailytasks.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import tw.knox.dailytasks.CompleteTaskConfirmActivity
import tw.knox.dailytasks.data.AppDatabase
import tw.knox.dailytasks.data.ReminderHistoryEntity
import tw.knox.dailytasks.data.TaskRepository
import tw.knox.dailytasks.data.TaskRules
import tw.knox.dailytasks.widget.TaskWidgetUpdater

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                if (taskId < 0) return@launch
                val db = AppDatabase.get(context)
                when (intent.action) {
                    ACTION_COMPLETE -> {
                        context.startActivity(
                            Intent(context, CompleteTaskConfirmActivity::class.java)
                                .putExtra(CompleteTaskConfirmActivity.EXTRA_TASK_ID, taskId)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                    ACTION_SNOOZE -> {
                        val minutes = intent.getIntExtra(EXTRA_MINUTES, 10)
                        TaskRepository(context).snooze(taskId, minutes)
                    }
                    ACTION_DISMISSED -> {
                        TaskRinger.stop(taskId)
                        db.reminderHistoryDao().insert(
                            ReminderHistoryEntity(taskId = taskId, triggeredAt = System.currentTimeMillis(), action = "DISMISSED")
                        )
                    }
                    ACTION_TRIGGER -> {
                        val task = db.taskDao().getEntity(taskId) ?: return@launch
                        if (task.isCompleted || !task.reminderEnabled) return@launch
                        val expectedAt = intent.getLongExtra(EXTRA_TRIGGER_AT, -1L)
                        val standardTimes = TaskRules.reminderTimes(task)
                        val validTrigger = expectedAt < 0L || expectedAt in standardTimes ||
                            (task.repeatUntilComplete && task.nextReminderAt == expectedAt)
                        if (!validTrigger) return@launch
                        db.reminderHistoryDao().insert(
                            ReminderHistoryEntity(taskId = taskId, triggeredAt = System.currentTimeMillis(), action = "TRIGGERED")
                        )
                        val hideContent = context.getSharedPreferences("settings", Context.MODE_PRIVATE).getBoolean("hide_lock_content", false)
                        val now = System.currentTimeMillis()
                        val shownAt = expectedAt.takeIf { it >= 0L } ?: task.nextReminderAt ?: now
                        NotificationHelper.show(context, task.copy(nextReminderAt = shownAt), hideContent)
                        val nextStandard = standardTimes.firstOrNull { it > maxOf(shownAt + 1_000L, now + 1_000L) }
                        val nextRepeat = if (task.repeatUntilComplete) {
                            now + task.repeatReminderInterval.coerceAtLeast(1) * 60_000L
                        } else null
                        val next = listOfNotNull(nextStandard, nextRepeat).minOrNull()
                        db.taskDao().setNextReminder(taskId, next, now)
                        if (next != null) AlarmScheduler(context).schedule(task.copy(nextReminderAt = next))
                        TaskRinger.play(context, task)
                    }
                }
                TaskWidgetUpdater.updateAll(context)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_TRIGGER = "tw.knox.dailytasks.TRIGGER"
        const val ACTION_COMPLETE = "tw.knox.dailytasks.COMPLETE"
        const val ACTION_SNOOZE = "tw.knox.dailytasks.SNOOZE"
        const val ACTION_DISMISSED = "tw.knox.dailytasks.DISMISSED"
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_MINUTES = "minutes"
        const val EXTRA_TRIGGER_AT = "trigger_at"
    }
}
