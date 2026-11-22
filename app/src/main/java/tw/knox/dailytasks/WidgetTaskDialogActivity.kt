package tw.knox.dailytasks

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tw.knox.dailytasks.data.AppDatabase
import tw.knox.dailytasks.data.TaskEntity
import tw.knox.dailytasks.data.TaskPriority
import tw.knox.dailytasks.data.TaskRepository
import tw.knox.dailytasks.data.TaskRules
import tw.knox.dailytasks.widget.TaskWidgetUpdater
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class WidgetTaskDialogActivity : ChineseActivity() {
    private var taskDialog: AlertDialog? = null
    private var deleteDialog: AlertDialog? = null
    private var taskId = -1L
    private var editing = false
    private var deleting = false
    private val editor = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        editing = false
        loadTask()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        taskId = intent.getLongExtra(EXTRA_TASK_ID, -1)
        editing = savedInstanceState?.getBoolean("editing", false) ?: false
        if (taskId < 0) {
            finish()
            return
        }
        if (intent.getBooleanExtra(EXTRA_COMPLETE, false)) {
            startActivity(
                Intent(this, CompleteTaskConfirmActivity::class.java)
                    .putExtra(CompleteTaskConfirmActivity.EXTRA_TASK_ID, taskId)
            )
            finish()
            return
        }
        if (!editing) loadTask()
    }

    private fun loadTask() {
        lifecycleScope.launch {
            val task = withContext(Dispatchers.IO) { AppDatabase.get(this@WidgetTaskDialogActivity).taskDao().getEntity(taskId) }
            if (task == null) finish() else showTask(task)
        }
    }

    private fun showTask(task: TaskEntity) {
        val reminderTime = (task.nextReminderAt ?: task.reminderAt)?.let {
            Instant.ofEpochMilli(it)
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("M月d日 (E) HH:mm", Locale.TAIWAN))
        } ?: "未設定"
        val accent = priorityTextColor(task)
        val message = SpannableStringBuilder().apply {
            appendLine("提醒時間")
            appendLine(reminderTime)
            appendLine()
            appendLine("提醒內容")
            val titleStart = length
            append(task.title)
            setSpan(ForegroundColorSpan(accent), titleStart, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(StyleSpan(Typeface.BOLD), titleStart, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(RelativeSizeSpan(1.22f), titleStart, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            appendLine()
            appendLine()
            appendLine("備註")
            append(task.note.ifBlank { "無" })
        }
        val builder = AlertDialog.Builder(this)
        if (task.isCompleted && !TaskRules.isPastDate(task.startDateTime)) {
            val content = FrameLayout(this).apply {
                setPadding(24.dp, 24.dp, 24.dp, 20.dp)
                addView(
                    TextView(this@WidgetTaskDialogActivity).apply {
                        text = task.title
                        setTextAppearance(android.R.style.TextAppearance_Material_Headline)
                        setTextColor(accent)
                        textSize = 22f
                        setTypeface(typeface, Typeface.BOLD)
                        gravity = Gravity.CENTER
                    },
                    FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
                )
                addView(
                    View(this@WidgetTaskDialogActivity).apply { setBackgroundColor(0xFF61708A.toInt()) },
                    FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, 2.dp, Gravity.CENTER_VERTICAL)
                )
            }
            builder.setView(content)
        } else {
            builder.setMessage(message)
            if (TaskRules.canEdit(task)) builder.setNeutralButton("修改") { _, _ ->
                editing = true
                editor.launch(
                    Intent(this, WidgetTaskEditorActivity::class.java)
                        .putExtra(WidgetTaskEditorActivity.EXTRA_TASK_ID, task.id)
                )
            }
        }
        taskDialog = builder
            .setNegativeButton("刪除", null)
            .setPositiveButton("關閉") { _, _ -> finish() }
            .create()
            .also { dialog ->
                dialog.setOnDismissListener { if (!editing && !isFinishing) finish() }
                dialog.show()
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
                    if (!deleting && deleteDialog?.isShowing != true) showDeleteConfirmation(task)
                }
                dialog.findViewById<TextView>(android.R.id.message)?.apply {
                    textSize = 16f
                    setLineSpacing(0f, 1.12f)
                }
            }
    }

    private fun showDeleteConfirmation(task: TaskEntity) {
        deleteDialog = AlertDialog.Builder(this)
            .setTitle("是否刪除此項目？")
            .setMessage("${task.title}\n刪除後無法復原。")
            .setNegativeButton("取消", null)
            .setPositiveButton("刪除") { _, _ ->
                if (!deleting) {
                    deleting = true
                    taskDialog?.getButton(AlertDialog.BUTTON_NEGATIVE)?.isEnabled = false
                    lifecycleScope.launch {
                        try {
                            withContext(Dispatchers.IO) { TaskRepository(applicationContext).delete(task.id) }
                            finish()
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            deleting = false
                            taskDialog?.getButton(AlertDialog.BUTTON_NEGATIVE)?.isEnabled = true
                            deleteDialog = AlertDialog.Builder(this@WidgetTaskDialogActivity)
                                .setTitle("無法刪除")
                                .setMessage("請稍後再試，或返回應用程式確認項目狀態。")
                                .setPositiveButton("關閉", null)
                                .show()
                        }
                    }
                }
            }
            .show()
    }

    private fun priorityTextColor(task: TaskEntity): Int = ContextCompat.getColor(
        this,
        when (runCatching { TaskPriority.valueOf(task.priority) }.getOrDefault(TaskPriority.MEDIUM)) {
            TaskPriority.HIGH -> R.color.priority_high_text
            TaskPriority.MEDIUM -> R.color.priority_medium_text
            TaskPriority.LOW -> R.color.priority_low_text
        }
    )

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("editing", editing)
        super.onSaveInstanceState(outState)
    }

    override fun onStop() {
        super.onStop()
        TaskWidgetUpdater.scrollToTop(this)
    }

    override fun onDestroy() {
        deleteDialog?.dismiss()
        taskDialog?.setOnDismissListener(null)
        taskDialog?.dismiss()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_TASK_ID = "widget_task_id"
        const val EXTRA_COMPLETE = "widget_complete"
    }
}
