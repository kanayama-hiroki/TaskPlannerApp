package jp.oit.`is`.yourname.taskplanner.planner

import java.text.Normalizer
import kotlin.math.roundToInt

enum class StudyLevel(val label: String, val factor: Double) {
    BEGINNER("初心者", 1.0),
    EXPERIENCED("経験あり", 0.5),
}

data class StudyEstimate(
    val certName: String,
    val minHours: Int,
    val maxHours: Int,
    /** 合計時間の初期値として使う中間値。 */
    val suggestedHours: Int,
)

/**
 * 資格名から標準的な勉強時間の目安を返す（内蔵の表による推定。ネットワーク不要）。
 * 数値は一般的に言われる「初心者が合格するまでの目安」で、個人差が大きい。
 */
object StudyEstimator {
    private class Entry(val name: String, val aliases: List<String>, val min: Int, val max: Int)

    private val entries = listOf(
        Entry("ITパスポート", listOf("itパスポート", "ipパス"), 100, 150),
        Entry("情報セキュリティマネジメント", listOf("情報セキュリティマネジメント", "sg試験"), 100, 150),
        Entry("基本情報技術者", listOf("基本情報", "fe試験"), 200, 300),
        Entry("応用情報技術者", listOf("応用情報", "ap試験"), 300, 500),
        Entry("日商簿記3級", listOf("簿記3級", "簿記三級"), 50, 100),
        Entry("日商簿記2級", listOf("簿記2級", "簿記二級"), 150, 250),
        Entry("日商簿記1級", listOf("簿記1級", "簿記一級"), 500, 1000),
        Entry("FP3級", listOf("fp3級", "ファイナンシャルプランナー3級", "fp三級"), 40, 60),
        Entry("FP2級", listOf("fp2級", "ファイナンシャルプランナー2級", "fp二級"), 150, 250),
        Entry("宅地建物取引士", listOf("宅建"), 300, 400),
        Entry("行政書士", listOf("行政書士"), 600, 1000),
        Entry("社会保険労務士", listOf("社労士", "社会保険労務士"), 800, 1000),
        Entry("中小企業診断士", listOf("診断士", "中小企業診断士"), 800, 1200),
        Entry("危険物取扱者乙種4類", listOf("乙4", "乙種4類", "危険物取扱者"), 20, 40),
        Entry("第二種電気工事士", listOf("電気工事士", "二種電気工事士"), 60, 100),
        Entry("電験三種", listOf("電験三種", "電験3種", "第三種電気主任技術者"), 500, 1000),
        Entry("G検定", listOf("g検定", "ジェネラリスト検定"), 30, 50),
        Entry("AWS認定ソリューションアーキテクト", listOf("aws", "ソリューションアーキテクト"), 60, 100),
    )

    private fun normalize(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFKC).lowercase().filterNot { it.isWhitespace() }

    fun estimate(input: String, level: StudyLevel = StudyLevel.BEGINNER): StudyEstimate? {
        val query = normalize(input)
        if (query.isEmpty()) return null

        // 別名が長く一致するものを優先（例:「応用情報」が「情報」より優先）
        val entry = entries
            .flatMap { e -> e.aliases.map { alias -> normalize(alias) to e } }
            .filter { (alias, _) -> query.contains(alias) }
            .maxByOrNull { (alias, _) -> alias.length }
            ?.second ?: return null

        val min = (entry.min * level.factor).roundToInt()
        val max = (entry.max * level.factor).roundToInt()
        return StudyEstimate(entry.name, min, max, ((min + max) / 2.0).roundToInt())
    }
}
