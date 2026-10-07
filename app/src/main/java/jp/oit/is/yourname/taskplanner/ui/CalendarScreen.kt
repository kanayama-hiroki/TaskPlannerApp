package jp.oit.`is`.yourname.taskplanner.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import jp.oit.`is`.yourname.taskplanner.data.Task
import jp.oit.`is`.yourname.taskplanner.planner.Planner
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.launch

/** 作業時間がこの値以上の日を最も濃い色にする。 */
private const val HEAVY_DAY_HOURS = 4.0

/** 月のページ番号の基準（今月）。左右にスワイプして前後の月へ動かせる。 */
private const val BASE_PAGE = 1200
private const val PAGE_COUNT = BASE_PAGE * 2

/** 月表示のカレンダー。日ごとの作業時間を色の濃さで示し、日付をタップするとその日の予定が出る。左右にスワイプして月を動かせる。 */
@Composable
fun CalendarScreen(
    tasks: List<Task>,
    today: LocalDate,
    maxDailyHours: Double,
    onChangeMaxDailyHours: (Double) -> Unit,
    onAddLog: (id: String, date: LocalDate, deltaHours: Double) -> Unit,
    onOpenTask: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val thisMonth = remember(today) { YearMonth.from(today) }
    val pagerState = rememberPagerState(initialPage = BASE_PAGE) { PAGE_COUNT }
    val scope = rememberCoroutineScope()
    val month = thisMonth.plusMonths((pagerState.currentPage - BASE_PAGE).toLong())
    var selectedText by rememberSaveable { mutableStateOf(today.toString()) }
    val selected = LocalDate.parse(selectedText)

    val totals = remember(tasks, today) { Planner.totalsByDate(tasks, today) }
    val deadlines = remember(tasks) { tasks.filter { !it.done }.map { it.deadline }.toSet() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "前の月")
            }
            Text(
                text = "${month.year}年${month.monthValue}月",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "次の月")
            }
        }

        Row(Modifier.fillMaxWidth()) {
            WEEKDAYS.forEachIndexed { index, label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (index == 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                )
            }
        }

        // 左右にスワイプして月を切り替える。高さが月ごとに変わらないよう、常に6週間分の行を並べる。
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth()) { page ->
            val pageMonth = thisMonth.plusMonths((page - BASE_PAGE).toLong())
            val leadingBlanks = pageMonth.atDay(1).dayOfWeek.value % 7 // 日曜始まり
            val cells: List<LocalDate?> = List(leadingBlanks) { null } +
                (1..pageMonth.lengthOfMonth()).map { pageMonth.atDay(it) }
            Column(Modifier.fillMaxWidth()) {
                repeat(6) { week ->
                    Row(Modifier.fillMaxWidth()) {
                        repeat(7) { col ->
                            val date = cells.getOrNull(week * 7 + col)
                            Box(Modifier.weight(1f).padding(1.dp)) {
                                if (date != null) {
                                    DayCell(
                                        date = date,
                                        hours = totals[date] ?: 0.0,
                                        overLimit = (totals[date] ?: 0.0) > maxDailyHours + 1e-9,
                                        isDeadline = date in deadlines,
                                        isToday = date == today,
                                        isSelected = date == selected,
                                        onClick = { selectedText = date.toString() },
                                    )
                                } else {
                                    Spacer(Modifier.fillMaxWidth().height(64.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        CalendarLegend()
        val overDaysInMonth = Planner.overloadedDays(totals, maxDailyHours).count { YearMonth.from(it) == month }
        DailyLimitRow(maxDailyHours, overDaysInMonth, onChangeMaxDailyHours)

        SectionTitle("${selected.monthValue}月${selected.dayOfMonth}日（${WEEKDAYS[selected.dayOfWeek.value % 7]}）の予定")
        DayDetail(date = selected, today = today, tasks = tasks, onAddLog = onAddLog, onOpenTask = onOpenTask)
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
    val alpha = (hours / HEAVY_DAY_HOURS).coerceIn(0.12, 0.9).toFloat()
    val background = if (hours > 0) {
        MaterialTheme.colorScheme.primary.copy(alpha = alpha)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    }
    // 背景が濃いときは白い文字にして、読みやすくする
    val textColor = if (hours > 0 && alpha >= 0.5f) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val border = when {
        isSelected -> BorderStroke(3.dp, MaterialTheme.colorScheme.tertiary)
        overLimit -> BorderStroke(2.dp, MaterialTheme.colorScheme.error)
        isToday -> BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        else -> null
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(shape)
            .background(background)
            .let { if (border != null) it.border(border, shape) else it }
            .clickable(onClick = onClick),
    ) {
        Text(
            date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            color = textColor,
        )
        Text(
            text = if (hours > 0) formatHours(hours) + (if (overLimit) "!" else "") else " ",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (overLimit) FontWeight.Bold else FontWeight.Normal,
            color = textColor,
        )
        Text(
            text = if (isDeadline) "締切" else " ",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (hours > 0 && alpha >= 0.5f) textColor else MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun CalendarLegend() {
    Text(
        "左右にスワイプすると月が変わります。色が濃い日ほど作業時間が多く、赤い枠（!）は1日の上限を超える日です。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun DailyLimitRow(maxDailyHours: Double, overDays: Int, onChange: (Double) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("1日に使える時間の上限", style = MaterialTheme.typography.bodyMedium)
                    Text(formatHours(maxDailyHours), style = MaterialTheme.typography.titleLarge)
                }
                FilledTonalButton(onClick = { onChange(-0.5) }) { Text("−0.5h") }
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(onClick = { onChange(0.5) }) { Text("＋0.5h") }
            }
            if (overDays > 0) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = "注意",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "この月に、上限を超える日が${overDays}日あります。締切や時間を見直してみよう",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
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
    onOpenTask: (String) -> Unit,
) {
    val items = tasks.mapNotNull { task ->
        val hours = Planner.plan(task, today)[date]
        if (hours != null || task.deadline == date) task to hours else null
    }
    if (items.isEmpty()) {
        Text(
            text = if (tasks.isEmpty()) "「＋」から課題を追加してね" else "この日の予定はありません",
            style = MaterialTheme.typography.bodyLarge,
        )
        return
    }
    items.forEach { (task, hours) ->
        val logged = task.logs[date] ?: 0.0
        Card(
            onClick = { onOpenTask(task.id) },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(task.name, style = MaterialTheme.typography.titleMedium)
                val detail = buildString {
                    if (hours != null) append(if (date.isBefore(today)) "実績 ${formatHours(hours)}" else "予定 ${formatHours(hours)}")
                    if (date == today && logged > 0) append("（うち記録済み ${formatHours(logged)}）")
                    if (task.deadline == date) append(if (isEmpty()) "締切日" else "・締切日")
                }
                Text(detail, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 2.dp))

                if (!date.isAfter(today) && !task.done) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                        TextButton(onClick = { onAddLog(task.id, date, -0.5) }, enabled = logged > 0) { Text("−0.5h") }
                        FilledTonalButton(onClick = { onAddLog(task.id, date, 0.5) }) { Text("+0.5h") }
                        Spacer(Modifier.width(8.dp))
                        FilledTonalButton(onClick = { onAddLog(task.id, date, 1.0) }) { Text("+1h") }
                    }
                    Text(
                        "この日にやった時間を記録できます",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
