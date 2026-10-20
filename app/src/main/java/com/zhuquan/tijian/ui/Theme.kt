package com.zhuquan.tijian.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/* ------------------------------------------------------------------
 * 题笺 · Neutral rounded（8.1.0）
 *
 * 中性白底 + 石墨主操作 + 靛蓝状态强调。层级靠字号/字重/留白，不靠
 * 装饰符号；所有正文 ≥ 7:1，次要文字 ≥ 4.5:1，功能边框 ≥ 3:1。
 * 实测值见 docs/ui/contrast.py 与 docs/ui/DESIGN-0.5.md。
 * ------------------------------------------------------------------ */

val Bg = Color(0xFFF9F9F9)
val CardBg = Color(0xFFFFFFFF)
val SurfaceAlt = Color(0xFFF0F0F0)
val Ink = Color(0xFF212121)
val Ink2 = Color(0xFF444444)
val Muted = Color(0xFF606060)
/** 装饰性 hairline：只用于分组，不承担可点击边界。 */
val Line = Color(0xFFE5E5E5)
/** 输入框/可选控件的功能边框：保持可辨识的中性灰描边。 */
val FieldBorder = Color(0xFF767676)
val Accent = Color(0xFF355CCE)
val AccentSoft = Color(0xFFEDF2FF)
val AccentPressed = Color(0xFF2449AE)
val Green = Color(0xFF2F7D63)
val GreenSoft = Color(0xFFE8F3EE)
val Amber = Color(0xFF9A6B1F)
val AmberSoft = Color(0xFFFBF1DC)
val Red = Color(0xFFB83C45)
val RedSoft = Color(0xFFFBEBEC)
val OnHeroMuted = Color(0xFFC3CBDA)
val OnHeroKicker = Color(0xFFAEB8C8)

val CardShape = RoundedCornerShape(24.dp)
val SmallShape = RoundedCornerShape(16.dp)
val InnerShape = RoundedCornerShape(12.dp)

/** 4/8 栅格：同一屏只用这几档间距。 */
object Space {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    /** 页面左右统一内边距。 */
    val page = 20.dp
}

@Composable
fun TijianTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        shapes = androidx.compose.material3.Shapes(
            extraSmall = InnerShape, small = SmallShape, medium = SmallShape,
            large = CardShape, extraLarge = RoundedCornerShape(28.dp),
        ),
        colorScheme = lightColorScheme(
            primary = Ink,
            onPrimary = Color.White,
            primaryContainer = AccentSoft,
            onPrimaryContainer = Ink,
            secondary = Green,
            onSecondary = Color.White,
            secondaryContainer = GreenSoft,
            onSecondaryContainer = Color(0xFF1D4F3F),
            background = Bg,
            onBackground = Ink,
            surface = CardBg,
            onSurface = Ink,
            surfaceVariant = SurfaceAlt,
            onSurfaceVariant = Ink2,
            outline = FieldBorder,
            outlineVariant = Line,
            error = Red,
            onError = Color.White,
            errorContainer = RedSoft,
            onErrorContainer = Color(0xFF7A1F26),
        ),
        typography = Typography(
            displaySmall = Typography().displaySmall.copy(
                fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold,
                fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = (-0.9).sp,
            ),
            headlineMedium = Typography().headlineMedium.copy(
                fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold,
                fontSize = 22.sp, lineHeight = 30.sp, letterSpacing = (-0.4).sp,
            ),
            titleLarge = Typography().titleLarge.copy(
                fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp, lineHeight = 24.sp, letterSpacing = (-0.2).sp,
            ),
            bodyLarge = Typography().bodyLarge.copy(
                fontFamily = FontFamily.SansSerif, fontSize = 15.sp, lineHeight = 25.sp,
            ),
            bodyMedium = Typography().bodyMedium.copy(
                fontFamily = FontFamily.SansSerif, fontSize = 13.sp, lineHeight = 21.sp,
            ),
            labelSmall = Typography().labelSmall.copy(
                fontFamily = FontFamily.SansSerif, fontSize = 11.sp,
                lineHeight = 16.sp, letterSpacing = 0.3.sp,
            ),
        ),
        content = content
    )
}

/** Shared text-field colors: readable labels, a visible border and near-black input text. */
@Composable
fun tjFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Ink,
    unfocusedTextColor = Ink,
    focusedContainerColor = CardBg,
    unfocusedContainerColor = CardBg,
    cursorColor = Accent,
    focusedBorderColor = Ink,
    unfocusedBorderColor = FieldBorder,
    disabledBorderColor = Line,
    focusedLabelColor = Ink,
    unfocusedLabelColor = Ink2,
    focusedPlaceholderColor = Muted,
    unfocusedPlaceholderColor = Muted,
    focusedLeadingIconColor = Ink2,
    unfocusedLeadingIconColor = Muted,
    focusedTrailingIconColor = Ink2,
    unfocusedTrailingIconColor = Muted,
)

/**
 * 所有可点元素的统一入口：真实涟漪 + 无障碍 role + 可读标签。
 * 视觉尺寸可以小（例如 36dp 的 chip），但 `minimumInteractiveComponentSize`
 * 保证实际触控区域不小于 48dp —— 这是“看得见却点不中”的根因修复。
 */
@Composable
fun Modifier.tjClickable(
    label: String? = null,
    role: Role = Role.Button,
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier {
    val indication = LocalIndication.current
    var m = this
    if (enabled) m = m.minimumInteractiveComponentSize()
    return m
        .then(if (label != null) Modifier.semantics { contentDescription = label } else Modifier)
        .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = indication,
            enabled = enabled,
            role = role,
            onClick = onClick,
        )
}

