package jp.oit.`is`.yourname.taskplanner.planner

import java.text.Normalizer
import kotlin.math.roundToInt

enum class StudyLevel(val label: String, val factor: Double) {
    BEGINNER("初心者", 1.0),
    EXPERIENCED("経験あり", 0.5),
}

/** 学習ステップのひな形。「何を」（title）を「どこまで」（goal）やれば終わりか、と、時間配分の重み。 */
class StepTemplate(val title: String, val goal: String, val weight: Int)

data class StudyEstimate(
    val certName: String,
    val minHours: Int,
    val maxHours: Int,
    /** 合計時間の初期値として使う中間値。 */
    val suggestedHours: Int,
    /** その資格専用のステップを内蔵しているか。false のときは共通の3段階が入る。 */
    val hasOwnSteps: Boolean,
)

/**
 * 資格名から標準的な勉強時間の目安と、学習ステップのひな形を返す（内蔵の表による。ネットワーク不要）。
 * 数値は一般的に言われる「初心者が合格するまでの目安」で、個人差が大きい。
 * 合格基準や出題範囲は改定されることがあるので、受験前に公式の案内を確認すること。
 */
object StudyEstimator {
    private class Entry(
        val name: String,
        val aliases: List<String>,
        val min: Int,
        val max: Int,
        val steps: List<StepTemplate> = emptyList(),
    )

    private fun step(title: String, goal: String, weight: Int) = StepTemplate(title, goal, weight)

    /** 専用のステップを持たない資格に使う、共通の3段階。 */
    private val genericSteps = listOf(
        step("テキスト・講義で、全体を一通り学ぶ", "章ごとの内容を、自分の言葉で説明できる", 4),
        step("問題集を解いて、間違えた所を潰す", "問題集の正答率が7割を超える", 4),
        step("過去問・模試で、合格点を確かめる", "公式の合格基準を、過去問で安定して超える", 3),
    )

