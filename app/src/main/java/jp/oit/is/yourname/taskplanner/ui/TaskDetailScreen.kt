package jp.oit.`is`.yourname.taskplanner.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import jp.oit.`is`.yourname.taskplanner.data.Task
import jp.oit.`is`.yourname.taskplanner.planner.Planner
import java.time.LocalDate
import java.util.Locale

/** 課題1件の詳細。進み具合・タイマー・習得度・操作を、見出しごとに分けて出す。 */
@Composable
fun TaskDetailScreen(
    task: Task,
    today: LocalDate,
    onToggleDone: (String) -> Unit,
    onSetMastery: (String, Int) -> Unit,
    onApplyTotalHours: (String, Double) -> Unit,
    onStartTimer: (String) -> Unit,
    onStopTimer: (String, Boolean) -> Unit,
    onEdit: (String) -> Unit,
    onAddToCalendarApp: (Task) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val status = Planner.status(task, today)
    var sliderValue by remember(task.id, task.mastery) { mutableFloatStateOf((task.mastery ?: 0).toFloat()) }
    val reestimate = Planner.reestimatedTotal(task)
    val differsMuch = reestimate != null && kotlin.math.abs(reestimate - task.plannedHours) >= task.plannedHours * 0.2

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(task.name, style = MaterialTheme.typography.headlineSmall)
        Text(
            "${deadlineText(task, today)}（${task.deadline}）・ペース ${task.pace.label}",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = task.done, onCheckedChange = { onToggleDone(task.id) })
            Text("完了にする", style = MaterialTheme.typography.bodyLarge)
        }

        SectionTitle("進み具合")
        LinearProgressIndicator(progress = { Planner.progress(task) }, modifier = Modifier.fillMaxWidth())
        Text(
            "作業 ${formatHours(task.loggedHours)} / 計画 ${formatHours(task.plannedHours)}" +
                "（残り ${formatHours(Planner.remainingHours(task))}）",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(vertical = 4.dp),
        )
        StatusLine(status)

        SectionTitle("作業タイマー")
        TimerRow(task, onStartTimer, onStopTimer)

        SectionTitle("習得度")
        Text("いまの習得度（自己評価）: ${sliderValue.toInt()}%", style = MaterialTheme.typography.bodyLarge)
        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = { onSetMastery(task.id, sliderValue.toInt()) },
            valueRange = 0f..100f,
        )
        Text(
            "スライダーを動かすと、今日の習得度として記録されます。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MasteryChart(task)
        Planner.masteryGap(task, today)?.let { gap ->
            val (text, warn) = when {
                gap >= 3 -> "習得度は目標ペースより ${gap}ポイント先行しています" to false
                gap <= -3 -> "習得度は目標ペースより ${-gap}ポイント遅れています。時間を増やすか、締切・内容を見直そう" to true
                else -> "習得度は目標ペースどおりです" to false
            }
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (warn) MaterialTheme.colorScheme.error else Color.Unspecified,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Planner.learningRatePerHour(task)?.let { rate ->
            Text(
                "これまでのペース：作業1時間あたり 習得度 約${String.format(Locale.getDefault(), "%.1f", rate)}%",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (differsMuch && reestimate != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "今のペースだと、あなたの場合は合計 約${formatHours(reestimate)} かかりそうです" +
                            "（今の計画は ${formatHours(task.plannedHours)}）。目安なので、参考にしてね。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(onClick = { onApplyTotalHours(task.id, reestimate) }) { Text("この見積もりで計画し直す") }
                }
            }
        }

        SectionTitle("操作")
        OutlinedButton(onClick = { onEdit(task.id) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Edit, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("課題を編集する")
        }
        OutlinedButton(
            onClick = { onAddToCalendarApp(task) },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            Icon(Icons.Default.Event, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("締切を、カレンダーアプリに追加する")
        }
        OutlinedButton(
            onClick = { onDelete(task.id) },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            Icon(Icons.Default.Delete, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("この課題を削除する")
        }
        Spacer(Modifier.height(32.dp))
    }
}
