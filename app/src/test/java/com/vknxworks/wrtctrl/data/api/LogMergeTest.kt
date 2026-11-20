package com.vknxworks.wrtctrl.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.util.Random

class LogMergeTest {
    /** Line [id] of the log since boot number [boot]: a restarted router numbers from 0 again with other times and texts. */
    private fun line(id: Long, boot: Int = 0) = LogLine(1000L * id + boot * 7919L, "0", 6, "line $id of boot $boot", id)

    private fun lines(from: Long, to: Long, boot: Int = 0) = (from..to).map { line(it, boot) }

    @Test
    fun joinedListAlwaysEqualsReadingTheWholeLogAgain() {
        val rnd = Random(1)
        var router = lines(0, 49)
        var shown = router.takeLast(1000)
        repeat(3000) {
            val added = when (rnd.nextInt(6)) {
                0 -> 0
                1 -> 1
                2 -> 5
                3 -> 99
                4 -> 100
                else -> rnd.nextInt(300)
            }
            val next = router.last().id + 1
            router = (router + lines(next, next + added - 1)).takeLast(3000)
            val whole = router.takeLast(1000)
            val joined = mergeLogTail(shown, router.takeLast(100), 1000)
            if (added == 0) assertSame(shown, joined)
            if (added in 1..99) assertEquals(whole, joined)
            // 100 or more new lines: the tail no longer reaches what's shown, so the whole log is read.
            if (added >= 100 && shown.size >= 100) assertNull(joined)
            shown = joined ?: whole
            assertEquals(whole, shown)
        }
    }

    @Test
    fun keepsOnlyTheNewestLines() {
        assertEquals(lines(50, 1049), mergeLogTail(lines(0, 999), lines(950, 1049), 1000))
    }

    @Test
    fun joinsAShortLog() {
        assertEquals(lines(0, 25), mergeLogTail(lines(0, 20), lines(0, 25), 1000))
    }

    @Test
    fun refusesAfterARestartOfTheRouterOrLogd() {
        // The new boot's ids follow on exactly, overlap, or start low: never joined.
        assertNull(mergeLogTail(lines(0, 299), lines(300, 399, boot = 1), 1000))
        assertNull(mergeLogTail(lines(0, 299), lines(250, 349, boot = 1), 1000))
        assertNull(mergeLogTail(lines(0, 5000), lines(0, 99, boot = 1), 1000))
    }

    @Test
    fun refusesWithoutIdsOrWithNothingToJoin() {
        val noIds = lines(0, 9).map { it.copy(id = -1) }
        assertNull(mergeLogTail(noIds, noIds, 1000))
        assertNull(mergeLogTail(emptyList(), lines(0, 9), 1000))
        assertNull(mergeLogTail(lines(0, 9), emptyList(), 1000))
        assertNull(mergeLogTail(lines(10, 20), lines(5, 25), 1000))
        assertNull(mergeLogTail(lines(0, 20), lines(5, 15), 1000))
    }
}
