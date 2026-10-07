package jp.oit.`is`.yourname.taskplanner.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import jp.oit.`is`.yourname.taskplanner.data.Task
import jp.oit.`is`.yourname.taskplanner.planner.PlanStatus
import jp.oit.`is`.yourname.taskplanner.planner.Planner
import java.time.LocalDate

/** 今日やることを最初に見せる画面。タイマーと記録ボタンもここから使える。 */
@Composable
fun HomeScreen(
    tasks: List<Task>,
    today: LocalDate,
    maxDailyHours: Double,
    onOpenTask: (String) -> Unit,
    onAddLog: (id: String, date: LocalDate, deltaHours: Double) -> Unit,
    onStartTimer: (String) -> Unit,
    onStopTimer: (id: String, save: Boolean) -> Unit,
    onAddTask: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = tasks.filter { !it.done }
    val todayItems = active.mapNotNull { t -> Planner.plan(t, today)[today]?.let { t to it } }
    val plannedTotal = todayItems.sumOf { it.second }
    val loggedTotal = todayItems.sumOf { it.first.logs[today] ?: 0.0 }
    val attention = active.filter {
        val s = Planner.status(it, today)
        s == PlanStatus.BEHIND || s == PlanStatus.OVERDUE
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        if (tasks.isEmpty()) {
            EmptyHome(onAddTask)
            return@Column
        }

        Text(
            "${today.monthValue}月${today.dayOfMonth}日（${WEEKDAYS[today.dayOfWeek.value % 7]}）",
            style = MaterialTheme.typography.headlineSmall,
        )

        TodaySummary(plannedTotal, loggedTotal, maxDailyHours, hasItems = todayItems.isNotEmpty())

        SectionTitle("今日やること")
        if (todayItems.isEmpty()) {
            Text(
                "今日やる予定の課題はありません。ゆっくり休んでね。",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        todayItems.forEach { (task, planned) ->
            TodayTaskCard(task, planned, today, onOpenTask, onAddLog, onStartTimer, onStopTimer)
        }

        if (attention.isNotEmpty()) {
            SectionTitle("気をつけたい課題")
            attention.forEach { task ->
                Card(
                    onClick = { onOpenTask(task.id) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = "注意",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(task.name, style = MaterialTheme.typography.titleMedium)
                        }
                        Text(
                            planStatusMessage(Planner.status(task, today)).first,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        Text(
                            "タップして詳細を見る",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(88.dp)) // FAB と重ならない余白
    }
}

@Composable
private fun EmptyHome(onAddTask: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 64.dp),
    ) {
        Text("まだ課題がありません", style = MaterialTheme.typography.headlineSmall)
        Text(
            "締切と、かかりそうな時間を入れると、毎日どれくらいやればいいかを計画します。",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 16.dp),
        )
        Button(onClick = onAddTask, contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("最初の課題を追加する")
        }
    }
}

@Composable
private fun TodaySummary(planned: Double, logged: Double, maxDailyHours: Double, hasItems: Boolean) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            if (!hasItems) {
                Text("今日の予定はありません", style = MaterialTheme.typography.titleMedium)
                return@Column
            }
            Text("今日の予定", style = MaterialTheme.typography.bodyMedium)
            Text(formatHours(planned), style = MaterialTheme.typography.displaySmall)
            LinearProgressIndicator(
                progress = { if (planned > 0) (logged / planned).coerceIn(0.0, 1.0).toFloat() else 0f },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )
            Text(
                "記録済み ${formatHours(logged)}（残り ${formatHours((planned - logged).coerceAtLeast(0.0))}）",
                style = MaterialTheme.typography.bodyLarge,
            )
            if (planned > maxDailyHours + 1e-9) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = "注意",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "今日の予定が、1日の上限（${formatHours(maxDailyHours)}）を超えています",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun TodayTaskCard(
    task: Task,
    planned: Double,
    today: LocalDate,
    onOpenTask: (String) -> Unit,
    onAddLog: (String, LocalDate, Double) -> Unit,
    onStartTimer: (String) -> Unit,
    onStopTimer: (String, Boolean) -> Unit,
) {
    val logged = task.logs[today] ?: 0.0
    Card(
        onClick = { onOpenTask(task.id) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(task.name, style = MaterialTheme.typography.titleMedium)
            Text(
                "今日の予定 ${formatHours(planned)}・記録済み ${formatHours(logged)}",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 2.dp),
            )
            Text(
                deadlineText(task, today),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Planner.nextStep(task)?.let { step ->
                Text(
                    "次にやること：${step.title}",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            TimerRow(task, onStartTimer, onStopTimer)

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Text("手で記録：", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.width(4.dp))
                FilledTonalButton(onClick = { onAddLog(task.id, today, 0.5) }) { Text("+0.5h") }
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(onClick = { onAddLog(task.id, today, 1.0) }) { Text("+1h") }
            }
        }
    }
}
