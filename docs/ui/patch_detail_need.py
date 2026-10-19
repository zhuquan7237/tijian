import pathlib

p = pathlib.Path('app/src/main/java/com/zhuquan/tijian/ui/HomeScreens.kt')
s = p.read_text(encoding='utf-8').replace('\r\n', '\n')

old = '''            val knowledge = parseKnowledgeTags(question.tags)'''
new = '''            val needFormulas = parseKnowledgeTags(question.needFormulas)
            val needConcepts = parseKnowledgeTags(question.needConcepts)
            val needMethods = parseKnowledgeTags(question.needMethods)
            if (needFormulas.isNotEmpty() || needConcepts.isNotEmpty() || needMethods.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                RichSection("要做出这题，需要掌握", "公式 · 概念 · 方法", Amber) {
                    Column {
                        NeedLine("公式", needFormulas, Accent)
                        NeedLine("概念", needConcepts, Green)
                        NeedLine("方法", needMethods, Amber)
                    }
                }
            }

            val knowledge = parseKnowledgeTags(question.tags)'''
assert old in s
s = s.replace(old, new, 1)

# add composable before RichSection definition
anchor = '''@Composable
fun RichSection('''
need = '''@Composable
private fun NeedLine(label: String, items: List<String>, color: Color) {
    if (items.isEmpty()) return
    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.Top) {
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
                ) { RichText(item, fontSize = 13) }
            }
        }
    }
}

@Composable
fun RichSection('''
assert anchor in s
s = s.replace(anchor, need, 1)

p.write_text(s, encoding='utf-8')
print('detail need section added')
