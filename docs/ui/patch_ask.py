import pathlib

p = pathlib.Path('app/src/main/java/com/zhuquan/tijian/ai/AiClient.kt')
s = p.read_text(encoding='utf-8').replace('\r\n', '\n')

anchor = '''    // ---------------- transport ----------------'''
new_fun = '''    /**
     * 题目追问：把题目、解析、需要掌握的知识点和用户当前掌握度一起交给模型，
     * 再附上最近几轮对话。回答使用 Markdown + LaTeX，由应用里的富文本管线渲染。
     */
    suspend fun ask(
        cfg: ProviderConfig,
        context: String,
        history: List<Pair<String, String>>,
        question: String,
        quote: String = "",
    ): String = withContext(Dispatchers.IO) {
        val askText = buildString {
            if (quote.isNotBlank()) {
                append("我在解析里选中的这段内容：\\n> ")
                append(quote.replace("\\n", "\\n> "))
                append("\\n\\n")
            }
            append(question)
        }
        val body = if (cfg.protocol == "responses") {
            val transcript = buildString {
                append("【题目资料】\\n").append(context).append("\\n\\n")
                if (history.isNotEmpty()) {
                    append("【此前的对话】\\n")
                    history.takeLast(8).forEach { (role, text) ->
                        append(if (role == "user") "学生：" else "老师：").append(text).append("\\n")
                    }
                    append("\\n")
                }
                append("【学生现在的问题】\\n").append(askText)
            }
            JSONObject().apply {
                put("model", cfg.model)
                put("input", transcript)
                put("instructions", TUTOR_SYSTEM)
                put("temperature", 0.4)
                put("max_output_tokens", 1600)
            }
        } else {
            val messages = JSONArray()
            messages.put(JSONObject().apply { put("role", "system"); put("content", TUTOR_SYSTEM) })
            messages.put(JSONObject().apply {
                put("role", "user")
                put("content", "【题目资料】\\n$context")
            })
            messages.put(JSONObject().apply {
                put("role", "assistant")
                put("content", "我已读完这道题的题干、答案、解析、需要掌握的知识点，以及你目前的掌握情况。你想先弄懂哪一步？")
            })
            history.takeLast(8).forEach { (role, text) ->
                messages.put(JSONObject().apply { put("role", role); put("content", text) })
            }
            messages.put(JSONObject().apply { put("role", "user"); put("content", askText) })
            JSONObject().apply {
                put("model", cfg.model)
                put("messages", messages)
                put("temperature", 0.4)
                put("max_tokens", 1600)
            }
        }
        call(cfg, body).trim()
    }

    // ---------------- transport ----------------'''

assert anchor in s
s = s.replace(anchor, new_fun, 1)

# add TUTOR_SYSTEM next to SYSTEM
old_sys = '''        private const val SYSTEM = "你是严谨的题目结构化助手。只输出 JSON，不输出解释或 markdown 代码块。"'''
new_sys = '''        private const val SYSTEM = "你是严谨的题目结构化助手。只输出 JSON，不输出解释或 markdown 代码块。"

        private const val TUTOR_SYSTEM = """你是一位耐心、准确的错题辅导老师。学生正在复盘一道已经做错的题。
规则：
1. 只围绕当前这道题回答，不要跑题去讲整章内容。
2. 学生可能只问某一步；如果给了「选中的内容」，优先解释那一段，并指出它在解题中的作用。
3. 结合学生当前的掌握度：mastery 低时把前置概念补出来，mastery 高时直接讲关键卡点。
4. 回答用中文，先给结论或直接答案，再分步解释；控制在 300 字以内，除非学生要求展开。
5. 所有数学公式使用 LaTeX：行内 $...$，独立公式 $$...$$。不要输出纯文本公式。
6. 不要编造题目里没有的条件；不确定就说不确定。"""'''
assert old_sys in s
s = s.replace(old_sys, new_sys, 1)

p.write_text(s, encoding='utf-8')
print('AiClient.ask added')
