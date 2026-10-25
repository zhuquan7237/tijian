package com.zhuquan.tijian

import com.zhuquan.tijian.data.colorFor
import com.zhuquan.tijian.data.contentHash
import com.zhuquan.tijian.data.knowledgeMarkdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 题目内容指纹（去重）与分类配色。 */
class DataFingerprintTest {

    @Test fun sameContentSameHash() {
        assertEquals(
            contentHash("下列说法正确的是", """["A.对","B.错"]""", "A"),
            contentHash("下列说法正确的是", """["A.对","B.错"]""", "A"),
        )
    }

    @Test fun sameStemDifferentOptionsIsNotDuplicate() {
        // 8.10.0 的核心修复：通用短题干 + 不同选项必须是两道题
        assertNotEquals(
            contentHash("下列说法正确的是", """["A.甲","B.乙"]""", "A"),
            contentHash("下列说法正确的是", """["A.丙","B.丁"]""", "B"),
        )
    }

    @Test fun blankStemNeverHashes() {
        // 手动录入还没填题干时不参与判重，交给图片 sha 那层
        assertEquals("", contentHash("", "[]", ""))
        assertEquals("", contentHash("   ", "", ""))
    }

    @Test fun hashStableAcrossWhitespace() {
        assertEquals(contentHash("题干", "[]", "答"), contentHash(" 题干 ", "[]", "答 "))
    }

    @Test fun colorForNeverCrashesOnExtremeHash() {
        // 构造能命中各种 hashCode 的名字；关键是 Int.MIN_VALUE 一类值不越界
        val probe = listOf("", "a", "晶体结构", "\u0000", "测试" + Int.MIN_VALUE, "数学/函数")
        probe.forEach { name ->
            val hex = colorFor(name)
            assertTrue(hex.matches(Regex("#[0-9A-F]{6}")))
        }
    }

    @Test fun knowledgeMarkdownRendersPoints() {
        val raw = """{"title":"极限","points":[{"title":"洛必达","definition":"0/0 型","key_points":["先验证条件"],"pitfalls":["条件不满足就用"]}]}"""
        val md = knowledgeMarkdown(raw)
        assertTrue(md.contains("# 极限"))
        assertTrue(md.contains("## 1. 洛必达"))
        assertTrue(md.contains("先验证条件"))
        assertTrue(md.contains("条件不满足就用"))
    }

    @Test fun knowledgeMarkdownAcceptsFencedJson() {
        val raw = "```json\n{\"title\":\"T\",\"points\":[]}\n```"
        val md = knowledgeMarkdown(raw)
        assertTrue(md.contains("# T"))
    }
}
