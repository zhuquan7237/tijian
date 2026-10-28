package com.zhuquan.tijian.ui

import android.content.Context
import android.graphics.*
import android.net.Uri
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.RotateRight
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Transform
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
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
            if (cropMode || warpMode) return false
            val old = zoom
            zoom = (zoom * detector.scaleFactor).coerceIn(1f, 16f)
            val factor = zoom / old
            dx = (dx - (detector.focusX - width / 2f)) * factor + detector.focusX - width / 2f
            dy = (dy - (detector.focusY - height / 2f)) * factor + detector.focusY - height / 2f
            invalidate(); return true
        }
    })

    /** 透视校正模式：显示四个可拖角点，拖到纸面四角后由外层应用变换。 */
    var warpMode = false
        set(value) { field = value; if (!value) dragCorner = -1; invalidate() }

    /** 归一化四角 [x,y]×4，顺序 左上、右上、右下、左下（相对当前显示图）。 */
    var warpCorners = floatArrayOf(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f)
        private set

    fun resetWarpCorners() {
        warpCorners = floatArrayOf(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f)
        invalidate()
    }

    fun warpIsDefault(): Boolean = warpCorners.contentEquals(floatArrayOf(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f))

    private var dragCorner = -1

    private fun cornerScreenPos(r: RectF, k: Int): Pair<Float, Float> {
        val nx = warpCorners[k * 2]; val ny = warpCorners[k * 2 + 1]
        return (r.left + nx * r.width()) to (r.top + ny * r.height())
    }
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
        if (warpMode) {
            // 校正模式：四角手柄 + 连线，拖到纸面四角
            val r = imageRect()
            paint.color = 0x66006EFD
            paint.strokeWidth = resources.displayMetrics.density
            val pts = FloatArray(16)
            for (k in 0 until 4) {
                val (x, y) = cornerScreenPos(r, k)
                pts[k * 4] = x; pts[k * 4 + 1] = y
                val nk = (k + 1) % 4
                val (x2, y2) = cornerScreenPos(r, nk)
                pts[k * 4 + 2] = x2; pts[k * 4 + 3] = y2
            }
            paint.style = Paint.Style.STROKE
            canvas.drawLines(pts, paint)
            paint.style = Paint.Style.FILL
            for (k in 0 until 4) {
                val (x, y) = cornerScreenPos(r, k)
                paint.color = android.graphics.Color.WHITE
                canvas.drawCircle(x, y, 13 * resources.displayMetrics.density, paint)
                paint.color = android.graphics.Color.rgb(0, 110, 253)
                canvas.drawCircle(x, y, 7 * resources.displayMetrics.density, paint)
            }
        }
        if (cropMode) {
            val c = RectF(r.left + crop.left * r.width(), r.top + crop.top * r.height(), r.left + crop.right * r.width(), r.top + crop.bottom * r.height())
            // 选区外压暗；整图选区时外框仍在，靠内缩的描边保证四边可见。
            paint.color = 0x99000000.toInt(); paint.style = Paint.Style.FILL
            canvas.drawRect(r.left, r.top, r.right, c.top, paint); canvas.drawRect(r.left, c.bottom, r.right, r.bottom, paint)
            canvas.drawRect(r.left, c.top, c.left, c.bottom, paint); canvas.drawRect(c.right, c.top, r.right, c.bottom, paint)
            val line = RectF(c); val inset = 1.5f * resources.displayMetrics.density; line.inset(inset, inset)
            // 三分线让白纸上的选区也有可读的裁剪参考。
            paint.style = Paint.Style.STROKE; paint.strokeWidth = resources.displayMetrics.density
            paint.color = 0x66006EFD
            for (i in 1..2) {
                val x = line.left + line.width() * i / 3f
                val y = line.top + line.height() * i / 3f
                canvas.drawLine(x, line.top, x, line.bottom, paint)
                canvas.drawLine(line.left, y, line.right, y, paint)
            }
            // 白底 + 品牌蓝双层描边，浅色试卷和深色桌面上都看得见。
            paint.color = android.graphics.Color.WHITE
            paint.strokeWidth = 4 * resources.displayMetrics.density
            canvas.drawRect(line, paint)
            paint.color = android.graphics.Color.rgb(0, 110, 253)
            paint.strokeWidth = 2 * resources.displayMetrics.density
            canvas.drawRect(line, paint)
            paint.style = Paint.Style.FILL
            val whiteRing = 8 * resources.displayMetrics.density
            val accentDot = 5 * resources.displayMetrics.density
            for (x in listOf(line.left, line.right)) for (y in listOf(line.top, line.bottom)) {
                paint.color = android.graphics.Color.WHITE
                canvas.drawCircle(x, y, whiteRing, paint)
                paint.color = android.graphics.Color.rgb(0, 110, 253)
                canvas.drawCircle(x, y, accentDot, paint)
            }
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
        if (warpMode && event.pointerCount == 1) {
            val r = imageRect()
            val touch = 30 * resources.displayMetrics.density
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dragCorner = -1
                    for (k in 0 until 4) {
                        val (x, y) = cornerScreenPos(r, k)
                        if (abs(event.x - x) < touch && abs(event.y - y) < touch) { dragCorner = k; break }
                    }
                    if (dragCorner >= 0) invalidate()
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (dragCorner >= 0) {
                        val nx = ((event.x - r.left) / r.width()).coerceIn(0f, 1f)
                        val ny = ((event.y - r.top) / r.height()).coerceIn(0f, 1f)
                        warpCorners[dragCorner * 2] = nx
                        warpCorners[dragCorner * 2 + 1] = ny
                        invalidate()
                    }
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    dragCorner = -1; invalidate(); return true
                }
            }
            return true
        }
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
    /** 选区仍是整张图时不要重新编码保存，避免无意义的画质损失。 */
    fun hasCropSelection(): Boolean =
        crop.left > .001f || crop.top > .001f || crop.right < .999f || crop.bottom < .999f
    fun resetCrop() { crop.set(0f, 0f, 1f, 1f); updateGestureExclusion(); invalidate() }
    fun undoStroke() { if (strokes.isNotEmpty()) { strokes.removeAt(strokes.size - 1); invalidate() } }
    fun clearStrokes() { strokes.clear(); invalidate() }

    /** Flatten the current view (oriented source + annotations) into a clean PNG at up to 2200px.
     *  屏显 preview 只有 1600px，导出时按目标尺寸重新解码原图，标注副本才不会比纯裁剪的糊。
     *  crop 非空时先按整图坐标画标注、再从成品里裁出选区：裁剪+标注共用一条路径，
     *  标注坐标始终对齐整图，不会被裁剪平移。 */
    fun exportWithStrokes(output: File, crop: RectF? = null): File {
        val maxDim = 2200f
        val scale = min(1f, maxDim / max(imageWidth, imageHeight))
        val w = (imageWidth * scale).toInt().coerceAtLeast(1)
        val h = (imageHeight * scale).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.WHITE)
        val m = Matrix()
        m.set(orientation); m.postTranslate(-oriented.left, -oriented.top)
        m.postScale(scale, scale)
        val base = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val source = Importer.decodeSampled(file, 2200) ?: preview
        canvas.save(); canvas.concat(m)
        canvas.drawBitmap(source, null, RectF(0f, 0f, decoder.width.toFloat(), decoder.height.toFloat()), base)
        canvas.restore()
        if (source !== preview) source.recycle()
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
        var result = bitmap
        if (crop != null) {
            val x = (crop.left * w).toInt().coerceIn(0, w - 1)
            val y = (crop.top * h).toInt().coerceIn(0, h - 1)
            val cw = ((crop.right - crop.left) * w).toInt().coerceIn(1, w - x)
            val ch = ((crop.bottom - crop.top) * h).toInt().coerceIn(1, h - y)
            val carved = Bitmap.createBitmap(bitmap, x, y, cw, ch)
            if (carved !== bitmap) bitmap.recycle()
            result = carved
        }
        try { output.outputStream().use { check(result.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
        finally { result.recycle() }
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
    var error by remember(uri) { mutableStateOf<String?>(null) }
    // 模式：裁剪 / 旋转（90°+小角度）/ 校正（透视四角）/ 画笔
    var mode by remember { mutableStateOf("crop") }
    var fineAngle by remember { mutableStateOf(0f) }
    var quarter by remember { mutableStateOf(0) }
    var applying by remember { mutableStateOf(false) }
    var brushColor by remember { mutableStateOf<Int>(0xFFE74C3C.toInt()) }
    var brushWidth by remember { mutableStateOf(0.006f) }
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
    FullScreenDialog(onDismissRequest = onDismiss, dismissEnabled = !saving) {
            TjTopBar("原图查看与编辑", "裁剪 · 旋转 · 透视校正 · 标注，另存副本", onBack = { if (!saving) onDismiss() })
            // 图片占据剩余空间，工具栏固定在底部；这样短屏也不会把保存按钮挤出屏幕。
            Box(
                Modifier.fillMaxWidth().weight(1f)
                    .padding(horizontal = 20.dp).padding(top = 4.dp, bottom = 12.dp)
                    .clip(RoundedCornerShape(18.dp)).background(SurfaceAlt)
            ) {
                file?.let { imageFile ->
                    // 变换后 file 会换成新文件：key 住让它重建视图，裁剪/标注状态随之复位
                    androidx.compose.runtime.key(imageFile.absolutePath) {
                        androidx.compose.foundation.layout.Box(
                            Modifier.fillMaxSize()
                                .then(
                                    if (mode == "rotate" && abs(fineAngle) > 0.05f)
                                        Modifier.graphicsLayer { rotationZ = fineAngle }
                                    else Modifier
                                )
                        ) {
                            AndroidView(factory = { ctx ->
                                runCatching { OriginalImageView(ctx, imageFile) }.getOrElse { failure ->
                                    // 文件被系统清理或从旧备份恢复时，原图可能已经不存在；
                                    // 不能因为这个让整个详情页崩溃。
                                    error = failure.message ?: "原图文件已丢失，无法编辑"
                                    android.widget.FrameLayout(ctx)
                                }.also { v -> if (v is OriginalImageView) { view = v; v.resetCrop() } }
                            }, modifier = Modifier.fillMaxSize(), update = { v ->
                                if (v is OriginalImageView) {
                                    val crop = mode == "crop"
                                    val brush = mode == "brush"
                                    val warp = mode == "warp"
                                    if (v.cropMode != crop) v.cropMode = crop
                                    if (v.brushMode != brush) v.brushMode = brush
                                    if (v.warpMode != warp) v.warpMode = warp
                                    v.brushColor = brushColor; v.brushWidth = brushWidth
                                }
                            }, onRelease = { (it as? OriginalImageView)?.close() })
                        }
                    }
                }
                if (file == null && error == null) CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 四个模式一行排开；右侧保留画笔的历史操作
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    EditorToolGroup {
                        EditorTool("裁剪", Icons.Rounded.Crop, mode == "crop", !saving && !applying, Modifier.width(64.dp)) { mode = "crop" }
                        EditorToolDivider()
                        EditorTool("旋转", Icons.AutoMirrored.Rounded.RotateRight, mode == "rotate", !saving && !applying, Modifier.width(64.dp)) { mode = "rotate" }
                        EditorToolDivider()
                        EditorTool("校正", Icons.Rounded.Transform, mode == "warp", !saving && !applying, Modifier.width(64.dp)) { mode = "warp" }
                        EditorToolDivider()
                        EditorTool("画笔", Icons.Rounded.Brush, mode == "brush", !saving && !applying, Modifier.width(64.dp)) { mode = "brush" }
                    }
                    if (mode == "brush") {
                        EditorToolGroup {
                            EditorTool("撤销", Icons.AutoMirrored.Rounded.Undo, false, !saving && !applying, Modifier.width(56.dp)) { view?.undoStroke() }
                            EditorToolDivider()
                            EditorTool("清空", Icons.Rounded.DeleteOutline, false, !saving && !applying, Modifier.width(56.dp)) { view?.clearStrokes() }
                        }
                    }
                }
                when (mode) {
                    "rotate" -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("微调", fontSize = 11.sp, color = Muted)
                                androidx.compose.material3.Slider(
                                    value = fineAngle,
                                    onValueChange = { fineAngle = (it * 2).roundToInt() / 2f },
                                    valueRange = -15f..15f,
                                    enabled = !applying && !saving,
                                    modifier = Modifier.weight(1f).height(30.dp),
                                )
                                Text("${"%.1f".format(fineAngle)}°", fontSize = 11.sp, color = Ink2, modifier = Modifier.width(38.dp))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                TjButton("⟲ 90°", Modifier.weight(1f), style = ActionStyle.Secondary, enabled = !applying && !saving) { quarter = (quarter + 3) % 4 }
                                TjButton("⟳ 90°", Modifier.weight(1f), style = ActionStyle.Secondary, enabled = !applying && !saving) { quarter = (quarter + 1) % 4 }
                                TjButton(
                                    if (applying) "应用中…" else "应用旋转",
                                    Modifier.weight(1.4f),
                                    enabled = !applying && !saving && (quarter != 0 || abs(fineAngle) > 0.05f),
                                    loading = applying,
                                ) {
                                    val src = file ?: return@TjButton
                                    applying = true; error = null
                                    scope.launch {
                                        try {
                                            val nf = withContext(Dispatchers.IO) {
                                                transformImage(src, File.createTempFile("rot_", ".png", context.cacheDir), quarter, fineAngle, null)
                                            }
                                            quarter = 0; fineAngle = 0f
                                            file = nf
                                        } catch (cancelled: CancellationException) { throw cancelled }
                                        catch (failure: Exception) { error = failure.message ?: "旋转失败" }
                                        finally { applying = false }
                                    }
                                }
                            }
                            Text("小角度旋转把拍歪的纸面调平，再切回「裁剪」就不会裁到邻题；90° 整数旋转立即换向。", fontSize = 11.sp, lineHeight = 16.sp, color = Muted)
                        }
                    }
                    "warp" -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TjButton("重置四角", Modifier.weight(1f), style = ActionStyle.Secondary, enabled = !applying && !saving) {
                                    view?.resetWarpCorners()
                                }
                                TjButton(
                                    if (applying) "应用中…" else "应用透视校正",
                                    Modifier.weight(1.4f),
                                    enabled = !applying && !saving && view?.warpIsDefault() == false,
                                    loading = applying,
                                ) {
                                    val src = file ?: return@TjButton
                                    val target = view ?: return@TjButton
                                    applying = true; error = null
                                    scope.launch {
                                        try {
                                            val nf = withContext(Dispatchers.IO) {
                                                transformImage(src, File.createTempFile("warp_", ".png", context.cacheDir), 0, 0f, target.warpCorners.copyOf())
                                            }
                                            file = nf
                                            mode = "crop"
                                        } catch (cancelled: CancellationException) { throw cancelled }
                                        catch (failure: Exception) { error = failure.message ?: "校正失败" }
                                        finally { applying = false }
                                    }
                                }
                            }
                            Text("把四个蓝点拖到纸面的四个角，应用后视角拉正、纸面填满画布；之后可再裁剪或标注。", fontSize = 11.sp, lineHeight = 16.sp, color = Muted)
                        }
                    }
                    "brush" -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("颜色", fontSize = 11.sp, color = Muted, modifier = Modifier.width(30.dp))
                                listOf(0xFFE74C3C, 0xFF2D7FF9, 0xFF16A34A, 0xFF111827).map { it.toInt() }.forEach { c ->
                                    Box(
                                        Modifier.size(32.dp).clip(CircleShape).background(ComposeColor(c))
                                            .border(if (brushColor == c) 2.5.dp else 1.dp, if (brushColor == c) Accent else Line, CircleShape)
                                            .tjClickable { brushColor = c; view?.brushColor = c }
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("粗细", fontSize = 11.sp, color = Muted, modifier = Modifier.width(30.dp))
                                listOf(0.003f, 0.006f, 0.011f).zip(listOf("细", "中", "粗")).forEach { (w, label) ->
                                    Box(
                                        Modifier.size(36.dp).clip(CircleShape)
                                            .background(if (brushWidth == w) AccentSoft else CardBg)
                                            .border(1.dp, if (brushWidth == w) Accent else Line, CircleShape)
                                            .tjClickable { brushWidth = w; view?.brushWidth = w },
                                        contentAlignment = Alignment.Center
                                    ) { Text(label, fontSize = 11.sp, color = if (brushWidth == w) Accent else Ink2) }
                                }
                            }
                        }
                    }
                    else -> {
                        Text(
                            "拖动四角或边框裁剪；保存时只裁选中的区域。拍歪了先去「旋转/校正」调平。",
                            fontSize = 11.sp, lineHeight = 16.sp, color = Muted
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TjButton("重置", Modifier.weight(1f), style = ActionStyle.Secondary, enabled = !saving) {
                        view?.resetCrop()
                    }
                    TjButton(
                        if (saving) "保存中" else "保存副本", Modifier.weight(1.35f),
                        enabled = !saving && file != null && !applying, loading = saving
                    ) {
                        val source = file ?: return@TjButton
                        val target = view
                        // 选区只看「有没有裁过」，不看当前在哪个模式：用户拖完选区后
                        // 切到画笔、或点「裁剪」按钮想确认，保存时选区都必须生效。
                        // 之前要求 cropping 模式下保存才裁，导致第一次裁剪经常被静默丢弃。
                        val cropRect = target?.takeIf { it.hasCropSelection() }?.let { RectF(it.crop) }
                        val annotated = target?.strokes?.isNotEmpty() == true
                        saving = true; error = null
                        if (cropRect != null || annotated) scope.launch {
                            var output: File? = null
                            try {
                                output = withContext(Dispatchers.IO) {
                                    when {
                                        // 标注导出自己处理裁剪（整图渲染后按选区裁出），坐标才对得上
                                        annotated -> target!!.exportWithStrokes(
                                            File.createTempFile("edit_", ".png", context.cacheDir), cropRect
                                        )
                                        cropRect != null -> cropOriginal(
                                            source, cropRect, File.createTempFile("crop_", ".png", context.cacheDir)
                                        )
                                        else -> source
                                    }
                                }
                                onSaved(Uri.fromFile(output))
                            } catch (cancelled: CancellationException) { throw cancelled }
                            catch (failure: Exception) { error = failure.message ?: "保存失败，请重试" }
                            finally { output?.delete(); saving = false }
                        } else scope.launch { saving = true; try { onSaved(Uri.fromFile(source)) } finally { saving = false } }
                    }
                }
                error?.let { Text(it, color = Red, fontSize = 11.5.sp) }
            }
    }
}

