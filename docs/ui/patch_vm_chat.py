import pathlib

p = pathlib.Path('app/src/main/java/com/zhuquan/tijian/ui/AppVm.kt')
s = p.read_text(encoding='utf-8').replace('\r\n', '\n')

# import parseKnowledgeTags
s = s.replace(
    'import com.zhuquan.tijian.data.QuestionRow\n',
    'import com.zhuquan.tijian.data.QuestionRow\nimport com.zhuquan.tijian.data.parseKnowledgeTags\n'
)

anchor = '''    // ---------- pdf ----------'''
new_block = '''    // ---------- 题目追问 ----------

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

    fun rememberQuestion(id: String) { lastQuestionId = id }

    fun openChat(quote: String = "") {
        val id = when (val r = route) {
            is Route.Detail -> r.id
            is Route.Review -> reviewQueue.getOrNull(reviewIndex)?.id
            else -> lastQuestionId
        }
        if (id == null) { toast = "先打开一道题，再来追问"; return }
        viewModelScope.launch {
            val q = withContext(Dispatchers.IO) { db.question(id) }
            if (q == null) { toast = "题目不存在"; return@launch }
            val history = withContext(Dispatchers.IO) { db.chatMessages(id) }
            chatQuestion = q
            chatMessages = history
            chatQuote = quote
            chatError = null
            chatOpen = true
        }
    }

    fun closeChat() { chatOpen = false }

    fun setChatQuote(text: String) { chatQuote = text }

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

    // ---------- pdf ----------'''

assert anchor in s
s = s.replace(anchor, new_block, 1)
p.write_text(s, encoding='utf-8')
print('AppVm chat added')
