package tw.knox.dailytasks.backup

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject
import tw.knox.dailytasks.chineseMessage
import tw.knox.dailytasks.data.AppDatabase
import tw.knox.dailytasks.data.CategoryEntity
import tw.knox.dailytasks.data.MemoEntity
import tw.knox.dailytasks.data.MemoRules
import tw.knox.dailytasks.data.ReminderHistoryEntity
import tw.knox.dailytasks.data.SubTaskEntity
import tw.knox.dailytasks.data.TagEntity
import tw.knox.dailytasks.data.TaskEntity
import tw.knox.dailytasks.data.TaskRepository
import tw.knox.dailytasks.data.TaskTagEntity
import tw.knox.dailytasks.reminder.AlarmScheduler
import tw.knox.dailytasks.widget.TaskWidgetUpdater
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class BackupManager(private val context: Context, private val db: AppDatabase = AppDatabase.get(context)) {
    suspend fun export(uri: Uri, password: String): BackupResult = runCatching {
        val payload = buildPayload().toString().toByteArray(Charsets.UTF_8)
        val encrypted = password.isNotBlank()
        val manifest = JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("createdAt", System.currentTimeMillis())
            .put("encrypted", encrypted)
        val data: ByteArray
        val entryName: String
        if (encrypted) {
            val salt = ByteArray(16).also(SecureRandom()::nextBytes)
            val iv = ByteArray(12).also(SecureRandom()::nextBytes)
            data = crypt(Cipher.ENCRYPT_MODE, payload, password, salt, iv)
            manifest.put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            manifest.put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
            entryName = "payload.bin"
        } else {
            data = payload
            entryName = "data.json"
        }
        context.contentResolver.openOutputStream(uri, "w")?.use { output ->
            ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry("manifest.json"))
                zip.write(manifest.toString(2).toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                zip.putNextEntry(ZipEntry(entryName))
                zip.write(data)
                zip.closeEntry()
            }
        } ?: error("無法開啟備份檔案")
        BackupResult(true, if (encrypted) "已匯出加密備份" else "已匯出備份")
    }.getOrElse { BackupResult(false, it.chineseMessage("匯出失敗，請確認檔案位置與儲存權限。")) }

    suspend fun import(uri: Uri, password: String): BackupResult = runCatching {
        val entries = mutableMapOf<String, ByteArray>()
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val buffer = ByteArrayOutputStream()
                    zip.copyTo(buffer)
                    entries[entry.name] = buffer.toByteArray()
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        } ?: error("無法讀取備份檔案")
        val manifest = entries["manifest.json"]?.let { JSONObject(String(it, Charsets.UTF_8)) } ?: error("不是有效的吐兔卡備份")
        require(manifest.optString("format") == FORMAT) { "備份格式不正確" }
        require(manifest.optInt("version") <= VERSION) { "備份版本較新，請先更新應用程式" }
        val encrypted = manifest.optBoolean("encrypted")
        val payloadBytes = if (encrypted) {
            require(password.isNotBlank()) { "此備份需要密碼" }
            val salt = Base64.decode(manifest.getString("salt"), Base64.NO_WRAP)
            val iv = Base64.decode(manifest.getString("iv"), Base64.NO_WRAP)
            crypt(Cipher.DECRYPT_MODE, entries["payload.bin"] ?: error("加密資料遺失"), password, salt, iv)
        } else entries["data.json"] ?: error("備份資料遺失")
        restorePayload(JSONObject(String(payloadBytes, Charsets.UTF_8)))
        TaskRepository(context).reschedulePending()
        TaskWidgetUpdater.updateAll(context)
        BackupResult(true, "資料與提醒已完整還原")
    }.getOrElse { BackupResult(false, it.chineseMessage("匯入失敗，請確認備份檔案、密碼與讀取權限。")) }

    private suspend fun buildPayload(): JSONObject = JSONObject().apply {
        put("tasks", JSONArray().apply { db.taskDao().getAllEntities().forEach { put(it.toJson()) } })
        put("categories", JSONArray().apply { db.categoryDao().getAll().forEach { put(it.toJson()) } })
        put("tags", JSONArray().apply { db.tagDao().getAll().forEach { put(it.toJson()) } })
        put("taskTags", JSONArray().apply { db.taskTagDao().getAll().forEach { put(it.toJson()) } })
        put("subTasks", JSONArray().apply { db.subTaskDao().getAll().forEach { put(it.toJson()) } })
        put("reminderHistory", JSONArray().apply { db.reminderHistoryDao().getAll().forEach { put(it.toJson()) } })
        put("memos", JSONArray().apply { db.memoDao().getAll().forEach { put(it.toJson()) } })
        put("settings", JSONObject().apply {
            val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            put("theme", prefs.getString("theme", "LIGHT"))
            put("hideLockContent", prefs.getBoolean("hide_lock_content", false))
        })
    }

    private suspend fun restorePayload(root: JSONObject) = db.withTransaction {
        db.taskDao().getAllEntities().forEach { AlarmScheduler(context).cancel(it) }
        db.reminderHistoryDao().deleteAll()
        db.taskTagDao().deleteAll()
        db.subTaskDao().deleteAll()
        db.taskDao().deleteAll()
        db.tagDao().deleteAll()
        db.categoryDao().deleteAll()

        root.getJSONArray("categories").objects().forEach { json ->
            db.categoryDao().insert(CategoryEntity(json.getLong("id"), json.getString("name"), json.optString("icon", "folder"), json.optInt("sortOrder")))
        }
        root.getJSONArray("tags").objects().forEach { json -> db.tagDao().insert(TagEntity(json.getLong("id"), json.getString("name"))) }
        root.getJSONArray("tasks").objects().forEach { json -> db.taskDao().insert(json.toTask()) }
        root.getJSONArray("taskTags").objects().forEach { json -> db.taskTagDao().insert(TaskTagEntity(json.getLong("taskId"), json.getLong("tagId"))) }
        root.getJSONArray("subTasks").objects().forEach { json ->
            db.subTaskDao().insert(SubTaskEntity(json.getLong("id"), json.getLong("taskId"), json.getString("title"), json.optBoolean("isCompleted"), json.optInt("sortOrder")))
        }
        root.getJSONArray("reminderHistory").objects().forEach { json ->
            db.reminderHistoryDao().insert(ReminderHistoryEntity(json.getLong("id"), json.getLong("taskId"), json.getLong("triggeredAt"), json.getString("action"), json.nullableLong("nextReminderAt")))
        }
        root.optJSONArray("memos")?.let { memos ->
            db.memoDao().deleteAll()
            memos.objects().forEach { json ->
                db.memoDao().save(MemoEntity(
                    id = json.getString("id"),
                    title = json.optString("title"),
                    content = MemoRules.limitContent(json.optString("content")),
                    createdAt = json.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = json.optLong("updatedAt", System.currentTimeMillis())
                ))
            }
        }
        root.optJSONObject("settings")?.let { settings ->
            context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
                .putString("theme", settings.optString("theme", "LIGHT"))
                .putBoolean("hide_lock_content", settings.optBoolean("hideLockContent"))
                .apply()
        }
    }

    private fun crypt(mode: Int, input: ByteArray, password: String, salt: ByteArray, iv: ByteArray): ByteArray {
        val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(password.toCharArray(), salt, 120_000, 256)).encoded
        return Cipher.getInstance("AES/GCM/NoPadding").run {
            init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
            doFinal(input)
        }
    }

    companion object {
        private const val FORMAT = "tw.knox.dailytasks.backup"
        private const val VERSION = 4
    }
}

