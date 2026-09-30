package jp.oit.`is`.yourname.taskplanner.planner

import jp.oit.`is`.yourname.taskplanner.data.Task
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

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
     * これまでの「作業1時間あたりの習得度の伸び（%/時間）」。記録が足りなければ null。
     *
     * 習得度の記録が2日以上あれば、最初の記録を基準に、その後の伸びと作業時間から計算する。
     * 記録が1つだけのときは、0% から始めたものとして、最新の習得度とそれまでの作業時間から計算する。
     */
    fun learningRatePerHour(task: Task): Double? {
        val points = task.masteryLogs.toSortedMap().toList()
        val latest = points.lastOrNull() ?: return null

        fun hoursUpTo(date: LocalDate) = task.logs.filterKeys { !it.isAfter(date) }.values.sum()

        val gain: Double
        val hours: Double
        if (points.size >= 2) {
            val first = points.first()
            gain = (latest.second - first.second).toDouble()
            hours = hoursUpTo(latest.first) - hoursUpTo(first.first)
        } else {
            gain = latest.second.toDouble()
            hours = hoursUpTo(latest.first)
        }
        if (gain < MIN_GAIN_FOR_ESTIMATE || hours < MIN_LOGGED_FOR_ESTIMATE) return null
        return gain / hours
    }

    /** 今のペースで習得度100%に届くまでの、残りの作業時間。見積もれなければ null。 */
    fun remainingHoursByRate(task: Task): Double? {
        val rate = learningRatePerHour(task) ?: return null
        val mastery = task.mastery ?: return null
        return (100 - mastery).coerceAtLeast(0) / rate
    }

    /**
     * 自分のペースで見積もり直した合計時間（ここまでの作業時間＋残り）。
     * 「時間あたりの伸び」が計算できないときは null。
     */
    fun reestimatedTotal(task: Task): Double? {
        val remaining = remainingHoursByRate(task) ?: return null
        return task.loggedHours + remaining
    }

    /**
     * 最初の習得度の記録を出発点に、締切日に100%へ届く一定ペースだった場合の、指定日の目標習得度。
     * 習得度の記録がない、または出発点が締切以降なら null。
     */
    fun targetMastery(task: Task, date: LocalDate): Double? {
        val first = task.masteryLogs.toSortedMap().entries.firstOrNull() ?: return null
        val totalDays = ChronoUnit.DAYS.between(first.key, task.deadline)
        if (totalDays <= 0) return null
        val elapsed = ChronoUnit.DAYS.between(first.key, date).coerceIn(0, totalDays)
        return first.value + (100 - first.value) * elapsed.toDouble() / totalDays
    }

    /** 最新の習得度が、今日の目標より何ポイント先行（＋）・遅れ（−）しているか。記録が2日未満なら null。 */
    fun masteryGap(task: Task, today: LocalDate): Int? {
        if (task.masteryLogs.size < 2) return null
        val latest = task.mastery ?: return null
        val target = targetMastery(task, today) ?: return null
        return (latest - target).roundToInt()
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
    private const val MIN_GAIN_FOR_ESTIMATE = 10
    private const val MIN_LOGGED_FOR_ESTIMATE = 1.0
}
