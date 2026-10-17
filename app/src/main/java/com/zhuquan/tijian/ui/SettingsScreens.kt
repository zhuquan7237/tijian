@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.zhuquan.tijian.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Info
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
            subtitle = "本地优先 · 数据只在你的手机与你配置的模型之间流转",
            actions = { IconCircle(Icons.Rounded.Info, "关于") { showAbout = true } }
        )
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(Color.White)
                    .border(1.dp, Line, RoundedCornerShape(15.dp)).padding(vertical = 14.dp)
            ) {
                StatCell("${vm.stats.total}", "错题", Modifier.weight(1f))
                StatCell("${vm.stats.mastered}", "已掌握", Modifier.weight(1f))
                StatCell("${vm.stats.due}", "待复习", Modifier.weight(1f))
            }
            Spacer(Modifier.height(14.dp))

            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(Color.White)
                    .border(1.dp, Line, RoundedCornerShape(15.dp))
            ) {
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

            Spacer(Modifier.height(14.dp))
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(Color.White)
                    .border(1.dp, Line, RoundedCornerShape(15.dp))
            ) {
                SettingRow(Icons.Rounded.Info, "关于题笺", "v${Updater.currentName()} · 本地优先") { showAbout = true }
            }

            Spacer(Modifier.height(18.dp))
            Text(
                "题笺 v${Updater.currentName()} · 数据存储在本机\n识别与分类由你配置的模型完成，请自行评估隐私与费用。",
                fontSize = 10.sp, lineHeight = 16.sp, color = Muted
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
private fun StatCell(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 19.sp, fontWeight = FontWeight.Medium, color = Ink)
        Text(label, fontSize = 10.sp, color = Muted, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
fun SettingRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickableNoRipple(onClick).padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFF6F6F1)),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = Ink2, modifier = Modifier.size(15.dp)) }
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(title, fontSize = 12.5.sp, color = Ink)
            Text(subtitle, fontSize = 10.sp, color = Muted, modifier = Modifier.padding(top = 2.dp))
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = Color(0xFFC4C4BC), modifier = Modifier.size(16.dp))
    }
}

@Composable
fun ProviderScreen(vm: AppVm) {
    var baseUrl by remember { mutableStateOf(vm.provider.baseUrl) }
    var apiKey by remember { mutableStateOf(vm.provider.apiKey) }
    var model by remember { mutableStateOf(vm.provider.model) }
    var protocol by remember { mutableStateOf(vm.provider.protocol) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var testError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        TjTopBar(
            title = "AI 接入",
            onBack = { vm.back() },
            actions = {
                Text("保存", fontSize = 12.5.sp, color = Accent, modifier = Modifier.clickableNoRipple {
                    vm.saveProvider(ProviderConfig(baseUrl, apiKey, model, protocol))
                    vm.back()
                }.padding(8.dp))
            }
        )
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
            Text(
                "任何 OpenAI 兼容接口都可以。识别需要模型支持图片输入（vision）。",
                fontSize = 11.sp, lineHeight = 18.sp, color = Muted
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = baseUrl, onValueChange = { baseUrl = it },
                label = { Text("Base URL", fontSize = 11.sp) },
                placeholder = { Text("https://api.example.com/v1", fontSize = 12.sp, color = Muted) },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(9.dp))
            OutlinedTextField(
                value = apiKey, onValueChange = { apiKey = it },
                label = { Text("API Key", fontSize = 11.sp) },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(9.dp))
            OutlinedTextField(
                value = model, onValueChange = { model = it },
                label = { Text("模型", fontSize = 11.sp) },
                placeholder = { Text("例如 gpt-5.6-luna / gemini-3-flash", fontSize = 12.sp, color = Muted) },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            Text("协议", fontSize = 10.sp, color = Muted)
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TjChip("chat/completions", protocol == "chat") { protocol = "chat" }
                TjChip("responses", protocol == "responses") { protocol = "responses" }
            }
            Spacer(Modifier.height(16.dp))

            Box(
                Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(11.dp))
                    .background(Color(0xFFF4F4EF)).clickableNoRipple {
                        if (!testing) {
                            testing = true; testError = null; testResult = null
                            scope.launch {
                                try {
                                    testResult = vm.testProvider(ProviderConfig(baseUrl, apiKey, model, protocol))
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
                    Text("测试中…", fontSize = 11.5.sp, color = Ink2, modifier = Modifier.padding(start = 8.dp))
                } else Text("测试连接", fontSize = 12.sp, color = Ink2)
            }
            testResult?.let {
                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CheckCircle, null, tint = Green, modifier = Modifier.size(14.dp))
                    Text(it, fontSize = 10.5.sp, color = Green, modifier = Modifier.padding(start = 5.dp))
                }
            }
            testError?.let {
                Text(it, fontSize = 10.5.sp, color = Red, lineHeight = 16.sp, modifier = Modifier.padding(top = 10.dp))
            }

            Spacer(Modifier.height(22.dp))
            Text("说明", fontSize = 10.sp, letterSpacing = 1.sp, color = Muted)
            Spacer(Modifier.height(6.dp))
            Text(
                "· Base URL 一般以 /v1 结尾，App 会自动补 /chat/completions 或 /responses。\n" +
                    "· 分类与知识点整理会走同一个模型；想省钱可以在以后拆成两个角色。\n" +
                    "· API Key 保存在本机数据库，仅用于你配置的接口。\n" +
                    "· 识别失败时原图会保留，可稍后手动补录或换模型重试。",
                fontSize = 10.5.sp, lineHeight = 18.sp, color = Muted
            )
            Spacer(Modifier.height(60.dp))
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
                    Text("下载中 ${vm.updateProgress}%", fontSize = 10.sp, color = Muted, modifier = Modifier.padding(top = 6.dp))
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
