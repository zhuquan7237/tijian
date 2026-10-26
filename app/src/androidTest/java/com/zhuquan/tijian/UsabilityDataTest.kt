package com.zhuquan.tijian

import androidx.test.platform.app.InstrumentationRegistry
import com.zhuquan.tijian.data.*
import org.junit.Assert.*
import org.junit.Test

class UsabilityDataTest {
    private fun withDb(block: (Db) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "usability-${System.nanoTime()}.db"
        val db = Db(context, name)
        try { block(db) } finally { db.close(); context.deleteDatabase(name) }
    }
    @Test fun textOnlyImportIsIdempotentAndSearchesLiteralQuestionNumber() = withDb { db ->
        val draft = DraftQuestion(imageId = "", imagePath = "", stem = "例题", customTag = "A_10%")
        assertEquals(1 to 0, db.saveDrafts(listOf(draft), null))
        assertEquals(0 to 1, db.saveDrafts(listOf(draft), null))
        assertNull(db.question(draft.id)!!.imageId)
        assertEquals(draft.id, db.questions(query = "A_10%").single().id)
        assertTrue(db.questions(query = "B_10%").isEmpty())
    }
    @Test fun deleteRestoreAndPurgeRespectTrashBoundary() = withDb { db ->
        val draft = DraftQuestion(imageId = "", imagePath = "", stem = "回收测试")
        db.saveDrafts(listOf(draft), null)
        db.purgeQuestion(draft.id)
        assertNotNull(db.question(draft.id))
        db.deleteQuestion(draft.id)
        assertTrue(db.questions().isEmpty())
        assertEquals(1, db.questions(filter = "trash").size)
        db.restoreQuestions(listOf(draft.id))
        assertEquals(1, db.questions().size)
        db.deleteQuestion(draft.id); db.purgeQuestion(draft.id)
        assertNull(db.question(draft.id))
    }
    @Test fun undoRestoresSchedulingAndRemovesOnlyItsOwnLog() = withDb { db ->
        val draft = DraftQuestion(imageId = "", imagePath = "", stem = "复习测试")
        db.saveDrafts(listOf(draft), null)
        val before = db.question(draft.id)!!
        val snapshot = db.reviewSnapshot(draft.id)
        db.review(draft.id, 2, logId = "test-log")
        assertEquals(before.reviewCount + 1, db.question(draft.id)!!.reviewCount)
        db.undoReview(snapshot, "test-log")
        val after = db.question(draft.id)!!
        assertEquals(before.mastery, after.mastery)
        assertEquals(before.reviewCount, after.reviewCount)
        assertEquals(before.nextReviewAt, after.nextReviewAt)
        db.readableDatabase.rawQuery("SELECT COUNT(*) FROM review_log", null).use {
            it.moveToFirst(); assertEquals(0, it.getInt(0))
        }
    }
}
