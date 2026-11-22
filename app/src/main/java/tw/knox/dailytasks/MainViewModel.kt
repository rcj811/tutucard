package tw.knox.dailytasks

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tw.knox.dailytasks.backup.BackupManager
import tw.knox.dailytasks.backup.BackupResult
import tw.knox.dailytasks.data.CategoryEntity
import tw.knox.dailytasks.data.ReminderHistoryEntity
import tw.knox.dailytasks.data.TagEntity
import tw.knox.dailytasks.data.TaskDraft
import tw.knox.dailytasks.data.TaskRepository
import tw.knox.dailytasks.data.TaskRules
import tw.knox.dailytasks.data.TaskWithDetails
import tw.knox.dailytasks.ui.ThemeMode

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = TaskRepository(application)
    private val backup = BackupManager(application)
    private val prefs = application.getSharedPreferences("settings", 0)

    val tasksLoaded = MutableStateFlow(false)
    val saving = MutableStateFlow(false)
    val saveError = MutableStateFlow<String?>(null)
    val tasks: StateFlow<List<TaskWithDetails>> = repository.tasks.onEach { tasksLoaded.value = true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val categories: StateFlow<List<CategoryEntity>> = repository.categories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val tags: StateFlow<List<TagEntity>> = repository.tags.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val histories: StateFlow<List<ReminderHistoryEntity>> = repository.histories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val themeMode = MutableStateFlow(runCatching { ThemeMode.valueOf(prefs.getString("theme", "LIGHT") ?: "LIGHT") }.getOrDefault(ThemeMode.LIGHT))
    val hideLockContent = MutableStateFlow(prefs.getBoolean("hide_lock_content", false))
    val allowEarlyComplete = MutableStateFlow(prefs.getBoolean(PREF_ALLOW_EARLY_COMPLETE, false))

    init {
        viewModelScope.launch {
            repository.ensureInitialData()
            repository.reschedulePending()
        }
    }

    fun save(draft: TaskDraft, onSaved: (Long) -> Unit) {
        if (saving.value) return
        saving.value = true
        saveError.value = null
        viewModelScope.launch {
            try {
                onSaved(repository.save(draft))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: IllegalArgumentException) {
                saveError.value = error.chineseMessage("無法儲存此任務，請返回清單重新選擇")
            } catch (_: Exception) {
                saveError.value = "儲存失敗，請再試一次"
            } finally {
                saving.value = false
            }
        }
    }

    fun dismissSaveError() { saveError.value = null }

    fun toggleCompleted(id: Long) = viewModelScope.launch { repository.toggleCompleted(id) }
    fun complete(id: Long) = viewModelScope.launch { repository.complete(id) }
    fun snooze(id: Long, minutes: Int) = viewModelScope.launch { repository.snooze(id, minutes) }
    fun moveToToday(id: Long) = viewModelScope.launch { repository.moveToToday(id) }
    fun skipOccurrence(id: Long) = viewModelScope.launch { repository.skipOccurrence(id) }
    fun delete(id: Long, onDeleted: () -> Unit) = viewModelScope.launch { repository.delete(id); onDeleted() }
    fun setSubTaskCompleted(id: Long, completed: Boolean) = viewModelScope.launch { repository.setSubTaskCompleted(id, completed) }
    fun addCategory(name: String) = viewModelScope.launch { repository.addCategory(name) }
    fun renameCategory(id: Long, name: String) = viewModelScope.launch { repository.renameCategory(id, name) }
    fun deleteCategory(id: Long) = viewModelScope.launch { repository.deleteCategory(id) }
    fun addTag(name: String) = viewModelScope.launch { repository.addTag(name) }
    fun renameTag(id: Long, name: String) = viewModelScope.launch { repository.renameTag(id, name) }
    fun deleteTag(id: Long) = viewModelScope.launch { repository.deleteTag(id) }

    fun setTheme(mode: ThemeMode) {
        themeMode.value = mode
        prefs.edit().putString("theme", mode.name).apply()
    }

    fun setHideLockContent(hidden: Boolean) {
        hideLockContent.value = hidden
        prefs.edit().putBoolean("hide_lock_content", hidden).apply()
    }

    fun setAllowEarlyComplete(allowed: Boolean) {
        allowEarlyComplete.value = allowed
        TaskRules.allowEarlyComplete = allowed
        prefs.edit().putBoolean(PREF_ALLOW_EARLY_COMPLETE, allowed).apply()
    }

    fun exportBackup(uri: Uri, password: String, onResult: (BackupResult) -> Unit) = viewModelScope.launch {
        onResult(backup.export(uri, password))
    }

    fun importBackup(uri: Uri, password: String, onResult: (BackupResult) -> Unit) = viewModelScope.launch {
        onResult(backup.import(uri, password))
    }

    companion object {
        const val PREF_ALLOW_EARLY_COMPLETE = "allow_early_complete"
    }
}
