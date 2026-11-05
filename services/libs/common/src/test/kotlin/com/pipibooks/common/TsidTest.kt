package com.pipibooks.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class TsidTest {
    @Test
    fun `TSID는 시간순으로 증가한다`() {
        val ids = List(1000) { Tsid.next() }
        assertEquals(ids.sorted(), ids)
        assertEquals(1000, ids.toSet().size)
    }

    @Test
    fun `TSID 문자열은 왕복 변환된다`() {
        val id = Tsid.next()
        assertEquals(id, Tsid.parse(Tsid.format(id)))
    }

    @Test
    fun `숫자가 아닌 ID는 INVALID_ID로 거부된다`() {
        listOf("abc", "-1", "", "1.5", "9999999999999999999").forEach {
            assertEquals("INVALID_ID", assertThrows<ApiException> { Tsid.parse(it) }.code, it)
        }
    }
}
