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
}
