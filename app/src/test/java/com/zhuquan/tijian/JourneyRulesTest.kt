package com.zhuquan.tijian

import com.zhuquan.tijian.data.REVIEW_DAY_MS
import com.zhuquan.tijian.data.ReviewSessionState
import com.zhuquan.tijian.data.decodeReviewSession
import com.zhuquan.tijian.data.encodeReviewSession
import com.zhuquan.tijian.data.firstReviewAt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JourneyRulesTest {
    @Test fun newQuestionIsDueTomorrowNotNow() {
        val captured = 1_700_000_000_000L
        assertEquals(captured + REVIEW_DAY_MS, firstReviewAt(captured))
        assertEquals(86_400_000L, firstReviewAt(captured) - captured)
    }

    @Test fun reviewSessionRoundTrip() {
        val state = ReviewSessionState("DUE", 2, true, listOf("a", "b", "c"), listOf("b"))
        val back = decodeReviewSession(encodeReviewSession(state))
        assertEquals(state, back)
    }

    @Test fun blankOrBrokenSessionIsIgnored() {
        assertNull(decodeReviewSession(""))
        assertNull(decodeReviewSession("{"))
        assertNull(decodeReviewSession("""{"ids":[]}"""))
    }
}
