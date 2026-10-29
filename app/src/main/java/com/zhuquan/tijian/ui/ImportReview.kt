@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.zhuquan.tijian.ui

import android.net.Uri
import com.zhuquan.tijian.data.fullName

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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.zhuquan.tijian.data.DraftQuestion
import com.zhuquan.tijian.data.parseKnowledgeTags
import java.io.File
import kotlin.math.roundToInt

@Composable
fun ImportScreen(vm: AppVm) {
    var editing by remember { mutableStateOf<DraftQuestion?>(null) }
    var previewing by remember { mutableStateOf<DraftQuestion?>(null) }
    var pickerFor by remember { mutableStateOf<DraftQuestion?>(null) }
    var attachFor by remember { mutableStateOf<String?>(null) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val pickAttach = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val id = attachFor
        attachFor = null
        if (uri != null && id != null) vm.attachDraftImage(id, uri)
    }

    Column(Modifier.fillMaxSize()) {
        TjTopBar(
            title = if (vm.importing) "识别中" else "识别校对",
            subtitle = if (vm.importing) vm.importLabel else "${vm.pending.size} 题待确认",
            onBack = { vm.home() },
            actions = {
                if (!vm.importing && vm.pending.isNotEmpty()) {
                    Text(
                        "丢弃本批", fontSize = 12.5.sp, color = Muted,
                        modifier = Modifier.tjClickable(label = "丢弃本批待确认题目") { confirmDiscard = true }.padding(8.dp)
                    )
                    Text("全部确认", fontSize = 12.5.sp, color = Accent, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.tjClickable(enabled = !vm.busy) { vm.confirmPending() }.padding(8.dp))
                }
            }
        )

        if (vm.importing) {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    progress = { vm.importProgress }, modifier = Modifier.size(54.dp),
                    color = Accent, strokeWidth = 3.dp, trackColor = SurfaceAlt
                )
                Spacer(Modifier.height(18.dp))
                Text(vm.importLabel, fontSize = 13.sp, color = Ink)
                Text("图片只在你配置的模型服务里处理", fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 6.dp))
                Spacer(Modifier.height(22.dp))
                TjButton("停止识别（已完成部分会保留）", style = ActionStyle.Secondary) { vm.cancelImport() }
            }
            return@Column
        }

        if (vm.pending.isEmpty()) {
            EmptyState("没有待确认的题目", if (vm.captureUris.isNotEmpty()) "待导入图片已保留，可返回工作台重试" else "回到错题本重新导入") {
                TjButton(if (vm.captureUris.isNotEmpty()) "返回工作台" else "返回错题本") {
                    if (vm.captureUris.isNotEmpty()) vm.go(Route.Capture) else vm.home()
                }
            }
            if (vm.importErrors.isNotEmpty()) {
                Column(Modifier.padding(horizontal = Space.page)) {
                    vm.importErrors.take(4).forEach {
                        Text("· $it", fontSize = 12.sp, lineHeight = 18.sp, color = Amber, modifier = Modifier.padding(vertical = 2.dp))
                    }
                }
            }
            if (vm.lastFailedUris.isNotEmpty()) {
                TjButton(
                    "重试失败的 ${vm.lastFailedUris.size} 张图片",
                    Modifier.fillMaxWidth().padding(horizontal = Space.page, vertical = 10.dp),
                    style = ActionStyle.Secondary
                ) { vm.retryFailedImports() }
            }
            return@Column
        }

        if (vm.importErrors.isNotEmpty()) {
            Column(
                Modifier.padding(horizontal = Space.page).fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp)).background(AmberSoft)
                    .padding(10.dp)
            ) {
                Text(vm.importErrors.first(), fontSize = 12.sp, lineHeight = 18.sp, color = Amber, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (vm.lastFailedUris.isNotEmpty()) {
                    Text(
                        "重试失败的 ${vm.lastFailedUris.size} 张图片（已完成的不会重跑）",
                        fontSize = 12.sp, color = Accent, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.tjClickable(label = "重试失败图片") { vm.retryFailedImports() }.padding(top = 6.dp)
                    )
                }
            }
        }

        LazyColumn(Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Space.page, vertical = 10.dp)) {
            items(vm.pending, key = { it.id }) { d ->
                DraftCard(
                    draft = d,
                    onTagChange = { tag -> vm.updateDraft(d.copy(customTag = tag)) },
                    onPreview = { previewing = d },
                    onEdit = { editing = d },
                    onPickCategory = { pickerFor = d },
                    onAttachImage = { attachFor = d.id; pickAttach.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    onRemove = { vm.removeDraft(d.id) }
                )
                Spacer(Modifier.height(9.dp))
            }
        }

        Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 22.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TjButton("AI 重新分类", Modifier.weight(1f), style = ActionStyle.Secondary, enabled = !vm.busy) {
                    vm.autoClassify()
                }
                TjButton(if (vm.busy) vm.busyLabel else "确认入库 ${vm.pending.size} 题", Modifier.weight(1f), loading = vm.busy) { vm.confirmPending() }
            }
            Text(
                "草稿已自动保存，中途退出也不会丢",
                fontSize = 11.5.sp, color = Muted,
                modifier = Modifier.padding(top = 8.dp).align(Alignment.CenterHorizontally)
            )
        }
    }

    previewing?.let { d ->
        DraftPreviewDialog(
            draft = d,
            onDismiss = { previewing = null },
            onEdit = { previewing = null; editing = d }
        )
    }

    editing?.let { d ->
        EditDraftScreen(
            draft = d,
            onDraftChange = { vm.updateDraft(it) },
            onAttachImage = { uri -> vm.attachDraftImage(d.id, uri) },
            onDetachImage = { vm.detachDraftImage(d.id) },
            onClose = { editing = null }
        )
    }

    pickerFor?.let { d ->
        CategoryPicker(
            current = d.categoryName,
            categories = vm.categories.map { it.fullName() },
            onDismiss = { pickerFor = null },
            onPick = { name ->
                vm.updateDraft(d.copy(categoryName = name))
                pickerFor = null
            }
        )
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("丢弃本批 ${vm.pending.size} 道待确认题目？", fontSize = 16.sp) },
            text = { Text("已识别的文字内容会一并清空，原图片文件仍保留在本机。", fontSize = 12.5.sp, lineHeight = 19.sp, color = Ink2) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    vm.discardPending()
                    vm.home()
                }) { Text("丢弃", color = Red, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("先留着", color = Ink2) } }
        )
    }

    if (vm.busy) BusyOverlay(vm.busyLabel)
}

