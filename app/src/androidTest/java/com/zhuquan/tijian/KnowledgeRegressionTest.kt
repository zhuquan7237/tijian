package com.zhuquan.tijian

import com.zhuquan.tijian.data.knowledgeMarkdown
import com.zhuquan.tijian.data.parseKnowledgeTags
import com.zhuquan.tijian.ui.Normalize
import org.junit.Assert.*
import org.junit.Test

/** Pure regressions for fields that previously disappeared between list/detail/export. */
class KnowledgeRegressionTest {
    @Test fun knowledgeTagsWithLongChineseAndLatexStayComplete() {
        val dollar = "$"
        val raw = "[\"隐函数求导 / 切线方程 / 截距与最值判断\",\"极限与洛必达法则\",\"参数范围 " + dollar + "a>1/e" + dollar + "\"]"
        val tags = parseKnowledgeTags(raw)
        assertEquals(3, tags.size)
        assertTrue(tags[0].contains("截距与最值判断"))
        assertTrue(tags[2].contains("a>1/e"))
    }

    @Test fun knowledgeJsonVariantsProduceStableMarkdown() {
        val json = org.json.JSONObject().apply {
            put("title", "切线")
            put("points", org.json.JSONArray().apply {
                put(org.json.JSONObject().apply {
                    put("name", "隐函数")
                    put("summary", "两边求导")
                    put("key_points", org.json.JSONArray().put("对 x^{3/2} 求导").put(org.json.JSONObject().put("text", "链式法则")))
                    put("pitfalls", org.json.JSONArray().put(org.json.JSONObject().put("content", "漏写 y'")))
                })
                put(org.json.JSONObject().apply {
                    put("title", "截距")
                    put("definition", "令 y=0")
                    put("key_points", org.json.JSONArray().put("横截距"))
                    put("pitfalls", org.json.JSONArray().put("对调"))
                })
            })
        }.toString()
        val md = knowledgeMarkdown(json)
        assertTrue(md.startsWith("# 切线"))
        assertTrue(md.contains("## 1. 隐函数"))
        assertTrue(md.contains("- 链式法则"))
        assertTrue(md.contains("- 漏写 y'"))
        assertTrue(md.contains("## 2. 截距"))
    }

    @Test fun normalizeDoesNotDropInlineLatex() {
        val d = "$"
        val src = "原式 " + d + "=\\lim_{x\\to1}\\dfrac{1}{x}" + d + "，且 \\(x>0\\)。"
        val out = Normalize.text(src)
        assertTrue(out.contains("\\lim"))
        assertTrue(out.contains("\\dfrac"))
        assertTrue(out.contains(d + "x>0" + d))
    }
}
