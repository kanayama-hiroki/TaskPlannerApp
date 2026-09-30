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

    fun delete(id: String) {
        cancelNotification(getApplication(), id)
        cancelTimerNotification(getApplication(), id)
        update(tasks.filterNot { it.id == id })
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
