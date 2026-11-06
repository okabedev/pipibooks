package com.pipibooks.core.card

import com.pipibooks.core.support.CatalogTestData
import com.pipibooks.core.support.DbTestSupport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate

@SpringBootTest
class CardQueryTest(
    @Autowired private val query: CardQuery,
    @Autowired private val bookQuery: BookQuery,
    @Autowired private val service: CardService,
    @Autowired private val jdbc: JdbcTemplate,
) : DbTestSupport() {
    @BeforeEach
    fun clean() {
        jdbc.update("DELETE FROM core.card_slide")
        jdbc.update("DELETE FROM core.card")
        CatalogTestData.clear()
    }

    private fun publish(
        isbn: String,
        kdc: String? = "813.7",
    ): Long = service.publishDraft(CatalogTestData.insertDraft(CatalogTestData.insertBook(isbn, kdc = kdc)))

    @Test
    fun `publishedViews는 PUBLISHED가 아닌 카드를 빼고 입력 순서를 유지한다`() {
        val a = publish("9782222222221")
        val b = publish("9782222222222")
        val c = publish("9782222222223")
        jdbc.update("UPDATE core.card SET status = 'HIDDEN' WHERE id = ?", b)

        val views = query.publishedViews(listOf(c, b, a, 999L))

        assertEquals(listOf(c.toString(), a.toString()), views.map { it.id })
        assertEquals(listOf("9782222222223", "9782222222221"), views.map { it.isbn13 })
    }

    @Test
    fun `candidates는 최근 발행순이며 kdcMajor를 담는다`() {
        val a = publish("9782222222231", kdc = "813.7")
        val b = publish("9782222222232", kdc = "005")
        val c = publish("9782222222233", kdc = null)
        jdbc.update("UPDATE core.card SET published_at = now() - interval '2 day' WHERE id = ?", a)
        jdbc.update("UPDATE core.card SET published_at = now() - interval '1 day' WHERE id = ?", b)

        val list = query.candidates(2)

        assertEquals(listOf(c, b), list.map { it.cardId })
        assertEquals(listOf(null, 0), list.map { it.kdcMajor })
        assertEquals(true, list.all { it.bookId > 0 })
    }

    @Test
    fun `BookQuery는 kdc 첫 글자가 숫자일 때만 kdcMajor를 준다`() {
        val x = CatalogTestData.insertBook("9782222222241", kdc = "813.7")
        val y = CatalogTestData.insertBook("9782222222242", kdc = null)
        val z = CatalogTestData.insertBook("9782222222243", kdc = "N/A")

        val map = bookQuery.findAll(listOf(x, y, z))

        assertEquals(8, map[x]?.kdcMajor)
        assertEquals(null, map[y]?.kdcMajor)
        assertEquals(null, map[z]?.kdcMajor)
        assertEquals("9782222222241", bookQuery.find(x)?.isbn13)
        assertEquals(null, bookQuery.find(1L))
        assertEquals(emptyMap<Long, BookSummary>(), bookQuery.findAll(emptyList()))
    }
}
