@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.zhuquan.tijian.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
    var showBackup by remember { mutableStateOf(false) }
    val ctx = LocalContext.current

    val exportZip = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri: Uri? ->
        if (uri != null) vm.exportBackup(uri)
    }
    val importZip = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) vm.importBackup(uri)
    }

    Column(Modifier.fillMaxSize()) {
        TjTopBar(
            title = "我的",
            subtitle = "本地优先 · 数据只在本机与你配置的模型之间流转"
        )
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Space.page)) {
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
                    SettingRow(Icons.Rounded.PlayArrow, "开始今日复习", if (vm.stats.due > 0) "${vm.stats.due} 道到期" else "今天没有到期任务") {
                        if (vm.stats.due > 0) vm.startReview() else vm.home()
                    }
                    DividerSoft()
                    SettingRow(Icons.Rounded.AutoStories, "生成知识文档", "从一个分类提炼知识点") { vm.replace(Route.Library) }
                }
            }

            SectionLabel("设置")
            CardBox(Modifier.fillMaxWidth()) {
                Column {
                SettingRow(Icons.Rounded.Key, "AI 接入", if (vm.provider.ready()) "${vm.provider.model} · ${vm.provider.protocol}" else "未配置，点此接入") {
                    vm.go(Route.Provider)
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
            Spacer(Modifier.height(80.dp))
        }
    }

    if (showBackup) {
        AlertDialog(
            onDismissRequest = { showBackup = false },
            title = { Text("备份与恢复", fontSize = 16.sp) },
            text = { Text("导出会打包数据库和全部图片；恢复会覆盖当前数据，建议先导出一份。", fontSize = 12.sp, color = Muted) },
            confirmButton = {
                TextButton(onClick = {
                    showBackup = false
                    exportZip.launch("tijian-backup-${dateStamp()}.zip")
                }) { Text("导出备份") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showBackup = false
                    importZip.launch(arrayOf("application/zip", "application/octet-stream"))
                }) { Text("恢复备份", color = Red) }
            }
        )
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
    Row(
        Modifier.fillMaxWidth().tjClickable { onClick() }.padding(horizontal = 16.dp, vertical = 14.dp),
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
        Icon(Icons.Rounded.ChevronRight, null, tint = Color(0xFF98A3B3), modifier = Modifier.size(18.dp))
    }
}

