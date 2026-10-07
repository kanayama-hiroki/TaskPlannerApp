package jp.oit.`is`.yourname.taskplanner.data

import java.time.LocalDate

/** 学習・作業ペース。必要時間に掛ける係数（早い人は少なく、ゆっくりな人は多く見積もる）。 */
enum class Pace(val label: String, val factor: Double) {
    FAST("早い", 0.8),
    NORMAL("ふつう", 1.0),
    SLOW("ゆっくり", 1.4),
}

/** 課題の中の「やること」1つ。何を、どこまでやれば済みか（goal）を持つ。 */
data class Step(
    val id: String,
    val title: String,
    /** 達成の目安（どこまでできれば、このステップを終えてよいか）。 */
    val goal: String = "",
    /** 全ステップの中での大きさ。合計時間を、この比率でステップに配分する。 */
    val weight: Int = 1,
    val done: Boolean = false,
)

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
    /** 「何を、どこまでやるか」のステップ。空なら、時間だけで進み具合を見る。 */
    val steps: List<Step> = emptyList(),
    /** 作業タイマーの開始時刻（エポックミリ秒）。計測していなければ null。 */
    val timerStartedAt: Long? = null,
) {
    /** ペース補正後の、計画に使う合計時間。 */
    val plannedHours: Double get() = totalHours * pace.factor
    val loggedHours: Double get() = logs.values.sum()
}
