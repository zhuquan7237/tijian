package com.zhuquan.tijian.data

import android.graphics.*
import android.media.ExifInterface
import java.io.File

/** Maps all eight EXIF orientations, including mirrored camera images. */
fun orientationMatrix(path: String): Matrix = Matrix().apply {
    val orientation = runCatching { ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) }.getOrDefault(1)
    when (orientation) {
        2 -> setScale(-1f, 1f)
        3 -> setRotate(180f)
        4 -> setScale(1f, -1f)
        5 -> { setRotate(90f); postScale(-1f, 1f) }
        6 -> setRotate(90f)
        7 -> { setRotate(270f); postScale(-1f, 1f) }
        8 -> setRotate(270f)
    }
}

fun orientBitmap(bitmap: Bitmap, path: String): Bitmap {
    val m = orientationMatrix(path)
    return if (m.isIdentity) bitmap else Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
}

/** Normalized crop coordinates in the oriented image; decode only requested pixels. */
fun cropOriginal(file: File, crop: RectF, output: File): File {
    val decoder = BitmapRegionDecoder.newInstance(file.path, false) ?: error("图片不支持裁剪")
    try {
        val matrix = orientationMatrix(file.path)
        val bounds = RectF(0f, 0f, decoder.width.toFloat(), decoder.height.toFloat())
        matrix.mapRect(bounds)
        matrix.postTranslate(-bounds.left, -bounds.top)
        val requested = RectF(crop.left * bounds.width(), crop.top * bounds.height(), crop.right * bounds.width(), crop.bottom * bounds.height())
        val inverse = Matrix(); check(matrix.invert(inverse))
        inverse.mapRect(requested)
        val rect = Rect(kotlin.math.floor(requested.left).toInt().coerceIn(0, decoder.width - 1),
            kotlin.math.floor(requested.top).toInt().coerceIn(0, decoder.height - 1),
            kotlin.math.ceil(requested.right).toInt().coerceIn(1, decoder.width),
            kotlin.math.ceil(requested.bottom).toInt().coerceIn(1, decoder.height))
        require(rect.width().toLong() * rect.height() <= 40_000_000L) { "裁剪区域过大，请缩小边框后重试（原图仍完整保留）" }
        val raw = decoder.decodeRegion(rect, BitmapFactory.Options()) ?: error("裁剪失败")
        val result = orientBitmap(raw, file.path)
        try { output.outputStream().use { check(result.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
        finally { if (result !== raw) result.recycle(); raw.recycle() }
        return output
    } finally { decoder.recycle() }
}
