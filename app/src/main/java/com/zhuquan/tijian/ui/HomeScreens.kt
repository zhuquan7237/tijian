@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.zhuquan.tijian.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.Add
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.zhuquan.tijian.data.CategoryRow
import com.zhuquan.tijian.data.QuestionRow
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
    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val results by produceState<List<QuestionRow>?>(null, query) {
        value = if (query.isBlank()) null else withContext(Dispatchers.IO) { vm.db.questions(query = query) }
    }

    Column(Modifier.fillMaxSize()) {
        TjTopBar(
            title = "错题本",
            subtitle = "${vm.stats.total} 道 · ${vm.categories.count { it.count > 0 }} 个分类 · ${vm.stats.due} 道待复习",
            actions = {
                IconCircle(Icons.Rounded.Search, "搜索") { searchOpen = !searchOpen }
            }
        )

        if (searchOpen) {
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                placeholder = { Text("搜索题干、知识点", fontSize = 12.sp, color = Muted) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp)
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
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 96.dp)
        ) {
            ReviewHero(vm.stats.due, vm.stats.total, vm.stats.mastered) { vm.startReview() }

            SectionLabel("分类", Modifier.padding(horizontal = 18.dp)) {
                Text("${vm.categories.count { it.count > 0 }} 个", fontSize = 11.sp, color = Muted)
            }

            if (vm.categories.all { it.count == 0 } && vm.stats.total == 0) {
                EmptyState(
                    "还没有错题",
                    "拍一张试卷，AI 会自动切题、分类",
                ) {
                    BigButton("识别导入") { onImport() }
                }
            } else {
                val list = vm.categories.filter { it.id != "uncat" || it.count > 0 }
                list.chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        pair.forEach { cat ->
                            CategoryCard(cat, Modifier.weight(1f)) { vm.go(Route.Category(cat.id)) }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(10.dp))
                }
                if (list.isEmpty()) {
                    EmptyState("还没有分类", "导入错题后会自动生成")
                }
            }
        }
    }
}

@Composable
private fun ReviewHero(due: Int, total: Int, mastered: Int, onClick: () -> Unit) {
    Box(
        Modifier.padding(horizontal = 18.dp).fillMaxWidth()
            .clip(RoundedCornerShape(18.dp)).background(Ink)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text("今日复习", fontSize = 10.sp, letterSpacing = 1.2.sp, color = Color(0x8CFFFFFF))
                    Text(
                        if (due > 0) "到期的错题在等你" else "今天没有到期任务",
                        fontSize = 12.sp, color = Color(0x9EFFFFFF), modifier = Modifier.padding(top = 8.dp)
                    )
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("$due", fontSize = 38.sp, fontWeight = FontWeight.Light, color = Color.White)
                    Text("题", fontSize = 12.sp, color = Color(0x80FFFFFF), modifier = Modifier.padding(bottom = 6.dp, start = 4.dp))
                }
            }
            Spacer(Modifier.height(14.dp))
            Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF2C2C36))) {
                Box(
                    Modifier.fillMaxWidth(if (total == 0) 0f else (1f - due.toFloat() / total).coerceIn(0f, 1f))
                        .height(3.dp).background(Color(0xFF8E9BFF))
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("共 $total 道 · 已掌握 $mastered", fontSize = 11.sp, color = Color(0x99FFFFFF))
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.clip(RoundedCornerShape(999.dp)).background(Color.White)
                        .clickableNoRipple(onClick).padding(horizontal = 14.dp, vertical = 7.dp)
                ) { Text(if (due > 0) "开始复习" else "随便看看", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = Ink) }
            }
        }
    }
}


