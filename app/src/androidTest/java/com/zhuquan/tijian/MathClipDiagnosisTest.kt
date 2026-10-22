package com.zhuquan.tijian

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * 「公式渲染不完全」定位用：把用户反馈的原题分别渲染一遍，
 * 报告被裁剪的公式、渲染错误数，以及每个公式容器的包围盒与内容盒。
 */
@Suppress("DEPRECATION")
class MathClipDiagnosisTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private var sequence = 0
    private lateinit var host: RichTextRegressionActivity

    @Before fun setUp() {
        host = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, RichTextRegressionActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ) as RichTextRegressionActivity
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
            if (view == null) done.countDown() else view.evaluateJavascript(
                """
                JSON.stringify({
                  text: document.getElementById('out').innerText,
                  h: window.__measureRichText ? window.__measureRichText() : 0,
                  fonts: document.fonts.status,
                  errors: document.querySelectorAll('.katex-error,.rt-error').length,
                  formulas: document.querySelectorAll('.katex').length,
                  math: Array.from(document.querySelectorAll('.math-inline,.math-display')).map(function(e){
                    var r = e.getBoundingClientRect();
                    var b = e.querySelector('.base');
                    var br = b ? b.getBoundingClientRect() : null;
                    var html = e.querySelector('.katex-html');
                    var hr = html ? html.getBoundingClientRect() : null;
                    return {tex: (e.getAttribute('data-tex')||'').slice(0,60),
                            cls: e.className,
                            box: [Math.round(r.left), Math.round(r.top), Math.round(r.right), Math.round(r.bottom)],
                            base: br ? [Math.round(br.top), Math.round(br.bottom)] : null,
                            html: hr ? [Math.round(hr.top), Math.round(hr.bottom)] : null,
                            scrollW: e.scrollWidth, clientW: e.clientWidth,
                            scrollH: e.scrollHeight, clientH: e.clientHeight,
                            clippedTop: b ? (b.getBoundingClientRect().top < r.top - 1) : null,
                            clippedBottom: b ? (b.getBoundingClientRect().bottom > r.bottom + 1) : null};
                  })
                })
                """.trimIndent()
            ) { value ->
                try {
                    val decoded = JSONTokener(value ?: "null").nextValue()
                    if (decoded is String) result = JSONObject(decoded)
                } finally { done.countDown() }
            }
        }
        if (!done.await(500, TimeUnit.MILLISECONDS)) return JSONObject()
        return result
    }

    private fun render(markdown: String): JSONObject {
        val marker = "DIAG-" + (++sequence)
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
            if (last.optString("text").contains(marker) && last.optString("fonts") == "loaded" &&
                last.optInt("h") > 0 && abs(nativeDp - last.optInt("h")) < 1.1f
            ) return last
        }
        fail("render did not converge: " + last)
        return last
    }

    @Test fun userQuoteStemRendersCompletely() {
        val d = "$"
        val stem = "曲线 " + d + "x^{2/3}+y^{2/3}=1" + d + "（" + d + "x>0" + d + "，" + d + "y>0" + d + "）" +
            "上任意一点处的切线在两坐标轴上的截距之和为 " + d + "l" + d + "，试求 " + d + "l" + d + " 的最大值。"
        val m = render(stem)
        assertEquals("katex errors: $m", 0, m.optInt("errors"))
        val math = m.optJSONArray("math")
        var clipped = 0
        for (i in 0 until (math?.length() ?: 0)) {
            val o = math!!.getJSONObject(i)
            if (o.optBoolean("clippedTop") || o.optBoolean("clippedBottom")) clipped++
        }
        assertEquals("clipped formulas: $m", 0, clipped)
    }

    @Test fun userLimitStemRendersCompletely() {
        val d = "$"
        val stem = "若 " + d + "x\\ge 0" + d + "，则 " + d + "\\lim_{n\\to\\infty}\\sqrt[n]{1+x^n+\\left(\\frac{x^2}{2}\\right)^n}=" + d + " ______。"
        val m = render(stem)
        assertEquals("katex errors: $m", 0, m.optInt("errors"))
        val math = m.optJSONArray("math")
        var clipped = 0
        for (i in 0 until (math?.length() ?: 0)) {
            val o = math!!.getJSONObject(i)
            if (o.optBoolean("clippedTop") || o.optBoolean("clippedBottom")) clipped++
        }
        assertEquals("clipped formulas: $m", 0, clipped)
    }
}
