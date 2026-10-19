# 题笺 0.6.0 验收报告

日期：2026-10-19 · 版本 0.6.0 (versionCode 60000) · 设备 1080×2400 / 420dpi（AtlasRefactorQA）

## 一、本轮交付

| # | 需求 | 实现 | 证据 |
|---|------|------|------|
| 1 | 用 Astra 生成多套 UI 模板并评分选优 | `docs/ui/astra/v1..v3.html/png`，v2 得分最高（4/5），把 2×2 学科网格、石墨色主视觉、浅色主按钮、17sp 分区标题合并进现有体系 | `astra/v2.png`、`v060/11-home-v2-late.png` |
| 2 | 追问 AI 悬浮窗（可移动、全局） | `ChatOverlay.kt`：可拖动悬浮球 + 底部对话面板，所有页面可用；带题目上下文、掌握度、最近对话 | `v060/31-chat-open2.png` |
| 2a | 记录掌握程度 | `buildQuestionContext()` 每次提问都读取题目当前 `mastery` 与「需要掌握」三类知识点，导入/调整掌握度后自动反映到后续回答 | 代码 + `v060/34/35` 对话 |
| 2b | 结合掌握度回答 | 同上，system 提示要求优先补「没掌握的公式/概念/方法」 | `v060/35-chat-reply2.png` |
| 2c | 长按已渲染解析 → 复制 / 追问 | WebView 里 `__RT_SELECTABLE__` 监听 selectionchange，把**渲染后**的文字回传原生；详情页底部出现「已选中 / 复制 / 追问」工具条，追问自动带引用 | `v060/13-selection-bar.png`、`v060/14-chat-quoted.png` |
| 3 | 识别结果预览 | 校对卡片「预览」打开全屏预览，题干/选项/答案/解析/需要掌握全部走 RichText（Markdown、表格、LaTeX 与详情页一致） | `v060/23b-preview-now.png` |
| 4 | 需要掌握哪些知识点 | 数据层新增 `needFormulas/needConcepts/needMethods`（DB v4 迁移），AI 提示词要求输出三类；详情页新增「要做这题，需要掌握」卡片 | `v060/12-selection.png` |
| 5 | 知识库编辑 / 删除 / 增删分类 | 文档行「更多操作」菜单：编辑内容、导出 PDF、删除（带确认）；编辑页可改标题、正文、分类，新建分类入口保留 | `v060/20-library-menu.png`、`v060/21-doc-edit.png` |
| 6 | 上下滑动把选择面板拖走 | 所有 ModalBottomSheet 内容套 `blockSheetDrag()`（nestedScroll 预消费过滚），列表在面板内部滚动 | `v060/26-sheet-before.png` → `v060/28-sheet-after-drag2.png`（两次上滑后仍在） |

## 二、界面评分（Astra 三套）

| 方案 | 风格 | 评分 | 结论 |
|------|------|------|------|
| v1 | 学术编辑风：大字号标题、纸质留白 | 3/5 | 信息密度偏低，复习卡不够聚焦 |
| **v2** | **冷灰纸面 + 石墨主视觉 + 钴蓝点缀，2×2 学科网格** | **4/5** | **采用**：层级清楚、数字更克制、主按钮更轻 |
| v3 | 夜间专注风：深色底、荧光强调 | 2.5/5 | 与本地题库的长时间阅读场景冲突，对比过强 |

合并进现有「墨白 Editorial」的具体改动：`Ink #202733`、`Accent #355CCE`、`AccentSoft #EDF2FF`、
主视觉 `#252F40` 圆角 16dp、到期数字 40sp、主按钮改浅蓝底蓝字、分区标题 17sp/25sp。

## 三、自动化检查

- 单元测试：`testDebugUnitTest`（本仓库暂无 JVM 单测，NO-SOURCE）
- Lint：`lintDebug` **0 error / 23 warning**
- 仪器测试：`connectedDebugAndroidTest` **19/19 通过**（API 37 模拟器，含 DB v1→v4 迁移、PDF 导出、富文本渲染）
- 富文本渲染器：题干/答案/解析/预览/聊天共用同一 `renderer.html`，公式与表格表现一致

## 三·补｜0.6.1 修复

- 知识文档编辑弹窗加 `imePadding()`：输入法浮条不再遮住「保存」，实机验证保存后标题即时更新（`docs/ui/v060/60-edit-ime.png`、`61-after-save.png`），删除后知识库回到 0 篇（`65-library-state.png`）。

## 四、已知边界

- 聊天头部的题目摘要为单行纯文本（去掉 LaTeX 记号），完整公式在详情/预览/回答里渲染。
- 悬浮球首次冷启动位置在屏幕右侧 58% 高度，可拖动并记忆在本次会话内。
- 预览与详情都依赖系统 WebView；无 WebView 的设备会退化为纯文本。

## 五、复现命令

```bash
export JAVA_HOME=<JDK17>
export ANDROID_HOME=<Android SDK>
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
python docs/ui/verify_features.py   # 截图：选中追问 / 知识库菜单 / 编辑页
```
