package jp.oit.`is`.yourname.taskplanner.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** 課題を端末内の SharedPreferences に JSON で保存する。 */
class TaskRepository(context: Context) {
    private val prefs = context.getSharedPreferences("tasks", Context.MODE_PRIVATE)

    fun load(): List<Task> {
        val json = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                Task(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    startDate = LocalDate.parse(o.getString("startDate")),
                    deadline = LocalDate.parse(o.getString("deadline")),
                    totalHours = o.getDouble("totalHours"),
                    done = o.optBoolean("done", false),
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun save(tasks: List<Task>) {
        val array = JSONArray()
        tasks.forEach { t ->
            array.put(
                JSONObject()
                    .put("id", t.id)
                    .put("name", t.name)
                    .put("startDate", t.startDate.toString())
                    .put("deadline", t.deadline.toString())
                    .put("totalHours", t.totalHours)
                    .put("done", t.done)
            )
        }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    private companion object {
        const val KEY = "tasks_json"
    }
}
