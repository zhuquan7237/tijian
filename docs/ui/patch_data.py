import pathlib
import re

ROOT = pathlib.Path('app/src/main/java/com/zhuquan/tijian')


def edit(path, pairs):
    p = ROOT / path
    s = p.read_text(encoding='utf-8').replace('\r\n', '\n')
    for old, new in pairs:
        assert old in s, f"{path}: NOT FOUND {old[:80]}"
        s = s.replace(old, new, 1)
    p.write_text(s, encoding='utf-8')
    print('patched', path)


# ---------------- Models ----------------
edit('data/Models.kt', [
    ('''    val tags: String,
    val difficulty: Int,''',
     '''    val tags: String,
    /** 做出这题需要的公式 / 概念 / 方法，均为 JSON 字符串数组。 */
    val needFormulas: String,
    val needConcepts: String,
    val needMethods: String,
    val difficulty: Int,'''),
    ('''    val tags: String = "",
    val difficulty: Int = 3,''',
     '''    val tags: String = "",
    val needFormulas: String = "[]",
    val needConcepts: String = "[]",
    val needMethods: String = "[]",
    val difficulty: Int = 3,'''),
])

# ---------------- Db ----------------
edit('data/Db.kt', [
    ('class Db(context: Context, databaseName: String = "tijian.db") : SQLiteOpenHelper(context, databaseName, null, 3) {',
     'class Db(context: Context, databaseName: String = "tijian.db") : SQLiteOpenHelper(context, databaseName, null, 4) {'),
    ('''                knowledge_tags TEXT DEFAULT '[]', difficulty INTEGER NOT NULL DEFAULT 3,''',
     '''                knowledge_tags TEXT DEFAULT '[]',
                need_formulas TEXT DEFAULT '[]', need_concepts TEXT DEFAULT '[]',
                need_methods TEXT DEFAULT '[]', difficulty INTEGER NOT NULL DEFAULT 3,'''),
    ('''        db.execSQL("CREATE TABLE setting(k TEXT PRIMARY KEY, v TEXT NOT NULL)")''',
     '''        db.execSQL("CREATE TABLE setting(k TEXT PRIMARY KEY, v TEXT NOT NULL)")
        db.execSQL(
            """CREATE TABLE chat_message(
                id TEXT PRIMARY KEY, question_id TEXT NOT NULL, role TEXT NOT NULL,
                content TEXT NOT NULL, quote TEXT DEFAULT '', created_at INTEGER NOT NULL)"""
        )
        db.execSQL("CREATE INDEX idx_chat_q ON chat_message(question_id, created_at)")'''),
    ('''        if (oldVersion < 3) {
            val hasCategory = db.rawQuery(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='category'", null
            ).use { if (it.moveToFirst()) it.getInt(0) > 0 else false }
            if (hasCategory) seedMaterialCategories(db)
        }''',
     '''        if (oldVersion < 3) {
            val hasCategory = db.rawQuery(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='category'", null
            ).use { if (it.moveToFirst()) it.getInt(0) > 0 else false }
            if (hasCategory) seedMaterialCategories(db)
        }
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE question ADD COLUMN need_formulas TEXT DEFAULT '[]'")
            db.execSQL("ALTER TABLE question ADD COLUMN need_concepts TEXT DEFAULT '[]'")
            db.execSQL("ALTER TABLE question ADD COLUMN need_methods TEXT DEFAULT '[]'")
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS chat_message(
                    id TEXT PRIMARY KEY, question_id TEXT NOT NULL, role TEXT NOT NULL,
                    content TEXT NOT NULL, quote TEXT DEFAULT '', created_at INTEGER NOT NULL)"""
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_chat_q ON chat_message(question_id, created_at)")
        }'''),
    ('''        val sql = """SELECT q.id, q.category_id, IFNULL(c.name,'未分类'), q.type, q.stem, q.options,
            q.answer, q.analysis, q.knowledge_tags, q.difficulty, q.error_reasons, q.source,
            q.mastery, q.review_count, IFNULL(q.next_review_at,0), q.ai_confidence, q.note,
            q.image_id, i.path, q.created_at
            FROM question q LEFT JOIN category c ON c.id=q.category_id''',
     '''        val sql = """SELECT q.id, q.category_id, IFNULL(c.name,'未分类'), q.type, q.stem, q.options,
            q.answer, q.analysis, q.knowledge_tags, q.difficulty, q.error_reasons, q.source,
            q.mastery, q.review_count, IFNULL(q.next_review_at,0), q.ai_confidence, q.note,
            q.image_id, i.path, q.created_at, q.need_formulas, q.need_concepts, q.need_methods
            FROM question q LEFT JOIN category c ON c.id=q.category_id'''),
    ('''                imageId = c.getString(17), imagePath = c.getString(18), createdAt = c.getLong(19)
            )''',
     '''                imageId = c.getString(17), imagePath = c.getString(18), createdAt = c.getLong(19),
                needFormulas = c.getString(20) ?: "[]",
                needConcepts = c.getString(21) ?: "[]",
                needMethods = c.getString(22) ?: "[]"
            )'''),
    ('''            put("knowledge_tags", draft.tags); put("difficulty", draft.difficulty)''',
     '''            put("knowledge_tags", draft.tags); put("difficulty", draft.difficulty)
            put("need_formulas", draft.needFormulas)
            put("need_concepts", draft.needConcepts)
            put("need_methods", draft.needMethods)'''),
    ('''    fun deleteQuestion(id: String) = writableDatabase.execSQL(''',
     '''    fun updateQuestionNeeds(id: String, formulas: String, concepts: String, methods: String) =
        writableDatabase.execSQL(
            "UPDATE question SET need_formulas=?, need_concepts=?, need_methods=?, updated_at=? WHERE id=?",
            arrayOf(formulas, concepts, methods, System.currentTimeMillis(), id)
        )

    fun deleteQuestion(id: String) = writableDatabase.execSQL('''),
    ('''    fun deleteDoc(id: String) = writableDatabase.execSQL(
        "UPDATE knowledge_doc SET deleted_at=? WHERE id=?",
        arrayOf(System.currentTimeMillis(), id)
    )''',
     '''    fun deleteDoc(id: String) = writableDatabase.execSQL(
        "UPDATE knowledge_doc SET deleted_at=? WHERE id=?",
        arrayOf(System.currentTimeMillis(), id)
    )

    fun updateDoc(id: String, title: String, content: String) = writableDatabase.execSQL(
        "UPDATE knowledge_doc SET title=?, content_md=?, updated_at=? WHERE id=?",
        arrayOf(title.trim(), content, System.currentTimeMillis(), id)
    )

    // ---------- chat ----------

    fun chatMessages(questionId: String): List<Pair<String, String>> = read(
        "SELECT role, content FROM chat_message WHERE question_id=? ORDER BY created_at ASC",
        arrayOf(questionId)
    ) { it.getString(0) to it.getString(1) }

    fun addChatMessage(questionId: String, role: String, content: String, quote: String = "") {
        writableDatabase.insert("chat_message", null, ContentValues().apply {
            put("id", newId()); put("question_id", questionId); put("role", role)
            put("content", content); put("quote", quote); put("created_at", System.currentTimeMillis())
        })
    }

    fun clearChat(questionId: String) = writableDatabase.execSQL(
        "DELETE FROM chat_message WHERE question_id=?", arrayOf(questionId)
    )'''),
])

