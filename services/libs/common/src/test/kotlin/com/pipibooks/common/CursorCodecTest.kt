package com.pipibooks.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.Base64

class CursorCodecTest {
    @Test
    fun `커서는 왕복 변환된다`() {
        val fields = mapOf("score" to "0.5", "id" to "123", "제목" to "한글")
        assertEquals(fields, CursorCodec.decode(CursorCodec.encode(fields)))
    }

    @Test
    fun `깨진 커서는 INVALID_CURSOR로 거부된다`() {
        val e = assertThrows<ApiException> { CursorCodec.decode("%%%") }
        assertEquals("INVALID_CURSOR", e.code)
    }

    @Test
    fun `null JSON 커서는 INVALID_CURSOR로 거부된다`() {
        val cursor = Base64.getUrlEncoder().withoutPadding().encodeToString("null".toByteArray())
        assertEquals("INVALID_CURSOR", assertThrows<ApiException> { CursorCodec.decode(cursor) }.code)
    }

    @Test
    fun `객체가 아닌 JSON 커서는 INVALID_CURSOR로 거부된다`() {
        listOf("[1,2]", "42", "\"x\"", """{"a":{"b":1}}""").forEach {
            val cursor = Base64.getUrlEncoder().withoutPadding().encodeToString(it.toByteArray())
            assertEquals("INVALID_CURSOR", assertThrows<ApiException> { CursorCodec.decode(cursor) }.code, it)
        }
    }
}
