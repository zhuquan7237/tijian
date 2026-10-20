@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.zhuquan.tijian.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zhuquan.tijian.BuildConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun AppRoot(vm: AppVm = viewModel()) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var showImport by remember { mutableStateOf(false) }
    val reducedMotion = android.provider.Settings.Global.getFloat(
        ctx.contentResolver,
        android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
        1f
    ) == 0f

    val pickImages = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(9)
    ) { uris -> if (uris.isNotEmpty()) { vm.clearCapture(); uris.forEach { vm.addCapture(it) }; vm.go(Route.Capture) } }

    val pickOne = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) vm.addManual(uri) }

    BackHandler(enabled = !vm.chatOpen && vm.route != Route.Home) { if (!vm.back()) vm.home() }

    Box(Modifier.fillMaxSize().background(Bg).windowInsetsPadding(WindowInsets.safeDrawing)) {
        // Keep live page compositions: returning must not recreate WebViews or
        // briefly show their empty initial layout. Pages outside the stack are
        // removed only after their exit animation completes.
        val pages = remember { androidx.compose.runtime.mutableStateMapOf<Route, Int>() }
        if (!pages.containsKey(vm.route)) pages[vm.route] = vm.navigationDepth()
        var visualRoute by remember { mutableStateOf(vm.route) }
        if (vm.canDisplay(vm.route)) visualRoute = vm.route
        val transition = updateTransition(visualRoute, label = "navigation")
        LaunchedEffect(vm.route, transition.currentState, transition.targetState, transition.isRunning) {
            if (!transition.isRunning && transition.currentState == transition.targetState) {
                pages.keys.toList().filter { !vm.retainsPage(it) }.forEach { pages.remove(it); vm.forgetPage(it) }
            }
        }
        LaunchedEffect(vm.route, visualRoute, transition.isRunning, transition.currentState) {
            vm.navigating = vm.route != visualRoute || transition.isRunning || transition.currentState != visualRoute
        }
        val peerChange = isPeerRoute(transition.currentState) && isPeerRoute(transition.targetState)
        Box(Modifier.fillMaxSize()) {
        pages.toMap().forEach { (route, depth) ->
        androidx.compose.runtime.key(route) {
            // Material Motion：同级 tab 用 fade-through（只淡入淡出、不横向位移）；
            // 进入/返回详情用 shared-axis X（约 14dp 位移 + 依次淡出/淡入）。
            // 旧版重影来自两页同时以 0.96 alpha 叠着，现在旧页会完整淡到 0。
            val dir = when {
                peerChange -> 0f
                vm.navForward -> 1f
                else -> -1f
            }
            val offset by transition.animateFloat(
                transitionSpec = { tween(300, easing = FastOutSlowInEasing) },
                label = "page-position"
            ) { target ->
                if (target == route || peerChange) 0f
                else if (transition.targetState == route) 0.04f * dir
                else -0.04f * dir
            }
            val alpha by transition.animateFloat(
                transitionSpec = {
                    if (transition.targetState == route)
                        tween(220, delayMillis = 90, easing = FastOutSlowInEasing)
                    else tween(100, easing = LinearOutSlowInEasing)
                },
                label = "page-opacity"
            ) { target -> if (target == route) 1f else 0f }
            val active = route == transition.currentState || route == transition.targetState
            val front = if (peerChange || vm.navForward) route == transition.targetState else route == transition.currentState
            Box(Modifier.fillMaxSize().zIndex(if (!active) -2f else if (front) 1f else 0f)
                .graphicsLayer {
                    translationX = if (active) size.width * offset else size.width * 2
                    this.alpha = if (active) 1f else 0f
                    clip = true
                }.then(if (route != visualRoute) Modifier.clearAndSetSemantics {} else Modifier).background(Bg)) {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f).graphicsLayer { this.alpha = alpha }) {
                        when (route) {
                            is Route.Home -> HomeScreen(vm) { showImport = true }
                            is Route.Subject -> SubjectScreen(vm, route)
                            is Route.Category -> CategoryScreen(vm, route)
                            is Route.Detail -> QuestionDetailScreen(vm, route)
                            is Route.Import -> ImportScreen(vm)
                            is Route.Capture -> CaptureScreen(vm)
                            is Route.Review -> ReviewScreen(vm)
                            is Route.Library -> LibraryScreen(vm)
                            is Route.Doc -> DocScreen(vm, route)
                            is Route.Settings -> SettingsScreen(vm)
                            is Route.Provider -> ProviderScreen(vm)
                        }
                    }
                    if (isPeerRoute(route) && peerChange) Spacer(Modifier.height(65.dp))
                    else if (isPeerRoute(route)) BottomBar(route,
                        onHome = { vm.home() },
                        onLibrary = { vm.replace(Route.Library); vm.refresh() },
                        onSettings = { vm.replace(Route.Settings); vm.refresh() })
                }
            }
        }

        }
        }
        // A single stationary bar for peer changes; the page surface stays opaque
        // while only incoming content fades, so outgoing text cannot bleed through.
        if (peerChange) Box(Modifier.align(Alignment.BottomCenter)) {
            BottomBar(visualRoute, onHome = { vm.home() },
                onLibrary = { vm.replace(Route.Library); vm.refresh() },
                onSettings = { vm.replace(Route.Settings); vm.refresh() })
        }
        if (visualRoute != vm.route) {
            Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp)
                .clip(RoundedCornerShape(12.dp)).background(CardBg).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                androidx.compose.material3.CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Text("正在排版题目…", color = Ink2, fontSize = 12.sp)
            }
        }
        vm.toast?.let { msg ->
            LaunchedEffect(msg) {
                delay(2400)
                if (vm.toast == msg) vm.toast = null
            }
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 92.dp, start = 20.dp, end = 20.dp)) {
                Box(
                    Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xF217171C))
                        .padding(horizontal = 16.dp, vertical = 11.dp)
                ) { Text(msg, fontSize = 12.5.sp, lineHeight = 18.sp, color = Color.White) }
            }
        }

        if (vm.busy && vm.route !is Route.Import && vm.route !is Route.Category &&
            vm.route !is Route.Doc && vm.route !is Route.Library && vm.route !is Route.Home) {
            BusyOverlay(vm.busyLabel)
        }

        if (vm.updateInfo != null) UpdateDialog(vm)

        // 全局追问：悬浮球在所有页面可见；面板打开时接管整个屏幕
        ChatOverlay(vm)

        if (showImport) {
            ModalBottomSheet(onDismissRequest = { showImport = false }, containerColor = CardBg) {
                Column(Modifier.blockSheetDrag().padding(horizontal = Space.page).padding(bottom = 26.dp)) {
                    Text("导入错题", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink, letterSpacing = (-0.3).sp)
                    Text("可以一次选 9 张，AI 会自动切题", fontSize = 12.5.sp, color = Muted, modifier = Modifier.padding(top = 4.dp, bottom = 14.dp))
                    ImportOption(Icons.Rounded.PhotoLibrary, "从相册选择", "支持多选，原图会保留") {
                        showImport = false; pickImages.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                    ImportOption(Icons.Rounded.CameraAlt, "连续拍照", "拍一张回到这里，可接着拍多张") {
                        showImport = false
                        vm.clearCapture()
                        vm.go(Route.Capture)
                    }
                    ImportOption(Icons.Rounded.EditNote, "手动录入", "只存图片，题干自己填") {
                        showImport = false
                        pickOne.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                    if (BuildConfig.DEBUG) {
                        ImportOption(Icons.Rounded.Add, "导入示例图（调试）", "用内置样张跑通整条链路") {
                            showImport = false
                            scope.launch {
                                val f = File(ctx.cacheDir, "sample_exam.jpg")
                                ctx.assets.open("sample_exam.jpg").use { input ->
                                    f.outputStream().use { input.copyTo(it) }
                                }
                                vm.importUris(listOf(Uri.fromFile(f)), "debug")
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun isPeerRoute(route: Route): Boolean = route is Route.Home || route is Route.Library || route is Route.Settings

@Composable
private fun ImportOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().tjClickable(label = title, onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(SurfaceAlt),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = Ink2, modifier = Modifier.size(21.dp)) }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(title, fontSize = 15.sp, color = Ink, fontWeight = FontWeight.SemiBold)
            Text(subtitle, fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun BottomBar(route: Route, onHome: () -> Unit, onLibrary: () -> Unit, onSettings: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(Color(0xFAFFFFFF))) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
            TabItem(Icons.Rounded.MenuBook, "错题本", route is Route.Home, Modifier.weight(1f), onHome)
            TabItem(Icons.Rounded.AutoStories, "知识库", route is Route.Library, Modifier.weight(1f), onLibrary)
            TabItem(Icons.Rounded.Person, "我的", route is Route.Settings, Modifier.weight(1f), onSettings)
        }
    }
}

@Composable
private fun TabItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (pressed) 0.90f else 1f,
        animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.5f, stiffness = 820f),
        label = "tab-press"
    )
    val tint by androidx.compose.animation.animateColorAsState(
        targetValue = if (selected) Accent else Muted,
        animationSpec = tween(160), label = "tab-tint"
    )
    val pill by androidx.compose.animation.animateColorAsState(
        targetValue = if (selected) AccentSoft else Color.Transparent,
        animationSpec = tween(160), label = "tab-pill"
    )
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    Column(
        modifier.heightIn(min = 64.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(
                interactionSource = src,
                indication = androidx.compose.foundation.LocalIndication.current,
                role = androidx.compose.ui.semantics.Role.Tab,
            ) {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier.clip(RoundedCornerShape(999.dp)).background(pill)
                .padding(horizontal = 14.dp, vertical = 3.dp)
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        }
        Text(
            label, fontSize = 11.sp, color = tint,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}
