package com.zhuquan.tijian.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ReplacementSpan
import android.widget.TextView
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.scilab.forge.jlatexmath.TeXConstants
import org.scilab.forge.jlatexmath.TeXFormula
import kotlin.math.roundToInt

/**
 * 列表里的行内公式。用文本样式（不是展示样式）光栅化，高度封顶，
 * 基线和旁边的汉字对齐，超宽的公式按顶层运算符拆开以便换行。
 * 单条公式失败只回退成可读文本，不把 LaTeX 源码甩到界面上。
 */
object MathTextEngine {

    private class Glyph(val bitmap: Bitmap, val baselineFromTop: Int)

    private class MathImageSpan(
        private val bitmap: Bitmap,
        private val baselineFromTop: Int,
    ) : ReplacementSpan() {
        override fun getSize(
            paint: android.graphics.Paint,
            text: CharSequence,
            start: Int,
            end: Int,
            fm: android.graphics.Paint.FontMetricsInt?,
        ): Int {
            if (fm != null) {
                val ascent = -baselineFromTop
                val descent = bitmap.height - baselineFromTop
                if (fm.ascent > ascent) fm.ascent = ascent
                if (fm.top > fm.ascent) fm.top = fm.ascent
                if (fm.descent < descent) fm.descent = descent
                if (fm.bottom < fm.descent) fm.bottom = fm.descent
            }
            return bitmap.width + 2
        }

        override fun draw(
            canvas: Canvas,
            text: CharSequence,
            start: Int,
            end: Int,
            x: Float,
            top: Int,
            y: Int,
            bottom: Int,
            paint: android.graphics.Paint,
        ) {
            canvas.drawBitmap(bitmap, x + 1f, (y - baselineFromTop).toFloat(), null)
        }
    }

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
                else -> null
            }
            if (delimiter != null) {
                val close = t.indexOf(delimiter, i + delimiter.length)
                if (close > i) {
                    val inner = t.substring(i + delimiter.length, close).trim()
                    if (inner.isNotEmpty()) {
                        flush()
                        out.add(inner to inner)
                        i = close + delimiter.length
                        continue
                    }
                }
            }
            plain.append(t[i])
            i++
        }
        flush()
        if (out.isEmpty()) out.add(t to null)
        return out
    }

    fun build(raw: String, sizePx: Float, color: Int, maxWidthPx: Int): Spanned? {
        val segs = segments(raw)
        if (segs.all { it.second == null }) return null
        val sb = SpannableStringBuilder()
        var renderedAny = false
        val heightCap = (sizePx * 1.7f).roundToInt().coerceAtLeast(1)
        val widthCap = maxWidthPx.coerceAtLeast(1)
        for ((plain, tex) in segs) {
            if (tex == null) {
                sb.append(plain)
                continue
            }
            val glyphs = layoutFormula(tex, sizePx, color, widthCap, heightCap)
            if (glyphs.isEmpty()) {
                sb.append(cleanMathLite(tex))
                continue
            }
            renderedAny = true
            glyphs.forEachIndexed { index, glyph ->
                if (index > 0) sb.append(' ')
                val start = sb.length
                sb.append('\uFFFC')
                sb.setSpan(
                    MathImageSpan(glyph.bitmap, glyph.baselineFromTop),
                    start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
        }
        return if (renderedAny) sb else null
    }

    private fun layoutFormula(tex: String, sizePx: Float, color: Int, widthCap: Int, heightCap: Int): List<Glyph> {
        val whole = render(tex, sizePx, color, widthCap, heightCap)
        if (whole != null && whole.bitmap.width <= widthCap) return listOf(whole)
        val parts = splitTopLevel(tex)
        if (parts.size <= 1) return listOfNotNull(whole)
        return parts.mapNotNull { render(it, sizePx, color, widthCap, heightCap) }
    }

    /** 只在花括号外面拆 + − =，长公式才能换行，分式内部不会被拦腰截断。 */
    private fun splitTopLevel(tex: String): List<String> {
        val parts = ArrayList<String>()
        val sb = StringBuilder()
        var depth = 0
        var i = 0
        while (i < tex.length) {
            val c = tex[i]
            if (c == '\\') {
                sb.append(c)
                if (i + 1 < tex.length) sb.append(tex[i + 1])
                i += 2
                continue
            }
            if (c == '{') depth++
            if (c == '}') depth = (depth - 1).coerceAtLeast(0)
            val breakHere = depth == 0 && sb.isNotEmpty() && (c == '+' || c == '=' || (c == '-' && sb.last().let { it.isLetterOrDigit() || it == '}' || it == ')' }))
            if (breakHere) {
                val left = sb.toString().trim()
                if (left.isNotEmpty()) parts.add(left)
                sb.clear()
                sb.append(c)
            } else {
                sb.append(c)
            }
            i++
        }
        val tail = sb.toString().trim()
        if (tail.isNotEmpty()) parts.add(tail)
        return parts
    }

    private fun render(tex: String, sizePx: Float, color: Int, widthCap: Int, heightCap: Int): Glyph? {
        val trimmed = tex.trim()
        if (trimmed.isEmpty()) return null
        return runCatching {
            val icon = TeXFormula(trimmed).TeXIconBuilder()
                .setStyle(TeXConstants.STYLE_TEXT)
                .setSize(sizePx)
                .setFGColor(ru.noties.jlatexmath.awt.Color(color))
                .build()
            val w = icon.iconWidth.coerceAtLeast(1)
            val h = icon.iconHeight.coerceAtLeast(1)
            val depth = icon.iconDepth.coerceIn(0, h)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            val graphics = ru.noties.jlatexmath.awt.AndroidGraphics2D()
            graphics.setCanvas(canvas)
            icon.paintIcon(null, graphics, 0, 0)
            var scale = 1f
            if (h > heightCap) scale = minOf(scale, heightCap.toFloat() / h)
            if (w * scale > widthCap) scale = minOf(scale, widthCap.toFloat() / w)
            if (scale < 0.98f) {
                val nw = (w * scale).roundToInt().coerceAtLeast(1)
                val nh = (h * scale).roundToInt().coerceAtLeast(1)
                val scaled = Bitmap.createScaledBitmap(bmp, nw, nh, true)
                Glyph(scaled, ((h - depth) * scale).roundToInt().coerceIn(0, nh))
            } else {
                Glyph(bmp, h - depth)
            }
        }.onFailure {
            android.util.Log.d("MathText", "formula render failed: ${trimmed.take(80)}: $it")
        }.getOrNull()
    }
}

/** 是否值得走公式管线：定界符、反斜杠命令，或 lim_() / e^() 这种半成品。 */
fun looksLikeMath(text: String): Boolean {
    if (text.contains('$') || text.contains("\\(") || text.contains("\\[") || text.contains('\\')) return true
    if (text.contains('^') || text.contains('→') || text.contains('∫')) return true
    return text.contains("lim_") || text.contains("_(") || text.contains("_\\{")
}

@Composable
fun MathText(
    text: String,
    color: androidx.compose.ui.graphics.Color,
    fontSizeSp: Float,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
) {
    val density = LocalDensity.current
    val argb = android.graphics.Color.argb(
        (color.alpha * 255).toInt(),
        (color.red * 255).toInt(),
        (color.green * 255).toInt(),
        (color.blue * 255).toInt(),
    )
    BoxWithConstraints(modifier) {
        val widthPx = with(density) {
            (if (maxWidth.value.isFinite()) maxWidth else 360.dp).toPx()
        }.roundToInt().coerceAtLeast(1)
        val fontPx = fontSizeSp * density.fontScale * density.density
        val spanned = remember(text, fontPx, argb, widthPx) {
            runCatching { MathTextEngine.build(text, fontPx, argb, widthPx) }.getOrNull()
        }
        if (spanned == null) {
            androidx.compose.material3.Text(
                cleanMathLite(text),
                color = color,
                fontSize = androidx.compose.ui.unit.TextUnit(fontSizeSp, androidx.compose.ui.unit.TextUnitType.Sp),
                lineHeight = androidx.compose.ui.unit.TextUnit(fontSizeSp * 1.45f, androidx.compose.ui.unit.TextUnitType.Sp),
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            AndroidView(
                modifier = Modifier.fillMaxWidth(),
                factory = { ctx ->
                    TextView(ctx).apply {
                        includeFontPadding = false
                        ellipsize = TextUtils.TruncateAt.END
                        setTextColor(argb)
                    }
                },
                update = { tv ->
                    tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, fontPx)
                    tv.setLineSpacing(fontPx * 0.28f, 1f)
                    tv.setTextColor(argb)
                    tv.maxLines = maxLines
                    tv.ellipsize = TextUtils.TruncateAt.END
                    tv.text = spanned
                },
            )
        }
    }
}
