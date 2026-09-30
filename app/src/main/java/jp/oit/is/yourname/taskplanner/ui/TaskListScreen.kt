package jp.oit.`is`.yourname.taskplanner.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import jp.oit.`is`.yourname.taskplanner.data.Task
import jp.oit.`is`.yourname.taskplanner.planner.PlanStatus
import jp.oit.`is`.yourname.taskplanner.planner.Planner
import java.time.LocalDate

/** 課題の一覧。締切が近い順に並べ、完了した課題は下にまとめる。タップすると詳細が開く。 */
@Composable
fun TaskListScreen(
    tasks: List<Task>,
    today: LocalDate,
    onOpenTask: (String) -> Unit,
    showDemo: Boolean,
    onAddDemo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sorted = tasks.sortedWith(compareBy<Task> { it.done }.thenBy { it.deadline })

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        if (tasks.isEmpty()) {
            Text(
                "課題はまだありません。「＋」から追加してね。",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
        sorted.forEach { task -> TaskSummaryCard(task, today, onOpenTask) }

        if (showDemo) {
            TextButton(onClick = onAddDemo) { Text("（開発用）デモの課題を追加") }
        }
        Spacer(Modifier.height(88.dp)) // FAB と重ならない余白
    }
}

@Composable
private fun TaskSummaryCard(task: Task, today: LocalDate, onOpenTask: (String) -> Unit) {
    val status = Planner.status(task, today)
    val warn = status == PlanStatus.BEHIND || status == PlanStatus.OVERDUE
    Card(
        onClick = { onOpenTask(task.id) },
        colors = CardDefaults.cardColors(
            containerColor = if (task.done) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                task.name,
                style = MaterialTheme.typography.titleMedium,
                textDecoration = if (task.done) TextDecoration.LineThrough else null,
            )
            Text(
                if (task.done) "完了" else "${deadlineText(task, today)}（${task.deadline}）",
                style = MaterialTheme.typography.bodyMedium,
                color = if (warn && !task.done) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
            LinearProgressIndicator(
                progress = { Planner.progress(task) },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
            )
            Text(
                "作業 ${formatHours(task.loggedHours)} / 計画 ${formatHours(task.plannedHours)}" +
                    (task.mastery?.let { "　習得度 $it%" } ?: ""),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (!task.done) {
                Text(
                    planStatusMessage(status).first,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (warn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
