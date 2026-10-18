@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.zhuquan.tijian.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

    val pickImages = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(9)
    ) { uris -> if (uris.isNotEmpty()) vm.importUris(uris) }

    val pickOne = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) vm.addManual(uri) }

    BackHandler(enabled = vm.route != Route.Home) { if (!vm.back()) vm.home() }

    Box(Modifier.fillMaxSize().background(Bg).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = vm.route,
                    transitionSpec = {
                        pageTurnTransform(vm.navForward)
                    },
                    label = "page-turn"
                ) { route ->
                    when (route) {
                        is Route.Home -> HomeScreen(vm) { showImport = true }
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
            }
            if (vm.route is Route.Home || vm.route is Route.Library || vm.route is Route.Settings) {
                BottomBar(
                    route = vm.route,
                    onHome = { vm.home() },
                    onLibrary = { vm.replace(Route.Library); vm.refresh() },
                    onSettings = { vm.replace(Route.Settings); vm.refresh() }
                )
            }
        }

        if (vm.route is Route.Home) {
            Box(
                Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 104.dp)
                    .size(54.dp).clip(CircleShape).background(Ink)
                    .bounceClick { showImport = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Add, "导入", tint = Color.White, modifier = Modifier.size(26.dp))
            }
        }

        vm.toast?.let { msg ->
            LaunchedEffect(msg) {
                delay(2400)
                if (vm.toast == msg) vm.toast = null
            }
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp)) {
                Box(
                    Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0xF217171C))
                        .padding(horizontal = 14.dp, vertical = 9.dp)
                ) { Text(msg, fontSize = 11.5.sp, color = Color.White) }
            }
        }

        if (vm.busy && vm.route !is Route.Import && vm.route !is Route.Category && vm.route !is Route.Doc) {
            BusyOverlay(vm.busyLabel)
        }

        if (vm.updateInfo != null) UpdateDialog(vm)

        if (showImport) {
            ModalBottomSheet(onDismissRequest = { showImport = false }, containerColor = Color.White) {
                Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 30.dp)) {
                    Text("导入错题", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                    Text("可以一次选 9 张，AI 会自动切题", fontSize = 10.5.sp, color = Muted, modifier = Modifier.padding(top = 3.dp, bottom = 12.dp))
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

private fun pageTurnTransform(forward: Boolean): ContentTransform {
    val incoming = if (forward) {
        slideInHorizontally(animationSpec = tween(260, easing = FastOutSlowInEasing), initialOffsetX = { it })
    } else {
        slideInHorizontally(animationSpec = tween(260, easing = FastOutSlowInEasing), initialOffsetX = { -it })
    }
    val outgoing = if (forward) {
        slideOutHorizontally(animationSpec = tween(260, easing = FastOutSlowInEasing), targetOffsetX = { -it / 3 })
    } else {
        slideOutHorizontally(animationSpec = tween(260, easing = FastOutSlowInEasing), targetOffsetX = { it / 3 })
    }
    return incoming togetherWith outgoing
}

@Composable
private fun ImportOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickableNoRipple(onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFF6F6F1)),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = Ink2, modifier = Modifier.size(19.dp)) }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(title, fontSize = 13.sp, color = Ink, fontWeight = FontWeight.Medium)
            Text(subtitle, fontSize = 10.5.sp, color = Muted, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun BottomBar(route: Route, onHome: () -> Unit, onLibrary: () -> Unit, onSettings: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(86.dp).background(Color(0xF7FFFFFF)).padding(bottom = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TabItem(Icons.Rounded.MenuBook, "错题本", route is Route.Home, Modifier.weight(1f), onHome)
        TabItem(Icons.Rounded.AutoStories, "知识库", route is Route.Library, Modifier.weight(1f), onLibrary)
        TabItem(Icons.Rounded.Person, "我的", route is Route.Settings, Modifier.weight(1f), onSettings)
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
    Column(
        modifier.clickableNoRipple(onClick).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, label, tint = if (selected) Ink else Color(0xFFA2A2A8), modifier = Modifier.size(21.dp))
        Text(label, fontSize = 10.sp, color = if (selected) Ink else Color(0xFFA2A2A8), modifier = Modifier.padding(top = 4.dp))
    }
}
