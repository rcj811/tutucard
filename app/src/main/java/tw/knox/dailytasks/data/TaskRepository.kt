package tw.knox.dailytasks.data

import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import tw.knox.dailytasks.reminder.AlarmScheduler
import tw.knox.dailytasks.reminder.TaskRinger
import tw.knox.dailytasks.widget.TaskWidgetUpdater

class TaskRepository(private val context: Context, val db: AppDatabase = AppDatabase.get(context)) {
    val tasks: Flow<List<TaskWithDetails>> = db.taskDao().observeAll()
    val categories: Flow<List<CategoryEntity>> = db.categoryDao().observeAll()
    val tags: Flow<List<TagEntity>> = db.tagDao().observeAll()
    val histories: Flow<List<ReminderHistoryEntity>> = db.reminderHistoryDao().observeAll()

    suspend fun ensureInitialData() {
        val dataState = context.getSharedPreferences("data_state", Context.MODE_PRIVATE)
        if (!dataState.getBoolean("empty_dataset_v2_applied", false)) {
            db.taskDao().getAllEntities().forEach { AlarmScheduler(context).cancel(it) }
            db.withTransaction {
                db.reminderHistoryDao().deleteAll()
                db.taskTagDao().deleteAll()
                db.subTaskDao().deleteAll()
                db.taskDao().deleteAll()
                db.tagDao().deleteAll()
            }
            dataState.edit().putBoolean("empty_dataset_v2_applied", true).apply()
        }
        if (db.categoryDao().getAll().isEmpty()) {
            val categories = listOf("工作", "家庭", "購物", "財務", "健康", "個人", "重要")
            categories.forEachIndexed { index, name ->
                db.categoryDao().insert(CategoryEntity(name = name, sortOrder = index))
            }
        }
        TaskWidgetUpdater.updateAll(context)
    }

    suspend fun save(draft: TaskDraft): Long {
        val previousTask = draft.id?.let { db.taskDao().getEntity(it) }
        val saved = db.withTransaction {
        val existingDetails = draft.id?.let { db.taskDao().get(it) }
        val existing = existingDetails?.task
        if (draft.id != null) {
            requireNotNull(existing) { "原任務已刪除，請返回清單重新選擇" }
            require(!existing.isCompleted) { "任務已完成，無法修改" }
            require(!TaskRules.isPastDate(existing.startDateTime)) { "過去日期的任務僅可查閱或刪除" }
        }
        val now = System.currentTimeMillis()
        require(!TaskRules.isPastDate(draft.startDateTime, now)) { "只能新增或修改為今天起的日期" }
        val reminderOffsets = if (draft.reminderEnabled) draft.reminderOffsets.filter { it >= 0 }.toSet() else emptySet()
        val reminderTimes = reminderOffsets.map { TaskRules.reminderAt(draft.startDateTime, it) }.sorted()
        val reminderAt = reminderTimes.firstOrNull()
        val nextReminderAt = reminderTimes.firstOrNull { it >= now }
        val entity = TaskEntity(
            id = existing?.id ?: 0,
            title = draft.title.trim(),
            note = draft.note.trim(),
            startDateTime = draft.startDateTime,
            endDateTime = draft.endDateTime,
            priority = draft.priority.name,
            categoryId = draft.categoryId,
            isCompleted = existing?.isCompleted ?: false,
            completedAt = existing?.completedAt,
            repeatRule = TaskRules.encodeRepeat(draft.repeat, draft.customRepeatIntervalWeeks, draft.customRepeatWeekdays),
            reminderEnabled = reminderOffsets.isNotEmpty(),
            reminderAt = reminderAt,
            reminderOffsets = TaskRules.encodeReminderOffsets(reminderOffsets),
            repeatUntilComplete = draft.repeatUntilComplete,
            repeatReminderInterval = draft.repeatReminderInterval.coerceAtLeast(1),
            nextReminderAt = if (existing?.isCompleted != true) nextReminderAt else null,
            ringEnabled = draft.ringEnabled && reminderOffsets.isNotEmpty(),
            ringDurationSeconds = draft.ringDurationSeconds.coerceIn(1, 10),
            ringToneUri = draft.ringToneUri,
            ringVolume = draft.ringVolume.coerceIn(0, 100),
            createdAt = existing?.createdAt ?: now,
            updatedAt = now
        )
        val taskId = db.taskDao().insert(entity)
        db.taskTagDao().deleteForTask(taskId)
        draft.tags.map { it.trim().removePrefix("#") }.filter { it.isNotBlank() }.distinct().forEach { name ->
            val tagId = db.tagDao().find(name)?.id ?: db.tagDao().insert(TagEntity(name = name))
            db.taskTagDao().insert(TaskTagEntity(taskId, tagId))
        }
        db.subTaskDao().deleteForTask(taskId)
        val priorSubTasks = existingDetails?.subTasks?.associateBy { it.title }.orEmpty()
        draft.subTasks.map { it.trim() }.filter { it.isNotBlank() }.forEachIndexed { index, title ->
            db.subTaskDao().insert(SubTaskEntity(taskId = taskId, title = title, isCompleted = priorSubTasks[title]?.isCompleted ?: false, sortOrder = index))
        }
        entity.copy(id = taskId)
        }
        previousTask?.let { AlarmScheduler(context).cancel(it) } ?: AlarmScheduler(context).cancel(saved.id)
        if (!saved.isCompleted && saved.reminderEnabled && saved.nextReminderAt != null) AlarmScheduler(context).schedule(saved)
        TaskWidgetUpdater.updateAll(context)
        return saved.id
    }

