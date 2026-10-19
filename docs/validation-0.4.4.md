# 0.4.4 PDF 完整性、图片比例、知识点与知识库修复验证记录

环境：2026-10-19，Windows 本地构建；AtlasRefactorQA / emulator-5554，Android 17 API 37，1080×2400。Kotlin + Jetpack Compose、自建 Route/AppVm、本地 WebView/KaTeX；未新增依赖。

本版处理用户截图中的六类问题：PDF 漏解析、导出图片高度被压扁、编辑器底部按钮在手机上显示不全、题目列表知识点被截断、详情页缺知识点、知识库多选/双层加载/公式源码直显。物理手机尚未验证。

## 根因与修复

| 问题 | 根因 | 本版修改 |
| --- | --- | --- |
| PDF 有解析但导出后丢失 | 题头、题干先绘制，图片按页面宽度铺满后把答案/解析挤到页外；绘制没有页边界保护，富文本失败后退回纯文本 | 改为题干 → 预留答案区 → 图片缩放 → 答案/解析；每次文本/图片绘制前 `ensure` 翻页；富文本失败时保留原始 LaTeX 并走可渲染路径 |
| PDF 导出图片上下压缩 | `image()` 只使用高度上限缩放 `h`，宽度仍取 `CONTENT_W`，宽高比被单独改变 | 统一按一个 `fittedScale` 同比例缩放宽高；高图按高度限制、宽图按内容宽限制 |
| `原图查看与编辑` 最下面按钮手机上显示不全 | 图片区用 `weight(1f)` 吃掉全部剩余高度，底部工具行在真实手机更高导航栏/字体下被挤到屏幕外 | 图片区固定为 0.68 权重、工具区 0.32 且可滚动；纵向补 navigationBars inset，工具行整体上移 |
| 题目列表知识点不完整 | 行高固定 68dp，`maxLines=1` + 省略号 | 行高 84dp；知识点标签可左右滑动，全部标签保留；长中文不再直接截掉 |
| 详情页没有知识点 | 详情只渲染题干、答案、解析 | 新增“知识点”卡片，标签自动换行完整展示 |
| 选择分类不能多选 | 知识库复用了单选题 `CategoryPicker`，点一行立刻回调并关闭 | 新增 `CategoryMultiPicker`：勾选不关闭、显示已选数量、统一“生成知识文档（N 个分类）”确认 |
| 提炼知识点出现双层加载 | 页面内 `BusyOverlay` 与 `AppRoot` 全局 `BusyOverlay` 同时渲染 | 知识库/文档页只保留一处；全局层排除 Library/Home/Doc 路由 |
| 知识文档公式随机未渲染 | 知识 JSON 的 `points`、`key_points`、`pitfalls` 存在多种嵌套；部分字段直接成为源码；渲染器遇模板异常直接输出 TeX | 统一 `knowledgeMarkdown()` 归一化对象/字符串；提示词要求 LaTeX；渲染器失败重试并显示可诊断标记而非源码 |

## 文件与关键实现

- `export/PdfExporter.kt`：题目内容顺序重排、页边界保护、等比图片缩放；不再无条件把每道题推到新页，标题与第一题共页。
- `export/RichBitmap.kt`：将离屏 WebView 挂到真实 Activity 窗口（原 Dialog 无窗口令牌导致 `BadTokenException` 并静默退回纯文本）；等待字体/布局稳定，容器与 WebView 同步到目标高度后再绘制。
- `ui/LibraryScreens.kt`：新增 `CategoryMultiPicker`；知识库删除重复 BusyOverlay。
- `ui/AppVm.kt`：`generateKnowledge(List<CategoryRow>)`，合并多分类题目、生成对应标题。
- `data/Models.kt`：`parseKnowledgeTags()`、`knowledgeMarkdown()`。
- `ui/HomeScreens.kt`：列表知识标签横滑；详情新增知识点卡片。
- `ui/ImageEditor.kt`：图片/工具区权重、滚动、导航栏保护。
- `assets/richtext/renderer.html`：公式失败重试与可诊断占位。
- `ai/AiClient.kt`：知识点 JSON 归一化与 LaTeX 输出要求。
- 测试：`ImageEditorLayoutTest` 新增 PDF 完整性、等比图片、富文本位图渲染；`KnowledgeRegressionTest` 覆盖长知识点、JSON 变体、行内公式。

## 实际测试

- `assembleDebug`、`assembleRelease`、`assembleDebugAndroidTest`、`lintDebug` 成功；lint 0 errors、17 warnings。
- Android 仪器测试 19 项全部通过（51.359s，`docs/validation/0.4.4/instrument-044.xml`）：原有 13 项 + 6 项新增（PDF 有图题、PDF 公式题、富文本公式位图、长知识点、知识 JSON、Normalize）。
- 真实 PDF 渲染检查：`pdf-complete.pdf`（900×1500 图）逐页渲染显示题干、图片、答案、解析完整，图片宽高比未变形；`pdf-rich.pdf` 的题干、答案、解析和 `\lim/\dfrac` 公式均出现在同一页，无源码直显。
- 模拟器交互：知识库多选勾选两个分类保持打开并显示“生成知识文档（2 个分类）”；详情页知识点标签完整；编辑器在 1080×2400 下裁剪/画笔/撤销/重置/保存全部可见。
- 证据截图：`docs/validation/0.4.4/`。

## 性能与边界

本版未重新录制动效性能基准。物理手机、线上 AI 服务、全部相册/图片格式未验证；旧版已压缩或裁剪的图片无法恢复丢失像素。知识文档的旧记录不会被自动重写，需重新生成才能应用新的 JSON/LaTeX 规则。
