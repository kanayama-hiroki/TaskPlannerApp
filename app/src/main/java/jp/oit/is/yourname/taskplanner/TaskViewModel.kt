package jp.oit.`is`.yourname.taskplanner

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import jp.oit.`is`.yourname.taskplanner.data.Pace
import jp.oit.`is`.yourname.taskplanner.data.Task
import jp.oit.`is`.yourname.taskplanner.data.TaskRepository
import jp.oit.`is`.yourname.taskplanner.notification.cancelNotification
import jp.oit.`is`.yourname.taskplanner.notification.cancelTimerNotification
import jp.oit.`is`.yourname.taskplanner.notification.showTimerNotification
import jp.oit.`is`.yourname.taskplanner.notification.scheduleNotification
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

class TaskViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = TaskRepository(app)

    var tasks by mutableStateOf(repository.load())
        private set

    /** 1日に使える作業時間の上限。これを超える日は警告する。 */
    var maxDailyHours by mutableStateOf(repository.loadMaxDailyHours())
        private set

    fun changeMaxDailyHours(delta: Double) {
        maxDailyHours = (maxDailyHours + delta).coerceIn(0.5, 16.0)
        repository.saveMaxDailyHours(maxDailyHours)
    }

    fun add(name: String, deadline: LocalDate, totalHours: Double, pace: Pace) {
        val task = Task(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            startDate = LocalDate.now(),
            deadline = deadline,
            totalHours = totalHours,
            pace = pace,
        )
        update(tasks + task)
        scheduleNotification(getApplication(), task)
    }

    /** 動作確認用: 過去10日分の作業時間と習得度の記録が入った課題を追加する（デバッグビルドのみ画面に出る）。 */
    fun addDemoTask() {
        val today = LocalDate.now()
        val hoursByDaysAgo = mapOf(9 to 1.5, 8 to 2.0, 6 to 2.5, 5 to 1.0, 4 to 2.0, 2 to 3.0, 1 to 1.5, 0 to 2.0)
        val masteryByDaysAgo = mapOf(9 to 5, 7 to 12, 5 to 20, 3 to 31, 1 to 38, 0 to 45)
        val task = Task(
            id = UUID.randomUUID().toString(),
            name = "【デモ】基本情報",
            startDate = today.minusDays(9),
            deadline = today.plusDays(20),
            totalHours = 100.0,
            logs = hoursByDaysAgo.mapKeys { today.minusDays(it.key.toLong()) },
            masteryLogs = masteryByDaysAgo.mapKeys { today.minusDays(it.key.toLong()) },
        )
        update(tasks + task)
    }

    fun toggleDone(id: String) = modify(id) { it.copy(done = !it.done) }

    /** その日の作業時間の記録を deltaHours だけ増減する（0 未満にはならない）。 */
    fun addLog(id: String, date: LocalDate, deltaHours: Double) = modify(id) { task ->
        val newHours = ((task.logs[date] ?: 0.0) + deltaHours).coerceAtLeast(0.0)
        val logs = if (newHours == 0.0) task.logs - date else task.logs + (date to newHours)
        task.copy(logs = logs)
    }

    /** 今日の習得度として記録する（同じ日は上書き）。 */
    fun setMastery(id: String, mastery: Int) = modify(id) {
        it.copy(masteryLogs = it.masteryLogs + (LocalDate.now() to mastery.coerceIn(0, 100)))
    }

    /** 作業タイマーを開始する。すでに計測中・完了済みの課題では何もしない。 */
    fun startTimer(id: String) {
        val task = tasks.firstOrNull { it.id == id } ?: return
        if (task.timerStartedAt != null || task.done) return
        val now = System.currentTimeMillis()
        modify(id) { it.copy(timerStartedAt = now) }
        showTimerNotification(getApplication(), task, now)
    }

    /** 作業タイマーを止める。save が true なら、開始した日の作業時間として記録する。 */
    fun stopTimer(id: String, save: Boolean) {
        val task = tasks.firstOrNull { it.id == id } ?: return
        val started = task.timerStartedAt ?: return
        val hours = (System.currentTimeMillis() - started) / MILLIS_PER_HOUR
        val date = Instant.ofEpochMilli(started).atZone(ZoneId.systemDefault()).toLocalDate()
        cancelTimerNotification(getApplication(), id)
        modify(id) {
            val logs = if (save && hours > 0) it.logs + (date to ((it.logs[date] ?: 0.0) + hours)) else it.logs
            it.copy(timerStartedAt = null, logs = logs)
        }
    }

    /** 再見積もりした合計時間で計画し直す（ペース補正は再見積もりに含まれるので ふつう に戻す）。 */
    fun applyTotalHours(id: String, totalHours: Double) = modify(id) {
        it.copy(totalHours = totalHours, pace = Pace.NORMAL)
    }

    /** 課題の名前・締切・時間・ペースを直す。通知は新しい締切で予約し直す。 */
    fun updateTask(id: String, name: String, deadline: LocalDate, totalHours: Double, pace: Pace) {
        modify(id) { it.copy(name = name.trim(), deadline = deadline, totalHours = totalHours, pace = pace) }
        tasks.firstOrNull { it.id == id }?.let { scheduleNotification(getApplication(), it) }
    }

    /** 課題を削除し、削除した課題を返す（「元に戻す」用）。 */
    fun delete(id: String): Task? {
        val removed = tasks.firstOrNull { it.id == id } ?: return null
        cancelNotification(getApplication(), id)
        cancelTimerNotification(getApplication(), id)
        update(tasks.filterNot { it.id == id })
        return removed
    }

    /** 削除した課題を元に戻す。計測中だったタイマーは止まった状態に戻る。 */
    fun restore(task: Task) {
        if (tasks.any { it.id == task.id }) return
        val restored = task.copy(timerStartedAt = null)
        update(tasks + restored)
        scheduleNotification(getApplication(), restored)
    }

    private fun modify(id: String, transform: (Task) -> Task) {
        update(tasks.map { if (it.id == id) transform(it) else it })
    }

    private companion object {
        const val MILLIS_PER_HOUR = 3_600_000.0
    }

    private fun update(newTasks: List<Task>) {
        tasks = newTasks
        repository.save(newTasks)
    }
}
