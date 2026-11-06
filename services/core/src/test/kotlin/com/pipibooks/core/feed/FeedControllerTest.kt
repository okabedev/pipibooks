package com.pipibooks.core.feed

import com.pipibooks.common.CursorCodec
import com.pipibooks.core.card.CardService
import com.pipibooks.core.support.CatalogTestData
import com.pipibooks.core.support.DbTestSupport
import com.pipibooks.eventschema.RedisKeys
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
class FeedControllerTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val cards: CardService,
    @Autowired private val jdbc: JdbcTemplate,
    @Autowired private val redis: StringRedisTemplate,
) : DbTestSupport() {
    @BeforeEach
    fun clean() {
        redis.connectionFactory!!.connection.use { it.serverCommands().flushAll() }
        jdbc.update("DELETE FROM core.card_slide")
        jdbc.update("DELETE FROM core.card")
        CatalogTestData.clear()
        repeat(3) {
            val bookId = CatalogTestData.insertBook("978%010d".format(it))
            cards.publishDraft(CatalogTestData.insertDraft(bookId))
        }
    }

    @Test
    fun `X-Anonymous-Id 없이도 피드를 준다`() {
        mockMvc.get("/v1/feed").andExpect {
            status { isOk() }
            jsonPath("$.items.length()") { value(3) }
            jsonPath("$.items[0].slides[0].type") { value("COVER") }
            jsonPath("$.nextCursor") { value(null) }
        }
    }

    @Test
    fun `UUID가 아닌 X-Anonymous-Id는 없는 것으로 본다`() {
        mockMvc.get("/v1/feed") { header("X-Anonymous-Id", "not-a-uuid") }.andExpect {
            status { isOk() }
            jsonPath("$.items.length()") { value(3) }
        }
    }

    @Test
    fun `X-Anonymous-Id의 본 카드는 제외된다`() {
        val anon = UUID.randomUUID().toString()
        val cardId = jdbc.queryForObject("SELECT id FROM core.card LIMIT 1", Long::class.java)!!
        redis.opsForZSet().add(RedisKeys.seenCards(anon), cardId.toString(), System.currentTimeMillis().toDouble())

        mockMvc.get("/v1/feed") { header("X-Anonymous-Id", anon) }.andExpect {
            status { isOk() }
            jsonPath("$.items.length()") { value(2) }
        }
    }

    @Test
    fun `깨진 커서는 400 INVALID_CURSOR`() {
        mockMvc.get("/v1/feed?cursor=%%%").andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_CURSOR") }
        }
    }

    @Test
    fun `offset이 숫자가 아니거나 음수인 커서는 400 INVALID_CURSOR`() {
        listOf("abc", "-1").forEach { o ->
            val cursor = CursorCodec.encode(mapOf("s" to "1", "o" to o))
            mockMvc.get("/v1/feed?cursor=$cursor").andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("INVALID_CURSOR") }
            }
        }
    }
}
