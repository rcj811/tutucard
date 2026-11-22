package tw.knox.dailytasks.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Entity(tableName = "memos")
data class MemoEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val content: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Dao
interface MemoDao {
    @Query("SELECT * FROM memos ORDER BY updatedAt DESC, id")
    fun observeAll(): Flow<List<MemoEntity>>

    @Query("SELECT * FROM memos ORDER BY updatedAt DESC, id")
    suspend fun getAll(): List<MemoEntity>

    @Query("SELECT * FROM memos WHERE id = :id")
    suspend fun get(id: String): MemoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(memo: MemoEntity)

    @Query("DELETE FROM memos WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM memos")
    suspend fun deleteAll()
}

object MemoRules {
    const val MAX_CONTENT_LENGTH = 500

    fun contentLength(text: String): Int = text.codePointCount(0, text.length)

    fun limitContent(text: String): String = if (contentLength(text) <= MAX_CONTENT_LENGTH) text
        else text.substring(0, text.offsetByCodePoints(0, MAX_CONTENT_LENGTH))
}
