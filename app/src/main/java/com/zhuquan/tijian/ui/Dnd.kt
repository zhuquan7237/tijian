package com.zhuquan.tijian.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
 */
class DragDropState internal constructor(
    val listState: LazyListState,
    private val scope: CoroutineScope,
    private val onMove: (Int, Int) -> Unit,
) {
    var draggingItemIndex by mutableStateOf<Int?>(null)
        private set
    private var dragDelta by mutableFloatStateOf(0f)
    private var initialOffset by mutableIntStateOf(0)

    /** 拖动行相对当前位置的位移：初始位置 + 拖动量 − 当前行位置。 */
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
            draggingItemIndex = target.index
            dragDelta += item.offset - target.offset
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

/** 给 LazyColumn 挂长按拖动手势；行内不用感知。 */
@Composable
fun Modifier.dragContainer(state: DragDropState, onSettled: () -> Unit): Modifier {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    return this.pointerInput(state) {
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

/** 拖动容器里的每一行都包一层：负责拖动态的位移、缩放与 z 序。 */
@Composable
fun LazyItemScope.DraggableItem(
    state: DragDropState,
    index: Int,
    content: @Composable (isDragging: Boolean) -> Unit,
) {
    val isDragging = state.draggingItemIndex == index
    val scale by animateFloatAsState(
        targetValue = if (isDragging) 1.02f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
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
