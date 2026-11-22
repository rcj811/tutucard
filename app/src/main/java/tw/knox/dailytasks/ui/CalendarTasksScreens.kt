package tw.knox.dailytasks.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import tw.knox.dailytasks.data.CategoryEntity
import tw.knox.dailytasks.data.TaskPriority
import tw.knox.dailytasks.data.TaskRules
import tw.knox.dailytasks.data.TaskWithDetails
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class CalendarMode(val label: String) { TODAY("今日"), WEEK("本週"), MONTH("月份") }

@Composable
fun CalendarScreen(
    tasks: List<TaskWithDetails>,
    onOpenTask: (Long) -> Unit,
    onToggle: (Long) -> Unit,
    onDeleteCompleted: (Long) -> Unit
) {
    var selectedDate by rememberSaveable(stateSaver = LocalDateStateSaver) { mutableStateOf(LocalDate.now()) }
    var month by rememberSaveable(stateSaver = YearMonthStateSaver) { mutableStateOf(YearMonth.from(selectedDate)) }
    var mode by rememberSaveable { mutableStateOf(CalendarMode.TODAY) }
    val selectedTasks = tasks
        .filter { it.task.startDateTime.toLocalDate() == selectedDate }
        .sortedWith(compareBy<TaskWithDetails> { it.task.isCompleted }.thenBy { it.task.startDateTime })
    val headerTitle = when (mode) {
        CalendarMode.TODAY -> selectedDate.format(DateTimeFormatter.ofPattern("M月d日 (E)", Locale.TAIWAN))
        CalendarMode.WEEK -> "本週"
        CalendarMode.MONTH -> month.format(DateTimeFormatter.ofPattern("yyyy年M月", Locale.TAIWAN))
    }

    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp)) {
        item {
            ScreenTitle(
                title = headerTitle,
                subtitle = "任務行程"
            ) {
                IconButton(onClick = {
                    selectedDate = when (mode) {
                        CalendarMode.TODAY -> selectedDate.minusDays(1)
                        CalendarMode.WEEK -> selectedDate.minusWeeks(1)
                        CalendarMode.MONTH -> month.minusMonths(1).atDay(1)
                    }
                    month = YearMonth.from(selectedDate)
                }) { Icon(Icons.Filled.ChevronLeft, contentDescription = "上一段日期") }
                IconButton(onClick = {
                    selectedDate = when (mode) {
                        CalendarMode.TODAY -> selectedDate.plusDays(1)
                        CalendarMode.WEEK -> selectedDate.plusWeeks(1)
                        CalendarMode.MONTH -> month.plusMonths(1).atDay(1)
                    }
                    month = YearMonth.from(selectedDate)
                }) { Icon(Icons.Filled.ChevronRight, contentDescription = "下一段日期") }
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CalendarMode.entries.forEach { item ->
                    FilterChip(selected = mode == item, onClick = {
                        mode = item
                        if (item == CalendarMode.TODAY) {
                            selectedDate = LocalDate.now()
                            month = YearMonth.from(selectedDate)
                        }
                    }, label = { Text(item.label) })
                }
            }
        }
        item {
            when (mode) {
                CalendarMode.TODAY -> Spacer(Modifier.height(4.dp))
                CalendarMode.MONTH -> MonthGrid(month, selectedDate, tasks) { selectedDate = it }
                CalendarMode.WEEK -> WeekStrip(selectedDate, tasks) { selectedDate = it; month = YearMonth.from(it) }
            }
        }
        item {
            Text(
                selectedDate.format(DateTimeFormatter.ofPattern("M月d日 (E)", Locale.TAIWAN)),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)
            )
        }
        if (selectedTasks.isEmpty()) {
            item { EmptyState("這天沒有任務", "按右下角新增待辦", Icons.Filled.EventAvailable) }
        } else {
            items(selectedTasks, key = { it.task.id }) { item ->
                TaskCard(
                    item = item,
                    onClick = { onOpenTask(item.task.id) },
                    onToggle = { onToggle(item.task.id) },
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
                    showTime = true,
                    allowCompletedToggle = false,
                    clickEnabled = !item.task.isCompleted || TaskRules.isPastDate(item.task.startDateTime),
                    onLongClick = if (item.task.isCompleted || TaskRules.isPastDate(item.task.startDateTime)) {
                        { onDeleteCompleted(item.task.id) }
                    } else null
                )
            }
        }
    }
}