@Composable
private fun DraftCard(
    draft: DraftQuestion,
    onTagChange: (String) -> Unit,
    onPreview: () -> Unit,
    onEdit: () -> Unit,
    onPickCategory: () -> Unit,
    onAttachImage: () -> Unit,
    onRemove: () -> Unit,
) {
    // 题号本地态：直接在识别校对卡上填，随输入即时回写草稿
    var tagText by remember(draft.id) { mutableStateOf(draft.customTag) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(CardBg)
            .border(1.dp, Line, RoundedCornerShape(13.dp)).padding(11.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (draft.imagePath != null) {
                Thumb(draft.imagePath, Modifier.width(40.dp).height(48.dp))
            } else {
                Box(
                    Modifier.width(40.dp).height(48.dp).clip(RoundedCornerShape(6.dp))
                        .background(AccentSoft).border(1.dp, Accent.copy(alpha = 0.25f), RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.EditNote, null, tint = Accent, modifier = Modifier.size(20.dp))
                }
            }
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (draft.type.isNotBlank()) {
                        Tag(draft.type)
                        Spacer(Modifier.width(5.dp))
                    }
                    if (draft.confidence > 0) {
                        val color = when {
                            draft.confidence >= 0.75 -> Green
                            draft.confidence >= 0.5 -> Amber
                            else -> Red
                        }
                        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
                        Text(
                            "${(draft.confidence * 100).toInt()}%",
                            fontSize = 11.sp, color = Muted, modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                    if (draft.imagePath == null) {
                        Text("文字录入", fontSize = 11.sp, color = Accent, modifier = Modifier.padding(start = 4.dp))
                    }
                }
                StemText(
                    draft.stem,
                    color = if (draft.stem.isBlank()) Muted else Ink,
                    fontSizeSp = 12.5f,
                    lineHeightSp = 19f,
                    maxLines = 3,
                    blankHint = "（未识别题干，点此补录）",
                    modifier = Modifier.padding(top = 5.dp).tjClickable { onEdit() },
                )
            }
            IconCircle(Icons.Rounded.Close, "移除这道题", tint = Muted) { onRemove() }
        }
        Spacer(Modifier.height(8.dp))
        TjTextField(
            value = tagText,
            onValueChange = { tagText = it; onTagChange(it) },
            placeholder = { Text("题号（选填，如：12 或 三(7)），入库后列表直接显示", fontSize = 11.sp, color = Muted) },
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.5.sp, color = Ink),
            colors = tjFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.clip(RoundedCornerShape(7.dp))
                    .background(if (draft.categoryName.isBlank()) AmberSoft else SurfaceAlt)
                    .border(1.dp, Line, RoundedCornerShape(7.dp))
                    .tjClickable { onPickCategory() }
                    .padding(horizontal = 9.dp, vertical = 5.dp)
            ) {
                Text(
                    draft.categoryName.ifBlank { "选择分类" },
                    fontSize = 11.5.sp,
                    color = if (draft.categoryName.isBlank()) Amber else Ink2
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                if (draft.imagePath == null) "补图" else "看图",
                fontSize = 12.5.sp, color = Accent, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.tjClickable(label = if (draft.imagePath == null) "补充题目图片" else "查看题目图片") {
                    if (draft.imagePath == null) onAttachImage() else onPreview()
                }.padding(horizontal = 8.dp, vertical = 6.dp)
            )
            Text(
                "编辑", fontSize = 12.5.sp, color = Ink2,
                modifier = Modifier.tjClickable(label = "编辑识别结果") { onEdit() }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
fun Tag(text: String) {
    Box(Modifier.clip(RoundedCornerShape(4.dp)).background(SurfaceAlt).padding(horizontal = 6.dp, vertical = 2.dp)) {
        Text(text, fontSize = 11.sp, color = Ink2)
    }
}

@Composable
private fun DraftPreviewDialog(draft: DraftQuestion, onDismiss: () -> Unit, onEdit: () -> Unit) {
    FullScreenDialog(onDismissRequest = onDismiss) {
            Row(
                Modifier.fillMaxWidth().padding(start = Space.page, end = 10.dp, top = 12.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("识别结果预览", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink)
                    Text(
                        "公式、表格和 Markdown 都按最终界面渲染",
                        fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 2.dp)
                    )
                }
                Icon(
                    Icons.Rounded.Close, "关闭",
                    tint = Ink2, modifier = Modifier.size(48.dp).clip(CircleShape)
                        .tjClickable(label = "关闭预览") { onDismiss() }.padding(10.dp)
                )
            }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState())
                    .padding(horizontal = Space.page).padding(bottom = 20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Tag(draft.type.ifBlank { "题目" })
                    Spacer(Modifier.width(8.dp))
                    Text(draft.categoryName.ifBlank { "未分类" }, fontSize = 12.sp, color = Muted)
                    Spacer(Modifier.weight(1f))
                    Text("置信 ${"%.0f".format(draft.confidence * 100)}%", fontSize = 11.5.sp, color = Muted)
                }
                Spacer(Modifier.height(10.dp))
                if (!draft.imagePath.isNullOrBlank()) {
                    AsyncImage(
                        model = File(draft.imagePath), contentDescription = "原题图片",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp)
                            .clip(RoundedCornerShape(14.dp)).background(CardBg)
                            .border(1.dp, Line, RoundedCornerShape(14.dp))
                    )
                    Spacer(Modifier.height(14.dp))
                }
                RichSection("题目", "识别到的题干与选项", Ink2) {
                    RichText(draft.stem.ifBlank { "（未识别到题干）" }, fontSize = 16, interactive = false)
                }
                if (draft.answer.isNotBlank()) {
                    Spacer(Modifier.height(14.dp))
                    RichSection("答案", "核对最终结论", Green) { RichText(draft.answer, fontSize = 15, interactive = false) }
                }
                if (draft.analysis.isNotBlank()) {
                    Spacer(Modifier.height(14.dp))
                    RichSection("解析", "按步骤复盘思路", Accent) { RichText(draft.analysis, fontSize = 15, interactive = false) }
                }
                val formulas = parseKnowledgeTags(draft.needFormulas)
                val concepts = parseKnowledgeTags(draft.needConcepts)
                val methods = parseKnowledgeTags(draft.needMethods)
                if (formulas.isNotEmpty() || concepts.isNotEmpty() || methods.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    RichSection("要做出这题，需要掌握", "公式 / 概念 / 方法", Amber) {
                        NeedGroups(formulas, concepts, methods)
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(horizontal = Space.page, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TjButton("修改", Modifier.weight(1f).fillMaxHeight(), style = ActionStyle.Secondary) { onEdit() }
                BigButton("看完了", Modifier.weight(1f).fillMaxHeight()) { onDismiss() }
            }
    }
}

@Composable
private fun NeedGroups(formulas: List<String>, concepts: List<String>, methods: List<String>) {
    Column {
        NeedGroup("公式", formulas, Accent)
        NeedGroup("概念", concepts, Green)
        NeedGroup("方法", methods, Amber)
    }
}

@Composable
private fun NeedGroup(label: String, items: List<String>, color: Color) {
    if (items.isEmpty()) return
    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Top) {
        Text(
            label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = color,
            modifier = Modifier.width(34.dp).padding(top = 3.dp)
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items.forEach { item ->
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .background(color.copy(alpha = 0.08f))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    RichText(item, fontSize = 13)
                }
            }
        }
    }
}

