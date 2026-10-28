package com.zhuquan.tijian.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Stop
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 全局追问入口。
 *
 * 对话内容渲染在**一个** WebView 里（chat.html），滚动完全交给 WebView 原生处理：
 * 长公式左右滑、消息上下滑都不会再和 Compose 的滚动互相抢手势。
 * 长按选词走 JS 自己的逻辑，只弹应用内的「复制 / 追问」，不弹系统菜单。
 */
@Composable
fun ChatOverlay(vm: AppVm) {
    BackHandler(enabled = vm.chatOpen) { vm.closeChat() }
    // 悬浮球只在「有题目上下文」的页面出现（详情 / 复习）。识别校对、导入工作台
    // 这类页面它没有可追问的对象，留在那里只会压住「确认入库」「开始识别」等主按钮。
    val contextual = vm.route is Route.Detail || vm.route is Route.Review
    when {
        vm.chatOpen -> ChatPanel(vm)
        contextual -> ChatFab(vm)
        else -> Unit
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
        // 位置挂在 AppVm 上：页面来回切不会把用户拖好的位置弹回右下角
        var pos by remember {
            mutableStateOf(
                vm.fabPos ?: IntOffset(
                    (maxW - sizePx - 18.dp.value * density.density).toInt(),
                    // 贴右下角（距底 24dp）；这一层只在详情/复习页出现，没有底部 tab 要避让。
                    (maxH - sizePx - 24.dp.value * density.density).toInt()
                )
            )
        }
        androidx.compose.runtime.LaunchedEffect(maxW, maxH) {
            pos = IntOffset(pos.x.coerceIn(0, (maxW - sizePx).coerceAtLeast(0f).toInt()),
                pos.y.coerceIn(0, (maxH - sizePx).coerceAtLeast(0f).toInt()))
        }
        Box(
            Modifier
                .offset { pos }
                .size(size)
                .shadow(10.dp, CircleShape, ambientColor = Color(0x33000000), spotColor = Color(0x33000000))
                .clip(CircleShape)
                .background(Fill)
                .border(1.dp, HeroLine, CircleShape)
                .tjClickable(label = "追问 AI") { vm.openChat() }
                .pointerInput(maxW, maxH, sizePx) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        pos = IntOffset(
                            (pos.x + drag.x).coerceIn(0f, (maxW - sizePx).coerceAtLeast(0f)).toInt(),
                            (pos.y + drag.y).coerceIn(0f, (maxH - sizePx).coerceAtLeast(0f)).toInt()
                        )
                        vm.fabPos = pos
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Forum, "追问 AI", tint = Accent, modifier = Modifier.size(24.dp))
            if (vm.chatQuestion != null) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 6.dp)
                        .size(8.dp).clip(CircleShape).background(Accent)
                )
            }
        }
    }
}

