package com.zhuquan.tijian.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import com.zhuquan.tijian.ai.AiClient
import com.zhuquan.tijian.ai.ParsedQuestion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest

data class ImportedImage(
    val id: String, val path: String, val width: Int, val height: Int,
    val sha: String, val duplicate: Boolean,
)

data class ImportOutcome(
    val batchId: String,
    val drafts: List<DraftQuestion>,
    val errors: List<String>,
    val duplicates: Int,
)

class Importer(private val context: Context, private val db: Db, private val ai: AiClient) {

    private val dir = File(context.filesDir, "images").apply { mkdirs() }

    suspend fun import(uris: List<Uri>, source: String, onStep: (Int, Int, String) -> Unit): ImportOutcome =
        withContext(Dispatchers.IO) {
            val batchId = db.createBatch(source, uris.size)
            val drafts = ArrayList<DraftQuestion>()
            val errors = ArrayList<String>()
            var duplicates = 0

            uris.forEachIndexed { index, uri ->
                onStep(index + 1, uris.size, "读取图片 ${index + 1}/${uris.size}")
                val img = try {
                    ingest(uri)
                } catch (e: Exception) {
                    errors.add("第 ${index + 1} 张：${e.message ?: "读取失败"}")
                    null
                } ?: return@forEachIndexed

                if (img.duplicate) {
                    duplicates++
                    return@forEachIndexed
                }

                onStep(index + 1, uris.size, "识别第 ${index + 1}/${uris.size} 张")
                val cfg = db.provider()
                if (!cfg.ready()) {
                    drafts.add(emptyDraft(img))
                    errors.add("未配置 AI 接口，已保留原图，可稍后手动录入")
                    return@forEachIndexed
                }
                try {
                    val jpeg = downscaleJpeg(File(img.path), 1600)
                    val parsed = ai.recognize(cfg, jpeg)
                    parsed.forEachIndexed { i, p -> drafts.add(toDraft(img, p, i)) }
                } catch (e: Exception) {
                    drafts.add(emptyDraft(img))
                    errors.add("识别失败：${e.message ?: "未知错误"}")
                }
            }
            db.finishBatch(batchId, "review", db.provider().model)
            ImportOutcome(batchId, drafts, errors, duplicates)
        }

    /** 只入库图片，不调用 AI（手动录入用） */
    suspend fun ingestOnly(uri: Uri): ImportedImage = withContext(Dispatchers.IO) { ingest(uri) }

    private fun emptyDraft(img: ImportedImage) = DraftQuestion(
        imageId = img.id, imagePath = img.path, confidence = 0.0
    )

    private fun toDraft(img: ImportedImage, p: ParsedQuestion, index: Int): DraftQuestion {
        var imageId = img.id
        var path = img.path
        if (p.bbox != null) {
            val cropped = crop(img, p.bbox, index)
            if (cropped != null) { imageId = cropped.id; path = cropped.path }
        }
        return DraftQuestion(
            imageId = imageId,
            imagePath = path,
            type = p.type,
            stem = p.stem,
            options = JSONArray(p.options).toString(),
            answer = p.answer,
            analysis = p.analysis,
            tags = JSONArray(p.tags).toString(),
            difficulty = p.difficulty,
            categoryName = p.category,
            confidence = p.confidence,
            bbox = p.bbox,
        )
    }

    private fun crop(img: ImportedImage, bbox: List<Double>, index: Int): ImportedImage? {
        return try {
            val bmp = decodeSampled(File(img.path), 2400) ?: return null
            val pad = 0.012
            val x1 = ((bbox[0] - pad).coerceIn(0.0, 1.0) * bmp.width).toInt()
            val y1 = ((bbox[1] - pad).coerceIn(0.0, 1.0) * bmp.height).toInt()
            val x2 = ((bbox[2] + pad).coerceIn(0.0, 1.0) * bmp.width).toInt()
            val y2 = ((bbox[3] + pad).coerceIn(0.0, 1.0) * bmp.height).toInt()
            val w = (x2 - x1).coerceAtLeast(40)
            val h = (y2 - y1).coerceAtLeast(40)
            if (x1 >= bmp.width || y1 >= bmp.height) return null
            val rect = android.graphics.Rect(x1, y1, minOf(x1 + w, bmp.width), minOf(y1 + h, bmp.height))
            val out = Bitmap.createBitmap(bmp, rect.left, rect.top, rect.width(), rect.height())
            persist(out, "${img.sha}_$index")
        } catch (e: Exception) {
            null
        }
    }

