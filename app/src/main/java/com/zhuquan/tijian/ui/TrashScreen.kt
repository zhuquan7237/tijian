@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.zhuquan.tijian.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhuquan.tijian.data.QuestionRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 回收站：软删除的题目在这里，可恢复、可彻底清除。 */
@Composable
fun TrashScreen(vm: AppVm) {
    var rows by remember { mutableStateOf<List<QuestionRow>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var purging by remember { mutableStateOf<QuestionRow?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    fun reload() {
        scope.launch {
            rows = withContext(Dispatchers.IO) { vm.db.deletedQuestions() }
            loading = false
        }
    }
    LaunchedEffect(vm.dataRevision) { reload() }

    Column(Modifier.fillMaxSize()) {
        TjTopBar(
            title = "回收站",
            subtitle = if (rows.isEmpty()) "空空如也" else "${rows.size} 道题躺在这里",
            onBack = { vm.back() }
        )
        if (!loading && rows.isEmpty()) {
            EmptyState("回收站是空的", "删除的题目会先到这里，随时可以恢复")
            return@Column
        }
        LazyColumn(Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Space.page, vertical = 10.dp)) {
            items(rows, key = { it.id }) { q ->
                Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Thumb(q.imagePath, Modifier.size(width = 40.dp, height = 48.dp))
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            StemText(
                                q.stem, color = Ink, fontSizeSp = 13f, lineHeightSp = 19f, maxLines = 2,
                            )
                            Text(
                                listOf(q.categoryName, q.customTag).filter { it.isNotBlank() }
                                    .joinToString(" · ").ifBlank { "删除于 ${formatDeletedAt(q.createdAt)}" },
                                fontSize = 11.5.sp, color = Muted, modifier = Modifier.padding(top = 3.dp),
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                        Box(
                            Modifier.size(44.dp).clip(RoundedCornerShape(10.dp))
                                .tjClickable(label = "恢复这道题") {
                                    vm.restoreQuestions(listOf(q.id))
                                    vm.toast = "已恢复，题目回到原分类"
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.RestoreFromTrash, "恢复", tint = Accent, modifier = Modifier.size(20.dp))
                        }
                        Box(
                            Modifier.size(44.dp).clip(RoundedCornerShape(10.dp))
                                .tjClickable(label = "彻底删除这道题") { purging = q },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.DeleteForever, "彻底删除", tint = Red, modifier = Modifier.size(20.dp))
                        }
                    }
                    HorizontalDivider(color = Line, thickness = 1.dp)
                }
            }
            item {
                Text(
                    "彻底删除会连复习记录、追问记录一起清掉，不能恢复；图片文件仍保留在原目录。",
                    fontSize = 11.5.sp, lineHeight = 18.sp, color = Muted,
                    modifier = Modifier.padding(vertical = 14.dp)
                )
            }
        }
    }

    purging?.let { q ->
        AlertDialog(
            onDismissRequest = { purging = null },
            title = { Text("彻底删除这道题？", fontSize = 16.sp) },
            text = { Text("「${cleanMathLite(q.stem).take(24).ifBlank { "无题干" }}…」将从本机彻底清除，无法再恢复。", fontSize = 12.5.sp, lineHeight = 19.sp, color = Ink2) },
            confirmButton = {
                TextButton(onClick = {
                    val id = q.id
                    purging = null
                    vm.purgeQuestions(listOf(id))
                    vm.toast = "已彻底删除"
                }) { Text("彻底删除", color = Red, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = { TextButton(onClick = { purging = null }) { Text("取消", color = Ink2) } }
        )
    }
}

private fun formatDeletedAt(ms: Long): String =
    SimpleDateFormat("M月d日", Locale.CHINA).format(Date(ms))
