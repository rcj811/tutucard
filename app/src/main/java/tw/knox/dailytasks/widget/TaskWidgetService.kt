package tw.knox.dailytasks.widget

import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import tw.knox.dailytasks.R
import tw.knox.dailytasks.WidgetTaskDialogActivity
import tw.knox.dailytasks.data.AppDatabase
import tw.knox.dailytasks.data.TaskEntity
import tw.knox.dailytasks.data.TaskPriority
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class TaskWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = TaskWidgetFactory(applicationContext)
}

private class TaskWidgetFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {
    @Volatile private var tasks: List<TaskEntity> = emptyList()

    override fun onCreate() = Unit

    override fun onDataSetChanged() {
        tasks = runBlocking(Dispatchers.IO) {
            AppDatabase.get(context).taskDao().getAllEntities().filterNot { it.isCompleted }
                .sortedWith(compareBy<TaskEntity> { it.widgetDisplayTime() }.thenBy { it.id })
        }
    }

    override fun onDestroy() { tasks = emptyList() }
    override fun getCount(): Int = tasks.size
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = tasks.getOrNull(position)?.id ?: -1L
    override fun hasStableIds(): Boolean = true
    override fun getLoadingView(): RemoteViews? = null

    override fun getViewAt(position: Int): RemoteViews? {
        val snapshot = tasks
        val task = snapshot.getOrNull(position) ?: return null
        val dateTime = Instant.ofEpochMilli(task.widgetDisplayTime()).atZone(ZoneId.systemDefault())
        val date = dateTime.toLocalDate()
        val previousDate = snapshot.getOrNull(position - 1)?.widgetDisplayTime()?.widgetLocalDate()
        val priority = runCatching { TaskPriority.valueOf(task.priority) }.getOrDefault(TaskPriority.MEDIUM)
        val accent = ContextCompat.getColor(context, when (priority) {
            TaskPriority.HIGH -> R.color.priority_high_text
            TaskPriority.MEDIUM -> R.color.priority_medium_text
            TaskPriority.LOW -> R.color.priority_low_text
        })
        val preview = Intent().putExtra(WidgetTaskDialogActivity.EXTRA_TASK_ID, task.id)
            .putExtra(WidgetTaskDialogActivity.EXTRA_COMPLETE, false)
        val complete = Intent().putExtra(WidgetTaskDialogActivity.EXTRA_TASK_ID, task.id)
            .putExtra(WidgetTaskDialogActivity.EXTRA_COMPLETE, true)
        return RemoteViews(context.packageName, R.layout.widget_task_list_item).apply {
            setTextViewText(R.id.widget_item_title, task.title)
            setTextColor(R.id.widget_item_title, accent)
            setTextColor(R.id.widget_item_check, accent)
            setTextViewText(R.id.widget_item_time, dateTime.format(DateTimeFormatter.ofPattern("HH:mm")))
            setTextViewText(R.id.widget_item_date, widgetDateHeading(date))
            setViewVisibility(R.id.widget_item_date, if (position > 0 && date != previousDate) View.VISIBLE else View.GONE)
            setInt(R.id.widget_item_row, "setBackgroundResource", when (priority) {
                TaskPriority.HIGH -> R.drawable.widget_priority_high
                TaskPriority.MEDIUM -> R.drawable.widget_priority_medium
                TaskPriority.LOW -> R.drawable.widget_priority_low
            })
            setOnClickFillInIntent(R.id.widget_item_check, complete)
            setOnClickFillInIntent(R.id.widget_item_content, preview)
            setOnClickFillInIntent(R.id.widget_item_title, preview)
            setOnClickFillInIntent(R.id.widget_item_time, preview)
        }
    }
}
