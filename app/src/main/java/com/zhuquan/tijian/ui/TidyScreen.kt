package com.zhuquan.tijian.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhuquan.tijian.ui.AppVm.TidyProbe

/**
 * 一键大整理：旧版本导的题与现行规范不一致（分类乱、缺知识点标签、题型不规范），
 * 这里先本地预检给出问题清单，再由 AI 分批复核，统一归到当前版本的分类体系。
 * 只动分类 / 知识点标签 / 题型，不碰答案与解析。
 */
@Composable
fun TidyScreen(vm: AppVm) {
    var probe by remember { mutableStateOf<TidyProbe?>(null) }
    var confirmStart by remember { mutableStateOf(false) }

    LaunchedEffect(vm.dataRevision, vm.tidyRunning) {
        if (!vm.tidyRunning) probe = vm.probeTidy()
    }

    Column(Modifier.fillMaxSize()) {
        TjTopBar(
            title = "一键大整理",
            subtitle = "把旧题统一整理到最新规范",
            onBack = { if (!vm.tidyRunning) vm.back() },
        )
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState())
                .padding(horizontal = Space.page)
        ) {
            if (probe == null) {
                Box(Modifier.fillMaxWidth().padding(vertical = 60.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(26.dp), color = Accent, strokeWidth = 2.5.dp)
                }
            } else {
                val p = probe!!
                Text(
                    "本地预检 · 共 ${p.total} 道题",
                    fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink
                )
                Text(
                    "先扫一遍全库，再交给 AI 分批复核修正",
                    fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 3.dp)
                )
                Spacer(Modifier.height(14.dp))
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    TidyIssueRow(p.total - p.noTags, "知识点标签规范", "其余题目缺标签，会由 AI 补全")
                    TidyIssueRow(p.total - p.noSolution, "答案与解析齐全", "缺失或可疑的会由 AI 重写（先推理后结论）")
                    TidyIssueRow(p.total - p.uncat, "已归入有效分类", "未分类的题会由 AI 重新归档")
                    if (p.duplicates > 0) {
                        TidyIssueRow(0, "发现 ${p.duplicates} 道重复题", "内容相同的重复题会移入回收站（保留最早一道）")
                    } else {
                        TidyIssueRow(1, "没有重复题", "本地按内容指纹查重")
                    }
                    TidyIssueRow(
                        if (p.pendingCats == 0) 1 else 0,
                        if (p.pendingCats == 0) "分类树已确认" else "有 ${p.pendingCats} 个 AI 待确认分类",
                        "大整理后新分类会以「待确认」出现，可在分类页确认",
                    )
                }
                Spacer(Modifier.height(14.dp))
                CardBox(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("整理内容", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                        Text(
                            "· 分类：纠错归位，统一到「学科/小类」两级结构\n" +
                                "· 知识点标签：补全缺失、纠正过宽或重复\n" +
                                "· 题型：统一为选择 / 填空 / 解答等标准命名\n" +
                                "· 查重：内容相同的重复题移入回收站（保留最早一道）\n" +
                                "· 答案与解析：算错、缺漏、或「先给答案再补过程」的由 AI 重写（先推理后结论）",
                            fontSize = 12.sp, lineHeight = 20.sp, color = Ink2,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                        if (vm.provider.webSearch) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "已开启联网搜索：AI 复核时遇到不确定的知识点会先查证再下结论。",
                                fontSize = 11.5.sp, lineHeight = 17.sp, color = Green
                            )
                        }
                    }
                }
                if (p.total > 0) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "建议先在「备份与恢复」导出一份备份再开始；整理过程可随时停止，已完成部分保留。",
                        fontSize = 11.5.sp, lineHeight = 18.sp, color = Muted
                    )
                    Spacer(Modifier.height(14.dp))
                    TjButton(
                        "开始大整理（${p.total} 道题）", Modifier.fillMaxWidth(),
                        enabled = !vm.busy && !vm.importing,
                    ) { confirmStart = true }
                }
            }
            Spacer(Modifier.height(20.dp))
        }

        // 进行中状态卡：进度 + 停止
        if (vm.tidyRunning) {
            Column(
                Modifier.fillMaxWidth().background(CardBg).padding(horizontal = Space.page, vertical = 14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        progress = { vm.tidyProgress },
                        modifier = Modifier.size(26.dp), color = Accent, strokeWidth = 3.dp,
                        trackColor = SurfaceAlt,
                    )
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text("正在整理", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                        Text(vm.tidyLabel, fontSize = 11.5.sp, color = Muted, modifier = Modifier.padding(top = 2.dp))
                    }
                    TjButton("停止", style = ActionStyle.Danger) { vm.stopTidy() }
                }
            }
        } else {
            vm.tidyReport?.let { report ->
                Column(
                    Modifier.fillMaxWidth().background(CardBg)
                        .border(1.dp, Line, CardShape).padding(horizontal = Space.page, vertical = 14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CheckCircle, null, tint = Green, modifier = Modifier.size(18.dp))
                        Text(
                            report, fontSize = 12.5.sp, lineHeight = 19.sp, color = Ink,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }

    if (confirmStart) {
        AlertDialog(
            onDismissRequest = { confirmStart = false },
            title = { Text("开始大整理？", fontSize = 16.sp) },
            text = {
                Text(
                    "AI 会逐批复核全部题目并直接写回分类、知识点标签与题型。过程联网消耗 token，可随时停止。",
                    fontSize = 12.5.sp, lineHeight = 19.sp, color = Ink2
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmStart = false; vm.startTidy() }) {
                    Text("开始", color = Accent, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = { TextButton(onClick = { confirmStart = false }) { Text("再想想", color = Ink2) } }
        )
    }
}

@Composable
private fun TidyIssueRow(okCount: Int, title: String, hint: String) {
    val allGood = okCount > 0
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(if (allGood) GreenSoft else AmberSoft)
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (allGood) Icons.Rounded.CheckCircle else Icons.Rounded.HelpOutline,
            null, tint = if (allGood) Green else Amber, modifier = Modifier.size(17.dp)
        )
        Column(Modifier.weight(1f).padding(start = 9.dp)) {
            Text(title, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = Ink)
            Text(hint, fontSize = 11.sp, lineHeight = 16.sp, color = Ink2, modifier = Modifier.padding(top = 2.dp))
        }
        if (allGood) {
            Text("$okCount", fontSize = 15.sp, color = Green, fontWeight = FontWeight.SemiBold)
        }
    }
}
