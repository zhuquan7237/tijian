package com.zhuquan.tijian

import androidx.test.platform.app.InstrumentationRegistry
import com.zhuquan.tijian.data.Db
import com.zhuquan.tijian.data.DraftQuestion
import org.junit.Assert.*
import org.junit.Test

class ImportTransactionTest {
    private fun withDb(block: (Db) -> Unit) {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "import-transaction-${System.nanoTime()}.db"
        val db = Db(ctx, name)
        try { block(db) } finally { db.close(); ctx.deleteDatabase(name) }
    }

    private fun draft(stem: String) = DraftQuestion(
        imageId = "", imagePath = "", stem = stem, categoryName = "数学 / 事务测试"
    )

    @Test fun failedBatchRollsBackQuestionsAndCategoriesThenCanRetry() = withDb { db ->
        val before = db.categories().map { it.id }.toSet()
        db.writableDatabase.execSQL("""CREATE TRIGGER reject_test BEFORE INSERT ON question
            WHEN NEW.stem = 'fail' BEGIN SELECT RAISE(ABORT, 'forced failure'); END""")
        val drafts = listOf(draft("first"), draft("fail"))
        try {
            db.saveDrafts(drafts, null)
            fail("Expected second insert to fail")
        } catch (_: android.database.sqlite.SQLiteException) { }
        assertEquals(0, db.questions().size)
        assertEquals(before, db.categories().map { it.id }.toSet())
        db.writableDatabase.execSQL("DROP TRIGGER reject_test")
        assertEquals(2 to 0, db.saveDrafts(drafts, null))
        assertEquals(2, db.questions().size)
    }

    @Test fun duplicatesAreSkippedWithinBatchAndOnRetry() = withDb { db ->
        val drafts = listOf(draft("same"), draft("same"), draft("other"))
        assertEquals(2 to 1, db.saveDrafts(drafts, null))
        assertEquals(0 to 3, db.saveDrafts(drafts, null))
        assertEquals(2, db.questions().size)
    }
}
