package com.zhuquan.tijian.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhuquan.tijian.data.CategoryRow
import com.zhuquan.tijian.data.subjectGroup

@Composable
fun SubjectScreen(vm: AppVm, route: Route.Subject) {
    var creating by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<CategoryRow?>(null) }
    Column(Modifier.fillMaxSize()) {
        TjTopBar(
            route.name,
            if (route.name == "专业课") "材料类 · 材料科学基础（上交第三版）" else "按知识点管理子分类",
            onBack = { vm.back() },
            actions = { IconCircle(Icons.Rounded.Add, "新增子分类") { name = ""; creating = true } }
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 4.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val children = vm.categories.filter { it.subjectGroup() == route.name }
            if (children.isEmpty()) {
                CardBox(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp)) {
                        Text("还没有子分类", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                        Text(
                            "点击右上角 + 新建；AI 识别后也会归类到这里。",
                            fontSize = 12.sp, lineHeight = 18.sp, color = Ink2, modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }
            children.forEach { cat ->
                SubjectChildCard(cat, onOpen = { vm.go(Route.Category(cat.id)) }, onDelete = { deleting = cat })
            }
        }
    }
    if (creating) {
        AlertDialog(
            onDismissRequest = { creating = false },
            title = { Text("新增子分类 · " + route.name, fontSize = 16.sp, color = Ink) },
            text = {
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, label = { Text("子分类名称") },
                    placeholder = { Text("例如：晶体结构", color = Muted) },
                    singleLine = true, colors = tjFieldColors(), modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    vm.db.ensureCategory(route.name + "/" + name.trim(), auto = false); vm.refresh(); creating = false
                }) { Text("创建", color = Accent, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = { TextButton(onClick = { creating = false }) { Text("取消", color = Ink2) } }
        )
    }
    deleting?.let { cat ->
        AlertDialog(
            onDismissRequest = { deleting = null }, title = { Text("删除“" + cat.name + "”？", fontSize = 16.sp) },
            text = { Text("题目不会删除，将移至其他 / 未分类。", fontSize = 12.sp, color = Ink2) }, confirmButton = {
                TextButton(onClick = { vm.db.deleteCategory(cat.id); vm.refresh(); deleting = null }) {
                    Text("删除", color = Red)
                }
            }, dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消", color = Ink2) } }
        )
    }
}

@Composable
private fun SubjectChildCard(cat: CategoryRow, onOpen: () -> Unit, onDelete: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CardBg)
            .border(1.dp, Line, RoundedCornerShape(16.dp)).clickableNoRipple(onOpen)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 18.dp, top = 16.dp, bottom = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(cat.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(if (cat.count > 0) Accent else Line))
                    Text(
                        "${cat.count} 题 · 已掌握 ${cat.mastered}", fontSize = 11.5.sp, color = Ink2,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "打开", tint = Muted, modifier = Modifier.size(22.dp))
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).clickable(
                    interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDelete
                ), contentAlignment = Alignment.Center
            ) { Icon(Icons.Rounded.DeleteOutline, "删除子分类", tint = Red.copy(alpha = 0.82f), modifier = Modifier.size(19.dp)) }
        }
    }
}
