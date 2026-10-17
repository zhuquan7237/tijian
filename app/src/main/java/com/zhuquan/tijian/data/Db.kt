package com.zhuquan.tijian.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class Db(context: Context) : SQLiteOpenHelper(context, "tijian.db", null, 1) {

    companion object {
        const val UNCAT = "uncat"
        private const val SUBJECTS = "语文,数学,英语,物理,化学,生物,政治,历史,地理"
    }

    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE category(
                id TEXT PRIMARY KEY, name TEXT NOT NULL, subject TEXT DEFAULT '',
                auto_created INTEGER NOT NULL DEFAULT 0, status TEXT NOT NULL DEFAULT 'confirmed',
                color TEXT DEFAULT '', sort INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, deleted_at INTEGER)"""
        )
        db.execSQL(
            """CREATE TABLE image(
                id TEXT PRIMARY KEY, sha256 TEXT NOT NULL UNIQUE, path TEXT NOT NULL,
                width INTEGER NOT NULL DEFAULT 0, height INTEGER NOT NULL DEFAULT 0,
                bytes INTEGER NOT NULL DEFAULT 0, created_at INTEGER NOT NULL)"""
        )
        db.execSQL(
            """CREATE TABLE batch(
                id TEXT PRIMARY KEY, source TEXT, image_count INTEGER NOT NULL DEFAULT 0,
                status TEXT NOT NULL DEFAULT 'queued', model TEXT,
                tokens_in INTEGER NOT NULL DEFAULT 0, tokens_out INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL)"""
        )
        db.execSQL(
            """CREATE TABLE question(
                id TEXT PRIMARY KEY, batch_id TEXT, subject TEXT DEFAULT '',
                category_id TEXT, type TEXT DEFAULT '', stem TEXT DEFAULT '',
                options TEXT DEFAULT '[]', answer TEXT DEFAULT '', analysis TEXT DEFAULT '',
                knowledge_tags TEXT DEFAULT '[]', difficulty INTEGER NOT NULL DEFAULT 3,
                error_reasons TEXT DEFAULT '[]', source TEXT DEFAULT '',
                captured_at INTEGER, mastery INTEGER NOT NULL DEFAULT 0,
                review_count INTEGER NOT NULL DEFAULT 0,
                last_review_at INTEGER, next_review_at INTEGER,
                ai_confidence REAL NOT NULL DEFAULT 0, note TEXT DEFAULT '',
                image_id TEXT, content_hash TEXT,
                created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, deleted_at INTEGER)"""
        )
        db.execSQL("CREATE INDEX idx_q_cat ON question(category_id, deleted_at)")
        db.execSQL("CREATE INDEX idx_q_due ON question(next_review_at)")
        db.execSQL(
            """CREATE TABLE review_log(
                id TEXT PRIMARY KEY, question_id TEXT NOT NULL, rating INTEGER NOT NULL,
                mode TEXT, duration_ms INTEGER NOT NULL DEFAULT 0, reviewed_at INTEGER NOT NULL)"""
        )
        db.execSQL(
            """CREATE TABLE knowledge_doc(
                id TEXT PRIMARY KEY, title TEXT NOT NULL, category_id TEXT,
                source_refs TEXT DEFAULT '[]', content_md TEXT DEFAULT '',
                script_md TEXT DEFAULT '', status TEXT NOT NULL DEFAULT 'ready',
                created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, deleted_at INTEGER)"""
        )
        db.execSQL("CREATE TABLE setting(k TEXT PRIMARY KEY, v TEXT NOT NULL)")

        val now = System.currentTimeMillis()
        db.insert("category", null, ContentValues().apply {
            put("id", UNCAT); put("name", "未分类"); put("subject", "")
            put("auto_created", 0); put("status", "confirmed"); put("color", "#F0F0EA")
            put("sort", 999); put("created_at", now); put("updated_at", now)
        })
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    // ---------- categories ----------

    fun categories(): List<CategoryRow> {
        val sql = """SELECT c.id, c.name, c.subject, c.auto_created, c.status, c.color,
            (SELECT COUNT(*) FROM question q WHERE q.category_id=c.id AND q.deleted_at IS NULL) cnt,
            (SELECT COUNT(*) FROM question q WHERE q.category_id=c.id AND q.deleted_at IS NULL AND q.mastery>=4) mast
            FROM category c WHERE c.deleted_at IS NULL
            ORDER BY (c.id='$UNCAT'), c.sort, c.name"""
        return read(sql) { c ->
            CategoryRow(
                id = c.getString(0), name = c.getString(1), subject = c.getString(2),
                autoCreated = c.getInt(3) == 1, status = c.getString(4),
                color = c.getString(5).ifBlank { colorFor(c.getString(1)) },
                count = c.getInt(6), mastered = c.getInt(7)
            )
        }
    }

    fun category(id: String): CategoryRow? =
        categories().firstOrNull { it.id == id }

    fun ensureCategory(rawName: String, auto: Boolean = true): String {
        var name = rawName.trim()
        if (name.isBlank()) return UNCAT
        // "数学/函数与导数" -> "函数与导数"；同时把学科记到 subject
        var subject = ""
        val parts = name.split('/', '·', '>').map { it.trim() }.filter { it.isNotBlank() }
        if (parts.size >= 2) {
            if (SUBJECTS.split(",").contains(parts[0])) subject = parts[0]
            name = parts.last()
        }
        val existing = read("SELECT id, name FROM category WHERE deleted_at IS NULL") { c ->
            c.getString(0) to c.getString(1)
        }.firstOrNull { normalize(it.second) == normalize(name) }
        if (existing != null) return existing.first
        val id = newId()
        val now = System.currentTimeMillis()
        writableDatabase.insert("category", null, ContentValues().apply {
            put("id", id); put("name", name); put("subject", subject)
            put("auto_created", if (auto) 1 else 0)
            put("status", if (auto) "pending" else "confirmed")
            put("color", colorFor(name)); put("sort", 100)
            put("created_at", now); put("updated_at", now)
        })
        return id
    }

    fun createCategory(name: String, subject: String = ""): String {
        val id = newId()
        val now = System.currentTimeMillis()
        writableDatabase.insert("category", null, ContentValues().apply {
            put("id", id); put("name", name.trim()); put("subject", subject)
            put("auto_created", 0); put("status", "confirmed")
            put("color", colorFor(name)); put("sort", 100)
            put("created_at", now); put("updated_at", now)
        })
        return id
    }

    fun confirmCategory(id: String) = writableDatabase.execSQL(
        "UPDATE category SET status='confirmed', updated_at=? WHERE id=?",
        arrayOf(System.currentTimeMillis(), id)
    )

    fun renameCategory(id: String, name: String) = writableDatabase.execSQL(
        "UPDATE category SET name=?, updated_at=? WHERE id=?",
        arrayOf(name.trim(), System.currentTimeMillis(), id)
    )

    fun deleteCategory(id: String) {
        if (id == UNCAT) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.execSQL(
                "UPDATE question SET category_id=?, updated_at=? WHERE category_id=?",
                arrayOf(UNCAT, System.currentTimeMillis(), id)
            )
            db.execSQL(
                "UPDATE category SET deleted_at=?, updated_at=? WHERE id=?",
                arrayOf(System.currentTimeMillis(), System.currentTimeMillis(), id)
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun mergeCategory(fromId: String, intoId: String) {
        if (fromId == intoId || intoId == UNCAT) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.execSQL(
                "UPDATE question SET category_id=?, updated_at=? WHERE category_id=?",
                arrayOf(intoId, System.currentTimeMillis(), fromId)
            )
            db.execSQL(
                "UPDATE category SET deleted_at=?, updated_at=? WHERE id=?",
                arrayOf(System.currentTimeMillis(), System.currentTimeMillis(), fromId)
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    // ---------- questions ----------

    fun questions(categoryId: String? = null, filter: String = "all", query: String = ""): List<QuestionRow> {
        val where = StringBuilder("q.deleted_at IS NULL")
        val args = ArrayList<String>()
        if (categoryId != null) { where.append(" AND q.category_id=?"); args.add(categoryId) }
        when (filter) {
            "weak" -> where.append(" AND q.mastery<4")
            "week" -> where.append(" AND q.created_at>=?").also {
                args.add((System.currentTimeMillis() - 7L * 86400_000).toString())
            }
            "errors" -> where.append(" AND q.error_reasons IS NOT NULL AND q.error_reasons!='[]'")
        }
        if (query.isNotBlank()) {
            where.append(" AND (q.stem LIKE ? OR q.knowledge_tags LIKE ?)")
            args.add("%$query%"); args.add("%$query%")
        }
        return queryQuestions(where.toString(), args.toTypedArray(), "q.created_at DESC")
    }

    fun allQuestions(): List<QuestionRow> = questions()

    fun dueQuestions(limit: Int = 30): List<QuestionRow> =
        queryQuestions(
            "q.deleted_at IS NULL AND q.next_review_at IS NOT NULL AND q.next_review_at<=?",
            arrayOf(System.currentTimeMillis().toString()),
            "q.next_review_at ASC LIMIT $limit"
        )

    fun question(id: String): QuestionRow? =
        queryQuestions("q.id=?", arrayOf(id), "q.created_at DESC").firstOrNull()

    private fun queryQuestions(where: String, args: Array<String>, order: String): List<QuestionRow> {
        val sql = """SELECT q.id, q.category_id, IFNULL(c.name,'未分类'), q.type, q.stem, q.options,
            q.answer, q.analysis, q.knowledge_tags, q.difficulty, q.error_reasons, q.source,
            q.mastery, q.review_count, IFNULL(q.next_review_at,0), q.ai_confidence, q.note,
            q.image_id, i.path, q.created_at
            FROM question q LEFT JOIN category c ON c.id=q.category_id
            LEFT JOIN image i ON i.id=q.image_id
            WHERE $where ORDER BY $order"""
        return read(sql, args) { c ->
            QuestionRow(
                id = c.getString(0), categoryId = c.getString(1), categoryName = c.getString(2),
                type = c.getString(3), stem = c.getString(4), options = c.getString(5),
                answer = c.getString(6), analysis = c.getString(7), tags = c.getString(8),
                difficulty = c.getInt(9), errorReasons = c.getString(10), source = c.getString(11),
                mastery = c.getInt(12), reviewCount = c.getInt(13), nextReviewAt = c.getLong(14),
                confidence = c.getDouble(15), note = c.getString(16),
                imageId = c.getString(17), imagePath = c.getString(18), createdAt = c.getLong(19)
            )
        }
    }

    fun insertQuestion(
        draft: DraftQuestion, categoryId: String, batchId: String?, subject: String,
        errorReasons: String = "[]", source: String = ""
    ): String {
        val id = newId()
        val now = System.currentTimeMillis()
        writableDatabase.insert("question", null, ContentValues().apply {
            put("id", id); put("batch_id", batchId); put("subject", subject)
            put("category_id", categoryId); put("type", draft.type); put("stem", draft.stem)
            put("options", draft.options); put("answer", draft.answer); put("analysis", draft.analysis)
            put("knowledge_tags", draft.tags); put("difficulty", draft.difficulty)
            put("error_reasons", errorReasons); put("source", source)
            put("captured_at", now); put("mastery", 0); put("review_count", 0)
            put("next_review_at", now); put("ai_confidence", draft.confidence)
            put("image_id", draft.imageId); put("created_at", now); put("updated_at", now)
        })
        return id
    }

    fun updateQuestion(
        id: String, stem: String, answer: String, analysis: String, type: String,
        tags: String, note: String, errorReasons: String, categoryId: String
    ) = writableDatabase.execSQL(
        """UPDATE question SET stem=?, answer=?, analysis=?, type=?, knowledge_tags=?,
           note=?, error_reasons=?, category_id=?, updated_at=? WHERE id=?""",
        arrayOf(stem, answer, analysis, type, tags, note, errorReasons, categoryId, System.currentTimeMillis(), id)
    )

    fun deleteQuestion(id: String) = writableDatabase.execSQL(
        "UPDATE question SET deleted_at=?, updated_at=? WHERE id=?",
        arrayOf(System.currentTimeMillis(), System.currentTimeMillis(), id)
    )

    fun review(id: String, rating: Int, mode: String = "card") {
        val q = question(id) ?: return
        val now = System.currentTimeMillis()
        var mastery = q.mastery
        var interval = 0L
        when (rating) {
            0 -> { mastery = (mastery - 1).coerceAtLeast(0); interval = 1 }
            1 -> { interval = maxOf(1, INTERVALS[mastery] / 2).toLong() }
            else -> { mastery = (mastery + 1).coerceAtMost(5); interval = INTERVALS[mastery].toLong() }
        }
        val next = now + interval * 86400_000L
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.execSQL(
                "UPDATE question SET mastery=?, review_count=review_count+1, last_review_at=?, next_review_at=?, updated_at=? WHERE id=?",
                arrayOf(mastery, now, next, now, id)
            )
            db.insert("review_log", null, ContentValues().apply {
                put("id", newId()); put("question_id", id); put("rating", rating)
                put("mode", mode); put("duration_ms", 0); put("reviewed_at", now)
            })
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun setMastery(id: String, value: Int) = writableDatabase.execSQL(
        "UPDATE question SET mastery=?, updated_at=? WHERE id=?",
        arrayOf(clampMastery(value), System.currentTimeMillis(), id)
    )

    fun stats(): Stats {
        val total = scalar("SELECT COUNT(*) FROM question WHERE deleted_at IS NULL")
        val mastered = scalar("SELECT COUNT(*) FROM question WHERE deleted_at IS NULL AND mastery>=4")
        val due = scalar("SELECT COUNT(*) FROM question WHERE deleted_at IS NULL AND next_review_at IS NOT NULL AND next_review_at<=${System.currentTimeMillis()}")
        val streak = scalar(
            "SELECT COUNT(DISTINCT date(reviewed_at/1000,'unixepoch','localtime')) FROM review_log WHERE reviewed_at>=${System.currentTimeMillis() - 7L * 86400_000}"
        )
        return Stats(total, mastered, due, streak)
    }

    private fun scalar(sql: String): Int = readableDatabase.rawQuery(sql, null).use {
        if (it.moveToFirst()) it.getInt(0) else 0
    }

    // ---------- images ----------

    fun insertImage(id: String, sha: String, path: String, w: Int, h: Int, bytes: Long): Boolean {
        val existing = read("SELECT id FROM image WHERE sha256=?", arrayOf(sha)) { it.getString(0) }.firstOrNull()
        if (existing != null) return false
        writableDatabase.insert("image", null, ContentValues().apply {
            put("id", id); put("sha256", sha); put("path", path)
            put("width", w); put("height", h); put("bytes", bytes)
            put("created_at", System.currentTimeMillis())
        })
        return true
    }

    fun imagePath(id: String?): String? = if (id == null) null else read(
        "SELECT path FROM image WHERE id=?", arrayOf(id)
    ) { it.getString(0) }.firstOrNull()

    fun imageIdBySha(sha: String): String? = read(
        "SELECT id FROM image WHERE sha256=?", arrayOf(sha)
    ) { it.getString(0) }.firstOrNull()

    fun allImages(): List<Triple<String, String, String>> = read(
        "SELECT id, sha256, path FROM image"
    ) { Triple(it.getString(0), it.getString(1), it.getString(2)) }

    // ---------- batch ----------

    fun createBatch(source: String, imageCount: Int): String {
        val id = newId()
        writableDatabase.insert("batch", null, ContentValues().apply {
            put("id", id); put("source", source); put("image_count", imageCount)
            put("status", "running"); put("created_at", System.currentTimeMillis())
        })
        return id
    }

    fun finishBatch(id: String, status: String, model: String) = writableDatabase.execSQL(
        "UPDATE batch SET status=?, model=? WHERE id=?", arrayOf(status, model, id)
    )

    // ---------- docs ----------

    fun docs(): List<DocRow> = read(
        "SELECT id,title,category_id,content_md,script_md,status,created_at FROM knowledge_doc WHERE deleted_at IS NULL ORDER BY created_at DESC"
    ) { c ->
        DocRow(c.getString(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4), c.getString(5), c.getLong(6))
    }

    fun doc(id: String): DocRow? = docs().firstOrNull { it.id == id }

    fun insertDoc(title: String, categoryId: String?, sourceRefs: String, content: String): String {
        val id = newId(); val now = System.currentTimeMillis()
        writableDatabase.insert("knowledge_doc", null, ContentValues().apply {
            put("id", id); put("title", title); put("category_id", categoryId)
            put("source_refs", sourceRefs); put("content_md", content)
            put("script_md", ""); put("status", "ready"); put("created_at", now); put("updated_at", now)
        })
        return id
    }

    fun deleteDoc(id: String) = writableDatabase.execSQL(
        "UPDATE knowledge_doc SET deleted_at=? WHERE id=?",
        arrayOf(System.currentTimeMillis(), id)
    )

    // ---------- settings ----------

    fun getSetting(key: String): String? = read(
        "SELECT v FROM setting WHERE k=?", arrayOf(key)
    ) { it.getString(0) }.firstOrNull()

    fun putSetting(key: String, value: String) {
        writableDatabase.insertWithOnConflict(
            "setting", null,
            ContentValues().apply { put("k", key); put("v", value) },
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun provider(): ProviderConfig = ProviderConfig(
        baseUrl = getSetting("ai.baseUrl") ?: "",
        apiKey = getSetting("ai.apiKey") ?: "",
        model = getSetting("ai.model") ?: "",
        protocol = getSetting("ai.protocol") ?: "chat",
    )

    fun saveProvider(cfg: ProviderConfig) {
        putSetting("ai.baseUrl", cfg.baseUrl.trim())
        putSetting("ai.apiKey", cfg.apiKey.trim())
        putSetting("ai.model", cfg.model.trim())
        putSetting("ai.protocol", cfg.protocol)
    }

    // ---------- helpers ----------

    fun <T> read(sql: String, args: Array<String> = emptyArray(), map: (Cursor) -> T): List<T> {
        val out = ArrayList<T>()
        readableDatabase.rawQuery(sql, if (args.isEmpty()) null else args).use { c ->
            while (c.moveToNext()) out.add(map(c))
        }
        return out
    }
}

private val INTERVALS = intArrayOf(0, 1, 3, 7, 16, 35)
private fun normalize(s: String) = s.trim().lowercase().replace(Regex("[\\s·•\\-_/（）()【】\\[\\]]"), "")
