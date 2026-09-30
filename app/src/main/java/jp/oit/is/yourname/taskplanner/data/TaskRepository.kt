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
                    pace = Pace.entries.firstOrNull { it.name == o.optString("pace") } ?: Pace.NORMAL,
                    logs = o.optJSONObject("logs")?.let { l ->
                        l.keys().asSequence().associate { k -> LocalDate.parse(k) to l.getDouble(k) }
                    } ?: emptyMap(),
                    mastery = o.optInt("mastery", -1).takeIf { it >= 0 },
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
                    .put("pace", t.pace.name)
                    .put("logs", JSONObject().apply { t.logs.forEach { (d, h) -> put(d.toString(), h) } })
                    .put("mastery", t.mastery ?: -1)
            )
        }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    fun loadMaxDailyHours(): Double =
        prefs.getFloat(KEY_MAX_DAILY, DEFAULT_MAX_DAILY_HOURS.toFloat()).toDouble()

    fun saveMaxDailyHours(hours: Double) {
        prefs.edit().putFloat(KEY_MAX_DAILY, hours.toFloat()).apply()
    }

    companion object {
        const val DEFAULT_MAX_DAILY_HOURS = 3.0
        private const val KEY = "tasks_json"
        private const val KEY_MAX_DAILY = "max_daily_hours"
    }
}
