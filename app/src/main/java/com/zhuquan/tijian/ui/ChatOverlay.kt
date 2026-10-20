package com.zhuquan.tijian.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * 全局追问入口：可拖动悬浮球 + 基于当前题目的对话面板。
 *
 * 面板直接挂在 AppRoot 的 Box 里（不是 Dialog），复用它已经留好的
 * WindowInsets.safeDrawing，键盘 / 导航栏都不会盖住输入框。
 */
@Composable
fun ChatOverlay(vm: AppVm) {
    if (vm.chatOpen) ChatPanel(vm) else ChatFab(vm)
}

@Composable
private fun ChatFab(vm: AppVm) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val size = 56.dp
        val sizePx = with(density) { size.toPx() }
        val maxW = constraints.maxWidth.toFloat()
        val maxH = constraints.maxHeight.toFloat()
        var pos by remember {
            mutableStateOf(
                IntOffset(
                    (maxW - sizePx - 18.dp.value * density.density).toInt(),
                    (maxH * 0.58f).toInt()
                )
            )
        }
        Box(
            Modifier
                .offset { pos }
                .size(size)
                .shadow(10.dp, CircleShape, ambientColor = Color(0x33000000), spotColor = Color(0x33000000))
                .clip(CircleShape)
                .background(Accent)
                .pointerInput(maxW, maxH, sizePx) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        pos = IntOffset(
                            (pos.x + drag.x).coerceIn(0f, maxW - sizePx).toInt(),
                            (pos.y + drag.y).coerceIn(0f, maxH - sizePx).toInt()
                        )
                    }
                }
                .pointerInput(Unit) { detectTapGestures { vm.openChat() } },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Forum, "追问 AI", tint = Color.White, modifier = Modifier.size(24.dp))
            if (vm.chatQuestion != null) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 6.dp)
                        .size(8.dp).clip(CircleShape).background(Color(0xFF8CFFD9))
                )
            }
        }
    }
}

@Composable
private fun ChatPanel(vm: AppVm) {
    val q = vm.chatQuestion
    var input by remember { mutableStateOf("") }
    val scroll = rememberScrollState()
    val hasThread = vm.chatMessages.isNotEmpty() || vm.chatBusy || vm.chatError != null

    LaunchedEffect(vm.chatMessages.size, vm.chatBusy) {
        if (hasThread) {
            repeat(5) {
                delay(90)
                scroll.animateScrollTo(scroll.maxValue)
            }
        }
    }

    BoxWithConstraints(
        Modifier.fillMaxSize()
            .background(Color(0x5C000000))
            .pointerInput(Unit) { detectTapGestures { vm.closeChat() } }
    ) {
        val panelMax = (maxHeight - 88.dp).coerceAtLeast(320.dp)
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .then(if (hasThread) Modifier.height(panelMax) else Modifier.wrapContentHeight())
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(CardBg)
                .pointerInput(Unit) { detectTapGestures { } }
        ) {
            ChatHeader(vm, q)

            QuestionContext(q)

            Box(Modifier.fillMaxWidth().height(1.dp).background(Line))

            if (hasThread) {
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    vm.chatMessages.forEach { (role, text) ->
                        if (role == "user") UserBubble(text) else AiBubble(text)
                    }
                    if (vm.chatBusy) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(16.dp), color = Accent, strokeWidth = 2.dp)
                            Text("正在结合你的掌握情况思考…", fontSize = 12.sp, color = Muted, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                    vm.chatError?.let { Text(it, fontSize = 12.sp, lineHeight = 18.sp, color = Red) }
                    Spacer(Modifier.height(2.dp))
                }
            } else {
                ChatHints(q) { input = it }
            }

            if (vm.chatQuote.isNotBlank()) {
                QuoteBlock(vm.chatQuote) { vm.updateChatQuote("") }
            }

            QuickChips(enabled = !vm.chatBusy) { vm.sendChat(it) }

            ChatInput(
                input = input,
                busy = vm.chatBusy,
                onInput = { input = it },
                onSend = {
                    val text = input
                    if (text.isNotBlank() && !vm.chatBusy) {
                        input = ""
                        vm.sendChat(text)
                    }
                }
            )
        }
    }
}

@Composable
private fun ChatHeader(vm: AppVm, q: com.zhuquan.tijian.data.QuestionRow?) {
    Row(
        Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("追问这道题", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text(
                buildString {
                    append(q?.type?.ifBlank { "题目" } ?: "未选择题")
                    q?.categoryName?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
                    q?.let { append(" · 掌握度 ").append(it.mastery).append("/5") }
                },
                fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 2.dp)
            )
        }
        if (vm.chatMessages.isNotEmpty()) {
            Box(
                Modifier.size(44.dp).clip(CircleShape)
                    .tjClickable(label = "清空这道题的对话") { vm.clearChat() },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Rounded.DeleteOutline, null, tint = Muted, modifier = Modifier.size(20.dp)) }
        }
        Box(
            Modifier.size(44.dp).clip(CircleShape)
                .tjClickable(label = "关闭") { vm.closeChat() },
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Rounded.Close, null, tint = Ink2, modifier = Modifier.size(22.dp)) }
    }
}

