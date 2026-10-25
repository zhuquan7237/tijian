package com.zhuquan.tijian.data

import java.security.MessageDigest
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

fun newId(): String {
    val ts = System.currentTimeMillis().toString(36).padStart(8, '0')
    val rnd = UUID.randomUUID().toString().replace("-", "").take(12)
    return ts + rnd
}

data class CategoryRow(
    val id: String,
    val name: String,
    val subject: String,
    val autoCreated: Boolean,
    val status: String,
    val color: String,
    val count: Int,
    val mastered: Int,
)

data class QuestionRow(
    val id: String,
    val categoryId: String?,
    val categoryName: String,
    val type: String,
    val stem: String,
    val options: String,
    val answer: String,
    val analysis: String,
    val tags: String,
    /** 做出这题需要的公式 / 概念 / 方法，均为 JSON 字符串数组。 */
    val needFormulas: String = "[]",
    val needConcepts: String = "[]",
    val needMethods: String = "[]",
    val difficulty: Int,
    val errorReasons: String,
    val source: String,
    val mastery: Int,
    val reviewCount: Int,
    val nextReviewAt: Long,
    val confidence: Double,
    val note: String,
    val imageId: String?,
    val imagePath: String?,
    val createdAt: Long,
)

data class DocRow(
    val id: String,
    val title: String,
    val categoryId: String?,
    val contentMd: String,
    val scriptMd: String,
    val status: String,
    val createdAt: Long,
)

data class Stats(val total: Int, val mastered: Int, val due: Int, val streak: Int)

data class ProviderConfig(
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
    val protocol: String = "chat", // chat | responses
    val models: List<String> = emptyList(), // 上次从 /models 拉到的列表
) {
    fun ready() = baseUrl.isNotBlank() && model.isNotBlank()
}

/** 识别阶段尚未入库的题目草稿 */
data class DraftQuestion(
    val id: String = newId(),
    val imageId: String,
    val imagePath: String,
    val type: String = "",
    val stem: String = "",
    val options: String = "",
    val answer: String = "",
    val analysis: String = "",
    val tags: String = "",
    val needFormulas: String = "[]",
    val needConcepts: String = "[]",
    val needMethods: String = "[]",
    val difficulty: Int = 3,
    val categoryName: String = "",
    val confidence: Double = 0.0,
    val bbox: List<Double>? = null,
)

fun clampMastery(v: Int) = v.coerceIn(0, 5)
fun colorFor(name: String): String {
    val palette = listOf("#E7EAF8", "#EDEAE1", "#E4EFE9", "#F2E7E3", "#E9E6F2", "#E8EEF2", "#F4EDE4")
    // hashCode 可能是 Int.MIN_VALUE，abs 后仍为负，先取模再回正
    val idx = ((name.hashCode() % palette.size) + palette.size) % palette.size
    return palette[idx]
}

/**
 * 题目内容指纹：题干 + 选项 + 答案归一后哈希。
 * 只按题干前缀判重会把「下列说法正确的是」这类通用短题干全部误杀；
 * 空题干（手动录入还没补文字）不参与判重，交给图片 sha256 那一层。
 */
