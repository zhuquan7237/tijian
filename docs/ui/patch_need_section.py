import pathlib

p = pathlib.Path('app/src/main/java/com/zhuquan/tijian/ui/HomeScreens.kt')
s = p.read_text(encoding='utf-8')

old = '''            val needFormulas = parseKnowledgeTags(question.needFormulas)
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
'''
new = '''            val needFormulas = parseKnowledgeTags(question.needFormulas)
            val needConcepts = parseKnowledgeTags(question.needConcepts)
            val needMethods = parseKnowledgeTags(question.needMethods)
            val hasNeeds = needFormulas.isNotEmpty() || needConcepts.isNotEmpty() || needMethods.isNotEmpty()
            Spacer(Modifier.height(16.dp))
            RichSection("要做出这题，需要掌握", "公式 · 概念 · 方法", Amber) {
                Column {
                    if (hasNeeds) {
                        NeedLine("公式", needFormulas, Accent)
                        NeedLine("概念", needConcepts, Green)
                        NeedLine("方法", needMethods, Amber)
                    } else {
                        Text(
                            "还缺这一块：做这道题需要哪些公式、概念和方法？",
                            fontSize = 13.sp, lineHeight = 20.sp, color = Muted
                        )
                        Spacer(Modifier.height(10.dp))
                        Box(
                            Modifier.height(42.dp).clip(RoundedCornerShape(10.dp))
                                .background(AccentSoft)
                                .tjClickable(label = "让 AI 补全知识点") { vm.fillNeeds(question.id) }
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (vm.needsBusyFor == question.id) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(Modifier.size(14.dp), color = Accent, strokeWidth = 2.dp)
                                    Text("正在分析…", fontSize = 12.sp, color = Accent, modifier = Modifier.padding(start = 7.dp))
                                }
                            } else {
                                Text("让 AI 分析这题需要掌握什么", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = Accent)
                            }
                        }
                    }
                }
            }
'''
assert old in s, 'need section anchor missing'
s = s.replace(old, new, 1)
p.write_text(s, encoding='utf-8')
print('detail need section updated')
