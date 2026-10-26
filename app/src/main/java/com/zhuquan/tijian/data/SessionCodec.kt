package com.zhuquan.tijian.data

import org.json.JSONArray
import org.json.JSONObject

/** Explicit, versioned draft format; no API credentials or Java serialization. */
object SessionCodec {
    fun encodeDrafts(drafts: List<DraftQuestion>): String = JSONArray().apply {
        drafts.forEach { d -> put(JSONObject().apply {
            put("id", d.id); put("imageId", d.imageId); put("imagePath", d.imagePath)
            put("type", d.type); put("stem", d.stem); put("options", d.options)
            put("answer", d.answer); put("analysis", d.analysis); put("tags", d.tags)
            put("needFormulas", d.needFormulas); put("needConcepts", d.needConcepts); put("needMethods", d.needMethods)
            put("difficulty", d.difficulty); put("categoryName", d.categoryName); put("customTag", d.customTag)
            put("confidence", d.confidence); d.bbox?.let { put("bbox", JSONArray(it)) }
        }) }
    }.toString()

    fun decodeDrafts(raw: String): List<DraftQuestion> {
        val arr = JSONArray(raw)
        return (0 until arr.length()).map { i ->
            val d = arr.getJSONObject(i)
            DraftQuestion(id = d.getString("id"), imageId = d.optString("imageId"), imagePath = d.optString("imagePath"),
                type = d.optString("type"), stem = d.optString("stem"), options = d.optString("options"),
                answer = d.optString("answer"), analysis = d.optString("analysis"), tags = d.optString("tags"),
                needFormulas = d.optString("needFormulas", "[]"), needConcepts = d.optString("needConcepts", "[]"),
                needMethods = d.optString("needMethods", "[]"), difficulty = d.optInt("difficulty", 3),
                categoryName = d.optString("categoryName"), customTag = d.optString("customTag"),
                confidence = d.optDouble("confidence", 0.0),
                bbox = d.optJSONArray("bbox")?.let { b -> (0 until b.length()).map { b.getDouble(it) } })
        }
    }
}

fun reviewDays(mastery: Int, rating: Int): Int = when (rating) {
    0 -> 1
    1 -> maxOf(1, listOf(0, 1, 3, 7, 16, 35)[mastery.coerceIn(0, 5)] / 2)
    else -> listOf(0, 1, 3, 7, 16, 35)[(mastery + 1).coerceIn(0, 5)]
}
