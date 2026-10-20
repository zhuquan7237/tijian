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
import com.zhuquan.tijian.data.parseKnowledgeTags
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

    var navigating = false
    fun go(next: Route) {
        if (navigating) return
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
        captureUris = captureUris + uri
    }

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
        viewModelScope.launch(Dispatchers.IO) {
            db.review(q.id, rating)
            withContext(Dispatchers.Main) {
                reviewIndex++
                revealAnswer = false
                if (reviewIndex >= reviewQueue.size) refresh()
            }
        }
    }

    var needsBusyFor by androidx.compose.runtime.mutableStateOf<String?>(null)
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
    var chatError by androidx.compose.runtime.mutableStateOf<String?>(null)
        private set
    /** 最近一次打开过的题目，用于离开详情页后悬浮球仍能追问同一题。 */
    var lastQuestionId by androidx.compose.runtime.mutableStateOf<String?>(null)

    /** 题目详情的内存缓存：返回/重进同一题时直接出内容，不再闪“正在读取题目…”。 */
    private val questionCache = HashMap<String, QuestionRow>()
    fun cachedQuestion(id: String): QuestionRow? = questionCache[id]
    fun cacheQuestion(q: QuestionRow) { questionCache[q.id] = q }

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
            val resolved = id ?: withContext(Dispatchers.IO) { db.questions().firstOrNull()?.id }
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
        chatError = null
        viewModelScope.launch {
            try {
                val contextText = withContext(Dispatchers.IO) { buildQuestionContext(q) }
                val answer = ai.ask(provider, contextText, history, prompt, quote)
                withContext(Dispatchers.IO) {
                    db.addChatMessage(q.id, "user", prompt, quote)
                    db.addChatMessage(q.id, "assistant", answer)
                }
                chatMessages = chatMessages + ("assistant" to answer)
            } catch (e: Exception) {
                chatError = e.message ?: "回答失败"
            } finally {
                chatBusy = false
            }
        }
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
     */
    private fun buildQuestionContext(q: QuestionRow): String {
        val all = db.questions()
        val tagScores = LinkedHashMap<String, MutableList<Int>>()
        all.forEach { item ->
            parseKnowledgeTags(item.tags).forEach { tag ->
                tagScores.getOrPut(tag) { mutableListOf() }.add(item.mastery)
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
        PdfExporter.exportText(doc.title, doc.contentMd, f)
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
