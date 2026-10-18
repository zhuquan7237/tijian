# 0.4.2 原图、编辑、分类与导航验证记录

环境：2026-10-18，Windows 本地构建；AtlasRefactorQA / emulator-5554，Android 17 API 37，1080×2400，host GPU。Kotlin + Jetpack Compose、自建 Route/AppVm、本地 WebView/KaTeX；未新增大型依赖。

本版使用实际有题干、答案 -2、长解析、表格及矩阵的题目验证。性能仍未达到完整流畅性验收标准，物理手机未验证；APK 用于实机复验，不宣称全部验收完成。

## 根因与修复

| 问题 | 根因 | 本版修改 |
| --- | --- | --- |
| 原图模糊、缺少全貌 | 导入时缩至 2200px 并 JPEG 重编码，AI bbox 裁图替代原图 | 原始文件逐字节保存、流式 SHA 去重；AI 单独生成缩略输入，不覆盖源图；取消 bbox 自动替换 |
| 无法查看和编辑 | 详情只有内嵌缩放，没有全屏查看器及裁剪入口 | 点击全屏，按可见区域解码源像素，双指缩放；四角/边框自由拖动、框内移动；裁剪另存 PNG，保留未裁剪源图 |
| 图片区域不能上下滚动 | 内嵌 detectTransformGestures 抢占单指拖动 | 内嵌图片只处理点击，详情负责纵向滚动；缩放和拖框放到独立查看器 |
| 裁剪保存按钮被底部挡住 | Dialog 窗口适配 inset 后内容再次补 inset | 完整窗口坐标，内容只应用一次 safeDrawing；实际截图检查保存按钮 |
| 贴边拖框触发返回 | 裁剪角点与系统 Back 边缘冲突 | 仅角点设置局部系统手势排除，其余边缘保留返回 |
| 关闭查看器崩溃 | 释放后仍收到绘制/解码回调 | 幂等 close、关闭标记、解码代次检查、串行释放；关闭后不绘制回收 bitmap |
| 备注难够到、编辑框不明显 | 右上角唯一入口、浅色轮廓与文字 | 右下浮动“编辑 / 备注”；增强正文、次级文字和输入轮廓；图片与正文分区间距 |
| 首页小类混杂且无增删入口 | 首页直接展示动态小类，同名类别全局复用 | 固定数学/专业课/英语/政治/其他；学科内增删子类；按学科+名称复用；删除保留题目，移至其他/未分类 |
| AI 分类层级不明确 | 提示词要求不带学科前缀，单题强制回退全局分类 | 要求先学科后小类，未知学科归其他；无小类归该学科/未分类，不跨学科复用 |
| 详情闪空、返回重排 | 路由销毁重建 WebView，未测量就转场 | 保留返回栈及三个根页的 live composition；详情等待非空富文本测量；旧页保持可见，加载失败可返回 |
| 切换残影、底栏闪动 | 整页透明合成透出旧文字，多底栏参与过渡 | 背景始终不透明，平级仅内容 .96→1 淡入 140ms，共用固定底栏；父子页 250/230ms 横向推进/返回 |
| 裁剪后列表仍是旧图 | 页面缓存只按题数刷新，编辑不改变题数 | dataRevision 刷新必要列表，保存持久化完成后更新页面 |

## 文件与关键实现

- data/Importer.kt：保留源文件字节，AI 输入独立缩放；data/ImageFiles.kt：8 种 EXIF 方向、显示坐标逆映射到源像素、BitmapRegionDecoder 局部解码、PNG 输出。裁剪区域超过 40MP 提示缩小，源图保留。
- data/Db.kt：schema 1→2 仅增加 original_id；originalImagePath/linkOriginal；按学科去重。data/Models.kt、ai/AiClient.kt：五大类和学科/小类输出。
- ui/ImageEditor.kt：原图区域解码、缩放/拖框、保存异常显示、资源释放、窗口 inset、角点手势排除。ui/CaptureScreen.kt、ui/AppVm.kt：拍照自动编辑，相册工作台点击编辑，等待落盘并关联源图后关闭。
- ui/HomeScreens.kt：图片点击、自然滚动、浮动编辑、间距及刷新。ui/SubjectScreen.kt、ui/ImportReview.kt：子类管理和完整分类路径。ui/Theme.kt：文字及边框对比度。
- ui/AppRoot.kt：层级/平级转场、有限缓存清理、准备状态、固定底栏。ui/RichText.kt：测量完成通知，保留原有尺寸修复。

关键代码：源图保存使用 raw.copyTo(File(dir, "$sha.original"))；裁剪使用 decoder.decodeRegion(rect, BitmapFactory.Options())；保存完成再执行 q = vm.saveQuestionImage(...) 和 vm.refresh()。

公式布局链路：AppRoot → 页面 Column → 单个纵向滚动区域 → 自适应 RichSection → 实际宽度 BoxWithConstraints → WebView → #out。沿用 CSS 高度按 dp 回传（不二次除 density）、文档代次检查、字体/图片/宽度/恢复后重测。卡片不固定内容高度、不缩字、不用空白补偿。超宽公式局部横向滚动。

导航由 updateTransition 的 currentState、targetState、isRunning 和详情 ready 标记协调。重复导航忽略，返回/平级切换可中断；仅保留返回栈及三个根页，退出完成清理。减少动态效果设置为 0 时无转场。弹窗保留独立动画。

## 实际测试

- assembleDebug、assembleRelease、assembleDebugAndroidTest 构建成功；lintDebug：0 errors、16 warnings，包含弃用 API 和自定义 View 设计时构造器提示。
- Android instrumentation 10 项通过：原图字节/3200×2400 尺寸、1600×1200 裁剪像素、源图关联/去重、EXIF 方向尺寸、同名跨学科小类、删除保留题目、v1→v2 迁移、原生拖框/重置、双指缩放实际绘制，以及四项富文本测试。详见 instrument-042-final.txt，22.041s。
- 富文本测试覆盖单行、20 行以上、嵌套根式/分式、积分/极限、多行推导、中英文、超宽公式、图文、空答案长解析、宽度/字体/恢复更新；断言原生高度和 CSS/公式边界。
- 模拟器交互：五个根类；新增/删除子类；有内容详情从图片起点上下滑动；浮动编辑打开有边框字段；裁剪保存及查看未裁剪源图；拍照完成自动打开编辑器并保存回工作台；字体 1.3、横竖屏；HOME/恢复；详情返回列表；进入中返回；连续根页导航。
- 字体/拍照/编辑截图部分来自本轮 debug APK；详情和导航另用 release APK 复测。不把 debug 截图冒称 final release 真机证据。证据置于 validation/0.4.2/。

## 性能与边界

最终代码的 release 预发布包，预热三个根页后无录屏连续 18 次切换：209 帧，71 超时帧（33.97%），P50 25ms、P90 38ms、P95 44ms、P99 53ms，slow UI thread 19、slow issue draw commands 65，详见原始 gfxinfo。仍未达到稳定 60fps，不能称为性能验收通过。另一次有录屏交互包含 12 次快速切换、实际详情、后台恢复、进入中返回及额外 30 次切换，脚本断言通过。

没有同条件修复前基线，不能用 debug+录屏与 release 无录屏的差异宣称优化比例。有限录屏和内存样本不能证明长期无泄漏或所有设备无闪屏。

本轮未验证物理手机、线上 AI 服务端到端、全部相册供应商/图片格式。区域解码由平台格式支持决定；旧版已压缩/裁掉的像素无法恢复，需重新导入。保存源图和裁剪副本增加磁盘占用，未自动删源文件以免丢失数据。
