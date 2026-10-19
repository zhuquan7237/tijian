package com.zhuquan.tijian.ai

import com.zhuquan.tijian.data.fullName
import com.zhuquan.tijian.data.CategoryRow
import com.zhuquan.tijian.data.DraftQuestion
import com.zhuquan.tijian.data.ProviderConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class AiException(message: String) : Exception(message)

data class ParsedQuestion(
    val no: String = "",
    val type: String = "",
    val stem: String = "",
    val options: List<String> = emptyList(),
    val answer: String = "",
    val analysis: String = "",
    val tags: List<String> = emptyList(),
    val difficulty: Int = 3,
    val category: String = "",
    val confidence: Double = 0.0,
    val bbox: List<Double>? = null,
)

private val client = OkHttpClient.Builder()
    .connectTimeout(20, TimeUnit.SECONDS)
    .readTimeout(240, TimeUnit.SECONDS)
    .writeTimeout(60, TimeUnit.SECONDS)
    .build()

private val JSON = "application/json; charset=utf-8".toMediaType()

class AiClient {

    // ---------------- public API ----------------

    suspend fun listModels(cfg: ProviderConfig): List<String> = withContext(Dispatchers.IO) {
        if (cfg.baseUrl.isBlank()) throw AiException("先填 Base URL")
        val url = normalizeBase(cfg.baseUrl) + "/models"
        val builder = Request.Builder().url(url).get()
        if (cfg.apiKey.isNotBlank()) builder.header("Authorization", "Bearer ${cfg.apiKey}")
        val resp = try {
            client.newCall(builder.build()).execute()
        } catch (e: Exception) {
            throw AiException("网络错误：${e.message ?: e.javaClass.simpleName}")
        }
        resp.use {
            val text = it.body?.string().orEmpty()
            if (!it.isSuccessful) {
                val msg = runCatching {
                    JSONObject(text).optJSONObject("error")?.optString("message")
                }.getOrNull()
                throw AiException("HTTP ${it.code}：${msg ?: text.take(200)}")
            }
            val obj = runCatching { JSONObject(text) }.getOrElse { throw AiException("返回不是 JSON") }
            val arr = obj.optJSONArray("data") ?: obj.optJSONArray("models")
            val out = ArrayList<String>()
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i)
                    val id = item?.optString("id")?.takeIf { it.isNotBlank() }
                        ?: item?.optString("name")?.takeIf { it.isNotBlank() }
                        ?: arr.optString(i).takeIf { it.isNotBlank() }
                    if (id != null) out.add(id)
                }
            } else {
                obj.optJSONArray("models")?.let { a ->
                    for (i in 0 until a.length()) a.optString(i).takeIf { it.isNotBlank() }?.let(out::add)
                }
            }
            out.distinct().sorted()
        }
    }

    private fun normalizeBase(raw: String): String {
        var b = raw.trim().trimEnd('/')
        if (b.endsWith("/chat/completions")) b = b.removeSuffix("/chat/completions")
        if (b.endsWith("/responses")) b = b.removeSuffix("/responses")
        return b
    }

    suspend fun test(cfg: ProviderConfig): String = withContext(Dispatchers.IO) {
        val body = if (cfg.protocol == "responses") {
            JSONObject().apply {
                put("model", cfg.model)
                put("input", "只回复两个字：正常")
                put("max_output_tokens", 20)
            }
        } else {
            JSONObject().apply {
                put("model", cfg.model)
                put("messages", JSONArray().put(JSONObject().apply {
                    put("role", "user"); put("content", "只回复两个字：正常")
                }))
                put("max_tokens", 20)
            }
        }
        val started = System.currentTimeMillis()
        val text = call(cfg, body)
        val ms = System.currentTimeMillis() - started
        "${if (text.isBlank()) "连接成功" else text.take(40)} · ${ms}ms"
    }

    suspend fun recognize(cfg: ProviderConfig, jpeg: ByteArray): List<ParsedQuestion> =
        withContext(Dispatchers.IO) {
            val b64 = android.util.Base64.encodeToString(jpeg, android.util.Base64.NO_WRAP)
            val prompt = RECOGNIZE_PROMPT + "\ncategory必须使用学科/小类格式；学科仅数学、专业课、英语、政治、其他。专业课指材料类，参考书目为材料科学基础（上海交通大学出版社第三版），小类示例：晶体结构、晶体缺陷、固体扩散、相图与相变、金属材料、高分子材料、陶瓷材料、复合材料。无法识别学科用其他/未分类。"
            val body = if (cfg.protocol == "responses") {
                JSONObject().apply {
                    put("model", cfg.model)
                    put("input", JSONArray().put(JSONObject().apply {
                        put("role", "user")
                        put("content", JSONArray()
                            .put(JSONObject().apply { put("type", "input_text"); put("text", prompt) })
                            .put(JSONObject().apply {
                                put("type", "input_image")
                                put("image_url", "data:image/jpeg;base64,$b64")
                            }))
                    }))
                    put("temperature", 0.1)
                    put("max_output_tokens", 8000)
                }
            } else {
                JSONObject().apply {
                    put("model", cfg.model)
                    put("messages", JSONArray()
                        .put(JSONObject().apply { put("role", "system"); put("content", SYSTEM) })
                        .put(JSONObject().apply {
                            put("role", "user")
                            put("content", JSONArray()
                                .put(JSONObject().apply { put("type", "text"); put("text", prompt) })
                                .put(JSONObject().apply {
                                    put("type", "image_url")
                                    put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$b64"))
                                }))
                        }))
                    put("temperature", 0.1)
                    put("max_tokens", 8000)
                }
            }
            val raw = call(cfg, body)
            parseQuestions(raw)
        }

    suspend fun classify(
        cfg: ProviderConfig, drafts: List<DraftQuestion>, cats: List<CategoryRow>
    ): Map<String, String> = withContext(Dispatchers.IO) {
        if (drafts.isEmpty()) return@withContext emptyMap()
        val list = JSONArray()
        drafts.forEachIndexed { i, d ->
            list.put(JSONObject().apply {
                put("id", d.id)
                put("index", i + 1)
                put("type", d.type)
                put("stem", d.stem.take(300))
                put("knowledge", d.tags)
                put("suggested", d.categoryName)
            })
        }
        val catNames = JSONArray()
        cats.filter { it.id != "uncat" }.forEach { catNames.put(it.fullName()) }
        val prompt = """你是错题归档助手。下面是 ${drafts.size} 道题的结构化信息，以及用户现有分类。
为每题选择最合适的分类；确实没有合适的，才提议新分类。

现有分类：$catNames

题目：
$list

规则：
1. 优先复用现有分类，category 必须与给定名称完全一致（必须带学科前缀，例如数学/函数与导数）。
2. 同一批中同类题目必须落到同一分类。
3. 新分类是"可复用的主题"（如"极值点偏移"），不是具体题名；最多提议 2 个新分类。
4. 学科已知但没有合适小类时，填该学科/未分类；无法识别学科才填其他/未分类。不得为了复用分类跨学科归档。
5. 学科仅可为数学、专业课、英语、政治、其他。先识别学科，再选择该学科的小类；无法识别学科归其他/未分类。不要跨学科归档。
6. 专业课只指材料类专业内容，参考书目为《材料科学基础》（上海交通大学出版社，第三版）。材料题应归专业课，小类优先使用晶体结构、晶体缺陷、固体扩散、相图与相变、金属材料、高分子材料、陶瓷材料、复合材料等。

只输出 JSON：{"assignments":[{"id":"q1","category":"数学/函数与导数","confidence":0.88}]}"""

        val body = if (cfg.protocol == "responses") {
            JSONObject().apply {
                put("model", cfg.model)
                put("input", prompt)
                put("temperature", 0.1)
                put("max_output_tokens", 2000)
            }
        } else {
            JSONObject().apply {
                put("model", cfg.model)
                put("messages", JSONArray()
                    .put(JSONObject().apply { put("role", "system"); put("content", SYSTEM) })
                    .put(JSONObject().apply { put("role", "user"); put("content", prompt) }))
                put("temperature", 0.1)
                put("max_tokens", 2000)
            }
        }
        val raw = call(cfg, body)
        val json = JSONObject(extractJson(raw) ?: throw AiException("分类结果不是 JSON"))
        val arr = json.optJSONArray("assignments") ?: JSONArray()
        val map = HashMap<String, String>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id")
            val cat = o.optString("category").trim()
            if (id.isNotBlank() && cat.isNotBlank()) map[id] = cat
        }
        map
    }

    suspend fun knowledge(cfg: ProviderConfig, material: String): String = withContext(Dispatchers.IO) {
        val prompt = """你是知识点整理助手。根据下面的错题材料，提炼成可复习、可朗读的知识点。
要求：
1. 合并重复，按主题分组，3~8 个知识点。
2. 每个知识点包含：title、definition（一句话定义）、key_points（2~4 条要点）、pitfalls（1~3 条易错点）。
3. 不要编造材料里没有的结论；不确定就省略。
4. 所有数学公式必须使用 LaTeX：行内用 $...$，独立公式用 $$...$$；不要输出 x^(3/2)、sqrt(...) 这类纯文本公式。
5. 只输出 JSON，不要 markdown 代码块。

材料：
${material.take(9000)}

输出模板：
{"title":"主题名","points":[{"title":"","definition":"","key_points":[""],"pitfalls":[""]}]}"""
        val body = if (cfg.protocol == "responses") {
            JSONObject().apply {
                put("model", cfg.model); put("input", prompt)
                put("temperature", 0.3); put("max_output_tokens", 8000)
            }
        } else {
            JSONObject().apply {
                put("model", cfg.model)
                put("messages", JSONArray()
                    .put(JSONObject().apply { put("role", "system"); put("content", SYSTEM) })
                    .put(JSONObject().apply { put("role", "user"); put("content", prompt) }))
                put("temperature", 0.3); put("max_tokens", 8000)
            }
        }
        val raw = call(cfg, body)
        val json = extractJson(raw) ?: throw AiException("知识点结果不是 JSON")
        com.zhuquan.tijian.data.knowledgeMarkdown(json)
    }

    // ---------------- transport ----------------

    private fun call(cfg: ProviderConfig, body: JSONObject): String {
        if (cfg.baseUrl.isBlank()) throw AiException("未配置 AI 接口")
        val base = normalizeBase(cfg.baseUrl)
        val url = when {
            base.endsWith("/chat/completions") || base.endsWith("/responses") -> base
            cfg.protocol == "responses" -> "$base/responses"
            else -> "$base/chat/completions"
        }
        val builder = Request.Builder().url(url).post(body.toString().toRequestBody(JSON))
        if (cfg.apiKey.isNotBlank()) builder.header("Authorization", "Bearer ${cfg.apiKey}")
        val resp = try {
            client.newCall(builder.build()).execute()
        } catch (e: Exception) {
            throw AiException("网络错误：${e.message ?: e.javaClass.simpleName}")
        }
        resp.use {
            val text = it.body?.string().orEmpty()
            if (!it.isSuccessful) {
                val msg = runCatching {
                    JSONObject(text).optJSONObject("error")?.optString("message")
                }.getOrNull()
                throw AiException("HTTP ${it.code}：${msg ?: text.take(300)}")
            }
            return extractText(text)
        }
    }

    private fun extractText(raw: String): String {
        val obj = runCatching { JSONObject(raw) }.getOrElse { return raw }
        // responses API
        obj.optString("output_text").takeIf { it.isNotBlank() }?.let { return it }
        obj.optJSONArray("output")?.let { out ->
            val sb = StringBuilder()
            for (i in 0 until out.length()) {
                val item = out.optJSONObject(i) ?: continue
                val content = item.optJSONArray("content") ?: continue
                for (j in 0 until content.length()) {
                    val c = content.optJSONObject(j) ?: continue
                    val t = c.optString("text")
                    if (t.isNotBlank()) sb.append(t)
                }
            }
            if (sb.isNotBlank()) return sb.toString()
        }
        // chat completions
        val choices = obj.optJSONArray("choices")
        if (choices != null && choices.length() > 0) {
            val msg = choices.optJSONObject(0)?.optJSONObject("message")
            if (msg != null) {
                val content = msg.opt("content")
                if (content is String) return content
                if (content is JSONArray) {
                    val sb = StringBuilder()
                    for (i in 0 until content.length()) {
                        val c = content.optJSONObject(i) ?: continue
                        sb.append(c.optString("text"))
                    }
                    return sb.toString()
                }
            }
        }
        return raw
    }

    private fun parseQuestions(raw: String): List<ParsedQuestion> {
        val jsonText = extractJson(raw) ?: throw AiException("模型没有返回 JSON：${raw.take(160)}")
        val root = JSONObject(jsonText)
        val arr = root.optJSONArray("questions") ?: root.optJSONArray("items")
        ?: throw AiException("JSON 里没有 questions 字段")
        val out = ArrayList<ParsedQuestion>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val stem = o.optString("stem").ifBlank { o.optString("question") }
            if (stem.isBlank() && o.optString("type").isBlank()) continue
            val opts = ArrayList<String>()
            o.optJSONArray("options")?.let { a ->
                for (k in 0 until a.length()) opts.add(a.optString(k))
            }
            val tags = ArrayList<String>()
            val tagArr = o.optJSONArray("knowledge_tags") ?: o.optJSONArray("knowledge_points")
            tagArr?.let { a -> for (k in 0 until a.length()) tags.add(a.optString(k)) }
            val bbox = ArrayList<Double>()
            o.optJSONArray("bbox")?.let { a ->
                for (k in 0 until a.length()) bbox.add(a.optDouble(k))
            }
            out.add(
                ParsedQuestion(
                    no = o.optString("no"),
                    type = normalizeType(o.optString("type"), opts, stem),
                    stem = stem,
                    options = opts,
                    answer = o.optString("answer"),
                    analysis = o.optString("analysis"),
                    tags = tags,
                    difficulty = o.optInt("difficulty", 3).coerceIn(1, 5),
                    category = o.optString("category").ifBlank { o.optString("category_path") },
                    confidence = o.optDouble("confidence", 0.0).let { if (it.isNaN()) 0.0 else it },
                    bbox = if (bbox.size == 4) bbox else null
                )
            )
        }
        if (out.isEmpty()) throw AiException("没有识别出题目")
        return out
    }

    private fun normalizeType(raw: String, options: List<String>, stem: String): String {
        val t = raw.trim().replace(" ", "")
        return when {
            t.contains("选择") || t.contains("单选") || t.contains("多选") -> "选择题"
            t.contains("判断") -> "判断题"
            t.contains("填空") -> "填空题"
            t.contains("证明") -> "证明题"
            t.contains("实验") -> "实验题"
            t.contains("材料") -> "材料题"
            t.contains("计算") -> "计算题"
            t.contains("解答") || t.contains("大题") -> "解答题"
            options.size >= 2 -> "选择题"
            stem.contains("证明") -> "证明题"
            else -> t.ifBlank { "题目" }
        }
    }

    companion object {
        private const val SYSTEM = "你是严谨的题目结构化助手。只输出 JSON，不输出解释或 markdown 代码块。"

        val RECOGNIZE_PROMPT = """你是题目数字化助手。输入是一张中文试卷/练习册照片，可能包含多道题。
任务：切分每一道独立题目，逐题输出结构化 JSON。

规则：
1. 只输出 JSON，不要解释，不要 markdown 代码块。
2. 严格按模板；无法确定的字段填空字符串。禁止编造答案与解析——照片里没有就不要写。
3. stem 保留原文与公式（LaTeX 用 ${'$'}...${'$'}），不要把选项混进题干；options 只放选项文本。
4. 手写内容无法辨认时，用 ⟨?⟩ 标记该片段，不要猜。
5. bbox 为 0~1 归一化坐标 [x1,y1,x2,y2]，粗略即可；若整张图只有一题可省略。
6. 跨页/跨栏的同一题，合并为一道。
7. type 只使用：选择题、判断题、填空题、计算题、证明题、解答题、实验题、材料题；无法判断时填“题目”。
8. answer 只写最终答案，analysis 写清楚推理步骤；不要把答案重复粘进解析，也不要把解析塞进 answer。

模板：
{"questions":[{"no":"12","type":"选择题","stem":"...","options":["A. ...","B. ..."],"answer":"","analysis":"","knowledge_tags":["..."],"difficulty":3,"category":"数学/函数与导数","confidence":0.9,"bbox":[0.06,0.31,0.94,0.52]}]}"""

        fun extractJson(text: String): String? {
            var t = text.trim()
            if (t.startsWith("```")) {
                val firstNl = t.indexOf('\n')
                if (firstNl > 0) t = t.substring(firstNl + 1)
                val end = t.lastIndexOf("```")
                if (end > 0) t = t.substring(0, end)
            }
            val start = t.indexOf('{')
            if (start < 0) return null
            var depth = 0
            var inStr = false
            var esc = false
            for (i in start until t.length) {
                val ch = t[i]
                if (inStr) {
                    when {
                        esc -> esc = false
                        ch == '\\' -> esc = true
                        ch == '"' -> inStr = false
                    }
                } else {
                    when (ch) {
                        '"' -> inStr = true
                        '{' -> depth++
                        '}' -> {
                            depth--
                            if (depth == 0) return t.substring(start, i + 1)
                        }
                    }
                }
            }
            return null
        }
    }
}