data class BackupResult(val success: Boolean, val message: String)

private fun JSONArray.objects() = sequence { for (i in 0 until length()) yield(getJSONObject(i)) }
private fun JSONObject.nullableLong(name: String): Long? = if (isNull(name) || !has(name)) null else getLong(name)
private fun JSONObject.putNullable(name: String, value: Any?) = put(name, value ?: JSONObject.NULL)

private fun TaskEntity.toJson() = JSONObject()
    .put("id", id).put("title", title).put("note", note).put("startDateTime", startDateTime)
    .putNullable("endDateTime", endDateTime).put("priority", priority).putNullable("categoryId", categoryId)
    .put("isCompleted", isCompleted).putNullable("completedAt", completedAt).put("repeatRule", repeatRule)
    .put("reminderEnabled", reminderEnabled).putNullable("reminderAt", reminderAt).put("reminderOffsets", reminderOffsets)
    .put("repeatUntilComplete", repeatUntilComplete).put("repeatReminderInterval", repeatReminderInterval)
    .putNullable("nextReminderAt", nextReminderAt).put("ringEnabled", ringEnabled)
    .put("ringDurationSeconds", ringDurationSeconds).putNullable("ringToneUri", ringToneUri)
    .put("ringVolume", ringVolume).put("createdAt", createdAt).put("updatedAt", updatedAt)

private fun CategoryEntity.toJson() = JSONObject().put("id", id).put("name", name).put("icon", icon).put("sortOrder", sortOrder)
private fun MemoEntity.toJson() = JSONObject().put("id", id).put("title", title).put("content", content).put("createdAt", createdAt).put("updatedAt", updatedAt)
private fun TagEntity.toJson() = JSONObject().put("id", id).put("name", name)
private fun TaskTagEntity.toJson() = JSONObject().put("taskId", taskId).put("tagId", tagId)
private fun SubTaskEntity.toJson() = JSONObject().put("id", id).put("taskId", taskId).put("title", title).put("isCompleted", isCompleted).put("sortOrder", sortOrder)
private fun ReminderHistoryEntity.toJson() = JSONObject().put("id", id).put("taskId", taskId).put("triggeredAt", triggeredAt).put("action", action).putNullable("nextReminderAt", nextReminderAt)

private fun JSONObject.toTask() = TaskEntity(
    id = getLong("id"),
    title = getString("title"),
    note = optString("note"),
    startDateTime = getLong("startDateTime"),
    endDateTime = nullableLong("endDateTime"),
    priority = optString("priority", "MEDIUM"),
    categoryId = nullableLong("categoryId"),
    isCompleted = optBoolean("isCompleted"),
    completedAt = nullableLong("completedAt"),
    repeatRule = optString("repeatRule", "NONE"),
    reminderEnabled = optBoolean("reminderEnabled", true),
    reminderAt = nullableLong("reminderAt"),
    reminderOffsets = optString("reminderOffsets", ""),
    repeatUntilComplete = optBoolean("repeatUntilComplete", false),
    repeatReminderInterval = optInt("repeatReminderInterval", 10),
    nextReminderAt = nullableLong("nextReminderAt"),
    ringEnabled = optBoolean("ringEnabled", false),
    ringDurationSeconds = optInt("ringDurationSeconds", 5).coerceIn(1, 10),
    ringToneUri = if (isNull("ringToneUri") || !has("ringToneUri")) null else optString("ringToneUri").takeIf { it.isNotBlank() },
    ringVolume = optInt("ringVolume", 70).coerceIn(0, 100),
    createdAt = optLong("createdAt"),
    updatedAt = optLong("updatedAt")
)
