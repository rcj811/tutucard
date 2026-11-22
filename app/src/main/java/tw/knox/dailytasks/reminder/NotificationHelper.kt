package tw.knox.dailytasks.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import tw.knox.dailytasks.CompleteTaskConfirmActivity
import tw.knox.dailytasks.MainActivity
import tw.knox.dailytasks.R
import tw.knox.dailytasks.data.TaskEntity
import tw.knox.dailytasks.data.TaskPriority
import tw.knox.dailytasks.data.TaskRules
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object NotificationHelper {
    private const val CHANNEL_HIGH = "task_high"
    private const val CHANNEL_MEDIUM = "task_medium"
    private const val CHANNEL_LOW = "task_low"

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        listOf(
            NotificationChannel(CHANNEL_HIGH, "紅色提醒", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "重要、付款與時效性任務"
                enableVibration(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            },
            NotificationChannel(CHANNEL_MEDIUM, "藍色提醒", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "一般重要任務"
                enableVibration(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            },
            NotificationChannel(CHANNEL_LOW, "綠色提醒", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "例行待辦提醒"
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
        ).forEach(manager::createNotificationChannel)
    }

    fun show(context: Context, task: TaskEntity, hideSensitiveContent: Boolean) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val priority = runCatching { TaskPriority.valueOf(task.priority) }.getOrDefault(TaskPriority.MEDIUM)
        val channel = when (priority) {
            TaskPriority.HIGH -> CHANNEL_HIGH
            TaskPriority.MEDIUM -> CHANNEL_MEDIUM
            TaskPriority.LOW -> CHANNEL_LOW
        }
        val color = ContextCompat.getColor(context, when (priority) {
            TaskPriority.HIGH -> R.color.notification_high_background
            TaskPriority.MEDIUM -> R.color.notification_medium_background
            TaskPriority.LOW -> R.color.notification_low_background
        })
        val title = if (hideSensitiveContent) "你有一項待辦事項" else task.title
        val titleColor = ContextCompat.getColor(context, when (priority) {
            TaskPriority.HIGH -> R.color.priority_high_text
            TaskPriority.MEDIUM -> R.color.priority_medium_text
            TaskPriority.LOW -> R.color.priority_low_text
        })
        val styledTitle = SpannableString(title).apply {
            setSpan(ForegroundColorSpan(titleColor), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val note = if (hideSensitiveContent) "內容已隱藏" else task.note.ifBlank { "無" }
        val displayTime = task.nextReminderAt ?: task.reminderAt ?: task.startDateTime
        val time = Instant.ofEpochMilli(displayTime).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("M月d日 HH:mm"))
        val snoozeMinutes = task.repeatReminderInterval.coerceAtLeast(1)
        val snoozeText = snoozeLabel(snoozeMinutes)
        val actionVisibility = if (TaskRules.canEdit(task)) View.VISIBLE else View.GONE
        val content = RemoteViews(context.packageName, R.layout.notification_task).apply {
            setTextViewText(R.id.notification_title, styledTitle)
            setTextColor(R.id.notification_title, titleColor)
            setTextViewText(R.id.notification_time, "$time · 尚未完成")
            setTextViewText(R.id.notification_note, "備註：$note")
            setInt(R.id.notification_card, "setBackgroundColor", color)
            setOnClickPendingIntent(R.id.notification_done, completeIntent(context, task.id))
            setTextViewText(R.id.notification_snooze, snoozeText)
            setOnClickPendingIntent(R.id.notification_snooze, actionIntent(context, task.id, ReminderReceiver.ACTION_SNOOZE, 200_000, snoozeMinutes))
            setViewVisibility(R.id.notification_done, actionVisibility)
            setViewVisibility(R.id.notification_snooze, actionVisibility)
        }
        val bigContent = RemoteViews(context.packageName, R.layout.notification_task_big).apply {
            setTextViewText(R.id.notification_title, styledTitle)
            setTextColor(R.id.notification_title, titleColor)
            setTextViewText(R.id.notification_time, "$time · 尚未完成")
            setTextViewText(R.id.notification_note, "備註：$note")
            setInt(R.id.notification_card, "setBackgroundColor", color)
            setOnClickPendingIntent(R.id.notification_done, completeIntent(context, task.id))
            setOnClickPendingIntent(R.id.notification_snooze5, actionIntent(context, task.id, ReminderReceiver.ACTION_SNOOZE, 205_000, 5))
            setOnClickPendingIntent(R.id.notification_snooze10, actionIntent(context, task.id, ReminderReceiver.ACTION_SNOOZE, 210_000, 10))
            setOnClickPendingIntent(R.id.notification_snooze30, actionIntent(context, task.id, ReminderReceiver.ACTION_SNOOZE, 230_000, 30))
            setViewVisibility(R.id.notification_done, actionVisibility)
            setViewVisibility(R.id.notification_snooze5, actionVisibility)
            setViewVisibility(R.id.notification_snooze10, actionVisibility)
            setViewVisibility(R.id.notification_snooze30, actionVisibility)
        }
        val openIntent = PendingIntent.getActivity(
            context,
            task.id.toInt(),
            Intent(context, MainActivity::class.java).putExtra(EXTRA_OPEN_TASK_ID, task.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val deleteIntent = actionIntent(context, task.id, ReminderReceiver.ACTION_DISMISSED, 300_000)
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(styledTitle)
            .setContentText("$time · $note")
            .setColor(titleColor)
            .setCustomContentView(content)
            .setCustomBigContentView(bigContent)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setContentIntent(openIntent)
            .setDeleteIntent(deleteIntent)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(if (priority == TaskPriority.LOW) NotificationCompat.PRIORITY_DEFAULT else NotificationCompat.PRIORITY_MAX)
            .setSilent(task.ringEnabled)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(false)
            .build()
        NotificationManagerCompat.from(context).notify(task.id.toInt(), notification)
    }

    fun cancel(context: Context, taskId: Long) = NotificationManagerCompat.from(context).cancel(taskId.toInt())

    private fun snoozeLabel(minutes: Int): String = when {
        minutes % (24 * 60) == 0 -> "延後${minutes / (24 * 60)}天"
        minutes % 60 == 0 -> "延後${minutes / 60}小時"
        else -> "延後${minutes}分鐘"
    }

    private fun actionIntent(context: Context, taskId: Long, action: String, offset: Int, minutes: Int = 0): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(action)
            .putExtra(ReminderReceiver.EXTRA_TASK_ID, taskId)
            .putExtra(ReminderReceiver.EXTRA_MINUTES, minutes)
        return PendingIntent.getBroadcast(
            context,
            taskId.toInt() + offset,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun completeIntent(context: Context, taskId: Long): PendingIntent {
        val intent = Intent(context, CompleteTaskConfirmActivity::class.java)
            .setData(Uri.parse("tutucard://notification/complete/$taskId"))
            .putExtra(CompleteTaskConfirmActivity.EXTRA_TASK_ID, taskId)
        return PendingIntent.getActivity(
            context,
            taskId.toInt() + 100_000,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    const val EXTRA_OPEN_TASK_ID = "open_task_id"
}
