# 0.4.1 内容显示与页面转场修复记录

验证日期：2026-10-18。技术栈为 Kotlin、Jetpack Compose、自建 Route/AppVm 导航、Android WebView 内的本地 Markdown-it/KaTeX 渲染。

## 结论与验证边界

已在正在运行的 AtlasRefactorQA 模拟器（Android 17，1080×2400）安装 release APK，查看数据库里实际有内容的题目、答案和长解析；不以空白卡片作为验收证据。下方截图来自 release APK，不是设计稿或测试宿主。

内容尺寸回归测试通过，实际题目的答案 -2、分式推导、两行表格及矩阵均能显示，长页面可滚动。放大字体后，超宽公式右端可通过公式内部横向拖动访问。横竖屏往返及 HOME 后恢复保留了解析浏览位置。

**转场性能尚未达到完整验收标准。** 本模拟器本轮导航采样 173 帧，超时帧 80（46.24%），P50 46ms、P90 101ms、P95 150ms。没有同条件修复前基线，不能宣称帧率提升或达到 60fps，也不能仅凭模拟器断言实机流畅。此次发布用于交付已验证的内容及导航修复，实机性能仍需复验。

## 一、具体根因

1. **高度单位重复换算**：WebView 的 viewport 与 CSS px 对应 dp，JavaScript 上报 CSS 高度；旧 Compose 代码却把该值当物理像素调用 toDp()，在高密度屏幕上再次除以 density。原生 WebView 高度小于实际排版高度，父卡片裁掉题干、答案和解析。不是公式数据本身少了内容。
2. **宽度估计不等于实际可用宽度**：按全屏宽减固定边距生成 HTML，没有包含所有父级内边距，造成换行及高度测量失配。
3. **高度反馈循环**：body/document 的 scrollHeight 至少等于当前 WebView viewport 高度。原生高度反过来影响测量，内容变短也可能保留旧高度。应只测内在内容节点 #out。
4. **旧文档回调写入新尺寸**：AndroidView factory 创建的桥接回调比当前文档活得更久，异步加载、切题和字体变化时可能写入过期高度。
5. **公式及表格布局**：行内公式需要把高分式尺寸计入行盒，超宽公式需要自身滚动；原有对公式插入换行/提升块级的处理还会破坏 Markdown 表格行结构。
6. **图片固定展示高度、间距不足**：原图统一 320dp 高度，与图片比例无关；图片、标题、正文缺少分层间距。
7. **页面图层及状态**：路由动画根节点缺少统一不透明满屏表面，可能透出退场文字；返回位移与 z 顺序不一致。普通 remember 在卸载后丢失筛选、搜索状态。
8. **滚动恢复被异步高度清零**：新建 WebView 尚未回传高度时仅占 1dp，ScrollState 先把保存位置截到临时范围。横竖屏切换会错误回到顶部。

## 二、布局链路与关键代码

详情：AppRoot 满屏不透明 Box → AnimatedContent → 路由满屏 Box → QuestionDetailScreen Column → 顶栏及单一 verticalScroll Column → RichSection 自适应 Column → BoxWithConstraints → AndroidView/WebView → #out → 段落/公式/图片。

RichSection 保留圆角裁剪，但不设置内容固定高度、最大行数或截断。WebView 高度来自实际测量，外层页面负责纵向滚动；仅公式及表格内部横向滚动。没有全局放开 overflow 或缩小字号。

RichText.kt 关键逻辑：

~~~kotlin
val widthDp = maxWidth.value.roundToInt().coerceAtLeast(1)
// CSS px 在当前 viewport 下对应 dp，不再调用 toDp()
Modifier.height(height.intValue.dp)
~~~

rememberUpdatedState 保持桥接指向当前状态，主线程检查 renderId 后更新高度；恢复及宽度变化触发重测，释放时清理 bridge 并销毁 WebView。

renderer.html 仅测量 #out：

~~~javascript
return Math.ceil(Math.max(el.getBoundingClientRect().height, el.scrollHeight));
~~~

ResizeObserver、MutationObserver、字体及图片加载、窗口 resize/恢复触发 requestAnimationFrame 合并上报，同高度去重。行内公式 inline-block 参与行高，超宽公式局部横向滚动，保留 Markdown 表格结构。

AppRoot 统一选择 A+B：

- Home、Library、Settings 平级：新页初始 alpha 0.96、140ms 淡入；旧页 80ms 淡出。底栏在 AnimatedContent 外。
- 层级前进：新页从右进入、旧页向左移动 1/3 宽，250ms。
- 层级返回：当前页向右退出、上一页从左侧 1/3 宽恢复，230ms；返回 z 顺序相反。
- AnimatedContent 管理进入/退出及目标变更，无任意延时定时器；相同路由重复导航直接忽略。系统动画比例为 0 时无转场。
- SaveableStateHolder 只保留当前页、返回栈及三个底栏根页，退出栈的页面清理；不无限保留 WebView。
- 详情独立保存用户滚动 offset，内容范围增长后恢复，不把布局临时截断当作用户滚动写回。

## 三、修改文件