    private fun ingest(uri: Uri): ImportedImage {
        val raw = File(context.cacheDir, "import_${System.currentTimeMillis()}.bin")
        context.contentResolver.openInputStream(uri)?.use { input ->
            raw.outputStream().use { output -> input.copyTo(output) }
        } ?: throw IllegalStateException("无法打开图片")
        val bytes = raw.readBytes()
        val sha = sha256(bytes)

        db.imageIdBySha(sha)?.let { existing ->
            val path = db.imagePath(existing)
            if (path != null && File(path).exists()) {
                val (w, h) = imageSize(path)
                raw.delete()
                return ImportedImage(existing, path, w, h, sha, duplicate = true)
            }
        }

        val degrees = exifDegrees(raw.absolutePath)
        val decoded = decodeSampled(bytes, maxDim = 2200)
            ?: throw IllegalStateException("图片解码失败")
        val bmp = decoded.rotateIfNeeded(degrees)
        raw.delete()
        val saved = persist(bmp, sha) ?: throw IllegalStateException("保存失败")
        return saved.copy(sha = sha)
    }

    private fun persist(bmp: Bitmap, key: String): ImportedImage? {
        val file = File(dir, "$key.jpg")
        if (!file.exists()) {
            file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        }
        val bytes = file.length()
        val sha = sha256(file.readBytes())
        val existing = db.imageIdBySha(sha)
        if (existing != null) {
            val p = db.imagePath(existing) ?: file.absolutePath
            return ImportedImage(existing, p, bmp.width, bmp.height, sha, duplicate = false)
        }
        val id = newId()
        db.insertImage(id, sha, file.absolutePath, bmp.width, bmp.height, bytes)
        return ImportedImage(id, file.absolutePath, bmp.width, bmp.height, sha, duplicate = false)
    }

    private fun imageSize(path: String): Pair<Int, Int> {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, o)
        return o.outWidth to o.outHeight
    }

    private fun downscaleJpeg(file: File, maxDim: Int): ByteArray {
        val bmp = decodeSampled(file, maxDim) ?: throw IllegalStateException("图片解码失败")
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
        return out.toByteArray()
    }

    companion object {
        fun decodeSampled(file: File, maxDim: Int): Bitmap? =
            decodeSampled(file.readBytes(), maxDim)

        fun decodeSampled(rawBytes: ByteArray, maxDim: Int): Bitmap? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxDim * 2) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            var bmp = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, opts) ?: return null
            val ratio = maxDim.toFloat() / maxOf(bmp.width, bmp.height)
            if (ratio < 1f) {
                bmp = Bitmap.createScaledBitmap(
                    bmp, (bmp.width * ratio).toInt(), (bmp.height * ratio).toInt(), true
                )
            }
            return bmp
        }

        fun sha256(bytes: ByteArray): String {
            val md = MessageDigest.getInstance("SHA-256")
            return md.digest(bytes).joinToString("") { "%02x".format(it) }
        }
    }
}

/** 相册/相机图片按 EXIF 摆正后再存 */
fun Bitmap.rotateIfNeeded(degrees: Int): Bitmap =
    if (degrees == 0) this else Bitmap.createBitmap(
        this, 0, 0, width, height,
        Matrix().apply { postRotate(degrees.toFloat()) }, true
    )

fun exifDegrees(path: String): Int = try {
    val exif = ExifInterface(path)
    when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90
        ExifInterface.ORIENTATION_ROTATE_180 -> 180
        ExifInterface.ORIENTATION_ROTATE_270 -> 270
        else -> 0
    }
} catch (e: Exception) { 0 }
