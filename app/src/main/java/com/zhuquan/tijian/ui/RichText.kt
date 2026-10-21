package com.zhuquan.tijian.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
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
/** 详情页的一段富文本：标题、副标题、强调色、正文，最终渲染成一张 HTML 白卡。 */
data class RichSectionSpec(
    val title: String,
    val markdown: String,
    val subtitle: String = "",
    val color: String = "#2DD4BF",
    val fontSize: Int = 15,
    /** 渲染变体：analysis 走「思路 / 分步 / 易错 / 结论」结构化排版；answer / needs 只改样式。 */
    val kind: String = "",
)

object RichTextRenderer {
    private val generations = AtomicInteger()
    fun nextGeneration(): Int = generations.incrementAndGet()
    @Volatile
    private var template: String? = null

    private fun sectionsJson(sections: List<RichSectionSpec>?): String {
        if (sections == null) return "null"
        val arr = org.json.JSONArray()
        sections.forEach { sec ->
            arr.put(JSONObject().apply {
                put("title", sec.title)
                put("subtitle", sec.subtitle)
                put("color", sec.color)
                put("markdown", sec.markdown)
                put("fontSize", sec.fontSize)
                put("kind", sec.kind)
            })
        }
        return arr.toString()
    }

    fun html(
        context: Context,
        markdown: String,
        fontSize: Int,
        widthDp: Int = 360,
        renderId: Int = 0,
        selectable: Boolean = false,
        sections: List<RichSectionSpec>? = null,
        /** PDF 位图导出用浅色打印配色；界面始终是夜读台深色。 */
        print: Boolean = false,
    ): String {
        val tpl = template ?: context.assets.open("richtext/renderer.html")
            .bufferedReader().use { it.readText() }.also { template = it }
        return tpl
            .replace("__FONTSIZE__", fontSize.toString())
            .replace("__WIDTH__", widthDp.toString())
            .replace("__RENDER_ID__", renderId.toString())
            .replace("__SELECTABLE__", if (selectable) "true" else "false")
            .replace("__SECTIONS__", sectionsJson(sections))
            .replace("__THEME__", if (print) "rt-print" else "")
            .replace("__CONTENT__", JSONObject.quote(markdown))
    }
}

/**
 * 原生长按选词 + 拖手柄扩选，但把系统的「翻译 / 网页搜索 / 全选」菜单
 * 换成应用自己的「复制 / 追问」。选区手柄保留，菜单只出现两项。
 */
private class TjWebView(ctx: Context) : WebView(ctx) {
    var selectionEnabled: Boolean = false
    var onCopyText: ((String) -> Unit)? = null
    var onAskText: ((String) -> Unit)? = null

    override fun startActionMode(callback: ActionMode.Callback?, type: Int): ActionMode? {
        if (!selectionEnabled) return super.startActionMode(callback, type)
        return super.startActionMode(SelectionMenu(), type)
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun startActionMode(callback: ActionMode.Callback?): ActionMode? {
        if (!selectionEnabled) return super.startActionMode(callback)
        return super.startActionMode(SelectionMenu())
    }

    private inner class SelectionMenu : ActionMode.Callback {
        private fun build(menu: Menu) {
            menu.clear()
            menu.add(Menu.NONE, MENU_COPY, 0, "复制")
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            menu.add(Menu.NONE, MENU_ASK, 1, "追问")
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }

        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            build(menu)
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            build(menu)
            return true
        }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            if (item.itemId != MENU_COPY && item.itemId != MENU_ASK) return false
            evaluateJavascript(
                "(function(){var s=window.getSelection();return window.__rtSelText||(s?s.toString():'');})()"
            ) { raw ->
                val text = decodeJsString(raw)
                if (text.isNotBlank()) {
                    if (item.itemId == MENU_COPY) onCopyText?.invoke(text)
                    else onAskText?.invoke(text)
                }
                evaluateJavascript("window.__rtClearSel && window.__rtClearSel()", null)
                mode.finish()
            }
            return true
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            // 不要在这里清空选区：拖动左右手柄时 WebView 会先销毁再重建
            // ActionMode，清空会把正在调整的选区直接抹掉，手柄也随之消失。
            // 选区交给系统在点击空白处时自行收起。
        }
    }

    companion object {
        const val MENU_COPY = 1
        const val MENU_ASK = 2
    }
}

private fun decodeJsString(raw: String?): String {
    if (raw.isNullOrBlank() || raw == "null") return ""
    return try {
        org.json.JSONArray("[$raw]").optString(0, "")
    } catch (e: Exception) {
        raw.trim('"')
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun RichText(
    markdown: String,
    modifier: Modifier = Modifier,
    fontSize: Int = 15,
    sections: List<RichSectionSpec>? = null,
    selectable: Boolean = false,
    /** false 时 WebView 不接管触摸，交给外层 Compose 滚动（聊天面板这种只读场景）。 */
    interactive: Boolean = true,
    onSelect: (String) -> Unit = {},
    onAsk: (String) -> Unit = {},
    onReady: () -> Unit = {},
) {
    if (sections == null && markdown.isBlank()) return
    val context = LocalContext.current
    val fontScale = LocalDensity.current.fontScale
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    BoxWithConstraints(modifier.fillMaxWidth()) {
    val widthDp = maxWidth.value.roundToInt().coerceAtLeast(1)
    val scaledFontSize = (fontSize * fontScale).roundToInt().coerceAtLeast(1)
    val normalized = remember(markdown) { Normalize.text(markdown) }
    val renderId = remember(normalized, scaledFontSize, widthDp, selectable, sections) { RichTextRenderer.nextGeneration() }
    val height = remember(renderId) {
        mutableIntStateOf(0)
    }
    val html = remember(normalized, scaledFontSize, widthDp, renderId) {
        RichTextRenderer.html(context, normalized, scaledFontSize, widthDp, renderId, selectable, sections)
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
            TjWebView(ctx).apply {
                webReference[0] = this
                selectionEnabled = selectable && interactive
                onCopyText = { text ->
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("题笺", text))
                    android.widget.Toast.makeText(ctx, "已复制", android.widget.Toast.LENGTH_SHORT).show()
                }
                onAskText = { text -> askCallback.value(text) }
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
                isFocusable = selectable && interactive
                isFocusableInTouchMode = selectable && interactive
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
