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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
 * 布局规范（8.15.1 重排）：
 * - 底部操作区所有控件统一 50dp 视觉高度、同一水平线；区块之间 8/12dp 呼吸间距；
 * - 引用块与附件预览用同一套「次级卡」样式（SurfaceAlt、14dp 圆角），不再各画各的；
 * - 消息区用页面底色，与操作区分层；文字选取走系统原生选区（见 ChatTranscript）。
 */
@Composable
fun ChatOverlay(vm: AppVm) {
    BackHandler(enabled = vm.chatOpen) { vm.closeChat() }
    // 详情页已经有「还没看懂？」卡片，不再叠一颗会盖住它的悬浮球。
    // 复习揭晓后没有这张卡片，才保留追问球。揭晓前不放：那一步是自己回想。
    val contextual = vm.route is Route.Review && vm.revealAnswer
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

/** 底部操作区统一的次级卡样式：引用块与附件预览共用。 */
@Composable
private fun Modifier.secondaryCard(): Modifier =
    this.fillMaxWidth()
        .clip(RoundedCornerShape(14.dp))
        .background(SurfaceAlt)

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

    // 面板从底部轻轻抬起并淡入（180ms），不用 scale(0) 那种"凭空出现"。
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
                .clip(RoundedCornerShape(26.dp, 26.dp, 0.dp, 0.dp))
                .background(CardBg)
                .pointerInput(Unit) { detectTapGestures { } }
                .imePadding()
        ) {
            val compact = panelMax < 360.dp
            ChatHeader(vm, q)

            if (hasThread) {
                // 消息区用页面底色，与操作区分层
                Box(
                    Modifier.weight(1f).fillMaxWidth().background(Bg)
                ) {
                    ChatTranscript(
                        messages = vm.chatMessages,
                        modifier = Modifier.fillMaxSize(),
                        onAsk = { vm.openChat(it) },
                    )
                    if (vm.chatBusy) {
                        Row(
                            Modifier.align(Alignment.BottomStart)
                                .padding(start = 16.dp, bottom = 12.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(SurfaceAlt)
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(Modifier.size(14.dp), color = Accent, strokeWidth = 2.dp)
                            Text("正在思考…", fontSize = 11.5.sp, color = Muted, modifier = Modifier.padding(start = 7.dp))
                        }
                    }
                }
            } else {
                Column(Modifier.weight(1f).fillMaxWidth().background(Bg).verticalScroll(rememberScrollState())) {
                    ChatHints(q) { input = it }
                }
            }

            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp, 22.dp, 0.dp, 0.dp))
                    .background(CardBg)
                    .padding(horizontal = 14.dp)
            ) {
                Spacer(Modifier.height(10.dp))

                vm.chatError?.let {
                    Text(it, fontSize = 12.sp, color = Red, modifier = Modifier.padding(bottom = 6.dp))
                }

                // 引用块与附件预览共用同一套次级卡样式
                if (vm.chatQuote.isNotBlank()) {
                    Row(
                        Modifier.secondaryCard().padding(start = 12.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                            Text("引用", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Accent)
                            Text(
                                vm.chatQuote, fontSize = 12.sp, lineHeight = 16.sp, color = Ink2,
                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        Box(
                            Modifier.size(40.dp).clip(CircleShape)
                                .tjClickable(label = "取消引用") { vm.updateChatQuote("") },
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Rounded.Close, "取消引用", tint = Muted, modifier = Modifier.size(16.dp)) }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                vm.chatAttachment?.let {
                    Row(
                        Modifier.secondaryCard().padding(start = 12.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (vm.chatAttachmentName == "图片") {
                            coil.compose.AsyncImage(
                                model = it, contentDescription = "待发送图片",
                                modifier = Modifier.padding(vertical = 7.dp).size(36.dp).clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            Box(
                                Modifier.padding(vertical = 7.dp).size(36.dp).clip(RoundedCornerShape(10.dp))
                                    .background(AccentSoft),
                                contentAlignment = Alignment.Center
                            ) { Icon(Icons.Rounded.AttachFile, null, tint = Accent, modifier = Modifier.size(17.dp)) }
                        }
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text(
                                if (vm.chatAttachmentName == "图片") "图片" else vm.chatAttachmentName,
                                fontSize = 12.5.sp, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            Text("随下一条消息一起发送", fontSize = 10.5.sp, color = Muted)
                        }
                        Box(
                            Modifier.size(40.dp).clip(CircleShape)
                                .tjClickable(label = "移除附件") { vm.clearChatAttachment() },
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Rounded.Close, "移除附件", tint = Muted, modifier = Modifier.size(16.dp)) }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                if (vm.chatTruncated && !vm.chatBusy) {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("回答被截断", fontSize = 11.5.sp, color = Amber)
                        Spacer(Modifier.weight(1f))
                        Box(
                            Modifier.clip(RoundedCornerShape(999.dp)).background(AmberSoft)
                                .tjClickable(label = "继续生成") { vm.continueChat() }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) { Text("继续", fontSize = 11.5.sp, color = Amber, fontWeight = FontWeight.SemiBold) }
                    }
                    Spacer(Modifier.height(6.dp))
                }

                if (!compact) QuickChips(enabled = !vm.chatBusy) { vm.sendChat(it) }

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
}

@Composable
private fun ChatHeader(vm: AppVm, q: com.zhuquan.tijian.data.QuestionRow?) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        QuestionThumb(q)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
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
                Modifier.size(44.dp).clip(CircleShape)
                    .tjClickable(label = "清空这道题的对话") { vm.clearChat() },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Rounded.DeleteOutline, null, tint = Muted, modifier = Modifier.size(19.dp)) }
        }
        Box(
            Modifier.size(44.dp).clip(CircleShape)
                .tjClickable(label = "关闭") { vm.closeChat() },
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Rounded.Close, null, tint = Ink2, modifier = Modifier.size(20.dp)) }
    }
}

