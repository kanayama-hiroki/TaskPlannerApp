package jp.oit.`is`.yourname.taskplanner.planner

import jp.oit.`is`.yourname.taskplanner.data.Pace
import jp.oit.`is`.yourname.taskplanner.data.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PlannerTest {
    private val d = { day: Int -> LocalDate.of(2026, 10, day) }

    @Test
    fun spreadsEvenlyAndKeepsBufferDay() {
        // 10/1〜10/6 締切: 作業日は 10/1〜10/5、10/5 は予備日 → 4日で 8h
        val plan = Planner.dailyHours(d(1), d(6), 8.0)
        assertEquals(listOf(d(1), d(2), d(3), d(4)), plan.keys.sorted())
        plan.values.forEach { assertEquals(2.0, it, 1e-9) }
        assertEquals(8.0, plan.values.sum(), 1e-9)
    }

    @Test
    fun twoDaysLeftUsesBothDays() {
        val plan = Planner.dailyHours(d(1), d(3), 4.0)
        assertEquals(listOf(d(1), d(2)), plan.keys.sorted())
        assertEquals(2.0, plan[d(1)]!!, 1e-9)
    }

    @Test
    fun oneDayLeftPutsEverythingOnThatDay() {
        val plan = Planner.dailyHours(d(1), d(2), 3.0)
        assertEquals(mapOf(d(1) to 3.0), plan)
    }

    @Test
    fun deadlineTodayUsesToday() {
        val plan = Planner.dailyHours(d(1), d(1), 3.0)
        assertEquals(mapOf(d(1) to 3.0), plan)
    }

    @Test
    fun pastDeadlineOrNoHoursGivesEmptyPlan() {
        assertTrue(Planner.dailyHours(d(5), d(1), 3.0).isEmpty())
        assertTrue(Planner.dailyHours(d(1), d(5), 0.0).isEmpty())
    }

    @Test
    fun paceScalesPlannedHours() {
        val slow = Task("a", "A", d(1), d(3), 4.0, pace = Pace.SLOW)
        assertEquals(5.6, slow.plannedHours, 1e-9)
        val fast = Task("b", "B", d(1), d(3), 5.0, pace = Pace.FAST)
        assertEquals(4.0, fast.plannedHours, 1e-9)
    }

    @Test
    fun totalsSumOverlappingTasksAndSkipDone() {
        val a = Task("a", "A", d(1), d(3), 4.0) // 10/1, 10/2 に 2h
        val b = Task("b", "B", d(1), d(3), 2.0) // 10/1, 10/2 に 1h
        val c = Task("c", "C", d(1), d(3), 100.0, done = true)
        val totals = Planner.totalsByDate(listOf(a, b, c), today = d(1))
        assertEquals(3.0, totals[d(1)]!!, 1e-9)
        assertEquals(3.0, totals[d(2)]!!, 1e-9)
        assertNull(totals[d(3)])
    }

    @Test
    fun planReplansRemainingHoursAfterLogging() {
        // 10/1〜10/6 締切、8h。10/1 に 1h だけやった状態で 10/2 になった
        val task = Task("a", "A", d(1), d(6), 8.0, logs = mapOf(d(1) to 1.0))
        val plan = Planner.plan(task, today = d(2))
        assertEquals(1.0, plan[d(1)]!!, 1e-9) // 過去は実績
        // 残り 7h を 10/2〜10/5（10/5 は予備日）→ 3日で 7/3h
        assertEquals(7.0 / 3, plan[d(2)]!!, 1e-9)
        assertEquals(7.0 / 3, plan[d(4)]!!, 1e-9)
        assertNull(plan[d(5)])
        assertEquals(8.0, plan.values.sum(), 1e-9)
    }

    @Test
    fun statusDetectsBehindAndDone() {
        val onTrack = Task("a", "A", d(1), d(6), 8.0, logs = mapOf(d(1) to 2.0))
        assertEquals(PlanStatus.ON_TRACK, Planner.status(onTrack, today = d(2)))

        val behind = Task("b", "B", d(1), d(6), 8.0) // 10/3 まで何もしていない
        assertEquals(PlanStatus.BEHIND, Planner.status(behind, today = d(3)))

        val overdue = Task("c", "C", d(1), d(3), 8.0)
        assertEquals(PlanStatus.OVERDUE, Planner.status(overdue, today = d(5)))

        val finished = Task("d", "D", d(1), d(3), 2.0, logs = mapOf(d(1) to 2.0))
        assertEquals(PlanStatus.DONE, Planner.status(finished, today = d(2)))
    }

    @Test
    fun reestimateFromSingleMasteryRecordAssumesStartingAtZero() {
        // 5h やって習得度 25% → 1時間あたり 5%。残り 75% は 15h → 合計 20h
        val task = Task("a", "A", d(1), d(20), 10.0, logs = mapOf(d(1) to 5.0), masteryLogs = mapOf(d(1) to 25))
        assertEquals(5.0, Planner.learningRatePerHour(task)!!, 1e-9)
        assertEquals(15.0, Planner.remainingHoursByRate(task)!!, 1e-9)
        assertEquals(20.0, Planner.reestimatedTotal(task)!!, 1e-9)
    }

    @Test
    fun reestimateUsesGrowthBetweenMasteryRecords() {
        // 10/1 に習得度 20%（その日までに 2h）、10/5 に 60%（その日までに合計 10h）
        // 伸び 40% ÷ 追加作業 8h = 5%/h。残り 40% は 8h → 合計 18h
        val logs = mapOf(d(1) to 2.0, d(3) to 4.0, d(5) to 4.0)
        val task = Task("a", "A", d(1), d(20), 10.0, logs = logs, masteryLogs = mapOf(d(1) to 20, d(5) to 60))
        assertEquals(5.0, Planner.learningRatePerHour(task)!!, 1e-9)
        assertEquals(18.0, Planner.reestimatedTotal(task)!!, 1e-9)
    }

    @Test
    fun reestimateNeedsEnoughData() {
        val task = Task("a", "A", d(1), d(20), 10.0, logs = mapOf(d(1) to 5.0), masteryLogs = mapOf(d(1) to 25))
        assertNull(Planner.reestimatedTotal(task.copy(masteryLogs = mapOf(d(1) to 5)))) // 伸びが小さすぎる
        assertNull(Planner.reestimatedTotal(task.copy(masteryLogs = emptyMap())))
        assertNull(Planner.reestimatedTotal(task.copy(logs = mapOf(d(1) to 0.5)))) // 作業が少なすぎる
    }

    @Test
    fun latestMasteryIsTheMostRecentRecord() {
        val task = Task("a", "A", d(1), d(20), 10.0, masteryLogs = mapOf(d(2) to 40, d(1) to 10, d(3) to 35))
        assertEquals(35, task.mastery)
    }

    @Test
    fun progressIsCappedAtOne() {
        val task = Task("a", "A", d(1), d(5), 2.0, logs = mapOf(d(1) to 5.0))
        assertEquals(1f, Planner.progress(task), 1e-6f)
    }
}