@Composable
private fun MonthGrid(month: YearMonth, selected: LocalDate, tasks: List<TaskWithDetails>, onSelect: (LocalDate) -> Unit) {
    val weekLabels = listOf("日", "一", "二", "三", "四", "五", "六")
    val startOffset = month.atDay(1).dayOfWeek.value % 7
    val firstCell = month.atDay(1).minusDays(startOffset.toLong())
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth()) {
            weekLabels.forEach { label ->
                Text(label, modifier = Modifier.weight(1f).padding(vertical = 6.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        repeat(6) { row ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { column ->
                    val date = firstCell.plusDays((row * 7 + column).toLong())
                    val dayTasks = tasks.filter { !it.task.isCompleted && it.task.startDateTime.toLocalDate() == date }
                    CalendarDayCell(date, date == selected, date.month == month.month, dayTasks, { onSelect(date) }, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun WeekStrip(selected: LocalDate, tasks: List<TaskWithDetails>, onSelect: (LocalDate) -> Unit) {
    val start = selected.minusDays((selected.dayOfWeek.value % 7).toLong())
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
        repeat(7) { index ->
            val date = start.plusDays(index.toLong())
            val dayTasks = tasks.filter { !it.task.isCompleted && it.task.startDateTime.toLocalDate() == date }
            CalendarDayCell(date, date == selected, true, dayTasks, { onSelect(date) }, Modifier.weight(1f))
        }
    }
}

@Composable
private fun CalendarDayCell(
    date: LocalDate,
    selected: Boolean,
    inMonth: Boolean,
    tasks: List<TaskWithDetails>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.aspectRatio(1f).clickable(onClick = onClick).padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(if (selected) BrandBlue else Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            Text(date.dayOfMonth.toString(), color = if (selected) Color.White else if (inMonth) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = .35f), style = MaterialTheme.typography.bodySmall, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.height(6.dp)) {
            tasks.map { it.priority() }.distinct().take(3).forEach { Box(Modifier.size(4.dp).background(priorityColor(it), CircleShape)) }
        }
    }
}

private enum class TimeFilter(val label: String) { ALL("全部"), TODAY("今天"), TOMORROW("明天"), WEEK("本週"), MONTH("本月"), OVERDUE("已過期"), COMPLETED("已完成") }

@Composable
fun TasksScreen(
    tasks: List<TaskWithDetails>,
    categories: List<CategoryEntity>,
    onOpenTask: (Long) -> Unit,
    onToggle: (Long) -> Unit,
    onDeleteCompleted: (Long) -> Unit
) {
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var selectedCategoryId by rememberSaveable(key = "task_category_id") { mutableStateOf<Long?>(null) }
    var timeFilter by rememberSaveable { mutableStateOf(TimeFilter.ALL) }
    var priorityFilter by rememberSaveable { mutableStateOf<TaskPriority?>(null) }
    val today = LocalTaskToday.current
    val endOfWeek = today.plusDays((7 - today.dayOfWeek.value).toLong())
    val activeCategoryId = selectedCategoryId?.takeIf { id -> categories.any { it.id == id } }
    val filtered = tasks.filter { item ->
        val date = item.task.startDateTime.toLocalDate()
        val searchMatches = query.isBlank() || item.task.title.contains(query, true) || item.task.note.contains(query, true) || item.tags.any { it.name.contains(query, true) } || item.category?.name?.contains(query, true) == true
        val categoryMatches = activeCategoryId == null || item.task.categoryId == activeCategoryId
        val timeMatches = when (timeFilter) {
            TimeFilter.ALL -> true
            TimeFilter.TODAY -> date == today
            TimeFilter.TOMORROW -> date == today.plusDays(1)
            TimeFilter.WEEK -> !date.isBefore(today) && !date.isAfter(endOfWeek)
            TimeFilter.MONTH -> YearMonth.from(date) == YearMonth.from(today)
            TimeFilter.OVERDUE -> date.isBefore(today)
            TimeFilter.COMPLETED -> item.task.isCompleted
        }
        val priorityMatches = priorityFilter == null || item.priority() == priorityFilter
        searchMatches && categoryMatches && timeMatches && priorityMatches
    }

    val sorted = filtered.sortedWith(compareBy<TaskWithDetails> { it.task.isCompleted }.thenBy { it.task.startDateTime })

    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp)) {
        item {
            ScreenTitle("任務", "依日期分類，顏色區分優先順序") {
                IconButton(onClick = { searchOpen = !searchOpen }) { Icon(Icons.Filled.Search, contentDescription = "搜尋任務") }
            }
        }
        if (searchOpen) item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp),
                label = { Text("搜尋名稱、備註、標籤或分類") },
                singleLine = true
            )
        }
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = activeCategoryId == null,
                    onClick = { selectedCategoryId = null },
                    label = { Text("全部") }
                )
                categories.forEach { category ->
                    FilterChip(
                        selected = activeCategoryId == category.id,
                        onClick = { selectedCategoryId = category.id },
                        label = { Text(category.name) }
                    )
                }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TimeFilter.entries.forEach { item -> FilterChip(selected = timeFilter == item, onClick = { timeFilter = item }, label = { Text("${item.label} ${countFor(item, tasks)}") }) }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = priorityFilter == null, onClick = { priorityFilter = null }, label = { Text("全部優先") })
                TaskPriority.entries.forEach { item ->
                    FilterChip(
                        selected = priorityFilter == item,
                        onClick = { priorityFilter = item },
                        leadingIcon = { Icon(Icons.Filled.Flag, contentDescription = null, tint = priorityColor(item)) },
                        label = { Text(priorityColorName(item)) }
                    )
                }
            }
        }
        if (sorted.isEmpty()) {
            item { EmptyState("沒有符合的任務", "調整分類或搜尋條件", Icons.Filled.TaskAlt) }
        } else {
            items(sorted, key = { it.task.id }) { item ->
                TaskCard(
                    item = item,
                    onClick = { onOpenTask(item.task.id) },
                    onToggle = { onToggle(item.task.id) },
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
                    showDate = true,
                    clickEnabled = !item.task.isCompleted || TaskRules.isPastDate(item.task.startDateTime),
                    onLongClick = if (item.task.isCompleted || TaskRules.isPastDate(item.task.startDateTime)) {
                        { onDeleteCompleted(item.task.id) }
                    } else null
                )
            }
        }
    }
}

private fun countFor(filter: TimeFilter, tasks: List<TaskWithDetails>): Int {
    val today = LocalDate.now()
    val endOfWeek = today.plusDays((7 - today.dayOfWeek.value).toLong())
    return tasks.count { item ->
        val date = item.task.startDateTime.toLocalDate()
        when (filter) {
            TimeFilter.ALL -> true
            TimeFilter.TODAY -> date == today
            TimeFilter.TOMORROW -> date == today.plusDays(1)
            TimeFilter.WEEK -> !date.isBefore(today) && !date.isAfter(endOfWeek)
            TimeFilter.MONTH -> YearMonth.from(date) == YearMonth.from(today)
            TimeFilter.OVERDUE -> date.isBefore(today)
            TimeFilter.COMPLETED -> item.task.isCompleted
        }
    }
}
