package tw.knox.dailytasks.ui

import android.app.DatePickerDialog
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.widget.NumberPicker
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import tw.knox.dailytasks.data.CategoryEntity
import tw.knox.dailytasks.data.QuickAddParser
import tw.knox.dailytasks.data.ReminderHistoryEntity
import tw.knox.dailytasks.data.TaskDraft
import tw.knox.dailytasks.data.TaskPriority
import tw.knox.dailytasks.data.TaskRepeat
import tw.knox.dailytasks.data.TaskRules
import tw.knox.dailytasks.data.TaskWithDetails
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun TaskEditorScreen(
    existing: TaskWithDetails?,
    categories: List<CategoryEntity>,
    onClose: () -> Unit,
    onSave: (TaskDraft) -> Unit,
    isSaving: Boolean = false,
    initialDate: LocalDate? = null
) {
    val context = LocalContext.current
    val darkTheme = isDarkAppTheme()
    val zone = ZoneId.systemDefault()
    val today = LocalTaskToday.current
    if (existing != null && (existing.task.isCompleted || existing.task.startDateTime.toLocalDate().isBefore(today))) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().padding(24.dp)) {
            Text("此任務僅可查閱或刪除，無法修改。", style = MaterialTheme.typography.bodyLarge)
            TextButton(onClick = onClose) { Text("返回預覽") }
        }
        return
    }
    val initial = remember(existing?.task?.id, initialDate) {
        existing?.task?.startDateTime?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDateTime() }
            ?: LocalDateTime.now().plusMinutes(10).withSecond(0).withNano(0).let {
                if (initialDate == null) it else initialDate.atTime(it.toLocalTime())
            }
    }
    var title by remember(existing?.task?.id) { mutableStateOf(existing?.task?.title.orEmpty()) }
    var note by remember(existing?.task?.id) { mutableStateOf(existing?.task?.note.orEmpty()) }
    var date by remember(existing?.task?.id) { mutableStateOf(initial.toLocalDate()) }
    var startTime by remember(existing?.task?.id) { mutableStateOf(initial.toLocalTime()) }
    var endTime by remember(existing?.task?.id) {
        mutableStateOf(existing?.task?.endDateTime?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalTime() } ?: initial.toLocalTime().plusHours(1))
    }
    var priority by remember(existing?.task?.id) { mutableStateOf(existing?.priority() ?: TaskPriority.HIGH) }
    var categoryId by remember(existing?.task?.id) { mutableStateOf(existing?.task?.categoryId ?: categories.firstOrNull()?.id) }
    val initialCustomRepeat = TaskRules.parseCustom(existing?.task?.repeatRule.orEmpty())
    var repeat by remember(existing?.task?.id) {
        mutableStateOf(if (initialCustomRepeat != null) TaskRepeat.CUSTOM else runCatching { TaskRepeat.valueOf(existing?.task?.repeatRule ?: "NONE") }.getOrDefault(TaskRepeat.NONE))
    }
    var customRepeatInterval by remember(existing?.task?.id) { mutableStateOf(initialCustomRepeat?.intervalWeeks ?: 1) }
    var customRepeatDays by remember(existing?.task?.id) { mutableStateOf(initialCustomRepeat?.weekdays ?: setOf(initial.dayOfWeek.value)) }
    var reminderOffsets by remember(existing?.task?.id) {
        mutableStateOf(
            if (existing == null) setOf(0)
            else if (existing.task.reminderEnabled) TaskRules.reminderOffsets(existing.task)
            else emptySet()
        )
    }
    val reminderEnabled = reminderOffsets.isNotEmpty()
    var repeatUntilComplete by remember(existing?.task?.id) {
        mutableStateOf((existing?.task?.repeatUntilComplete ?: false) && (existing?.task?.reminderEnabled ?: true))
    }
    var repeatInterval by remember(existing?.task?.id) { mutableStateOf(existing?.task?.repeatReminderInterval ?: 10) }
    var ringEnabled by remember(existing?.task?.id) { mutableStateOf(existing?.task?.ringEnabled ?: false) }
    var ringDurationSeconds by remember(existing?.task?.id) { mutableStateOf(existing?.task?.ringDurationSeconds ?: 5) }
    var ringToneUri by remember(existing?.task?.id) { mutableStateOf(existing?.task?.ringToneUri) }
    var ringVolume by remember(existing?.task?.id) { mutableStateOf(existing?.task?.ringVolume ?: 70) }
    var quickText by remember(existing?.task?.id) { mutableStateOf("") }
    var dialog by remember { mutableStateOf<String?>(null) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showRingSettings by remember { mutableStateOf(false) }
    val ringtonePicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        @Suppress("DEPRECATION")
        val picked = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        if (picked != null) ringToneUri = picked.toString()
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose, enabled = !isSaving) { Icon(Icons.Filled.Close, contentDescription = "取消") }
            Text(if (existing == null) "新增任務" else "編輯任務", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(
                onClick = {
                    if (title.isNotBlank() && !isSaving && !date.isBefore(LocalDate.now()) &&
                        (existing == null || TaskRules.canEdit(existing.task))) {
                        val start = date.atTime(startTime).atZone(zone).toInstant().toEpochMilli()
                        var end = date.atTime(endTime).atZone(zone).toInstant().toEpochMilli()
                        if (end <= start) end += 24 * 60 * 60_000L
                        onSave(
                            TaskDraft(
                                id = existing?.task?.id,
                                title = title,
                                note = note,
                                startDateTime = start,
                                endDateTime = end,
                                priority = priority,
                                categoryId = categoryId,
                                repeat = repeat,
                                customRepeatIntervalWeeks = customRepeatInterval,
                                customRepeatWeekdays = customRepeatDays,
                                reminderEnabled = reminderEnabled,
                                reminderOffsets = reminderOffsets,
                                repeatUntilComplete = repeatUntilComplete,
                                repeatReminderInterval = repeatInterval,
                                ringEnabled = ringEnabled,
                                ringDurationSeconds = ringDurationSeconds,
                                ringToneUri = ringToneUri,
                                ringVolume = ringVolume,
                                tags = existing?.tags?.map { it.name }.orEmpty(),
                                subTasks = emptyList()
                            )
                        )
                    }
                },
                enabled = title.isNotBlank() && !isSaving && !date.isBefore(today)
            ) {
                if (isSaving) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                else Icon(Icons.Filled.Check, contentDescription = "儲存", tint = if (title.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
            }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 40.dp)) {
            if (existing == null) item {
                val applyQuick: () -> Unit = {
                    if (quickText.isNotBlank()) {
                        val parsed = QuickAddParser.parse(quickText, today)
                        title = parsed.title
                        parsed.date?.let { if (!it.isBefore(today)) date = it }
                        parsed.time?.let { startTime = it; endTime = it.plusHours(1) }
                        if (parsed.repeat != TaskRepeat.NONE) {
                            repeat = parsed.repeat
                            if (parsed.repeat == TaskRepeat.CUSTOM) {
                                customRepeatInterval = 1
                                customRepeatDays = parsed.weekdays
                            }
                        }
                        quickText = ""
                    }
                }
                OutlinedTextField(
                    value = quickText,
                    onValueChange = { quickText = it.take(100) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp),
                    label = { Text("快速輸入") },
                    placeholder = { Text("例：明天下午3點開會、每月5號繳房租") },
                    leadingIcon = { Icon(Icons.Filled.AutoAwesome, contentDescription = null) },
                    trailingIcon = { TextButton(onClick = applyQuick, enabled = quickText.isNotBlank()) { Text("套用") } },
                    supportingText = { Text("套用後會帶入下方欄位，確認無誤再按右上角儲存") },
                    singleLine = true
                )
            }
            item {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
                    placeholder = { Text("輸入任務標題…") },
                    leadingIcon = { Icon(Icons.Filled.Checklist, contentDescription = null, tint = priorityColor(priority)) },
                    textStyle = MaterialTheme.typography.titleLarge.copy(
                        color = priorityColor(priority),
                        fontWeight = FontWeight.Bold
                    ),
                    minLines = 1,
                    maxLines = 3
                )
            }
            item {
                SettingRow(Icons.Filled.DateRange, "日期", date.format(DateTimeFormatter.ofPattern("yyyy年M月d日 (E)", Locale.TAIWAN)), onClick = {
                    DatePickerDialog(
                        context,
                        if (darkTheme) android.R.style.Theme_Material_Dialog_Alert else android.R.style.Theme_Material_Light_Dialog_Alert,
                        { _, y, m, d -> date = LocalDate.of(y, m + 1, d) },
                        date.year,
                        date.monthValue - 1,
                        date.dayOfMonth
                    ).apply {
                        datePicker.minDate = TaskRules.startOfToday()
                    }.show()
                })
                HorizontalDivider(Modifier.padding(start = 56.dp))
                SettingRow(Icons.Filled.Schedule, "時間", startTime.format(DateTimeFormatter.ofPattern("HH:mm")), onClick = { showTimePicker = true })
                HorizontalDivider(Modifier.padding(start = 56.dp))
                SettingRow(Icons.Filled.Repeat, "重複", repeatLabel(repeat), onClick = { dialog = "repeat" })
                if (repeat == TaskRepeat.CUSTOM) {
                    Text("間隔週數", modifier = Modifier.padding(start = 56.dp, top = 8.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 56.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (1..4).forEach { weeks -> FilterChip(selected = customRepeatInterval == weeks, onClick = { customRepeatInterval = weeks }, label = { Text("每 $weeks 週") }) }
                    }
                    Text("重複日", modifier = Modifier.padding(start = 56.dp, top = 8.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 56.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("一", "二", "三", "四", "五", "六", "日").forEachIndexed { index, label ->
                            val day = index + 1
                            FilterChip(selected = day in customRepeatDays, onClick = {
                                customRepeatDays = if (day in customRepeatDays && customRepeatDays.size > 1) customRepeatDays - day else customRepeatDays + day
                            }, label = { Text(label) })
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(start = 56.dp))
                SettingRow(
                    Icons.Filled.Alarm,
                    "提醒",
                    if (reminderEnabled) reminderOffsets.sortedDescending().joinToString("、", transform = ::reminderLabel) else "不提醒",
                    onClick = { dialog = "reminder" }
                )
                HorizontalDivider(Modifier.padding(start = 56.dp))
                SettingRow(Icons.Filled.Alarm, "未完成持續提醒", if (repeatUntilComplete) "每 $repeatInterval 分鐘，直到完成" else "關閉", trailing = {
                    Switch(checked = repeatUntilComplete, onCheckedChange = { repeatUntilComplete = it }, enabled = reminderEnabled)
                })
                if (repeatUntilComplete) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 56.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(5, 10, 30, 60).forEach { minutes -> FilterChip(selected = repeatInterval == minutes, onClick = { repeatInterval = minutes }, label = { Text(if (minutes == 60) "1 小時" else "$minutes 分鐘") }) }
                        FilterChip(selected = repeatInterval !in listOf(5, 10, 30, 60), onClick = { dialog = "repeatIntervalCustom" }, label = { Text("自訂") })
                    }
                }
                HorizontalDivider(Modifier.padding(start = 56.dp))
                SettingRow(Icons.Filled.Flag, "優先順序", "", onClick = { dialog = "priority" }, trailing = {
                    Icon(Icons.Filled.Flag, contentDescription = priorityColorName(priority), tint = priorityColor(priority))
                })
                HorizontalDivider(Modifier.padding(start = 56.dp))
                SettingRow(Icons.Filled.Category, "分類", categories.firstOrNull { it.id == categoryId }?.name ?: "未分類", onClick = { dialog = "category" })
            }
            item {
                SettingRow(
                    Icons.Filled.NotificationsActive,
                    "鈴聲提醒",
                    if (ringEnabled) "$ringDurationSeconds 秒 · 音量 $ringVolume%" else "關閉",
                    trailing = {
                        Switch(
                            checked = ringEnabled,
                            onCheckedChange = { ringEnabled = it },
                            enabled = reminderEnabled
                        )
                    }
                )
                OutlinedButton(
                    onClick = { showRingSettings = true },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                    enabled = reminderEnabled,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                ) {
                    Icon(Icons.Filled.MusicNote, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("設定鈴聲")
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
                    label = { Text("備註") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Notes, contentDescription = null) },
                    minLines = 3,
                    maxLines = 8
                )
            }
        }
    }

    when (dialog) {
        "repeat" -> ChoiceDialog("重複事項", TaskRepeat.entries.map(::repeatLabel), TaskRepeat.entries.indexOf(repeat), { repeat = TaskRepeat.entries[it]; dialog = null }, { dialog = null })
        "reminder" -> ReminderMultiChoiceDialog(
            selected = reminderOffsets,
            onConfirm = {
                reminderOffsets = it
                if (it.isEmpty()) {
                    repeatUntilComplete = false
                    ringEnabled = false
                }
                dialog = null
            },
            onCustom = {
                reminderOffsets = it
                dialog = "reminderCustom"
            },
            onDismiss = { dialog = null }
        )
        "reminderCustom" -> NumberInputDialog("自訂提前時間", "分鐘", 15, { reminderOffsets = reminderOffsets + it; dialog = null }, { dialog = null })
        "repeatIntervalCustom" -> NumberInputDialog("未完成再次提醒", "分鐘", repeatInterval, { repeatInterval = it; dialog = null }, { dialog = null })
        "priority" -> PriorityDialog(priority, { priority = it; dialog = null }, { dialog = null })
        "category" -> ChoiceDialog("分類", categories.map { it.name }, categories.indexOfFirst { it.id == categoryId }.coerceAtLeast(0), { categoryId = categories[it].id; dialog = null }, { dialog = null })
    }
    if (showTimePicker) WheelTimePickerDialog(startTime, {
        startTime = it
        endTime = it.plusHours(1)
        showTimePicker = false
    }, { showTimePicker = false })
    if (showRingSettings) RingSettingsDialog(
        durationSeconds = ringDurationSeconds,
        volume = ringVolume,
        ringtoneName = ringtoneTitle(context, ringToneUri),
        onDurationChange = { ringDurationSeconds = it },
        onVolumeChange = { ringVolume = it },
        onChooseRingtone = {
            val current = ringToneUri?.let(Uri::parse) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ringtonePicker.launch(
                Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                    putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                    putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "選擇提醒鈴聲")
                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                    putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, current)
                }
            )
        },
        onDismiss = { showRingSettings = false }
    )
}

