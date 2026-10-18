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

        return t
    }

    /** 去掉 Markdown/LaTeX 标记，用于通知、标题等纯文本场景。 */
    fun plain(input: String): String = text(input)
        .replace(Regex("\\$\\$?"), "")
        .replace(Regex("\\\\([a-zA-Z]+)"), "$1")
        .replace(Regex("[#*_`>]"), "")
        .trim()
}
