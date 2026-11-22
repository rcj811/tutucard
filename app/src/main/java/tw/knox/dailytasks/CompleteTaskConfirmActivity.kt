package tw.knox.dailytasks

import android.app.AlertDialog
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.widget.TextView
import android.view.WindowManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tw.knox.dailytasks.data.AppDatabase
import tw.knox.dailytasks.data.TaskEntity
import tw.knox.dailytasks.data.TaskPriority
import tw.knox.dailytasks.data.TaskRepository
import tw.knox.dailytasks.data.TaskRules
import tw.knox.dailytasks.ui.formatDateTime

class CompleteTaskConfirmActivity : ChineseActivity() {
    private var confirmDialog: AlertDialog? = null
    private var confirming = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
        if (taskId < 0L) {
            finish()
            return
        }
        lifecycleScope.launch {
            val task = withContext(Dispatchers.IO) {
                AppDatabase.get(this@CompleteTaskConfirmActivity).taskDao().getEntity(taskId)
            }
            if (task == null || !TaskRules.canComplete(task)) finish() else showConfirmation(task)
        }
    }

    private fun showConfirmation(task: TaskEntity) {
        val accent = priorityTextColor(task)
        val styledTitle = SpannableString(task.title).apply {
            setSpan(ForegroundColorSpan(accent), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(StyleSpan(Typeface.BOLD), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(RelativeSizeSpan(1.15f), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val message = SpannableStringBuilder(styledTitle)
            .append("\n日期時間：${task.startDateTime.formatDateTime()}")
        confirmDialog = AlertDialog.Builder(this)
            .setTitle("是否完成項目？")
            .setMessage(message)
            .setNegativeButton("取消") { _, _ -> finish() }
            .setPositiveButton("確認完成") { _, _ ->
                confirming = true
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        TaskRepository(applicationContext).complete(task.id)
                    }
                    finish()
                }
            }
            .create()
            .also { dialog ->
                dialog.setOnCancelListener { finish() }
                dialog.setOnDismissListener { if (!confirming && !isFinishing) finish() }
                dialog.show()
                dialog.findViewById<TextView>(android.R.id.message)?.textSize = 18f
            }
    }

    private fun priorityTextColor(task: TaskEntity): Int = ContextCompat.getColor(
        this,
        when (runCatching { TaskPriority.valueOf(task.priority) }.getOrDefault(TaskPriority.MEDIUM)) {
            TaskPriority.HIGH -> R.color.priority_high_text
            TaskPriority.MEDIUM -> R.color.priority_medium_text
            TaskPriority.LOW -> R.color.priority_low_text
        }
    )

    override fun onDestroy() {
        confirmDialog?.setOnDismissListener(null)
        confirmDialog?.dismiss()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_TASK_ID = "complete_task_id"
    }
}
