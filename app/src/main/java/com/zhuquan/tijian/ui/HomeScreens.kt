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
fun HomeScreen(vm: AppVm, onImport: () -> Unit) {    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<QuestionRow>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf(false) }
    var searchRevision by remember { mutableStateOf(0) }
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

        if (searchOpen) {
            TjTextField(
                value = query, onValueChange = { query = it },
                placeholder = { Text("搜索题干、知识点", fontSize = 12.sp, color = Muted) },
                singleLine = true,
                colors = tjFieldColors(),
                modifier = Modifier.fillMaxWidth().padding(horizontal = Space.page, vertical = 4.dp)
            )
        }

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
                EmptyState("没有匹配的题", "换个关键词试试")
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
            ReviewHero(vm.stats.due, vm.stats.total, vm.stats.mastered) {
                if (vm.stats.total == 0) onImport() else vm.startReview(includeAll = vm.stats.due == 0)
            }

            // 搜索/导入原先在标题栏右上角，单手够不到；挪到「学科」这一行的最右侧，
            // 和内容区同一条水平线，拇指自然就能碰到。
            SectionLabel("学科", Modifier.padding(horizontal = Space.page)) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconCircle(Icons.Rounded.Search, "搜索") { searchOpen = !searchOpen; if (!searchOpen) query = "" }
                    IconCircle(Icons.Rounded.Add, "导入错题", tint = Color.White, filled = true) { onImport() }
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
            TjChip("未掌握", filter == "weak") { filter = "weak" }
            TjChip("本周", filter == "week") { filter = "week" }
            TjChip("有错因", filter == "errors") { filter = "errors" }
        }

        Spacer(Modifier.height(12.dp))

                CardBox(Modifier.padding(horizontal = Space.page).fillMaxWidth().weight(1f)) {
            Column {
                Row(Modifier.fillMaxWidth().background(SurfaceAlt).padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text(if (selecting) "" else "题目", fontSize = 11.sp, color = Ink2, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(56.dp))
                    Spacer(Modifier.width(14.dp))
                    Text("题型", fontSize = 11.sp, color = Ink2, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(64.dp))
                    Text("知识点", fontSize = 11.sp, color = Ink2, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text("掌握", fontSize = 11.sp, color = Ink2, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(56.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                }
                LazyColumn(Modifier.weight(1f), state = listState) {
                    items(rows, key = { it.id }) { q ->
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
                            Spacer(Modifier.width(14.dp))
                            Text(q.type.ifBlank { "—" }, fontSize = 12.sp, color = Ink2, modifier = Modifier.width(64.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val tags = parseKnowledgeTags(q.tags)
                            Row(
                                Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (q.customTag.isNotBlank()) {
                                    Box(
                                        Modifier.clip(RoundedCornerShape(6.dp))
                                            .background(AmberSoft)
                                            .border(1.dp, Amber.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                                            .padding(horizontal = 7.dp, vertical = 4.dp)
                                    ) {
                                        Text(q.customTag, fontSize = 11.5.sp, color = Amber, maxLines = 1, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                                if (tags.isEmpty() && q.customTag.isBlank()) {
                                    Text("—", fontSize = 12.sp, color = Muted)
                                } else {
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
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
        TjTopBar(
            title = question.type.ifBlank { "题目" },
            subtitle = listOf(question.customTag, question.categoryName)
                .filter { it.isNotBlank() }.joinToString(" · "),
            onBack = { vm.back() },
            actions = {
                IconCircle(Icons.Rounded.Edit, "编辑") { editing = true }
                IconCircle(Icons.Rounded.DeleteOutline, "删除", tint = Red) { confirmDelete = true }
            }
        )
        Column(Modifier.fillMaxSize().verticalScroll(detailScroll).padding(horizontal = Space.page, vertical = 8.dp)) {
            if (question.imagePath != null) {
                CardBox {
                    Column(Modifier.padding(10.dp)) {
                        ZoomableImage(File(question.imagePath), maxHeight = 200.dp) { viewingImage = question.imagePath }
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
            val stem = buildString {
                append(question.stem)
                parseOptions(question.options).forEach {
                    append(10.toChar()); append(10.toChar()); append(it)
                }
            }
            if (stem.isBlank()) {
                Text("（未识别题干，点下方编辑按钮补录）", fontSize = 12.sp, color = Muted)
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
            // 题目 / 答案 / 解析 / 需要掌握 合成一份 HTML：整页只有一个 WebView，
            // KaTeX 只加载一次。8.8.0 每条「需要掌握」各开一个 WebView，打开详情页最多十几个。
            val docSections = buildList {
                if (stem.isNotBlank()) add(RichSectionSpec("题目", stem, "", Muted.toHexColor(), 16, kind = "stem"))
                if (question.answer.isNotBlank()) add(RichSectionSpec("答案", question.answer, "", Accent.toHexColor(), 17, kind = "answer"))
                if (question.analysis.isNotBlank()) add(RichSectionSpec("解析", question.analysis, "", Ink2.toHexColor(), 15, kind = "analysis"))
                if (hasNeeds) add(RichSectionSpec("要做出这题，需要掌握", needsMd, "", Amber.toHexColor(), 14, kind = "needs"))
            }
            if (docSections.isNotEmpty()) {
                RichText(
                    markdown = "",
                    sections = docSections,
                    selectable = true,
                    onAsk = { vm.openChat(it) },
                )
            }
            if (question.answer.isBlank() || question.analysis.isBlank()) {
                Spacer(Modifier.height(12.dp))
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
            }
            if (!hasNeeds) {
            Spacer(Modifier.height(12.dp))
            RichSection("要做出这题，需要掌握", "公式 · 概念 · 方法", Amber) {
                Column {
                    run {
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
            }
            }

            val knowledge = parseKnowledgeTags(question.tags)
            if (knowledge.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
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
            }
            if (question.analysis.isNotBlank()) {
                if (looksTruncated(question.analysis)) {
                    Text(
                        "这段解析可能被模型截断（公式或括号未闭合），可点下方编辑按钮补全，或重新识别。",
                        fontSize = 11.sp, color = Red,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
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
                Text("0 完全不会 · 5 能独立讲清", fontSize = 11.5.sp, color = Muted)
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
                Spacer(Modifier.height(16.dp))
                NoteCard(vm, question)
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
        EditQuestionDialog(vm, question, vm.categories) { updated ->
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
            text = { Text("只会在错题本里隐藏，原图仍保留。", fontSize = 12.sp, color = Muted) },
            confirmButton = { TextButton(onClick = {
                confirmDelete = false
                vm.write { it.deleteQuestion(question.id) }
                vm.back()
            }) { Text("删除", color = Red) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } }
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
fun EditQuestionDialog(vm: AppVm, q: QuestionRow, cats: List<CategoryRow>, onDone: (QuestionRow) -> Unit) {
    var stem by remember { mutableStateOf(q.stem) }
    var answer by remember { mutableStateOf(q.answer) }
    var analysis by remember { mutableStateOf(q.analysis) }
    var note by remember { mutableStateOf(q.note) }
    var type by remember { mutableStateOf(q.type) }
    var tag by remember { mutableStateOf(q.customTag) }
    var catId by remember { mutableStateOf(q.categoryId ?: "uncat") }
    AlertDialog(
        onDismissRequest = { onDone(q.copy()) },
        title = { Text("编辑题目", fontSize = 16.sp) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                TjTextField(value = tag, onValueChange = { tag = it }, label = { Text("自定义标签（如：卷一 · 第 12 题）", fontSize = 12.sp) }, singleLine = true, textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = Ink), colors = tjFieldColors(), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                TjTextField(value = stem, onValueChange = { stem = it }, label = { Text("题干", fontSize = 12.sp) }, minLines = 3, textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, lineHeight = 22.sp, color = Ink), colors = tjFieldColors(), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                TjTextField(value = answer, onValueChange = { answer = it }, label = { Text("答案", fontSize = 12.sp) }, textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = Ink), colors = tjFieldColors(), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                TjTextField(value = analysis, onValueChange = { analysis = it }, label = { Text("解析", fontSize = 12.sp) }, minLines = 2, textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, lineHeight = 22.sp, color = Ink), colors = tjFieldColors(), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                TjTextField(value = note, onValueChange = { note = it }, label = { Text("笔记", fontSize = 12.sp) }, textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = Ink), colors = tjFieldColors(), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                TjTextField(value = type, onValueChange = { type = it }, label = { Text("题型", fontSize = 12.sp) }, textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = Ink), colors = tjFieldColors(), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                Text("分类", fontSize = 11.sp, color = Muted)
                Column {
                    cats.forEach { c ->
                        Row(Modifier.fillMaxWidth().tjClickable { catId = c.id }.padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioDot(catId == c.id)
                            Text(c.fullName(), fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                vm.write {
                    it.updateQuestion(
                        id = q.id, stem = stem, answer = answer, analysis = analysis,
                        type = type, tags = q.tags, note = note,
                        errorReasons = q.errorReasons, categoryId = catId, customTag = tag
                    )
                }
                onDone(
                    q.copy(
                        stem = stem, answer = answer, analysis = analysis, note = note,
                        type = type, customTag = tag.trim(), categoryId = catId,
                        categoryName = cats.firstOrNull { it.id == catId }?.name ?: q.categoryName
                    )
                )
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = { onDone(q.copy()) }) { Text("取消") } }
    )
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
            Text(cleanMathLite(q.stem).ifBlank { "（无题干）" }, fontSize = 13.sp, lineHeight = 19.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, color = Ink)
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
 * （裁剪/标注）再入库；点缩略图可再次编辑，右上角 X 移除。
 */
@Composable
private fun NoteCard(vm: AppVm, question: QuestionRow) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var refs by remember(question.noteImages) {
        mutableStateOf<List<Pair<String, String>>>(emptyList())
    }
    LaunchedEffect(question.noteImages) {
        refs = withContext(Dispatchers.IO) { vm.db.noteImageRefs(question.noteImages) }
    }
    var editing by remember { mutableStateOf<NoteEdit?>(null) }
    var cameraFile by remember { mutableStateOf<File?>(null) }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = cameraFile; cameraFile = null
        if (ok && f != null && f.exists()) editing = NoteEdit(Uri.fromFile(f), null, f)
        else f?.delete()
    }
    val pickNote = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) editing = NoteEdit(uri, null, null)
    }

    RichSection("笔记", "文字 · 手写拍照 · 可裁剪标注", Amber) {
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
                                .tjClickable(label = "编辑这张笔记图片") {
                                    editing = NoteEdit(Uri.fromFile(File(path)), id, null)
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
            Text(label, fontSize = 11.5.sp, color = Ink2, modifier = Modifier.padding(top = 10.dp))
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
            Text(
                cleanMathLite(q.stem).ifBlank { "（无题干）" },
                fontSize = 13.sp, lineHeight = 19.sp, color = Ink,
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
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
        Text(label, fontSize = 11.5.sp, color = Muted, modifier = Modifier.padding(top = 3.dp))
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
