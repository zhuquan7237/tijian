package com.zhuquan.tijian.data

import org.json.JSONArray
import org.json.JSONObject

/** 复习进度只写 setting 表，不改题目表。进程被杀后可以接着做。 */
data class ReviewSessionState(
    val mode: String,
    val index: Int,
    val reveal: Boolean,
    val ids: List<String>,
    val wrong: List<String>,
)

fun encodeReviewSession(state: ReviewSessionState): String = JSONObject().apply {
    put("mode", state.mode)
    put("index", state.index)
    put("reveal", state.reveal)
    put("ids", JSONArray(state.ids))
    put("wrong", JSONArray(state.wrong))
}.toString()

fun decodeReviewSession(raw: String): ReviewSessionState? {
    if (raw.isBlank()) return null
    return try {
        val obj = JSONObject(raw)
        val idsArr = obj.optJSONArray("ids") ?: return null
        val ids = (0 until idsArr.length()).map { idsArr.optString(it) }.filter { it.isNotBlank() }
        if (ids.isEmpty()) return null
        val wrongArr = obj.optJSONArray("wrong")
        val wrong = if (wrongArr == null) emptyList() else
            (0 until wrongArr.length()).map { wrongArr.optString(it) }.filter { it.isNotBlank() }
        ReviewSessionState(
            mode = obj.optString("mode").ifBlank { "DUE" },
            index = obj.optInt("index").coerceAtLeast(0),
            reveal = obj.optBoolean("reveal"),
            ids = ids,
            wrong = wrong,
        )
    } catch (_: Exception) {
        null
    }
}
