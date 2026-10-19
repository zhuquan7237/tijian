package com.zhuquan.tijian.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * 全局追问入口：一个可以拖动的悬浮球 + 基于当前题目的对话面板。
 * 悬浮球不挡住内容（默认贴右下、避让底部导航），拖动后的位置在本进程内保留。
 */
@Composable
fun ChatOverlay(vm: AppVm) {
    if (vm.chatOpen) {
        ChatPanel(vm)
    } else {
        ChatFab(vm)
    }
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
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf("") }

    LaunchedEffect(vm.chatMessages.size, vm.chatBusy) {
        if (vm.chatMessages.isNotEmpty()) {
            listState.animateScrollToItem((vm.chatMessages.size - 1).coerceAtLeast(0))
        }
    }

    Dialog(
        onDismissRequest = { vm.closeChat() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(Modifier.fillMaxSize().background(Color(0x66000000))) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(if (vm.chatMessages.isEmpty() && !vm.chatBusy) 430.dp else 640.dp)
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(CardBg)
                    .padding(bottom = systemNavBarHeight())
                    .imePadding()
            ) {
                // header
                Row(
                    Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 12.dp, bottom = 10.dp),
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
                        val stemPreview = q?.stem?.let { plainPreview(it, 42) }
                        if (!stemPreview.isNullOrBlank()) {
                            Text(
                                stemPreview + if ((q?.stem?.length ?: 0) > 42) "…" else "",
                                fontSize = 11.5.sp, color = Ink2, maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                    if (vm.chatMessages.isNotEmpty()) {
                        Box(
                            Modifier.size(44.dp).clip(CircleShape)
                                .tjClickable(label = "清空对话") { vm.clearChat() },
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Rounded.DeleteOutline, null, tint = Muted, modifier = Modifier.size(20.dp)) }
                    }
                    Box(
                        Modifier.size(44.dp).clip(CircleShape)
                            .tjClickable(label = "关闭") { vm.closeChat() },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Rounded.Close, null, tint = Ink2, modifier = Modifier.size(22.dp)) }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Line))

                // messages
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    state = listState,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp, end = 16.dp, top = 14.dp, bottom = 10.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (vm.chatMessages.isEmpty()) {
                        item {
                            Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                                Text(
                                    if (q == null) "先打开一道题，再来追问。" else "可以问解析里的任何一步，也可以长按解析中的文字选择「追问」。",
                                    fontSize = 13.sp, lineHeight = 21.sp, color = Ink2
                                )
                                Spacer(Modifier.height(12.dp))
                                listOf(
                                    "这一步为什么要这样变形？",
                                    "这道题用到哪些公式和概念？",
                                    "用更简单的方法再讲一遍",
                                    "我卡在哪一步？帮我定位薄弱点"
                                ).forEach { hint ->
                                    Box(
                                        Modifier.padding(bottom = 8.dp)
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(AccentSoft)
                                            .tjClickable(label = hint) { input = hint }
                                            .padding(horizontal = 12.dp, vertical = 8.dp)
                                    ) { Text(hint, fontSize = 12.5.sp, color = Accent) }
                                }
                            }
                        }
                    }
                    items(vm.chatMessages.size) { index ->
                        val (role, text) = vm.chatMessages[index]
                        if (role == "user") {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                Box(
                                    Modifier
                                        .widthIn(max = 300.dp)
                                        .clip(RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp))
                                        .background(Accent)
                                        .padding(horizontal = 14.dp, vertical = 10.dp)
                                ) {
                                    Text(text, fontSize = 14.sp, lineHeight = 22.sp, color = Color.White)
                                }
                            }
                        } else {
                            Column(
                                Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp))
                                    .background(SurfaceAlt)
                                    .padding(horizontal = 14.dp, vertical = 12.dp)
                            ) {
                                Text("题笺 AI", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Accent)
                                Spacer(Modifier.height(6.dp))
                                RichText(text, fontSize = 14, selectable = false)
                            }
                        }
                    }
                    if (vm.chatBusy) {
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(16.dp), color = Accent, strokeWidth = 2.dp)
                                Text("正在结合你的掌握情况思考…", fontSize = 12.sp, color = Muted, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                    vm.chatError?.let { error ->
                        item {
                            Text(error, fontSize = 12.sp, lineHeight = 18.sp, color = Red)
                        }
                    }
                }

                // quote
                if (vm.chatQuote.isNotBlank()) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                            .clip(RoundedCornerShape(10.dp)).background(AccentSoft)
                            .padding(start = 10.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("引用解析", fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = Accent)
                            Text(
                                vm.chatQuote.take(80),
                                fontSize = 12.sp, lineHeight = 18.sp, color = Ink2,
                                maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        Box(
                            Modifier.size(36.dp).clip(CircleShape)
                                .tjClickable(label = "取消引用") { vm.updateChatQuote("") },
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Rounded.Close, null, tint = Muted, modifier = Modifier.size(18.dp)) }
                    }
                    Spacer(Modifier.height(6.dp))
                }

                // input
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = { Text("继续追问这道题…", fontSize = 13.sp, color = Muted) },
                        maxLines = 4,
                        colors = tjFieldColors(),
                        shape = RoundedCornerShape(14.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            val text = input
                            if (text.isNotBlank() && !vm.chatBusy) {
                                input = ""
                                vm.sendChat(text)
                            }
                        }),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier.size(52.dp).clip(RoundedCornerShape(14.dp))
                            .background(if (input.isBlank() || vm.chatBusy) SurfaceAlt else Accent)
                            .tjClickable(enabled = input.isNotBlank() && !vm.chatBusy, label = "发送") {
                                val text = input
                                input = ""
                                vm.sendChat(text)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.Send, "发送",
                            tint = if (input.isBlank() || vm.chatBusy) Muted else Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}


/** 把 $...$ / Markdown 标记压成一行纯文本，用于聊天头部的小字预览。 */
private fun plainPreview(src: String, max: Int): String {
    var t = src.replace(Regex("\\s+"), " ").trim()
    t = t.replace(Regex("\\$([^$]*)\\$")) { m ->
        m.value.removePrefix("$").removeSuffix("$")
            .replace("\\", "").replace("{", "").replace("}", "")
            .replace("^", "").replace("_", "")
    }
    t = t.replace("*", "").replace("#", "").replace(Regex("\\s+"), " ").trim()
    return if (t.length > max) t.take(max).trimEnd() + "…" else t
}
