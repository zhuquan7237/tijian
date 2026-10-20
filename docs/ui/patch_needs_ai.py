import pathlib

# ---------- AiClient: analyzeNeeds + more tokens for chat ----------
p = pathlib.Path('app/src/main/java/com/zhuquan/tijian/ai/AiClient.kt')
s = p.read_text(encoding='utf-8')

s = s.replace('import com.zhuquan.tijian.data.ProviderConfig',
              'import com.zhuquan.tijian.data.ProviderConfig\nimport com.zhuquan.tijian.data.QuestionRow', 1)

# chat answers need room for long derivations; truncation was cutting formulas in half
s = s.replace('put("max_output_tokens", 1600)', 'put("max_output_tokens", 3000)', 1)
s = s.replace('put("max_tokens", 1600)', 'put("max_tokens", 3000)', 1)

anchor = '''    // ---------------- transport ----------------'''
assert anchor in s
method = '''    /**
     * 只补「这道题需要掌握什么」：公式 / 概念 / 方法。
     * 用于导入时模型漏字段、或旧题补全。
     */
    suspend fun analyzeNeeds(cfg: ProviderConfig, q: QuestionRow): Triple<List<String>, List<String>, List<String>> =
        withContext(Dispatchers.IO) {
            val prompt = """
你是错题教练。学生做错了下面这道题，需要知道「要做出这道题，必须会哪些公式、概念、方法」。
要求：
1. 只列真正关键、缺了就做不出来的内容，不要泛泛而谈。
2. formulas 写具体公式（LaTeX，$...$ 包裹）；concepts 写概念名；methods 写解题方法/步骤名。
3. 每类 1~4 条，没有就返回空数组。
4. 只输出 JSON，不要 markdown 代码块。

题型：${q.type}
题干：
${q.stem.take(1200)}
答案：${q.answer.take(400)}
解析：
${q.analysis.take(1600)}

输出模板：
{"formulas":[""],"concepts":[""],"methods":[""]}
""".trimIndent()
            val body = if (cfg.protocol == "responses") {
                JSONObject().apply {
                    put("model", cfg.model)
                    put("input", prompt)
                    put("instructions", "你是严谨的理科错题教练，只输出 JSON。")
                    put("temperature", 0.2)
                    put("max_output_tokens", 800)
                }
            } else {
                JSONObject().apply {
                    put("model", cfg.model)
                    put("messages", JSONArray()
                        .put(JSONObject().apply { put("role", "system"); put("content", "你是严谨的理科错题教练，只输出 JSON。") })
                        .put(JSONObject().apply { put("role", "user"); put("content", prompt) }))
                    put("temperature", 0.2)
                    put("max_tokens", 800)
                }
            }
            val raw = call(cfg, body)
            val json = extractJson(raw) ?: throw AiException("模型没有返回 JSON：${raw.take(120)}")
            val o = JSONObject(json)
            fun arr(key: String): List<String> {
                val a = o.optJSONArray(key) ?: return emptyList()
                return (0 until a.length()).map { a.optString(it).trim() }.filter { it.isNotBlank() }
            }
            Triple(arr("formulas"), arr("concepts"), arr("methods"))
        }

'''
s = s.replace(anchor, method + anchor, 1)
p.write_text(s, encoding='utf-8')
print('AiClient.analyzeNeeds added')

# ---------- AppVm: fillNeeds ----------
p = pathlib.Path('app/src/main/java/com/zhuquan/tijian/ui/AppVm.kt')
s = p.read_text(encoding='utf-8')
anchor = '    // ---------- knowledge ----------'
assert anchor in s
add = '''    var needsBusyFor by androidx.compose.runtime.mutableStateOf<String?>(null)
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

'''
s = s.replace(anchor, add + anchor, 1)
p.write_text(s, encoding='utf-8')
print('AppVm.fillNeeds added')

# ---------- detail reload on revision ----------
p = pathlib.Path('app/src/main/java/com/zhuquan/tijian/ui/HomeScreens.kt')
s = p.read_text(encoding='utf-8')
s = s.replace('    LaunchedEffect(id) {\n        vm.rememberQuestion(id)',
              '    LaunchedEffect(id, vm.dataRevision) {\n        vm.rememberQuestion(id)', 1)
p.write_text(s, encoding='utf-8')
print('detail reload keyed by revision')
