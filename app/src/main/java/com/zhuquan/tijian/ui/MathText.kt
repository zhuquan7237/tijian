package com.zhuquan.tijian.ui

import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ImageSpan
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView

/**
 * 列表行内的轻量公式渲染：直接用 JLatexMath Android 分支把 $...$ / $$...$$ /
 * \(...\) / 裸 LaTeX 命令段光栅化成 ImageSpan，套在原生 TextView 里。
 * 比每行挂 WebView 轻得多；单条公式渲染失败只回退原文，不影响整行。
 */
object MathTextEngine {

    private val LaTeX_COMMAND = Regex("""\\[a-zA-Z]+""")

    /**
     * 把题干切成 (文本 | LaTeX) 段：
     * 1. 显式定界 $$..$$ / $..$ / \(..\) / \[..\]；
     * 2. 没有定界符时，抓「以 \ 命令开头的连续 ASCII 数学串」
     *    （识别输出经常漏写 $；中文/全角会自然截断段落）。
     */
    fun segments(raw: String): List<Pair<String, String?>> {
        val t = Normalize.text(raw)
        if (!looksLikeMath(t)) return listOf(t to null)

        val out = ArrayList<Pair<String, String?>>()
        val plain = StringBuilder()
        fun flush() { if (plain.isNotEmpty()) { out.add(plain.toString() to null); plain.clear() } }

        var i = 0
        val n = t.length
        while (i < n) {
            val delimiter = when {
                t.startsWith("$$", i) -> "$$"
                t[i] == '$' -> "$"
                t.startsWith("\\(", i) -> "\\("
                t.startsWith("\\[", i) -> "\\["
                else -> null
            }
            if (delimiter != null) {
                val closing = when (delimiter) {
                    "$$" -> "$$"
                    "\\[" -> "\\]"
                    "\\(" -> "\\)"
                    else -> "$"
                }
                val close = t.indexOf(closing, i + delimiter.length)
                if (close > i) {
                    val inner = t.substring(i + delimiter.length, close)
                    if (inner.isNotBlank()) {
                        flush()
                        out.add(inner to inner)
                        i = close + closing.length
                        continue
                    }
                }
            }
            val cmd = LaTeX_COMMAND.find(t, i)
            if (cmd != null && cmd.range.first == i) {
                var j = i
                while (j < n && t[j].code < 0x80) j++
                val inner = t.substring(i, j)
                if (inner.length > 1) {
                    flush()
                    out.add(inner to inner)
                    i = j
                    continue
                }
            }
            plain.append(t[i])
            i++
        }
        flush()
        if (out.isEmpty()) out.add(t to null)

        // 相邻纯文本合并，减少碎片段
        val merged = ArrayList<Pair<String, String?>>()
        for (seg in out) {
            val last = merged.lastOrNull()
            if (last != null && last.second == null && seg.second == null) {
                merged[merged.size - 1] = (last.first + seg.first) to null
            } else merged.add(seg)
        }
        return merged
    }

    /** LaTeX → 着色好的 Drawable（JLatexMath 官方 Android 封装）；失败返回 null（调用方回退原文）。 */
    fun texDrawable(tex: String, sizePx: Float, foreground: Int): android.graphics.drawable.Drawable? = runCatching {
        ru.noties.jlatexmath.JLatexMathDrawable.builder(tex.trim())
            .textSize(sizePx)
            .color(foreground)
            .padding(1)
            .build()
    }.onFailure { android.util.Log.d("MathText", "formula render failed: ${tex.take(80)}: $it") }.getOrNull()

    /** 文本 → 含公式 ImageSpan 的 Spanned；没有成功渲染的公式时返回 null（调用方用普通 Text）。 */
    fun build(raw: String, sizePx: Float, color: Int): Spanned? {
        val segs = segments(raw)
        if (segs.all { it.second == null }) return null
        val sb = SpannableStringBuilder()
        var renderedAny = false
        for ((plain, tex) in segs) {
            if (tex == null) {
                sb.append(plain)
                continue
            }
            val drawable = texDrawable(tex, sizePx, color)
            if (drawable == null) {
                sb.append(cleanMathLite(tex))
            } else {
                renderedAny = true
                sb.append("￼")
                val start = sb.length - 1
                sb.setSpan(ImageSpan(drawable), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        return if (renderedAny) sb else null
    }
}

/** 是否值得一渲染管线：$ 定界、\( \[ 或反斜杠命令。 */
fun looksLikeMath(text: String): Boolean {
    if (text.contains('$') || text.contains("\\(") || text.contains("\\[")) return true
    return text.contains('\\') && LaTeX_COMMAND_RUN.containsMatchIn(text)
}

private val LaTeX_COMMAND_RUN = Regex("""\\[a-zA-Z]+""")

@Composable
fun MathText(
    text: String,
    color: androidx.compose.ui.graphics.Color,
    fontSizeSp: Float,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
) {
    val density = LocalDensity.current
    val fontScale = density.fontScale
    val fs = fontSizeSp * fontScale
    val argb = android.graphics.Color.argb(
        (color.alpha * 255).toInt(), (color.red * 255).toInt(), (color.green * 255).toInt(), (color.blue * 255).toInt()
    )
    val spanned = remember(text, fs, argb) {
        runCatching { MathTextEngine.build(text, fs * density.density, argb) }.getOrNull()
    }
    if (spanned == null) {
        androidx.compose.material3.Text(
            cleanMathLite(text),
            color = color,
            fontSize = androidx.compose.ui.unit.TextUnit(fontSizeSp, androidx.compose.ui.unit.TextUnitType.Sp),
            lineHeight = androidx.compose.ui.unit.TextUnit(fontSizeSp * 1.45f, androidx.compose.ui.unit.TextUnitType.Sp),
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier,
        )
        return
    }
    AndroidView(
        modifier = modifier,
        factory = { tctx ->
            TextView(tctx).apply {
                textSize = fs
                ellipsize = TextUtils.TruncateAt.END
                setTextColor(argb)
            }
        },
        update = { tv ->
            tv.textSize = fs
            tv.setTextColor(argb)
            tv.maxLines = maxLines
            tv.ellipsize = TextUtils.TruncateAt.END
            tv.text = spanned
        },
    )
}
