import pathlib

# ---- AppVm: batch delete ----
p = pathlib.Path('app/src/main/java/com/zhuquan/tijian/ui/AppVm.kt')
s = p.read_text(encoding='utf-8')
anchor = '    fun deleteDoc(id: String) {'
assert anchor in s
add = '''    /** 多选批量删除（软删除），成功后刷新列表。 */
    fun deleteQuestions(ids: List<String>) {
        if (ids.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            ids.forEach { db.deleteQuestion(it) }
            withContext(Dispatchers.Main) { refresh() }
        }
    }

'''
s = s.replace(anchor, add + anchor, 1)
p.write_text(s, encoding='utf-8')
print('vm deleteQuestions added')

# ---- HomeScreens: bottom bar delete + confirm dialog ----
p = pathlib.Path('app/src/main/java/com/zhuquan/tijian/ui/HomeScreens.kt')
s = p.read_text(encoding='utf-8')

# state next to selected
old_state = '    val selected = remember { mutableStateOf<Set<String>>(emptySet()) }'
assert old_state in s
s = s.replace(old_state, old_state + '\n    var pendingBatchDelete by remember { mutableStateOf(false) }', 1)

old_bar = '''                Text("已选 ${selected.value.size} 题", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.clip(RoundedCornerShape(9.dp)).background(Color.White)
                        .tjClickable { showExport = true }.padding(horizontal = 14.dp, vertical = 8.dp)
                ) { Text("导出 PDF", fontSize = 11.5.sp, color = Ink, fontWeight = FontWeight.SemiBold) }'''
new_bar = '''                Text("已选 ${selected.value.size} 题", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.height(38.dp).clip(RoundedCornerShape(9.dp))
                        .border(1.dp, Color(0x59FF8A8A), RoundedCornerShape(9.dp))
                        .tjClickable(label = "删除所选") { pendingBatchDelete = true }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) { Text("删除", fontSize = 12.sp, color = Color(0xFFFF9B9B), fontWeight = FontWeight.SemiBold) }
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.height(38.dp).clip(RoundedCornerShape(9.dp)).background(Color.White)
                        .tjClickable { showExport = true }.padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) { Text("导出 PDF", fontSize = 11.5.sp, color = Ink, fontWeight = FontWeight.SemiBold) }'''
assert old_bar in s, 'bottom bar anchor missing'
s = s.replace(old_bar, new_bar, 1)

old_export = '    if (showExport) {'
assert old_export in s
dialog = '''    if (pendingBatchDelete) {
        AlertDialog(
            onDismissRequest = { pendingBatchDelete = false },
            title = { Text("删除所选 ${selected.value.size} 道题？", fontSize = 17.sp) },
            text = { Text("删除后不再出现在复习和导出里，原图文件会保留。", fontSize = 13.sp, lineHeight = 20.sp, color = Ink2) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    val ids = selected.value.toList()
                    pendingBatchDelete = false
                    selected.value = emptySet()
                    selecting = false
                    vm.deleteQuestions(ids)
                }) { Text("删除", color = Red, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { pendingBatchDelete = false }) {
                    Text("取消", color = Ink2)
                }
            }
        )
    }

'''
s = s.replace(old_export, dialog + old_export, 1)
p.write_text(s, encoding='utf-8')
print('batch delete UI added')