    private val entries = listOf(
        Entry(
            "ITパスポート", listOf("itパスポート", "ipパス"), 100, 150,
            listOf(
                step("ストラテジ系（経営・法務）", "用語を覚え、章末問題で7割正解できる", 3),
                step("マネジメント系（プロジェクト・サービス管理）", "用語を覚え、章末問題で7割正解できる", 2),
                step("テクノロジ系（ネットワーク・セキュリティ・データベース）", "用語と仕組みを覚え、章末問題で7割正解できる", 4),
                step("過去問を解く", "過去問で総合600点以上・3分野とも300点以上（合格基準）を取れる", 3),
            ),
        ),
        Entry("情報セキュリティマネジメント", listOf("情報セキュリティマネジメント", "sg試験"), 100, 150),
        Entry(
            "基本情報技術者", listOf("基本情報", "fe試験"), 200, 300,
            listOf(
                step("科目A：テクノロジ系（基礎理論・コンピュータ・ネットワーク・データベース・セキュリティ）", "各分野の問題集で7割正解できる", 5),
                step("科目A：マネジメント系・ストラテジ系", "各分野の問題集で7割正解できる", 2),
                step("科目B：アルゴリズムと擬似言語", "擬似言語のプログラムを読んで、変数の変化を手で追える", 4),
                step("科目B：情報セキュリティ", "事例問題の読み方に慣れ、問題集で7割正解できる", 1),
                step("過去問・公開問題を解く", "科目A・科目Bとも600点以上（合格基準）を取れる", 3),
            ),
        ),
        Entry(
            "応用情報技術者", listOf("応用情報", "ap試験"), 300, 500,
            listOf(
                step("午前：テクノロジ・マネジメント・ストラテジ", "午前の過去問で6割（合格基準）を超える", 5),
                step("午後：必須の情報セキュリティ", "過去問の記述問題で、設問の意図に沿って答えられる", 3),
                step("午後：選択分野を決めて演習する", "選んだ分野の過去問で6割を超える", 5),
                step("午前・午後の通し演習", "過去問を時間内に解いて、午前・午後とも6割（合格基準）を超える", 3),
            ),
        ),
        Entry(
            "日商簿記3級", listOf("簿記3級", "簿記三級"), 50, 100,
            listOf(
                step("仕訳の基本（現金預金・商品売買・債権債務）", "取引を見て、仕訳を見ずに書ける", 3),
                step("決算整理（減価償却・貸倒引当金・経過勘定）", "決算整理仕訳を、見ずに書ける", 3),
                step("試算表・精算表・財務諸表を作る", "精算表と貸借対照表・損益計算書を、時間内に作れる", 2),
                step("過去問・予想問題を解く", "100点満点で70点以上（合格基準）を取れる", 3),
            ),
        ),
        Entry(
            "日商簿記2級", listOf("簿記2級", "簿記二級"), 150, 250,
            listOf(
                step("商業簿記：株式・固定資産・本支店・連結", "各論点の仕訳を、見ずに書ける", 4),
                step("工業簿記：費目別・個別・総合原価計算", "原価計算表と仕掛品の勘定を、自分で作れる", 4),
                step("標準原価・直接原価・CVP分析", "計算の手順を、自分の言葉で説明できる", 2),
                step("過去問・予想問題を解く", "100点満点で70点以上（合格基準）を取れる", 3),
            ),
        ),
        Entry("日商簿記1級", listOf("簿記1級", "簿記一級"), 500, 1000),
        Entry(
            "FP3級", listOf("fp3級", "ファイナンシャルプランナー3級", "fp三級"), 40, 60,
            listOf(
                step("ライフプランニングと資金計画（年金・社会保険）", "各制度の仕組みと数字を、問題集で7割正解できる", 2),
                step("リスク管理（保険）・金融資産運用", "保険の種類と運用商品の違いを、問題集で7割正解できる", 2),
                step("タックスプランニング（所得税）", "所得の種類と控除の計算を、問題集で7割正解できる", 2),
                step("不動産・相続・事業承継", "用語と税の基本を、問題集で7割正解できる", 2),
                step("過去問を解く", "学科・実技とも6割以上（合格基準）を取れる", 3),
            ),
        ),
        Entry(
            "FP2級", listOf("fp2級", "ファイナンシャルプランナー2級", "fp二級"), 150, 250,
            listOf(
                step("ライフプランニング・リスク管理・金融資産運用", "計算問題を、電卓を使って解ける", 4),
                step("タックスプランニング・不動産・相続・事業承継", "税額の計算を、自分で最後まで出せる", 4),
                step("実技の事例問題", "事例問題を、時間内に解ける", 3),
                step("過去問を解く", "学科・実技とも6割以上（合格基準）を取れる", 3),
            ),
        ),
        Entry(
            "宅地建物取引士", listOf("宅建"), 300, 400,
            listOf(
                step("宅建業法（20問・得点源）", "過去問で、18問以上正解できる", 4),
                step("権利関係（民法など・14問）", "過去問で、8問以上正解できる", 5),
                step("法令上の制限（8問）", "過去問で、6問以上正解できる", 2),
                step("税・その他（8問）", "頻出の論点を覚え、過去問で5問以上正解できる", 1),
                step("過去問を年度ごとに解く", "50問で、35点以上（合格点は年によって変わる）を取れる", 4),
            ),
        ),
        Entry("行政書士", listOf("行政書士"), 600, 1000),
        Entry("社会保険労務士", listOf("社労士", "社会保険労務士"), 800, 1000),
        Entry("中小企業診断士", listOf("診断士", "中小企業診断士"), 800, 1200),
        Entry(
            "危険物取扱者乙種4類", listOf("乙4", "乙種4類", "危険物取扱者"), 20, 40,
            listOf(
                step("基礎的な物理学・基礎的な化学", "燃焼・物質の性質の問題を、問題集で6割以上正解できる", 3),
                step("危険物に関する法令", "指定数量・貯蔵の基準を覚え、問題集で6割以上正解できる", 4),
                step("危険物の性質・火災予防・消火の方法", "第4類の物質の性質を覚え、問題集で6割以上正解できる", 4),
                step("過去問を解く", "3科目とも6割以上（合格基準）を取れる", 2),
            ),
        ),
        Entry(
            "第二種電気工事士", listOf("電気工事士", "二種電気工事士"), 60, 100,
            listOf(
                step("筆記：電気理論・配線設計", "計算問題を、公式を見ずに解ける", 3),
                step("筆記：電気機器・器具・材料・工事方法・法令", "器具や材料の写真を見て、名前と用途が分かる", 3),
                step("筆記：過去問を解く", "筆記で6割以上（合格基準）を取れる", 2),
                step("技能：複線図を描く練習", "候補問題の複線図を、間違えずに描ける", 3),
                step("技能：候補問題を作る練習", "候補問題を、制限時間内に欠陥なく作れる", 5),
            ),
        ),
        Entry("電験三種", listOf("電験三種", "電験3種", "第三種電気主任技術者"), 500, 1000),
        Entry(
            "G検定", listOf("g検定", "ジェネラリスト検定"), 30, 50,
            listOf(
                step("人工知能の定義と、これまでの歴史", "ブームの流れと、主な用語を説明できる", 1),
                step("機械学習の手法", "教師あり・教師なし・強化学習の違いと、代表的な手法を説明できる", 3),
                step("ディープラーニング", "CNN・RNN・Transformer などの特徴を説明できる", 3),
                step("AIの応用・社会実装と、倫理・法律", "活用事例と、データ・著作権まわりのルールを説明できる", 2),
                step("模擬問題を解く", "模擬問題で7割程度（合格ラインの目安）正解できる", 3),
            ),
        ),
        Entry(
            "AWS認定ソリューションアーキテクト", listOf("aws", "ソリューションアーキテクト"), 60, 100,
            listOf(
                step("セキュアなアーキテクチャの設計", "IAM・暗号化・ネットワーク分離を、使い分けて説明できる", 3),
                step("弾力性・耐障害性のあるアーキテクチャの設計", "マルチAZ・負荷分散・Auto Scaling の構成を、自分で描ける", 3),
                step("高パフォーマンス・コスト最適化の設計", "ストレージやDBの種類を、要件に合わせて選べる", 3),
                step("模擬試験を解く", "模擬試験で720点以上（合格スコア）を取れる", 3),
            ),
        ),
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
        return StudyEstimate(entry.name, min, max, ((min + max) / 2.0).roundToInt(), entry.steps.isNotEmpty())
    }

    /** 資格の正式名（[StudyEstimate.certName]）から、学習ステップのひな形を返す。専用の物が無ければ共通の3段階。 */
    fun stepsFor(certName: String): List<StepTemplate> =
        entries.firstOrNull { it.name == certName }?.steps?.takeIf { it.isNotEmpty() } ?: genericSteps
}
