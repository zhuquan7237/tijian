# 题笺 0.7.0 验收报告

日期：2026-10-20 · 版本 0.7.0 (versionCode 70000) · 1080×2400 模拟器 + 实机反馈修复

## 本轮修复（对照用户截图）

| # | 反馈 | 改动 | 证据 |
|---|------|------|------|
| 1 | 多选后没有删除入口，导错的题删不掉 | 分类页多选底栏新增「删除」，二次确认「删除所选 N 道题？」，删完自动退出多选 | `docs/ui/v070/07-multiselect.png`、`08-delete-confirm.png`、`09-after-delete.png`（3 道 → 1 道） |
| 2 | 拉起键盘后聊天面板出现大片白色，输入框位置不合理 | 面板从 `Dialog` 改为挂在 AppRoot 内的普通层，高度随键盘收缩；空对话按内容包裹，不再固定 430/640dp | `01-chat-empty.png`、`02-chat-keyboard.png` |
| 3 | 实机上输入框下半截被导航栏/键盘挡住 | 面板改用 AppRoot 的 `safeDrawing` 内边距（状态栏 + 导航栏 + 输入法都算进去），不再手写 `navigation_bar_height`；真机与模拟器同一套 inset 逻辑 | `14-quote-keyboard.png`（输入框 1993–2143，键盘在 2200 以下） |
| 4 | 对话窗只占一部分，空白区域点了不返回 | 遮罩可点击关闭；有对话时面板也保留顶部 88dp 空白条，随时可点外部关闭 | `11-qB-empty-chat.png`、外部点击验证脚本 |
| 5 | 对话历史串题 | 打开对话先清空上一题气泡，历史严格按 `question_id` 读写；A 题（函数与导数）有记录，B 题（极值点偏移）打开是空对话 | `11-qB-empty-chat.png`（B 题为空，无 A 题消息） |
| 6 | 引用的解析显示不全、公式变成原始字符 | 引用块改用 RichText 渲染，保留 Markdown/LaTeX 排版；去掉 `take(80)` + 2 行截断；高度上限 150dp，超出可滚动查看 | `13-quote.png`、`14-quote-keyboard.png` |

## 代码位置

- `ui/ChatOverlay.kt`：悬浮球 + 面板；无 Dialog；遮罩点击关闭；引用块 RichText
- `ui/AppVm.kt`：`deleteQuestions(ids)`；`openChat()` 换题先清空气泡
- `ui/HomeScreens.kt`：多选底栏「删除」+ 确认框
- `ui/AppRoot.kt`：`WindowInsets.safeDrawing`（已有，本轮让聊天面板复用它）

## 自动化

- `lintDebug`：0 error
- `connectedDebugAndroidTest`：19/19 通过（API 37 模拟器）

## 已知边界

- 引用块最多显示 150dp 高，超出部分滚动查看（避免长段落把对话挤没）。
- 系统自带的文字选择菜单（Translate / Copy / Select all）由 WebView 提供，与应用底部工具条并存。
