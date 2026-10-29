package com.zhuquan.tijian.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhuquan.tijian.data.QuestionRow
import com.zhuquan.tijian.data.SUBJECT_ROOTS
import com.zhuquan.tijian.export.PdfExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private data class PrintTopic(
    val id: String,
    val subject: String,
    val name: String,
    val questions: List<QuestionRow>,
)

@Composable
fun PrintScreen(vm: AppVm) {
    val scope = rememberCoroutineScope()
    var topics by remember { mutableStateOf<List<PrintTopic>>(emptyList()) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var expanded by remember { mutableStateOf<Set<String>>(emptySet()) }
    var withAnswers by remember { mutableStateOf(false) }
    var pendingFile by remember { mutableStateOf<File?>(null) }
    val createPdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val file = pendingFile
        pendingFile = null
        if (uri != null && file != null) vm.saveTo(uri, file)
    }
    LaunchedEffect(vm.dataRevision) {
        val rows = withContext(Dispatchers.IO) { vm.db.questions() }
        val grouped = rows.groupBy { it.categoryId ?: it.categoryName }
            .map { (id, qs) ->
                val first = qs.first()
                PrintTopic(
                    id = id,
                    subject = first.subjectName.ifBlank { "其他" },
                    name = first.categoryName.ifBlank { "未分类" },
                    questions = qs,
                )
            }
        val subjectOrder = SUBJECT_ROOTS.withIndex().associate { it.value to it.index }
        topics = grouped.sortedWith(
            compareBy<PrintTopic> { subjectOrder[it.subject] ?: SUBJECT_ROOTS.size }
                .thenBy { it.name }
        )
        val live = rows.map { it.id }.toSet()
        selected = selected.intersect(live)
    }
    val allIds = topics.flatMap { it.questions.map { q -> q.id } }
    val starredIds = topics.flatMap { it.questions.filter { q -> q.starred }.map { q -> q.id } }
    val chosen = topics.flatMap { it.questions }.filter { it.id in selected }

    fun export(share: Boolean) {
        if (chosen.isEmpty() || vm.busy) return
        val packet = chosen
        scope.launch {
            var file: File? = null
            vm.withBusy("排版 PDF") {
                file = vm.buildPdf(
                    packet,
                    "错题",
                    PdfExporter.Options(
                        includeAnswer = withAnswers,
                        includeAnalysis = false,
                        includeTags = false,
                        onePerPage = true,
                        answersAtEnd = withAnswers,
                    ),
                )
            }
            val ready = file ?: return@launch
            if (share) shareFile(vm.getApplication(), ready)
            else {
                pendingFile = ready
                createPdf.launch("错题_${dateStamp()}.pdf")
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TjTopBar(
            title = "打印题目",
            subtitle = if (selected.isEmpty()) "按主题勾选，可以跨主题合成一份" else "已选 ${selected.size} 道",
            onBack = { vm.back() },
            actions = {
                Text(
                    if (selected.size == allIds.size && allIds.isNotEmpty()) "取消全选" else "全选",
                    fontSize = 13.sp, color = Accent, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.tjClickable(label = "全选所有题目") {
                        selected = if (selected.size == allIds.size) emptySet() else allIds.toSet()
                    }.padding(8.dp)
                )
            }
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Space.page, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TjChip("只选标星${if (starredIds.isEmpty()) "" else " · ${starredIds.size}"}", selected.isNotEmpty() && selected == starredIds.toSet()) {
                if (starredIds.isEmpty()) vm.toast = "还没有标星的题。点题目右侧的星，以后就能一次选中。"
                else selected = starredIds.toSet()
            }
        }
        if (topics.isEmpty()) {
            EmptyState("还没有可以打印的题", "先收录几道错题")
        } else {
            LazyColumn(Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 12.dp)) {
                val bySubject = topics.groupBy { it.subject }
                val subjectOrder = SUBJECT_ROOTS + bySubject.keys.filter { it !in SUBJECT_ROOTS }
                subjectOrder.forEach { subject ->
                    val group = bySubject[subject].orEmpty()
                    if (group.isEmpty()) return@forEach
                    item(key = "sub-$subject") {
                        Text(
                            subject,
                            fontSize = 13.sp, color = Muted, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = Space.page, top = 14.dp, bottom = 4.dp)
                        )
                    }
                    items(group, key = { it.id }) { topic ->
                        TopicBlock(
                            topic = topic,
                            selected = selected,
                            expanded = topic.id in expanded,
                            onToggleExpand = {
                                expanded = if (topic.id in expanded) expanded - topic.id else expanded + topic.id
                            },
                            onToggleTopic = {
                                val ids = topic.questions.map { it.id }.toSet()
                                selected = if (ids.all { it in selected }) selected - ids else selected + ids
                            },
                            onToggleQuestion = { id ->
                                selected = if (id in selected) selected - id else selected + id
                            },
                            onToggleStar = { q -> vm.setStarred(q.id, !q.starred) },
                            onSelectStarredInTopic = {
                                val ids = topic.questions.filter { it.starred }.map { it.id }.toSet()
                                if (ids.isEmpty()) vm.toast = "这个主题还没有标星的题"
                                else selected = (selected - topic.questions.map { it.id }.toSet()) + ids
                            },
                        )
                    }
                }
            }
        }
        Column(Modifier.fillMaxWidth().background(CardBg).padding(horizontal = Space.page, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (withAnswers) "附上答案（印在最后）" else "只印题目",
                    fontSize = 13.sp, color = Ink, modifier = Modifier.weight(1f)
                )
                Text(
                    if (withAnswers) "关闭答案" else "附上答案",
                    fontSize = 13.sp, color = Accent, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.tjClickable(label = "切换是否附答案") { withAnswers = !withAnswers }
                        .padding(vertical = 8.dp, horizontal = 4.dp)
                )
            }
            Text(
                "默认不含答案和解析，每题单独一页，页下留作答线。",
                fontSize = 11.5.sp, color = Muted, modifier = Modifier.padding(bottom = 8.dp)
            )
            Row(Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TjButton(
                    "分享",
                    Modifier.weight(1f).fillMaxHeight(),
                    style = ActionStyle.Secondary,
                    enabled = selected.isNotEmpty() && !vm.busy,
                ) { export(true) }
                TjButton(
                    if (selected.isEmpty()) "先勾选题目" else "保存 ${selected.size} 道",
                    Modifier.weight(1.3f).fillMaxHeight(),
                    enabled = selected.isNotEmpty() && !vm.busy,
                ) { export(false) }
            }
        }
    }
}

