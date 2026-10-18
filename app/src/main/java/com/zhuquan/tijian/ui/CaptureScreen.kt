@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.zhuquan.tijian.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
    var pendingUri by remember { mutableStateOf<Uri?>(null) }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) pendingUri?.let { vm.addCapture(it) }
    }
    val pickMore = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(9)) { uris ->
        uris.forEach { vm.addCapture(it) }
    }

    fun shoot() {
        val dir = File(ctx.cacheDir, "camera").apply { mkdirs() }
        val f = File(dir, "cap_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
        pendingUri = uri
        takePicture.launch(uri)
    }

    Column(Modifier.fillMaxSize()) {
        TjTopBar(
            title = "拍照导入",
            subtitle = if (vm.captureUris.isEmpty()) "一张试卷可以拍多张，最后一起识别"
            else "已拍 ${vm.captureUris.size} 张 · 可以继续拍",
            onBack = { vm.back() },
            actions = {
                if (vm.captureUris.isNotEmpty()) {
                    Text(
                        "清空",
                        fontSize = 12.sp,
                        color = Muted,
                        modifier = Modifier.clickableNoRipple { vm.clearCapture() }.padding(8.dp)
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
                            .shadow(8.dp, RoundedCornerShape(14.dp), ambientColor = Color(0x14362A1C), spotColor = Color(0x14362A1C))
                            .clip(RoundedCornerShape(14.dp)).background(CardBg)
                    ) {
                        AsyncImage(
                            model = uri, contentDescription = null,
                            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            Modifier.align(Alignment.TopEnd).padding(7.dp).size(26.dp)
                                .clip(CircleShape).background(Color(0xCC232220))
                                .clickableNoRipple { vm.removeCapture(uri) },
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Rounded.Close, "删除", tint = Color.White, modifier = Modifier.size(14.dp)) }
                        Text(
                            "${vm.captureUris.indexOf(uri) + 1}",
                            fontSize = 10.sp, color = Color.White,
                            modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)
                                .clip(RoundedCornerShape(6.dp)).background(Color(0x99232220))
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }

        Column(Modifier.padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 30.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(14.dp)).background(CardBg)
                        .clickableNoRipple {
                            pickMore.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.PhotoLibrary, null, tint = Ink2, modifier = Modifier.size(18.dp))
                        Text("从相册添加", fontSize = 12.5.sp, color = Ink2, modifier = Modifier.padding(start = 6.dp))
                    }
                }
                Box(
                    Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(14.dp)).background(Accent)
                        .clickableNoRipple { shoot() },
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CameraAlt, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Text(
                            if (vm.captureUris.isEmpty()) "拍照" else "继续拍",
                            fontSize = 12.5.sp, color = Color.White, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(14.dp))
                    .background(if (vm.captureUris.isEmpty()) SurfaceAlt else Ink)
                    .clickableNoRipple { if (vm.captureUris.isNotEmpty()) vm.importCapture() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (vm.captureUris.isEmpty()) "拍一张后开始识别" else "开始识别（${vm.captureUris.size} 张）",
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    color = if (vm.captureUris.isEmpty()) Muted else Color.White
                )
            }
        }
    }
}
