package com.zhuquan.tijian

import android.content.Intent
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.zhuquan.tijian.ui.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class UsabilityUiTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Test fun editorActionsRemainVisibleWithLargeSystemText() {
        val context = instrumentation.targetContext
        val host = instrumentation.startActivitySync(Intent(context, RichTextRegressionActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as RichTextRegressionActivity
        try {
            instrumentation.runOnMainSync {
                host.setContent {
                    TijianTheme {
                        CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.5f)) {
                            FullEditor(onDismissRequest = {}, title = { Text("编辑题目") }, text = {
                                Column {
                                    TjTextField("这是需要校对的长题干", {}, Modifier.fillMaxWidth(), minLines = 6)
                                    Text("修改自动保存，返回后可以继续")
                                }
                            }, confirmButton = { TjButton("保存题目") {} }, dismissButton = { TjButton("稍后继续", style = ActionStyle.Quiet) {} })
                        }
                    }
                }
            }
            instrumentation.waitForIdleSync()
            Thread.sleep(800)
            val root = instrumentation.uiAutomation.rootInActiveWindow
            val buttons = root.findAccessibilityNodeInfosByText("保存题目")
            assertTrue("Save action must be discoverable", buttons.isNotEmpty())
            val bounds = android.graphics.Rect()
            buttons.first().getBoundsInScreen(bounds)
            assertTrue(bounds.width() > 0 && bounds.height() > 0)
            val screen = instrumentation.uiAutomation.takeScreenshot()
            assertTrue("Save action must be above screen bottom", bounds.bottom <= screen.height)
            val dir = (InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?.let { File(it) }
                ?: context.getExternalFilesDir("usability")!!).apply { mkdirs() }
            File(dir, "editor-large-text.png").outputStream().use { screen.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            screen.recycle()
        } finally { instrumentation.runOnMainSync { host.finish() } }
    }
    @Test fun learningListAndTableFitSmallScreenAndPreserveNavigation() {
        val context = instrumentation.targetContext
        val app = context.applicationContext as App
        val draft = com.zhuquan.tijian.data.DraftQuestion(imageId = "", imagePath = "",
            stem = "求极限：当 x 趋于零时，sin(x) / x 的极限是多少？", answer = "1",
            analysis = "利用重要极限求解。", categoryName = "数学/界面回归", customTag = "卷一 · 12")
        app.db.saveDrafts(listOf(draft), null)
        val categoryId = app.db.question(draft.id)!!.categoryId!!
        val host = instrumentation.startActivitySync(Intent(context, RichTextRegressionActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as RichTextRegressionActivity
        val store = androidx.lifecycle.ViewModelStore()
        lateinit var vm: AppVm
        fun screenshot(name: String) {
            instrumentation.waitForIdleSync()
            Thread.sleep(600)
            val bitmap = instrumentation.uiAutomation.takeScreenshot()
            val dir = (InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?.let { File(it) }
                ?: context.getExternalFilesDir("usability")!!).apply { mkdirs() }
            File(dir, name + ".png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        try {
            instrumentation.runOnMainSync {
                vm = AppVm(app); store.put("vm", vm)
                host.setContent {
                    TijianTheme {
                        CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.3f)) {
                            Box(Modifier.width(androidx.compose.ui.unit.Dp(320f)).fillMaxHeight()) { AppRoot(vm) }
                        }
                    }
                }
            }
            Thread.sleep(700)
            screenshot("home-small-screen")
            instrumentation.runOnMainSync { vm.go(Route.Category(categoryId)) }
            screenshot("learning-list-small-screen")
            val root = instrumentation.uiAutomation.rootInActiveWindow
            assertTrue(root.findAccessibilityNodeInfosByText("学习列表").isNotEmpty())
            val chips = root.findAccessibilityNodeInfosByText("整理表格")
            assertTrue(chips.isNotEmpty())
            var node: android.view.accessibility.AccessibilityNodeInfo? = chips.first()
            while(node != null && !node.isClickable) node = node.parent
            assertTrue(node?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK) == true)
            screenshot("table-small-screen")
            instrumentation.runOnMainSync { vm.go(Route.Detail(draft.id)) }
            screenshot("detail-small-screen")
            instrumentation.runOnMainSync { vm.back() }
            assertEquals(Route.Category(categoryId), vm.route)
        } finally {
            instrumentation.runOnMainSync { store.clear(); host.finish() }
            app.db.deleteQuestion(draft.id); app.db.purgeQuestion(draft.id)
        }
    }

}
