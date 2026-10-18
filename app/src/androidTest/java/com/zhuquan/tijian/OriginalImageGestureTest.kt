package com.zhuquan.tijian

import android.content.Intent
import android.graphics.*
import android.os.SystemClock
import android.view.MotionEvent
import androidx.test.platform.app.InstrumentationRegistry
import com.zhuquan.tijian.ui.OriginalImageView
import org.junit.Test
import org.junit.Assert.*
import java.io.File

class OriginalImageGestureTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun withImage(block: (OriginalImageView) -> Unit) {
        val ctx = instrumentation.targetContext
        val file = File(ctx.cacheDir, "gesture-regression.png")
        val bitmap = Bitmap.createBitmap(1000, 1000, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.BLUE)
            drawRect(400f, 400f, 600f, 600f, Paint().apply { color = Color.RED })
        }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        val host = instrumentation.startActivitySync(Intent(ctx, RichTextRegressionActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as RichTextRegressionActivity
        try { instrumentation.runOnMainSync {
            val view = OriginalImageView(host, file)
            host.setContentView(view)
            view.layout(0, 0, 800, 1000)
            try { block(view) } finally { view.close() }
        } } finally { instrumentation.runOnMainSync { host.finish() }; file.delete() }
    }
    @Test fun freeformCornerDragAndReset() = withImage { view ->
        view.cropMode = true
        val now = SystemClock.uptimeMillis()
        for ((i, p) in listOf(0f to 100f, 160f to 260f, 160f to 260f).withIndex()) {
            val action = listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP)[i]
            MotionEvent.obtain(now, now + i * 20L, action, p.first, p.second, 0).let { view.onTouchEvent(it); it.recycle() }
        }
        assertEquals(.2f, view.crop.left, .001f); assertEquals(.2f, view.crop.top, .001f)
        assertEquals(1f, view.crop.right, .001f); assertEquals(1f, view.crop.bottom, .001f)
        view.resetCrop(); assertEquals(RectF(0f, 0f, 1f, 1f), view.crop)
    }
    @Test fun pinchZoomChangesActualRenderedPixels() = withImage { view ->
        fun pixel(): Int {
            val image = Bitmap.createBitmap(800, 1000, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(image)); val color = image.getPixel(280, 500); image.recycle(); return color
        }
        assertEquals(Color.BLUE, pixel())
        val now = SystemClock.uptimeMillis()
        fun touch(action: Int, xs: List<Float>, offset: Long) {
            val props = xs.mapIndexed { i, _ -> MotionEvent.PointerProperties().apply { id = i; toolType = MotionEvent.TOOL_TYPE_FINGER } }.toTypedArray()
            val coords = xs.map { x -> MotionEvent.PointerCoords().apply { this.x = x; y = 500f; pressure = 1f; size = 1f } }.toTypedArray()
            MotionEvent.obtain(now, now + offset, action, xs.size, props, coords, 0, 0, 1f, 1f, 0, 0, 0, 0)
                .let { view.onTouchEvent(it); it.recycle() }
        }
        touch(MotionEvent.ACTION_DOWN, listOf(300f), 0)
        touch(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), listOf(300f, 500f), 20)
        for (i in 1..10) touch(MotionEvent.ACTION_MOVE, listOf(300f - 25f * i, 500f + 25f * i), 20L + i * 20)
        touch(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), listOf(50f, 750f), 250)
        touch(MotionEvent.ACTION_UP, listOf(50f), 270)
        assertEquals("Pinch must enlarge actual image content", Color.RED, pixel())
    }
}

