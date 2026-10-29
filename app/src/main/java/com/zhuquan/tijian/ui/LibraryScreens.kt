@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.zhuquan.tijian.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhuquan.tijian.data.CategoryRow
import com.zhuquan.tijian.data.DocRow
import com.zhuquan.tijian.data.fullName
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LibraryScreen(vm: AppVm) {
    var pickCategory by remember { mutableStateOf(false) }
    var editingDoc by remember { mutableStateOf<DocRow?>(null) }
    var deletingDoc by remember { mutableStateOf<DocRow?>(null) }
    var regeneratingDoc by remember { mutableStateOf<DocRow?>(null) }
    var pendingFile by remember { mutableStateOf<File?>(null) }
    val scope = rememberCoroutineScope()

    val createPdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri: Uri? ->
        val f = pendingFile
        if (uri != null && f != null) vm.saveTo(uri, f)
        pendingFile = null
    }

    Column(Modifier.fillMaxSize()) {
        TjTopBar(
            title = "知识库",
            subtitle = if (vm.docs.isEmpty()) "从错题里整理出能复习的知识点" else "${vm.docs.size} 篇文档",
            actions = {
                IconCircle(Icons.Rounded.AutoAwesome, "生成") { pickCategory = true }
            }
        )

        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Space.page, vertical = 6.dp)) {
            item {
                GenerateCard(vm.categories.filter { it.count > 0 }.size) { pickCategory = true }
                SectionLabel("最近生成") {
                    Text("${vm.docs.size} 篇", fontSize = 11.sp, color = Muted)
                }
            }
            if (vm.docs.isEmpty()) {
                item { EmptyState("还没有知识文档", "选一个错题分类，AI 会提炼知识点并整理成文") }
            } else {
                items(vm.docs, key = { it.id }) { doc ->
                    DocRowItem(doc,
                        onOpen = { vm.go(Route.Doc(doc.id)) },
                        onEdit = { editingDoc = doc },
                        onRegenerate = { regeneratingDoc = doc },
                        onDelete = { deletingDoc = doc },
                        onPdf = {
                            scope.launch {
                                vm.withBusy("生成 PDF") {
                                    pendingFile = vm.buildDocPdf(doc)
                                    createPdf.launch("${doc.title}_${dateStamp()}.pdf")
                                }
                            }
                        })
                    HorizontalDivider(color = Line, thickness = 1.dp)
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (vm.busy) BusyOverlay(vm.busyLabel)

    editingDoc?.let { doc ->
        DocEditDialog(
            doc = doc,
            onDismiss = { editingDoc = null },
            onSave = { title, content ->
                vm.updateDoc(doc.id, title, content)
                editingDoc = null
            }
        )
    }

    deletingDoc?.let { doc ->
        AlertDialog(
            onDismissRequest = { deletingDoc = null },
            title = { Text("删除这篇文档？", fontSize = 17.sp) },
            text = { Text("「${doc.title}」会从知识库移除，原始错题不受影响。", fontSize = 13.sp, lineHeight = 20.sp, color = Ink2) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    vm.deleteDoc(doc.id)
                    deletingDoc = null
                }) { Text("删除", color = Red, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { deletingDoc = null }) { Text("取消", color = Ink2) }
            }
        )
    }

    if (pickCategory) {
        CategoryMultiPicker(
            categories = vm.categories.filter { it.count > 0 },
            onDismiss = { pickCategory = false },
            onConfirm = { selected ->
                pickCategory = false
                if (selected.isNotEmpty()) vm.generateKnowledge(selected)
            }
        )
    }

    regeneratingDoc?.let { doc ->
        CategoryMultiPicker(
            categories = vm.categories.filter { it.count > 0 },
            preselected = setOfNotNull(doc.categoryId),
            confirmText = "按这些分类重新生成",
            onDismiss = { regeneratingDoc = null },
            onConfirm = { selected ->
                regeneratingDoc = null
                if (selected.isNotEmpty()) vm.regenerateKnowledge(doc.id, selected)
            }
        )
    }
}

@Composable
private fun GenerateCard(categoryCount: Int, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(CardShape)
            .background(HeroBrush)
            .border(1.dp, HeroLine, CardShape)
            .tjClickable { onClick() }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(AccentSoft),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Rounded.AutoAwesome, null, tint = Accent, modifier = Modifier.size(20.dp)) }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text("生成知识文档", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink)
            Text("从 $categoryCount 个分类里提炼要点与易错点", fontSize = 12.sp, color = OnHeroMuted, modifier = Modifier.padding(top = 3.dp))
        }
        Box(
            Modifier.heightIn(min = 48.dp).clip(CircleShape).background(Accent)
                .tjClickable(label = "选择分类") { onClick() }
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center
        ) { Text("选择分类", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = OnAccent) }
    }
}

