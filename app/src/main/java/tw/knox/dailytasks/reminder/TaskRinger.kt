package tw.knox.dailytasks.reminder

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import tw.knox.dailytasks.data.TaskEntity

object TaskRinger {
    private data class ActiveRing(val player: MediaPlayer, val audioManager: AudioManager)

    private val lock = Any()
    private val activeRings = mutableMapOf<Long, ActiveRing>()
    private var originalAlarmVolume: Int? = null

    suspend fun play(context: Context, task: TaskEntity) {
        if (!task.ringEnabled || !task.reminderEnabled || task.isCompleted) return
        withContext(Dispatchers.IO) {
            val audioManager = context.getSystemService(AudioManager::class.java)
            val uri = task.ringToneUri?.let(Uri::parse)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: return@withContext
            val player = runCatching {
                MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    setDataSource(context, uri)
                    isLooping = true
                    val volume = task.ringVolume.coerceIn(0, 100) / 100f
                    setVolume(volume, volume)
                    prepare()
                }
            }.getOrNull() ?: return@withContext

            synchronized(lock) {
                finishLocked(task.id)
                if (activeRings.isEmpty()) {
                    originalAlarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
                    runCatching {
                        audioManager.setStreamVolume(
                            AudioManager.STREAM_ALARM,
                            audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM),
                            0
                        )
                    }
                }
                activeRings[task.id] = ActiveRing(player, audioManager)
            }

            try {
                player.start()
                delay(task.ringDurationSeconds.coerceIn(1, 10) * 1_000L)
            } finally {
                synchronized(lock) { finishLocked(task.id) }
            }
        }
    }

    fun stop(taskId: Long) {
        synchronized(lock) { finishLocked(taskId) }
    }

    private fun finishLocked(taskId: Long) {
        val active = activeRings.remove(taskId) ?: return
        runCatching { active.player.stop() }
        runCatching { active.player.release() }
        if (activeRings.isEmpty()) {
            originalAlarmVolume?.let { volume ->
                runCatching { active.audioManager.setStreamVolume(AudioManager.STREAM_ALARM, volume, 0) }
            }
            originalAlarmVolume = null
        }
    }
}
