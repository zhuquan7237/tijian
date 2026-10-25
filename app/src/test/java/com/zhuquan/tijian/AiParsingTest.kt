package com.zhuquan.tijian

import com.zhuquan.tijian.ai.AiClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** 模型返回的 JSON 提取与截断补齐——导入链路的第一道解析防线。 */
class AiParsingTest {

    @Test fun extractsPlainJson() {
        assertEquals("""{"a":1}""", AiClient.extractJson("""{"a":1}"""))
    }

    @Test fun stripsMarkdownFence() {
        val raw = "```json\n{\"questions\":[{\"stem\":\"1+1\"}]}\n```"
        assertEquals("""{"questions":[{"stem":"1+1"}]}""", AiClient.extractJson(raw))
    }

    @Test fun extractsJsonFromChatter() {
        val raw = "好的，以下是结果：{\"items\":[1,2]} 希望有帮助"
        assertEquals("""{"items":[1,2]}""", AiClient.extractJson(raw))
    }

    @Test fun ignoresBracesInsideStrings() {
        val raw = """{"text":"包含 } 和 { 的内容","n":2}"""
        assertEquals(raw, AiClient.extractJson(raw))
    }

    @Test fun escapesInsideStringsDoNotConfuseStack() {
        val raw = """{"text":"她说：\"{\"}","n":3}"""
        assertEquals(raw, AiClient.extractJson(raw))
    }

    @Test fun completesTruncatedJson() {
        val raw = """{"questions":[{"stem":"半截题","answer":"B""""
        val fixed = AiClient.extractJson(raw)
        assertNotNull(fixed)
        // 补齐后必须是可解析的 JSON
        org.json.JSONObject(fixed!!)
    }

    @Test fun returnsNullWhenNoJsonAtAll() {
        assertNull(AiClient.extractJson("抱歉，我无法识别这张图片。"))
    }
}
