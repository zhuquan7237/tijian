package com.zhuquan.tijian.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.FrameLayout
import com.zhuquan.tijian.ui.Normalize
import com.zhuquan.tijian.ui.RichTextRenderer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * 把富文本渲染成位图，供 PDF 使用。
 * 这样导出结果与 App 内显示共用同一套 KaTeX / Markdown 管线，公式不会退化成源码。
 */
object RichBitmap {

    suspend fun render(
        ctx: Context,
        markdown: String,
        widthPx: Int = 1400,
        fontSize: Int = 13
    ): Bitmap? = withContext(Dispatchers.Main) {
        if (markdown.isBlank()) return@withContext null

        // 固定 CSS 视口宽度，避免受设备 DPR 影响导致字号与排版比例失真
        val html = RichTextRenderer.html(ctx, Normalize.text(markdown), fontSize, widthPx)
        // Attach the offscreen WebView to the running activity's window. A Dialog
        // needs a valid window token; using the application context raised
        // BadTokenException and made PDF formulas silently fall back to text.
        val activity = com.zhuquan.tijian.MainActivity.current?.get()
            ?: (ctx as? android.app.Activity)
        val root = activity?.window?.decorView as? android.view.ViewGroup
        val web = WebView(ctx)
        var container: FrameLayout? = null
        var lastHeight = 0
        val first = CompletableDeferred<Int>()

        try {
            web.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            web.setBackgroundColor(Color.WHITE)
            web.settings.javaScriptEnabled = true
            web.settings.allowFileAccess = true
            web.settings.domStorageEnabled = false
            web.settings.javaScriptCanOpenWindowsAutomatically = false
            web.setInitialScale(100)
            web.isVerticalScrollBarEnabled = false
            web.addJavascriptInterface(object {
                @JavascriptInterface
                fun height(h: Int, renderId: Int) {
                    if (h > 0) {
                        lastHeight = h
                        if (!first.isCompleted) first.complete(h)
                    }
                }
            }, "AndroidRichText")
            web.webViewClient = object : android.webkit.WebViewClient() {
                override fun onPageFinished(view: android.webkit.WebView, url: String?) {
                    view.evaluateJavascript("window.__report && window.__report()", null)
                }
            }

            val host = FrameLayout(ctx)
            container = host
            host.addView(
                web,
                ViewGroup.LayoutParams(widthPx, ViewGroup.LayoutParams.WRAP_CONTENT)
            )
            host.alpha = 0f
            if (root != null) {
                root.addView(host, ViewGroup.LayoutParams(widthPx, ViewGroup.LayoutParams.WRAP_CONTENT))
                web.resumeTimers()
                web.onResume()
            } else {
                android.util.Log.e("RichBitmap", "no activity window for offscreen renderer")
                return@withContext null
            }

            web.loadDataWithBaseURL(
                "file:///android_asset/richtext/", html, "text/html", "utf-8", null
            )

            val h0 = withTimeoutOrNull(9000) { first.await() } ?: return@withContext null
            var stable = 0
            var previous = -1
            for (attempt in 0 until 12) {
                delay(180)
                val now = queryHeight(web)
                if (now > 0 && now == previous) stable++ else stable = 0
                previous = now
                if (stable >= 2) break
            }
            val reported = maxOf(h0, lastHeight)
            val measured = queryHeight(web)
            // measured 是最终视口下的真实高度；reported 可能来自窄视口的早期测量
            // A window-attached WebView can report the visible viewport height
            // instead of the full document. Use the larger of both measurements
            // and the currently laid out height so no block is clipped.
            val laidOut = web.height.coerceAtLeast(0)
            val contentH = maxOf(measured, reported, laidOut, 24).coerceIn(24, 8000) + 6

            web.measure(
                View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(contentH, View.MeasureSpec.EXACTLY)
            )
            // The WebView is still in a FrameLayout; force the container to the
            // measured size before drawing, otherwise the window clips to screen
            // height and only the first block makes it into the bitmap.
            container?.measure(
                View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(contentH, View.MeasureSpec.EXACTLY)
            )
            container?.layout(0, 0, widthPx, contentH)
            web.layout(0, 0, widthPx, contentH)
            web.requestLayout()
            web.invalidate()
            delay(350)

            val bmp = Bitmap.createBitmap(widthPx, contentH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            canvas.drawColor(Color.WHITE)
            web.invalidate()
            web.draw(canvas)
            bmp
        } catch (e: Exception) {
            android.util.Log.e("RichBitmap", "render failed", e)
            null
        } finally {
            try { container?.let { (it.parent as? ViewGroup)?.removeView(it) } } catch (_: Exception) {}
            try { web.destroy() } catch (_: Exception) {}
        }
    }

    private suspend fun queryHeight(web: WebView): Int = suspendCancellableCoroutine { cont ->
        web.evaluateJavascript(
            "(function(){var e=document.getElementById('out');" +
                "return JSON.stringify({h:window.__measureRichText ? window.__measureRichText() : e.scrollHeight," +
                "o:e.scrollHeight,b:document.body.scrollHeight,w:document.body.clientWidth," +
                "fs:getComputedStyle(document.body).fontSize,len:document.body.innerText.length});})()"
        ) { v ->
            val h = try {
                val raw = org.json.JSONTokener(v ?: "\"\"").nextValue() as? String ?: "{}"
                org.json.JSONObject(raw).optInt("h", 0)
            } catch (e: Exception) { 0 }
            if (cont.isActive) cont.resume(h)
        }
    }
}
