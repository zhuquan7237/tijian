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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// 纸墨体系：暖象牙底 + 墨色字 + 赤陶强调
val Bg = Color(0xFFF7F4EC)
val CardBg = Color(0xFFFFFDF8)
val SurfaceAlt = Color(0xFFF1ECE1)
val Ink = Color(0xFF232220)
val Ink2 = Color(0xFF4A463F)
val Muted = Color(0xFF837C6E)
val Line = Color(0xFFE6DFD2)
val Accent = Color(0xFFB4553A)
val AccentSoft = Color(0xFFF7E5DC)
val Green = Color(0xFF4F7A5A)
val Amber = Color(0xFFA9762F)
val Red = Color(0xFFA8453A)

val CardShape = RoundedCornerShape(20.dp)
val SmallShape = RoundedCornerShape(12.dp)

@Composable
fun TijianTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Accent,
            onPrimary = Color.White,
            primaryContainer = AccentSoft,
            onPrimaryContainer = Color(0xFF6E2E1C),
            secondary = Green,
            secondaryContainer = Color(0xFFE3EDE3),
            background = Bg,
            onBackground = Ink,
            surface = CardBg,
            onSurface = Ink,
            surfaceVariant = SurfaceAlt,
            onSurfaceVariant = Muted,
            outline = Line,
            outlineVariant = Line,
            error = Red,
        ),
        typography = Typography(
            displaySmall = Typography().displaySmall.copy(
                fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.8).sp,
            ),
            headlineMedium = Typography().headlineMedium.copy(
                fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp,
            ),
            titleLarge = Typography().titleLarge.copy(
                fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp,
            ),
            bodyLarge = Typography().bodyLarge.copy(
                fontFamily = FontFamily.SansSerif, fontSize = 15.sp, lineHeight = 25.sp,
            ),
            bodyMedium = Typography().bodyMedium.copy(
                fontFamily = FontFamily.SansSerif, fontSize = 13.sp, lineHeight = 21.sp,
            ),
            labelSmall = Typography().labelSmall.copy(
                fontFamily = FontFamily.SansSerif, fontSize = 10.sp, letterSpacing = 0.8.sp,
            ),
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = if (onBack != null) 8.dp else 20.dp,
                end = 20.dp,
                top = if (onBack != null) 8.dp else 14.dp,
                bottom = 14.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).clickableNoRipple(onBack),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回", tint = Ink, modifier = Modifier.size(21.dp)) }
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = if (onBack != null) 18.sp else 31.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = if (onBack != null) (-0.2).sp else (-0.9).sp,
                color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) Text(
                subtitle, fontSize = 12.sp, lineHeight = 18.sp, color = Muted,
                modifier = Modifier.padding(top = if (onBack != null) 4.dp else 6.dp)
            )
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
        modifier = Modifier.size(38.dp).clip(CircleShape).background(CardBg)
            .border(1.dp, Line, CircleShape).clickableNoRipple(onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, label, tint = Ink2, modifier = Modifier.size(18.dp)) }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(top = 24.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, fontSize = 10.sp, letterSpacing = 1.6.sp, color = Muted, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
fun TjChip(text: String, selected: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(999.dp))
            .background(if (selected) AccentSoft else CardBg)
            .border(1.dp, if (selected) Accent.copy(alpha = 0.35f) else Line, RoundedCornerShape(999.dp))
            .clickableNoRipple(onClick)
            .padding(horizontal = 13.dp, vertical = 7.dp)
    ) {
        Text(text, fontSize = 11.5.sp, color = if (selected) Accent else Ink2, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
fun CardBox(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .shadow(10.dp, CardShape, ambientColor = Color(0x14362A1C), spotColor = Color(0x14362A1C))
            .clip(CardShape)
            .background(CardBg)
    ) { content() }
}

@Composable
fun MasteryDots(mastery: Int, size: Int = 6) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(5) { i ->
            Box(
                Modifier.size(size.dp).clip(CircleShape)
                    .background(if (i < mastery) Green else Color(0xFFE2DCCE))
            )
        }
    }
}

@Composable
fun EmptyState(title: String, hint: String, action: (@Composable () -> Unit)? = null) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 56.dp, horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Ink)
        Text(hint, fontSize = 12.sp, lineHeight = 19.sp, color = Muted, modifier = Modifier.padding(top = 8.dp))
        if (action != null) { Spacer(Modifier.height(18.dp)); action() }
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
    Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFF0EBE0)))
}
