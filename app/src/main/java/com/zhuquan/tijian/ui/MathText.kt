package com.zhuquan.tijian.ui

import android.text.TextUtils
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.Markwon
import io.noties.markwon.ext.latex.JLatexMathPlugin

/** 题干里是否带 LaTeX：含 $ 定界或 \( \[ 定界就值得走公式渲染。 */
fun looksLikeMath(text: String): Boolean =
    text.contains('$') || text.contains("\\(") || text.contains("\\[")

/**
 * 把常见行内定界统一成 Markwon Latex 插件认识的 $$...$$：
 * 单 $...$ 与 \(...\)、\[...\] 都转过去；已有的 $$..$$ 不动。
 */
private fun normalizeMathDelimiters(input: String): String {
    var t = input
        .replace("\\(", "\$\$")
        .replace("\\)", "\$\$")
        .replace("\\[", "\$\$\n")
        .replace("\\]", "\n\$\$")
    t = Regex("(?<!\\$)\\$(?!\\$)([^\\$\\n]+?)(?<!\\$)\\$(?!\\$)")
        .replace(t) { "\$\$${it.groupValues[1]}\$\$" }
    return t
}

/**
 * 列表行内的轻量公式文本：含 LaTeX 的题干用 JLatexMath（Markwon Latex 插件）
 * 在原生 TextView 里渲染，比每行挂 WebView 轻得多；公式残缺时插件回退原文。
 * 不含公式的文本请直接用 Compose Text，别走这条路。
 */
@Composable
fun MathText(
    text: String,
    color: androidx.compose.ui.graphics.Color,
    fontSizeSp: Float,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
) {
    val ctx = LocalContext.current
    val fontScale = LocalDensity.current.fontScale
    val fs = (fontSizeSp * fontScale).toInt().coerceAtLeast(8)
    val argb = android.graphics.Color.argb(
        (color.alpha * 255).toInt(), (color.red * 255).toInt(), (color.green * 255).toInt(), (color.blue * 255).toInt()
    )
    val normalized = remember(text) { normalizeMathDelimiters(Normalize.text(text)) }
    val markwon = remember(ctx, fs) {
        runCatching {
            Markwon.builder(ctx)
                .usePlugin(
                    JLatexMathPlugin.create(
                        JLatexMathPlugin.builder(fs.toFloat())
                            .inlinesEnabled(true)
                            .blocksEnabled(true)
                            // 公式残缺时给透明占位，绝不让渲染抛异常带崩列表
                            .errorHandler { _, _ -> android.graphics.drawable.ColorDrawable(0x00000000) }
                            .build()
                    )
                )
                .build()
        }.getOrNull()
    }
    AndroidView(
        modifier = modifier,
        factory = { tctx ->
            TextView(tctx).apply {
                textSize = fs.toFloat()
                includeFontPadding = false
                ellipsize = TextUtils.TruncateAt.END
                setTextColor(argb)
            }
        },
        update = { tv ->
            tv.textSize = fs.toFloat()
            tv.setTextColor(argb)
            tv.maxLines = maxLines
            tv.ellipsize = TextUtils.TruncateAt.END
            val rendered = markwon?.let { mw ->
                runCatching { mw.setMarkdown(tv, normalized); true }.getOrDefault(false)
            } ?: false
            if (!rendered) tv.text = cleanMathLite(text)
        },
    )
}