/**
 * 草稿编辑改为完整页面（原来是挤在小弹窗里）：改动即时回写草稿并落盘，
 * 误触返回、进程被杀都不丢。图片是选填项：没有可以补，有了可以移除。
 */
@Composable
private fun EditDraftScreen(
    draft: DraftQuestion,
    onDraftChange: (DraftQuestion) -> Unit,
    onAttachImage: (Uri) -> Unit,
    onDetachImage: () -> Unit,
    onClose: () -> Unit,
) {
    var stem by remember(draft.id) { mutableStateOf(draft.stem) }
    var answer by remember(draft.id) { mutableStateOf(draft.answer) }
    var analysis by remember(draft.id) { mutableStateOf(draft.analysis) }
    var type by remember(draft.id) { mutableStateOf(draft.type) }
    var formulas by remember(draft.id) { mutableStateOf(parseKnowledgeTags(draft.needFormulas).joinToString("，")) }
    var concepts by remember(draft.id) { mutableStateOf(parseKnowledgeTags(draft.needConcepts).joinToString("，")) }
    var methods by remember(draft.id) { mutableStateOf(parseKnowledgeTags(draft.needMethods).joinToString("，")) }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onAttachImage(uri)
    }
    fun push(
        newStem: String = stem, newAnswer: String = answer, newAnalysis: String = analysis,
        newType: String = type, newFormulas: String = formulas,
        newConcepts: String = concepts, newMethods: String = methods,
    ) {
        onDraftChange(
            draft.copy(
                stem = newStem, answer = newAnswer, analysis = newAnalysis, type = newType,
                needFormulas = tagsToJson(newFormulas), needConcepts = tagsToJson(newConcepts),
                needMethods = tagsToJson(newMethods),
            )
        )
    }

    FullScreenDialog(onDismissRequest = onClose) {
        Row(
            Modifier.fillMaxWidth().padding(start = Space.page, end = 10.dp, top = 12.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("补录 / 修改", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink)
                Text("改动即时保存到草稿", fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 2.dp))
            }
            Icon(
                Icons.Rounded.Close, "关闭",
                tint = Ink2, modifier = Modifier.size(48.dp).clip(CircleShape)
                    .tjClickable(label = "关闭编辑") { onClose() }.padding(10.dp)
            )
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Space.page)) {
            if (draft.imagePath != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = File(draft.imagePath), contentDescription = "题目图片",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.weight(1f).heightIn(max = 160.dp)
                            .clip(RoundedCornerShape(12.dp)).background(CardBg)
                            .border(1.dp, Line, RoundedCornerShape(12.dp))
                    )
                    Text(
                        "移除图片", fontSize = 12.5.sp, color = Red, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.tjClickable(label = "移除题目图片") { onDetachImage() }
                            .padding(start = 12.dp)
                    )
                }
            } else {
                Row(
                    Modifier.fillMaxWidth().clip(SmallShape).background(SurfaceAlt)
                        .tjClickable(label = "补充一张题目图片") { pickImage.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Image, null, tint = Ink2, modifier = Modifier.size(18.dp))
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text("补充图片（选填）", fontSize = 13.5.sp, color = Ink, fontWeight = FontWeight.Medium)
                        Text("文字已经够用就不用加；原图能帮复习时对照", fontSize = 11.5.sp, color = Muted, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            TjTextField(value = stem, onValueChange = { stem = it; push(newStem = it) }, label = { Text("题干", fontSize = 12.sp) }, minLines = 4, textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, lineHeight = 23.sp, color = Ink), colors = tjFieldColors(), modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            TjTextField(value = type, onValueChange = { type = it; push(newType = it) }, label = { Text("题型", fontSize = 12.sp) }, singleLine = true, textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = Ink), colors = tjFieldColors(), modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            TjTextField(value = answer, onValueChange = { answer = it; push(newAnswer = it) }, label = { Text("答案", fontSize = 12.sp) }, minLines = 2, textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, lineHeight = 23.sp, color = Ink), colors = tjFieldColors(), modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            TjTextField(value = analysis, onValueChange = { analysis = it; push(newAnalysis = it) }, label = { Text("解析", fontSize = 12.sp) }, minLines = 4, textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, lineHeight = 23.sp, color = Ink), colors = tjFieldColors(), modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Text("要做出这题，需要掌握（用逗号分隔）", fontSize = 11.5.sp, color = Muted, modifier = Modifier.padding(top = 2.dp))
            Spacer(Modifier.height(4.dp))
            TjTextField(value = formulas, onValueChange = { formulas = it; push(newFormulas = it) }, label = { Text("公式", fontSize = 12.sp) }, minLines = 1, textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = Ink), colors = tjFieldColors(), modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            TjTextField(value = concepts, onValueChange = { concepts = it; push(newConcepts = it) }, label = { Text("概念", fontSize = 12.sp) }, minLines = 1, textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = Ink), colors = tjFieldColors(), modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            TjTextField(value = methods, onValueChange = { methods = it; push(newMethods = it) }, label = { Text("方法", fontSize = 12.sp) }, minLines = 1, textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = Ink), colors = tjFieldColors(), modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Space.page, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            BigButton("完成", Modifier.weight(1f)) { onClose() }
        }
    }
}