@Composable
private fun ChatPanel(vm: AppVm) {
    val q = vm.chatQuestion
    var input by androidx.compose.runtime.saveable.rememberSaveable(q?.id) { mutableStateOf("") }
    val hasThread = vm.chatMessages.isNotEmpty()
    val pickChatImage = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) vm.attachChatImage(uri) }
    val pickChatFile = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri -> if (uri != null) vm.attachChatFile(uri) }

    // 面板从底部轻轻抬起并淡入（180ms），不用 scale(0) 那种“凭空出现”。
    val appear = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) {
        appear.animateTo(1f, androidx.compose.animation.core.tween(Motion.Base, easing = Motion.Enter))
    }
    BoxWithConstraints(
        Modifier.fillMaxSize()
            .graphicsLayer { alpha = appear.value }
            .background(Color(0x99000000))
            .pointerInput(Unit) { detectTapGestures { vm.closeChat() } }
    ) {
        val panelMax = (maxHeight - 24.dp).coerceAtLeast(0.dp)
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(panelMax)
                .graphicsLayer { translationY = (1f - appear.value) * 24.dp.toPx() }
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(CardBg)
                .pointerInput(Unit) { detectTapGestures { } }
        ) {
            val compact = panelMax < 360.dp
            ChatHeader(vm, q)
            Box(Modifier.fillMaxWidth().height(1.dp).background(Line))

            if (hasThread) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    ChatTranscript(
                        messages = vm.chatMessages,
                        modifier = Modifier.fillMaxSize(),
                        onSelect = { vm.updateChatQuote(it) }
                    )
                    if (vm.chatBusy) {
                        Row(
                            Modifier.align(Alignment.BottomStart)
                                .padding(start = 16.dp, bottom = 10.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(SurfaceAlt)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(Modifier.size(13.dp), color = Accent, strokeWidth = 2.dp)
                            Text("正在思考…", fontSize = 11.sp, color = Muted, modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                }
            } else {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    ChatHints(q) { input = it }
                }
            }

            vm.chatError?.let {
                Text(it, fontSize = 12.sp, color = Red, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
            if (vm.chatQuote.isNotBlank()) {
                QuoteBlock(vm.chatQuote, compact = compact) { vm.updateChatQuote("") }
            }

            if (vm.chatTruncated && !vm.chatBusy) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("回答被模型长度限制截断", fontSize = 11.5.sp, color = Amber)
                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier.clip(RoundedCornerShape(999.dp)).background(AmberSoft)
                            .tjClickable(label = "继续生成") { vm.continueChat() }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) { Text("继续", fontSize = 12.sp, color = Amber, fontWeight = FontWeight.SemiBold) }
                }
            }
            if (!compact) QuickChips(enabled = !vm.chatBusy) { vm.sendChat(it) }

            // 附件预览：一张图或一个文本文件，发送后自动清空
            vm.chatAttachment?.let {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(10.dp)).background(SurfaceAlt)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (vm.chatAttachmentName == "图片") {
                        coil.compose.AsyncImage(
                            model = it, contentDescription = "待发送图片",
                            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Box(
                            Modifier.size(36.dp).clip(RoundedCornerShape(8.dp))
                                .background(AccentSoft),
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Rounded.AttachFile, null, tint = Accent, modifier = Modifier.size(17.dp)) }
                    }
                    Column(Modifier.weight(1f).padding(start = 9.dp)) {
                        Text(
                            if (vm.chatAttachmentName == "图片") "图片" else vm.chatAttachmentName,
                            fontSize = 12.sp, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Text("随下一条消息一起发送", fontSize = 10.5.sp, color = Muted)
                    }
                    Box(
                        Modifier.size(40.dp).clip(CircleShape)
                            .tjClickable(label = "移除附件") { vm.clearChatAttachment() },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Rounded.Close, "移除附件", tint = Muted, modifier = Modifier.size(16.dp)) }
                }
            }

            ChatInput(
                input = input,
                busy = vm.chatBusy,
                compact = compact,
                onInput = { input = it },
                onSend = {
                    val text = input
                    if (text.isNotBlank() && !vm.chatBusy) {
                        input = ""
                        vm.sendChat(text)
                    }
                },
                onStop = { vm.stopChat() },
                onPickImage = { pickChatImage.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onPickFile = { pickChatFile.launch("*/*") },
            )
        }
    }
}

@Composable
private fun ChatHeader(vm: AppVm, q: com.zhuquan.tijian.data.QuestionRow?) {
    Row(
        Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        QuestionThumb(q)
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text("追问这道题", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text(
                buildString {
                    append(q?.type?.ifBlank { "题目" } ?: "未选择题")
                    q?.categoryName?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
                    q?.let { append(" · 掌握度 ").append(it.mastery).append("/5") }
                },
                fontSize = 11.5.sp, color = Muted, modifier = Modifier.padding(top = 2.dp),
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
        }
        if (vm.chatMessages.isNotEmpty()) {
            Box(
                Modifier.size(48.dp).clip(CircleShape)
                    .tjClickable(label = "清空这道题的对话") { vm.clearChat() },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Rounded.DeleteOutline, null, tint = Muted, modifier = Modifier.size(19.dp)) }
        }
        Box(
            Modifier.size(48.dp).clip(CircleShape)
                .tjClickable(label = "关闭") { vm.closeChat() },
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Rounded.Close, null, tint = Ink2, modifier = Modifier.size(21.dp)) }
    }
}

