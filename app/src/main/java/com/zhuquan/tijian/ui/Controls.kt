package com.zhuquan.tijian.ui

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
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.text.KeyboardOptions

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
