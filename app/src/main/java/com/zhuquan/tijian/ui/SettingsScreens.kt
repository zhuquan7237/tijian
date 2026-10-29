@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.zhuquan.tijian.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhuquan.tijian.data.ProviderConfig
import com.zhuquan.tijian.update.Updater
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(vm: AppVm) {
    var showAbout by remember { mutableStateOf(false) }
    var showRemind by remember { mutableStateOf(false) }
    var showBackup by remember { mutableStateOf(false) }
    var showAppearance by remember { mutableStateOf(false) }
    var showCrash by remember { mutableStateOf(false) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { vm.loadBackupInfo() }

    val exportZip = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri: Uri? ->
        if (uri != null) vm.exportBackup(uri)
    }
    val importZip = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) restoreUri = uri
    }

    Column(Modifier.fillMaxSize()) {
        TjTopBar(
            title = "我的",
            subtitle = if (vm.provider.ready()) "AI 已接入" else "识别前需要接入 AI，题目仍只存在本机"
        )
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Space.page, vertical = 4.dp)) {
            if (vm.lastCrashReport != null) {
                CardBox(Modifier.fillMaxWidth().padding(bottom = 8.dp).border(1.dp, Amber.copy(alpha = 0.5f), CardShape)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("上次异常退出", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = Amber)
                        Text(
                            "检测到应用上次崩溃退出。如果当时有未确认的导入题目，已自动隔离，可到错题本页恢复。",
                            fontSize = 12.sp, lineHeight = 18.sp, color = Ink2, modifier = Modifier.padding(top = 4.dp)
                        )
                        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "查看崩溃日志", fontSize = 12.sp, color = Accent, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.tjClickable { showCrash = true }.padding(vertical = 6.dp)
                            )
                            Spacer(Modifier.width(16.dp))
                            Text(
                                "复制日志", fontSize = 12.sp, color = Ink2,
                                modifier = Modifier.tjClickable {
                                    val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    cm.setPrimaryClip(android.content.ClipData.newPlainText("题笺崩溃日志", vm.lastCrashReport ?: ""))
                                    android.widget.Toast.makeText(ctx, "已复制崩溃日志", android.widget.Toast.LENGTH_SHORT).show()
                                }.padding(vertical = 6.dp)
                            )
                        }
                    }
                }
            }
            CardBox(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(vertical = 16.dp)) {
                    StatCell("${vm.stats.total}", "错题", Modifier.weight(1f)) { vm.home() }
                    StatCell("${vm.stats.mastered}", "已掌握", Modifier.weight(1f)) { vm.home() }
                    StatCell("${vm.stats.due}", "待复习", Modifier.weight(1f)) {
                        if (vm.stats.due > 0) vm.startReview()
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            SectionLabel("快捷操作")
            CardBox(Modifier.fillMaxWidth()) {
                Column {
                    SettingRow(
                        Icons.Rounded.Notifications,
                        "复习提醒",
                        if (!vm.remindEnabled) "已关闭" else "每天 %02d:%02d · 只提醒今天要看的题".format(vm.remindHour, vm.remindMinute)
                    ) { showRemind = true }
                    DividerSoft()
                    SettingRow(Icons.Rounded.PlayArrow, "开始今日复习", if (vm.stats.due > 0) "${vm.todayQueue.size} 道，共 ${vm.stats.due} 道到期" else "今天没有到期任务") {
                        if (vm.stats.due > 0) vm.startReview() else vm.home()
                    }
                    DividerSoft()
                    SettingRow(Icons.Rounded.AutoStories, "生成知识文档", "从一个分类提炼知识点") { vm.replace(Route.Library) }
                }
            }

            SectionLabel("设置")
            CardBox(Modifier.fillMaxWidth()) {
                Column {
                SettingRow(Icons.Rounded.Key, "AI 接入", if (vm.provider.ready()) {
                    "已就绪 · ${vm.provider.model}"
                } else "未配置，点此接入") {
                    vm.go(Route.Provider)
                }
                DividerSoft()
                SettingRow(Icons.Rounded.AutoFixHigh, "一键大整理", "旧题统一归到最新分类体系") {
                    vm.go(Route.Tidy)
                }
                DividerSoft()
                SettingRow(Icons.Rounded.RestoreFromTrash, "回收站", if (vm.trashCount > 0) "${vm.trashCount} 道已删除的题可恢复" else "删除的题目会先到这里") {
                    vm.go(Route.Trash)
                }
                DividerSoft()
                SettingRow(Icons.Rounded.Info, "外观", if (tjAppearance.intValue == 1) "浅色" else if (tjAppearance.intValue == 2) "深色" else "跟随系统") {
                    showAppearance = true
                }
                DividerSoft()
                SettingRow(
                    Icons.Rounded.CloudDownload, "检查更新",
                    if (vm.checkingUpdate) "检查中…" else "当前 ${Updater.currentName()}"
                ) { vm.checkUpdate(silent = false) }
                DividerSoft()
                SettingRow(Icons.Rounded.Backup, "备份与恢复", "数据库 + 图片，整包 zip") { showBackup = true }
                }
            }

            SectionLabel("关于")
            CardBox(Modifier.fillMaxWidth()) {
                SettingRow(Icons.Rounded.Info, "关于题笺", "v${Updater.currentName()} · 本地优先") { showAbout = true }
            }

            Spacer(Modifier.height(20.dp))
            Text(
                "题笺 v${Updater.currentName()} · 全部数据保存在本机\n识别与分类由你配置的模型完成，请自行评估隐私与费用。",
                fontSize = 12.sp, lineHeight = 19.sp, color = Muted
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showRemind) {
        var enabled by remember { mutableStateOf(vm.remindEnabled) }
        var hour by remember { mutableIntStateOf(vm.remindHour) }
        val ctx = LocalContext.current
        val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                vm.setReminder(true, hour, 0)
                showRemind = false
            } else vm.toast = "没有通知权限。首页仍会列出今天的题。"
        }
        AlertDialog(
            onDismissRequest = { showRemind = false },
            title = { Text("复习提醒", fontSize = 16.sp) },
            text = {
                Column {
                    Text(
                        if (enabled) "每天这个时间提醒你今天要复习哪几道。关掉之后，清单还在首页。" else "提醒已关闭。",
                        fontSize = 13.sp, color = Ink2
                    )
                    Row(Modifier.padding(top = 12.dp).horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(8, 12, 18, 20, 21, 22).forEach { h ->
                            TjChip("%02d:00".format(h), enabled && hour == h) { enabled = true; hour = h }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (!enabled) {
                        vm.setReminder(false, hour, 0)
                        showRemind = false
                    } else if (android.os.Build.VERSION.SDK_INT >= 33 &&
                        !com.zhuquan.tijian.review.ReviewReminder.notificationsAllowed(ctx)
                    ) {
                        askNotify.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        vm.setReminder(true, hour, 0)
                        showRemind = false
                    }
                }) { Text(if (enabled) "保存" else "关闭提醒", color = if (enabled) Accent else Ink2) }
            },
            dismissButton = {
                TextButton(onClick = {
                    vm.setReminder(false, hour, 0)
                    showRemind = false
                }) { Text("关掉提醒", color = Muted) }
            }
        )
    }

    if (showAppearance) {
        AlertDialog(
            onDismissRequest = { showAppearance = false },
            title = { Text("外观", fontSize = 16.sp) },
            text = {
                Column {
                    listOf(
                        0 to "跟随系统",
                        1 to "浅色（日间墨白）",
                        2 to "深色（夜读台）",
                    ).forEach { (mode, label) ->
                        Row(
                            Modifier.fillMaxWidth().tjClickable {
                                vm.setAppearance(mode); showAppearance = false
                            }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioDot(tjAppearance.intValue == mode)
                            Text(label, fontSize = 14.sp, color = Ink, modifier = Modifier.padding(start = 10.dp))
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 10.dp), color = Line)
                    Text("阅读字号", fontSize = 13.sp, color = Ink, fontWeight = FontWeight.Medium)
                    Text("题干、解析、复习页的文字大小", fontSize = 11.5.sp, color = Muted, modifier = Modifier.padding(top = 2.dp, bottom = 4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TjChip("标准", vm.readingScaleValue <= 1.01f) { vm.setReadingScale(1f) }
                        TjChip("大", vm.readingScaleValue in 1.02f..1.11f) { vm.setReadingScale(1.1f) }
                        TjChip("特大", vm.readingScaleValue >= 1.12f) { vm.setReadingScale(1.2f) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showAppearance = false }) { Text("完成", color = Accent, fontWeight = FontWeight.SemiBold) } }
        )
    }

    if (showBackup) {
        AlertDialog(
            onDismissRequest = { showBackup = false },
            title = { Text("备份与恢复", fontSize = 16.sp) },
            text = {
                Column {
                    if (vm.lastBackupAt > 0) {
                        Text(
                            "上次备份：${formatBackupTime(vm.lastBackupAt)} · 包含 ${vm.lastBackupCount} 道题",
                            fontSize = 12.sp, color = Green
                        )
                    } else {
                        Text("还没有导出过备份", fontSize = 12.sp, color = Muted)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "导出会打包数据库和全部图片（不含 AI API Key）。\n恢复会覆盖当前数据并重启应用；恢复前会自动把现在的数据在本机存一份快照，后悔了还有救。",
                        fontSize = 12.sp, lineHeight = 19.sp, color = Muted
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showBackup = false
                    exportZip.launch("tijian-backup-${dateStamp()}.zip")
                }) { Text("导出备份", color = Accent, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showBackup = false
                    importZip.launch(arrayOf("application/zip", "application/octet-stream"))
                }) { Text("恢复备份", color = Red) }
            }
        )
    }

    restoreUri?.let { uri ->
        AlertDialog(onDismissRequest = { restoreUri = null },
            title = { Text("恢复并覆盖当前数据？") },
            text = { Text("恢复会替换本机错题、图片及设置，完成后应用会自动重启。请确认已导出当前备份。") },
            confirmButton = { TextButton(onClick = { restoreUri = null; vm.importBackup(uri) }) { Text("恢复", color = Red) } },
            dismissButton = { TextButton(onClick = { restoreUri = null }) { Text("取消") } })
    }

    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = { Text("题笺", fontSize = 18.sp) },
            text = {
                Column {
                    Text("拍 → 认 → 分 → 练。", fontSize = 12.5.sp, color = Ink)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "错题识别、自动分类、PDF 导出、间隔复习、知识点整理。\n" +
                            "所有数据保存在本机；AI 请求只会发往你填写的接口。",
                        fontSize = 11.5.sp, lineHeight = 19.sp, color = Muted
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showAbout = false }) { Text("好") } }
        )
    }

    if (showCrash) {
        AlertDialog(
            onDismissRequest = { showCrash = false },
            title = { Text("上次崩溃日志", fontSize = 16.sp) },
            text = {
                Column {
                    Text(
                        vm.lastCrashReport ?: "",
                        fontSize = 10.5.sp, lineHeight = 15.sp, color = Ink2,
                        modifier = Modifier
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState())
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    com.zhuquan.tijian.App.clearCrashReport(ctx.filesDir)
                    showCrash = false
                }) { Text("清除", color = Red) }
            },
            dismissButton = { TextButton(onClick = { showCrash = false }) { Text("关闭", color = Ink2) } }
        )
    }

}

