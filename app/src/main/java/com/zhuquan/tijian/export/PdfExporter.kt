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
import com.zhuquan.tijian.data.orientBitmap
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
        // 文档标题只占页面顶部的一小块，第一题紧随其后；不再生成空白封面页。
        val sub = paint(10f, false, muted)
        pager.canvas!!.drawText("题笺", MARGIN, TOP + 2f, paint(9.5f, false, accent))
        pager.canvas!!.drawText(title, MARGIN, TOP + 32f, paint(22f, true))
        pager.canvas!!.drawText("共 ${questions.size} 题 · ${date()}", MARGIN, TOP + 52f, sub)
        // The title belongs to the first question page, not a cover page.
        pager.y = TOP + 66f
        pager.hairline()
        pager.y += 18f

        questions.forEachIndexed { index, q ->
            val head = paint(13f, true)
            val small = paint(9f, false, muted)
            val body = paint(11f)
            val bmp0 = q.imagePath?.let { load(it) }

            // 统一走富文本位图：公式、表格、化学式与 App 内完全一致
            // The sheet contains one block. Split it into the Markdown block and
            // the answer block so rich text can continue directly under the image
            // instead of being forced onto a new page by one oversized bitmap.
            val richStemMd = buildString {
                append(q.stem.ifBlank { "（未识别题干）" })
                val optsList = optionsOf(q)
                if (optsList.isNotEmpty()) { append("\n\n"); append(optsList.joinToString("\n")) }
            }
            val richAnswerMd = buildString {
                if (opts.includeAnswer && q.answer.isNotBlank()) append("**答案**\n\n").append(q.answer)
                if (opts.includeAnalysis && q.analysis.isNotBlank()) {
                    if (isNotEmpty()) append("\n\n")
                    append("**解析**\n\n").append(q.analysis)
                }
            }
            val richStem = if (richStemMd.isBlank()) null else RichBitmap.render(context, richStemMd)
            val richAnswer = if (richAnswerMd.isBlank()) null else RichBitmap.render(context, richAnswerMd)
            val richStemH = richStem?.let { CONTENT_W * it.height / it.width.toFloat() } ?: 0f
            val richAnswerH = richAnswer?.let { CONTENT_W * it.height / it.width.toFloat() } ?: 0f

            // 位图渲染失败时才退回纯文本
            val stemLines = if (richStem == null)
                wrap(cleanMath(q.stem.ifBlank { "（未识别题干）" }), body, CONTENT_W)
            else emptyList()
            val answerText = if (richAnswer != null) "" else buildString {
                if (opts.includeAnswer && q.answer.isNotBlank()) append("答案：").append(q.answer).append("\n")
                if (opts.includeAnalysis && q.analysis.isNotBlank()) append("解析：").append(q.analysis)
            }.trim()
            val answerLines = if (answerText.isBlank()) emptyList() else wrap(answerText, paint(10.5f), CONTENT_W)

            // 题块高度预判：标题 + 题干 + 图片 + 富文本 + 答案
            val bmp = bmp0
            // Title, tags and question number consume real vertical space even
            // though they were drawn before this measurement.
            val headerH = 32f + if (opts.includeTags && tagsOf(q).isNotEmpty()) 10f else 0f
            val imgH = if (bmp != null) CONTENT_W * bmp.height.toFloat() / bmp.width else 0f
            // Pager.y already includes the question-number/header row (32f) when
            // the fit is calculated. Count drawing offsets exactly once here.
            val blockH = headerH + stemLines.size * 17f + 6f + imgH + 4f + richStemH + 4f + richAnswerH + 18f

            // 一题一页时每题整页起排；紧凑版式只在题块放不下时才翻页
            if (index > 0) {
                if (opts.onePerPage) pager.newPage() else pager.ensure(headerH + 8f)
            }

            // Compute after any page switch: the available height is the space left
            // below this page's actual starting cursor, not the whole page height.
            val available = BOTTOM - pager.y - 6f
            val fit = 1f

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
                pager.ensure(17f * fit)
                pager.canvas!!.drawText(line, MARGIN, pager.y + 11f * fit, Paint(body).apply { textSize = body.textSize * fit })
                pager.y += 17f * fit
            }
            pager.y += 6f * fit

            // Text is painted before the scan so an oversized image can never
            // push the answer/analysis outside the page. The scan receives only
            // the remaining height and shrinks uniformly.
            if (richStem != null) {
                // Text must stay readable at natural scale; if the whole block does
                // not fit, move it to the next page instead of clipping it.
                pager.ensure(richStemH + 4f)
                pager.y += 4f
                pager.image(richStem, 1f, allowSlice = false)
                richStem.recycle()
            }
            if (bmp != null) {
                val reservedText = richAnswerH + answerLines.size * 16f + 42f
                val imageRoom = (BOTTOM - pager.y - reservedText).coerceAtLeast(36f)
                // Keep the scan's aspect ratio. Wide/tall source images may be far
                // taller than the page; fit by the limiting dimension here.
                pager.image(bmp, 1f, allowSlice = false, maxHeight = imageRoom)
                bmp.recycle()
            }
            if (richAnswer != null) {
                pager.ensure(richAnswerH + 6f)
                pager.y += 4f
                pager.image(richAnswer, 1f, allowSlice = false)
                richAnswer.recycle()
            }

            if (answerLines.isNotEmpty()) {
                pager.y += 8f
                pager.ensure(16f * fit)
                pager.canvas!!.drawLine(MARGIN, pager.y, MARGIN + 36f, pager.y, Paint().apply {
                    color = lineColor; strokeWidth = 1f
                })
                pager.y += 14f * fit
                answerLines.forEach { line ->
                    pager.ensure(16f * fit)
                    pager.canvas!!.drawText(line, MARGIN, pager.y + 10f * fit, paint(10.5f * fit))
                    pager.y += 16f * fit
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

    /**
     * 知识库 PDF：整篇 Markdown 走与 App 内相同的富文本管线（KaTeX + mhchem + 表格），
     * 不再按行画纯文本——之前 LaTeX 会原样以源码形式出现在 PDF 里。
     * 渲染成位图后按页切片，单页放不下就续到下一页。
     */
    suspend fun exportText(context: Context, title: String, markdown: String, target: File): Int {
        val doc = PdfDocument()
        val pager = Pager(doc)
        pager.newPage()
        pager.canvas!!.drawText("题笺 · 知识库", MARGIN, TOP, paint(10f, false, accent))
        pager.y = TOP + 26f
        pager.canvas!!.drawText(title, MARGIN, pager.y, paint(20f, true))
        pager.y += 16f
        pager.hairline()
        pager.y += 18f

        val blocks = splitSections(markdown)
        blocks.forEach { block ->
            val bitmap = RichBitmap.render(context, block, widthPx = 1400, fontSize = 13)
            if (bitmap != null) {
                pager.ensure(24f)
                pager.y += 4f
                pager.image(bitmap, 1f, allowSlice = true)
                bitmap.recycle()
            } else {
                // 位图渲染失败时退回纯文本，至少内容不丢。
                drawPlainMarkdown(pager, block)
            }
            pager.y += 10f
        }
        pager.finish()
        FileOutputStream(target).use { doc.writeTo(it) }
        val pages = pager.pageNo
        doc.close()
        return pages
    }

    /** 按二级标题切块：单块位图不会超过 RichBitmap 的高度上限，长文档也能分页。 */
    private fun splitSections(markdown: String): List<String> {
        val out = ArrayList<String>()
        val current = StringBuilder()
        markdown.lineSequence().forEach { line ->
            if (line.startsWith("## ") && current.isNotBlank()) {
                out.add(current.toString().trim())
                current.setLength(0)
            }
            current.append(line).append('\n')
        }
        if (current.isNotBlank()) out.add(current.toString().trim())
        return out.ifEmpty { listOf(markdown) }
    }

    /** 纯文本兜底：仅在富文本位图渲染失败时使用。 */
    private fun drawPlainMarkdown(pager: Pager, markdown: String) {
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

        fun image(bmp: Bitmap, fit: Float = 1f, allowSlice: Boolean = true, maxHeight: Float = Float.MAX_VALUE) {
            val scale = CONTENT_W / bmp.width.toFloat() * fit
            if (!allowSlice) {
                var fittedScale = scale
                // Constrain both dimensions while preserving aspect ratio.
                if (bmp.height * fittedScale > maxHeight) fittedScale = maxHeight / bmp.height
                if (bmp.width * fittedScale > CONTENT_W) fittedScale = CONTENT_W / bmp.width
                val fittedHeight = bmp.height * fittedScale
                val fittedWidth = bmp.width * fittedScale
                val dst = RectF(MARGIN, y, MARGIN + fittedWidth, y + fittedHeight)
                canvas!!.drawBitmap(bmp, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
                y += fittedHeight
                return
            }
            val overlap = (12f / scale).toInt().coerceAtLeast(0)
            // Draw as much as fits on the current page, then continue on the next.
            // Starting a fresh page whenever an image merely exceeds the remaining
            // space produced title/header-only pages with a large white area.
            var srcY = 0
            while (srcY < bmp.height) {
                val remaining = BOTTOM - y
                if (remaining < 40f) { newPage(); continue }
                val hPx = minOf(((remaining + 0.5f) / scale).toInt().coerceAtLeast(1), bmp.height - srcY)
                val src = Rect(0, srcY, bmp.width, srcY + hPx)
                val dst = RectF(MARGIN, y, MARGIN + CONTENT_W, y + hPx * scale)
                canvas!!.drawBitmap(bmp, src, dst, Paint(Paint.FILTER_BITMAP_FLAG))
                y += hPx * scale
                srcY += hPx - if (srcY + hPx < bmp.height) overlap.coerceAtMost(hPx - 1) else 0
                if (srcY < bmp.height) newPage()
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
        val raw = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        // 存的是带 EXIF 的原图：不转正的话竖拍照片在 PDF 里是躺倒的
        val oriented = orientBitmap(raw, path)
        if (oriented !== raw) raw.recycle()
        oriented
    } catch (e: Exception) { null }

    private fun date(): String = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date())
}