/** 题目缩略图：和列表里一样，让人一眼认出这是哪道题；没有原图时退回题干首行。 */
@Composable
private fun QuestionThumb(q: com.zhuquan.tijian.data.QuestionRow?) {
    val path = q?.imagePath?.takeIf { it.isNotBlank() }
    val file = path?.let { java.io.File(it) }?.takeIf { it.exists() }
    Box(
        Modifier.size(64.dp).clip(InnerShape).background(SurfaceAlt)
            .border(1.dp, Line, InnerShape),
        contentAlignment = Alignment.Center
    ) {
        if (file != null) {
            coil.compose.AsyncImage(model = file, contentDescription = "题目缩略图",
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else Text((q?.type ?: "题").take(2), fontSize = 13.sp, color = Muted)
    }
}

@Composable
private fun ChatHints(q: com.zhuquan.tijian.data.QuestionRow?, onPick: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(
            if (q == null) "先打开一道题，再来追问。" else "可以问解析里的任何一步，也可以长按对话里的文字选择「追问」。",
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
    val chips = listOf("再讲细一点", "这步为什么成立？", "用到的公式列一下", "有没有更快的解法？")
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

/** 引用块：保留原排版（公式、表格），最多 150dp，可滚动。 */
@Composable
private fun QuoteBlock(quote: String, compact: Boolean = false, onClear: () -> Unit) {
    if (compact) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(InnerShape).background(SurfaceAlt),
            verticalAlignment = Alignment.CenterVertically) {
            Text("引用：$quote", Modifier.weight(1f).padding(start = 12.dp), color = Ink2,
                fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            IconCircle(Icons.Rounded.Close, "取消引用") { onClear() }
        }
        return
    }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(AccentSoft)
            .padding(start = 10.dp, top = 6.dp, bottom = 8.dp, end = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("引用", fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = Accent)
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.size(48.dp).clip(CircleShape)
                    .tjClickable(label = "取消引用") { onClear() },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Rounded.Close, null, tint = Muted, modifier = Modifier.size(16.dp)) }
        }
        Box(Modifier.fillMaxWidth().heightIn(max = 120.dp).verticalScroll(rememberScrollState())) {
            RichText(quote, fontSize = 13, selectable = false, interactive = false)
        }
    }
}

@Composable
private fun ChatInput(
    input: String,
    busy: Boolean,
    compact: Boolean = false,
    onInput: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onPickImage: () -> Unit = {},
    onPickFile: () -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 10.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        // 附件入口：发图走多模态，发文本类文件直接拼进问题
        Box(
            Modifier.size(42.dp).clip(CircleShape)
                .tjClickable(label = "发送文本文件") { onPickFile() },
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Rounded.AttachFile, "发送文本文件", tint = Ink2, modifier = Modifier.size(19.dp)) }
        Box(
            Modifier.size(42.dp).clip(CircleShape)
                .tjClickable(label = "发送图片") { onPickImage() },
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Rounded.AddPhotoAlternate, "发送图片", tint = Ink2, modifier = Modifier.size(20.dp)) }
        TjTextField(
            value = input,
            onValueChange = onInput,
            placeholder = { Text("继续追问这道题…", fontSize = 13.sp, color = Muted) },
            maxLines = if (compact) 2 else 4,
            colors = tjFieldColors(),
            shape = CardShape,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSend() }),
            modifier = Modifier.weight(1f).padding(start = 2.dp)
        )
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.size(48.dp).clip(CircleShape)
                .background(when {
                    busy -> RedSoft
                    input.isBlank() -> SurfaceAlt
                    else -> Accent
                })
                .tjClickable(enabled = busy || input.isNotBlank(), label = if (busy) "停止" else "发送") {
                    if (busy) onStop() else onSend()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (busy) Icons.Rounded.Stop else Icons.AutoMirrored.Rounded.Send,
                if (busy) "停止" else "发送",
                tint = when {
                    busy -> Red
                    input.isBlank() -> Muted
                    else -> OnAccent
                },
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