fun contentHash(stem: String, options: String, answer: String): String {
    val s = stem.trim()
    if (s.isEmpty()) return ""
    val key = s + "\u0000" + options.trim() + "\u0000" + answer.trim()
    val md = MessageDigest.getInstance("SHA-256")
    return md.digest(key.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}

/** Human-readable knowledge points from the JSON tag array stored in questions. */
fun parseKnowledgeTags(raw: String): List<String> = try {
    val arr = org.json.JSONArray(raw)
    (0 until arr.length()).mapNotNull { i -> arr.optString(i).trim().takeIf { it.isNotBlank() } }
} catch (_: Exception) { emptyList() }

/** Convert the knowledge model's JSON into stable Markdown regardless of nesting. */
fun knowledgeMarkdown(raw: String): String {
    val obj = JSONObject(raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim())
    val sb = StringBuilder()
    sb.append("# ").append(obj.optString("title").ifBlank { "知识点整理" }).append("\n\n")
    val points = obj.optJSONArray("points") ?: obj.optJSONArray("knowledge_points") ?: JSONArray()
    if (points.length() == 0) obj.optJSONObject("point")?.let { points.put(it) }
    for (i in 0 until points.length()) {
        val p = points.optJSONObject(i) ?: continue
        val title = p.optString("title").ifBlank { p.optString("name") }.ifBlank { "知识点 ${i + 1}" }
        sb.append("## ").append(i + 1).append(". ").append(title).append("\n\n")
        val def = p.optString("definition").ifBlank { p.optString("summary") }
        if (def.isNotBlank()) sb.append(def).append("\n\n")
        val kp = p.optJSONArray("key_points") ?: p.optJSONArray("points")
        if (kp != null && kp.length() > 0) {
            sb.append("**要点**\n")
            for (k in 0 until kp.length()) {
                val v = when (val item = kp.opt(k)) {
                    is String -> item
                    is JSONObject -> item.optString("text").ifBlank { item.optString("content") }
                    else -> kp.optString(k)
                }
                if (v.isNotBlank()) sb.append("- ").append(v.trim()).append("\n")
            }
            sb.append("\n")
        }
        val pf = p.optJSONArray("pitfalls")
        if (pf != null && pf.length() > 0) {
            sb.append("**易错**\n")
            for (k in 0 until pf.length()) {
                val v = when (val item = pf.opt(k)) {
                    is String -> item
                    is JSONObject -> item.optString("text").ifBlank { item.optString("content") }
                    else -> pf.optString(k)
                }
                if (v.isNotBlank()) sb.append("- ").append(v.trim()).append("\n")
            }
            sb.append("\n")
        }
    }
    return sb.toString().trim()
}

val SUBJECT_ROOTS = listOf("数学", "专业课", "英语", "政治", "其他")

/** 材料类专业课题库常用小类；AI 也会持续补充。 */
val MATERIAL_SUBCATEGORIES = listOf(
    "晶体结构", "晶体缺陷", "固体扩散", "相图与相变", "金属材料",
    "高分子材料", "陶瓷材料", "复合材料", "材料力学性能", "材料化学"
)

fun subjectRoot(subject: String): String = when (subject.trim()) {
    "数学", "高等数学", "线性代数", "概率论" -> "数学"
    "专业课", "材料", "材料科学", "材料科学与工程", "物理", "化学", "生物", "计算机", "数据结构" -> "专业课"
    "英语", "英文" -> "英语"
    "政治", "思想政治" -> "政治"
    else -> "其他"
}
/** 单段分类名（AI 返回的“函数与导数”）按关键词推断学科。 */
fun inferSubject(name: String): String = when {
    listOf("数学", "函数", "极限", "导数", "积分", "代数", "几何", "概率", "数列", "矩阵", "三角", "不等式")
        .any { name.contains(it) } -> "数学"
    listOf("英语", "词汇", "语法", "英文", "阅读", "写作", "翻译", "完形")
        .any { name.contains(it) } -> "英语"
    listOf("政治", "马原", "毛概", "思修", "近代史", "时事", "哲学")
        .any { name.contains(it) } -> "政治"
    listOf("材料", "晶体", "扩散", "相图", "相变", "位错", "陶瓷", "高分子", "复合", "物理", "化学", "力学")
        .any { name.contains(it) } -> "专业课"
    else -> "其他"
}

/**
 * 学科归属：优先使用显式 subject；若它缺失或只落到兜底的“其他”，
 * 再用分类名推断 —— 否则 AI 新建的“函数与导数”会一直挂在“其他”下。
 */
fun CategoryRow.subjectGroup(): String {
    val declared = if (subject.isBlank()) "其他" else subjectRoot(subject)
    if (declared != "其他") return declared
    return inferSubject(name)
}
fun CategoryRow.fullName(): String = subjectGroup() + "/" + name
