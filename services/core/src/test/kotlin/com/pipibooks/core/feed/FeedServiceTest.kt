package com.pipibooks.core.feed

import com.pipibooks.common.CursorCodec
import com.pipibooks.core.card.CardService
import com.pipibooks.core.support.CatalogTestData
import com.pipibooks.core.support.DbTestSupport
import com.pipibooks.eventschema.RedisKeys
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Duration
import java.time.Instant
import java.util.UUID

@SpringBootTest
class FeedServiceTest(
    @Autowired private val service: FeedService,
    @Autowired private val cards: CardService,
    @Autowired private val jdbc: JdbcTemplate,
    @Autowired private val redis: StringRedisTemplate,
) : DbTestSupport() {
    private val anon = UUID.randomUUID().toString()
    private val viewer = Viewer(memberId = null, anonymousId = anon)

    @BeforeEach
    fun clean() {
        redis.connectionFactory!!.connection.use { it.serverCommands().flushAll() }
        jdbc.update("DELETE FROM core.card_slide")
        jdbc.update("DELETE FROM core.card")
        CatalogTestData.clear()
    }

    private fun publish(n: Int): List<Long> =
        (1..n).map {
            val bookId = CatalogTestData.insertBook("978%010d".format(it))
            cards.publishDraft(CatalogTestData.insertDraft(bookId))
        }

    private fun ids(page: FeedPage) = page.items.map { it.id.toLong() }

    private fun seen(
        cardId: Long,
        at: Instant = Instant.now(),
    ) {
        redis.opsForZSet().add(RedisKeys.seenCards(anon), cardId.toString(), at.toEpochMilli().toDouble())
    }

    @Test
    fun `발행 카드가 0장이면 빈 목록과 nextCursor null`() {
        val page = service.page(viewer, null)

        assertTrue(page.items.isEmpty())
        assertNull(page.nextCursor)
    }

    @Test
    fun `카드가 20장보다 적으면 한 페이지로 끝난다`() {
        publish(5)

        val page = service.page(viewer, null)

        assertEquals(5, page.items.size)
        assertNull(page.nextCursor)
    }

    @Test
    fun `커서로 다음 20장을 이어 준다`() {
        publish(45)

        val p1 = service.page(viewer, null)
        val p2 = service.page(viewer, p1.nextCursor)
        val p3 = service.page(viewer, p2.nextCursor)

        assertEquals(listOf(20, 20, 5), listOf(p1, p2, p3).map { it.items.size })
        assertNotNull(p1.nextCursor)
        assertNotNull(p2.nextCursor)
        assertNull(p3.nextCursor)
        val all = ids(p1) + ids(p2) + ids(p3)
        assertEquals(45, all.toSet().size)
    }

    @Test
    fun `정확히 20장이면 nextCursor가 없다`() {
        publish(20)

        assertNull(service.page(viewer, null).nextCursor)
    }

    @Test
    fun `본 카드 집합에 있는 카드는 후보에서 빠진다`() {
        val published = publish(5)
        seen(published[0])
        seen(published[1])

        val got = ids(service.page(viewer, null))

        assertEquals(published.drop(2).toSet(), got.toSet())
    }

    @Test
    fun `7일보다 오래전에 본 카드는 다시 후보가 된다`() {
        val published = publish(3)
        seen(published[0], Instant.now().minus(Duration.ofDays(8)))
        seen(published[1], Instant.now().minus(Duration.ofDays(1)))

        val got = ids(service.page(viewer, null))

        assertEquals(setOf(published[0], published[2]), got.toSet())
    }

    @Test
    fun `응답만 한 카드는 다음 세션에서도 빠지지 않는다`() {
        publish(5)

        val first = ids(service.page(viewer, null))
        val second = ids(service.page(viewer, null))

        assertEquals(5, first.size)
        assertEquals(first.toSet(), second.toSet())
    }

    @Test
    fun `세션 중 숨겨진 카드는 다음 페이지에서 빠진다`() {
        val published = publish(25)
        val p1 = service.page(viewer, null)
        jdbc.update("UPDATE core.card SET status = 'HIDDEN' WHERE id = ?", published[0])

        val p2 = service.page(viewer, p1.nextCursor)
        val rest = ids(p2)

        assertTrue(published[0] !in rest)
        assertEquals(5 - if (published[0] in ids(p1)) 0 else 1, rest.size)
    }

    @Test
    fun `만료된 세션의 커서는 새 세션으로 이어진다`() {
        publish(25)
        val p1 = service.page(viewer, null)
        redis.delete(redis.keys("feed:session:*"))

        val p2 = service.page(viewer, p1.nextCursor)

        assertEquals(20, p2.items.size)
        val sid = CursorCodec.decode(p2.nextCursor!!)["s"]!!
        assertEquals("20", CursorCodec.decode(p2.nextCursor)["o"])
        assertTrue(redis.hasKey(RedisKeys.feedSession(sid)))
    }

    @Test
    fun `세션은 30분 TTL로 저장된다`() {
        publish(3)
        val p = service.page(viewer, null)
        assertNull(p.nextCursor)

        val key = redis.keys("feed:session:*").single()
        val ttl = redis.getExpire(key)
        assertTrue(ttl in 1..1800, "ttl=$ttl")
    }

    @Test
    fun `익명 ID 없이도 피드를 준다`() {
        publish(3)

        assertEquals(3, service.page(Viewer(null, null), null).items.size)
    }
}
