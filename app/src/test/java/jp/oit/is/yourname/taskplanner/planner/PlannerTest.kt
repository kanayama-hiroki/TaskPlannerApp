package jp.oit.`is`.yourname.taskplanner.planner

import jp.oit.`is`.yourname.taskplanner.data.Pace
import jp.oit.`is`.yourname.taskplanner.data.Step
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

    private fun steps(vararg done: Boolean) =
        done.mapIndexed { i, isDone -> Step("s$i", "ステップ$i", weight = 1, done = isDone) }

    @Test
    fun stepProgressCountsDoneWeight() {
        val task = Task("a", "A", d(1), d(20), 10.0, steps = listOf(
            Step("1", "小", weight = 1, done = true),
            Step("2", "大", weight = 3, done = false),
        ))
        assertEquals(0.25f, Planner.stepProgress(task)!!, 1e-6f)
        assertNull(Planner.stepProgress(task.copy(steps = emptyList())))
    }

    @Test
    fun stepHoursSplitPlannedHoursByWeight() {
        val big = Step("2", "大", weight = 3)
        val task = Task("a", "A", d(1), d(20), 8.0, steps = listOf(Step("1", "小", weight = 1), big))
        assertEquals(6.0, Planner.stepHours(task, big), 1e-9)
        assertEquals(8.0, task.steps.sumOf { Planner.stepHours(task, it) }, 1e-9)
    }

    @Test
    fun nextStepIsFirstNotDone() {
        val task = Task("a", "A", d(1), d(20), 10.0, steps = steps(true, false, true))
        assertEquals("s1", Planner.nextStep(task)!!.id)
        assertNull(Planner.nextStep(task.copy(steps = steps(true, true))))
    }

    @Test
    fun reestimateFromStepProgress() {
        // 4ステップのうち1つ（25%）を、5h かけて終えた → 全部で 20h
        val task = Task("a", "A", d(1), d(20), 10.0, logs = mapOf(d(1) to 5.0), steps = steps(true, false, false, false))
        assertEquals(20.0, Planner.reestimatedTotal(task)!!, 1e-9)
    }

    @Test
    fun reestimateNeedsEnoughData() {
        val base = Task("a", "A", d(1), d(20), 10.0, logs = mapOf(d(1) to 5.0), steps = steps(true, false, false, false))
        assertNull(Planner.reestimatedTotal(base.copy(steps = emptyList()))) // ステップなし
        assertNull(Planner.reestimatedTotal(base.copy(steps = steps(false, false, false, false)))) // 1つも終わっていない
        assertNull(Planner.reestimatedTotal(base.copy(steps = steps(true, true, true, true)))) // 全部終わっている
        assertNull(Planner.reestimatedTotal(base.copy(logs = mapOf(d(1) to 0.5)))) // 作業が少なすぎる
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
