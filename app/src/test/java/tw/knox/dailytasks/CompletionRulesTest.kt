package tw.knox.dailytasks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tw.knox.dailytasks.data.TaskEntity
import tw.knox.dailytasks.data.TaskRepeat
import tw.knox.dailytasks.data.TaskRules
import java.time.LocalDateTime
import java.time.ZoneId

class CompletionRulesTest {
    private val zone = ZoneId.systemDefault()
    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int = 0) =
        LocalDateTime.of(y, m, d, h, min).atZone(zone).toInstant().toEpochMilli()
    private fun task(start: Long, completed: Boolean = false, repeat: String = TaskRepeat.NONE.name) =
        TaskEntity(title = "t", startDateTime = start, isCompleted = completed, repeatRule = repeat)

    private val now = at(2026, 10, 23, 15, 0)

    @Test fun futureTaskNeedsEarlyCompleteSetting() {
        val later = task(at(2026, 10, 23, 18))
        assertFalse(TaskRules.canComplete(later, now, allowEarly = false))
        assertTrue(TaskRules.canComplete(later, now, allowEarly = true))
    }

    @Test fun dueTaskCanComplete() {
        assertTrue(TaskRules.canComplete(task(at(2026, 10, 23, 9)), now, allowEarly = false))
    }

    @Test fun overdueTaskCanBeCompletedLate() {
        assertTrue(TaskRules.canComplete(task(at(2026, 10, 20, 9)), now, allowEarly = false))
    }

    @Test fun completedTaskCannotCompleteAgain() {
        assertFalse(TaskRules.canComplete(task(at(2026, 10, 23, 9), completed = true), now, allowEarly = true))
    }

    @Test fun onlyPastPendingTasksCanMoveToToday() {
        assertTrue(TaskRules.canMoveToToday(task(at(2026, 10, 20, 9)), now))
        assertFalse(TaskRules.canMoveToToday(task(at(2026, 10, 23, 9)), now))
        assertFalse(TaskRules.canMoveToToday(task(at(2026, 10, 20, 9), completed = true), now))
    }

    @Test fun moveToTodayKeepsTimeWhenStillAhead() {
        assertEquals(at(2026, 10, 23, 18, 30), TaskRules.moveToTodayStart(at(2026, 10, 20, 18, 30), now, zone))
    }

    @Test fun moveToTodayUsesTenMinutesLaterWhenTimePassed() {
        assertEquals(at(2026, 10, 23, 15, 10), TaskRules.moveToTodayStart(at(2026, 10, 20, 9, 0), now, zone))
    }

    @Test fun skipOnlyForPendingRepeatTasks() {
        assertTrue(TaskRules.canSkip(task(now, repeat = TaskRepeat.DAILY.name)))
        assertTrue(TaskRules.canSkip(task(now, repeat = TaskRules.encodeRepeat(TaskRepeat.CUSTOM, 1, setOf(1)))))
        assertFalse(TaskRules.canSkip(task(now)))
        assertFalse(TaskRules.canSkip(task(now, completed = true, repeat = TaskRepeat.DAILY.name)))
    }

    @Test fun nextOccurrenceFromJumpsPastOldDates() {
        val old = at(2026, 10, 18, 9)
        val todayStart = TaskRules.startOfToday(now, zone)
        assertEquals(at(2026, 10, 23, 9), TaskRules.nextOccurrenceFrom(old, TaskRepeat.DAILY.name, todayStart, zone))
        assertEquals(at(2026, 10, 25, 9), TaskRules.nextOccurrenceFrom(old, TaskRepeat.WEEKLY.name, todayStart, zone))
    }

    @Test fun nextOccurrenceFromNoneIsNull() {
        assertNull(TaskRules.nextOccurrenceFrom(now, TaskRepeat.NONE.name, 0L, zone))
    }
}
