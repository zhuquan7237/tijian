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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import java.io.File

@Composable
fun ImportScreen(vm: AppVm) {
    var editing by remember { mutableStateOf<DraftQuestion?>(null) }
    var pickerFor by remember { mutableStateOf<DraftQuestion?>(null) }

    Column(Modifier.fillMaxSize()) {
        TjTopBar(
            title = if (vm.importing) "识别中" else "识别校对",
            subtitle = if (vm.importing) vm.importLabel else "${vm.pending.size} 题待确认",
            onBack = { vm.home() },
            actions = {
                if (!vm.importing && vm.pending.isNotEmpty()) {
                    Text("全部确认", fontSize = 12.5.sp, color = Accent,
                        modifier = Modifier.clickableNoRipple { vm.confirmPending() }.padding(8.dp))
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
                    color = Accent, strokeWidth = 3.dp, trackColor = Color(0xFFEDEDE7)
                )
                Spacer(Modifier.height(18.dp))
                Text(vm.importLabel, fontSize = 13.sp, color = Ink)
                Text("图片只在你配置的模型服务里处理", fontSize = 10.5.sp, color = Muted, modifier = Modifier.padding(top = 6.dp))
            }
            return@Column
        }

        if (vm.pending.isEmpty()) {
            EmptyState("没有待确认的题目", "回到错题本重新导入") {
                BigButton("返回", Modifier.width(140.dp)) { vm.home() }
            }
            if (vm.importErrors.isNotEmpty()) {
                Column(Modifier.padding(horizontal = 18.dp)) {
                    vm.importErrors.take(4).forEach {
                        Text("· $it", fontSize = 10.5.sp, color = Amber, modifier = Modifier.padding(vertical = 2.dp))
                    }
                }
            }
            return@Column
        }

        if (vm.importErrors.isNotEmpty()) {
            Box(
                Modifier.padding(horizontal = 18.dp).fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp)).background(Color(0xFFFDF8EF))
                    .padding(10.dp)
            ) {
                Text(vm.importErrors.first(), fontSize = 10.5.sp, color = Color(0xFF8A5E1F), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }

        LazyColumn(Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp, vertical = 10.dp)) {
            items(vm.pending, key = { it.id }) { d ->
                DraftCard(
                    draft = d,
                    onEdit = { editing = d },
                    onPickCategory = { pickerFor = d },
                    onRemove = { vm.removeDraft(d.id) }
                )
                Spacer(Modifier.height(9.dp))
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                Modifier.weight(0.38f).height(46.dp).clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFF4F4EF)).clickableNoRipple { vm.autoClassify() },
                contentAlignment = Alignment.Center
            ) { Text("AI 重新分类", fontSize = 11.5.sp, color = Ink2) }
            BigButton("确认入库 ${vm.pending.size} 题", Modifier.weight(0.62f)) { vm.confirmPending() }
        }
    }

    editing?.let { d ->
        EditDraftDialog(d, onDismiss = { editing = null }) { updated ->
            vm.updateDraft(updated)
            editing = null
        }
    }

    pickerFor?.let { d ->
        CategoryPicker(
            current = d.categoryName,
            categories = vm.categories.map { it.name },
            onDismiss = { pickerFor = null },
            onPick = { name ->
                vm.updateDraft(d.copy(categoryName = name))
                pickerFor = null
            }
        )
    }

    if (vm.busy) BusyOverlay(vm.busyLabel)
}

@Composable
private fun DraftCard(
    draft: DraftQuestion,
    onEdit: () -> Unit,
    onPickCategory: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(Color.White)
            .border(1.dp, Line, RoundedCornerShape(13.dp)).padding(11.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Thumb(draft.imagePath, Modifier.width(40.dp).height(48.dp))
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
                            "  ${(draft.confidence * 100).toInt()}%",
                            fontSize = 9.5.sp, color = Muted
                        )
                    }
                }
                Text(
                    draft.stem.ifBlank { "（未识别题干，点此补录）" },
                    fontSize = 12.5.sp, color = if (draft.stem.isBlank()) Muted else Ink,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 5.dp).clickableNoRipple(onEdit)
                )
            }
            Icon(
                Icons.Rounded.Close, "移除", tint = Color(0xFFBDBDB6),
                modifier = Modifier.size(18.dp).clickableNoRipple(onRemove)
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.clip(RoundedCornerShape(7.dp))
                    .background(if (draft.categoryName.isBlank()) Color(0xFFFDF8EF) else Color(0xFFFCFCF9))
                    .border(1.dp, Line, RoundedCornerShape(7.dp))
                    .clickableNoRipple(onPickCategory)
                    .padding(horizontal = 9.dp, vertical = 5.dp)
            ) {
                Text(
                    draft.categoryName.ifBlank { "选择分类" },
                    fontSize = 10.5.sp,
                    color = if (draft.categoryName.isBlank()) Amber else Ink2
                )
            }
            Spacer(Modifier.weight(1f))
            Text("编辑", fontSize = 10.5.sp, color = Accent, modifier = Modifier.clickableNoRipple(onEdit).padding(4.dp))
        }
    }
}

@Composable
fun Tag(text: String) {
    Box(Modifier.clip(RoundedCornerShape(4.dp)).background(Color(0xFFF3F3EE)).padding(horizontal = 6.dp, vertical = 2.dp)) {
        Text(text, fontSize = 9.5.sp, color = Ink2)
    }
}

