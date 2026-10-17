package com.zhuquan.tijian.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Ink = Color(0xFF141418)
val Ink2 = Color(0xFF3C3C42)
val Muted = Color(0xFF8A8A90)
val Line = Color(0xFFE7E7E1)
val Bg = Color(0xFFFAFAF7)
val CardBg = Color(0xFFFFFFFF)
val Accent = Color(0xFF4457D6)
val AccentSoft = Color(0xFFEEF0FC)
val Green = Color(0xFF2E7D5B)
val Amber = Color(0xFFB0741F)
val Red = Color(0xFFC0453B)

@Composable
fun TijianTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Accent,
            onPrimary = Color.White,
            background = Bg,
            surface = CardBg,
            onSurface = Ink,
            outline = Line,
            secondary = Green,
            error = Red,
        ),
        typography = Typography(
            bodyLarge = Typography().bodyLarge.copy(fontFamily = FontFamily.SansSerif, color = Ink),
            bodyMedium = Typography().bodyMedium.copy(fontFamily = FontFamily.SansSerif, color = Ink2),
            titleLarge = Typography().titleLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp),
        ),
        content = content
    )
}

@Composable
fun TjTopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = if (onBack != null) 6.dp else 18.dp, end = 18.dp, top = 10.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            Box(
                modifier = Modifier.size(38.dp).clip(CircleShape).clickableNoRipple(onBack),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回", tint = Ink) }
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = if (onBack != null) 17.sp else 27.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, fontSize = 11.5.sp, color = Muted, modifier = Modifier.padding(top = 4.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) { actions() }
    }
}

fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = this.clickable(
    interactionSource = androidx.compose.foundation.interaction.MutableInteractionSource(),
    indication = null,
    onClick = onClick
)

@Composable
fun IconCircle(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(34.dp).clip(CircleShape).background(CardBg)
            .border(1.dp, Line, CircleShape).clickableNoRipple(onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, label, tint = Ink2, modifier = Modifier.size(17.dp)) }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(top = 20.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, fontSize = 10.sp, letterSpacing = 1.2.sp, color = Muted, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
fun TjChip(text: String, selected: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(999.dp))
            .background(if (selected) Ink else Color.White)
            .border(1.dp, if (selected) Ink else Line, RoundedCornerShape(999.dp))
            .clickableNoRipple(onClick)
            .padding(horizontal = 11.dp, vertical = 6.dp)
    ) {
        Text(text, fontSize = 11.sp, color = if (selected) Color.White else Ink2, fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal)
    }
}

@Composable
fun CardBox(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(15.dp)).background(CardBg).border(1.dp, Line, RoundedCornerShape(15.dp))
    ) { content() }
}

@Composable
fun MasteryDots(mastery: Int, size: Int = 5) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(5) { i ->
            Box(
                Modifier.size(size.dp).clip(CircleShape)
                    .background(if (i < mastery) Green else Color(0xFFDEDED8))
            )
        }
    }
}

@Composable
fun EmptyState(title: String, hint: String, action: (@Composable () -> Unit)? = null) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 60.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink)
        Text(hint, fontSize = 11.5.sp, color = Muted, modifier = Modifier.padding(top = 6.dp))
        if (action != null) { Spacer(Modifier.height(14.dp)); action() }
    }
}

fun cleanMath(input: String): String {
    var t = input.replace("$", "")
    val bs = 0x5C.toChar().toString()
    listOf("ln", "log", "sin", "cos", "tan").forEach { t = t.replace(bs + it, it) }
    t = t.replace(bs + "ge", "≥").replace(bs + "le", "≤").replace(bs + "infty", "∞")
        .replace(bs + "cup", "∪").replace(bs + "cap", "∩").replace(bs + "pm", "±")
        .replace(bs + "times", "×").replace(bs + "cdot", "·").replace(bs + "sqrt", "√")
        .replace(bs + "Rightarrow", "⇒").replace(bs + "rightarrow", "→")
    val pattern = bs + bs + "frac" + bs + "{" + "([^{}]*)" + bs + "}" + bs + "{" + "([^{}]*)" + bs + "}"
    t = Regex(pattern).replace(t) { m -> m.groupValues[1] + "/" + m.groupValues[2] }
    return t
}

fun parseOptions(json: String): List<String> = try {
    val arr = org.json.JSONArray(json)
    (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
} catch (e: Exception) { emptyList() }

@Composable
fun DividerSoft() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFF1F1EB)))
}
