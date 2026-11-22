package tw.knox.dailytasks.ui

import android.os.Build
import android.view.ViewGroup
import android.widget.NumberPicker
import tw.knox.dailytasks.data.LunarDate
import tw.knox.dailytasks.data.lunarDate
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import tw.knox.dailytasks.data.TaskWithDetails
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun RecordsScreen(
    tasks: List<TaskWithDetails>,
    onOpenTask: (Long) -> Unit,
    onToggle: (Long) -> Unit,
    onDeleteCompleted: (Long) -> Unit,
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit
) {
    var month by rememberSaveable(stateSaver = YearMonthStateSaver) { mutableStateOf(YearMonth.now()) }
    var openedDateText by rememberSaveable { mutableStateOf<String?>(null) }
    var showMonthPicker by rememberSaveable { mutableStateOf(false) }
    val openedDate = openedDateText?.let { LocalDate.parse(it) }
    val tasksByDate = remember(tasks) { tasks.groupBy { it.task.startDateTime.toLocalDate() } }
    val swipeThreshold = with(LocalDensity.current) { 48.dp.toPx() }
    val changeMonth: (YearMonth) -> Unit = { target ->
        if (target.year in 1..9999) {
            month = target
            onDateSelected(target.atDay(selectedDate.dayOfMonth.coerceAtMost(target.lengthOfMonth())))
        }
    }

    if (showMonthPicker) {
        CalendarMonthPicker(
            initial = month,
            onConfirm = { changeMonth(it); showMonthPicker = false },
            onDismiss = { showMonthPicker = false }
        )
    }

    BackHandler(enabled = openedDate != null) {
        openedDateText = null
    }

    openedDate?.let { date ->
        RecordsDayScreen(
            date = date,
            tasks = tasksByDate[date].orEmpty(),
            onBack = { openedDateText = null },
            onOpenTask = onOpenTask,
            onToggle = onToggle,
            onDeleteCompleted = onDeleteCompleted
        )
        return
    }

    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .pointerInput(month, selectedDate, swipeThreshold) {
                var distance = 0f
                detectHorizontalDragGestures(
                    onDragStart = { distance = 0f },
                    onDragCancel = { distance = 0f },
                    onDragEnd = {
                        if (distance <= -swipeThreshold) changeMonth(month.plusMonths(1))
                        else if (distance >= swipeThreshold) changeMonth(month.minusMonths(1))
                        distance = 0f
                    },
                    onHorizontalDrag = { change, amount -> change.consume(); distance += amount }
                )
            },
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp)
    ) {
        item {
            ScreenTitle("行事曆", month.format(DateTimeFormatter.ofPattern("yyyy年M月", Locale.TAIWAN)), onHeadingClick = { showMonthPicker = true }) {
                IconButton(onClick = { changeMonth(month.minusMonths(1)) }, enabled = month != YearMonth.of(1, 1)) {
                    Icon(Icons.Filled.ChevronLeft, contentDescription = "上個月")
                }
                IconButton(onClick = { changeMonth(month.plusMonths(1)) }, enabled = month != YearMonth.of(9999, 12)) {
                    Icon(Icons.Filled.ChevronRight, contentDescription = "下個月")
                }
            }
        }
        item {
            RecordsMonthGrid(
                month = month,
                selected = selectedDate,
                tasksByDate = tasksByDate,
                onSelect = { date ->
                    onDateSelected(date)
                    month = YearMonth.from(date)
                    openedDateText = date.toString()
                }
            )
        }
    }
}

@Composable
private fun RecordsDayScreen(
    date: LocalDate,
    tasks: List<TaskWithDetails>,
    onBack: () -> Unit,
    onOpenTask: (Long) -> Unit,
    onToggle: (Long) -> Unit,
    onDeleteCompleted: (Long) -> Unit
) {
    val lunar = remember(date) { lunarDate(date) }
    val today = LocalTaskToday.current
    val holiday = remember(date) { holidayName(date, lunar) }
    val darkTheme = isDarkAppTheme()
    val sortedTasks = tasks.sortedWith(compareBy<TaskWithDetails> { it.task.isCompleted }.thenBy { it.task.startDateTime })
    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回月曆")
                }
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        date.format(DateTimeFormatter.ofPattern("M月d日 (E)", Locale.TAIWAN)),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        buildString {
                            append("農曆")
                            append(lunar.fullLabel())
                            holiday?.let { append(" · "); append(it) }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (darkTheme || holiday == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                    )
                }
            }
        }
        if (sortedTasks.isEmpty()) {
            item { EmptyState("這天沒有任務", "", Icons.Filled.EventAvailable) }
        } else {
            items(sortedTasks, key = { it.task.id }) { item ->
                TaskCard(
                    item = item,
                    onClick = { onOpenTask(item.task.id) },
                    onToggle = { onToggle(item.task.id) },
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
                    showTime = true,
                    clickEnabled = !item.task.isCompleted || item.task.startDateTime.toLocalDate().isBefore(today),
                    onLongClick = if (item.task.isCompleted || item.task.startDateTime.toLocalDate().isBefore(today)) {
                        { onDeleteCompleted(item.task.id) }
                    } else null
                )
            }
        }
    }
}

