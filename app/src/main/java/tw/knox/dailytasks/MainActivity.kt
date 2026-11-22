package tw.knox.dailytasks

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import tw.knox.dailytasks.reminder.NotificationHelper
import tw.knox.dailytasks.data.TaskRules
import tw.knox.dailytasks.data.TaskStats
import tw.knox.dailytasks.ui.DailyTasksTheme
import tw.knox.dailytasks.ui.LocalTaskToday
import tw.knox.dailytasks.ui.RecordsScreen
import tw.knox.dailytasks.ui.MemoListScreen
import tw.knox.dailytasks.ui.MemoEditorScreen
import tw.knox.dailytasks.ui.SettingsScreen
import tw.knox.dailytasks.ui.TaskDetailScreen
import tw.knox.dailytasks.ui.TaskEditorScreen
import tw.knox.dailytasks.ui.TasksScreen
import tw.knox.dailytasks.ui.formatDateTime
import tw.knox.dailytasks.ui.priority
import tw.knox.dailytasks.ui.priorityColor
import tw.knox.dailytasks.widget.TaskWidgetUpdater
import java.time.LocalDate

class MainActivity : ChineseActivity() {
    private val openTaskRequest = MutableStateFlow<Long?>(null)
    private val openAddRequest = MutableStateFlow(false)
    private val openMemoRequest = MutableStateFlow(false)
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        continueFirstRunPermissionSetup()
    }
    private val exactAlarmPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        markPermissionSetupComplete()
        sendBroadcast(
            Intent("android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED")
                .setPackage(packageName)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        acceptIntent(intent, fromShortcut = savedInstanceState == null)
        setContent {
            val vm: MainViewModel = viewModel()
            val theme by vm.themeMode.collectAsState()
            val systemDark = isSystemInDarkTheme()
            DailyTasksTheme(theme) {
                val dark = theme == tw.knox.dailytasks.ui.ThemeMode.DARK || theme == tw.knox.dailytasks.ui.ThemeMode.SYSTEM && systemDark
                val background = MaterialTheme.colorScheme.background.toArgb()
                val surface = MaterialTheme.colorScheme.surface.toArgb()
                SideEffect {
                    window.statusBarColor = background
                    window.navigationBarColor = surface
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = !dark
                        isAppearanceLightNavigationBars = !dark
                    }
                }
                DailyTasksRoot(vm, openTaskRequest, openAddRequest, openMemoRequest)
            }
        }
        if (!getSharedPreferences("first_run", MODE_PRIVATE).getBoolean("permission_setup_complete", false)) beginFirstRunPermissionSetup()
    }

    override fun onStop() {
        super.onStop()
        // 離開 App 回到桌面時，小工具清單回到第一筆
        TaskWidgetUpdater.scrollToTop(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        acceptIntent(intent, fromShortcut = true)
    }

    private fun acceptIntent(intent: Intent?, fromShortcut: Boolean) {
        val taskId = intent?.getLongExtra(NotificationHelper.EXTRA_OPEN_TASK_ID, -1) ?: -1
        if (taskId >= 0) openTaskRequest.value = taskId
        if (intent?.getBooleanExtra(EXTRA_OPEN_ADD, false) == true) openAddRequest.value = true
        // 長按 App 圖示的捷徑；畫面旋轉重建時不重複開啟
        if (fromShortcut) when (intent?.action) {
            ACTION_ADD_TASK -> openAddRequest.value = true
            ACTION_ADD_MEMO -> openMemoRequest.value = true
        }
    }

    private fun beginFirstRunPermissionSetup() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            continueFirstRunPermissionSetup()
        }
    }

    private fun continueFirstRunPermissionSetup() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(AlarmManager::class.java)
            if (!alarmManager.canScheduleExactAlarms()) {
                val intent = Intent(
                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:$packageName")
                )
                runCatching { exactAlarmPermission.launch(intent) }
                    .onFailure { markPermissionSetupComplete() }
                return
            }
        }
        markPermissionSetupComplete()
    }

    private fun markPermissionSetupComplete() {
        getSharedPreferences("first_run", MODE_PRIVATE).edit().putBoolean("permission_setup_complete", true).apply()
    }

    companion object {
        const val EXTRA_OPEN_ADD = "open_add"
        const val ACTION_ADD_TASK = "tw.knox.dailytasks.ADD_TASK"
        const val ACTION_ADD_MEMO = "tw.knox.dailytasks.ADD_MEMO"
    }
}