@Composable
private fun DocRowItem(
    doc: DocRow,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onRegenerate: () -> Unit,
    onDelete: () -> Unit,
    onPdf: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val rowSrc = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Row(
        Modifier.pressScale(rowSrc).fillMaxWidth()
            .tjClickable(interactionSource = rowSrc) { onOpen() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(SurfaceAlt),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Rounded.Description, null, tint = Ink2, modifier = Modifier.size(19.dp)) }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(doc.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${countPoints(doc.contentMd)} 个知识点 · ${formatDay(doc.createdAt)}", fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 3.dp))
        }
        Box {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(10.dp))
                    .tjClickable(label = "更多操作") { menu = true },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Rounded.MoreVert, null, tint = Muted, modifier = Modifier.size(20.dp)) }
            androidx.compose.material3.DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text("重新选择分类生成", fontSize = 14.sp) },
                    onClick = { menu = false; onRegenerate() }
                )
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text("编辑文字内容", fontSize = 14.sp) },
                    onClick = { menu = false; onEdit() }
                )
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text("导出 PDF", fontSize = 14.sp) },
                    onClick = { menu = false; onPdf() }
                )
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text("删除", fontSize = 14.sp, color = Red) },
                    onClick = { menu = false; onDelete() }
                )
            }
        }
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).tjClickable(label = "导出 PDF") { onPdf() }, contentAlignment = Alignment.Center) {
            androidx.compose.material3.Icon(Icons.Rounded.PictureAsPdf, "导出 PDF", tint = Muted, modifier = Modifier.size(19.dp))
        }
    }
}

@Composable
private fun DocEditDialog(
    doc: DocRow,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var title by remember { mutableStateOf(doc.title) }
    var content by remember { mutableStateOf(doc.contentMd) }
    FullScreenDialog(onDismissRequest = onDismiss) {
            Row(
                Modifier.fillMaxWidth().padding(start = Space.page, end = 10.dp, top = 12.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("编辑知识文档", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink)
                    Text("支持 Markdown 与 LaTeX（$...$）", fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 2.dp))
                }
                Box(
                    Modifier.size(48.dp).clip(CircleShape).tjClickable(label = "取消") { onDismiss() },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Rounded.Close, null, tint = Ink2, modifier = Modifier.size(22.dp)) }
            }
            Column(Modifier.weight(1f).padding(horizontal = Space.page)) {
                TjTextField(
                    value = title, onValueChange = { title = it },
                    label = { Text("标题", fontSize = 12.sp) },
                    singleLine = true, colors = tjFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                TjTextField(
                    value = content, onValueChange = { content = it },
                    label = { Text("正文", fontSize = 12.sp) },
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, lineHeight = 22.sp, color = Ink),
                    colors = tjFieldColors(),
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "提示：二级标题以 ## 开头；公式用 $...$ 包裹；表格用 GFM 语法。",
                    fontSize = 11.5.sp, lineHeight = 18.sp, color = Muted
                )
            }
            Row(
                Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(horizontal = Space.page, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TjButton("取消", Modifier.weight(1f).fillMaxHeight(), style = ActionStyle.Secondary) { onDismiss() }
                BigButton("保存", Modifier.weight(1f).fillMaxHeight(), enabled = title.isNotBlank()) {
                    if (title.isNotBlank()) onSave(title.trim(), content)
                }
            }
    }
}

