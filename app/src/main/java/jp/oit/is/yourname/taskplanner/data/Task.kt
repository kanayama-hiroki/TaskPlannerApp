package jp.oit.`is`.yourname.taskplanner.data

import java.time.LocalDate

/** 学習・作業ペース。必要時間に掛ける係数（早い人は少なく、ゆっくりな人は多く見積もる）。 */
enum class Pace(val label: String, val factor: Double) {
    FAST("早い", 0.8),
    NORMAL("ふつう", 1.0),
    SLOW("ゆっくり", 1.4),
}

data class Task(
    val id: String,
    val name: String,
    val startDate: LocalDate,
    val deadline: LocalDate,
    /** 入力された（ペース補正前の）合計時間。 */
    val totalHours: Double,
    val done: Boolean = false,
    val pace: Pace = Pace.NORMAL,
    /** 日ごとの実際の作業時間の記録。 */
    val logs: Map<LocalDate, Double> = emptyMap(),
    /** 自己評価の習得度（0〜100）。未入力は null。 */
    val mastery: Int? = null,
) {
    /** ペース補正後の、計画に使う合計時間。 */
    val plannedHours: Double get() = totalHours * pace.factor
    val loggedHours: Double get() = logs.values.sum()
}
