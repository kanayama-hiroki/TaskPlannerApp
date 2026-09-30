package jp.oit.`is`.yourname.taskplanner.planner

import jp.oit.`is`.yourname.taskplanner.data.Task
import java.time.LocalDate

enum class PlanStatus { DONE, ON_TRACK, BEHIND, OVERDUE }

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

    /** ペース補正後の合計時間で、開始日から均等に配分した「当初の計画」。 */
    fun initialPlan(task: Task): Map<LocalDate, Double> =
        dailyHours(task.startDate, task.deadline, task.plannedHours)

    /**
     * 記録を反映した現在の計画。
     * 過去〜今日は実際の記録、今日以降は「残り時間」を締切まで振り直した予定を合算する。
     */
    fun plan(task: Task, today: LocalDate): Map<LocalDate, Double> {
        val result = HashMap<LocalDate, Double>()
        task.logs.forEach { (d, h) -> if (h > 0) result[d] = h }

        if (!task.done && !today.isAfter(task.deadline)) {
            val remaining = task.plannedHours - task.loggedHours
            val from = if (today.isAfter(task.startDate)) today else task.startDate
            dailyHours(from, task.deadline, remaining).forEach { (d, h) ->
                result[d] = (result[d] ?: 0.0) + h
            }
        }
        return result
    }

    /** 未完了の課題すべてについて、日付ごとの合計作業時間（記録＋予定）を返す。 */
    fun totalsByDate(tasks: List<Task>, today: LocalDate): Map<LocalDate, Double> {
        val totals = HashMap<LocalDate, Double>()
        tasks.filter { !it.done }.forEach { task ->
            plan(task, today).forEach { (date, h) -> totals[date] = (totals[date] ?: 0.0) + h }
        }
        return totals
    }

    /** 時間ベースの進み具合（0.0〜1.0）。 */
    fun progress(task: Task): Float {
        if (task.plannedHours <= 0.0) return 0f
        return (task.loggedHours / task.plannedHours).coerceIn(0.0, 1.0).toFloat()
    }

    fun remainingHours(task: Task): Double = (task.plannedHours - task.loggedHours).coerceAtLeast(0.0)

    /** 記録から見た、締切に間に合いそうかの判定。 */
    fun status(task: Task, today: LocalDate): PlanStatus {
        val remaining = remainingHours(task)
        if (task.done || remaining <= 0.0) return PlanStatus.DONE
        if (today.isAfter(task.deadline)) return PlanStatus.OVERDUE

        val from = if (today.isAfter(task.startDate)) today else task.startDate
        val requiredPerDay = dailyHours(from, task.deadline, remaining).values.firstOrNull() ?: return PlanStatus.OVERDUE
        val originalPerDay = initialPlan(task).values.firstOrNull() ?: return PlanStatus.ON_TRACK
        return if (requiredPerDay > originalPerDay * BEHIND_RATIO) PlanStatus.BEHIND else PlanStatus.ON_TRACK
    }

    /**
     * 自己評価の習得度から、自分の場合に必要な合計時間を再見積もりする。
     * 「ここまでの作業時間 ÷ 習得度」。記録が少なすぎる・習得度が低すぎると当てにならないので null。
     */
    fun reestimatedTotal(task: Task): Double? {
        val mastery = task.mastery ?: return null
        if (mastery < MIN_MASTERY_FOR_ESTIMATE || task.loggedHours < MIN_LOGGED_FOR_ESTIMATE) return null
        return task.loggedHours / (mastery / 100.0)
    }

    private const val BEHIND_RATIO = 1.25
    private const val MIN_MASTERY_FOR_ESTIMATE = 10
    private const val MIN_LOGGED_FOR_ESTIMATE = 1.0
}
