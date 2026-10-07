package jp.oit.`is`.yourname.taskplanner.planner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class StudyEstimatorTest {
    @Test
    fun findsByCommonName() {
        val e = StudyEstimator.estimate("基本情報技術者試験")
        assertNotNull(e)
        assertEquals("基本情報技術者", e!!.certName)
        assertEquals(200, e.minHours)
        assertEquals(300, e.maxHours)
        assertEquals(250, e.suggestedHours)
    }

    @Test
    fun ignoresWidthAndCaseAndSpaces() {
        assertEquals("ITパスポート", StudyEstimator.estimate("ＩＴ パスポート")!!.certName)
        assertEquals("日商簿記2級", StudyEstimator.estimate("簿記２級")!!.certName)
    }

    @Test
    fun prefersLongerMatch() {
        assertEquals("応用情報技術者", StudyEstimator.estimate("応用情報技術者")!!.certName)
    }

    @Test
    fun experiencedLevelHalvesHours() {
        val e = StudyEstimator.estimate("宅建", StudyLevel.EXPERIENCED)!!
        assertEquals(150, e.minHours)
        assertEquals(200, e.maxHours)
    }

    @Test
    fun unknownOrBlankReturnsNull() {
        assertNull(StudyEstimator.estimate("存在しない資格"))
        assertNull(StudyEstimator.estimate("  "))
    }

    @Test
    fun certWithOwnCurriculumHasConcreteSteps() {
        val e = StudyEstimator.estimate("基本情報")!!
        assertEquals(true, e.hasOwnSteps)
        val steps = StudyEstimator.stepsFor(e.certName)
        assertEquals(5, steps.size)
        assertEquals(true, steps.any { it.title.contains("アルゴリズム") })
    }

    @Test
    fun certWithoutCurriculumFallsBackToGenericSteps() {
        val e = StudyEstimator.estimate("行政書士")!!
        assertEquals(false, e.hasOwnSteps)
        assertEquals(3, StudyEstimator.stepsFor(e.certName).size)
        assertEquals(3, StudyEstimator.stepsFor("存在しない資格").size)
    }

    @Test
    fun everyStepHasTitleGoalAndPositiveWeight() {
        listOf("ITパスポート", "基本情報", "応用情報", "簿記3級", "簿記2級", "FP3級", "FP2級", "宅建", "乙4", "電気工事士", "G検定", "AWS")
            .forEach { name ->
                val e = StudyEstimator.estimate(name)!!
                assertEquals(name, true, e.hasOwnSteps)
                StudyEstimator.stepsFor(e.certName).forEach {
                    assertEquals(false, it.title.isBlank())
                    assertEquals(false, it.goal.isBlank())
                    assertEquals(true, it.weight > 0)
                }
            }
    }
}
