package com.zhuquan.tijian.export

import android.app.Dialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
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
        val html = RichTextRenderer.html(ctx, Normalize.text(markdown), fontSize)
            .replace("width=device-width", "width=$widthPx")
        val dialog = Dialog(ctx, android.R.style.Theme_Translucent_NoTitleBar)
        val web = WebView(ctx)
        var lastHeight = 0
        val first = CompletableDeferred<Int>()

        try {
            web.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            web.setBackgroundColor(Color.WHITE)
            web.settings.javaScriptEnabled = true
            web.settings.allowFileAccess = true
            web.isVerticalScrollBarEnabled = false
            web.addJavascriptInterface(object {
                @JavascriptInterface
                fun height(h: Int) {
                    if (h > 0) {
                        lastHeight = h
                        if (!first.isCompleted) first.complete(h)
                    }
                }
            }, "AndroidRichText")

            val container = FrameLayout(ctx)
            container.addView(
                web,
                ViewGroup.LayoutParams(widthPx, ViewGroup.LayoutParams.WRAP_CONTENT)
            )
            dialog.setContentView(container)
            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            dialog.window?.setLayout(1, 1)
            dialog.show()

            web.loadDataWithBaseURL(
                "file:///android_asset/richtext/", html, "text/html", "utf-8", null
            )

            val h0 = withTimeoutOrNull(7000) { first.await() } ?: return@withContext null
            delay(1100) // 等字体、KaTeX、表格完成排版
            val reported = maxOf(h0, lastHeight)
            val measured = queryHeight(web)
            // measured 是最终视口下的真实高度；reported 可能来自窄视口的早期测量
            val contentH = (if (measured > 0) measured else reported).coerceIn(24, 8000) + 6

            web.measure(
                View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(contentH, View.MeasureSpec.EXACTLY)
            )
            web.layout(0, 0, widthPx, contentH)
            delay(350)

            val bmp = Bitmap.createBitmap(widthPx, contentH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            canvas.drawColor(Color.WHITE)
            web.draw(canvas)
            bmp
        } catch (e: Exception) {
            null
        } finally {
            try { dialog.dismiss() } catch (_: Exception) {}
            try { web.destroy() } catch (_: Exception) {}
        }
    }

    private suspend fun queryHeight(web: WebView): Int = suspendCancellableCoroutine { cont ->
        web.evaluateJavascript(
            "(function(){var e=document.getElementById('out');" +
                "return JSON.stringify({h:Math.max(e.scrollHeight,document.body.scrollHeight,document.documentElement.scrollHeight)," +
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
