@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.zhuquan.tijian.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
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
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Key
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
import androidx.core.view.WindowCompat
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

    // 外观：跟随系统 / 浅色 / 深色（设置页可改）。系统深浅变化或用户切换都即时生效。
    val sysDark = androidx.compose.foundation.isSystemInDarkTheme()
    updateSystemNight(sysDark)
    val nightNow = isNight
    LaunchedEffect(nightNow) {
        (ctx as? android.app.Activity)?.window?.let { w ->
            WindowCompat.getInsetsController(w, w.decorView).apply {
                isAppearanceLightStatusBars = !nightNow
                isAppearanceLightNavigationBars = !nightNow
            }
        }
    }

    // 回到前台刷新统计：待复习数跨午夜后不会自己变
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) vm.refresh()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    val pickImages = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(9)
    ) { uris -> if (uris.isNotEmpty()) { vm.clearCapture(); uris.forEach { vm.addCapture(it) }; vm.go(Route.Capture) } }

    val pickOne = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) vm.addManual(uri) }

    BackHandler(enabled = !vm.chatOpen && vm.route != Route.Home) { if (!vm.back()) vm.home() }

    // 首次打开：给三条都走得通的路，别让人卡在配置页
    LaunchedEffect(Unit) { vm.maybeShowOnboarding() }

    Box(Modifier.fillMaxSize().background(Bg).windowInsetsPadding(WindowInsets.safeDrawing)) {
        // Retain page state, but draw only the active page. Never snapshot WebViews:
        // full-screen readback adds latency and can capture stale AndroidView frames.
        val pages = remember { androidx.compose.runtime.mutableStateMapOf<Route, Int>() }
        if (!pages.containsKey(vm.route)) pages[vm.route] = vm.navigationDepth()
        val navAnim = remember(vm.route) {
            androidx.compose.animation.core.Animatable(if (reducedMotion) 1f else 0f)
        }
        LaunchedEffect(vm.route, reducedMotion) {
                if (reducedMotion) navAnim.snapTo(1f)
                else navAnim.animateTo(1f, tween(Motion.Base, easing = Motion.Enter))
                pages.keys.toList().filter { !vm.retainsPage(it) }.forEach {
                    pages.remove(it)
                    vm.forgetPage(it)
                }
        }
        Box(Modifier.fillMaxSize().clip(androidx.compose.ui.graphics.RectangleShape)) {
        pages.keys.toList().forEach { route ->
        androidx.compose.runtime.key(route) {
            val isCurrent = route == vm.route
            Box(Modifier.fillMaxSize().zIndex(if (isCurrent) 1f else -1f)
                .graphicsLayer {
                    // A small opaque translation avoids alpha compositing of WebViews.
                    translationX = if (!isCurrent) size.width * 2f
                        else if (isPeerRoute(route)) 0f
                        else (1f - navAnim.value) * 24.dp.toPx() * (if (vm.navForward) 1 else -1)
                    clip = true
                }.then(if (!isCurrent) Modifier.clearAndSetSemantics {} else Modifier)) {
                Column(Modifier.fillMaxSize().background(Bg)) {
                    Box(Modifier.weight(1f)) {
                        when (route) {
                            is Route.Home -> HomeScreen(vm) {
                                when {
                                    vm.importing || vm.pending.isNotEmpty() -> vm.go(Route.Import)
                                    vm.captureUris.isNotEmpty() -> vm.go(Route.Capture)
                                    else -> showImport = true
                                }
                            }
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
                            is Route.Trash -> TrashScreen(vm)
                            is Route.Tidy -> TidyScreen(vm)
                        }
                    }
                    if (isPeerRoute(route)) BottomBar(route,
                        onHome = { vm.home() },
                        onLibrary = { vm.replace(Route.Library); vm.refresh() },
                        onSettings = { vm.replace(Route.Settings); vm.refresh() })
                }
            }
        }
        }
        }
        vm.toast?.let { msg ->
            LaunchedEffect(msg) {
                delay(2400)
                if (vm.toast == msg) vm.toast = null
            }
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 92.dp, start = 20.dp, end = 20.dp)) {
                Box(
                    Modifier.semantics { liveRegion = LiveRegionMode.Polite }.clip(RoundedCornerShape(12.dp)).background(Color(0xF23A3D44))
                        .padding(horizontal = 16.dp, vertical = 11.dp)
                ) { Text(msg, fontSize = 12.5.sp, lineHeight = 18.sp, color = Color.White) }
            }
        }

        // 删除后的撤销条：点一下就恢复，几秒不点自动消失
        vm.undoMsg?.let { msg ->
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 92.dp, start = 20.dp, end = 20.dp)) {
                Row(
                    Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                        .clip(RoundedCornerShape(12.dp)).background(Color(0xF23A3D44))
                        .tjClickable(label = "撤销刚才的删除") { vm.runUndo() }
                        .padding(horizontal = 16.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(msg, fontSize = 12.5.sp, color = Color.White)
                    Text(
                        "撤销", fontSize = 12.5.sp, color = Color(0xFF5EEAD4),
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 12.dp)
                    )
                }
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
                    // 名副其实的手动录入：直接写文字，图片想补随时补
                    ImportOption(Icons.Rounded.EditNote, "手动录入", "直接输入文字，图片是选填项") {
                        showImport = false
                        vm.addManualText()
                    }
                    ImportOption(Icons.Rounded.Add, "只存一张图片", "先留个底，题干以后再补") {
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

        if (vm.showOnboarding) {
            OnboardingSheet(
                onTry = { vm.loadSampleQuestions() },
                onImport = { vm.dismissOnboarding(); showImport = true },
                onConfigure = { vm.dismissOnboarding(); vm.go(Route.Provider) },
                onSkip = { vm.dismissOnboarding() },
            )
        }
    }
}

