package tw.knox.dailytasks.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Transaction
    @Query("SELECT * FROM tasks ORDER BY isCompleted, startDateTime")
    fun observeAll(): Flow<List<TaskWithDetails>>

    @Transaction
    @Query("SELECT * FROM tasks WHERE id = :id")
    fun observe(id: Long): Flow<TaskWithDetails?>

    @Transaction
    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun get(id: Long): TaskWithDetails?

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getEntity(id: Long): TaskEntity?

    @Query("SELECT * FROM tasks ORDER BY startDateTime")
    suspend fun getAllEntities(): List<TaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: TaskEntity): Long

    @Update
    suspend fun update(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM tasks")
    suspend fun deleteAll()

    @Query("UPDATE tasks SET isCompleted = :completed, completedAt = :completedAt, nextReminderAt = :nextReminderAt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setCompleted(id: Long, completed: Boolean, completedAt: Long?, nextReminderAt: Long?, updatedAt: Long)

    @Query("UPDATE tasks SET isCompleted = 1, completedAt = :completedAt, nextReminderAt = NULL, updatedAt = :completedAt WHERE id = :id AND isCompleted = 0")
    suspend fun completeIfPending(id: Long, completedAt: Long): Int

    @Query("UPDATE tasks SET nextReminderAt = :nextReminderAt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setNextReminder(id: Long, nextReminderAt: Long?, updatedAt: Long)

    @Query("""
        UPDATE tasks SET
            startDateTime = startDateTime + :delayMillis,
            endDateTime = CASE WHEN endDateTime IS NULL THEN NULL ELSE endDateTime + :delayMillis END,
            reminderAt = CASE WHEN reminderAt IS NULL THEN NULL ELSE reminderAt + :delayMillis END,
            nextReminderAt = :nextReminderAt,
            repeatUntilComplete = 1,
            repeatReminderInterval = :intervalMinutes,
            updatedAt = :updatedAt
        WHERE id = :id AND isCompleted = 0 AND reminderEnabled = 1 AND startDateTime >= :todayStart
    """)
    suspend fun snoozeAndEnableRepeat(id: Long, nextReminderAt: Long, delayMillis: Long, intervalMinutes: Int, updatedAt: Long, todayStart: Long): Int

    @Query("SELECT * FROM tasks WHERE isCompleted = 0 AND reminderEnabled = 1 AND nextReminderAt IS NOT NULL")
    suspend fun pendingReminders(): List<TaskEntity>
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY sortOrder, name") fun observeAll(): Flow<List<CategoryEntity>>
    @Query("SELECT * FROM categories ORDER BY sortOrder, name") suspend fun getAll(): List<CategoryEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(item: CategoryEntity): Long
    @Query("UPDATE categories SET name = :name WHERE id = :id") suspend fun rename(id: Long, name: String)
    @Query("UPDATE tasks SET categoryId = NULL WHERE categoryId = :id") suspend fun clearFromTasks(id: Long)
    @Query("DELETE FROM categories WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM categories") suspend fun deleteAll()
}

@Dao
interface TagDao {
    @Query("SELECT * FROM tags ORDER BY name") fun observeAll(): Flow<List<TagEntity>>
    @Query("SELECT * FROM tags ORDER BY name") suspend fun getAll(): List<TagEntity>
    @Query("SELECT * FROM tags WHERE name = :name LIMIT 1") suspend fun find(name: String): TagEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(item: TagEntity): Long
    @Query("UPDATE tags SET name = :name WHERE id = :id") suspend fun rename(id: Long, name: String)
    @Query("DELETE FROM tags WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM tags") suspend fun deleteAll()
}

@Dao
interface TaskTagDao {
    @Query("SELECT * FROM task_tags") suspend fun getAll(): List<TaskTagEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(item: TaskTagEntity)
    @Query("DELETE FROM task_tags WHERE taskId = :taskId") suspend fun deleteForTask(taskId: Long)
    @Query("DELETE FROM task_tags WHERE tagId = :tagId") suspend fun deleteForTag(tagId: Long)
    @Query("DELETE FROM task_tags") suspend fun deleteAll()
}

@Dao
interface SubTaskDao {
    @Query("SELECT * FROM sub_tasks ORDER BY taskId, sortOrder") suspend fun getAll(): List<SubTaskEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(item: SubTaskEntity): Long
    @Query("UPDATE sub_tasks SET isCompleted = :completed WHERE id = :id") suspend fun setCompleted(id: Long, completed: Boolean)
    @Query("DELETE FROM sub_tasks WHERE taskId = :taskId") suspend fun deleteForTask(taskId: Long)
    @Query("DELETE FROM sub_tasks") suspend fun deleteAll()
}

@Dao
interface ReminderHistoryDao {
    @Query("SELECT * FROM reminder_history ORDER BY triggeredAt DESC") fun observeAll(): Flow<List<ReminderHistoryEntity>>
    @Query("SELECT * FROM reminder_history ORDER BY triggeredAt") suspend fun getAll(): List<ReminderHistoryEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(item: ReminderHistoryEntity): Long
    @Query("DELETE FROM reminder_history WHERE taskId = :taskId") suspend fun deleteForTask(taskId: Long)
    @Query("DELETE FROM reminder_history") suspend fun deleteAll()
}
