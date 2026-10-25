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
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
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
 * 题笺 · 夜读台（8.9.0）/ 日间墨白（8.11.0）
 *
 * 深浅两套 token：夜读台（深石墨 + 青绿）与日间墨白（暖纸白 + 深青）。
 * 层级靠明度（Bg < CardBg < SurfaceAlt < Fill），不靠阴影。
 * WebView 端在 assets/richtext 目录的 html 里用同名 CSS 变量镜像（rt-day / 默认夜色），
 * 改色时两边一起改。
 * ------------------------------------------------------------------ */

/** 当前是否夜色：由「外观」设置 + 系统深色模式共同决定（AppRoot 写入系统值）。
 *  组合期读取（内部是两个状态位），主题切换会触发订阅它的组件重组。 */
val isNight: Boolean
    get() = when (tjAppearance.intValue) { 1 -> false; 2 -> true; else -> sysNight.value }

/** 外观偏好：0 跟随系统 / 1 浅色 / 2 深色（setting 表 appearance 键）。 */
val tjAppearance = androidx.compose.runtime.mutableIntStateOf(0)

private val sysNight = androidx.compose.runtime.mutableStateOf(false)

/** AppRoot 每次组合把系统深浅写进来。 */
internal fun updateSystemNight(dark: Boolean) { sysNight.value = dark }

private val night: Boolean get() = isNight

// ---- 夜读台（深） ----
private val DarkBg = Color(0xFF1C1E22)
private val DarkCardBg = Color(0xFF26282C)
private val DarkSurfaceAlt = Color(0xFF303236)
private val DarkFill = Color(0xFF3A3D44)
private val DarkInk = Color(0xFFE6E8EB)
private val DarkInk2 = Color(0xFFC3C8D0)
private val DarkMuted = Color(0xFF9CA3AF)
private val DarkLine = Color(0xFF3F4451)
private val DarkFieldBorder = Color(0xFF6B7280)
private val DarkAccent = Color(0xFF2DD4BF)
private val DarkAccentSoft = Color(0xFF163B38)
private val DarkAccentPressed = Color(0xFF0FB89E)
private val DarkOnAccent = Color(0xFF04201C)
private val DarkHeroBrush = Brush.linearGradient(listOf(Color(0xFF123B37), Color(0xFF1E2A2E), Color(0xFF26282C)))
private val DarkHeroLine = Color(0x332DD4BF)
private val DarkGreen = Color(0xFF34D399)
private val DarkGreenSoft = Color(0xFF1F4D3D)
private val DarkAmber = Color(0xFFFBBF24)
private val DarkAmberSoft = Color(0xFF3F2E1A)
private val DarkRed = Color(0xFFF87171)
private val DarkRedSoft = Color(0xFF3D2326)
private val DarkOnHeroMuted = Color(0xFF8B94A8)
private val DarkOnHeroKicker = Color(0xFF9CA8BD)
private val DarkBarBg = Color(0xFA1C1E22)

// ---- 日间墨白（浅）：暖纸白底 + 深青强调，白天不刺眼、夜里开深色 ----
private val LightBg = Color(0xFFF5F4EF)
private val LightCardBg = Color(0xFFFFFFFF)
private val LightSurfaceAlt = Color(0xFFEDEBE3)
private val LightFill = Color(0xFFE2DFD5)
private val LightInk = Color(0xFF23262B)
private val LightInk2 = Color(0xFF464B54)
private val LightMuted = Color(0xFF7C828C)
private val LightLine = Color(0xFFE0DED4)
private val LightFieldBorder = Color(0xFFA9ADB6)
private val LightAccent = Color(0xFF0B8A7F)
private val LightAccentSoft = Color(0xFFDCF0ED)
private val LightAccentPressed = Color(0xFF096F66)
private val LightOnAccent = Color(0xFFFFFFFF)
private val LightHeroBrush = Brush.linearGradient(listOf(Color(0xFFD8EEEA), Color(0xFFEFEDE4), Color(0xFFFFFFFF)))
private val LightHeroLine = Color(0x330B8A7F)
private val LightGreen = Color(0xFF15803D)
private val LightGreenSoft = Color(0xFFDCEFDD)
private val LightAmber = Color(0xFF9A6B1F)
private val LightAmberSoft = Color(0xFFFBF1DC)
private val LightRed = Color(0xFFC0454D)
private val LightRedSoft = Color(0xFFFBEDEE)
private val LightOnHeroMuted = Color(0xFF6E7683)
private val LightOnHeroKicker = Color(0xFF5B6472)
private val LightBarBg = Color(0xFAFFFFFF)

