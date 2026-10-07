package jp.oit.`is`.yourname.taskplanner.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import jp.oit.`is`.yourname.taskplanner.data.Step
import jp.oit.`is`.yourname.taskplanner.data.Task
import jp.oit.`is`.yourname.taskplanner.planner.Planner
import java.time.LocalDate

/**
 * 課題1件の詳細。上から「次にやること」「進み具合」「ステップ」「操作」の順に並べる。
 * ステップは「何を、どこまでやれば終わりか」を決めたやることリストで、進み具合の物差しになる。
 */
@Composable
fun TaskDetailScreen(
    task: Task,
    today: LocalDate,
    onToggleDone: (String) -> Unit,
    onToggleStep: (taskId: String, stepId: String) -> Unit,
    onAddStep: (taskId: String, title: String, goal: String) -> Unit,
    onDeleteStep: (taskId: String, stepId: String) -> Unit,
    onApplyTotalHours: (String, Double) -> Unit,
    onStartTimer: (String) -> Unit,
    onStopTimer: (String, Boolean) -> Unit,
    onEdit: (String) -> Unit,
    onAddToCalendarApp: (Task) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val status = Planner.status(task, today)
    val next = Planner.nextStep(task)
    val stepProgress = Planner.stepProgress(task)
    val reestimate = Planner.reestimatedTotal(task)
    val differsMuch = reestimate != null && kotlin.math.abs(reestimate - task.plannedHours) >= task.plannedHours * 0.2
    val todayHours = if (task.done) null else Planner.plan(task, today)[today]
    var showAddStep by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(task.name, style = MaterialTheme.typography.headlineSmall)
        Text(
            "${deadlineText(task, today)}（${task.deadline}）",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = task.done, onCheckedChange = { onToggleDone(task.id) })
            Text("この課題を完了にする", style = MaterialTheme.typography.bodyLarge)
        }

        if (!task.done) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Column(Modifier.padding(16.dp)) {
                    when {
                        next != null -> {
                            Text("次にやること", style = MaterialTheme.typography.labelLarge)
                            Text(
                                next.title,
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                            if (next.goal.isNotBlank()) {
                                Text(
                                    "達成の目安：${next.goal}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                            Text(
                                "このステップの時間の目安：${formatHours(Planner.stepHours(task, next))}",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        task.steps.isNotEmpty() -> Text(
                            "すべてのステップを達成しました！ 上の「この課題を完了にする」を押して終わりにしよう",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        else -> Text(
                            "下の「ステップ」に、何をどこまでやるかを足すと、次にやることがここに出ます",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    if (todayHours != null) {
                        Text(
                            "今日の時間の目安：${formatHours(todayHours)}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    TimerRow(task, onStartTimer, onStopTimer)
                }
            }
        }

        SectionTitle("進み具合")
        if (stepProgress != null) {
            val doneCount = task.steps.count { it.done }
            Text(
                "ステップ：${task.steps.size}個のうち ${doneCount}個を達成（${(stepProgress * 100).toInt()}%）",
                style = MaterialTheme.typography.bodyLarge,
            )
            LinearProgressIndicator(
                progress = { stepProgress },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp),
            )
        }
        Text(
            "作業時間：${formatHours(task.loggedHours)} ／ 計画 ${formatHours(task.plannedHours)}" +
                "（残り ${formatHours(Planner.remainingHours(task))}）",
            style = MaterialTheme.typography.bodyLarge,
        )
        LinearProgressIndicator(
            progress = { Planner.progress(task) },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp),
        )
        StatusLine(status)

        if (differsMuch && reestimate != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "ステップの進み方から見ると、あなたの場合は合計 約${formatHours(reestimate)} かかりそうです" +
                            "（今の計画は ${formatHours(task.plannedHours)}）。目安なので、参考にしてね。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(onClick = { onApplyTotalHours(task.id, reestimate) }) { Text("この見積もりで計画し直す") }
                }
            }
        }

        SectionTitle("ステップ（何を、どこまでやるか）")
        if (task.steps.isEmpty()) {
            Text(
                "「何を」「どこまでできたら終わりか」に分けて入れると、進み具合がはっきり見えます。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        task.steps.forEachIndexed { index, step ->
            StepCard(
                index = index,
                step = step,
                hours = Planner.stepHours(task, step),
                onToggle = { onToggleStep(task.id, step.id) },
                onDelete = { onDeleteStep(task.id, step.id) },
            )
        }
        OutlinedButton(onClick = { showAddStep = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("ステップを足す")
        }

        SectionTitle("操作")
        OutlinedButton(onClick = { onEdit(task.id) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Edit, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("課題の名前・締切・時間を直す")
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

    if (showAddStep) {
        AddStepDialog(
            onDismiss = { showAddStep = false },
            onAdd = { title, goal ->
                onAddStep(task.id, title, goal)
                showAddStep = false
            },
        )
    }
}

@Composable
private fun StepCard(index: Int, step: Step, hours: Double, onToggle: () -> Unit, onDelete: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (step.done) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 8.dp)) {
            Checkbox(checked = step.done, onCheckedChange = { onToggle() })
            Column(Modifier.weight(1f).padding(top = 12.dp)) {
                Text(
                    "${index + 1}. ${step.title}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    textDecoration = if (step.done) TextDecoration.LineThrough else null,
                )
                if (step.goal.isNotBlank()) {
                    Text(
                        "達成の目安：${step.goal}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Text(
                    "時間の目安 ${formatHours(hours)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Close, contentDescription = "このステップを削除")
            }
        }
    }
}

@Composable
private fun AddStepDialog(onDismiss: () -> Unit, onAdd: (title: String, goal: String) -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var goal by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ステップを足す") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("何をやる？（例：第3章を読む）") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = goal,
                    onValueChange = { goal = it },
                    label = { Text("どこまでできたら終わり？（例：章末問題で7割）") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(title, goal) }, enabled = title.isNotBlank()) { Text("足す") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } },
    )
}
