package com.zhuquan.tijian.data

import java.util.Calendar
import java.util.TimeZone

/** 每日队列的排序依据。标星优先，其次更早到期的。已掌握和不重要的不占名额。 */
data class QueueCandidate(
    val id: String,
    val starred: Boolean,
    val mastery: Int,
    val unimportant: Boolean,
    val nextReviewAt: Long,
)

fun selectDailyIds(items: List<QueueCandidate>, now: Long, cap: Int): List<String> {
    val limit = cap.coerceIn(1, 30)
    return items.asSequence()
        .filter { !it.unimportant && it.mastery < 4 && it.nextReviewAt > 0L && it.nextReviewAt <= now }
        .sortedWith(compareByDescending<QueueCandidate> { it.starred }.thenBy { it.nextReviewAt }.thenBy { it.id })
        .take(limit)
        .map { it.id }
        .toList()
}

/** 下一次本地提醒时刻。若今天的时间已过，排到明天同一时刻。 */
fun nextDailyTrigger(hour: Int, minute: Int, now: Long, zone: TimeZone): Long {
    val cal = Calendar.getInstance(zone)
    cal.timeInMillis = now
    cal.set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
    cal.set(Calendar.MINUTE, minute.coerceIn(0, 59))
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    if (cal.timeInMillis <= now) cal.add(Calendar.DATE, 1)
    return cal.timeInMillis
}

fun reviewNotificationText(stems: List<String>, overflow: Int): String {
    val lines = stems.take(3).map { stem ->
        val plain = stem.replace("$", "").replace("\n", " ").trim()
        val short = if (plain.length > 28) plain.take(28) + "…" else plain.ifBlank { "（看原图）" }
        "· $short"
    }
    val tail = if (overflow > 0) "\n还有 $overflow 道排到后面" else ""
    return (lines.joinToString("\n") + tail).ifBlank { "打开题笺看看今天的题" }
}
