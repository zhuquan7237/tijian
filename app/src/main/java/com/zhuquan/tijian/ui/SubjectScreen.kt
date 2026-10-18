package com.zhuquan.tijian.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zhuquan.tijian.data.*

@Composable
fun SubjectScreen(vm: AppVm, route: Route.Subject) {
    var creating by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<CategoryRow?>(null) }
    Column(Modifier.fillMaxSize()) {
        TjTopBar(route.name, "按知识点管理子分类", onBack = { vm.back() }, actions = {
            IconCircle(Icons.Rounded.Add, "新增子分类") { name = ""; creating = true }
        })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            val children = vm.categories.filter { it.subjectGroup() == route.name }
            if (children.isEmpty()) Text("还没有子分类，点击 + 新建；AI 识别后也会自动归档。", color = Ink2)
            children.forEach { cat ->
                CardBox {
                    Column(Modifier.fillMaxWidth().padding(18.dp)) {
                        TextButton(onClick = { vm.go(Route.Category(cat.id)) }) { Text(cat.name + " · " + cat.count + " 题", color = Ink) }
                        if (cat.id != Db.UNCAT) TextButton(onClick = { deleting = cat }) { Text("删除子分类", color = Red) }
                    }
                }
            }
        }
    }
    if (creating) AlertDialog(onDismissRequest = { creating = false }, title = { Text("新增子分类 · " + route.name) }, text = {
        OutlinedTextField(name, { name = it }, label = { Text("子分类名称") }, singleLine = true)
    }, confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = {
        vm.db.ensureCategory(route.name + "/" + name.trim(), auto = false); vm.refresh(); creating = false
    }) { Text("创建") } }, dismissButton = { TextButton(onClick = { creating = false }) { Text("取消") } })
    deleting?.let { cat -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("删除“" + cat.name + "”？") },
        text = { Text("题目不会删除，将移至其他 / 未分类。") }, confirmButton = { TextButton(onClick = {
            vm.db.deleteCategory(cat.id); vm.refresh(); deleting = null
        }) { Text("删除", color = Red) } }, dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } }) }
}
