package com.pipibooks.core.card

import com.pipibooks.core.support.CatalogTestData
import com.pipibooks.core.support.DbTestSupport
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@SpringBootTest
@AutoConfigureMockMvc
class CardControllerTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val service: CardService,
    @Autowired private val jdbc: JdbcTemplate,
) : DbTestSupport() {
    @BeforeEach
    fun clean() {
        jdbc.update("DELETE FROM core.card_slide")
        jdbc.update("DELETE FROM core.card")
        CatalogTestData.clear()
    }

    private fun publishedCard(isbn: String): Long {
        val bookId = CatalogTestData.insertBook(isbn)
        return service.publishDraft(CatalogTestData.insertDraft(bookId))
    }

    @Test
    fun `발행된 카드를 조회한다`() {
        val cardId = publishedCard("9781111111111")

        mockMvc.get("/v1/cards/$cardId").andExpect {
            status { isOk() }
            jsonPath("$.id") { value(cardId.toString()) }
            jsonPath("$.isbn13") { value("9781111111111") }
            jsonPath("$.slides[0].type") { value("COVER") }
        }
    }

    @Test
    fun `숨김 카드는 404 CARD_NOT_FOUND`() {
        val cardId = publishedCard("9781111111112")
        jdbc.update("UPDATE core.card SET status = 'HIDDEN' WHERE id = ?", cardId)

        mockMvc.get("/v1/cards/$cardId").andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("CARD_NOT_FOUND") }
        }
    }

    @Test
    fun `숫자가 아닌 ID는 404`() {
        mockMvc.get("/v1/cards/abc").andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("CARD_NOT_FOUND") }
        }
    }
}
