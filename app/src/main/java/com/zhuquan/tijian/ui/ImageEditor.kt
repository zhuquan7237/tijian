package com.zhuquan.tijian.ui

import android.content.Context
import android.graphics.*
import android.net.Uri
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    /** Strokes are normalized to the displayed image, so zoom and crop share one coordinate space. */
    val strokes = ArrayList<Stroke>()
    var brushColor = android.graphics.Color.rgb(231, 76, 60)
    var brushWidth = .006f
    private var activeStroke: Stroke? = null
    var brushMode = false
        set(value) { field = value; gestureMoved = false; invalidate() }

    data class Stroke(val points: MutableList<Offset>, val color: Int, val width: Float)
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
        canvas.drawColor(android.graphics.Color.rgb(245, 246, 250))
        val r = updateMatrix()
        canvas.save(); canvas.concat(matrix)
        canvas.drawBitmap(preview, null, RectF(0f, 0f, decoder.width.toFloat(), decoder.height.toFloat()), paint)
        tile?.let { bitmap -> tileRect?.let { canvas.drawBitmap(bitmap, null, RectF(it), paint) } }
        canvas.restore()
        strokes.forEach { stroke ->
            val path = Path()
            stroke.points.forEachIndexed { index, point ->
                val x = r.left + point.x * r.width(); val y = r.top + point.y * r.height()
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            paint.color = stroke.color
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = (stroke.width * r.width()).coerceAtLeast(2f)
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeJoin = Paint.Join.ROUND
            canvas.drawPath(path, paint)
        }
        paint.style = Paint.Style.FILL
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
        if (brushMode && event.pointerCount == 1) {
            val p = imageRect()
            val nx = ((event.x - p.left) / p.width()).coerceIn(0f, 1f)
            val ny = ((event.y - p.top) / p.height()).coerceIn(0f, 1f)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    gestureMoved = false
                    activeStroke = Stroke(mutableListOf(Offset(nx, ny)), brushColor, brushWidth).also { strokes.add(it) }
                    invalidate(); return true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (activeStroke == null) return true
                    activeStroke!!.points.add(Offset(nx, ny))
                    invalidate(); return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    activeStroke = null; invalidate(); return true
                }
            }
            return true
        }
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
    fun undoStroke() { if (strokes.isNotEmpty()) { strokes.removeAt(strokes.size - 1); invalidate() } }
    fun clearStrokes() { strokes.clear(); invalidate() }

    /** Flatten the current view (oriented source + annotations) into a clean PNG at up to 2200px. */
    fun exportWithStrokes(output: File): File {
        val maxDim = 2200f
        val scale = min(1f, maxDim / max(imageWidth, imageHeight))
        val w = (imageWidth * scale).toInt().coerceAtLeast(1)
        val h = (imageHeight * scale).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.WHITE)
        val image = RectF(0f, 0f, w.toFloat(), h.toFloat())
        val m = Matrix()
        m.set(orientation); m.postTranslate(-oriented.left, -oriented.top)
        m.postScale(scale, scale)
        val base = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.save(); canvas.concat(m)
        canvas.drawBitmap(preview, null, RectF(0f, 0f, decoder.width.toFloat(), decoder.height.toFloat()), base)
        canvas.restore()
        strokes.forEach { stroke ->
            val path = Path()
            stroke.points.forEachIndexed { index, point ->
                val x = point.x * w; val y = point.y * h
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            base.color = stroke.color
            base.style = Paint.Style.STROKE
            base.strokeWidth = (stroke.width * w).coerceAtLeast(2f)
            base.strokeCap = Paint.Cap.ROUND; base.strokeJoin = Paint.Join.ROUND
            canvas.drawPath(path, base)
        }
        try { output.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
        finally { bitmap.recycle() }
        return output
    }
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
    var brushing by remember { mutableStateOf(false) }
    var brushColor by remember { mutableStateOf<Int>(0xFFE74C3C.toInt()) }
    var brushWidth by remember { mutableStateOf(0.006f) }
    var saving by remember { mutableStateOf(false) }
    var view by remember { mutableStateOf<OriginalImageView?>(null) }
    var editRevision by remember { mutableIntStateOf(0) }
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
            TjTopBar("原图查看与编辑", "双指缩放 · 拖动查看 · 裁剪或标注后另存副本", onBack = { if (!saving) onDismiss() })
            // Image keeps a bounded share of the screen. The toolbox below is
            // always fully reachable and can scroll independently on short phones.
            Box(
                Modifier.fillMaxWidth().heightIn(min = 180.dp).weight(0.68f)
                    .padding(horizontal = 22.dp).padding(bottom = 10.dp)
                    .clip(RoundedCornerShape(14.dp)).background(ComposeColor(0xFFF5F6FA))
            ) {
                file?.let { imageFile ->
                    AndroidView(factory = { OriginalImageView(it, imageFile).also { v -> view = v } },
                        modifier = Modifier.fillMaxSize(), update = { v ->
                            if (v.cropMode != cropping) v.cropMode = cropping
                            if (v.brushMode != brushing) v.brushMode = brushing
                            v.brushColor = brushColor; v.brushWidth = brushWidth
                        }, onRelease = { it.close() })
                }
                if (file == null && error == null) CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
            Column(
                Modifier.fillMaxWidth().weight(0.32f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    when {
                        cropping -> "拖动四角或边框自由裁剪；拖动框内移动选区"
                        brushing -> "在图上书写标注；可撤销或清空"
                        else -> "原图完整显示，放大时按可见区域读取原始像素"
                    },
                    fontSize = 11.sp, color = Ink2
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    ToolChip("裁剪", cropping, enabled = !saving && !brushing) { cropping = !cropping }
                    ToolChip("画笔", brushing, enabled = !saving && !cropping) { brushing = !brushing }
                    ToolChip("撤销", false, enabled = !saving && brushing) { view?.undoStroke() }
                    ToolChip("清空", false, enabled = !saving && brushing && (view?.strokes?.isNotEmpty() == true)) { view?.clearStrokes() }
                }
                if (brushing) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf(0xFFE74C3C, 0xFF2D7FF9, 0xFF16A34A, 0xFF111827).map { it.toInt() }.forEach { c ->
                            Box(
                                Modifier.size(25.dp).clip(CircleShape).background(ComposeColor(c))
                                    .border(if (brushColor == c) 2.5.dp else 1.dp, if (brushColor == c) Accent else Line, CircleShape)
                                    .clickableNoRipple { brushColor = c; view?.brushColor = c }
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        listOf(0.003f, 0.006f, 0.011f).zip(listOf("细", "中", "粗")).forEach { (w, label) ->
                            Box(
                                Modifier.height(30.dp).clip(RoundedCornerShape(9.dp))
                                    .background(if (brushWidth == w) AccentSoft else SurfaceAlt)
                                    .clickableNoRipple { brushWidth = w; view?.brushWidth = w }
                                    .padding(horizontal = 11.dp), contentAlignment = Alignment.Center
                            ) { Text(label, fontSize = 11.sp, color = if (brushWidth == w) Accent else Ink2) }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        enabled = !saving && !brushing, onClick = { view?.resetCrop() }, modifier = Modifier.height(46.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Line)
                    ) { Text("重置", color = Ink2) }
                    Button(
                        enabled = !saving && file != null, onClick = {
                            val source = file ?: return@Button
                            saving = true; error = null
                            val annotated = view?.strokes?.isNotEmpty() == true
                            if (cropping || annotated) scope.launch {
                                var output: File? = null
                                try {
                                    output = withContext(Dispatchers.IO) {
                                        val v = view
                                        val raw = if (cropping && v != null) {
                                            cropOriginal(source, RectF(v.crop), File.createTempFile("crop_", ".png", context.cacheDir))
                                        } else source
                                        if (annotated && v != null) v.exportWithStrokes(raw) else raw
                                    }
                                    onSaved(Uri.fromFile(output))
                                } catch (cancelled: CancellationException) { throw cancelled }
                                catch (failure: Exception) { error = failure.message ?: "保存失败，请重试" }
                                finally { output?.delete(); saving = false; editRevision++ }
                            } else scope.launch { saving = true; try { onSaved(Uri.fromFile(source)) } finally { saving = false } }
                        }, modifier = Modifier.weight(1f).height(46.dp)
                    ) { Text(if (saving) "保存中" else "保存副本", fontWeight = FontWeight.SemiBold) }
                }
                error?.let { Text(it, color = Red, fontSize = 11.5.sp) }
            }
        }
    }
}

@Composable
private fun ToolChip(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.height(38.dp).clip(RoundedCornerShape(10.dp))
            .background(if (selected) Accent else SurfaceAlt)
            .border(1.dp, if (selected) Accent else Line, RoundedCornerShape(10.dp))
            .clickableNoRipple(enabled, onClick).padding(horizontal = 13.dp),
        contentAlignment = Alignment.Center
    ) { Text(label, fontSize = 12.sp, color = if (!enabled) Muted.copy(alpha = .45f) else if (selected) androidx.compose.ui.graphics.Color.White else Ink, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal) }
}
