# 题笺 8.5.1 过渡动画：RikkaHub push/pop + FlClash tab

## 详情页 push / pop（回到 RikkaHub）

按 `rikkahub-src/app/.../RouteActivity.kt` 的 `NavDisplay` 几何关系：

- 进入：新页 `x = (1-p)·W`（从右整屏推入）；旧页 `x = -p·W/2`、`scale = 1-0.3p`、`alpha = 1-p`。
- 返回：当前页 `x = p·W`（整屏右移退出）；上一页 `x = -(1-p)·W/2`、`scale = 0.7+0.3p`、`alpha = p`。
- 时长 300ms，`FastOutSlowInEasing`（与 RikkaHub 的默认观感一致）。

## 一级 tab（照 FlClash 的 PageView）

源码：`D:\FlClash\lib\pages\home.dart` 的 `_HomePageView`。

- 三个一级页常驻，切换是**整屏横向滑动**：新页从对应方向推入，旧页同速退出，两页都不透明。
- 方向由 tab 顺序决定（错题本 0 / 知识库 1 / 我的 2），不是简单的“前进/后退”。
- 两页 alpha 始终为 1，没有交叉淡化，所以不会出现文字重影或白屏。
- 底部导航栏在动画期间由顶层覆盖一条同样的 bar，保持固定不动；页面里的 bar 跟着滑但被盖住，动画结束撤掉覆盖层，不跳动。
- 时长 300ms，`FastOutSlowInEasing`（对应 FlClash 的 `Curves.easeOut`）。

## 验证

- 10x 慢放截帧 `70-tab-slide.png`：错题本在左、知识库从右推入，底栏固定，无重影、无白屏。
- 页面 push/pop 几何未变，仍为 RikkaHub 参数。
- 知识已同步进 `compose-page-transitions` skill（新增 FlClash 一节）。
