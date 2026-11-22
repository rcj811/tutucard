package tw.knox.dailytasks.data

import java.time.Instant
import java.time.ZoneId

object TaskRules {
    /** 設定頁「允許提前完成」，App 啟動時由 DailyTasksApp 載入。 */
    @Volatile var allowEarlyComplete: Boolean = false

    fun startOfToday(now: Long = System.currentTimeMillis(), zoneId: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochMilli(now).atZone(zoneId).toLocalDate().atStartOfDay(zoneId).toInstant().toEpochMilli()

    fun isPastDate(startAt: Long, now: Long = System.currentTimeMillis()): Boolean =
        startAt < startOfToday(now)

    fun canEdit(task: TaskEntity, now: Long = System.currentTimeMillis()): Boolean =
        !task.isCompleted && !isPastDate(task.startDateTime, now)

    /** 未完成即可勾選；過去日期可補完成。未到時間的任務需開啟「允許提前完成」。 */
    fun canComplete(task: TaskEntity, now: Long = System.currentTimeMillis(), allowEarly: Boolean = allowEarlyComplete): Boolean =
        !task.isCompleted && (allowEarly || task.startDateTime <= now)

    fun canMoveToToday(task: TaskEntity, now: Long = System.currentTimeMillis()): Boolean =
        !task.isCompleted && isPastDate(task.startDateTime, now)

    fun isRepeating(rule: String): Boolean =
        parseCustom(rule) != null || runCatching { TaskRepeat.valueOf(rule) }.getOrDefault(TaskRepeat.NONE) != TaskRepeat.NONE

    fun canSkip(task: TaskEntity): Boolean = !task.isCompleted && isRepeating(task.repeatRule)

    /** 移到今天：保留原時間；若今天該時間已過，改為現在起 10 分鐘後。 */
    fun moveToTodayStart(startAt: Long, now: Long = System.currentTimeMillis(), zoneId: ZoneId = ZoneId.systemDefault()): Long {
        val nowAt = Instant.ofEpochMilli(now).atZone(zoneId)
        val time = Instant.ofEpochMilli(startAt).atZone(zoneId).toLocalTime().withSecond(0).withNano(0)
        val sameTime = nowAt.toLocalDate().atTime(time).atZone(zoneId)
        if (!sameTime.toInstant().isBefore(nowAt.toInstant())) return sameTime.toInstant().toEpochMilli()
        val soon = nowAt.plusMinutes(10).withSecond(0).withNano(0)
        return if (soon.toLocalDate() == nowAt.toLocalDate()) soon.toInstant().toEpochMilli() else sameTime.toInstant().toEpochMilli()
    }

    /** 下一期日期，若仍早於 notBefore 則持續往後推（避免補完成舊任務時產生更多過去任務）。 */
    fun nextOccurrenceFrom(epochMillis: Long, rule: String, notBefore: Long, zoneId: ZoneId = ZoneId.systemDefault()): Long? {
        var next = nextOccurrence(epochMillis, rule, zoneId) ?: return null
        var guard = 0
        while (next < notBefore && guard++ < 5000) {
            next = nextOccurrence(next, rule, zoneId) ?: return null
        }
        return next
    }

    fun nextOccurrence(epochMillis: Long, repeat: TaskRepeat, zoneId: ZoneId = ZoneId.systemDefault()): Long? {
        if (repeat == TaskRepeat.NONE) return null
        val dateTime = Instant.ofEpochMilli(epochMillis).atZone(zoneId)
        val next = when (repeat) {
            TaskRepeat.DAILY -> dateTime.plusDays(1)
            TaskRepeat.WEEKLY -> dateTime.plusWeeks(1)
            TaskRepeat.MONTHLY -> dateTime.plusMonths(1)
            TaskRepeat.YEARLY -> dateTime.plusYears(1)
            TaskRepeat.CUSTOM -> return null
            TaskRepeat.NONE -> return null
        }
        return next.toInstant().toEpochMilli()
    }

    fun reminderAt(startAt: Long, minutesBefore: Int): Long = startAt - minutesBefore.coerceAtLeast(0) * 60_000L

    fun encodeReminderOffsets(offsets: Set<Int>): String = offsets
        .filter { it >= 0 }
        .distinct()
        .sortedDescending()
        .joinToString(",")

    fun parseReminderOffsets(value: String): Set<Int> = value
        .split(",")
        .mapNotNull(String::toIntOrNull)
        .filter { it >= 0 }
        .toSet()

    fun reminderOffsets(task: TaskEntity): Set<Int> {
        val stored = parseReminderOffsets(task.reminderOffsets)
        if (stored.isNotEmpty()) return stored
        if (!task.reminderEnabled) return emptySet()
        return task.reminderAt?.let { setOf(((task.startDateTime - it) / 60_000L).coerceAtLeast(0).toInt()) }.orEmpty()
    }

    fun reminderTimes(task: TaskEntity): List<Long> = reminderOffsets(task)
        .map { reminderAt(task.startDateTime, it) }
        .distinct()
        .sorted()

    fun encodeRepeat(repeat: TaskRepeat, intervalWeeks: Int = 1, weekdays: Set<Int> = emptySet()): String {
        if (repeat != TaskRepeat.CUSTOM) return repeat.name
        val days = weekdays.filter { it in 1..7 }.sorted().ifEmpty { listOf(1) }
        return "CUSTOM:${intervalWeeks.coerceIn(1, 12)}:${days.joinToString(",")}" 
    }

    fun parseCustom(rule: String): CustomRepeat? {
        if (!rule.startsWith("CUSTOM:")) return null
        val parts = rule.split(":")
        val interval = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(1, 12) ?: 1
        val days = parts.getOrNull(2)?.split(",")?.mapNotNull(String::toIntOrNull)?.filter { it in 1..7 }?.toSet().orEmpty()
        return CustomRepeat(interval, days.ifEmpty { setOf(1) })
    }

    fun nextOccurrence(epochMillis: Long, rule: String, zoneId: ZoneId = ZoneId.systemDefault()): Long? {
        val custom = parseCustom(rule)
        if (custom == null) {
            val repeat = runCatching { TaskRepeat.valueOf(rule) }.getOrDefault(TaskRepeat.NONE)
            return nextOccurrence(epochMillis, repeat, zoneId)
        }
        val current = Instant.ofEpochMilli(epochMillis).atZone(zoneId)
        val currentDay = current.dayOfWeek.value
        val sortedDays = custom.weekdays.sorted()
        val nextDay = sortedDays.firstOrNull { it > currentDay }
        val daysToAdd = if (nextDay != null) {
            nextDay - currentDay
        } else {
            custom.intervalWeeks * 7 - (currentDay - sortedDays.first())
        }
        return current.plusDays(daysToAdd.toLong()).toInstant().toEpochMilli()
    }
}

data class CustomRepeat(val intervalWeeks: Int, val weekdays: Set<Int>)