    suspend fun toggleCompleted(taskId: Long) {
        complete(taskId)
    }

    suspend fun complete(taskId: Long) {
        val completed = db.withTransaction {
            val details = db.taskDao().get(taskId) ?: return@withTransaction null
            val current = details.task
            val now = System.currentTimeMillis()
            if (!TaskRules.canComplete(current, now)) return@withTransaction null
            if (db.taskDao().completeIfPending(taskId, now) == 0) return@withTransaction null
            db.reminderHistoryDao().insert(ReminderHistoryEntity(taskId = taskId, triggeredAt = now, action = "COMPLETED"))
            val nextTask = TaskRules.nextOccurrenceFrom(current.startDateTime, current.repeatRule, TaskRules.startOfToday(now))?.let { nextStart ->
                val duration = current.endDateTime?.minus(current.startDateTime)
                val nextReminderTimes = TaskRules.reminderOffsets(current)
                    .map { TaskRules.reminderAt(nextStart, it) }
                    .sorted()
                val next = current.copy(
                    id = 0,
                    startDateTime = nextStart,
                    endDateTime = duration?.let { nextStart + it },
                    isCompleted = false,
                    completedAt = null,
                    reminderAt = nextReminderTimes.firstOrNull(),
                    nextReminderAt = nextReminderTimes.firstOrNull { it >= now },
                    createdAt = now,
                    updatedAt = now
                )
                val newId = db.taskDao().insert(next)
                details.tags.forEach { db.taskTagDao().insert(TaskTagEntity(newId, it.id)) }
                details.subTasks.sortedBy { it.sortOrder }.forEach {
                    db.subTaskDao().insert(it.copy(id = 0, taskId = newId, isCompleted = false))
                }
                next.copy(id = newId)
            }
            current to nextTask
        } ?: return
        TaskRinger.stop(taskId)
        AlarmScheduler(context).cancel(completed.first)
        completed.second?.let { if (it.nextReminderAt != null) AlarmScheduler(context).schedule(it) }
        TaskWidgetUpdater.updateAll(context)
    }

    /** 過去日期未完成的任務移到今天（保留時間，已過則改為 10 分鐘後）。 */
    suspend fun moveToToday(taskId: Long) = reschedule(taskId, "MOVED") { task, now ->
        if (TaskRules.canMoveToToday(task, now)) TaskRules.moveToTodayStart(task.startDateTime, now) else null
    }

    /** 重複任務跳過這一次：直接改到下一期（至少今天起），不建立完成紀錄。 */
    suspend fun skipOccurrence(taskId: Long) = reschedule(taskId, "SKIPPED") { task, now ->
        if (TaskRules.canSkip(task)) TaskRules.nextOccurrenceFrom(task.startDateTime, task.repeatRule, TaskRules.startOfToday(now)) else null
    }

