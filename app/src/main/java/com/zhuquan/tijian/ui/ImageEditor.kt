package com.zhuquan.tijian.ui

import android.content.Context
import android.graphics.*
import android.net.Uri
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.zhuquan.tijian.data.*
import kotlinx.coroutines.*
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.*

/** Preview is bounded; visible zoom regions are decoded directly from the original file. */
internal class OriginalImageView(context: Context, val file: File) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val executor = Executors.newSingleThreadExecutor()
    private val decoder = BitmapRegionDecoder.newInstance(file.path, false) ?: error("图片无法读取")
    private val orientation = orientationMatrix(file.path)
    private val oriented = RectF(0f, 0f, decoder.width.toFloat(), decoder.height.toFloat()).also { orientation.mapRect(it) }
    private val imageWidth = oriented.width()
    private val imageHeight = oriented.height()
    private val matrix = Matrix()
    private val preview = Importer.decodeSampled(file, 1600) ?: error("图片无法读取")
    private var tile: Bitmap? = null
    private var tileRect: Rect? = null
    @Volatile private var generation = 0
    @Volatile private var closed = false
    var cropMode = false
        set(value) { field = value; zoom = 1f; dx = 0f; dy = 0f; invalidate(); updateGestureExclusion(); requestTile() }
    val crop = RectF(0f, 0f, 1f, 1f)
    private var zoom = 1f
    private var dx = 0f
    private var dy = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var edge = 0
    private var gestureMoved = false
    private val scale = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            if (cropMode) return false
            val old = zoom
            zoom = (zoom * detector.scaleFactor).coerceIn(1f, 16f)
            val factor = zoom / old
            dx = (dx - (detector.focusX - width / 2f)) * factor + detector.focusX - width / 2f
            dy = (dy - (detector.focusY - height / 2f)) * factor + detector.focusY - height / 2f
            invalidate(); return true
        }
    })
    private fun imageRect(): RectF {
        val fit = min(width / imageWidth, height / imageHeight) * zoom
        return RectF((width - imageWidth * fit) / 2f + dx, (height - imageHeight * fit) / 2f + dy,
            (width + imageWidth * fit) / 2f + dx, (height + imageHeight * fit) / 2f + dy)
    }
    private fun updateMatrix(): RectF {
        val r = imageRect()
        matrix.set(orientation); matrix.postTranslate(-oriented.left, -oriented.top)
        matrix.postScale(r.width() / imageWidth, r.height() / imageHeight); matrix.postTranslate(r.left, r.top)
        return r
    }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) { updateGestureExclusion(); requestTile() }
    private fun updateGestureExclusion() {
        if (android.os.Build.VERSION.SDK_INT < 29 || width <= 0 || height <= 0) return
        if (!cropMode) { systemGestureExclusionRects = emptyList(); return }
        // Reserve only corner handles; keep the rest of both screen edges available for Back.
        val r = imageRect()
        val reach = (36 * resources.displayMetrics.density).toInt()
        val corners = listOf(crop.top, crop.bottom).map { (r.top + it * r.height()).toInt() }
        systemGestureExclusionRects = corners.flatMap { y ->
            listOf(Rect(0, (y - reach).coerceAtLeast(0), reach, (y + reach).coerceAtMost(height)),
                Rect((width - reach).coerceAtLeast(0), (y - reach).coerceAtLeast(0), width, (y + reach).coerceAtMost(height)))
        }
    }
    override fun onDraw(canvas: Canvas) {
        if (closed) return
        canvas.clipRect(0, 0, width, height)
        canvas.drawColor(android.graphics.Color.rgb(20, 24, 33))
        val r = updateMatrix()
        canvas.save(); canvas.concat(matrix)
        canvas.drawBitmap(preview, null, RectF(0f, 0f, decoder.width.toFloat(), decoder.height.toFloat()), paint)
        tile?.let { bitmap -> tileRect?.let { canvas.drawBitmap(bitmap, null, RectF(it), paint) } }
        canvas.restore()
        if (cropMode) {
            val c = RectF(r.left + crop.left * r.width(), r.top + crop.top * r.height(), r.left + crop.right * r.width(), r.top + crop.bottom * r.height())
            paint.color = 0x99000000.toInt(); paint.style = Paint.Style.FILL
            canvas.drawRect(r.left, r.top, r.right, c.top, paint); canvas.drawRect(r.left, c.bottom, r.right, r.bottom, paint)
            canvas.drawRect(r.left, c.top, c.left, c.bottom, paint); canvas.drawRect(c.right, c.top, r.right, c.bottom, paint)
            paint.color = android.graphics.Color.WHITE; paint.style = Paint.Style.STROKE; paint.strokeWidth = 3 * resources.displayMetrics.density
            canvas.drawRect(c, paint)
            paint.style = Paint.Style.FILL
            for (x in listOf(c.left, c.right)) for (y in listOf(c.top, c.bottom)) canvas.drawCircle(x, y, 7 * resources.displayMetrics.density, paint)
        }
    }
    private fun requestTile() {
        if (width <= 0 || height <= 0 || closed) return
        updateMatrix()
        val inverse = Matrix(); if (!matrix.invert(inverse)) return
        val visible = RectF(0f, 0f, width.toFloat(), height.toFloat()); inverse.mapRect(visible)
        if (!visible.intersect(0f, 0f, decoder.width.toFloat(), decoder.height.toFloat())) return
        val rect = Rect(floor(visible.left).toInt(), floor(visible.top).toInt(), ceil(visible.right).toInt(), ceil(visible.bottom).toInt())
        val r = imageRect()
        var sample = 1
        while (sample * 2 <= imageWidth / r.width()) sample *= 2
        val token = ++generation
        executor.execute {
            if (token != generation || closed) return@execute
            val bitmap = runCatching { decoder.decodeRegion(rect, BitmapFactory.Options().apply { inSampleSize = sample }) }.getOrNull()
            post {
                if (token == generation && !closed) { tile?.recycle(); tile = bitmap; tileRect = rect; invalidate() }
                else bitmap?.recycle()
            }
        }
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        scale.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x; lastY = event.y; gestureMoved = false; edge = 0
                if (cropMode) {
                    val r = imageRect(); val x = (event.x - r.left) / r.width(); val y = (event.y - r.top) / r.height()
                    val toleranceX = 32 * resources.displayMetrics.density / r.width()
                    val toleranceY = 32 * resources.displayMetrics.density / r.height()
                    if (abs(x - crop.left) < toleranceX) edge = edge or 1
                    if (abs(x - crop.right) < toleranceX) edge = edge or 2
                    if (abs(y - crop.top) < toleranceY) edge = edge or 4
                    if (abs(y - crop.bottom) < toleranceY) edge = edge or 8
                    if (edge == 0 && crop.contains(x, y)) edge = 16
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val px = event.x - lastX; val py = event.y - lastY
                if (abs(px) + abs(py) > 2) gestureMoved = true
                if (cropMode) {
                    val r = imageRect(); val x = px / r.width(); val y = py / r.height()
                    if (edge == 16) crop.offset(x.coerceIn(-crop.left, 1f - crop.right), y.coerceIn(-crop.top, 1f - crop.bottom))
                    else {
                        if (edge and 1 != 0) crop.left = (crop.left + x).coerceIn(0f, crop.right - .02f)
                        if (edge and 2 != 0) crop.right = (crop.right + x).coerceIn(crop.left + .02f, 1f)
                        if (edge and 4 != 0) crop.top = (crop.top + y).coerceIn(0f, crop.bottom - .02f)
                        if (edge and 8 != 0) crop.bottom = (crop.bottom + y).coerceIn(crop.top + .02f, 1f)
                    }
                } else if (!scale.isInProgress && zoom > 1f && event.pointerCount == 1) { dx += px; dy += py }
                lastX = event.x; lastY = event.y; invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (zoom <= 1f) { dx = 0f; dy = 0f }
                updateGestureExclusion(); requestTile(); invalidate()
                if (!gestureMoved && event.actionMasked == MotionEvent.ACTION_UP) performClick()
            }
        }
        return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
    fun resetCrop() { crop.set(0f, 0f, 1f, 1f); updateGestureExclusion(); invalidate() }
    fun close() {
        if (closed) return
        closed = true; generation++
        executor.execute { decoder.recycle() }; executor.shutdown()
        tile?.recycle(); tile = null; preview.recycle()
    }
}

