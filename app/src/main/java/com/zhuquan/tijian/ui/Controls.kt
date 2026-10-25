package com.zhuquan.tijian.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalDensity

/** One size and shape for actions; height can grow with the system font scale. */
enum class ActionStyle { Primary, Secondary, Danger, Quiet }

@Composable
fun TjButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: ActionStyle = ActionStyle.Primary,
    loading: Boolean = false,
    onClick: () -> Unit,
) {
    val bg = when (style) {
        ActionStyle.Primary -> Accent
        ActionStyle.Secondary -> SurfaceAlt
        ActionStyle.Danger -> RedSoft
        ActionStyle.Quiet -> Color.Transparent
    }
    val fg = when (style) {
        ActionStyle.Primary -> OnAccent
        ActionStyle.Danger -> Red
        else -> Ink
    }
    val pressSource = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by pressSource.collectIsPressedAsState()
    val pressScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = androidx.compose.animation.core.tween(Motion.Fast, easing = Motion.Enter),
        label = "button-press"
    )
    Button(
        onClick = onClick, enabled = enabled && !loading,
        modifier = modifier.heightIn(min = 48.dp)
            .graphicsLayer { scaleX = pressScale; scaleY = pressScale },
        interactionSource = pressSource,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = bg, contentColor = fg,
            disabledContainerColor = SurfaceAlt, disabledContentColor = Muted,
        ),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        elevation = null,
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(18.dp), color = Muted, strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}

/** All search, form and editor inputs share a 16dp corner and readable text. */
@Composable
fun TjTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    colors: TextFieldColors = tjFieldColors(),
    shape: Shape = SmallShape,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange, modifier = modifier,
        label = label, placeholder = placeholder, singleLine = singleLine,
        minLines = minLines, maxLines = maxLines, textStyle = textStyle,
        colors = colors, shape = shape, visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions, keyboardActions = keyboardActions,
    )
}

/**
 * 全屏 Dialog 统一入口。Dialog 自己的窗口默认会把状态栏高度算进去，手机上
 * 底部操作栏会被挤出物理屏幕；模拟器没有状态栏时又完全看不出来。
 * 这里把窗口钉在物理边界，再由 safeDrawingPadding 统一处理系统栏。
 */
@Composable
fun FullScreenDialog(
    onDismissRequest: () -> Unit,
    dismissEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    // decorFitsSystemWindows = true：让系统把 Dialog 窗口裁到状态栏/导航栏之内。
    // 之前用 false + setFitInsetsTypes(0) 走边到边，但部分 OEM（小米/HyperOS）
    // 不给 Dialog 窗口派发 WindowInsets，safeDrawingPadding 拿到 0，真机上底部
    // 一排按钮就被导航栏盖住；模拟器没有导航栏所以复现不出来。
    Dialog(
        onDismissRequest = { if (dismissEnabled) onDismissRequest() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = true)
    ) {
        Column(
            // 实测部分机型连 decorFits 都不生效（底部按钮被导航栏裁掉一半），
            // 这里无条件加底部避让：insets 拿得到就用 insets，拿不到退回
            // navigation_bar_height 系统资源值，两者取大，宁可多留空白不挡按钮。
            Modifier.fillMaxSize().background(Bg).padding(bottom = navigationBarAvoidance()),
            content = content
        )
    }
}

/** 底部导航栏避让高度：insets 与系统资源值取大。 */
@Composable
private fun navigationBarAvoidance(): Dp {
    val density = LocalDensity.current
    val insetPx = WindowInsets.navigationBars.getBottom(density)
    val fallbackPx = runCatching {
        val res = android.content.res.Resources.getSystem()
        val id = res.getIdentifier("navigation_bar_height", "dimen", "android")
        if (id != 0) res.getDimensionPixelSize(id) else 0
    }.getOrDefault(0)
    return with(density) { maxOf(insetPx, fallbackPx).coerceAtLeast(0).toDp() }
}
