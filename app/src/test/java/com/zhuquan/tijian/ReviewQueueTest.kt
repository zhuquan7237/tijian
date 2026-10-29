package com.zhuquan.tijian

import com.zhuquan.tijian.data.QueueCandidate
import com.zhuquan.tijian.data.nextDailyTrigger
import com.zhuquan.tijian.data.reviewNotificationText
import com.zhuquan.tijian.data.selectDailyIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class ReviewQueueTest {
    private fun q(
        id: String,
        starred: Boolean = false,
        mastery: Int = 0,
        unimportant: Boolean = false,
        due: Long = 1_000L,
    ) = QueueCandidate(id, starred, mastery, unimportant, due)

    @Test fun starredAndDueComeFirstMasteredAndUnimportantDropOut() {
        val now = 10_000L
        val ids = selectDailyIds(
            listOf(
                q("late", due = 9_000),
                q("star", starred = true, due = 8_000),
                q("done", mastery = 4, due = 1),
                q("skip", unimportant = true, due = 1),
                q("early", due = 2_000),
                q("future", due = 50_000),
            ),
            now = now,
            cap = 8,
        )
        assertEquals(listOf("star", "early", "late"), ids)
    }

    @Test fun capRollsTheRestForward() {
        val now = 100L
        val items = (1..12).map { q("q$it", due = it.toLong()) }
        val ids = selectDailyIds(items, now, cap = 8)
        assertEquals(8, ids.size)
        assertEquals("q1", ids.first())
        assertTrue("q9" !in ids)
    }

    @Test fun triggerRollsToTomorrowAfterTheClock() {
        val zone = TimeZone.getTimeZone("Asia/Shanghai")
        val cal = java.util.Calendar.getInstance(zone)
        cal.set(2026, java.util.Calendar.OCTOBER, 29, 21, 0, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        val next = nextDailyTrigger(20, 0, cal.timeInMillis, zone)
        val out = java.util.Calendar.getInstance(zone).apply { timeInMillis = next }
        assertEquals(30, out.get(java.util.Calendar.DAY_OF_MONTH))
        assertEquals(20, out.get(java.util.Calendar.HOUR_OF_DAY))
    }

    @Test fun triggerStaysTodayIfStillAhead() {
        val zone = TimeZone.getTimeZone("Asia/Shanghai")
        val cal = java.util.Calendar.getInstance(zone)
        cal.set(2026, java.util.Calendar.OCTOBER, 29, 18, 0, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        val next = nextDailyTrigger(20, 30, cal.timeInMillis, zone)
        val out = java.util.Calendar.getInstance(zone).apply { timeInMillis = next }
        assertEquals(29, out.get(java.util.Calendar.DAY_OF_MONTH))
        assertEquals(20, out.get(java.util.Calendar.HOUR_OF_DAY))
        assertEquals(30, out.get(java.util.Calendar.MINUTE))
    }

    @Test fun notificationNamesTheQuestions() {
        val text = reviewNotificationText(listOf("求极限 \$x\$", "配位数"), overflow = 4)
        assertTrue(text.contains("求极限"))
        assertTrue(text.contains("还有 4 道"))
    }
}
