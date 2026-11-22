package tw.knox.dailytasks.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

/** 中文快速新增解析結果；未辨識的欄位為 null，由編輯畫面保留原值。 */
data class QuickAddResult(
    val title: String,
    val date: LocalDate? = null,
    val time: LocalTime? = null,
    val repeat: TaskRepeat = TaskRepeat.NONE,
    val weekdays: Set<Int> = emptySet()
)

/**
 * 本機規則式解析，不需網路。支援例：
 * 「明天下午3點開會」「星期五繳電費」「每月5號繳房租」「每週一 10 點會議」「10/25 9:30 看牙醫」
 */
object QuickAddParser {
    private const val NUM = "[0-9０-９零〇一二兩三四五六七八九十]+"
    private const val WEEKDAY = "[一二三四五六日天]"
    private val weekPrefix = "(?:週|周|星期|禮拜)"

    fun parse(input: String, today: LocalDate = LocalDate.now()): QuickAddResult {
        var text = input.trim()
        var date: LocalDate? = null
        var time: LocalTime? = null
        var repeat = TaskRepeat.NONE
        var weekdays = emptySet<Int>()

        fun take(regex: Regex, handle: (MatchResult) -> Boolean) {
            val match = regex.find(text) ?: return
            if (handle(match)) text = text.replaceRange(match.range, " ")
        }

        // 重複
        take(Regex("每(?:天|日)")) { repeat = TaskRepeat.DAILY; true }
        if (repeat == TaskRepeat.NONE) take(Regex("每\\s*$weekPrefix\\s*($WEEKDAY)")) { m ->
            val day = weekdayOf(m.groupValues[1])
            repeat = TaskRepeat.CUSTOM
            weekdays = setOf(day)
            date = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.of(day)))
            true
        }
        if (repeat == TaskRepeat.NONE) take(Regex("每\\s*月\\s*($NUM)\\s*[號号日]")) { m ->
            val day = number(m.groupValues[1]) ?: return@take false
            if (day !in 1..31) return@take false
            repeat = TaskRepeat.MONTHLY
            date = dayOfMonthOnOrAfter(today, day)
            true
        }
        if (repeat == TaskRepeat.NONE) take(Regex("每\\s*(?:週|周|星期|禮拜)")) { repeat = TaskRepeat.WEEKLY; true }
        if (repeat == TaskRepeat.NONE) take(Regex("每\\s*月")) { repeat = TaskRepeat.MONTHLY; true }
        if (repeat == TaskRepeat.NONE) take(Regex("每\\s*年")) { repeat = TaskRepeat.YEARLY; true }

        // 日期
        if (date == null) take(Regex("大後天|後天|明天|明日|今天|今日")) { m ->
            date = when (m.value) {
                "大後天" -> today.plusDays(3)
                "後天" -> today.plusDays(2)
                "明天", "明日" -> today.plusDays(1)
                else -> today
            }
            true
        }
        if (date == null) take(Regex("下\\s*$weekPrefix\\s*($WEEKDAY)")) { m ->
            val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(1)
            date = monday.plusDays((weekdayOf(m.groupValues[1]) - 1).toLong())
            true
        }
        if (date == null) take(Regex("$weekPrefix\\s*($WEEKDAY)")) { m ->
            date = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.of(weekdayOf(m.groupValues[1]))))
            true
        }
        if (date == null) take(Regex("($NUM)\\s*月\\s*($NUM)\\s*[號号日]?")) { m ->
            val month = number(m.groupValues[1]) ?: return@take false
            val day = number(m.groupValues[2]) ?: return@take false
            date = monthDayOnOrAfter(today, month, day) ?: return@take false
            true
        }
        if (date == null) take(Regex("(?<![0-9:：])([0-9]{1,2})/([0-9]{1,2})(?![0-9])")) { m ->
            date = monthDayOnOrAfter(today, m.groupValues[1].toInt(), m.groupValues[2].toInt()) ?: return@take false
            true
        }
        if (date == null) take(Regex("($NUM)\\s*[號号]")) { m ->
            val day = number(m.groupValues[1]) ?: return@take false
            if (day !in 1..31) return@take false
            date = dayOfMonthOnOrAfter(today, day)
            true
        }

        // 時間
        val period = "(早上|上午|中午|下午|晚上|傍晚|凌晨)?"
        take(Regex("$period\\s*([0-9]{1,2})\\s*[:：]\\s*([0-9]{2})")) { m ->
            time = toTime(m.groupValues[1], m.groupValues[2].toInt(), m.groupValues[3].toInt()) ?: return@take false
            true
        }
        if (time == null) take(Regex("$period\\s*($NUM)\\s*[點点时時](?:\\s*(半)|\\s*($NUM)\\s*分?)?")) { m ->
            val hour = number(m.groupValues[2]) ?: return@take false
            val minute = when {
                m.groupValues[3].isNotEmpty() -> 30
                m.groupValues[4].isNotEmpty() -> number(m.groupValues[4]) ?: return@take false
                else -> 0
            }
            time = toTime(m.groupValues[1], hour, minute) ?: return@take false
            true
        }

        val title = text
            .replace(Regex("提醒我|記得|要去"), " ")
            .replace(Regex("\\s+"), " ")
            .trim(' ', '，', ',', '。', '、', '的', '在', '：', ':')
            .ifBlank { input.trim() }
        return QuickAddResult(title, date, time, repeat, weekdays)
    }

    private fun weekdayOf(label: String): Int = when (label) {
        "一" -> 1; "二" -> 2; "三" -> 3; "四" -> 4; "五" -> 5; "六" -> 6; else -> 7
    }

    private fun toTime(period: String, hour: Int, minute: Int): LocalTime? {
        if (minute !in 0..59) return null
        var h = hour
        when (period) {
            "下午", "晚上", "傍晚" -> if (h in 1..11) h += 12
            "中午" -> if (h in 1..10) h += 12
            "凌晨" -> if (h == 12) h = 0
        }
        if (h == 24) h = 0
        if (h !in 0..23) return null
        return LocalTime.of(h, minute)
    }

    private fun dayOfMonthOnOrAfter(today: LocalDate, day: Int): LocalDate {
        val thisMonth = today.withDayOfMonth(day.coerceAtMost(today.lengthOfMonth()))
        if (!thisMonth.isBefore(today)) return thisMonth
        val next = today.plusMonths(1)
        return next.withDayOfMonth(day.coerceAtMost(next.lengthOfMonth()))
    }

    private fun monthDayOnOrAfter(today: LocalDate, month: Int, day: Int): LocalDate? {
        if (month !in 1..12 || day !in 1..31) return null
        val candidate = runCatching { LocalDate.of(today.year, month, day) }.getOrNull() ?: return null
        return if (candidate.isBefore(today)) runCatching { LocalDate.of(today.year + 1, month, day) }.getOrNull() else candidate
    }

    /** 阿拉伯數字（含全形）或一～九十九的中文數字。 */
    internal fun number(raw: String): Int? {
        val text = raw.map { if (it in '０'..'９') '0' + (it - '０') else it }.joinToString("")
        text.toIntOrNull()?.let { return it }
        val digits = mapOf('零' to 0, '〇' to 0, '一' to 1, '二' to 2, '兩' to 2, '三' to 3, '四' to 4, '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9)
        val tenIndex = text.indexOf('十')
        if (tenIndex < 0) return if (text.length == 1) digits[text[0]] else null
        val tens = if (tenIndex == 0) 1 else digits[text[0]]?.takeIf { tenIndex == 1 } ?: return null
        val unitsText = text.substring(tenIndex + 1)
        val units = when (unitsText.length) {
            0 -> 0
            1 -> digits[unitsText[0]] ?: return null
            else -> return null
        }
        return tens * 10 + units
    }
}