@Composable
private fun CalendarMonthPicker(initial: YearMonth, onConfirm: (YearMonth) -> Unit, onDismiss: () -> Unit) {
    var year by rememberSaveable(initial) { mutableStateOf(initial.year) }
    var month by rememberSaveable(initial) { mutableStateOf(initial.monthValue) }
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("選擇年月") },
        text = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("年份")
                    AndroidView(
                        modifier = Modifier.fillMaxWidth(),
                        factory = { context -> NumberPicker(context).apply {
                            minValue = 1
                            maxValue = 9999
                            wrapSelectorWheel = false
                            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                        } },
                        update = { picker ->
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) picker.textColor = textColor
                            picker.value = year
                            picker.setOnValueChangedListener { _, _, value -> year = value }
                        }
                    )
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("月份")
                    AndroidView(
                        modifier = Modifier.fillMaxWidth(),
                        factory = { context -> NumberPicker(context).apply {
                            minValue = 1
                            maxValue = 12
                            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                        } },
                        update = { picker ->
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) picker.textColor = textColor
                            picker.value = month
                            picker.setOnValueChangedListener { _, _, value -> month = value }
                        }
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(YearMonth.of(year, month)) }) { Text("確定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun RecordsMonthGrid(
    month: YearMonth,
    selected: LocalDate,
    tasksByDate: Map<LocalDate, List<TaskWithDetails>>,
    onSelect: (LocalDate) -> Unit
) {
    val darkTheme = isDarkAppTheme()
    val weekLabels = listOf("日", "一", "二", "三", "四", "五", "六")
    val startOffset = month.atDay(1).dayOfWeek.value % 7
    val firstCell = month.atDay(1).minusDays(startOffset.toLong())
    val weekCount = (startOffset + month.lengthOfMonth() + 6) / 7
    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp)) {
        Row(Modifier.fillMaxWidth()) {
            weekLabels.forEachIndexed { index, label ->
                Text(
                    text = label,
                    modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (darkTheme) MaterialTheme.colorScheme.onSurface else if (index == 0 || index == 6) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        repeat(weekCount) { row ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { column ->
                    val date = firstCell.plusDays((row * 7 + column).toLong())
                    RecordsDayCell(
                        date = date,
                        selected = date == selected,
                        inMonth = YearMonth.from(date) == month,
                        tasks = tasksByDate[date].orEmpty(),
                        onClick = { onSelect(date) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun RecordsDayCell(
    date: LocalDate,
    selected: Boolean,
    inMonth: Boolean,
    tasks: List<TaskWithDetails>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val darkTheme = isDarkAppTheme()
    val lunar = remember(date) { lunarDate(date) }
    val holiday = remember(date) { holidayName(date, lunar) }
    val normalColor = MaterialTheme.colorScheme.onSurface.copy(alpha = if (inMonth) 1f else 0.35f)
    val secondaryColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (inMonth) 1f else 0.35f)
    val selectedBackground = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val lunarLabel = lunar.cellLabel()
    val compactLabelLength = lunarLabel.length + (holiday?.length ?: 0)
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val compactBaseSize = when {
        compactLabelLength <= 5 -> 8f
        compactLabelLength <= 8 -> 7f
        else -> 6f
    }
    val compactFontSize = (compactBaseSize / fontScale).sp
    val compactLineHeight = (9f / fontScale).sp
    Column(
        modifier = modifier
            .padding(1.dp)
            .heightIn(min = 54.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(selectedBackground)
            .clickable(enabled = date.year in 1..9999, onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            date.dayOfMonth.toString(),
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else normalColor,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else secondaryColor)) {
                    append(lunarLabel)
                }
                holiday?.let {
                    append(" ")
                    withStyle(SpanStyle(color = if (darkTheme) secondaryColor else MaterialTheme.colorScheme.error.copy(alpha = if (inMonth) 1f else 0.45f))) {
                        append(it)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            fontSize = compactFontSize,
            lineHeight = compactLineHeight,
            letterSpacing = 0.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Row(
            modifier = Modifier.height(8.dp).padding(top = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tasks.map { it.priority() }.distinct().take(3).forEach { priority ->
                Box(
                    Modifier.size(6.dp).background(
                        priorityColor(priority).copy(alpha = if (inMonth) 1f else 0.4f),
                        CircleShape
                    )
                )
            }
        }
    }
}

private fun holidayName(date: LocalDate, lunar: LunarDate): String? {
    val names = mutableListOf<String>()
    when (date.monthValue to date.dayOfMonth) {
        1 to 1 -> names += "元旦"
        2 to 28 -> names += "和平紀念日"
        4 to 4 -> names += "兒童節"
        5 to 1 -> names += "勞動節"
        9 to 28 -> names += "教師節"
        10 to 10 -> names += "國慶日"
        10 to 25 -> names += "臺灣光復節"
        12 to 25 -> names += "行憲紀念日"
    }
    if (date.monthValue == 4 && date.dayOfMonth == qingmingDay(date.year)) names += "清明節"
    if (!lunar.isLeapMonth) {
        when (lunar.month to lunar.day) {
            1 to 1, 1 to 2, 1 to 3 -> names += "春節"
            1 to 15 -> names += "元宵節"
            5 to 5 -> names += "端午節"
            7 to 7 -> names += "七夕"
            7 to 15 -> names += "中元節"
            8 to 15 -> names += "中秋節"
            9 to 9 -> names += "重陽節"
        }
    }
    val tomorrow = lunarDate(date.plusDays(1))
    if (tomorrow.month == 1 && tomorrow.day == 1 && !tomorrow.isLeapMonth) names += "除夕"
    return names.distinct().joinToString("/").ifBlank { null }
}

private fun qingmingDay(year: Int): Int {
    if (year !in 2000..2099) return 4
    val shortYear = year % 100
    return (shortYear * 0.2422 + 4.81).toInt() - shortYear / 4
}