/** 题目缩略图：和列表里一样，让人一眼认出这是哪道题；没有原图时退回题干首行。 */
@Composable
private fun QuestionThumb(q: com.zhuquan.tijian.data.QuestionRow?) {
    val path = q?.imagePath?.takeIf { it.isNotBlank() }
    val file = path?.let { java.io.File(it) }?.takeIf { it.exists() }
    Box(
        Modifier.size(44.dp).clip(InnerShape).background(SurfaceAlt)
            .border(1.dp, Line, InnerShape),
        contentAlignment = Alignment.Center
    ) {
        if (file != null) {
            coil.compose.AsyncImage(model = file, contentDescription = "题目缩略图",
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else Text((q?.type ?: "题").take(2), fontSize = 12.sp, color = Muted)
    }
}

@Composable
private fun ChatHints(q: com.zhuquan.tijian.data.QuestionRow?, onPick: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            if (q == null) "先打开一道题，再来追问。" else "可以问解析里的任何一步；长按回答里的文字可选取复制，或点回答下方的「复制全文」。",
            fontSize = 13.sp, lineHeight = 20.sp, color = Ink2
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
                    .background(SurfaceAlt)
                    .tjClickable(label = hint) { onPick(hint) }
                    .padding(horizontal = 13.dp, vertical = 8.dp)
            ) { Text(hint, fontSize = 12.5.sp, color = Ink2) }
        }
    }
}

/** 输入框上方的快捷追问：点一下直接发送。 */
@Composable
private fun QuickChips(enabled: Boolean, onSend: (String) -> Unit) {
    val chips = listOf("再讲细一点", "这步为什么成立？", "用到的公式列一下", "有没有更快的解法？")
    Row(
        Modifier.fillMaxWidth()
            .padding(vertical = 6.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        chips.forEach { chip ->
            Box(
                Modifier.clip(RoundedCornerShape(999.dp))
                    .background(AccentSoft)
                    .tjClickable(enabled = enabled, label = chip) { onSend(chip) }
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) { Text(chip, fontSize = 11.5.sp, color = Accent, maxLines = 1) }
        }
    }
}

/** 输入行：附加 / 输入 / 发送三个控件统一 50dp 高，同一条水平线上。 */
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
    var attachMenu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            Box(
                Modifier.size(50.dp)
                    .clip(CircleShape)
                    .background(if (attachMenu) AccentSoft else SurfaceAlt)
                    .tjClickable(label = "添加附件") { attachMenu = !attachMenu },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Add, "添加附件",
                    tint = if (attachMenu) Accent else Ink2,
                    modifier = Modifier.size(22.dp)
                )
            }
            androidx.compose.material3.DropdownMenu(
                expanded = attachMenu,
                onDismissRequest = { attachMenu = false },
                containerColor = CardBg,
                shape = RoundedCornerShape(14.dp),
                shadowElevation = 8.dp,
            ) {
                androidx.compose.material3.DropdownMenuItem(
                    leadingIcon = { Icon(Icons.Rounded.AddPhotoAlternate, null, tint = Ink2, modifier = Modifier.size(19.dp)) },
                    text = { Text("发送图片", fontSize = 14.sp, color = Ink) },
                    onClick = { attachMenu = false; onPickImage() }
                )
                androidx.compose.material3.DropdownMenuItem(
                    leadingIcon = { Icon(Icons.Rounded.AttachFile, null, tint = Ink2, modifier = Modifier.size(19.dp)) },
                    text = { Text("发送文本文件", fontSize = 14.sp, color = Ink) },
                    onClick = { attachMenu = false; onPickFile() }
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            TjTextField(
                value = input,
                onValueChange = onInput,
                placeholder = { Text("继续追问这道题…", fontSize = 13.sp, color = Muted) },
                maxLines = if (compact) 2 else 4,
                colors = tjFieldColors(),
                shape = SmallShape,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.size(50.dp).clip(CircleShape)
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
