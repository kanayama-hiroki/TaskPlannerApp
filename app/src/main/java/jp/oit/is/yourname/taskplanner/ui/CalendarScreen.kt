package jp.oit.`is`.yourname.taskplanner.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import jp.oit.`is`.yourname.taskplanner.data.Task
import jp.oit.`is`.yourname.taskplanner.planner.PlanStatus
import jp.oit.`is`.yourname.taskplanner.planner.Planner
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.delay
import java.util.Locale

private val WEEKDAYS = listOf("日", "月", "火", "水", "木", "金", "土")

/** 作業時間がこの値以上の日を最も濃い色にする。 */
private const val HEAVY_DAY_HOURS = 4.0

fun formatHours(hours: Double): String =
    if (hours % 1.0 == 0.0) "${hours.toInt()}h" else String.format(Locale.getDefault(), "%.1fh", hours)

@Composable
fun CalendarScreen(
    tasks: List<Task>,
    onToggleDone: (String) -> Unit,
    onDelete: (String) -> Unit,
    onAddToCalendarApp: (Task) -> Unit,
    onAddLog: (id: String, date: LocalDate, deltaHours: Double) -> Unit,
    onSetMastery: (id: String, mastery: Int) -> Unit,
    onApplyTotalHours: (id: String, totalHours: Double) -> Unit,
    onStartTimer: (String) -> Unit,
    onStopTimer: (id: String, save: Boolean) -> Unit,
    maxDailyHours: Double,
    onChangeMaxDailyHours: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    var monthText by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    var selectedText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    val month = YearMonth.parse(monthText)
    val selected = LocalDate.parse(selectedText)

    val today = LocalDate.now()
    val totals = remember(tasks, today) { Planner.totalsByDate(tasks, today) }
    val deadlines = remember(tasks) { tasks.filter { !it.done }.map { it.deadline }.toSet() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = { monthText = month.minusMonths(1).toString() }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "前の月")
            }
            Text(
                text = "${month.year}年${month.monthValue}月",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { monthText = month.plusMonths(1).toString() }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "次の月")
            }
        }

        Row(Modifier.fillMaxWidth()) {
            WEEKDAYS.forEach {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        val leadingBlanks = month.atDay(1).dayOfWeek.value % 7 // 日曜始まり
        val cells: List<LocalDate?> = List(leadingBlanks) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                (week + List(7 - week.size) { null }).forEach { date ->
                    Box(Modifier.weight(1f).padding(1.dp)) {
                        if (date != null) {
                            DayCell(
                                date = date,
                                hours = totals[date] ?: 0.0,
                                overLimit = (totals[date] ?: 0.0) > maxDailyHours + 1e-9,
                                isDeadline = date in deadlines,
                                isToday = date == LocalDate.now(),
                                isSelected = date == selected,
                                onClick = { selectedText = date.toString() },
                            )
                        }
                    }
                }
            }
        }

        val overDaysInMonth = Planner.overloadedDays(totals, maxDailyHours).count { YearMonth.from(it) == month }
        DailyLimitRow(maxDailyHours, overDaysInMonth, onChangeMaxDailyHours)

        Spacer(Modifier.height(16.dp))
        DayDetail(date = selected, today = today, tasks = tasks, onAddLog = onAddLog)
        Spacer(Modifier.height(24.dp))
        ProgressSection(
            tasks = tasks,
            today = today,
            onToggleDone = onToggleDone,
            onDelete = onDelete,
            onAddToCalendarApp = onAddToCalendarApp,
            onSetMastery = onSetMastery,
            onApplyTotalHours = onApplyTotalHours,
            onStartTimer = onStartTimer,
            onStopTimer = onStopTimer,
        )
        Spacer(Modifier.height(88.dp)) // FAB と重ならない余白
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    hours: Double,
    overLimit: Boolean,
    isDeadline: Boolean,
    isToday: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    val heat = MaterialTheme.colorScheme.primary
    val background = if (hours > 0) {
        heat.copy(alpha = (hours / HEAVY_DAY_HOURS).coerceIn(0.12, 0.85).toFloat())
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    }
    val border = when {
        isSelected -> BorderStroke(2.dp, MaterialTheme.colorScheme.tertiary)
        isToday -> BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
        else -> null
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(shape)
            .background(background)
            .let { if (border != null) it.border(border, shape) else it }
            .clickable(onClick = onClick),
    ) {
        Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.labelLarge)
        Text(
            text = if (hours > 0) formatHours(hours) + (if (overLimit) "!" else "") else " ",
            style = MaterialTheme.typography.labelSmall,
            color = if (overLimit) MaterialTheme.colorScheme.error else Color.Unspecified,
            fontWeight = if (overLimit) FontWeight.Bold else null,
        )
        Text(
            text = if (isDeadline) "締切" else " ",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun DailyLimitRow(maxDailyHours: Double, overDays: Int, onChange: (Double) -> Unit) {
    Column(Modifier.padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("1日に使える時間の上限: ${formatHours(maxDailyHours)}", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = { onChange(-0.5) }) { Text("−") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = { onChange(0.5) }) { Text("＋") }
        }
        if (overDays > 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "上限を超える日が${overDays}日あります（赤い「!」の日）。締切や時間を見直してみよう",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun DayDetail(
    date: LocalDate,
    today: LocalDate,
    tasks: List<Task>,
    onAddLog: (String, LocalDate, Double) -> Unit,
) {
    Text("${date.monthValue}月${date.dayOfMonth}日の予定", style = MaterialTheme.typography.titleMedium)
    val items = tasks.mapNotNull { task ->
        val hours = Planner.plan(task, today)[date]
        if (hours != null || task.deadline == date) task to hours else null
    }
    if (items.isEmpty()) {
        Text(
            text = if (tasks.isEmpty()) "「＋」から課題を追加してね" else "この日の予定はありません",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
        return
    }
    items.forEach { (task, hours) ->
        val logged = task.logs[date] ?: 0.0
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            Column(Modifier.padding(12.dp)) {
                Text(task.name, style = MaterialTheme.typography.titleSmall)
                val detail = buildString {
                    if (hours != null) append(if (date.isBefore(today)) "実績 ${formatHours(hours)}" else "予定 ${formatHours(hours)}")
                    if (date == today && logged > 0) append("（うち記録済み ${formatHours(logged)}）")
                    if (task.deadline == date) append(if (isEmpty()) "締切日" else "・締切日")
                }
                Text(detail, style = MaterialTheme.typography.bodySmall)

                if (!date.isAfter(today) && !task.done) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                        Text("やった時間を記録：", style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { onAddLog(task.id, date, -0.5) }, enabled = logged > 0) { Text("−0.5h") }
                        FilledTonalButton(onClick = { onAddLog(task.id, date, 0.5) }) { Text("+0.5h") }
                        Spacer(Modifier.width(8.dp))
                        FilledTonalButton(onClick = { onAddLog(task.id, date, 1.0) }) { Text("+1h") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressSection(
    tasks: List<Task>,
    today: LocalDate,
    onToggleDone: (String) -> Unit,
    onDelete: (String) -> Unit,
    onAddToCalendarApp: (Task) -> Unit,
    onSetMastery: (String, Int) -> Unit,
    onApplyTotalHours: (String, Double) -> Unit,
    onStartTimer: (String) -> Unit,
    onStopTimer: (String, Boolean) -> Unit,
) {
    if (tasks.isEmpty()) return
    Text("課題ごとの進み具合", style = MaterialTheme.typography.titleMedium)
    tasks.sortedBy { it.deadline }.forEach { task ->
        TaskProgressCard(
            task, today, onToggleDone, onDelete, onAddToCalendarApp,
            onSetMastery, onApplyTotalHours, onStartTimer, onStopTimer,
        )
    }
}

@Composable
private fun TaskProgressCard(
    task: Task,
    today: LocalDate,
    onToggleDone: (String) -> Unit,
    onDelete: (String) -> Unit,
    onAddToCalendarApp: (Task) -> Unit,
    onSetMastery: (String, Int) -> Unit,
    onApplyTotalHours: (String, Double) -> Unit,
    onStartTimer: (String) -> Unit,
    onStopTimer: (String, Boolean) -> Unit,
) {
    val status = Planner.status(task, today)
    var sliderValue by remember(task.id, task.mastery) { mutableFloatStateOf((task.mastery ?: 0).toFloat()) }
    val reestimate = Planner.reestimatedTotal(task)
    val differsMuch = reestimate != null && kotlin.math.abs(reestimate - task.plannedHours) >= task.plannedHours * 0.2

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Column(Modifier.padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = task.done, onCheckedChange = { onToggleDone(task.id) })
                Column(Modifier.weight(1f)) {
                    Text(task.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "締切 ${task.deadline}・ペース ${task.pace.label}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                IconButton(onClick = { onAddToCalendarApp(task) }) {
                    Icon(Icons.Default.Event, contentDescription = "カレンダーアプリに締切を追加")
                }
                IconButton(onClick = { onDelete(task.id) }) {
                    Icon(Icons.Default.Delete, contentDescription = "削除")
                }
            }

            Column(Modifier.padding(horizontal = 12.dp)) {
                LinearProgressIndicator(
                    progress = { Planner.progress(task) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "作業 ${formatHours(task.loggedHours)} / 計画 ${formatHours(task.plannedHours)}" +
                        "（残り ${formatHours(Planner.remainingHours(task))}）",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )

                val (message, isWarning) = when (status) {
                    PlanStatus.DONE -> "計画の時間をやりきりました！" to false
                    PlanStatus.ON_TRACK -> "順調です。この調子で！" to false
                    PlanStatus.BEHIND -> "少し遅れ気味です。残りを振り直したので、今日からの予定を見てね" to true
                    PlanStatus.OVERDUE -> "締切を過ぎています。締切日を見直すか、残りを整理しよう" to true
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    if (isWarning) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(message, style = MaterialTheme.typography.bodySmall)
                }

                TimerRow(task, onStartTimer, onStopTimer)

                Text(
                    "習得度（自己評価）: ${sliderValue.toInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    onValueChangeFinished = { onSetMastery(task.id, sliderValue.toInt()) },
                    valueRange = 0f..100f,
                    steps = 19,
                )
                MasteryChart(task)
                Planner.learningRatePerHour(task)?.let { rate ->
                    Text(
                        "これまでのペース：作業1時間あたり 習得度 約${String.format(Locale.getDefault(), "%.1f", rate)}%",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                if (differsMuch && reestimate != null) {
                    Text(
                        "今のペースだと、あなたの場合は合計 約${formatHours(reestimate)} かかりそうです" +
                            "（今の計画は ${formatHours(task.plannedHours)}）。目安なので、参考にしてね。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(onClick = { onApplyTotalHours(task.id, reestimate) }) { Text("この見積もりで計画し直す") }
                }
            }
        }
    }
}

private const val LONG_SESSION_HOURS = 4.0

private fun formatElapsed(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return String.format(Locale.getDefault(), "%02d:%02d:%02d", total / 3600, (total % 3600) / 60, total % 60)
}

/** 作業タイマー。開始時刻を保存しているので、アプリを閉じても経過時間はずれない。 */
@Composable
private fun TimerRow(task: Task, onStart: (String) -> Unit, onStop: (String, Boolean) -> Unit) {
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

/** 習得度の推移（日ごとの記録）の折れ線グラフ。 */
@Composable
private fun MasteryChart(task: Task) {
    val points = task.masteryLogs.toSortedMap().toList()
    if (points.size < 2) {
        Text(
            "習得度を2日以上記録すると、推移のグラフが見えます",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp),
        )
        return
    }
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val first = points.first().first
    val span = ChronoUnit.DAYS.between(first, points.last().first).coerceAtLeast(1).toFloat()

    Canvas(Modifier.fillMaxWidth().height(100.dp).padding(horizontal = 6.dp, vertical = 4.dp)) {
        listOf(0f, 50f, 100f).forEach { v ->
            val y = size.height * (1f - v / 100f)
            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
        }
        val offsets = points.map { (date, value) ->
            Offset(
                x = size.width * ChronoUnit.DAYS.between(first, date).toFloat() / span,
                y = size.height * (1f - value / 100f),
            )
        }
        for (i in 0 until offsets.size - 1) {
            drawLine(lineColor, offsets[i], offsets[i + 1], strokeWidth = 2.dp.toPx())
        }
        offsets.forEach { drawCircle(lineColor, radius = 4.dp.toPx(), center = it) }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            "${first.monthValue}/${first.dayOfMonth}  ${points.first().second}%",
            style = MaterialTheme.typography.labelSmall,
        )
        Text(
            "${points.last().first.monthValue}/${points.last().first.dayOfMonth}  ${points.last().second}%",
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