@Composable
private fun TopicBlock(
    topic: PrintTopic,
    selected: Set<String>,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onToggleTopic: () -> Unit,
    onToggleQuestion: (String) -> Unit,
    onToggleStar: (QuestionRow) -> Unit,
    onSelectStarredInTopic: () -> Unit,
) {
    val ids = topic.questions.map { it.id }
    val picked = ids.count { it in selected }
    val starred = topic.questions.count { it.starred }
    val all = picked == ids.size && ids.isNotEmpty()
    Column(Modifier.fillMaxWidth().padding(horizontal = Space.page, vertical = 4.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(CardShape).background(CardBg).border(1.dp, Line, CardShape)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CheckBox(
                checked = all,
                partial = picked in 1 until ids.size,
                label = if (all) "取消全选${topic.name}" else "全选${topic.name}",
                onClick = onToggleTopic,
            )
            Column(
                Modifier.weight(1f).tjClickable(label = "展开${topic.name}", onClick = onToggleExpand)
                    .padding(vertical = 4.dp)
            ) {
                Text(topic.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    buildString {
                        append("${ids.size} 道")
                        if (picked > 0) append(" · 已选 $picked")
                        if (starred > 0) append(" · 标星 $starred")
                    },
                    fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 2.dp)
                )
            }
            Icon(
                if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = if (expanded) "收起" else "展开",
                tint = Muted,
                modifier = Modifier.size(22.dp).tjClickable(label = "展开${topic.name}", onClick = onToggleExpand)
            )
        }
        if (expanded) {
            if (starred > 0) {
                Text(
                    "只选此主题里的标星",
                    fontSize = 12.5.sp, color = Accent, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 8.dp, top = 6.dp)
                        .tjClickable(label = "只选此主题里的标星", onClick = onSelectStarredInTopic)
                        .padding(vertical = 4.dp)
                )
            }
            topic.questions.forEach { q ->
                Row(
                    Modifier.fillMaxWidth().padding(start = 4.dp, top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CheckBox(
                        checked = q.id in selected,
                        partial = false,
                        label = "选择这道题",
                        onClick = { onToggleQuestion(q.id) },
                    )
                    StemText(
                        q.stem, color = Ink, fontSizeSp = 13.5f, lineHeightSp = 20f, maxLines = 2,
                        modifier = Modifier.weight(1f).tjClickable(label = "选择这道题") { onToggleQuestion(q.id) }
                            .padding(vertical = 6.dp)
                    )
                    Icon(
                        if (q.starred) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        contentDescription = if (q.starred) "取消标星" else "标为重要",
                        tint = if (q.starred) Amber else Muted,
                        modifier = Modifier
                            .size(40.dp)
                            .tjClickable(label = if (q.starred) "取消标星" else "标为重要") { onToggleStar(q) }
                            .padding(8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CheckBox(checked: Boolean, partial: Boolean, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(28.dp).clip(RoundedCornerShape(7.dp))
            .background(if (checked) Accent else CardBg)
            .border(1.5.dp, if (checked || partial) Accent else FieldBorder, RoundedCornerShape(7.dp))
            .tjClickable(label = label, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            Text("✓", fontSize = 14.sp, color = OnAccent, fontWeight = FontWeight.Bold)
        } else if (partial) {
            Box(Modifier.size(width = 12.dp, height = 2.dp).background(Accent))
        }
    }
    Spacer(Modifier.size(8.dp))
}
