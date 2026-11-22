package tw.knox.dailytasks.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tw.knox.dailytasks.data.TaskPriority
import tw.knox.dailytasks.data.TaskRules
import tw.knox.dailytasks.data.TaskWithDetails
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal val LocalDateStateSaver = Saver<LocalDate, String>(
    save = { it.toString() },
    restore = { LocalDate.parse(it) }
)
internal val YearMonthStateSaver = Saver<YearMonth, String>(
    save = { it.toString() },
    restore = { YearMonth.parse(it) }
)

fun TaskWithDetails.priority(): TaskPriority = runCatching { TaskPriority.valueOf(task.priority) }.getOrDefault(TaskPriority.MEDIUM)
fun TaskWithDetails.displayTime(): Long = task.nextReminderAt ?: task.reminderAt ?: task.startDateTime

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TaskCard(
    item: TaskWithDetails,
    onClick: () -> Unit,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    showDate: Boolean = false,
    showTime: Boolean = false,
    allowCompletedToggle: Boolean = false,
    clickEnabled: Boolean = true,
    onLongClick: (() -> Unit)? = null
) {
    val completed = item.task.isCompleted
    val priority = item.priority()
    val accent = priorityColor(priority)
    val reminderText = TaskRules.reminderOffsets(item.task)
        .sortedDescending()
        .joinToString("、") { minutes ->
            when {
                minutes == 0 -> "準時"
                minutes == 24 * 60 -> "前一天"
                minutes % (24 * 60) == 0 -> "${minutes / (24 * 60)} 天前"
                minutes % 60 == 0 -> "${minutes / 60} 小時前"
                else -> "$minutes 分鐘前"
            }
        }
        .ifBlank { "未設定" }
    val tagText = item.tags.joinToString("  ") { "#${it.name}" }.ifBlank { "無" }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(
                        onClick = { if (clickEnabled) onClick() },
                        onLongClick = onLongClick
                    )
                } else if (clickEnabled) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            ),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = priorityContainer(priority)
        ),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.24f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box {
            Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(6.dp).fillMaxHeight().background(accent))
                IconButton(
                    onClick = { if (TaskRules.canComplete(item.task)) onToggle() },
                    modifier = Modifier.size(48.dp),
                    enabled = !completed || allowCompletedToggle
                ) {
                    Icon(
                        if (completed) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                        contentDescription = if (completed && !allowCompletedToggle) "已完成" else if (completed) "標示為未完成" else "標示為完成",
                        tint = accent
                    )
                }
                if (!completed) {
                    Icon(categoryIcon(item.category?.name), contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                }
                Column(Modifier.weight(1f).padding(vertical = 12.dp)) {
                    Box(Modifier.fillMaxWidth()) {
                        Text(
                            item.task.title,
                            color = accent,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 19.sp,
                                lineHeight = 25.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            textDecoration = if (completed) TextDecoration.LineThrough else null,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (completed) {
                            Box(
                                Modifier
                                    .align(Alignment.CenterStart)
                                    .fillMaxWidth()
                                    .height(2.dp)
                                    .background(accent.copy(alpha = 0.72f))
                            )
                        }
                    }
                    if (showDate) {
                        MetadataLine(Icons.Filled.DateRange, "日期時間：${item.task.startDateTime.formatDateTime()}")
                    } else if (showTime) {
                        MetadataLine(Icons.Filled.Schedule, "時間：${item.task.startDateTime.formatTime()}")
                    }
                    if (!completed) {
                        MetadataLine(Icons.Filled.Alarm, "提醒：$reminderText")
                    }
                    MetadataLine(Icons.Filled.Tag, "標籤：$tagText")
                    MetadataLine(Icons.AutoMirrored.Filled.Notes, "備註：${item.task.note.ifBlank { "無" }}")
                }
                Spacer(Modifier.width(12.dp))
            }
        }
    }
}

@Composable
private fun MetadataLine(icon: ImageVector, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun SectionLabel(label: String, count: Int, color: androidx.compose.ui.graphics.Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.width(7.dp))
        Text(
            "$label（$count）",
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
fun ScreenTitle(title: String, subtitle: String? = null, onHeadingClick: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).then(if (onHeadingClick != null) Modifier.clickable(onClick = onHeadingClick) else Modifier)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        actions()
    }
}

@Composable
fun EmptyState(title: String, message: String, icon: ImageVector) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(42.dp))
        Spacer(Modifier.height(10.dp))
        Text(title, fontWeight = FontWeight.Bold)
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun SettingRow(icon: ImageVector, title: String, value: String, onClick: (() -> Unit)? = null, trailing: @Composable (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            if (value.isNotBlank()) Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing?.invoke()
    }
}

private fun categoryIcon(name: String?): ImageVector = when (name) {
    "工作", "重要", "財務" -> Icons.Filled.Work
    "健康" -> Icons.Filled.FitnessCenter
    "購物" -> Icons.Filled.ShoppingCart
    "家庭" -> Icons.Filled.Home
    else -> Icons.AutoMirrored.Filled.MenuBook
}

fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()
fun Long.formatDateTime(): String = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("M月d日 (E) HH:mm", Locale.TAIWAN))
fun Long.formatTime(): String = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm", Locale.TAIWAN))
