import pathlib

p = pathlib.Path('app/src/main/java/com/zhuquan/tijian/ui/AppVm.kt')
s = p.read_text(encoding='utf-8').replace('\r\n', '\n')
old = '''    fun deleteDoc(id: String) {'''
new = '''    fun updateDoc(id: String, title: String, content: String) {
        viewModelScope.launch(Dispatchers.IO) {
            db.updateDoc(id, title, content)
            refresh()
            withContext(Dispatchers.Main) { toast = "已保存修改" }
        }
    }

    fun deleteDoc(id: String) {'''
assert old in s
s = s.replace(old, new, 1)
p.write_text(s, encoding='utf-8')

p = pathlib.Path('app/src/main/java/com/zhuquan/tijian/ui/LibraryScreens.kt')
s = p.read_text(encoding='utf-8').replace('\r\n', '\n')


def rep(old, new):
    global s
    assert old in s, "NOT FOUND: " + old[:90]
    s = s.replace(old, new, 1)


# screen state
rep('''fun LibraryScreen(vm: AppVm) {
    var pickCategory by remember { mutableStateOf(false) }''',
    '''fun LibraryScreen(vm: AppVm) {
    var pickCategory by remember { mutableStateOf(false) }
    var editingDoc by remember { mutableStateOf<DocRow?>(null) }
    var deletingDoc by remember { mutableStateOf<DocRow?>(null) }''')

# pass callbacks
rep('''                    DocRowItem(doc,
                        onOpen = { vm.go(Route.Doc(doc.id)) },
                        onPdf = {''',
    '''                    DocRowItem(doc,
                        onOpen = { vm.go(Route.Doc(doc.id)) },
                        onEdit = { editingDoc = doc },
                        onDelete = { deletingDoc = doc },
                        onPdf = {''')

# dialogs before end of LibraryScreen (after pickCategory block)
rep('''    if (pickCategory) {
        CategoryMultiPicker(''',
    '''    editingDoc?.let { doc ->
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
        CategoryMultiPicker(''')

# DocRowItem signature + menu
rep('''private fun DocRowItem(doc: DocRow, onOpen: () -> Unit, onPdf: () -> Unit) {''',
    '''private fun DocRowItem(
    doc: DocRow,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onPdf: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }''')

rep('''        Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).tjClickable(label = "导出 PDF") { onPdf() }, contentAlignment = Alignment.Center) {''',
    '''        Box {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(10.dp))
                    .tjClickable(label = "更多操作") { menu = true },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Rounded.MoreVert, null, tint = Muted, modifier = Modifier.size(20.dp)) }
            androidx.compose.material3.DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text("编辑内容", fontSize = 14.sp) },
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
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).tjClickable(label = "导出 PDF") { onPdf() }, contentAlignment = Alignment.Center) {''')

# add DocEditDialog before DocScreen
rep('''@Composable
fun DocScreen(''',
    '''@Composable
private fun DocEditDialog(
    doc: DocRow,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var title by remember { mutableStateOf(doc.title) }
    var content by remember { mutableStateOf(doc.contentMd) }
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
                    Text("编辑知识文档", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink)
                    Text("支持 Markdown 与 LaTeX（$...$）", fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 2.dp))
                }
                Box(
                    Modifier.size(44.dp).clip(CircleShape).tjClickable(label = "取消") { onDismiss() },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Rounded.Close, null, tint = Ink2, modifier = Modifier.size(22.dp)) }
            }
            Column(Modifier.weight(1f).padding(horizontal = Space.page)) {
                OutlinedTextField(
                    value = title, onValueChange = { title = it },
                    label = { Text("标题", fontSize = 12.sp) },
                    singleLine = true, colors = tjFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
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
                Modifier.fillMaxWidth().padding(horizontal = Space.page, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    Modifier.weight(0.38f).height(50.dp).clip(RoundedCornerShape(12.dp))
                        .background(SurfaceAlt).tjClickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) { Text("取消", fontSize = 14.sp, color = Ink2) }
                BigButton("保存", Modifier.weight(0.62f)) {
                    if (title.isNotBlank()) onSave(title.trim(), content)
                }
            }
        }
    }
}

@Composable
fun DocScreen(''')

# imports
s = s.replace('import androidx.compose.material.icons.rounded.Description\n',
              'import androidx.compose.material.icons.rounded.Description\nimport androidx.compose.material.icons.rounded.MoreVert\n')
s = s.replace('import androidx.compose.material3.Text\n',
              'import androidx.compose.material3.AlertDialog\nimport androidx.compose.material3.Icon\nimport androidx.compose.material3.Text\n')
s = s.replace('import androidx.compose.ui.window.Dialog\n', '')
s = s.replace('import androidx.compose.foundation.layout.heightIn\n',
              'import androidx.compose.foundation.layout.heightIn\nimport androidx.compose.ui.window.Dialog\n')

p.write_text(s, encoding='utf-8')
print('library edit added')
