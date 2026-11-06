package com.pipibooks.core.card

import com.pipibooks.common.ApiException
import com.pipibooks.core.support.CatalogTestData
import com.pipibooks.core.support.DbTestSupport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Instant

@SpringBootTest
class CardServiceTest(
    @Autowired private val service: CardService,
    @Autowired private val jdbc: JdbcTemplate,
) : DbTestSupport() {
    @BeforeEach
    fun clean() {
        jdbc.update("DELETE FROM core.card_slide")
        jdbc.update("DELETE FROM core.card")
        jdbc.update("DELETE FROM core.sync_cursor")
        CatalogTestData.clear()
    }

    private fun cards(bookId: Long) = jdbc.queryForList("SELECT * FROM core.card WHERE book_id = ?", bookId)

    @Test
    fun `자동 발행 가능한 새 초안은 AUTO PUBLISHED 카드가 된다`() {
        val bookId = CatalogTestData.insertBook("9780000000001")
        CatalogTestData.insertDraft(bookId)

        assertEquals(1, service.syncDrafts())

        val card = cards(bookId).single()
        assertEquals("AUTO", card["source"])
        assertEquals("PUBLISHED", card["status"])
        assertNotNull(card["published_at"])
        val slides = jdbc.queryForList("SELECT position, type FROM core.card_slide WHERE card_id = ?", card["id"])
        assertEquals(listOf(mapOf("position" to 0, "type" to "COVER")), slides)
    }

    @Test
    fun `자동 발행 불가 초안은 카드를 만들지 않는다`() {
        val bookId = CatalogTestData.insertBook("9780000000002")
        val draftId = CatalogTestData.insertDraft(bookId, autoPublishable = false)

        assertEquals(0, service.syncDrafts())

        assertEquals(0, cards(bookId).size)
        // 커서는 자동 발행 불가 초안도 지나간다
        val cursor = jdbc.queryForObject("SELECT last_id FROM core.sync_cursor", Long::class.java)
        assertEquals(draftId, cursor)
    }

    @Test
    fun `이미 카드가 있는 책의 새 초안은 카드를 바꾸지 않는다`() {
        val bookId = CatalogTestData.insertBook("9780000000003")
        CatalogTestData.insertDraft(bookId, CatalogTestData.coverSlides("첫 번째"))
        assertEquals(1, service.syncDrafts())

        CatalogTestData.insertDraft(bookId, CatalogTestData.coverSlides("두 번째"))
        assertEquals(0, service.syncDrafts())

        val payload = jdbc.queryForObject("SELECT payload::text FROM core.card_slide", String::class.java)!!
        assertEquals(true, payload.contains("첫 번째"))
        assertEquals(1, cards(bookId).size)
    }

    @Test
    fun `같은 책의 자동 발행 초안 두 건은 카드 하나만 만든다`() {
        val bookId = CatalogTestData.insertBook("9780000000004")
        CatalogTestData.insertDraft(bookId, CatalogTestData.coverSlides("먼저"))
        CatalogTestData.insertDraft(bookId, CatalogTestData.coverSlides("나중"))

        assertEquals(1, service.syncDrafts())

        assertEquals(1, cards(bookId).size)
        val payload = jdbc.queryForObject("SELECT payload::text FROM core.card_slide", String::class.java)!!
        assertEquals(true, payload.contains("먼저"))
    }

    @Test
    fun `동기화 커서 이후의 초안만 읽는다`() {
        val a = CatalogTestData.insertBook("9780000000005")
        CatalogTestData.insertDraft(a)
        assertEquals(1, service.syncDrafts())
        assertEquals(0, service.syncDrafts())

        val b = CatalogTestData.insertBook("9780000000006")
        CatalogTestData.insertDraft(b)
        assertEquals(1, service.syncDrafts())
    }

    @Test
    fun `생성된 지 60초가 지나지 않은 초안은 읽지 않는다`() {
        val bookId = CatalogTestData.insertBook("9780000000007")
        CatalogTestData.insertDraft(bookId, createdAt = Instant.now())

        assertEquals(0, service.syncDrafts())
        assertEquals(0, cards(bookId).size)
    }

    @Test
    fun `최신 초안 다시 적용은 슬라이드를 교체하고 edited_at을 남긴다`() {
        val bookId = CatalogTestData.insertBook("9780000000008")
        CatalogTestData.insertDraft(bookId, CatalogTestData.coverSlides("옛 표지"))
        service.syncDrafts()
        val cardId = cards(bookId).single()["id"] as Long
        assertNull(cards(bookId).single()["edited_at"])
        val newer =
            """[{"type":"COVER","title":"새 표지","author":"저자","coverUrl":null},{"type":"HOOK","sentences":["한 줄"]}]"""
        CatalogTestData.insertDraft(bookId, newer, autoPublishable = false)

        service.reapplyLatestDraft(cardId)

        val slides =
            jdbc.queryForList(
                "SELECT position, type, payload::text AS p FROM core.card_slide WHERE card_id = ? ORDER BY position",
                cardId,
            )
        assertEquals(listOf("COVER", "HOOK"), slides.map { it["type"] })
        assertEquals(true, (slides[0]["p"] as String).contains("새 표지"))
        val card = cards(bookId).single()
        assertNotNull(card["edited_at"])
        assertEquals("PUBLISHED", card["status"])
    }

    @Test
    fun `초안 발행은 CURATED PUBLISHED 카드를 만든다`() {
        val bookId = CatalogTestData.insertBook("9780000000009")
        val draftId = CatalogTestData.insertDraft(bookId, autoPublishable = false)

        val cardId = service.publishDraft(draftId)

        val card = cards(bookId).single()
        assertEquals(cardId, card["id"])
        assertEquals("CURATED", card["source"])
        assertEquals("PUBLISHED", card["status"])
    }

    @Test
    fun `없는 초안을 발행하면 404 DRAFT_NOT_FOUND`() {
        val e = assertThrows<ApiException> { service.publishDraft(1L) }
        assertEquals("DRAFT_NOT_FOUND", e.code)
        assertEquals(404, e.status.value())
    }

    @Test
    fun `이미 카드가 있는 책의 초안을 발행하면 409 CARD_ALREADY_EXISTS`() {
        val bookId = CatalogTestData.insertBook("9780000000010")
        val draftId = CatalogTestData.insertDraft(bookId)
        service.publishDraft(draftId)

        val e = assertThrows<ApiException> { service.publishDraft(draftId) }
        assertEquals("CARD_ALREADY_EXISTS", e.code)
        assertEquals(409, e.status.value())
    }

    @Test
    fun `없는 카드에 최신 초안을 적용하면 404 CARD_NOT_FOUND`() {
        val e = assertThrows<ApiException> { service.reapplyLatestDraft(1L) }
        assertEquals("CARD_NOT_FOUND", e.code)
    }

    @Test
    fun `책에 초안이 없으면 최신 초안 적용은 404 DRAFT_NOT_FOUND`() {
        val bookId = CatalogTestData.insertBook("9780000000011")
        val draftId = CatalogTestData.insertDraft(bookId)
        val cardId = service.publishDraft(draftId)
        CatalogTestData.clear() // 초안과 책 삭제

        val e = assertThrows<ApiException> { service.reapplyLatestDraft(cardId) }
        assertEquals("DRAFT_NOT_FOUND", e.code)
    }
}
