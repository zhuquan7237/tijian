package com.zhuquan.tijian.ui

/**
 * AI 输出格式归一化。
 * 不同模型会混用 \(...\)、\[...\]、$$...$$，JSON 多层转义还会把 \frac 变成 \\frac。
 * 在进入渲染器前统一成 KaTeX 认识的形式，避免源码直显。
 */
object Normalize {

    private val cmds = listOf(
        "lim", "frac", "dfrac", "tfrac", "sqrt", "to", "infty", "ln", "log", "lg",
        "sin", "cos", "tan", "cot", "sec", "csc", "arcsin", "arccos", "arctan",
        "int", "iint", "iiint", "sum", "prod", "partial", "nabla",
        "alpha", "beta", "gamma", "delta", "epsilon", "varepsilon", "zeta", "eta",
        "theta", "lambda", "mu", "nu", "xi", "pi", "rho", "sigma", "tau", "phi",
        "varphi", "chi", "psi", "omega", "Delta", "Omega", "Sigma",
        "cdot", "times", "div", "pm", "mp", "le", "leq", "ge", "geq", "ne", "neq",
        "approx", "equiv", "sim", "propto", "left", "right", "text", "mathrm",
        "mathbf", "mathbb", "mathcal", "overline", "underline", "vec", "hat", "bar",
        "quad", "qquad", "begin", "end", "ce", "overset", "underset", "binom",
        "displaystyle", "limits", "big", "Big", "bigg", "Bigg"
    )

    fun text(input: String): String {
        var t = input.replace("\r\n", "\n")

        // \( \) → $ ；\[ \] → $$
        t = t.replace("\\(", "$").replace("\\)", "$")
        t = t.replace("\\[", "$$").replace("\\]", "$$")

        // JSON 多层转义：\\frac → \frac（不会碰矩阵换行 \\ 后接普通字母的情况）
        for (c in cmds) t = t.replace("\\\\$c", "\\$c")

        // 识别结果经常漏写 $，甚至写成 lim_(x→1)、e^(ln x) 这种半成品。
        // 先补成 KaTeX / JLatexMath 都能吃的 $...$，列表、详情和 PDF 走同一条。
        return promoteLooseMath(t)
    }

    /** 去掉 Markdown/LaTeX 标记，用于通知、标题等纯文本场景。 */
    fun plain(input: String): String = text(input)
        .replace(Regex("\\$\\$?"), "")
        .replace(Regex("\\\\([a-zA-Z]+)"), "$1")
        .replace(Regex("[#*_`>]"), "")
        .trim()
}

private val mathFuncs = listOf(
    "arcsin", "arccos", "arctan", "sinh", "cosh", "tanh",
    "lim", "log", "sin", "cos", "tan", "cot", "sec", "csc",
    "exp", "max", "min", "sup", "inf", "det", "ln", "lg",
)

/** 把 `^(...)` / `_(...)` 收成 `^{...}` / `_{...}`，括号可以嵌套。 */
internal fun parenScriptsToBraces(input: String): String {
    val sb = StringBuilder()
    var i = 0
    while (i < input.length) {
        val c = input[i]
        if ((c == '^' || c == '_') && i + 1 < input.length && input[i + 1] == '(') {
            val end = matchParen(input, i + 1)
            if (end > i + 1) {
                sb.append(c).append('{').append(parenScriptsToBraces(input.substring(i + 2, end))).append('}')
                i = end + 1
                continue
            }
        }
        sb.append(c)
        i++
    }
    return sb.toString()
}

private fun matchParen(s: String, open: Int): Int {
    if (open >= s.length || s[open] != '(') return -1
    var depth = 0
    for (i in open until s.length) {
        when (s[i]) {
            '(' -> depth++
            ')' -> {
                depth--
                if (depth == 0) return i
            }
        }
    }
    return -1
}

/**
 * 已有 $...$ / $$...$$ 原样保留。其余文本里，连续的公式碎片
 * （含 ^、_、反斜杠命令或箭头）收成一段 $...$。
 */
