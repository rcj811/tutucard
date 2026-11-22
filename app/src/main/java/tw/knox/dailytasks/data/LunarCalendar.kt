package tw.knox.dailytasks.data

import android.icu.util.Calendar
import android.icu.util.ChineseCalendar
import java.time.LocalDate
import java.time.ZoneId

internal data class LunarDate(val month: Int, val day: Int, val isLeapMonth: Boolean) {
    fun cellLabel(): String = if (day == 1) {
        "${if (isLeapMonth) "閏" else ""}${lunarMonthName(month)}月"
    } else {
        lunarDayName(day)
    }

    fun fullLabel(): String = "${if (isLeapMonth) "閏" else ""}${lunarMonthName(month)}月${lunarDayName(day)}"
}

internal fun lunarDate(date: LocalDate): LunarDate {
    val calendar = ChineseCalendar().apply {
        timeInMillis = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
    return LunarDate(
        month = calendar.get(Calendar.MONTH) + 1,
        day = calendar.get(Calendar.DAY_OF_MONTH),
        isLeapMonth = calendar.get(ChineseCalendar.IS_LEAP_MONTH) == 1
    )
}

private fun lunarMonthName(month: Int): String = listOf("正", "二", "三", "四", "五", "六", "七", "八", "九", "十", "冬", "臘")[month - 1]

private fun lunarDayName(day: Int): String {
    val digits = listOf("", "一", "二", "三", "四", "五", "六", "七", "八", "九", "十")
    return when {
        day <= 10 -> "初${digits[day]}"
        day < 20 -> "十${digits[day - 10]}"
        day == 20 -> "二十"
        day < 30 -> "廿${digits[day - 20]}"
        else -> "三十"
    }
}
