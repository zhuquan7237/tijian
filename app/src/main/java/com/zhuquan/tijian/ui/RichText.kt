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
                put("markdown", Normalize.text(sec.markdown))
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
        /** PDF 位图导出用浅色打印配色；界面按日/夜主题传 rt-day 或默认夜色。 */
        print: Boolean = false,
        day: Boolean = false,
    ): String {
        val tpl = template ?: context.assets.open("richtext/renderer.html")
            .bufferedReader().use { it.readText() }.also { template = it }
        val theme = when {
            print -> "rt-print"
            day -> "rt-day"
            else -> ""
        }
        return tpl
            .replace("__FONTSIZE__", fontSize.toString())
            .replace("__WIDTH__", widthDp.toString())
            .replace("__RENDER_ID__", renderId.toString())
            .replace("__SELECTABLE__", if (selectable) "true" else "false")
            .replace("__SECTIONS__", sectionsJson(sections))
            .replace("__THEME__", theme)
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
                "(function(){var s=window.getSelection();var t=(s&&s.rangeCount&&window.tjMath)?window.tjMath.selectionText(s.getRangeAt(0)):'';return window.__rtSelText||t;})()"
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
    val fontScale = LocalDensity.current.fontScale * (readingSize.intValue / 15f)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    BoxWithConstraints(modifier.fillMaxWidth()) {
    val widthDp = maxWidth.value.roundToInt().coerceAtLeast(1)
    val scaledFontSize = (fontSize * fontScale).roundToInt().coerceAtLeast(1)
    val normalized = remember(markdown) { Normalize.text(markdown) }
    val scaledSections = sections?.map { it.copy(fontSize = (it.fontSize * fontScale).roundToInt()) }
    val day = !isNight
    // 主题切换要整页重渲染（CSS 类在 HTML 里），day 计入 renderId 的 key
    val renderId = remember(normalized, scaledFontSize, widthDp, selectable, sections, day) { RichTextRenderer.nextGeneration() }
    val height = remember(renderId) {
        mutableIntStateOf(0)
    }
    val html = remember(normalized, scaledFontSize, widthDp, renderId) {
        RichTextRenderer.html(context, normalized, scaledFontSize, widthDp, renderId, selectable, scaledSections, day = day)
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
                // file:///android_asset/ 在新版系统上不受此开关限制，收紧到 false
                settings.allowFileAccess = false
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

/** 列表/卡片用的轻量文本：把常见 LaTeX 转成可读符号，绝不把源码直显。 */
fun cleanMathLite(input: String): String {
    var t = input
        .replace("$$", "")
        .replace("$", "")
        .replace("\r\n", "\n")
    // \(..\) 与 \[..\] 定界符直接去掉，正文保留
    t = t.replace("\\(", "").replace("\\)", "")
        .replace("\\[", "").replace("\\]", "")
    // JSON 多层转义：\\sin → \sin
    t = Regex("""\\\\(?=[A-Za-z])""").replace(t, """\""")
    t = expandLatex(t)
    // 上下标整体处理：纯数字/正负号或单个可映射字符 → Unicode 上标（x²、eˣ）；
    // 多字符字母组合（如积分上限 x-y）退回 ^(x-y) 的可读形式，绝不逐字符拆散。
    t = Regex("""\^\{([^{}]*)\}""").replace(t) { m ->
        val inner = m.groupValues[1]
        val simple = inner.isNotEmpty() && (
            inner.all { superMap.containsKey(it) && !it.isLetter() } ||
                (inner.length == 1 && superMap.containsKey(inner[0]))
            )
        if (simple) superscript(inner) else "^($inner)"
    }
    t = Regex("""_\{([^{}]*)\}""").replace(t) { m ->
        val inner = m.groupValues[1]
        if (inner.isNotEmpty() && inner.all { subMap.containsKey(it) }) subscript(inner)
        else "_($inner)"
    }
    t = t.replace("{", "").replace("}", "")
    // 卡片空间有限，把常见的上下标转成 Unicode，至少读起来像公式而不是源码。
    t = Regex("\\^(\\d+)").replace(t) { superscript(it.groupValues[1]) }
    t = Regex("\\^([xyn])").replace(t) { superscript(it.groupValues[1]) }
    t = Regex("lim_([^\\s(]+)").replace(t) { "lim(${it.groupValues[1]})" }
    t = Regex("""_([0-9a-zA-Z])""").replace(t) { m ->
        val c = m.groupValues[1]
        if (subMap.containsKey(c[0])) subMap[c[0]].toString() else "_" + c
    }
    t = Regex("\\\\([A-Za-z]+)").replace(t) { it.groupValues[1] }
    t = t.replace("&", " ")
    t = Regex("[ \t]+").replace(t, " ")
    return t.trim()
}

private val superMap = mapOf(
    '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴',
    '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹',
    'x' to 'ˣ', 'y' to 'ʸ', 'n' to 'ⁿ', '+' to '⁺', '-' to '⁻'
)

private val subMap = mapOf(
    '0' to '₀', '1' to '₁', '2' to '₂', '3' to '₃', '4' to '₄',
    '5' to '₅', '6' to '₆', '7' to '₇', '8' to '₈', '9' to '₉', 'n' to 'ₙ'
)

private fun superscript(raw: String): String =
    raw.map { superMap[it] ?: it }.joinToString("")

private fun subscript(raw: String): String =
    raw.map { subMap[it] ?: it }.joinToString("")

private val latexSymbols = mapOf(
    "alpha" to "α", "beta" to "β", "gamma" to "γ", "delta" to "δ", "theta" to "θ",
    "lambda" to "λ", "mu" to "μ", "nu" to "ν", "xi" to "ξ", "pi" to "π", "rho" to "ρ",
    "sigma" to "σ", "tau" to "τ", "phi" to "φ", "chi" to "χ", "psi" to "ψ", "omega" to "ω",
    "Delta" to "Δ", "Sigma" to "Σ", "Omega" to "Ω", "Gamma" to "Γ", "Lambda" to "Λ",
    "Phi" to "Φ", "Psi" to "Ψ", "Pi" to "Π", "Theta" to "Θ",
    "varepsilon" to "ε", "epsilon" to "ε", "varphi" to "φ", "vartheta" to "ϑ",
    "geqslant" to "≥", "leqslant" to "≤", "geq" to "≥", "leq" to "≤", "ge" to "≥", "le" to "≤",
    "ne" to "≠", "neq" to "≠", "approx" to "≈", "equiv" to "≡", "sim" to "∼", "simeq" to "≃",
    "propto" to "∝", "ll" to "≪", "gg" to "≫",
    "to" to "→", "rightarrow" to "→", "leftarrow" to "←", "Rightarrow" to "⇒",
    "Leftarrow" to "⇐", "leftrightarrow" to "↔", "mapsto" to "↦",
    "infty" to "∞", "pm" to "±", "mp" to "∓", "times" to "×", "cdot" to "·", "div" to "÷",
    "sum" to "∑", "prod" to "∏", "int" to "∫", "iint" to "∬", "iiint" to "∭",
    "lim" to "lim", "ln" to "ln", "log" to "log", "lg" to "lg",
    "sin" to "sin", "cos" to "cos", "tan" to "tan", "cot" to "cot", "sec" to "sec", "csc" to "csc",
    "arcsin" to "arcsin", "arccos" to "arccos", "arctan" to "arctan",
    "max" to "max", "min" to "min", "sup" to "sup", "inf" to "inf", "det" to "det",
    "dim" to "dim", "ker" to "ker", "deg" to "deg", "gcd" to "gcd", "hom" to "hom",
    "partial" to "∂", "nabla" to "∇", "emptyset" to "∅", "varnothing" to "∅",
    "in" to "∈", "notin" to "∉", "ni" to "∋",
    "subset" to "⊂", "subseteq" to "⊆", "supset" to "⊃", "supseteq" to "⊇",
    "cup" to "∪", "cap" to "∩", "forall" to "∀", "exists" to "∃",
    "perp" to "⊥", "angle" to "∠", "circ" to "∘", "degree" to "°",
    "quad" to " ", "qquad" to "  ", "ldots" to "…", "cdots" to "⋯", "dots" to "…",
    "because" to "∵", "therefore" to "∴", "langle" to "⟨", "rangle" to "⟩",
    "lvert" to "|", "rvert" to "|", "mid" to "|",
)

/** 递归展开 \frac / \sqrt / \text，并把命令换成 Unicode 符号。 */
private fun expandLatex(input: String): String {
    val sb = StringBuilder()
    var i = 0
    while (i < input.length) {
        val ch = input[i]
        if (ch != '\\') { sb.append(ch); i++; continue }
        val cmd = Regex("""^[A-Za-z]+""").find(input.substring(i + 1))?.value
        if (cmd == null) {
            val next = input.getOrNull(i + 1) ?: ' '
            when (next) {
                '{', '}', '%', '_', '&', '#', '$' -> sb.append(next)
                ',', ';', ':', '!', ' ' -> sb.append(' ')
                '\\' -> sb.append("; ")
                else -> sb.append(next)
            }
            i += 2
            continue
        }
        val after = i + 1 + cmd.length
        when (cmd) {
            "frac", "dfrac", "tfrac" -> {
                val a = latexGroup(input, skipSpace(input, after))
                val b = a?.let { latexGroup(input, skipSpace(input, it.second)) }
                if (a != null && b != null) {
                    sb.append('(').append(expandLatex(a.first)).append(")/(")
                        .append(expandLatex(b.first)).append(')')
                    i = b.second
                } else { sb.append(cmd); i = after }
            }
            "sqrt" -> {
                var j = skipSpace(input, after)
                var index = ""
                if (input.getOrNull(j) == '[') {
                    val close = input.indexOf(']', j + 1)
                    if (close > j) { index = input.substring(j + 1, close); j = skipSpace(input, close + 1) }
                }
                val g = latexGroup(input, j)
                if (g != null) {
                    sb.append(if (index.isBlank()) "√(" else "√[$index](")
                        .append(expandLatex(g.first)).append(')')
                    i = g.second
                } else { sb.append("√"); i = after }
            }
            "text", "mathrm", "mathbf", "mathit", "operatorname", "mbox" -> {
                val g = latexGroup(input, skipSpace(input, after))
                if (g != null) { sb.append(expandLatex(g.first)); i = g.second }
                else { sb.append(cmd); i = after }
            }
            "left", "right", "limits", "nolimits", "displaystyle", "textstyle",
            "big", "Big", "bigg", "Bigg", "bigl", "bigr", "Bigl", "Bigr" -> i = after
            "begin", "end" -> {
                val g = latexGroup(input, skipSpace(input, after))
                if (g != null) i = g.second else { sb.append(cmd); i = after }
            }
            else -> { sb.append(latexSymbols[cmd] ?: cmd); i = after }
        }
    }
    return sb.toString()
}

private fun skipSpace(s: String, from: Int): Int {
    var i = from
    while (i < s.length && s[i].isWhitespace()) i++
    return i
}

private fun latexGroup(s: String, open: Int): Pair<String, Int>? {
    if (s.getOrNull(open) != '{') return null
    var depth = 0
    for (i in open until s.length) {
        when (s[i]) {
            '{' -> depth++
            '}' -> {
                depth--
                if (depth == 0) return s.substring(open + 1, i) to i + 1
            }
        }
    }
    return null
}
