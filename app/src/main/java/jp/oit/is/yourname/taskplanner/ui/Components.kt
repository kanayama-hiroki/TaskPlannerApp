package jp.oit.`is`.yourname.taskplanner.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import jp.oit.`is`.yourname.taskplanner.data.Task
import jp.oit.`is`.yourname.taskplanner.planner.PlanStatus
import kotlinx.coroutines.delay
import java.time.temporal.ChronoUnit
import java.util.Locale

val WEEKDAYS = listOf("日", "月", "火", "水", "木", "金", "土")

fun formatHours(hours: Double): String =
    if (hours % 1.0 == 0.0) "${hours.toInt()}h" else String.format(Locale.getDefault(), "%.1fh", hours)

/** 締切までの日数を、読みやすい文にする。 */
fun deadlineText(task: Task, today: java.time.LocalDate): String {
    val days = ChronoUnit.DAYS.between(today, task.deadline)
    return when {
        days > 0 -> "締切まで あと${days}日"
        days == 0L -> "今日が締切"
        else -> "締切を${-days}日過ぎています"
    }
}

/** 進み具合の判定を、画面に出す文と、警告かどうかに変える。 */
fun planStatusMessage(status: PlanStatus): Pair<String, Boolean> = when (status) {
    PlanStatus.DONE -> "計画の時間をやりきりました！" to false
    PlanStatus.ON_TRACK -> "順調です。この調子で！" to false
    PlanStatus.BEHIND -> "少し遅れ気味です。残りを振り直したので、今日からの予定を見てね" to true
    PlanStatus.OVERDUE -> "締切を過ぎています。締切日を見直すか、残りを整理しよう" to true
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier.padding(top = 20.dp, bottom = 8.dp),
    )
}

@Composable
fun StatusLine(status: PlanStatus, modifier: Modifier = Modifier) {
    val (message, isWarning) = planStatusMessage(status)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        if (isWarning) {
            Icon(
                Icons.Default.Warning,
                contentDescription = "注意",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isWarning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
    }
}

private const val LONG_SESSION_HOURS = 4.0

fun formatElapsed(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return String.format(Locale.getDefault(), "%02d:%02d:%02d", total / 3600, (total % 3600) / 60, total % 60)
}

/** 作業タイマー。開始時刻を保存しているので、アプリを閉じても経過時間はずれない。 */
@Composable
fun TimerRow(task: Task, onStart: (String) -> Unit, onStop: (String, Boolean) -> Unit) {
    if (task.done) return
    val started = task.timerStartedAt
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var confirmLong by remember { mutableStateOf(false) }

    LaunchedEffect(started) {
        if (started != null) {
            while (true) {
                now = System.currentTimeMillis()
                delay(1000)
            }
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
        if (started == null) {
            FilledTonalButton(onClick = { onStart(task.id) }) { Text("▶ 作業を始める") }
            Text(
                "  やった時間を自動で記録します",
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            val elapsed = now - started
            Button(onClick = {
                if (elapsed / 3_600_000.0 > LONG_SESSION_HOURS) confirmLong = true else onStop(task.id, true)
            }) { Text("■ 止める") }
            Text(
                "  計測中 ${formatElapsed(elapsed)}",
                style = MaterialTheme.typography.titleSmall,
            )
        }
    }

    if (confirmLong && started != null) {
        AlertDialog(
            onDismissRequest = { confirmLong = false },
            title = { Text("長時間の計測です") },
            text = {
                Text("${formatElapsed(now - started)} 計測しています。止め忘れていませんか？ この時間を記録しますか？")
            },
            confirmButton = {
                TextButton(onClick = { confirmLong = false; onStop(task.id, true) }) { Text("記録する") }
            },
            dismissButton = {
                TextButton(onClick = { confirmLong = false; onStop(task.id, false) }) { Text("記録せず止める") }
            },
        )
    }
}
