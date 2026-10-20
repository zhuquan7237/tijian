package com.zhuquan.tijian.ui

import androidx.compose.animation.core.CubicBezierEasing

/**
 * 全站动效 token。原则来自 Emil Kowalski：
 * - UI 动效控制在 300ms 内；按压反馈 100-160ms。
 * - 进入用强 ease-out（起步快，立刻有反馈），移动用 ease-in-out，退出比进入快。
 * - 只动 transform / opacity，不动布局属性。
 */
object Motion {
    /** 按压、图标状态切换 */
    const val Fast = 120

    /** 常规 UI 过渡：面板、徽标、颜色 */
    const val Base = 180

    /** 页面切换上限（<300ms） */
    const val Page = 260

    /** 强 ease-out：进入 */
    val Enter = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

    /** ease-in-out：屏幕内位移 */
    val Move = CubicBezierEasing(0.77f, 0f, 0.175f, 1f)

    /** 退出：比进入更快、更直接 */
    val Exit = CubicBezierEasing(0.4f, 0f, 1f, 1f)
}
