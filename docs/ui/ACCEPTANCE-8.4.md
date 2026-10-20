# 题笺 8.4.0 过渡动画（对齐 RikkaHub）

## 参考来源

本机 `D:\hermes_workspace\rikkahub-src`，`app/src/main/java/me/rerere/rikkahub/RouteActivity.kt` 第 318-340 行：
Navigation 3 `NavDisplay` 的 `transitionSpec` / `popTransitionSpec`。

原文：

```kotlin
transitionSpec = {
    if (backStack.size == 1) fadeIn() togetherWith fadeOut()
    else slideInHorizontally { it } togetherWith
        slideOutHorizontally { -it / 2 } + scaleOut(targetScale = 0.7f) + fadeOut()
},
popTransitionSpec = {
    slideInHorizontally { -it / 2 } + scaleIn(initialScale = 0.7f) + fadeIn() togetherWith
        slideOutHorizontally { it }
}
```

## 移植方式

题笺没有用 Navigation 3，而是自己维护 `pages` + 一条 `Animatable(0f → 1f)`（300ms，FastOutSlowInEasing），按进度还原同样的几何关系：

- **进入**：新页 `x = (1-p)·W`（从右侧整屏推入）；旧页 `x = -p·W/2`、`scale = 1-0.3p`、`alpha = 1-p`。
- **返回**：上一页 `x = -(1-p)·W/2`、`scale = 0.7+0.3p`、`alpha = p`；当前页 `x = p·W`（整屏右移退出）。
- **同级 tab**：`alpha` 0→1 淡入，旧页保持不透明，不做位移。
- 层级：进入时新页在上；返回时当前页在上，滑出后露出上一页。

## 验证

- 动画 10 倍慢放截帧：
  - `40-push-mid.png`：详情页从右侧推入约 72%，列表页左移并淡化。
  - `41-pop-mid.png`：详情页右移只剩约 20%，列表页从左侧 0.7 缩放淡入到接近全屏。
- 不再有空白帧、不再硬切；进入/返回/同级共用同一条动画进度。
- versionName 8.4.0 / versionCode 8040000。
