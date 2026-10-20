package com.zhuquan.tijian

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.zhuquan.tijian.ui.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class UiConsistencyTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Test fun pairedActionsKeepEqualHeightWithLargeText() {
        val ctx = instrumentation.targetContext
        val host = instrumentation.startActivitySync(Intent(ctx, RichTextRegressionActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as RichTextRegressionActivity
        val heights = IntArray(2)
        try {
            instrumentation.runOnMainSync {
                host.setContent {
                    TijianTheme {
                        val density = LocalDensity.current.density
                        CompositionLocalProvider(LocalDensity provides Density(density, 1.5f)) {
                            Row(Modifier.width(320.dp).height(IntrinsicSize.Min).padding(20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TjButton("生成并分享", Modifier.weight(1f).fillMaxHeight().onGloballyPositioned { heights[0] = it.size.height }, style = ActionStyle.Secondary) {}
                                TjButton("保存 PDF", Modifier.weight(1f).fillMaxHeight().onGloballyPositioned { heights[1] = it.size.height }) {}
                            }
                        }
                    }
                }
            }
            instrumentation.waitForIdleSync()
            android.os.SystemClock.sleep(600)
            assertTrue("button must be laid out", heights[0] > 0)
            assertEquals("paired actions must have the same height", heights[0], heights[1])
            assertTrue("48dp touch target", heights[0] >= (48 * ctx.resources.displayMetrics.density).toInt())
        } finally { instrumentation.runOnMainSync { host.finish() } }
    }

    @Test fun repeatedPhotosStayUniqueAndBatchIsBounded() {
        val app = instrumentation.targetContext.applicationContext as Application
        instrumentation.runOnMainSync {
            val vm = AppVm(app)
            try {
                val first = Uri.parse("content://test/image/0")
                vm.addCapture(first); vm.addCapture(first)
                assertEquals(1, vm.captureUris.size)
                (1..12).forEach { vm.addCapture(Uri.parse("content://test/image/$it")) }
                assertEquals(9, vm.captureUris.size)
                assertEquals(9, vm.captureUris.distinct().size)
            } finally { vm.clearCapture() }
        }
    }

    @Test fun failedExportAlwaysReleasesBusyState() {
        val app = instrumentation.targetContext.applicationContext as Application
        instrumentation.runOnMainSync {
            val vm = AppVm(app)
            runBlocking { vm.withBusy("测试导出") { error("模拟导出失败") } }
            assertFalse(vm.busy)
            assertEquals("", vm.busyLabel)
            assertEquals("模拟导出失败", vm.toast)
        }
    }
    @Test fun captureProductionScreensForVisualReview() {
        val ctx = instrumentation.targetContext
        val host = instrumentation.startActivitySync(Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        lateinit var vm: AppVm
        instrumentation.runOnMainSync { vm = androidx.lifecycle.ViewModelProvider(host)[AppVm::class.java] }
        val out = java.io.File(ctx.getExternalFilesDir(null), "ui-8.1.0").apply { mkdirs() }
        fun capture(name: String) {
            instrumentation.waitForIdleSync()
            android.os.SystemClock.sleep(700)
            val bitmap = instrumentation.uiAutomation.takeScreenshot()
            assertNotNull("screenshot $name", bitmap)
            java.io.File(out, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        try {
            capture("01-home")
            listOf("02-subject" to Route.Subject("数学"), "03-library" to Route.Library,
                "04-settings" to Route.Settings, "05-provider" to Route.Provider,
                "06-capture" to Route.Capture, "07-import" to Route.Import).forEach { (name, route) ->
                instrumentation.runOnMainSync { vm.replace(route) }
                capture(name)
            }
            instrumentation.runOnMainSync { vm.home(); vm.openChat() }
            capture("08-chat")
            instrumentation.runOnMainSync { vm.closeChat() }
        } finally { instrumentation.runOnMainSync { host.finish() } }
    }

}