@Composable
private fun EditDraftDialog(draft: DraftQuestion, onDismiss: () -> Unit, onSave: (DraftQuestion) -> Unit) {
    var stem by remember { mutableStateOf(draft.stem) }
    var answer by remember { mutableStateOf(draft.answer) }
    var analysis by remember { mutableStateOf(draft.analysis) }
    var type by remember { mutableStateOf(draft.type) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("补录 / 修改", fontSize = 16.sp) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(value = stem, onValueChange = { stem = it }, label = { Text("题干", fontSize = 11.sp) }, minLines = 3, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = type, onValueChange = { type = it }, label = { Text("题型", fontSize = 11.sp) }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = answer, onValueChange = { answer = it }, label = { Text("答案", fontSize = 11.sp) }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = analysis, onValueChange = { analysis = it }, label = { Text("解析", fontSize = 11.sp) }, minLines = 2, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(draft.copy(stem = stem, answer = answer, analysis = analysis, type = type))
            }) { Text("完成") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
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
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color.White) {
        Column(Modifier.padding(horizontal = 18.dp).padding(bottom = 26.dp)) {
            Text("选择分类", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text("当前：${current.ifBlank { "未分类" }}", fontSize = 10.5.sp, color = Muted, modifier = Modifier.padding(top = 3.dp, bottom = 10.dp))
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                placeholder = { Text("搜索分类", fontSize = 12.sp, color = Muted) },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Column(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
                filtered.forEach { name ->
                    Row(
                        Modifier.fillMaxWidth().clickableNoRipple { onPick(name) }.padding(vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioDot(current == name)
                        Text(name, fontSize = 12.5.sp, color = Ink, modifier = Modifier.padding(start = 9.dp))
                    }
                    HorizontalDivider(color = Color(0xFFF2F2EC), thickness = 1.dp)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newName, onValueChange = { newName = it },
                    placeholder = { Text("新建分类", fontSize = 12.sp, color = Muted) },
                    singleLine = true, modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.height(44.dp).clip(RoundedCornerShape(10.dp)).background(Accent)
                        .clickableNoRipple { if (newName.isNotBlank()) onPick(newName.trim()) }
                        .padding(horizontal = 14.dp), contentAlignment = Alignment.Center
                ) { Text("新建", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

@Composable
fun ReviewScreen(vm: AppVm) {
    val q = vm.reviewQueue.getOrNull(vm.reviewIndex)

    Column(Modifier.fillMaxSize()) {
        TjTopBar(
            title = "复习",
            subtitle = if (vm.reviewQueue.isEmpty()) "" else "${vm.reviewIndex + 1} / ${vm.reviewQueue.size}",
            onBack = { vm.home() }
        )

        if (q == null) {
            EmptyState("没有到期的错题", "掌握度高的题会自动延长间隔") {
                BigButton("返回", Modifier.width(140.dp)) { vm.home() }
            }
            return@Column
        }

        Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
            Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(SurfaceAlt)) {
                Box(
                    Modifier.fillMaxWidth((vm.reviewIndex + 1).toFloat() / vm.reviewQueue.size)
                        .height(3.dp).background(Ink)
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Tag(q.type.ifBlank { "题目" })
                Spacer(Modifier.width(6.dp))
                Text(q.categoryName, fontSize = 10.5.sp, color = Muted)
            }
            Spacer(Modifier.height(12.dp))

            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                if (q.imagePath != null) {
                    AsyncImage(
                        model = File(q.imagePath), contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 340.dp).clip(RoundedCornerShape(14.dp))
                            .background(Color.White).border(1.dp, Line, RoundedCornerShape(14.dp))
                    )
                    Spacer(Modifier.height(14.dp))
                }
                val richStem = buildString {
                    append(q.stem.ifBlank { "（无题干，见原图）" })
                    parseOptions(q.options).forEach { append(10.toChar()); append(10.toChar()); append(it) }
                }
                RichSection("题目", "先独立作答，再查看答案", Accent) {
                    RichText(richStem, fontSize = 14)
                }

                if (vm.revealAnswer) {
                    Spacer(Modifier.height(18.dp))
                    if (q.answer.isNotBlank()) {
                        RichSection("答案", "核对最终结论", Green) {
                            RichText(q.answer, fontSize = 14)
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                    if (q.analysis.isNotBlank()) {
                        RichSection("解析", "按步骤复盘思路", Accent) {
                            RichText(q.analysis, fontSize = 14)
                        }
                    }
                    if (q.answer.isBlank() && q.analysis.isBlank()) {
                        Text("这道题还没有答案与解析。", fontSize = 12.sp, color = Muted)
                    }
                }
                Spacer(Modifier.height(80.dp))
            }

            if (!vm.revealAnswer) {
                BigButton("显示答案", Modifier.fillMaxWidth()) { vm.reveal() }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RateButton("忘了", Red, Modifier.weight(1f)) { vm.rate(0) }
                    RateButton("模糊", Amber, Modifier.weight(1f)) { vm.rate(1) }
                    RateButton("记得", Green, Modifier.weight(1f)) { vm.rate(2) }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun RateButton(text: String, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(50.dp).clip(RoundedCornerShape(14.dp)).background(Color.White)
            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
            .clickableNoRipple(onClick),
        contentAlignment = Alignment.Center
    ) { Text(text, fontSize = 12.5.sp, color = color, fontWeight = FontWeight.SemiBold) }
}
