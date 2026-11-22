package tw.knox.dailytasks.ui

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import tw.knox.dailytasks.data.CategoryEntity
import tw.knox.dailytasks.data.TaskStats
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    hideLockContent: Boolean,
    categories: List<CategoryEntity>,
    completedCount: Int,
    stats: TaskStats,
    allowEarlyComplete: Boolean,
    onAllowEarlyComplete: (Boolean) -> Unit,
    onTheme: (ThemeMode) -> Unit,
    onHideLock: (Boolean) -> Unit,
    onAddCategory: (String) -> Unit,
    onRenameCategory: (Long, String) -> Unit,
    onDeleteCategory: (Long) -> Unit,
    onExport: (android.net.Uri, String, (String) -> Unit) -> Unit,
    onImport: (android.net.Uri, String, (String) -> Unit) -> Unit
) {
    val context = LocalContext.current
    val versionName = remember(context) { context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty() }
    var password by remember { mutableStateOf("") }
    var categoryDialog by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<CategoryEntity?>(null) }
    var categoryName by remember { mutableStateOf("") }
    var deletingCategory by remember { mutableStateOf<CategoryEntity?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let { onExport(it, password) { message -> Toast.makeText(context, message, Toast.LENGTH_LONG).show() } }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { onImport(it, password) { message -> Toast.makeText(context, message, Toast.LENGTH_LONG).show() } }
    }
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp)) {
        item { ScreenTitle("設定", "外觀、備份與資料") }
        item {
            SettingsSection("外觀") {
                SettingRow(Icons.Filled.Palette, "主題", when (themeMode) { ThemeMode.LIGHT -> "淺色"; ThemeMode.DARK -> "深色"; ThemeMode.SYSTEM -> "跟隨系統" })
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 56.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(ThemeMode.SYSTEM to "系統", ThemeMode.LIGHT to "淺色", ThemeMode.DARK to "深色").forEach { (mode, label) ->
                        FilterChip(selected = themeMode == mode, onClick = { onTheme(mode) }, label = { Text(label) })
                    }
                }
            }
        }
        item {
            SettingsSection("任務") {
                SettingRow(Icons.Filled.TaskAlt, "允許提前完成", if (allowEarlyComplete) "未到時間也可以勾選完成" else "到達任務時間後才可完成", trailing = {
                    Switch(checked = allowEarlyComplete, onCheckedChange = onAllowEarlyComplete)
                })
            }
        }
        item {
            SettingsSection("完成統計") {
                SettingRow(Icons.Filled.BarChart, "本週", "完成 ${stats.weekDone} / ${stats.weekTotal} 項 · ${stats.weekRate}%")
                SettingRow(Icons.Filled.CalendarMonth, "本月", "完成 ${stats.monthDone} / ${stats.monthTotal} 項 · ${stats.monthRate}%")
                SettingRow(Icons.Filled.History, "逾期未完成", "${stats.overdue} 項")
            }
        }
        item {
            SettingsSection("備份與換機") {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    label = { Text("備份密碼（選填）") },
                    leadingIcon = { Icon(Icons.Filled.Security, contentDescription = null) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    supportingText = { Text(if (password.isBlank()) "未加密壓縮備份" else "高強度加密備份") }
                )
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = {
                        val filename = "吐兔卡備份-${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))}.zip"
                        exportLauncher.launch(filename)
                    }, modifier = Modifier.weight(1f).heightIn(min = 50.dp)) {
                        Icon(Icons.Filled.Backup, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("匯出")
                    }
                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
                        modifier = Modifier.weight(1f).heightIn(min = 50.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                    ) {
                        Icon(Icons.Filled.Restore, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("匯入")
                    }
                }
            }
        }
        item {
            SettingsSection("資料") {
                SettingRow(Icons.Filled.Category, "分類", "${categories.size} 個", trailing = {
                    IconButton(onClick = { editingCategory = null; categoryName = ""; categoryDialog = true }) { Icon(Icons.Filled.Add, contentDescription = "新增分類") }
                })
                categories.forEach { category ->
                    Row(Modifier.fillMaxWidth().padding(start = 56.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(category.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        IconButton(onClick = { editingCategory = category; categoryName = category.name; categoryDialog = true }) { Icon(Icons.Filled.Edit, contentDescription = "編輯${category.name}") }
                        IconButton(onClick = { deletingCategory = category }) { Icon(Icons.Filled.Delete, contentDescription = "刪除${category.name}") }
                    }
                }
            }
        }
        item {
            Text("吐兔卡 $versionName", modifier = Modifier.fillMaxWidth().padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
    if (categoryDialog) AlertDialog(
        onDismissRequest = { categoryDialog = false },
        title = { Text(if (editingCategory == null) "新增分類" else "編輯分類") },
        text = { OutlinedTextField(categoryName, { categoryName = it }, label = { Text("分類名稱") }, singleLine = true) },
        confirmButton = {
            TextButton(onClick = {
                if (categoryName.isNotBlank()) {
                    editingCategory?.let { onRenameCategory(it.id, categoryName) } ?: onAddCategory(categoryName)
                    categoryDialog = false
                }
            }) { Text("儲存", color = MaterialTheme.colorScheme.onSurface) }
        },
        dismissButton = { TextButton(onClick = { categoryDialog = false }) { Text("取消", color = MaterialTheme.colorScheme.onSurface) } }
    )
    deletingCategory?.let { category ->
        AlertDialog(
            onDismissRequest = { deletingCategory = null },
            title = { Text("刪除「${category.name}」？") },
            text = { Text("既有任務會保留，分類將改為未分類。") },
            confirmButton = { TextButton(onClick = { onDeleteCategory(category.id); deletingCategory = null }) { Text("刪除", color = MaterialTheme.colorScheme.onSurface) } },
            dismissButton = { TextButton(onClick = { deletingCategory = null }) { Text("取消", color = MaterialTheme.colorScheme.onSurface) } }
        )
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Text(title, modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) { content() }
    Spacer(Modifier.height(8.dp))
}