@Composable
private fun CategoryCard(cat: CategoryRow, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val glyph = GLYPHS[(cat.name.hashCode() and Int.MAX_VALUE) % GLYPHS.size]
    val base = parseColor(cat.color)
    Column(
        modifier.clip(RoundedCornerShape(15.dp)).background(CardBg).border(1.dp, Line, RoundedCornerShape(15.dp))
            .clickableNoRipple(onClick)
    ) {
        Box(
            Modifier.fillMaxWidth().height(58.dp)
                .background(Brush.linearGradient(listOf(base, base.copy(alpha = 0.45f))))
        ) {
            Text(glyph, fontSize = 36.sp, color = Color(0x33141418), modifier = Modifier.align(Alignment.CenterStart).padding(start = 14.dp))
            Text("${cat.count}", fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = Color(0x73141418),
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 11.dp, bottom = 8.dp))
            if (cat.status == "pending") {
                Box(
                    Modifier.align(Alignment.TopStart).padding(start = 9.dp, top = 8.dp)
                        .clip(RoundedCornerShape(4.dp)).background(AccentSoft)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) { Text("AI 新建", fontSize = 9.sp, color = Accent, fontWeight = FontWeight.SemiBold) }
            }
        }
        Column(Modifier.padding(start = 11.dp, end = 11.dp, top = 8.dp, bottom = 10.dp)) {
            Text(cat.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("已掌握 ${cat.mastered} / ${cat.count}", fontSize = 10.5.sp, color = Muted, modifier = Modifier.padding(top = 2.dp))
            Box(Modifier.fillMaxWidth().padding(top = 6.dp).height(2.dp).clip(RoundedCornerShape(1.dp)).background(Color(0xFFEEEEE9))) {
                Box(
                    Modifier.fillMaxWidth(if (cat.count == 0) 0f else cat.mastered.toFloat() / cat.count)
                        .height(2.dp).background(Green.copy(alpha = 0.75f))
                )
            }
        }
    }
}

