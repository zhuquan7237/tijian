package com.zhuquan.tijian.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.graphics.Color
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
    // 本地顺序：拖动过程中即时交换，松手落库；数据刷新后按新 sort 重读
    var children by remember(route.name) { mutableStateOf(vm.categories.filter { it.subjectGroup() == route.name }) }
    LaunchedEffect(vm.categories, route.name) {
        children = vm.categories.filter { it.subjectGroup() == route.name }
    }
    val listState = rememberLazyListState()
    val dragState = rememberDragDropState(listState) { fromKey, toKey ->
        val from = children.indexOfFirst { it.id == fromKey }
        val to = children.indexOfFirst { it.id == toKey }
        if (from >= 0 && to >= 0 && from != to) {
            children = children.toMutableList().apply { add(to, removeAt(from)) }
        }
    }
    Column(Modifier.fillMaxSize()) {
        TjTopBar(
            route.name,
            if (route.name == "专业课") "材料类 · 材料科学基础（上交第三版）" else "按知识点管理子分类 · 长按可拖动排序",
            onBack = { vm.back() },
            actions = { IconCircle(Icons.Rounded.Add, "新增子分类") { name = ""; creating = true } }
        )
        if (children.isEmpty()) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Space.page, vertical = 4.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
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
        } else {
            LazyColumn(
                Modifier.fillMaxSize().dragContainer(dragState) {
                    vm.write { it.reorderCategories(children.map { c -> c.id }) }
                }
                    .padding(horizontal = Space.page, vertical = 4.dp)
                    .padding(bottom = 24.dp),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(children, key = { _, c -> c.id }) { _, cat ->
                    DraggableItem(dragState, cat.id) { _ ->
                        SubjectChildCard(
                            cat,
                            onOpen = { if (!dragState.clickConsumed) vm.go(Route.Category(cat.id)) },
                            onDelete = { deleting = cat },
                        )
                    }
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
    if (creating) {
        AlertDialog(
            onDismissRequest = { creating = false },
            title = { Text("新增子分类 · " + route.name, fontSize = 16.sp, color = Ink) },
            text = {
                TjTextField(
                    value = name, onValueChange = { name = it }, label = { Text("子分类名称") },
                    placeholder = { Text("例如：晶体结构", color = Muted) },
                    singleLine = true, colors = tjFieldColors(), modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    val path = route.name + "/" + name.trim(); vm.write { it.ensureCategory(path, auto = false) }; creating = false
                }) { Text("创建", color = Accent, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = { TextButton(onClick = { creating = false }) { Text("取消", color = Ink2) } }
        )
    }
    deleting?.let { cat ->
        AlertDialog(
            onDismissRequest = { deleting = null }, title = { Text("删除“" + cat.name + "”？", fontSize = 16.sp) },
            text = { Text("题目不会删除，将移至其他 / 未分类。", fontSize = 12.sp, color = Ink2) }, confirmButton = {
                TextButton(onClick = { val catId = cat.id; vm.write { it.deleteCategory(catId) }; deleting = null }) {
                    Text("删除", color = Red)
                }
            }, dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消", color = Ink2) } }
        )
    }
}

@Composable
private fun SubjectChildCard(cat: CategoryRow, onOpen: () -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .bounceClick(label = cat.name, onClick = onOpen)
            .clip(CardShape)
            .background(CardBg)
            .border(1.dp, Line, CardShape)
            .padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(cat.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Ink)
            Row(Modifier.padding(top = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(SurfaceAlt)
                ) {
                    if (cat.count > 0) {
                        Box(
                            Modifier.fillMaxWidth((cat.mastered.toFloat() / cat.count).coerceIn(0f, 1f))
                                .height(4.dp).background(Green)
                        )
                    }
                }
                Text(
                    if (cat.count > 0) "${cat.count} 题 · 已掌握 ${cat.mastered}" else "暂无错题",
                    fontSize = 12.sp, color = Ink2,
                    modifier = Modifier.padding(start = 10.dp)
                )
            }
        }
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
                .tjClickable(label = "删除 ${cat.name}") { onDelete() },
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Rounded.DeleteOutline, null, tint = Red, modifier = Modifier.size(20.dp)) }
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight, null,
            tint = Muted, modifier = Modifier.size(20.dp).padding(end = 6.dp)
        )
    }
}
