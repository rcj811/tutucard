package tw.knox.dailytasks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import tw.knox.dailytasks.data.QuickAddParser
import tw.knox.dailytasks.data.TaskRepeat
import java.time.LocalDate
import java.time.LocalTime

class QuickAddParserTest {
    // 2026-10-23 是星期五
    private val today = LocalDate.of(2026, 10, 23)

    @Test fun tomorrowAfternoon() {
        val r = QuickAddParser.parse("明天下午三點提醒我打電話給王先生", today)
        assertEquals("打電話給王先生", r.title)
        assertEquals(LocalDate.of(2026, 10, 24), r.date)
        assertEquals(LocalTime.of(15, 0), r.time)
        assertEquals(TaskRepeat.NONE, r.repeat)
    }

    @Test fun tomorrowNineMeeting() {
        val r = QuickAddParser.parse("明天 9 點開會", today)
        assertEquals("開會", r.title)
        assertEquals(LocalDate.of(2026, 10, 24), r.date)
        assertEquals(LocalTime.of(9, 0), r.time)
    }

    @Test fun weekdayWithoutTime() {
        val r = QuickAddParser.parse("星期一繳電費", today)
        assertEquals("繳電費", r.title)
        assertEquals(LocalDate.of(2026, 10, 26), r.date)
        assertNull(r.time)
    }

    @Test fun monthlyDay() {
        val r = QuickAddParser.parse("每月 5 號繳房租", today)
        assertEquals("繳房租", r.title)
        assertEquals(TaskRepeat.MONTHLY, r.repeat)
        assertEquals(LocalDate.of(2026, 11, 5), r.date)
    }

    @Test fun weeklyMonday() {
        val r = QuickAddParser.parse("每週一 10 點會議", today)
        assertEquals("會議", r.title)
        assertEquals(TaskRepeat.CUSTOM, r.repeat)
        assertEquals(setOf(1), r.weekdays)
        assertEquals(LocalDate.of(2026, 10, 26), r.date)
        assertEquals(LocalTime.of(10, 0), r.time)
    }

    @Test fun slashDateAndClock() {
        val r = QuickAddParser.parse("10/25 9:30 看牙醫", today)
        assertEquals("看牙醫", r.title)
        assertEquals(LocalDate.of(2026, 10, 25), r.date)
        assertEquals(LocalTime.of(9, 30), r.time)
    }

    @Test fun pastMonthDayRollsToNextYear() {
        val r = QuickAddParser.parse("3月1日 繳稅", today)
        assertEquals(LocalDate.of(2027, 3, 1), r.date)
        assertEquals("繳稅", r.title)
    }

    @Test fun eveningHalfHour() {
        val r = QuickAddParser.parse("今天晚上七點半 運動", today)
        assertEquals(LocalTime.of(19, 30), r.time)
        assertEquals(today, r.date)
        assertEquals("運動", r.title)
    }

    @Test fun plainTextStaysAsTitle() {
        val r = QuickAddParser.parse("買牛奶", today)
        assertEquals("買牛奶", r.title)
        assertNull(r.date)
        assertNull(r.time)
    }

    @Test fun chineseNumbers() {
        assertEquals(10, QuickAddParser.number("十"))
        assertEquals(15, QuickAddParser.number("十五"))
        assertEquals(21, QuickAddParser.number("二十一"))
        assertEquals(2, QuickAddParser.number("兩"))
        assertEquals(12, QuickAddParser.number("１２"))
    }
}
