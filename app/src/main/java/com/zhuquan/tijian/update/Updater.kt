package com.zhuquan.tijian.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.zhuquan.tijian.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * 应用内更新：GitHub Release 优先，relay 镜像兜底。
 * Release 需附带 update.json 资产：
 * {"version":"0.2.0","versionCode":2,"apkUrl":"...","sha256":"...","size":123,"notes":"..."}
 */
object Updater {

    private const val REPO = "zhuquan7237/tijian"
    private const val API = "https://api.github.com/repos/$REPO/releases/latest"
    private const val LATEST_JSON = "https://github.com/$REPO/releases/latest/download/update.json"
    private const val MIRROR = "https://relay.zhuquan.xyz/dl/tijian-latest.json"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    data class Info(
        val version: String,
        val versionCode: Int,
        val apkUrl: String,
        val sha256: String,
        val size: Long,
        val notes: String,
        val source: String,
    )

    fun currentCode(): Int = BuildConfig.VERSION_CODE
    fun currentName(): String = BuildConfig.VERSION_NAME

    suspend fun check(ctx: Context): Info? = withContext(Dispatchers.IO) {
        val candidates = ArrayList<Info>()
        // 1) 固定名 update.json（GitHub latest 重定向）
        runCatching { fetchJson(LATEST_JSON)?.let { parse(it, "github") } }
            .getOrNull()?.let { candidates.add(it) }
        // 2) GitHub API：找 update.json 资产，或退化到 tag
        runCatching { fromApi() }.getOrNull()?.let { candidates.add(it) }
        // 3) 自建镜像
        runCatching { fetchJson(MIRROR)?.let { parse(it, "mirror") } }
            .getOrNull()?.let { candidates.add(it) }

        val best = candidates.filter { it.versionCode > currentCode() }
            .maxByOrNull { it.versionCode }
        best
    }

    private fun fromApi(): Info? {
        val text = fetchJson(API) ?: return null
        val obj = JSONObject(text)
        val tag = obj.optString("tag_name").removePrefix("v").removePrefix("V")
        val assets = obj.optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val a = assets.optJSONObject(i) ?: continue
            if (a.optString("name") == "update.json") {
                val url = a.optString("browser_download_url")
                val body = fetchJson(url) ?: continue
                return parse(body, "github-api")
            }
        }
        // 没有 update.json 时用 tag 推导 versionCode
        val code = semverCode(tag) ?: return null
        var apkUrl = ""
        var size = 0L
        for (i in 0 until assets.length()) {
            val a = assets.optJSONObject(i) ?: continue
            if (a.optString("name").endsWith(".apk")) {
                apkUrl = a.optString("browser_download_url"); size = a.optLong("size")
                break
            }
        }
        if (apkUrl.isBlank()) return null
        return Info(tag, code, apkUrl, "", size, obj.optString("body").take(500), "github-api")
    }

    private fun parse(text: String, source: String): Info? = runCatching {
        val o = JSONObject(text)
        val v = o.optString("version").removePrefix("v")
        val code = o.optInt("versionCode", semverCode(v) ?: 0)
        val apk = o.optString("apkUrl")
        if (apk.isBlank() || code <= 0) return null
        Info(
            version = v, versionCode = code, apkUrl = apk,
            sha256 = o.optString("sha256"), size = o.optLong("size"),
            notes = o.optString("notes"), source = source
        )
    }.getOrNull()

    private fun fetchJson(url: String): String? = runCatching {
        val req = Request.Builder().url(url)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Tijian-Android")
            .build()
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) null else r.body?.string()
        }
    }.getOrNull()

    private fun semverCode(v: String): Int? {
        val parts = v.split('.').mapNotNull { it.toIntOrNull() }
        if (parts.isEmpty()) return null
        val a = parts.getOrElse(0) { 0 }
        val b = parts.getOrElse(1) { 0 }
        val c = parts.getOrElse(2) { 0 }
        return a * 1000000 + b * 10000 + c * 100
    }

    suspend fun download(ctx: Context, info: Info, onProgress: (Int) -> Unit): File =
        withContext(Dispatchers.IO) {
            val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
            val out = File(dir, "tijian-${info.version}.apk")
            if (out.exists() && info.sha256.isNotBlank() && sha256(out) == info.sha256) return@withContext out

            val req = Request.Builder().url(info.apkUrl).header("User-Agent", "Tijian-Android").build()
            client.newCall(req).execute().use { r ->
                if (!r.isSuccessful) throw IllegalStateException("下载失败：HTTP ${r.code}")
                val body = r.body ?: throw IllegalStateException("下载失败：空响应")
                val total = body.contentLength()
                body.byteStream().use { input ->
                    FileOutputStream(out).use { output ->
                        val buf = ByteArray(64 * 1024)
                        var read: Int
                        var done = 0L
                        var last = -1
                        while (input.read(buf).also { read = it } > 0) {
                            output.write(buf, 0, read)
                            done += read
                            if (total > 0) {
                                val p = (done * 100 / total).toInt()
                                if (p != last) { last = p; onProgress(p) }
                            }
                        }
                    }
                }
            }
            if (info.sha256.isNotBlank()) {
                val actual = sha256(out)
                if (!actual.equals(info.sha256, ignoreCase = true)) {
                    out.delete()
                    throw IllegalStateException("校验失败：安装包不完整")
                }
            }
            out
        }

    fun install(ctx: Context, file: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        val direct = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            data = uri
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val viaView = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            if (direct.resolveActivity(ctx.packageManager) != null) ctx.startActivity(direct)
            else ctx.startActivity(viaView)
        } catch (e: Exception) {
            runCatching { ctx.startActivity(viaView) }
        }
    }

    fun shareApk(ctx: Context, file: File) {
        val uri: Uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.android.package-archive"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(intent, "分享安装包").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            var n: Int
            while (input.read(buf).also { n = it } > 0) md.update(buf, 0, n)
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