    private suspend fun reschedule(taskId: Long, action: String, newStartOf: (TaskEntity, Long) -> Long?) {
        val result = db.withTransaction {
            val task = db.taskDao().getEntity(taskId) ?: return@withTransaction null
            val now = System.currentTimeMillis()
            val newStart = newStartOf(task, now) ?: return@withTransaction null
            val reminderTimes = TaskRules.reminderOffsets(task).map { TaskRules.reminderAt(newStart, it) }.sorted()
            val updated = task.copy(
                startDateTime = newStart,
                endDateTime = task.endDateTime?.let { newStart + (it - task.startDateTime) },
                reminderAt = reminderTimes.firstOrNull(),
                nextReminderAt = if (task.reminderEnabled) reminderTimes.firstOrNull { it >= now } else null,
                updatedAt = now
            )
            db.taskDao().update(updated)
            db.reminderHistoryDao().insert(ReminderHistoryEntity(taskId = taskId, triggeredAt = now, action = action))
            task to updated
        } ?: return
        TaskRinger.stop(taskId)
        AlarmScheduler(context).cancel(result.first)
        if (result.second.reminderEnabled && result.second.nextReminderAt != null) AlarmScheduler(context).schedule(result.second)
        TaskWidgetUpdater.updateAll(context)
    }

    suspend fun snooze(taskId: Long, minutes: Int) {
        val interval = minutes.coerceAtLeast(1)
        val delayMillis = interval.toLong() * 60_000L
        val updated = db.withTransaction {
            val task = db.taskDao().getEntity(taskId) ?: return@withTransaction null
            val now = System.currentTimeMillis()
            if (!TaskRules.canEdit(task, now) || !task.reminderEnabled) return@withTransaction null
            val next = now + delayMillis
            if (db.taskDao().snoozeAndEnableRepeat(taskId, next, delayMillis, interval, now, TaskRules.startOfToday(now)) == 0) return@withTransaction null
            db.reminderHistoryDao().insert(ReminderHistoryEntity(taskId = taskId, triggeredAt = now, action = "SNOOZED", nextReminderAt = next))
            db.taskDao().getEntity(taskId)
        } ?: return
        TaskRinger.stop(taskId)
        AlarmScheduler(context).cancel(taskId)
        AlarmScheduler(context).schedule(updated)
        TaskWidgetUpdater.updateAll(context)
    }

    suspend fun delete(taskId: Long) {
        TaskRinger.stop(taskId)
        db.taskDao().getEntity(taskId)?.let { AlarmScheduler(context).cancel(it) } ?: AlarmScheduler(context).cancel(taskId)
        db.withTransaction {
            db.reminderHistoryDao().deleteForTask(taskId)
            db.taskTagDao().deleteForTask(taskId)
            db.subTaskDao().deleteForTask(taskId)
            db.taskDao().delete(taskId)
        }
        TaskWidgetUpdater.updateAll(context)
    }

    suspend fun setSubTaskCompleted(id: Long, completed: Boolean) = db.subTaskDao().setCompleted(id, completed)

    suspend fun addCategory(name: String) {
        val trimmed = name.trim()
        if (trimmed.isNotBlank()) db.categoryDao().insert(CategoryEntity(name = trimmed, sortOrder = db.categoryDao().getAll().size))
    }

    suspend fun renameCategory(id: Long, name: String) {
        val trimmed = name.trim()
        if (trimmed.isNotBlank()) db.categoryDao().rename(id, trimmed)
    }

    suspend fun deleteCategory(id: Long) = db.withTransaction {
        db.categoryDao().clearFromTasks(id)
        db.categoryDao().delete(id)
    }

    suspend fun addTag(name: String) {
        val trimmed = name.trim().removePrefix("#")
        if (trimmed.isNotBlank() && db.tagDao().find(trimmed) == null) db.tagDao().insert(TagEntity(name = trimmed))
    }

    suspend fun renameTag(id: Long, name: String) {
        val trimmed = name.trim().removePrefix("#")
        if (trimmed.isNotBlank()) db.tagDao().rename(id, trimmed)
    }

    suspend fun deleteTag(id: Long) = db.withTransaction {
        db.taskTagDao().deleteForTag(id)
        db.tagDao().delete(id)
    }

    suspend fun reschedulePending() {
        db.taskDao().pendingReminders().forEach { AlarmScheduler(context).schedule(it) }
    }
}
