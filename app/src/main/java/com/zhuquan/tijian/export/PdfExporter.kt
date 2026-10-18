package com.zhuquan.tijian.export

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.zhuquan.tijian.data.QuestionRow
import com.zhuquan.tijian.ui.cleanMath
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 极简 A4 PDF 排版引擎：不依赖第三方库。
 * 规则：内容区 17cm 宽（约 482pt），超长图按像素行切片，题块尽量不跨页。
 */
object PdfExporter {

    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 52f
    private const val TOP = 58f
    private const val BOTTOM = 792f
    private const val CONTENT_W = PAGE_W - MARGIN * 2

    data class Options(
        val includeAnswer: Boolean = true,
        val includeAnalysis: Boolean = true,
        val includeTags: Boolean = true,
        val onePerPage: Boolean = true,
    )

    private val ink = Color.rgb(20, 20, 24)
    private val muted = Color.rgb(138, 138, 144)
    private val accent = Color.rgb(68, 87, 214)
    private val lineColor = Color.rgb(231, 231, 225)

    private fun paint(size: Float, bold: Boolean = false, color: Int = ink) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = if (bold) Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        else Typeface.SANS_SERIF
    }

    suspend fun export(context: Context, questions: List<QuestionRow>, target: File, title: String, opts: Options): Int {
        val doc = PdfDocument()
        val pager = Pager(doc)
        pager.newPage()

        // 封面
        val big = paint(26f, true)
        val sub = paint(11f, false, muted)
        pager.canvas!!.drawText("题笺", MARGIN, TOP + 10f, paint(11f, false, accent))
        pager.canvas!!.drawText(title, MARGIN, TOP + 60f, big)
        pager.canvas!!.drawText("共 ${questions.size} 题 · ${date()}", MARGIN, TOP + 86f, sub)
        pager.y = TOP + 110f
        pager.hairline()
        pager.y += 26f
        val intro = paint(10.5f, false, muted)
        wrap("由题笺导出。题目按分类整理，图片按页面宽度自动缩放，超长截图自动分页。", intro, CONTENT_W)
            .forEach { pager.canvas!!.drawText(it, MARGIN, pager.y, intro); pager.y += 17f }

        pager.newPage()

        questions.forEachIndexed { index, q ->
            val head = paint(13f, true)
            val small = paint(9f, false, muted)
            val body = paint(11f)
            val bmp0 = q.imagePath?.let { load(it) }

            // 统一走富文本位图：公式、表格、化学式与 App 内完全一致
            val richMd = buildString {
                if (bmp0 == null) {
                    append(q.stem.ifBlank { "（未识别题干）" })
                    val optsList = optionsOf(q)
                    if (optsList.isNotEmpty()) {
                        append("\n\n")
                        append(optsList.joinToString("\n"))
                    }
                }
                if (opts.includeAnswer && q.answer.isNotBlank()) {
                    append("\n\n**答案**\n\n").append(q.answer)
                }
                if (opts.includeAnalysis && q.analysis.isNotBlank()) {
                    append("\n\n**解析**\n\n").append(q.analysis)
                }
            }
            val rich = if (richMd.isBlank()) null else RichBitmap.render(context, richMd)
            val richH = rich?.let { CONTENT_W * it.height / it.width.toFloat() } ?: 0f

            // 位图渲染失败时才退回纯文本
            val stemLines = if (bmp0 == null && rich == null)
                wrap(cleanMath(q.stem.ifBlank { "（未识别题干）" }), body, CONTENT_W)
            else emptyList()
            val answerText = if (rich != null) "" else buildString {
                if (opts.includeAnswer && q.answer.isNotBlank()) append("答案：").append(cleanMath(q.answer)).append("\n")
                if (opts.includeAnalysis && q.analysis.isNotBlank()) append("解析：").append(cleanMath(q.analysis))
            }.trim()
            val answerLines = if (answerText.isBlank()) emptyList() else wrap(answerText, paint(10.5f), CONTENT_W)

            // 题块高度预判：标题 + 题干 + 图片 + 富文本 + 答案
            val bmp = bmp0
            val imgH = if (bmp != null) minOf(
                CONTENT_W * bmp.height.toFloat() / bmp.width,
                BOTTOM - TOP - 80f
            ) else 0f
            val blockH = 26f + stemLines.size * 17f + imgH + richH + answerLines.size * 16f + 18f

            if (opts.onePerPage && index > 0) pager.newPage()
            else if (pager.y + minOf(blockH, 120f) > BOTTOM) pager.newPage()

            pager.canvas!!.drawText("${index + 1}", MARGIN, pager.y + 10f, paint(11f, true, accent))
            pager.canvas!!.drawText(
                listOf(q.type.ifBlank { "题目" }, q.categoryName).filter { it.isNotBlank() }.joinToString(" · "),
                MARGIN + 22f, pager.y + 10f, head
            )
            if (opts.includeTags) {
                val tags = tagsOf(q)
                if (tags.isNotEmpty()) {
                    val t = tags.take(3).joinToString(" / ")
                    pager.canvas!!.drawText(t, MARGIN, pager.y + 25f, small)
                }
            }
            pager.y += 32f

            stemLines.forEach { line ->
                pager.ensure(17f)
                pager.canvas!!.drawText(line, MARGIN, pager.y + 11f, body)
                pager.y += 17f
            }
            pager.y += 6f

            if (bmp != null) pager.image(bmp)

            if (rich != null) {
                if (pager.y > TOP + 1f) pager.y += 8f
                pager.image(rich)
                rich.recycle()
            }

            if (answerLines.isNotEmpty()) {
                pager.y += 8f
                pager.ensure(16f)
                pager.canvas!!.drawLine(MARGIN, pager.y, MARGIN + 36f, pager.y, Paint().apply {
                    color = lineColor; strokeWidth = 1f
                })
                pager.y += 14f
                answerLines.forEach { line ->
                    pager.ensure(16f)
                    pager.canvas!!.drawText(line, MARGIN, pager.y + 10f, paint(10.5f))
                    pager.y += 16f
                }
            }
            pager.y += 20f
        }

        pager.finish()
        FileOutputStream(target).use { doc.writeTo(it) }
        val pages = pager.pageNo
        doc.close()
        return pages
    }

    fun exportText(title: String, markdown: String, target: File): Int {
        val doc = PdfDocument()
        val pager = Pager(doc)
        pager.newPage()
        pager.canvas!!.drawText("题笺 · 知识库", MARGIN, TOP, paint(10f, false, accent))
        pager.y = TOP + 26f
        pager.canvas!!.drawText(title, MARGIN, pager.y, paint(20f, true))
        pager.y += 16f
        pager.hairline()
        pager.y += 18f

        markdown.lineSequence().forEach { raw ->
            val line = raw.trimEnd()
            when {
                line.startsWith("## ") -> {
                    pager.y += 8f
                    pager.ensure(24f)
                    pager.canvas!!.drawText(line.removePrefix("## "), MARGIN, pager.y + 14f, paint(13f, true))
                    pager.y += 24f
                }
                line.startsWith("# ") -> {
                    pager.ensure(30f)
                    pager.canvas!!.drawText(line.removePrefix("# "), MARGIN, pager.y + 18f, paint(16f, true))
                    pager.y += 30f
                }
                line.startsWith("- ") || line.startsWith("* ") -> {
                    val indent = MARGIN + 10f
                    val lines = wrap("· " + line.drop(2), paint(10.5f), CONTENT_W - 10f)
                    lines.forEachIndexed { i, l ->
                        pager.ensure(16f)
                        pager.canvas!!.drawText(l, if (i == 0) indent else MARGIN + 18f, pager.y + 10f, paint(10.5f))
                        pager.y += 16f
                    }
                }
                line.startsWith("**") && line.endsWith("**") -> {
                    pager.ensure(18f)
                    pager.canvas!!.drawText(line.trim('*'), MARGIN, pager.y + 11f, paint(10f, true, muted))
                    pager.y += 18f
                }
                line.isBlank() -> pager.y += 7f
                else -> {
                    val lines = wrap(line, paint(10.5f), CONTENT_W)
                    lines.forEach { l ->
                        pager.ensure(16f)
                        pager.canvas!!.drawText(l, MARGIN, pager.y + 10f, paint(10.5f))
                        pager.y += 16f
                    }
                }
            }
        }
        pager.finish()
        FileOutputStream(target).use { doc.writeTo(it) }
        val pages = pager.pageNo
        doc.close()
        return pages
    }

    // ---------------- layout primitives ----------------

    private class Pager(val doc: PdfDocument) {
        var page: PdfDocument.Page? = null
        var canvas: Canvas? = null
        var y = TOP
        var pageNo = 0

        fun newPage() {
            page?.let { doc.finishPage(it) }
            pageNo++
            val info = PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create()
            page = doc.startPage(info)
            canvas = page!!.canvas
            y = TOP
            canvas!!.drawLine(MARGIN, 42f, PAGE_W - MARGIN, 42f, Paint().apply {
                color = lineColor; strokeWidth = 0.8f
            })
            val f = paint(8f, false, muted)
            canvas!!.drawText("题笺", MARGIN, 830f, f)
            val num = "$pageNo"
            canvas!!.drawText(num, PAGE_W - MARGIN - f.measureText(num), 830f, f)
        }

        fun ensure(h: Float) {
            if (canvas == null || y + h > BOTTOM) newPage()
        }

        fun finish() {
            page?.let { doc.finishPage(it) }
            page = null
            canvas = null
        }

        fun hairline() {
            canvas?.drawLine(MARGIN, y, PAGE_W - MARGIN, y, Paint().apply {
                color = lineColor; strokeWidth = 0.8f
            })
        }

        fun image(bmp: Bitmap) {
            val scale = CONTENT_W / bmp.width.toFloat()
            var scaledH = bmp.height * scale
            val maxH = BOTTOM - TOP - 10f
            if (scaledH <= (BOTTOM - y)) {
                val dst = RectF(MARGIN, y, MARGIN + CONTENT_W, y + scaledH)
                canvas!!.drawBitmap(bmp, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
                y += scaledH
                return
            }
            if (scaledH <= maxH) {
                newPage()
                val dst = RectF(MARGIN, y, MARGIN + CONTENT_W, y + scaledH)
                canvas!!.drawBitmap(bmp, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
                y += scaledH
                return
            }
            // 超长图：按像素行切片，每片占一页
            val pagePx = ((maxH) / scale).toInt().coerceAtLeast(200)
            val overlap = (18f / scale).toInt()
            var srcY = 0
            var first = true
            while (srcY < bmp.height) {
                if (!first) newPage()
                first = false
                val hPx = minOf(pagePx, bmp.height - srcY)
                val src = Rect(0, srcY, bmp.width, srcY + hPx)
                val dst = RectF(MARGIN, y, MARGIN + CONTENT_W, y + hPx * scale)
                canvas!!.drawBitmap(bmp, src, dst, Paint(Paint.FILTER_BITMAP_FLAG))
                y += hPx * scale
                srcY += hPx - if (srcY + hPx < bmp.height) overlap else 0
            }
        }
    }

    private fun wrap(text: String, p: Paint, width: Float): List<String> {
        val out = ArrayList<String>()
        text.split('\n').forEach { paragraph ->
            if (paragraph.isBlank()) { out.add(""); return@forEach }
            var line = StringBuilder()
            var lastBreak = -1
            for (ch in paragraph) {
                line.append(ch)
                if (p.measureText(line.toString()) > width) {
                    val breakAt = if (lastBreak > 0) lastBreak else line.length - 1
                    out.add(line.substring(0, breakAt))
                    val rest = line.substring(breakAt)
                    line = StringBuilder(rest)
                    lastBreak = -1
                } else if (ch == ' ' || ch == '，' || ch == '。' || ch == '；' || ch == '、') {
                    lastBreak = line.length
                }
            }
            if (line.isNotBlank()) out.add(line.toString())
        }
        return out
    }

    private fun optionsOf(q: QuestionRow): List<String> = try {
        val arr = org.json.JSONArray(q.options)
        (0 until arr.length()).mapNotNull { i ->
            val v = arr.optString(i).trim()
            if (v.isBlank()) null else v
        }
    } catch (e: Exception) { emptyList() }

    private fun tagsOf(q: QuestionRow): List<String> = try {
        val arr = org.json.JSONArray(q.tags)
        (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
    } catch (e: Exception) { emptyList() }

    private fun load(path: String): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2400) sample *= 2
        BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    } catch (e: Exception) { null }

    private fun date(): String = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date())
}
