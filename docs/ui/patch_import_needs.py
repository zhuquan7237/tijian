import pathlib

p = pathlib.Path('app/src/main/java/com/zhuquan/tijian/ui/ImportReview.kt')
s = p.read_text(encoding='utf-8')

# state
s = s.replace('''    var type by remember { mutableStateOf(draft.type) }
    AlertDialog(''', '''    var type by remember { mutableStateOf(draft.type) }
    var formulas by remember { mutableStateOf(parseKnowledgeTags(draft.needFormulas).joinToString("，")) }
    var concepts by remember { mutableStateOf(parseKnowledgeTags(draft.needConcepts).joinToString("，")) }
    var methods by remember { mutableStateOf(parseKnowledgeTags(draft.needMethods).joinToString("，")) }
    AlertDialog(''', 1)

# fields after analysis
old_field = '''                OutlinedTextField(value = analysis, onValueChange = { analysis = it }, label = { Text("解析", fontSize = 12.sp) }, minLines = 2, textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, lineHeight = 22.sp, color = Ink), colors = tjFieldColors(), modifier = Modifier.fillMaxWidth())'''
new_field = old_field + '''
                Spacer(Modifier.height(8.dp))
                Text("要做出这题，需要掌握（用逗号分隔）", fontSize = 11.5.sp, color = Muted, modifier = Modifier.padding(top = 2.dp))
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(value = formulas, onValueChange = { formulas = it }, label = { Text("公式", fontSize = 12.sp) }, minLines = 1, textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = Ink), colors = tjFieldColors(), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = concepts, onValueChange = { concepts = it }, label = { Text("概念", fontSize = 12.sp) }, minLines = 1, textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = Ink), colors = tjFieldColors(), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = methods, onValueChange = { methods = it }, label = { Text("方法", fontSize = 12.sp) }, minLines = 1, textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = Ink), colors = tjFieldColors(), modifier = Modifier.fillMaxWidth())'''
assert old_field in s
s = s.replace(old_field, new_field, 1)

# save
s = s.replace('''                onSave(draft.copy(stem = stem, answer = answer, analysis = analysis, type = type))''',
              '''                onSave(draft.copy(
                    stem = stem, answer = answer, analysis = analysis, type = type,
                    needFormulas = tagsToJson(formulas),
                    needConcepts = tagsToJson(concepts),
                    needMethods = tagsToJson(methods)
                ))''', 1)

# helper at end of file
s += '''

/** 逗号 / 换行 / 分号分隔的知识点文本 → JSON 字符串数组。 */
private fun tagsToJson(raw: String): String {
    val arr = org.json.JSONArray()
    raw.split('，', ',', '；', ';', '\\n').map { it.trim() }.filter { it.isNotEmpty() }.forEach { arr.put(it) }
    return arr.toString()
}
'''
p.write_text(s, encoding='utf-8')
print('import edit needs fields')
