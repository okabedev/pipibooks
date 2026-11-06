package com.pipibooks.catalog.draft

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class KeywordExtractorTest {
    @Test
    fun `명사 끝의 이는 조사로 떼지 않는다`() {
        val keywords = KeywordExtractor.extract("고양이 어린이", "원숭이와 호랑이를", null)
        assertEquals(listOf("원숭이", "호랑이", "고양이", "어린이"), keywords)
    }

    @Test
    fun `조사를 떼고 두 글자 이상 남을 때만 뗀다`() {
        assertEquals(listOf("사랑", "책을"), KeywordExtractor.extract("사랑을 책을", null, null))
        assertEquals(listOf("동물", "나무"), KeywordExtractor.extract("동물들 나무가", null, null))
    }

    @Test
    fun `불용어는 조사를 뗀 뒤에 걸러진다`() {
        val keywords = KeywordExtractor.extract("우주", "이야기를 이야기가 이야기는 우주를 우주", null)
        assertEquals(listOf("우주"), keywords)
    }

    @Test
    fun `책은과 책이는 키워드가 아니다`() {
        val intro = "책은 책이 책을 책의 책도 책과 책에 책으로 우주"
        assertEquals(listOf("우주"), KeywordExtractor.extract("책", intro, "책은 우주"))
    }
}
