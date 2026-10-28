@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.zhuquan.tijian.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import kotlin.math.roundToInt
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.ZoomOutMap
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.zhuquan.tijian.data.SUBJECT_ROOTS
import com.zhuquan.tijian.data.fullName
import com.zhuquan.tijian.data.subjectGroup
import com.zhuquan.tijian.data.CategoryRow
import com.zhuquan.tijian.data.QuestionRow
import com.zhuquan.tijian.data.parseKnowledgeTags
import com.zhuquan.tijian.export.PdfExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(vm: AppVm, onImport: () -> Unit) {
    // 搜索框常驻：题目越积越多时，「找题」不该先去找入口
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<QuestionRow>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf(false) }
    var searchRevision by remember { mutableStateOf(0) }
    var showReviewSetup by remember { mutableStateOf(false) }
    LaunchedEffect(query, vm.dataRevision, searchRevision) {
        searchError = false
        if (query.isBlank()) { results = emptyList(); searching = false; return@LaunchedEffect }
        searching = true
        try {
            kotlinx.coroutines.delay(250)
            results = withContext(Dispatchers.IO) { vm.db.questions(query = query.trim()) }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            searchError = true
        } finally {
            searching = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        TjTopBar(
            title = "错题本",
            subtitle = "${vm.stats.total} 道错题"
        )

        TjTextField(
            value = query, onValueChange = { query = it },
            placeholder = { Text("搜题干、题号、知识点…", fontSize = 13.sp, color = Muted) },
            singleLine = true,
            colors = tjFieldColors(),
            leadingIcon = { Icon(Icons.Rounded.Search, null, tint = Muted, modifier = Modifier.size(18.dp)) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = Space.page, vertical = 4.dp)
        )

        if (query.isNotBlank()) {
            val list = results
            if (searching) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Accent)
                }
            } else if (searchError) {
                EmptyState("搜索失败", "请重试，题目仍保存在本机") {
                    TjButton("重新搜索") { searchRevision++ }
                }
            } else if (list.isEmpty()) {
                EmptyState("没有匹配的题", "换个关键词试试") {
                    Text(
                        "也可以搜题号、知识点标签", fontSize = 12.sp, color = Muted,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(list, key = { it.id }) { q ->
                        SearchRow(q) { vm.go(Route.Detail(q.id)) }
                    }
                }
            }
            return@Column
        }

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 88.dp)
        ) {
            // 上次异常退出时被隔离的待确认题目：由用户决定恢复还是丢弃
            if (vm.quarantinedDrafts.isNotEmpty()) {
                Row(
                    Modifier.padding(horizontal = Space.page, vertical = 4.dp).fillMaxWidth()
                        .clip(CardShape).background(AmberSoft)
                        .border(1.dp, Amber.copy(alpha = 0.4f), CardShape)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("上次异常退出", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Amber)
                        Text(
                            "有 ${vm.quarantinedDrafts.size} 道未确认题目已隔离，恢复后可继续校对",
                            fontSize = 12.sp, color = Ink2, modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    Text(
                        "丢弃", fontSize = 12.5.sp, color = Ink2,
                        modifier = Modifier
                            .tjClickable(label = "丢弃隔离题目") { vm.discardQuarantinedDrafts() }
                            .padding(horizontal = 8.dp, vertical = 8.dp)
                    )
                    Box(
                        Modifier.clip(CircleShape).background(Amber)
                            .tjClickable(label = "恢复隔离题目") { vm.restoreQuarantinedDrafts() }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) { Text("恢复", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.SemiBold) }
                }
                Spacer(Modifier.height(6.dp))
            }
            if (vm.importing || vm.pending.isNotEmpty() || vm.captureUris.isNotEmpty()) {
                TjButton(
                    when {
                        vm.importing -> "正在识别 · 查看进度"
                        vm.pending.isNotEmpty() -> "继续校对 ${vm.pending.size} 道题"
                        else -> "继续导入 ${vm.captureUris.size} 张图片"
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Space.page, vertical = 8.dp),
                    style = ActionStyle.Secondary,
                    onClick = onImport,
                )
            }

            if (vm.canResumeReview) {
                Row(
                    Modifier.padding(horizontal = Space.page, vertical = 4.dp).fillMaxWidth()
                        .clip(CardShape).background(AccentSoft)
                        .border(1.dp, Accent.copy(alpha = 0.25f), CardShape)
                        .tjClickable(label = "继续上次复习") { vm.resumeReview() }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.PlayCircle, null, tint = Accent, modifier = Modifier.size(20.dp))
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text("上次复习还没结束", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Accent)
                        Text(
                            "进行到第 ${vm.reviewIndex + 1} / ${vm.reviewQueue.size} 题，点这里接着来",
                            fontSize = 12.sp, color = Ink2, modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Accent)
                }
            }

            ReviewHero(vm.stats.due, vm.stats.total, vm.stats.mastered) {
                if (vm.stats.total == 0) onImport() else showReviewSetup = true
            }

            // 学科行右侧：带文字的「添加错题」，单手也能碰到；搜索已常驻顶部，不再占这里
            SectionLabel("学科", Modifier.padding(horizontal = Space.page)) {
                Row(
                    Modifier.clip(CircleShape).background(Accent)
                        .tjClickable(label = "添加错题") { onImport() }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Add, null, tint = OnAccent, modifier = Modifier.size(16.dp))
                    Text(
                        "添加错题", fontSize = 12.5.sp, color = OnAccent, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 3.dp)
                    )
                }
            }

            Column(
                Modifier.fillMaxWidth().padding(horizontal = Space.page),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val majors = SUBJECT_ROOTS.dropLast(1)
                majors.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        pair.forEach { subject ->
                            val children = vm.categories.filter { it.subjectGroup() == subject }
                            SubjectTile(
                                name = subject,
                                count = children.sumOf { it.count },
                                mastered = children.sumOf { it.mastered },
                                modifier = Modifier.weight(1f),
                                onClick = { vm.go(Route.Subject(subject)) }
                            )
                        }
                    }
                }
                val other = SUBJECT_ROOTS.last()
                val otherChildren = vm.categories.filter { it.subjectGroup() == other }
                SubjectWideRow(
                    name = other,
                    count = otherChildren.sumOf { it.count },
                    mastered = otherChildren.sumOf { it.mastered },
                    onClick = { vm.go(Route.Subject(other)) }
                )
            }
            run {
                var recent by remember { mutableStateOf<List<QuestionRow>>(emptyList()) }
                LaunchedEffect(vm.stats.total, vm.stats.due, vm.dataRevision) {
                    recent = withContext(Dispatchers.IO) { vm.db.recentQuestions(8) }
                }
                if (recent.isNotEmpty()) {
                    SectionLabel("最近导入", Modifier.padding(horizontal = Space.page)) {
                        Text("全部 ${vm.stats.total} 道", fontSize = 12.sp, color = Muted, fontWeight = FontWeight.Medium)
                    }
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                            .padding(start = Space.page, end = Space.page),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        recent.forEach { q ->
                            RecentCard(q) { vm.go(Route.Detail(q.id)) }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }

    if (showReviewSetup) {
        ReviewSetupSheet(vm, onDismiss = { showReviewSetup = false })
    }
}

/** 开始复习前的题量与范围选择；记住上次设置，日常一次点击就能开练。 */
@Composable
private fun ReviewSetupSheet(vm: AppVm, onDismiss: () -> Unit) {
    var mode by remember { mutableStateOf(if (vm.stats.due > 0) AppVm.ReviewMode.DUE else AppVm.ReviewMode.FREE) }
    var count by remember { mutableStateOf(vm.reviewCountPref) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = CardBg) {
        Column(Modifier.blockSheetDrag().padding(horizontal = Space.page).padding(bottom = 26.dp)) {
            Text("开始复习", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink, letterSpacing = (-0.3).sp)
            Text(
                "自评不会的题明天会再出现在队列里",
                fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )
            Text("复习范围", fontSize = 12.sp, color = Ink2, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TjChip(
                    if (vm.stats.due > 0) "到期复习 · ${vm.stats.due} 道" else "到期复习 · 今天没有到期",
                    selected = mode == AppVm.ReviewMode.DUE
                ) { if (vm.stats.due > 0) mode = AppVm.ReviewMode.DUE }
                TjChip("自由练习 · 全部", selected = mode == AppVm.ReviewMode.FREE) { mode = AppVm.ReviewMode.FREE }
            }
            Spacer(Modifier.height(14.dp))
            Text("每次题量", fontSize = 12.sp, color = Ink2, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TjChip("5 题", selected = count == 5) { count = 5 }
                TjChip("10 题", selected = count == 10) { count = 10 }
                TjChip("全部", selected = count == 0) { count = 0 }
            }
            Text(
                "会记住这次的选择，下次直接用",
                fontSize = 11.5.sp, color = Muted, modifier = Modifier.padding(top = 8.dp)
            )
            Spacer(Modifier.height(16.dp))
            TjButton(
                when {
                    mode == AppVm.ReviewMode.DUE && count > 0 -> "开始复习 ${minOf(count, vm.stats.due)} 题"
                    mode == AppVm.ReviewMode.DUE -> "开始复习 ${vm.stats.due} 题"
                    count > 0 -> "自由练习 ${count} 题"
                    else -> "自由练习全部题目"
                },
                Modifier.fillMaxWidth()
            ) {
                vm.chooseReviewCount(count)
                vm.startReview(mode, count)
                onDismiss()
            }
        }
    }
}

@Composable
private fun ReviewHero(due: Int, total: Int, mastered: Int, onClick: () -> Unit) {
    // 夜读台主卡：整套界面唯一一处渐变光源，数字是视觉中心。
    Column(
        Modifier.padding(horizontal = Space.page).fillMaxWidth()
            .clip(CardShape).background(HeroBrush)
            .border(1.dp, HeroLine, CardShape)
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Text("今日复习", fontSize = 11.sp, letterSpacing = 1.6.sp, fontWeight = FontWeight.SemiBold, color = Accent)
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.Bottom) {
            Text(
                "$due", fontSize = 64.sp, lineHeight = 64.sp, fontWeight = FontWeight.Medium,
                color = Ink, letterSpacing = (-2.5).sp, fontFamily = NumFont,
            )
            Text("题待复习", fontSize = 13.sp, color = Ink2, modifier = Modifier.padding(start = 8.dp, bottom = 10.dp))
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(bottom = 8.dp)) {
                Text(
                    if (total == 0) "0%" else "${(mastered * 100 / total)}%",
                    fontSize = 22.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium,
                    color = Green, fontFamily = NumFont, letterSpacing = (-0.6).sp,
                )
                Text("已掌握", fontSize = 11.sp, color = Muted)
            }
        }
        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Accent.copy(alpha = 0.20f))) {
            Box(
                Modifier.fillMaxWidth(if (total == 0) 0f else (mastered.toFloat() / total).coerceIn(0f, 1f))
                    .height(4.dp).clip(RoundedCornerShape(2.dp)).background(Green)
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (total == 0) "拍照或从相册导入，开始整理错题" else "已掌握 $mastered / $total 道",
                fontSize = 12.sp, color = Muted, fontFamily = if (total == 0) null else NumFont,
            )
            Spacer(Modifier.weight(1f))
            Text(
                if (due > 0) "约 ${maxOf(1, due * 2 / 3 + 1)} 分钟" else if (total == 0) "尚未开始" else "今天没有到期",
                fontSize = 12.sp, color = Muted,
            )
        }
        Spacer(Modifier.height(16.dp))
        val primary = due > 0 || total == 0
        Box(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(CircleShape)
                .background(if (primary) Accent else Fill)
                .tjClickable(label = if (due > 0) "开始复习" else if (total > 0) "自由复习" else "导入第一道题") { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (due > 0) "开始复习" else if (total > 0) "自由复习" else "导入第一道题",
                fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                color = if (primary) OnAccent else Ink
            )
        }
    }
}

