package com.zhuquan.tijian

import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.zhuquan.tijian.ui.AppVm
import com.zhuquan.tijian.data.*
import org.junit.Assert.*
import org.junit.Test

class SessionRecoveryTest {
    private val inst get() = InstrumentationRegistry.getInstrumentation()
    private fun awaitMain(condition: () -> Boolean) {
        repeat(200) {
            var done = false
            inst.runOnMainSync { done = condition() }
            if(done) return
            Thread.sleep(25)
        }
        fail("Timed out waiting for persisted UI state")
    }
    @Test fun editedDraftAndReviewUndoSurviveViewModelRecreation() {
        val app = inst.targetContext.applicationContext as App
        val store = ViewModelStore()
        val keys = listOf("drafts.v1", "drafts.batch", "review.session")
        val saved = keys.associateWith { app.db.getSetting(it).orEmpty() }
        keys.forEach { app.db.putSetting(it, if(it == "drafts.v1") "[]" else "") }
        var questionId: String? = null
        try {
            lateinit var first: AppVm
            inst.runOnMainSync { first = AppVm(app); store.put("first", first) }
            awaitMain { first.sessionReady }
            inst.runOnMainSync {
                first.addTextDraft()
                first.updateDraft(first.pending.single().copy(stem = "重启后还在的公式 \\(x^2\\)", categoryName = "数学/会话回归"))
            }
            awaitMain { first.draftStatus == "已保存草稿" }
            lateinit var restored: AppVm
            inst.runOnMainSync { restored = AppVm(app); store.put("restored", restored) }
            awaitMain { restored.sessionReady }
            assertEquals(first.pending, restored.pending)
            val draft = restored.pending.single()
            app.db.saveDrafts(listOf(draft), null); questionId = draft.id
            val category = app.db.question(draft.id)!!.categoryId!!
            inst.runOnMainSync { restored.startReviewFor(category); restored.beginReview() }
            awaitMain { !restored.reviewSaving && restored.reviewQueue.isNotEmpty() }
            inst.runOnMainSync { restored.reveal(); restored.rate(2) }
            awaitMain { !restored.reviewSaving && restored.reviewIndex == 1 }
            lateinit var reviewRestored: AppVm
            inst.runOnMainSync { reviewRestored = AppVm(app); store.put("review", reviewRestored) }
            awaitMain { reviewRestored.sessionReady }
            assertEquals(1, reviewRestored.reviewIndex)
            assertTrue(reviewRestored.canUndoReview)
            inst.runOnMainSync { reviewRestored.undoRating() }
            awaitMain { !reviewRestored.reviewSaving && reviewRestored.reviewIndex == 0 }
            assertEquals(0, app.db.question(draft.id)!!.reviewCount)
        } finally {
            inst.runOnMainSync { store.clear() }
            saved.forEach { (key, value) -> app.db.putSetting(key, value) }
            questionId?.let { app.db.deleteQuestion(it); app.db.purgeQuestion(it) }
        }
    }
}
