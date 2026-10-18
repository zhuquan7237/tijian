package com.zhuquan.tijian.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONObject
import kotlin.math.abs

/**
 * 统一富文本渲染：Markdown + KaTeX + mhchem + GFM 表格 + 代码块。
 * 内容在本地 WebView 中渲染，高度回传给 Compose，父级滚动。
 */
object RichTextRenderer {
    @Volatile
    private var template: String? = null

    fun html(context: Context, markdown: String, fontSize: Int, widthDp: Int = 360): String {
        val tpl = template ?: context.assets.open("richtext/renderer.html")
            .bufferedReader().use { it.readText() }.also { template = it }
        return tpl
            .replace("__FONTSIZE__", fontSize.toString())
            .replace("__WIDTH__", widthDp.toString())
            .replace("__CONTENT__", JSONObject.quote(markdown))
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun RichText(
    markdown: String,
    modifier: Modifier = Modifier,
    fontSize: Int = 15,
) {
    if (markdown.isBlank()) return
    val context = LocalContext.current
    val density = LocalDensity.current
    val height = remember(markdown) { mutableIntStateOf(0) }
    // 固定 CSS 视口宽度，避免部分 OEM WebView 按 980px 宽屏排版导致右侧被裁
    // RichText 可能嵌在带内边距的内容卡片里，视口必须比整屏窄，才能触发正确换行。
    // 过宽会让 WebView 认为内容可以横向铺开，Compose 外层再把右侧直接裁掉。
    val widthDp = (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp - 64)
        .coerceAtLeast(240)
    val html = remember(markdown, fontSize, widthDp) {
        RichTextRenderer.html(context, Normalize.text(markdown), fontSize, widthDp)
    }

    val heightModifier = if (height.intValue > 0) {
        Modifier.height(with(density) { height.intValue.toDp() })
    } else {
        Modifier.heightIn(min = 44.dp)
    }

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                webChromeClient = object : android.webkit.WebChromeClient() {
                    override fun onConsoleMessage(m: android.webkit.ConsoleMessage): Boolean {
                        android.util.Log.d("RichTextJS", "${m.message()} @${m.lineNumber()}")
                        return true
                    }
                }
                settings.javaScriptEnabled = true
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = false
                settings.textZoom = 100
                settings.allowFileAccess = true
                settings.allowContentAccess = false
                settings.domStorageEnabled = false
                settings.setSupportZoom(false)
                settings.builtInZoomControls = false
                settings.displayZoomControls = false
                settings.loadsImagesAutomatically = true
                setBackgroundColor(Color.TRANSPARENT)
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                isFocusable = false
                isFocusableInTouchMode = false
                isLongClickable = false
                isNestedScrollingEnabled = false
                addJavascriptInterface(object {
                    @JavascriptInterface
                    fun height(h: Int) {
                        if (h > 0) {
                            val cur = height.intValue
                            if (abs(cur - h) > 1) {
                                android.os.Handler(android.os.Looper.getMainLooper()).post {
                                    height.intValue = h
                                }
                            }
                        }
                    }
                }, "AndroidRichText")
                addOnLayoutChangeListener { v, l, t, r, b, ol, ot, or_, ob ->
                    if (r - l != or_ - ol || b - t != ob - ot) {
                        (v as? WebView)?.evaluateJavascript(
                            "window.__report && window.__report()", null
                        )
                    }
                }
                tag = html
                loadDataWithBaseURL(
                    "file:///android_asset/richtext/", html, "text/html", "utf-8", null
                )
            }
        },
        update = { wv ->
            if (wv.tag != html) {
                wv.tag = html
                wv.loadDataWithBaseURL(
                    "file:///android_asset/richtext/", html, "text/html", "utf-8", null
                )
            }
        },
        modifier = modifier.fillMaxWidth().then(heightModifier)
    )
}

/** 列表/卡片用的轻量文本：不做完整排版，只把常见命令转成可读符号 */
fun cleanMathLite(input: String): String {
    var t = cleanMath(input)
    val bs = 0x5C.toChar().toString()
    val greek = mapOf(
        "alpha" to "α", "beta" to "β", "gamma" to "γ", "delta" to "δ", "theta" to "θ",
        "lambda" to "λ", "mu" to "μ", "pi" to "π", "rho" to "ρ", "sigma" to "σ",
        "phi" to "φ", "omega" to "ω", "Delta" to "Δ", "Sigma" to "Σ", "Omega" to "Ω",
        "varepsilon" to "ε", "epsilon" to "ε", "varphi" to "φ"
    )
    greek.forEach { (k, v) -> t = t.replace(bs + k, v) }
    t = t.replace(bs + "lim", "lim").replace(bs + "to", "→").replace(bs + "rightarrow", "→")
        .replace(bs + "Rightarrow", "⇒").replace(bs + "int", "∫").replace(bs + "sum", "∑")
        .replace(bs + "prod", "∏").replace(bs + "cdot", "·").replace(bs + "times", "×")
        .replace(bs + "pm", "±").replace(bs + "approx", "≈").replace(bs + "neq", "≠")
        .replace(bs + "leq", "≤").replace(bs + "geq", "≥").replace(bs + "infty", "∞")
        .replace(bs + "quad", " ").replace(bs + "qquad", "  ")
    t = t.replace(Regex(bs + "dfrac"), "").replace(Regex(bs + "tfrac"), "")
    // sqrt{...}
    t = Regex(bs + bs + "sqrt" + bs + "?\\{([^{}]*)\\}").replace(t) { "√(${it.groupValues[1]})" }
    // lim_{x\to 0}
    t = t.replace(Regex("lim_\\{([^{}]*)\\}"), "lim($1)")
    // 保留 \\ 换行与矩阵环境，但去掉环境标记
    t = Regex(bs + "begin\\{[a-zA-Z*]+\\}").replace(t, "")
    t = Regex(bs + "end\\{[a-zA-Z*]+\\}").replace(t, "")
    t = t.replace("&", " ").replace(bs + bs, "  ")
    return t.trim()
}