@Composable
fun CategoryScreen(vm: AppVm) {
    val id = (vm.route as? Route.Category)?.id ?: return
    val cat = vm.categories.firstOrNull { it.id == id }
    var filter by remember { mutableStateOf("all") }
    var selecting by remember { mutableStateOf(false) }
    val selected = remember { mutableStateOf<Set<String>>(emptySet()) }
    var showExport by remember { mutableStateOf(false) }
    var pendingFile by remember { mutableStateOf<File?>(null) }
    val scope = rememberCoroutineScope()

    val rows by produceState<List<QuestionRow>>(emptyList(), id, filter) {
        value = withContext(Dispatchers.IO) { vm.db.questions(id, filter) }
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
                Text(
                    if (selecting) "取消" else "选择",
                    fontSize = 12.5.sp, color = Accent,
                    modifier = Modifier.clickableNoRipple {
                        selecting = !selecting
                        if (!selecting) selected.value = emptySet()
                    }.padding(8.dp)
                )
            }
        )

        if (cat?.status == "pending") {
            Row(
                Modifier.padding(horizontal = 18.dp).fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp)).background(AccentSoft)
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("AI 新建的分类", fontSize = 10.5.sp, color = Accent, modifier = Modifier.weight(1f))
                Text("确认为正式分类", fontSize = 10.5.sp, color = Accent, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickableNoRipple {
                        vm.db.confirmCategory(id); vm.refresh()
                    }.padding(4.dp))
            }
            Spacer(Modifier.height(8.dp))
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TjChip("全部 ${cat?.count ?: rows.size}", filter == "all") { filter = "all" }
            TjChip("未掌握", filter == "weak") { filter = "weak" }
            TjChip("本周", filter == "week") { filter = "week" }
            TjChip("有错因", filter == "errors") { filter = "errors" }
        }

        Spacer(Modifier.height(12.dp))

        CardBox(Modifier.padding(horizontal = 18.dp).fillMaxWidth()) {
            Column {
                Row(Modifier.fillMaxWidth().background(Color(0xFFFBFBF8)).padding(horizontal = 11.dp, vertical = 8.dp)) {
                    Text(if (selecting) "" else "题目", fontSize = 9.5.sp, letterSpacing = 1.sp, color = Muted, modifier = Modifier.width(44.dp))
                    Text("题型", fontSize = 9.5.sp, letterSpacing = 1.sp, color = Muted, modifier = Modifier.width(70.dp))
                    Text("知识点", fontSize = 9.5.sp, letterSpacing = 1.sp, color = Muted, modifier = Modifier.weight(1f))
                    Text("掌握", fontSize = 9.5.sp, letterSpacing = 1.sp, color = Muted, modifier = Modifier.width(44.dp))
                }
                LazyColumn(Modifier.heightIn(max = 460.dp)) {
                    items(rows, key = { it.id }) { q ->
                        val isSel = selected.value.contains(q.id)
                        Row(
                            Modifier.fillMaxWidth().clickableNoRipple {
                                if (selecting) {
                                    selected.value = if (isSel) selected.value - q.id else selected.value + q.id
                                } else vm.go(Route.Detail(q.id))
                            }.padding(horizontal = 11.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.width(44.dp)) {
                                if (selecting) {
                                    Checkbox(checked = isSel, onCheckedChange = null, modifier = Modifier.size(20.dp))
                                } else {
                                    Thumb(q.imagePath, Modifier.width(38.dp).height(46.dp))
                                }
                            }
                            Text(q.type.ifBlank { "—" }, fontSize = 11.5.sp, color = Ink2, modifier = Modifier.width(70.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                knowledgeText(q).ifBlank { "—" }, fontSize = 11.5.sp, color = Ink,
                                modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            Box(Modifier.width(44.dp), contentAlignment = Alignment.CenterEnd) { MasteryDots(q.mastery) }
                        }
                        HorizontalDivider(color = Color(0xFFF0F0EB), thickness = 1.dp)
                    }
                    if (rows.isEmpty()) {
                        item { EmptyState("这里是空的", "从下方导入错题") }
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))

        if (selecting && selected.value.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().background(Ink).padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("已选 ${selected.value.size} 题", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.clip(RoundedCornerShape(9.dp)).background(Color.White)
                        .clickableNoRipple { showExport = true }.padding(horizontal = 14.dp, vertical = 8.dp)
                ) { Text("导出 PDF", fontSize = 11.5.sp, color = Ink, fontWeight = FontWeight.SemiBold) }
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 22.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BigButton("导入错题", Modifier.weight(1f)) { vm.go(Route.Import) }
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

    if (vm.busy) BusyOverlay(vm.busyLabel)
}

@Composable
fun QuestionDetailScreen(vm: AppVm) {
    val id = (vm.route as? Route.Detail)?.id ?: return
    var q by remember { mutableStateOf<QuestionRow?>(null) }
    var editing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(id) { q = withContext(Dispatchers.IO) { vm.db.question(id) } }

    val question = q ?: return
    Column(Modifier.fillMaxSize()) {
        TjTopBar(
            title = question.type.ifBlank { "题目" },
            subtitle = question.categoryName,
            onBack = { vm.back() },
            actions = {
                IconCircle(Icons.Rounded.Edit, "编辑") { editing = true }
                IconCircle(Icons.Rounded.DeleteOutline, "删除") { confirmDelete = true }
            }
        )
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
            if (question.imagePath != null) {
                ZoomableImage(File(question.imagePath))
                Spacer(Modifier.height(14.dp))
            }
            if (question.stem.isNotBlank()) {
                Text(cleanMath(question.stem), fontSize = 14.sp, lineHeight = 23.sp, color = Ink)
            } else {
                Text("（未识别题干，点右上角编辑补录）", fontSize = 12.sp, color = Muted)
            }
            val opts = parseOptions(question.options)
            if (opts.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                opts.forEach { o ->
                    Text(cleanMath(o), fontSize = 13.sp, lineHeight = 22.sp, color = Ink2,
                        modifier = Modifier.padding(vertical = 2.dp))
                }
            }
            if (question.answer.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                LabelValue("答案", cleanMath(question.answer))
            }
            if (question.analysis.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                LabelValue("解析", cleanMath(question.analysis))
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("掌握度", fontSize = 10.sp, letterSpacing = 1.sp, color = Muted)
                Spacer(Modifier.width(10.dp))
                MasteryDots(question.mastery, 7)
                Spacer(Modifier.weight(1f))
                Text("复习 ${question.reviewCount} 次", fontSize = 10.5.sp, color = Muted)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(6) { i ->
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                            .background(if (i == question.mastery) Ink else Color.White)
                            .border(1.dp, Line, RoundedCornerShape(8.dp))
                            .clickableNoRipple {
                                vm.db.setMastery(question.id, i)
                                q = question.copy(mastery = i)
                                vm.refresh()
                            }
                            .padding(vertical = 8.dp), contentAlignment = Alignment.Center
                    ) {
                        Text("$i", fontSize = 11.sp, color = if (i == question.mastery) Color.White else Ink2)
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
                OutlinedTextField(value = stem, onValueChange = { stem = it }, label = { Text("题干", fontSize = 11.sp) }, minLines = 3, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = answer, onValueChange = { answer = it }, label = { Text("答案", fontSize = 11.sp) }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = analysis, onValueChange = { analysis = it }, label = { Text("解析", fontSize = 11.sp) }, minLines = 2, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("笔记", fontSize = 11.sp) }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = type, onValueChange = { type = it }, label = { Text("题型", fontSize = 11.sp) }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                Text("分类", fontSize = 10.sp, color = Muted)
                Column {
                    cats.forEach { c ->
                        Row(Modifier.fillMaxWidth().clickableNoRipple { catId = c.id }.padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioDot(catId == c.id)
                            Text(c.name, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp))
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
        Modifier.size(16.dp).clip(CircleShape).border(1.4.dp, if (selected) Accent else Color(0xFFC9C9C2), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (selected) Box(Modifier.size(8.dp).clip(CircleShape).background(Accent))
    }
}

@Composable
fun SearchRow(q: QuestionRow, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickableNoRipple(onClick).padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Thumb(q.imagePath, Modifier.width(38.dp).height(46.dp))
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(q.stem.ifBlank { "（无题干）" }, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, color = Ink)
            Text("${q.categoryName} · ${q.type}", fontSize = 10.sp, color = Muted, modifier = Modifier.padding(top = 3.dp))
        }
    }
    HorizontalDivider(color = Color(0xFFF1F1EB), thickness = 1.dp)
}

@Composable
fun Thumb(path: String?, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(4.dp)).background(Color(0xFFF4F4EF)).border(1.dp, Color(0xFFE2E2DA), RoundedCornerShape(4.dp))) {
        if (path != null) {
            AsyncImage(model = File(path), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text("题", fontSize = 12.sp, color = Color(0xFFB6B6AE), modifier = Modifier.align(Alignment.Center))
        }
    }
}

@Composable
fun ZoomableImage(file: File) {
    var scale by remember { mutableStateOf(1f) }
    var dx by remember { mutableStateOf(0f) }
    var dy by remember { mutableStateOf(0f) }
    Box(
        Modifier.fillMaxWidth().height(320.dp).clip(RoundedCornerShape(14.dp)).background(Color.White)
            .border(1.dp, Line, RoundedCornerShape(14.dp))
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    if (scale > 1f) { dx += pan.x; dy += pan.y }
                    else { dx = 0f; dy = 0f }
                }
            }
    ) {
        AsyncImage(
            model = file, contentDescription = "题目原图", contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().graphicsLayer(scaleX = scale, scaleY = scale, translationX = dx, translationY = dy)
        )
        if (scale > 1.01f) {
            Text("双指缩放 · 拖动查看", fontSize = 9.sp, color = Muted, modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
                .background(Color(0xCCFFFFFF), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 3.dp))
        }
    }
}

@Composable
fun LabelValue(label: String, value: String) {
    Column {
        Text(label, fontSize = 10.sp, letterSpacing = 1.sp, color = Muted)
        Text(value, fontSize = 12.5.sp, lineHeight = 20.sp, color = Ink, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
fun BigButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(46.dp).clip(RoundedCornerShape(12.dp)).background(Accent)
            .clickableNoRipple(onClick), contentAlignment = Alignment.Center
    ) { Text(text, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = Color.White) }
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
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color.White) {
        Column(Modifier.padding(horizontal = 18.dp).padding(bottom = 24.dp)) {
            Text("导出 PDF", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text("已选 $count 题 · A4 / 2cm 页边距", fontSize = 10.5.sp, color = Muted, modifier = Modifier.padding(top = 3.dp, bottom = 12.dp))
            OptRow("版式", if (onePerPage) "一题一页" else "紧凑") { onePerPage = !onePerPage }
            OptRow("答案", if (answer) "包含" else "不包含") { answer = !answer }
            OptRow("解析", if (analysis) "包含" else "不包含") { analysis = !analysis }
            OptRow("知识点标签", if (tags) "包含" else "不包含") { tags = !tags }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(11.dp)).background(Color(0xFFF4F4EF))
                        .clickableNoRipple { onExport(PdfExporter.Options(answer, analysis, tags, onePerPage), true) },
                    contentAlignment = Alignment.Center
                ) { Text("生成并分享", fontSize = 12.sp, color = Ink2) }
                Box(
                    Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(11.dp)).background(Accent)
                        .clickableNoRipple { onExport(PdfExporter.Options(answer, analysis, tags, onePerPage), false) },
                    contentAlignment = Alignment.Center
                ) { Text("保存 PDF", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

@Composable
private fun OptRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickableNoRipple(onClick).padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 12.sp, color = Ink)
        Spacer(Modifier.weight(1f))
        Text(value, fontSize = 11.sp, color = Muted)
    }
    HorizontalDivider(color = Color(0xFFF2F2EC), thickness = 1.dp)
}

fun knowledgeText(q: QuestionRow): String = try {
    val arr = org.json.JSONArray(q.tags)
    (0 until arr.length()).joinToString(" / ") { arr.optString(it) }
} catch (e: Exception) { "" }

fun parseColor(hex: String): Color = try {
    Color(android.graphics.Color.parseColor(hex))
} catch (e: Exception) { Color(0xFFE7EAF8) }

fun dateStamp(): String = SimpleDateFormat("yyyyMMdd", Locale.CHINA).format(Date())
