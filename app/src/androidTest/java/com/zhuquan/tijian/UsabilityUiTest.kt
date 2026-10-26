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
            val dir = context.getExternalFilesDir("usability")!!.apply { mkdirs() }
            File(dir, "editor-large-text.png").outputStream().use { screen.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            screen.recycle()
        } finally { instrumentation.runOnMainSync { host.finish() } }
    }
}
