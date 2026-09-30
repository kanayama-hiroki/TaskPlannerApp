package jp.oit.`is`.yourname.taskplanner.planner

import jp.oit.`is`.yourname.taskplanner.data.Task
import java.time.LocalDate

/**
 * 計画計算（Android 非依存の純関数）。
 *
 * 締切当日は提出日として作業を入れず、開始日〜締切前日に均等に配分する。
 * 作業できる日が3日以上ある場合は、締切前日を予備日（0時間）にする。
 * 開始日と締切が同じ日のときだけ、その日に全時間を割り当てる。
 */
object Planner {
    fun dailyHours(startDate: LocalDate, deadline: LocalDate, totalHours: Double): Map<LocalDate, Double> {
        if (deadline.isBefore(startDate) || totalHours <= 0.0) return emptyMap()

        val lastWorkDay = if (deadline.isAfter(startDate)) deadline.minusDays(1) else deadline
        val allDays = generateSequence(startDate) { it.plusDays(1) }
            .takeWhile { !it.isAfter(lastWorkDay) }
            .toList()
        val workDays = if (allDays.size >= 3) allDays.dropLast(1) else allDays

        val perDay = totalHours / workDays.size
        return workDays.associateWith { perDay }
    }

    fun dailyHours(task: Task): Map<LocalDate, Double> =
        dailyHours(task.startDate, task.deadline, task.totalHours)

    /** 未完了の課題すべてについて、日付ごとの合計作業時間を返す。 */
    fun totalsByDate(tasks: List<Task>): Map<LocalDate, Double> {
        val totals = HashMap<LocalDate, Double>()
        tasks.filter { !it.done }.forEach { task ->
            dailyHours(task).forEach { (date, h) -> totals[date] = (totals[date] ?: 0.0) + h }
        }
        return totals
    }
}
