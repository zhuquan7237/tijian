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
import androidx.compose.runtime.mutableIntStateOf
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
 * 长按拖动排序（LazyColumn 通用）：
 * - 长按行进入拖动态（带触感反馈），拖过相邻行中点即交换；
 * - 拖到列表边缘自动滚动；
 * - 松手回调 onSettled，由调用方把新顺序落库。
 *
 * 位移只用「原始手指拖动量」计算，交换后不做补偿修正：
 * 行的新槽位由列表重新布局给出，translation = 手指位置 − 新槽位。
 */
class DragDropState internal constructor(
    val listState: LazyListState,
    private val scope: CoroutineScope,
    private val onMove: (Int, Int) -> Unit,
) {
    var draggingItemIndex by mutableStateOf<Int?>(null)
        private set

    /** 本轮手势是否已进入拖动：行点击据此丢弃长按后紧跟的误触发 click。 */
    var clickConsumed by mutableStateOf(false)
        private set

    private var dragDelta by mutableFloatStateOf(0f)
    private var initialOffset by mutableIntStateOf(0)

    /** 拖动行相对当前槽位的位移：手指位置（初始槽位 + 拖动量）− 当前行槽位。 */
    internal fun translationFor(itemOffset: Int, itemIndex: Int): Float {
        if (itemIndex != draggingItemIndex) return 0f
        return initialOffset + dragDelta - itemOffset
    }

    internal fun onDragStart(offset: Offset) {
        listState.layoutInfo.visibleItemsInfo
            .firstOrNull { item -> offset.y.toInt() in item.offset..(item.offset + item.size) }
            ?.also {
                draggingItemIndex = it.index
                initialOffset = it.offset
                dragDelta = 0f
                clickConsumed = true
            }
    }

    internal fun onDrag(offset: Offset) {
        val dragged = draggingItemIndex ?: return
        dragDelta += offset.y
        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == dragged } ?: return
        val start = item.offset + dragDelta
        val middle = start + item.size / 2f
        val target = listState.layoutInfo.visibleItemsInfo.firstOrNull {
            it.index != dragged && middle.toInt() in it.offset..(it.offset + it.size)
        }
        if (target != null) {
            onMove(dragged, target.index)
            // 不调整 dragDelta：交换后列表重排，target.index 处的槽位就是被拖行的新位置，
            // translationFor 会用新槽位重新对齐手指。
            draggingItemIndex = target.index
        } else {
            val viewportStart = listState.layoutInfo.viewportStartOffset
            val viewportEnd = listState.layoutInfo.viewportEndOffset
            val overscroll = when {
                start < viewportStart -> start - viewportStart
                start + item.size > viewportEnd -> start + item.size - viewportEnd
                else -> 0f
            }
            if (overscroll != 0f) scope.launch { listState.scrollBy(overscroll) }
        }
    }

    internal fun onDragEnd() {
        draggingItemIndex = null
        dragDelta = 0f
        initialOffset = 0
    }

    /** 每次新手指按下时重置；由容器上的 downReset 手势调用。 */
    internal fun onNewTouchDown() { clickConsumed = false }
}

@Composable
fun rememberDragDropState(
    listState: LazyListState = rememberLazyListState(),
    onMove: (Int, Int) -> Unit,
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
                // 等本轮手势结束再退出，保证标记只在下一轮按下时重置
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

/** 拖动容器里的每一行都包一层：负责拖动态的位移、轻微缩放与 z 序。 */
@Composable
fun LazyItemScope.DraggableItem(
    state: DragDropState,
    index: Int,
    content: @Composable (isDragging: Boolean) -> Unit,
) {
    val isDragging = state.draggingItemIndex == index
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
                val info = state.listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
                translationY = if (isDragging && info != null) {
                    state.translationFor(info.offset, index)
                } else 0f
                scaleX = scale; scaleY = scale
            }
    ) { content(isDragging) }
}
