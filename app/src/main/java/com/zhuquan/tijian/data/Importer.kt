package com.zhuquan.tijian.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.zhuquan.tijian.ai.AiClient
import com.zhuquan.tijian.ai.ParsedQuestion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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
                // 用户点「停止」后立刻退出循环，不再发起下一张的识别请求
                currentCoroutineContext().ensureActive()
                onStep(index + 1, uris.size, "读取图片 ${index + 1}/${uris.size}")
                val img = try {
                    ingest(uri)
                } catch (e: Exception) {
                    errors.add("第 ${index + 1} 张：${e.message ?: "读取失败"}")
                    null
                } ?: return@forEachIndexed

                if (img.duplicate) {
                    duplicates++
                }

                onStep(index + 1, uris.size, "识别第 ${index + 1}/${uris.size} 张")
                val cfg = db.provider()
                if (!cfg.ready()) {
                    drafts.add(emptyDraft(img))
                    errors.add("未配置 AI 接口，已保留原图，可稍后手动录入")
                    return@forEachIndexed
                }
                try {
                    currentCoroutineContext().ensureActive()
                    val jpeg = downscaleJpeg(File(img.path), 1600)
                    var parsed = ai.recognize(cfg, jpeg)
                    // 照片里没印答案/解析时，识别结果会是空字段。这里自动补一次，
                    // 避免入库后详情页出现「有题目、没解析、没答案」。
                    if (parsed.any { it.answer.isBlank() || it.analysis.isBlank() }) {
                        onStep(index + 1, uris.size, "补全解析 ${index + 1}/${uris.size}")
                        parsed = try {
                            ai.fillSolutions(cfg, parsed)
                        } catch (e: Exception) {
                            // 补全失败不让整张图报废，但要告诉用户这批的解析是缺失的
                            errors.add("第 ${index + 1} 张：答案/解析补全失败（${e.message ?: "网络错误"}），已保留识别结果")
                            parsed
                        }
                    }
                    parsed.forEachIndexed { i, p -> drafts.add(toDraft(img, p, i)) }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
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
        return DraftQuestion(
            imageId = img.id,
            imagePath = img.path,
            type = p.type,
            stem = p.stem,
            options = JSONArray(p.options).toString(),
            answer = p.answer,
            analysis = p.analysis,
            tags = JSONArray(p.tags).toString(),
            // 识别阶段已经给出的「需要掌握」必须带进草稿：漏掉这三行会让入库的永远是 []，
            // 用户看到的就是每道题都要再点一次「让 AI 分析」。
            needFormulas = JSONArray(p.needFormulas).toString(),
            needConcepts = JSONArray(p.needConcepts).toString(),
            needMethods = JSONArray(p.needMethods).toString(),
            difficulty = p.difficulty,
            categoryName = p.category,
            confidence = p.confidence,
            bbox = p.bbox,
        )
    }

    private fun ingest(uri: Uri): ImportedImage {
        val raw = File(context.cacheDir, "import_${System.currentTimeMillis()}.bin")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                raw.outputStream().use { output -> input.copyTo(output) }
            } ?: throw IllegalStateException("无法打开图片")
            val digest = MessageDigest.getInstance("SHA-256")
            raw.inputStream().buffered().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            val sha = digest.digest().joinToString("") { "%02x".format(it) }

            db.imageIdBySha(sha)?.let { existing ->
                val path = db.imagePath(existing)
                if (path != null && File(path).exists()) {
                    val (w, h) = imageSize(path)
                    return ImportedImage(existing, path, w, h, sha, duplicate = true)
                }
                // 行还在、文件被清理了：把行重新绑到新落盘的文件并沿用原 id。
                // 直接换个新 id 插入会被唯一索引挡掉，产生从未入库的「幻影 id」。
                val (w2, h2) = imageSize(raw.absolutePath)
                require(w2 > 0 && h2 > 0) { "图片格式无法读取" }
                val file = File(dir, "$sha.original")
                raw.copyTo(file, overwrite = true)
                db.rebindImage(existing, file.absolutePath, w2, h2, file.length())
                return ImportedImage(existing, file.absolutePath, w2, h2, sha, duplicate = true)
            }

            val (w, h) = imageSize(raw.absolutePath)
            require(w > 0 && h > 0) { "图片格式无法读取" }
            val file = File(dir, "$sha.original")
            raw.copyTo(file, overwrite = true)
            val id = newId()
            if (!db.insertImage(id, sha, file.absolutePath, w, h, file.length())) {
                // 并发兜底：同 sha 的行刚好被别的线程插入了，回到已存在的行
                val winner = db.imageIdBySha(sha)
                if (winner != null) {
                    val p = db.imagePath(winner) ?: file.absolutePath
                    return ImportedImage(winner, p, w, h, sha, duplicate = true)
                }
            }
            return ImportedImage(id, file.absolutePath, w, h, sha, duplicate = false)
        } finally {
            // 任何失败路径（包括「图片格式无法读取」）都不能把临时文件留在 cache 里
            raw.delete()
        }
    }

    private fun imageSize(path: String): Pair<Int, Int> {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, o)
        return o.outWidth to o.outHeight
    }

    private fun downscaleJpeg(file: File, maxDim: Int): ByteArray {
        val raw = decodeSampled(file, maxDim) ?: throw IllegalStateException("图片解码失败")
        val bmp = orientBitmap(raw, file.path)
        if (bmp !== raw) raw.recycle()
        val out = ByteArrayOutputStream()
        try { bmp.compress(Bitmap.CompressFormat.JPEG, 85, out); return out.toByteArray() }
        finally { bmp.recycle() }
    }

    companion object {
        fun decodeSampled(file: File, maxDim: Int): Bitmap? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxDim * 2) sample *= 2
            val bitmap = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
            val ratio = maxDim.toFloat() / maxOf(bitmap.width, bitmap.height)
            if (ratio >= 1f) return bitmap
            return Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt().coerceAtLeast(1),
                (bitmap.height * ratio).toInt().coerceAtLeast(1), true).also { if (it !== bitmap) bitmap.recycle() }
        }

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
