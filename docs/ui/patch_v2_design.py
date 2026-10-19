import pathlib

p = pathlib.Path('app/src/main/java/com/zhuquan/tijian/ui/Theme.kt')
s = p.read_text(encoding='utf-8')
pairs = [
    ('val Ink = Color(0xFF12161C)', 'val Ink = Color(0xFF202733)'),
    ('val Ink2 = Color(0xFF37414D)', 'val Ink2 = Color(0xFF46505F)'),
    ('val Muted = Color(0xFF5A6675)', 'val Muted = Color(0xFF596372)'),
    ('val Line = Color(0xFFDFE4EB)', 'val Line = Color(0xFFE0E4EA)'),
    ('val Accent = Color(0xFF2F4BD8)', 'val Accent = Color(0xFF355CCE)'),
    ('val AccentSoft = Color(0xFFEDF0FE)', 'val AccentSoft = Color(0xFFEDF2FF)'),
    ('Color(0xFF243CAE)', 'Color(0xFF2449AE)'),
]
for a, b in pairs:
    s = s.replace(a, b, 1)
s = s.replace(
    'Text(text, fontSize = 15.sp, lineHeight = 22.sp, color = Ink, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp)',
    'Text(text, fontSize = 17.sp, lineHeight = 25.sp, color = Ink, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp)'
)
p.write_text(s, encoding='utf-8')
print('theme updated')

h = pathlib.Path('app/src/main/java/com/zhuquan/tijian/ui/HomeScreens.kt')
t = h.read_text(encoding='utf-8').replace('\r\n', '\n')


def rep(a, b):
    global t
    assert a in t, "NOT FOUND " + a[:80]
    t = t.replace(a, b, 1)


rep('.clip(RoundedCornerShape(20.dp)).background(Ink)', '.clip(RoundedCornerShape(16.dp)).background(Color(0xFF252F40))')
rep('Text("$due", fontSize = 52.sp, lineHeight = 54.sp, fontWeight = FontWeight.Light, color = Color.White, letterSpacing = (-2).sp)',
    'Text("$due", fontSize = 40.sp, lineHeight = 44.sp, fontWeight = FontWeight.SemiBold, color = Color.White, letterSpacing = (-1.2).sp)')
rep('''                .background(if (due > 0) Accent else Color(0xFF2A2F39))
                .tjClickable(label = if (due > 0) "开始复习" else "查看全部错题") { onClick() },''',
    '''                .background(if (due > 0) AccentSoft else Color(0xFF3A4454))
                .tjClickable(label = if (due > 0) "开始复习" else "查看全部错题") { onClick() },''')
rep('''                fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White''',
    '''                fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                color = if (due > 0) Color(0xFF2449AE) else Color.White''')

old_loop = '''            Column(
                Modifier.fillMaxWidth().padding(horizontal = Space.page),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SUBJECT_ROOTS.forEach { subject ->
                    val children = vm.categories.filter { it.subjectGroup() == subject }
                    SubjectRow(
                        name = subject,
                        count = children.sumOf { it.count },
                        mastered = children.sumOf { it.mastered },
                        onClick = { vm.go(Route.Subject(subject)) }
                    )
                }
            }'''
new_loop = '''            Column(
                Modifier.fillMaxWidth().padding(horizontal = Space.page),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val majors = SUBJECT_ROOTS.dropLast(1)
                majors.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { subject ->
                            val children = vm.categories.filter { it.subjectGroup() == subject }
                            SubjectTile(
                                name = subject,
                                count = children.sumOf { it.count },
                                mastered = children.sumOf { it.mastered },
                                modifier = Modifier.weight(1f),
                                onClick = { vm.go(Route.Subject(subject)) }
                            )
                        }
                    }
                }
                val other = SUBJECT_ROOTS.last()
                val otherChildren = vm.categories.filter { it.subjectGroup() == other }
                SubjectWideRow(
                    name = other,
                    count = otherChildren.sumOf { it.count },
                    mastered = otherChildren.sumOf { it.mastered },
                    onClick = { vm.go(Route.Subject(other)) }
                )
            }'''
assert old_loop in t
t = t.replace(old_loop, new_loop, 1)

start = t.index('@Composable\nprivate fun SubjectRow(')
end = t.index('@Composable\nfun CategoryScreen(')
new_impl = '''@Composable
private fun SubjectTile(name: String, count: Int, mastered: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val hasQuestions = count > 0
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(CardBg)
            .border(1.dp, Line, RoundedCornerShape(12.dp))
            .bounceClick(label = name, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                subjectIcon(name), null,
                tint = if (hasQuestions) Accent else Muted,
                modifier = Modifier.size(18.dp)
            )
            Text(
                name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                color = if (hasQuestions) Ink else Ink2,
                modifier = Modifier.padding(start = 7.dp)
            )
            Spacer(Modifier.weight(1f))
            Text(
                if (hasQuestions) "$count 道" else "0",
                fontSize = 12.sp, color = Muted
            )
        }
        Spacer(Modifier.height(9.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(2.dp))
                    .background(if (hasQuestions) SurfaceAlt else Color(0xFFF1F3F6))
            ) {
                if (hasQuestions) {
                    Box(
                        Modifier.fillMaxWidth((mastered.toFloat() / count).coerceIn(0f, 1f))
                            .height(3.dp).background(Green)
                    )
                }
            }
            Text(
                if (hasQuestions) "$mastered/$count" else "暂无",
                fontSize = 11.sp, color = Muted,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

@Composable
private fun SubjectWideRow(name: String, count: Int, mastered: Int, onClick: () -> Unit) {
    val hasQuestions = count > 0
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardBg)
            .border(1.dp, Line, RoundedCornerShape(12.dp))
            .bounceClick(label = name, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            subjectIcon(name), null,
            tint = if (hasQuestions) Accent else Muted,
            modifier = Modifier.size(19.dp)
        )
        Text(
            name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            color = if (hasQuestions) Ink else Ink2,
            modifier = Modifier.padding(start = 8.dp)
        )
        Text(
            if (hasQuestions) "$count 道" else "暂无错题",
            fontSize = 12.sp, color = Muted, modifier = Modifier.padding(start = 8.dp)
        )
        Spacer(Modifier.weight(1f))
        if (hasQuestions) {
            Text("已掌握 $mastered / $count", fontSize = 11.5.sp, color = Ink2)
            Spacer(Modifier.width(6.dp))
        }
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight, null,
            tint = Color(0xFF98A3B3), modifier = Modifier.size(20.dp)
        )
    }
}

'''
t = t[:start] + new_impl + t[end:]
h.write_text(t, encoding='utf-8')
print('subjects regridded')
