package com.zhuquan.tijian

import com.zhuquan.tijian.ui.Normalize
import com.zhuquan.tijian.ui.cleanMathLite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** LaTeX 归一化与列表卡片里的轻量公式转写。 */
class UiTextProcessingTest {

    @Test fun normalizeConvertsParenDelimiters() {
        assertEquals("若 \$x>1\$ 则", Normalize.text("""若 \(x>1\) 则"""))
    }

    @Test fun normalizeConvertsBracketDelimiters() {
        assertEquals("\$\$E=mc^2\$\$", Normalize.text("""\[E=mc^2\]"""))
    }

    @Test fun normalizeUnwrapsDoubleEscapedCommands() {
        assertEquals("""$\frac{1}{2}$""", Normalize.text("""$\\frac{1}{2}$"""))
    }

    @Test fun plainStripsMarkup() {
        val p = Normalize.plain("# 标题 \$x^2\$ **粗体**")
        assertFalse(p.contains("$"))
        assertFalse(p.contains("#"))
        assertFalse(p.contains("*"))
    }

    @Test fun liteSuperscript() {
        assertEquals("x²", cleanMathLite("x^2"))
    }

    @Test fun liteFractionExponentStaysWhole() {
        // 8.9.2 修复过的回归：x^{2/3} 不能被读成 x²/3
        assertEquals("x^(2/3)", cleanMathLite("x^{2/3}"))
    }

    @Test fun liteFrac() {
        assertEquals("(a+b)/(c)", cleanMathLite("""\frac{a+b}{c}"""))
    }

    @Test fun liteSqrt() {
        assertEquals("√(x+1)", cleanMathLite("""\sqrt{x+1}"""))
    }

    @Test fun liteGreekSymbols() {
        assertTrue(cleanMathLite("""$\alpha+\beta$""").contains("α"))
        assertTrue(cleanMathLite("""$\alpha+\beta$""").contains("β"))
    }

    @Test fun liteSubscript() {
        assertEquals("x₁+x₂", cleanMathLite("x_1+x_2"))
    }

    @Test fun liteIntegralWithBounds() {
        // 8.11.0：积分上下限组合，字母上标退回 ^(…) 而不是逐字符猜
        val out = cleanMathLite("""$\int_0^{x-y}(x-y-t)f(t)dt$""")
        assertTrue(out.contains("∫"))
        assertTrue(out.contains("₀"))
        assertTrue(out.contains("^(x-y)"))
    }

    @Test fun liteSubscriptBracesFallback() {
        assertEquals("x_(i=1)", cleanMathLite("x_{i=1}"))
    }

    @Test fun liteSingleLetterSuperscriptStaysUnicode() {
        assertEquals("eˣ", cleanMathLite("e^{x}"))
        assertEquals("x³", cleanMathLite("x^{3}"))
    }

    @Test fun liteParenDelimitersStripped() {
        val out = cleanMathLite("""\(x>1\)""")
        assertFalse(out.contains("\\"))
        assertTrue(out.contains("x>1"))
    }

    @Test fun litePartialDerivative() {
        val out = cleanMathLite("""$\frac{\partial F}{\partial x}$""")
        assertTrue(out.contains("∂"))
        assertTrue(out.contains("F"))
        assertTrue(out.contains("/"))
    }
}