# ---------------- AiClient ----------------
edit('ai/AiClient.kt', [
    ('''    val tags: List<String> = emptyList(),
    val difficulty: Int = 3,''',
     '''    val tags: List<String> = emptyList(),
    val needFormulas: List<String> = emptyList(),
    val needConcepts: List<String> = emptyList(),
    val needMethods: List<String> = emptyList(),
    val difficulty: Int = 3,'''),
    ('''8. answer 只写最终答案，analysis 写清楚推理步骤；不要把答案重复粘进解析，也不要把解析塞进 answer。''',
     '''8. answer 只写最终答案，analysis 写清楚推理步骤；不要把答案重复粘进解析，也不要把解析塞进 answer。
9. need_formulas / need_concepts / need_methods 分别列出"要独立做出这道题必须掌握"的公式、概念、方法，各 0~4 条，每条一句话，可用 LaTeX；只写本题真正需要的内容，不要泛泛而谈。'''),
    ('''{"questions":[{"no":"12","type":"选择题","stem":"...","options":["A. ...","B. ..."],"answer":"","analysis":"","knowledge_tags":["..."],"difficulty":3,"category":"数学/函数与导数","confidence":0.9,"bbox":[0.06,0.31,0.94,0.52]}]}''',
     '''{"questions":[{"no":"12","type":"选择题","stem":"...","options":["A. ...","B. ..."],"answer":"","analysis":"","knowledge_tags":["..."],"need_formulas":["导数的几何意义 $k=f'(x_0)$"],"need_concepts":["切点既在曲线上又在切线上"],"need_methods":["点斜式求切线方程"],"difficulty":3,"category":"数学/函数与导数","confidence":0.9,"bbox":[0.06,0.31,0.94,0.52]}]}'''),
    ('''            val tags = ArrayList<String>()''',
     '''            fun strList(vararg keys: String): List<String> {
                for (k in keys) {
                    val a = o.optJSONArray(k) ?: continue
                    val list = ArrayList<String>()
                    for (n in 0 until a.length()) {
                        val v = a.optString(n).trim()
                        if (v.isNotBlank()) list.add(v)
                    }
                    if (list.isNotEmpty()) return list
                }
                return emptyList()
            }
            val tags = ArrayList<String>()'''),
    ('''                    tags = tags,
                    difficulty = o.optInt("difficulty", 3).coerceIn(1, 5),''',
     '''                    tags = tags,
                    needFormulas = strList("need_formulas", "required_formulas"),
                    needConcepts = strList("need_concepts", "required_concepts"),
                    needMethods = strList("need_methods", "required_methods"),
                    difficulty = o.optInt("difficulty", 3).coerceIn(1, 5),'''),
])
print('data layer done')
