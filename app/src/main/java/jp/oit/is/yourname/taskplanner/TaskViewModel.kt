package jp.oit.`is`.yourname.taskplanner

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
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

    fun add(name: String, deadline: LocalDate, totalHours: Double) {
        val task = Task(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            startDate = LocalDate.now(),
            deadline = deadline,
            totalHours = totalHours,
        )
        update(tasks + task)
        scheduleNotification(getApplication(), task)
    }

    fun toggleDone(id: String) {
        update(tasks.map { if (it.id == id) it.copy(done = !it.done) else it })
    }

    fun delete(id: String) {
        cancelNotification(getApplication(), id)
        update(tasks.filterNot { it.id == id })
    }

    private fun update(newTasks: List<Task>) {
        tasks = newTasks
        repository.save(newTasks)
    }
}
