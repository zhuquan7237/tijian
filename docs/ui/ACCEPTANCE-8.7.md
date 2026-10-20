# 题笺 8.7.0：离场页冻结快照，彻底消除切换残影

## 一、为什么 8.6 的修法没解决

8.6 去掉了整页 alpha，改成位移 + 纯色遮罩。理论上不会再出现 alpha 合成层的残帧，但**旧页仍然是活的**：WebView 在它自己的渲染管线里继续出帧，Coil 图片、阴影 RenderNode 也各自独立提交。页面被 `graphicsLayer` 平移/缩放时，父层（Compose RenderNode）已经动了，子层（WebView 的 Chromium 合成器）可能还停在上一帧——这就是“文字走了、图片还在”的来源。这类问题在 Android 上属于 WebView + 硬件加速的已知缺陷：

- StackOverflow「Android WebView flicker bug while animating view on device」(q/12806094)：WebView 在硬件加速下播放位移动画会闪烁/残留，最高票答案是 `setLayerType(LAYER_TYPE_SOFTWARE, null)`，代价是 WebView 内失去硬件加速。
- StackOverflow「hardware accelerated webview slide-in animation flickering on ICS」(q/9132749)：同样结论，软件层能消除闪烁但拖慢 WebView。
- 生产级阅读器（如 Readest）在 Android 翻页时不用“移动 WebView”，而是把当前页截成位图再翻——翻的是图片，不是活的 WebView。

## 二、采用的方案：离场页冻结成位图

不再让旧页参与动画。切换开始时：

1. 把整个页面容器录进一个 `GraphicsLayer`（`drawWithContent { pageLayer.record { drawContent() } }`）；
2. 在 p=0 的那一帧用 `pageLayer.toImageBitmap()` 截成 `ImageBitmap`（Compose 1.7 API）；
3. 立即把旧页从绘制中移除（`translationX = 2 * width`），屏幕上只保留这张**静态位图**，让它按原几何做位移/缩放/压暗；
4. 新页照常活体滑入；
5. 动画结束后释放位图。

位图不含任何会继续出帧的渲染管线，WebView、AsyncImage、阴影都不可能“留在后面”。这不是用动画掩盖问题，而是从根上取消了“旧页在动画期间继续渲染”这件事。

## 三、实现位置

- `ui/AppRoot.kt`
  - 新增 `rememberGraphicsLayer()` + `captureArmed` 录制开关；
  - `LaunchedEffect(vm.route)`：先 `snapTo(0f)`，等两帧让 p=0 画面落定，`toImageBitmap()` 截帧，再 `animateTo(1f)`；
  - `exitSnapshot` 非空时，非当前页不再绘制（`translationX = 2W`）；
  - 快照按 `navDir` 决定层级：push 时在页面下方，pop 时在页面上方（否则返回第一帧会露出被裁掉一半的上一页）；
  - 快照沿用原几何：push 左移半屏 + 缩到 0.7 + 0.36 压暗；pop 右移整屏；tab 按 FlClash 方向整屏横滑。
- 截帧失败（`runCatching` 返回 null）时自动退回 8.6 的活页动画，不会白屏。

## 四、验证

- 10 倍慢放返回（详情 → 分类列表，`pop5.mp4` / `pframes5`）：
  - 过渡中旧详情页内容（含题目原图与解析）完整出现在快照里，随页面右移；
  - 起始帧左侧只露出正确宽度的列表页，无半屏错位；
  - 结束帧只剩列表，无任何旧页文字/图片（`f137.png`）。
- 10 倍慢放进入（列表 → 详情）：快照正确，旧列表不残影。
- 截帧耗时实测约 50ms（10 倍慢放下从按键到开始位移约 15 帧），肉眼不可感。
- 23/23 仪器测试、lint 0 error（见发布记录）。
- 未验证：真机（用户的小米 Turbo 3）实际观感；WebView 内含视频等独立 Surface 的场景（本应用没有）。

## 五、取舍

- 每次切换多一次 ~10MB 位图分配与一次 GPU 读回，约 50ms 起步延迟；换来的是任何设备上都不可能出现旧页残影。
- 快照是静态的：切换期间旧页里的输入光标、加载动画会停住。本应用的旧页在切换瞬间本来就该离场，这个代价可接受。
- 低端机若 50ms 截帧过慢，可退回活页动画（代码已保留兜底）。
