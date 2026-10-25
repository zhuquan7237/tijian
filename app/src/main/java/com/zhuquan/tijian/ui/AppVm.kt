package com.zhuquan.tijian.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.ui.unit.IntOffset
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
import com.zhuquan.tijian.data.parseKnowledgeTags
import com.zhuquan.tijian.data.Stats
import com.zhuquan.tijian.data.newId
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
    data class Subject(val name: String) : Route
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
    private var importJob: kotlinx.coroutines.Job? = null

    /** 连拍工作台：拍一张回到这里，可继续拍，最后一起识别。
     *  文件落在 filesDir/capture_staging 并持久化清单，进程被杀也能接着用。 */
    var captureUris by androidx.compose.runtime.mutableStateOf<List<Uri>>(emptyList())
        private set

    private val stagingDir = File(app.filesDir, "capture_staging").apply { mkdirs() }
    private val manifestFile get() = File(stagingDir, "pending.json")

    private fun saveCaptureManifest() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                manifestFile.writeText(org.json.JSONArray(captureUris.map { it.toString() }).toString())
            }
        }
    }

    private fun restoreCaptureManifest() {
        viewModelScope.launch(Dispatchers.IO) {
            val uris = runCatching {
                val arr = org.json.JSONArray(manifestFile.readText())
                (0 until arr.length()).mapNotNull { i ->
                    val s = arr.optString(i)
                    val f = File(s.removePrefix("file://"))
                    if (s.startsWith("file:") && f.exists()) Uri.fromFile(f) else null
                }
            }.getOrDefault(emptyList())
            withContext(Dispatchers.Main) {
                if (captureUris.isEmpty() && uris.isNotEmpty()) captureUris = uris
            }
        }
    }

    /** 相册/分享来的 uri 统一拷进工作台目录，避免 cache 被系统清理后引用悬空。 */
    private suspend fun stageUri(uri: Uri): Uri = withContext(Dispatchers.IO) {
        val target = File(stagingDir, "pick_${System.currentTimeMillis()}_${newId().take(6)}.img")
        val ok = runCatching {
            getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { input.copyTo(it) }
            } != null
        }.getOrDefault(false)
        if (ok) Uri.fromFile(target) else uri
    }

    /** 清掉工作台目录里的暂存文件和清单（导入完成或用户清空时）。 */
    private fun wipeStaging() {
        viewModelScope.launch(Dispatchers.IO) {
            stagingDir.listFiles()?.forEach { it.delete() }
        }
    }

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
        restoreCaptureManifest()
        val last = db.getSetting("update.lastCheck")?.toLongOrNull() ?: 0L
        if (System.currentTimeMillis() - last > 6 * 3600_000L) checkUpdate(silent = true)
    }

    var dataRevision by androidx.compose.runtime.mutableIntStateOf(0)
        private set

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            val cats = db.categories()
            val st = db.stats()
            val ds = db.docs()
            val p = db.provider()
            withContext(Dispatchers.Main) {
                categories = cats; stats = st; docs = ds; provider = p; dataRevision++
            }
        }
    }

    // ---------- navigation ----------

    private val readyPages = androidx.compose.runtime.mutableStateMapOf<Route, Boolean>()
    fun pageReady(page: Route) { readyPages[page] = true }
    // 详情页不再等 WebView 排版完成才显示：先出页面骨架，正文随后填入，
    // 否则点进题目会有明显的「卡住一下」。
    fun canDisplay(page: Route): Boolean = true
    fun forgetPage(page: Route) { readyPages.remove(page) }

    fun navigationDepth(): Int = stack.size

    fun retainsPage(page: Route): Boolean = page == route || page in stack ||
        page is Route.Home || page is Route.Library || page is Route.Settings

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

    suspend fun saveQuestionImage(questionId: String, sourceId: String?, uri: Uri): QuestionRow = withContext(Dispatchers.IO) {
        val image = importer.ingestOnly(uri)
        if (sourceId != null) db.linkOriginal(image.id, sourceId)
        db.updateQuestionImage(questionId, image.id)
        db.question(questionId) ?: error("题目不存在")
    }

    suspend fun replaceCapture(source: Uri, edited: Uri) {
        val copy = withContext(Dispatchers.IO) {
            val original = importer.ingestOnly(source)
            importer.ingestOnly(edited).also { db.linkOriginal(it.id, original.id) }
        }
        captureUris = captureUris.map { if (it == source) Uri.fromFile(File(copy.path)) else it }
    }

    fun addCapture(uri: Uri) {
        if (uri in captureUris) return
        if (captureUris.size >= 9) { toast = "一次最多导入 9 张图片"; return }
        // 先同步入列（去重与上限都是同步语义），content:// 的拷贝后台补做
        captureUris = captureUris + uri
        saveCaptureManifest()
        if (uri.scheme != "file") {
            viewModelScope.launch {
                val staged = stageUri(uri)
                if (staged != uri) {
                    captureUris = captureUris.map { if (it == uri) staged else it }
                    saveCaptureManifest()
                }
            }
        }
    }

    fun removeCapture(uri: Uri) {
        captureUris = captureUris - uri
        if (uri.scheme == "file") {
            runCatching { uri.path?.let { File(it).takeIf { f -> f.parentFile == stagingDir }?.delete() } }
        }
        if (captureUris.isEmpty()) wipeStaging() else saveCaptureManifest()
    }

    fun clearCapture() {
        captureUris = emptyList()
        wipeStaging()
    }

    fun importCapture() {
        val list = captureUris
        if (list.isEmpty() || importing || busy) return
        importUris(list, "camera")
    }

    /** 识别中途停止：取消协程与在途 HTTP，已入库的图片保留。 */
    fun cancelImport() {
        if (!importing) return
        importJob?.cancel()
        importJob = null
        ai.cancelActive()
        importing = false
        importLabel = ""
        importProgress = 0f
        toast = "已停止导入，可稍后继续"
    }

    fun importUris(uris: List<Uri>, source: String = "share") {
        if (uris.isEmpty() || importing || busy) return
        if (pending.isNotEmpty()) {
            route = Route.Import
            toast = "请先确认或移除上一批题目，再导入新图片"
            return
        }
        importJob = viewModelScope.launch {
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
                if (out.errors.isEmpty() && out.drafts.isNotEmpty() && source == "camera") {
                    // 连拍批次已全部识别完：工作台清空，暂存文件一并清掉
                    captureUris = emptyList()
                    wipeStaging()
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
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                importing = false
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
        if (list.isEmpty() || !provider.ready() || busy) return
        busy = true; busyLabel = "AI 分类中"
        viewModelScope.launch {
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
        if (busy || importing) return
        val requestedIds = only?.map { it.id }?.toSet()
        val list = pending.filter { requestedIds == null || it.id in requestedIds }
        if (list.isEmpty()) return
        busy = true
        busyLabel = "正在入库"
        viewModelScope.launch {
            try {
                val (saved, skipped) = withContext(Dispatchers.IO) { db.saveDrafts(list, pendingBatch) }
                val completed = list.map { it.id }.toSet()
                pending = pending.filterNot { it.id in completed }
                if (pending.isEmpty()) { pendingBatch = null; home() }
                refresh()
                toast = if (skipped > 0) "已入库 $saved 题 · 跳过 $skipped 道重复" else "已入库 $saved 题"
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                toast = "入库失败，待确认题目已保留，请重试"
            } finally {
                busy = false
                busyLabel = ""
            }
        }
    }

    fun addManual(imageUri: Uri) {
        if (importing || busy) return
        if (pending.isNotEmpty()) {
            route = Route.Import
            toast = "请先处理上一批待确认题目"
            return
        }
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

    fun startReview(includeAll: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            val list = if (includeAll) db.questions().take(40) else db.dueQuestions(40)
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
        // 先同步推进队列再落库：评分按钮立刻失效，连点不会对同一题记两次
        reviewIndex++
        revealAnswer = false
        viewModelScope.launch(Dispatchers.IO) {
            db.review(q.id, rating)
            if (reviewIndex >= reviewQueue.size) withContext(Dispatchers.Main) { refresh() }
        }
    }

    var needsBusyFor by androidx.compose.runtime.mutableStateOf<String?>(null)
    var solutionBusyFor by androidx.compose.runtime.mutableStateOf<String?>(null)
        private set

    /** 旧题 / 模型漏字段时，补全「需要掌握」的公式、概念、方法。 */
    fun fillNeeds(id: String) {
        if (needsBusyFor != null) return
        if (!provider.ready()) { toast = "先在「我的 → AI 接入」配置模型"; return }
        needsBusyFor = id
        viewModelScope.launch {
            try {
                val q = withContext(Dispatchers.IO) { db.question(id) } ?: return@launch
                val (f, c, m) = ai.analyzeNeeds(provider, q)
                if (f.isEmpty() && c.isEmpty() && m.isEmpty()) { toast = "模型没有给出可用结果"; return@launch }
                withContext(Dispatchers.IO) {
                    db.updateQuestionNeeds(id, org.json.JSONArray(f).toString(), org.json.JSONArray(c).toString(), org.json.JSONArray(m).toString())
                }
                refresh()
                toast = "已补全这题的知识点"
            } catch (e: Exception) {
                toast = "补全失败：${e.message}"
            } finally {
                needsBusyFor = null
            }
        }
    }

    /**
     * 旧题/识别时漏掉答案或解析的补全入口。走和导入时同一套 fillSolutions，
     * 只填空字段，不覆盖已有的答案。
     */
    fun fillSolution(id: String) {
        if (solutionBusyFor != null) return
        if (!provider.ready()) { toast = "先在「我的 → AI 接入」配置模型"; return }
        solutionBusyFor = id
        viewModelScope.launch {
            try {
                val q = withContext(Dispatchers.IO) { db.question(id) } ?: return@launch
                val seed = com.zhuquan.tijian.ai.ParsedQuestion(
                    type = q.type, stem = q.stem, options = parseOptions(q.options),
                    answer = q.answer, analysis = q.analysis,
                    needFormulas = parseKnowledgeTags(q.needFormulas),
                    needConcepts = parseKnowledgeTags(q.needConcepts),
                    needMethods = parseKnowledgeTags(q.needMethods),
                )
                val fixed = ai.fillSolutions(provider, listOf(seed)).firstOrNull() ?: return@launch
                if (fixed.answer.isBlank() && fixed.analysis.isBlank()) {
                    toast = "模型没有给出可用结果，检查题目图片是否清晰"
                    return@launch
                }
                withContext(Dispatchers.IO) {
                    db.updateQuestionSolution(id, fixed.answer.ifBlank { q.answer }, fixed.analysis.ifBlank { q.analysis })
                    if (fixed.needFormulas.isNotEmpty() || fixed.needConcepts.isNotEmpty() || fixed.needMethods.isNotEmpty()) {
                        db.updateQuestionNeeds(
                            id,
                            org.json.JSONArray(fixed.needFormulas).toString(),
                            org.json.JSONArray(fixed.needConcepts).toString(),
                            org.json.JSONArray(fixed.needMethods).toString()
                        )
                    }
                }
                refresh()
                toast = "已补全答案与解析"
            } catch (e: Exception) {
                toast = "补全失败：${e.message}"
            } finally {
                solutionBusyFor = null
            }
        }
    }

    // ---------- knowledge ----------

    fun generateKnowledge(categories: List<CategoryRow>) {
        val selected = categories.distinctBy { it.id }
        if (selected.isEmpty()) { toast = "先选择至少一个分类"; return }
        if (!provider.ready()) { toast = "先在「我的 → AI 接入」配置模型"; return }
        viewModelScope.launch {
            busy = true; busyLabel = "AI 提炼知识点"
            try {
                val qs = withContext(Dispatchers.IO) {
                    selected.flatMap { db.questions(it.id, "all") }.distinctBy { it.id }
                }
                if (qs.isEmpty()) { toast = "所选分类还没有题目"; return@launch }
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
                    val title = if (selected.size == 1) selected.first().name else selected.joinToString(" · ") { it.name }
                    db.insertDoc(title.ifBlank { "知识点整理" }, selected.first().id, ids, md)
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

    /** 重新选分类，用新范围重新生成同一篇知识文档（覆盖原内容）。 */
    fun regenerateKnowledge(docId: String, categories: List<CategoryRow>) {
        val selected = categories.distinctBy { it.id }
        if (selected.isEmpty()) { toast = "先选择至少一个分类"; return }
        if (!provider.ready()) { toast = "先在「我的 → AI 接入」配置模型"; return }
        viewModelScope.launch {
            busy = true; busyLabel = "重新提炼知识点"
            try {
                val qs = withContext(Dispatchers.IO) {
                    selected.flatMap { db.questions(it.id, "all") }.distinctBy { it.id }
                }
                if (qs.isEmpty()) { toast = "所选分类还没有题目"; return@launch }
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
                val title = if (selected.size == 1) selected.first().name else selected.joinToString(" · ") { it.name }
                withContext(Dispatchers.IO) {
                    db.updateDocSources(docId, selected.first().id, ids, title.ifBlank { "知识点整理" }, md)
                }
                refresh()
                toast = "已按 ${selected.size} 个分类重新生成"
                go(Route.Doc(docId))
            } catch (e: Exception) {
                toast = "重新生成失败：${e.message}"
            } finally {
                busy = false; busyLabel = ""
            }
        }
    }

    fun updateDoc(id: String, title: String, content: String) {
        viewModelScope.launch(Dispatchers.IO) {
            db.updateDoc(id, title, content)
            refresh()
            withContext(Dispatchers.Main) { toast = "已保存修改" }
        }
    }

    /** 多选批量删除（软删除），成功后刷新列表。 */
    /**
     * 页面里的数据库写入统一走这里：在 IO 线程执行，不阻塞点击后的那一帧；
     * 挂在 viewModelScope 上，页面/对话框关闭也不会把写入取消。完成后刷新统计。
     */
    fun write(block: (com.zhuquan.tijian.data.Db) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val error = runCatching { block(db) }.exceptionOrNull()
            withContext(Dispatchers.Main) {
                if (error != null) toast = "保存失败：${error.message ?: error.javaClass.simpleName}"
                refresh()
            }
        }
    }

    fun deleteQuestions(ids: List<String>) {
        if (ids.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            ids.forEach { db.deleteQuestion(it) }
            withContext(Dispatchers.Main) { refresh() }
        }
    }

    fun deleteDoc(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            db.deleteDoc(id); refresh()
            withContext(Dispatchers.Main) { home() }
        }
    }

    // ---------- 题目追问 ----------

    var chatOpen by androidx.compose.runtime.mutableStateOf(false)
        private set
    var chatQuestion by androidx.compose.runtime.mutableStateOf<QuestionRow?>(null)
        private set
    var chatMessages by androidx.compose.runtime.mutableStateOf<List<Pair<String, String>>>(emptyList())
        private set
    var chatQuote by androidx.compose.runtime.mutableStateOf("")
        private set
    var chatBusy by androidx.compose.runtime.mutableStateOf(false)
        private set
    /** 上一次回答是否被模型长度限制截断；截断后界面给「继续」。 */
    var chatTruncated by androidx.compose.runtime.mutableStateOf(false)
        private set
    var chatError by androidx.compose.runtime.mutableStateOf<String?>(null)
        private set
    private var chatJob: kotlinx.coroutines.Job? = null
    /** 最近一次打开过的题目，用于离开详情页后悬浮球仍能追问同一题。 */
    var lastQuestionId by androidx.compose.runtime.mutableStateOf<String?>(null)

    /** 悬浮球拖动后的位置（px），跨页面保留；null 表示仍在默认右下角。 */
    var fabPos by androidx.compose.runtime.mutableStateOf<IntOffset?>(null)

    /** 题目详情的内存缓存：返回/重进同一题时直接出内容，不再闪“正在读取题目…”。
     *  按 LRU 限量，题目全文不小，不能无限常驻。 */
    private val questionCache = object : LinkedHashMap<String, QuestionRow>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, QuestionRow>?): Boolean =
            size > 96
    }
    fun cachedQuestion(id: String): QuestionRow? = synchronized(questionCache) { questionCache[id] }
    fun cacheQuestion(q: QuestionRow) { synchronized(questionCache) { questionCache[q.id] = q } }

    fun rememberQuestion(id: String) { lastQuestionId = id }

    fun openChat(quote: String = "") {
        val id = when (val r = route) {
            is Route.Detail -> r.id
            is Route.Review -> reviewQueue.getOrNull(reviewIndex)?.id
            else -> lastQuestionId
        }
        // 换题时先把上一题的气泡清掉，避免短暂串题；历史仍按 question_id 各存各的
        chatMessages = emptyList()
        chatError = null
        chatQuote = quote
        viewModelScope.launch {
            // 没有打开题目时，用最近导入的一道题作为上下文，保证悬浮球随时可用
            val resolved = id ?: withContext(Dispatchers.IO) { db.recentQuestions(1).firstOrNull()?.id }
            if (resolved == null) { toast = "还没有错题可以追问"; return@launch }
            val q = withContext(Dispatchers.IO) { db.question(resolved) }
            if (q == null) { toast = "题目不存在"; return@launch }
            lastQuestionId = resolved
            val history = withContext(Dispatchers.IO) { db.chatMessages(resolved) }
            chatQuestion = q
            chatMessages = history
            chatError = null
            chatOpen = true
        }
    }

    fun closeChat() { chatOpen = false }

    fun updateChatQuote(text: String) { chatQuote = text }

    fun sendChat(text: String) {
        val q = chatQuestion ?: return
        val prompt = text.trim()
        if (prompt.isBlank() || chatBusy) return
        val quote = chatQuote
        val history = chatMessages
        chatMessages = chatMessages + ("user" to prompt)
        chatQuote = ""
        chatBusy = true
        chatTruncated = false
        chatError = null
        chatJob = viewModelScope.launch {
            try {
                val contextText = withContext(Dispatchers.IO) { buildQuestionContext(q) }
                val (answer, truncated) = ai.ask(provider, contextText, history, prompt, quote)
                withContext(Dispatchers.IO) {
                    db.addChatMessage(q.id, "user", prompt, quote)
                    db.addChatMessage(q.id, "assistant", answer)
                }
                chatMessages = chatMessages + ("assistant" to answer)
                chatTruncated = truncated
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                chatError = "已停止生成"
            } catch (e: Exception) {
                chatError = e.message ?: "回答失败"
            } finally {
                chatBusy = false
            }
        }
    }

    /** 停止当前追问：取消 HTTP 请求，不再把半截回答写进历史。 */
    fun stopChat() {
        ai.cancelActive()
        chatJob?.cancel()
        chatJob = null
        chatBusy = false
        chatTruncated = false
    }

    /** 回答被长度限制截断后，从断点接着写，不重复已写内容。 */
    fun continueChat() {
        if (chatBusy) return
        chatTruncated = false
        sendChat("请从你上次回答被截断的地方接着写，不要重复已经写过的内容，也不要重新开头。")
    }

    fun clearChat() {
        val q = chatQuestion ?: return
        viewModelScope.launch(Dispatchers.IO) {
            db.clearChat(q.id)
            withContext(Dispatchers.Main) { chatMessages = emptyList() }
        }
    }

    /**
     * 追问上下文：题目本身 + 需要掌握的知识点 + 本题掌握度 +
     * 同一知识点下其它题的掌握度（用来说明“你缺的是哪一块”）。
     * 标签统计只取 tags+mastery 两列，不整题拉取。
     */
    private fun buildQuestionContext(q: QuestionRow): String {
        val tagScores = LinkedHashMap<String, MutableList<Int>>()
        db.tagMasteryPairs().forEach { (tags, mastery) ->
            parseKnowledgeTags(tags).forEach { tag ->
                tagScores.getOrPut(tag) { mutableListOf() }.add(mastery)
            }
        }
        val weak = tagScores.entries
            .filter { entry -> entry.value.average() < 3.0 }
            .sortedBy { it.value.average() }
            .take(8)
            .joinToString("；") { (tag, scores) ->
                "${tag}（${scores.size} 题，平均 ${"%.1f".format(scores.average())}/5）"
            }
        return buildString {
            appendLine("题型：${q.type.ifBlank { "题目" }}")
            appendLine("分类：${q.categoryName}")
            appendLine("题干：${q.stem}")
            val opts = parseOptions(q.options)
            if (opts.isNotEmpty()) appendLine("选项：${opts.joinToString(" / ")}")
            if (q.answer.isNotBlank()) appendLine("答案：${q.answer}")
            if (q.analysis.isNotBlank()) appendLine("解析：${q.analysis}")
            val formulas = parseKnowledgeTags(q.needFormulas)
            val concepts = parseKnowledgeTags(q.needConcepts)
            val methods = parseKnowledgeTags(q.needMethods)
            if (formulas.isNotEmpty() || concepts.isNotEmpty() || methods.isNotEmpty()) {
                appendLine("要做出这道题需要掌握：")
                if (formulas.isNotEmpty()) appendLine("  公式：${formulas.joinToString("；")}")
                if (concepts.isNotEmpty()) appendLine("  概念：${concepts.joinToString("；")}")
                if (methods.isNotEmpty()) appendLine("  方法：${methods.joinToString("；")}")
            }
            appendLine("本题当前掌握度：${q.mastery}/5（复习 ${q.reviewCount} 次）")
            val tags = parseKnowledgeTags(q.tags)
            if (tags.isNotEmpty()) appendLine("本题知识点标签：${tags.joinToString("、")}")
            if (weak.isNotBlank()) appendLine("学生目前偏弱的知识点：$weak")
            if (q.note.isNotBlank()) appendLine("学生自己的笔记：${q.note}")
        }
    }

    // ---------- pdf ----------

    suspend fun withBusy(label: String, action: suspend () -> Unit) {
        if (busy) return
        busy = true; busyLabel = label
        try { action() }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (failure: Exception) { toast = failure.message ?: "操作失败，请重试" }
        finally { busy = false; busyLabel = "" }
    }

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
        PdfExporter.exportText(getApplication(), doc.title, doc.contentMd, f)
        f
    }

    fun saveTo(uri: Uri, file: File) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val ctx = getApplication<Application>()
                (ctx.contentResolver.openOutputStream(uri) ?: error("无法写入所选文件")).use { out ->
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
        viewModelScope.launch(Dispatchers.IO) {
            db.saveProvider(cfg)
            withContext(Dispatchers.Main) {
                provider = cfg
                toast = "已保存"
            }
        }
    }

    suspend fun testProvider(cfg: ProviderConfig): String = ai.test(cfg)

    // ---------- backup ----------

    fun exportBackup(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val ctx = getApplication<Application>()
                (ctx.contentResolver.openOutputStream(uri) ?: error("无法写入所选文件")).use { out ->
                    val n = com.zhuquan.tijian.data.Backup.export(ctx, db, out)
                    withContext(Dispatchers.Main) { toast = "备份完成（$n 个文件）" }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { toast = "备份失败：${e.message}" }
            }
        }
    }

    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            toast = "恢复完成，应用即将重启"
            withContext(Dispatchers.IO) {
                try {
                    val ctx = getApplication<Application>()
                    ctx.contentResolver.openInputStream(uri)?.use { input ->
                        com.zhuquan.tijian.data.Backup.import(ctx, db, input)
                    }
                } catch (e: Exception) {
                    // Backup.import 成功路径会直接重启进程；走到这里说明恢复失败
                    withContext(Dispatchers.Main) { toast = "恢复失败：${e.message}" }
                }
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
                withContext(Dispatchers.IO) {
                    db.putSetting("update.lastCheck", System.currentTimeMillis().toString())
                }
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