class PlannerLimitTest {
    private val d = { day: Int -> LocalDate.of(2026, 10, day) }

    @Test
    fun earliestFeasibleDeadlineFitsWithinDailyLimit() {
        // 6h を 1日 2h 以内: 作業3日 + 予備日1日 + 締切日 → 10/1 開始なら 10/5 締切（作業 10/1〜10/3、予備 10/4）
        val deadline = Planner.earliestFeasibleDeadline(d(1), 6.0, 2.0)!!
        val plan = Planner.dailyHours(d(1), deadline, 6.0)
        assertTrue(plan.values.max() <= 2.0 + 1e-9)
        // 1日早い締切では上限を超える
        val earlier = Planner.dailyHours(d(1), deadline.minusDays(1), 6.0)
        assertTrue(earlier.isEmpty() || earlier.values.max() > 2.0)
    }

    @Test
    fun overloadedDaysListsDaysOverLimit() {
        val totals = mapOf(d(1) to 2.0, d(2) to 3.5, d(3) to 3.0)
        assertEquals(listOf(d(2)), Planner.overloadedDays(totals, 3.0))
    }

    @Test
    fun invalidInputsGiveNull() {
        assertNull(Planner.earliestFeasibleDeadline(d(1), 0.0, 2.0))
        assertNull(Planner.earliestFeasibleDeadline(d(1), 5.0, 0.0))
    }
}

class PlannerMasteryTargetTest {
    private val d = { day: Int -> LocalDate.of(2026, 10, day) }

    @Test
    fun targetMasteryRisesLinearlyToHundredAtDeadline() {
        val task = Task("a", "A", d(1), d(11), 10.0, masteryLogs = mapOf(d(1) to 10))
        assertEquals(10.0, Planner.targetMastery(task, d(1))!!, 1e-9)
        assertEquals(55.0, Planner.targetMastery(task, d(6))!!, 1e-9)
        assertEquals(100.0, Planner.targetMastery(task, d(11))!!, 1e-9)
        assertEquals(100.0, Planner.targetMastery(task, d(20))!!, 1e-9) // 締切後は100%で頭打ち
    }

    @Test
    fun masteryGapComparesLatestWithTodaysTarget() {
        val ahead = Task("a", "A", d(1), d(11), 10.0, masteryLogs = mapOf(d(1) to 10, d(6) to 60))
        assertEquals(5, Planner.masteryGap(ahead, today = d(6)))
        val behind = ahead.copy(masteryLogs = mapOf(d(1) to 10, d(6) to 40))
        assertEquals(-15, Planner.masteryGap(behind, today = d(6)))
    }

    @Test
    fun noTargetWithoutEnoughRecords() {
        val none = Task("a", "A", d(1), d(11), 10.0)
        assertNull(Planner.targetMastery(none, d(3)))
        val one = none.copy(masteryLogs = mapOf(d(1) to 10))
        assertNull(Planner.masteryGap(one, d(3)))
        val pastDeadline = none.copy(deadline = d(1), masteryLogs = mapOf(d(1) to 10))
        assertNull(Planner.targetMastery(pastDeadline, d(1)))
    }
}
