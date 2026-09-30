package jp.oit.`is`.yourname.taskplanner.planner

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
    fun totalsSumOverlappingTasksAndSkipDone() {
        val a = Task("a", "A", d(1), d(3), 4.0) // 10/1, 10/2 に 2h
        val b = Task("b", "B", d(1), d(3), 2.0) // 10/1, 10/2 に 1h
        val c = Task("c", "C", d(1), d(3), 100.0, done = true)
        val totals = Planner.totalsByDate(listOf(a, b, c))
        assertEquals(3.0, totals[d(1)]!!, 1e-9)
        assertEquals(3.0, totals[d(2)]!!, 1e-9)
        assertNull(totals[d(3)])
    }
}