private fun promoteLooseMath(input: String): String {
    val out = StringBuilder()
    var i = 0
    val n = input.length
    while (i < n) {
        if (input.startsWith("$$", i) || input[i] == '$') {
            val marker = if (input.startsWith("$$", i)) "$$" else "$"
            val close = input.indexOf(marker, i + marker.length)
            if (close < 0) {
                out.append(input.substring(i))
                break
            }
            out.append(input, i, close + marker.length)
            i = close + marker.length
            continue
        }
        val end = looseMathEnd(input, i)
        if (end > i) {
            val raw = input.substring(i, end).trim()
            out.append('$').append(asciiMathToTex(raw)).append('$')
            i = end
            continue
        }
        out.append(input[i])
        i++
    }
    return out.toString()
}

private fun looseMathEnd(s: String, start: Int): Int {
    if (start >= s.length || !canStartMath(s[start])) return -1
    var i = start
    while (i < s.length && isMathChar(s[i])) i++
    while (i > start && s[i - 1].isWhitespace()) i--
    if (i <= start) return -1
    val raw = s.substring(start, i)
    if (!raw.any { it == '^' || it == '_' || it == '\\' || it == '→' || it == '≤' || it == '≥' || it == '∫' || it == '∑' || it == '√' || it == '∞' }) {
        return -1
    }
    return i
}

private fun canStartMath(c: Char): Boolean = c == '\\' || c.isLetterOrDigit() || c == '('

private fun isMathChar(c: Char): Boolean {
    if (c == ' ') return true
    if (c.code < 128) return c.isLetterOrDigit() || c in "\\^_+-*/=<>.,()[]{}|"
    return c in "→←↔⇒⇐∞≤≥≠≈·×−±√∫∑∏∂π"
}

private fun asciiMathToTex(raw: String): String {
    var t = raw
        .replace("→", "\\to ")
        .replace("←", "\\leftarrow ")
        .replace("⇒", "\\Rightarrow ")
        .replace("∞", "\\infty ")
        .replace("≤", "\\le ")
        .replace("≥", "\\ge ")
        .replace("≠", "\\ne ")
        .replace("≈", "\\approx ")
        .replace("×", "\\times ")
        .replace("·", "\\cdot ")
        .replace("−", "-")
        .replace("±", "\\pm ")
        .replace("√", "\\sqrt ")
        .replace("∫", "\\int ")
        .replace("∑", "\\sum ")
        .replace("∏", "\\prod ")
        .replace("∂", "\\partial ")
    t = parenScriptsToBraces(t)
    for (name in mathFuncs) {
        val cmd = "\\$name "
        t = Regex("(?<![A-Za-z\\\\])$name(?![A-Za-z])").replace(t) { cmd }
        if (name == "ln" || name == "log" || name == "sin" || name == "cos" || name == "tan" || name == "exp") {
            t = Regex("(?<=[A-Za-z0-9])$name(?![A-Za-z])").replace(t) { cmd }
        }
    }
    t = parenthesizedFractions(t)
    return t.replace(Regex("""[ \t]{2,}"""), " ").trim()
}

/** `(分子)/(分母)` → `\frac{分子}{分母}`，只认成对括号，避免把普通除号吃掉。 */
private fun parenthesizedFractions(input: String): String {
    val sb = StringBuilder()
    var i = 0
    while (i < input.length) {
        if (input[i] == '(') {
            val end = matchParen(input, i)
            if (end > i) {
                var j = end + 1
                while (j < input.length && input[j].isWhitespace()) j++
                if (j < input.length && input[j] == '/') {
                    var k = j + 1
                    while (k < input.length && input[k].isWhitespace()) k++
                    if (k < input.length && input[k] == '(') {
                        val end2 = matchParen(input, k)
                        if (end2 > k) {
                            val num = parenthesizedFractions(input.substring(i + 1, end))
                            val den = parenthesizedFractions(input.substring(k + 1, end2))
                            sb.append("\\frac{").append(num).append("}{").append(den).append('}')
                            i = end2 + 1
                            continue
                        }
                    }
                }
            }
        }
        sb.append(input[i])
        i++
    }
    return sb.toString()
}