@Composable
fun CategoryPicker(
    current: String,
    categories: List<String>,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var newName by remember { mutableStateOf("") }
    val filtered = categories.filter { it.contains(query.trim(), ignoreCase = true) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = CardBg) {
        Column(Modifier.blockSheetDrag().padding(horizontal = Space.page).padding(bottom = 26.dp)) {
            Text("选择分类", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text("当前：${current.ifBlank { "未分类" }}", fontSize = 11.5.sp, color = Muted, modifier = Modifier.padding(top = 3.dp, bottom = 10.dp))
            TjTextField(
                value = query, onValueChange = { query = it },
                placeholder = { Text("搜索分类", fontSize = 12.sp, color = Muted) },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Column(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
                filtered.forEach { name ->
                    Row(
                        Modifier.fillMaxWidth().tjClickable { onPick(name) }.padding(vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioDot(current == name)
                        Text(name, fontSize = 12.5.sp, color = Ink, modifier = Modifier.padding(start = 9.dp))
                    }
                    HorizontalDivider(color = Line, thickness = 1.dp)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TjTextField(
                    value = newName, onValueChange = { newName = it },
                    placeholder = { Text("学科/子分类，如数学/积分", fontSize = 12.sp, color = Muted) },
                    singleLine = true, modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                TjButton("新建", enabled = newName.isNotBlank(), style = ActionStyle.Secondary) { onPick(newName.trim()) }
            }
        }
    }
}

@Composable
fun ReviewScreen(vm: AppVm) {
    val q = vm.reviewQueue.getOrNull(vm.reviewIndex)
    // 答完最后一题要给出明确的完成态，不能和「本来就没有到期题」混用同一个空界面
    val finished = vm.reviewQueue.isNotEmpty() && vm.reviewIndex >= vm.reviewQueue.size
    val rs = vm.readingScaleValue
    fun s(v: Int) = (v * rs).roundToInt()

    Column(Modifier.fillMaxSize()) {
        TjTopBar(
            title = vm.reviewMode.label,
            subtitle = if (vm.reviewQueue.isEmpty()) "" else "${minOf(vm.reviewIndex + 1, vm.reviewQueue.size)} / ${vm.reviewQueue.size}",
            onBack = {
                if (finished) vm.clearReviewSession()
                vm.home()
            },
            actions = {
                if (vm.canUndoRate) {
                    Text(
                        "撤销上一题", fontSize = 12.5.sp, color = Accent, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.tjClickable(label = "撤销上一题评分") { vm.undoRating() }.padding(8.dp)
                    )
                }
            }
        )

        if (finished) {
            val wrong = vm.wrongThisRound.toList().mapNotNull { wid -> vm.reviewQueue.firstOrNull { it.id == wid } }
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Space.page),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(40.dp))
                Text("这组复习完成", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink)
                Text(
                    "已过完 ${vm.reviewQueue.size} 道题，掌握度已更新",
                    fontSize = 13.sp, color = Muted, modifier = Modifier.padding(top = 6.dp)
                )
                if (wrong.isNotEmpty()) {
                    Spacer(Modifier.height(22.dp))
                    Column(
                        Modifier.fillMaxWidth().clip(CardShape).background(CardBg)
                            .border(1.dp, Line, CardShape).padding(14.dp)
                    ) {
                        Text(
                            "本轮自评「不会」的 ${wrong.size} 道题",
                            fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = Amber
                        )
                        Spacer(Modifier.height(8.dp))
                        wrong.take(5).forEach { w ->
                            StemText(
                                w.stem,
                                color = Ink2,
                                fontSizeSp = 12.5f,
                                lineHeightSp = 18f,
                                maxLines = 1,
                                blankHint = "（无题干，见原图）",
                                modifier = Modifier.padding(vertical = 3.dp),
                            )
                        }
                        if (wrong.size > 5) Text("…等 ${wrong.size} 道", fontSize = 12.sp, color = Muted)
                    }
                    Spacer(Modifier.height(14.dp))
                    TjButton("再练这些题（${wrong.size} 道）", Modifier.fillMaxWidth()) { vm.retryWrongQuestions() }
                }
                Spacer(Modifier.height(14.dp))
                BigButton("返回错题本", Modifier.fillMaxWidth()) { vm.clearReviewSession(); vm.home() }
                Spacer(Modifier.height(24.dp))
            }
            return@Column
        }

        if (q == null) {
            EmptyState("没有到期的错题", "掌握度高的题会自动延长间隔") {
                BigButton("返回", Modifier.width(140.dp)) { vm.home() }
            }
            return@Column
        }

        Column(Modifier.fillMaxSize().padding(horizontal = Space.page)) {
            Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(SurfaceAlt)) {
                Box(
                    Modifier.fillMaxWidth((vm.reviewIndex + 1).toFloat() / vm.reviewQueue.size)
                        .height(3.dp).background(Accent)
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Tag(q.type.ifBlank { "题目" })
                if (q.customTag.isNotBlank()) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier.clip(RoundedCornerShape(4.dp)).background(AmberSoft)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) { Text(q.customTag, fontSize = 11.sp, color = Amber, maxLines = 1, fontWeight = FontWeight.SemiBold) }
                }
                Spacer(Modifier.width(6.dp))
                Text(q.categoryName, fontSize = 11.5.sp, color = Muted)
            }
            Spacer(Modifier.height(12.dp))

            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                // 原图可能带着手写答案或老师批注：揭晓前默认只给题干，想看再点开
                if (q.imagePath != null && (vm.revealAnswer || vm.showImageBeforeReveal)) {
                    AsyncImage(
                        model = File(q.imagePath), contentDescription = "题目原图",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 340.dp).clip(RoundedCornerShape(14.dp))
                            .background(SurfaceAlt).border(1.dp, Line, RoundedCornerShape(14.dp))
                    )
                    if (!vm.revealAnswer) {
                        Text(
                            "隐藏原图", fontSize = 12.sp, color = Accent, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.tjClickable(label = "隐藏原图") { vm.chooseShowImage(false) }
                                .padding(top = 6.dp)
                        )
                    }
                    Spacer(Modifier.height(18.dp))
                }
                val richStem = buildString {
                    append(q.stem.ifBlank { "（无题干，请点上方「显示原图」对照作答）" })
                    parseOptions(q.options).forEach { append(10.toChar()); append(10.toChar()); append(it) }
                }
                RichSection("题目", "先独立作答，再查看答案", Accent) {
                    RichText(richStem, fontSize = s(15))
                }
                if (q.imagePath != null && !vm.revealAnswer && !vm.showImageBeforeReveal) {
                    Text(
                        "原图已隐藏（可能含手写答案）· 点此显示",
                        fontSize = 12.sp, color = Accent, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.tjClickable(label = "显示原图") { vm.chooseShowImage(true) }
                            .padding(top = 8.dp)
                    )
                }

                if (vm.revealAnswer) {
                    Spacer(Modifier.height(18.dp))
                    if (q.answer.isNotBlank()) {
                        RichSection("答案", "核对最终结论", Green) {
                            RichText(q.answer, fontSize = s(15))
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                    if (q.analysis.isNotBlank()) {
                        RichSection("解析", "按步骤复盘思路", Accent) {
                            RichText(q.analysis, fontSize = s(15))
                        }
                    }
                    if (q.answer.isBlank() && q.analysis.isBlank()) {
                        Text("这道题还没有答案与解析。", fontSize = 12.sp, color = Muted)
                    }
                }
                Spacer(Modifier.height(24.dp))
            }

            if (!vm.revealAnswer) {
                BigButton("显示答案", Modifier.fillMaxWidth()) { vm.reveal() }
            } else {
                Text(
                    "这道题记得多牢？",
                    fontSize = 12.sp, color = Muted,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RateButton("不会", rateHint(q.mastery, 0, vm.db.previewIntervalDays(q.mastery, 0)), Red, Modifier.weight(1f)) { vm.rate(0) }
                    RateButton("不熟", rateHint(q.mastery, 1, vm.db.previewIntervalDays(q.mastery, 1)), Amber, Modifier.weight(1f)) { vm.rate(1) }
                    RateButton("掌握", rateHint(q.mastery, 2, vm.db.previewIntervalDays(q.mastery, 2)), Green, Modifier.weight(1f)) { vm.rate(2) }
                }
                if (vm.canUndoRate) {
                    Text(
                        "撤销上一题",
                        fontSize = 12.5.sp, color = Accent, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 8.dp).align(Alignment.CenterHorizontally)
                            .tjClickable(label = "撤销上一题评分") { vm.undoRating() }
                            .padding(vertical = 6.dp)
                    )
                }
            }
            Spacer(Modifier.height(if (vm.revealAnswer) 72.dp else 16.dp))
        }
    }
}

private fun intervalLabel(days: Long): String = if (days <= 1L) "明天" else "约 $days 天后"

private fun rateHint(mastery: Int, rating: Int, days: Long): String {
    val whenText = intervalLabel(days)
    return when (rating) {
        0 -> if (mastery <= 0) whenText else "$whenText · 降"
        1 -> "$whenText · 保持"
        else -> if (mastery >= 5) "$whenText · 满" else "$whenText · +1"
    }
}

@Composable
private fun RateButton(text: String, sub: String, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.heightIn(min = 52.dp).clip(CircleShape).background(CardBg)
            .border(1.dp, color.copy(alpha = 0.45f), CircleShape)
            .tjClickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text, fontSize = 14.sp, color = color, fontWeight = FontWeight.SemiBold)
            Text(sub, fontSize = 11.sp, color = Muted, modifier = Modifier.padding(top = 1.dp))
        }
    }
}


/** 逗号 / 换行 / 分号分隔的知识点文本 → JSON 字符串数组。 */
private fun tagsToJson(raw: String): String {
    val arr = org.json.JSONArray()
    raw.split('，', ',', '；', ';', '\n').map { it.trim() }.filter { it.isNotEmpty() }.forEach { arr.put(it) }
    return arr.toString()
}
