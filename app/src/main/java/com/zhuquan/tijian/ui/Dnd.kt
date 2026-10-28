package com.zhuquan.tijian.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 长按拖动排序（LazyColumn 通用，按 key 追踪）：
 *
 * 关键实现点（都是「拖动乱跳」的教训）：
 * - 被拖行按 **key** 而非 index 追踪：交换发生后的一两帧里 LazyColumn 布局
 *   还是旧的，按 index 查会把位移套到刚换过来的另一行上，看起来就是「乱跳」。
 *   key 在重排中不变，任何时刻都能查到被拖行自己的槽位。
 * - 位移 = 手指视口位置 − 被拖行当前槽位。布局过期的那一帧里，
 *   「旧槽位 + 位移」仍等于手指位置，行在视觉上始终钉在手指下。
 * - 交换回调按 (fromKey, toKey) 给出，由调用方在**自己的最新列表**里找索引，
 *   布局滞后导致的重复回调是幂等的（找不到或 from==to 直接无操作）。
 */
class DragDropState internal constructor(
    val listState: LazyListState,
    private val scope: CoroutineScope,
    private val onMove: (fromKey: Any, toKey: Any) -> Unit,
) {
    var draggingKey by mutableStateOf<Any?>(null)
        private set

    /** 本轮手势是否已进入拖动：行点击据此丢弃长按后紧跟的误触发 click。 */
    var clickConsumed by mutableStateOf(false)
        private set

    private var fingerStartY by mutableFloatStateOf(0f)
    private var grabOffsetY by mutableFloatStateOf(0f)
    private var dragDelta by mutableFloatStateOf(0f)

    private fun draggedInfo() =
        listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == draggingKey }

    /** 被拖行相对当前槽位的位移，使「槽位 + 位移」恒等于手指下的行顶。 */
    internal fun translationFor(itemKey: Any): Float {
        if (itemKey != draggingKey) return 0f
        val info = draggedInfo() ?: return 0f
        return (fingerStartY + dragDelta - grabOffsetY) - info.offset
    }

    internal fun onDragStart(offset: Offset) {
        val hit = listState.layoutInfo.visibleItemsInfo
            .firstOrNull { item -> offset.y.toInt() in item.offset..(item.offset + item.size) }
            ?: return
        draggingKey = hit.key
        fingerStartY = offset.y
        grabOffsetY = offset.y - hit.offset
        dragDelta = 0f
        clickConsumed = true
    }

    internal fun onDrag(offset: Offset) {
        val key = draggingKey ?: return
        val item = draggedInfo() ?: return
        dragDelta += offset.y
        val fingerY = fingerStartY + dragDelta
        val target = listState.layoutInfo.visibleItemsInfo.firstOrNull {
            it.key != key && fingerY.toInt() in it.offset..(it.offset + it.size)
        }
        if (target != null) {
            onMove(key, target.key)
        } else {
            val itemTop = fingerY - grabOffsetY
            val viewportStart = listState.layoutInfo.viewportStartOffset
            val viewportEnd = listState.layoutInfo.viewportEndOffset
            val overscroll = when {
                itemTop < viewportStart -> itemTop - viewportStart
                itemTop + item.size > viewportEnd -> itemTop + item.size - viewportEnd
                else -> 0f
            }
            if (overscroll != 0f) scope.launch { listState.scrollBy(overscroll) }
        }
    }

    internal fun onDragEnd() {
        draggingKey = null
        dragDelta = 0f
        fingerStartY = 0f
        grabOffsetY = 0f
    }

    /** 每次新手指按下时重置；由容器上的 downReset 手势调用。 */
    internal fun onNewTouchDown() { clickConsumed = false }
}

@Composable
fun rememberDragDropState(
    listState: LazyListState = rememberLazyListState(),
    onMove: (fromKey: Any, toKey: Any) -> Unit,
): DragDropState {
    val scope = rememberCoroutineScope()
    val currentOnMove by rememberUpdatedState(onMove)
    return remember(listState) { DragDropState(listState, scope) { from, to -> currentOnMove(from, to) } }
}

/** 给 LazyColumn 挂长按拖动手势；行内不用感知。onSettled 在松手时回调一次。 */
@Composable
fun Modifier.dragContainer(state: DragDropState, onSettled: () -> Unit): Modifier {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    return this
        .pointerInput(state) {
            // 每次新的按下都清掉上一轮的 click 抑制标记
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                state.onNewTouchDown()
                while (true) {
                    val event = awaitPointerEvent()
                    if (event.changes.all { !it.pressed }) break
                }
            }
        }
        .pointerInput(state) {
            detectDragGesturesAfterLongPress(
                onDragStart = { offset ->
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    state.onDragStart(offset)
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    state.onDrag(dragAmount)
                },
                onDragEnd = { state.onDragEnd(); onSettled() },
                onDragCancel = { state.onDragEnd(); onSettled() },
            )
        }
}

/** 拖动容器里的每一行都包一层：按 key 追踪拖动态的位移、轻微缩放与 z 序。 */
@Composable
fun LazyItemScope.DraggableItem(
    state: DragDropState,
    key: Any,
    content: @Composable (isDragging: Boolean) -> Unit,
) {
    val isDragging = state.draggingKey == key
    val scale by animateFloatAsState(
        targetValue = if (isDragging) 1.015f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f),
        label = "dnd-scale",
    )
    Box(
        Modifier
            .animateItem()
            .zIndex(if (isDragging) 1f else 0f)
            .graphicsLayer {
                translationY = state.translationFor(key)
                scaleX = scale; scaleY = scale
            }
    ) { content(isDragging) }
}
