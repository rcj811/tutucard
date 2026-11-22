package tw.knox.dailytasks.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TaskEntity::class,
        CategoryEntity::class,
        TagEntity::class,
        TaskTagEntity::class,
        SubTaskEntity::class,
        ReminderHistoryEntity::class,
        MemoEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun categoryDao(): CategoryDao
    abstract fun tagDao(): TagDao
    abstract fun taskTagDao(): TaskTagDao
    abstract fun subTaskDao(): SubTaskDao
    abstract fun reminderHistoryDao(): ReminderHistoryDao
    abstract fun memoDao(): MemoDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        private val migration1To2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE tasks ADD COLUMN ringEnabled INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE tasks ADD COLUMN ringDurationSeconds INTEGER NOT NULL DEFAULT 5")
                database.execSQL("ALTER TABLE tasks ADD COLUMN ringToneUri TEXT")
                database.execSQL("ALTER TABLE tasks ADD COLUMN ringVolume INTEGER NOT NULL DEFAULT 70")
            }
        }

        private val migration2To3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE tasks ADD COLUMN reminderOffsets TEXT NOT NULL DEFAULT ''")
                database.execSQL(
                    """UPDATE tasks
                       SET reminderOffsets = CAST(MAX(0, (startDateTime - reminderAt) / 60000) AS TEXT)
                       WHERE reminderEnabled = 1 AND reminderAt IS NOT NULL"""
                )
            }
        }

        private val migration3To4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS memos (id TEXT NOT NULL, title TEXT NOT NULL, content TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(id))")
            }
        }

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "daily_tasks.db"
            ).addMigrations(migration1To2, migration2To3, migration3To4).build().also { instance = it }
        }
    }
}
