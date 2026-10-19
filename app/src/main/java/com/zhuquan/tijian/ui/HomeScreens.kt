@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.zhuquan.tijian.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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

private val GLYPHS = listOf("∫", "△", "∑", "∿", "∂", "√", "π", "⚗", "≈", "∞")

@Composable
fun HomeScreen(vm: AppVm, onImport: () -> Unit) {
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember(query) { mutableStateOf<List<QuestionRow>?>(null) }
    LaunchedEffect(query) {
        results = if (query.isBlank()) null else withContext(Dispatchers.IO) { vm.db.questions(query = query) }
    }

    Column(Modifier.fillMaxSize()) {
        TjTopBar(
            title = "错题本",
            subtitle = "${vm.stats.total} 道错题",
            actions = {
                IconCircle(Icons.Rounded.Search, "搜索") { searchOpen = !searchOpen }
                IconCircle(Icons.Rounded.Add, "导入错题", tint = Color.White, filled = true) { onImport() }
            }
        )

        if (searchOpen) {
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                placeholder = { Text("搜索题干、知识点", fontSize = 12.sp, color = Muted) },
                singleLine = true,
                colors = tjFieldColors(),
                modifier = Modifier.fillMaxWidth().padding(horizontal = Space.page, vertical = 4.dp)
            )
        }

        if (results != null) {
            val list = results!!
            if (list.isEmpty()) {
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
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 132.dp)
        ) {
            ReviewHero(vm.stats.due, vm.stats.total, vm.stats.mastered) { vm.startReview() }

            SectionLabel("学科", Modifier.padding(horizontal = Space.page))

            Column(
                Modifier.fillMaxWidth().padding(horizontal = Space.page),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SUBJECT_ROOTS.forEach { subject ->
                    val children = vm.categories.filter { it.subjectGroup() == subject }
                    SubjectRow(
                        name = subject,
                        count = children.sumOf { it.count },
                        mastered = children.sumOf { it.mastered },
                        onClick = { vm.go(Route.Subject(subject)) }
                    )
                }
            }
            run {
                var recent by remember { mutableStateOf<List<QuestionRow>>(emptyList()) }
                LaunchedEffect(vm.stats.total, vm.stats.due, vm.dataRevision) {
                    recent = withContext(Dispatchers.IO) { vm.db.questions().take(8) }
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
    Column(
        Modifier.padding(horizontal = Space.page).fillMaxWidth()
            .clip(RoundedCornerShape(20.dp)).background(Ink)
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text("今日复习", fontSize = 11.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.SemiBold, color = OnHeroKicker)
                Text(
                    if (due > 0) "间隔 0/1/3/7/16/35 天，按到期排序" else "今天没有到期任务",
                    fontSize = 12.5.sp, color = OnHeroMuted, modifier = Modifier.padding(top = 8.dp)
                )
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text("$due", fontSize = 52.sp, lineHeight = 54.sp, fontWeight = FontWeight.Light, color = Color.White, letterSpacing = (-2).sp)
                Text("题", fontSize = 13.sp, color = OnHeroMuted, modifier = Modifier.padding(bottom = 10.dp, start = 5.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
        // 4dp 进度条：白色 16% 轨道 + 明确填充，深色底上也能看清
        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x38FFFFFF))) {
            Box(
                Modifier.fillMaxWidth(if (total == 0) 0f else (mastered.toFloat() / total).coerceIn(0f, 1f))
                    .height(4.dp).background(Color(0xFF7C8FFF))
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("已掌握 $mastered / $total", fontSize = 12.sp, color = OnHeroMuted)
            Spacer(Modifier.weight(1f))
            Text("约 ${maxOf(1, due * 2 / 3 + 1)} 分钟", fontSize = 12.sp, color = OnHeroMuted)
        }
        Spacer(Modifier.height(16.dp))
        Box(
            Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(12.dp))
                .background(if (due > 0) Accent else Color(0xFF2A2F39))
                .tjClickable(label = if (due > 0) "开始复习" else "查看全部错题") { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (due > 0) "开始复习" else "查看全部错题",
                fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White
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
private fun SubjectRow(name: String, count: Int, mastered: Int, onClick: () -> Unit) {
    val hasQuestions = count > 0
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardBg)
            .border(1.dp, Line, RoundedCornerShape(14.dp))
            .bounceClick(label = name, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(11.dp))
                .background(if (hasQuestions) AccentSoft else SurfaceAlt),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                subjectIcon(name), null,
                tint = if (hasQuestions) Accent else Muted,
                modifier = Modifier.size(20.dp)
            )
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    color = if (hasQuestions) Ink else Ink2
                )
                Spacer(Modifier.weight(1f))
                if (hasQuestions) {
                    Text("$count 道", fontSize = 12.5.sp, color = Ink2, fontWeight = FontWeight.Medium)
                } else {
                    Text("暂无错题", fontSize = 12.sp, color = Muted)
                }
            }
            if (hasQuestions) {
                Spacer(Modifier.height(7.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(SurfaceAlt)
                    ) {
                        Box(
                            Modifier.fillMaxWidth((mastered.toFloat() / count).coerceIn(0f, 1f))
                                .height(4.dp).background(Green)
                        )
                    }
                    Text(
                        "已掌握 $mastered / $count",
                        fontSize = 11.5.sp, color = Ink2,
                        modifier = Modifier.padding(start = 10.dp)
                    )
                }
            }
        }
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight, null,
            tint = Color(0xFF98A3B3), modifier = Modifier.size(20.dp).padding(start = 6.dp)
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
    var showExport by remember { mutableStateOf(false) }
    var pendingFile by remember { mutableStateOf<File?>(null) }
    val scope = rememberCoroutineScope()

    val listState = androidx.compose.foundation.lazy.rememberLazyListState(
        initialFirstVisibleItemIndex = vm.categoryScroll[id] ?: 0
    )
    androidx.compose.runtime.DisposableEffect(id) {
        onDispose { vm.categoryScroll[id] = listState.firstVisibleItemIndex }
    }
    var rows by remember(id, filter) { mutableStateOf<List<QuestionRow>>(emptyList()) }
    LaunchedEffect(id, filter, vm.dataRevision) {
        rows = withContext(Dispatchers.IO) { vm.db.questions(id, filter) }
    }

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
                        vm.db.confirmCategory(id); vm.refresh()
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

                CardBox(Modifier.padding(horizontal = Space.page).fillMaxWidth()) {
            Column {
                Row(Modifier.fillMaxWidth().background(SurfaceAlt).padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text(if (selecting) "" else "题目", fontSize = 11.sp, color = Ink2, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(48.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("题型", fontSize = 11.sp, color = Ink2, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(64.dp))
                    Text("知识点", fontSize = 11.sp, color = Ink2, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text("掌握", fontSize = 11.sp, color = Ink2, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(56.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                }
                val tableHeight = ((rows.size * 80 + 44).coerceIn(96, 520)).dp
                LazyColumn(Modifier.height(tableHeight), state = listState) {
                    items(rows, key = { it.id }) { q ->
                        val isSel = selected.value.contains(q.id)
                        Row(
                            Modifier.fillMaxWidth().height(80.dp).tjClickable {
                                if (selecting) {
                                    selected.value = if (isSel) selected.value - q.id else selected.value + q.id
                                } else vm.go(Route.Detail(q.id))
                            }.padding(horizontal = 14.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.width(48.dp)) {
                                if (selecting) {
                                    Checkbox(checked = isSel, onCheckedChange = null, modifier = Modifier.size(22.dp))
                                } else {
                                    Thumb(q.imagePath, Modifier.width(48.dp).height(60.dp))
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(q.type.ifBlank { "—" }, fontSize = 12.sp, color = Ink2, modifier = Modifier.width(64.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                .clip(RoundedCornerShape(16.dp)).background(Ink)
                .tjClickable { vm.startReviewFor(id) }
                .padding(horizontal = Space.page, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("复习这组", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                Text(
                    "${rows.count { it.mastery < 4 }} 道未掌握 · 约 ${maxOf(1, rows.size / 3)} 分钟",
                    fontSize = 11.sp, color = Color(0xD9FFFFFF), modifier = Modifier.padding(top = 3.dp)
                )
            }
            Box(
                Modifier.clip(RoundedCornerShape(999.dp)).background(Color.White)
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) { Text("开始", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = Ink) }
        }

        Spacer(Modifier.weight(1f))

        if (selecting && selected.value.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().background(Ink).padding(horizontal = Space.page, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("已选 ${selected.value.size} 题", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.clip(RoundedCornerShape(9.dp)).background(Color.White)
                        .tjClickable { showExport = true }.padding(horizontal = 14.dp, vertical = 8.dp)
                ) { Text("导出 PDF", fontSize = 11.5.sp, color = Ink, fontWeight = FontWeight.SemiBold) }
            }
        }
    }

    if (showExport) {
        ExportSheet(
            count = selected.value.size,
            onDismiss = { showExport = false },
            onExport = { opts, share ->
                val list = rows.filter { selected.value.contains(it.id) }
                showExport = false
                scope.launch {
                    vm.busy = true
                    vm.busyLabel = "排版 PDF"
                    val f = vm.buildPdf(list, cat?.name ?: "错题导出", opts)
                    vm.busy = false; vm.busyLabel = ""
                    if (share) shareFile(vm.getApplication(), f)
                    else { pendingFile = f; createPdf.launch("${cat?.name ?: "错题"}_${dateStamp()}.pdf") }
                }
            }
        )
    }

    if (vm.busy && (showExport || selecting)) BusyOverlay(vm.busyLabel)
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
    var q by remember(id) { mutableStateOf<QuestionRow?>(null) }
    var loadError by remember(id) { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var viewingImage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val readySections = remember(id) { androidx.compose.runtime.mutableStateMapOf<String, Boolean>() }

    LaunchedEffect(id) {
        runCatching { withContext(Dispatchers.IO) { vm.db.question(id) ?: error("题目不存在或已删除") } }
            .onSuccess { q = it }.onFailure { loadError = it.message ?: "题目读取失败"; vm.pageReady(Route.Detail(id)) }
    }

    val question = q ?: run {
        Column(Modifier.fillMaxSize()) {
            TjTopBar(title = "题目", subtitle = if (loadError == null) "正在加载" else "读取失败", onBack = { vm.back() })
            Text(loadError ?: "正在读取题目…", color = Muted, modifier = Modifier.padding(18.dp))
        }
        return
    }
    val expectedSections = listOf("stem" to (question.stem.isNotBlank() || parseOptions(question.options).isNotEmpty()),
        "answer" to question.answer.isNotBlank(), "analysis" to question.analysis.isNotBlank()).filter { it.second }.map { it.first }
    LaunchedEffect(id, readySections.toMap(), expectedSections) {
        if (expectedSections.all { readySections[it] == true }) vm.pageReady(Route.Detail(id))
    }
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
        TjTopBar(
            title = question.type.ifBlank { "题目" },
            subtitle = question.categoryName,
            onBack = { vm.back() },
            actions = {
                IconCircle(Icons.Rounded.Edit, "编辑") { editing = true }
                IconCircle(Icons.Rounded.DeleteOutline, "删除", tint = Red) { confirmDelete = true }
            }
        )
        Column(Modifier.fillMaxSize().verticalScroll(detailScroll).padding(horizontal = Space.page, vertical = 8.dp)) {
            if (question.imagePath != null) {
                CardBox {
                    Column(Modifier.padding(12.dp)) {
                        Text("题目原图", fontSize = 12.sp, color = Muted)
                        Spacer(Modifier.height(12.dp))
                        ZoomableImage(File(question.imagePath)) { viewingImage = question.imagePath }
                        Text("点击查看原画质 / 裁剪", color = Ink2, modifier = Modifier.padding(top = 12.dp))
                        question.imageId?.let { imageId ->
                            val original = remember(imageId) { vm.db.originalImagePath(imageId) }
                            if (original != null && original != question.imagePath) TextButton(onClick = { viewingImage = original }) { Text("查看未裁剪原图") }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
            val stem = buildString {
                append(question.stem)
                parseOptions(question.options).forEach {
                    append(10.toChar()); append(10.toChar()); append(it)
                }
            }
            if (stem.isBlank()) {
                Text("（未识别题干，点下方编辑按钮补录）", fontSize = 12.sp, color = Muted)
            } else {
                RichSection("题目", "题干与选项", Ink2) {
                    RichText(stem, fontSize = 16, onReady = { readySections["stem"] = true })
                }
            }
            if (question.answer.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                RichSection("答案", "先看结论，再对照解析", Green) {
                    RichText(question.answer, fontSize = 15, onReady = { readySections["answer"] = true })
                }
            }
            if (question.analysis.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                RichSection("解析", "按步骤展开推理", Accent) {
                    RichText(question.analysis, fontSize = 15, onReady = { readySections["analysis"] = true })
                }
            }
            val knowledge = parseKnowledgeTags(question.tags)
            if (knowledge.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                RichSection("知识点", "本题涉及的主题", Amber) {
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
                        Box(
                            Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(10.dp))
                                .background(if (on) Accent else SurfaceAlt)
                                .border(1.dp, if (on) Accent else FieldBorder.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                .tjClickable(label = "掌握度 $i") {
                                    vm.db.setMastery(question.id, i)
                                    q = question.copy(mastery = i)
                                    vm.refresh()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "$i", fontSize = 14.sp,
                                fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                                color = if (on) Color.White else Ink2
                            )
                        }
                    }
                }
            }
            if (question.note.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                LabelValue("笔记", question.note)
            }
            Spacer(Modifier.height(80.dp))
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
                vm.db.deleteQuestion(question.id)
                vm.refresh()
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
    var catId by remember { mutableStateOf(q.categoryId ?: "uncat") }
    AlertDialog(
        onDismissRequest = {},
        title = { Text("编辑题目", fontSize = 16.sp) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(value = stem, onValueChange = { stem = it }, label = { Text("题干", fontSize = 12.sp) }, minLines = 3, textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, lineHeight = 22.sp, color = Ink), colors = tjFieldColors(), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = answer, onValueChange = { answer = it }, label = { Text("答案", fontSize = 12.sp) }, textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = Ink), colors = tjFieldColors(), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = analysis, onValueChange = { analysis = it }, label = { Text("解析", fontSize = 12.sp) }, minLines = 2, textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, lineHeight = 22.sp, color = Ink), colors = tjFieldColors(), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("笔记", fontSize = 12.sp) }, textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = Ink), colors = tjFieldColors(), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = type, onValueChange = { type = it }, label = { Text("题型", fontSize = 12.sp) }, textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = Ink), colors = tjFieldColors(), modifier = Modifier.fillMaxWidth())
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
                vm.db.updateQuestion(
                    id = q.id, stem = stem, answer = answer, analysis = analysis,
                    type = type, tags = q.tags, note = note,
                    errorReasons = q.errorReasons, categoryId = catId
                )
                vm.refresh()
                onDone(
                    q.copy(
                        stem = stem, answer = answer, analysis = analysis, note = note,
                        type = type, categoryId = catId,
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
            Text("${q.categoryName} · ${q.type}", fontSize = 11.5.sp, color = Muted, modifier = Modifier.padding(top = 3.dp))
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
fun ZoomableImage(file: File, onClick: () -> Unit = {}) {
    Box(Modifier.fillMaxWidth().heightIn(max = 440.dp).clip(RoundedCornerShape(14.dp))
        .background(Color.White).border(1.dp, Line, RoundedCornerShape(14.dp)).tjClickable { onClick() }) {
        AsyncImage(model = file, contentDescription = "查看题目原图", contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp, max = 320.dp))
    }
}

@Composable
fun LabelValue(label: String, value: String) {
    Column {
        Text(label, fontSize = 11.sp, letterSpacing = 1.sp, color = Muted)
        Text(value, fontSize = 12.5.sp, lineHeight = 20.sp, color = Ink, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
fun BigButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(52.dp).clip(RoundedCornerShape(14.dp)).background(Accent)
            .bounceClick(label = text) { onClick() }, contentAlignment = Alignment.Center
    ) { Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White) }
}

@Composable
fun BusyOverlay(label: String) {
            Box(Modifier.fillMaxSize().background(Color(0x66000000)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.clip(RoundedCornerShape(14.dp)).background(Color.White).padding(horizontal = 24.dp, vertical = 18.dp),
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
        Column(Modifier.padding(horizontal = Space.page).padding(bottom = 26.dp)) {
            Text("导出 PDF", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text("已选 $count 题 · A4 / 2cm 页边距", fontSize = 12.5.sp, color = Muted, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
            OptRow("版式", if (onePerPage) "一题一页" else "紧凑") { onePerPage = !onePerPage }
            OptRow("答案", if (answer) "包含" else "不包含") { answer = !answer }
            OptRow("解析", if (analysis) "包含" else "不包含") { analysis = !analysis }
            OptRow("知识点标签", if (tags) "包含" else "不包含") { tags = !tags }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier.weight(1f).height(50.dp).clip(RoundedCornerShape(12.dp)).background(SurfaceAlt)
                        .tjClickable { onExport(PdfExporter.Options(answer, analysis, tags, onePerPage), true) },
                    contentAlignment = Alignment.Center
                ) { Text("生成并分享", fontSize = 14.sp, color = Ink2, fontWeight = FontWeight.Medium) }
                Box(
                    Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(11.dp)).background(Accent)
                        .tjClickable { onExport(PdfExporter.Options(answer, analysis, tags, onePerPage), false) },
                    contentAlignment = Alignment.Center
                ) { Text("保存 PDF", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.SemiBold) }
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
} catch (e: Exception) { Color(0xFFE7EAF8) }

fun dateStamp(): String = SimpleDateFormat("yyyyMMdd", Locale.CHINA).format(Date())


@Composable
private fun RecentCard(q: QuestionRow, onClick: () -> Unit) {
    Column(
        Modifier.width(148.dp).shadow(2.dp, RoundedCornerShape(16.dp), ambientColor = Color(0x0F0F1B3D), spotColor = Color(0x0F0F1B3D))
            .clip(RoundedCornerShape(16.dp)).background(CardBg)
            .border(1.dp, Line, RoundedCornerShape(16.dp))
            .bounceClick(label = "打开错题", onClick = onClick)
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
            Text(q.categoryName, fontSize = 11.sp, color = Muted, modifier = Modifier.padding(top = 5.dp), maxLines = 1)
        }
    }
}


@Composable
private fun OverviewCell(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value, fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
            color = if (value == "0") Muted else Ink
        )
        Text(label, fontSize = 11.5.sp, color = Muted, modifier = Modifier.padding(top = 3.dp))
    }
}


/** 粗判 AI 输出是否被截断：未闭合的 $ 或 \begin 环境、以冒号逗号结尾。 */
private fun looksTruncated(text: String): Boolean {
    val t = text.trim()
    if (t.length < 8) return false
    if (t.count { it == '$' } % 2 != 0) return true
    val bs = 0x5C.toChar().toString()
    if (t.contains(bs + "begin{") && !t.contains(bs + "end{")) return true
    if (t.endsWith("：") || t.endsWith(":") || t.endsWith("，") || t.endsWith(",")) return true
    return false
}