@Composable
private fun RingSettingsDialog(
    durationSeconds: Int,
    volume: Int,
    ringtoneName: String,
    onDurationChange: (Int) -> Unit,
    onVolumeChange: (Int) -> Unit,
    onChooseRingtone: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("鈴聲提醒設定") },
        text = {
            Column {
                Text("響鈴時間：$durationSeconds 秒", fontWeight = FontWeight.Bold)
                Slider(
                    value = durationSeconds.toFloat(),
                    onValueChange = { onDurationChange(it.toInt().coerceIn(1, 10)) },
                    valueRange = 1f..10f,
                    steps = 8
                )
                OutlinedButton(
                    onClick = onChooseRingtone,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                ) {
                    Icon(Icons.Filled.MusicNote, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(ringtoneName, maxLines = 2)
                }
                Spacer(Modifier.height(12.dp))
                Text("任務音量：$volume%", fontWeight = FontWeight.Bold)
                Slider(
                    value = volume.toFloat(),
                    onValueChange = { onVolumeChange(it.toInt().coerceIn(0, 100)) },
                    valueRange = 0f..100f,
                    steps = 9
                )
                Text(
                    "響鈴時使用此任務的音量，結束後恢復手機原本的鬧鐘音量。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成", color = MaterialTheme.colorScheme.onSurface) } }
    )
}

