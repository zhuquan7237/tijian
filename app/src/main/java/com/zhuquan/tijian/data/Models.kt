package com.zhuquan.tijian.data

import java.util.UUID
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
    val difficulty: Int = 3,
    val categoryName: String = "",
    val confidence: Double = 0.0,
    val bbox: List<Double>? = null,
)

fun clampMastery(v: Int) = v.coerceIn(0, 5)
fun colorFor(name: String): String {
    val palette = listOf("#E7EAF8", "#EDEAE1", "#E4EFE9", "#F2E7E3", "#E9E6F2", "#E8EEF2", "#F4EDE4")
    return palette[abs(name.hashCode()) % palette.size]
}
