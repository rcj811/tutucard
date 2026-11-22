package tw.knox.dailytasks

import org.junit.Assert.assertEquals
import org.junit.Test
import tw.knox.dailytasks.data.TaskEntity
import tw.knox.dailytasks.data.TaskStats
import java.time.LocalDate
import java.time.ZoneId

class TaskStatsTest {
    private val zone = ZoneId.of("Asia/Taipei")
    private fun on(date: LocalDate, done: Boolean) =
        TaskEntity(title = "t", startDateTime = date.atTime(9, 0).atZone(zone).toInstant().toEpochMilli(), isCompleted = done)

    @Test fun countsWeekMonthAndOverdue() {
        val today = LocalDate.of(2026, 10, 23) // 週五，本週 10/19–10/25
        val tasks = listOf(
            on(LocalDate.of(2026, 10, 19), true),
            on(LocalDate.of(2026, 10, 20), false),  // 逾期
            on(LocalDate.of(2026, 10, 23), true),
            on(LocalDate.of(2026, 10, 25), false),
            on(LocalDate.of(2026, 10, 2), true),
            on(LocalDate.of(2026, 9, 30), false)    // 逾期，上個月
        )
        val stats = TaskStats.compute(tasks, today, zone)
        assertEquals(2, stats.weekDone)
        assertEquals(4, stats.weekTotal)
        assertEquals(50, stats.weekRate)
        assertEquals(3, stats.monthDone)
        assertEquals(5, stats.monthTotal)
        assertEquals(2, stats.overdue)
    }

    @Test fun emptyIsZeroPercent() {
        assertEquals(0, TaskStats.compute(emptyList(), LocalDate.of(2026, 10, 23), zone).weekRate)
    }
}