private fun ringtoneTitle(context: android.content.Context, uriText: String?): String {
    if (uriText == null) return "系統預設鬧鐘"
    return runCatching {
        RingtoneManager.getRingtone(context, Uri.parse(uriText))?.getTitle(context)
    }.getOrNull().orEmpty().let { title ->
        when {
            title.isBlank() -> "系統預設鬧鐘"
            title.any { it in 'A'..'Z' || it in 'a'..'z' } -> "已選擇鈴聲"
            else -> title
        }
    }
}

@Composable
private fun WheelTimePickerDialog(initial: LocalTime, onConfirm: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    var hour by remember(initial) { mutableStateOf(initial.hour) }
    var minute by remember(initial) { mutableStateOf(initial.minute) }
    val pickerTextColor = MaterialTheme.colorScheme.onSurface.toArgb()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("選擇時間") },
        text = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                AndroidView(
                    factory = { context -> NumberPicker(context).apply {
                        minValue = 0
                        maxValue = 23
                        setFormatter { "%02d".format(it) }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) textColor = pickerTextColor
                    } },
                    update = { picker -> picker.value = hour; picker.setOnValueChangedListener { _, _, value -> hour = value } }
                )
                Text(":", modifier = Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.titleLarge)
                AndroidView(
                    factory = { context -> NumberPicker(context).apply {
                        minValue = 0
                        maxValue = 59
                        setFormatter { "%02d".format(it) }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) textColor = pickerTextColor
                    } },
                    update = { picker -> picker.value = minute; picker.setOnValueChangedListener { _, _, value -> minute = value } }
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(LocalTime.of(hour, minute)) }) { Text("確定", color = MaterialTheme.colorScheme.onSurface) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = MaterialTheme.colorScheme.onSurface) } }
    )
}

