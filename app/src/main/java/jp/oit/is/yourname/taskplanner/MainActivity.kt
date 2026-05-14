package jp.oit.`is`.yourname.taskplanner

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.CalendarContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.work.*
import jp.oit.`is`.yourname.taskplanner.ui.theme.TaskPlannerAppTheme
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        createNotificationChannel()
        setContent {
            TaskPlannerAppTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    TaskInputScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Deadline Reminder"
            val descriptionText = "Notification for task deadlines"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel("DEADLINE_CHANNEL", name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}

class NotificationWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val taskName = inputData.getString("taskName") ?: "課題"
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        val notification = NotificationCompat.Builder(applicationContext, "DEADLINE_CHANNEL")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("明日は締切日です！")
            .setContentText("「$taskName」の締切が明日です。計画通り進んでいますか？")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
        return Result.success()
    }
}

@Composable
fun TaskInputScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var taskName by remember { mutableStateOf("") }
    var deadline by remember { mutableStateOf("") }
    var totalHours by remember { mutableStateOf("") }
    var resultMessage by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    var showConfirmation by remember { mutableStateOf(value = false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        if (!isGranted) {
            errorMessage = "通知を許可しないと、リマインダーが届きません"
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "課題計画プランナー",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        TextField(
            value = taskName,
            onValueChange = { taskName = it },
            label = { Text("課題名（例：プログラミング演習）") },
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            singleLine = true
        )

        TextField(
            value = deadline,
            onValueChange = { deadline = it },
            label = { Text("締切日 (yyyy-MM-dd)") },
            placeholder = { Text("2026-05-20") },
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )

        TextField(
            value = totalHours,
            onValueChange = { totalHours = it },
            label = { Text("完了に必要な合計時間 (時間)") },
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )

        Button(
            onClick = {
                errorMessage = ""
                resultMessage = ""
                showConfirmation = false
                
                try {
                    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply {
                        isLenient = false
                    }
                    val deadlineDate = sdf.parse(deadline)
                    
                    val calendar = Calendar.getInstance()
                    calendar.apply {
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    val today = calendar.time

                    val hours = totalHours.toIntOrNull()

                    if (taskName.isBlank()) {
                        errorMessage = "課題名を入力してね"
                        return@Button
                    }
                    if (deadlineDate == null) {
                        errorMessage = "日付を正しく入力してね"
                        return@Button
                    }
                    if ((hours == null) || (hours <= 0)) {
                        errorMessage = "合計時間は正の数字で入力してね"
                        return@Button
                    }

                    val diffInMs = deadlineDate.time - today.time
                    val daysRemaining = TimeUnit.DAYS.convert(diffInMs, TimeUnit.MILLISECONDS)

                    when {
                        daysRemaining < 0 -> {
                            errorMessage = "締切日が既に過ぎているようです"
                        }
                        daysRemaining == 0L -> {
                            resultMessage = "今日が締切日です！今すぐ $hours 時間で完了させましょう！"
                        }
                        else -> {
                            val dailyHours = hours.toDouble() / daysRemaining
                            resultMessage = "あと${daysRemaining}日。今日から毎日 ${String.format(Locale.getDefault(), "%.1f", dailyHours)}時間進めましょう！"
                            
                            scheduleNotification(context, taskName, deadlineDate)
                            openCalendarIntent(context, taskName, deadlineDate)
                            
                            showConfirmation = true
                        }
                    }
                } catch (_: Exception) {
                    errorMessage = "日付は「2026-05-20」のような形式で正しく入力してね"
                }
            },
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(12.dp)
        ) {
            Icon(Icons.Default.DateRange, contentDescription = null)
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text("計画を計算する")
        }

        if (showConfirmation) {
            Text(
                text = "通知を予約し、カレンダーへの追加画面を開きます",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        if (errorMessage.isNotEmpty()) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 16.dp)
            )
        }

        if (resultMessage.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .padding(top = 24.dp)
                    .fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "【$taskName】の計画",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = resultMessage,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}

fun scheduleNotification(context: Context, taskName: String, deadlineDate: Date) {
    val calendar = Calendar.getInstance()
    calendar.time = deadlineDate
    calendar.add(Calendar.DAY_OF_YEAR, -1)
    calendar.apply {
        set(Calendar.HOUR_OF_DAY, 9)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
    }

    val targetTime = calendar.timeInMillis
    val delay = targetTime - System.currentTimeMillis()

    if (delay > 0) {
        val workRequest = OneTimeWorkRequestBuilder<NotificationWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf("taskName" to taskName))
            .build()
        WorkManager.getInstance(context).enqueue(workRequest)
    }
}

fun openCalendarIntent(context: Context, taskName: String, deadlineDate: Date) {
    val intent = Intent(Intent.ACTION_INSERT)
        .setData(CalendarContract.Events.CONTENT_URI)
        .putExtra(CalendarContract.Events.TITLE, "締切: $taskName")
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, deadlineDate.time)
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, (deadlineDate.time + (60 * 60 * 1000)))
        .putExtra(CalendarContract.Events.ALL_DAY, true)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    TaskPlannerAppTheme {
        TaskInputScreen()
    }
}