/** 深浅都成立的功能色（不随主题翻转）。 */
val Scrim = Color(0x99000000)

val Bg get() = if (night) DarkBg else LightBg
val CardBg get() = if (night) DarkCardBg else LightCardBg
val SurfaceAlt get() = if (night) DarkSurfaceAlt else LightSurfaceAlt
/** 深色填充：按钮、hero card、强调底色（原 Ink 深色语义）。 */
val Fill get() = if (night) DarkFill else LightFill
val Ink get() = if (night) DarkInk else LightInk
val Ink2 get() = if (night) DarkInk2 else LightInk2
val Muted get() = if (night) DarkMuted else LightMuted
/** 装饰性 hairline：只用于分组。 */
val Line get() = if (night) DarkLine else LightLine
/** 输入框/可选控件的功能边框。 */
val FieldBorder get() = if (night) DarkFieldBorder else LightFieldBorder
val Accent get() = if (night) DarkAccent else LightAccent
val AccentSoft get() = if (night) DarkAccentSoft else LightAccentSoft
val AccentPressed get() = if (night) DarkAccentPressed else LightAccentPressed
/** 强调色上的文字/图标。 */
val OnAccent get() = if (night) DarkOnAccent else LightOnAccent
/** 首页 / 知识库主卡：唯一的「光源」。 */
val HeroBrush get() = if (night) DarkHeroBrush else LightHeroBrush
val HeroLine get() = if (night) DarkHeroLine else LightHeroLine
val Green get() = if (night) DarkGreen else LightGreen
val GreenSoft get() = if (night) DarkGreenSoft else LightGreenSoft
val Amber get() = if (night) DarkAmber else LightAmber
val AmberSoft get() = if (night) DarkAmberSoft else LightAmberSoft
val Red get() = if (night) DarkRed else LightRed
val RedSoft get() = if (night) DarkRedSoft else LightRedSoft
val OnHeroMuted get() = if (night) DarkOnHeroMuted else LightOnHeroMuted
val OnHeroKicker get() = if (night) DarkOnHeroKicker else LightOnHeroKicker
/** 底部导航栏底色。 */
val BarBg get() = if (night) DarkBarBg else LightBarBg

/** 三档圆角：卡片 20 / 控件 14 / 内嵌 10。WebView 卡片（.rt-card）同为 20px。 */
val CardShape = RoundedCornerShape(20.dp)
val SmallShape = RoundedCornerShape(14.dp)
val InnerShape = RoundedCornerShape(10.dp)

