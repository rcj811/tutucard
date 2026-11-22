package tw.knox.dailytasks.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** 設定頁顯示的簡易完成統計。 */
data class TaskStats(
    val weekDone: Int,
    val weekTotal: Int,
    val monthDone: Int,
    val monthTotal: Int,
    val overdue: Int
) {
    val weekRate: Int get() = rate(weekDone, weekTotal)
    val monthRate: Int get() = rate(monthDone, monthTotal)

    private fun rate(done: Int, total: Int) = if (total == 0) 0 else (done * 100 + total / 2) / total

    companion object {
        /** 本週（週一到週日）、本月的任務完成數，以及過去日期仍未完成的數量。 */
        fun compute(tasks: List<TaskEntity>, today: LocalDate = LocalDate.now(), zoneId: ZoneId = ZoneId.systemDefault()): TaskStats {
            val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val weekEnd = weekStart.plusDays(6)
            val monthStart = today.withDayOfMonth(1)
            val monthEnd = today.withDayOfMonth(today.lengthOfMonth())
            var weekDone = 0; var weekTotal = 0; var monthDone = 0; var monthTotal = 0; var overdue = 0
            tasks.forEach { task ->
                val date = Instant.ofEpochMilli(task.startDateTime).atZone(zoneId).toLocalDate()
                if (!date.isBefore(weekStart) && !date.isAfter(weekEnd)) {
                    weekTotal++
                    if (task.isCompleted) weekDone++
                }
                if (!date.isBefore(monthStart) && !date.isAfter(monthEnd)) {
                    monthTotal++
                    if (task.isCompleted) monthDone++
                }
                if (!task.isCompleted && date.isBefore(today)) overdue++
            }
            return TaskStats(weekDone, weekTotal, monthDone, monthTotal, overdue)
        }
    }
}