/**
 * 底部选择面板里的长列表滚到边缘后，继续拖动会把整个面板一起拖下去。
 * 这里把子列表未消费的纵向滚动全部吃掉，面板只能通过关闭按钮/点遮罩退出。
 */
private val consumeSheetOverscroll = object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
    override fun onPostScroll(
        consumed: androidx.compose.ui.geometry.Offset,
        available: androidx.compose.ui.geometry.Offset,
        source: androidx.compose.ui.input.nestedscroll.NestedScrollSource,
    ): androidx.compose.ui.geometry.Offset =
        androidx.compose.ui.geometry.Offset(0f, available.y)
}

fun Modifier.blockSheetDrag(): Modifier =
    this.nestedScroll(consumeSheetOverscroll)

/** 卡片式压缩反馈：用于整块卡片，已有足够大的触控面积。 */
@Composable
fun Modifier.bounceClick(
    label: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    // 按下要“立刻有反应”：0.95 的压缩 + 高刚度弹簧，松手回弹。
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = if (pressed) spring(dampingRatio = 0.55f, stiffness = 900f)
        else spring(dampingRatio = 0.65f, stiffness = 420f),
        label = "press"
    )
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .then(if (label != null) Modifier.semantics { contentDescription = label } else Modifier)
        .clickable(interactionSource = src, indication = LocalIndication.current, enabled = enabled, role = Role.Button) {
            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
            onClick()
        }
}

/** 圆角图标按钮：视觉与触控统一 48dp。 */
@Composable
fun IconCircle(
    icon: ImageVector,
    label: String,
    tint: Color = Ink2,
    filled: Boolean = false,
    onClick: () -> Unit,
) {
    val bg = if (filled) Ink else SurfaceAlt
    val fg = if (filled) Color.White else tint
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(bg)
            .then(if (filled) Modifier else Modifier.border(1.dp, Line, CircleShape))
            .tjClickable(label = label) { onClick() },
        contentAlignment = Alignment.Center
    ) { Icon(icon, label, tint = fg, modifier = Modifier.size(20.dp)) }
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
                start = if (onBack != null) 10.dp else Space.page,
                end = Space.page,
                top = if (onBack != null) 6.dp else 12.dp,
                bottom = if (onBack != null) 10.dp else 12.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            Box(
                modifier = Modifier.size(48.dp).clip(CircleShape).tjClickable(label = "返回") { onBack() },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回", tint = Ink, modifier = Modifier.size(22.dp)) }
        }
        Column(Modifier.weight(1f).padding(start = if (onBack != null) 4.dp else 0.dp)) {
            Text(
                title,
                fontSize = if (onBack != null) 19.sp else 26.sp,
                lineHeight = if (onBack != null) 26.sp else 34.sp,
                fontWeight = if (onBack != null) FontWeight.SemiBold else FontWeight.Bold,
                letterSpacing = if (onBack != null) (-0.3).sp else (-1.0).sp,
                color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) Text(
                subtitle, fontSize = 12.sp, lineHeight = 18.sp, color = Muted,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = if (onBack != null) 3.dp else 5.dp)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) { actions() }
    }
}

/** 区块标题：15sp 半粗，右侧可选计数。中文不做夸张字距。 */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(top = Space.xl, bottom = Space.m),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, fontSize = 17.sp, lineHeight = 25.sp, color = Ink, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp)
        Spacer(Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** 筛选 chip：选中=实心靛蓝白字，未选中=白底可辨边框。 */
@Composable
fun TjChip(text: String, selected: Boolean = false, onClick: () -> Unit) {
    androidx.compose.material3.FilterChip(
        selected = selected, onClick = onClick,
        label = { Text(text, fontSize = 13.sp, fontWeight = FontWeight.Medium) },
        shape = CircleShape,
        modifier = Modifier.heightIn(min = 48.dp),
        colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
            containerColor = CardBg, labelColor = Ink2,
            selectedContainerColor = Ink, selectedLabelColor = Color.White,
        ),
    )
}

/** 白卡：1dp 可见边框 + 极轻阴影，替代“描边糊成一片”的旧观感。 */
@Composable
fun CardBox(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .shadow(2.dp, CardShape, ambientColor = Color(0x0F0F1B3D), spotColor = Color(0x0F0F1B3D))
            .clip(CardShape)
            .background(CardBg)
            .border(1.dp, Line, CardShape)
    ) { content() }
}

@Composable
fun MasteryDots(mastery: Int, size: Int = 7) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(5) { i ->
            Box(
                Modifier.size(size.dp).clip(CircleShape)
                    .background(if (i < mastery) Green else Color(0xFFD5DCE8))
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
        Text(hint, fontSize = 13.sp, lineHeight = 21.sp, color = Muted,
            modifier = Modifier.padding(top = 6.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (action != null) { Spacer(Modifier.height(18.dp)); action() }
    }
}

/** 状态标签：用色块 + 文本双通道表达，不只靠颜色。 */
@Composable
fun StatusTag(text: String, color: Color, bg: Color) {
    Box(
        Modifier.clip(RoundedCornerShape(6.dp)).background(bg)
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) { Text(text, fontSize = 11.sp, lineHeight = 14.sp, color = color, fontWeight = FontWeight.SemiBold) }
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
    Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
}
