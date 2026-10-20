# 题笺 8.6.0：全局按压反馈 + 图片残影修复

## 一、根因

### 1. 点击反馈弱
`bounceClick` / `tjClickable` 的 `graphicsLayer` 缩放被写在 modifier 链的**末尾**。Compose 的 modifier 只影响它**之后**的绘制，所以 `.clip().background().border().bounceClick()` 里的缩放只作用在一个空的点击节点上——背景、卡片、图片根本没缩。视觉上就只剩一圈很淡的 ripple，感觉“点了没反应”。

定位依据：逐条检查 SubjectTile / SubjectWideRow / RecentCard 的 modifier 顺序，三者都是先画背景再挂 `bounceClick`；把顺序调换后按压缩放才实际可见。

### 2. 图片残影
页面切换时给**整页**做 `alpha` 淡出。页面里同时有 Coil `AsyncImage`、WebView、阴影图层，alpha 合成层在动画帧之间会保留上一帧的图片纹理：文字已经淡掉，图片还挂在旧位置，形成拖影。旧版还叠了 0.96 alpha 的双页交叉，进一步放大这个问题。

定位依据：
- 10 倍慢放截帧，旧列表页文字已变淡时缩略图仍保持原亮度；
- 把整页 alpha 去掉、改成“位移 + 纯色遮罩压暗”后，同一机位截帧里缩略图跟着页面一起移动、一起变暗，不再脱离页面；
- 动画结束帧（`gframes4/f130.png`）干净，无任何旧图残留。

## 二、修改

- `ui/AppRoot.kt`
  - 页面不再做 alpha 动画，`alpha` 恒为 1，只用 `translationX / scale` 做 RikkaHub 的 push/pop 几何。
  - 旧页“退后变暗”改用纯色遮罩 `Box(...graphicsLayer { alpha = ... }.background(Color.Black))`，遮罩是纯色层，不会残留图片纹理。
  - 修正遮罩的 modifier 顺序（graphicsLayer 必须在 background 之前，否则遮罩永远全黑——这个 bug 在自查截图中被抓到并修复）。
  - tab 仍用 FlClash 的整屏横滑。
- `ui/Theme.kt`
  - 新增 `Modifier.pressScale(interactionSource)`：必须放在链首，缩放才能包住背景和图片。
  - `IconCircle`、`TjChip` 接入按压缩放；`bounceClick` 参数改为 0.95、120ms。
- `ui/Controls.kt`：`TjButton` 按压 `scale(0.97)`。
- `ui/HomeScreens.kt`：学科卡片、最近导入卡片把 `bounceClick` 移到链首；分类题目行加 `pressScale`。
- `ui/SettingsScreens.kt` / `ui/LibraryScreens.kt`：设置行、知识库文档行加 `pressScale`。

## 三、统一交互规则

- 普通按钮 / 图标按钮：`scale 0.97`，120ms，强 ease-out。
- 卡片 / 列表行 / 学科入口：`scale 0.985`，120ms；卡片类 0.95。
- Chip：`scale 0.96`。
- 底部 tab：0.90 + 轻触感；主按钮 0.97。
- 页面切换：260–300ms，只动 transform / opacity（且页面本身不再动 alpha）。
- 所有按压反馈在 ACTION_DOWN 即触发，不等业务逻辑。

## 四、验证

- 构建：`assembleDebug` 通过。
- 仪器测试：23/23；lint 0 error。
- 设备验证（模拟器 1080×2400）：
  - 10x 慢放进入详情：旧列表缩略图随页面左移并变暗，无脱离残影（`gframes4/f085.png`）。
  - 动画结束：只剩详情页，左侧无残留（`f130.png`）。
  - 快速“进详情→返回→再进→返回”：无黑屏、无残影、无半透明覆盖（`85-rapid.png`）。
- 未验证：真机（尤其低端 GPU）与 iOS 端；WebView 内部图片的极端场景。

## 五、遗留风险

- 低端设备上整页 `scale` 仍可能掉帧；若出现，可对详情页关闭 scale，只保留位移。
- 图片量特别大的页面（几十张缩略图）在快速切换时仍可能出现单帧解码延迟；后续可给 Coil 设置固定 `memoryCachePolicy` 和 `crossfade(false)`。