/** 当前对话属于哪道题：题干按渲染结果展示，最多 4 行，超出裁掉。 */
@Composable
private fun QuestionContext(q: com.zhuquan.tijian.data.QuestionRow?) {
    val stem = q?.stem?.trim().orEmpty()
    if (stem.isBlank()) return
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(10.dp)).background(SurfaceAlt)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text("当前题目", fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = Muted)
        Box(
            Modifier.fillMaxWidth().heightIn(max = 96.dp).clipToBounds().padding(top = 4.dp)
        ) {
            RichText(stem, fontSize = 13, selectable = false, interactive = false)
        }
    }
}

@Composable
private fun UserBubble(text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Box(
            Modifier.widthIn(max = 310.dp)
                .clip(RoundedCornerShape(14.dp, 14.dp, 4.dp, 14.dp))
                .background(AccentSoft)
                .padding(horizontal = 12.dp, vertical = 9.dp)
        ) {
            RichText(text, fontSize = 14, selectable = false, interactive = false)
        }
    }
}

@Composable
private fun AiBubble(text: String) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp, 14.dp, 14.dp, 4.dp))
            .background(CardBg)
            .border(1.dp, Line, RoundedCornerShape(14.dp, 14.dp, 14.dp, 4.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text("题笺 AI", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Accent)
        Spacer(Modifier.height(5.dp))
        RichText(text, fontSize = 14, selectable = false, interactive = false)
    }
}

@Composable
private fun ChatHints(q: com.zhuquan.tijian.data.QuestionRow?, onPick: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(
            if (q == null) "先打开一道题，再来追问。" else "可以问解析里的任何一步，也可以长按解析中的文字选择「追问」。",
            fontSize = 13.sp, lineHeight = 21.sp, color = Ink2
        )
        Spacer(Modifier.height(10.dp))
        listOf(
            "这一步为什么要这样变形？",
            "这道题用到哪些公式和概念？",
            "用更简单的方法再讲一遍",
            "我卡在哪一步？帮我定位薄弱点"
        ).forEach { hint ->
            Box(
                Modifier.padding(bottom = 7.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(SurfaceAlt)
                    .tjClickable(label = hint) { onPick(hint) }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) { Text(hint, fontSize = 12.5.sp, color = Ink2) }
        }
    }
}

/** 输入框上方的快捷追问：点一下直接发送。 */
@Composable
private fun QuickChips(enabled: Boolean, onSend: (String) -> Unit) {
    val chips = listOf(
        "再讲细一点",
        "这步为什么成立？",
        "用到的公式列一下",
        "有没有更快的解法？"
    )
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        chips.forEach { chip ->
            Box(
                Modifier.clip(RoundedCornerShape(999.dp))
                    .background(AccentSoft)
                    .tjClickable(enabled = enabled, label = chip) { onSend(chip) }
                    .padding(horizontal = 11.dp, vertical = 7.dp)
            ) { Text(chip, fontSize = 11.5.sp, color = Accent, maxLines = 1) }
        }
    }
}

/** 引用块：保留解析原本的排版（公式、表格），最多 150dp，可滚动。 */
@Composable
private fun QuoteBlock(quote: String, onClear: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(AccentSoft)
            .padding(start = 10.dp, top = 6.dp, bottom = 8.dp, end = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("引用解析", fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = Accent)
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.size(32.dp).clip(CircleShape)
                    .tjClickable(label = "取消引用") { onClear() },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Rounded.Close, null, tint = Muted, modifier = Modifier.size(16.dp)) }
        }
        Box(Modifier.fillMaxWidth().heightIn(max = 150.dp).verticalScroll(rememberScrollState())) {
            RichText(quote, fontSize = 13, selectable = false, interactive = false)
        }
    }
}

@Composable
private fun ChatInput(input: String, busy: Boolean, onInput: (String) -> Unit, onSend: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 10.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        OutlinedTextField(
            value = input,
            onValueChange = onInput,
            placeholder = { Text("继续追问这道题…", fontSize = 13.sp, color = Muted) },
            maxLines = 4,
            colors = tjFieldColors(),
            shape = RoundedCornerShape(14.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSend() }),
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.size(52.dp).clip(RoundedCornerShape(14.dp))
                .background(if (input.isBlank() || busy) SurfaceAlt else Accent)
                .tjClickable(enabled = input.isNotBlank() && !busy, label = "发送") { onSend() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.AutoMirrored.Rounded.Send, "发送",
                tint = if (input.isBlank() || busy) Muted else Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
