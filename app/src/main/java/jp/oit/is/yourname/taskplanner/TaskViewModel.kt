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
import jp.oit.`is`.yourname.taskplanner.notification.scheduleNotification
import java.time.LocalDate
import java.util.UUID

class TaskViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = TaskRepository(app)

    var tasks by mutableStateOf(repository.load())
        private set

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

    fun setMastery(id: String, mastery: Int) = modify(id) { it.copy(mastery = mastery.coerceIn(0, 100)) }

    /** 再見積もりした合計時間で計画し直す（ペース補正は再見積もりに含まれるので ふつう に戻す）。 */
    fun applyTotalHours(id: String, totalHours: Double) = modify(id) {
        it.copy(totalHours = totalHours, pace = Pace.NORMAL)
    }

    fun delete(id: String) {
        cancelNotification(getApplication(), id)
        update(tasks.filterNot { it.id == id })
    }

    private fun modify(id: String, transform: (Task) -> Task) {
        update(tasks.map { if (it.id == id) transform(it) else it })
    }

    private fun update(newTasks: List<Task>) {
        tasks = newTasks
        repository.save(newTasks)
    }
}
