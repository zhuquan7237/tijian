import pathlib

# ---------- Db: update doc with new sources ----------
p = pathlib.Path('app/src/main/java/com/zhuquan/tijian/data/Db.kt')
s = p.read_text(encoding='utf-8')
anchor = '''    fun updateDoc(id: String, title: String, content: String) = writableDatabase.execSQL(
        "UPDATE knowledge_doc SET title=?, content_md=?, updated_at=? WHERE id=?",
        arrayOf(title.trim(), content, System.currentTimeMillis(), id)
    )
'''
assert anchor in s
add = anchor + '''
    fun updateDocSources(id: String, categoryId: String?, sourceRefs: String, title: String, content: String) =
        writableDatabase.execSQL(
            "UPDATE knowledge_doc SET category_id=?, source_refs=?, title=?, content_md=?, updated_at=? WHERE id=?",
            arrayOf(categoryId, sourceRefs, title.trim(), content, System.currentTimeMillis(), id)
        )
'''
s = s.replace(anchor, add, 1)
p.write_text(s, encoding='utf-8')
print('Db.updateDocSources')

# ---------- AppVm: regenerateKnowledge ----------
p = pathlib.Path('app/src/main/java/com/zhuquan/tijian/ui/AppVm.kt')
s = p.read_text(encoding='utf-8')
anchor = '    fun updateDoc(id: String, title: String, content: String) {'
assert anchor in s
method = '''    /** 重新选分类，用新范围重新生成同一篇知识文档（覆盖原内容）。 */
    fun regenerateKnowledge(docId: String, categories: List<CategoryRow>) {
        val selected = categories.distinctBy { it.id }
        if (selected.isEmpty()) { toast = "先选择至少一个分类"; return }
        if (!provider.ready()) { toast = "先在「我的 → AI 接入」配置模型"; return }
        viewModelScope.launch {
            busy = true; busyLabel = "重新提炼知识点"
            try {
                val qs = withContext(Dispatchers.IO) {
                    selected.flatMap { db.questions(it.id, "all") }.distinctBy { it.id }
                }
                if (qs.isEmpty()) { toast = "所选分类还没有题目"; return@launch }
                val material = qs.take(20).joinToString("\\n\\n") { q ->
                    buildString {
                        append("题型：").append(q.type).append("\\n")
                        append("题干：").append(q.stem).append("\\n")
                        if (q.answer.isNotBlank()) append("答案：").append(q.answer).append("\\n")
                        if (q.analysis.isNotBlank()) append("解析：").append(q.analysis).append("\\n")
                        append("知识点：").append(q.tags)
                    }
                }
                val md = ai.knowledge(provider, material)
                val ids = JSONArray(qs.take(20).map { it.id }).toString()
                val title = if (selected.size == 1) selected.first().name else selected.joinToString(" · ") { it.name }
                withContext(Dispatchers.IO) {
                    db.updateDocSources(docId, selected.first().id, ids, title.ifBlank { "知识点整理" }, md)
                }
                refresh()
                toast = "已按 ${selected.size} 个分类重新生成"
                go(Route.Doc(docId))
            } catch (e: Exception) {
                toast = "重新生成失败：${e.message}"
            } finally {
                busy = false; busyLabel = ""
            }
        }
    }

'''
s = s.replace(anchor, method + anchor, 1)
p.write_text(s, encoding='utf-8')
print('AppVm.regenerateKnowledge')

# ---------- LibraryScreens: regenerate picker + menu item ----------
p = pathlib.Path('app/src/main/java/com/zhuquan/tijian/ui/LibraryScreens.kt')
s = p.read_text(encoding='utf-8')

s = s.replace('    var deletingDoc by remember { mutableStateOf<DocRow?>(null) }',
              '    var deletingDoc by remember { mutableStateOf<DocRow?>(null) }\n    var regeneratingDoc by remember { mutableStateOf<DocRow?>(null) }', 1)

s = s.replace('''                        onEdit = { editingDoc = doc },''',
              '''                        onEdit = { editingDoc = doc },
                        onRegenerate = { regeneratingDoc = doc },''', 1)

# picker block
anchor = '''    if (pickCategory) {
        CategoryMultiPicker(
            categories = vm.categories.filter { it.count > 0 },
            onDismiss = { pickCategory = false },
            onConfirm = { selected ->
                pickCategory = false
                if (selected.isNotEmpty()) vm.generateKnowledge(selected)
            }
        )
    }
'''
assert anchor in s
new_block = anchor + '''
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
'''
s = s.replace(anchor, new_block, 1)

# DocRowItem signature + menu item
s = s.replace('''private fun DocRowItem(
    doc: DocRow,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onPdf: () -> Unit,
) {''', '''private fun DocRowItem(
    doc: DocRow,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onRegenerate: () -> Unit,
    onDelete: () -> Unit,
    onPdf: () -> Unit,
) {''', 1)

s = s.replace('''                androidx.compose.material3.DropdownMenuItem(
                    text = { Text("编辑内容", fontSize = 14.sp) },
                    onClick = { menu = false; onEdit() }
                )''', '''                androidx.compose.material3.DropdownMenuItem(
                    text = { Text("重新选择分类生成", fontSize = 14.sp) },
                    onClick = { menu = false; onRegenerate() }
                )
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text("编辑文字内容", fontSize = 14.sp) },
                    onClick = { menu = false; onEdit() }
                )''', 1)

# CategoryMultiPicker: preselected + confirmText
s = s.replace('''fun CategoryMultiPicker(
    categories: List<CategoryRow>,
    onDismiss: () -> Unit,
    onConfirm: (List<CategoryRow>) -> Unit,
) {
    val chosen = remember { mutableStateOf<Set<String>>(emptySet()) }''', '''fun CategoryMultiPicker(
    categories: List<CategoryRow>,
    preselected: Set<String> = emptySet(),
    confirmText: String = "生成知识文档",
    onDismiss: () -> Unit,
    onConfirm: (List<CategoryRow>) -> Unit,
) {
    val chosen = remember { mutableStateOf(preselected) }''', 1)

s = s.replace('''                    if (chosen.value.isEmpty()) "选择分类后生成" else "生成知识文档（${chosen.value.size} 个分类）",''',
              '''                    if (chosen.value.isEmpty()) "选择分类后生成" else "$confirmText（${chosen.value.size} 个分类）",''', 1)

p.write_text(s, encoding='utf-8')
print('LibraryScreens regenerate UI')

# ---------- DocScreen reload on revision ----------
p = pathlib.Path('app/src/main/java/com/zhuquan/tijian/ui/LibraryScreens.kt')
s = p.read_text(encoding='utf-8')
s = s.replace('LaunchedEffect(id) { doc = withContext(Dispatchers.IO) { vm.db.doc(id) } }',
              'LaunchedEffect(id, vm.dataRevision) { doc = withContext(Dispatchers.IO) { vm.db.doc(id) } }', 1)
p.write_text(s, encoding='utf-8')
print('DocScreen reload keyed by revision')
