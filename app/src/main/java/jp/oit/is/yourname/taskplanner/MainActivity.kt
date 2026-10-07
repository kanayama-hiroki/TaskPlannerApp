package jp.oit.`is`.yourname.taskplanner

import android.Manifest
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import jp.oit.`is`.yourname.taskplanner.notification.createNotificationChannel
import jp.oit.`is`.yourname.taskplanner.notification.openCalendarIntent
import jp.oit.`is`.yourname.taskplanner.ui.CalendarScreen
import jp.oit.`is`.yourname.taskplanner.ui.HomeScreen
import jp.oit.`is`.yourname.taskplanner.ui.TaskDetailScreen
import jp.oit.`is`.yourname.taskplanner.ui.TaskFormScreen
import jp.oit.`is`.yourname.taskplanner.ui.TaskListScreen
import jp.oit.`is`.yourname.taskplanner.ui.theme.TaskPlannerAppTheme
import kotlinx.coroutines.launch
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    private val viewModel: TaskViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        createNotificationChannel(this)
        setContent {
            TaskPlannerAppTheme {
                TaskPlannerApp(viewModel)
            }
        }
    }
}

private val TAB_TITLES = listOf("ホーム", "カレンダー", "課題")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskPlannerApp(viewModel: TaskViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showForm by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }

    val tasks = viewModel.tasks
    val today = LocalDate.now()
    val detailTask = tasks.firstOrNull { it.id == detailId }
    val editingTask = tasks.firstOrNull { it.id == editingId }
    val showDemo = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(context, "通知を許可しないと、リマインダーが届きません", Toast.LENGTH_LONG).show()
        }
    }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    BackHandler(enabled = !showForm && detailTask != null) { detailId = null }

    fun closeForm() {
        showForm = false
        editingId = null
    }

    val onDelete: (String) -> Unit = { id ->
        val removed = viewModel.delete(id)
        detailId = null
        if (removed != null) {
            scope.launch {
                snackbarHostState.currentSnackbarData?.dismiss()
                val result = snackbarHostState.showSnackbar(
                    message = "「${removed.name}」を削除しました",
                    actionLabel = "元に戻す",
                    duration = SnackbarDuration.Long,
                )
                if (result == SnackbarResult.ActionPerformed) viewModel.restore(removed)
            }
        }
    }
    val onAddToCalendarApp: (jp.oit.`is`.yourname.taskplanner.data.Task) -> Unit = { task ->
        if (!openCalendarIntent(context, task)) {
            Toast.makeText(context, "カレンダーアプリが見つかりません", Toast.LENGTH_SHORT).show()
        }
    }

    val inMainTabs = !showForm && detailTask == null

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            when {
                showForm -> Unit
                detailTask != null -> TopAppBar(
                    title = { Text("課題の詳細") },
                    navigationIcon = {
                        IconButton(onClick = { detailId = null }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                        }
                    },
                )
                else -> TopAppBar(title = { Text(TAB_TITLES[tab]) })
            }
        },
        bottomBar = {
            if (inMainTabs) {
                NavigationBar {
                    val icons = listOf(Icons.Default.Home, Icons.Default.CalendarMonth, Icons.AutoMirrored.Filled.List)
                    TAB_TITLES.forEachIndexed { index, title ->
                        NavigationBarItem(
                            selected = tab == index,
                            onClick = { tab = index },
                            icon = { Icon(icons[index], contentDescription = null) },
                            label = { Text(title) },
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (inMainTabs && tasks.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { showForm = true },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("課題を追加") },
                )
            }
        },
    ) { innerPadding ->
        val contentModifier = Modifier.padding(innerPadding)
        when {
            showForm -> TaskFormScreen(
                initial = editingTask,
                onSave = { name, deadline, hours, pace, steps ->
                    if (editingTask != null) {
                        viewModel.updateTask(editingTask.id, name, deadline, hours, pace)
                    } else {
                        viewModel.add(name, deadline, hours, pace, steps)
                    }
                    closeForm()
                },
                onCancel = ::closeForm,
                existingTasks = tasks,
                maxDailyHours = viewModel.maxDailyHours,
                modifier = contentModifier,
            )

            detailTask != null -> TaskDetailScreen(
                task = detailTask,
                today = today,
                onToggleDone = viewModel::toggleDone,
                onToggleStep = viewModel::toggleStep,
                onAddStep = viewModel::addStep,
                onDeleteStep = viewModel::deleteStep,
                onApplyTotalHours = viewModel::applyTotalHours,
                onStartTimer = viewModel::startTimer,
                onStopTimer = viewModel::stopTimer,
                onEdit = { id ->
                    editingId = id
                    showForm = true
                },
                onAddToCalendarApp = onAddToCalendarApp,
                onDelete = onDelete,
                modifier = contentModifier,
            )

            tab == 0 -> HomeScreen(
                tasks = tasks,
                today = today,
                maxDailyHours = viewModel.maxDailyHours,
                onOpenTask = { detailId = it },
                onAddLog = viewModel::addLog,
                onStartTimer = viewModel::startTimer,
                onStopTimer = viewModel::stopTimer,
                onAddTask = { showForm = true },
                modifier = contentModifier,
            )

            tab == 1 -> CalendarScreen(
                tasks = tasks,
                today = today,
                maxDailyHours = viewModel.maxDailyHours,
                onChangeMaxDailyHours = viewModel::changeMaxDailyHours,
                onAddLog = viewModel::addLog,
                onOpenTask = { detailId = it },
                modifier = contentModifier,
            )

            else -> TaskListScreen(
                tasks = tasks,
                today = today,
                onOpenTask = { detailId = it },
                showDemo = showDemo,
                onAddDemo = viewModel::addDemoTask,
                modifier = contentModifier,
            )
        }
    }
}
