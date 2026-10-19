@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.zhuquan.tijian.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LibraryScreen(vm: AppVm) {
    var pickCategory by remember { mutableStateOf(false) }
    var pendingFile by remember { mutableStateOf<File?>(null) }
    var pendingDoc by remember { mutableStateOf<DocRow?>(null) }
    val scope = rememberCoroutineScope()

    val createPdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri: Uri? ->
        val f = pendingFile
        if (uri != null && f != null) vm.saveTo(uri, f)
        pendingFile = null
    }

    Column(Modifier.fillMaxSize()) {
        TjTopBar(
            title = "知识库",
            subtitle = "${vm.docs.size} 篇 · 从错题里长出来的知识点",
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
                        onPdf = {
                            pendingDoc = doc
                            scope.launch {
                                vm.busy = true; vm.busyLabel = "生成 PDF"
                                val f = vm.buildDocPdf(doc)
                                vm.busy = false; vm.busyLabel = ""
                                pendingFile = f
                                createPdf.launch("${doc.title}_${dateStamp()}.pdf")
                            }
                        })
                    HorizontalDivider(color = Line, thickness = 1.dp)
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
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
}

@Composable
private fun GenerateCard(categoryCount: Int, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(Ink)
            .tjClickable { onClick() }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(Color(0x1FFFFFFF)),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Rounded.AutoAwesome, null, tint = Color(0xFFB9BEFF), modifier = Modifier.size(20.dp)) }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text("生成知识文档", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            Text("从 $categoryCount 个分类里提炼要点与易错点", fontSize = 12.sp, color = OnHeroMuted, modifier = Modifier.padding(top = 3.dp))
        }
        Box(
            Modifier.height(36.dp).clip(RoundedCornerShape(999.dp)).background(Color.White)
                .tjClickable(label = "选择分类") { onClick() }
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center
        ) { Text("选择分类", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = Ink) }
    }
}

@Composable
private fun DocRowItem(doc: DocRow, onOpen: () -> Unit, onPdf: () -> Unit) {
    Row(Modifier.fillMaxWidth().tjClickable { onOpen() }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(SurfaceAlt),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Rounded.Description, null, tint = Ink2, modifier = Modifier.size(19.dp)) }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(doc.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${countPoints(doc.contentMd)} 个知识点 · ${formatDay(doc.createdAt)}", fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 3.dp))
        }
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).tjClickable(label = "导出 PDF") { onPdf() }, contentAlignment = Alignment.Center) {
            androidx.compose.material3.Icon(Icons.Rounded.PictureAsPdf, "导出 PDF", tint = Muted, modifier = Modifier.size(19.dp))
        }
    }
}

@Composable
fun DocScreen(vm: AppVm, route: Route.Doc? = vm.route as? Route.Doc) {
    val id = route?.id ?: return
    var doc by remember { mutableStateOf<DocRow?>(null) }
    var pendingFile by remember { mutableStateOf<File?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(id) { doc = withContext(Dispatchers.IO) { vm.db.doc(id) } }

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
                        vm.busy = true; vm.busyLabel = "排版 PDF"
                        val f = vm.buildDocPdf(d)
                        vm.busy = false; vm.busyLabel = ""
                        pendingFile = f
                        createPdf.launch("${d.title}_${dateStamp()}.pdf")
                    }
                }
            }
        )
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Space.page)) {
            RichText(d.contentMd, fontSize = 14)
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier.weight(1f).height(50.dp).clip(RoundedCornerShape(12.dp)).background(SurfaceAlt)
                        .tjClickable {
                            scope.launch {
                                vm.busy = true; vm.busyLabel = "排版 PDF"
                                val f = vm.buildDocPdf(d)
                                vm.busy = false; vm.busyLabel = ""
                                shareFile(vm.getApplication(), f)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) { Text("生成并分享", fontSize = 14.sp, color = Ink2, fontWeight = FontWeight.Medium) }
                Box(
                    Modifier.weight(1f).height(50.dp).clip(RoundedCornerShape(12.dp)).background(Accent)
                        .tjClickable {
                            scope.launch {
                                vm.busy = true; vm.busyLabel = "排版 PDF"
                                val f = vm.buildDocPdf(d)
                                vm.busy = false; vm.busyLabel = ""
                                pendingFile = f
                                createPdf.launch("${d.title}_${dateStamp()}.pdf")
                            }
                        },
                    contentAlignment = Alignment.Center
                ) { Text("保存 PDF", fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.SemiBold) }
            }
            Spacer(Modifier.height(60.dp))
        }
    }
}

@Composable
fun MarkdownText(md: String) {
    Column {
        md.lineSequence().forEach { raw ->
            val line = raw.trimEnd()
            when {
                line.startsWith("# ") -> Text(line.removePrefix("# "), fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = Ink, modifier = Modifier.padding(top = 6.dp, bottom = 10.dp))
                line.startsWith("## ") -> Text(line.removePrefix("## "), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Ink, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
                line.startsWith("- ") || line.startsWith("* ") -> Row(Modifier.padding(vertical = 2.dp)) {
                    Text("·", fontSize = 12.sp, color = Muted, modifier = Modifier.width(12.dp))
                    Text(line.drop(2), fontSize = 12.5.sp, lineHeight = 20.sp, color = Ink2)
                }
                line.startsWith("**") && line.endsWith("**") -> Text(line.trim('*'), fontSize = 11.sp, letterSpacing = 1.sp, color = Muted, modifier = Modifier.padding(top = 10.dp, bottom = 3.dp))
                line.isBlank() -> Spacer(Modifier.height(6.dp))
                else -> Text(line, fontSize = 12.5.sp, lineHeight = 21.sp, color = Ink2, modifier = Modifier.padding(vertical = 2.dp))
            }
        }
    }
}

fun countPoints(md: String): Int = md.lineSequence().count { it.startsWith("## ") }

/** Multi-select version for knowledge generation; unlike the draft picker it never closes on a row tap. */
@Composable
fun CategoryMultiPicker(
    categories: List<CategoryRow>,
    onDismiss: () -> Unit,
    onConfirm: (List<CategoryRow>) -> Unit,
) {
    val chosen = remember { mutableStateOf<Set<String>>(emptySet()) }
    var query by remember { mutableStateOf("") }
    val filtered = categories.filter { it.fullName().contains(query.trim(), ignoreCase = true) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color.White) {
        Column(Modifier.padding(horizontal = Space.page).padding(bottom = 26.dp)) {
            Text("选择分类", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink)
            Text(
                "可多选 · 已选 ${chosen.value.size} 个",
                fontSize = 11.5.sp, color = Muted, modifier = Modifier.padding(top = 3.dp, bottom = 10.dp)
            )
            OutlinedTextField(
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
            Box(
                Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (chosen.value.isEmpty()) SurfaceAlt else Accent)
                    .tjClickable(enabled = chosen.value.isNotEmpty()) {
                        onConfirm(categories.filter { chosen.value.contains(it.id) })
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (chosen.value.isEmpty()) "选择分类后生成" else "生成知识文档（${chosen.value.size} 个分类）",
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    color = if (chosen.value.isEmpty()) Muted else Color.White
                )
            }
        }
    }
}

fun formatDay(ms: Long): String = SimpleDateFormat("MM-dd", Locale.CHINA).format(Date(ms))
