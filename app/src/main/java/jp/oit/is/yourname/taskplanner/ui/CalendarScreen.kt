package jp.oit.`is`.yourname.taskplanner.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import jp.oit.`is`.yourname.taskplanner.data.Task
import jp.oit.`is`.yourname.taskplanner.planner.Planner
import java.time.LocalDate
import java.time.YearMonth
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
    modifier: Modifier = Modifier,
) {
    var monthText by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    var selectedText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    val month = YearMonth.parse(monthText)
    val selected = LocalDate.parse(selectedText)

    val totals = remember(tasks) { Planner.totalsByDate(tasks) }
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

        Spacer(Modifier.height(16.dp))
        DayDetail(
            date = selected,
            tasks = tasks,
            onToggleDone = onToggleDone,
            onDelete = onDelete,
            onAddToCalendarApp = onAddToCalendarApp,
        )
        Spacer(Modifier.height(88.dp)) // FAB と重ならない余白
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    hours: Double,
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
            text = if (hours > 0) formatHours(hours) else " ",
            style = MaterialTheme.typography.labelSmall,
        )
        Text(
            text = if (isDeadline) "締切" else " ",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun DayDetail(
    date: LocalDate,
    tasks: List<Task>,
    onToggleDone: (String) -> Unit,
    onDelete: (String) -> Unit,
    onAddToCalendarApp: (Task) -> Unit,
) {
    Text("${date.monthValue}月${date.dayOfMonth}日の予定", style = MaterialTheme.typography.titleMedium)
    val items = tasks.mapNotNull { task ->
        val hours = Planner.dailyHours(task)[date]
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
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            ) {
                Checkbox(checked = task.done, onCheckedChange = { onToggleDone(task.id) })
                Column(Modifier.weight(1f)) {
                    Text(task.name, style = MaterialTheme.typography.titleSmall)
                    val detail = buildString {
                        if (hours != null) append("この日 ${formatHours(hours)}")
                        if (task.deadline == date) append(if (isEmpty()) "締切日" else "・締切日")
                    }
                    Text(detail, style = MaterialTheme.typography.bodySmall)
                    Text(
                        "締切 ${task.deadline}／合計 ${formatHours(task.totalHours)}",
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
        }
    }
}
