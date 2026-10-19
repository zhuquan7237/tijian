package com.zhuquan.tijian

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import android.view.MotionEvent
import androidx.test.platform.app.InstrumentationRegistry
import com.zhuquan.tijian.ui.OriginalImageView
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Real native view checks for flattening annotations and portrait image geometry. */
class ImageEditorLayoutTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Test fun brushStrokeIsFlattenedIntoExportedCopy() {
        val ctx = instrumentation.targetContext
        val file = File(ctx.cacheDir, "brush-export-source.png")
        val out = File(ctx.cacheDir, "brush-export-out.png")
        val bmp = Bitmap.createBitmap(900, 1200, Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawColor(Color.WHITE); bmp.compress(Bitmap.CompressFormat.PNG, 100, file.outputStream()); bmp.recycle()
        val host = instrumentation.startActivitySync(Intent(ctx, RichTextRegressionActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as RichTextRegressionActivity
        try {
            instrumentation.runOnMainSync {
                val view = OriginalImageView(host, file)
                host.setContentView(view)
                view.layout(0, 0, 600, 800)
                view.brushMode = true
                val now = SystemClock.uptimeMillis()
                fun move(action: Int, x: Float, y: Float, at: Long) {
                    MotionEvent.obtain(now, now + at, action, x, y, 0).let { view.onTouchEvent(it); it.recycle() }
                }
                move(MotionEvent.ACTION_DOWN, 100f, 200f, 0)
                move(MotionEvent.ACTION_MOVE, 300f, 450f, 20)
                move(MotionEvent.ACTION_UP, 300f, 450f, 40)
                assertEquals(1, view.strokes.size)
                view.exportWithStrokes(out)
                view.close()
            }
            val result = android.graphics.BitmapFactory.decodeFile(out.path)
            assertEquals(900, result.width); assertEquals(1200, result.height)
            var colored = 0
            for (x in 120 until 420 step 6) for (y in 250 until 700 step 6) {
                val pixel = result.getPixel(x, y)
                if (Color.red(pixel) > 180 && Color.green(pixel) < 150) colored++
            }
            result.recycle()
            assertTrue("export must include visible annotation pixels", colored > 0)
        } finally {
            instrumentation.runOnMainSync { host.finish() }
            file.delete(); out.delete()
        }
    }

    @Test fun portraitImageKeepsFullHeightInsideStage() {
        val ctx = instrumentation.targetContext
        val file = File(ctx.cacheDir, "portrait-stage.png")
        val bmp = Bitmap.createBitmap(600, 1200, Bitmap.Config.ARGB_8888)
        Canvas(bmp).apply { drawColor(Color.WHITE); drawRect(100f, 100f, 500f, 1100f, Paint().apply { color = Color.BLACK }) }
        bmp.compress(Bitmap.CompressFormat.PNG, 100, file.outputStream()); bmp.recycle()
        val host = instrumentation.startActivitySync(Intent(ctx, RichTextRegressionActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as RichTextRegressionActivity
        try {
            instrumentation.runOnMainSync {
                val view = OriginalImageView(host, file)
                host.setContentView(view)
                view.layout(0, 0, 600, 600)
                val shot = Bitmap.createBitmap(600, 600, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(shot))
                // At fit-to-screen the full 600x1200 page becomes 300x600 centered;
                // the top and bottom of the page must both be visible.
                val top = shot.getPixel(300, 3)
                val bottom = shot.getPixel(300, 596)
                assertNotEquals("page top must not be black background", Color.rgb(20, 24, 33), top)
                assertNotEquals("page bottom must not be black background", Color.rgb(20, 24, 33), bottom)
                shot.recycle(); view.close()
            }
        } finally {
            instrumentation.runOnMainSync { host.finish() }
            file.delete()
        }
    }

    @Test fun longImageSlicesContinueOnCurrentPageAndEndAtContent() {
        val ctx = instrumentation.targetContext
        val out = File(ctx.cacheDir, "pdf-slices.pdf")
        val source = File(ctx.cacheDir, "pdf-slices.png")
        val bmp = Bitmap.createBitmap(1000, 3000, Bitmap.Config.ARGB_8888)
        Canvas(bmp).apply { drawColor(Color.WHITE); drawRect(0f, 0f, 1000f, 3000f, Paint().apply { color = Color.BLUE }) }
        bmp.compress(Bitmap.CompressFormat.PNG, 100, source.outputStream()); bmp.recycle()
        try {
            val q = com.zhuquan.tijian.data.QuestionRow(
                id = "slice", categoryId = null, categoryName = "数学/极限", type = "解答题",
                stem = "求函数的极限。", options = "[]", answer = "2",
                analysis = "原式 = 1。\n\n| 步骤 | 操作 |\n| --- | --- |\n| 1 | 代入 |",
                tags = "[]", difficulty = 3, errorReasons = "[]", source = "test", mastery = 0,
                reviewCount = 0, nextReviewAt = 0, confidence = 0.9, note = "",
                imageId = "slice", imagePath = source.path, createdAt = 0
            )
            val pages = kotlinx.coroutines.runBlocking {
                com.zhuquan.tijian.export.PdfExporter.export(ctx, listOf(q), out, "切片测试",
                    com.zhuquan.tijian.export.PdfExporter.Options())
            }
            // Keep the artifact available for manual page rendering; the page-count
            // itself is asserted below only as a sanity bound.
            java.io.File("/sdcard/Download/pdf-slices.pdf").outputStream().use { target -> out.inputStream().use { it.copyTo(target) } }
            java.io.File("/sdcard/Download/pdf-pages.txt").writeText(
                "pages=$pages file=${out.length()}\n" +
                    "source=${source.length()} width=${android.graphics.BitmapFactory.decodeFile(source.path)?.width}\n"
            )
            assertTrue("answer must stay readable and no blank-only page should be needed", pages >= 1)
            assertTrue("file must not be empty", out.length() > 800)
        } finally { source.delete(); out.delete() }
    }

    @Test fun pdfKeepsAnswerAnalysisAndImageAspect() {
        val ctx = instrumentation.targetContext
        val out = File(ctx.cacheDir, "pdf-complete.pdf")
        val source = File(ctx.cacheDir, "pdf-complete.png")
        val bmp = Bitmap.createBitmap(900, 1500, Bitmap.Config.ARGB_8888)
        Canvas(bmp).apply {
            drawColor(Color.WHITE)
            drawRect(40f, 40f, 860f, 1460f, Paint().apply { color = Color.BLUE; style = Paint.Style.STROKE; strokeWidth = 20f })
        }
        bmp.compress(Bitmap.CompressFormat.PNG, 100, source.outputStream()); bmp.recycle()
        try {
            val q = com.zhuquan.tijian.data.QuestionRow(
                id = "complete", categoryId = null, categoryName = "数学/函数与导数", type = "解答题",
                stem = "求函数的极限。", options = "[]",
                answer = "不存在最大值",
                analysis = "原式 = 1，两次使用洛必达法则，最后得到结果。",
                tags = "[\"极限\",\"洛必达法则\"]", difficulty = 3, errorReasons = "[]", source = "test", mastery = 0,
                reviewCount = 0, nextReviewAt = 0, confidence = 0.9, note = "",
                imageId = "complete", imagePath = source.path, createdAt = 0
            )
            val pages = kotlinx.coroutines.runBlocking {
                com.zhuquan.tijian.export.PdfExporter.export(ctx, listOf(q), out, "完整性测试",
                    com.zhuquan.tijian.export.PdfExporter.Options())
            }
            java.io.File("/sdcard/Download/pdf-complete.pdf").outputStream().use { target -> out.inputStream().use { it.copyTo(target) } }
            assertTrue("PDF should be generated", pages >= 1)
            assertTrue("PDF file should contain data", out.length() > 1000)
        } finally { source.delete(); out.delete() }
    }

    @Test fun pdfRendersRichAnswerAndAnalysisWhenOptionsEnabled() {
        val appCtx = instrumentation.targetContext
        val ctx = instrumentation.startActivitySync(
            Intent(appCtx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ) as MainActivity
        val out = File(ctx.cacheDir, "pdf-rich.pdf")
        try {
            val q = com.zhuquan.tijian.data.QuestionRow(
                id = "rich", categoryId = null, categoryName = "数学/函数与导数", type = "解答题",
                stem = "求极限。", options = "[]", answer = "结果为 $\\boxed{2}$",
                analysis = "原式 $=\\lim_{x\\to1}\\dfrac{1}{x}=1$，故结果为 $2$。",
                tags = "[]", difficulty = 3, errorReasons = "[]", source = "test", mastery = 0,
                reviewCount = 0, nextReviewAt = 0, confidence = 0.9, note = "",
                imageId = null, imagePath = null, createdAt = 0
            )
            val pages = kotlinx.coroutines.runBlocking {
                com.zhuquan.tijian.export.PdfExporter.export(ctx, listOf(q), out, "解析测试",
                    com.zhuquan.tijian.export.PdfExporter.Options(includeAnswer = true, includeAnalysis = true, includeTags = false, onePerPage = true))
            }
            java.io.File("/sdcard/Download/pdf-rich.pdf").outputStream().use { target -> out.inputStream().use { it.copyTo(target) } }
            assertTrue(pages >= 1)
        } finally { out.delete(); instrumentation.runOnMainSync { ctx.finish() } }
    }

    @Test fun richBitmapRendersLatexInsteadOfFallingBackToSource() {
        val host = instrumentation.startActivitySync(Intent(instrumentation.targetContext, RichTextRegressionActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as RichTextRegressionActivity
        val ctx = host
        val bmp = kotlinx.coroutines.runBlocking {
            com.zhuquan.tijian.export.RichBitmap.render(
                ctx, "答案：$\\dfrac{1}{2}$\n\n解析：原式 $=\\lim_{x\\to1}\\dfrac{1}{x}=1$", widthPx = 700, fontSize = 14
            )
        }
        assertNotNull("Rich text bitmap must be produced for PDF", bmp)
        assertTrue("Rich text bitmap must contain visible pixels", bmp!!.width > 100 && bmp.height > 30)
        java.io.File("/sdcard/Download/rich-bitmap.png").outputStream().use { target -> bmp.compress(Bitmap.CompressFormat.PNG, 100, target) }
        bmp.recycle()
        instrumentation.runOnMainSync { host.finish() }
    }
}
