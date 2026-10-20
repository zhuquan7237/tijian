package com.zhuquan.tijian.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.zhuquan.tijian.BuildConfig
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.roundToInt
import java.util.concurrent.atomic.AtomicInteger

/**
 * 统一富文本渲染：Markdown + KaTeX + mhchem + GFM 表格 + 代码块。
 * 内容在本地 WebView 中渲染，高度回传给 Compose，父级滚动。
 */
object RichTextRenderer {
    private val generations = AtomicInteger()
    fun nextGeneration(): Int = generations.incrementAndGet()
    @Volatile
    private var template: String? = null

    fun html(
        context: Context,
        markdown: String,
        fontSize: Int,
        widthDp: Int = 360,
        renderId: Int = 0,
        selectable: Boolean = false,
    ): String {
        val tpl = template ?: context.assets.open("richtext/renderer.html")
            .bufferedReader().use { it.readText() }.also { template = it }
        return tpl
            .replace("__FONTSIZE__", fontSize.toString())
            .replace("__WIDTH__", widthDp.toString())
            .replace("__RENDER_ID__", renderId.toString())
            .replace("__SELECTABLE__", if (selectable) "true" else "false")
            .replace("__CONTENT__", JSONObject.quote(markdown))
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun RichText(
    markdown: String,
    modifier: Modifier = Modifier,
    fontSize: Int = 15,
    selectable: Boolean = false,
    /** false 时 WebView 不接管触摸，交给外层 Compose 滚动（聊天面板这种只读场景）。 */
    interactive: Boolean = true,
    onSelect: (String) -> Unit = {},
    onAsk: (String) -> Unit = {},
    onReady: () -> Unit = {},
) {
    if (markdown.isBlank()) return
    val context = LocalContext.current
    val fontScale = LocalDensity.current.fontScale
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    BoxWithConstraints(modifier.fillMaxWidth()) {
    val widthDp = maxWidth.value.roundToInt().coerceAtLeast(1)
    val scaledFontSize = (fontSize * fontScale).roundToInt().coerceAtLeast(1)
    val normalized = remember(markdown) { Normalize.text(markdown) }
    val renderId = remember(normalized, scaledFontSize, widthDp, selectable) { RichTextRenderer.nextGeneration() }
    val height = remember(renderId) {
        mutableIntStateOf(0)
    }
    val html = remember(normalized, scaledFontSize, widthDp, renderId) {
        RichTextRenderer.html(context, normalized, scaledFontSize, widthDp, renderId, selectable)
    }
    // AndroidView factory lives longer than this document: always route callbacks
    // to the current state, and check the generation again on the UI thread.
    val receiveHeight = rememberUpdatedState<(Int, Int) -> Unit> { h, generation ->
        if (generation == renderId && h > 0 && abs(height.intValue - h) >= 1) height.intValue = h
    }
    val readyCallback = rememberUpdatedState(onReady)
    val askCallback = rememberUpdatedState(onAsk)
    val selectCallback = rememberUpdatedState(onSelect)
    androidx.compose.runtime.LaunchedEffect(renderId, height.intValue) {
        if (height.intValue > 0) readyCallback.value()
    }
    val webReference = remember { arrayOfNulls<WebView>(1) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            webReference[0]?.let { web ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    web.onResume()
                    web.evaluateJavascript("window.__report && window.__report()", null)
                } else if (event == Lifecycle.Event.ON_PAUSE) web.onPause()
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    val heightModifier = if (height.intValue > 0) {
        // JS reports CSS px from the WebView viewport. CSS px already maps to
        // the Android dp-sized viewport, so converting it as physical px with
        // toDp() would divide by density a second time and clip the content.
        Modifier.height(height.intValue.dp)
    } else {
        Modifier.heightIn(min = 1.dp)
    }

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                webReference[0] = this
                if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true)
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        view.scrollTo(0, 0)
                        view.evaluateJavascript("window.__report && window.__report()", null)
                    }
                }
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
                isLongClickable = selectable && interactive
                isEnabled = interactive
                isNestedScrollingEnabled = false
                if (!interactive) {
                    setOnTouchListener { _, _ -> false }
                }
                addJavascriptInterface(object {
                    @JavascriptInterface
                    fun height(h: Int, callbackRenderId: Int) {
                        post { receiveHeight.value(h, callbackRenderId) }
                    }

                    @JavascriptInterface
                    fun copy(text: String) {
                        post {
                            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            cm.setPrimaryClip(android.content.ClipData.newPlainText("题笺", text))
                            android.widget.Toast.makeText(ctx, "已复制", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }

                    @JavascriptInterface
                    fun ask(text: String) {
                        post { askCallback.value(text) }
                    }

                    @JavascriptInterface
                    fun select(text: String) {
                        post { selectCallback.value(text) }
                    }

                    @JavascriptInterface
                    fun clearSelection() {
                        post { selectCallback.value("") }
                    }
                }, "AndroidRichText")
                addOnLayoutChangeListener { v, l, t, r, b, ol, ot, or_, ob ->
                    if (r - l != or_ - ol) {
                        (v as? WebView)?.evaluateJavascript(
                            "window.__report && window.__report()", null
                        )
                    }
                }
                scrollTo(0, 0)
                tag = html
                loadDataWithBaseURL(
                    "file:///android_asset/richtext/", html, "text/html", "utf-8", null
                )
            }
        },
        update = { wv ->
            if (wv.tag != html) {
                wv.stopLoading()
                wv.scrollTo(0, 0)
                wv.tag = html
                wv.loadDataWithBaseURL(
                    "file:///android_asset/richtext/", html, "text/html", "utf-8", null
                )
            }
        },
        onRelease = { web ->
            if (webReference[0] === web) webReference[0] = null
            web.stopLoading()
            web.removeJavascriptInterface("AndroidRichText")
            web.destroy()
        },
        modifier = Modifier.fillMaxWidth().then(heightModifier)
    )
    }
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