private fun subjectIcon(subject: String): ImageVector = when (subject) {
    "数学" -> Icons.Rounded.Calculate
    "专业课" -> Icons.Rounded.School
    "英语" -> Icons.Rounded.Translate
    "政治" -> Icons.Rounded.Gavel
    else -> Icons.Rounded.Category
}

@Composable
private fun SubjectTile(name: String, count: Int, mastered: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val hasQuestions = count > 0
    Column(
        modifier
            .bounceClick(label = name, onClick = onClick)
            .clip(CardShape)
            .background(CardBg)
            .border(1.dp, Line, CardShape)
            .padding(horizontal = 14.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                subjectIcon(name), null,
                tint = if (hasQuestions) Accent else Muted,
                modifier = Modifier.size(18.dp)
            )
            Text(
                name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                color = if (hasQuestions) Ink else Ink2,
                modifier = Modifier.padding(start = 7.dp)
            )
            Spacer(Modifier.weight(1f))
            Text(
                if (hasQuestions) "$count" else "0",
                fontSize = 20.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium,
                color = if (hasQuestions) Ink else Muted, fontFamily = NumFont, letterSpacing = (-0.5).sp
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(2.dp))
                    .background(Fill)
            ) {
                if (hasQuestions) {
                    Box(
                        Modifier.fillMaxWidth((mastered.toFloat() / count).coerceIn(0f, 1f))
                            .height(3.dp).background(Green)
                    )
                }
            }
            Text(
                if (hasQuestions) "$mastered/$count" else "暂无",
                fontSize = 11.sp, color = Muted, fontFamily = NumFont,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

@Composable
private fun SubjectWideRow(name: String, count: Int, mastered: Int, onClick: () -> Unit) {
    val hasQuestions = count > 0
    Row(
        Modifier.fillMaxWidth()
            .bounceClick(label = name, onClick = onClick)
            .clip(CardShape)
            .background(CardBg)
            .border(1.dp, Line, CardShape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            subjectIcon(name), null,
            tint = if (hasQuestions) Accent else Muted,
            modifier = Modifier.size(19.dp)
        )
        Text(
            name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            color = if (hasQuestions) Ink else Ink2,
            modifier = Modifier.padding(start = 8.dp)
        )
        Text(
            if (hasQuestions) "$count 道" else "暂无错题",
            fontSize = 12.sp, color = Muted, modifier = Modifier.padding(start = 8.dp)
        )
        Spacer(Modifier.weight(1f))
        if (hasQuestions) {
            Text("已掌握 $mastered / $count", fontSize = 11.5.sp, color = Ink2, fontFamily = NumFont)
            Spacer(Modifier.width(6.dp))
        }
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight, null,
            tint = Muted, modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun CategoryScreen(vm: AppVm, route: Route.Category? = vm.route as? Route.Category) {
    val id = route?.id ?: return
    val cat = vm.categories.firstOrNull { it.id == id }
    var filter by rememberSaveable(id) { mutableStateOf("all") }
    var selecting by remember { mutableStateOf(false) }
    val selected = remember { mutableStateOf<Set<String>>(emptySet()) }
    var pendingBatchDelete by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }
    var pendingFile by remember { mutableStateOf<File?>(null) }
    // 题号行内编辑：记录正在填的行号，对话框支持「保存并下一题」连续填写
    var tagEditIndex by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()

    val listState = androidx.compose.foundation.lazy.rememberLazyListState(
        initialFirstVisibleItemIndex = vm.categoryScroll[id] ?: 0
    )
    androidx.compose.runtime.DisposableEffect(id) {
        onDispose { vm.categoryScroll[id] = listState.firstVisibleItemIndex }
    }
    // 切换筛选时保留旧列表，等新结果回来再整体替换；否则 remember 以 filter
    // 为 key 会先把 rows 置空，界面闪一下空白再填充。
    var rows by remember(id) { mutableStateOf<List<QuestionRow>>(emptyList()) }
    LaunchedEffect(id, filter, vm.dataRevision) {
        val next = withContext(Dispatchers.IO) { vm.db.questions(id, filter) }
        rows = next
    }
    // 长按拖动排序：拖动过程中本地交换，松手一次性落库
    val dragState = rememberDragDropState(listState) { from, to ->
        if (from in rows.indices && to in rows.indices) {
            rows = rows.toMutableList().apply { add(to, removeAt(from)) }
        }
    }
    fun persistOrder() {
        val ordered = rows.map { it.id }
        vm.write { it.reorderQuestions(ordered) }
    }

    LaunchedEffect(filter) {
        selected.value = emptySet()
        runCatching { listState.animateScrollToItem(0) }
    }
    LaunchedEffect(rows) { selected.value = selected.value.intersect(rows.map { it.id }.toSet()) }

    val createPdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri: Uri? ->
        val f = pendingFile
        if (uri != null && f != null) vm.saveTo(uri, f)
        pendingFile = null
    }

    Column(Modifier.fillMaxSize()) {
        TjTopBar(
            title = cat?.name ?: "分类",
            subtitle = "${rows.size} 道题",
            onBack = { vm.back() },
            actions = {
                IconCircle(Icons.Rounded.Add, "导入错题", tint = Color.White, filled = true) { vm.go(Route.Import) }
                Text(
                    if (selecting) "取消" else "选择",
                    fontSize = 13.sp, color = Accent, fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .tjClickable {
                            selecting = !selecting
                            if (!selecting) selected.value = emptySet()
                        }.padding(horizontal = 8.dp, vertical = 10.dp)
                )
            }
        )

        if (cat?.status == "pending") {
            Row(
                Modifier.padding(horizontal = Space.page).fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp)).background(AccentSoft)
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("AI 新建的分类", fontSize = 11.5.sp, color = Accent, modifier = Modifier.weight(1f))
                Text("确认为正式分类", fontSize = 11.5.sp, color = Accent, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.tjClickable {
                        vm.write { it.confirmCategory(id) }
                    }.padding(4.dp))
            }
            Spacer(Modifier.height(8.dp))
        }

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Space.page),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TjChip("全部 ${cat?.count ?: rows.size}", filter == "all") { filter = "all" }
            TjChip("待复习", filter == "due") { filter = "due" }
            TjChip("未掌握", filter == "weak") { filter = "weak" }
            TjChip("本周", filter == "week") { filter = "week" }
            TjChip("有错因", filter == "errors") { filter = "errors" }
        }

        Spacer(Modifier.height(10.dp))
        // 视图切换：平时看题（学习列表），批量整理时切表格
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Space.page),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (vm.categoryView == 0) "长按题目行可拖动排序" else "按列整理",
                fontSize = 12.sp, color = Muted
            )
            Spacer(Modifier.weight(1f))
            ViewToggle(vm.categoryView) { vm.chooseCategoryView(it) }
        }

        Spacer(Modifier.height(10.dp))

        CardBox(Modifier.padding(horizontal = Space.page).fillMaxWidth().weight(1f)) {
            if (vm.categoryView == 0) {
                // 学习列表：题干两行 + 缩略图 + 关键知识点 + 掌握状态，竖向信息一屏可比
                LazyColumn(Modifier.weight(1f).dragContainer(dragState) { persistOrder() }, state = listState) {
                    itemsIndexed(rows, key = { _, q -> q.id }) { idx, q ->
                        DraggableItem(dragState, idx) { isDragging ->
                            Column {
                                StudyListRow(q, selecting = selecting || isDragging, isSel = selected.value.contains(q.id)) {
                                    if (selecting) {
                                        selected.value = if (selected.value.contains(q.id)) selected.value - q.id else selected.value + q.id
                                    } else vm.go(Route.Detail(q.id))
                                }
                                HorizontalDivider(color = Line, thickness = 1.dp)
                            }
                        }
                    }
                    if (rows.isEmpty()) {
                        item { EmptyState("这里是空的", "从下方导入错题") }
                    }
                }
            } else {
            Column {
                Row(Modifier.fillMaxWidth().background(SurfaceAlt).padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text(if (selecting) "" else "题目", fontSize = 11.sp, color = Ink2, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(56.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("题号", fontSize = 11.sp, color = Amber, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(76.dp))
                    Text("知识点", fontSize = 11.sp, color = Ink2, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text("掌握", fontSize = 11.sp, color = Ink2, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(56.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                }
                LazyColumn(Modifier.weight(1f), state = listState) {
                    itemsIndexed(rows, key = { _, q -> q.id }) { idx, q ->
                        val isSel = selected.value.contains(q.id)
                        val rowSrc = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                        Row(
                            Modifier.pressScale(rowSrc)
                                .fillMaxWidth().heightIn(min = 88.dp)
                                .tjClickable(interactionSource = rowSrc) {
                                    if (selecting) {
                                        selected.value = if (isSel) selected.value - q.id else selected.value + q.id
                                    } else vm.go(Route.Detail(q.id))
                                }.padding(horizontal = 14.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.width(56.dp)) {
                                if (selecting) {
                                    Checkbox(checked = isSel, onCheckedChange = null, modifier = Modifier.size(22.dp))
                                } else {
                                    Thumb(q.imagePath, Modifier.width(56.dp).height(70.dp))
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            // 题号专列：点击直接行内编辑，不用打开题目再点编辑
                            Column(
                                Modifier.width(76.dp)
                                    .tjClickable(label = "填写题号") { tagEditIndex = idx }
                            ) {
                                if (q.customTag.isNotBlank()) {
                                    Text(
                                        q.customTag, fontSize = 12.sp, lineHeight = 16.sp,
                                        color = Amber, fontWeight = FontWeight.SemiBold, maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                } else {
                                    Text(
                                        "＋题号", fontSize = 11.5.sp, color = Muted,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    q.type.ifBlank { "—" }, fontSize = 11.sp, color = Muted,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = if (q.customTag.isNotBlank()) 3.dp else 2.dp)
                                )
                            }
                            val tags = parseKnowledgeTags(q.tags)
                            if (tags.isEmpty()) {
                                Text("—", fontSize = 12.sp, color = Muted, modifier = Modifier.weight(1f))
                            } else {
                                Row(
                                    Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    tags.forEach { tag ->
                                        Box(
                                            Modifier.clip(RoundedCornerShape(6.dp)).background(AccentSoft)
                                                .padding(horizontal = 7.dp, vertical = 4.dp)
                                        ) { Text(tag, fontSize = 11.5.sp, color = Ink2, maxLines = 1) }
                                    }
                                }
                            }
                            Box(Modifier.width(56.dp), contentAlignment = Alignment.CenterEnd) { MasteryDots(q.mastery) }
                        }
                        HorizontalDivider(color = Line, thickness = 1.dp)
                    }
                    if (rows.isEmpty()) {
                        item { EmptyState("这里是空的", "从下方导入错题") }
                    }
                }
            }
            }
        }

        Spacer(Modifier.height(12.dp))
        CardBox(Modifier.padding(horizontal = Space.page).fillMaxWidth()) {
            Row(Modifier.padding(vertical = 16.dp)) {
                OverviewCell("${rows.size}", "本类题目", Modifier.weight(1f))
                OverviewCell("${rows.count { it.mastery >= 4 }}", "已掌握", Modifier.weight(1f))
                OverviewCell("${rows.count { it.nextReviewAt in 1..System.currentTimeMillis() }}", "待复习", Modifier.weight(1f))
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.padding(horizontal = Space.page).fillMaxWidth()
                .clip(CardShape).background(HeroBrush).border(1.dp, HeroLine, CardShape)
                .tjClickable { vm.startReviewFor(id) }
                .padding(horizontal = Space.page, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("复习这组", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                Text(
                    "${rows.count { it.mastery < 4 }} 道未掌握 · 约 ${maxOf(1, rows.size / 3)} 分钟",
                    fontSize = 11.sp, color = Muted, modifier = Modifier.padding(top = 3.dp)
                )
            }
            Box(
                Modifier.clip(CircleShape).background(Accent)
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) { Text("开始", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = OnAccent) }
        }

        Spacer(Modifier.height(12.dp))

        if (selecting && selected.value.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().background(Fill).padding(horizontal = Space.page, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("已选 ${selected.value.size} 题", fontSize = 12.sp, color = Ink, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.heightIn(min = 48.dp).clip(CircleShape)
                        .border(1.dp, Red.copy(alpha = 0.5f), CircleShape)
                        .tjClickable(label = "删除所选") { pendingBatchDelete = true }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) { Text("删除", fontSize = 12.sp, color = Red, fontWeight = FontWeight.SemiBold) }
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.heightIn(min = 48.dp).clip(CircleShape).background(Accent)
                        .tjClickable { showExport = true }.padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) { Text("导出 PDF", fontSize = 11.5.sp, color = OnAccent, fontWeight = FontWeight.SemiBold) }
            }
        }
    }

    tagEditIndex?.let { idx ->
        val q = rows.getOrNull(idx)
        if (q == null) {
            tagEditIndex = null
        } else {
            var text by remember(idx, q.id) { mutableStateOf(q.customTag) }
            fun save(next: Boolean) {
                val cur = idx
                vm.write { it.updateQuestionTag(q.id, text) }
                if (next) {
                    val n = cur + 1
                    if (n < rows.size) tagEditIndex = n else tagEditIndex = null
                } else tagEditIndex = null
            }
            AlertDialog(
                onDismissRequest = { tagEditIndex = null },
                title = { Text("填写题号", fontSize = 16.sp) },
                text = {
                    Column {
                        Text(
                            "第 ${idx + 1}/${rows.size} 题 · ${q.type.ifBlank { "题目" }}",
                            fontSize = 11.5.sp, color = Muted
                        )
                        Spacer(Modifier.height(8.dp))
                        TjTextField(
                            value = text, onValueChange = { text = it },
                            placeholder = { Text("如：12 或 三(7)", fontSize = 12.5.sp, color = Muted) },
                            singleLine = true, colors = tjFieldColors(), modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "题号只用于你自己对照卷子，AI 分类不受影响。",
                            fontSize = 10.5.sp, color = Muted
                        )
                    }
                },
                confirmButton = {
                    Row {
                        TextButton(onClick = { save(true) }) { Text("保存并下一题", color = Accent) }
                        TextButton(onClick = { save(false) }) { Text("保存", color = Accent, fontWeight = FontWeight.SemiBold) }
                    }
                },
                dismissButton = { TextButton(onClick = { tagEditIndex = null }) { Text("取消", color = Ink2) } }
            )
        }
    }

    if (pendingBatchDelete) {
        AlertDialog(
            onDismissRequest = { pendingBatchDelete = false },
            title = { Text("删除所选 ${selected.value.size} 道题？", fontSize = 17.sp) },
            text = { Text("删除后不再出现在复习和导出里，原图文件会保留。", fontSize = 13.sp, lineHeight = 20.sp, color = Ink2) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    val ids = selected.value.toList()
                    pendingBatchDelete = false
                    selected.value = emptySet()
                    selecting = false
                    vm.deleteQuestions(ids)
                }) { Text("删除", color = Red, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { pendingBatchDelete = false }) {
                    Text("取消", color = Ink2)
                }
            }
        )
    }

    if (showExport) {
        ExportSheet(
            count = selected.value.size,
            onDismiss = { showExport = false },
            onExport = { opts, share ->
                val list = rows.filter { selected.value.contains(it.id) }
                showExport = false
                scope.launch {
                    vm.withBusy("排版 PDF") {
                        val f = vm.buildPdf(list, cat?.name ?: "错题导出", opts)
                        if (share) shareFile(vm.getApplication(), f)
                        else { pendingFile = f; createPdf.launch("${cat?.name ?: "错题"}_${dateStamp()}.pdf") }
                    }
                }
            }
        )
    }

    if (vm.busy && (showExport || selecting)) BusyOverlay(vm.busyLabel)
}

private fun Color.toHexColor(): String = "#%06X".format(0xFFFFFF and toArgb())

/** 分类页双视图切换：学习列表（默认）/ 整理表格。 */
@Composable
private fun ViewToggle(current: Int, onPick: (Int) -> Unit) {
    Row(
        Modifier.clip(CircleShape).background(SurfaceAlt).border(1.dp, Line, CircleShape).padding(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf(0 to "列表", 1 to "表格").forEach { (v, label) ->
            val on = current == v
            Box(
                Modifier.clip(CircleShape)
                    .background(if (on) Accent else Color.Transparent)
                    .tjClickable(label = if (v == 0) "切换到学习列表" else "切换到整理表格") { onPick(v) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    label, fontSize = 12.sp,
                    color = if (on) OnAccent else Ink2, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

/** 列表行题干：带公式的走 JLatexMath 真渲染，普通文本走 Compose Text。 */
@Composable
fun StemText(
    stem: String,
    color: Color,
    fontSizeSp: Float,
    modifier: Modifier = Modifier,
    lineHeightSp: Float = fontSizeSp * 1.45f,
    maxLines: Int = 2,
    blankHint: String = "（无题干，点开补录）",
) {
    if (stem.isBlank()) {
        Text(
            blankHint, fontSize = fontSizeSp.sp, lineHeight = lineHeightSp.sp,
            color = Muted, maxLines = maxLines, overflow = TextOverflow.Ellipsis, modifier = modifier,
        )
    } else if (looksLikeMath(stem)) {
        MathText(stem, color = color, fontSizeSp = fontSizeSp, maxLines = maxLines, modifier = modifier)
    } else {
        Text(
            cleanMathLite(stem).ifBlank { blankHint }, fontSize = fontSizeSp.sp, lineHeight = lineHeightSp.sp,
            color = color, maxLines = maxLines, overflow = TextOverflow.Ellipsis, modifier = modifier,
        )
    }
}

/** 学习列表行：两行题干、可辨认的缩略图、关键知识点、掌握状态。 */
@Composable
private fun StudyListRow(q: QuestionRow, selecting: Boolean, isSel: Boolean, onClick: () -> Unit) {
    val rowSrc = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Row(
        Modifier.pressScale(rowSrc).fillMaxWidth()
            .tjClickable(interactionSource = rowSrc) { onClick() }
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(Modifier.width(52.dp)) {
            if (selecting) {
                Checkbox(checked = isSel, onCheckedChange = null, modifier = Modifier.size(22.dp))
            } else {
                Thumb(q.imagePath, Modifier.width(52.dp).height(66.dp))
            }
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            StemText(
                q.stem,
                color = if (q.stem.isBlank()) Muted else Ink,
                fontSizeSp = 13.5f,
                lineHeightSp = 20f,
            )
            Row(
                Modifier.padding(top = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (q.customTag.isNotBlank()) {
                    Box(
                        Modifier.clip(RoundedCornerShape(5.dp)).background(AmberSoft)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            q.customTag, fontSize = 11.sp, color = Amber,
                            fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.width(7.dp))
                }
                Text(q.type.ifBlank { "题目" }, fontSize = 11.sp, color = Muted)
                Spacer(Modifier.weight(1f))
                MasteryDots(q.mastery)
            }
            val tags = parseKnowledgeTags(q.tags)
            if (tags.isNotEmpty()) {
                Row(
                    Modifier.padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    tags.take(2).forEach { tag ->
                        Box(
                            Modifier.clip(RoundedCornerShape(5.dp)).background(AccentSoft)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) { Text(tag, fontSize = 11.sp, color = Ink2, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                    if (tags.size > 2) {
                        Text("＋${tags.size - 2}", fontSize = 11.sp, color = Muted)
                    }
                }
            }
        }
    }
}

@Composable
fun QuestionDetailScreen(vm: AppVm, route: Route.Detail? = vm.route as? Route.Detail) {
    val id = route?.id ?: return
    var savedScroll by rememberSaveable(id) { mutableStateOf(0) }
    val detailScroll = rememberScrollState()
    // WebViews initially measure at 1dp while fonts/content load. ScrollState
    // clamps its restored offset to that temporary range. Keep the user's
    // position independently and restore as the intrinsic content grows.
    LaunchedEffect(id, detailScroll) {
        snapshotFlow { detailScroll.maxValue }.collect { maximum ->
            if (maximum != Int.MAX_VALUE && !detailScroll.isScrollInProgress) {
                detailScroll.scrollTo(savedScroll.coerceAtMost(maximum))
            }
        }
    }
    LaunchedEffect(id, detailScroll) {
        var wasScrolling = false
        snapshotFlow { detailScroll.isScrollInProgress to detailScroll.value }.collect { (scrolling, offset) ->
            if (scrolling || wasScrolling) savedScroll = offset
            wasScrolling = scrolling
        }
    }
    var q by remember(id) { mutableStateOf(vm.cachedQuestion(id)) }
    var loadError by remember(id) { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var viewingImage by remember { mutableStateOf<String?>(null) }
    // 学习顺序：看题 → 独立思考 → 看提示或答案 → 理解解析 → 记录错因。
    // 练习模式先把答案与解析藏起来；「需要掌握」是提示，始终可见。
    var practice by rememberSaveable(id) { mutableStateOf(false) }
    var revealed by rememberSaveable(id) { mutableStateOf(false) }
    // 原图与识别题干快捷切换：默认看题干，免得同一内容占两屏
    var showImage by rememberSaveable(id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(id, vm.dataRevision) {
        vm.rememberQuestion(id)
        runCatching { withContext(Dispatchers.IO) { vm.db.question(id) ?: error("题目不存在或已删除") } }
            .onSuccess { q = it; vm.cacheQuestion(it) }
            .onFailure { loadError = it.message ?: "题目读取失败"; vm.pageReady(Route.Detail(id)) }
    }

    val question = q ?: run {
        Column(Modifier.fillMaxSize()) {
            TjTopBar(title = "题目", subtitle = if (loadError == null) "正在加载" else "读取失败", onBack = { vm.back() })
            Text(loadError ?: "正在读取题目…", color = Muted, modifier = Modifier.padding(18.dp))
        }
        return
    }
    val rs = vm.readingScaleValue
    fun s(v: Int) = (v * rs).roundToInt()
    val hasImage = question.imagePath != null
    val stemText = buildString {
        append(question.stem)
        parseOptions(question.options).forEach {
            append(10.toChar()); append(10.toChar()); append(it)
        }
    }
    val needFormulas = parseKnowledgeTags(question.needFormulas)
    val needConcepts = parseKnowledgeTags(question.needConcepts)
    val needMethods = parseKnowledgeTags(question.needMethods)
    val hasNeeds = needFormulas.isNotEmpty() || needConcepts.isNotEmpty() || needMethods.isNotEmpty()
    val needsMd = buildString {
        fun group(title: String, items: List<String>) {
            if (items.isEmpty()) return
            append("#### ").append(title).append("\n\n")
            items.forEach { append("- ").append(it.replace("\n", " ")).append("\n") }
            append("\n")
        }
        group("公式", needFormulas); group("概念", needConcepts); group("方法", needMethods)
    }
    val missingSolution = question.answer.isBlank() || question.analysis.isBlank()
    val showSolution = !practice || revealed

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
        TjTopBar(
            title = question.type.ifBlank { "题目" },
            subtitle = listOf(question.customTag, question.categoryName)
                .filter { it.isNotBlank() }.joinToString(" · "),
            onBack = { vm.back() },
            actions = {
                IconCircle(
                    if (practice) Icons.Rounded.MenuBook else Icons.Rounded.School,
                    if (practice) "切换到阅读模式" else "切换到练习模式",
                ) {
                    if (practice) { practice = false; revealed = false } else practice = true
                }
                IconCircle(Icons.Rounded.Edit, "编辑") { editing = true }
                IconCircle(Icons.Rounded.DeleteOutline, "删除", tint = Red) { confirmDelete = true }
            }
        )
        Column(Modifier.fillMaxSize().verticalScroll(detailScroll).padding(horizontal = Space.page, vertical = 8.dp)) {
            // —— 看题：题干与原图快捷切换，默认题干 ——
            if (hasImage && hasStemText(stemText)) {
                Row(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MiniToggle("识别题干", !showImage) { showImage = false }
                    MiniToggle("原图", showImage) { showImage = true }
                }
            }
            if (hasImage && (showImage || !hasStemText(stemText))) {
                CardBox {
                    Column(Modifier.padding(10.dp)) {
                        ZoomableImage(File(question.imagePath!!), maxHeight = 200.dp) { viewingImage = question.imagePath }
                        Row(Modifier.padding(start = 6.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("原图 · 点按放大或裁剪", fontSize = 12.sp, color = Muted, modifier = Modifier.weight(1f))
                            question.imageId?.let { imageId ->
                                var original by remember(imageId) { mutableStateOf<String?>(null) }
                                LaunchedEffect(imageId) { original = withContext(Dispatchers.IO) { vm.db.originalImagePath(imageId) } }
                                val uncropped = original
                                if (uncropped != null && uncropped != question.imagePath) {
                                    Text(
                                        "未裁剪原图", fontSize = 12.sp, color = Accent, fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.tjClickable(label = "查看未裁剪原图") { viewingImage = uncropped }.padding(horizontal = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            if (hasStemText(stemText) && !(hasImage && showImage)) {
                CardBox {
                    Column(Modifier.padding(vertical = 6.dp)) {
                        RichText(
                            markdown = "",
                            sections = listOf(RichSectionSpec("题目", stemText, "", Ink2.toHexColor(), s(16), kind = "stem")),
                            selectable = true,
                            onAsk = { vm.openChat(it) },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            if (!hasImage && !hasStemText(stemText)) {
                Text("（未识别题干，点下方编辑按钮补录）", fontSize = 12.sp, color = Muted)
            }

            // —— 独立思考：练习模式先藏答案 ——
            if (practice && !revealed) {
                CardBox {
                    Column(Modifier.padding(16.dp)) {
                        Text("练习中", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                        Text(
                            "先独立作答，再对答案。想不起来可以看下面的「需要掌握」当提示。",
                            fontSize = 12.5.sp, lineHeight = 19.sp, color = Muted, modifier = Modifier.padding(top = 4.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        BigButton("显示答案", Modifier.fillMaxWidth()) { revealed = true }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // —— 看答案 → 理解解析 ——
            if (showSolution) {
                if (missingSolution) {
                    RichSection("答案与解析", "识别时缺失，可让 AI 补全", Red) {
                        Column {
                            Text(
                                "这道题还缺" + listOfNotNull(
                                    if (question.answer.isBlank()) "答案" else null,
                                    if (question.analysis.isBlank()) "解析" else null
                                ).joinToString("和") + "。补全后再回来复习。",
                                fontSize = 13.sp, lineHeight = 20.sp, color = Muted
                            )
                            Spacer(Modifier.height(10.dp))
                            Box(
                                Modifier.heightIn(min = 48.dp).clip(CircleShape)
                                    .background(RedSoft)
                                    .tjClickable(label = "让 AI 补全答案与解析") { vm.fillSolution(question.id) }
                                    .padding(horizontal = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (vm.solutionBusyFor == question.id) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(Modifier.size(14.dp), color = Red, strokeWidth = 2.dp)
                                        Text("正在补全…", fontSize = 12.sp, color = Red, modifier = Modifier.padding(start = 7.dp))
                                    }
                                } else {
                                    Text("让 AI 补全答案与解析", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = Red)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                } else {
                    CollapsibleCard(
                        title = "答案与解析",
                        subtitle = "对完答案，把每一步都看懂",
                        color = Accent,
                        expanded = vm.answerExpanded,
                        onToggle = { vm.chooseAnswerExpanded(!vm.answerExpanded) },
                    ) {
                        val solSections = buildList {
                            if (question.answer.isNotBlank()) add(RichSectionSpec("答案", question.answer, "", Green.toHexColor(), s(16), kind = "answer"))
                            if (question.analysis.isNotBlank()) add(RichSectionSpec("解析", question.analysis, "", Ink2.toHexColor(), s(15), kind = "analysis"))
                        }
                        RichText(markdown = "", sections = solSections, selectable = true, onAsk = { vm.openChat(it) })
                        if (question.analysis.isNotBlank() && looksTruncated(question.analysis)) {
                            Text(
                                "这段解析可能被模型截断（公式或括号未闭合），点右上角编辑补全，或重新识别。",
                                fontSize = 11.5.sp, lineHeight = 17.sp, color = Red,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }

            // —— 提示：要做出这题需要掌握什么（练习模式下就是提示卡） ——
            if (hasNeeds) {
                RichSection("要做出这题，需要掌握", "公式 · 概念 · 方法 · 提示", Amber) {
                    RichText(markdown = "", sections = listOf(RichSectionSpec("", needsMd, "", Amber.toHexColor(), s(14), kind = "needs")), selectable = true, onAsk = { vm.openChat(it) })
                }
                Spacer(Modifier.height(12.dp))
            } else {
                RichSection("要做出这题，需要掌握", "公式 · 概念 · 方法", Amber) {
                    Column {
                        Text(
                            "还缺这一块：做这道题需要哪些公式、概念和方法？",
                            fontSize = 13.sp, lineHeight = 20.sp, color = Muted
                        )
                        Spacer(Modifier.height(10.dp))
                        Box(
                            Modifier.heightIn(min = 48.dp).clip(CircleShape)
                                .background(AccentSoft)
                                .tjClickable(label = "让 AI 补全知识点") { vm.fillNeeds(question.id) }
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (vm.needsBusyFor == question.id) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(Modifier.size(14.dp), color = Accent, strokeWidth = 2.dp)
                                    Text("正在分析…", fontSize = 12.sp, color = Accent, modifier = Modifier.padding(start = 7.dp))
                                }
                            } else {
                                Text("让 AI 分析这题需要掌握什么", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = Accent)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // —— 记录错因：比「属于哪一章」更能帮下次做对 ——
            if (showSolution) {
                val reasons = parseKnowledgeTags(question.errorReasons)
                RichSection("错因", "点选这道题为什么丢分", Red) {
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ERROR_REASONS.forEach { reason ->
                            val on = reason in reasons
                            Box(
                                Modifier.clip(CircleShape)
                                    .background(if (on) RedSoft else SurfaceAlt)
                                    .border(1.dp, if (on) Red.copy(alpha = 0.5f) else Line, CircleShape)
                                    .tjClickable(label = if (on) "移除错因：$reason" else "标记错因：$reason") {
                                        val next = (if (on) reasons - reason else reasons + reason)
                                        val json = org.json.JSONArray(next).toString()
                                        q = question.copy(errorReasons = json)
                                        vm.cacheQuestion(question.copy(errorReasons = json))
                                        vm.write { it.updateQuestionErrorReasons(question.id, json) }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    reason, fontSize = 12.5.sp,
                                    color = if (on) Red else Ink2,
                                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            val knowledge = parseKnowledgeTags(question.tags)
            if (knowledge.isNotEmpty()) {
                RichSection("知识点", "本题涉及的主题", Accent) {
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        knowledge.forEach { tag ->
                            Box(
                                Modifier.clip(RoundedCornerShape(8.dp)).background(AccentSoft)
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) { Text(tag, fontSize = 12.sp, lineHeight = 17.sp, color = Ink2) }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            Column(
                Modifier.fillMaxWidth().clip(CardShape).background(CardBg)
                    .border(1.dp, Line, CardShape).padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("掌握度", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                    Spacer(Modifier.width(8.dp))
                    MasteryDots(question.mastery, 7)
                    Spacer(Modifier.weight(1f))
                    Text("复习 ${question.reviewCount} 次", fontSize = 12.sp, color = Muted)
                }
                Spacer(Modifier.height(6.dp))
                Text("0 完全不会 · 5 能独立讲清", fontSize = 12.sp, color = Muted)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(6) { i ->
                        val on = i == question.mastery
                        val segBg by androidx.compose.animation.animateColorAsState(
                            targetValue = if (on) Accent else SurfaceAlt,
                            animationSpec = androidx.compose.animation.core.tween(Motion.Base, easing = Motion.Enter),
                            label = "mastery-bg"
                        )
                        val segFg by androidx.compose.animation.animateColorAsState(
                            targetValue = if (on) OnAccent else Ink2,
                            animationSpec = androidx.compose.animation.core.tween(Motion.Fast),
                            label = "mastery-fg"
                        )
                        val segScale by androidx.compose.animation.core.animateFloatAsState(
                            targetValue = if (on) 1.04f else 1f,
                            animationSpec = androidx.compose.animation.core.tween(Motion.Fast, easing = Motion.Enter),
                            label = "mastery-scale"
                        )
                        Box(
                            Modifier.weight(1f).heightIn(min = 48.dp).clip(SmallShape)
                                .graphicsLayer { scaleX = segScale; scaleY = segScale }
                                .background(segBg)
                                .border(1.dp, segBg, RoundedCornerShape(10.dp))
                                .tjClickable(label = "掌握度 $i") {
                                    q = question.copy(mastery = i)
                                    vm.cacheQuestion(question.copy(mastery = i))
                                    vm.write { it.setMastery(question.id, i) }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "$i", fontSize = 14.sp,
                                fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                                color = segFg
                            )
                        }
                    }
                }
            }
            if (question.note.isNotBlank() || question.noteImages != "[]") {
                Spacer(Modifier.height(12.dp))
                CollapsibleCard(
                    title = "笔记",
                    subtitle = "文字 · 手写拍照 · 可裁剪标注",
                    color = Amber,
                    expanded = vm.noteExpanded,
                    onToggle = { vm.chooseNoteExpanded(!vm.noteExpanded) },
                ) {
                    NoteCardContent(vm, question)
                }
            }
            // 底部留出悬浮追问球的高度，最后一张卡（掌握度）能滚到球上方，不被压住。
            Spacer(Modifier.height(96.dp))
        }
    }

    }
    viewingImage?.let { path ->
        ImageEditorDialog(Uri.fromFile(File(path)), onDismiss = { viewingImage = null }, onSaved = { uri ->
            q = vm.saveQuestionImage(question.id, question.imageId, uri)
            vm.refresh()
            viewingImage = null
        })
    }

    if (editing) {
        EditQuestionScreen(vm, question, vm.categories) { updated ->
            editing = false
            q = updated
            vm.cacheQuestion(updated)
            vm.refresh()
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除这道题？", fontSize = 16.sp) },
            text = { Text("只会在错题本里隐藏，可在「我的 → 回收站」恢复，撤销入口在删除后的提示里。", fontSize = 12.sp, color = Muted) },
            confirmButton = { TextButton(onClick = {
                confirmDelete = false
                vm.write { it.deleteQuestion(question.id) }
                vm.refreshTrash()
                vm.showUndo("已删除 1 道题") { vm.restoreQuestions(listOf(question.id)) }
                vm.back()
            }) { Text("删除", color = Red) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } }
        )
    }
}

/** 详情页可折叠卡片：标题行点击收起/展开，展开状态全局记住。 */
@Composable
private fun CollapsibleCard(
    title: String,
    subtitle: String,
    color: Color,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().clip(CardShape).background(CardBg)
            .border(1.dp, Line, CardShape)
    ) {
        Row(
            Modifier.fillMaxWidth()
                .tjClickable(label = if (expanded) "收起$title" else "展开$title") { onToggle() }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                Text(subtitle, fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 2.dp))
            }
            Icon(
                if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = if (expanded) "收起" else "展开",
                tint = Muted, modifier = Modifier.size(20.dp)
            )
        }
        if (expanded) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) { content() }
        }
    }
}

@Composable
private fun MiniToggle(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(CircleShape)
            .background(if (selected) AccentSoft else SurfaceAlt)
            .border(1.dp, if (selected) Accent.copy(alpha = 0.4f) else Line, CircleShape)
            .tjClickable(label = text) { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text, fontSize = 12.sp,
            color = if (selected) Accent else Ink2,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

private fun hasStemText(stem: String): Boolean = stem.isNotBlank()

private val ERROR_REASONS = listOf("概念不清", "方法没想到", "计算失误", "审题遗漏")

/**
 * 完整编辑页：长文本不再挤在小弹窗里；改动自动暂存成草稿，
 * 误触返回、进程被杀都不丢；保存前可预览最终排版。
 */
@Composable
fun EditQuestionScreen(vm: AppVm, q: QuestionRow, cats: List<CategoryRow>, onDone: (QuestionRow) -> Unit) {
    var tag by remember { mutableStateOf(q.customTag) }
    var stem by remember { mutableStateOf(q.stem) }
    var answer by remember { mutableStateOf(q.answer) }
    var analysis by remember { mutableStateOf(q.analysis) }
    var note by remember { mutableStateOf(q.note) }
    var type by remember { mutableStateOf(q.type) }
    var catId by remember { mutableStateOf(q.categoryId ?: "uncat") }
    // 知识点标签：AI 给的可以删，也可以自己补
    var tags by remember { mutableStateOf(parseKnowledgeTags(q.tags)) }
    var newTag by remember { mutableStateOf("") }
    var showPreview by remember { mutableStateOf(false) }
    var pickingCategory by remember { mutableStateOf(false) }
    var restoredDraft by remember { mutableStateOf(false) }
    val pristine = stem == q.stem && answer == q.answer && analysis == q.analysis &&
        note == q.note && type == q.type && tag == q.customTag && catId == (q.categoryId ?: "uncat") &&
        tags == parseKnowledgeTags(q.tags)

    // 上次没保存的编辑：打开即恢复
    LaunchedEffect(q.id) {
        val saved = vm.loadEditDraft(q.id)
        if (!saved.isNullOrBlank()) {
            runCatching {
                val o = org.json.JSONObject(saved)
                if (o.optString("v").isBlank()) return@runCatching
                tag = o.optString("customTag", q.customTag)
                stem = o.optString("stem", q.stem)
                answer = o.optString("answer", q.answer)
                analysis = o.optString("analysis", q.analysis)
                note = o.optString("note", q.note)
                type = o.optString("type", q.type)
                catId = o.optString("categoryId", q.categoryId ?: "uncat")
                tags = parseKnowledgeTags(o.optString("tags", q.tags))
                restoredDraft = true
            }
        }
    }
    // 自动草稿：停手 900ms 落一次盘；保存成功后由 onDone 侧清除
    LaunchedEffect(tag, stem, answer, analysis, note, type, catId, tags) {
        if (pristine) return@LaunchedEffect
        kotlinx.coroutines.delay(900)
        vm.saveEditDraft(
            q.id,
            org.json.JSONObject().apply {
                put("v", 1)
                put("customTag", tag); put("stem", stem); put("answer", answer)
                put("analysis", analysis); put("note", note); put("type", type); put("categoryId", catId)
                put("tags", org.json.JSONArray(tags).toString())
            }.toString()
        )
    }

    FullScreenDialog(onDismissRequest = { onDone(q.copy()) }) {
        Row(
            Modifier.fillMaxWidth().padding(start = Space.page, end = 10.dp, top = 12.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("编辑题目", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink)
                Text(
                    "改动自动暂存，误触返回也不会丢",
                    fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 2.dp)
                )
            }
            Icon(
                Icons.Rounded.Close, "关闭",
                tint = Ink2, modifier = Modifier.size(48.dp).clip(CircleShape)
                    .tjClickable(label = "关闭编辑") { onDone(q.copy()) }.padding(10.dp)
            )
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Space.page)) {
            if (restoredDraft) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(AmberSoft)
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("已恢复上次没保存的修改", fontSize = 12.sp, color = Amber, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text(
                        "丢弃", fontSize = 12.sp, color = Ink2,
                        modifier = Modifier.tjClickable(label = "丢弃草稿") {
                            restoredDraft = false
                            tag = q.customTag; stem = q.stem; answer = q.answer
                            analysis = q.analysis; note = q.note; type = q.type
                            catId = q.categoryId ?: "uncat"
                            tags = parseKnowledgeTags(q.tags)
                            vm.clearEditDraft(q.id)
                        }.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Spacer(Modifier.height(10.dp))
            }
            TjTextField(value = tag, onValueChange = { tag = it }, label = { Text("自定义标签 / 题号（如：卷一 · 第 12 题）", fontSize = 12.sp) }, singleLine = true, textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = Ink), colors = tjFieldColors(), modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            TjTextField(value = type, onValueChange = { type = it }, label = { Text("题型", fontSize = 12.sp) }, singleLine = true, textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = Ink), colors = tjFieldColors(), modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            TjTextField(value = stem, onValueChange = { stem = it }, label = { Text("题干", fontSize = 12.sp) }, minLines = 4, textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, lineHeight = 23.sp, color = Ink), colors = tjFieldColors(), modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            TjTextField(value = answer, onValueChange = { answer = it }, label = { Text("答案", fontSize = 12.sp) }, minLines = 2, textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, lineHeight = 23.sp, color = Ink), colors = tjFieldColors(), modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            TjTextField(value = analysis, onValueChange = { analysis = it }, label = { Text("解析", fontSize = 12.sp) }, minLines = 4, textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, lineHeight = 23.sp, color = Ink), colors = tjFieldColors(), modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            TjTextField(value = note, onValueChange = { note = it }, label = { Text("笔记（错因、思路、老师讲的话都可以记）", fontSize = 12.sp) }, minLines = 2, textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, lineHeight = 23.sp, color = Ink), colors = tjFieldColors(), modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            // —— 知识点标签：点 × 删除，输入后「添加」补新标签 ——
            Text("知识点标签", fontSize = 12.sp, color = Ink2, fontWeight = FontWeight.SemiBold)
            if (tags.isNotEmpty()) {
                androidx.compose.foundation.layout.FlowRow(
                    Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    tags.forEach { t ->
                        Row(
                            Modifier.clip(RoundedCornerShape(8.dp)).background(AccentSoft)
                                .padding(start = 10.dp, end = 4.dp, top = 5.dp, bottom = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(t, fontSize = 12.sp, color = Ink2)
                            Box(
                                Modifier.size(26.dp).clip(CircleShape)
                                    .tjClickable(label = "删除标签 $t") { tags = tags - t },
                                contentAlignment = Alignment.Center
                            ) { Icon(Icons.Rounded.Close, "删除标签 $t", tint = Muted, modifier = Modifier.size(13.dp)) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TjTextField(
                    value = newTag, onValueChange = { newTag = it },
                    placeholder = { Text("补充知识点，如：导数的几何意义", fontSize = 12.5.sp, color = Muted) },
                    singleLine = true, colors = tjFieldColors(),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onSend = { if (newTag.isNotBlank()) { tags = tags + newTag.trim(); newTag = "" } }
                    ),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Send),
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.heightIn(min = 48.dp).clip(SmallShape)
                        .background(if (newTag.isBlank()) SurfaceAlt else Accent)
                        .tjClickable(enabled = newTag.isNotBlank(), label = "添加标签") {
                            tags = tags + newTag.trim(); newTag = ""
                        }
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "添加", fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        color = if (newTag.isBlank()) Muted else OnAccent
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("分类", fontSize = 12.sp, color = Ink2, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.fillMaxWidth().clip(SmallShape).background(SurfaceAlt)
                    .tjClickable(label = "选择分类") { pickingCategory = true }
                    .padding(horizontal = 14.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    cats.firstOrNull { it.id == catId }?.fullName() ?: "未分类",
                    fontSize = 13.5.sp, color = Ink, modifier = Modifier.weight(1f)
                )
                Icon(Icons.Rounded.ExpandMore, null, tint = Muted, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.height(16.dp))
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Space.page, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TjButton(if (showPreview) "继续编辑" else "排版预览", Modifier.weight(1f), style = ActionStyle.Secondary) {
                showPreview = !showPreview
            }
            TjButton("保存", Modifier.weight(1f), enabled = !pristine) {
                val tagsJson = org.json.JSONArray(tags).toString()
                vm.write {
                    it.updateQuestion(
                        id = q.id, stem = stem, answer = answer, analysis = analysis,
                        type = type, tags = tagsJson, note = note,
                        errorReasons = q.errorReasons, categoryId = catId, customTag = tag
                    )
                }
                vm.clearEditDraft(q.id)
                onDone(
                    q.copy(
                        stem = stem, answer = answer, analysis = analysis, note = note,
                        type = type, customTag = tag.trim(), categoryId = catId, tags = tagsJson,
                        categoryName = cats.firstOrNull { it.id == catId }?.name ?: q.categoryName
                    )
                )
            }
        }
    }

    if (showPreview) {
        FullScreenDialog(onDismissRequest = { showPreview = false }) {
            Row(
                Modifier.fillMaxWidth().padding(start = Space.page, end = 10.dp, top = 12.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("排版预览", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink)
                    Text("公式、表格按最终界面渲染", fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 2.dp))
                }
                Icon(
                    Icons.Rounded.Close, "关闭",
                    tint = Ink2, modifier = Modifier.size(48.dp).clip(CircleShape)
                        .tjClickable(label = "关闭预览") { showPreview = false }.padding(10.dp)
                )
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Space.page)) {
                if (stem.isNotBlank()) {
                    RichSection("题目", "识别到的题干与选项", Ink2) {
                        RichText(stem, fontSize = 16, interactive = false)
                    }
                    Spacer(Modifier.height(12.dp))
                }
                if (answer.isNotBlank()) {
                    RichSection("答案", "核对最终结论", Green) { RichText(answer, fontSize = 15, interactive = false) }
                    Spacer(Modifier.height(12.dp))
                }
                if (analysis.isNotBlank()) {
                    RichSection("解析", "按步骤复盘思路", Accent) { RichText(analysis, fontSize = 15, interactive = false) }
                }
                Spacer(Modifier.height(20.dp))
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Space.page, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                BigButton("返回编辑", Modifier.weight(1f)) { showPreview = false }
            }
        }
    }

    if (pickingCategory) {
        CategoryPicker(
            current = cats.firstOrNull { it.id == catId }?.fullName() ?: "未分类",
            categories = cats.map { it.fullName() },
            onDismiss = { pickingCategory = false },
            onPick = { name ->
                catId = cats.firstOrNull { it.fullName() == name }?.id ?: "uncat"
                pickingCategory = false
            }
        )
    }
}

@Composable
fun RichSection(
    title: String,
    subtitle: String,
    color: Color,
    content: @Composable () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().clip(CardShape).background(CardBg)
            .border(1.dp, Line, CardShape).padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
            Column(Modifier.padding(start = 8.dp)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                Text(subtitle, fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 2.dp))
            }
        }
        Spacer(Modifier.height(14.dp))
        content()
    }
}

@Composable
fun RadioDot(selected: Boolean) {
    Box(
        Modifier.size(16.dp).clip(CircleShape).border(1.4.dp, if (selected) Accent else FieldBorder, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (selected) Box(Modifier.size(8.dp).clip(CircleShape).background(Accent))
    }
}

@Composable
fun SearchRow(q: QuestionRow, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().tjClickable { onClick() }.padding(horizontal = Space.page, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Thumb(q.imagePath, Modifier.width(38.dp).height(46.dp))
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            StemText(q.stem, color = Ink, fontSizeSp = 13f, lineHeightSp = 19f, blankHint = "（无题干）")
            Text(
                listOf(q.customTag, q.categoryName, q.type).filter { it.isNotBlank() }.joinToString(" · "),
                fontSize = 11.5.sp, color = Muted, modifier = Modifier.padding(top = 3.dp)
            )
        }
    }
    HorizontalDivider(color = Line, thickness = 1.dp)
}

@Composable
fun Thumb(path: String?, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(6.dp)).background(SurfaceAlt).border(1.dp, Line, RoundedCornerShape(6.dp))) {
        if (path != null) {
            AsyncImage(model = File(path), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text("无图", fontSize = 10.sp, color = Muted, modifier = Modifier.align(Alignment.Center))
        }
    }
}

@Composable
fun ZoomableImage(file: File, maxHeight: Dp = 240.dp, onClick: () -> Unit = {}) {
    Box(Modifier.fillMaxWidth().heightIn(max = maxHeight).clip(SmallShape)
        .background(SurfaceAlt).border(1.dp, Line, SmallShape).tjClickable(label = "查看题目原图") { onClick() }) {
        AsyncImage(model = file, contentDescription = "查看题目原图", contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp, max = maxHeight))
        Box(
            Modifier.align(Alignment.BottomEnd).padding(8.dp).clip(CircleShape)
                .background(Color(0xCC000000)).padding(horizontal = 9.dp, vertical = 5.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.ZoomOutMap, null, tint = Color.White, modifier = Modifier.size(13.dp))
                Text("放大", fontSize = 11.sp, color = Color.White, modifier = Modifier.padding(start = 4.dp))
            }
        }
    }
}

@Composable
fun LabelValue(label: String, value: String) {
    Column {
        Text(label, fontSize = 11.sp, letterSpacing = 1.sp, color = Muted)
        Text(value, fontSize = 12.5.sp, lineHeight = 20.sp, color = Ink, modifier = Modifier.padding(top = 4.dp))
    }
}

/** 笔记编辑上下文：source 是待编辑的图，oldImageId 非空表示替换已有笔记图，tempFile 是拍照临时文件。 */
private data class NoteEdit(val source: Uri, val oldImageId: String?, val tempFile: File?)

/**
 * 笔记卡：文字 + 手写拍照图片。图片走拍照/相册导入，统一先进原图编辑器
 * （裁剪/标注）再入库；点缩略图是全屏预览（可缩放），要改再点预览右上角的编辑。
 */
@Composable
private fun NoteCardContent(vm: AppVm, question: QuestionRow) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var refs by remember(question.noteImages) {
        mutableStateOf<List<Pair<String, String>>>(emptyList())
    }
    LaunchedEffect(question.noteImages) {
        refs = withContext(Dispatchers.IO) { vm.db.noteImageRefs(question.noteImages) }
    }
    var editing by remember { mutableStateOf<NoteEdit?>(null) }
    var viewing by remember { mutableStateOf<Pair<String, String>?>(null) } // imageId to path
    var cameraFile by remember { mutableStateOf<File?>(null) }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = cameraFile; cameraFile = null
        if (ok && f != null && f.exists()) editing = NoteEdit(Uri.fromFile(f), null, f)
        else f?.delete()
    }
    val pickNote = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) editing = NoteEdit(uri, null, null)
    }
    if (question.note.isNotBlank()) {
        Text(question.note, fontSize = 13.sp, lineHeight = 20.sp, color = Ink2)
        Spacer(Modifier.height(10.dp))
    }
    if (refs.isNotEmpty()) {
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            refs.forEach { (id, path) ->
                Box(Modifier.size(84.dp)) {
                    AsyncImage(
                        model = File(path), contentDescription = "笔记图片",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(SmallShape)
                            .border(1.dp, Line, SmallShape)
                            .tjClickable(label = "预览这张笔记图片") {
                                viewing = id to path
                            }
                    )
                    Box(
                        Modifier.align(Alignment.TopEnd).padding(4.dp).size(24.dp)
                            .clip(CircleShape).background(Color(0xCC000000))
                            .tjClickable(label = "移除这张笔记图片") {
                                vm.removeNoteImage(question.id, id)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Close, "移除", tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TjButton("拍照导入", Modifier.weight(1f), style = ActionStyle.Secondary) {
            val dir = File(ctx.cacheDir, "note_camera").apply { mkdirs() }
            val f = File(dir, "note_${System.currentTimeMillis()}.jpg")
            val uri = androidx.core.content.FileProvider.getUriForFile(
                ctx, "${ctx.packageName}.fileprovider", f
            )
            cameraFile = f
            runCatching { takePicture.launch(uri) }.onFailure {
                f.delete(); cameraFile = null; vm.toast = "无法打开相机，请从相册添加"
            }
        }
        TjButton("从相册导入", Modifier.weight(1f), style = ActionStyle.Secondary) {
            pickNote.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }

    viewing?.let { (imageId, path) ->
        NoteImageViewerDialog(
            path = path,
            onDismiss = { viewing = null },
            onEdit = {
                viewing = null
                editing = NoteEdit(Uri.fromFile(File(path)), imageId, null)
            },
        )
    }

    editing?.let { e ->
        ImageEditorDialog(
            uri = e.source,
            onDismiss = { e.tempFile?.delete(); editing = null },
            onSaved = { uri ->
                runCatching {
                    if (e.oldImageId != null) vm.replaceNoteImage(question.id, e.oldImageId, uri)
                    else vm.addNoteImage(question.id, uri)
                    vm.refresh()
                }.onFailure { vm.toast = "保存失败：${it.message}" }
                e.tempFile?.delete()
                editing = null
            }
        )
    }
}

/** 笔记图片全屏预览：双指缩放、拖动平移；右上角进编辑，平时点开只读不打误触。 */
@Composable
private fun NoteImageViewerDialog(path: String, onDismiss: () -> Unit, onEdit: () -> Unit) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    FullScreenDialog(onDismissRequest = onDismiss) {
        androidx.compose.foundation.layout.Box(
            Modifier.fillMaxSize().background(Color(0xFF101113))
        ) {
            AsyncImage(
                model = File(path), contentDescription = "笔记图片预览",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 6f)
                            offset = if (scale > 1f) offset + pan else androidx.compose.ui.geometry.Offset.Zero
                        }
                    }
                    .graphicsLayer {
                        scaleX = scale; scaleY = scale
                        translationX = offset.x; translationY = offset.y
                    }
            )
            Row(
                Modifier.fillMaxWidth()
                    .background(Color(0x99000000))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape)
                        .tjClickable(label = "关闭预览") { onDismiss() },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Rounded.Close, "关闭", tint = Color.White, modifier = Modifier.size(21.dp)) }
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    Text("笔记图片", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    Text("双指缩放 · 拖动查看", fontSize = 11.sp, color = Color(0xCCFFFFFF))
                }
                IconCircle(Icons.Rounded.Edit, "编辑这张图片", tint = Color.White, filled = true) { onEdit() }
            }
        }
    }
}

@Composable
fun BigButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    TjButton(text, modifier, enabled = enabled, onClick = onClick)
}

@Composable
fun BusyOverlay(label: String) {
            Box(Modifier.fillMaxSize().background(Color(0x99000000)).pointerInput(Unit) {
                awaitPointerEventScope { while (true) { awaitPointerEvent().changes.forEach { it.consume() } } }
            }, contentAlignment = Alignment.Center) {
        Column(
            Modifier.clip(RoundedCornerShape(14.dp)).background(SurfaceAlt).padding(horizontal = 24.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            androidx.compose.material3.CircularProgressIndicator(Modifier.size(26.dp), color = Accent, strokeWidth = 2.dp)
            Text(label, fontSize = 12.5.sp, color = Ink2, modifier = Modifier.padding(top = 10.dp))
        }
    }
}

@Composable
fun ExportSheet(count: Int, onDismiss: () -> Unit, onExport: (PdfExporter.Options, Boolean) -> Unit) {
    var onePerPage by remember { mutableStateOf(true) }
    var answer by remember { mutableStateOf(true) }
    var analysis by remember { mutableStateOf(true) }
    var tags by remember { mutableStateOf(true) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = CardBg) {
        Column(Modifier.blockSheetDrag().padding(horizontal = Space.page).padding(bottom = 26.dp)) {
            Text("导出 PDF", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text("已选 $count 题 · A4 / 2cm 页边距", fontSize = 12.5.sp, color = Muted, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
            OptRow("版式", if (onePerPage) "一题一页" else "紧凑") { onePerPage = !onePerPage }
            OptRow("答案", if (answer) "包含" else "不包含") { answer = !answer }
            OptRow("解析", if (analysis) "包含" else "不包含") { analysis = !analysis }
            OptRow("知识点标签", if (tags) "包含" else "不包含") { tags = !tags }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TjButton("生成并分享", Modifier.weight(1f).fillMaxHeight(), style = ActionStyle.Secondary) {
                    onExport(PdfExporter.Options(answer, analysis, tags, onePerPage), true)
                }
                TjButton("保存 PDF", Modifier.weight(1f).fillMaxHeight()) {
                    onExport(PdfExporter.Options(answer, analysis, tags, onePerPage), false)
                }
            }
        }
    }
}

@Composable
private fun OptRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().tjClickable { onClick() }.padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 12.sp, color = Ink)
        Spacer(Modifier.weight(1f))
        Text(value, fontSize = 11.sp, color = Muted)
    }
    HorizontalDivider(color = Line, thickness = 1.dp)
}

fun knowledgeText(q: QuestionRow): String = try {
    val arr = org.json.JSONArray(q.tags)
    (0 until arr.length()).joinToString(" / ") { arr.optString(it) }
} catch (e: Exception) { "" }

fun parseColor(hex: String): Color = try {
    Color(android.graphics.Color.parseColor(hex))
} catch (e: Exception) { SurfaceAlt }

fun dateStamp(): String = SimpleDateFormat("yyyyMMdd", Locale.CHINA).format(Date())


@Composable
private fun RecentCard(q: QuestionRow, onClick: () -> Unit) {
    Column(
        Modifier.width(148.dp)
            .bounceClick(label = "打开错题", onClick = onClick)
            .clip(RoundedCornerShape(16.dp)).background(CardBg)
            .border(1.dp, Line, RoundedCornerShape(16.dp))
    ) {
        Box(Modifier.fillMaxWidth().height(100.dp).background(SurfaceAlt)) {
            if (q.imagePath != null) {
                AsyncImage(
                    model = File(q.imagePath), contentDescription = null,
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                )
            } else {
                Text("题", fontSize = 22.sp, color = Muted, modifier = Modifier.align(Alignment.Center))
            }
        }
        Column(Modifier.padding(horizontal = 12.dp, vertical = 14.dp)) {
            StemText(q.stem, color = Ink, fontSizeSp = 13f, lineHeightSp = 19f, maxLines = 2, blankHint = "（无题干）")
            Text(
                listOf(q.customTag, q.categoryName).filter { it.isNotBlank() }.joinToString(" · "),
                fontSize = 11.sp, color = Muted, modifier = Modifier.padding(top = 5.dp), maxLines = 1
            )
        }
    }
}


@Composable
private fun OverviewCell(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value, fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.Medium,
            color = if (value == "0") Muted else Ink, fontFamily = NumFont, letterSpacing = (-0.8).sp
        )
        Text(label, fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 3.dp))
    }
}


/** 粗判 AI 输出是否被截断：$ 未闭合或 \begin 环境没收尾。
 *  不再按结尾标点判断：正常解析以冒号逗号收尾的误报太多。 */
private fun looksTruncated(text: String): Boolean {
    val t = text.trim()
    if (t.length < 8) return false
    if (t.count { it == '$' } % 2 != 0) return true
    val bs = 0x5C.toChar().toString()
    return t.contains(bs + "begin{") && !t.contains(bs + "end{")
}
