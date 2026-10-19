import pathlib

p = pathlib.Path('app/src/main/java/com/zhuquan/tijian/ui/ImportReview.kt')
s = p.read_text(encoding='utf-8').replace('\r\n', '\n')


def rep(old, new):
    global s
    assert old in s, "NOT FOUND: " + old[:90]
    s = s.replace(old, new, 1)


# state + wiring
rep('''    var editing by remember { mutableStateOf<DraftQuestion?>(null) }
    var pickerFor by remember { mutableStateOf<DraftQuestion?>(null) }''',
    '''    var editing by remember { mutableStateOf<DraftQuestion?>(null) }
    var previewing by remember { mutableStateOf<DraftQuestion?>(null) }
    var pickerFor by remember { mutableStateOf<DraftQuestion?>(null) }''')

rep('''                DraftCard(
                    draft = d,
                    onEdit = { editing = d },
                    onPickCategory = { pickerFor = d },
                    onRemove = { vm.removeDraft(d.id) }
                )''',
    '''                DraftCard(
                    draft = d,
                    onPreview = { previewing = d },
                    onEdit = { editing = d },
                    onPickCategory = { pickerFor = d },
                    onRemove = { vm.removeDraft(d.id) }
                )''')

rep('''    editing?.let { d ->''',
    '''    previewing?.let { d ->
        DraftPreviewDialog(
            draft = d,
            onDismiss = { previewing = null },
            onEdit = { previewing = null; editing = d }
        )
    }

    editing?.let { d ->''')

rep('''private fun DraftCard(
    draft: DraftQuestion,
    onEdit: () -> Unit,
    onPickCategory: () -> Unit,
    onRemove: () -> Unit,
) {''',
    '''private fun DraftCard(
    draft: DraftQuestion,
    onPreview: () -> Unit,
    onEdit: () -> Unit,
    onPickCategory: () -> Unit,
    onRemove: () -> Unit,
) {''')

# actions row: add 预览
rep('''            Spacer(Modifier.weight(1f))
            Text("编辑", fontSize = 11.5.sp, color = Accent, modifier = Modifier.tjClickable { onEdit() }.padding(4.dp))''',
    '''            Spacer(Modifier.weight(1f))
            Text(
                "预览", fontSize = 12.5.sp, color = Accent, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.tjClickable(label = "预览识别结果") { onPreview() }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            )
            Text(
                "编辑", fontSize = 12.5.sp, color = Ink2,
                modifier = Modifier.tjClickable(label = "编辑识别结果") { onEdit() }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            )''')

# dialog implementation before EditDraftDialog
rep('''@Composable
private fun EditDraftDialog(''',
    '''@Composable
private fun DraftPreviewDialog(draft: DraftQuestion, onDismiss: () -> Unit, onEdit: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false, decorFitsSystemWindows = false
        )
    ) {
        Column(Modifier.fillMaxSize().background(Bg)) {
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
                    tint = Ink2, modifier = Modifier.size(44.dp).clip(CircleShape)
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
                if (draft.imagePath.isNotBlank()) {
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
                    RichText(draft.stem.ifBlank { "（未识别到题干）" }, fontSize = 16)
                }
                if (draft.answer.isNotBlank()) {
                    Spacer(Modifier.height(14.dp))
                    RichSection("答案", "核对最终结论", Green) { RichText(draft.answer, fontSize = 15) }
                }
                if (draft.analysis.isNotBlank()) {
                    Spacer(Modifier.height(14.dp))
                    RichSection("解析", "按步骤复盘思路", Accent) { RichText(draft.analysis, fontSize = 15) }
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
                Modifier.fillMaxWidth().padding(horizontal = Space.page, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    Modifier.weight(0.42f).height(50.dp).clip(RoundedCornerShape(12.dp))
                        .background(SurfaceAlt).tjClickable { onEdit() },
                    contentAlignment = Alignment.Center
                ) { Text("修改", fontSize = 14.sp, color = Ink2, fontWeight = FontWeight.Medium) }
                BigButton("看完了", Modifier.weight(0.58f)) { onDismiss() }
            }
        }
    }
}

@Composable
private fun NeedGroups(formulas: List<String>, concepts: List<String>, methods: List<String>) {
    Column {
        NeedGroup("公式", formulas, Color(0xFF2F4BD8))
        NeedGroup("概念", concepts, Color(0xFF2F7D63))
        NeedGroup("方法", methods, Color(0xFF9A6B1F))
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

@Composable
private fun EditDraftDialog(''')

# imports
s = s.replace('import androidx.compose.ui.text.style.TextOverflow\n',
              'import androidx.compose.ui.text.style.TextOverflow\nimport androidx.compose.ui.window.Dialog\n')
p.write_text(s, encoding='utf-8')
print('preview added')
