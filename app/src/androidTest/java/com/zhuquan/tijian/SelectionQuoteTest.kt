package com.zhuquan.tijian

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.platform.app.InstrumentationRegistry
import com.zhuquan.tijian.ui.RichTextRenderer
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 「长按选中公式 → 追问」时选区文本的还原质量。
 * 选中范围部分覆盖公式时，若不把边界公式整体换成原始 TeX，
 * 选区文本会退化成 KaTeX 渲染后的零散字符（x^{2/3} → "x23"）。
 */
class SelectionQuoteTest {

    private val d = "$"

    private fun renderAndSelect(context: Context, markdown: String, script: String): String {
        val html = RichTextRenderer.html(context, markdown, 15, 360, 7, true)
        val out = StringBuilder()
        val latch = CountDownLatch(1)
        Handler(Looper.getMainLooper()).post {
            @SuppressLint("SetJavaScriptEnabled")
            val wv = WebView(context)
            wv.settings.javaScriptEnabled = true
            wv.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    view.evaluateJavascript(script) { v ->
                        out.append(v ?: "")
                        latch.countDown()
                    }
                }

                override fun onRenderProcessGone(
                    view: WebView,
                    detail: android.webkit.RenderProcessGoneDetail,
                ): Boolean {
                    latch.countDown()
                    return true
                }
            }
            wv.loadDataWithBaseURL("file:///android_asset/richtext/", html, "text/html", "utf-8", null)
        }
        assertTrue("webview render timed out", latch.await(30, TimeUnit.SECONDS))
        return out.toString()
    }

    private val selectScript = """
        (function(){
          var p = document.querySelector('#out p');
          if (!p) { var o = document.getElementById('out'); return JSON.stringify({err:'NO_P', out: o ? o.innerHTML.slice(0,200) : 'NO_OUT', body: document.body.innerHTML.slice(0,120), ready: document.readyState}); }
          var maths = p.querySelectorAll('[data-tex]');
          if (!maths.length) return 'NO_MATH';
          var tn = null, w = document.createTreeWalker(maths[0], NodeFilter.SHOW_TEXT, null);
          while (w.nextNode()) { if (w.currentNode.nodeValue.trim()) { tn = w.currentNode; break; } }
          if (!tn) return 'NO_TEXT';
          var r = document.createRange();
          r.setStart(tn, 0);
          r.setEnd(p, p.childNodes.length);
          var s = window.getSelection(); s.removeAllRanges(); s.addRange(r);
          return JSON.stringify({raw: s.toString(), fixed: window.tjMath.selectionText(r)});
        })()
    """.trimIndent()

    @Test fun selectionStartingInsideMathKeepsTex() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val md = "曲线 " + d + "x^{2/3}+y^{2/3}=1" + d + "（" + d + "x>0" + d + "，" + d + "y>0" + d + "）" +
            "上任意一点处的切线在两坐标轴上的截距之和为 " + d + "l" + d + "，试求 " + d + "l" + d + " 的最大值。"
        val json = renderAndSelect(ctx, md, selectScript)
        File(ctx.getExternalFilesDir(null), "selection-quote.json").writeText(json)
        println("SELECTION_RESULT=$json")
        assertTrue("selection text should keep the TeX source, got: $json", json.contains("x^{2/3}"))
        assertTrue("selection text should not leak rendered glyphs, got: $json", !json.contains("x23"))
    }

    @Test fun selectionFullyInsideMathKeepsTex() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val md = "已知函数 " + d + "f(x)=x^3-3x" + d + "，则 " + d + "f(x)" + d + " 的单调递增区间是（  ）"
        val json = renderAndSelect(ctx, md, selectScript)
        File(ctx.getExternalFilesDir(null), "selection-quote-2.json").writeText(json)
        println("SELECTION_RESULT2=$json")
        assertTrue("got: $json", json.contains("f(x)=x^3-3x"))
    }
    /** 选区从公式中间开始 / 结束，或跨两个公式——真实拖手柄时的常见形态。 */
    @Test fun selectionPartiallyCoveringMathKeepsTex() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val md = "曲线 " + d + "x^{2/3}+y^{2/3}=1" + d + "（" + d + "x>0" + d + "，" + d + "y>0" + d + "）" +
            "上任意一点处的切线在两坐标轴上的截距之和为 " + d + "l" + d + "，试求 " + d + "l" + d + " 的最大值。"
        val script = """
            (function(){
              var p = document.querySelector('#out p');
              var maths = p.querySelectorAll('[data-tex]');
              var r = document.createRange();
              // 从第一个公式内部（深层文本节点）开始，到段落末尾结束
              var tn = null, w = document.createTreeWalker(maths[0], NodeFilter.SHOW_TEXT, null);
              var texts = [];
              while (w.nextNode()) if (w.currentNode.nodeValue.trim()) texts.push(w.currentNode);
              tn = texts[texts.length - 1] || maths[0];
              r.setStart(tn, 0);
              r.setEnd(p, p.childNodes.length);
              var s = window.getSelection(); s.removeAllRanges(); s.addRange(r);
              // 再测：从段落开头到第二个公式内部结束
              var r2 = document.createRange();
              var tn2 = null, w2 = document.createTreeWalker(maths[1], NodeFilter.SHOW_TEXT, null);
              while (w2.nextNode()) { if (w2.currentNode.nodeValue.trim()) { tn2 = w2.currentNode; break; } }
              r2.setStart(p.firstChild, 0);
              r2.setEnd(tn2 || maths[1], 1);
              var s2 = window.getSelection(); s2.removeAllRanges(); s2.addRange(r2);
              return JSON.stringify({midToEnd: window.tjMath.selectionText(r), startToMid: window.tjMath.selectionText(r2)});
            })()
        """.trimIndent()
        val json = renderAndSelect(ctx, md, script)
        File(ctx.getExternalFilesDir(null), "selection-quote-3.json").writeText(json)
        assertTrue("partial math must keep TeX, got: $json", json.contains("x^{2/3}+y^{2/3}=1"))
        assertTrue("partial math must not leak glyphs, got: $json", !json.contains("x23"))
    }
}
