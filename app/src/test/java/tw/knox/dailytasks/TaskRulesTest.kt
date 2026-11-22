package tw.knox.dailytasks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import tw.knox.dailytasks.data.TaskRepeat
import tw.knox.dailytasks.data.TaskRules
import java.time.LocalDateTime
import java.time.ZoneId

class TaskRulesTest {
    private val zone = ZoneId.of("Asia/Taipei")

    @Test fun noneDoesNotCreateOccurrence() {
        assertNull(TaskRules.nextOccurrence(0, TaskRepeat.NONE, zone))
    }

    @Test fun monthlyRepeatKeepsLocalClockAndClampsDay() {
        val original = LocalDateTime.of(2026, 1, 31, 9, 30).atZone(zone).toInstant().toEpochMilli()
        val actual = TaskRules.nextOccurrence(original, TaskRepeat.MONTHLY, zone)
        val expected = LocalDateTime.of(2026, 2, 28, 9, 30).atZone(zone).toInstant().toEpochMilli()
        assertEquals(expected, actual)
    }

    @Test fun reminderOffsetIsIndependentFromTaskRepeat() {
        assertEquals(9_400_000L, TaskRules.reminderAt(10_000_000L, 10))
    }

    @Test fun customWeekdaysAdvanceThenSkipConfiguredWeeks() {
        val monday = LocalDateTime.of(2026, 9, 28, 9, 0).atZone(zone).toInstant().toEpochMilli()
        val wednesday = LocalDateTime.of(2026, 9, 30, 9, 0).atZone(zone).toInstant().toEpochMilli()
        val friday = LocalDateTime.of(2026, 10, 2, 9, 0).atZone(zone).toInstant().toEpochMilli()
        val nextMondayTwoWeeks = LocalDateTime.of(2026, 10, 12, 9, 0).atZone(zone).toInstant().toEpochMilli()
        val rule = TaskRules.encodeRepeat(TaskRepeat.CUSTOM, 2, setOf(1, 3, 5))
        assertEquals(wednesday, TaskRules.nextOccurrence(monday, rule, zone))
        assertEquals(nextMondayTwoWeeks, TaskRules.nextOccurrence(friday, rule, zone))
    }
}