@Composable
private fun StatCell(value: String, label: String, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    Column(
        modifier.tjClickable(label = "$label $value") { onClick() }.padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            value, fontSize = 24.sp, fontWeight = FontWeight.SemiBold,
            color = if (value == "0") Muted else Ink
        )
        Text(label, fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 3.dp))
    }
}

@Composable
fun SettingRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    val rowSrc = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Row(
        Modifier.pressScale(rowSrc).fillMaxWidth()
            .tjClickable(interactionSource = rowSrc) { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(SurfaceAlt),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = Ink2, modifier = Modifier.size(18.dp)) }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(title, fontSize = 15.sp, color = Ink, fontWeight = FontWeight.Medium)
            Text(subtitle, fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 3.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = Muted, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun ProviderScreen(vm: AppVm) {
    // 以最新落库配置为初值；页面停留期间数据被别的入口改过也能跟得上
    var baseUrl by remember(vm.provider) { mutableStateOf(vm.provider.baseUrl) }
    var apiKey by remember(vm.provider) { mutableStateOf(vm.provider.apiKey) }
    var model by remember(vm.provider) { mutableStateOf(vm.provider.model) }
    var protocol by remember(vm.provider) { mutableStateOf(vm.provider.protocol) }
    var reasoning by remember(vm.provider) { mutableStateOf(vm.provider.reasoning) }
    var models by remember(vm.provider) { mutableStateOf(vm.provider.models) }
    var webSearch by remember(vm.provider) { mutableStateOf(vm.provider.webSearch) }
    var showPicker by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var fetchError by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var testError by remember { mutableStateOf<String?>(null) }
    var namingProfile by remember { mutableStateOf(false) }
    var profileName by remember { mutableStateOf("") }
    // 新手只需要服务商 + Key + 模型三件事；协议和思考强度收进高级设置
    var showAdvanced by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun cfg() = ProviderConfig(baseUrl, apiKey, model, protocol, models, reasoning, webSearch)

    fun pull() {
        if (baseUrl.isBlank()) { fetchError = "先填 Base URL"; return }
        if (loading) return
        loading = true
        fetchError = null
        scope.launch {
            try {
                val list = vm.ai.listModels(cfg())
                models = list
                if (model.isBlank()) model = list.firstOrNull().orEmpty()
            } catch (e: Exception) {
                fetchError = e.message ?: "拉取失败"
            } finally {
                loading = false
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TjTopBar(
            title = "AI 接入",
            subtitle = if (vm.provider.ready()) "当前 ${vm.provider.model}" else "填一个 OpenAI 兼容接口即可开始",
            onBack = { vm.back() },
            actions = {
                TjButton("保存", enabled = cfg().ready()) {
                    vm.saveProvider(cfg().copy(baseUrl = baseUrl.trim(), apiKey = apiKey.trim(), model = model.trim()))
                    // 从导入任务跳过来配置的：配好直接回去接着导入
                    val back = vm.returnAfterProvider
                    vm.returnAfterProvider = null
                    if (back != null) vm.replace(back) else vm.back()
                }
            }
        )

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
        ) {
            Text(
                "接入任意 OpenAI 兼容服务。识别需要模型支持图片输入；分类、知识点整理会复用同一个模型。",
                fontSize = 12.sp, lineHeight = 19.sp, color = Muted
            )

            SectionLabel("常用提供商")
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (vm.profiles.isEmpty()) {
                    Text(
                        "填好地址和 Key 后点「保存当前」，多提供商一键切换",
                        fontSize = 11.5.sp, color = Muted
                    )
                }
                vm.profiles.forEach { p ->
                    val active = baseUrl.trim() == p.baseUrl && apiKey.trim() == p.apiKey
                    TjChip(if (active) "● ${p.name}" else p.name, selected = active) { vm.applyProfile(p.name) }
                }
                TjChip("＋ 保存当前", selected = false) {
                    profileName = ""
                    namingProfile = true
                }
            }

            SectionLabel("服务商")
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PRESETS.forEach { (name, url) ->
                    TjChip(name, selected = baseUrl.trim() == url) {
                        baseUrl = url; models = emptyList(); fetchError = null; testResult = null; testError = null
                        if (protocol.isBlank()) protocol = "chat"
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            CardBox(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    FieldLabel("接口地址")
                    TjTextField(
                        value = baseUrl, onValueChange = { baseUrl = it; models = emptyList(); fetchError = null; testResult = null; testError = null },
                        placeholder = { Text("https://api.example.com/v1", fontSize = 12.5.sp, color = Muted) },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                        shape = SmallShape
                    )
                    Spacer(Modifier.height(12.dp))
                    FieldLabel("API Key")
                    TjTextField(
                        value = apiKey, onValueChange = { apiKey = it },
                        placeholder = { Text("sk-…", fontSize = 12.5.sp, color = Muted) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                        shape = SmallShape
                    )
                    Spacer(Modifier.height(12.dp))
                    FieldLabel("模型")
                    TjTextField(
                        value = model, onValueChange = { model = it },
                        placeholder = { Text("点下方拉取，或手动输入", fontSize = 12.sp, color = Muted) },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                        shape = SmallShape
                    )
                    Spacer(Modifier.height(8.dp))
                    // 一眼能找到的大按钮：拉取后直接弹出模型选择
                    TjButton(
                        when {
                            loading -> "正在拉取模型…"
                            models.isEmpty() -> "拉取模型列表"
                            else -> "浏览模型 · ${models.size} 个"
                        },
                        Modifier.fillMaxWidth(), loading = loading, style = ActionStyle.Secondary,
                    ) {
                        showPicker = true
                        if (models.isEmpty()) pull()
                    }

                    Spacer(Modifier.height(12.dp))
                    // 高级设置默认折叠：先让人用起来，协议/思考强度是进阶话题
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .tjClickable(label = if (showAdvanced) "收起高级设置" else "展开高级设置") { showAdvanced = !showAdvanced }
                            .padding(horizontal = 4.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("高级设置", fontSize = 12.5.sp, color = Ink2, fontWeight = FontWeight.SemiBold)
                        Text(
                            "协议 · 思考强度", fontSize = 11.5.sp, color = Muted,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                        Spacer(Modifier.weight(1f))
                        Icon(
                            if (showAdvanced) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            contentDescription = if (showAdvanced) "收起" else "展开",
                            tint = Muted, modifier = Modifier.size(18.dp)
                        )
                    }
                    if (showAdvanced) {
                        FieldLabel("协议")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TjChip("chat/completions", protocol == "chat") { protocol = "chat" }
                            TjChip("responses", protocol == "responses") { protocol = "responses" }
                        }

                        Spacer(Modifier.height(12.dp))
                        FieldLabel("思考强度")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TjChip("默认", reasoning.isBlank()) { reasoning = "" }
                            TjChip("低", reasoning == "low") { reasoning = "low" }
                            TjChip("中", reasoning == "medium") { reasoning = "medium" }
                            TjChip("高", reasoning == "high") { reasoning = "high" }
                        }
                        Text(
                            "推理模型（R1 / o 系 / Qwen-Thinking 等）的思考档位，随每次请求发送；「默认」不发参数，由服务商自定。不支持该参数的服务请保持默认。",
                            fontSize = 11.sp, lineHeight = 17.sp, color = Muted, modifier = Modifier.padding(top = 6.dp)
                        )

                        Spacer(Modifier.height(12.dp))
                        FieldLabel("联网搜索")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TjChip("关闭", !webSearch) { webSearch = false }
                            TjChip("允许 AI 按需搜索", webSearch) { webSearch = true }
                        }
                        Text(
                            "开启后按服务商注入联网参数：阿里百炼 enable_search、OpenRouter :online、智谱 web_search；是否真的搜索由模型自行判断（比如题目涉及它不知道的知识点）。其他服务商不支持时保持关闭。",
                            fontSize = 11.sp, lineHeight = 17.sp, color = Muted, modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }

            fetchError?.let {
                Spacer(Modifier.height(12.dp))
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(RedSoft)
                        .padding(12.dp)
                ) {
                    Text(it, fontSize = 11.sp, lineHeight = 17.sp, color = Red)
                    Text(
                        "重试", fontSize = 11.sp, color = Accent, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.tjClickable { pull() }.padding(top = 6.dp)
                    )
                }
            }

            testResult?.let {
                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CheckCircle, null, tint = Green, modifier = Modifier.size(14.dp))
                    Text(it, fontSize = 12.sp, color = Green, modifier = Modifier.padding(start = 5.dp))
                }
            }
            testError?.let {
                Column(
                    Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(10.dp))
                        .background(RedSoft).padding(10.dp)
                ) {
                    Text(it, fontSize = 12.sp, lineHeight = 18.sp, color = Red)
                }
            }

            SectionLabel("说明")
            Text(
                "· 拉取模型走 GET {Base URL}/models，和你填的地址保持同源。\n" +
                    "· 拉取失败通常是 Key 无效、地址少了 /v1，或中转没开放 /models。\n" +
                    "· API Key 只保存在本机数据库，不上传。\n" +
                    "· http:// 开头的地址是明文传输，公网服务请尽量用 https://。",
                fontSize = 11.sp, lineHeight = 18.sp, color = Muted
            )
            Spacer(Modifier.height(16.dp))
        }

        // 常驻底部操作条：测试与保存永远在同一位置，不用满页找按钮
        Row(
            Modifier.fillMaxWidth().background(CardBg).padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TjButton(
                if (testing) "测试中…" else "测试连接",
                Modifier.weight(1f), enabled = cfg().ready() && !testing,
                loading = testing, style = ActionStyle.Secondary,
            ) {
                testing = true; testError = null; testResult = null
                scope.launch {
                    try { testResult = vm.testProvider(cfg()) }
                    catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                    catch (e: Exception) { testError = e.message ?: "连接失败" }
                    finally { testing = false }
                }
            }
            TjButton(
                "保存配置", Modifier.weight(1f), enabled = cfg().ready(),
            ) {
                vm.saveProvider(cfg().copy(baseUrl = baseUrl.trim(), apiKey = apiKey.trim(), model = model.trim()))
                // 从导入任务跳过来配置的：配好直接回去接着导入
                val back = vm.returnAfterProvider
                vm.returnAfterProvider = null
                if (back != null) vm.replace(back) else vm.back()
            }
        }
    }

    if (namingProfile) {
        AlertDialog(
            onDismissRequest = { namingProfile = false },
            title = { Text("保存为常用提供商", fontSize = 16.sp) },
            text = {
                Column {
                    Text(
                        "会把当前表单里的地址、Key、协议、思考强度和模型列表一起存下来。",
                        fontSize = 11.5.sp, lineHeight = 17.sp, color = Muted
                    )
                    Spacer(Modifier.height(10.dp))
                    TjTextField(
                        value = profileName, onValueChange = { profileName = it },
                        placeholder = { Text("名字，如：DeepSeek 官方 / 硅基流动", fontSize = 12.5.sp, color = Muted) },
                        singleLine = true, colors = tjFieldColors(), modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = profileName.isNotBlank(), onClick = {
                    vm.saveCurrentAsProfile(profileName)
                    namingProfile = false
                }) { Text("保存", color = Accent, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = { TextButton(onClick = { namingProfile = false }) { Text("取消", color = Ink2) } }
        )
    }

    if (showPicker) {
        ModelPickerSheet(
            models = models, current = model, loading = loading, error = fetchError,
            onRefresh = { pull() },
            onDismiss = { showPicker = false },
            onPick = { model = it; showPicker = false }
        )
    }
}

private fun formatBackupTime(ms: Long): String =
    java.text.SimpleDateFormat("M月d日 HH:mm", java.util.Locale.CHINA).format(java.util.Date(ms))

private val PRESETS = listOf(
    "自定义" to "",
    "OpenAI" to "https://api.openai.com/v1",
    "DeepSeek" to "https://api.deepseek.com/v1",
    "OpenRouter" to "https://openrouter.ai/api/v1",
    "硅基流动" to "https://api.siliconflow.cn/v1",
    "阿里百炼" to "https://dashscope.aliyuncs.com/compatible-mode/v1",
)

@Composable
private fun FieldLabel(text: String) {
    Text(
        text, fontSize = 12.sp, color = Ink2, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

@Composable
private fun ModelPickerSheet(
    models: List<String>,
    current: String,
    loading: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = models.filter { it.contains(query.trim(), ignoreCase = true) }
    // 自绘底部面板 + LazyColumn 自滚动：不再用 ModalBottomSheet 套列表，
    // 「拉到一半拖不动」的手势冲突从根上消除。面板带淡入与上抬过渡。
    val appear = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) {
        appear.animateTo(1f, androidx.compose.animation.core.tween(Motion.Base, easing = Motion.Enter))
    }
    androidx.activity.compose.BackHandler { onDismiss() }
    androidx.compose.foundation.layout.BoxWithConstraints(
        Modifier.fillMaxSize()
            .graphicsLayer { alpha = appear.value }
            .background(Scrim)
            .pointerInput(Unit) { detectTapGestures { onDismiss() } }
    ) {
        val panelMaxHeight = maxHeight * 0.84f
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(panelMaxHeight)
                .graphicsLayer { translationY = (1f - appear.value) * 40f }
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(CardBg)
                .pointerInput(Unit) { detectTapGestures { } }
                .imePadding()
        ) {
            val spin = androidx.compose.animation.core.rememberInfiniteTransition(label = "pull-spin")
            val angle by spin.animateFloat(
                0f, 360f,
                androidx.compose.animation.core.infiniteRepeatable(
                    androidx.compose.animation.core.tween(900, easing = androidx.compose.animation.core.LinearEasing)
                ),
                label = "pull-angle",
            )
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("选择模型", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink)
                    Text(
                        when {
                            loading -> "正在从 /models 拉取…"
                            models.isEmpty() -> "还没有拉取到模型"
                            else -> "共 ${models.size} 个 · 点一下即选用"
                        },
                        fontSize = 11.5.sp, color = Muted, modifier = Modifier.padding(top = 3.dp)
                    )
                }
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(SurfaceAlt)
                        .tjClickable(enabled = !loading, label = "重新拉取模型") { onRefresh() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.Refresh, "重新拉取模型", tint = Ink2,
                        modifier = Modifier.size(19.dp).graphicsLayer { if (loading) rotationZ = angle },
                    )
                }
                Spacer(Modifier.width(6.dp))
                Box(
                    Modifier.size(44.dp).clip(CircleShape)
                        .tjClickable(label = "关闭") { onDismiss() },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Rounded.Close, "关闭", tint = Ink2, modifier = Modifier.size(20.dp)) }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                TjTextField(
                    value = query, onValueChange = { query = it },
                    placeholder = { Text("搜索模型，如 deepseek / vl / 4o", fontSize = 12.sp, color = Muted) },
                    singleLine = true, modifier = Modifier.weight(1f),
                    shape = SmallShape,
                    leadingIcon = { Icon(Icons.Rounded.Search, null, tint = Muted, modifier = Modifier.size(17.dp)) },
                )
                if (query.isNotEmpty()) {
                    Text(
                        "清空", fontSize = 12.sp, color = Accent,
                        modifier = Modifier.tjClickable { query = "" }.padding(start = 10.dp)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            when {
                loading && models.isEmpty() -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(Modifier.size(28.dp), color = Accent, strokeWidth = 2.5.dp)
                        Text("正在拉取模型列表…", fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 12.dp))
                    }
                }
                error != null && models.isEmpty() -> Column(
                    Modifier.fillMaxWidth().weight(1f).padding(horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("拉取失败", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Red)
                    Text(error, fontSize = 11.5.sp, lineHeight = 18.sp, color = Muted,
                        modifier = Modifier.padding(top = 6.dp, bottom = 14.dp))
                    TjButton("重试拉取", style = ActionStyle.Secondary) { onRefresh() }
                }
                filtered.isEmpty() -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        if (models.isEmpty()) "点右上角 ↻ 拉取模型列表" else "没有匹配「$query」的模型",
                        fontSize = 12.5.sp, color = Muted
                    )
                }
                else -> LazyColumn(
                    Modifier.fillMaxSize().weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    items(filtered, key = { it }) { m ->
                        val selected = m == current
                        val family = m.substringBefore('/').takeIf { it != m && it.isNotBlank() }
                        Row(
                            Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selected) AccentSoft else SurfaceAlt)
                                .border(
                                    1.dp,
                                    if (selected) Accent.copy(alpha = 0.55f) else Line,
                                    RoundedCornerShape(12.dp)
                                )
                                .tjClickable { onPick(m) }
                                .padding(horizontal = 13.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    m, fontSize = 12.5.sp, color = Ink,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                                if (family != null) {
                                    Text(family, fontSize = 10.5.sp, color = Muted, modifier = Modifier.padding(top = 2.dp))
                                }
                            }
                            if (selected) {
                                Box(
                                    Modifier.clip(RoundedCornerShape(999.dp)).background(Accent)
                                        .padding(horizontal = 9.dp, vertical = 3.dp)
                                ) {
                                    Text("当前", fontSize = 10.5.sp, color = OnAccent, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
            Text(
                "选不到就回上层手动填模型名 · 拉取走 GET {地址}/models",
                fontSize = 10.5.sp, color = Muted,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
fun UpdateDialog(vm: AppVm) {
    val info = vm.updateInfo ?: return
    val ctx = LocalContext.current
    val downloading = vm.updateProgress in 0..99
    val ready = vm.updateReady

    AlertDialog(
        onDismissRequest = { if (!downloading) vm.clearUpdate() },
        title = { Text("发现新版本 ${info.version}", fontSize = 16.sp) },
        text = {
            Column {
                Text("当前 ${Updater.currentName()} → ${info.version}", fontSize = 11.5.sp, color = Muted)
                if (info.notes.isNotBlank()) {
                    Text(info.notes.take(400), fontSize = 11.sp, lineHeight = 18.sp, color = Ink2, modifier = Modifier.padding(top = 8.dp))
                }
                if (downloading) {
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(progress = { vm.updateProgress / 100f }, modifier = Modifier.fillMaxWidth(), color = Accent)
                    Text("下载中 ${vm.updateProgress}%", fontSize = 11.sp, color = Muted, modifier = Modifier.padding(top = 6.dp))
                }
                if (ready != null) {
                    Spacer(Modifier.height(10.dp))
                    Text("下载完成，点「安装」交给系统安装器。", fontSize = 11.sp, color = Green)
                }
            }
        },
        confirmButton = {
            when {
                ready != null -> TextButton(onClick = {
                    if (!ctx.packageManager.canRequestPackageInstalls()) {
                        ctx.startActivity(
                            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                        )
                    } else {
                        Updater.install(ctx, ready)
                    }
                }) { Text("安装") }
                downloading -> TextButton(onClick = {}, enabled = false) { Text("下载中…", color = Muted) }
                else -> TextButton(onClick = { vm.downloadUpdate() }) { Text("下载并安装") }
            }
        },
        dismissButton = {
            TextButton(onClick = { vm.clearUpdate() }, enabled = !downloading) { Text("稍后") }
        }
    )
}
