@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.zhuquan.tijian.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import java.io.File

@Composable
fun CaptureScreen(vm: AppVm) {
    val ctx = LocalContext.current
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    var editingUri by remember { mutableStateOf<Uri?>(null) }
    var pendingUri by rememberSaveable { mutableStateOf<Uri?>(null) }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) pendingUri?.let { vm.addCapture(it); editingUri = it }
    }
    val pickMore = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(9)) { uris ->
        uris.forEach { vm.addCapture(it) }
    }

    fun shoot() {
        // 落在 filesDir 的工作台目录：cache 可能被系统随时清理，拍一半丢一半
        val dir = File(ctx.filesDir, "capture_staging").apply { mkdirs() }
        val f = File(dir, "cap_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
        pendingUri = uri
        runCatching { takePicture.launch(uri) }.onFailure {
            f.delete(); pendingUri = null; vm.toast = "无法打开相机，请从相册添加"
        }
    }

    Column(Modifier.fillMaxSize()) {
        TjTopBar(
            title = "导入工作台",
            subtitle = if (vm.captureUris.isEmpty()) "一张试卷可以拍多张，最后一起识别"
            else "已选 ${vm.captureUris.size} / 9 张 · 点击图片可裁剪",
            onBack = { vm.back() },
            actions = {
                if (vm.captureUris.isNotEmpty()) {
                    Text(
                        "清空",
                        fontSize = 12.sp,
                        color = Muted,
                        modifier = Modifier.tjClickable(label = "清空待导入图片") { confirmClear = true }.padding(8.dp)
                    )
                }
            }
        )

        if (vm.captureUris.isEmpty()) {
            Box(
                Modifier.padding(horizontal = 20.dp).fillMaxWidth().weight(1f)
                    .clip(RoundedCornerShape(20.dp)).background(SurfaceAlt),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(64.dp).clip(CircleShape).background(CardBg),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Rounded.CameraAlt, null, tint = Accent, modifier = Modifier.size(28.dp)) }
                    Text("先拍第一张", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Ink, modifier = Modifier.padding(top = 14.dp))
                    Text(
                        "拍完会自动回到这里，可以接着拍下一张；\n也可以从相册补充。",
                        fontSize = 12.sp, lineHeight = 19.sp, color = Muted,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(vm.captureUris, key = { it.toString() }) { uri ->
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(0.78f)
                            .clip(RoundedCornerShape(14.dp)).background(CardBg)
                            .border(1.dp, Line, RoundedCornerShape(14.dp))
                    ) {
                        AsyncImage(
                            model = uri, contentDescription = null,
                            contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().tjClickable { editingUri = uri }
                        )
                        Box(
                            Modifier.align(Alignment.TopEnd).padding(7.dp).size(48.dp)
                                .clip(CircleShape).background(Color(0xCC232220))
                                .tjClickable { vm.removeCapture(uri) },
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Rounded.Close, "删除", tint = Color.White, modifier = Modifier.size(20.dp)) }
                        Text(
                            "${vm.captureUris.indexOf(uri) + 1}",
                            fontSize = 11.sp, color = Color.White,
                            modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)
                                .clip(RoundedCornerShape(6.dp)).background(Color(0x99232220))
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }

        Column(Modifier.padding(horizontal = Space.page, vertical = 16.dp)) {
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TjButton("从相册添加", Modifier.weight(1f).fillMaxHeight(), style = ActionStyle.Secondary, enabled = vm.captureUris.size < 9 && !vm.importing) {
                    pickMore.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                TjButton(if (vm.captureUris.isEmpty()) "拍照" else "继续拍", Modifier.weight(1f).fillMaxHeight(),
                    style = ActionStyle.Secondary, enabled = vm.captureUris.size < 9 && !vm.importing) { shoot() }
            }
            Spacer(Modifier.height(8.dp))
            TjButton(
                if (vm.captureUris.isEmpty()) "拍一张后开始识别" else "开始识别（${vm.captureUris.size} 张）",
                Modifier.fillMaxWidth(), enabled = vm.captureUris.isNotEmpty() && !vm.importing,
            ) { vm.importCapture() }
        }
    }
    if (confirmClear) androidx.compose.material3.AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("清空待导入图片？") },
        text = { Text("将移除这 ${vm.captureUris.size} 张待导入图片，已有错题和相册原图不会删除。") },
        confirmButton = { TjButton("清空", style = ActionStyle.Danger) {
            vm.clearCapture(); confirmClear = false
        } },
        dismissButton = { TjButton("保留", style = ActionStyle.Quiet) { confirmClear = false } }
    )
    editingUri?.let { source -> ImageEditorDialog(source, onDismiss = { editingUri = null }, onSaved = { edited ->
        vm.replaceCapture(source, edited); editingUri = null
    }) }

}