@Composable
fun DocScreen(vm: AppVm, route: Route.Doc? = vm.route as? Route.Doc) {
    val id = route?.id ?: return
    var doc by remember { mutableStateOf<DocRow?>(null) }
    var pendingFile by remember { mutableStateOf<File?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(id, vm.dataRevision) { doc = withContext(Dispatchers.IO) { vm.db.doc(id) } }

    val createPdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri: Uri? ->
        val f = pendingFile
        if (uri != null && f != null) vm.saveTo(uri, f)
        pendingFile = null
    }

    val d = doc ?: return
    Column(Modifier.fillMaxSize()) {
        TjTopBar(
            title = d.title,
            subtitle = "${countPoints(d.contentMd)} 个知识点",
            onBack = { vm.back() },
            actions = {
                IconCircle(Icons.Rounded.PictureAsPdf, "导出") {
                    scope.launch {
                        vm.withBusy("排版 PDF") {
                            pendingFile = vm.buildDocPdf(d)
                            createPdf.launch("${d.title}_${dateStamp()}.pdf")
                        }
                    }
                }
            }
        )
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Space.page)) {
            RichText(d.contentMd, fontSize = (14 * vm.readingScaleValue).roundToInt())
            Spacer(Modifier.height(18.dp))
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TjButton("生成并分享", Modifier.weight(1f).fillMaxHeight(), style = ActionStyle.Secondary, enabled = !vm.busy) {
                    scope.launch { vm.withBusy("排版 PDF") { shareFile(vm.getApplication(), vm.buildDocPdf(d)) } }
                }
                TjButton("保存 PDF", Modifier.weight(1f).fillMaxHeight(), enabled = !vm.busy) {
                    scope.launch { vm.withBusy("排版 PDF") {
                        pendingFile = vm.buildDocPdf(d)
                        createPdf.launch("${d.title}_${dateStamp()}.pdf")
                    } }
                }
            }
            Spacer(Modifier.height(60.dp))
        }
    }
    if (vm.busy) BusyOverlay(vm.busyLabel)
}


fun countPoints(md: String): Int = md.lineSequence().count { it.startsWith("## ") }

/** Multi-select version for knowledge generation; unlike the draft picker it never closes on a row tap. */
@Composable
fun CategoryMultiPicker(
    categories: List<CategoryRow>,
    preselected: Set<String> = emptySet(),
    confirmText: String = "生成知识文档",
    onDismiss: () -> Unit,
    onConfirm: (List<CategoryRow>) -> Unit,
) {
    val chosen = remember { mutableStateOf(preselected) }
    var query by remember { mutableStateOf("") }
    val filtered = categories.filter { it.fullName().contains(query.trim(), ignoreCase = true) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = CardBg) {
        Column(Modifier.blockSheetDrag().padding(horizontal = Space.page).padding(bottom = 26.dp)) {
            Text("选择分类", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink)
            Text(
                "可多选 · 已选 ${chosen.value.size} 个",
                fontSize = 11.5.sp, color = Muted, modifier = Modifier.padding(top = 3.dp, bottom = 10.dp)
            )
            TjTextField(
                value = query, onValueChange = { query = it },
                placeholder = { Text("搜索分类", fontSize = 12.sp, color = Muted) },
                singleLine = true, colors = tjFieldColors(), modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                filtered.forEach { cat ->
                    val checked = chosen.value.contains(cat.id)
                    Row(
                        Modifier.fillMaxWidth().tjClickable {
                            chosen.value = if (checked) chosen.value - cat.id else chosen.value + cat.id
                        }.padding(vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.size(22.dp))
                        Column(Modifier.weight(1f).padding(start = 9.dp)) {
                            Text(cat.fullName(), fontSize = 12.5.sp, color = Ink)
                            Text("${cat.count} 题", fontSize = 11.5.sp, color = Muted, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                    HorizontalDivider(color = Line, thickness = 1.dp)
                }
                if (filtered.isEmpty()) Text("没有匹配的分类", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 16.dp))
            }
            Spacer(Modifier.height(12.dp))
            TjButton(
                if (chosen.value.isEmpty()) "选择分类后生成" else "$confirmText（${chosen.value.size} 个分类）",
                Modifier.fillMaxWidth(), enabled = chosen.value.isNotEmpty(),
            ) { onConfirm(categories.filter { it.id in chosen.value }) }
        }
    }
}

fun formatDay(ms: Long): String = SimpleDateFormat("MM-dd", Locale.CHINA).format(Date(ms))
