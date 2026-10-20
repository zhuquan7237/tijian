# 题笺 0.8.1 验收报告

日期：2026-10-20 · 版本 0.8.1 (versionCode 80100)

## 本轮修复（对应实机截图）

| # | 反馈 | 改动 | 验证 |
|---|------|------|------|
| 1 | 对话窗口里长公式左右滑很卡，上下滑也卡 | 整段对话改为**一个 WebView** 渲染（`chat.html`）：消息、公式、表格都在同一个文档里，上下滚动和公式横向滚动全部交给 WebView 原生滚动，不再经过 Compose 的 LazyColumn/多 WebView 测量，也不再和 Compose 抢手势 | 对话里对比 `interactive`/多 WebView 版本，消息区滚动由 WebView 接管；公式块 `overflow-x:auto` 原生横滑 |
| 2 | 长按直接整片选中，选不了具体文字 | 长按从"整段 block"改为**按词选中**：`caretRangeFromPoint` 取手指下的文本节点，按中英文/数字边界扩到词；按住不放拖动可以**扩选**；仍然不弹系统菜单 | 长按"求导"位置 → 引用块出现「求导」；长按公式符号 → 单个符号（`docs/ui/v080/11-quote-word.png`） |
| 3 | 不知道对话属于哪道题，要缩略图 | 面板左上角改为**题目原图缩略图**（56dp，圆角），和列表缩略图一致；没有原图时退回题型首字 | `docs/ui/v080/08-chat-transcript.png`、`09-chat-msg.png` |

## 实现说明

- `assets/richtext/chat.html`：新增对话渲染器。Markdown + KaTeX 逻辑与 `renderer.html` 一致；消息以用户气泡 / AI 卡片排布；未闭合公式同样兜底。
- `ui/ChatTranscript.kt`：单个 WebView 承载全部消息，`__setMessages(json)` 更新，`AndroidChat.select/clearSelection` 回传选中文字。
- `ui/ChatOverlay.kt`：消息区改为 `ChatTranscript`；头部改成缩略图 + 题型/分类/掌握度；快捷追问、引用、输入保持不变。
- `assets/richtext/renderer.html`：详情页的长按选择同步升级为**按词 + 拖动扩选**，与聊天一致。

## 自动化

- `lintDebug`：0 error
- `connectedDebugAndroidTest`：19/19

## 已知取舍

- 聊天里的 WebView 现在自己滚动，Compose 不再参与消息区滚动；因此消息区不能再用 Compose 的下拉刷新等手势（没有这类需求）。
- 选中以"词"为单位，中文按连续汉字成词，公式按 KaTeX 文本节点成段；精细到单个字符可长按后拖动微调。
