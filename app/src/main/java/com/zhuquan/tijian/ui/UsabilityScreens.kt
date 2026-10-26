package com.zhuquan.tijian.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.zhuquan.tijian.data.QuestionRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val readingSize = mutableIntStateOf(17)

/** Full-screen editing, including IME and system insets; action row stays visible. */
@Composable
fun FullEditor(onDismissRequest: () -> Unit, title: @Composable () -> Unit,
    text: @Composable () -> Unit, confirmButton: @Composable () -> Unit,
    dismissButton: @Composable () -> Unit) {
    Dialog(onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(Bg).windowInsetsPadding(WindowInsets.safeDrawing).imePadding().padding(16.dp)) {
            title()
            Spacer(Modifier.height(12.dp))
            Box(Modifier.weight(1f).fillMaxWidth()) { text() }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { dismissButton(); confirmButton() }
        }
    }
}

@Composable
fun WelcomeCard(vm: AppVm, onImport: () -> Unit) {
    if (vm.stats.total != 0 || !vm.sessionReady) return
    CardBox(Modifier.fillMaxWidth().padding(horizontal = Space.page, vertical = 8.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("先收录一道错题", fontSize = 20.sp, color = Ink)
            Text("不用配置 AI 也能保存和复习。示例题可以随时删除。", color = Ink2)
            TjButton("体验示例题", Modifier.fillMaxWidth(), style = ActionStyle.Secondary) { vm.addTextDraft(true) }
            TjButton("直接收录错题", Modifier.fillMaxWidth(), onClick = onImport)
            TjButton("配置 AI 识别", Modifier.fillMaxWidth(), style = ActionStyle.Quiet) { vm.go(Route.Provider) }
        }
    }
}

@Composable
fun ReviewSetup(vm: AppVm) {
    AlertDialog(onDismissRequest = { vm.reviewSetup = false }, title = { Text("安排本轮复习") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(if (vm.reviewCategory == null) "全部错题" else "当前分类", color = Ink2)
            listOf("due" to "到期复习", "weak" to "专项练习 · 未掌握", "free" to "自由练习 · 全部").forEach { (key, label) ->
                TjChip(label, vm.reviewMode == key) { vm.reviewMode = key }
            }
            Text("本轮题量", Modifier.padding(top = 12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(5, 10, 0).forEach { n -> TjChip(if(n == 0) "全部" else "$n 题", vm.reviewLimit == n) { vm.reviewLimit = n } }
            }
            if (vm.hasReview) Text("开始新一轮会替换未完成的队列，已保存的评分不受影响。", color = Muted)
        }
    }, confirmButton = { TextButton(onClick = { vm.beginReview() }, enabled = vm.sessionReady && !vm.reviewSaving) { Text("开始复习") } },
        dismissButton = { TextButton(onClick = { vm.reviewSetup = false; if(vm.hasReview) vm.resumeReview() }) { Text(if(vm.hasReview) "继续上次" else "取消") } })
}

@Composable
fun TrashScreen(vm: AppVm) {
    var rows by remember { mutableStateOf<List<QuestionRow>>(emptyList()) }
    var purge by remember { mutableStateOf<QuestionRow?>(null) }
    LaunchedEffect(vm.dataRevision) { rows = withContext(Dispatchers.IO) { vm.db.questions(filter = "trash") } }
    Column(Modifier.fillMaxSize()) {
        TjTopBar("回收站", "${rows.size} 道题 · 彻底删除后无法恢复", onBack = { vm.back() })
        if(rows.isEmpty()) EmptyState("回收站为空", "删除的题目会先保留在这里")
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp)) {
            items(rows, key = { it.id }) { q ->
                CardBox(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        Text(cleanMathLite(q.stem).ifBlank { "图片题" }, color = Ink, maxLines = 3)
                        Text(q.customTag + " · " + q.categoryName, color = Ink2)
                        Row {
                            TjButton("恢复", style = ActionStyle.Secondary) { vm.write { it.restoreQuestions(listOf(q.id)) } }
                            Spacer(Modifier.width(8.dp))
                            TjButton("彻底删除", style = ActionStyle.Danger) { purge = q }
                        }
                    }
                }
            }
        }
    }
    purge?.let { q -> AlertDialog(onDismissRequest = { purge = null }, title = { Text("彻底删除这道题？") },
        text = { Text("题目和复习记录将永久删除。共享原图保留，避免影响其他题目。") },
        confirmButton = { TextButton(onClick = { vm.write { it.purgeQuestion(q.id) }; purge = null }) { Text("彻底删除", color = Red) } },
        dismissButton = { TextButton(onClick = { purge = null }) { Text("取消") } }) }
}
