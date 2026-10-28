package com.zhuquan.tijian.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.abs

class PerspectiveTransformTest {

    private fun assertNear(a: Float, b: Float, eps: Float = 1e-3f) {
        if (abs(a - b) > eps) throw AssertionError("expected $b ± $eps but was $a")
    }

    @Test
    fun `identity quad maps every point to itself`() {
        val t = PerspectiveTransform(
            floatArrayOf(0f, 0f, 100f, 0f, 100f, 100f, 0f, 100f),
            floatArrayOf(0f, 0f, 100f, 0f, 100f, 100f, 0f, 100f),
        )
        val out = FloatArray(2)
        for (x in listOf(0f, 13f, 50f, 77f, 100f)) for (y in listOf(0f, 31f, 50f, 88f, 100f)) {
            t.map(x, y, out)
            assertNear(out[0], x); assertNear(out[1], y)
        }
    }

    @Test
    fun `inset quad maps corners and center correctly`() {
        // 用户把四个角拖到 (25,25)(75,25)(75,75)(25,75)，输出 100x100
        val t = PerspectiveTransform(
            floatArrayOf(25f, 25f, 75f, 25f, 75f, 75f, 25f, 75f),
            floatArrayOf(0f, 0f, 100f, 0f, 100f, 100f, 0f, 100f),
        )
        val out = FloatArray(2)
        t.map(25f, 25f, out); assertNear(out[0], 0f); assertNear(out[1], 0f)
        t.map(75f, 25f, out); assertNear(out[0], 100f); assertNear(out[1], 0f)
        t.map(75f, 75f, out); assertNear(out[0], 100f); assertNear(out[1], 100f)
        t.map(25f, 75f, out); assertNear(out[0], 0f); assertNear(out[1], 100f)
        // 对称中心不变
        t.map(50f, 50f, out); assertNear(out[0], 50f); assertNear(out[1], 50f)
        // 中心与右边缘中点线性关系（仿射子集情形下投影退化为中心对称缩放）
        t.map(62.5f, 50f, out); assertNear(out[0], 75f); assertNear(out[1], 50f)
    }

    @Test
    fun `trapezoid perspective is not affine but corners hold`() {
        // 梯形（上窄下宽）→ 矩形：真正的透视校正场景
        val t = PerspectiveTransform(
            floatArrayOf(30f, 20f, 70f, 20f, 90f, 80f, 10f, 80f),
            floatArrayOf(0f, 0f, 100f, 0f, 100f, 100f, 0f, 100f),
        )
        val out = FloatArray(2)
        t.map(30f, 20f, out); assertNear(out[0], 0f); assertNear(out[1], 0f)
        t.map(70f, 20f, out); assertNear(out[0], 100f); assertNear(out[1], 0f)
        t.map(90f, 80f, out); assertNear(out[0], 100f); assertNear(out[1], 100f)
        t.map(10f, 80f, out); assertNear(out[0], 0f); assertNear(out[1], 100f)
        // 投影不保持中点（源 y=50 的中点 (50,50) 不映射到 (50,50)），
        // 但必须保持交比：源水平线上四点 (20,50)(50,50)(80,50)(140,50) 的交比 1.5 不变。
        val cr = fun(x1: Float, x2: Float, x3: Float, x4: Float): Float =
            ((x3 - x1) / (x3 - x2)) / ((x4 - x1) / (x4 - x2))
        val srcCr = cr(20f, 50f, 80f, 140f)
        val p = FloatArray(2)
        t.map(20f, 50f, p); val u1 = p[0]
        t.map(50f, 50f, p); val u2 = p[0]
        t.map(80f, 50f, p); val u3 = p[0]
        t.map(140f, 50f, p); val u4 = p[0]
        assertNear(cr(u1, u2, u3, u4), srcCr, eps = 1e-2f)
    }
}