/** 数字字体：等宽数字 + 收紧字距，用于计数、百分比、掌握度。 */
val NumFont = FontFamily.SansSerif

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
    val scheme = if (isNight) darkColorScheme(
        primary = Accent,
        onPrimary = OnAccent,
        primaryContainer = AccentSoft,
        onPrimaryContainer = Ink,
        secondary = Green,
        onSecondary = OnAccent,
        secondaryContainer = GreenSoft,
        onSecondaryContainer = Green,
        background = Bg,
        onBackground = Ink,
        surface = CardBg,
        onSurface = Ink,
        surfaceVariant = SurfaceAlt,
        onSurfaceVariant = Ink2,
        outline = FieldBorder,
        outlineVariant = Line,
        error = Red,
        onError = Color(0xFF2A0E10),
        errorContainer = RedSoft,
        onErrorContainer = Red,
        surfaceTint = Color.Transparent,
        surfaceDim = Bg,
        surfaceBright = SurfaceAlt,
        surfaceContainerLowest = Bg,
        surfaceContainerLow = CardBg,
        surfaceContainer = CardBg,
        surfaceContainerHigh = SurfaceAlt,
        surfaceContainerHighest = Fill,
        inverseSurface = Ink,
        inverseOnSurface = Bg,
        inversePrimary = AccentPressed,
        scrim = Color.Black,
    ) else lightColorScheme(
        primary = Accent,
        onPrimary = OnAccent,
        primaryContainer = AccentSoft,
        onPrimaryContainer = Ink,
        secondary = Green,
        onSecondary = OnAccent,
        secondaryContainer = GreenSoft,
        onSecondaryContainer = Green,
        background = Bg,
        onBackground = Ink,
        surface = CardBg,
        onSurface = Ink,
        surfaceVariant = SurfaceAlt,
        onSurfaceVariant = Ink2,
        outline = FieldBorder,
        outlineVariant = Line,
        error = Red,
        onError = Color(0xFFFFEDEF),
        errorContainer = RedSoft,
        onErrorContainer = Red,
        surfaceTint = Color.Transparent,
        surfaceDim = Bg,
        surfaceBright = SurfaceAlt,
        surfaceContainerLowest = Bg,
        surfaceContainerLow = CardBg,
        surfaceContainer = CardBg,
        surfaceContainerHigh = SurfaceAlt,
        surfaceContainerHighest = Fill,
        inverseSurface = Ink,
        inverseOnSurface = Bg,
        inversePrimary = AccentPressed,
        scrim = Color.Black,
    )
    MaterialTheme(
        shapes = androidx.compose.material3.Shapes(
            extraSmall = InnerShape, small = SmallShape, medium = SmallShape,
            large = CardShape, extraLarge = RoundedCornerShape(28.dp),
        ),
        colorScheme = scheme,
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
    focusedBorderColor = Accent,
    unfocusedBorderColor = FieldBorder,
    disabledBorderColor = Line,
    focusedLabelColor = Accent,
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
    interactionSource: MutableInteractionSource? = null,
    onClick: () -> Unit,
): Modifier {
    val indication = LocalIndication.current
    var m = this
    if (enabled) m = m.minimumInteractiveComponentSize()
    val src = interactionSource ?: remember { MutableInteractionSource() }
    return m
        .then(if (label != null) Modifier.semantics { contentDescription = label } else Modifier)
        .clickable(
            interactionSource = src,
            indication = indication,
            enabled = enabled,
            role = role,
            onClick = onClick,
        )
}

/**
 * 全局按压反馈：必须放在组件 modifier 链的**最前面**，这样缩放才能包住
 * 背景、图片和文字。配合 [tjClickable] 的 interactionSource 使用。
 */
@Composable
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.985f,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = tween(Motion.Fast, easing = Motion.Enter),
        label = "press-scale",
    )
    return this.graphicsLayer { scaleX = scale; scaleY = scale }
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
    val bg = if (filled) Accent else SurfaceAlt
    val fg = if (filled) OnAccent else tint
    val src = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .pressScale(src)
            .size(48.dp)
            .clip(CircleShape)
            .background(bg)
            .then(if (filled) Modifier else Modifier.border(1.dp, Line, CircleShape))
            .tjClickable(label = label, interactionSource = src) { onClick() },
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
            ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回", tint = Ink2, modifier = Modifier.size(22.dp)) }
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
    val src = remember { MutableInteractionSource() }
    androidx.compose.material3.FilterChip(
        selected = selected, onClick = onClick,
        interactionSource = src,
        label = { Text(text, fontSize = 13.sp, fontWeight = FontWeight.Medium) },
        shape = CircleShape,
        modifier = Modifier.pressScale(src, pressedScale = 0.96f).heightIn(min = 48.dp),
        colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
            containerColor = CardBg, labelColor = Ink2,
            selectedContainerColor = Accent, selectedLabelColor = OnAccent,
        ),
    )
}

/** 白卡：1dp 可见边框 + 极轻阴影，替代“描边糊成一片”的旧观感。 */
@Composable
fun CardBox(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
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
                    .background(if (i < mastery) Green else FieldBorder)
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