@Composable
private fun PriorityDialog(selected: TaskPriority, onSelect: (TaskPriority) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("優先順序") },
        text = {
            Column {
                TaskPriority.entries.forEach { priority ->
                    Row(Modifier.fillMaxWidth().clickable { onSelect(priority) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.RadioButton(selected = selected == priority, onClick = { onSelect(priority) })
                        Icon(Icons.Filled.Flag, contentDescription = null, tint = priorityColor(priority))
                        Text(priorityColorName(priority), modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = MaterialTheme.colorScheme.onSurface) } }
    )
}

@Composable
private fun ReminderMultiChoiceDialog(
    selected: Set<Int>,
    onConfirm: (Set<Int>) -> Unit,
    onCustom: (Set<Int>) -> Unit,
    onDismiss: () -> Unit
) {
    var choices by remember(selected) { mutableStateOf(selected) }
    val options = listOf(0, 5, 10, 30, 60, 24 * 60)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("提醒時間（可複選）") },
        text = {
            Column {
                Row(
                    Modifier.fillMaxWidth().clickable { choices = emptySet() }.padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = choices.isEmpty(), onCheckedChange = { if (it) choices = emptySet() })
                    Text("不提醒", modifier = Modifier.padding(start = 8.dp))
                }
                (options + choices.filterNot { it in options }.sortedDescending()).distinct().forEach { minutes ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            choices = if (minutes in choices) choices - minutes else choices + minutes
                        }.padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = minutes in choices,
                            onCheckedChange = { checked ->
                                choices = if (checked) choices + minutes else choices - minutes
                            }
                        )
                        Text(reminderLabel(minutes), modifier = Modifier.padding(start = 8.dp))
                    }
                }
                TextButton(onClick = { onCustom(choices) }) {
                    Text("新增自訂提前時間", color = MaterialTheme.colorScheme.onSurface)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(choices) }) { Text("完成", color = MaterialTheme.colorScheme.onSurface) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = MaterialTheme.colorScheme.onSurface) } }
    )
}

