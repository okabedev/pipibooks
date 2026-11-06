package com.pipibooks.catalog.draft

import com.pipibooks.common.slide.SlidePayload
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RuleBasedCardDraftGeneratorTest {
    private val generator = RuleBasedCardDraftGenerator()

    private val intro =
        "숲속에서 만난 작은 동물들이 계절마다 건네는 이야기를 담았다. 겨울이 오면 나무는 조용히 숨을 고르고 " +
            "동물들은 서로의 체온으로 밤을 견딘다! 이 책은 느리게 사는 법을 숲에서 배우는 이야기이다. " +
            "숲과 나무, 동물과 계절의 순환을 따라가며 독자는 잊고 지낸 고요를 만난다."
    private val toc = "1. 봄의 숲\n2. 여름의 나무\n3. 가을의 동물\n4. 겨울의 고요"

    private fun slides(
        introduction: String? = intro,
        toc: String? = this.toc,
        kdc: String? = "8",
        coverUrl: String? = "https://cdn.example.com/c.jpg",
    ) = generator.generate(
        sampleBook(introduction = introduction, tableOfContents = toc, kdc = kdc, coverUrl = coverUrl),
    )!!

    @Test
    fun `소개가 없으면 초안을 만들지 않는다`() {
        assertNull(generator.generate(sampleBook(introduction = null)))
        assertNull(generator.generate(sampleBook(introduction = "  ")))
    }

    @Test
    fun `다섯 슬라이드를 정해진 순서로 만든다`() {
        val s = slides()
        assertEquals(
            listOf(
                SlidePayload.Cover::class,
                SlidePayload.Hook::class,
                SlidePayload.Info::class,
                SlidePayload.Keywords::class,
                SlidePayload.ForWhom::class,
            ),
            s.map { it::class },
        )
        assertEquals("rule-based", generator.name)
        assertEquals(1, generator.version)
        val info = s[2] as SlidePayload.Info
        assertEquals("문학", info.kdcLabel)
        assertEquals("푸른출판", info.publisher)
    }

    @Test
    fun `HOOK은 소개에서 문장 1~2개를 고른다`() {
        val hook = slides().filterIsInstance<SlidePayload.Hook>().single()
        assertTrue(hook.sentences.size in 1..2)
        assertTrue(hook.sentences.all { it.length in 20..120 })
        assertEquals("숲속에서 만난 작은 동물들이 계절마다 건네는 이야기를 담았다.", hook.sentences.first())
    }

    @Test
    fun `HOOK은 알맞은 문장이 없으면 첫 문장을 120자로 줄인다`() {
        val long = "가".repeat(150) + ". 짧다."
        val hook = generator.generate(sampleBook(introduction = long))!!.filterIsInstance<SlidePayload.Hook>().single()
        assertEquals(1, hook.sentences.size)
        assertEquals(120, hook.sentences[0].length)
        assertTrue(hook.sentences[0].endsWith("…"))
    }

    @Test
    fun `KEYWORDS는 3~6개다`() {
        val keywords = slides().filterIsInstance<SlidePayload.Keywords>().single().keywords
        assertTrue(keywords.size in 3..6, "$keywords")
        assertEquals(keywords.distinct(), keywords)
        assertTrue(keywords.all { it.length >= 2 })
        assertTrue("고요" in keywords || "나무" in keywords, "$keywords")
    }

    @Test
    fun `키워드가 1~2개뿐이면 있는 만큼 만든다`() {
        val book = sampleBook(introduction = "x".repeat(30) + " 우주 우주", tableOfContents = null, title = "a")
        val s = generator.generate(book)!!
        val keywords = s.filterIsInstance<SlidePayload.Keywords>().single().keywords
        assertEquals(listOf("우주"), keywords)
        assertEquals(5, s.size)
    }

    @Test
    fun `키워드가 하나도 없으면 KEYWORDS 슬라이드를 생략한다`() {
        val s = generator.generate(sampleBook(introduction = "abc def ghi.", tableOfContents = null, title = "abc"))!!
        assertEquals(4, s.size)
        assertTrue(s.none { it is SlidePayload.Keywords })
        assertTrue(s.last() is SlidePayload.ForWhom)
    }

    @Test
    fun `FOR_WHOM은 KDC 대분류 템플릿을 쓰고, KDC가 없으면 기본 템플릿을 쓴다`() {
        fun lines(kdc: String?) = slides(kdc = kdc).filterIsInstance<SlidePayload.ForWhom>().single().lines

        assertEquals(KdcTemplates.forKdc("8"), lines("8"))
        assertEquals(KdcTemplates.forKdc("813.6"), lines("813.6"))
        assertEquals(KdcTemplates.default, lines(null))
        assertEquals(KdcTemplates.default, lines("x"))
        assertNotEquals(lines("8"), lines(null))
        assertEquals(2, lines("8").size)
        (0..9).forEach { assertEquals(2, KdcTemplates.forKdc("$it").size) }
    }

    @Test
    fun `표지가 없으면 COVER의 coverUrl은 null이다`() {
        val cover = slides(coverUrl = null).first() as SlidePayload.Cover
        assertNull(cover.coverUrl)
        assertEquals("조용한 숲", cover.title)
        assertEquals("홍길동", cover.author)
    }
}
