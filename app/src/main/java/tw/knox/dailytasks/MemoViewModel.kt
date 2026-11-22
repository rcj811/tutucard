package tw.knox.dailytasks

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tw.knox.dailytasks.data.AppDatabase
import tw.knox.dailytasks.data.MemoEntity
import tw.knox.dailytasks.data.MemoRules

class MemoViewModel(application: Application, private val savedState: SavedStateHandle) : AndroidViewModel(application) {
    private val db = AppDatabase.get(application)
    private val dao = db.memoDao()
    private var original: MemoEntity? = null
    private var wasExistingDraft = savedState.get<Boolean>("memo_existing") ?: false
    private var afterExit: (() -> Unit)? = null

    val loaded = MutableStateFlow(false)
    val memos = dao.observeAll().onEach { loaded.value = true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val editor = MutableStateFlow(restoreDraft())
    val busy = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    val confirmExit = MutableStateFlow(false)

    init {
        editor.value?.let { draft ->
            busy.value = true
            viewModelScope.launch {
                try {
                    original = dao.get(draft.id)
                    if (original != null) {
                        wasExistingDraft = true
                        savedState["memo_existing"] = true
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    error.value = "無法讀取原筆記，請稍後再試。"
                } finally {
                    busy.value = false
                }
            }
        }
    }

    fun create() {
        if (busy.value || editor.value != null) return
        original = null
        wasExistingDraft = false
        setDraft(MemoEntity())
    }

    fun open(memo: MemoEntity) {
        if (busy.value || editor.value != null) return
        original = memo
        wasExistingDraft = true
        setDraft(memo)
    }

    fun updateTitle(title: String) = update { it.copy(title = title) }
    fun updateContent(content: String) = update { it.copy(content = MemoRules.limitContent(content)) }
    fun dismissError() { error.value = null }

    private fun update(change: (MemoEntity) -> MemoEntity) {
        if (busy.value) return
        val current = editor.value ?: return
        setDraft(change(current))
    }

    private fun setDraft(memo: MemoEntity?) {
        editor.value = memo
        if (memo == null) {
            listOf("memo_id", "memo_title", "memo_content", "memo_created", "memo_updated", "memo_existing").forEach { savedState.remove<Any>(it) }
        } else {
            savedState["memo_id"] = memo.id
            savedState["memo_title"] = memo.title
            savedState["memo_content"] = memo.content
            savedState["memo_created"] = memo.createdAt
            savedState["memo_updated"] = memo.updatedAt
            savedState["memo_existing"] = wasExistingDraft
        }
    }

    private fun restoreDraft(): MemoEntity? = savedState.get<String>("memo_id")?.let { id ->
        MemoEntity(
            id = id,
            title = savedState.get<String>("memo_title").orEmpty(),
            content = MemoRules.limitContent(savedState.get<String>("memo_content").orEmpty()),
            createdAt = savedState.get<Long>("memo_created") ?: System.currentTimeMillis(),
            updatedAt = savedState.get<Long>("memo_updated") ?: System.currentTimeMillis()
        )
    }

    fun save() {
        if (busy.value) return
        val draft = editor.value ?: return
        val wasExisting = wasExistingDraft
        busy.value = true
        viewModelScope.launch {
            try {
                db.withTransaction {
                    val stored = dao.get(draft.id)
                    require(!wasExisting || stored != null) { "原筆記已刪除，請返回列表重新選擇。" }
                    if (stored == null && draft.title.isBlank() && draft.content.isBlank()) return@withTransaction
                    val content = MemoRules.limitContent(draft.content)
                    if (stored != null && stored.title == draft.title && stored.content == content) return@withTransaction
                    val now = System.currentTimeMillis()
                    dao.save(draft.copy(content = content, createdAt = stored?.createdAt ?: now, updatedAt = now))
                }
                clearEditor()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error.value = failure.chineseMessage("筆記儲存失敗，內容仍保留在編輯畫面，請稍後再試。")
            } finally {
                busy.value = false
            }
        }
    }

    fun close(onClosed: () -> Unit = {}) {
        if (busy.value) return
        val draft = editor.value
        val changed = draft != null && (draft.title != original?.title.orEmpty() || draft.content != original?.content.orEmpty())
        if (changed) {
            afterExit = onClosed
            confirmExit.value = true
        } else {
            clearEditor()
            onClosed()
        }
    }

    fun keepEditing() {
        confirmExit.value = false
        afterExit = null
    }

    fun discard() {
        if (busy.value) return
        val action = afterExit
        clearEditor()
        action?.invoke()
    }

    private fun clearEditor() {
        setDraft(null)
        original = null
        wasExistingDraft = false
        afterExit = null
        confirmExit.value = false
    }

    fun delete() {
        if (busy.value) return
        val draft = editor.value ?: return
        busy.value = true
        viewModelScope.launch {
            try {
                dao.delete(draft.id)
                clearEditor()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                error.value = "筆記刪除失敗，請稍後再試。"
            } finally {
                busy.value = false
            }
        }
    }
}
