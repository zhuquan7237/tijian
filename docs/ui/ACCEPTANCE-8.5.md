# 题笺 8.5.0 —— software-design 工作流落地

本轮按新建的 `software-design` bundle 执行：frontend-design 定方向、ui-ux-pro-max 校对设计系统、Emil Kowalski 的动效原则做细节。

## 动效

- 新增 `ui/Motion.kt`：统一 `Fast 120ms / Base 180ms / Page 260ms` 和三条曲线（Enter `cubic-bezier(.23,1,.32,1)`、Move、Exit）。
- 页面 push/pop 从 300ms 收到 260ms，进入曲线换成强 ease-out；一级 tab 仍用 fade-through。
- 所有按钮（`TjButton`）增加 `scale(0.97)` 按压反馈，120ms；底部 tab 保持压缩 + 触感。
- 掌握度 0–5：选中段底色、文字色、`scale(1.04)` 都有 120–180ms 过渡，不再是硬切。
- 追问面板打开：遮罩淡入 + 面板上移 24dp，180ms，不再瞬间出现。
- 追问悬浮球默认落在右下、底栏之上（96dp），不再压在学科卡片中间。

## 设计系统

- `docs/ui/DESIGN-SYSTEM.md`：颜色/对比度、字号层级、间距刻度、圆角、动效 token、组件规则。
- 原则：单一强调色只用于可操作；一级列表用分隔线而不是每行一张卡；不从 `scale(0)` 进入；UI 动效 ≤300ms。

## 验证

- 首页、详情、追问面板截图检查无错位（`60-home.png`、`61-detail.png`、`62-chat-mid.png`）。
- 23/23 仪器测试、lint 0 error。
