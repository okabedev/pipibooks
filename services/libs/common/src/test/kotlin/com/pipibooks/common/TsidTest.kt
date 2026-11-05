package com.pipibooks.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

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
}