@Composable
private fun EditorToolGroup(modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        modifier.clip(RoundedCornerShape(18.dp))
            .background(SurfaceAlt)
            .border(1.dp, Line, RoundedCornerShape(18.dp))
            .padding(5.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.EditorToolDivider() {
    Box(Modifier.width(1.dp).height(26.dp).background(Line.copy(alpha = .6f)))
}

@Composable
private fun EditorTool(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val tint = when {
        !enabled -> Muted.copy(alpha = .38f)
        selected -> Accent
        else -> Ink
    }
    Column(
        modifier.height(56.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) AccentSoft else ComposeColor.Transparent)
            .tjClickable(enabled = enabled) { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(6.dp))
        Text(
            label, fontSize = 11.5.sp, color = tint,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}

/**
 * 单应变换（4 点对应 → 3×3 投影矩阵）。用于透视校正：
 * 用户在图上选的四个角（源坐标）映射到输出矩形的四个角。
 * 解 8 元线性方程组（高斯消元）。
 */
internal class PerspectiveTransform(src: FloatArray, dst: FloatArray) {
    private val h = FloatArray(9)

    init {
        val a = FloatArray(8 * 9)
        for (i in 0 until 4) {
            val x = src[i * 2]; val y = src[i * 2 + 1]
            val u = dst[i * 2]; val v = dst[i * 2 + 1]
            val r = i * 2
            a[r * 9] = x; a[r * 9 + 1] = y; a[r * 9 + 2] = 1f
            a[r * 9 + 6] = -x * u; a[r * 9 + 7] = -y * u; a[r * 9 + 8] = u
            val r2 = (i * 2 + 1)
            a[r2 * 9 + 3] = x; a[r2 * 9 + 4] = y; a[r2 * 9 + 5] = 1f
            a[r2 * 9 + 6] = -x * v; a[r2 * 9 + 7] = -y * v; a[r2 * 9 + 8] = v
        }
        // 高斯消元（部分主元）
        for (col in 0 until 8) {
            var pivot = col
            for (row in col + 1 until 8) if (abs(a[row * 9 + col]) > abs(a[pivot * 9 + col])) pivot = row
            if (abs(a[pivot * 9 + col]) < 1e-6f) throw IllegalArgumentException("degenerate quad")
            if (pivot != col) for (k in col..8) { val t = a[col * 9 + k]; a[col * 9 + k] = a[pivot * 9 + k]; a[pivot * 9 + k] = t }
            val d = a[col * 9 + col]
            for (k in col..8) a[col * 9 + k] /= d
            for (row in 0 until 8) {
                if (row == col) continue
                val f = a[row * 9 + col]
                if (f != 0f) for (k in col..8) a[row * 9 + k] -= f * a[col * 9 + k]
            }
        }
        for (i in 0 until 8) h[i] = a[i * 9 + 8]
        h[8] = 1f
    }

    fun map(x: Float, y: Float, out: FloatArray) {
        val w = h[6] * x + h[7] * y + h[8]
        out[0] = (h[0] * x + h[1] * y + h[2]) / w
        out[1] = (h[3] * x + h[4] * y + h[5]) / w
    }
}

/** 把位图按四角网格 warp 到输出矩形（drawBitmapMesh，projective 的分片线性逼近）。 */
private fun warpBitmap(src: Bitmap, transform: PerspectiveTransform, outW: Int, outH: Int, mesh: Int): Bitmap {
    val verts = FloatArray((mesh + 1) * (mesh + 1) * 2)
    val pt = FloatArray(2)
    var idx = 0
    for (j in 0..mesh) {
        for (i in 0..mesh) {
            transform.map(i.toFloat() / mesh * src.width, j.toFloat() / mesh * src.height, pt)
            verts[idx++] = pt[0]
            verts[idx++] = pt[1]
        }
    }
    val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    canvas.drawColor(android.graphics.Color.WHITE)
    val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    canvas.drawBitmapMesh(src, mesh, mesh, verts, 0, null, 0, paint)
    return out
}

/**
 * 旋转 + 透视校正一步到位（全分辨率）：
 * 1. EXIF 摆正 → 90° quarterTurns → fineAngle 小角度（画布外扩，不裁内容）；
 * 2. 透视四角（归一化，相对旋转后图）映射到输出矩形（drawBitmapMesh）。
 */
internal fun transformImage(
    src: File,
    out: File,
    quarterTurns: Int,
    fineAngle: Float,
    warpCorners: FloatArray?,
): File {
    val base = Importer.decodeSampled(src, 2200) ?: throw IllegalStateException("图片解码失败")
    val m = Matrix(orientationMatrix(src.path))
    // 90° 整数旋转
    if (quarterTurns.mod(4) != 0) m.postRotate((quarterTurns % 4) * 90f)
    // 归一化到正坐标
    val bounds1 = RectF(0f, 0f, base.width.toFloat(), base.height.toFloat())
    m.mapRect(bounds1)
    m.postTranslate(-bounds1.left, -bounds1.top)
    // 小角度旋转（绕当前画布中心，画布外扩不裁边）
    var bw = bounds1.width(); var bh = bounds1.height()
    if (abs(fineAngle) > 0.05f) {
        m.postRotate(fineAngle, bw / 2f, bh / 2f)
        val bounds2 = RectF(0f, 0f, bw, bh)
        m.mapRect(bounds2)
        m.postTranslate(-bounds2.left, -bounds2.top)
        bw = bounds2.width(); bh = bounds2.height()
    }
    var rotated = Bitmap.createBitmap(bw.toInt().coerceAtLeast(1), bh.toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(rotated)
    canvas.drawColor(android.graphics.Color.WHITE)
    val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    canvas.drawBitmap(base, m, paint)
    if (base !== rotated) base.recycle()

    if (warpCorners != null && warpCorners.size == 8) {
        val rw = rotated.width.toFloat(); val rh = rotated.height.toFloat()
        val srcPts = FloatArray(8)
        for (k in 0 until 4) {
            srcPts[k * 2] = warpCorners[k * 2] * rw
            srcPts[k * 2 + 1] = warpCorners[k * 2 + 1] * rh
        }
        // 输出尺寸：对边平均长度，封顶 2200
        val top = dist(srcPts[0], srcPts[1], srcPts[2], srcPts[3])
        val bottom = dist(srcPts[6], srcPts[7], srcPts[4], srcPts[5])
        val left = dist(srcPts[0], srcPts[1], srcPts[6], srcPts[7])
        val right = dist(srcPts[2], srcPts[3], srcPts[4], srcPts[5])
        var outW = ((top + bottom) / 2f).toInt().coerceAtLeast(32)
        var outH = ((left + right) / 2f).toInt().coerceAtLeast(32)
        val scale = min(1f, 2200f / max(outW, outH))
        outW = (outW * scale).toInt().coerceAtLeast(32)
        outH = (outH * scale).toInt().coerceAtLeast(32)
        val dst = floatArrayOf(0f, 0f, outW.toFloat(), 0f, outW.toFloat(), outH.toFloat(), 0f, outH.toFloat())
        val warped = warpBitmap(rotated, PerspectiveTransform(srcPts, dst), outW, outH, 80)
        rotated.recycle()
        rotated = warped
    }
    try {
        out.outputStream().use { check(rotated.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    } finally {
        rotated.recycle()
    }
    return out
}

private fun dist(x1: Float, y1: Float, x2: Float, y2: Float): Float =
    sqrt((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1))
