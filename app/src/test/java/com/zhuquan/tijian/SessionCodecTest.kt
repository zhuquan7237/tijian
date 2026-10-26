package com.zhuquan.tijian

import com.zhuquan.tijian.data.*
import org.junit.Assert.*
import org.junit.Test

class SessionCodecTest {
    @Test fun preservesEditedMathAndAllDraftFieldsAcrossRestart() {
        val d = DraftQuestion(id = "stable-id", imageId = "image", imagePath = "/files/image.jpg",
            stem = "求极限 \\(\\frac{x}{2}\\)", options = "[\"A\",\"B\"]", answer = "1/2",
            analysis = "第一步\n第二步", tags = "[\"极限\"]", needFormulas = "[\"公式\"]",
            needConcepts = "[\"概念\"]", needMethods = "[\"方法\"]", categoryName = "数学/极限",
            customTag = "卷一·12", confidence = 0.3, difficulty = 4, bbox = listOf(0.1, 0.2, 0.8, 0.9))
        assertEquals(listOf(d), SessionCodec.decodeDrafts(SessionCodec.encodeDrafts(listOf(d))))
    }
    @Test fun textOnlyAndEmptySessionsRoundTrip() {
        val d = DraftQuestion(imageId = "", imagePath = "", stem = "文字题")
        assertEquals(listOf(d), SessionCodec.decodeDrafts(SessionCodec.encodeDrafts(listOf(d))))
        assertTrue(SessionCodec.decodeDrafts("[]").isEmpty())
    }
    @Test fun schedulesAreBoundedAndMatchMasteryProgression() {
        assertEquals(listOf(1, 3, 7, 16, 35, 35), (0..5).map { reviewDays(it, 2) })
        assertEquals(1, reviewDays(0, 1))
        assertEquals(1, reviewDays(5, 0))
    }
}
