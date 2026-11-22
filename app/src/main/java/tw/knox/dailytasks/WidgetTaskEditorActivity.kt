package tw.knox.dailytasks

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tw.knox.dailytasks.data.CategoryEntity
import tw.knox.dailytasks.data.TaskDraft
import tw.knox.dailytasks.data.TaskRepository
import tw.knox.dailytasks.data.TaskRules
import tw.knox.dailytasks.data.TaskWithDetails
import tw.knox.dailytasks.ui.DailyTasksTheme
import tw.knox.dailytasks.ui.TaskEditorScreen
import tw.knox.dailytasks.ui.ThemeMode
import tw.knox.dailytasks.ui.isDarkAppTheme

class WidgetTaskEditorActivity : ChineseActivity() {
    private val repository by lazy { TaskRepository(applicationContext) }
    private var task by mutableStateOf<TaskWithDetails?>(null)
    private var categories by mutableStateOf<List<CategoryEntity>>(emptyList())
    private var saving by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
        if (taskId < 0L) {
            finish()
            return
        }
        val theme = runCatching {
            ThemeMode.valueOf(getSharedPreferences("settings", MODE_PRIVATE).getString("theme", "LIGHT") ?: "LIGHT")
        }.getOrDefault(ThemeMode.LIGHT)
        setContent {
            DailyTasksTheme(theme) {
                val dark = isDarkAppTheme()
                val background = MaterialTheme.colorScheme.background.toArgb()
                val surface = MaterialTheme.colorScheme.surface.toArgb()
                SideEffect {
                    window.statusBarColor = background
                    window.navigationBarColor = surface
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = !dark
                        isAppearanceLightNavigationBars = !dark
                    }
                }
                BackHandler { if (!saving) finish() }
                val existing = task
                if (existing == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    TaskEditorScreen(
                        existing = existing,
                        categories = categories,
                        onClose = { if (!saving) finish() },
                        onSave = ::save,
                        isSaving = saving
                    )
                }
                if (saving) {
                    AlertDialog(
                        onDismissRequest = {},
                        text = { CircularProgressIndicator() },
                        confirmButton = {}
                    )
                }
            }
        }
        lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                repository.db.taskDao().get(taskId) to repository.db.categoryDao().getAll()
            }
            if (loaded.first?.task?.let { TaskRules.canEdit(it) } != true) {
                finish()
            } else {
                categories = loaded.second
                task = loaded.first
            }
        }
    }

    private fun save(draft: TaskDraft) {
        if (saving) return
        saving = true
        lifecycleScope.launch {
            try {
                val saved = withContext(Dispatchers.IO) {
                    val current = draft.id?.let { repository.db.taskDao().getEntity(it) }
                    if (current == null || !TaskRules.canEdit(current)) false else {
                        repository.save(draft)
                        true
                    }
                }
                if (saved) {
                    setResult(RESULT_OK)
                } else {
                    Toast.makeText(this@WidgetTaskEditorActivity, "項目已刪除、完成或屬於過去日期，無法修改", Toast.LENGTH_LONG).show()
                }
                finish()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                saving = false
                Toast.makeText(this@WidgetTaskEditorActivity, "儲存失敗，請再試一次", Toast.LENGTH_LONG).show()
            }
        }
    }

    companion object { const val EXTRA_TASK_ID = "widget_edit_task_id" }
}
