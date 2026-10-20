# 题笺 0.8.0 验收报告

日期：2026-10-20 · 版本 0.8.0 (versionCode 80000)

## 本轮修复

| # | 反馈 | 改动 | 验证 |
|---|------|------|------|
| 1 | 导入后要有「这道题需要知道哪些知识点才能做出来」，第 1 张详情页没有 | 详情页**始终**显示「要做出这题，需要掌握」；没有数据时给「让 AI 分析这题需要掌握什么」按钮，点一下补全公式/概念/方法并落库；导入编辑弹窗新增公式/概念/方法三栏可改；AI 提示词本来就要求 need_formulas/concepts/methods | 详情出现该区块 → 点按钮 → 显示「公式/概念/导数的几何意义/方法」（`docs/ui/v080/03-need.png`、`04-need-filled.png`） |
| 2 | 悬浮窗渲染不全、不知道在问哪道题、滑动卡顿、没有快捷消息 | 面板重做：① 顶部新增「当前题目」卡片，题干按 RichText 渲染（最多 96dp，超出裁切）；② 消息区从 LazyColumn 改为 Column + verticalScroll，WebView 在聊天里 `interactive=false`，不再抢滚动手势；③ 用户气泡改用 RichText，公式不再裸露；④ 输入框上方常驻 4 个快捷追问，点一下直接发送 | 面板出现「当前题目」；点「这步为什么成立？」立即发送并收到回答（`02-chat.png`）；`interactive=false` 后 WebView 不再拦截滚动 |
| 3 | 长按会弹系统「复制/全选/网页搜索」菜单且不消失；ABCD 选项长按没反应 | 关闭 WebView 原生选择（`user-select:none` + `contextmenu/selectstart preventDefault`），改成 JS 自己检测 420ms 长按，用 `caretRangeFromPoint` 找到手指下的段落整段回传，只弹应用自己的「复制 / 追问」 | 长按题干/AI 回答只出现应用底部工具条，不再出现系统菜单（`01-longpress-option.png`）；选项行与正文走同一套 block 命中 |
| 4 | 回答被截断后 `$$...$$` 裸露、公式变纯文本 | ① 聊天 max_tokens 1600 → 3000；② 渲染器把未闭合的 `$$` / `\[` / `\(` 整段兜底成公式，KaTeX 失败显示「[公式]」而不是源码；③ 剩余孤立 `$` 直接清掉 | 渲染器 JS 通过 `node --check`；截断样本按公式兜底 |
| 5 | 知识库「编辑」应是重新选分类再生成，不是改文字 | 文档菜单新增「重新选择分类生成」：弹出分类多选（默认勾选原分类），确认后按新范围重新提炼并覆盖原文档；原「编辑文字内容」保留 | 生成 1 个分类的文档 → 重新选 2 个分类 → 标题变为「函数与导数 · 极值点偏移」（`05-regen.png`） |

## 代码位置

- `assets/richtext/renderer.html`：自定义长按选段、屏蔽系统选择菜单、未闭合公式兜底
- `ui/RichText.kt`：新增 `interactive` 参数（聊天内 WebView 不接管触摸）
- `ui/ChatOverlay.kt`：面板信息架构重做（当前题目 / 消息 / 快捷追问 / 引用 / 输入）
- `ui/HomeScreens.kt`：需要掌握区块常驻 + AI 补全入口
- `ai/AiClient.kt`：`analyzeNeeds()`；聊天回答 max_tokens 提高
- `ui/AppVm.kt`：`fillNeeds()`、`regenerateKnowledge()`
- `ui/LibraryScreens.kt`：文档菜单「重新选择分类生成」、分类多选支持预选
- `ui/ImportReview.kt`：校对编辑新增公式/概念/方法

## 自动化

- `lintDebug`：0 error
- `connectedDebugAndroidTest`：19/19

## 关于 Astra

按要求把关键源码交给 `gpt-6-astra` 做设计审查，但该 key 现在返回 **HTTP 403**（额度/权限已失效），审查请求未能完成；本轮界面调整由我直接完成。
