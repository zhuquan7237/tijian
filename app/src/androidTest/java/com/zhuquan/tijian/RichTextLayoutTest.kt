package com.zhuquan.tijian

import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.Assert.*
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import org.json.JSONObject
import org.json.JSONTokener
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs

@Suppress("DEPRECATION")
class RichTextLayoutTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private var sequence = 0
    private lateinit var host: RichTextRegressionActivity
    @Before fun setUp() {
        host = instrumentation.startActivitySync(Intent(instrumentation.targetContext,
            RichTextRegressionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as RichTextRegressionActivity
    }
    @After fun tearDown() {
        instrumentation.runOnMainSync { host.finish() }
    }
    private fun web(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) web(view.getChildAt(i))?.let { return it }
        return null
    }
    private fun metrics(): JSONObject {
        val done = CountDownLatch(1)
        var result = JSONObject()
        instrumentation.runOnMainSync {
            val view = web(host.window.decorView)
            if (view == null) done.countDown() else view.evaluateJavascript("""
                JSON.stringify({text:document.getElementById('out').innerText,
                h:window.__measureRichText?window.__measureRichText():0,
                fonts:document.fonts.status,
                imagesReady:Array.from(document.images).every(e=>e.complete&&e.naturalWidth>0),
                rows:document.querySelectorAll('tbody tr').length,
                errors:document.querySelectorAll('.katex-error,.rt-error').length,
                formulas:document.querySelectorAll('.katex').length,
                clipped:Array.from(document.querySelectorAll('.math-inline,.math-display')).filter(e=>{
                  const r=e.getBoundingClientRect();return Array.from(e.querySelectorAll('.base')).some(b=>{
                    const q=b.getBoundingClientRect();return q.top<r.top-1||q.bottom>r.bottom+1;
                  });}).length,
                wide:Array.from(document.querySelectorAll('.math-inline,.math-display')).filter(e=>e.scrollWidth>e.clientWidth+1).length})
            """.trimIndent()) { value ->
                try {
                    // A replaced document can return JavaScript null rather than
                    // a metrics string. Let render() retry the current document.
                    val decoded = JSONTokener(value ?: "null").nextValue()
                    if (decoded is String) result = JSONObject(decoded)
                } finally { done.countDown() }
            }
        }
        // A document navigation can discard an in-flight JS callback. Retry the
        // current native view until render()'s deadline, never accept old content.
        if (!done.await(500, TimeUnit.MILLISECONDS)) return JSONObject()
        return result
    }
    private fun render(markdown: String): JSONObject {
        val marker = "REGRESSION-" + (++sequence)
        instrumentation.runOnMainSync { host.content.value = markdown + "\n\n" + marker }
        val deadline = System.currentTimeMillis() + 10000
        var last = JSONObject()
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(100)
            last = metrics()
            var nativeDp = 0f
            instrumentation.runOnMainSync {
                web(host.window.decorView)?.let { nativeDp = it.height / it.resources.displayMetrics.density }
            }
            if (last.optString("text").contains(marker) && last.optString("fonts")=="loaded" &&
                last.optBoolean("imagesReady") && last.optInt("h") > 0 && abs(nativeDp-last.optInt("h"))<1.1f) {
                assertEquals("KaTeX render errors", 0, last.optInt("errors"))
                assertEquals("Formula extends beyond vertical scroll box", 0, last.optInt("clipped"))
                return last
            }
        }
        fail("Native/CSS height did not converge: " + last)
        return last
    }
    @Test fun testTextAndFormulaCorpus() {
        val cases = listOf(
            "普通文字 END",
            (1..25).joinToString("\n\n") { "第" + it + "行，中英文 long text with wrapping" } + "\n\nEND",
            "分式与根式 $\\dfrac{1}{1+\\dfrac{1}{\\sqrt{1+\\sqrt{x}}}}$ END",
            "积分与极限 $\\int_{0}^{\\infty}e^{-x}dx$，$\\lim_{x\\to0}\\frac{\\sin x}{x}$ END",
            "$$\\begin{aligned}a&=b+c\\\\&=\\frac{1}{x}\\\\&=\\sqrt{x}\\end{aligned}$$\n\nEND",
            "矩阵 $\\begin{pmatrix}1&2\\\\3&4\\end{pmatrix}$ END",
            "中文 English " + '$' + "x_i^2$ 与公式混排 END",
            "$$" + (1..50).joinToString("+") { "x_{" + it + "}" } + "$$\n\nEND",
            "![image](file:///android_asset/sample_exam.jpg)\n\n图片后文字 END",
            "| 步骤 | 结果 |\n| --- | --- |\n| 1 | $\\frac{1}{x}$ |\n| 2 | $-2$ |\n\nEND"
        )
        for ((i, content) in cases.withIndex()) {
            val m = render(content)
            if (i in 2..7) assertTrue("Missing formulas for case " + i, m.getInt("formulas") > 0)
            if (i == 7) assertTrue("Wide formula must be locally scrollable", m.getInt("wide") > 0)
            if (i == 9) assertEquals("Formula processing broke Markdown table", 2, m.getInt("rows"))
        }
    }
    @Test fun testAsyncReplacementShrinkAndStableHeight() {
        val large = render((1..35).joinToString("\n\n") { "很长的解析 " + it } + "\n\nEND")
        val small = render("答案 $-2$ END")
        assertTrue("Retained stale height", small.getInt("h") < large.getInt("h") / 5)
        Thread.sleep(1000)
        assertEquals("Viewport feedback loop", small.getInt("h"), metrics().getInt("h"))
    }
    @Test fun testEmptyContentThenLongAnalysis() {
        render("答案 $-2$")
        instrumentation.runOnMainSync { host.content.value = "" }
        val deadline = System.currentTimeMillis() + 5000
        var removed = false
        while (!removed && System.currentTimeMillis() < deadline) {
            instrumentation.runOnMainSync { removed = web(host.window.decorView) == null }
            if (!removed) Thread.sleep(50)
        }
        assertTrue("Blank answer retained a stale WebView", removed)
        val analysis = render((1..30).joinToString("\n\n") { "解析步骤 " + it + " $\\frac{1}{x}$" })
        assertTrue(analysis.getInt("formulas") >= 30)
    }
    @Test fun testWidthFontScaleAndResume() {
        val content = "中英文 mixed text $\\frac{1}{\\sqrt{x}}$ ".repeat(25) + "END"
        val normal = render(content)
        instrumentation.runOnMainSync { host.width.value=240; host.fontScale.value=1.5f }
        val scaled = render(content)
        assertTrue("Width/font did not reflow", scaled.getInt("h") > normal.getInt("h"))
        instrumentation.runOnMainSync {
            instrumentation.callActivityOnPause(host)
            instrumentation.callActivityOnResume(host)
        }
        render(content)
    }
}
