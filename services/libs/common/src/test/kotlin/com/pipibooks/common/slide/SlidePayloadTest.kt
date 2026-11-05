package com.pipibooks.common.slide

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tools.jackson.module.kotlin.jacksonObjectMapper

class SlidePayloadTest {
    private val mapper = jacksonObjectMapper()

    @Test
    fun `슬라이드 페이로드는 type 필드로 직렬화된다`() {
        val json = mapper.writeValueAsString(SlidePayload.Hook(listOf("a")))
        assertEquals("""{"type":"HOOK","sentences":["a"]}""", json)
    }

    @Test
    fun `슬라이드 페이로드는 type 필드로 역직렬화된다`() {
        val back = mapper.readValue("""{"type":"QUOTE","text":"t","page":3}""", SlidePayload::class.java)
        assertEquals(SlidePayload.Quote("t", 3), back)
    }
}