@Composable
fun ProviderScreen(vm: AppVm) {
    var baseUrl by remember { mutableStateOf(vm.provider.baseUrl) }
    var apiKey by remember { mutableStateOf(vm.provider.apiKey) }
    var model by remember { mutableStateOf(vm.provider.model) }
    var protocol by remember { mutableStateOf(vm.provider.protocol) }
    var models by remember { mutableStateOf(vm.provider.models) }
    var showPicker by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var fetchError by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var testError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun cfg() = ProviderConfig(baseUrl, apiKey, model, protocol, models)

    fun pull() {
        if (baseUrl.isBlank()) { fetchError = "先填 Base URL"; return }
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
                Box(
                    Modifier.height(40.dp).clip(RoundedCornerShape(10.dp)).background(Accent)
                        .tjClickable(label = "保存") {
                            vm.saveProvider(ProviderConfig(baseUrl, apiKey, model, protocol, models))
                            vm.back()
                        }.padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) { Text("保存", fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.SemiBold) }
            }
        )

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
        ) {
            Text(
                "接入任意 OpenAI 兼容服务。识别需要模型支持图片输入；分类、知识点整理会复用同一个模型。",
                fontSize = 12.sp, lineHeight = 19.sp, color = Muted
            )

            SectionLabel("服务商")
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PRESETS.forEach { (name, url) ->
                    TjChip(name, selected = baseUrl.trim() == url) {
                        baseUrl = url
                        if (protocol.isBlank()) protocol = "chat"
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            CardBox(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    FieldLabel("接口地址")
                    OutlinedTextField(
                        value = baseUrl, onValueChange = { baseUrl = it },
                        placeholder = { Text("https://api.example.com/v1", fontSize = 12.5.sp, color = Muted) },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    FieldLabel("API Key")
                    OutlinedTextField(
                        value = apiKey, onValueChange = { apiKey = it },
                        placeholder = { Text("sk-…", fontSize = 12.5.sp, color = Muted) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    FieldLabel("模型")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = model, onValueChange = { model = it },
                            placeholder = { Text("点右侧拉取，或手动输入", fontSize = 12.sp, color = Muted) },
                            singleLine = true, modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Box(
                            Modifier.height(56.dp).clip(RoundedCornerShape(12.dp)).background(Accent)
                                .tjClickable {
                                    if (models.isEmpty()) { showPicker = true; pull() }
                                    else showPicker = true
                                }
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (loading) CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                            else Text("拉取", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            when {
                                loading -> "正在请求 /models…"
                                models.isEmpty() -> "还没有拉取过模型"
                                else -> "已拉取 ${models.size} 个模型"
                            },
                            fontSize = 11.5.sp, color = Muted
                        )
                        Spacer(Modifier.weight(1f))
                        if (models.isNotEmpty()) {
                            Text(
                                "浏览", fontSize = 11.5.sp, color = Accent,
                                modifier = Modifier.tjClickable { showPicker = true }.padding(4.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    FieldLabel("协议")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TjChip("chat/completions", protocol == "chat") { protocol = "chat" }
                        TjChip("responses", protocol == "responses") { protocol = "responses" }
                    }
                }
            }

            fetchError?.let {
                Spacer(Modifier.height(12.dp))
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFFBEDE9))
                        .padding(12.dp)
                ) {
                    Text(it, fontSize = 11.sp, lineHeight = 17.sp, color = Color(0xFF8E3B26))
                    Text(
                        "重试", fontSize = 11.sp, color = Accent, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.tjClickable { pull() }.padding(top = 6.dp)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Box(
                Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(14.dp)).background(CardBg)
                    .border(1.dp, Accent, RoundedCornerShape(14.dp))
                    .tjClickable {
                        if (!testing) {
                            testing = true; testError = null; testResult = null
                            scope.launch {
                                try {
                                    testResult = vm.testProvider(cfg())
                                } catch (e: Exception) {
                                    testError = e.message ?: "连接失败"
                                } finally { testing = false }
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (testing) Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(15.dp), color = Accent, strokeWidth = 2.dp)
                    Text("测试中…", fontSize = 12.sp, color = Ink2, modifier = Modifier.padding(start = 8.dp))
                } else Text("测试连接（用当前模型发一句话）", fontSize = 12.sp, color = Ink2)
            }
            testResult?.let {
                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CheckCircle, null, tint = Green, modifier = Modifier.size(14.dp))
                    Text(it, fontSize = 11.sp, color = Green, modifier = Modifier.padding(start = 5.dp))
                }
            }
            testError?.let {
                Text(it, fontSize = 11.sp, lineHeight = 17.sp, color = Red, modifier = Modifier.padding(top = 10.dp))
            }

            SectionLabel("说明")
            Text(
                "· 拉取模型走 GET {Base URL}/models，和你填的地址保持同源。\n" +
                    "· 拉取失败通常是 Key 无效、地址少了 /v1，或中转没开放 /models。\n" +
                    "· API Key 只保存在本机数据库，不上传。",
                fontSize = 11.sp, lineHeight = 18.sp, color = Muted
            )
            Spacer(Modifier.height(50.dp))
        }
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
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = CardBg) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("选择模型", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                    Text(
                        if (models.isEmpty()) "从 /models 拉取" else "共 ${models.size} 个 · 点击选用",
                        fontSize = 11.sp, color = Muted, modifier = Modifier.padding(top = 3.dp)
                    )
                }
                Text(
                    if (loading) "拉取中…" else "刷新", fontSize = 12.sp, color = Accent,
                    modifier = Modifier.tjClickable { if (!loading) onRefresh() }.padding(8.dp)
                )
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                placeholder = { Text("搜索模型", fontSize = 12.sp, color = Muted) },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(Modifier.height(8.dp))
            when {
                loading && models.isEmpty() -> Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(26.dp), color = Accent, strokeWidth = 2.dp)
                }
                error != null && models.isEmpty() -> Column(Modifier.fillMaxWidth().padding(vertical = 28.dp)) {
                    Text(error, fontSize = 11.5.sp, lineHeight = 18.sp, color = Red)
                    Text(
                        "重试", fontSize = 12.sp, color = Accent, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.tjClickable { onRefresh() }.padding(top = 10.dp)
                    )
                }
                filtered.isEmpty() -> Text(
                    if (models.isEmpty()) "还没有拉取到模型" else "没有匹配的模型",
                    fontSize = 12.sp, color = Muted, modifier = Modifier.padding(vertical = 28.dp)
                )
                else -> LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                    items(filtered, key = { it }) { m ->
                        Row(
                            Modifier.fillMaxWidth().tjClickable { onPick(m) }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(m, fontSize = 12.5.sp, color = Ink, maxLines = 1)
                                if (m == current) Text("当前使用", fontSize = 11.sp, color = Accent, modifier = Modifier.padding(top = 2.dp))
                            }
                            if (m == current) Icon(Icons.Rounded.CheckCircle, null, tint = Accent, modifier = Modifier.size(17.dp))
                        }
                        HorizontalDivider(color = Line, thickness = 1.dp)
                    }
                }
            }
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
                downloading -> TextButton(onClick = {}) { Text("下载中…", color = Muted) }
                else -> TextButton(onClick = { vm.downloadUpdate() }) { Text("下载并安装") }
            }
        },
        dismissButton = {
            TextButton(onClick = { vm.clearUpdate() }, enabled = !downloading) { Text("稍后") }
        }
    )
}
