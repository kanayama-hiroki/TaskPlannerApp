package jp.oit.`is`.yourname.taskplanner.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import jp.oit.`is`.yourname.taskplanner.planner.Planner
import jp.oit.`is`.yourname.taskplanner.planner.StudyEstimator
import jp.oit.`is`.yourname.taskplanner.planner.StudyLevel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskFormScreen(
    onSave: (name: String, deadline: LocalDate, totalHours: Double) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onCancel)

    var name by rememberSaveable { mutableStateOf("") }
    var deadlineText by rememberSaveable { mutableStateOf("") }
    var hoursText by rememberSaveable { mutableStateOf("") }
    var showPicker by rememberSaveable { mutableStateOf(false) }
    var submitted by rememberSaveable { mutableStateOf(false) }
    var levelName by rememberSaveable { mutableStateOf(StudyLevel.BEGINNER.name) }
    var estimateMessage by rememberSaveable { mutableStateOf("") }
    val level = StudyLevel.valueOf(levelName)

    val today = LocalDate.now()
    val deadline = deadlineText.takeIf { it.isNotEmpty() }?.let(LocalDate::parse)
    val hours = hoursText.toDoubleOrNull()

    val nameError = if (submitted && name.isBlank()) "課題名を入力してね" else null
    val deadlineError = when {
        !submitted -> null
        deadline == null -> "締切日を選んでね"
        deadline.isBefore(today) -> "締切日が既に過ぎているようです"
        else -> null
    }
    val hoursError = if (submitted && (hours == null || hours <= 0)) "合計時間は正の数字で入力してね" else null

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(24.dp),
    ) {
        Text(
            text = "課題を追加",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 24.dp),
        )

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("課題名（例：プログラミング演習）") },
            isError = nameError != null,
            supportingText = nameError?.let { { Text(it) } },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        )

        Text("資格名を入れると、勉強時間の目安を出せます", style = MaterialTheme.typography.bodySmall)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        ) {
            StudyLevel.entries.forEach { l ->
                FilterChip(
                    selected = l == level,
                    onClick = { levelName = l.name },
                    label = { Text(l.label) },
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
            TextButton(onClick = {
                val e = StudyEstimator.estimate(name, level)
                if (e == null) {
                    estimateMessage = "この資格は内蔵の目安にありません。合計時間を自分で入力してね"
                } else {
                    hoursText = e.suggestedHours.toString()
                    estimateMessage = "${e.certName}の目安は${e.minHours}〜${e.maxHours}時間（${level.label}）。" +
                        "合計時間に${e.suggestedHours}時間を入れました。個人差が大きいので、自由に直してね"
                }
            }) { Text("勉強時間を推定") }
        }
        if (estimateMessage.isNotEmpty()) {
            Text(
                text = estimateMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }

        OutlinedButton(
            onClick = { showPicker = true },
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
        ) {
            Icon(Icons.Default.DateRange, contentDescription = null)
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text(if (deadline == null) "締切日を選ぶ" else "締切日: $deadline")
        }
        Text(
            text = deadlineError ?: " ",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 8.dp),
        )

        OutlinedTextField(
            value = hoursText,
            onValueChange = { hoursText = it },
            label = { Text("完了に必要な合計時間 (時間)") },
            isError = hoursError != null,
            supportingText = hoursError?.let { { Text(it) } },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        )

        if (deadline != null && !deadline.isBefore(today) && hours != null && hours > 0) {
            val plan = Planner.dailyHours(today, deadline, hours)
            if (plan.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                ) {
                    Text(
                        text = "今日から${plan.size}日間、毎日 ${formatHours(plan.values.first())} 進めましょう！",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }

        Button(
            onClick = {
                submitted = true
                if (name.isNotBlank() && deadline != null && !deadline.isBefore(today) &&
                    hours != null && hours > 0
                ) {
                    onSave(name, deadline, hours)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(12.dp),
        ) { Text("計画に追加する") }

        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("キャンセル") }
    }

    if (showPicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (deadline ?: today).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        deadlineText = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()
                    }
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("キャンセル") } },
        ) { DatePicker(state = state) }
    }
}