| 文件 | 修改 |
| --- | --- |
| app/src/main/java/com/zhuquan/tijian/ui/RichText.kt | 真实宽度、高度单位、文档代次、字体缩放、生命周期、释放 |
| app/src/main/assets/richtext/renderer.html | 内在高度测量、重测、行内/块级公式、表格 |
| app/src/main/java/com/zhuquan/tijian/export/RichBitmap.kt | 同步桥接签名及内在测量 |
| app/src/main/java/com/zhuquan/tijian/ui/HomeScreens.kt | 原图比例、详情/列表/近期卡片间距、滚动及筛选搜索恢复、加载状态 |
| app/src/main/java/com/zhuquan/tijian/ui/ImportReview.kt | 图片与正文间距 |
| app/src/main/java/com/zhuquan/tijian/ui/AppRoot.kt | A+B、背景、返回图层、有限状态保存 |
| app/src/main/java/com/zhuquan/tijian/ui/AppVm.kt | 状态保留规则 |
| app/src/main/java/com/zhuquan/tijian/ui/Theme.kt | 记住 interaction source，防止重组重建按压状态 |
| app/src/main/res/values/themes.xml、values-v27/themes.xml | 分离 API 27 导航栏属性，解决 minSdk 26 的 Lint 错误 |
| app/src/debug/AndroidManifest.xml、debug/java/.../RichTextRegressionActivity.kt | 仅 debug 的非导出测试宿主 |
| app/src/androidTest/java/com/zhuquan/tijian/RichTextLayoutTest.kt | 内容尺寸回归 |
| app/build.gradle.kts | 测试依赖、runner、版本 0.4.1 / 40100 |

## 四、实际测试

~~~text
gradlew.bat lintRelease assembleRelease connectedDebugAndroidTest
BUILD SUCCESSFUL
~~~

4 项仪器测试，0 failures、0 errors：

1. 10 组内容：单行、25 段长文、嵌套分式根式、积分与极限、多行推导、矩阵、中英文公式、50 项长公式、图片文字、两行公式表格。
2. 35 段解析替换为短答案：高度收缩，等待 1 秒后不反馈增长。
3. 清空后重现 30 段公式解析：空内容移除旧 WebView，后续尺寸正确。
4. 宽度 300→240dp、字体比例 1→1.5、暂停/恢复后重新排版。

每次以唯一标记确认当前文档，等待字体及图片完成，校验原生高度/density 与 CSS 高度误差 <1.1dp，检查 KaTeX 错误和公式 base 上下边界；长公式必须可横向滚动，表格必须保留两行。

Lint：0 errors、12 warnings，无全局忽略或 baseline。release APK 签名 v2、zipalign -c -P 16 4 验证通过；沿用现有签名配置。

从模拟器提取已安装 base.apk，与待发布 app-release.apk 比对 SHA-256 完全相同：652187cd72d1da6e130dc447e679a6aa2bbe2d71733e4f79333cfa1198ea6e4f。包名 com.zhuquan.tijian，versionName 0.4.1，versionCode 40100。确认验证对象就是待发布包。

本轮导航的原始帧统计：[gfxinfo-after.txt](validation/0.4.1/gfxinfo-after.txt)。

release APK 实际操作：

- “极限 / 洛必达法则”题干、答案 -2、长解析末尾表格/化学式/矩阵；从 WebView 正文开始滑动可自然纵向滚动。
- “导数与单调性”题干、A–D 选项、答案 C 完整。
- 系统字体 1.5，滑动解析并横向拖动公式，看到右端 =-2。
- 横竖屏往返及 HOME 后恢复解析位置。结束恢复 font_scale=1.0、accelerometer_rotation=1、user_rotation=0。
- 5 轮题目进入后立即系统返回，每轮确认回到 3 道题的分类页。
- 18 次连续底栏切换，最终回到错题本，导航状态一致。
- 额外录制平级、分类→题目→分类→首页，检查抽帧图。抽帧未观察到整屏空白，不代表逐帧证明无闪烁；仍有异步内容出现及模拟器掉帧。

## 五、正式包截图

| 场景 | 截图 |
| --- | --- |
| 题干及答案 | [release-detail.png](validation/0.4.1/release-detail.png) |
| 长解析末尾 | [release-analysis-bottom.png](validation/0.4.1/release-analysis-bottom.png) |
| 选择题 | [release-choice.png](validation/0.4.1/release-choice.png) |
| 大字体公式右端 | [release-formula-horizontal.png](validation/0.4.1/release-formula-horizontal.png) |
| 横竖屏恢复 | [release-portrait-restored.png](validation/0.4.1/release-portrait-restored.png) |
| 后台恢复 | [release-resume.png](validation/0.4.1/release-resume.png) |

## 六、尚未验证及未完成

- **没有完成物理真机验证。** 不同厂商 WebView、较老 Android、不同物理屏幕和高刷新率设备需复验。
- 转场帧耗时不达标，不能声称彻底消除卡顿；没有同条件修复前基线及实机性能跟踪。
- 重进页面仍可能新建 WebView、重新排版公式；状态保存不等于缓存已绘制页面。未实现重型内容初始化与转场的完整调度。
- 未做长时间内存趋势采样。结束单点 PSS 137966KB 不能证明没有泄漏。
- 未调用线上 AI 识别；异步替换由仪器测试覆盖，不能宣称实时识别链路已通过。
- PDF 导出仅同步接口，未重做视觉回归；拍照、文件导入、删除、迁移未完整回归，未更改既有题目数据。
- 未新增返回手势；使用系统返回键及现有返回路径，未覆盖所有系统手势组合。
- 此次聚焦内容显示、间距及转场，不代表早先全部功能和全界面重设计均已完成。
