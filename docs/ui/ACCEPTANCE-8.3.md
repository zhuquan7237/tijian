# 题笺 8.3.0 验收记录

## 1. 长按选区拖手柄失效
- 根因：`onDestroyActionMode()` 里调用了 `removeAllRanges()`。WebView 在拖动左右手柄时会先销毁再重建 ActionMode，这一下把正在调整的选区直接抹掉，手柄和菜单一起消失。
- 修复：ActionMode 销毁时不再清选区，只在「复制 / 追问」真正执行后清一次。
- 验证：长按选中公式里的 `3` → 出现高亮、两个手柄、复制/追问菜单 → 拖右手柄 260px → 选区扩大、手柄跟随、菜单仍在（`docs/ui/v082/29-drag.png`）。

## 2. 过渡动画
参考 Material Motion 的三种模式（container transform / shared axis / fade through）：
- **同级 tab**（错题本 / 知识库 / 我的）：fade-through，无横向位移；旧页 100ms 淡出，新页延迟 90ms、220ms 淡入。
- **进入 / 返回详情**：shared-axis X，约 4% 屏宽位移 + 先淡出后淡入。
- 旧版重影来自两页以 0.96 alpha 长时间叠加；现在旧页会完整淡到 0。

## 3. 按钮反馈
- 底部 tab：按下 0.90 压缩（高刚度弹簧）+ 触感反馈，图标/胶囊颜色 160ms 过渡。
- `bounceClick`：0.95 压缩 + 回弹，替换原来 0.976/110ms 的“软绵”参数。

## 4. 筛选切换闪屏
- 根因：`rows` 用 `remember(id, filter)`，切筛选瞬间先置空再加载，列表闪一下白。
- 修复：保留旧列表，等新查询返回再整体替换；切换时平滑回到顶部。

## 5. 详情页速度与滚动
- 三份 WebView（题目 / 答案 / 解析）合并为 **一份 HTML 文档**，内部用 `.rt-card` 还原白卡样式：KaTeX 只初始化一次，滚动时只有一个 WebView 参与测量。
- 去掉“等所有 WebView ready 才显示页面”的门禁：点进题目立即出顶栏和原图，正文随后填入。
- 验证：详情页三个卡片正常渲染（`21-detail-doc.png`、`23-detail-scroll.png`），公式正常。

## 自动化
- lint 0 error
- connectedDebugAndroidTest 23/23
- versionName 8.3.0 / versionCode 8030000
- 签名与 8.2.0 一致，可覆盖升级

## 截图
- `21-detail-doc.png` 合并后的详情文档
- `23-detail-scroll.png` 滚动到答案/解析
- `29-drag.png` 拖动手柄后选区保持
- `31-filter.png` 筛选切换
