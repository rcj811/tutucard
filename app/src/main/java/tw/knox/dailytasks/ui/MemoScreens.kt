package tw.knox.dailytasks.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.automirrored.filled.Subject
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tw.knox.dailytasks.data.MemoEntity
import tw.knox.dailytasks.data.MemoRules
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun MemoListScreen(memos: List<MemoEntity>, loaded: Boolean, onOpen: (MemoEntity) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val search = query.trim()
    val filtered = memos.filter { search.isBlank() || it.title.contains(search, ignoreCase = true) || it.content.contains(search, ignoreCase = true) }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ScreenTitle("筆記", "${memos.size} 則")
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
            label = { Text("搜尋標頭或內文") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, contentDescription = "清除搜尋") }
            }
        )
        if (!loaded) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (filtered.isEmpty()) item {
                    EmptyState(if (search.isEmpty()) "還沒有筆記" else "沒有符合的筆記", "", Icons.AutoMirrored.Filled.Notes)
                }
                items(filtered, key = { it.id }) { memo ->
                    Card(
                        onClick = { onOpen(memo) },
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(memo.timeLabel(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(memo.title.ifBlank { "未命名筆記" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            if (memo.content.isNotBlank()) Text(memo.content, maxLines = 3, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MemoEditorScreen(
    memo: MemoEntity,
    busy: Boolean,
    onTitleChange: (String) -> Unit,
    onContentChange: (String) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var confirmDelete by rememberSaveable(memo.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, enabled = !busy) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回筆記") }
            Text("筆記", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            IconButton(
                onClick = { shareMemo(context, memo) },
                enabled = !busy && (memo.title.isNotBlank() || memo.content.isNotBlank())
            ) { Icon(Icons.Filled.Share, contentDescription = "分享筆記") }
            IconButton(onClick = onSave, enabled = !busy) {
                if (busy) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                else Icon(Icons.Filled.Save, contentDescription = "儲存", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = { confirmDelete = true }, enabled = !busy) { Icon(Icons.Filled.Delete, contentDescription = "刪除") }
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = memo.title,
                onValueChange = onTitleChange,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("標頭") },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Subject, contentDescription = null) },
                textStyle = MaterialTheme.typography.titleMedium
            )
            OutlinedTextField(
                value = memo.content,
                onValueChange = onContentChange,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                minLines = 10,
                label = { Text("明細") },
                supportingText = { Text("${MemoRules.contentLength(memo.content)} / ${MemoRules.MAX_CONTENT_LENGTH}") }
            )
        }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("是否刪除此筆記？") },
        text = { Text("${memo.title.ifBlank { "未命名筆記" }}\n刪除後無法復原。", modifier = Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("刪除", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } }
    )
}

private fun shareMemo(context: Context, memo: MemoEntity) {
    val text = listOf(memo.title, memo.content).filter { it.isNotBlank() }.joinToString("\n\n")
    if (text.isBlank()) {
        Toast.makeText(context, "尚無可分享的內容", Toast.LENGTH_SHORT).show()
        return
    }
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
        if (memo.title.isNotBlank()) putExtra(Intent.EXTRA_TITLE, memo.title)
    }
    try {
        context.startActivity(Intent.createChooser(send, "分享筆記"))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "目前沒有可分享文字的應用程式", Toast.LENGTH_LONG).show()
    } catch (_: SecurityException) {
        Toast.makeText(context, "系統暫時不允許分享，筆記內容仍保留", Toast.LENGTH_LONG).show()
    }
}

private fun MemoEntity.timeLabel(): String {
    val dateTime = Instant.ofEpochMilli(updatedAt).atZone(ZoneId.systemDefault())
    val date = dateTime.format(DateTimeFormatter.ofPattern("yyyy/MM/dd", Locale.TAIWAN))
    val weekday = "一二三四五六日"[dateTime.dayOfWeek.value - 1]
    val time = dateTime.format(DateTimeFormatter.ofPattern("HH:mm", Locale.TAIWAN))
    return "$date ($weekday) $time ${if (updatedAt == createdAt) "建立" else "編輯"}"
}
