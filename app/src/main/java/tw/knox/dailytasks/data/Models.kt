package tw.knox.dailytasks.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Junction
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

enum class TaskPriority { HIGH, MEDIUM, LOW }

enum class TaskRepeat { NONE, DAILY, WEEKLY, MONTHLY, YEARLY, CUSTOM }

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val note: String = "",
    val startDateTime: Long,
    val endDateTime: Long? = null,
    val priority: String = TaskPriority.MEDIUM.name,
    val categoryId: Long? = null,
    val isCompleted: Boolean = false,
    val completedAt: Long? = null,
    val repeatRule: String = TaskRepeat.NONE.name,
    val reminderEnabled: Boolean = true,
    val reminderAt: Long? = null,
    val reminderOffsets: String = "0",
    val repeatUntilComplete: Boolean = false,
    val repeatReminderInterval: Int = 10,
    val nextReminderAt: Long? = null,
    val ringEnabled: Boolean = false,
    val ringDurationSeconds: Int = 5,
    val ringToneUri: String? = null,
    val ringVolume: Int = 70,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: String = "folder",
    val sortOrder: Int = 0
)

@Entity(tableName = "tags")
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String
)

@Entity(tableName = "task_tags", primaryKeys = ["taskId", "tagId"], indices = [Index("tagId")])
data class TaskTagEntity(val taskId: Long, val tagId: Long)

@Entity(tableName = "sub_tasks")
data class SubTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val title: String,
    val isCompleted: Boolean = false,
    val sortOrder: Int = 0
)

@Entity(tableName = "reminder_history")
data class ReminderHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val triggeredAt: Long,
    val action: String,
    val nextReminderAt: Long? = null
)

data class TaskWithDetails(
    @Embedded val task: TaskEntity,
    @Relation(parentColumn = "categoryId", entityColumn = "id")
    val category: CategoryEntity?,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(TaskTagEntity::class, parentColumn = "taskId", entityColumn = "tagId")
    )
    val tags: List<TagEntity>,
    @Relation(parentColumn = "id", entityColumn = "taskId")
    val subTasks: List<SubTaskEntity>
)

data class TaskDraft(
    val id: Long? = null,
    val title: String,
    val note: String,
    val startDateTime: Long,
    val endDateTime: Long?,
    val priority: TaskPriority,
    val categoryId: Long?,
    val repeat: TaskRepeat,
    val customRepeatIntervalWeeks: Int = 1,
    val customRepeatWeekdays: Set<Int> = emptySet(),
    val reminderEnabled: Boolean,
    val reminderOffsets: Set<Int>,
    val repeatUntilComplete: Boolean,
    val repeatReminderInterval: Int,
    val ringEnabled: Boolean,
    val ringDurationSeconds: Int,
    val ringToneUri: String?,
    val ringVolume: Int,
    val tags: List<String>,
    val subTasks: List<String>
)