/** 首次打开的三岔口：体验示例 / 直接收录 / 配置 AI，都不依赖彼此。 */
@Composable
private fun OnboardingSheet(
    onTry: () -> Unit,
    onImport: () -> Unit,
    onConfigure: () -> Unit,
    onSkip: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onSkip, containerColor = CardBg) {
        Column(Modifier.blockSheetDrag().padding(horizontal = Space.page).padding(bottom = 22.dp)) {
            Text("欢迎使用题笺", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ink, letterSpacing = (-0.4).sp)
            Text(
                "拍下错题 → AI 识别归类 → 按记忆曲线复习。\n三件事按你的节奏来，先选一件：",
                fontSize = 13.sp, lineHeight = 21.sp, color = Muted, modifier = Modifier.padding(top = 6.dp, bottom = 12.dp)
            )
            ImportOption(Icons.Rounded.AutoAwesome, "体验示例题", "不用配置 AI，先看看复习怎么用") { onTry() }
            ImportOption(Icons.Rounded.PhotoLibrary, "直接收录错题", "拍照或选图导入，稍后再配 AI") { onImport() }
            ImportOption(Icons.Rounded.Key, "配置 AI", "填一个 OpenAI 兼容接口，当场测试连接") { onConfigure() }
            Text(
                "跳过，自己逛逛",
                fontSize = 12.5.sp, color = Muted,
                modifier = Modifier.padding(top = 10.dp).align(Alignment.CenterHorizontally)
                    .tjClickable(label = "跳过引导") { onSkip() }.padding(horizontal = 12.dp, vertical = 6.dp)
            )
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
    Column(Modifier.fillMaxWidth().background(BarBg)) {
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
    Column(
        modifier.semantics { this.selected = selected }.heightIn(min = 64.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(
                interactionSource = src,
                indication = androidx.compose.foundation.LocalIndication.current,
                role = androidx.compose.ui.semantics.Role.Tab,
            ) {
                if (!selected) onClick()
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
            label, fontSize = 12.sp, color = tint,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}
