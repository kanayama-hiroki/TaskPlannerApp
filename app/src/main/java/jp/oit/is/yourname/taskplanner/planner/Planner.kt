package jp.oit.`is`.yourname.taskplanner.planner

import jp.oit.`is`.yourname.taskplanner.data.Step
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

    /** ステップの達成度（0.0〜1.0）。ステップが無ければ null。大きいステップほど重く数える。 */
    fun stepProgress(task: Task): Float? {
        val total = task.steps.sumOf { it.weight }
        if (total <= 0) return null
        return task.steps.filter { it.done }.sumOf { it.weight }.toFloat() / total
    }

    /** ステップ1つぶんの時間の目安。ペース補正後の合計時間を、重みの比率で配分する。 */
    fun stepHours(task: Task, step: Step): Double {
        val total = task.steps.sumOf { it.weight }
        return if (total <= 0) 0.0 else task.plannedHours * step.weight / total
    }

    /** 次にやるステップ（まだ終えていない先頭）。全部終えていれば null。 */
    fun nextStep(task: Task): Step? = task.steps.firstOrNull { !it.done }

    /**
     * ここまでのステップの進み方から、自分のペースで見積もり直した合計時間。
     * 「達成したステップの割合」と「かけた時間」から、全部終えるまでの時間を割り出す。
     * ステップが少ししか終わっていない、作業時間が少ない、全部終えているときは null。
     */
    fun reestimatedTotal(task: Task): Double? {
        val share = stepProgress(task) ?: return null
        if (share < MIN_STEP_SHARE || share >= 1f || task.loggedHours < MIN_LOGGED_FOR_ESTIMATE) return null
        return task.loggedHours / share
    }

    /** 上限（1日あたり）に収まる、最も早い締切日。見つからなければ null。 */
    fun earliestFeasibleDeadline(startDate: LocalDate, totalHours: Double, maxDailyHours: Double): LocalDate? {
        if (totalHours <= 0.0 || maxDailyHours <= 0.0) return null
        var deadline = startDate
        repeat(MAX_SEARCH_DAYS) {
            val plan = dailyHours(startDate, deadline, totalHours)
            if (plan.isNotEmpty() && plan.values.max() <= maxDailyHours + 1e-9) return deadline
            deadline = deadline.plusDays(1)
        }
        return null
    }

    /** 1日の合計が上限を超える日の一覧。 */
    fun overloadedDays(totals: Map<LocalDate, Double>, maxDailyHours: Double): List<LocalDate> =
        totals.filterValues { it > maxDailyHours + 1e-9 }.keys.sorted()

    private const val MAX_SEARCH_DAYS = 365 * 3
    private const val BEHIND_RATIO = 1.25
    private const val MIN_STEP_SHARE = 0.2
    private const val MIN_LOGGED_FOR_ESTIMATE = 1.0
}