@Composable
private fun ChoiceDialog(title: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEachIndexed { index, option ->
                    Row(Modifier.fillMaxWidth().clickable { onSelect(index) }.padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.RadioButton(selected = selected == index, onClick = { onSelect(index) })
                        Text(option, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = MaterialTheme.colorScheme.onSurface) } }
    )
}

@Composable
fun TaskDetailScreen(
    item: TaskWithDetails,
    histories: List<ReminderHistoryEntity>,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onComplete: () -> Unit,
    onSnooze: (Int) -> Unit,
    onDelete: () -> Unit,
    onMoveToToday: () -> Unit = {},
    onSkip: () -> Unit = {}
) {
    val context = LocalContext.current
    val priority = item.priority()
    val editable = !item.task.isCompleted && !item.task.startDateTime.toLocalDate().isBefore(LocalTaskToday.current)
    val overdue = !item.task.isCompleted && item.task.startDateTime.toLocalDate().isBefore(LocalTaskToday.current)
    val skippable = TaskRules.canSkip(item.task)
    var showSkip by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showSnooze by remember { mutableStateOf(false) }
    var showCustomSnooze by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
            Spacer(Modifier.weight(1f))
            if (editable) {
                IconButton(onClick = { if (TaskRules.canEdit(item.task)) onEdit() }) { Icon(Icons.Filled.Edit, contentDescription = "編輯") }
            }
            IconButton(onClick = { showDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = "刪除") }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 12.dp)) {
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(58.dp).background(
                            priorityColor(priority),
                            CircleShape
                        ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(if (item.task.isCompleted) Icons.Filled.Check else Icons.Filled.PriorityHigh, contentDescription = null, tint = Color.White, modifier = Modifier.size(34.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        item.task.title,
                        color = priorityColor(priority),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        textDecoration = if (item.task.isCompleted) TextDecoration.LineThrough else null
                    )
                    if (item.task.isCompleted) {
                        Spacer(Modifier.height(4.dp))
                        HorizontalDivider(
                            modifier = Modifier.fillMaxWidth(0.8f),
                            thickness = 2.dp,
                            color = priorityColor(priority).copy(alpha = 0.72f)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Surface(color = priorityContainer(priority), shape = RoundedCornerShape(16.dp)) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (item.task.isCompleted) Icons.Filled.Check else Icons.Filled.Flag,
                                contentDescription = null,
                                tint = priorityColor(priority)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (item.task.isCompleted) "已完成 · ${priorityColorName(priority)}" else priorityColorName(priority),
                                color = priorityColor(priority),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                }
            }
            item {
                Card(Modifier.fillMaxWidth().padding(horizontal = 18.dp), shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    SettingRow(Icons.Filled.DateRange, "日期與時間", item.task.startDateTime.formatDateTime())
                    HorizontalDivider(Modifier.padding(start = 56.dp))
                    SettingRow(Icons.Filled.Repeat, "重複", repeatRuleLabel(item.task.repeatRule))
                    HorizontalDivider(Modifier.padding(start = 56.dp))
                    SettingRow(Icons.Filled.Alarm, "提醒", taskReminderLabel(item))
                    HorizontalDivider(Modifier.padding(start = 56.dp))
                    SettingRow(
                        Icons.Filled.NotificationsActive,
                        "鈴聲提醒",
                        if (item.task.ringEnabled) {
                            "${item.task.ringDurationSeconds} 秒 · 音量 ${item.task.ringVolume}% · ${ringtoneTitle(context, item.task.ringToneUri)}"
                        } else "關閉"
                    )
                    HorizontalDivider(Modifier.padding(start = 56.dp))
                    SettingRow(
                        Icons.Filled.Alarm,
                        "未完成持續提醒",
                        if (item.task.reminderEnabled && item.task.repeatUntilComplete) "每 ${item.task.repeatReminderInterval} 分鐘，直到完成" else "關閉"
                    )
                    HorizontalDivider(Modifier.padding(start = 56.dp))
                    SettingRow(Icons.Filled.Category, "分類", item.category?.name ?: "未分類")
                    HorizontalDivider(Modifier.padding(start = 56.dp))
                    SettingRow(Icons.Filled.Tag, "標籤", item.tags.joinToString("  ") { "#${it.name}" }.ifBlank { "無標籤" })
                }
            }
            if (item.task.note.isNotBlank()) item {
                Card(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp), shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Text(item.task.note, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
            val taskHistory = histories.filter { it.taskId == item.task.id }.take(5)
            if (taskHistory.isNotEmpty()) item {
                Text("提醒紀錄", modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp), fontWeight = FontWeight.Bold)
                taskHistory.forEach { history ->
                    Text("${historyAction(history.action)} · ${history.triggeredAt.formatDateTime()}", modifier = Modifier.padding(horizontal = 18.dp, vertical = 5.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (editable || overdue) {
            Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 18.dp, vertical = 10.dp).navigationBarsPadding()) {
                Button(onClick = { if (TaskRules.canComplete(item.task)) onComplete() }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), colors = ButtonDefaults.buttonColors(containerColor = priorityColor(priority))) {
                    Icon(Icons.Filled.Check, contentDescription = null); Spacer(Modifier.width(8.dp)); Text(if (overdue) "補完成" else "標記為完成", fontWeight = FontWeight.Bold)
                }
                if (overdue) {
                    OutlinedButton(
                        onClick = { if (TaskRules.canMoveToToday(item.task)) onMoveToToday() },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                    ) {
                        Icon(Icons.Filled.Today, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("移到今天")
                    }
                }
                if (skippable) {
                    OutlinedButton(
                        onClick = { showSkip = true },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                    ) {
                        Icon(Icons.Filled.SkipNext, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("跳過這一次")
                    }
                }
                if (editable && item.task.reminderEnabled) {
                    OutlinedButton(
                        onClick = { if (TaskRules.canEdit(item.task)) showSnooze = true },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                    ) {
                        Icon(Icons.Filled.Snooze, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("延後提醒")
                    }
                }
            }
        }
    }
    if (showSkip) {
        val nextText = TaskRules.nextOccurrenceFrom(item.task.startDateTime, item.task.repeatRule, TaskRules.startOfToday())?.formatDateTime()
        AlertDialog(
            onDismissRequest = { showSkip = false },
            title = { Text("跳過這一次？") },
            text = { Text(if (nextText != null) "此任務將改到下一次：$nextText\n不會記為完成。" else "此任務將改到下一次，不會記為完成。") },
            confirmButton = { TextButton(onClick = { showSkip = false; if (TaskRules.canSkip(item.task)) onSkip() }) { Text("跳過", color = MaterialTheme.colorScheme.onSurface) } },
            dismissButton = { TextButton(onClick = { showSkip = false }) { Text("取消", color = MaterialTheme.colorScheme.onSurface) } }
        )
    }
    if (showDelete) AlertDialog(onDismissRequest = { showDelete = false }, title = { Text("刪除任務？") }, text = { Text("刪除後無法從完成紀錄復原。") }, confirmButton = { TextButton(onClick = { showDelete = false; onDelete() }) { Text("刪除", color = MaterialTheme.colorScheme.onSurface) } }, dismissButton = { TextButton(onClick = { showDelete = false }) { Text("取消", color = MaterialTheme.colorScheme.onSurface) } })
    if (showSnooze) ChoiceDialog("延後提醒", listOf("5 分鐘", "10 分鐘", "30 分鐘", "1 小時", "明天", "自訂"), 0, {
        if (it == 5) { showSnooze = false; showCustomSnooze = true } else { onSnooze(listOf(5, 10, 30, 60, 24 * 60)[it]); showSnooze = false }
    }, { showSnooze = false })
    if (showCustomSnooze) NumberInputDialog("自訂延後時間", "分鐘", item.task.repeatReminderInterval.coerceAtLeast(1), { onSnooze(it); showCustomSnooze = false }, { showCustomSnooze = false })
}

@Composable
private fun NumberInputDialog(title: String, suffix: String, initial: Int, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    var value by remember(title, initial) { mutableStateOf(initial.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { input -> value = input.filter(Char::isDigit).take(5) },
                label = { Text(suffix) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )
        },
        confirmButton = { TextButton(onClick = { value.toIntOrNull()?.takeIf { it > 0 }?.let(onConfirm) }, enabled = value.toIntOrNull()?.let { it > 0 } == true) { Text("確定", color = MaterialTheme.colorScheme.onSurface) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = MaterialTheme.colorScheme.onSurface) } }
    )
}

fun priorityColorName(priority: TaskPriority) = when (priority) { TaskPriority.HIGH -> "紅色"; TaskPriority.MEDIUM -> "藍色"; TaskPriority.LOW -> "綠色" }
private fun repeatLabel(repeat: TaskRepeat) = when (repeat) { TaskRepeat.NONE -> "不重複"; TaskRepeat.DAILY -> "每天"; TaskRepeat.WEEKLY -> "每週"; TaskRepeat.MONTHLY -> "每月"; TaskRepeat.YEARLY -> "每年"; TaskRepeat.CUSTOM -> "自訂" }
private fun repeatRuleLabel(rule: String): String {
    val custom = TaskRules.parseCustom(rule) ?: return repeatLabel(runCatching { TaskRepeat.valueOf(rule) }.getOrDefault(TaskRepeat.NONE))
    val labels = listOf("一", "二", "三", "四", "五", "六", "日")
    val days = custom.weekdays.sorted().joinToString("、") { "週${labels[it - 1]}" }
    return "每 ${custom.intervalWeeks} 週 · $days"
}
private fun reminderLabel(minutes: Int) = when {
    minutes == 0 -> "準時"
    minutes == 24 * 60 -> "前一天"
    minutes % (24 * 60) == 0 -> "${minutes / (24 * 60)} 天前"
    minutes % 60 == 0 -> "${minutes / 60} 小時前"
    else -> "$minutes 分鐘前"
}
private fun taskReminderLabel(item: TaskWithDetails): String {
    if (!item.task.reminderEnabled) return "不提醒"
    return TaskRules.reminderOffsets(item.task)
        .sortedDescending()
        .joinToString("、", transform = ::reminderLabel)
        .ifBlank { "不提醒" }
}
private fun historyAction(action: String) = when (action) { "TRIGGERED" -> "已提醒"; "COMPLETED" -> "已完成"; "SNOOZED" -> "已延後"; "DISMISSED" -> "已滑除提醒"; "MOVED" -> "已移到今天"; "SKIPPED" -> "已跳過一次"; else -> "其他提醒紀錄" }
