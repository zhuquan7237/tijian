package com.zhuquan.tijian.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zhuquan.tijian.App
import com.zhuquan.tijian.data.CategoryRow
import com.zhuquan.tijian.data.DocRow
import com.zhuquan.tijian.data.DraftQuestion
import com.zhuquan.tijian.data.ProviderConfig
import com.zhuquan.tijian.data.QuestionRow
import com.zhuquan.tijian.data.Stats
import com.zhuquan.tijian.export.PdfExporter
import com.zhuquan.tijian.update.Updater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream

sealed interface Route {
    data object Home : Route
    data object Library : Route
    data object Settings : Route
    data class Category(val id: String) : Route
    data class Detail(val id: String) : Route
    data object Import : Route
    data object Capture : Route
    data object Review : Route
    data class Doc(val id: String) : Route
    data object Provider : Route
}

class AppVm(app: Application) : AndroidViewModel(app) {

    private val application = app as App
    val db = application.db
    val ai = application.ai
    private val importer = application.importer

    var route by androidx.compose.runtime.mutableStateOf<Route>(Route.Home)
        private set
    var navForward by androidx.compose.runtime.mutableStateOf(true)
        private set
    val categoryScroll = HashMap<String, Int>()
    private val stack = ArrayList<Route>()

    var categories by androidx.compose.runtime.mutableStateOf<List<CategoryRow>>(emptyList())
        private set
    var stats by androidx.compose.runtime.mutableStateOf(Stats(0, 0, 0, 0))
        private set
    var docs by androidx.compose.runtime.mutableStateOf<List<DocRow>>(emptyList())
        private set
    var provider by androidx.compose.runtime.mutableStateOf(ProviderConfig())
        private set

    var toast by androidx.compose.runtime.mutableStateOf<String?>(null)
    var importing by androidx.compose.runtime.mutableStateOf(false)
        private set
    var importProgress by androidx.compose.runtime.mutableStateOf(0f)
        private set
    var importLabel by androidx.compose.runtime.mutableStateOf("")
        private set
    var pending by androidx.compose.runtime.mutableStateOf<List<DraftQuestion>>(emptyList())
        private set
    var importErrors by androidx.compose.runtime.mutableStateOf<List<String>>(emptyList())
        private set
    var pendingBatch by androidx.compose.runtime.mutableStateOf<String?>(null)
        private set

    /** 连拍工作台：拍一张回到这里，可继续拍，最后一起识别 */
    var captureUris by androidx.compose.runtime.mutableStateOf<List<Uri>>(emptyList())
        private set

    var reviewQueue by androidx.compose.runtime.mutableStateOf<List<QuestionRow>>(emptyList())
        private set
    var reviewIndex by androidx.compose.runtime.mutableStateOf(0)
        private set
    var revealAnswer by androidx.compose.runtime.mutableStateOf(false)
        private set

    var busy by androidx.compose.runtime.mutableStateOf(false)
    var busyLabel by androidx.compose.runtime.mutableStateOf("")
    var lastExport by androidx.compose.runtime.mutableStateOf<File?>(null)
    var lastExportName by androidx.compose.runtime.mutableStateOf("")

    var updateInfo by androidx.compose.runtime.mutableStateOf<Updater.Info?>(null)
        private set
    var updateProgress by androidx.compose.runtime.mutableStateOf(-1)
        private set
    var checkingUpdate by androidx.compose.runtime.mutableStateOf(false)
        private set
    var updateReady by androidx.compose.runtime.mutableStateOf<File?>(null)
        private set

