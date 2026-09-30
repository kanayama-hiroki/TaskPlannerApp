package jp.oit.`is`.yourname.taskplanner

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import jp.oit.`is`.yourname.taskplanner.notification.createNotificationChannel
import jp.oit.`is`.yourname.taskplanner.notification.openCalendarIntent
import jp.oit.`is`.yourname.taskplanner.ui.CalendarScreen
import jp.oit.`is`.yourname.taskplanner.ui.TaskFormScreen
import jp.oit.`is`.yourname.taskplanner.ui.theme.TaskPlannerAppTheme

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskPlannerApp(viewModel: TaskViewModel) {
    val context = LocalContext.current
    var showForm by rememberSaveable { mutableStateOf(false) }

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

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { if (!showForm) TopAppBar(title = { Text("課題計画プランナー") }) },
        floatingActionButton = {
            if (!showForm) {
                FloatingActionButton(onClick = { showForm = true }) {
                    Icon(Icons.Default.Add, contentDescription = "課題を追加")
                }
            }
        },
    ) { innerPadding ->
        if (showForm) {
            TaskFormScreen(
                onSave = { name, deadline, hours, pace ->
                    viewModel.add(name, deadline, hours, pace)
                    showForm = false
                },
                onCancel = { showForm = false },
                existingTasks = viewModel.tasks,
                maxDailyHours = viewModel.maxDailyHours,
                modifier = Modifier.padding(innerPadding),
            )
        } else {
            CalendarScreen(
                tasks = viewModel.tasks,
                onToggleDone = viewModel::toggleDone,
                onDelete = viewModel::delete,
                onAddLog = viewModel::addLog,
                onSetMastery = viewModel::setMastery,
                onApplyTotalHours = viewModel::applyTotalHours,
                maxDailyHours = viewModel.maxDailyHours,
                onChangeMaxDailyHours = viewModel::changeMaxDailyHours,
                onAddToCalendarApp = { task ->
                    if (!openCalendarIntent(context, task)) {
                        Toast.makeText(context, "カレンダーアプリが見つかりません", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}