@Composable
fun ImageEditorDialog(uri: Uri, onDismiss: () -> Unit, onSaved: suspend (Uri) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var file by remember(uri) { mutableStateOf<File?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var cropping by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var view by remember { mutableStateOf<OriginalImageView?>(null) }
    LaunchedEffect(uri) {
        runCatching { withContext(Dispatchers.IO) {
            if (uri.scheme == "file") File(requireNotNull(uri.path)) else File.createTempFile("original_", ".image", context.cacheDir).also { dest ->
                context.contentResolver.openInputStream(uri)?.use { input -> dest.outputStream().use { input.copyTo(it) } } ?: error("无法读取原图")
            }
        } }.onSuccess { file = it }.onFailure { error = it.message }
    }
    DisposableEffect(uri) { onDispose { if (uri.scheme != "file") file?.delete() } }
    Dialog(onDismissRequest = { if (!saving) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            // The full-screen dialog must share the physical window bounds;
            // fitting its frame to status bars and padding again offsets its bottom.
            if (android.os.Build.VERSION.SDK_INT >= 30) dialogWindow?.let { window ->
                window.attributes = window.attributes.apply { setFitInsetsTypes(0) }
            }
            dialogWindow?.setLayout(android.view.WindowManager.LayoutParams.MATCH_PARENT, android.view.WindowManager.LayoutParams.MATCH_PARENT)
            dialogWindow?.setGravity(android.view.Gravity.TOP or android.view.Gravity.START)
        }
        Column(Modifier.fillMaxSize().background(Bg).padding(WindowInsets.safeDrawing.asPaddingValues())) {
            TjTopBar("原图查看与编辑", "双指缩放 · 拖动查看；裁剪另存无损副本", onBack = { if (!saving) onDismiss() })
            Box(Modifier.weight(1f).fillMaxWidth()) {
                file?.let { imageFile -> AndroidView(factory = { OriginalImageView(it, imageFile).also { v -> view = v } },
                    modifier = Modifier.fillMaxSize(), update = { if (it.cropMode != cropping) it.cropMode = cropping }, onRelease = { it.close() }) }
                if (file == null && error == null) CircularProgressIndicator()
            }
            error?.let { Text(it, color = Red, modifier = Modifier.padding(16.dp)) }
            Text(if (cropping) "拖动四角或边框自由裁剪；拖动框内移动选区" else "原图完整显示，放大时按可见区域读取原始像素", color = Ink2, modifier = Modifier.padding(16.dp))
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(modifier = Modifier.weight(1f), enabled = !saving, onClick = { cropping = !cropping }) { Text(if (cropping) "退出裁剪" else "自定义裁剪") }
                if (cropping) TextButton(enabled = !saving, onClick = { view?.resetCrop() }) { Text("重置") }
                }
                if (cropping) {
                    Button(modifier = Modifier.fillMaxWidth(), enabled = !saving && file != null, onClick = {
                        val source = file ?: return@Button; val rect = RectF(view?.crop ?: return@Button)
                        saving = true; error = null
                        scope.launch {
                            var output: File? = null
                            try {
                                output = withContext(Dispatchers.IO) { cropOriginal(source, rect, File.createTempFile("crop_", ".png", context.cacheDir)) }
                                onSaved(Uri.fromFile(output))
                            } catch (cancelled: CancellationException) { throw cancelled }
                            catch (failure: Exception) { error = failure.message ?: "保存失败，请重试" }
                            finally { output?.delete(); saving = false }
                        }
                    }) { Text(if (saving) "保存中" else "保存副本") }
                }
            }
        }
    }
}