// Keep CALENDAR deserializable for saved activity state from older APKs, but never show its tab.
private enum class MainTab(val label: String) { RECORDS("行事曆"), TASKS("任務"), MEMO("筆記"), SETTINGS("設定"), CALENDAR("日曆") }

@Composable
private fun DailyTasksRoot(
    vm: MainViewModel,
    openTaskRequest: MutableStateFlow<Long?>,
    openAddRequest: MutableStateFlow<Boolean>,
    openMemoRequest: MutableStateFlow<Boolean>
) {
    val memoVm: MemoViewModel = viewModel()
    val today = LocalTaskToday.current
    val memos by memoVm.memos.collectAsState()
    val memosLoaded by memoVm.loaded.collectAsState()
    val memoEditor by memoVm.editor.collectAsState()
    val memoBusy by memoVm.busy.collectAsState()
    val memoError by memoVm.error.collectAsState()
    val confirmMemoExit by memoVm.confirmExit.collectAsState()
    val tasks by vm.tasks.collectAsState()
    val tasksLoaded by vm.tasksLoaded.collectAsState()
    val saving by vm.saving.collectAsState()
    val saveError by vm.saveError.collectAsState()
    val categories by vm.categories.collectAsState()
    val histories by vm.histories.collectAsState()
    val theme by vm.themeMode.collectAsState()
    val hideLock by vm.hideLockContent.collectAsState()
    val requestedTask by openTaskRequest.collectAsState()
    val requestedAdd by openAddRequest.collectAsState()
    val requestedMemo by openMemoRequest.collectAsState()
    val allowEarlyComplete by vm.allowEarlyComplete.collectAsState()
    var tab by rememberSaveable(key = "main_tabs_v2") { mutableStateOf(MainTab.RECORDS) }
    var recordsDateText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var newTaskDateText by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedTaskId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showEditor by rememberSaveable { mutableStateOf(false) }
    var editTaskId by rememberSaveable { mutableStateOf<Long?>(null) }
    val pageState = rememberSaveableStateHolder()
    var pendingCompleteTaskId by remember { mutableStateOf<Long?>(null) }
    var pendingPermanentDeleteTaskId by remember { mutableStateOf<Long?>(null) }

    BackHandler(enabled = showEditor) {
        if (!saving) showEditor = false
    }
    BackHandler(enabled = !showEditor && selectedTaskId != null) {
        selectedTaskId = null
    }
    BackHandler(enabled = !showEditor && selectedTaskId == null && tab == MainTab.MEMO && memoEditor != null) {
        memoVm.close()
    }

    LaunchedEffect(requestedTask) {
        if (requestedTask != null) {
            selectedTaskId = requestedTask
            showEditor = false
            openTaskRequest.value = null
        }
    }
    LaunchedEffect(requestedAdd) {
        if (requestedAdd) {
            newTaskDateText = null
            editTaskId = null
            showEditor = true
            selectedTaskId = null
            openAddRequest.value = false
        }
    }

    LaunchedEffect(requestedMemo) {
        if (requestedMemo) {
            showEditor = false
            selectedTaskId = null
            tab = MainTab.MEMO
            if (memoEditor == null) memoVm.create()
            openMemoRequest.value = false
        }
    }

    val selected = tasks.firstOrNull { it.task.id == selectedTaskId }
    val editingTask = tasks.firstOrNull { it.task.id == editTaskId }
    val requestComplete: (Long) -> Unit = { taskId ->
        tasks.firstOrNull { it.task.id == taskId }
            ?.takeIf { TaskRules.canComplete(it.task) }
            ?.let { pendingCompleteTaskId = taskId }
    }
    when {
        showEditor && editTaskId != null && editingTask == null -> Column(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!tasksLoaded) CircularProgressIndicator()
            else Text("找不到此任務，可能已被刪除。")
            TextButton(onClick = { showEditor = false; selectedTaskId = null }) { Text("返回清單") }
        }
        showEditor -> TaskEditorScreen(
            existing = editingTask,
            categories = categories,
            onClose = { if (!saving) showEditor = false },
            onSave = { draft -> vm.save(draft) { showEditor = false } },
            isSaving = saving,
            initialDate = newTaskDateText?.let { LocalDate.parse(it) }
        )
        selected != null -> pageState.SaveableStateProvider("task:${selected.task.id}") {
            TaskDetailScreen(
                item = selected,
                histories = histories,
                onBack = { selectedTaskId = null },
                onEdit = { if (TaskRules.canEdit(selected.task)) { editTaskId = selected.task.id; showEditor = true } },
                onComplete = { requestComplete(selected.task.id) },
                onSnooze = { vm.snooze(selected.task.id, it); selectedTaskId = null },
                onDelete = { vm.delete(selected.task.id) { selectedTaskId = null } },
                onMoveToToday = { vm.moveToToday(selected.task.id) },
                onSkip = { vm.skipOccurrence(selected.task.id) }
            )
        }
        else -> pageState.SaveableStateProvider(tab.name) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                bottomBar = {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        MainTab.entries.filter { it != MainTab.CALENDAR }.forEach { item ->
                            val icon = when (item) {
                                MainTab.RECORDS, MainTab.CALENDAR -> Icons.Filled.CalendarMonth
                                MainTab.TASKS -> Icons.Filled.Checklist
                                MainTab.MEMO -> Icons.AutoMirrored.Filled.Notes
                                MainTab.SETTINGS -> Icons.Filled.Settings
                            }
                            NavigationBarItem(
                                selected = tab == item,
                                onClick = {
                                    if (item != tab) {
                                        if (tab == MainTab.MEMO && memoEditor != null) memoVm.close { tab = item }
                                        else tab = item
                                    }
                                },
                                enabled = !memoBusy,
                                icon = { Icon(icon, contentDescription = item.label) },
                                label = { Text(item.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onSurface,
                                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                },
                floatingActionButton = {
                    if (tab == MainTab.MEMO && memoEditor == null) {
                        FloatingActionButton(
                            onClick = memoVm::create,
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ) { Icon(Icons.AutoMirrored.Filled.NoteAdd, contentDescription = "新增筆記") }
                    } else if (tab != MainTab.SETTINGS && tab != MainTab.MEMO &&
                        (tab != MainTab.RECORDS || !LocalDate.parse(recordsDateText).isBefore(today))) {
                        FloatingActionButton(
                            onClick = {
                                if (tab == MainTab.RECORDS && LocalDate.parse(recordsDateText).isBefore(LocalDate.now())) return@FloatingActionButton
                                newTaskDateText = if (tab == MainTab.RECORDS) recordsDateText else null
                                editTaskId = null
                                showEditor = true
                            },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ) { Icon(Icons.Filled.Add, contentDescription = "新增任務") }
                    }
                }
            ) { padding ->
                val openTask: (Long) -> Unit = { selectedTaskId = it }
                val toggle: (Long) -> Unit = requestComplete
                val deleteCompleted: (Long) -> Unit = { taskId -> pendingPermanentDeleteTaskId = taskId }
                androidx.compose.foundation.layout.Box(Modifier.padding(padding)) {
                    when (tab) {
                        MainTab.TASKS -> TasksScreen(
                            tasks = tasks,
                            categories = categories,
                            onOpenTask = openTask,
                            onToggle = toggle,
                            onDeleteCompleted = deleteCompleted
                        )
                        MainTab.MEMO -> {
                            val editingMemo = memoEditor
                            if (editingMemo != null) MemoEditorScreen(
                                memo = editingMemo,
                                busy = memoBusy,
                                onTitleChange = memoVm::updateTitle,
                                onContentChange = memoVm::updateContent,
                                onSave = memoVm::save,
                                onBack = { memoVm.close() },
                                onDelete = memoVm::delete
                            ) else pageState.SaveableStateProvider("memo_list") {
                                MemoListScreen(memos = memos, loaded = memosLoaded, onOpen = memoVm::open)
                            }
                        }
                        MainTab.RECORDS, MainTab.CALENDAR -> RecordsScreen(
                            tasks = tasks,
                            onOpenTask = openTask,
                            onToggle = toggle,
                            onDeleteCompleted = deleteCompleted,
                            selectedDate = LocalDate.parse(recordsDateText),
                            onDateSelected = { recordsDateText = it.toString() }
                        )
                        MainTab.SETTINGS -> SettingsScreen(
                            themeMode = theme,
                            hideLockContent = hideLock,
                            categories = categories,
                            completedCount = tasks.count { it.task.isCompleted },
                            stats = remember(tasks, today) { TaskStats.compute(tasks.map { it.task }, today) },
                            allowEarlyComplete = allowEarlyComplete,
                            onAllowEarlyComplete = vm::setAllowEarlyComplete,
                            onTheme = vm::setTheme,
                            onHideLock = vm::setHideLockContent,
                            onAddCategory = vm::addCategory,
                            onRenameCategory = vm::renameCategory,
                            onDeleteCategory = vm::deleteCategory,
                            onExport = { uri, password, done -> vm.exportBackup(uri, password) { done(it.message) } },
                            onImport = { uri, password, done -> vm.importBackup(uri, password) { done(it.message) } }
                        )
                    }
                }
            }
        }
    }
    pendingCompleteTaskId?.let { taskId ->
        val pendingTask = tasks.firstOrNull { it.task.id == taskId }
        val taskTitle = pendingTask?.task?.title.orEmpty()
        val taskTitleColor = pendingTask?.let { priorityColor(it.priority()) } ?: MaterialTheme.colorScheme.onSurface
        AlertDialog(
            onDismissRequest = { pendingCompleteTaskId = null },
            title = { Text("是否完成項目？") },
            text = {
                Column {
                    if (taskTitle.isNotBlank()) {
                        Text(
                            taskTitle,
                            color = taskTitleColor,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                    }
                    pendingTask?.let { Text("日期時間：${it.task.startDateTime.formatDateTime()}") }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingCompleteTaskId = null
                    if (pendingTask != null && TaskRules.canComplete(pendingTask.task)) {
                        vm.complete(taskId)
                        if (selectedTaskId == taskId) selectedTaskId = null
                    }
                }) { Text("確認完成") }
            },
            dismissButton = {
                TextButton(onClick = { pendingCompleteTaskId = null }) { Text("取消") }
            }
        )
    }
    pendingPermanentDeleteTaskId?.let { taskId ->
        val pendingTask = tasks.firstOrNull { it.task.id == taskId }
        val taskTitle = pendingTask?.task?.title.orEmpty()
        val taskTitleColor = pendingTask?.let { priorityColor(it.priority()) } ?: MaterialTheme.colorScheme.onSurface
        AlertDialog(
            onDismissRequest = { pendingPermanentDeleteTaskId = null },
            title = { Text("徹底刪除此項目？") },
            text = {
                androidx.compose.foundation.layout.Column {
                    if (taskTitle.isNotBlank()) {
                        Text(
                            taskTitle,
                            color = taskTitleColor,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                        androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 4.dp))
                    }
                    Text("刪除後無法復原。")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingPermanentDeleteTaskId = null
                    vm.delete(taskId) {}
                }) { Text("徹底刪除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingPermanentDeleteTaskId = null }) { Text("取消") }
            }
        )
    }
    if (confirmMemoExit) AlertDialog(
        onDismissRequest = memoVm::keepEditing,
        title = { Text("尚有未儲存的變更") },
        text = { Text("離開將放棄尚未儲存的內容。") },
        confirmButton = { TextButton(onClick = memoVm::discard) { Text("放棄變更", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = memoVm::keepEditing) { Text("繼續編輯") } }
    )
    memoError?.let { message ->
        AlertDialog(
            onDismissRequest = memoVm::dismissError,
            title = { Text("筆記") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = memoVm::dismissError) { Text("確定") } }
        )
    }
    saveError?.let { message ->
        AlertDialog(
            onDismissRequest = vm::dismissSaveError,
            title = { Text("儲存失敗") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = vm::dismissSaveError) { Text("確定") } }
        )
    }
}