    init {
        refresh()
        val last = db.getSetting("update.lastCheck")?.toLongOrNull() ?: 0L
        if (System.currentTimeMillis() - last > 6 * 3600_000L) checkUpdate(silent = true)
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            val cats = db.categories()
            val st = db.stats()
            val ds = db.docs()
            val p = db.provider()
            withContext(Dispatchers.Main) {
                categories = cats; stats = st; docs = ds; provider = p
            }
        }
    }

    // ---------- navigation ----------

    fun go(next: Route) {
        if (next != route) { navForward = true; stack.add(route); route = next }
    }

    fun replace(next: Route) {
        if (route == next) return
        navForward = true
        stack.clear()
        route = next
    }

    fun back(): Boolean {
        if (stack.isNotEmpty()) {
            navForward = false
            route = stack.removeAt(stack.size - 1)
            return true
        }
        if (route != Route.Home) {
            navForward = false
            route = Route.Home
            return true
        }
        return false
    }

    fun home() { navForward = false; stack.clear(); route = Route.Home }

    // ---------- import ----------

    fun addCapture(uri: Uri) { captureUris = captureUris + uri }

    fun removeCapture(uri: Uri) { captureUris = captureUris - uri }

    fun clearCapture() { captureUris = emptyList() }

    fun importCapture() {
        val list = captureUris
        if (list.isEmpty()) return
        captureUris = emptyList()
        importUris(list, "camera")
    }

    fun importUris(uris: List<Uri>, source: String = "share") {
        if (uris.isEmpty() || importing) return
        viewModelScope.launch {
            importing = true
            importProgress = 0f
            importLabel = "准备中"
            importErrors = emptyList()
            pending = emptyList()
            route = Route.Import
            try {
                val out = importer.import(uris, source) { done, total, label ->
                    importProgress = done.toFloat() / total.coerceAtLeast(1)
                    importLabel = label
                }
                pending = out.drafts
                pendingBatch = out.batchId
                importErrors = out.errors
                importing = false
                if (pending.isEmpty()) {
                    toast = "没有可入库的题目"
                } else if (db.provider().ready()) {
                    autoClassify()
                }
            } catch (e: Exception) {
                importing = false
                importErrors = listOf(e.message ?: "导入失败")
                toast = "导入失败：${e.message}"
            }
        }
    }

    fun importSingle(uri: Uri, source: String = "gallery") = importUris(listOf(uri), source)

    fun autoClassify() {
        val list = pending
        if (list.isEmpty() || !provider.ready()) return
        viewModelScope.launch {
            busy = true; busyLabel = "AI 分类中"
            try {
                val map = ai.classify(provider, list, categories)
                pending = pending.map { d -> map[d.id]?.let { d.copy(categoryName = it) } ?: d }
            } catch (e: Exception) {
                toast = "自动分类失败，可手动选择"
            } finally {
                busy = false
            }
        }
    }

    fun updateDraft(updated: DraftQuestion) {
        pending = pending.map { if (it.id == updated.id) updated else it }
    }

    fun removeDraft(id: String) {
        pending = pending.filter { it.id != id }
    }

    fun confirmPending(only: List<DraftQuestion>? = null) {
        val list = only ?: pending
        if (list.isEmpty()) return
        viewModelScope.launch {
            busy = true; busyLabel = "正在入库"
            var saved = 0
            var skipped = 0
            withContext(Dispatchers.IO) {
                val seen = HashSet<String>()
                list.forEach { d ->
                    val key = d.stem.trim().take(80)
                    if (key.isNotBlank() && (!seen.add(key) || db.hasStem(key))) {
                        skipped++
                        return@forEach
                    }
                    val cat = if (d.categoryName.isBlank()) "未分类" else d.categoryName
                    val catId = db.ensureCategory(cat)
                    val subject = db.category(catId)?.subject.orEmpty()
                    db.insertQuestion(d, catId, pendingBatch, subject)
                    saved++
                }
            }
            pending = emptyList()
            pendingBatch = null
            busy = false
            refresh()
            home()
            toast = if (skipped > 0) "已入库 $saved 题 · 跳过 $skipped 道重复" else "已入库 $saved 题"
        }
    }

    fun addManual(imageUri: Uri) {
        viewModelScope.launch {
            importing = true
            importLabel = "保存图片"
            try {
                val img = importer.ingestOnly(imageUri)
                pending = listOf(
                    DraftQuestion(
                        imageId = img.id, imagePath = img.path,
                        type = "", stem = "", categoryName = "未分类"
                    )
                )
                pendingBatch = null
                importing = false
                route = Route.Import
            } catch (e: Exception) {
                importing = false
                toast = "导入失败：${e.message}"
            }
        }
    }

    // ---------- review ----------

    fun startReview() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = db.dueQuestions(40)
            withContext(Dispatchers.Main) {
                reviewQueue = list
                reviewIndex = 0
                revealAnswer = false
                route = Route.Review
            }
        }
    }

    fun startReviewFor(categoryId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val all = db.questions(categoryId)
            val list = all.filter { it.mastery < 4 }.ifEmpty { all }
            withContext(Dispatchers.Main) {
                reviewQueue = list
                reviewIndex = 0
                revealAnswer = false
                route = Route.Review
            }
        }
    }

    fun reveal() { revealAnswer = true }

    fun rate(rating: Int) {
        val q = reviewQueue.getOrNull(reviewIndex) ?: return
        viewModelScope.launch(Dispatchers.IO) {
            db.review(q.id, rating)
            withContext(Dispatchers.Main) {
                reviewIndex++
                revealAnswer = false
                if (reviewIndex >= reviewQueue.size) refresh()
            }
        }
    }

    // ---------- knowledge ----------

    fun generateKnowledge(category: CategoryRow) {
        if (!provider.ready()) { toast = "先在「我的 → AI 接入」配置模型"; return }
        viewModelScope.launch {
            busy = true; busyLabel = "AI 提炼知识点"
            try {
                val qs = withContext(Dispatchers.IO) { db.questions(category.id) }
                if (qs.isEmpty()) { toast = "这个分类还没有题目"; return@launch }
                val material = qs.take(20).joinToString("\n\n") { q ->
                    buildString {
                        append("题型：").append(q.type).append("\n")
                        append("题干：").append(q.stem).append("\n")
                        if (q.answer.isNotBlank()) append("答案：").append(q.answer).append("\n")
                        if (q.analysis.isNotBlank()) append("解析：").append(q.analysis).append("\n")
                        append("知识点：").append(q.tags)
                    }
                }
                val md = ai.knowledge(provider, material)
                val ids = JSONArray(qs.take(20).map { it.id }).toString()
                val docId = withContext(Dispatchers.IO) {
                    db.insertDoc(category.name.ifBlank { "知识点整理" }, category.id, ids, md)
                }
                refresh()
                go(Route.Doc(docId))
            } catch (e: Exception) {
                toast = "生成失败：${e.message}"
            } finally {
                busy = false
            }
        }
    }

    fun deleteDoc(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            db.deleteDoc(id); refresh()
            withContext(Dispatchers.Main) { home() }
        }
    }

    // ---------- pdf ----------

    suspend fun buildPdf(questions: List<QuestionRow>, title: String, opts: PdfExporter.Options): File =
        withContext(Dispatchers.IO) {
            val f = File(getApplication<Application>().cacheDir, "export/${safe(title)}_${System.currentTimeMillis()}.pdf")
            f.parentFile?.mkdirs()
            PdfExporter.export(com.zhuquan.tijian.MainActivity.current?.get() ?: getApplication(), questions, f, title, opts)
            f
        }

    suspend fun buildDocPdf(doc: DocRow): File = withContext(Dispatchers.IO) {
        val f = File(getApplication<Application>().cacheDir, "export/${safe(doc.title)}_${System.currentTimeMillis()}.pdf")
        f.parentFile?.mkdirs()
        PdfExporter.exportText(doc.title, doc.contentMd, f)
        f
    }

    fun saveTo(uri: Uri, file: File) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val ctx = getApplication<Application>()
                ctx.contentResolver.openOutputStream(uri)?.use { out ->
                    file.inputStream().use { it.copyTo(out) }
                }
                withContext(Dispatchers.Main) { toast = "已保存到文件" }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { toast = "保存失败：${e.message}" }
            }
        }
    }

    // ---------- provider ----------

    fun saveProvider(cfg: ProviderConfig) {
        db.saveProvider(cfg)
        provider = cfg
        toast = "已保存"
    }

    suspend fun testProvider(cfg: ProviderConfig): String = ai.test(cfg)

    // ---------- backup ----------

    fun exportBackup(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val ctx = getApplication<Application>()
                ctx.contentResolver.openOutputStream(uri)?.use { out ->
                    val n = com.zhuquan.tijian.data.Backup.export(ctx, db, out)
                    withContext(Dispatchers.Main) { toast = "备份完成（$n 个文件）" }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { toast = "备份失败：${e.message}" }
            }
        }
    }

    fun importBackup(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val ctx = getApplication<Application>()
                ctx.contentResolver.openInputStream(uri)?.use { input ->
                    com.zhuquan.tijian.data.Backup.import(ctx, db, input)
                }
                withContext(Dispatchers.Main) {
                    refresh()
                    home()
                    toast = "已恢复备份"
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { toast = "恢复失败：${e.message}" }
            }
        }
    }

    // ---------- update ----------
    fun checkUpdate(silent: Boolean = false) {
        if (checkingUpdate) return
        viewModelScope.launch {
            checkingUpdate = true
            try {
                val info = Updater.check(getApplication())
                if (info != null) updateInfo = info
                else if (!silent) toast = "已是最新版本（${Updater.currentName()}）"
            } catch (e: Exception) {
                if (!silent) toast = "检查更新失败：${e.message}"
            } finally {
                checkingUpdate = false
                db.putSetting("update.lastCheck", System.currentTimeMillis().toString())
            }
        }
    }

    fun downloadUpdate() {
        val info = updateInfo ?: return
        viewModelScope.launch {
            updateProgress = 0
            try {
                val file = Updater.download(getApplication(), info) { p -> updateProgress = p }
                updateReady = file
                updateProgress = 100
            } catch (e: Exception) {
                updateProgress = -1
                toast = "下载失败：${e.message}"
            }
        }
    }

    fun clearUpdate() {
        updateInfo = null; updateReady = null; updateProgress = -1
    }

    private fun safe(s: String) = s.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(40).ifBlank { "未命名" }
}

fun shareFile(ctx: Context, file: File) {
    val uri = androidx.core.content.FileProvider.getUriForFile(
        ctx, "${ctx.packageName}.fileprovider", file
    )
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = if (file.name.endsWith(".pdf")) "application/pdf" else "*/*"
        putExtra(android.content.Intent.EXTRA_STREAM, uri)
        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    ctx.startActivity(
        android.content.Intent.createChooser(intent, "分享").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}
