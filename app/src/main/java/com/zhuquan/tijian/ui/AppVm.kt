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
import com.zhuquan.tijian.data.ReviewSessionState
import com.zhuquan.tijian.data.decodeReviewSession
import com.zhuquan.tijian.data.encodeReviewSession
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
    data object Trash : Route
    data object Tidy : Route
    data object Print : Route
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
    var todayQueue by androidx.compose.runtime.mutableStateOf<List<QuestionRow>>(emptyList())
        private set
    var dueOverflow by androidx.compose.runtime.mutableIntStateOf(0)
        private set
    var dailyCap by androidx.compose.runtime.mutableIntStateOf(8)
        private set
    var remindEnabled by androidx.compose.runtime.mutableStateOf(true)
        private set
    var remindHour by androidx.compose.runtime.mutableIntStateOf(20)
        private set
    var remindMinute by androidx.compose.runtime.mutableIntStateOf(0)
        private set
    var docs by androidx.compose.runtime.mutableStateOf<List<DocRow>>(emptyList())
        private set
    var provider by androidx.compose.runtime.mutableStateOf(ProviderConfig())
        private set

    var toast by androidx.compose.runtime.mutableStateOf<String?>(null)

    /** 首页「其他方式」底部面板。任意页面都能打开，不必各自再做一套入口。 */
    var importSheet by androidx.compose.runtime.mutableStateOf(false)

    /** 刚入库的题目：详情页用来串「看解析 → 追问 → 下一题」。 */
    var freshIds by androidx.compose.runtime.mutableStateOf<List<String>>(emptyList())
        private set

    private var armCamera = false
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

    /** 上一次导入里识别失败的原图：只重跑失败的那几张，已完成的不动。 */
    var lastFailedUris by androidx.compose.runtime.mutableStateOf<List<Uri>>(emptyList())
        private set

    /** 待确认草稿自动落盘：进程被系统回收后，校对进度照常恢复。 */
    private val draftsFile get() = com.zhuquan.tijian.App.draftsFile(getApplication<Application>().filesDir)

    /** 上次崩溃的日志；非空说明上一进程异常退出，设置页可查看。 */
    var lastCrashReport by androidx.compose.runtime.mutableStateOf<String?>(null)
        private set

    /**
     * 崩溃后隔离出的待确认草稿：如果上一进程是在识别/校对页闪退的，
     * 原样恢复很可能在同一个页面再崩一次（「一点导入就闪退」的根源）。
     * 启动时把草稿移出恢复路径，由用户在首页明确选择恢复或丢弃。
     */
    var quarantinedDrafts by androidx.compose.runtime.mutableStateOf<List<DraftQuestion>>(emptyList())
        private set
    private val quarantineFile get() = File(getApplication<Application>().filesDir, "import_drafts.quarantine.json")

    fun restoreQuarantinedDrafts() {
        val list = quarantinedDrafts
        quarantinedDrafts = emptyList()
        quarantineFile.delete()
        if (list.isNotEmpty() && pending.isEmpty()) {
            pending = list
            pendingBatch = null
            saveDraftState()
            toast = "已恢复 ${list.size} 道待确认题目，请检查后入库"
        }
    }

    fun discardQuarantinedDrafts() {
        quarantinedDrafts = emptyList()
        quarantineFile.delete()
        toast = "已丢弃隔离的待确认题目（原图仍保留）"
    }

    private fun saveDraftState() {
        val snapshot = pending
        val batch = pendingBatch
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                if (snapshot.isEmpty()) {
                    draftsFile.delete()
                } else {
                    val root = org.json.JSONObject()
                    root.put("batch", batch ?: "")
                    root.put("drafts", org.json.JSONArray().apply { snapshot.forEach { put(it.toJson()) } })
                    draftsFile.writeText(root.toString())
                }
            }
        }
    }

    private fun restoreDrafts() {
        // 上个进程崩过：把草稿隔离，不再自动回到校对页，避免「一点导入就闪退」的循环
        val crashed = com.zhuquan.tijian.App.lastCrashReport(getApplication<Application>().filesDir)
        viewModelScope.launch(Dispatchers.IO) {
            var quarantined: List<DraftQuestion>? = null
            if (crashed != null && draftsFile.exists()) {
                runCatching {
                    val text = draftsFile.readText()
                    quarantineFile.writeText(text)
                    draftsFile.delete()
                    val root = org.json.JSONObject(text)
                    val arr = root.optJSONArray("drafts")
                    if (arr != null && arr.length() > 0) {
                        quarantined = (0 until arr.length()).mapNotNull { i ->
                            arr.optJSONObject(i)?.let { runCatching { DraftQuestion.fromJson(it) }.getOrNull() }
                        }
                    }
                }
            }
            val restored = runCatching {
                val root = org.json.JSONObject(draftsFile.readText())
                val arr = root.optJSONArray("drafts") ?: return@runCatching null
                val list = ArrayList<DraftQuestion>()
                for (i in 0 until arr.length()) {
                    runCatching { arr.optJSONObject(i)?.let { DraftQuestion.fromJson(it) } }
                        .getOrNull()?.let { list.add(it) }
                }
                // 图片文件可能已被系统清理；丢了图的草稿仍保留文字部分
                val cleaned = list.map { d ->
                    if (d.imagePath != null && !File(d.imagePath).exists()) d.copy(imagePath = null, imageId = null) else d
                }
                cleaned to root.optString("batch").takeIf { it.isNotBlank() }
            }.getOrNull()
            withContext(Dispatchers.Main) {
                lastCrashReport = crashed
                if (quarantined != null) quarantinedDrafts = quarantined!!
                if (pending.isEmpty() && restored != null && restored.first.isNotEmpty()) {
                    pending = restored.first
                    pendingBatch = restored.second
                }
            }
            // 崩溃报告只提示一次：读完即清，避免之后每次启动都当成新崩溃
            if (crashed != null) com.zhuquan.tijian.App.clearCrashReport(getApplication<Application>().filesDir)
        }
    }

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

    /** 暂存前的原始 uri → 暂存后的文件 uri。条目被暂存副本替换后，
     *  replaceCapture 靠它找回当前条目，否则编辑结果替换不到、裁剪看起来像没生效。 */
    private val stagedByOriginal = HashMap<Uri, Uri>()

    /** 进行中的暂存任务；replaceCapture 先等它结束，避免和条目替换赛跑。 */
    private val stagingJobs = HashMap<Uri, kotlinx.coroutines.Job>()

    /** 清掉工作台目录里的暂存文件和清单（导入完成或用户清空时）。 */
    private fun wipeStaging() {
        viewModelScope.launch(Dispatchers.IO) {
            stagingDir.listFiles()?.forEach { it.delete() }
        }
    }

    var busy by androidx.compose.runtime.mutableStateOf(false)
    var busyLabel by androidx.compose.runtime.mutableStateOf("")
    var lastExport by androidx.compose.runtime.mutableStateOf<File?>(null)
    var lastExportName by androidx.compose.runtime.mutableStateOf("")

    /** 常用提供商档案（init 里的 IO 协程会写它，声明必须先于 init）。 */
    var profiles by androidx.compose.runtime.mutableStateOf<List<com.zhuquan.tijian.data.ProviderProfile>>(emptyList())
        private set

    var updateInfo by androidx.compose.runtime.mutableStateOf<Updater.Info?>(null)
        private set
    var updateProgress by androidx.compose.runtime.mutableStateOf(-1)
        private set
    var checkingUpdate by androidx.compose.runtime.mutableStateOf(false)
        private set
    var updateReady by androidx.compose.runtime.mutableStateOf<File?>(null)
        private set

    /** 阅读字号缩放：1f 标准 / 1.1f 大 / 1.2f 特大（题干、解析、复习页）。 */
    var readingScaleValue by androidx.compose.runtime.mutableFloatStateOf(1f)
        private set

    /** 配置 AI 后要回到的导入任务（Capture 工作台或 Import 校对页）。 */
    var returnAfterProvider by androidx.compose.runtime.mutableStateOf<Route?>(null)

    /** 删除后的撤销条：文案 + 恢复动作，几秒后自动消失。 */
    var undoMsg by androidx.compose.runtime.mutableStateOf<String?>(null)
        private set
    private var undoAction: (() -> Unit)? = null
    private var undoJob: kotlinx.coroutines.Job? = null

    fun showUndo(msg: String, action: () -> Unit) {
        undoMsg = msg
        undoAction = action
        undoJob?.cancel()
        undoJob = viewModelScope.launch {
            kotlinx.coroutines.delay(6000)
            if (undoMsg == msg) { undoMsg = null; undoAction = null }
        }
    }

    fun runUndo() {
        undoAction?.invoke()
        undoJob?.cancel()
        undoMsg = null
        undoAction = null
    }

    // ---------- 回收站 ----------

    var trashCount by androidx.compose.runtime.mutableIntStateOf(0)
        private set

    fun refreshTrash() {
        viewModelScope.launch(Dispatchers.IO) { trashCount = db.trashCount() }
    }

    fun restoreQuestions(ids: List<String>) {
        if (ids.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            ids.forEach { db.restoreQuestion(it) }
            withContext(Dispatchers.Main) { refresh(); refreshTrash() }
        }
    }

    fun purgeQuestions(ids: List<String>) {
        if (ids.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            ids.forEach { db.purgeQuestion(it) }
            withContext(Dispatchers.Main) { refresh(); refreshTrash() }
        }
    }

    init {
        refresh()
        restoreCaptureManifest()
        restoreDrafts()
        // 外观偏好：0 跟随系统 / 1 浅色 / 2 深色
        tjAppearance.intValue = db.getSetting("appearance")?.toIntOrNull() ?: 0
        readingScaleValue = db.getSetting("readingScale")?.toFloatOrNull() ?: 1f
        viewModelScope.launch(Dispatchers.IO) { profiles = db.providerProfiles() }
        val last = db.getSetting("update.lastCheck")?.toLongOrNull() ?: 0L
        if (System.currentTimeMillis() - last > 6 * 3600_000L) checkUpdate(silent = true)
    }

    fun setAppearance(mode: Int) {
        tjAppearance.intValue = mode.coerceIn(0, 2)
        viewModelScope.launch(Dispatchers.IO) {
            db.putSetting("appearance", mode.toString())
        }
    }

    fun setReadingScale(scale: Float) {
        readingScaleValue = scale.coerceIn(0.9f, 1.4f)
        viewModelScope.launch(Dispatchers.IO) { db.putSetting("readingScale", readingScaleValue.toString()) }
    }

    /** 分类页视图：0 学习列表（默认，看题干）/ 1 整理表格（批量改题号、导出）。 */
    var categoryView by androidx.compose.runtime.mutableIntStateOf(
        db.getSetting("category.view")?.toIntOrNull() ?: 0
    )
        private set

    fun chooseCategoryView(v: Int) {
        categoryView = v.coerceIn(0, 1)
        viewModelScope.launch(Dispatchers.IO) { db.putSetting("category.view", categoryView.toString()) }
    }

    /** 详情页折叠偏好（全局记住用户选择）：答案解析 / 笔记 默认展开。 */
    var answerExpanded by androidx.compose.runtime.mutableStateOf(db.getSetting("ui.answerExpanded") != "0")
        private set
    var noteExpanded by androidx.compose.runtime.mutableStateOf(db.getSetting("ui.noteExpanded") != "0")
        private set

    fun chooseAnswerExpanded(v: Boolean) {
        answerExpanded = v
        viewModelScope.launch(Dispatchers.IO) { db.putSetting("ui.answerExpanded", if (v) "1" else "0") }
    }

    fun chooseNoteExpanded(v: Boolean) {
        noteExpanded = v
        viewModelScope.launch(Dispatchers.IO) { db.putSetting("ui.noteExpanded", if (v) "1" else "0") }
    }

    // ---------- 首次使用引导 ----------

    var showOnboarding by androidx.compose.runtime.mutableStateOf(false)
        private set

    fun maybeShowOnboarding() {
        viewModelScope.launch(Dispatchers.IO) {
            val seen = db.getSetting("onboarded") != null
            val total = db.stats().total
            withContext(Dispatchers.Main) {
                if (!seen && total == 0) showOnboarding = true
            }
        }
    }

    fun dismissOnboarding() {
        showOnboarding = false
        viewModelScope.launch(Dispatchers.IO) { db.putSetting("onboarded", "1") }
    }

    /** 「体验示例题」：不依赖 AI，内置三道典型题让用户先摸清复习闭环。 */
    fun loadSampleQuestions() {
        viewModelScope.launch {
            busy = true; busyLabel = "载入示例题"
            try {
                val result = withContext(Dispatchers.IO) { db.saveDrafts(sampleDrafts(), null) }
                dismissOnboarding()
                refresh()
                if (result.ids.isNotEmpty()) landOnFresh(result.ids)
                toast = "已载入 ${result.saved} 道示例题。先看解析，看不懂就追问，明天再复习"
            } finally {
                busy = false; busyLabel = ""
            }
        }
    }

    private fun sampleDrafts(): List<DraftQuestion> {
        fun arr(vararg items: String) = org.json.JSONArray(items.toList()).toString()
        return listOf(
            DraftQuestion(
                type = "选择题",
                stem = "函数 ${'$'}f(x)=x^2-2x${'$'} 在区间 ${'$'}[0,3]${'$'} 上的最大值为（　）",
                options = arr("A. 0", "B. 2", "C. 3", "D. 4"),
                answer = "C",
                analysis = "思路：先求驻点，再比较驻点与端点处的函数值。\n1. 求导：${'$'}f'(x)=2x-2${'$'}，令 ${'$'}f'(x)=0${'$'} 得驻点 ${'$'}x=1${'$'}。\n2. 计算各点函数值：${'$'}f(0)=0${'$'}，${'$'}f(1)=-1${'$'}，${'$'}f(3)=3${'$'}。\n3. 三者比较，最大值在端点 ${'$'}x=3${'$'} 处取得。\n易错：只比较驻点值、漏掉端点是本题最常见的失分点。\n结论：最大值为 3，选 C。",
                tags = arr("二次函数", "闭区间最值"),
                needFormulas = arr("求导公式 ${'$'}(x^2)'=2x${'$'}"),
                needConcepts = arr("闭区间上的最值要比较驻点与端点"),
                needMethods = arr("求导找驻点，再比较端点"),
                categoryName = "数学/函数与导数",
                customTag = "示例 1",
                confidence = 1.0,
            ),
            DraftQuestion(
                type = "解答题",
                stem = "求极限 ${'$'}\\lim_{x\\to 0}\\dfrac{\\sin x - x}{x^3}${'$'}。",
                answer = "${'$'}-\\dfrac{1}{6}${'$'}",
                analysis = "思路：${'$'}\\sin x${'$'} 用泰勒展开比反复洛必达更快。\n1. 展开：${'$'}\\sin x = x-\\dfrac{x^3}{6}+o(x^3)${'$'}。\n2. 代入分子：${'$'}\\sin x - x = -\\dfrac{x^3}{6}+o(x^3)${'$'}。\n3. 与 ${'$'}x^3${'$'} 相除得极限 ${'$'}-\\dfrac{1}{6}${'$'}。\n易错：等价无穷小 ${'$'}\\sin x\\sim x${'$'} 只能用于乘除因子，相减时直接替换会得到 ${'$'}0${'$'}。\n结论：${'$'}-\\dfrac{1}{6}${'$'}。",
                tags = arr("极限", "泰勒展开"),
                needFormulas = arr("${'$'}\\sin x = x-\\dfrac{x^3}{6}+o(x^3)${'$'}"),
                needConcepts = arr("等价无穷小替换的适用条件"),
                needMethods = arr("泰勒展开求极限"),
                categoryName = "数学/极限",
                customTag = "示例 2",
                confidence = 1.0,
            ),
            DraftQuestion(
                type = "解答题",
                stem = "简述面心立方（FCC）结构的配位数与致密度，并与体心立方（BCC）作比较。",
                answer = "FCC：配位数 12，致密度 0.74；BCC：配位数 8，致密度 0.68。",
                analysis = "思路：从两种结构的硬球堆积方式入手。\n1. FCC 中每个原子与 12 个最近邻原子相切，配位数为 12。\n2. 按硬球模型计算，FCC 致密度为 ${'$'}\\dfrac{\\pi}{\\sqrt{18}}\\approx 0.74${'$'}。\n3. BCC 每个原子只有 8 个最近邻，致密度 ${'$'}\\approx 0.68${'$'}，堆积更松。\n易错：把 BCC 的配位数 8 记成 12，两种结构的致密度容易混淆。\n结论：FCC 堆积更密（0.74 > 0.68），配位数 12 > 8。",
                tags = arr("晶体结构", "金属晶体"),
                needConcepts = arr("配位数的定义", "致密度的定义"),
                needMethods = arr("硬球堆积模型"),
                categoryName = "专业课/晶体结构",
                customTag = "示例 3",
                confidence = 1.0,
            ),
        )
    }

    var dataRevision by androidx.compose.runtime.mutableIntStateOf(0)
        private set

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            val cap = db.getSetting("review.dailyCap")?.toIntOrNull()?.coerceIn(5, 15) ?: 8
            val cats = db.categories()
            val st = db.stats()
            val today = db.todayQueue(cap)
            val overflow = (db.dueEligibleCount() - today.size).coerceAtLeast(0)
            val ds = db.docs()
            val p = db.provider()
            val trash = db.trashCount()
            val remind = com.zhuquan.tijian.review.ReviewReminder.enabled(db)
            val hour = com.zhuquan.tijian.review.ReviewReminder.hour(db)
            val minute = com.zhuquan.tijian.review.ReviewReminder.minute(db)
            withContext(Dispatchers.Main) {
                dailyCap = cap
                categories = cats; stats = st; todayQueue = today; dueOverflow = overflow
                docs = ds; provider = p; trashCount = trash
                remindEnabled = remind; remindHour = hour; remindMinute = minute
                dataRevision++
            }
        }
    }

    fun chooseDailyCap(n: Int) {
        val cap = n.coerceIn(5, 15)
        dailyCap = cap
        viewModelScope.launch(Dispatchers.IO) {
            db.putSetting("review.dailyCap", cap.toString())
            withContext(Dispatchers.Main) { refresh() }
        }
    }

    fun setReminder(enabled: Boolean, hour: Int, minute: Int) {
        remindEnabled = enabled
        remindHour = hour.coerceIn(0, 23)
        remindMinute = minute.coerceIn(0, 59)
        viewModelScope.launch(Dispatchers.IO) {
            db.putSetting("review.remind", if (enabled) "1" else "0")
            db.putSetting("review.remindHour", remindHour.toString())
            db.putSetting("review.remindMinute", remindMinute.toString())
            withContext(Dispatchers.Main) {
                com.zhuquan.tijian.review.ReviewReminder.schedule(getApplication())
            }
        }
    }

    fun openTodayReview() {
        startReview(ReviewMode.DUE, dailyCap)
    }

    fun markMasteredQuick(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val hold = db.queueHold(id) ?: return@launch
            db.markMasteredOut(id)
            withContext(Dispatchers.Main) {
                dropFromReview(id)
                refresh()
                showUndo("已标为掌握，不再占复习名额") {
                    viewModelScope.launch(Dispatchers.IO) {
                        db.restoreQueueHold(hold)
                        withContext(Dispatchers.Main) { refresh() }
                    }
                }
            }
        }
    }

    fun markUnimportantQuick(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val hold = db.queueHold(id) ?: return@launch
            db.markUnimportant(id, true)
            withContext(Dispatchers.Main) {
                dropFromReview(id)
                refresh()
                showUndo("已标为不重要，不再占复习名额") {
                    viewModelScope.launch(Dispatchers.IO) {
                        db.restoreQueueHold(hold)
                        withContext(Dispatchers.Main) { refresh() }
                    }
                }
            }
        }
    }

    fun restoreToQueue(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val hold = db.queueHold(id) ?: return@launch
            val back = hold.copy(
                mastery = hold.mastery.coerceAtMost(3),
                unimportant = false,
                nextReviewAt = hold.nextReviewAt.takeIf { it > 0 } ?: System.currentTimeMillis(),
            )
            db.restoreQueueHold(back)
            withContext(Dispatchers.Main) { refresh(); toast = "已重新加入复习" }
        }
    }

    private fun dropFromReview(id: String) {
        if (reviewQueue.any { it.id == id }) {
            val idx = reviewQueue.indexOfFirst { it.id == id }
            reviewQueue = reviewQueue.filter { it.id != id }
            if (idx in 0 until reviewIndex) reviewIndex--
            if (reviewIndex > reviewQueue.size) reviewIndex = reviewQueue.size
            persistReviewSession()
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

    /** 给笔记追加一张图片（拍照/相册 → 编辑器 → 入库）。 */
    suspend fun addNoteImage(questionId: String, uri: Uri): QuestionRow = withContext(Dispatchers.IO) {
        val image = importer.ingestOnly(uri)
        db.appendNoteImage(questionId, image.id)
        db.question(questionId) ?: error("题目不存在")
    }

    /** 编辑已有笔记图后替换：新图沿用原图的 original 链路。 */
    suspend fun replaceNoteImage(questionId: String, oldImageId: String, uri: Uri): QuestionRow = withContext(Dispatchers.IO) {
        val image = importer.ingestOnly(uri)
        db.linkOriginal(image.id, oldImageId)
        db.replaceNoteImage(questionId, oldImageId, image.id)
        db.question(questionId) ?: error("题目不存在")
    }

    fun removeNoteImage(questionId: String, imageId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            db.removeNoteImage(questionId, imageId)
            withContext(Dispatchers.Main) { refresh() }
        }
    }

    suspend fun replaceCapture(source: Uri, edited: Uri) {
        // 等这条 uri 的暂存落地（如果还在进行），条目身份才稳定
        synchronized(stagingJobs) { stagingJobs[source] }?.join()
        val copy = withContext(Dispatchers.IO) {
            val original = importer.ingestOnly(source)
            importer.ingestOnly(edited).also { db.linkOriginal(it.id, original.id) }
        }
        // 条目可能已被暂存副本换掉了身份（相册图异步暂存），原始 uri 和暂存 uri 都要认
        val key = synchronized(stagedByOriginal) { stagedByOriginal.remove(source) } ?: source
        val replaced = Uri.fromFile(File(copy.path))
        captureUris = captureUris.map { if (it == key || it == source) replaced else it }
        saveCaptureManifest()
    }

    fun addCapture(uri: Uri) {
        if (uri in captureUris) return
        if (captureUris.size >= 9) { toast = "一次最多导入 9 张图片"; return }
        // 先同步入列（去重与上限都是同步语义），content:// 的拷贝后台补做
        captureUris = captureUris + uri
        saveCaptureManifest()
        if (uri.scheme != "file") {
            val job = viewModelScope.launch {
                val staged = stageUri(uri)
                if (staged != uri) {
                    synchronized(stagedByOriginal) { stagedByOriginal[uri] = staged }
                    captureUris = captureUris.map { if (it == uri) staged else it }
                    saveCaptureManifest()
                }
                synchronized(stagingJobs) { stagingJobs.remove(uri) }
            }
            synchronized(stagingJobs) { stagingJobs[uri] = job }
        }
    }

    fun removeCapture(uri: Uri) {
        synchronized(stagingJobs) { stagingJobs.remove(uri)?.cancel() }
        captureUris = captureUris - uri
        synchronized(stagedByOriginal) { stagedByOriginal.entries.removeAll { it.value == uri } }
        if (uri.scheme == "file") {
            runCatching { uri.path?.let { File(it).takeIf { f -> f.parentFile == stagingDir }?.delete() } }
        }
        if (captureUris.isEmpty()) { synchronized(stagedByOriginal) { stagedByOriginal.clear() }; wipeStaging() } else saveCaptureManifest()
    }

    /** 收录入口：有未完成的识别就接着做，否则打开方式面板。 */
    fun openAdd() {
        when {
            importing || pending.isNotEmpty() -> go(Route.Import)
            captureUris.isNotEmpty() -> go(Route.Capture)
            else -> importSheet = true
        }
    }

    /** 拍照是每天的第一步：直接进相机，而不是先看空工作台。 */
    fun openCameraCapture() {
        if (importing || pending.isNotEmpty()) {
            openAdd()
            return
        }
        clearCapture()
        armCamera = true
        go(Route.Capture)
    }

    fun consumeCameraArm(): Boolean {
        val armed = armCamera
        armCamera = false
        return armed
    }

    fun landOnFresh(ids: List<String>) {
        if (ids.isEmpty()) {
            home()
            return
        }
        freshIds = ids
        navForward = true
        stack.clear()
        stack.add(Route.Home)
        route = Route.Detail(ids.first())
    }

    fun stepFresh(delta: Int) {
        val current = (route as? Route.Detail)?.id ?: return
        val index = freshIds.indexOf(current)
        if (index < 0) return
        val next = index + delta
        if (next !in freshIds.indices) return
        route = Route.Detail(freshIds[next])
    }

    fun dismissFresh() {
        freshIds = emptyList()
    }

    fun setStarred(id: String, starred: Boolean) {
        cachedQuestion(id)?.let { cacheQuestion(it.copy(starred = starred)) }
        viewModelScope.launch(Dispatchers.IO) {
            db.setStarred(id, starred)
            withContext(Dispatchers.Main) { refresh() }
        }
    }

    fun clearCapture() {
        synchronized(stagingJobs) { stagingJobs.values.forEach { it.cancel() }; stagingJobs.clear() }
        captureUris = emptyList()
        synchronized(stagedByOriginal) { stagedByOriginal.clear() }
        wipeStaging()
    }

    fun importCapture() {
        val list = captureUris
        if (list.isEmpty() || importing || busy) return
        importUris(list, "camera")
    }

    /** 识别中途停止：取消协程与在途 HTTP；已识别完成的草稿保留为待确认。 */
    fun cancelImport() {
        if (!importing) return
        importJob?.cancel()
        importJob = null
        ai.cancelActive()
        val (done, errs) = importer.consumePartial()
        importing = false
        importLabel = ""
        importProgress = 0f
        if (done.isNotEmpty()) {
            pending = pending + done
            importErrors = errs
            saveDraftState()
            toast = "已停止识别，${done.size} 道题已存为待确认"
        } else {
            toast = "已停止导入，可稍后继续"
        }
    }

    fun importUris(uris: List<Uri>, source: String = "share") {
        if (uris.isEmpty() || importing || busy) return
        if (pending.isNotEmpty()) {
            route = Route.Import
            toast = "请先确认或移除上一批题目，再导入新图片"
            return
        }
        // 没配 AI 就直接进识别只会得到一堆空草稿；先去配置，配好自动回到导入任务
        if (!db.provider().ready()) {
            returnAfterProvider = if (captureUris.isNotEmpty()) Route.Capture else Route.Import
            toast = "先配置 AI 接口，保存后自动回到刚才的导入"
            go(Route.Provider)
            return
        }
        importJob = viewModelScope.launch {
            importing = true
            importProgress = 0f
            importLabel = "准备中"
            importErrors = emptyList()
            lastFailedUris = emptyList()
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
                lastFailedUris = out.failedUris
                importing = false
                saveDraftState()
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
                saveDraftState()
                toast = "导入失败：${e.message}"
            }
        }
    }

    /** 只重跑上次识别失败的几张图，已完成的草稿保持不动。 */
    fun retryFailedImports() {
        val uris = lastFailedUris
        if (uris.isEmpty() || importing || busy) return
        importJob = viewModelScope.launch {
            importing = true
            importProgress = 0f
            importLabel = "重试失败的图片"
            importErrors = emptyList()
            lastFailedUris = emptyList()
            route = Route.Import
            try {
                val out = importer.import(uris, "retry") { done, total, label ->
                    importProgress = done.toFloat() / total.coerceAtLeast(1)
                    importLabel = label
                }
                pending = pending + out.drafts
                if (pendingBatch == null) pendingBatch = out.batchId
                importErrors = out.errors
                lastFailedUris = out.failedUris
                importing = false
                saveDraftState()
                if (db.provider().ready()) autoClassify()
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                importing = false
            } catch (e: Exception) {
                importing = false
                importErrors = listOf(e.message ?: "重试失败")
                toast = "重试失败：${e.message}"
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
                saveDraftState()
            } catch (e: Exception) {
                toast = "自动分类失败，可手动选择"
            } finally {
                busy = false
            }
        }
    }

    fun updateDraft(updated: DraftQuestion) {
        pending = pending.map { if (it.id == updated.id) updated else it }
        saveDraftState()
    }

    fun removeDraft(id: String) {
        pending = pending.filter { it.id != id }
        saveDraftState()
    }

    /** 丢弃整批待确认题目（含草稿文件），需要用户在界面上明确确认过。 */
    fun discardPending() {
        pending = emptyList()
        pendingBatch = null
        importErrors = emptyList()
        lastFailedUris = emptyList()
        saveDraftState()
        toast = "已丢弃本批待确认题目"
    }

    /** 给无图草稿补一张图（手动录入的图片是选填项）。 */
    fun attachDraftImage(draftId: String, uri: Uri) {
        viewModelScope.launch {
            try {
                val img = withContext(Dispatchers.IO) { importer.ingestOnly(uri) }
                pending = pending.map { if (it.id == draftId) it.copy(imageId = img.id, imagePath = img.path) else it }
                saveDraftState()
            } catch (e: Exception) {
                toast = "图片保存失败：${e.message}"
            }
        }
    }

    /** 移除草稿上的图片（文字保留）。 */
    fun detachDraftImage(draftId: String) {
        pending = pending.map { if (it.id == draftId) it.copy(imageId = null, imagePath = null) else it }
        saveDraftState()
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
                val result = withContext(Dispatchers.IO) { db.saveDrafts(list, pendingBatch) }
                val completed = list.map { it.id }.toSet()
                pending = pending.filterNot { it.id in completed }
                val done = pending.isEmpty()
                if (done) pendingBatch = null
                saveDraftState()
                refresh()
                toast = if (result.skipped > 0) "已入库 ${result.saved} 题 · 跳过 ${result.skipped} 道重复" else "已入库 ${result.saved} 题"
                if (done && result.ids.isNotEmpty()) landOnFresh(result.ids)
                else if (done) home()
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
                saveDraftState()
                route = Route.Import
            } catch (e: Exception) {
                importing = false
                toast = "导入失败：${e.message}"
            }
        }
    }

    /** 真正的手动录入：直接输入文字，图片是选填项（可之后再补）。 */
    fun addManualText() {
        if (importing || busy) return
        if (pending.isNotEmpty()) {
            route = Route.Import
            toast = "请先处理上一批待确认题目"
            return
        }
        pending = listOf(DraftQuestion(type = "", stem = "", categoryName = ""))
        pendingBatch = null
        importErrors = emptyList()
        saveDraftState()
        route = Route.Import
    }

    // ---------- review ----------

    /** 复习模式：到期复习（队列）／专项练习（某个分类）／自由练习（全部题）。 */
    enum class ReviewMode(val label: String) {
        DUE("到期复习"), CATEGORY("专项练习"), FREE("自由练习"),
    }

    var reviewQueue by androidx.compose.runtime.mutableStateOf<List<QuestionRow>>(emptyList())
        private set
    var reviewIndex by androidx.compose.runtime.mutableStateOf(0)
        private set
    var revealAnswer by androidx.compose.runtime.mutableStateOf(false)
        private set
    var reviewMode by androidx.compose.runtime.mutableStateOf(ReviewMode.DUE)
        private set

    /** 本轮自评「不会」的题：完成页可针对它们再练一轮。 */
    val wrongThisRound = androidx.compose.runtime.mutableStateListOf<String>()

    /** 评分历史：支持「撤销上一题评分」，点错不再无法挽回。 */
    private val rateHistory = ArrayDeque<Pair<QuestionRow, com.zhuquan.tijian.data.Db.ReviewSnapshot>>()

    /** 是否还有可撤销的评分（复习页显示「撤销上一题」按钮用）。 */
    var canUndoRate by androidx.compose.runtime.mutableStateOf(false)
        private set

    /** 题量偏好：5 / 10 / 0=全部；记住上次的选择。 */
    var reviewCountPref by androidx.compose.runtime.mutableIntStateOf(
        db.getSetting("review.count")?.toIntOrNull()?.takeIf { it == 5 || it == 10 || it == 0 } ?: 10
    )
        private set

    /** 揭晓答案前是否直接展示原图。默认隐藏：原图里可能有手写答案或老师批注。 */
    var showImageBeforeReveal by androidx.compose.runtime.mutableStateOf(
        db.getSetting("review.showImage") == "1"
    )
        private set

    fun chooseReviewCount(n: Int) {
        reviewCountPref = n
        viewModelScope.launch(Dispatchers.IO) { db.putSetting("review.count", n.toString()) }
    }

    fun chooseShowImage(v: Boolean) {
        showImageBeforeReveal = v
        viewModelScope.launch(Dispatchers.IO) { db.putSetting("review.showImage", if (v) "1" else "0") }
    }

    /** 上一轮没做完（含刚打开还没评的第一题）。做完的队列不算。 */
    val canResumeReview: Boolean
        get() = reviewQueue.isNotEmpty() && reviewIndex < reviewQueue.size

    fun resumeReview() { if (canResumeReview) route = Route.Review }

    /** 一轮结束回到首页：清掉队列，避免「继续上次复习」残留。 */
    fun clearReviewSession() {
        reviewQueue = emptyList(); reviewIndex = 0; revealAnswer = false
        wrongThisRound.clear(); rateHistory.clear(); canUndoRate = false
        persistReviewSession()
    }

    private fun persistReviewSession() {
        val queue = reviewQueue
        val index = reviewIndex
        val modeName = reviewMode.name
        val reveal = revealAnswer
        val wrong = wrongThisRound.toList()
        viewModelScope.launch(Dispatchers.IO) {
            if (queue.isEmpty() || index >= queue.size) {
                db.putSetting("review.session", "")
            } else {
                db.putSetting(
                    "review.session",
                    encodeReviewSession(ReviewSessionState(modeName, index, reveal, queue.map { it.id }, wrong))
                )
            }
        }
    }

    private fun restoreReviewSession() {
        viewModelScope.launch(Dispatchers.IO) {
            val state = decodeReviewSession(db.getSetting("review.session").orEmpty()) ?: return@launch
            val list = state.ids.mapNotNull { db.question(it) }
            if (list.isEmpty()) return@launch
            val index = state.index.coerceIn(0, list.lastIndex)
            val mode = runCatching { ReviewMode.valueOf(state.mode) }.getOrDefault(ReviewMode.DUE)
            withContext(Dispatchers.Main) {
                if (reviewQueue.isNotEmpty()) return@withContext
                reviewQueue = list
                reviewIndex = index
                reviewMode = mode
                revealAnswer = state.reveal
                wrongThisRound.clear()
                val live = list.map { it.id }.toSet()
                state.wrong.filter { it in live }.forEach { wrongThisRound.add(it) }
            }
        }
    }

    init { restoreReviewSession() }

    fun startReview(mode: ReviewMode = ReviewMode.DUE, count: Int = reviewCountPref, categoryId: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val base = when (mode) {
                ReviewMode.DUE -> db.todayQueue(dailyCap)
                ReviewMode.CATEGORY -> {
                    val all = db.questions(categoryId)
                    all.filter { it.mastery < 4 }.ifEmpty { all }
                }
                ReviewMode.FREE -> db.questions()
            }
            val list = if (mode == ReviewMode.DUE || count <= 0) base else base.take(count)
            withContext(Dispatchers.Main) {
                reviewQueue = list
                reviewIndex = 0
                revealAnswer = false
                reviewMode = mode
                wrongThisRound.clear()
                rateHistory.clear()
                canUndoRate = false
                route = Route.Review
                persistReviewSession()
            }
        }
    }

    fun startReviewFor(categoryId: String) {
        startReview(ReviewMode.CATEGORY, reviewCountPref, categoryId)
    }

    /** 完成页「再练这些题」：只练本轮自评不会的题。 */
    fun retryWrongQuestions() {
        val ids = wrongThisRound.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val list = ids.mapNotNull { db.question(it) }
            withContext(Dispatchers.Main) {
                if (list.isEmpty()) { toast = "没有需要重练的题"; return@withContext }
                reviewQueue = list
                reviewIndex = 0
                revealAnswer = false
                reviewMode = ReviewMode.FREE
                wrongThisRound.clear()
                rateHistory.clear()
                canUndoRate = false
                route = Route.Review
                persistReviewSession()
            }
        }
    }

    fun reveal() { revealAnswer = true; persistReviewSession() }

    fun rate(rating: Int) {
        val index = reviewIndex
        val q = reviewQueue.getOrNull(index) ?: return
        // 先在主线程推进，连点只会落到下一题，不会把同一题记两次。
        reviewIndex = index + 1
        revealAnswer = false
        if (rating == 0 && q.id !in wrongThisRound) wrongThisRound.add(q.id)
        persistReviewSession()
        viewModelScope.launch(Dispatchers.IO) {
            val snapshot = db.snapshotForReview(q.id)
            db.review(q.id, rating)
            withContext(Dispatchers.Main) {
                if (snapshot != null) {
                    rateHistory.addLast(q to snapshot)
                    while (rateHistory.size > 20) rateHistory.removeFirst()
                    canUndoRate = true
                }
                if (reviewIndex >= reviewQueue.size) refresh()
            }
        }
    }

    /** 撤销上一题评分：题目状态还原，队列退回上一题。 */
    fun undoRating() {
        val (q, snapshot) = rateHistory.removeLastOrNull() ?: return
        wrongThisRound.remove(q.id)
        if (reviewIndex > 0) reviewIndex--
        revealAnswer = false
        canUndoRate = rateHistory.isNotEmpty()
        persistReviewSession()
        viewModelScope.launch(Dispatchers.IO) { db.revertReview(snapshot) }
    }

    /** 详情页上的快捷追问：带上当前题的上下文，点一下就发出去。 */
    fun askAboutCurrent(prompt: String) {
        val id = (route as? Route.Detail)?.id ?: return
        if (chatBusy) return
        viewModelScope.launch {
            val q = withContext(Dispatchers.IO) { db.question(id) } ?: return@launch
            if (!provider.ready()) {
                toast = "先在「我的 → AI 接入」配置模型"
                return@launch
            }
            lastQuestionId = id
            chatQuestion = q
            chatMessages = withContext(Dispatchers.IO) { db.chatMessages(id) }
            chatQuote = ""
            chatOpen = true
            sendChat(prompt)
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
            withContext(Dispatchers.Main) {
                refresh(); refreshTrash()
                showUndo(
                    if (ids.size == 1) "已删除 1 道题" else "已删除 ${ids.size} 道题"
                ) { restoreQuestions(ids) }
            }
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
    /** 本轮消息附带的图片或文本文件；发送后清空。 */
    var chatAttachment by androidx.compose.runtime.mutableStateOf<Uri?>(null)
    var chatAttachmentName by androidx.compose.runtime.mutableStateOf("")
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

    // ---------- 题目编辑草稿 ----------
    // 完整编辑页里随手改的内容自动暂存：误触返回、进程被杀都不丢，保存成功后清除。

    fun loadEditDraft(id: String): String? = db.getSetting("edit.draft.$id")

    fun saveEditDraft(id: String, json: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (json.isBlank()) db.putSetting("edit.draft.$id", "") else db.putSetting("edit.draft.$id", json)
        }
    }

    fun clearEditDraft(id: String) {
        viewModelScope.launch(Dispatchers.IO) { db.putSetting("edit.draft.$id", "") }
    }

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
        chatAttachment = null
        chatAttachmentName = ""
        pendingChatFile = null
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

    fun closeChat() { chatOpen = false; chatAttachment = null; chatAttachmentName = "" }

    fun updateChatQuote(text: String) { chatQuote = text }

    /** 附带一张图片（相册/拍照），发送时压缩成 base64 走多模态消息。 */
    fun attachChatImage(uri: Uri) {
        chatAttachment = uri
        chatAttachmentName = "图片"
    }

    /** 附带一个文件：文本类（txt/md/csv/代码等）按内容拼接，其他类型提示不支持。 */
    fun attachChatFile(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val cr = getApplication<Application>().contentResolver
            val name = queryDisplayName(cr, uri) ?: "附件"
            val mime = cr.getType(uri).orEmpty()
            val textual = mime.startsWith("text/") || mime.contains("json") || mime.contains("xml") ||
                mime.contains("csv") || name.endsWith(".md") || name.endsWith(".txt") || name.endsWith(".csv")
            if (!textual) {
                withContext(Dispatchers.Main) { toast = "暂时只支持文本类文件（txt / md / csv / 代码）" }
                return@launch
            }
            val content = runCatching {
                cr.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
            }.getOrDefault("")
            if (content.isBlank()) {
                withContext(Dispatchers.Main) { toast = "这个文件读不出文本内容" }
                return@launch
            }
            withContext(Dispatchers.Main) {
                pendingChatFile = com.zhuquan.tijian.ai.ChatAttachment.TextFile(name, content.take(12000))
                chatAttachment = uri
                chatAttachmentName = name
            }
        }
    }

    /** 文本附件的现成内容：图片附件为 null，发送时才从 uri 现场压缩。 */
    private var pendingChatFile: com.zhuquan.tijian.ai.ChatAttachment.TextFile? = null

    fun clearChatAttachment() {
        chatAttachment = null
        chatAttachmentName = ""
        pendingChatFile = null
    }

    private fun queryDisplayName(cr: android.content.ContentResolver, uri: Uri): String? = runCatching {
        cr.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    /** 图片 uri → 下采样 JPEG base64（1280px 足够模型看题，控制 token 与内存）。 */
    private suspend fun imageAttachment(uri: Uri): com.zhuquan.tijian.ai.ChatAttachment.Image? = withContext(Dispatchers.IO) {
        runCatching {
            val app = getApplication<Application>()
            val tmp = File.createTempFile("chat_", ".img", app.cacheDir)
            app.contentResolver.openInputStream(uri)?.use { input -> tmp.outputStream().use { input.copyTo(it) } }
                ?: return@runCatching null
            val bitmap = com.zhuquan.tijian.data.Importer.decodeSampled(tmp, 1280)
                ?: return@runCatching null
            // 相册 JPEG 可能带 EXIF 旋转，解码不自动应用，按方向摆正
            val oriented = runCatching {
                val exif = android.media.ExifInterface(tmp.absolutePath)
                val orientation = exif.getAttributeInt(
                    android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL
                )
                val m = android.graphics.Matrix()
                when (orientation) {
                    android.media.ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
                    android.media.ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
                    android.media.ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
                    android.media.ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.preScale(-1f, 1f)
                    android.media.ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.preScale(1f, -1f)
                }
                if (m.isIdentity) bitmap else android.graphics.Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
            }.getOrDefault(bitmap)
            val out = java.io.ByteArrayOutputStream()
            oriented.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out)
            if (oriented !== bitmap) oriented.recycle()
            bitmap.recycle()
            tmp.delete()
            com.zhuquan.tijian.ai.ChatAttachment.Image(
                android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)
            )
        }.getOrNull()
    }

    fun sendChat(text: String) {
        val q = chatQuestion ?: return
        val prompt = text.trim()
        if (prompt.isBlank() || chatBusy) return
        val quote = chatQuote
        val history = chatMessages
        val attachmentUri = chatAttachment
        val textFile = pendingChatFile
        chatMessages = chatMessages + ("user" to prompt)
        chatQuote = ""
        chatAttachment = null
        chatAttachmentName = ""
        pendingChatFile = null
        chatBusy = true
        chatTruncated = false
        chatError = null
        chatJob = viewModelScope.launch {
            try {
                val attachment = textFile ?: attachmentUri?.let { imageAttachment(it) }
                if (attachmentUri != null && attachment == null) toast = "附件读取失败，已只发送文字"
                val contextText = withContext(Dispatchers.IO) { buildQuestionContext(q) }
                val (answer, truncated) = ai.ask(provider, contextText, history, prompt, quote, attachment)
                val marker = when (attachment) {
                    is com.zhuquan.tijian.ai.ChatAttachment.Image -> "［图片］"
                    is com.zhuquan.tijian.ai.ChatAttachment.TextFile -> "［附件 ${attachment.name}］"
                    null -> ""
                }
                withContext(Dispatchers.IO) {
                    db.addChatMessage(q.id, "user", prompt + marker, quote)
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

    // ---------- 一键大整理 ----------

    /** 大整理预检结果：本地统计各维度待整理数量。 */
    data class TidyProbe(
        val total: Int,
        val noTags: Int,
        val noSolution: Int,
        val noNeeds: Int,
        val uncat: Int,
        val pendingCats: Int,
        val duplicates: Int,
    )

    /** 本地预检：不调 AI，扫一遍全部题目统计问题。 */
    suspend fun probeTidy(): TidyProbe = withContext(Dispatchers.IO) {
        val qs = db.allQuestions()
        val dupCount = qs.filter { it.stem.isNotBlank() }
            .groupBy { it.stem.trim() to com.zhuquan.tijian.data.contentHash(it.stem, it.options, it.answer) }
            .values.sumOf { g -> if (g.size > 1) g.size - 1 else 0 }
        TidyProbe(
            total = qs.size,
            noTags = qs.count { parseKnowledgeTags(it.tags).isEmpty() },
            noSolution = qs.count { it.answer.isBlank() || it.analysis.isBlank() },
            noNeeds = qs.count {
                parseKnowledgeTags(it.needFormulas).isEmpty() &&
                    parseKnowledgeTags(it.needConcepts).isEmpty() &&
                    parseKnowledgeTags(it.needMethods).isEmpty()
            },
            uncat = qs.count { it.categoryId == com.zhuquan.tijian.data.Db.UNCAT || it.categoryName == "未分类" },
            pendingCats = db.categories().count { it.status == "pending" },
            duplicates = dupCount,
        )
    }

    var tidyRunning by androidx.compose.runtime.mutableStateOf(false)
        private set
    var tidyProgress by androidx.compose.runtime.mutableStateOf(0f)
        private set
    var tidyLabel by androidx.compose.runtime.mutableStateOf("")
        private set
    var tidyReport by androidx.compose.runtime.mutableStateOf<String?>(null)
    private var tidyJob: kotlinx.coroutines.Job? = null

    /**
     * 一键大整理 v2：
     * 1) 本地查重（内容指纹相同的重复题，保留最早一道，其余进回收站）；
     * 2) AI 分批复核：统一分类/知识点标签/题型，并复核答案与解析——
     *    算错、缺漏、或「先给答案再补过程」的都重写（先推理后结论）。
     */
    fun startTidy() {
        if (tidyRunning || busy || importing) return
        if (!provider.ready()) { toast = "先在「AI 接入」配置模型，再运行大整理"; return }
        tidyJob = viewModelScope.launch {
            tidyRunning = true
            tidyProgress = 0f
            tidyReport = null
            var catFixed = 0
            var tagFixed = 0
            var typeFixed = 0
            var answerFixed = 0
            var analysisFixed = 0
            var dupRemoved = 0
            try {
                // —— 第一步：本地查重 ——
                tidyLabel = "本地查重…"
                val all = withContext(Dispatchers.IO) { db.allQuestions() }
                if (all.isEmpty()) { tidyReport = "错题本还是空的，没有需要整理的内容"; return@launch }
                val byHash = all.filter { it.stem.isNotBlank() }
                    .groupBy { it.stem.trim() to com.zhuquan.tijian.data.contentHash(it.stem, it.options, it.answer) }
                val dupIds = ArrayList<String>()
                byHash.forEach { (_, group) ->
                    if (group.size > 1) {
                        // 内容指纹一致才判重；保留最早导入的一道
                        group.sortedBy { it.createdAt }.drop(1).forEach { dupIds.add(it.id) }
                    }
                }
                if (dupIds.isNotEmpty()) {
                    withContext(Dispatchers.IO) { dupIds.forEach { db.deleteQuestion(it) } }
                    dupRemoved = dupIds.size
                }

                // —— 第二步：AI 分批复核 ——
                val remaining = withContext(Dispatchers.IO) { db.allQuestions() }
                val cats = withContext(Dispatchers.IO) { db.categories() }
                val batches = remaining.chunked(10)
                batches.forEachIndexed { bi, batch ->
                    tidyLabel = "AI 复核第 ${bi + 1}/${batches.size} 批（共 ${remaining.size} 题）"
                    tidyProgress = bi.toFloat() / batches.size
                    val suggestions = ai.tidyBatch(provider, batch, cats)
                    withContext(Dispatchers.IO) {
                        suggestions.forEach { (id, s) ->
                            val q = batch.firstOrNull { it.id == id } ?: return@forEach
                            val catId = if (s.category.isNotBlank() && s.category != q.categoryName) {
                                catFixed++
                                runCatching { db.ensureCategory(s.category) }.getOrNull()
                            } else null
                            val curTags = parseKnowledgeTags(q.tags)
                            val newTags = when {
                                s.tags.isEmpty() -> q.tags
                                s.tags == curTags -> q.tags
                                else -> { tagFixed++; org.json.JSONArray(s.tags).toString() }
                            }
                            val newType = if (s.type.isNotBlank() && s.type != q.type) { typeFixed++; s.type } else q.type
                            if (catId != null || newTags != q.tags || newType != q.type) {
                                db.applyTidyResult(id, catId, newTags, newType)
                            }
                            // 答案/解析纠错：仅替换判定不合格且给了新内容的维度
                            var finalAnswer = q.answer
                            var finalAnalysis = q.analysis
                            var solutionChanged = false
                            if (!s.answerOk && s.answer.isNotBlank() && s.answer != q.answer) {
                                finalAnswer = s.answer; answerFixed++; solutionChanged = true
                            }
                            if (!s.analysisOk && s.analysis.isNotBlank() && s.analysis != q.analysis) {
                                finalAnalysis = s.analysis; analysisFixed++; solutionChanged = true
                            }
                            if (solutionChanged) db.updateQuestionSolution(id, finalAnswer, finalAnalysis)
                        }
                    }
                }
                tidyProgress = 1f
                refresh()
                refreshTrash()
                tidyReport = buildString {
                    append("整理完成：复核 ${remaining.size} 道")
                    if (catFixed > 0) append(" · 分类调整 $catFixed")
                    if (tagFixed > 0) append(" · 知识点更新 $tagFixed")
                    if (typeFixed > 0) append(" · 题型修正 $typeFixed")
                    if (answerFixed > 0) append(" · 答案纠错 $answerFixed")
                    if (analysisFixed > 0) append(" · 解析重写 $analysisFixed")
                    if (dupRemoved > 0) append(" · 移除重复 $dupRemoved 道（回收站可恢复）")
                    if (catFixed + tagFixed + typeFixed + answerFixed + analysisFixed + dupRemoved == 0) append(" · 无需修改")
                }
                toast = tidyReport ?: "整理完成"
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                tidyReport = "已停止整理，完成的部分保留"
            } catch (e: Exception) {
                tidyReport = "整理中断：${e.message ?: "未知错误"}（已完成部分保留）"
            } finally {
                tidyRunning = false
                tidyLabel = ""
            }
        }
    }

    /** 停止大整理：在途请求取消，已写回的保留。 */
    fun stopTidy() {
        ai.cancelActive()
        tidyJob?.cancel()
        tidyJob = null
        tidyRunning = false
        tidyLabel = ""
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

    /** 把当前表单配置存为一个常用提供商档案。 */
    fun saveCurrentAsProfile(name: String) {
        val n = name.trim()
        if (n.isBlank()) { toast = "先给这个提供商起个名字"; return }
        viewModelScope.launch(Dispatchers.IO) {
            val cur = db.provider()
            val rest = db.providerProfiles().filterNot { it.name == n }
            db.saveProviderProfiles(rest + com.zhuquan.tijian.data.ProviderProfile(
                name = n, baseUrl = cur.baseUrl, apiKey = cur.apiKey, model = cur.model,
                protocol = cur.protocol, reasoning = cur.reasoning, models = cur.models,
                webSearch = cur.webSearch,
            ))
            withContext(Dispatchers.Main) {
                profiles = db.providerProfiles()
                toast = "已保存「$n」，以后一点就能切换"
            }
        }
    }

    /** 一键切换到某个常用提供商（写入当前配置，表单随之刷新）。 */
    fun applyProfile(name: String) {
        val target = profiles.firstOrNull { it.name == name } ?: return
        viewModelScope.launch(Dispatchers.IO) {
            db.saveProvider(target.toConfig())
            withContext(Dispatchers.Main) {
                provider = target.toConfig()
                toast = "已切换到「$name」"
            }
        }
    }

    fun deleteProfile(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            db.saveProviderProfiles(db.providerProfiles().filterNot { it.name == name })
            withContext(Dispatchers.Main) {
                profiles = db.providerProfiles()
                toast = "已删除「$name」"
            }
        }
    }

    suspend fun testProvider(cfg: ProviderConfig): String = ai.test(cfg)

    // ---------- backup ----------

    var lastBackupAt by androidx.compose.runtime.mutableStateOf(0L)
        private set
    var lastBackupCount by androidx.compose.runtime.mutableStateOf(0)
        private set

    fun loadBackupInfo() {
        viewModelScope.launch(Dispatchers.IO) {
            val at = db.getSetting("backup.lastAt")?.toLongOrNull() ?: 0L
            val count = db.getSetting("backup.lastCount")?.toIntOrNull() ?: 0
            withContext(Dispatchers.Main) { lastBackupAt = at; lastBackupCount = count }
        }
    }

    fun exportBackup(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val ctx = getApplication<Application>()
                val n = (ctx.contentResolver.openOutputStream(uri) ?: error("无法写入所选文件")).use { out ->
                    com.zhuquan.tijian.data.Backup.export(ctx, db, out)
                }
                val total = db.stats().total
                db.putSetting("backup.lastAt", System.currentTimeMillis().toString())
                db.putSetting("backup.lastCount", total.toString())
                withContext(Dispatchers.Main) {
                    lastBackupAt = System.currentTimeMillis(); lastBackupCount = total
                    toast = "备份完成（$n 个文件 · $total 道题）"
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { toast = "备份失败：${e.message}" }
            }
        }
    }

    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val ctx = getApplication<Application>()
                    // 恢复是覆盖性操作：先把当前数据在本机存一份快照，后悔了还有救
                    val snapDir = File(ctx.filesDir, "auto_backup").apply { mkdirs() }
                    val snap = File(snapDir, "before_restore_${System.currentTimeMillis()}.zip")
                    try {
                        snap.outputStream().use { com.zhuquan.tijian.data.Backup.export(ctx, db, it) }
                        // 只保留最近两份自动快照
                        snapDir.listFiles()?.sortedByDescending { it.name }?.drop(2)?.forEach { it.delete() }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) { toast = "无法保存当前数据快照，已取消恢复（${e.message}）" }
                        return@withContext
                    }
                    withContext(Dispatchers.Main) { toast = "已自动保存当前快照，正在恢复…" }
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
